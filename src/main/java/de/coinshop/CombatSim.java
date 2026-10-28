package de.coinshop;

import de.coinshop.ExpeditionManager.Copy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Berechneter Kampf zweier Gruppen (für NPC-Duelle und Untergrund-Arenen). Wie beim Spieler-Duell kämpfen die Ersten
 * beider Seiten gleichzeitig gegeneinander (vordere und hintere Reihe wie im Auto-Battler), Typen zählen, die Begleiter werden nicht verletzt.
 */
public final class CombatSim {

    private CombatSim() {
    }

    /** Ein Kämpfer: Typ, Leben, Angriff, Rolle (Verteidiger / Unterstützer mit Heilmenge) und Reihe. */
    public record Fighter(String name, CompanionTypes.MobType type, double hp, double atk, boolean defender, String entity,
                          boolean back, boolean support, double heal) {
        /** Gleicher Kämpfer in der vorderen (false) oder hinteren (true) Reihe. */
        public Fighter withBack(boolean b) {
            return new Fighter(name, type, hp, atk, defender, entity, b, support, heal);
        }
    }

    /** Ein Moment des Kampfes: Leben (Anteil 0 bis 1) jedes Kämpfers beider Seiten, 0 = besiegt. */
    public record Frame(double[] a, double[] b, int[] usesA, int[] usesB, int[] animalA, int[] animalB) { }

    /** Kampfablauf als Folge von Momenten (für die Live-Ansicht in der Arena, alle kämpfen gleichzeitig). */
    public record Script(boolean aWins, List<String> log, List<Frame> frames) { }

    private static final java.util.Map<CompanionTypes.MobType, String[]> LOOKS = new java.util.EnumMap<>(CompanionTypes.MobType.class);

    static {
        LOOKS.put(CompanionTypes.MobType.FEUER, new String[]{"MAGMA_CUBE", "PIGLIN_BRUTE", "HOGLIN", "ZOMBIFIED_PIGLIN"});
        LOOKS.put(CompanionTypes.MobType.WASSER, new String[]{"DROWNED", "AXOLOTL", "FROG", "TURTLE"});
        LOOKS.put(CompanionTypes.MobType.NATUR, new String[]{"WOLF", "FOX", "SPIDER", "OCELOT"});
        LOOKS.put(CompanionTypes.MobType.ERDE, new String[]{"IRON_GOLEM", "GOAT", "ARMADILLO", "RAVAGER"});
        LOOKS.put(CompanionTypes.MobType.EIS, new String[]{"POLAR_BEAR", "SNOW_GOLEM", "STRAY"});
        LOOKS.put(CompanionTypes.MobType.LUFT, new String[]{"BREEZE", "CHICKEN", "VEX"});
        LOOKS.put(CompanionTypes.MobType.UNTOTE, new String[]{"SKELETON", "ZOMBIE", "HUSK"});
        LOOKS.put(CompanionTypes.MobType.FINSTER, new String[]{"WITCH", "PILLAGER", "VINDICATOR", "EVOKER"});
    }

    /** Aussehen eines NPC-Kämpfers in der Live-Ansicht (passend zum Typ, je nach Name verschieden). */
    private static String lookOf(CompanionTypes.MobType type, String name) {
        String[] arr = LOOKS.get(type);
        return arr[Math.floorMod(name.hashCode(), arr.length)];
    }

    /** Ergebnis: Sieger (Seite A?) und Kampfbericht. */
    public record Result(boolean aWins, List<String> log) { }

    private static double traitSum(ExpeditionManager ex, UUID owner, Copy k, boolean hp) {
        double t = 1;
        for (CompanionTraits.Trait tr : ex.traits(owner, k.companion(), k.index())) {
            t += hp ? tr.hp : tr.dmg;
        }
        return Math.max(0.3, t);
    }

    /** Kämpfer aus einem echten Begleiter-Exemplar des Spielers "owner". */
    public static Fighter of(ExpeditionManager ex, UUID owner, Copy k) {
        double lb = ex.levelBonus(ex.copyLevel(owner, k.companion(), k.index()));
        double rar = ex.rarity(owner, k.companion(), k.index()).stats;
        double hp = (20 + k.companion().stars * 12.0) * lb * rar * traitSum(ex, owner, k, true) * ex.roleHpFactor(k.companion());
        double atk = (4 + k.companion().stars * 1.5) * lb * rar * traitSum(ex, owner, k, false) * ex.roleDmgFactor(k.companion());
        if (ex.isSupporter(k.companion())) {
            atk = Math.max(1.0, atk * 0.3); // Unterstützer kämpfen kaum, halten aber etwas aus
        }
        boolean support = ex.isSupporter(k.companion());
        return new Fighter(ex.trips().copyName(k.companion(), k.index()), k.companion().mobType(), hp, atk, ex.isDefender(k.companion()),
                k.companion().type, ex.duel().isBack(owner, k), support, support ? ex.healAmount(owner, k.companion(), k.index()) : 0);
    }

    /** Kämpfer eines NPC: Stufe und Sterne bestimmen die Werte wie bei echten Begleitern. */
    public static Fighter npc(ExpeditionManager ex, String name, CompanionTypes.MobType type, int level, int stars, double power) {
        double lb = ex.levelBonus(level);
        double hp = (20 + stars * 12.0) * lb * power;
        double atk = (4 + stars * 1.5) * lb * power;
        return new Fighter(name, type, hp, atk, false, lookOf(type, name), false, false, 0);
    }

    private enum Role { TANK, DAMAGE, SUPPORT }

    private static String roleEntity(Role role, CompanionTypes.MobType type, String name) {
        return switch (role) {
            case TANK -> switch (type) {
                case FEUER -> "PIGLIN_BRUTE";
                case WASSER -> "TURTLE";
                case NATUR -> "PANDA";
                case ERDE -> "IRON_GOLEM";
                case EIS -> "POLAR_BEAR";
                case LUFT -> "BREEZE";
                case UNTOTE -> "SKELETON_HORSE";
                case FINSTER -> "VINDICATOR";
            };
            case SUPPORT -> switch (type) {
                case FEUER -> "PIGLIN";
                case WASSER -> "DOLPHIN";
                case NATUR -> "COW";
                case ERDE -> "GOAT";
                case EIS -> "SNOW_GOLEM";
                case LUFT -> "ALLAY";
                case UNTOTE -> "ZOMBIE_VILLAGER";
                case FINSTER -> "WITCH";
            };
            case DAMAGE -> lookOf(type, name);
        };
    }

    /**
     * Taktisch zusammengestellte NPC-Gruppe: Schildträger vorne (Typ, der den Hauptangriff der Gegner am besten abfängt),
     * Kämpfer mit Typen, die gegen die Gegner stark sind, und hinten ein Heiler. Je größer die Gruppe, desto mehr Rollen.
     */
    public static List<Fighter> npcTeam(ExpeditionManager ex, String owner, CompanionTypes.MobType theme, int size, int level, int stars,
                                        double power, List<Fighter> foes) {
        // Fixes Team pro NPC: Es hängt nicht vom Team des Spielers ab. Es ist gegen die eigene Schwäche aufgestellt
        // (der Typ, der dem Thema des NPC am meisten Schaden macht): Schildträger halten ihn aus, Kämpfer schlagen zurück.
        CompanionTypes.MobType foe = theme;
        double worst = -1;
        for (CompanionTypes.MobType t : CompanionTypes.MobType.values()) {
            double dealt = CompanionTypes.factor(t, theme);
            if (dealt > worst) {
                worst = dealt;
                foe = t;
            }
        }
        CompanionTypes.MobType tankType = theme;
        CompanionTypes.MobType hitType = theme;
        double bestTank = Double.MAX_VALUE;
        double bestHit = -1;
        for (CompanionTypes.MobType t : CompanionTypes.MobType.values()) {
            double taken = CompanionTypes.factor(foe, t);
            if (taken < bestTank || (taken == bestTank && t == theme)) {
                bestTank = taken;
                tankType = t;
            }
            double dealt = CompanionTypes.factor(t, foe);
            if (dealt > bestHit || (dealt == bestHit && t == theme)) {
                bestHit = dealt;
                hitType = t;
            }
        }
        List<Role> roles = new ArrayList<>();
        int n = Math.max(1, size);
        roles.add(Role.TANK);
        if (n >= 2) {
            roles.add(Role.DAMAGE);
        }
        if (n >= 3) {
            roles.add(Role.SUPPORT);
        }
        if (n >= 4) {
            roles.add(Role.TANK);
        }
        for (int i = roles.size(); i < n; i++) {
            roles.add(Role.DAMAGE);
        }
        roles.sort(java.util.Comparator.comparingInt(Role::ordinal)); // Schildträger, Kämpfer, Heiler
        List<Fighter> list = new ArrayList<>();
        int damageSeen = 0;
        for (int i = 0; i < roles.size(); i++) {
            Role role = roles.get(i);
            CompanionTypes.MobType type = switch (role) {
                case TANK -> tankType;
                case SUPPORT -> theme;
                case DAMAGE -> damageSeen++ % 2 == 0 ? hitType : theme;
            };
            String label = switch (role) {
                case TANK -> "Schildträger";
                case DAMAGE -> "Kämpfer";
                case SUPPORT -> "Heiler";
            };
            String name = owner + " " + label + (roles.stream().filter(x -> x == role).count() > 1 ? " " + (i + 1) : "");
            double lb = ex.levelBonus(Math.max(1, level - i));
            double hp = (20 + stars * 12.0) * lb * power;
            double atk = (4 + stars * 1.5) * lb * power;
            switch (role) {
                case TANK -> {
                    hp *= 1.6;
                    atk *= 0.8;
                }
                case SUPPORT -> {
                    hp *= 0.9;
                    atk = Math.max(1.0, atk * 0.3);
                }
                default -> { }
            }
            boolean support = role == Role.SUPPORT;
            double heal = support ? 4.0 * lb * power : 0;
            boolean back = support || (role == Role.DAMAGE && roles.size() >= 5 && damageSeen >= 2);
            list.add(new Fighter(name, type, hp, atk, role == Role.TANK, roleEntity(role, type, name), back, support, heal));
        }
        return list;
    }

    /** Gesamtstärke wie bei den Duellen (Wurzel aus der Summe von Leben mal Angriff). */
    public static long strength(List<Fighter> team) {
        double sum = 0;
        for (Fighter f : team) {
            sum += f.hp() * f.atk();
        }
        return Math.round(Math.sqrt(sum));
    }

    /** Berechnet den Kampf. Bei Gleichstand gewinnt Seite B (der Verteidiger). */
    public static Result fight(List<Fighter> a, List<Fighter> b, Random r) {
        Script s = script(a, b, r);
        return new Result(s.aWins(), s.log());
    }

    /**
     * Wählt ein lebendes Ziel wie im Auto-Battler: Solange vorne noch jemand steht, wird die hintere Reihe kaum getroffen.
     * Verteidiger werden dreimal so oft gewählt.
     */
    private static int pick(List<Fighter> team, double[] hp, Random r) {
        boolean frontAlive = false;
        for (int i = 0; i < team.size(); i++) {
            if (hp[i] > 0 && !team.get(i).back()) {
                frontAlive = true;
            }
        }
        boolean defenderAlive = false;
        for (int i = 0; i < team.size(); i++) {
            if (hp[i] > 0 && team.get(i).defender()) {
                defenderAlive = true; // Verteidiger werden immer zuerst angegriffen
            }
        }
        double sum = 0;
        for (int i = 0; i < team.size(); i++) {
            if (hp[i] > 0 && (!defenderAlive || team.get(i).defender())) {
                sum += weight(team.get(i), frontAlive && !defenderAlive);
            }
        }
        double x = r.nextDouble() * sum;
        int last = -1;
        for (int i = 0; i < team.size(); i++) {
            if (hp[i] > 0 && (!defenderAlive || team.get(i).defender())) {
                last = i;
                x -= weight(team.get(i), frontAlive && !defenderAlive);
                if (x < 0) {
                    return i;
                }
            }
        }
        return last;
    }

    private static double weight(Fighter f, boolean frontAlive) {
        double w = f.defender() ? 3 : 1;
        return f.back() && frontAlive ? w * 0.12 : w;
    }

    private static Frame snapshot(List<Fighter> a, List<Fighter> b, double[] hpA, double[] hpB, int[] usesA, int[] usesB,
                                  int[] animalA, int[] animalB) {
        double[] fa = new double[a.size()];
        double[] fb = new double[b.size()];
        for (int i = 0; i < fa.length; i++) {
            fa[i] = hpA[i] <= 0 ? 0 : Math.max(0.02, hpA[i] / a.get(i).hp());
        }
        for (int i = 0; i < fb.length; i++) {
            fb[i] = hpB[i] <= 0 ? 0 : Math.max(0.02, hpB[i] / b.get(i).hp());
        }
        return new Frame(fa, fb, usesA.clone(), usesB.clone(), animalA.clone(), animalB.clone());
    }

    private static boolean alive(double[] hp) {
        for (double h : hp) {
            if (h > 0) {
                return true;
            }
        }
        return false;
    }

    private static double total(List<Fighter> team, double[] hp) {
        double t = 0;
        for (int i = 0; i < hp.length; i++) {
            t += Math.max(0, hp[i]) / team.get(i).hp();
        }
        return t;
    }

    private static final int ABILITY_USES = 2;
    private static final int ABILITY_FIRST = 2;
    private static final int ABILITY_GAP = 5;
    private static final int EFFECT_ROUNDS = 6;

    /** Ein Kampfabschnitt einer Seite (Leben und Wirkungen der Fähigkeiten). */
    private static final class Team {
        final List<Fighter> f;
        final double[] hp;
        final int[] atkUntil;
        final int[] guardUntil;
        final int[] uses;
        final int[] nextAt;
        final int[] animalUses; // Unterstützer: eigene Einsätze der Tierfähigkeit
        final int[] animalNext;
        int teamGuardUntil;
        int weakenedUntil;

        Team(List<Fighter> f) {
            this.f = f;
            int n = f.size();
            hp = new double[n];
            atkUntil = new int[n];
            guardUntil = new int[n];
            uses = new int[n];
            nextAt = new int[n];
            animalUses = new int[n];
            animalNext = new int[n];
            for (int i = 0; i < n; i++) {
                animalUses[i] = ABILITY_USES;
                animalNext[i] = ABILITY_FIRST;
                hp[i] = f.get(i).hp();
                uses[i] = ABILITY_USES;
                nextAt[i] = ABILITY_FIRST;
            }
        }

        int lowestAlly() {
            return lowestAlly(0.95);
        }

        int lowestAlly(double limit) {
            int best = -1;
            double ratio = limit;
            for (int i = 0; i < hp.length; i++) {
                if (hp[i] > 0 && hp[i] / f.get(i).hp() < ratio) {
                    ratio = hp[i] / f.get(i).hp();
                    best = i;
                }
            }
            return best;
        }

        void heal(int i, double amount) {
            hp[i] = Math.min(f.get(i).hp(), hp[i] + amount);
        }
    }

    /**
     * Berechnet den Kampf: Alle Kämpfer beider Seiten kämpfen gleichzeitig. Wie in den Expeditionen zählen Rollen und
     * Fähigkeiten: Verteidiger werden öfter angegriffen, Unterstützer heilen (zwei Heilungen), alle anderen setzen ihre
     * Fähigkeit ein (Schaden, Schwächen, Schutz, Stärke). Bei Gleichstand gewinnt Seite B.
     */
    public static Script script(List<Fighter> a, List<Fighter> b, Random r) {
        List<String> log = new ArrayList<>();
        List<Frame> frames = new ArrayList<>();
        if (a.isEmpty() || b.isEmpty()) {
            return new Script(!a.isEmpty(), log, frames);
        }
        Team ta = new Team(a);
        Team tb = new Team(b);
        frames.add(snapshot(a, b, ta.hp, tb.hp, ta.uses, tb.uses, ta.animalUses, tb.animalUses));
        int round = 0;
        while (alive(ta.hp) && alive(tb.hp) && round < 600) {
            round++;
            double[] toA = new double[a.size()];
            double[] toB = new double[b.size()];
            int[] hitA = new int[a.size()];
            int[] hitB = new int[b.size()];
            abilities(ta, tb, toB, hitB, round, r);
            abilities(tb, ta, toA, hitA, round, r);
            attacks(ta, tb, toB, hitB, round, r);
            attacks(tb, ta, toA, hitA, round, r);
            applyDamage(ta, toA, hitA, tb, log);
            applyDamage(tb, toB, hitB, ta, log);
            frames.add(snapshot(a, b, ta.hp, tb.hp, ta.uses, tb.uses, ta.animalUses, tb.animalUses));
        }
        boolean aAlive = alive(ta.hp);
        boolean bAlive = alive(tb.hp);
        boolean aWins = aAlive && (!bAlive || total(a, ta.hp) > total(b, tb.hp));
        return new Script(aWins, log, frames);
    }

    private static void abilities(Team me, Team foe, double[] toFoe, int[] hitFoe, int round, Random r) {
        for (int i = 0; i < me.hp.length; i++) {
            if (me.hp[i] <= 0) {
                continue;
            }
            Fighter f = me.f.get(i);
            if (f.support()) {
                // Unterstützer: heilen (zwei Einsätze) und setzen zusätzlich ihre Tierfähigkeit ein (zwei eigene Einsätze)
                if (me.uses[i] > 0 && round >= me.nextAt[i]) {
                    int t = me.lowestAlly(0.7); // erst ab 70 % Leben, damit die Einsätze nicht verpuffen
                    if (t >= 0) {
                        me.heal(t, Math.max(f.heal(), me.f.get(t).hp() * 0.25));
                        me.uses[i]--;
                        me.nextAt[i] = round + ABILITY_GAP;
                    }
                }
                if (me.animalUses[i] > 0 && round >= me.animalNext[i] && cast(me, foe, i, f, toFoe, hitFoe, round, r)) {
                    me.animalUses[i]--;
                    me.animalNext[i] = round + ABILITY_GAP;
                }
                continue;
            }
            if (me.uses[i] <= 0 || round < me.nextAt[i]) {
                continue;
            }
            if (cast(me, foe, i, f, toFoe, hitFoe, round, r)) {
                me.uses[i]--;
                me.nextAt[i] = round + ABILITY_GAP;
            }
        }
    }

    /** Die Tierfähigkeit von Kämpfer i einsetzen; false, wenn sie sich gerade nicht lohnt. */
    private static boolean cast(Team me, Team foe, int i, Fighter f, double[] toFoe, int[] hitFoe, int round, Random r) {
        CompanionPowers.Kind kind = CompanionPowers.kind(f.entity());
        switch (kind) {
            case HEAL_ALLIES -> {
                int t = me.lowestAlly();
                if (t < 0) {
                    return false;
                }
                me.heal(t, me.f.get(t).hp() * 0.2);
            }
            case SELF_HEAL -> {
                if (me.hp[i] / f.hp() > 0.7) {
                    return false;
                }
                me.heal(i, f.hp() * 0.25);
            }
            case BUFF_ALLIES -> me.teamGuardUntil = round + EFFECT_ROUNDS;
            case WEAKEN -> foe.weakenedUntil = round + EFFECT_ROUNDS;
            case SELF_GUARD -> me.guardUntil[i] = round + EFFECT_ROUNDS;
            case DAMAGE -> {
                int j = pick(foe.f, foe.hp, r);
                if (j < 0) {
                    return false;
                }
                toFoe[j] += guarded(foe, j, round, f.atk() * 3 * CompanionTypes.factor(f.type(), foe.f.get(j).type()));
                hitFoe[j] = i;
            }
            default -> me.atkUntil[i] = round + EFFECT_ROUNDS;
        }
        return true;
    }

    private static double guarded(Team target, int j, int round, double dmg) {
        double d = dmg;
        if (target.guardUntil[j] >= round) {
            d *= 0.6;
        }
        if (target.teamGuardUntil >= round) {
            d *= 0.8;
        }
        return d;
    }

    private static void attacks(Team me, Team foe, double[] toFoe, int[] hitFoe, int round, Random r) {
        for (int i = 0; i < me.hp.length; i++) {
            if (me.hp[i] <= 0) {
                continue;
            }
            int j = pick(foe.f, foe.hp, r);
            if (j < 0) {
                return;
            }
            Fighter f = me.f.get(i);
            double atk = f.atk() * (me.atkUntil[i] >= round ? 1.4 : 1.0) * (me.weakenedUntil >= round ? 0.75 : 1.0);
            double dmg = atk * CompanionTypes.factor(f.type(), foe.f.get(j).type()) * (0.85 + 0.3 * r.nextDouble());
            toFoe[j] += guarded(foe, j, round, dmg);
            hitFoe[j] = i;
        }
    }

    private static void applyDamage(Team victim, double[] to, int[] hit, Team attacker, List<String> log) {
        for (int i = 0; i < victim.hp.length; i++) {
            if (victim.hp[i] > 0) {
                victim.hp[i] -= to[i];
                if (victim.hp[i] <= 0) {
                    log.add(attacker.f.get(hit[i]).name() + " besiegt " + victim.f.get(i).name() + ".");
                }
            }
        }
    }
}
