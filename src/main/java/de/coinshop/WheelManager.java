package de.coinshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Das Glücksrad.
 *
 * - Eine Drehung kostet Coins; der Einsatz geht aufs Konto des Shops.
 * - Der Jackpot ist das GANZE Shop-Konto (Chance in der config, Standard 1 zu 1000).
 * - Dazu gibt es Trostpreise mit festen Beträgen, die ebenfalls vom Shop-Konto bezahlt werden
 *   (höchstens so viel, wie gerade darauf ist).
 *
 * Wichtig: Das Ergebnis wird sofort in der Datenbank entschieden und bezahlt. Die Animation im
 * Menü ist nur Show - wer das Menü währenddessen schließt, bekommt trotzdem seinen Gewinn.
 */
public final class WheelManager {

    /** Kennung des Spiels: "wheel", "slots" oder "scratch" (auch für das Protokoll und die Texte). */
    private final String id;
    /** Wo die Einstellungen in der config.yml stehen, z. B. "wheel" oder "casino.slots". */
    private final String path;

    /** Ein Feld auf dem Rad. */
    public record Prize(boolean jackpot, long amount, double weight) { }

    /** Das Ergebnis einer Drehung. "payout" ist der tatsächlich ausgezahlte Betrag. */
    public record SpinResult(Prize prize, long payout) { }

    private final CoinShopPlugin plugin;
    private final Random random = new Random();
    private final Set<UUID> spinning = ConcurrentHashMap.newKeySet();

    /** Jedes Casino-Spiel hat seine eigene Verwaltung mit eigenen Preisen und Chancen. */
    public WheelManager(CoinShopPlugin plugin, String id, String path) {
        this.plugin = plugin;
        this.id = id;
        this.path = path;
    }

    public String id() {
        return id;
    }

    /** Anzeigename des Spiels (aus messages.yml). */
    public Component displayName() {
        return plugin.messages().get("gui.casino.game-" + id);
    }

    // ------------------------------------------------------------------
    //  Einstellungen
    // ------------------------------------------------------------------

    public long price() {
        return Math.max(1, plugin.getConfig().getLong(path + ".price", 10));
    }

    /** Der Jackpot (= das GANZE Shop-Konto) hat die Chance 1 zu X. */
    public long jackpotOdds() {
        return Math.max(1, plugin.getConfig().getLong(path + ".jackpot-odds", 10000));
    }

    public double jackpotChance() {
        return 1.0 / jackpotOdds();
    }

    /** Eine Gewinnstufe: Chance 1 zu "odds"; gewonnen wird einer der Beträge (zufällig, alle gleich wahrscheinlich). */
    public record Tier(long odds, List<Long> amounts) { }

    /** Die Gewinnstufen aus der config, die seltenste zuerst. */
    public List<Tier> tiers() {
        List<Tier> list = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path + ".tiers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                long odds = Math.max(1, s.getLong("odds", 100));
                List<Long> amounts = new ArrayList<>();
                for (long amount : s.getLongList("amounts")) {
                    if (amount > 0) {
                        amounts.add(amount);
                    }
                }
                if (!amounts.isEmpty()) {
                    list.add(new Tier(odds, amounts));
                }
            }
        }
        list.sort((x, y) -> Long.compare(y.odds(), x.odds()));
        return list;
    }

    private Prize jackpotPrize() {
        return new Prize(true, 0, jackpotChance());
    }

    // ------------------------------------------------------------------
    //  Auslosen
    // ------------------------------------------------------------------

    /**
     * Wählt zufällig ein Feld (mit den echten Wahrscheinlichkeiten): Eine Zufallszahl zwischen 0 und 1
     * wird nacheinander gegen Jackpot, die seltenste Stufe, ... bis zur häufigsten Stufe geprüft.
     * Passt nichts, ist es eine Niete.
     */
    public Prize roll() {
        double r = random.nextDouble();
        double limit = jackpotChance();
        if (r < limit) {
            return jackpotPrize();
        }
        for (Tier tier : tiers()) {
            limit += 1.0 / tier.odds();
            if (r < limit) {
                long amount = tier.amounts().get(random.nextInt(tier.amounts().size()));
                return new Prize(false, amount, 1);
            }
        }
        return new Prize(false, 0, 1); // Niete
    }

    /** Nur für die Animation: ein beliebiges Feld, alle gleich wahrscheinlich (zählt NICHT als Ergebnis). */
    public Prize decorativeRoll() {
        List<Prize> all = new ArrayList<>();
        all.add(new Prize(false, 0, 1));
        for (Tier tier : tiers()) {
            for (long amount : tier.amounts()) {
                all.add(new Prize(false, amount, 1));
            }
        }
        all.add(jackpotPrize());
        return all.get(random.nextInt(all.size()));
    }

    public Random random() {
        return random;
    }

    // ------------------------------------------------------------------
    //  Drehen
    // ------------------------------------------------------------------

    /** Gewonnene Mystery Boxen, die nach der Animation übergeben werden. */
    private final java.util.Map<UUID, Integer> boxPending = new java.util.HashMap<>();

    private String displayNamePlain() {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName());
    }

    public boolean isSpinning(UUID uuid) {
        return spinning.contains(uuid);
    }

    public void endSpin(UUID uuid) {
        spinning.remove(uuid);
    }

    /**
     * Bezahlt den Einsatz, entscheidet das Ergebnis und zahlt den Gewinn aus.
     * Gibt null zurück, wenn nicht gedreht werden kann (die Nachricht wurde schon gesendet).
     */
    public SpinResult trySpin(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!spinning.add(uuid)) {
            return null; // dreht gerade schon
        }

        long price = price();
        db.begin();
        try {
            // Lizenz "freispiel": das erste Spiel des Tages ist gratis
            boolean free = plugin.licenses().consumeFreePlay(uuid);
            if (free) {
                price = 0;
            } else if (!db.takeCoins(uuid, price)) {
                db.rollback();
                spinning.remove(uuid);
                msg.send(player, "wheel-not-enough", "price", price());
                return null;
            }
            if (price > 0) {
                db.addShopAccount(price); // Einsatz aufs Shop-Konto
            }

            Prize prize = roll();
            // Zweitseltenste Belohnung (nach dem Jackpot): eine Mystery Box, zusätzlich zum normalen Ergebnis
            boolean boxWon = !prize.jackpot() && random.nextDouble() < plugin.mascot().luck(uuid) / Math.max(1, plugin.getConfig().getLong(path + ".box-odds", 5000));
            long account = db.getShopAccount();
            long wanted = prize.jackpot() ? account : prize.amount();
            long payout = Math.min(wanted, account);
            // Lizenz "glueck": Gewinne (nicht der Jackpot) sind um Prozent höher, soweit das Shop-Konto reicht
            if (!prize.jackpot() && payout > price && plugin.licenses().has(uuid, "glueck")) {
                long extra = plugin.licenses().boost(uuid, "glueck", payout) - payout;
                payout += Math.max(0, Math.min(extra, account - payout));
            }
            // Ereignis "Casino-Fieber": Gewinne (nicht der Jackpot) sind um Prozent höher, soweit das Shop-Konto reicht
            double casinoPercent = plugin.events().casinoBonusPercent() + plugin.titles().effect(uuid, "casino-bonus", "");
            if (!prize.jackpot() && payout > price && casinoPercent != 0) {
                long extra = (long) Math.floor(payout * casinoPercent / 100.0);
                if (extra > 0) {
                    payout += Math.min(extra, account - payout);
                } else {
                    payout = Math.max(0, payout + extra); // Pechsträhne
                }
            }
            if (payout > 0) {
                db.takeShopAccount(payout);
                db.addCoins(uuid, payout);
            }
            db.commit();
            db.logTx(uuid, player.getName(), logType(), payout - price,
                    prize.jackpot() ? "JACKPOT" : "Gewinn " + payout);
            if (free) {
                msg.send(player, "freeplay-used");
            }
            // Geheimer Erfolg "Gegengamble": verloren und gleich wieder gespielt
            plugin.secrets().onCasinoStart(uuid);
            plugin.secrets().onCasinoResult(uuid, payout < price);
            if (boxWon) {
                boxPending.put(uuid, plugin.getConfig().getInt(path + ".box-tier", 2));
            }
            return new SpinResult(prize, payout);
        } catch (RuntimeException e) {
            db.rollback();
            spinning.remove(uuid);
            plugin.getLogger().severe("Fehler beim Glücksrad: " + e.getMessage());
            msg.send(player, "error");
            return null;
        }
    }

    private String logType() {
        return switch (id) {
            case "slots" -> "spielautomat";
            case "scratch" -> "rubbellos";
            default -> "gluecksrad";
        };
    }

    /** Sagt dem Spieler (und bei Jackpot allen), was herausgekommen ist. */
    public void announce(Player player, SpinResult result) {
        Messages msg = plugin.messages();
        Integer boxTier = boxPending.remove(player.getUniqueId());
        if (boxTier != null) {
            plugin.mysteryBoxes().give(player, boxTier, displayNamePlain());
        }
        if (result.prize().jackpot()) {
            msg.send(player, "wheel-jackpot", "coins", result.payout());
            Bukkit.getServer().broadcast(msg.get("prefix").append(
                    msg.get("wheel-jackpot-broadcast", "player", player.getName(), "coins", result.payout(),
                            "game", displayName())));
        } else if (result.prize().amount() <= 0) {
            msg.send(player, "wheel-nothing");
        } else if (result.payout() < result.prize().amount()) {
            msg.send(player, "wheel-win-capped", "coins", result.payout());
        } else {
            msg.send(player, "wheel-win", "coins", result.payout());
        }
    }

    // ------------------------------------------------------------------
    //  Darstellung eines Feldes im Menü
    // ------------------------------------------------------------------

    /** "jackpotAmount" ist die Summe, die beim Jackpot-Feld angezeigt wird. */
    public ItemStack segmentItem(Prize prize, long jackpotAmount) {
        Messages msg = plugin.messages();
        if (prize.jackpot()) {
            return ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.wheel.seg-jackpot", "coins", jackpotAmount),
                    List.of());
        }
        long amount = prize.amount();
        Material pane;
        Component name;
        if (amount <= 0) {
            pane = Material.GRAY_STAINED_GLASS_PANE;
            name = msg.get("gui.wheel.seg-nothing");
        } else {
            name = msg.get("gui.wheel.seg-coins", "coins", amount);
            if (amount <= 50) {
                pane = Material.LIME_STAINED_GLASS_PANE;
            } else if (amount <= 100) {
                pane = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            } else if (amount <= 250) {
                pane = Material.YELLOW_STAINED_GLASS_PANE;
            } else if (amount <= 500) {
                pane = Material.ORANGE_STAINED_GLASS_PANE;
            } else {
                pane = Material.RED_STAINED_GLASS_PANE;
            }
        }
        return ItemUtil.icon(pane, name, List.of());
    }
}
