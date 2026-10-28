package de.coinshop;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Tägliche Aufgaben. Jeden Tag (zusammen mit dem Shop-Wechsel um 12:00 Uhr) gibt es für ALLE Spieler
 * dieselben Aufgaben, z. B. "Baue 300 Blöcke ab". Gemessen wird mit den normalen Minecraft-Statistiken:
 * Beim ersten Mal, dass das Plugin einen Spieler an diesem Tag sieht, wird der Stand notiert; geschafft
 * ist eine Aufgabe, wenn der Wert um die Zielmenge gestiegen ist.
 *
 * Welche Aufgaben es gibt, steht in der config.yml unter "quests.pool". Das Plugin würfelt daraus
 * jeden Tag (für alle gleich) 3 leichte und 3 schwere Aufgaben aus.
 */
public final class QuestManager {

    /** Eine Aufgabe von heute. idx = Platz 0-2 = leichte, 3-5 = schwere Aufgaben */
    public record Quest(int idx, String id, String name, String unit, String stat, Material icon,
                        long amount, long reward, boolean hard) { }

    /** Fortschritt eines Spielers bei einer Aufgabe. */
    public record Progress(Quest quest, long current, boolean done) { }

    /** Eine übernommene (gestern oder früher nicht geschaffte) Aufgabe. */
    public record Carried(Quest quest, String day, long current, boolean done) { }

    /** Platz für den Eintrag "Bonus für alle Aufgaben" in der Datenbank (damit er nur einmal ausgezahlt wird). */
    private static final int BONUS_IDX = 99;

    private final CoinShopPlugin plugin;
    private String cachedDay = "";
    private List<Quest> cachedQuests = new ArrayList<>();
    private final Map<String, List<Quest>> pastCache = new HashMap<>();
    private final Map<UUID, List<Carried>> carriedCache = new HashMap<>();

    /** Aufgaben eines früheren Tages (der Zufall hängt nur vom Datum ab, also lassen sie sich neu berechnen). */
    private synchronized List<Quest> questsOf(String day) {
        if (day.equals(dayKey())) {
            return today();
        }
        List<Quest> list = pastCache.get(day);
        if (list == null) {
            if (pastCache.size() > 40) {
                pastCache.clear();
            }
            list = draw(day);
            pastCache.put(day, list);
        }
        return list;
    }

    /** Offene übernommene Aufgaben dieses Spielers (Stand der letzten Prüfung). */
    public List<Carried> carried(UUID uuid) {
        return carriedCache.getOrDefault(uuid, List.of());
    }

    private long currentFor(Player player, Quest quest, long baseline, Map<String, Long> values) {
        UUID uuid = player.getUniqueId();
        long current = Math.max(0, values.getOrDefault(quest.stat(), 0L) - baseline);
        double mascotMult = plugin.mascot().taskMultiplier(uuid, quest.stat()); // Maskottchen: passt es zur Aufgabe?
        if (mascotMult > 1.0) {
            current = Math.round(current * mascotMult);
        }
        double titleBoost = plugin.titles().effect(uuid, "task-boost", quest.stat()); // Titel-Wirkung
        if (titleBoost > 0) {
            current += (long) Math.floor(current * titleBoost / 100.0);
        }
        return current;
    }

    public QuestManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("quests.enabled", true);
    }

    public long bonusAll() {
        return Math.max(0, plugin.getConfig().getLong("quests.bonus-all", 100));
    }

    /** Der "Aufgaben-Tag": wechselt zur selben Uhrzeit wie der Shop. */
    public String dayKey() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return now.minusHours(plugin.serverShop().resetHour()).toLocalDate().toString();
    }

    /** Die Aufgaben von heute (für alle gleich). */
    public synchronized List<Quest> today() {
        String day = dayKey();
        if (!day.equals(cachedDay)) {
            cachedQuests = draw(day);
            cachedDay = day;
            try {
                plugin.database().pruneQuests(day);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Alte Aufgaben konnten nicht aufgeräumt werden: " + e);
            }
        }
        return cachedQuests;
    }

    /** Würfelt die Aufgaben eines Tages aus. Der Zufall hängt nur vom Datum ab - also für alle gleich. */
    private List<Quest> draw(String day) {
        List<Quest> result = new ArrayList<>();
        ConfigurationSection pool = plugin.getConfig().getConfigurationSection("quests.pool");
        if (pool == null) {
            return result;
        }
        Random random = new Random(day.hashCode() * 2654435761L + 12345L);
        List<String> ids = new ArrayList<>(pool.getKeys(false));
        Collections.shuffle(ids, random);
        int easy = Math.max(0, plugin.getConfig().getInt("quests.easy-count", 3));
        int hard = Math.max(0, plugin.getConfig().getInt("quests.hard-count", 3));
        double hardAmount = Math.max(1.0, plugin.getConfig().getDouble("quests.hard-amount", 1.5));
        double hardReward = Math.max(1.0, plugin.getConfig().getDouble("quests.hard-reward", 2.2));
        java.util.Set<String> usedStats = new java.util.HashSet<>();

        for (String id : ids) {
            if (result.size() >= easy + hard) {
                break;
            }
            ConfigurationSection s = pool.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String stat = s.getString("stat", id);
            if (stat.equals("brewed") && plugin.timeline().netherLocked()) {
                continue; // Brauaufgaben gibt es erst, wenn der Nether offen ist (Netherwarze)
            }
            if (!usedStats.add(stat)) {
                continue; // nicht zwei Aufgaben zum selben Thema
            }
            boolean isHard = result.size() >= easy;
            List<Long> amount = s.getLongList("amount");
            List<Long> reward = s.getLongList("reward");
            long aMin = amount.size() > 0 ? amount.get(0) : 100;
            long aMax = amount.size() > 1 ? amount.get(1) : aMin;
            long rMin = reward.size() > 0 ? reward.get(0) : 20;
            long rMax = reward.size() > 1 ? reward.get(1) : rMin;
            // leicht: untere 35 % der Spanne; schwer: obere 40 % der Spanne, dazu mehr Menge und deutlich mehr Belohnung
            double t = isHard ? 0.6 + random.nextDouble() * 0.4 : random.nextDouble() * 0.35;
            double aMul = isHard ? hardAmount : 1.0;
            double rMul = isHard ? hardReward : 1.0;
            long a = round((aMin + t * (aMax - aMin)) * aMul, aMax >= 100 ? 10 : 1);
            long r = round((rMin + t * (rMax - rMin)) * rMul, 5);
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            result.add(new Quest(result.size(), id, s.getString("name", id), s.getString("unit", ""), stat,
                    icon == null ? Material.PAPER : icon, Math.max(1, a), Math.max(0, r), isHard));
        }
        return result;
    }

    private static long round(double value, long step) {
        return Math.max(step, Math.round(value / step) * step);
    }

    // ------------------------------------------------------------------
    //  Fortschritt prüfen
    // ------------------------------------------------------------------

    /** Alle 20 Sekunden für jeden Online-Spieler. */
    public void tickAll() {
        if (!enabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.expeditions().isIn(player.getUniqueId())) {
                continue; // Was in der Expedition passiert, zaehlt nicht
            }
            try {
                check(player);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Aufgaben von " + player.getName() + " konnten nicht geprüft werden: " + e);
            }
        }
    }

    /**
     * Berechnet den Fortschritt, legt fehlende Startwerte an und zahlt Belohnungen für gerade
     * geschaffte Aufgaben aus.
     */
    public List<Progress> check(Player player) {
        List<Progress> result = new ArrayList<>();
        if (!enabled() || plugin.expeditions().isIn(player.getUniqueId())) {
            return result;
        }
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        String day = dayKey();
        List<Quest> quests = today();
        if (quests.isEmpty()) {
            return result;
        }

        Map<Integer, Database.QuestRow> rows = new HashMap<>();
        for (Database.QuestRow row : db.getQuests(uuid, day)) {
            rows.put(row.idx(), row);
        }
        Map<String, Long> values = plugin.rankings().statValues(player);

        boolean allDone = true;
        for (Quest quest : quests) {
            Database.QuestRow row = rows.get(quest.idx());
            if (row == null) {
                long start = values.getOrDefault(quest.stat(), 0L);
                db.insertQuest(uuid, day, quest.idx(), start);
                row = new Database.QuestRow(quest.idx(), start, false);
            }
            long current = currentFor(player, quest, row.baseline(), values);
            boolean done = row.done();
            if (!done && current >= quest.amount()) {
                done = complete(player, day, quest);
            }
            if (!done) {
                allDone = false;
            }
            result.add(new Progress(quest, done ? quest.amount() : current, done));
        }

        if (allDone) {
            payBonus(player, day);
        }

        // Nicht geschaffte Aufgaben von früheren Tagen bleiben bestehen (Belohnung ja, aber kein Alle-geschafft-Bonus)
        List<Carried> carriedNow = new ArrayList<>();
        int carryMax = Math.max(0, plugin.getConfig().getInt("quests.carry-max", 6));
        if (carryMax > 0) {
            for (Database.OldQuestRow old : db.getOpenOldQuests(uuid, day, carryMax)) {
                Quest quest = null;
                for (Quest q : questsOf(old.day())) {
                    if (q.idx() == old.idx()) {
                        quest = q;
                    }
                }
                if (quest == null) {
                    continue;
                }
                long current = currentFor(player, quest, old.baseline(), values);
                boolean done = false;
                if (current >= quest.amount()) {
                    done = complete(player, old.day(), quest);
                }
                if (!done) {
                    carriedNow.add(new Carried(quest, old.day(), current, false));
                }
            }
        }
        carriedCache.put(uuid, carriedNow);
        updatePin(player, result);
        return result;
    }

    // ------------------------------------------------------------------
    //  Angeheftete Aufgabe (dauerhafte Anzeige als Leiste oben)
    // ------------------------------------------------------------------

    private final Map<UUID, org.bukkit.boss.BossBar> pinBars = new HashMap<>();

    private String pinKey(UUID uuid) {
        return "quest_pin_" + uuid;
    }

    /** Welche Aufgabe (Platz) ist heute angeheftet? -1 = keine. */
    public int pinned(UUID uuid) {
        long v = plugin.database().getMetaLong(pinKey(uuid), 0);
        if (v <= 0) {
            return -1;
        }
        long dayNr = v / 100;
        int idx = (int) (v % 100) - 1;
        if (dayNr != java.time.LocalDate.parse(dayKey()).toEpochDay()) {
            return -1; // gestern angeheftet: gilt nicht mehr
        }
        return idx;
    }

    /** Heftet eine Aufgabe an; wer dieselbe nochmal wählt, löst sie wieder. */
    public void togglePin(Player player, int idx) {
        UUID uuid = player.getUniqueId();
        if (pinned(uuid) == idx) {
            plugin.database().setMetaLong(pinKey(uuid), 0);
            hidePin(uuid);
            player.sendMessage(net.kyori.adventure.text.Component.text("Die Aufgaben-Anzeige ist aus.",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return;
        }
        long dayNr = java.time.LocalDate.parse(dayKey()).toEpochDay();
        plugin.database().setMetaLong(pinKey(uuid), dayNr * 100 + idx + 1);
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "Diese Aufgabe wird dir jetzt dauerhaft oben angezeigt.",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
        check(player);
    }

    public void hideAll() {
        for (org.bukkit.boss.BossBar bar : pinBars.values()) {
            bar.removeAll();
        }
        pinBars.clear();
    }

    public void hidePin(UUID uuid) {
        org.bukkit.boss.BossBar bar = pinBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void updatePin(Player player, List<Progress> progress) {
        UUID uuid = player.getUniqueId();
        int idx = pinned(uuid);
        if (idx < 0) {
            hidePin(uuid);
            return;
        }
        Progress p = null;
        for (Progress candidate : progress) {
            if (candidate.quest().idx() == idx) {
                p = candidate;
            }
        }
        if (p == null) {
            hidePin(uuid);
            return;
        }
        double fraction = p.quest().amount() <= 0 ? 1.0
                : Math.max(0.0, Math.min(1.0, (double) p.current() / p.quest().amount()));
        String text = p.done()
                ? "\u2714 " + p.quest().name() + " geschafft!"
                : p.quest().name() + ": " + Messages.number(p.current()) + " / " + amountText(p.quest());
        org.bukkit.boss.BossBar bar = pinBars.get(uuid);
        if (bar == null) {
            bar = org.bukkit.Bukkit.createBossBar(text, org.bukkit.boss.BarColor.YELLOW, org.bukkit.boss.BarStyle.SEGMENTED_10);
            pinBars.put(uuid, bar);
        }
        bar.setTitle(text);
        bar.setColor(p.done() ? org.bukkit.boss.BarColor.GREEN : org.bukkit.boss.BarColor.YELLOW);
        bar.setProgress(fraction);
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    /** Alle 5 Sekunden: Anzeige der angehefteten Aufgabe aktuell halten (in Expeditionen ausgeblendet). */
    public void pinTick() {
        if (!enabled()) {
            return;
        }
        pinBars.keySet().removeIf(id -> {
            if (plugin.getServer().getPlayer(id) == null) {
                pinBars.get(id).removeAll();
                return true;
            }
            return false;
        });
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (plugin.expeditions().isIn(uuid)) {
                hidePin(uuid);
                continue;
            }
            if (pinned(uuid) < 0) {
                hidePin(uuid);
                continue;
            }
            try {
                check(player);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Angeheftete Aufgabe von " + player.getName() + ": " + e);
            }
        }
    }

    /** Zahlt die Belohnung einer Aufgabe. Gibt true zurück, wenn die Aufgabe jetzt als erledigt gilt. */
    private boolean complete(Player player, String day, Quest quest) {
        if (plugin.timeline().previewFor(player)) {
            return false; // Testphase: nichts wird gewertet
        }
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        long reward;
        db.begin();
        try {
            if (!db.markQuestDone(uuid, day, quest.idx())) {
                db.rollback();
                return true; // war schon erledigt
            }
            reward = plugin.licenses().boost(uuid, "aufgaben", quest.reward());
            reward = Math.round(reward * plugin.playtime().factor(uuid)); // Spielzeit-Konto
            reward += Math.round(reward * plugin.mascot().questPercent(uuid) / 100.0); // Maskottchen-Bonus
            if (reward > 0) {
                db.addCoins(uuid, reward);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().warning("Aufgaben-Belohnung fehlgeschlagen: " + e);
            return false;
        }
        db.addStat(uuid, "quests_done", 1);
        db.logTx(uuid, player.getName(), "aufgabe", reward, quest.id());
        plugin.messages().send(player, "quest-complete", "name", quest.name(), "coins", reward);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        return true;
    }

    /** Bonus für "alle Aufgaben des Tages geschafft" (nur einmal pro Tag). */
    private void payBonus(Player player, String day) {
        if (plugin.timeline().previewFor(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long bonus = bonusAll() + Math.round(bonusAll() * plugin.mascot().questPercent(uuid) / 100.0);
        Database db = plugin.database();
        db.begin();
        try {
            db.insertQuest(uuid, day, BONUS_IDX, 0);
            if (!db.markQuestDone(uuid, day, BONUS_IDX)) {
                db.rollback();
                return;
            }
            if (bonus > 0) {
                db.addCoins(uuid, bonus);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().warning("Aufgaben-Bonus fehlgeschlagen: " + e);
            return;
        }
        db.logTx(uuid, player.getName(), "aufgaben-bonus", bonus, day);
        plugin.messages().send(player, "quest-all-done", "coins", bonus);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.mysteryBoxes().give(player, 1, "alle Tagesaufgaben geschafft"); // einfache Mystery Box
    }

    /** Wie viele Aufgaben von heute sind schon erledigt? (Ohne neu zu berechnen - für Anzeigen.) */
    public int doneCount(UUID uuid) {
        int done = 0;
        int total = today().size();
        for (Database.QuestRow row : plugin.database().getQuests(uuid, dayKey())) {
            if (row.idx() < total && row.done()) {
                done++;
            }
        }
        return done;
    }

    public int total() {
        return enabled() ? today().size() : 0;
    }

    /** "Blöcke abbauen: 300 Blöcke" als Text für die Anzeige. */
    public static String amountText(Quest q) {
        String unit = q.unit() == null || q.unit().isEmpty() ? "" : " " + q.unit();
        return Messages.number(q.amount()) + unit;
    }

    public static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
