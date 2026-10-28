package de.coinshop;

import de.coinshop.ExpeditionManager.Companion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Daten jedes einzelnen Begleiter-Exemplars (Kills, Seltenheit, Eigenschaften, Auswahl, Arbeit, Verletzung ...)
 * und die laufende Reise eines Spielers. Gespeichert in plugins/CoinShop/companions/UUID.yml.
 */
public final class CompanionRoster {

    /** Eine laufende (oder fertige, noch nicht abgeholte) Reise. */
    public static final class Trip {
        public int dest;
        public int hours;
        public long start;
        public long end;
        public long seed;
        public double power;
        public double aKills;
        public double aYield;
        public double aSafe;
        public double aCoinMob;
        public double aLootMob;
        public double aXp;
        public final Map<Companion, List<Integer>> copies = new LinkedHashMap<>();

        public int sent() {
            int sum = 0;
            for (List<Integer> list : copies.values()) {
                sum += list.size();
            }
            return sum;
        }
    }

    /** Seltenheit (0 bis 3), Generation (0 = gekauft) und Eigenschaften eines Exemplars. */
    public static final class Info {
        public int rarity;
        public int generation;
        public String variant = "";
        public final List<String> traits = new ArrayList<>();
    }

    /** Zuteilung im Großhandel: Nummer des Auftrags und seit wann. */
    public static final class Work {
        public long order;
        public long since;
    }

    private static final class Data {
        final Map<Companion, List<Long>> kills = new LinkedHashMap<>();
        final Map<Companion, Map<Integer, Long>> injured = new LinkedHashMap<>();
        final Map<Companion, Map<Integer, Long>> rest = new LinkedHashMap<>();
        final Map<Companion, Set<Integer>> taken = new LinkedHashMap<>();
        final Map<Companion, Set<Integer>> gone = new LinkedHashMap<>();
        final Map<Companion, Integer> extra = new LinkedHashMap<>();
        final Map<Companion, Map<Integer, Info>> info = new LinkedHashMap<>();
        final Map<Companion, Map<Integer, Work>> work = new LinkedHashMap<>();
        boolean takenInit;
        long resetNo;
        int duelWins;
        int duelLosses;
        Trip trip;
    }

    private final CoinShopPlugin plugin;
    private final Map<UUID, Data> cache = new HashMap<>();

    CompanionRoster(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private File file(UUID uuid) {
        File dir = new File(plugin.getDataFolder(), "companions");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Ordner companions konnte nicht angelegt werden.");
        }
        return new File(dir, uuid + ".yml");
    }

    private static Map<Integer, Long> readLongMap(ConfigurationSection sec) {
        Map<Integer, Long> map = new HashMap<>();
        for (String idx : sec.getKeys(false)) {
            try {
                map.put(Integer.parseInt(idx), sec.getLong(idx));
            } catch (NumberFormatException ignored) {
                // kaputter Eintrag
            }
        }
        return map;
    }

    private Data data(UUID uuid) {
        Data d = cache.get(uuid);
        if (d != null) {
            return d;
        }
        d = new Data();
        File f = file(uuid);
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            d.resetNo = y.getLong("resetNo", 0);
            ConfigurationSection kills = y.getConfigurationSection("kills");
            if (kills != null) {
                for (String key : kills.getKeys(false)) {
                    Companion c = Companion.byKey(key);
                    if (c != null) {
                        d.kills.put(c, new ArrayList<>(kills.getLongList(key)));
                    }
                }
            }
            for (String section : new String[]{"injured", "rest"}) {
                ConfigurationSection sec = y.getConfigurationSection(section);
                if (sec == null) {
                    continue;
                }
                for (String key : sec.getKeys(false)) {
                    Companion c = Companion.byKey(key);
                    ConfigurationSection inner = sec.getConfigurationSection(key);
                    if (c != null && inner != null) {
                        (section.equals("injured") ? d.injured : d.rest).put(c, readLongMap(inner));
                    }
                }
            }
            for (String section : new String[]{"taken", "gone"}) {
                ConfigurationSection sec = y.getConfigurationSection(section);
                if (sec == null) {
                    continue;
                }
                for (String key : sec.getKeys(false)) {
                    Companion c = Companion.byKey(key);
                    if (c != null) {
                        (section.equals("taken") ? d.taken : d.gone).put(c, new HashSet<>(sec.getIntegerList(key)));
                    }
                }
            }
            d.takenInit = y.getBoolean("taken-init", false);
            d.duelWins = y.getInt("duel.wins");
            d.duelLosses = y.getInt("duel.losses");
            ConfigurationSection extra = y.getConfigurationSection("extra");
            if (extra != null) {
                for (String key : extra.getKeys(false)) {
                    Companion c = Companion.byKey(key);
                    if (c != null) {
                        d.extra.put(c, extra.getInt(key));
                    }
                }
            }
            ConfigurationSection info = y.getConfigurationSection("info");
            if (info != null) {
                for (String key : info.getKeys(false)) {
                    Companion c = Companion.byKey(key);
                    ConfigurationSection inner = info.getConfigurationSection(key);
                    if (c == null || inner == null) {
                        continue;
                    }
                    Map<Integer, Info> map = new HashMap<>();
                    for (String idx : inner.getKeys(false)) {
                        try {
                            Info i = new Info();
                            i.rarity = inner.getInt(idx + ".r");
                            i.generation = inner.getInt(idx + ".g");
                            i.variant = inner.getString(idx + ".v", "");
                            i.traits.addAll(inner.getStringList(idx + ".t"));
                            map.put(Integer.parseInt(idx), i);
                        } catch (NumberFormatException ignored) {
                            // kaputter Eintrag
                        }
                    }
                    d.info.put(c, map);
                }
            }
            ConfigurationSection work = y.getConfigurationSection("work");
            if (work != null) {
                for (String key : work.getKeys(false)) {
                    Companion c = Companion.byKey(key);
                    ConfigurationSection inner = work.getConfigurationSection(key);
                    if (c == null || inner == null) {
                        continue;
                    }
                    Map<Integer, Work> map = new HashMap<>();
                    for (String idx : inner.getKeys(false)) {
                        try {
                            Work w = new Work();
                            w.order = inner.getLong(idx + ".order");
                            w.since = inner.getLong(idx + ".since");
                            map.put(Integer.parseInt(idx), w);
                        } catch (NumberFormatException ignored) {
                            // kaputter Eintrag
                        }
                    }
                    d.work.put(c, map);
                }
            }
            ConfigurationSection t = y.getConfigurationSection("trip");
            if (t != null) {
                Trip trip = new Trip();
                trip.dest = t.getInt("dest");
                trip.hours = t.getInt("hours");
                trip.start = t.getLong("start");
                trip.end = t.getLong("end");
                trip.seed = t.getLong("seed");
                trip.power = t.getDouble("power");
                trip.aKills = t.getDouble("a.kills");
                trip.aYield = t.getDouble("a.yield");
                trip.aSafe = t.getDouble("a.safe");
                trip.aCoinMob = t.getDouble("a.coinmob");
                trip.aLootMob = t.getDouble("a.lootmob");
                trip.aXp = t.getDouble("a.xp");
                ConfigurationSection copies = t.getConfigurationSection("copies");
                if (copies != null) {
                    for (String key : copies.getKeys(false)) {
                        Companion c = Companion.byKey(key);
                        if (c != null) {
                            trip.copies.put(c, new ArrayList<>(copies.getIntegerList(key)));
                        }
                    }
                }
                d.trip = trip;
            }
        }
        cache.put(uuid, d);
        return d;
    }

    public void save(UUID uuid) {
        Data d = data(uuid);
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<Companion, List<Long>> e : d.kills.entrySet()) {
            y.set("kills." + e.getKey().key, e.getValue());
        }
        for (Map.Entry<Companion, Map<Integer, Long>> e : d.injured.entrySet()) {
            for (Map.Entry<Integer, Long> i : e.getValue().entrySet()) {
                y.set("injured." + e.getKey().key + "." + i.getKey(), i.getValue());
            }
        }
        y.set("resetNo", d.resetNo);
        for (Map.Entry<Companion, Map<Integer, Long>> e : d.rest.entrySet()) {
            for (Map.Entry<Integer, Long> i : e.getValue().entrySet()) {
                y.set("rest." + e.getKey().key + "." + i.getKey(), i.getValue());
            }
        }
        for (Map.Entry<Companion, Set<Integer>> e : d.taken.entrySet()) {
            y.set("taken." + e.getKey().key, new ArrayList<>(e.getValue()));
        }
        for (Map.Entry<Companion, Set<Integer>> e : d.gone.entrySet()) {
            y.set("gone." + e.getKey().key, new ArrayList<>(e.getValue()));
        }
        for (Map.Entry<Companion, Integer> e : d.extra.entrySet()) {
            y.set("extra." + e.getKey().key, e.getValue());
        }
        for (Map.Entry<Companion, Map<Integer, Info>> e : d.info.entrySet()) {
            for (Map.Entry<Integer, Info> i : e.getValue().entrySet()) {
                String base = "info." + e.getKey().key + "." + i.getKey();
                y.set(base + ".r", i.getValue().rarity);
                y.set(base + ".g", i.getValue().generation);
                y.set(base + ".v", i.getValue().variant == null ? "" : i.getValue().variant);
                y.set(base + ".t", i.getValue().traits);
            }
        }
        for (Map.Entry<Companion, Map<Integer, Work>> e : d.work.entrySet()) {
            for (Map.Entry<Integer, Work> i : e.getValue().entrySet()) {
                String base = "work." + e.getKey().key + "." + i.getKey();
                y.set(base + ".order", i.getValue().order);
                y.set(base + ".since", i.getValue().since);
            }
        }
        y.set("taken-init", d.takenInit);
        y.set("duel.wins", d.duelWins);
        y.set("duel.losses", d.duelLosses);
        if (d.trip != null) {
            Trip t = d.trip;
            y.set("trip.dest", t.dest);
            y.set("trip.hours", t.hours);
            y.set("trip.start", t.start);
            y.set("trip.end", t.end);
            y.set("trip.seed", t.seed);
            y.set("trip.power", t.power);
            y.set("trip.a.kills", t.aKills);
            y.set("trip.a.yield", t.aYield);
            y.set("trip.a.safe", t.aSafe);
            y.set("trip.a.coinmob", t.aCoinMob);
            y.set("trip.a.lootmob", t.aLootMob);
            y.set("trip.a.xp", t.aXp);
            for (Map.Entry<Companion, List<Integer>> e : t.copies.entrySet()) {
                y.set("trip.copies." + e.getKey().key, e.getValue());
            }
        }
        try {
            y.save(file(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Begleiterdaten konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    // --- Kills / Level ------------------------------------------------

    public long kills(UUID uuid, Companion c, int idx) {
        List<Long> list = data(uuid).kills.get(c);
        return list == null || idx < 0 || idx >= list.size() ? 0 : list.get(idx);
    }

    public void addKills(UUID uuid, Companion c, int idx, long n) {
        List<Long> list = data(uuid).kills.computeIfAbsent(c, k -> new ArrayList<>());
        while (list.size() <= idx) {
            list.add(0L);
        }
        list.set(idx, list.get(idx) + Math.max(0, n));
    }

    // --- Verletzung und Ruhe -------------------------------------------

    public boolean injured(UUID uuid, Companion c, int idx) {
        return injuredUntil(uuid, c, idx) > System.currentTimeMillis();
    }

    public long injuredUntil(UUID uuid, Companion c, int idx) {
        Map<Integer, Long> map = data(uuid).injured.get(c);
        return map == null ? 0 : map.getOrDefault(idx, 0L);
    }

    public void setInjured(UUID uuid, Companion c, int idx, long until) {
        data(uuid).injured.computeIfAbsent(c, k -> new HashMap<>()).put(idx, until);
    }

    /** Ruhezeiten enden mit dem Shopwechsel: hat sich der Shop seit dem Eintrag zurückgesetzt, sind alle erholt. */
    private void syncReset(Data d) {
        long cur = plugin.database().getMetaLong("shop_reset_no", 0);
        if (d.resetNo != cur) {
            d.rest.clear();
            d.resetNo = cur;
        }
    }

    public long restUntil(UUID uuid, Companion c, int idx) {
        syncReset(data(uuid));
        Map<Integer, Long> map = data(uuid).rest.get(c);
        return map == null ? 0 : map.getOrDefault(idx, 0L);
    }

    public boolean resting(UUID uuid, Companion c, int idx) {
        return restUntil(uuid, c, idx) > System.currentTimeMillis();
    }

    public void setRest(UUID uuid, Companion c, int idx, long until) {
        syncReset(data(uuid));
        data(uuid).rest.computeIfAbsent(c, k -> new HashMap<>()).put(idx, until);
    }

    // --- Auswahl fürs Level -------------------------------------------

    public boolean takenInit(UUID uuid) {
        return data(uuid).takenInit;
    }

    public void setTakenInit(UUID uuid) {
        data(uuid).takenInit = true;
    }

    public boolean isTaken(UUID uuid, Companion c, int idx) {
        Set<Integer> set = data(uuid).taken.get(c);
        return set != null && set.contains(idx);
    }

    public void setTaken(UUID uuid, Companion c, int idx, boolean on) {
        Set<Integer> set = data(uuid).taken.computeIfAbsent(c, k -> new HashSet<>());
        if (on) {
            set.add(idx);
        } else {
            set.remove(idx);
        }
    }

    // --- Verbrauchte Exemplare (Opfer, Fusion) und zusätzliche (Zucht, Fusion) ---

    public boolean isGone(UUID uuid, Companion c, int idx) {
        Set<Integer> set = data(uuid).gone.get(c);
        return set != null && set.contains(idx);
    }

    public void markGone(UUID uuid, Companion c, int idx) {
        data(uuid).gone.computeIfAbsent(c, k -> new HashSet<>()).add(idx);
        setTaken(uuid, c, idx, false);
        clearWork(uuid, c, idx);
    }

    public int extra(UUID uuid, Companion c) {
        return data(uuid).extra.getOrDefault(c, 0);
    }

    public void addExtra(UUID uuid, Companion c) {
        data(uuid).extra.merge(c, 1, Integer::sum);
    }

    // --- Seltenheit, Eigenschaften ----------------------------------------

    public Info info(UUID uuid, Companion c, int idx) {
        Map<Integer, Info> map = data(uuid).info.get(c);
        return map == null ? null : map.get(idx);
    }

    public void setInfo(UUID uuid, Companion c, int idx, Info info) {
        data(uuid).info.computeIfAbsent(c, k -> new HashMap<>()).put(idx, info);
    }

    // --- Großhandel -----------------------------------------------------------

    public Work work(UUID uuid, Companion c, int idx) {
        Map<Integer, Work> map = data(uuid).work.get(c);
        return map == null ? null : map.get(idx);
    }

    public void setWork(UUID uuid, Companion c, int idx, long order, long since) {
        Work w = new Work();
        w.order = order;
        w.since = since;
        data(uuid).work.computeIfAbsent(c, k -> new HashMap<>()).put(idx, w);
    }

    public void clearWork(UUID uuid, Companion c, int idx) {
        Map<Integer, Work> map = data(uuid).work.get(c);
        if (map != null) {
            map.remove(idx);
        }
    }

    public int workerCount(UUID uuid) {
        int sum = 0;
        for (Map<Integer, Work> map : data(uuid).work.values()) {
            sum += map.size();
        }
        return sum;
    }

    // --- Duelle -----------------------------------------------------------

    public int duelWins(UUID uuid) {
        return data(uuid).duelWins;
    }

    public int duelLosses(UUID uuid) {
        return data(uuid).duelLosses;
    }

    public void addDuel(UUID uuid, boolean won) {
        if (won) {
            data(uuid).duelWins++;
        } else {
            data(uuid).duelLosses++;
        }
    }

    // --- Reise --------------------------------------------------------

    public Trip trip(UUID uuid) {
        return data(uuid).trip;
    }

    public void setTrip(UUID uuid, Trip trip) {
        data(uuid).trip = trip;
    }

    public boolean away(UUID uuid, Companion c, int idx) {
        Trip t = data(uuid).trip;
        if (t == null) {
            return false;
        }
        List<Integer> list = t.copies.get(c);
        return list != null && list.contains(idx);
    }
}
