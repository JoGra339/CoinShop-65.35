package de.coinshop;

import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Sklavenhandel im Schwarzmarkt. Es gibt mehrere Versteigerungen gleichzeitig:
 * <ul>
 *   <li>Die "Kreatur des Tages": einmal am Tag ein Begleiter mit schlechten Werten, angeboten vom Shop.</li>
 *   <li>Eigene Angebote der Spieler: Wer einen Begleiter nicht mehr will, kann ihn hier versteigern.</li>
 * </ul>
 * Alle Versteigerungen enden beim nächsten Shop-Wechsel. Das Gebot wird sofort abgebucht, wer überboten wird, bekommt
 * sein Geld zurück. Außerdem kann man einen eigenen Begleiter direkt an den Shop verkaufen (halber Wert).
 */
public final class CreatureAuction {

    /** Eine laufende Versteigerung. */
    public static final class Lot {
        public long id;
        /** null = der Shop selbst (Kreatur des Tages). */
        public UUID seller;
        public String sellerName = "";
        public Companion companion;
        public int rarity;
        public int generation = 1;
        public long kills;
        public List<String> traits = new ArrayList<>();
        public long minBid;
        public long bid;
        public UUID bidder;
        public String bidderName = "";
        /** Mystery Box (Stufe, 0 = keine), die der Gewinner zur Kreatur dazubekommt. */
        public int bonusBox;

        public boolean system() {
            return seller == null;
        }
    }

    private final CoinShopPlugin plugin;
    private final Random random = new Random();
    private final List<Lot> lots = new ArrayList<>();
    private boolean loaded;
    private long nextId = 1;

    CreatureAuction(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("creature-auction.enabled", true);
    }

    private File file() {
        return new File(plugin.getDataFolder(), "creature-auction.yml");
    }

    // ------------------------------------------------------------------
    //  Laden und Speichern
    // ------------------------------------------------------------------

    private static UUID uuidOf(String s) {
        try {
            return s == null || s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Lot readLot(ConfigurationSection y, long id) {
        Companion c = Companion.byKey(y.getString("companion", ""));
        if (c == null) {
            return null;
        }
        Lot l = new Lot();
        l.id = id;
        l.seller = uuidOf(y.getString("seller"));
        l.sellerName = y.getString("seller-name", "");
        l.companion = c;
        l.rarity = y.getInt("rarity");
        l.generation = Math.max(1, y.getInt("generation", 1));
        l.kills = y.getLong("kills");
        l.traits = new ArrayList<>(y.getStringList("traits"));
        l.minBid = y.getLong("min-bid");
        l.bid = y.getLong("bid");
        l.bidder = uuidOf(y.getString("bidder"));
        l.bidderName = y.getString("bidder-name", "");
        l.bonusBox = y.getInt("bonus-box", 0);
        return l;
    }

    private void load() {
        loaded = true;
        lots.clear();
        File f = file();
        if (!f.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        nextId = Math.max(1, y.getLong("next-id", 1));
        if (y.contains("companion")) {
            // altes Format (Version 48): genau eine Versteigerung
            Lot old = readLot(y, nextId++);
            if (old != null) {
                lots.add(old);
            }
            return;
        }
        ConfigurationSection sec = y.getConfigurationSection("lots");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection one = sec.getConfigurationSection(key);
            long id;
            try {
                id = Long.parseLong(key);
            } catch (NumberFormatException e) {
                continue;
            }
            if (one == null) {
                continue;
            }
            Lot l = readLot(one, id);
            if (l != null) {
                lots.add(l);
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", nextId);
        for (Lot l : lots) {
            String p = "lots." + l.id + ".";
            y.set(p + "seller", l.seller == null ? "" : l.seller.toString());
            y.set(p + "seller-name", l.sellerName == null ? "" : l.sellerName);
            y.set(p + "companion", l.companion.key);
            y.set(p + "rarity", l.rarity);
            y.set(p + "generation", l.generation);
            y.set(p + "kills", l.kills);
            y.set(p + "traits", l.traits);
            y.set(p + "min-bid", l.minBid);
            y.set(p + "bid", l.bid);
            y.set(p + "bidder", l.bidder == null ? "" : l.bidder.toString());
            y.set(p + "bidder-name", l.bidderName == null ? "" : l.bidderName);
            y.set(p + "bonus-box", l.bonusBox);
        }
        try {
            y.save(file());
        } catch (IOException e) {
            plugin.getLogger().warning("Sklavenhandel konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Alle laufenden Versteigerungen (die Kreatur des Tages zuerst); legt bei Bedarf die Tageskreatur an. */
    public List<Lot> lots() {
        if (!loaded) {
            load();
        }
        boolean hasSystem = false;
        for (Lot l : lots) {
            if (l.system()) {
                hasSystem = true;
                break;
            }
        }
        if (!hasSystem) {
            createDaily();
        }
        List<Lot> sorted = new ArrayList<>(lots);
        sorted.sort((a, b) -> {
            if (a.system() != b.system()) {
                return a.system() ? -1 : 1;
            }
            return Long.compare(a.id, b.id);
        });
        return sorted;
    }

    public Lot byId(long id) {
        for (Lot l : lots()) {
            if (l.id == id) {
                return l;
            }
        }
        return null;
    }

    private void createDaily() {
        Companion[] all = Companion.values();
        if (all.length == 0) {
            return;
        }
        Lot l = new Lot();
        l.id = nextId++;
        l.companion = all[random.nextInt(all.length)];
        l.rarity = 0;
        // Nur schlechte Eigenschaften: keine, eine oder zwei der beiden Schwächen
        int n = random.nextInt(3);
        if (n >= 1) {
            l.traits.add(random.nextBoolean() ? CompanionTraits.Trait.SCHWAECHLICH.name() : CompanionTraits.Trait.FAUL.name());
        }
        if (n == 2) {
            String other = l.traits.get(0).equals(CompanionTraits.Trait.FAUL.name())
                    ? CompanionTraits.Trait.SCHWAECHLICH.name() : CompanionTraits.Trait.FAUL.name();
            l.traits.add(other);
        }
        double percent = Math.max(1, plugin.getConfig().getDouble("creature-auction.start-percent", 20));
        l.minBid = Math.max(Math.max(1, plugin.getConfig().getLong("creature-auction.min-start", 200)),
                Math.round(l.companion.defaultPrice * percent / 100.0));
        // Manchmal gibt es eine Mystery Box obendrauf (Stufe 1, selten Stufe 2)
        if (plugin.mysteryBoxes().enabled()
                && random.nextInt(100) < Math.max(0, plugin.getConfig().getInt("creature-auction.bundle-box-percent", 30))) {
            l.bonusBox = random.nextInt(100) < 30 ? 2 : 1;
        }
        lots.add(l);
        save();
    }

    public long minNextBid(Lot l) {
        if (l.bidder == null) {
            return l.minBid;
        }
        long step = Math.max(1, (long) Math.ceil(l.bid * Math.max(1, plugin.getConfig().getInt("creature-auction.min-raise-percent", 10)) / 100.0));
        return l.bid + step;
    }

    public static String traitText(List<String> traits) {
        if (traits.isEmpty()) {
            return "keine Eigenschaften";
        }
        List<String> names = new ArrayList<>();
        for (String t : traits) {
            try {
                names.add(CompanionTraits.Trait.valueOf(t).label);
            } catch (IllegalArgumentException e) {
                names.add(t);
            }
        }
        return String.join(", ", names);
    }

    public String traitText(Lot l) {
        return traitText(l.traits);
    }

    public long millisLeft() {
        return Math.max(0, plugin.serverShop().timeUntilReset().toMillis());
    }

    // ------------------------------------------------------------------
    //  Wert, Verkauf an den Shop, eigene Angebote
    // ------------------------------------------------------------------

    /** Der Wert eines Exemplars: Grundpreis der Art mal Seltenheit mal Level-Bonus. */
    public long valueOf(UUID uuid, Copy k) {
        ExpeditionManager ex = plugin.expeditions();
        CompanionRoster.Info info = ex.info(uuid, k.companion(), k.index());
        int level = ex.copyLevel(uuid, k.companion(), k.index());
        double bonus = 1.0 + Math.max(0, plugin.getConfig().getDouble("creature-auction.level-bonus-percent", 5)) / 100.0 * (level - 1);
        return Math.max(1, Math.round(k.companion().defaultPrice * CompanionTraits.Rarity.of(info.rarity).stats * bonus));
    }

    /** Was der Shop für ein Exemplar zahlt (Standard: die Hälfte des Werts). */
    public long shopPrice(UUID uuid, Copy k) {
        double percent = Math.max(1, Math.min(100, plugin.getConfig().getDouble("creature-auction.sell-percent", 50)));
        return Math.max(1, Math.round(valueOf(uuid, k) * percent / 100.0));
    }

    public int maxLots() {
        return Math.max(1, plugin.getConfig().getInt("creature-auction.max-lots-per-player", 3));
    }

    public int lotsOf(UUID uuid) {
        int n = 0;
        for (Lot l : lots()) {
            if (uuid.equals(l.seller)) {
                n++;
            }
        }
        return n;
    }

    public long minutesBeforeReset() {
        return Math.max(0, plugin.getConfig().getLong("creature-auction.min-minutes-before-reset", 30));
    }

    /** Prüft, ob das Exemplar abgegeben werden darf; liefert null, sonst den Grund. */
    private String cannotGive(Player player, Copy k) {
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = player.getUniqueId();
        if (!enabled() || !plugin.blackMarket().allowed(player)) {
            return "Der Sklavenhandel ist für dich nicht offen.";
        }
        if (plugin.timeline().previewFor(player)) {
            return "In der Vorschau nicht möglich.";
        }
        if (!ex.exists(uuid, k.companion(), k.index())) {
            return "Dieses Exemplar gibt es nicht mehr.";
        }
        String busy = ex.busyReason(uuid, k.companion(), k.index());
        if (busy != null) {
            return "Gerade nicht verfügbar: " + busy;
        }
        return null;
    }

    private Lot lotFrom(Player player, Copy k) {
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = player.getUniqueId();
        CompanionRoster.Info info = ex.info(uuid, k.companion(), k.index());
        Lot l = new Lot();
        l.seller = uuid;
        l.sellerName = player.getName();
        l.companion = k.companion();
        l.rarity = info.rarity;
        l.generation = Math.max(1, info.generation);
        l.kills = ex.roster().kills(uuid, k.companion(), k.index());
        l.traits = new ArrayList<>(info.traits);
        return l;
    }

    /** Verkauft ein eigenes Exemplar an den Shop. */
    public void sellToShop(Player player, Copy k) {
        Messages msg = plugin.messages();
        String why = cannotGive(player, k);
        if (why != null) {
            msg.send(player, "beast-cannot", "why", why);
            return;
        }
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = player.getUniqueId();
        long price = shopPrice(uuid, k);
        String name = ex.trips().copyName(k.companion(), k.index());
        Database db = plugin.database();
        db.begin();
        try {
            db.addCoins(uuid, price);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Sklavenhandel: Verkauf an den Shop fehlgeschlagen: " + e);
            msg.send(player, "error");
            return;
        }
        ex.roster().markGone(uuid, k.companion(), k.index());
        ex.roster().save(uuid);
        db.logTx(uuid, player.getName(), "kreaturen-shop", price, "Begleiter an den Shop verkauft: " + name);
        msg.send(player, "beast-sold-shop", "name", name, "coins", price);
    }

    /** Stellt ein eigenes Exemplar als Versteigerung ein. */
    public void listCopy(Player player, Copy k, long minBid) {
        Messages msg = plugin.messages();
        String why = cannotGive(player, k);
        if (why != null) {
            msg.send(player, "beast-cannot", "why", why);
            return;
        }
        UUID uuid = player.getUniqueId();
        if (lotsOf(uuid) >= maxLots()) {
            msg.send(player, "beast-too-many", "max", maxLots());
            return;
        }
        long minMillis = minutesBeforeReset() * 60_000L;
        if (millisLeft() < minMillis) {
            msg.send(player, "beast-too-late", "minutes", minutesBeforeReset());
            return;
        }
        ExpeditionManager ex = plugin.expeditions();
        Lot l = lotFrom(player, k);
        l.minBid = Math.max(1, minBid);
        l.id = nextId++;
        String name = ex.trips().copyName(k.companion(), k.index());
        ex.roster().markGone(uuid, k.companion(), k.index());
        ex.roster().save(uuid);
        lots.add(l);
        save();
        plugin.database().logTx(uuid, player.getName(), "kreaturen-angebot", 0, "Begleiter versteigert: " + name + ", ab " + l.minBid);
        msg.send(player, "beast-listed", "name", name, "coins", l.minBid);
    }

    // ------------------------------------------------------------------
    //  Bieten
    // ------------------------------------------------------------------

    public void bid(Player player, long lotId, long amount) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (!enabled() || !plugin.blackMarket().allowed(player)) {
            return;
        }
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "bm-preview");
            return;
        }
        Lot l = byId(lotId);
        if (l == null) {
            msg.send(player, "beast-gone");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (uuid.equals(l.seller)) {
            msg.send(player, "beast-own");
            return;
        }
        long min = minNextBid(l);
        if (amount < min) {
            msg.send(player, "beast-too-low", "min", min);
            return;
        }
        UUID previous = l.bidder;
        long previousBid = l.bid;
        db.begin();
        try {
            if (previous != null) {
                db.addCoins(previous, previousBid);
            }
            if (!db.takeCoins(uuid, amount)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Sklavenhandel: Gebot fehlgeschlagen: " + e);
            msg.send(player, "error");
            return;
        }
        l.bid = amount;
        l.bidder = uuid;
        l.bidderName = player.getName();
        save();
        String what = l.companion.label;
        if (previous != null) {
            db.logTx(previous, db.getName(previous), "kreaturen-zurueck", previousBid, "überboten bei " + what);
            Player old = Bukkit.getPlayer(previous);
            if (old != null && !previous.equals(uuid)) {
                msg.send(old, "beast-outbid", "name", what, "player", player.getName(), "coins", amount);
            }
        }
        db.logTx(uuid, player.getName(), "kreaturen-gebot", -amount, "Gebot auf " + what);
        msg.send(player, "beast-bid-ok", "name", what, "coins", amount);
    }

    // ------------------------------------------------------------------
    //  Ende (täglich beim Shop-Wechsel)
    // ------------------------------------------------------------------

    private void giveTo(UUID target, Lot l) {
        ExpeditionManager ex = plugin.expeditions();
        if (l.bonusBox > 0 && !target.equals(l.seller)) {
            plugin.mysteryBoxes().give(target, plugin.database().getName(target), l.bonusBox, 1, "Sklavenhandel: Zugabe zur Kreatur");
        }
        int idx = ex.life().createCopy(target, new CompanionLife.Offspring(l.companion, l.rarity, l.generation, l.traits, l.kills));
        ex.roster().save(target);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            plugin.messages().send(online, target.equals(l.seller) ? "beast-returned" : "beast-won",
                    "name", l.companion.label, "number", idx + 1, "coins", l.bid);
        }
    }

    /** Beendet alle Versteigerungen: Gewinner bekommen ihre Kreatur, Verkäufer ihr Geld, Unverkauftes geht zurück. */
    public void dailyReset() {
        if (!enabled()) {
            return;
        }
        if (!loaded) {
            load();
        }
        try {
            Database db = plugin.database();
            double feePercent = Math.max(0, Math.min(90, plugin.getConfig().getDouble("creature-auction.fee-percent", 5)));
            for (Lot l : new ArrayList<>(lots)) {
                if (l.bidder != null) {
                    long fee = l.system() ? l.bid : Math.round(l.bid * feePercent / 100.0);
                    long pay = l.bid - fee;
                    db.begin();
                    try {
                        db.addShopAccount(fee);
                        if (!l.system() && pay > 0) {
                            db.addCoins(l.seller, pay);
                        }
                        db.commit();
                    } catch (RuntimeException e) {
                        db.rollback();
                        throw e;
                    }
                    if (!l.system() && pay > 0) {
                        db.logTx(l.seller, l.sellerName, "kreaturen-verkauf", pay, l.companion.label + " versteigert an " + l.bidderName);
                        Player seller = Bukkit.getPlayer(l.seller);
                        if (seller != null) {
                            plugin.messages().send(seller, "beast-seller-paid", "name", l.companion.label, "player", l.bidderName, "coins", pay);
                        }
                    }
                    giveTo(l.bidder, l);
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (plugin.blackMarket().unlocked(online) && !online.getUniqueId().equals(l.bidder)
                                && !online.getUniqueId().equals(l.seller)) {
                            plugin.messages().send(online, "beast-ended", "name", l.companion.label, "player", l.bidderName, "coins", l.bid);
                        }
                    }
                } else if (!l.system()) {
                    giveTo(l.seller, l);
                }
                lots.remove(l);
            }
            createDaily();
            save();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Sklavenhandel: Abschluss fehlgeschlagen: " + e);
        }
    }
}
