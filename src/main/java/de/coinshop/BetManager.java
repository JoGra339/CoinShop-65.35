package de.coinshop;

import de.coinshop.Database.Bet;
import de.coinshop.Database.Rig;
import de.coinshop.gui.BetMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Das Wettbüro (Lizenz "wetten"): Man wettet darauf, wie oft ein Spieler in dieser Woche stirbt (0 = gar nicht,
 * 5 = fünfmal oder öfter). Tode durch andere Spieler zählen nicht. Die Auswertung ist sonntags zur Reset-Uhrzeit.
 *
 * Die Quote ergibt sich aus der Todes-Geschichte des Spielers (Poisson-Verteilung) abzüglich einer Marge des Hauses.
 * Im Schwarzmarkt kann man die Quote für ein Ergebnis "türken" (manipulieren). Klappt das, sind die Quoten dafür
 * besser, und wer getürkt hat, bekommt einen Anteil vom Gewinn aller Wetten, die damit gewonnen werden.
 *
 * Merkzettel (meta): bw_UUID = Tode diese Woche, bs_UUID = Tode aller bisherigen Wochen, bf_UUID = erste Woche + 1,
 * bets_week = laufende Wochennummer, bn_/bk_/br_UUID = Meldungen für Spieler, die offline waren.
 */
public final class BetManager implements CommandExecutor, Listener {

    public static final int MAX_OUTCOME = 10;

    /** Arten von Todesnachrichten (Gruppen von Schadensarten). */
    public static final String[] CAUSES = {"Sturz", "Feuer und Lava", "Ertrinken", "Monster im Nahkampf",
            "Pfeile und Geschosse", "Explosion", "Sturz in die Leere", "Ersticken", "Hunger, Gift und Magie", "Sonstiges"};
    public static final org.bukkit.Material[] CAUSE_ICONS = {org.bukkit.Material.FEATHER, org.bukkit.Material.LAVA_BUCKET,
            org.bukkit.Material.WATER_BUCKET, org.bukkit.Material.ZOMBIE_HEAD, org.bukkit.Material.ARROW,
            org.bukkit.Material.TNT, org.bukkit.Material.ENDER_PEARL, org.bukkit.Material.GRAVEL,
            org.bukkit.Material.POISONOUS_POTATO, org.bukkit.Material.BONE};

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public BetManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        // Abgelaufene kleine Wetten jede Minute abrechnen
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireSmallBets, 1200L, 1200L);
    }

    // ------------------------------------------------------------------
    //  Einstellungen und Zugang
    // ------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("bets.enabled", true);
    }

    /** Das Wettbüro ist für alle offen. Die Wettlizenz bringt nur Vorteile (höheres Einsatzlimit). */
    public boolean unlocked(Player player) {
        return enabled();
    }

    public boolean allowed(Player player) {
        if (!unlocked(player)) {
            plugin.messages().send(player, "bet-locked");
            return false;
        }
        if (plugin.timeline().previewFor(player)) {
            plugin.messages().send(player, "bet-preview");
            return false;
        }
        return true;
    }

    /** Hat sich der Spieler vom Wettbüro abgemeldet? Dann kann niemand auf ihn wetten, und er kann selbst nicht wetten. */
    public boolean optedOut(UUID uuid) {
        return plugin.database().getMetaLong("bo_" + uuid, 0) > 0;
    }

    /** Meldet ab oder an. Mit offenen Wetten (auf ihn oder von ihm) ist die Abmeldung gesperrt. */
    public void toggleOptOut(Player player) {
        UUID uuid = player.getUniqueId();
        Database db = plugin.database();
        if (optedOut(uuid)) {
            db.setMetaLong("bo_" + uuid, 0);
            plugin.messages().send(player, "bet-optin");
            return;
        }
        for (Bet bet : db.getBets()) {
            if (bet.target().equals(uuid) || bet.bettor().equals(uuid)) {
                plugin.messages().send(player, "bet-optout-blocked");
                return;
            }
        }
        for (Database.SmallBet bet : db.getSmallBets()) {
            if (bet.target().equals(uuid) || bet.bettor().equals(uuid)) {
                plugin.messages().send(player, "bet-optout-blocked");
                return;
            }
        }
        db.setMetaLong("bo_" + uuid, 1);
        plugin.messages().send(player, "bet-optout");
    }

    public long minStake() {
        return Math.max(1, plugin.getConfig().getLong("bets.min-stake", 50));
    }

    public long maxStake() {
        return Math.max(minStake(), plugin.getConfig().getLong("bets.max-stake", 5000));
    }

    /** Höchster Einsatz für diesen Spieler (Wettlizenz erhöht das Limit; schwarze Wetten haben ein eigenes, höheres). */
    public long maxStakeFor(UUID uuid, boolean black) {
        long base = black ? Math.max(maxStake(), plugin.getConfig().getLong("bets.black-max-stake", 15000)) : maxStake();
        double factor = plugin.licenses().has(uuid, "wetten")
                ? Math.max(1.0, plugin.getConfig().getDouble("bets.license-stake-factor", 2.0)) : 1.0;
        return Math.round(base * factor);
    }

    // ---- Schwarze Wetten (Schwarzmarkt) ----
    private final java.util.Set<UUID> blackSession = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void setBlackSession(UUID uuid, boolean on) {
        if (on) {
            blackSession.add(uuid);
        } else {
            blackSession.remove(uuid);
        }
    }

    public boolean isBlackSession(UUID uuid) {
        return blackSession.contains(uuid);
    }

    /** Faktor auf die Quote, den der Spieler gerade im Menü sieht (schwarze Wette: höher). */
    public double sessionFactor(UUID uuid) {
        return isBlackSession(uuid) ? blackOddsFactor() : 1.0;
    }

    public double blackOddsFactor() {
        return Math.max(1.0, plugin.getConfig().getDouble("bets.black-odds-factor", 1.6));
    }

    public int blackFeePercent() {
        return Math.max(0, plugin.getConfig().getInt("bets.black-fee-percent", 8));
    }

    public int blackRaidChance() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("bets.black-raid-percent", 5)));
    }

    public int feePercent() {
        return Math.max(0, plugin.getConfig().getInt("bets.fee-percent", 5));
    }

    public int maxOpenBets() {
        return Math.max(1, plugin.getConfig().getInt("bets.max-open-bets", 6));
    }

    public long rigCost() {
        return Math.max(1, plugin.getConfig().getLong("bets.rig.cost", 4000));
    }

    public int rigSharePercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("bets.rig.share-percent", 30)));
    }

    public double rigFactor() {
        return Math.max(1.0, plugin.getConfig().getDouble("bets.rig.odds-factor", 1.5));
    }

    public int rigLimit() {
        return Math.max(1, plugin.getConfig().getInt("bets.rig.max-per-week", 2));
    }

    public int rigChance(UUID uuid) {
        double chance = plugin.getConfig().getDouble("bets.rig.chance-percent", 45)
                + plugin.licenses().value(uuid, "schmuggler") + plugin.licenses().value(uuid, "buchmacher");
        return (int) Math.max(1, Math.min(90, Math.round(chance)));
    }

    public static String outcomeText(int outcome) {
        if (outcome <= 0) {
            return "stirbt gar nicht";
        }
        if (outcome >= MAX_OUTCOME) {
            return MAX_OUTCOME + "-mal oder öfter";
        }
        return outcome + "-mal";
    }

    // ------------------------------------------------------------------
    //  Zeit
    // ------------------------------------------------------------------

    private ZonedDateTime nextEvaluation() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        ZonedDateTime t = now.withHour(plugin.serverShop().resetHour()).withMinute(0).withSecond(0).withNano(0);
        for (int i = 0; i < 9; i++) {
            if (t.getDayOfWeek() == DayOfWeek.SUNDAY && t.isAfter(now)) {
                return t;
            }
            t = t.plusDays(1);
        }
        return t;
    }

    public double hoursLeft() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return Math.max(0, Duration.between(now, nextEvaluation()).toMinutes() / 60.0);
    }

    /** Wetten (und Quote türken) gehen nur kurz nach der Auswertung: von Sonntag 12:00 bis Montag 12:00 für die kommende Woche. */
    public boolean closed() {
        long evalAt = plugin.database().getMetaLong("bets_eval_at", 0);
        long open = Math.max(1, plugin.getConfig().getLong("bets.open-hours", 24)) * 3_600_000L;
        long now = System.currentTimeMillis();
        return evalAt <= 0 || now < evalAt || now >= evalAt + open;
    }

    /** Stunden, bis das Wettfenster schließt (0, wenn es zu ist). */
    public double openHoursLeft() {
        if (closed()) {
            return 0;
        }
        long evalAt = plugin.database().getMetaLong("bets_eval_at", 0);
        long open = Math.max(1, plugin.getConfig().getLong("bets.open-hours", 24)) * 3_600_000L;
        return Math.max(0, (evalAt + open - System.currentTimeMillis()) / 3_600_000.0);
    }

    // ------------------------------------------------------------------
    //  Tode zählen
    // ------------------------------------------------------------------

    /** Zählt jeden Tod, der NICHT von einem anderen Spieler verursacht wurde. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (plugin.expeditions().isIn(victim.getUniqueId())) {
            return; // Expeditions-Tode zählen nicht
        }
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return;
        }
        // Schutz gegen absichtliches Sterben: /kill zählt nie
        org.bukkit.event.entity.EntityDamageEvent last = victim.getLastDamageCause();
        if (last != null) {
            org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = last.getCause();
            if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.KILL
                    || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUICIDE) {
                return;
            }
        }
        try {
            Database db = plugin.database();
            String key = "bw_" + victim.getUniqueId();
            db.setMetaLong(key, db.getMetaLong(key, 0) + 1);
            // Zeitpunkt und Art der Todesnachricht (Wert = Art + 1): für die Prüfung auf auffällige Tode und die Auswertung
            db.setMetaLong("bx_" + victim.getUniqueId() + "_" + System.currentTimeMillis(), causeOf(last) + 1);
            resolveSmallBets(victim.getUniqueId(), causeOf(last));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Tod konnte fürs Wettbüro nicht gezählt werden: " + e);
        }
    }

    // ------------------------------------------------------------------
    //  Spielzeit der Woche (für die Rückzahlung bei zu wenig Spielzeit)
    // ------------------------------------------------------------------

    public long minPlayMillis() {
        return Math.max(0, plugin.getConfig().getLong("bets.min-play-minutes", 60)) * 60_000L;
    }

    /** Spielzeit seit der letzten Auswertung in Millisekunden (laufende Sitzung eingerechnet). */
    public long playedMillis(UUID uuid) {
        Database db = plugin.database();
        long total = db.getMetaLong("bt_" + uuid, 0);
        long joined = db.getMetaLong("bj_" + uuid, 0);
        if (joined > 0 && Bukkit.getPlayer(uuid) != null) {
            total += Math.max(0, System.currentTimeMillis() - joined);
        }
        return total;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Database db = plugin.database();
        long joined = db.getMetaLong("bj_" + uuid, 0);
        if (joined > 0) {
            db.setMetaLong("bt_" + uuid, db.getMetaLong("bt_" + uuid, 0) + Math.max(0, System.currentTimeMillis() - joined));
            db.setMetaLong("bj_" + uuid, 0);
        }
    }

    /** Ordnet die letzte Schadensart einer Art von Todesnachricht zu (Index in CAUSES). */
    static int causeOf(org.bukkit.event.entity.EntityDamageEvent last) {
        if (last == null) {
            return 9;
        }
        return switch (last.getCause().name()) {
            case "FALL", "FLY_INTO_WALL" -> 0;
            case "FIRE", "FIRE_TICK", "LAVA", "HOT_FLOOR", "CAMPFIRE" -> 1;
            case "DROWNING" -> 2;
            case "ENTITY_ATTACK", "ENTITY_SWEEP_ATTACK", "THORNS" -> 3;
            case "PROJECTILE" -> 4;
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> 5;
            case "VOID" -> 6;
            case "SUFFOCATION", "CRAMMING" -> 7;
            case "STARVATION", "POISON", "MAGIC", "WITHER", "DRAGON_BREATH" -> 8;
            default -> 9;
        };
    }

    public int deathsThisWeek(UUID uuid) {
        return (int) Math.max(0, plugin.database().getMetaLong("bw_" + uuid, 0));
    }

    private long currentWeek() {
        return plugin.database().getMetaLong("bets_week", 0);
    }

    private void ensureFirst(UUID uuid) {
        Database db = plugin.database();
        if (db.getMetaLong("bf_" + uuid, 0) <= 0) {
            db.setMetaLong("bf_" + uuid, currentWeek() + 1);
        }
    }

    /** Erwartete Tode pro Woche (geglättet mit ein paar angenommenen Wochen). */
    public double weeklyRate(UUID uuid) {
        ensureFirst(uuid);
        Database db = plugin.database();
        long weeks = Math.max(0, currentWeek() - (db.getMetaLong("bf_" + uuid, 1) - 1));
        long sum = db.getMetaLong("bs_" + uuid, 0);
        double priorRate = Math.max(0.05, plugin.getConfig().getDouble("bets.default-deaths", 1.5));
        double priorWeeks = Math.max(0.5, plugin.getConfig().getDouble("bets.prior-weeks", 2));
        return (sum + priorRate * priorWeeks) / (weeks + priorWeeks);
    }

    // ------------------------------------------------------------------
    //  Quoten
    // ------------------------------------------------------------------

    private static double poisson(double lambda, int k) {
        double p = Math.exp(-lambda);
        for (int i = 1; i <= k; i++) {
            p *= lambda / i;
        }
        return p;
    }

    private static double atLeast(double lambda, int k) {
        if (k <= 0) {
            return 1.0;
        }
        double sum = 0;
        for (int i = 0; i < k; i++) {
            sum += poisson(lambda, i);
        }
        return Math.max(0.0, 1.0 - sum);
    }

    /** Wahrscheinlichkeit, dass das Ergebnis bis zur Auswertung eintritt (berücksichtigt die bisherigen Tode). */
    public double probability(UUID target, int outcome) {
        int already = deathsThisWeek(target);
        double lambda = weeklyRate(target) * Math.min(1.0, hoursLeft() / 168.0);
        if (outcome >= MAX_OUTCOME) {
            return atLeast(lambda, Math.max(0, MAX_OUTCOME - already));
        }
        int extra = outcome - already;
        if (extra < 0) {
            return 0.0;
        }
        return poisson(lambda, extra);
    }

    /** Wahrscheinlichkeit, dass ein Tod dieses Spielers diese Art von Todesnachricht hat (Geschichte plus Grundwerte). */
    public double causeProbability(UUID target, int cat) {
        Database db = plugin.database();
        java.util.List<Double> weights = new java.util.ArrayList<>();
        double weightSum = 0;
        for (int i = 0; i < CAUSES.length; i++) {
            java.util.List<?> list = plugin.getConfig().getList("bets.cause-weights");
            double w = list != null && i < list.size() && list.get(i) instanceof Number n ? n.doubleValue() : 10.0;
            weights.add(Math.max(0.1, w));
            weightSum += Math.max(0.1, w);
        }
        double priorDeaths = Math.max(0.5, plugin.getConfig().getDouble("bets.cause-prior-deaths", 4));
        long total = 0;
        long mine = 0;
        for (int i = 0; i < CAUSES.length; i++) {
            long n = db.getMetaLong("bq_" + target + "_" + i, 0);
            total += n;
            if (i == cat) {
                mine = n;
            }
        }
        return (mine + priorDeaths * weights.get(cat) / weightSum) / (total + priorDeaths);
    }

    public int maxCauses(int outcome) {
        return Math.max(0, Math.min(outcome, plugin.getConfig().getInt("bets.max-causes", 5)));
    }

    /** Zusatz-Faktor der Quote für genannte Todesnachrichten: je mehr und je unwahrscheinlicher, desto höher. */
    public double causeMultiplier(UUID target, java.util.List<Integer> causes) {
        if (causes.isEmpty()) {
            return 1.0;
        }
        double margin = Math.max(0, Math.min(0.5, plugin.getConfig().getDouble("bets.margin", 0.10)));
        double exponent = Math.max(0.1, Math.min(1.0, plugin.getConfig().getDouble("bets.cause-exponent", 0.6)));
        double maxEach = Math.max(1.0, plugin.getConfig().getDouble("bets.cause-max-each", 6));
        double multiplier = 1.0;
        for (int cat : causes) {
            double p = Math.max(0.01, causeProbability(target, cat));
            multiplier *= Math.max(1.0, Math.min(maxEach, Math.pow((1.0 - margin) / p, exponent)));
        }
        return Math.min(multiplier, Math.max(1.0, plugin.getConfig().getDouble("bets.cause-max-multiplier", 40)));
    }

    /** Ohne Manipulation: Quote als Faktor, 0 = auf dieses Ergebnis kann man nicht (mehr) wetten. */
    public double baseOdds(UUID target, int outcome) {
        double p = probability(target, outcome);
        if (p < plugin.getConfig().getDouble("bets.min-probability", 0.0005)
                || p > plugin.getConfig().getDouble("bets.max-probability", 0.85)) {
            return 0;
        }
        double margin = Math.max(0, Math.min(0.5, plugin.getConfig().getDouble("bets.margin", 0.10)));
        double min = Math.max(1.01, plugin.getConfig().getDouble("bets.min-odds", 1.1));
        double max = Math.max(min, plugin.getConfig().getDouble("bets.max-odds", 60));
        return Math.max(min, Math.min(max, (1.0 - margin) / p));
    }

    // ------------------------------------------------------------------
    //  Wettübersicht: wie oft auf wen wie viel gewettet wurde (fließt in die Quote ein)
    // ------------------------------------------------------------------

    /** Anzahl der Wetten auf dieses Ergebnis (outcome -1 = alle Ergebnisse dieses Spielers). */
    public int countOn(UUID target, int outcome) {
        int n = 0;
        for (Bet bet : plugin.database().getBets()) {
            if (bet.target().equals(target) && (outcome < 0 || bet.outcome() == outcome)) {
                n++;
            }
        }
        return n;
    }

    /** Summe der Einsätze auf dieses Ergebnis (outcome -1 = alle Ergebnisse dieses Spielers). */
    public long stakeOn(UUID target, int outcome) {
        long sum = 0;
        for (Bet bet : plugin.database().getBets()) {
            if (bet.target().equals(target) && (outcome < 0 || bet.outcome() == outcome)) {
                sum += bet.stake();
            }
        }
        return sum;
    }

    /** Je mehr Einsätze auf einem Ergebnis liegen (im Verhältnis zu allen auf den Spieler), desto schlechter die Quote. */
    private double poolFactor(UUID target, int outcome) {
        long total = stakeOn(target, -1);
        if (total <= 0) {
            return 1.0;
        }
        double smoothing = Math.max(1, plugin.getConfig().getDouble("bets.pool-smoothing", 2000));
        double maxCut = Math.max(0, Math.min(0.9, plugin.getConfig().getDouble("bets.pool-max-cut", 0.5)));
        double share = stakeOn(target, outcome) / (total + smoothing);
        return 1.0 - maxCut * share;
    }

    /** Quote mit Wettverhalten, ohne Manipulation. 0 = keine Wette möglich. */
    public double adjustedOdds(UUID target, int outcome) {
        double base = baseOdds(target, outcome);
        if (base <= 0) {
            return 0;
        }
        double min = Math.max(1.01, plugin.getConfig().getDouble("bets.min-odds", 1.1));
        return Math.max(min, base * poolFactor(target, outcome));
    }

    public boolean isRigged(UUID target, int outcome) {
        for (Rig rig : plugin.database().getRigs()) {
            if (rig.target().equals(target) && rig.outcome() == outcome) {
                return true;
            }
        }
        return false;
    }

    /** Die Quote, die ein Spieler jetzt sieht (mit Manipulation, falls sie geklappt hat). */
    public double odds(UUID target, int outcome) {
        double base = adjustedOdds(target, outcome);
        if (base <= 0) {
            return 0;
        }
        return isRigged(target, outcome) ? base * rigFactor() : base;
    }

    /** "3,0,5" -> "Sturz, Feuer und Lava, Explosion" */
    public static String causesText(String stored) {
        if (stored == null || stored.isBlank()) {
            return "keine";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : stored.split(",")) {
            try {
                int cat = Integer.parseInt(part.trim());
                if (cat >= 0 && cat < CAUSES.length) {
                    sb.append(sb.length() > 0 ? ", " : "").append(CAUSES[cat]);
                }
            } catch (NumberFormatException ignored) {
                // überspringen
            }
        }
        return sb.length() == 0 ? "keine" : sb.toString();
    }

    public static java.util.List<Integer> parseCauses(String stored) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        if (stored != null) {
            for (String part : stored.split(",")) {
                try {
                    list.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // überspringen
                }
            }
        }
        return list;
    }

    public static String oddsText(double odds) {
        return String.format(java.util.Locale.GERMANY, "%.2f", odds);
    }

    // ------------------------------------------------------------------
    //  Wetten
    // ------------------------------------------------------------------

    public long feeFor(UUID uuid, long stake) {
        return feeFor(uuid, stake, feePercent());
    }

    public long feeFor(UUID uuid, long stake, int percent) {
        double keep = 1.0 - Math.min(100.0, plugin.licenses().value(uuid, "gebuehrenfrei")) / 100.0;
        return (long) Math.ceil(stake * percent / 100.0 * keep * plugin.events().feeFactor());
    }

    public void placeBet(Player player, UUID target, String targetName, int outcome, long stake,
                         java.util.List<Integer> causes) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (!allowed(player)) {
            return;
        }
        boolean black = isBlackSession(player.getUniqueId()) && plugin.blackMarket().unlocked(player);
        if (target.equals(player.getUniqueId())) {
            msg.send(player, "bet-self");
            return;
        }
        if (optedOut(player.getUniqueId())) {
            msg.send(player, "bet-you-optout");
            return;
        }
        if (optedOut(target)) {
            msg.send(player, "bet-target-optout", "player", targetName);
            return;
        }
        if (closed()) {
            msg.send(player, "bet-closed");
            return;
        }
        long maxAllowed = maxStakeFor(player.getUniqueId(), black);
        if (stake < minStake() || stake > maxAllowed) {
            msg.send(player, "bet-stake-range", "min", minStake(), "max", maxAllowed);
            return;
        }
        if (db.getBetsBy(player.getUniqueId()).size() >= maxOpenBets()) {
            msg.send(player, "bet-too-many", "max", maxOpenBets());
            return;
        }
        double odds = odds(target, outcome);
        if (odds <= 0) {
            msg.send(player, "bet-impossible");
            return;
        }
        if (causes.size() > maxCauses(outcome)) {
            msg.send(player, "bet-too-many-causes", "max", maxCauses(outcome));
            return;
        }
        boolean rigged = isRigged(target, outcome);
        int oddsX100 = (int) Math.round(odds * (black ? blackOddsFactor() : 1.0) * 100);
        int causeMultX100 = (int) Math.round(causeMultiplier(target, causes) * 100);
        StringBuilder causeText = new StringBuilder();
        for (int cat : causes) {
            if (cat < 0 || cat >= CAUSES.length) {
                msg.send(player, "error");
                return;
            }
            causeText.append(causeText.length() > 0 ? "," : "").append(cat);
        }
        long fee = black ? feeFor(player.getUniqueId(), stake, blackFeePercent()) : feeFor(player.getUniqueId(), stake);
        db.begin();
        try {
            if (!db.takeCoins(player.getUniqueId(), stake + fee)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(stake + fee);
            db.addBet(player.getUniqueId(), target, targetName, outcome, stake, oddsX100, rigged, causeText.toString(),
                    causeMultX100, black);
            if (black) {
                // Wettbaron: zählt nur für illegale (schwarze) Wetten
                db.setMetaLong("bst_" + player.getUniqueId(), db.getMetaLong("bst_" + player.getUniqueId(), 0) + stake);
                payBaron(fee);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Wette: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(player.getUniqueId(), player.getName(), "wette", -(stake + fee),
                "Wette auf " + targetName + ": " + outcomeText(outcome));
        setBlackSession(player.getUniqueId(), false);
        if (black) {
            msg.send(player, "bet-black-note", "chance", blackRaidChance());
        }
        msg.send(player, "bet-placed", "player", targetName, "outcome", outcomeText(outcome), "stake", stake,
                "fee", fee, "odds", oddsText(oddsX100 * (double) causeMultX100 / 10000.0),
                "win", stake * oddsX100 / 100 * causeMultX100 / 100, "causes", causesText(causeText.toString()));
    }

    // ------------------------------------------------------------------
    //  Quote türken (Schwarzmarkt)
    // ------------------------------------------------------------------

    public void rig(Player player, UUID target, String targetName, int outcome) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        boolean asBaron = isBaronSession(uuid);
        setBaronSession(uuid, false);
        if (!enabled() || plugin.timeline().previewFor(player)) {
            msg.send(player, "bet-preview");
            return;
        }
        if (asBaron) {
            baronRig(player, target, targetName, outcome);
            return;
        }
        if (!plugin.blackMarket().allowed(player)) {
            return;
        }
        if (closed()) {
            msg.send(player, "bet-closed");
            return;
        }
        if (optedOut(target)) {
            msg.send(player, "bet-target-optout", "player", targetName);
            return;
        }
        if (baseOdds(target, outcome) <= 0) {
            msg.send(player, "bet-impossible");
            return;
        }
        if (isRigged(target, outcome)) {
            msg.send(player, "bet-rig-exists");
            return;
        }
        int own = 0;
        for (Rig r : db.getRigs()) {
            if (r.rigger().equals(uuid)) {
                own++;
            }
        }
        if (own >= rigLimit()) {
            msg.send(player, "bet-rig-limit", "max", rigLimit());
            return;
        }
        long price = plugin.blackMarket().priceFor(uuid, rigCost());
        long fee = plugin.blackMarket().feeFor(uuid, price, plugin.blackMarket().feePercent());
        boolean success = random.nextInt(100) < rigChance(uuid);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price + fee)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(price + fee);
            plugin.reviere().payPate(fee);
            if (success) {
                db.addRig(uuid, target, outcome);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Quote-Türken: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "quoten-tuerken", -(price + fee),
                "Quote für " + targetName + " (" + outcomeText(outcome) + ") " + (success ? "getürkt" : "fehlgeschlagen"));
        msg.send(player, success ? "bet-rig-success" : "bet-rig-fail", "player", targetName,
                "outcome", outcomeText(outcome), "price", price, "fee", fee, "percent", rigSharePercent());
    }

    // ------------------------------------------------------------------
    //  Kleine Wetten: auf den NÄCHSTEN Tod eines (online befindlichen) Spielers
    // ------------------------------------------------------------------

    public java.util.List<Integer> smallHours() {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (Integer h : plugin.getConfig().getIntegerList("bets.small-hours")) {
            if (h != null && h > 0) {
                list.add(h);
            }
        }
        if (list.isEmpty()) {
            list.add(24);
        }
        return list;
    }

    public long smallMaxStake(UUID uuid) {
        long base = Math.max(minStake(), plugin.getConfig().getLong("bets.small-max-stake", 2000));
        double factor = plugin.licenses().has(uuid, "wetten")
                ? Math.max(1.0, plugin.getConfig().getDouble("bets.license-stake-factor", 2.0)) : 1.0;
        return Math.round(base * factor);
    }

    public int smallMaxOpen() {
        return Math.max(1, plugin.getConfig().getInt("bets.small-max-open", 5));
    }

    /** Wie viele kleine Wetten der Spieler offen hat. */
    public java.util.List<Database.SmallBet> smallBetsBy(UUID bettor) {
        java.util.List<Database.SmallBet> own = new java.util.ArrayList<>();
        for (Database.SmallBet bet : plugin.database().getSmallBets()) {
            if (bet.bettor().equals(bettor)) {
                own.add(bet);
            }
        }
        return own;
    }

    /** Chance, dass der Spieler innerhalb der Stunden stirbt (und, falls cause >= 0, an dieser Art). */
    public double smallProbability(UUID target, int hours, int cause) {
        double factor = Math.max(0.1, plugin.getConfig().getDouble("bets.small-play-factor", 1.5));
        double lambda = weeklyRate(target) * factor * hours / 168.0;
        double p = 1.0 - Math.exp(-lambda);
        if (cause >= 0 && cause < CAUSES.length) {
            p *= causeProbability(target, cause);
        }
        return p;
    }

    /** Quote (Faktor) einer kleinen Wette; mit schwarzen Wetten hat das nichts zu tun. */
    public double smallOdds(UUID target, int hours, int cause) {
        double p = Math.max(0.002, Math.min(0.9, smallProbability(target, hours, cause)));
        double margin = Math.max(0, Math.min(0.5, plugin.getConfig().getDouble("bets.small-margin", 0.15)));
        double min = Math.max(1.01, plugin.getConfig().getDouble("bets.small-min-odds", 1.2));
        double max = Math.max(min, plugin.getConfig().getDouble("bets.small-max-odds", 40));
        return Math.max(min, Math.min(max, (1.0 - margin) / p));
    }

    public void placeSmallBet(Player player, UUID target, String targetName, int hours, int cause, long stake) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!allowed(player)) {
            return;
        }
        if (target.equals(uuid)) {
            msg.send(player, "bet-self");
            return;
        }
        if (optedOut(uuid)) {
            msg.send(player, "bet-you-optout");
            return;
        }
        if (optedOut(target)) {
            msg.send(player, "bet-target-optout", "player", targetName);
            return;
        }
        if (Bukkit.getPlayer(target) == null) {
            msg.send(player, "bet-small-offline", "player", targetName);
            return;
        }
        if (!smallHours().contains(hours) || cause < -1 || cause >= CAUSES.length) {
            msg.send(player, "error");
            return;
        }
        long maxAllowed = smallMaxStake(uuid);
        if (stake < minStake() || stake > maxAllowed) {
            msg.send(player, "bet-stake-range", "min", minStake(), "max", maxAllowed);
            return;
        }
        if (smallBetsBy(uuid).size() >= smallMaxOpen()) {
            msg.send(player, "bet-small-too-many", "max", smallMaxOpen());
            return;
        }
        int weekKey = java.time.LocalDate.now().get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100
                + java.time.LocalDate.now().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String weekMeta = "bsw_" + weekKey + "_" + uuid;
        int weekMax = Math.max(1, plugin.getConfig().getInt("bets.small-max-per-week", 3));
        if (db.getMetaLong(weekMeta, 0) >= weekMax) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "Du kannst pro Woche nur " + weekMax + " kleine Wetten machen. Die Woche beginnt am Montag.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        int oddsX100 = (int) Math.round(smallOdds(target, hours, cause) * 100);
        long fee = feeFor(uuid, stake);
        long until = System.currentTimeMillis() + hours * 3_600_000L;
        db.begin();
        try {
            if (!db.takeCoins(uuid, stake + fee)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(stake + fee);
            db.addSmallBet(uuid, target, targetName, stake, oddsX100, cause, until);
            db.setMetaLong(weekMeta, db.getMetaLong(weekMeta, 0) + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der kleinen Wette: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "wette-klein", -(stake + fee), "Kleine Wette auf " + targetName);
        msg.send(player, "bet-small-placed", "player", targetName, "hours", hours,
                "cause", cause < 0 ? "egal" : CAUSES[cause], "stake", stake, "fee", fee,
                "odds", oddsText(oddsX100 / 100.0), "win", stake * oddsX100 / 100);
    }

    private void smallNotify(UUID uuid, String key, Object... pairs) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.messages().send(online, key, pairs);
        } else if (key.equals("bet-small-won")) {
            Database db = plugin.database();
            Object amount = pairs.length >= 2 ? pairs[1] : 0;
            db.setMetaLong("bn_" + uuid, db.getMetaLong("bn_" + uuid, 0) + (amount instanceof Long l ? l : 0));
        }
    }

    /** Der nächste (zählende) Tod eines Spielers entscheidet alle offenen kleinen Wetten auf ihn. */
    private void resolveSmallBets(UUID victim, int cause) {
        Database db = plugin.database();
        long now = System.currentTimeMillis();
        java.util.List<Database.SmallBet> mine = new java.util.ArrayList<>();
        for (Database.SmallBet bet : db.getSmallBets()) {
            if (bet.target().equals(victim)) {
                mine.add(bet);
            }
        }
        if (mine.isEmpty()) {
            return;
        }
        db.begin();
        try {
            for (Database.SmallBet bet : mine) {
                db.deleteSmallBet(bet.id());
                boolean win = bet.until() >= now && (bet.cause() < 0 || bet.cause() == cause);
                if (!win) {
                    smallNotify(bet.bettor(), "bet-small-lost", "player", bet.targetName(), "stake", bet.stake());
                    continue;
                }
                long gross = bet.stake() * bet.odds() / 100;
                long profit = Math.max(0, gross - bet.stake());
                long tax = plugin.stock().taxOn(bet.bettor(), profit);
                long net = gross - tax;
                db.addCoins(bet.bettor(), net);
                db.addShopAccount(-gross);
                db.logTx(bet.bettor(), db.getName(bet.bettor()), "wette-klein-gewinn", net,
                        "Kleine Wette auf " + bet.targetName());
                smallNotify(bet.bettor(), "bet-small-won", "player", bet.targetName(), "amount", net);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Abrechnung kleiner Wetten: " + e.getMessage());
        }
    }

    /** Läuft jede Minute: Wetten, deren Zeit abgelaufen ist, sind verloren. */
    private void expireSmallBets() {
        try {
            Database db = plugin.database();
            long now = System.currentTimeMillis();
            for (Database.SmallBet bet : db.getSmallBets()) {
                if (bet.until() < now) {
                    db.deleteSmallBet(bet.id());
                    smallNotify(bet.bettor(), "bet-small-expired", "player", bet.targetName(), "stake", bet.stake());
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Kleine Wetten konnten nicht abgelaufen werden: " + e);
        }
    }

    // ------------------------------------------------------------------
    //  Wettmanipulation (Schwarzmarkt): ein Tod mehr oder weniger im Ergebnis der Woche
    // ------------------------------------------------------------------

    public long manipCost() {
        return Math.max(1, plugin.getConfig().getLong("bets.manipulation.cost", 6000));
    }

    public int manipChance(UUID uuid) {
        double chance = plugin.getConfig().getDouble("bets.manipulation.chance-percent", 35)
                + plugin.licenses().value(uuid, "buchmacher");
        return (int) Math.max(1, Math.min(85, Math.round(chance)));
    }

    public int manipCaughtPercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("bets.manipulation.caught-percent", 30)));
    }

    public int manipMax() {
        return Math.max(1, plugin.getConfig().getInt("bets.manipulation.max-per-player", 1));
    }

    /** Bisherige (geheime) Verschiebung des Ergebnisses dieses Spielers. */
    public long manipDelta(UUID target) {
        return plugin.database().getMetaLong("bmx_" + target, 0);
    }

    public void manipulate(Player player, UUID target, String targetName, int direction) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!enabled() || plugin.timeline().previewFor(player)) {
            msg.send(player, "bet-preview");
            return;
        }
        if (!plugin.blackMarket().allowed(player)) {
            return;
        }
        if (target.equals(uuid)) {
            msg.send(player, "bet-self");
            return;
        }
        if (optedOut(target)) {
            msg.send(player, "bet-target-optout", "player", targetName);
            return;
        }
        if (!closed()) {
            msg.send(player, "bet-manip-window");
            return;
        }
        double minHours = Math.max(0, plugin.getConfig().getDouble("bets.manipulation.min-hours-before", 2));
        if (hoursLeft() < minHours) {
            msg.send(player, "bet-manip-late", "hours", (int) minHours);
            return;
        }
        long delta = manipDelta(target);
        long changed = delta + direction;
        if (Math.abs(changed) > manipMax() || (direction < 0 && deathsThisWeek(target) + changed < 0)) {
            msg.send(player, "bet-manip-limit");
            return;
        }
        long price = plugin.blackMarket().priceFor(uuid, manipCost());
        long fee = plugin.blackMarket().feeFor(uuid, price, plugin.blackMarket().feePercent());
        boolean success = random.nextInt(100) < manipChance(uuid);
        boolean caught = !success && random.nextInt(100) < manipCaughtPercent();
        long fine = 0;
        db.begin();
        try {
            if (!db.takeCoins(uuid, price + fee)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(price + fee);
            plugin.reviere().payPate(fee);
            if (success) {
                db.setMetaLong("bmx_" + target, changed);
            } else if (caught) {
                long wish = Math.round(price * Math.max(0, plugin.getConfig().getDouble("bets.manipulation.fine-factor", 1.5)));
                if (wish > 0 && db.takeCoins(uuid, wish)) {
                    db.addShopAccount(wish);
                    fine = wish;
                }
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Wettmanipulation: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "wettmanipulation", -(price + fee + fine),
                "Ergebnis von " + targetName + (direction > 0 ? " +1" : " -1") + (success ? " geklappt" : " fehlgeschlagen"));
        if (success) {
            msg.send(player, "bet-manip-success", "player", targetName, "price", price, "fee", fee,
                    "change", direction > 0 ? "einen Tod mehr" : "einen Tod weniger");
        } else {
            msg.send(player, "bet-manip-fail", "player", targetName, "price", price, "fee", fee);
            if (caught) {
                msg.send(player, "bet-manip-fine", "fine", fine);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    msg.send(online, "bet-manip-caught", "player", player.getName(), "target", targetName);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Wettbaron: der Spieler mit dem höchsten Wetteinsatz der Woche
    // ------------------------------------------------------------------

    private final java.util.Set<UUID> baronSession = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void setBaronSession(UUID uuid, boolean on) {
        if (on) {
            baronSession.add(uuid);
        } else {
            baronSession.remove(uuid);
        }
    }

    public boolean isBaronSession(UUID uuid) {
        return baronSession.contains(uuid);
    }

    public UUID baron() {
        Database db = plugin.database();
        long most = db.getMetaLong("bb_m", 0);
        long least = db.getMetaLong("bb_l", 0);
        return most == 0 && least == 0 ? null : new UUID(most, least);
    }

    public int baronFeePercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("bets.baron.fee-percent", 5)));
    }

    public long baronMinStake() {
        return Math.max(0, plugin.getConfig().getLong("bets.baron.min-stake", 2000));
    }

    /** Hat der Baron seine Sonderquote für diese Woche schon gesetzt? */
    public boolean baronUsed() {
        return plugin.database().getMetaLong("bbu", -1) == currentWeek();
    }

    /** Der Baron bekommt einen Anteil jeder Wettgebühr (aus dem Shop-Konto). */
    private void payBaron(long fee) {
        UUID baron = baron();
        if (baron == null || fee <= 0) {
            return;
        }
        long share = fee * baronFeePercent() / 100;
        if (share <= 0) {
            return;
        }
        Database db = plugin.database();
        db.addCoins(baron, share);
        db.addShopAccount(-share);
        db.logTx(baron, db.getName(baron), "wettbaron-anteil", share, "Anteil an Wettgebühren");
        db.setMetaLong("bbe_" + baron, db.getMetaLong("bbe_" + baron, 0) + share);
    }

    /** Die Sonderquote des Barons: wie Quote türken, aber gratis, immer erfolgreich und nur einmal pro Woche. */
    private void baronRig(Player player, UUID target, String targetName, int outcome) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!uuid.equals(baron())) {
            msg.send(player, "bet-baron-only");
            return;
        }
        if (closed()) {
            msg.send(player, "bet-closed");
            return;
        }
        if (baronUsed()) {
            msg.send(player, "bet-baron-used");
            return;
        }
        if (optedOut(target)) {
            msg.send(player, "bet-target-optout", "player", targetName);
            return;
        }
        if (baseOdds(target, outcome) <= 0) {
            msg.send(player, "bet-impossible");
            return;
        }
        if (isRigged(target, outcome)) {
            msg.send(player, "bet-rig-exists");
            return;
        }
        db.addRig(uuid, target, outcome);
        db.setMetaLong("bbu", currentWeek());
        msg.send(player, "bet-baron-rig", "player", targetName, "outcome", outcomeText(outcome));
    }

    /** Aus den Wocheneinsätzen den neuen Baron bestimmen (bei Gleichstand bleibt der bisherige). */
    private void electBaron() {
        Database db = plugin.database();
        UUID old = baron();
        UUID best = null;
        long bestStake = baronMinStake() - 1;
        for (Map.Entry<String, Long> entry : db.getMetaPrefix("bst_").entrySet()) {
            try {
                UUID who = UUID.fromString(entry.getKey().substring(4));
                long value = entry.getValue();
                if (value > bestStake || (value == bestStake && best != null && who.equals(old))) {
                    best = who;
                    bestStake = value;
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
                // kein Spieler-Eintrag
            }
        }
        db.deleteMetaPrefix("bst_");
        db.deleteMetaPrefix("bbe_");
        db.setMetaLong("bb_m", best == null ? 0 : best.getMostSignificantBits());
        db.setMetaLong("bb_l", best == null ? 0 : best.getLeastSignificantBits());
        if (best != null && !best.equals(old)) {
            String name = db.getName(best);
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.messages().send(online, "bet-baron-new", "player", name == null ? "?" : name,
                        "stake", bestStake);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Wochenauswertung (sonntags zur Reset-Uhrzeit)
    // ------------------------------------------------------------------

    public void dailyReset() {
        try {
            weeklyCheck();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Wettbüro-Auswertung fehlgeschlagen: " + e);
        }
        try {
            policeRaid();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Razzia auf schwarze Wetten fehlgeschlagen: " + e);
        }
    }

    /** Jeden Tag kann die Polizei schwarze Wetten aufdecken: dann ist der Einsatz weg. */
    private void policeRaid() {
        int chance = blackRaidChance();
        if (chance <= 0) {
            return;
        }
        Database db = plugin.database();
        boolean any = false;
        for (Bet bet : db.getBets()) {
            if (!bet.black() || random.nextInt(100) >= chance) {
                continue;
            }
            db.deleteBet(bet.id()); // der Einsatz bleibt beim Haus
            db.setMetaLong("bd_" + bet.bettor(), db.getMetaLong("bd_" + bet.bettor(), 0) + 1);
            db.setMetaLong("bde_" + bet.bettor(), db.getMetaLong("bde_" + bet.bettor(), 0) + bet.stake());
            db.logTx(bet.bettor(), db.getName(bet.bettor()), "wette-aufgedeckt", 0,
                    "Schwarze Wette auf " + bet.targetName() + " von der Polizei aufgedeckt, Einsatz " + bet.stake() + " verloren");
            any = true;
        }
        if (any) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                sendPending(online);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Insider-Tipp (Schwarzmarkt)
    // ------------------------------------------------------------------

    public long tipPrice() {
        return Math.max(0, plugin.getConfig().getLong("bets.tip-price", 600));
    }

    /**
     * Kauft einen Tipp: ein zufälliger Spieler, der gerade online ist, und wie oft er nächste Woche vermutlich stirbt
     * und woran. Grundlage sind seine bisherigen Todesstatistiken und die Wetten anderer Spieler auf ihn.
     */
    /** Preis des Insider-Tipps mit der enthaltenen Schwarzmarkt-Gebühr. */
    public long tipTotal(UUID uuid) {
        return plugin.blackMarket().withFee(plugin.blackMarket().priceFor(uuid, tipPrice()));
    }

    public void buyTip(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!allowed(player) || !plugin.blackMarket().allowed(player)) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        long day = now.minusHours(plugin.serverShop().resetHour()).toLocalDate().toEpochDay();
        if (db.getMetaLong("bt_tip_" + uuid, -1) == day) {
            msg.send(player, "bet-tip-used");
            return;
        }
        List<Player> candidates = new java.util.ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(uuid) && !optedOut(online.getUniqueId())
                    && !plugin.timeline().previewFor(online)) {
                candidates.add(online);
            }
        }
        if (candidates.isEmpty()) {
            msg.send(player, "bet-tip-nobody");
            return;
        }
        long price = tipTotal(uuid);
        db.begin();
        try {
            if (price > 0 && !db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "bet-tip-money", "price", price);
                return;
            }
            if (price > 0) {
                db.addShopAccount(price);
                plugin.reviere().payPate(plugin.blackMarket().feePart(price));
            }
            db.setMetaLong("bt_tip_" + uuid, day);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Insider-Tipp: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        if (price > 0) {
            db.logTx(uuid, player.getName(), "insider-tipp", -price, "Insider-Tipp gekauft");
        }
        Player target = candidates.get(random.nextInt(candidates.size()));
        UUID t = target.getUniqueId();
        double lambda = weeklyRate(t);
        int bestCount = 0;
        double bestScore = -1;
        double total = 0;
        double[] score = new double[MAX_OUTCOME + 1];
        for (int o = 0; o <= MAX_OUTCOME; o++) {
            double p = o >= MAX_OUTCOME ? atLeast(lambda, MAX_OUTCOME) : poisson(lambda, o);
            score[o] = p * (1.0 + 0.1 * countOn(t, o)); // Wetten anderer zeigen, was erwartet wird
            total += score[o];
            if (score[o] > bestScore) {
                bestScore = score[o];
                bestCount = o;
            }
        }
        int bestCause = 0;
        double bestCauseP = -1;
        for (int cat = 0; cat < CAUSES.length; cat++) {
            double p = causeProbability(t, cat);
            if (p > bestCauseP) {
                bestCauseP = p;
                bestCause = cat;
            }
        }
        long chance = total <= 0 ? 0 : Math.round(100.0 * bestScore / total);
        msg.send(player, "bet-tip-result", "player", target.getName(),
                "count", bestCount == 0 ? "gar nicht" : outcomeText(bestCount), "cause", CAUSES[bestCause],
                "chance", chance);
    }

    private void weeklyCheck() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        if (now.getDayOfWeek() != DayOfWeek.SUNDAY) {
            return;
        }
        long today = now.toLocalDate().toEpochDay();
        Database db = plugin.database();
        if (db.getMetaLong("bets_last_eval", -1) == today) {
            return;
        }
        db.setMetaLong("bets_last_eval", today);

        Map<UUID, Long> deaths = new HashMap<>();
        for (Map.Entry<String, Long> entry : db.getMetaPrefix("bw_").entrySet()) {
            try {
                deaths.put(UUID.fromString(entry.getKey().substring(3)), entry.getValue());
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
                // kein Spieler-Eintrag
            }
        }
        // Auffällig viele Tode in der letzten Stunde vor der Auswertung (mehr als 5): Diese Tode zählen nicht
        Map<UUID, Integer> lateDeaths = new HashMap<>();
        Map<UUID, java.util.List<long[]>> deathCauses = new HashMap<>(); // je Spieler: {Zeit, Art}
        long windowStart = System.currentTimeMillis()
                - Math.max(1, plugin.getConfig().getLong("bets.suspicious-window-minutes", 60)) * 60_000L;
        for (Map.Entry<String, Long> entry : db.getMetaPrefix("bx_").entrySet()) {
            try {
                String rest = entry.getKey().substring(3);
                int cut = rest.lastIndexOf('_');
                UUID who = UUID.fromString(rest.substring(0, cut));
                long at = Long.parseLong(rest.substring(cut + 1));
                deathCauses.computeIfAbsent(who, k -> new java.util.ArrayList<>()).add(new long[] {at, entry.getValue() - 1});
                if (at >= windowStart) {
                    lateDeaths.merge(who, 1, Integer::sum);
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
                // kein Todes-Eintrag
            }
        }
        int suspiciousCount = Math.max(1, plugin.getConfig().getInt("bets.suspicious-deaths", 5));
        for (Map.Entry<UUID, Integer> late : lateDeaths.entrySet()) {
            if (late.getValue() > suspiciousCount) {
                deaths.merge(late.getKey(), -(long) late.getValue(), Long::sum);
                deaths.computeIfPresent(late.getKey(), (k, v) -> Math.max(0L, v));
                deathCauses.getOrDefault(late.getKey(), new java.util.ArrayList<>()).removeIf(e -> e[0] >= windowStart);
            }
        }
        // Wettmanipulation: das Ergebnis für die Wetten wird um die geheimen Verschiebungen geändert
        Map<UUID, Long> settled = new HashMap<>(deaths);
        for (Map.Entry<String, Long> entry : db.getMetaPrefix("bmx_").entrySet()) {
            try {
                UUID who = UUID.fromString(entry.getKey().substring(4));
                settled.merge(who, entry.getValue(), Long::sum);
                settled.computeIfPresent(who, (k, v) -> Math.max(0L, v));
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
                // kein Spieler-Eintrag
            }
        }
        List<Bet> bets = db.getBets();
        List<Rig> rigs = db.getRigs();
        Map<UUID, Long> won = new HashMap<>();
        Map<UUID, Long> lost = new HashMap<>();
        Map<UUID, Long> share = new HashMap<>();
        Map<UUID, Long> refund = new HashMap<>();
        Map<UUID, Boolean> playedEnough = new HashMap<>();
        db.begin();
        try {
            for (Bet bet : bets) {
                boolean enough = playedEnough.computeIfAbsent(bet.target(),
                        t -> playedMillis(t) >= minPlayMillis());
                if (!enough) {
                    // Zu wenig gespielt: Einsatz zurück, Gebühr bleibt beim Haus
                    db.addCoins(bet.bettor(), bet.stake());
                    db.addShopAccount(-bet.stake());
                    db.logTx(bet.bettor(), db.getName(bet.bettor()), "wette-rueckzahlung", bet.stake(),
                            "Wette auf " + bet.targetName() + " zurückgezahlt (zu wenig gespielt)");
                    refund.merge(bet.bettor(), bet.stake(), Long::sum);
                    continue;
                }
                long d = settled.getOrDefault(bet.target(), 0L);
                int actual = (int) Math.min(MAX_OUTCOME, d);
                if (actual != bet.outcome()) {
                    lost.merge(bet.bettor(), bet.stake(), Long::sum);
                    continue;
                }
                // Anzahl stimmt. Jetzt die genannten Todesnachrichten prüfen: alle richtig = volle Quote
                java.util.List<Integer> wanted = parseCauses(bet.causes());
                java.util.List<Integer> happened = new java.util.ArrayList<>();
                for (long[] e : deathCauses.getOrDefault(bet.target(), new java.util.ArrayList<>())) {
                    happened.add((int) e[1]);
                }
                int matched = 0;
                for (int cat : wanted) {
                    if (happened.remove(Integer.valueOf(cat))) {
                        matched++;
                    }
                }
                double factor = 1.0;
                if (!wanted.isEmpty()) {
                    double f = matched / (double) wanted.size();
                    factor = 1.0 + (bet.causeMult() / 100.0 - 1.0) * f;
                    if (matched < wanted.size()) {
                        factor *= Math.max(0, Math.min(1.0, plugin.getConfig().getDouble("bets.cause-partial-factor", 0.6)));
                    }
                }
                long gross = Math.round(bet.stake() * bet.odds() / 100.0 * factor);
                long profit = Math.max(0, gross - bet.stake());
                long tax = bet.black() ? 0 : plugin.stock().taxOn(bet.bettor(), profit); // schwarze Wetten: keine Steuer
                long net = gross - tax;
                db.addCoins(bet.bettor(), net);
                db.addShopAccount(-gross);
                db.logTx(bet.bettor(), db.getName(bet.bettor()), "wette-gewinn", net,
                        "Wette auf " + bet.targetName() + ": " + outcomeText(bet.outcome()));
                won.merge(bet.bettor(), net, Long::sum);
                if (bet.rigged() && profit > 0) {
                    long total = profit * rigSharePercent() / 100;
                    List<UUID> riggers = new java.util.ArrayList<>();
                    for (Rig rig : rigs) {
                        if (rig.target().equals(bet.target()) && rig.outcome() == bet.outcome()) {
                            riggers.add(rig.rigger());
                        }
                    }
                    if (total > 0 && !riggers.isEmpty()) {
                        long each = total / riggers.size();
                        for (UUID rigger : riggers) {
                            db.addCoins(rigger, each);
                            db.addShopAccount(-each);
                            db.logTx(rigger, db.getName(rigger), "quoten-anteil", each,
                                    "Anteil an getürkter Wette auf " + bet.targetName());
                            share.merge(rigger, each, Long::sum);
                        }
                    }
                }
            }
            for (Map.Entry<UUID, java.util.List<long[]>> entry : deathCauses.entrySet()) {
                for (long[] e : entry.getValue()) {
                    String key = "bq_" + entry.getKey() + "_" + e[1];
                    db.setMetaLong(key, db.getMetaLong(key, 0) + 1);
                }
            }
            for (Map.Entry<UUID, Long> entry : deaths.entrySet()) {
                ensureFirst(entry.getKey());
                db.setMetaLong("bs_" + entry.getKey(), db.getMetaLong("bs_" + entry.getKey(), 0) + entry.getValue());
            }
            electBaron();
            db.deleteMetaPrefix("bmx_");
            db.deleteMetaPrefix("bw_");
            db.deleteMetaPrefix("bt_");
            db.deleteMetaPrefix("bx_");
            long nowMs = System.currentTimeMillis();
            for (Player online : Bukkit.getOnlinePlayers()) {
                db.setMetaLong("bj_" + online.getUniqueId(), nowMs);
            }
            for (Map.Entry<UUID, Long> e : refund.entrySet()) {
                db.setMetaLong("bz_" + e.getKey(), db.getMetaLong("bz_" + e.getKey(), 0) + e.getValue());
            }
            db.clearBets();
            db.setMetaLong("bets_week", currentWeek() + 1);
            db.setMetaLong("bets_eval_at", System.currentTimeMillis());
            for (Map.Entry<UUID, Long> e : won.entrySet()) {
                db.setMetaLong("bn_" + e.getKey(), db.getMetaLong("bn_" + e.getKey(), 0) + e.getValue());
            }
            for (Map.Entry<UUID, Long> e : lost.entrySet()) {
                db.setMetaLong("bk_" + e.getKey(), db.getMetaLong("bk_" + e.getKey(), 0) + e.getValue());
            }
            for (Map.Entry<UUID, Long> e : share.entrySet()) {
                db.setMetaLong("br_" + e.getKey(), db.getMetaLong("br_" + e.getKey(), 0) + e.getValue());
            }
            db.commit();
            long boxMin = Math.max(1, plugin.getConfig().getLong("bets.box-min-win", 2000));
            int boxPercent = Math.max(0, Math.min(100, plugin.getConfig().getInt("bets.box-chance-percent", 30)));
            for (Map.Entry<UUID, Long> e : won.entrySet()) { // große Gewinne: mit etwas Glück eine Mystery Box
                if (e.getValue() >= boxMin && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < boxPercent) {
                    plugin.mysteryBoxes().give(e.getKey(), db.getName(e.getKey()), e.getValue() >= boxMin * 5 ? 3 : 2, 1, "große Wette gewonnen");
                }
            }
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Wettauswertung: " + e.getMessage());
            db.setMetaLong("bets_last_eval", -1);
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            sendPending(online);
        }
    }

    /** Meldet Gewinne, Verluste und Anteile aus der letzten Auswertung (auch für Spieler, die offline waren). */
    private void sendPending(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        long won = db.getMetaLong("bn_" + uuid, 0);
        long lost = db.getMetaLong("bk_" + uuid, 0);
        long share = db.getMetaLong("br_" + uuid, 0);
        long raided = db.getMetaLong("bd_" + uuid, 0);
        if (raided > 0) {
            long raidedStake = db.getMetaLong("bde_" + uuid, 0);
            db.setMetaLong("bd_" + uuid, 0);
            db.setMetaLong("bde_" + uuid, 0);
            plugin.messages().send(player, "bet-black-raided", "count", raided, "amount", raidedStake);
        }
        long back = db.getMetaLong("bz_" + uuid, 0);
        if (back > 0) {
            db.setMetaLong("bz_" + uuid, 0);
            plugin.messages().send(player, "bet-result-refund", "amount", back);
        }
        if (won > 0) {
            db.setMetaLong("bn_" + uuid, 0);
            plugin.messages().send(player, "bet-result-won", "amount", won);
        }
        if (lost > 0) {
            db.setMetaLong("bk_" + uuid, 0);
            plugin.messages().send(player, "bet-result-lost", "amount", lost);
        }
        if (share > 0) {
            db.setMetaLong("br_" + uuid, 0);
            plugin.messages().send(player, "bet-result-share", "amount", share);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.database().setMetaLong("bj_" + player.getUniqueId(), System.currentTimeMillis());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sendPending(player);
            }
        }, 100L);
    }

    // ------------------------------------------------------------------
    //  Befehl
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("abmelden") || args[0].equalsIgnoreCase("anmelden"))) {
            // Geht für alle Spieler, auch ohne Wettlizenz
            if (args[0].equalsIgnoreCase("abmelden") != optedOut(player.getUniqueId())) {
                plugin.messages().send(player, optedOut(player.getUniqueId()) ? "bet-optout-already" : "bet-optin-already");
            } else {
                toggleOptOut(player);
            }
            return true;
        }
        if (allowed(player)) {
            new BetMenu(plugin, player).open(player);
        }
        return true;
    }
}
