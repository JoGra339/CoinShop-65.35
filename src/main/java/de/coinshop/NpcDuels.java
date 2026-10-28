package de.coinshop;

import de.coinshop.CombatSim.Fighter;
import de.coinshop.ExpeditionManager.Copy;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Duelle gegen NPCs: eine Rangleiter mit benannten Gegnern (Geschichte, Typ, wachsende Stärke), eine tägliche Turnierrunde,
 * Revanche nach Niederlagen, Einsatz in Coins oder Risikokampf mit dem stärksten Exemplar und drei Schwarzmarkt-Gegner
 * (nur mit Schmuggler-Lizenz). Der Kampf wird berechnet, die Begleiter werden nicht verletzt (außer beim Risikokampf).
 */
public final class NpcDuels {

    /** Ein NPC-Gegner. */
    public record Npc(int rank, String name, String title, String story, CompanionTypes.MobType type, boolean black) { }

    public static final List<Npc> NPCS = List.of(
            new Npc(0, "Bauer Henrik", "Der Schafzüchter", "Er hat die schönsten Tiere weit und breit und will es beweisen.", CompanionTypes.MobType.NATUR, false),
            new Npc(1, "Müllerin Gerda", "Die Mahlende", "Sie mahlt Korn am Tag und Gegner am Abend.", CompanionTypes.MobType.ERDE, false),
            new Npc(2, "Fischer Jonte", "Der Netzwerfer", "Seit dem Sturm glaubt er, das Meer gehöre ihm.", CompanionTypes.MobType.WASSER, false),
            new Npc(3, "Jägerin Wilma", "Die Falkenfrau", "Ihr Falke kennt keine Gnade, und sie noch weniger.", CompanionTypes.MobType.LUFT, false),
            new Npc(4, "Schmied Brokk", "Der Funkenschläger", "Wer seine Amboss-Hand kennt, fordert ihn nicht zweimal.", CompanionTypes.MobType.FEUER, false),
            new Npc(5, "Eismeister Nils", "Der Frostige", "Er lächelt nie, denn seine Mundwinkel sind eingefroren.", CompanionTypes.MobType.EIS, false),
            new Npc(6, "Grabwächter Morten", "Der Nachtwächter", "Jede Nacht zählt er seine Gäste. Keiner ist je gegangen.", CompanionTypes.MobType.UNTOTE, false),
            new Npc(7, "Händlerin Saphira", "Die Schattenkrämerin", "Sie verkauft alles. Auch Niederlagen.", CompanionTypes.MobType.FINSTER, false),
            new Npc(8, "Hauptmann Rasmus", "Der Eiserne", "Sein Schild hat mehr Beulen als Siege, und er hat viele Siege.", CompanionTypes.MobType.ERDE, false),
            new Npc(9, "Alchemistin Zora", "Die Brodelnde", "Ihre Tränke wirken auf Gegner und manchmal auf sie selbst.", CompanionTypes.MobType.NATUR, false),
            new Npc(10, "Drachenreiter Falk", "Der Himmelsstürmer", "Er behauptet, einmal einen Drachen geritten zu haben.", CompanionTypes.MobType.LUFT, false),
            new Npc(11, "Champion Aurelia", "Die Ungeschlagene", "Seit Jahren hat niemand sie besiegt. Bis jetzt?", CompanionTypes.MobType.FEUER, false),
            new Npc(12, "Vito der Schmuggler", "Der Kistenschieber", "Er kennt jeden Gang unter dem Hafen und jede Schwäche.", CompanionTypes.MobType.FINSTER, true),
            new Npc(13, "Die Hehlerin", "Die Lautlose", "Was sie nimmt, sieht man nie wieder. Dich auch nicht.", CompanionTypes.MobType.UNTOTE, true),
            new Npc(14, "Der Pate der Schatten", "Das Ende der Leiter", "Wer ihn schlägt, dem gehört der Untergrund.", CompanionTypes.MobType.FEUER, true));

    public static final int NORMAL_COUNT = 12;
    public static final long[] STAKES = {0, 100, 500, 2000, 5000};

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Integer> stakeIndex = new HashMap<>();
    private final Map<UUID, Boolean> risk = new HashMap<>();

    NpcDuels(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
    }

    private int cfgI(String key, int def) {
        return plugin.getConfig().getInt("expedition.npc-duel." + key, def);
    }

    private void say(Player p, String text) {
        plugin.messages().send(p, "ex-npc-msg", "text", text);
    }

    private long resetNo() {
        return plugin.database().getMetaLong("shop_reset_no", 0);
    }

    // --- Fortschritt -----------------------------------------------------

    public int progress(UUID uuid) {
        return (int) plugin.database().getMetaLong("npc_prog_" + uuid, 0);
    }

    private void setProgress(UUID uuid, int value) {
        plugin.database().setMetaLong("npc_prog_" + uuid, value);
    }

    public boolean blackAllowed(UUID uuid) {
        return plugin.licenses().has(uuid, "schmuggler");
    }

    /** Darf der Spieler diesen Gegner herausfordern? */
    public boolean unlocked(UUID uuid, int rank) {
        if (rank < 0 || rank >= NPCS.size()) {
            return false;
        }
        if (NPCS.get(rank).black() && !blackAllowed(uuid)) {
            return false;
        }
        return rank <= progress(uuid);
    }

    // --- Einsatz ---------------------------------------------------------

    public long stake(UUID uuid) {
        return STAKES[Math.max(0, Math.min(STAKES.length - 1, stakeIndex.getOrDefault(uuid, 0)))];
    }

    public void cycleStake(UUID uuid) {
        stakeIndex.put(uuid, (stakeIndex.getOrDefault(uuid, 0) + 1) % STAKES.length);
    }

    public boolean risk(UUID uuid) {
        return risk.getOrDefault(uuid, false);
    }

    public void toggleRisk(UUID uuid) {
        risk.put(uuid, !risk(uuid));
    }

    // --- Gegner und Belohnung --------------------------------------------

    public List<Fighter> team(Npc n) {
        return team(n, List.of());
    }

    /** Taktisch zusammengestellte Gruppe des NPC; gegen die Typen der Gegner (foes) abgestimmt. */
    public List<Fighter> team(Npc n, List<Fighter> foes) {
        int rank = n.rank();
        int level = 2 + rank * 5 + (n.black() ? 10 : 0);
        int stars = Math.min(7, 2 + rank / 2);
        int size = Math.min(6, 2 + rank / 3);
        double power = 1.0 + rank * 0.03;
        return CombatSim.npcTeam(ex, n.name().split(" ")[0] + "s", n.type(), size, level, stars, power, foes);
    }

    private static String[] namesOf(CompanionTypes.MobType t) {
        return switch (t) {
            case FEUER -> new String[]{"Lohe", "Magmawürfel", "Schreitling"};
            case WASSER -> new String[]{"Wächter", "Delfin", "Axolotl"};
            case NATUR -> new String[]{"Wolf", "Biene", "Fuchs"};
            case ERDE -> new String[]{"Golem", "Ziege", "Gürteltier"};
            case EIS -> new String[]{"Eisbär", "Schneegolem", "Eiswanderer"};
            case LUFT -> new String[]{"Papagei", "Hilfsgeist", "Phantom"};
            case UNTOTE -> new String[]{"Skelett", "Zombie", "Wüstenzombie"};
            case FINSTER -> new String[]{"Enderman", "Hexe", "Plünderer"};
        };
    }

    public long reward(int rank) {
        return Math.max(10, Math.round(150 * Math.pow(1.45, rank) / 10.0) * 10);
    }

    public List<Fighter> myTeam(UUID uuid) {
        List<Fighter> list = new ArrayList<>();
        int max = Math.max(1, cfgI("max-team", 6));
        for (Copy k : ex.duel().deck(uuid)) {
            if (list.size() >= max) {
                break;
            }
            list.add(CombatSim.of(ex, uuid, k));
        }
        return list;
    }

    // --- Kampf -----------------------------------------------------------

    private boolean ready(Player p) {
        if (ex.hasLicense(p) && ex.duel().blocked(p)) {
            return false;
        }
        Messages msg = plugin.messages();
        UUID uuid = p.getUniqueId();
        if (!ex.hasLicense(p)) {
            msg.send(p, "ex-locked");
            return false;
        }
        if (ex.isIn(uuid) || ex.live().watching(uuid)) {
            say(p, "Du bist gerade in einer Expedition oder siehst einem Kampf zu.");
            return false;
        }
        if (ex.duel().deck(uuid).isEmpty()) {
            msg.send(p, "ex-duel-nodeck");
            return false;
        }
        long wait = cooldown.getOrDefault(uuid, 0L) - System.currentTimeMillis();
        if (wait > 0) {
            say(p, "Warte noch " + (wait / 1000 + 1) + " Sekunden bis zum nächsten Duell.");
            return false;
        }
        return true;
    }

    private void showLog(Player p, List<String> log) {
        for (int i = 0; i < Math.min(5, log.size()); i++) {
            plugin.messages().send(p, "ex-duel-line", "text", log.get(i));
        }
        if (log.size() > 5) {
            plugin.messages().send(p, "ex-duel-line", "text", "... und " + (log.size() - 5) + " weitere Kämpfe");
        }
    }

    private void pay(Player p, long amount, String note) {
        if (amount <= 0) {
            return;
        }
        Database db = plugin.database();
        db.addCoins(p.getUniqueId(), amount);
        db.logTx(p.getUniqueId(), p.getName(), "npc-duell", amount, note);
    }

    private final java.util.Set<UUID> formed = new java.util.HashSet<>();

    /** Vor jedem NPC-Kampf: Aufstellung zeigen. true = Menü wurde geöffnet, der Aufruf kommt nach "Kampf starten" noch einmal. */
    private boolean formationGate(Player p, Runnable again) {
        UUID u = p.getUniqueId();
        if (formed.remove(u)) {
            return false;
        }
        new de.coinshop.gui.FormationMenu(plugin, p, Math.max(1, cfgI("max-team", 6)), () -> {
            formed.add(u);
            try {
                again.run();
            } finally {
                formed.remove(u);
            }
        }).open(p);
        return true;
    }

    /** Fordert den NPC mit diesem Rang heraus. */
    public void challenge(Player p, int rank) {
        UUID uuid = p.getUniqueId();
        int teamMax = Math.max(1, cfgI("max-team", 6));
        if (unlocked(uuid, rank) && ex.hasLicense(p) && ex.duel().blocked(p)) {
            return;
        }
        if (unlocked(uuid, rank) && ex.duel().needsPick(p, teamMax)) {
            ex.duel().pick(p, teamMax, () -> challenge(p, rank));
            return;
        }
        if (!unlocked(uuid, rank)) {
            say(p, "Diesen Gegner kannst du noch nicht herausfordern.");
            return;
        }
        if (!ready(p)) {
            return;
        }
        if (formationGate(p, () -> challenge(p, rank))) {
            return;
        }
        Npc npc = NPCS.get(rank);
        Database db = plugin.database();
        long stake = stake(uuid);
        if (stake > 0 && db.getCoins(uuid) < stake) {
            say(p, "Dir fehlen Coins für den Einsatz von " + stake + ".");
            return;
        }
        List<Fighter> mine = myTeam(uuid);
        List<Fighter> theirs = team(npc, mine);
        Copy riskCopy = risk(uuid) ? ex.duel().deck(uuid).get(0) : null;
        cooldown.put(uuid, System.currentTimeMillis() + Math.max(0, cfgI("cooldown-seconds", 30)) * 1000L);
        CombatSim.Script script = CombatSim.script(mine, theirs, new Random());
        say(p, "" + npc.name() + " (" + npc.title() + ", Stärke " + CombatSim.strength(theirs) + ") gegen deine Gruppe (Stärke "
                + CombatSim.strength(mine) + ")");
        ex.live().start(List.of(p), "Duell gegen " + npc.name(), rank, mine, theirs, script,
                () -> settleChallenge(p, rank, npc, stake, riskCopy, new CombatSim.Result(script.aWins(), script.log())));
    }

    /** Nach dem Zuschauen: Belohnung, Einsatz und Risiko auswerten. */
    private void settleChallenge(Player p, int rank, Npc npc, long stake, Copy riskCopy, CombatSim.Result res) {
        UUID uuid = p.getUniqueId();
        Database db = plugin.database();
        showLog(p, res.log());
        boolean first = rank == progress(uuid);
        if (res.aWins()) {
            long base = reward(rank);
            double mult = 1.0;
            String bonus = "";
            if (db.getMetaLong("npc_rev_" + uuid + "_" + rank, 0) == 1) {
                db.setMetaLong("npc_rev_" + uuid + "_" + rank, 0);
                mult *= 1.5;
                bonus += " Revanche geglückt (+50 %).";
            }
            if (riskCopy != null) {
                mult *= 1.5;
                bonus += " Risikokampf gewonnen (+50 %).";
            }
            long coins;
            if (first) {
                coins = Math.round(base * mult);
                setProgress(uuid, rank + 1);
                giveBox(p, rank);
            } else {
                coins = paidReplay(uuid) ? Math.round(base * mult * Math.max(0, cfgI("replay-percent", 20)) / 100.0) : 0;
            }
            pay(p, coins, "NPC-Duell gegen " + npc.name());
            long won = 0;
            if (stake > 0) {
                long extra = Math.min(stake, db.getShopAccount());
                if (extra > 0 && db.takeShopAccount(extra)) {
                    pay(p, extra, "NPC-Einsatz gegen " + npc.name());
                    won = extra;
                }
            }
            say(p, "Sieg über " + npc.name() + "!" + bonus + " Belohnung: " + coins + " Coins" + (first ? " (erster Sieg)"
                    : coins == 0 ? " (heute keine Belohnung mehr für Wiederholungen)" : "") + (won > 0 ? ", Einsatzgewinn " + won : "") + ".");
            if (first && rank + 1 < NPCS.size() && unlocked(uuid, rank + 1)) {
                say(p, "Neuer Gegner freigeschaltet: " + NPCS.get(rank + 1).name() + ".");
            } else if (first && rank + 1 == NORMAL_COUNT && !blackAllowed(uuid)) {
                say(p, "Weitere Gegner warten im Untergrund (Schwarzmarkt-Lizenz „Schmuggler“ nötig).");
            }
        } else {
            db.setMetaLong("npc_rev_" + uuid + "_" + rank, 1);
            say(p, "Niederlage gegen " + npc.name() + ". Gewinnst du die Revanche, gibt es +50 %.");
            if (stake > 0 && db.takeCoins(uuid, stake)) {
                db.addShopAccount(stake);
                db.logTx(uuid, p.getName(), "npc-duell", -stake, "NPC-Einsatz verloren gegen " + npc.name());
                say(p, "Einsatz verloren: " + stake + " Coins.");
            }
            if (riskCopy != null) {
                ex.roster().setRest(uuid, riskCopy.companion(), riskCopy.index(), ex.restUntilShopReset());
                ex.roster().save(uuid);
                say(p, "" + ex.trips().copyName(riskCopy.companion(), riskCopy.index()) + " ruht sich bis zum Shopwechsel aus.");
            }
        }
    }

    /** Zählt Wiederholungs-Belohnungen pro Shop-Tag. true = noch erlaubt. */
    private boolean paidReplay(UUID uuid) {
        Database db = plugin.database();
        long no = resetNo();
        if (db.getMetaLong("npc_rpno_" + uuid, -1) != no) {
            db.setMetaLong("npc_rpno_" + uuid, no);
            db.setMetaLong("npc_rpn_" + uuid, 0);
        }
        long used = db.getMetaLong("npc_rpn_" + uuid, 0);
        if (used >= Math.max(0, cfgI("replays-per-day", 5))) {
            return false;
        }
        db.setMetaLong("npc_rpn_" + uuid, used + 1);
        return true;
    }

    private void giveBox(Player p, int rank) {
        int tier = rank >= 12 ? 3 : rank >= 9 ? 2 : rank >= 3 && rank % 3 == 0 ? 1 : 0;
        if (tier > 0 && plugin.mysteryBoxes() != null) {
            plugin.mysteryBoxes().give(p, tier, "NPC-Duell");
        }
    }

    // --- Tägliches Turnier -----------------------------------------------

    public boolean tournamentDone(UUID uuid) {
        return plugin.database().getMetaLong("npc_tour_" + uuid, -1) == resetNo();
    }

    /** Vier Runden gegen steigende Gegner, einmal pro Shop-Tag. Bei der ersten Niederlage endet das Turnier. */
    public void tournament(Player p) {
        UUID uuid = p.getUniqueId();
        if (tournamentDone(uuid)) {
            say(p, "Du hast heute schon am Turnier teilgenommen. Nach dem nächsten Shopwechsel geht es wieder.");
            return;
        }
        if (!ready(p)) {
            return;
        }
        if (formationGate(p, () -> tournament(p))) {
            return;
        }
        plugin.database().setMetaLong("npc_tour_" + uuid, resetNo());
        int base = Math.max(0, Math.min(NORMAL_COUNT - 4, progress(uuid) - 2));
        List<Fighter> mine = myTeam(uuid);
        Random r = new Random();
        cooldown.put(uuid, System.currentTimeMillis() + Math.max(0, cfgI("cooldown-seconds", 30)) * 1000L);
        say(p, "Turnier: vier Runden. Verlierst du, ist es vorbei. Du siehst jede Runde live zu.");
        tourRound(p, base, 0, 0, 0L, mine, r);
    }

    /** Eine Turnierrunde als Zuschau-Kampf; danach folgt die nächste oder die Auswertung. */
    private void tourRound(Player p, int base, int round, int wins, long total, List<Fighter> mine, Random r) {
        if (round >= 4 || !p.isOnline()) {
            finishTournament(p, base, wins, total);
            return;
        }
        Npc npc = NPCS.get(Math.min(NORMAL_COUNT - 1, base + round));
        List<Fighter> theirs = team(npc, mine);
        CombatSim.Script script = CombatSim.script(mine, theirs, r);
        ex.live().start(List.of(p), "Turnier, Runde " + (round + 1) + " gegen " + npc.name(), base + round, mine, theirs, script, () -> {
            if (!script.aWins()) {
                say(p, "Runde " + (round + 1) + ": Ausgeschieden gegen " + npc.name() + ".");
                finishTournament(p, base, wins, total);
                return;
            }
            long prize = reward(npc.rank()) / 2;
            say(p, "Runde " + (round + 1) + ": Sieg über " + npc.name() + " (+" + prize + " Coins).");
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin,
                    () -> tourRound(p, base, round + 1, wins + 1, total + prize, mine, r), 40L);
        });
    }

    private void finishTournament(Player p, int base, int wins, long total0) {
        long total = total0;
        if (wins == 4) {
            total *= 2;
            say(p, "Turniersieger! Doppelte Prämie.");
            if (plugin.mysteryBoxes() != null) {
                plugin.mysteryBoxes().give(p, base >= 6 ? 2 : 1, "Turnier");
            }
        }
        pay(p, total, "NPC-Turnier (" + wins + " Siege)");
        say(p, "Turnier beendet: " + wins + " von 4 Runden gewonnen, Preisgeld " + total + " Coins.");
    }
}
