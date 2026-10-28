package de.coinshop;

import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Duelle: Zwei Spieler lassen ihre gewählten Begleiter (das Gefolge) gegeneinander antreten. Berechnet wird der Kampf,
 * die Begleiter selbst werden nicht verletzt. Typen, Level, Seltenheit und Eigenschaften zählen. Auf Wunsch mit Einsatz.
 */
public final class CompanionDuel {

    private record Invite(UUID from, long stake, long expires) { }

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;
    /** Einladungen: Eingeladener -> Einladung. */
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    CompanionDuel(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
    }

    private int cfgI(String key, int def) {
        return plugin.getConfig().getInt("expedition.duel." + key, def);
    }

    /** Kampfwerte eines Exemplars. */
    private double hp(UUID uuid, Copy k) {
        double h = (20 + k.companion().stars * 12.0) * ex.levelBonus(ex.copyLevel(uuid, k.companion(), k.index()))
                * ex.rarity(uuid, k.companion(), k.index()).stats;
        double t = 1;
        for (CompanionTraits.Trait tr : ex.traits(uuid, k.companion(), k.index())) {
            t += tr.hp;
        }
        return h * Math.max(0.3, t);
    }

    private double atk(UUID uuid, Copy k) {
        double a = (4 + k.companion().stars * 1.5) * ex.levelBonus(ex.copyLevel(uuid, k.companion(), k.index()))
                * ex.rarity(uuid, k.companion(), k.index()).stats;
        double t = 1;
        for (CompanionTraits.Trait tr : ex.traits(uuid, k.companion(), k.index())) {
            t += tr.dmg;
        }
        return a * Math.max(0.3, t);
    }

    // --- Aufstellung (vordere/hintere Reihe) ---------------------------------

    private final java.util.Map<UUID, java.util.Map<String, Boolean>> rows = new java.util.HashMap<>();

    private static String rowKey(Copy k) {
        return k.companion().key + ":" + k.index();
    }

    /** Steht das Exemplar in der hinteren Reihe? Ohne eigene Wahl stehen Unterstützer hinten, alle anderen vorne. */
    public boolean isBack(UUID owner, Copy k) {
        java.util.Map<String, Boolean> m = rows.get(owner);
        Boolean b = m == null ? null : m.get(rowKey(k));
        return b != null ? b : ex.isSupporter(k.companion());
    }

    public void toggleRow(UUID owner, Copy k) {
        rows.computeIfAbsent(owner, x -> new java.util.HashMap<>()).put(rowKey(k), !isBack(owner, k));
    }

    /** Das Gefolge für Duelle: gewählte, einsatzbereite Exemplare (die stärksten zuerst). */
    public List<Copy> deck(UUID uuid) {
        List<Copy> list = ex.takenReady(uuid);
        list.sort((a, b) -> Double.compare(hp(uuid, b) * atk(uuid, b), hp(uuid, a) * atk(uuid, a)));
        return list;
    }

    public double strength(UUID uuid) {
        double sum = 0;
        for (Copy k : deck(uuid)) {
            sum += hp(uuid, k) * atk(uuid, k);
        }
        return Math.sqrt(sum);
    }

    public void command(Player player, String[] args) {
        Messages msg = plugin.messages();
        UUID me = player.getUniqueId();
        if (args.length == 0) {
            msg.send(player, "ex-duel-usage");
            return;
        }
        if (args[0].equalsIgnoreCase("annehmen")) {
            accept(player);
            return;
        }
        if (args[0].equalsIgnoreCase("ablehnen")) {
            Invite inv = invites.remove(me);
            if (inv != null) {
                Player from = Bukkit.getPlayer(inv.from());
                if (from != null) {
                    msg.send(from, "ex-duel-declined", "name", player.getName());
                }
                msg.send(player, "ex-duel-declined-self");
            }
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            msg.send(player, "ex-duel-nobody");
            return;
        }
        long stake = 0;
        if (args.length >= 2) {
            try {
                stake = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                msg.send(player, "ex-duel-usage");
                return;
            }
        }
        invite(player, target, stake);
    }

    private boolean ready(Player p) {
        UUID uuid = p.getUniqueId();
        return ex.hasLicense(p) && !ex.isIn(uuid) && !ex.live().watching(uuid) && !deck(uuid).isEmpty();
    }

    /** So viele Begleiter dürfen höchstens ins Duell. */
    public int teamMax() {
        return Math.max(1, cfgI("team-max", 6));
    }

    /** Das Gefolge für den Kampf, begrenzt auf die erlaubte Zahl (die Stärksten zuerst). */
    public List<Copy> team(UUID uuid, int max) {
        List<Copy> d = deck(uuid);
        return new ArrayList<>(d.subList(0, Math.min(d.size(), max)));
    }

    /** Ist gerade gar kein Begleiter einsatzbereit (alle verletzt, ruhend, im Training, auf Reise ...)? */
    public boolean noneAvailable(UUID uuid) {
        for (Copy k : ex.allCopies(uuid)) {
            if (ex.busyReason(uuid, k.companion(), k.index()) == null) {
                return false;
            }
        }
        return true;
    }

    /** Sagt dem Spieler, warum er nicht kämpfen kann, wenn kein Begleiter einsatzbereit ist. true = gesperrt. */
    public boolean blocked(Player p) {
        UUID uuid = p.getUniqueId();
        if (!noneAvailable(uuid)) {
            return false;
        }
        if (ex.allCopies(uuid).isEmpty()) {
            plugin.messages().send(p, "ex-duel-nodeck");
            return true;
        }
        plugin.messages().send(p, "ex-duel-line", "text", "Alle deine Begleiter sind gerade verhindert, so kannst du nicht kämpfen:");
        for (String line : ex.unavailableLines(uuid, 6)) {
            plugin.messages().send(p, "ex-duel-line", "text", line);
        }
        return true;
    }

    /** Muss der Spieler vor dem Kampf seine Begleiter wählen (zu viele oder keine)? */
    public boolean needsPick(Player p, int max) {
        UUID uuid = p.getUniqueId();
        if (!ex.hasLicense(p) || ex.isIn(uuid)) {
            return false;
        }
        int n = deck(uuid).size();
        return n > max || n == 0;
    }

    /** Öffnet das Auswahlmenü; mit "Weiter" läuft after. */
    public void pick(Player p, int max, Runnable after) {
        plugin.messages().send(p, "ex-team-pick", "max", max);
        new de.coinshop.gui.TeamPickMenu(plugin, p, 1, max, 0, after).open(p);
    }

    /** Fordert einen Spieler zum Duell heraus (aus dem Menü). */
    public void challenge(Player from, Player to, long stake) {
        invite(from, to, stake);
    }

    private void invite(Player from, Player to, long stake) {
        Messages msg = plugin.messages();
        if (blocked(from)) {
            return;
        }
        if (needsPick(from, teamMax())) {
            final long st = stake;
            pick(from, teamMax(), () -> invite(from, to, st));
            return;
        }
        long max = Math.max(0, cfgI("max-stake", 5000));
        stake = Math.max(0, Math.min(max, stake));
        long wait = cooldown.getOrDefault(from.getUniqueId(), 0L) - System.currentTimeMillis();
        if (wait > 0) {
            msg.send(from, "ex-duel-wait", "seconds", wait / 1000 + 1);
            return;
        }
        if (!ready(from)) {
            msg.send(from, "ex-duel-nodeck");
            return;
        }
        if (!ex.hasLicense(to) || ex.isIn(to.getUniqueId()) || deck(to.getUniqueId()).isEmpty()) {
            msg.send(from, "ex-duel-target-not-ready", "name", to.getName());
            return;
        }
        if (stake > 0 && (plugin.database().getCoins(from.getUniqueId()) < stake || plugin.database().getCoins(to.getUniqueId()) < stake)) {
            msg.send(from, "ex-duel-poor");
            return;
        }
        invites.put(to.getUniqueId(), new Invite(from.getUniqueId(), stake, System.currentTimeMillis() + 60_000L));
        msg.send(from, "ex-duel-sent", "name", to.getName(), "stake", stake);
        msg.send(to, "ex-duel-invite", "name", from.getName(), "stake", stake);
    }

    private void accept(Player me) {
        Messages msg = plugin.messages();
        Invite inv = invites.remove(me.getUniqueId());
        if (inv == null || inv.expires() < System.currentTimeMillis()) {
            msg.send(me, "ex-duel-none");
            return;
        }
        Player from = Bukkit.getPlayer(inv.from());
        if (from == null || !from.isOnline()) {
            msg.send(me, "ex-duel-nobody");
            return;
        }
        if (blocked(me)) {
            return;
        }
        if (needsPick(me, teamMax())) {
            invites.put(me.getUniqueId(), inv);
            pick(me, teamMax(), () -> accept(me));
            return;
        }
        if (!ready(me) || !ready(from)) {
            msg.send(me, "ex-duel-nodeck");
            msg.send(from, "ex-duel-nodeck");
            return;
        }
        run(from, me, inv.stake());
    }

    private void run(Player a, Player b, long stake) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID ua = a.getUniqueId();
        UUID ub = b.getUniqueId();
        long cooldownMs = Math.max(0, cfgI("cooldown-seconds", 120)) * 1000L;
        cooldown.put(ua, System.currentTimeMillis() + cooldownMs);
        cooldown.put(ub, System.currentTimeMillis() + cooldownMs);
        List<CombatSim.Fighter> fa = new ArrayList<>();
        for (Copy k : team(ua, teamMax())) {
            fa.add(CombatSim.of(ex, ua, k));
        }
        List<CombatSim.Fighter> fb = new ArrayList<>();
        for (Copy k : team(ub, teamMax())) {
            fb.add(CombatSim.of(ex, ub, k));
        }
        CombatSim.Script script = CombatSim.script(fa, fb, new Random());
        List<String> log = script.log();
        boolean aWins = script.aWins();
        long sa = CombatSim.strength(fa);
        long sb = CombatSim.strength(fb);
        ex.live().start(List.of(a, b), a.getName() + " gegen " + b.getName(), 0, fa, fb, script,
                () -> settle(a, b, stake, aWins, log, sa, sb));
    }

    /** Nach dem Zuschauen: Sieger eintragen, Einsatz auszahlen, Ergebnis melden. */
    private void settle(Player a, Player b, long stake, boolean aWins, List<String> log, long sa, long sb) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID ua = a.getUniqueId();
        UUID ub = b.getUniqueId();
        Player winner = aWins ? a : b;
        Player loser = aWins ? b : a;
        ex.roster().addDuel(winner.getUniqueId(), true);
        ex.roster().addDuel(loser.getUniqueId(), false);
        ex.roster().save(ua);
        ex.roster().save(ub);
        long paid = 0;
        if (stake > 0) {
            long tax = Math.round(stake * Math.max(0, cfgI("tax-percent", 10)) / 100.0);
            db.begin();
            try {
                if (db.takeCoins(loser.getUniqueId(), stake)) {
                    db.addCoins(winner.getUniqueId(), stake - tax);
                    db.addShopAccount(tax);
                    db.commit();
                    paid = stake - tax;
                } else {
                    db.rollback();
                }
            } catch (RuntimeException e) {
                db.rollback();
                plugin.getLogger().severe("Duell: Einsatz fehlgeschlagen: " + e.getMessage());
            }
            if (paid > 0) {
                db.logTx(winner.getUniqueId(), winner.getName(), "expedition", paid, "Begleiter-Duell gegen " + loser.getName());
                db.logTx(loser.getUniqueId(), loser.getName(), "expedition", -stake, "Begleiter-Duell gegen " + winner.getName());
            }
        }
        for (Player p : new Player[]{a, b}) {
            msg.send(p, "ex-duel-header", "a", a.getName(), "b", b.getName(), "sa", String.valueOf(sa),
                    "sb", String.valueOf(sb));
            for (int i = 0; i < Math.min(6, log.size()); i++) {
                msg.send(p, "ex-duel-line", "text", log.get(i));
            }
            if (log.size() > 6) {
                msg.send(p, "ex-duel-line", "text", "... und " + (log.size() - 6) + " weitere Kämpfe");
            }
            msg.send(p, "ex-duel-result", "winner", winner.getName(), "loser", loser.getName(), "coins", paid);
        }
    }
}
