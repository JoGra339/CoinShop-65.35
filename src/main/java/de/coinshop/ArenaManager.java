package de.coinshop;

import de.coinshop.CombatSim.Fighter;
import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Untergrund-Kampfarenen: Jedes Schwarzmarkt-Revier hat eine. Zuerst hält sie ein NPC. Wer ihn besiegt, besitzt die Arena
 * und stationiert bis zu 5 Begleiter als Verteidiger (mindestens 1). Andere Spieler können sie herausfordern; verliert der
 * Besitzer, gehört die Arena dem Sieger. Wer eine Arena bis zum nächsten Shopwechsel hält, bekommt einen Tagesertrag, der mit
 * jedem abgewehrten Herausforderer steigt. Die Kämpfe werden berechnet (die Begleiter werden nicht verletzt).
 */
public final class ArenaManager {

    /** Eine Arena. owner == null bedeutet: der NPC hält sie. */
    public static final class Arena {
        public final int site;
        public UUID owner;
        public String ownerName = "";
        public final List<String> station = new ArrayList<>();
        public long period;
        public int defendedPeriod;
        public int defendedTotal;

        Arena(int site) {
            this.site = site;
        }
    }

    private static final CompanionTypes.MobType[] SITE_TYPES = {
            CompanionTypes.MobType.ERDE, CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.FEUER, CompanionTypes.MobType.UNTOTE,
            CompanionTypes.MobType.WASSER, CompanionTypes.MobType.EIS, CompanionTypes.MobType.LUFT};
    private static final String[] NPC_NAMES = {"Türsteher", "Schläger", "Leibwächter", "Wachhund", "Eintreiber", "Keiler", "Schatten"};

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;
    private final File file;
    private final Map<Integer, Arena> arenas = new HashMap<>();
    private final Map<UUID, List<String>> notes = new HashMap<>();
    private final Map<String, Long> cooldown = new HashMap<>();
    private long nextRun;

    ArenaManager(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    private int cfgI(String key, int def) {
        return plugin.getConfig().getInt("expedition.arena." + key, def);
    }

    private void say(Player p, String text) {
        plugin.messages().send(p, "ex-arena-msg", "text", text);
    }

    private long resetNo() {
        return plugin.database().getMetaLong("shop_reset_no", 0);
    }

    public int sites() {
        return DrugTrade.SITES;
    }

    public int maxStation() {
        return Math.max(1, Math.min(10, cfgI("max-stationed", 5)));
    }

    public String siteName(int site) {
        return plugin.drugs() == null ? "Revier " + (site + 1) : plugin.drugs().siteName(site);
    }

    /** Eine Arena ist erst offen, wenn das zugehörige Revier freigekauft ist (Revier 1 gehört zur Lizenz Schmuggler). */
    public boolean siteUnlocked(UUID uuid, int site) {
        return allowed(uuid) && plugin.reviere().isUnlocked(uuid, site + 1);
    }

    public Arena arena(int site) {
        return arenas.computeIfAbsent(site, Arena::new);
    }

    public boolean allowed(UUID uuid) {
        return plugin.licenses().has(uuid, "schmuggler");
    }

    // --- Speichern -------------------------------------------------------

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("arenas");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    int site = Integer.parseInt(key);
                    ConfigurationSection s = sec.getConfigurationSection(key);
                    if (s == null || site < 0 || site >= sites()) {
                        continue;
                    }
                    Arena a = new Arena(site);
                    String o = s.getString("owner", "");
                    a.owner = o.isEmpty() ? null : UUID.fromString(o);
                    a.ownerName = s.getString("name", "");
                    a.station.addAll(s.getStringList("station"));
                    a.period = s.getLong("period", 0);
                    a.defendedPeriod = s.getInt("defended-period", 0);
                    a.defendedTotal = s.getInt("defended-total", 0);
                    arenas.put(site, a);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("arenas.yml: Eintrag " + key + " übersprungen: " + e.getMessage());
                }
            }
        }
        ConfigurationSection ns = y.getConfigurationSection("notes");
        if (ns != null) {
            for (String key : ns.getKeys(false)) {
                try {
                    notes.put(UUID.fromString(key), new ArrayList<>(ns.getStringList(key)));
                } catch (IllegalArgumentException ignored) {
                    // kaputter Schlüssel
                }
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            String p = "arenas." + a.site + ".";
            y.set(p + "owner", a.owner == null ? "" : a.owner.toString());
            y.set(p + "name", a.ownerName);
            y.set(p + "station", a.station);
            y.set(p + "period", a.period);
            y.set(p + "defended-period", a.defendedPeriod);
            y.set(p + "defended-total", a.defendedTotal);
        }
        for (Map.Entry<UUID, List<String>> e : notes.entrySet()) {
            if (!e.getValue().isEmpty()) {
                y.set("notes." + e.getKey(), e.getValue());
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("arenas.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void note(UUID uuid, String text) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            say(online, text);
            return;
        }
        notes.computeIfAbsent(uuid, k -> new ArrayList<>()).add(text);
    }

    // --- Stationierte Begleiter ------------------------------------------

    private static String key(Copy k) {
        return k.companion().key + ":" + k.index();
    }

    private Copy parse(String s) {
        int i = s.lastIndexOf(':');
        if (i < 0) {
            return null;
        }
        Companion c = Companion.byKey(s.substring(0, i));
        try {
            return c == null ? null : new Copy(c, Integer.parseInt(s.substring(i + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<Copy> stationed(Arena a) {
        List<Copy> list = new ArrayList<>();
        for (String s : a.station) {
            Copy k = parse(s);
            if (k != null) {
                list.add(k);
            }
        }
        return list;
    }

    /** In welcher Arena (Name des Reviers) ist das Exemplar stationiert? null = in keiner. */
    public String stationedAt(UUID uuid, Companion c, int idx) {
        String k = c.key + ":" + idx;
        for (Arena a : arenas.values()) {
            if (uuid.equals(a.owner) && a.station.contains(k)) {
                return siteName(a.site);
            }
        }
        return null;
    }

    public boolean isStationedHere(Arena a, Copy k) {
        return a.station.contains(key(k));
    }

    /** Entfernt Exemplare, die es nicht mehr gibt. Bleibt keiner übrig, fällt die Arena zurück an den NPC. */
    private void clean(Arena a) {
        if (a.owner == null) {
            return;
        }
        boolean changed = a.station.removeIf(s -> {
            Copy k = parse(s);
            return k == null || !ex.exists(a.owner, k.companion(), k.index());
        });
        if (a.station.isEmpty()) {
            UUID old = a.owner;
            a.owner = null;
            a.ownerName = "";
            a.defendedPeriod = 0;
            note(old, "Deine Arena " + siteName(a.site) + " hatte keine Verteidiger mehr und ist an den NPC zurückgefallen.");
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public List<Fighter> defenders(Arena a) {
        return defenders(a, List.of());
    }

    public List<Fighter> defenders(Arena a, List<Fighter> foes) {
        clean(a);
        List<Fighter> list = new ArrayList<>();
        if (a.owner == null) {
            return npcTeam(a.site, foes);
        }
        for (Copy k : stationed(a)) {
            if (!ex.exists(a.owner, k.companion(), k.index()) || ex.guardUnfit(a.owner, k.companion(), k.index()) != null) {
                continue; // verletzt, in Training, auf Reise usw.: kann die Arena gerade nicht verteidigen
            }
            list.add(CombatSim.of(ex, a.owner, k));
        }
        list.sort((x, y) -> Double.compare(y.hp() * y.atk(), x.hp() * x.atk()));
        return list;
    }

    public List<Fighter> npcTeam(int site) {
        return npcTeam(site, List.of());
    }

    /** Taktisch zusammengestellte Gruppe des Arena-NPCs, abgestimmt auf die Typen des Herausforderers. */
    public List<Fighter> npcTeam(int site, List<Fighter> foes) {
        int level = Math.max(1, cfgI("npc-base-level", 8) + site * Math.max(1, cfgI("npc-level-step", 7)));
        int stars = Math.min(7, 2 + site / 2);
        int size = Math.min(maxStation(), 3 + site / 3);
        CompanionTypes.MobType type = SITE_TYPES[site % SITE_TYPES.length];
        return CombatSim.npcTeam(ex, NPC_NAMES[site % NPC_NAMES.length], type, size, level, stars, 1.0 + site * 0.03, foes);
    }

    // --- Tagesertrag -----------------------------------------------------

    public long dailyPay(Arena a) {
        double base = Math.max(0, cfgI("daily-base", 300)) * (1.0 + 0.25 * a.site);
        return Math.round(base + (double) Math.max(0, cfgI("per-defense", 150)) * a.defendedPeriod);
    }

    /** Läuft jede Sekunde mit; arbeitet nur einmal pro Minute. */
    public void tick() {
        long now = System.currentTimeMillis();
        if (now < nextRun) {
            return;
        }
        nextRun = now + 60_000L;
        long cur = resetNo();
        boolean changed = false;
        for (Arena a : new ArrayList<>(arenas.values())) {
            clean(a);
            if (a.owner == null) {
                continue;
            }
            if (a.period < cur) {
                long pay = dailyPay(a);
                plugin.database().addCoins(a.owner, pay);
                plugin.database().logTx(a.owner, a.ownerName, "arena", pay, "Tagesertrag Arena " + siteName(a.site));
                note(a.owner, "Deine Arena " + siteName(a.site) + " hat bis zum Shopwechsel gehalten: Tagesertrag " + pay + " Coins ("
                        + a.defendedPeriod + " abgewehrte Herausforderer).");
                a.period = cur;
                a.defendedPeriod = 0;
                changed = true;
            }
        }
        if (changed) {
            save();
        }
        List<UUID> delivered = new ArrayList<>();
        for (Map.Entry<UUID, List<String>> e : notes.entrySet()) {
            Player online = Bukkit.getPlayer(e.getKey());
            if (online != null) {
                for (String text : e.getValue()) {
                    say(online, text);
                }
                delivered.add(e.getKey());
            }
        }
        if (!delivered.isEmpty()) {
            for (UUID id : delivered) {
                notes.remove(id);
            }
            save();
        }
    }

    // --- Herausfordern ---------------------------------------------------

    public List<Copy> challengerTeam(UUID uuid) {
        List<Copy> deck = ex.duel().deck(uuid);
        return new ArrayList<>(deck.subList(0, Math.min(deck.size(), maxStation())));
    }

    private final java.util.Set<UUID> formed = new java.util.HashSet<>();

    public void challenge(Player p, int site) {
        UUID uuid = p.getUniqueId();
        Arena a = arena(site);
        clean(a);
        if (!allowed(uuid)) {
            plugin.messages().send(p, "drug-license");
            return;
        }
        if (!siteUnlocked(uuid, site)) {
            say(p, "Diese Arena ist gesperrt: Kaufe zuerst das Revier „" + siteName(site) + "“ frei.");
            return;
        }
        if (!ex.hasLicense(p)) {
            plugin.messages().send(p, "ex-locked");
            return;
        }
        if (ex.isIn(uuid)) {
            say(p, "Du bist gerade in einer Expedition.");
            return;
        }
        if (uuid.equals(a.owner)) {
            say(p, "Das ist deine eigene Arena.");
            return;
        }
        if (ex.duel().blocked(p)) {
            return;
        }
        if (ex.duel().needsPick(p, maxStation())) {
            ex.duel().pick(p, maxStation(), () -> challenge(p, site));
            return;
        }
        if (ex.live().watching(uuid)) {
            return;
        }
        List<Copy> team = challengerTeam(uuid);
        if (team.isEmpty()) {
            plugin.messages().send(p, "ex-duel-nodeck");
            return;
        }
        if (formed.remove(uuid)) {
            // Aufstellung wurde gerade bestätigt
        } else {
            new de.coinshop.gui.FormationMenu(plugin, p, maxStation(), () -> {
                formed.add(uuid);
                try {
                    challenge(p, site);
                } finally {
                    formed.remove(uuid);
                }
            }).open(p);
            return;
        }
        String ck = uuid + ":" + site;
        long wait = cooldown.getOrDefault(ck, 0L) - System.currentTimeMillis();
        if (wait > 0) {
            say(p, "Du kannst diese Arena erst in " + (wait / 1000 + 1) + " Sekunden wieder herausfordern.");
            return;
        }
        cooldown.put(ck, System.currentTimeMillis() + Math.max(0, cfgI("challenge-cooldown-seconds", 300)) * 1000L);
        List<Fighter> mine = new ArrayList<>();
        for (Copy k : team) {
            mine.add(CombatSim.of(ex, uuid, k));
        }
        List<Fighter> theirs = defenders(a, mine);
        CombatSim.Script script = CombatSim.script(mine, theirs, new Random());
        String holder = a.owner == null ? "dem NPC" : a.ownerName;
        say(p, "Du forderst die Arena " + siteName(site) + " von " + holder + " heraus (Stärke " + CombatSim.strength(mine) + " gegen "
                + CombatSim.strength(theirs) + ").");
        final UUID ownerAtStart = a.owner;
        ex.live().start(List.of(p), "Arena " + siteName(site), site, mine, theirs, script, () -> {
            if (!java.util.Objects.equals(ownerAtStart, a.owner)) {
                say(p, "Die Arena hat inzwischen den Besitzer gewechselt. Dieser Kampf zählt nicht.");
                return;
            }
            for (int i = 0; i < Math.min(5, script.log().size()); i++) {
                plugin.messages().send(p, "ex-duel-line", "text", script.log().get(i));
            }
            if (script.aWins()) {
                capture(p, a, team);
            } else {
                say(p, "Niederlage. Die Arena bleibt bei " + holder + ".");
                if (a.owner != null) {
                    a.defendedPeriod++;
                    a.defendedTotal++;
                    note(a.owner, p.getName() + " hat deine Arena " + siteName(site) + " herausgefordert und verloren. Tagesertrag jetzt "
                            + dailyPay(a) + " Coins.");
                    save();
                }
            }
        });
    }

    private void capture(Player p, Arena a, List<Copy> team) {
        UUID uuid = p.getUniqueId();
        UUID old = a.owner;
        String oldName = a.ownerName;
        a.owner = uuid;
        a.ownerName = p.getName();
        a.station.clear();
        for (Copy k : team) {
            if (a.station.size() < maxStation()) {
                a.station.add(key(k));
            }
        }
        a.period = resetNo();
        a.defendedPeriod = 0;
        save();
        say(p, "Sieg! Die Arena " + siteName(a.site) + " gehört jetzt dir. Deine " + a.station.size()
                + " stärksten Begleiter bewachen sie. Du kannst sie jederzeit austauschen (mindestens einer muss bleiben).");
        if (old != null) {
            note(old, p.getName() + " hat dir die Arena " + siteName(a.site) + " abgenommen. Deine Verteidiger sind wieder frei.");
        } else {
            long bonus = Math.max(0, cfgI("first-capture-reward", 500)) * (a.site + 1);
            Database db = plugin.database();
            if (bonus > 0 && db.getMetaLong("arena_first_" + uuid + "_" + a.site, 0) == 0) {
                db.setMetaLong("arena_first_" + uuid + "_" + a.site, 1);
                db.addCoins(uuid, bonus);
                db.logTx(uuid, p.getName(), "arena", bonus, "Arena erobert: " + siteName(a.site));
                say(p, "Eroberungsprämie: " + bonus + " Coins.");
            }
        }
        if (oldName.isEmpty() && old != null) {
            plugin.getLogger().info("Arena " + a.site + ": Besitzer ohne Namen abgelöst.");
        }
    }

    // --- Stationieren ----------------------------------------------------

    /** Stationiert ein Exemplar oder zieht es ab (mindestens eines bleibt, höchstens maxStation). */
    public void toggle(Player p, int site, Copy k) {
        Arena a = arena(site);
        UUID uuid = p.getUniqueId();
        if (!uuid.equals(a.owner)) {
            say(p, "Die Arena gehört dir nicht.");
            return;
        }
        String key = key(k);
        if (a.station.contains(key)) {
            if (a.station.size() <= 1) {
                say(p, "Mindestens ein Begleiter muss die Arena bewachen. Stationiere zuerst einen anderen.");
                return;
            }
            a.station.remove(key);
        } else {
            if (a.station.size() >= maxStation()) {
                say(p, "Es dürfen höchstens " + maxStation() + " Begleiter stationiert sein. Ziehe zuerst einen ab.");
                return;
            }
            String busy = ex.busyReason(uuid, k.companion(), k.index());
            if (busy != null) {
                plugin.messages().send(p, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busy);
                return;
            }
            a.station.add(key);
        }
        save();
    }

    /** Vom Server beim Herunterfahren. */
    public void shutdown() {
        save();
    }
}
