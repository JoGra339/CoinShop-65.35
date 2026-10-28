package de.coinshop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lizenzen: Einmal gekauft, gelten sie für immer und geben kleine Vorteile.
 * Es gibt 20 Lizenzen (feste Kennungen, siehe IDS). Name, Symbol, Preis und Wert stehen in der config.yml
 * unter "licenses". Die Beschreibungen stehen in der messages.yml unter "gui.license.desc".
 *
 *  steuerfrei    - keine Verkaufssteuer im Spieler-Shop
 *  gebuehrenfrei - keine Einstellgebühr im Spieler-Shop
 *  freispiel     - einmal am Tag ein Casino-Spiel gratis
 *  rabatt        - Rabatt (in %) im Server-Shop
 *  rente         - zusätzliches tägliches Einkommen (Coins)
 *  zusatzslots   - zusätzliche Verkaufs-Slots im Spieler-Shop
 *  treue         - Login-Serien-Bonus um Prozent erhöht
 *  investor      - Einkommen aus Item-Sets um Prozent erhöht
 *  boersen       - niedrigere Börsen-Gebühr (Wert = Prozent)
 *  ranglisten    - Bonus aus den Ranglisten um Prozent erhöht
 *  aufgaben      - Belohnungen der täglichen Aufgaben um Prozent erhöht
 *  sammler       - Belohnungen der Kollektionen um Prozent erhöht
 *  glueck        - Gewinne bei Glücksrad, Spielautomat und Rubbellos um Prozent erhöht
 *  erfolge       - Belohnungen für Erfolge und geheime Erfolge um Prozent erhöht
 *  wetten        - schaltet das Wettbüro frei
 *  schmuggler    - schaltet den Schwarzmarkt frei (Wert = Prozentpunkte mehr Kaufchance)
 *  hehler        - Schwarzmarkt: mehr Erlös, wenn der Händler deine Items kauft (Prozent)
 *  hintertuer    - Schwarzmarkt: Rabatt auf die Preise (Prozent)
 *  bestechung    - Schwarzmarkt: weniger Gebühren (Prozent der Gebühr erlassen)
 *  kopfgeldjaeger - Kopfgelder, die du kassierst, bringen Prozent mehr
 *  schwarzlager  - zusätzliche Verkaufs-Slots im Schwarzmarkt
 *  buchmacher    - Schwarzmarkt: Quote türken gelingt öfter (Wert = Prozentpunkte)
 *
 * Lizenzen haben Stufen: Die erste Stufe steht bei "price" und "value", weitere Stufen unter "levels"
 * (Liste mit je price und value). Die Stufe eines Spielers steht in der Datenbank (Tabelle meta).
 */
public final class LicenseManager {

    public static final List<String> IDS = List.of("steuerfrei", "gebuehrenfrei", "freispiel", "rabatt", "rente",
            "zusatzslots", "treue", "investor", "boersen", "ranglisten", "aufgaben", "sammler", "glueck", "erfolge",
            "grosshandel", "wetten", "expedition", "bergbau",
            "schmuggler", "hehler", "hintertuer", "bestechung", "kopfgeldjaeger", "schwarzlager", "buchmacher", "drogenhandel");

    /** Die Schwarzmarkt-Lizenzen (alle außer "schmuggler" brauchen zuerst den Schmuggler). */
    public static final List<String> BLACK_IDS = List.of("schmuggler", "hehler", "hintertuer", "bestechung",
            "kopfgeldjaeger", "schwarzlager", "buchmacher", "drogenhandel");

    public record License(String id, String name, Material icon, long price, double value) { }

    /** Eine Stufe einer Lizenz: Preis (für diese Stufe) und Wert. */
    public record Level(long price, double value) { }

    private final CoinShopPlugin plugin;
    private final java.util.Map<UUID, Set<String>> cache = new ConcurrentHashMap<>();

    public LicenseManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Einstellungen
    // ------------------------------------------------------------------

    public List<License> all() {
        List<License> list = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("licenses");
        for (String id : IDS) {
            ConfigurationSection c = section == null ? null : section.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            Material icon = Material.matchMaterial(c.getString("icon", "PAPER"));
            list.add(new License(id, c.getString("name", id), icon == null ? Material.PAPER : icon,
                    Math.max(0, c.getLong("price", 10000)), c.getDouble("value", 0)));
        }
        return list;
    }

    public License get(String id) {
        for (License l : all()) {
            if (l.id().equals(id)) {
                return l;
            }
        }
        return null;
    }

    /** Der Wert der Lizenz (z. B. Prozent), 0 wenn es sie nicht gibt. */
    public double value(String id) {
        License l = get(id);
        return l == null ? 0 : l.value();
    }

    /** Alle Stufen einer Lizenz (Stufe 1 zuerst). */
    public List<Level> levelsOf(String id) {
        List<Level> levels = new ArrayList<>();
        License base = get(id);
        if (base == null) {
            return levels;
        }
        levels.add(new Level(base.price(), base.value()));
        for (java.util.Map<?, ?> map : plugin.getConfig().getMapList("licenses." + id + ".levels")) {
            Object price = map.get("price");
            Object value = map.get("value");
            if (price instanceof Number p && value instanceof Number v) {
                levels.add(new Level(Math.max(0, p.longValue()), v.doubleValue()));
            }
        }
        return levels;
    }

    public int maxLevel(String id) {
        return Math.max(1, levelsOf(id).size());
    }

    /** Die Stufe des Spielers: 0 = nicht gekauft. */
    public int level(UUID uuid, String id) {
        if (!has(uuid, id)) {
            return 0;
        }
        try {
            long stored = plugin.database().getMetaLong("license_level_" + uuid + "_" + id, 1);
            return (int) Math.max(1, Math.min(maxLevel(id), stored));
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** Der Wert der Lizenz auf der Stufe des Spielers (0, wenn er sie nicht hat). */
    public double value(UUID uuid, String id) {
        int level = level(uuid, id);
        List<Level> levels = levelsOf(id);
        if (level < 1 || levels.isEmpty()) {
            return 0;
        }
        return levels.get(Math.min(level, levels.size()) - 1).value();
    }

    /** Verbessert eine Lizenz auf die nächste Stufe. */
    public void upgrade(Player player, String id) {
        Messages msg = plugin.messages();
        UUID uuid = player.getUniqueId();
        int level = level(uuid, id);
        List<Level> levels = levelsOf(id);
        License license = get(id);
        if (license == null || level < 1) {
            return;
        }
        if (level >= levels.size()) {
            msg.send(player, "license-maxed");
            return;
        }
        Level next = levels.get(level); // Index level = Stufe level+1
        Database db = plugin.database();
        db.begin();
        try {
            if (!db.takeCoins(uuid, priceOf(id, next.price()))) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(priceOf(id, next.price()));
            if (BLACK_IDS.contains(id)) {
                plugin.reviere().payPate(plugin.blackMarket().feePart(priceOf(id, next.price())));
            }
            db.setMetaLong("license_level_" + uuid + "_" + id, level + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Lizenz-Upgrade: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "lizenz-stufe", -priceOf(id, next.price()), id + " Stufe " + (level + 1));
        msg.send(player, "license-upgraded", "name", license.name(), "level", level + 1);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
    }

    // ------------------------------------------------------------------
    //  Besitz
    // ------------------------------------------------------------------

    public Set<String> owned(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> new HashSet<>(plugin.database().getLicenses(u)));
    }

    public boolean has(UUID uuid, String id) {
        try {
            return owned(uuid).contains(id);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    /** Kauft eine Lizenz. */
    public void buy(Player player, String id) {
        Messages msg = plugin.messages();
        License license = get(id);
        UUID uuid = player.getUniqueId();
        if (license == null) {
            return;
        }
        if (has(uuid, id)) {
            msg.send(player, "license-owned");
            return;
        }
        if (BLACK_IDS.contains(id) && !id.equals("schmuggler") && !has(uuid, "schmuggler")) {
            msg.send(player, "license-needs-schmuggler");
            return;
        }
        Database db = plugin.database();
        db.begin();
        try {
            if (!db.takeCoins(uuid, priceOf(id, license.price()))) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(priceOf(id, license.price()));
            if (BLACK_IDS.contains(id)) {
                plugin.reviere().payPate(plugin.blackMarket().feePart(priceOf(id, license.price())));
            }
            db.addLicense(uuid, id);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Lizenz-Kauf: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        owned(uuid).add(id);
        db.logTx(uuid, player.getName(), "lizenz", -priceOf(id, license.price()), id);
        msg.send(player, "license-bought", "name", license.name());
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // ------------------------------------------------------------------
    //  Wirkungen
    // ------------------------------------------------------------------

    /** Lizenzpreis am heutigen Tag (Inflation macht alles teurer). */
    /** Lizenzpreis: Schwarzmarkt-Lizenzen enthalten die kleine Schwarzmarkt-Gebühr. */
    public long priceOf(String id, long base) {
        long price = inflated(base);
        return BLACK_IDS.contains(id) ? plugin.blackMarket().withFee(price) : price;
    }

    public long inflated(long price) {
        return Math.round(price * plugin.events().inflation());
    }

    /** Preis mit Rabatt (Server-Shop und Schwarzmarkt): Lizenz, Revier-Boss, Titel und Tagesereignis. */
    public long discounted(UUID uuid, long price) {
        double factor = 1.0;
        if (has(uuid, "rabatt")) {
            factor -= Math.min(100.0, value(uuid, "rabatt")) / 100.0;
        }
        if (plugin.reviere().isBoss(uuid)) {
            factor -= plugin.getConfig().getDouble("revier.boss-discount-percent", 10) / 100.0;
        }
        factor -= plugin.titles().effect(uuid, "shop-discount", "") / 100.0;
        factor -= plugin.mascot().discountPercent(uuid) / 100.0; // Maskottchen: wirkt nur im Shop
        factor = Math.max(0.05, factor);
        long result = Math.max(1, Math.round(price * factor));
        double event = plugin.events().saleFactor(); // Ausverkauf / Inflation
        if (event != 1.0) {
            result = Math.max(1, Math.round(result * event));
        }
        return result;
    }

    /** Betrag plus Bonus in Prozent (Lizenz plus Titel), danach das Tagesereignis. */
    public long boost(UUID uuid, String id, long amount) {
        if (amount <= 0) {
            return amount;
        }
        double percent = 0;
        if (has(uuid, id)) {
            percent += value(uuid, id);
        }
        percent += plugin.titles().effect(uuid, "boost-" + id, "");
        long result = amount + (long) Math.floor(amount * percent / 100.0);
        double event = plugin.events().boostFactor(id);
        if (event != 1.0) {
            result = Math.round(result * event);
        }
        return result;
    }

    /** Hat der Spieler den Schwarzmarkt freigeschaltet (Lizenz Schmuggler)? */
    public boolean blackMarketUnlocked(UUID uuid) {
        return has(uuid, "schmuggler");
    }

    /** Zusätzliches tägliches Einkommen (Rente). */
    public long dailyIncome(UUID uuid) {
        return has(uuid, "rente") ? (long) value(uuid, "rente") : 0;
    }

    public int extraSlots(UUID uuid) {
        return has(uuid, "zusatzslots") ? (int) value(uuid, "zusatzslots") : 0;
    }

    /** Welchen Tag haben wir (Wechsel zur Shop-Wechsel-Uhrzeit)? */
    private long dayNumber() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return now.minusHours(plugin.serverShop().resetHour()).toLocalDate().toEpochDay();
    }

    /** Wie viele Freispiele heute schon benutzt wurden. */
    private int freePlaysUsedToday(UUID uuid) {
        if (plugin.database().getMetaLong("freeplay_" + uuid, -1) != dayNumber()) {
            return 0;
        }
        return (int) plugin.database().getMetaLong("freeplay_count_" + uuid, 1);
    }

    /** Ist heute noch ein Freispiel frei? (ohne es zu verbrauchen) Die Stufe bestimmt, wie viele pro Tag. */
    public boolean freePlayAvailable(UUID uuid) {
        return has(uuid, "freispiel") && freePlaysUsedToday(uuid) < Math.max(1, (int) value(uuid, "freispiel"));
    }

    /**
     * Verbraucht ein heutiges Freispiel, falls vorhanden. Soll innerhalb einer Datenbank-Transaktion
     * aufgerufen werden (wird bei Abbruch zurückgenommen).
     */
    public boolean consumeFreePlay(UUID uuid) {
        if (!freePlayAvailable(uuid)) {
            return false;
        }
        int used = freePlaysUsedToday(uuid);
        plugin.database().setMetaLong("freeplay_" + uuid, dayNumber());
        plugin.database().setMetaLong("freeplay_count_" + uuid, used + 1);
        return true;
    }
}
