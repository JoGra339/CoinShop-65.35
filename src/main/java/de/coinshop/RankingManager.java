package de.coinshop;

import de.coinshop.Database.RankEntry;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die Ranglisten.
 *
 * Die Zahlen stammen aus den normalen Minecraft-Statistiken der Spieler (dieselben wie unter
 * "Statistiken" im Spielmenü). Das Plugin liest sie regelmäßig für alle Online-Spieler (und beim
 * Verlassen des Servers) und speichert sie in der Datenbank - so gibt es auch Ranglisten für
 * Spieler, die gerade offline sind.
 *
 * Die ersten 5 Plätze jeder Rangliste bekommen Bonus-Einkommen (Standard: 60, 40, 25, 15, 10 Coins pro Tag).
 */
public final class RankingManager {

    /** Alle Ranglisten. "stat" ist der Name in der Datenbank, "ascending" = wenigster Wert gewinnt. */
    public enum Rank {
        DIAMONDS("diamonds", Material.DIAMOND_ORE, "diamonds", false),
        DEBRIS("debris", Material.ANCIENT_DEBRIS, "debris", false),
        WOOD("wood", Material.OAK_LOG, "wood", false),
        STONE("stone", Material.COBBLESTONE, "stone", false),
        FISHING("fishing", Material.FISHING_ROD, "fishing", false),
        CREATURES("creatures", Material.IRON_SWORD, "creatures", false),
        DEATHS_LEAST("deaths-least", Material.TOTEM_OF_UNDYING, "deaths", true),
        DEATHS_MOST("deaths-most", Material.SKELETON_SKULL, "deaths", false),
        TRADES("trades", Material.EMERALD, "trades", false),
        WALKED("walked", Material.LEATHER_BOOTS, "walked", false),
        BOSSES("bosses", Material.DRAGON_HEAD, "bosses", false),
        PLACED("placed", Material.BRICKS, "placed", false),
        MINED("mined", Material.DIAMOND_PICKAXE, "mined", false),
        PLAYTIME("playtime", Material.CLOCK, "playtime", false);

        private final String id;
        private final Material icon;
        private final String stat;
        private final boolean ascending;

        Rank(String id, Material icon, String stat, boolean ascending) {
            this.id = id;
            this.icon = icon;
            this.stat = stat;
            this.ascending = ascending;
        }

        public String id() {
            return id;
        }

        public Material icon() {
            return icon;
        }

        public String stat() {
            return stat;
        }

        public boolean ascending() {
            return ascending;
        }
    }

    // ---- Welche Blöcke zählen zu "Holz" und "Stein"? ----
    private static final List<Material> WOOD_BLOCKS = new ArrayList<>();
    private static final List<Material> STONE_BLOCKS = new ArrayList<>();
    private static final List<Material> PLACEABLE_BLOCKS = new ArrayList<>();
    private static final List<Material> ALL_BLOCKS = new ArrayList<>();
    /** Alle Kreaturen-Arten (Tiere, Monster, Dorfbewohner ...), die zu "Kreaturen getötet" zählen. */
    private static final List<EntityType> CREATURE_TYPES = new ArrayList<>();

    static {
        for (EntityType type : EntityType.values()) {
            Class<?> cls = type.getEntityClass();
            if (cls != null && org.bukkit.entity.LivingEntity.class.isAssignableFrom(cls)
                    && type != EntityType.PLAYER && type != EntityType.ARMOR_STAND
                    && !type.name().equals("MANNEQUIN")) {
                CREATURE_TYPES.add(type);
            }
        }
        List<String> stoneNames = List.of("STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "DEEPSLATE",
                "COBBLED_DEEPSLATE", "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "BLACKSTONE",
                "BASALT", "SMOOTH_BASALT", "END_STONE", "NETHERRACK", "DRIPSTONE_BLOCK");
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) {
                continue;
            }
            ALL_BLOCKS.add(m);
            String n = m.name();
            // Holz: alle Stämme/Stiele und ihre "Holz"-Varianten (auch entrindet), dazu Bambusblöcke
            if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")
                    || n.equals("BAMBOO_BLOCK") || n.equals("STRIPPED_BAMBOO_BLOCK")) {
                WOOD_BLOCKS.add(m);
            }
            if (stoneNames.contains(n)) {
                STONE_BLOCKS.add(m);
            }
            if (m.isItem() && !m.isAir()) {
                PLACEABLE_BLOCKS.add(m);
            }
        }
    }

    private final CoinShopPlugin plugin;

    public RankingManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Einstellungen
    // ------------------------------------------------------------------

    /** Bonus pro Platz (Platz 1 zuerst). */
    public List<Long> bonuses() {
        List<Long> list = new ArrayList<>();
        for (long v : plugin.getConfig().getLongList("rankings.bonus")) {
            list.add(Math.max(0, v));
        }
        if (list.isEmpty()) {
            list.addAll(List.of(10L, 5L, 3L, 2L, 1L));
        }
        return list;
    }

    public long minPlaytimeMinutes() {
        return Math.max(0, plugin.getConfig().getLong("rankings.min-playtime-minutes", 120));
    }

    public int syncSeconds() {
        return Math.max(30, plugin.getConfig().getInt("rankings.sync-seconds", 120));
    }

    // ------------------------------------------------------------------
    //  Statistiken einlesen
    // ------------------------------------------------------------------

    private static long mined(Player p, List<Material> materials) {
        long sum = 0;
        for (Material m : materials) {
            try {
                sum += p.getStatistic(Statistic.MINE_BLOCK, m);
            } catch (IllegalArgumentException ignored) {
                // Material hat keine Statistik - überspringen
            }
        }
        return sum;
    }

    private static long killed(Player p, EntityType type) {
        try {
            return p.getStatistic(Statistic.KILL_ENTITY, type);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /**
     * Liest alle Zahlen eines Online-Spielers aus den Minecraft-Statistiken
     * (Schlüssel wie "mined", "wood", "walked" ...). Wird auch für die täglichen Aufgaben benutzt.
     */
    public Map<String, Long> statValues(Player p) {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("diamonds", mined(p, List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE)));
        values.put("debris", mined(p, List.of(Material.ANCIENT_DEBRIS)));
        values.put("wood", mined(p, WOOD_BLOCKS));
        values.put("stone", mined(p, STONE_BLOCKS));
        values.put("mined", mined(p, ALL_BLOCKS));
        values.put("fishing", (long) p.getStatistic(Statistic.FISH_CAUGHT));
        long creatures = 0;
        for (EntityType type : CREATURE_TYPES) {
            creatures += killed(p, type);
        }
        values.put("creatures", creatures);
        values.put("deaths", (long) p.getStatistic(Statistic.DEATHS));
        values.put("trades", (long) p.getStatistic(Statistic.TRADED_WITH_VILLAGER));
        long cm = (long) p.getStatistic(Statistic.WALK_ONE_CM)
                + p.getStatistic(Statistic.SPRINT_ONE_CM)
                + p.getStatistic(Statistic.CROUCH_ONE_CM);
        values.put("walked", cm / 100);
        values.put("bosses", killed(p, EntityType.WARDEN) + killed(p, EntityType.ENDER_DRAGON)
                + killed(p, EntityType.ELDER_GUARDIAN) + killed(p, EntityType.WITHER));

        long placed = 0;
        for (Material m : PLACEABLE_BLOCKS) {
            try {
                placed += p.getStatistic(Statistic.USE_ITEM, m);
            } catch (IllegalArgumentException ignored) {
                // kein benutzbares Item - überspringen
            }
        }
        values.put("placed", placed);

        // Verknüpfung mit dem normalen Minecraft (für die Tagesaufgaben)
        values.put("enchanted", (long) p.getStatistic(Statistic.ITEM_ENCHANTED));
        values.put("bred", (long) p.getStatistic(Statistic.ANIMALS_BRED));
        values.put("brewed", WorldActivity.count(plugin, p.getUniqueId(), "brew"));
        values.put("harvest", WorldActivity.count(plugin, p.getUniqueId(), "harvest"));

        // Spielzeit in Minuten (Minecraft zählt in Ticks: 20 pro Sekunde)
        values.put("playtime", (long) p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60);
        return values;
    }

    /** Liest die Statistiken eines Online-Spielers und speichert sie in der Datenbank. */
    public void sync(Player p) {
        if (plugin.expeditions().isIn(p.getUniqueId())) {
            return; // Expeditions-Werte zählen nicht
        }
        try {
            Map<String, Long> values = statValues(p);
            plugin.database().ensurePlayer(p.getUniqueId(), p.getName());
            plugin.database().setStats(p.getUniqueId(), values);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Statistiken von " + p.getName() + " konnten nicht gespeichert werden: " + e);
        }
    }

    public void syncOnline() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            sync(p);
        }
    }

    // ------------------------------------------------------------------
    //  Ranglisten abfragen
    // ------------------------------------------------------------------

    public List<RankEntry> top(Rank rank, int limit) {
        if (rank == Rank.DEATHS_LEAST) {
            return plugin.database().fewestDeaths(minPlaytimeMinutes(), limit);
        }
        return plugin.database().topStat(rank.stat(), limit);
    }

    /**
     * Wöchentliche Mystery Boxen für die Spitze jeder Rangliste (sonntags, einmal pro Tag):
     * Platz 1 bekommt eine Box der Stufe 2, Platz 2 und 3 eine der Stufe 1 (Werte in rankings.box-tiers).
     */
    public void weeklyBoxes() {
        if (!plugin.mysteryBoxes().enabled() || !plugin.getConfig().getBoolean("rankings.weekly-boxes", true)) {
            return;
        }
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(plugin.serverShop().zone());
        if (now.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            return;
        }
        long today = now.toLocalDate().toEpochDay();
        if (plugin.database().getMetaLong("rank_box_day", -1) == today) {
            return;
        }
        plugin.database().setMetaLong("rank_box_day", today);
        List<Integer> tiers = plugin.getConfig().getIntegerList("rankings.box-tiers");
        if (tiers.isEmpty()) {
            tiers = List.of(2, 1, 1);
        }
        for (Rank rank : Rank.values()) {
            for (Placed placed : ranked(rank, 10)) {
                if (placed.place() < 1 || placed.place() > tiers.size() || placed.entry().value() <= 0) {
                    continue;
                }
                UUID who = placed.entry().uuid();
                plugin.mysteryBoxes().give(who, plugin.database().getName(who), tiers.get(placed.place() - 1), 1,
                        "Platz " + placed.place() + " in der Rangliste " + rank.id());
            }
        }
    }

    /** Bonus, den ein Platz (1 = Erster) allein bringt. */
    public long bonusForPlace(int place) {
        List<Long> b = bonuses();
        return place >= 1 && place <= b.size() ? b.get(place - 1) : 0;
    }

    /** Ein Eintrag der Rangliste mit Platz (bei Gleichstand gleicher Platz) und Bonus-Anteil. */
    public record Placed(RankEntry entry, int place, int tied, long bonus) { }

    /** Mehrere Spieler mit demselben Wert teilen sich die Boni ihrer Plätze. */
    private record Group(int firstPlace, List<RankEntry> members, long pool) { }

    private List<Group> groups(Rank rank, int limit) {
        List<RankEntry> list = top(rank, limit);
        List<Group> groups = new ArrayList<>();
        int i = 0;
        while (i < list.size()) {
            int j = i;
            while (j < list.size() && list.get(j).value() == list.get(i).value()) {
                j++;
            }
            long pool = 0;
            for (int place = i + 1; place <= j; place++) {
                pool += bonusForPlace(place);
            }
            groups.add(new Group(i + 1, list.subList(i, j), pool));
            i = j;
        }
        return groups;
    }

    /** Die Rangliste mit Plätzen und (abgerundeten) Bonus-Anteilen. */
    public List<Placed> ranked(Rank rank, int limit) {
        List<Placed> result = new ArrayList<>();
        for (Group g : groups(rank, 200)) {
            for (RankEntry e : g.members()) {
                if (result.size() < limit) {
                    result.add(new Placed(e, g.firstPlace(), g.members().size(), g.pool() / g.members().size()));
                }
            }
        }
        return result;
    }

    /** Platz und Bonus-Anteil eines Spielers in einer Rangliste (null, wenn er nicht gelistet ist). */
    public Placed info(Rank rank, UUID uuid) {
        for (Group g : groups(rank, 200)) {
            for (RankEntry e : g.members()) {
                if (e.uuid().equals(uuid)) {
                    return new Placed(e, g.firstPlace(), g.members().size(), g.pool() / g.members().size());
                }
            }
        }
        return null;
    }

    /**
     * Bonus-Einkommen pro Spieler aus allen Ranglisten. Bei Gleichstand wird fair geteilt;
     * bleibt dabei ein Rest-Coin übrig, bekommt ihn ein zufällig gewählter Spieler der Gruppe.
     */
    public Map<UUID, Long> dailyBonuses() {
        return computeBonuses(true);
    }

    /** Wie dailyBonuses(), aber ohne Zufall (abgerundete Anteile) - für Anzeigen. */
    public Map<UUID, Long> currentBonuses() {
        return computeBonuses(false);
    }

    private Map<UUID, Long> computeBonuses(boolean distributeRemainder) {
        Map<UUID, Long> result = new HashMap<>();
        java.util.Random random = new java.util.Random();
        for (Rank rank : Rank.values()) {
            for (Group g : groups(rank, 200)) {
                if (g.pool() <= 0) {
                    continue;
                }
                int n = g.members().size();
                long share = g.pool() / n;
                long remainder = g.pool() % n;
                List<RankEntry> shuffled = new ArrayList<>(g.members());
                if (distributeRemainder) {
                    java.util.Collections.shuffle(shuffled, random);
                }
                for (int k = 0; k < n; k++) {
                    long amount = share + (distributeRemainder && k < remainder ? 1 : 0);
                    if (amount > 0) {
                        result.merge(shuffled.get(k).uuid(), amount, Long::sum);
                    }
                }
            }
        }
        // Lizenz "ranglisten": Bonus um Prozent erhöht
        result.replaceAll((uuid, value) -> plugin.licenses().boost(uuid, "ranglisten", value));
        return result;
    }
}
