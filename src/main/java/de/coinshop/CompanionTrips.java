package de.coinshop;

import de.coinshop.CompanionAspects.TripAspect;
import de.coinshop.CompanionRoster.Trip;
import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Begleiterreisen: Begleiter gehen selbststaendig auf Expedition (Dauer waehlbar). Die Ziele werden durch Meilensteine
 * (hoechstes geschafftes Expeditionslevel) freigeschaltet. Das Ergebnis steht beim Start fest (Zufallszahl) und haengt
 * von Staerke, Dauer, Aspekten und Level der Mitreisenden ab.
 */
public final class CompanionTrips {

    private static final String[] NAMES = {"Waldlichtung", "Felsenhöhle", "Verlassene Mine", "Hexensumpf", "Schneetundra",
            "Ruinenstadt", "Korallenriff", "Wüstentempel", "Nether-Festung", "Pilzinsel", "Geisterwald", "Gletscherspalte",
            "Ende-Insel", "Urzeit-Dschungel", "Tiefenwelt", "Ozeanmonument", "Vulkankrater", "Himmelsturm", "Drachenhort",
            "Wolkenfestung", "Obsidianspitze", "Frostzitadelle", "Schattenreich", "Weltenrand"};
    private static final Material[] ICONS = {Material.OAK_SAPLING, Material.IRON_PICKAXE, Material.RAIL, Material.LILY_PAD,
            Material.SNOW_BLOCK, Material.MOSSY_STONE_BRICKS, Material.BRAIN_CORAL, Material.SANDSTONE, Material.NETHER_BRICKS,
            Material.RED_MUSHROOM, Material.DARK_OAK_SAPLING, Material.BLUE_ICE, Material.END_STONE, Material.JUNGLE_SAPLING,
            Material.SCULK, Material.PRISMARINE, Material.MAGMA_BLOCK, Material.FEATHER, Material.DRAGON_HEAD, Material.ELYTRA,
            Material.CRYING_OBSIDIAN, Material.PACKED_ICE, Material.ECHO_SHARD, Material.BEDROCK};

    /** Typ der Gegner am Ziel: Begleiter mit starkem Typ sind dort 1,5-fach, mit schwachem 0,7-fach so stark. */
    private static final CompanionTypes.MobType[] DEST_TYPES = {CompanionTypes.MobType.NATUR, CompanionTypes.MobType.ERDE,
            CompanionTypes.MobType.ERDE, CompanionTypes.MobType.WASSER, CompanionTypes.MobType.EIS, CompanionTypes.MobType.UNTOTE,
            CompanionTypes.MobType.WASSER, CompanionTypes.MobType.ERDE, CompanionTypes.MobType.FEUER, CompanionTypes.MobType.NATUR,
            CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.EIS, CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.NATUR,
            CompanionTypes.MobType.UNTOTE, CompanionTypes.MobType.WASSER, CompanionTypes.MobType.FEUER, CompanionTypes.MobType.LUFT,
            CompanionTypes.MobType.FEUER, CompanionTypes.MobType.LUFT, CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.EIS,
            CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.ERDE};

    public CompanionTypes.MobType destType(int dest) {
        return DEST_TYPES[Math.max(0, Math.min(DEST_TYPES.length - 1, dest))];
    }

    /** Gewaehlte Exemplare einer Reise samt Staerke und Aspekten. */
    public record Selection(Map<Companion, List<Integer>> copies, double power, double aKills, double aYield,
                            double aSafe, double aCoinMob, double aLootMob, double aXp) {
        public int size() {
            int sum = 0;
            for (List<Integer> l : copies.values()) {
                sum += l.size();
            }
            return sum;
        }
    }

    /** Ergebnis einer Reise (feststehend, bevor man es abholt). */
    public record Outcome(int dest, int hours, int sent, double power, double required, int kills, int coinMobs,
                          long coinMobCoins, int lootMobs, long baseCoins, long totalCoins, int xpEach,
                          List<String> hurt, double hurtShare, double yieldFactor) { }

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;

    CompanionTrips(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
    }

    private double cfgD(String key, double def) {
        return plugin.getConfig().getDouble("expedition.trip." + key, def);
    }

    // ------------------------------------------------------------------
    //  Ziele und Dauer
    // ------------------------------------------------------------------

    public List<Integer> milestones() {
        List<Integer> list = plugin.getConfig().getIntegerList("expedition.trip.milestones");
        if (list.isEmpty()) {
            list = List.of(50, 100, 150, 200, 300, 400, 500, 650, 800, 1000, 1250, 1500, 1800, 2100, 2500, 3000, 3500, 4000, 5000, 6000, 7000, 8000, 9000, 10000);
        }
        return list;
    }

    public List<Integer> durations() {
        List<Integer> list = plugin.getConfig().getIntegerList("expedition.trip.durations-hours");
        if (list.isEmpty()) {
            list = List.of(1, 4, 8, 12, 24);
        }
        return list;
    }

    public int destinations() {
        return Math.min(NAMES.length, milestones().size());
    }

    public String name(int dest) {
        return NAMES[Math.max(0, Math.min(NAMES.length - 1, dest))];
    }

    public Material icon(int dest) {
        return ICONS[Math.max(0, Math.min(ICONS.length - 1, dest))];
    }

    public int milestone(int dest) {
        return milestones().get(Math.max(0, Math.min(milestones().size() - 1, dest)));
    }

    public int cleared(UUID uuid) {
        return ex.nextLevel(uuid) - 1;
    }

    public boolean unlocked(UUID uuid, int dest) {
        return cleared(uuid) >= milestone(dest);
    }

    // ------------------------------------------------------------------
    //  Zustand
    // ------------------------------------------------------------------

    /** Die Reise des Spielers (bereits bezahlte Reisen werden verworfen). */
    public Trip trip(UUID uuid) {
        Trip t = ex.roster().trip(uuid);
        if (t != null && plugin.database().getMetaLong("ex_t2_paid_" + uuid, 0) == t.start) {
            ex.roster().setTrip(uuid, null);
            ex.roster().save(uuid);
            return null;
        }
        return t;
    }

    public boolean active(UUID uuid) {
        return trip(uuid) != null;
    }

    public boolean finished(UUID uuid) {
        Trip t = trip(uuid);
        return t != null && System.currentTimeMillis() >= t.end;
    }

    public int totalAway(UUID uuid) {
        Trip t = trip(uuid);
        return t == null ? 0 : t.sent();
    }

    public int totalInjured(UUID uuid) {
        int sum = 0;
        for (Companion c : Companion.values()) {
            for (int i : ex.existingCopies(uuid, c)) {
                if (ex.roster().injured(uuid, c, i)) {
                    sum++;
                }
            }
        }
        return sum;
    }

    public String timeText(long millis) {
        long minutes = Math.max(0, millis) / 60_000L;
        return (minutes / 60) + " Std. " + (minutes % 60) + " Min.";
    }

    public String copyName(Companion c, int idx) {
        return c.label + " #" + (idx + 1);
    }

    // ------------------------------------------------------------------
    //  Staerke, Beute, Risiko
    // ------------------------------------------------------------------

    public double weight(Companion c) {
        return 1.0 + c.defaultPrice / 10000.0;
    }

    public double required(int milestone) {
        return Math.max(1, milestone * Math.max(0.01, cfgD("power-per-level", 0.6)));
    }

    /** Loot (in Coins), den man bis zu diesem Meilenstein haette erlangen koennen: Level-Belohnungen, CoinMobs, Loot-Mobs. */
    public long potential(UUID uuid, int milestone) {
        double mult = ex.multFactor(ex.multStage(uuid));
        double share = Math.max(0, cfgD("coin-mob-share", 0.5));
        double permille = plugin.getConfig().getDouble("expedition.loot-mob.permille", 1) / 1000.0;
        double base = plugin.getConfig().getDouble("expedition.loot-mob.value-base", 40000);
        double perLevel = plugin.getConfig().getDouble("expedition.loot-mob.value-per-level", 40);
        double sum = 0;
        for (int l = 1; l <= milestone; l++) {
            sum += l * mult * (1 + share) + permille * (base + perLevel * l) * 1.75;
        }
        return Math.round(sum);
    }

    public double yieldFactor(double ratio) {
        return Math.max(0.1, Math.min(1.5, ratio));
    }

    public double injuryChance(double ratio, int hours) {
        double base = Math.max(0, cfgD("base-injury-percent", 3)) / 100.0;
        double chance = base + (1 - Math.min(1, ratio)) * 0.4;
        return Math.min(0.9, chance * Math.min(1.0, 0.5 + hours / 24.0));
    }

    /** Stärke eines einzelnen Exemplars auf einer Reise zu diesem Ziel (Level, Seltenheit und Typ gegen das Ziel zählen). */
    public double power(UUID uuid, Companion c, int idx, int dest) {
        CompanionTraits.Rarity rar = ex.rarity(uuid, c, idx);
        int lvl = ex.copyLevel(uuid, c, idx);
        return weight(c) * (1.0 + 0.1 * (lvl - 1)) * rar.stats * CompanionTypes.factor(c.mobType(), destType(dest));
    }

    /** Stärke und Aspekte der gewählten Exemplare (Seltenheit, Eigenschaften und Typ gegen das Ziel zählen mit). */
    public Selection select(UUID uuid, int dest, Collection<Copy> chosen) {
        Map<Companion, List<Integer>> copies = new LinkedHashMap<>();
        double power = 0;
        double[] a = new double[6];
        CompanionTypes.MobType enemy = destType(dest);
        for (Copy k : chosen) {
            Companion c = k.companion();
            int idx = k.index();
            if (!ex.exists(uuid, c, idx)) {
                continue;
            }
            copies.computeIfAbsent(c, x -> new ArrayList<>()).add(idx);
            TripAspect ta = CompanionAspects.trip(c);
            CompanionTraits.Rarity rar = ex.rarity(uuid, c, idx);
            int lvl = ex.copyLevel(uuid, c, idx);
            power += power(uuid, c, idx, dest);
            double v = CompanionAspects.scaled(ta.base(), lvl) * rar.aspect;
            switch (ta.kind()) {
                case KILLS -> a[0] += v;
                case YIELD -> a[1] += v;
                case SAFE -> a[2] += v;
                case COINMOB -> a[3] += v;
                case LOOTMOB -> a[4] += v;
                case XP -> a[5] += v;
            }
            for (CompanionTraits.Trait t : ex.traits(uuid, c, idx)) {
                a[0] += t.tripKills;
                a[1] += t.tripYield;
                a[2] += t.tripSafe;
                a[5] += t.tripXp;
            }
        }
        var k = CompanionAspects.TripKind.values();
        return new Selection(copies, power, Math.max(0, Math.min(k[0].cap, a[0])), Math.max(0, Math.min(k[1].cap, a[1])),
                Math.max(0, Math.min(k[2].cap, a[2])), Math.min(k[3].cap, a[3]), Math.min(k[4].cap, a[4]),
                Math.max(0, Math.min(k[5].cap, a[5])));
    }

    /** Erwartete Beute ohne Zufall (fuer die Vorschau im Auswahlmenue). */
    public long expectedCoins(UUID uuid, int dest, int hours, Selection sel) {
        int m = milestone(dest);
        double yf = yieldFactor(sel.power() / required(m));
        double pct = Math.max(0, cfgD("yield-percent", 10)) / 100.0;
        return Math.round(potential(uuid, m) * pct * (hours / 24.0) * yf * (1 + sel.aYield()));
    }

    public int expectedKills(int dest, int hours, Selection sel) {
        int m = milestone(dest);
        double yf = yieldFactor(sel.power() / required(m));
        return (int) Math.round(hours * cfgD("waves-per-hour", 1.0) * Math.max(1, ex.creaturesIn(m)) * yf * (1 + sel.aKills()));
    }

    // ------------------------------------------------------------------
    //  Ergebnis (feststehend aus der Zufallszahl der Reise)
    // ------------------------------------------------------------------

    public Outcome outcome(UUID uuid, Trip t) {
        Random r = new Random(t.seed);
        int m = milestone(t.dest);
        double req = required(m);
        double ratio = t.power / req;
        double yf = yieldFactor(ratio);
        double waves = t.hours * cfgD("waves-per-hour", 1.0);
        int size = Math.max(1, ex.creaturesIn(m));
        double k0 = Math.max(1, waves * size * yf);
        int kills = (int) Math.round(k0 * (1 + t.aKills) * (0.9 + 0.2 * r.nextDouble()));
        // Ereignisse: pro Welle Chance auf einen CoinMob, selten auf einen Loot-Mob
        int rolls = (int) Math.floor(waves) + (r.nextDouble() < waves - Math.floor(waves) ? 1 : 0);
        double coinChance = Math.min(0.9, cfgD("coin-event-percent", 12) / 100.0 + t.aCoinMob);
        double lootChance = Math.min(0.2, cfgD("loot-event-permille", 3) / 1000.0 + t.aLootMob);
        long perLevel = Math.max(1, potential(uuid, m) / Math.max(1, m));
        int coinMobs = 0;
        int lootMobs = 0;
        long coinMobCoins = 0;
        for (int i = 0; i < rolls; i++) {
            if (r.nextDouble() < coinChance) {
                coinMobs++;
                coinMobCoins += perLevel * (2 + r.nextInt(3));
            }
            if (r.nextDouble() < lootChance) {
                lootMobs++;
            }
        }
        // Verletzungen
        double chance = Math.max(0, Math.min(0.9, injuryChance(ratio, t.hours) - t.aSafe));
        List<String> hurt = new ArrayList<>();
        for (Map.Entry<Companion, List<Integer>> e : t.copies.entrySet()) {
            for (int idx : e.getValue()) {
                if (r.nextDouble() < chance) {
                    hurt.add(e.getKey().key + ":" + idx);
                }
            }
        }
        int sent = Math.max(1, t.sent());
        double hurtShare = (double) hurt.size() / sent;
        double kFactor = Math.max(0.3, Math.min(2.5, kills / k0));
        double pct = Math.max(0, cfgD("yield-percent", 10)) / 100.0;
        long base = Math.round(potential(uuid, m) * pct * (t.hours / 24.0) * yf * (1 + t.aYield) * kFactor * (1 - 0.4 * hurtShare));
        int xpEach = (int) Math.round(kills * Math.max(0, cfgD("xp-factor", 0.2)) * (1 + t.aXp) / sent);
        return new Outcome(t.dest, t.hours, t.sent(), t.power, req, kills, coinMobs, coinMobCoins, lootMobs, base,
                base + coinMobCoins, xpEach, hurt, hurtShare, yf);
    }

    /** Lesbare Namen der Verletzten (aus "key:idx"). */
    public List<String> hurtNames(Outcome o) {
        List<String> names = new ArrayList<>();
        for (String s : o.hurt()) {
            String[] p = s.split(":");
            Companion c = Companion.byKey(p[0]);
            if (c != null) {
                names.add(copyName(c, Integer.parseInt(p[1])));
            }
        }
        return names;
    }

    // ------------------------------------------------------------------
    //  Losschicken und Abholen
    // ------------------------------------------------------------------

    public void start(Player player, int dest, int hours, Collection<Copy> chosen) {
        Messages msg = plugin.messages();
        UUID uuid = player.getUniqueId();
        if (!ex.hasLicense(player) || ex.isIn(uuid)) {
            return;
        }
        if (active(uuid)) {
            msg.send(player, "ex-trip-busy");
            return;
        }
        if (dest < 0 || dest >= destinations() || !unlocked(uuid, dest)) {
            msg.send(player, "ex-trip-locked", "level", milestone(Math.max(0, Math.min(destinations() - 1, dest))));
            return;
        }
        if (!durations().contains(hours)) {
            hours = durations().get(0);
        }
        for (Copy k : chosen) {
            if (!ex.exists(uuid, k.companion(), k.index()) || ex.busyReason(uuid, k.companion(), k.index()) != null) {
                msg.send(player, "ex-trip-away");
                return;
            }
        }
        Selection sel = select(uuid, dest, chosen);
        if (sel.size() <= 0) {
            msg.send(player, "ex-trip-none");
            return;
        }
        Trip t = new Trip();
        t.dest = dest;
        t.hours = hours;
        t.start = System.currentTimeMillis();
        t.end = t.start + hours * 3_600_000L;
        t.seed = new Random().nextLong();
        t.power = sel.power();
        t.aKills = sel.aKills();
        t.aYield = sel.aYield();
        t.aSafe = sel.aSafe();
        t.aCoinMob = sel.aCoinMob();
        t.aLootMob = sel.aLootMob();
        t.aXp = sel.aXp();
        t.copies.putAll(sel.copies());
        ex.roster().setTrip(uuid, t);
        ex.roster().save(uuid);
        msg.send(player, "ex-trip-started", "dest", name(dest), "time", timeText(t.end - t.start));
    }

    public void collect(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (ex.isIn(uuid)) {
            return;
        }
        Trip t = trip(uuid);
        if (t == null) {
            return;
        }
        if (System.currentTimeMillis() < t.end) {
            msg.send(player, "ex-trip-not-ready", "time", timeText(t.end - System.currentTimeMillis()));
            return;
        }
        Outcome o = outcome(uuid, t);
        db.begin();
        try {
            db.addCoins(uuid, o.totalCoins());
            db.setMetaLong("ex_t2_paid_" + uuid, t.start);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Reise abholen fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition", o.totalCoins(),
                "Begleiterreise " + name(t.dest) + " (" + t.hours + " Std., " + o.kills() + " Kreaturen)");
        // Erfahrung fuer alle Mitreisenden (deutlich weniger als im Level), Verletzungen
        long until = System.currentTimeMillis() + (long) (Math.max(0, cfgD("injury-hours", 12)) * 3_600_000L);
        for (Map.Entry<Companion, List<Integer>> e : t.copies.entrySet()) {
            for (int idx : e.getValue()) {
                int before = ex.copyLevel(uuid, e.getKey(), idx);
                ex.roster().addKills(uuid, e.getKey(), idx, o.xpEach());
                int after = ex.copyLevel(uuid, e.getKey(), idx);
                if (after > before) {
                    msg.send(player, "ex-comp-levelup", "name", copyName(e.getKey(), idx), "level", after);
                }
            }
        }
        for (String s : o.hurt()) {
            String[] p = s.split(":");
            Companion c = Companion.byKey(p[0]);
            if (c != null) {
                ex.roster().setInjured(uuid, c, Integer.parseInt(p[1]), until);
            }
        }
        ex.roster().setTrip(uuid, null);
        ex.roster().save(uuid);
        msg.send(player, "ex-trip-collected", "dest", name(t.dest), "coins", o.totalCoins(), "kills", o.kills());
        if (o.coinMobs() > 0) {
            msg.send(player, "ex-trip-event-coin", "count", o.coinMobs(), "coins", o.coinMobCoins());
        }
        int m = milestone(t.dest);
        for (int i = 0; i < o.lootMobs(); i++) {
            LootRewards loot = new LootRewards(plugin);
            LootRewards.Result res = loot.roll(player, m, loot.coinTier(m));
            if (res != null) {
                db.logTx(uuid, player.getName(), "expedition", 0, "Loot-Mob (Reise) Level " + m + ": " + res.text());
                msg.send(player, "ex-trip-event-loot", "reward", res.text());
            }
        }
        if (!o.hurt().isEmpty()) {
            msg.send(player, "ex-trip-injured", "count", o.hurt().size());
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }
}
