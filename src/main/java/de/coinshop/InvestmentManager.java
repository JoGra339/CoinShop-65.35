package de.coinshop;

import de.coinshop.Database.Ownership;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Investitionen ("Monopoly"): Items aus Sets kaufen, die täglich Einkommen bringen.
 *
 * - Jedes Item gibt es nur einmal auf dem Server und gehört genau einem Spieler.
 * - Ein komplettes Set bringt das volle Einkommen (= Preis pro Item), ein Teilbesitz einen Teil davon.
 * - Items können an andere Spieler übertragen werden.
 * - Hier steckt auch die Auszahlung des täglichen Einkommens.
 */
public final class InvestmentManager {

    /** Ein Set aus der config.yml. "id" ist der Schlüssel (z. B. "set01") und wird zum Speichern benutzt. */
    public record SetDef(String id, String name, long price, List<Material> items, long multiplier) { }

    private final CoinShopPlugin plugin;
    private List<SetDef> sets = new ArrayList<>();

    public InvestmentManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Liest die Sets (neu) aus der config.yml. */
    public void reload() {
        List<SetDef> loaded = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("investments.sets");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                List<Material> items = new ArrayList<>();
                for (String name : s.getStringList("items")) {
                    Material material = Material.matchMaterial(name);
                    if (material == null || !material.isItem() || material.isAir()) {
                        plugin.getLogger().warning("Investitionen: '" + name + "' in " + key + " ist kein gültiges Item.");
                        material = Material.BARRIER;
                    }
                    items.add(material);
                }
                if (!items.isEmpty()) {
                    loaded.add(new SetDef(key, s.getString("name", key), Math.max(1, s.getLong("price", 100)), items,
                            multiplierFor(key, s)));
                }
            }
        }
        sets = loaded;
    }

    /**
     * Wie teuer ein Item im Verhältnis zum Set-Einkommen ist (= Rentabilität). Jedes Set hat einen eigenen Wert zwischen
     * price-multiplier-min und price-multiplier-max (feste Zufallszahl aus der Set-Id, bleibt immer gleich); in der
     * config kann ein Set mit "price-multiplier: X" einen festen Wert bekommen.
     */
    private long multiplierFor(String key, ConfigurationSection s) {
        if (s.contains("price-multiplier")) {
            return Math.max(1, s.getLong("price-multiplier"));
        }
        long min = Math.max(1, plugin.getConfig().getLong("investments.price-multiplier-min", 1));
        long max = Math.max(min, plugin.getConfig().getLong("investments.price-multiplier-max", 5));
        return min + new java.util.Random(key.hashCode() * 31L + 17L).nextInt((int) (max - min + 1));
    }

    public List<SetDef> sets() {
        return sets;
    }

    public static String itemId(SetDef set, int itemIndex) {
        return set.id() + ":" + itemIndex;
    }

    // ------------------------------------------------------------------
    //  Besitz und Einkommen
    // ------------------------------------------------------------------

    /** Alle vergebenen Items: itemId -> Besitzer. */
    public Map<String, Ownership> ownershipMap() {
        Map<String, Ownership> map = new HashMap<>();
        for (Ownership o : plugin.database().getOwnerships()) {
            map.put(o.itemId(), o);
        }
        return map;
    }

    /** Wie viele Items dieses Sets besitzt der Spieler? */
    public int ownedCount(SetDef set, UUID uuid, Map<String, Ownership> map) {
        int count = 0;
        for (int i = 0; i < set.items().size(); i++) {
            Ownership o = map.get(itemId(set, i));
            if (o != null && o.owner().equals(uuid)) {
                count++;
            }
        }
        return count;
    }

    /** Wie viel Prozent des Set-Einkommens bringt es, "owned" von "total" Items zu besitzen? */
    private double percent(int owned, int total) {
        if (owned <= 0) {
            return 0;
        }
        if (owned >= total) {
            return 100;
        }
        String path = "investments.percent-by-owned-count." + owned;
        if (plugin.getConfig().contains(path)) {
            return plugin.getConfig().getDouble(path);
        }
        // Standard: gleichmäßig von 10 % (1 Item) bis 100 % (komplett)
        return total <= 1 ? 100 : 10 + (owned - 1) * 90.0 / (total - 1);
    }

    /** Was ein einzelnes Item des Sets kostet (Set-Preis mal Faktor aus der config). */
    public long itemPrice(SetDef set) {
        return Math.round(set.price() * set.multiplier() * plugin.events().inflation());
    }

    /** Tägliches Einkommen aus einem Set, wenn man "owned" Items davon besitzt. */
    public long setIncome(SetDef set, int owned) {
        return Math.round(set.price() * percent(owned, set.items().size()) / 100.0);
    }

    /** Einkommen aus allen Sets (ohne Grundeinkommen). */
    public long setIncomeOf(UUID uuid, Map<String, Ownership> map) {
        long total = 0;
        for (SetDef set : sets) {
            total += setIncome(set, ownedCount(set, uuid, map));
        }
        return plugin.licenses().boost(uuid, "investor", total); // Lizenz "investor"
    }

    /** Grundeinkommen eines Spielers (mit Rente aus der Lizenz). */
    public long baseIncome(UUID uuid) {
        return baseIncome() + plugin.licenses().dailyIncome(uuid);
    }

    public long baseIncome() {
        return Math.max(0, plugin.getConfig().getLong("daily-income.base", 10));
    }

    // ------------------------------------------------------------------
    //  Kaufen
    // ------------------------------------------------------------------

    public void buy(Player player, SetDef set, int itemIndex) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        String id = itemId(set, itemIndex);
        Material material = set.items().get(itemIndex);

        if (db.getOwnership(id).isPresent()) {
            msg.send(player, "invest-taken");
            return;
        }

        db.begin();
        try {
            if (!db.takeCoins(uuid, itemPrice(set))) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            // Wenn jemand schneller war, bricht alles ab (kein Doppelverkauf)
            if (!db.insertOwnership(id, uuid)) {
                db.rollback();
                msg.send(player, "invest-taken");
                return;
            }
            db.addShopAccount(itemPrice(set)); // Der Kaufpreis geht aufs Konto des Shops
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Investment-Kauf: " + e.getMessage());
            msg.send(player, "error");
            return;
        }

        db.logTx(uuid, player.getName(), "invest-kauf", -itemPrice(set), set.name() + ": " + material.name());
        Map<String, Ownership> map = ownershipMap();
        int owned = ownedCount(set, uuid, map);
        msg.send(player, "invest-bought", "item", ItemUtil.name(material), "set", set.name(),
                "price", itemPrice(set), "income", setIncome(set, owned));
        if (owned >= set.items().size()) {
            msg.send(player, "invest-set-complete", "set", set.name());
        }
    }

    // ------------------------------------------------------------------
    //  Übertragen (per Chat: erst Spielername, dann Bestätigung mit "ja")
    // ------------------------------------------------------------------

    public void startTransfer(Player player, SetDef set, int itemIndex) {
        Messages msg = plugin.messages();
        Optional<Ownership> owner = plugin.database().getOwnership(itemId(set, itemIndex));
        if (owner.isEmpty() || !owner.get().owner().equals(player.getUniqueId())) {
            msg.send(player, "transfer-failed");
            return;
        }
        player.closeInventory();
        msg.send(player, "transfer-ask-target", "item", ItemUtil.name(set.items().get(itemIndex)));
        plugin.prompts().start(player, text -> handleTarget(player, set, itemIndex, text));
    }

    private void handleTarget(Player player, SetDef set, int itemIndex, String text) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (text.equalsIgnoreCase("abbrechen")) {
            msg.send(player, "ask-cancelled");
            return;
        }
        Optional<UUID> target = db.findUuidByName(text.trim());
        if (target.isEmpty()) {
            msg.send(player, "player-unknown", "player", text);
            return;
        }
        if (target.get().equals(player.getUniqueId())) {
            msg.send(player, "transfer-self");
            return;
        }
        String targetName = db.getName(target.get());
        msg.send(player, "transfer-confirm", "item", ItemUtil.name(set.items().get(itemIndex)),
                "set", set.name(), "player", targetName);
        plugin.prompts().start(player, answer -> {
            if (answer.equalsIgnoreCase("ja")) {
                doTransfer(player, set, itemIndex, target.get(), targetName);
            } else {
                msg.send(player, "ask-cancelled");
            }
        });
    }

    private void doTransfer(Player from, SetDef set, int itemIndex, UUID to, String toName) {
        Messages msg = plugin.messages();
        Material material = set.items().get(itemIndex);
        boolean ok;
        try {
            ok = plugin.database().transferOwnership(itemId(set, itemIndex), from.getUniqueId(), to);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei der Übertragung: " + e.getMessage());
            msg.send(from, "error");
            return;
        }
        if (!ok) {
            msg.send(from, "transfer-failed");
            return;
        }
        msg.send(from, "transfer-done", "item", ItemUtil.name(material), "set", set.name(), "player", toName);
        Player receiver = Bukkit.getPlayer(to);
        if (receiver != null) {
            msg.send(receiver, "transfer-received", "item", ItemUtil.name(material), "set", set.name(),
                    "player", from.getName());
        }
    }

    // ------------------------------------------------------------------
    //  Tägliches Einkommen (wird beim Shop-Wechsel um 12:00 Uhr aufgerufen)
    // ------------------------------------------------------------------

    /**
     * Einkommens-Ausgleich: Das Tageseinkommen (Grund + Extras) der reichsten Spieler (nach Coins) wird gemittelt.
     * Die ärmsten Spieler (untere poor-share Prozent) bekommen davon catchup-percent Prozent zusätzlich, ohne Obergrenze.
     */
    private Map<UUID, Long> catchupBonuses(long base, Map<UUID, Long> extra) {
        Map<UUID, Long> result = new HashMap<>();
        if (!plugin.getConfig().getBoolean("daily-income.catchup.enabled", true)) {
            return result;
        }
        List<Database.Account> accounts = new ArrayList<>(plugin.database().allAccounts());
        if (accounts.size() < 4) {
            return result;
        }
        accounts.sort((a, b) -> Long.compare(b.coins(), a.coins()));
        int top = Math.max(1, Math.min(accounts.size(), plugin.getConfig().getInt("daily-income.catchup.rich-count", 3)));
        long sum = 0;
        for (int i = 0; i < top; i++) {
            sum += base + extra.getOrDefault(accounts.get(i).uuid(), 0L);
        }
        double avgRich = sum / (double) top;
        double percent = Math.max(0, plugin.getConfig().getDouble("daily-income.catchup.percent", 10));
        long bonus = Math.round(avgRich * percent / 100.0); // ohne Obergrenze
        if (bonus <= 0) {
            return result;
        }
        int share = Math.max(1, Math.min(100, plugin.getConfig().getInt("daily-income.catchup.poor-share-percent", 30)));
        int poorCount = Math.max(1, accounts.size() * share / 100);
        for (int i = accounts.size() - poorCount; i < accounts.size(); i++) {
            result.put(accounts.get(i).uuid(), bonus);
        }
        return result;
    }

    public void payDailyIncome() {
        if (plugin.timeline().previewActive()) {
            return; // Testphase vor dem Serverstart: kein Einkommen
        }
        Database db = plugin.database();
        long base = Math.round(baseIncome() * plugin.events().incomeFactor()); // Ereignisse: Zahltag / Sparhaushalt

        // Extra-Einkommen aus Sets pro Besitzer berechnen
        Map<String, Ownership> map = ownershipMap();
        Map<UUID, Long> extra = new HashMap<>();
        Map<UUID, long[]> parts = new HashMap<>(); // fürs Protokoll: Sets, Rangliste, Login-Serie, Rente
        for (Ownership o : map.values()) {
            extra.putIfAbsent(o.owner(), 0L);
        }
        for (UUID owner : new ArrayList<>(extra.keySet())) {
            long setsIncome = setIncomeOf(owner, map);
            extra.put(owner, setsIncome);
            parts.computeIfAbsent(owner, k -> new long[4])[0] += setsIncome;
        }

        // Bonus für die ersten 5 Plätze der Ranglisten (vorher die Zahlen der Online-Spieler auffrischen)
        try {
            plugin.rankings().syncOnline();
            for (Map.Entry<UUID, Long> bonus : plugin.rankings().dailyBonuses().entrySet()) {
                extra.merge(bonus.getKey(), bonus.getValue(), Long::sum);
                parts.computeIfAbsent(bonus.getKey(), k -> new long[4])[1] += bonus.getValue();
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Ranglisten-Bonus konnte nicht berechnet werden: " + e);
        }

        // Serien-Bonus der Login-Serie (gehört fest zum täglichen Einkommen)
        try {
            for (Database.Account account : db.allAccounts()) {
                long loginBonus = plugin.logins().bonusOf(account.uuid());
                if (loginBonus > 0) {
                    extra.merge(account.uuid(), loginBonus, Long::sum);
                    parts.computeIfAbsent(account.uuid(), k -> new long[4])[2] += loginBonus;
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Serien-Bonus konnte nicht berechnet werden: " + e);
        }

        // Rente aus der Lizenz
        try {
            for (Database.Account account : db.allAccounts()) {
                long rente = plugin.licenses().dailyIncome(account.uuid());
                if (rente > 0) {
                    extra.merge(account.uuid(), rente, Long::sum);
                    parts.computeIfAbsent(account.uuid(), k -> new long[4])[3] += rente;
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Rente konnte nicht berechnet werden: " + e);
        }

        // Ausgleich: die ärmsten Spieler bekommen einen Aufschlag, der sich am Einkommen der reichsten richtet
        Map<UUID, Long> catchup = new HashMap<>();
        try {
            catchup.putAll(catchupBonuses(base, extra));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Einkommens-Ausgleich konnte nicht berechnet werden: " + e);
        }
        for (Map.Entry<UUID, Long> c : catchup.entrySet()) {
            extra.merge(c.getKey(), c.getValue(), Long::sum);
        }

        db.begin();
        try {
            if (base > 0) {
                db.payToAll(base);
            }
            for (Map.Entry<UUID, Long> entry : extra.entrySet()) {
                if (entry.getValue() > 0) {
                    db.payExtra(entry.getKey(), entry.getValue());
                }
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Tägliches Einkommen konnte nicht ausgezahlt werden: " + e.getMessage());
            return;
        }

        // Buchhaltung: ein Eintrag pro Spieler mit allen Teilen des Einkommens
        try {
            for (Database.Account account : db.allAccounts()) {
                long[] pt = parts.getOrDefault(account.uuid(), new long[4]);
                long total = base + extra.getOrDefault(account.uuid(), 0L);
                if (total > 0) {
                    db.logTx(account.uuid(), account.name(), "einkommen", total, "Grund " + base + ", Sets " + pt[0]
                            + ", Rangliste " + pt[1] + ", Login-Serie " + pt[2] + ", Rente " + pt[3]);
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Einkommen konnte nicht protokolliert werden: " + e);
        }

        // Spieler, die gerade online sind, sehen es sofort (alle anderen beim Beitreten)
        for (Player online : Bukkit.getOnlinePlayers()) {
            long amount = db.popPendingIncome(online.getUniqueId());
            if (amount > 0) {
                plugin.messages().send(online, "daily-income", "coins", amount);
            }
        }
        plugin.getLogger().info("Tägliches Einkommen ausgezahlt.");
    }
}
