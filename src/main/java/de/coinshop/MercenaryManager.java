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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Söldner: Ein Spieler bietet einen Begleiter zur Miete an (Preis frei gewählt). Ein anderer Spieler mietet ihn bis zum nächsten
 * Shopwechsel und nimmt ihn in seine Expeditionen mit. Der Besitzer bekommt die Miete (abzüglich Anteil für den Shop) und für jedes
 * geschaffte Level seines Söldners Erfahrung. Solange der Begleiter angeboten oder vermietet ist, ist er für den Besitzer nicht frei.
 */
public final class MercenaryManager {

    /** Ein Angebot. renter == null: noch frei. */
    public static final class Offer {
        public final long id;
        public final UUID owner;
        public String ownerName = "";
        public final String key;
        public final int idx;
        public long price;
        public UUID renter;
        public String renterName = "";
        public long until;

        Offer(long id, UUID owner, String key, int idx) {
            this.id = id;
            this.owner = owner;
            this.key = key;
            this.idx = idx;
        }

        public Copy copy() {
            Companion c = Companion.byKey(key);
            return c == null ? null : new Copy(c, idx);
        }
    }

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;
    private final File file;
    private final Map<Long, Offer> offers = new LinkedHashMap<>();
    private long nextId = 1;

    MercenaryManager(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
        this.file = new File(plugin.getDataFolder(), "mercs.yml");
        load();
    }

    private int cfgI(String key, int def) {
        return plugin.getConfig().getInt("expedition.mercenary." + key, def);
    }

    private void say(Player p, String text) {
        plugin.messages().send(p, "ex-merc-msg", "text", text);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("expedition.mercenary.enabled", true);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = Math.max(1, y.getLong("next", 1));
        ConfigurationSection sec = y.getConfigurationSection("offers");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            try {
                ConfigurationSection s = sec.getConfigurationSection(k);
                if (s == null) {
                    continue;
                }
                Offer o = new Offer(Long.parseLong(k), UUID.fromString(s.getString("owner", "")), s.getString("key", ""), s.getInt("idx", 0));
                o.ownerName = s.getString("owner-name", "");
                o.price = s.getLong("price", 0);
                String r = s.getString("renter", "");
                o.renter = r.isEmpty() ? null : UUID.fromString(r);
                o.renterName = s.getString("renter-name", "");
                o.until = s.getLong("until", 0);
                offers.put(o.id, o);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("mercs.yml: Eintrag " + k + " übersprungen: " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", nextId);
        for (Offer o : offers.values()) {
            String p = "offers." + o.id + ".";
            y.set(p + "owner", o.owner.toString());
            y.set(p + "owner-name", o.ownerName);
            y.set(p + "key", o.key);
            y.set(p + "idx", o.idx);
            y.set(p + "price", o.price);
            y.set(p + "renter", o.renter == null ? "" : o.renter.toString());
            y.set(p + "renter-name", o.renterName);
            y.set(p + "until", o.until);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("mercs.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Abgelaufene Mieten verschwinden samt Angebot (der Besitzer kann neu anbieten). */
    private void expire() {
        long now = System.currentTimeMillis();
        if (offers.values().removeIf(o -> o.renter != null && o.until <= now)) {
            save();
        }
    }

    public List<Offer> open() {
        expire();
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers.values()) {
            if (o.renter == null) {
                out.add(o);
            }
        }
        return out;
    }

    public List<Offer> offersOf(UUID owner) {
        expire();
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers.values()) {
            if (o.owner.equals(owner)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Aktive Mieten dieses Spielers (er ist Mieter). */
    public List<Offer> rentedBy(UUID renter) {
        expire();
        List<Offer> out = new ArrayList<>();
        for (Offer o : offers.values()) {
            if (renter.equals(o.renter)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Warum ein Exemplar des Besitzers gerade angeboten oder vermietet ist (null = nicht). */
    public String busyText(UUID owner, Companion c, int idx) {
        for (Offer o : offers.values()) {
            if (o.owner.equals(owner) && o.key.equals(c.key) && o.idx == idx) {
                if (o.renter != null && o.until > System.currentTimeMillis()) {
                    return "als Söldner an " + o.renterName + " vermietet";
                }
                if (o.renter == null) {
                    return "als Söldner angeboten";
                }
            }
        }
        return null;
    }

    public void offer(Player p, Copy k, long price) {
        UUID uuid = p.getUniqueId();
        if (!enabled()) {
            say(p, "Söldner sind ausgeschaltet.");
            return;
        }
        if (offersOf(uuid).size() >= Math.max(1, cfgI("max-offers", 3))) {
            say(p, "Du kannst höchstens " + Math.max(1, cfgI("max-offers", 3)) + " Begleiter gleichzeitig anbieten.");
            return;
        }
        long min = Math.max(1, cfgI("min-price", 50));
        long max = Math.max(min, cfgI("max-price", 1_000_000));
        if (price < min || price > max) {
            say(p, "Der Preis muss zwischen " + min + " und " + max + " Coins liegen.");
            return;
        }
        String busy = ex.busyReason(uuid, k.companion(), k.index());
        if (busy != null) {
            plugin.messages().send(p, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busy);
            return;
        }
        if (ex.isIn(uuid)) {
            plugin.messages().send(p, "ex-already");
            return;
        }
        Offer o = new Offer(nextId++, uuid, k.companion().key, k.index());
        o.ownerName = p.getName();
        o.price = price;
        offers.put(o.id, o);
        save();
        say(p, ex.trips().copyName(k.companion(), k.index()) + " wird jetzt für " + price + " Coins angeboten (bis zum nächsten Shopwechsel pro Miete).");
    }

    public void withdraw(Player p, long id) {
        Offer o = offers.get(id);
        if (o == null || !o.owner.equals(p.getUniqueId())) {
            return;
        }
        if (o.renter != null && o.until > System.currentTimeMillis()) {
            say(p, "Der Söldner ist gerade vermietet. Warte bis zum Shopwechsel.");
            return;
        }
        offers.remove(id);
        save();
        say(p, "Das Angebot wurde zurückgenommen.");
    }

    public void rent(Player p, long id) {
        expire();
        Offer o = offers.get(id);
        UUID uuid = p.getUniqueId();
        if (o == null || o.renter != null) {
            say(p, "Dieser Söldner ist nicht mehr zu haben.");
            return;
        }
        if (o.owner.equals(uuid)) {
            say(p, "Du kannst deinen eigenen Söldner nicht mieten.");
            return;
        }
        if (rentedBy(uuid).size() >= Math.max(1, cfgI("max-rented", 2))) {
            say(p, "Du kannst höchstens " + Math.max(1, cfgI("max-rented", 2)) + " Söldner gleichzeitig mieten.");
            return;
        }
        if (!ex.hasLicense(p)) {
            plugin.messages().send(p, "ex-locked");
            return;
        }
        Copy k = o.copy();
        if (k == null) {
            offers.remove(id);
            save();
            return;
        }
        long fee = Math.round(o.price * Math.max(0, Math.min(100, cfgI("shop-percent", 10))) / 100.0);
        Database db = plugin.database();
        db.begin();
        try {
            if (!db.takeCoins(uuid, o.price)) {
                db.rollback();
                plugin.messages().send(p, "ex-no-money", "price", o.price);
                return;
            }
            db.addCoins(o.owner, o.price - fee);
            db.addShopAccount(fee);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.messages().send(p, "error");
            return;
        }
        db.logTx(uuid, p.getName(), "expedition-soeldner", -o.price, "Söldner von " + o.ownerName + " gemietet");
        db.logTx(o.owner, o.ownerName, "expedition-soeldner", o.price - fee, "Söldner an " + p.getName() + " vermietet");
        o.renter = uuid;
        o.renterName = p.getName();
        o.until = ex.restUntilShopReset();
        save();
        say(p, ex.trips().copyName(k.companion(), k.index()) + " von " + o.ownerName + " kämpft bis zum nächsten Shopwechsel für dich (" + o.price + " Coins).");
        Player owner = Bukkit.getPlayer(o.owner);
        if (owner != null) {
            say(owner, p.getName() + " hat deinen " + ex.trips().copyName(k.companion(), k.index()) + " gemietet: +" + (o.price - fee) + " Coins.");
        }
    }

    /** Für die Expedition: gemietete Söldner dieses Spielers (nur gültige Mieten). */
    public List<Offer> activeFor(UUID renter) {
        return rentedBy(renter);
    }
}
