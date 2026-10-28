package de.coinshop;

import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Training (Vorschlag A): Ein Begleiter geht auf einen Trainingsplatz und sammelt pro Stunde Erfahrung (Kills). Er ist in der Zeit
 * nicht für Level verfügbar. Das Ergebnis wird abgeholt. Mehr Plätze kosten Coins (jeder doppelt so viel wie der davor).
 */
public final class TrainingManager {

    /** Ein belegter Trainingsplatz. */
    public record Slot(String key, int idx, long start, int hours) {
        public Copy copy() {
            Companion c = Companion.byKey(key);
            return c == null ? null : new Copy(c, idx);
        }

        public long end() {
            return start + hours * 3_600_000L;
        }
    }

    public static final int[] HOURS = {1, 4, 8, 12, 24};

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;
    private final File file;
    private final Map<UUID, List<Slot>> data = new HashMap<>();

    TrainingManager(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
        this.file = new File(plugin.getDataFolder(), "training.yml");
        load();
    }

    private int cfgI(String key, int def) {
        return plugin.getConfig().getInt("expedition.training." + key, def);
    }

    private double cfgD(String key, double def) {
        return plugin.getConfig().getDouble("expedition.training." + key, def);
    }

    private void say(Player p, String text) {
        plugin.messages().send(p, "ex-train-msg", "text", text);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("expedition.training.enabled", true);
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(id);
                List<Slot> list = new ArrayList<>();
                for (String entry : sec.getStringList(id)) {
                    String[] p = entry.split(";");
                    if (p.length == 4) {
                        list.add(new Slot(p[0], Integer.parseInt(p[1]), Long.parseLong(p[2]), Integer.parseInt(p[3])));
                    }
                }
                data.put(uuid, list);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("training.yml: Eintrag " + id + " übersprungen: " + e.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, List<Slot>> e : data.entrySet()) {
            List<String> out = new ArrayList<>();
            for (Slot s : e.getValue()) {
                out.add(s.key() + ";" + s.idx() + ";" + s.start() + ";" + s.hours());
            }
            if (!out.isEmpty()) {
                y.set("players." + e.getKey(), out);
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("training.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public List<Slot> slots(UUID uuid) {
        return data.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    public int slotCount(UUID uuid) {
        return Math.max(1, cfgI("base-slots", 1)) + (int) plugin.database().getMetaLong("ex_tslots_" + uuid, 0);
    }

    public int maxSlots() {
        return Math.max(1, cfgI("max-slots", 5));
    }

    public long slotPrice(UUID uuid) {
        long extra = plugin.database().getMetaLong("ex_tslots_" + uuid, 0);
        long price = Math.max(1, cfgI("slot-price", 5000));
        for (long i = 0; i < extra && price < Long.MAX_VALUE / 4; i++) {
            price *= 2;
        }
        return price;
    }

    public void buySlot(Player p) {
        UUID uuid = p.getUniqueId();
        if (slotCount(uuid) >= maxSlots()) {
            say(p, "Du hast schon alle Trainingsplätze.");
            return;
        }
        long price = slotPrice(uuid);
        Database db = plugin.database();
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                plugin.messages().send(p, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.setMetaLong("ex_tslots_" + uuid, db.getMetaLong("ex_tslots_" + uuid, 0) + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.messages().send(p, "error");
            return;
        }
        db.logTx(uuid, p.getName(), "expedition-ausruestung", -price, "Trainingsplatz " + slotCount(uuid));
        say(p, "Neuer Trainingsplatz gekauft (" + price + " Coins).");
    }

    /** Wo trainiert dieses Exemplar? null = nirgends. */
    public Slot at(UUID uuid, Companion c, int idx) {
        List<Slot> list = data.get(uuid);
        if (list == null) {
            return null;
        }
        for (Slot s : list) {
            if (s.key().equals(c.key) && s.idx() == idx) {
                return s;
            }
        }
        return null;
    }

    /** Erfahrung pro Stunde: je höher das Level, desto weniger. */
    public double perHour(UUID uuid, Copy k) {
        int level = ex.copyLevel(uuid, k.companion(), k.index());
        double base = Math.max(0.1, cfgD("kills-per-hour", 5.0));
        double brake = Math.max(1, cfgD("level-brake", 50.0));
        return base * brake / (brake + Math.max(1, level));
    }

    /** Preis pro gebuchter Stunde. */
    public long hourPrice(UUID uuid, Copy k) {
        return Math.max(0, cfgI("cost-per-hour", 30)) + Math.max(0, cfgI("cost-per-level", 5)) * (long) ex.copyLevel(uuid, k.companion(), k.index());
    }

    public long earned(UUID uuid, Slot s) {
        Copy k = s.copy();
        if (k == null) {
            return 0;
        }
        double hours = Math.min(s.hours(), Math.max(0, (System.currentTimeMillis() - s.start()) / 3_600_000.0));
        return Math.round(Math.floor(hours * perHour(uuid, k) * 10) / 10.0);
    }

    public boolean finished(Slot s) {
        return System.currentTimeMillis() >= s.end();
    }

    public void start(Player p, Copy k, int hours) {
        UUID uuid = p.getUniqueId();
        if (!enabled()) {
            say(p, "Das Training ist ausgeschaltet.");
            return;
        }
        if (ex.isIn(uuid)) {
            plugin.messages().send(p, "ex-already");
            return;
        }
        if (slots(uuid).size() >= slotCount(uuid)) {
            say(p, "Alle deine Trainingsplätze sind belegt.");
            return;
        }
        String busy = ex.busyReason(uuid, k.companion(), k.index());
        if (busy != null) {
            plugin.messages().send(p, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busy);
            return;
        }
        int h = Math.max(1, Math.min(Math.max(1, cfgI("max-hours", 24)), hours));
        long cost = hourPrice(uuid, k) * h;
        Database db = plugin.database();
        db.begin();
        try {
            if (!db.takeCoins(uuid, cost)) {
                db.rollback();
                plugin.messages().send(p, "ex-no-money", "price", cost);
                return;
            }
            db.addShopAccount(cost);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.messages().send(p, "error");
            return;
        }
        db.logTx(uuid, p.getName(), "expedition-training", -cost, ex.trips().copyName(k.companion(), k.index()) + " " + h + " Std.");
        slots(uuid).add(new Slot(k.companion().key, k.index(), System.currentTimeMillis(), h));
        save();
        say(p, ex.trips().copyName(k.companion(), k.index()) + " trainiert jetzt " + h + " Stunden (" + cost + " Coins).");
    }

    /** Holt das Ergebnis ab (auch vorzeitig: dann gibt es den Anteil der gelaufenen Zeit). */
    public void collect(Player p, Slot s) {
        UUID uuid = p.getUniqueId();
        Copy k = s.copy();
        List<Slot> list = slots(uuid);
        if (!list.remove(s)) {
            return;
        }
        long gain = earned(uuid, s);
        if (k != null && gain > 0) {
            int before = ex.copyLevel(uuid, k.companion(), k.index());
            ex.roster().addKills(uuid, k.companion(), k.index(), gain);
            ex.roster().save(uuid);
            int after = ex.copyLevel(uuid, k.companion(), k.index());
            say(p, ex.trips().copyName(k.companion(), k.index()) + " hat " + gain + " Erfahrung gesammelt"
                    + (after > before ? " und ist jetzt Level " + after + "!" : "."));
        } else {
            say(p, "Das Training brachte noch keine Erfahrung.");
        }
        save();
    }

    /** Text für Menüs: warum ein Exemplar gerade trainiert (null = nicht im Training). */
    public String busyText(UUID uuid, Companion c, int idx) {
        Slot s = at(uuid, c, idx);
        if (s == null) {
            return null;
        }
        return finished(s) ? "Training fertig (im Trainingsmenü abholen)" : "im Training, noch " + ItemUtil.formatDuration(s.end() - System.currentTimeMillis());
    }

    /** Spieler hat die Welt verlassen oder Exemplar ist weg: Einträge zu verlorenen Exemplaren entfernen. */
    public void clean(UUID uuid) {
        List<Slot> list = data.get(uuid);
        if (list == null) {
            return;
        }
        if (list.removeIf(s -> s.copy() == null || ex.roster().isGone(uuid, s.copy().companion(), s.copy().index()))) {
            save();
        }
    }
}
