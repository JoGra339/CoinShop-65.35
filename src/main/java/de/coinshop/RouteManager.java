package de.coinshop;

import de.coinshop.Database.CryptoHolding;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Schmugglerrouten: das Gegenstück der Kryptominer im Schwarzmarkt. Man kauft Routen nacheinander (jede teurer als die
 * davor). Jede Route bringt beim täglichen Shop-Wechsel Habibi-Krypto, außer sie wird vom Zoll erwischt (Chance pro
 * Route und Tag) oder es ist Razzia-Tag. Die Anzahl der Routen steht im Merkzettel (meta) unter "routes_UUID".
 */
public final class RouteManager {

    private static final String PREFIX = "routes_";
    private static final String[] NAMES = {
            "Hinterhof-Pfad", "Kellertür", "Lieferwagen", "Hafenkai", "Nebelgasse", "Tunnel 7",
            "Schmugglerboot", "Zollschlupfloch", "Wüstenkarawane", "Luftbrücke", "Nachtzug", "Der Große Deal",
            "Fischkutter", "Hühnerstall", "Bergpass", "Abwasserkanal", "Heißluftballon", "Sargtransport",
            "Postkutsche", "Leuchtturm", "Bananenfracht", "Geheimes Dock", "Müllcontainer", "Seilbahn",
            "U-Boot", "Diplomatengepäck", "Zirkuszug", "Schattenpfad", "Die Goldene Route", "Königsroute",
            "Das Hintertor", "Der Letzte Weg"};
    private static final String LEVEL_PREFIX = "routelvl_";
    public static final int MAX_LEVEL = 5;

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public RouteManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public int max() {
        return Math.max(1, Math.min(NAMES.length, plugin.getConfig().getInt("blackmarket.routes.max", NAMES.length)));
    }

    public String name(int idx) {
        return NAMES[Math.max(1, Math.min(NAMES.length, idx)) - 1];
    }

    /** Schenkt dem Spieler eine weitere Route (ohne Bezahlung, zum Beispiel als Belohnung). */
    public void grant(UUID uuid) {
        plugin.database().setMetaLong(PREFIX + uuid, count(uuid) + 1);
    }

    public int count(UUID uuid) {
        return (int) plugin.database().getMetaLong(PREFIX + uuid, 0);
    }

    /** Preis der Route Nummer idx (1 = erste). */
    public long price(int idx) {
        double base = Math.max(1, plugin.getConfig().getDouble("blackmarket.routes.base-price", 1500));
        double factor = Math.max(1.0, plugin.getConfig().getDouble("blackmarket.routes.price-factor", 2.0));
        return plugin.blackMarket().withFee(Math.round(base * Math.pow(factor, idx - 1) * plugin.events().inflation()));
    }

    /** Stufe der Route idx (1 bis 5). */
    public int level(UUID uuid, int idx) {
        return (int) Math.max(1, Math.min(MAX_LEVEL, plugin.database().getMetaLong(LEVEL_PREFIX + uuid + "_" + idx, 1)));
    }

    /**
     * Ertrag der Route idx auf Stufe level in Hundertsteln Habibi-Krypto pro Tag (ohne Zoll und Ereignisse).
     * Pro Stufe steht ein fester Wert in der config (level-rewards); jede weitere Route bringt route-growth mal so viel
     * wie die davor, damit sich die teureren Routen auch lohnen.
     */
    public long rewardAt(int idx, int level) {
        List<Long> list = plugin.getConfig().getLongList("blackmarket.routes.level-rewards");
        int l = Math.max(1, Math.min(MAX_LEVEL, level));
        long perLevel = list.size() >= l ? list.get(l - 1) : 20L * l;
        double growth = Math.max(1.0, plugin.getConfig().getDouble("blackmarket.routes.route-growth", 1.5));
        return Math.max(1, Math.round(perLevel * Math.pow(growth, Math.max(0, idx - 1))));
    }

    public long reward(UUID uuid, int idx) {
        return rewardAt(idx, level(uuid, idx));
    }

    /** Preis für die Aufwertung der Route idx von Stufe level auf level+1. */
    public long upgradePrice(int idx, int level) {
        double f = Math.max(0.01, plugin.getConfig().getDouble("blackmarket.routes.upgrade-factor", 0.4));
        return Math.max(1, Math.round(price(idx) * f * level));
    }

    /** Ertrag aller Routen zusammen (ohne Zoll und Ereignisse). */
    public long totalReward(UUID uuid) {
        long sum = 0;
        for (int i = 1; i <= count(uuid); i++) {
            sum += reward(uuid, i);
        }
        return sum;
    }

    public void upgrade(Player player, int idx) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!plugin.blackMarket().allowed(player)) {
            return;
        }
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return;
        }
        if (idx < 1 || idx > count(uuid)) {
            return;
        }
        int level = level(uuid, idx);
        if (level >= MAX_LEVEL) {
            msg.send(player, "route-maxlevel");
            return;
        }
        long price = upgradePrice(idx, level);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(price);
            plugin.reviere().payPate(plugin.blackMarket().feePart(price));
            db.setMetaLong(LEVEL_PREFIX + uuid + "_" + idx, level + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Aufwerten einer Schmugglerroute: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "route-upgrade", -price, name(idx) + " Stufe " + (level + 1));
        msg.send(player, "route-upgraded", "name", name(idx), "level", level + 1, "price", price,
                "units", CryptoManager.fmt(rewardAt(idx, level + 1)));
    }

    public int catchPercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("blackmarket.routes.catch-percent", 50)));
    }

    public void buy(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!plugin.blackMarket().allowed(player)) {
            return;
        }
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return;
        }
        int have = count(uuid);
        if (have >= max()) {
            msg.send(player, "route-max");
            return;
        }
        long price = price(have + 1);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(price);
            plugin.reviere().payPate(plugin.blackMarket().feePart(price));
            db.setMetaLong(PREFIX + uuid, have + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Kauf einer Schmugglerroute: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "route-kauf", -price, name(have + 1));
        msg.send(player, "route-bought", "name", name(have + 1), "price", price,
                "units", CryptoManager.fmt(rewardAt(have + 1, 1)));
    }

    /** Beim täglichen Shop-Wechsel: jede Route bringt Habibi-Krypto, wenn der Zoll sie nicht erwischt. */
    public void payAll() {
        if (plugin.timeline().previewActive()) {
            return;
        }
        Database db = plugin.database();
        boolean raid = plugin.events().isActive(EventManager.Event.RAID);
        for (Map.Entry<String, Long> entry : db.getMetaPrefix(PREFIX).entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey().substring(PREFIX.length()));
                int routes = (int) Math.min(max(), entry.getValue());
                if (routes < 1 || plugin.blackMarket().bountyOptOut(uuid)) {
                    continue;
                }
                long units = 0;
                int caught = 0;
                for (int i = 1; i <= routes; i++) {
                    if (raid || random.nextInt(100) < catchPercent()) {
                        caught++;
                    } else {
                        units += reward(uuid, i);
                    }
                }
                units = Math.round(units * plugin.events().earnings());
                if (units > 0) {
                    CryptoHolding h = db.getCryptoHolding(uuid, CryptoManager.DUNKEL);
                    db.setCryptoHolding(uuid, CryptoManager.DUNKEL, h.units() + units, h.invested());
                    db.logTx(uuid, db.getName(uuid), "route-ertrag", 0, CryptoManager.fmt(units) + " Habibi-Krypto");
                }
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    plugin.messages().send(online, "route-income", "units", CryptoManager.fmt(units), "caught", caught);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Schmugglerrouten konnten nicht ausgezahlt werden: " + e);
            }
        }
    }
}
