package de.coinshop;

import de.coinshop.ExpeditionManager.Companion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Aspekte der Begleiter: Jede Begleiterart bringt einen Aspekt für normale Expeditionslevel und einen anderen für Begleiterreisen.
 * Jedes Exemplar trägt seinen Wert bei (mit dem Level des Exemplars wächst er), die Summe ist nach oben begrenzt.
 */
public final class CompanionAspects {

    private CompanionAspects() {
    }

    /** Wirkung im Expeditionslevel, solange die Begleiter mitkommen. */
    public enum LevelKind {
        DAMAGE("mehr Schaden gegen Kreaturen", 0.6),
        DEFENSE("weniger erlittener Schaden", 0.5),
        COINS("mehr Coins pro Level", 1.0),
        REGEN("Leben pro Sekunde für dich", 0.6);

        public final String text;
        public final double cap;

        LevelKind(String text, double cap) {
            this.text = text;
            this.cap = cap;
        }
    }

    /** Wirkung auf Begleiterreisen, wenn das Exemplar mitreist. */
    public enum TripKind {
        KILLS("mehr besiegte Kreaturen", 1.0),
        YIELD("mehr Beute", 1.0),
        SAFE("weniger Verletzungsrisiko", 0.2),
        COINMOB("mehr Chance auf CoinMobs", 0.3),
        LOOTMOB("mehr Chance auf Loot-Mobs", 0.03),
        XP("mehr Erfahrung für die Mitreisenden", 2.0);

        public final String text;
        public final double cap;

        TripKind(String text, double cap) {
            this.text = text;
            this.cap = cap;
        }
    }

    public record LevelAspect(LevelKind kind, double base) { }

    public record TripAspect(TripKind kind, double base) { }

    private static final Map<String, LevelAspect> LEVEL = new HashMap<>();
    private static final Map<String, TripAspect> TRIP = new HashMap<>();

    private static void fix(String key, LevelKind lk, double lb, TripKind tk, double tb) {
        LEVEL.put(key, new LevelAspect(lk, lb));
        TRIP.put(key, new TripAspect(tk, tb));
    }

    static {
        fix("wolf", LevelKind.DAMAGE, 0.02, TripKind.KILLS, 0.03);
        fix("cat", LevelKind.DEFENSE, 0.01, TripKind.SAFE, 0.01);
        fix("fox", LevelKind.COINS, 0.015, TripKind.COINMOB, 0.01);
        fix("bee", LevelKind.REGEN, 0.10, TripKind.YIELD, 0.02);
        fix("irongolem", LevelKind.DEFENSE, 0.03, TripKind.SAFE, 0.03);
        fix("polarbear", LevelKind.DAMAGE, 0.025, TripKind.KILLS, 0.035);
        fix("skeleton", LevelKind.DAMAGE, 0.015, TripKind.KILLS, 0.025);
        fix("witch", LevelKind.REGEN, 0.15, TripKind.XP, 0.05);
        fix("llama", LevelKind.DEFENSE, 0.015, TripKind.YIELD, 0.025);
        fix("allay", LevelKind.REGEN, 0.06, TripKind.SAFE, 0.025);
        fix("goat", LevelKind.DAMAGE, 0.02, TripKind.XP, 0.04);
        fix("panda", LevelKind.REGEN, 0.12, TripKind.SAFE, 0.015);
        fix("rabbit", LevelKind.COINS, 0.01, TripKind.COINMOB, 0.015);
        fix("snowman", LevelKind.DEFENSE, 0.01, TripKind.COINMOB, 0.01);
        fix("vex", LevelKind.DAMAGE, 0.025, TripKind.LOOTMOB, 0.0005);
        fix("witherskeleton", LevelKind.DAMAGE, 0.03, TripKind.KILLS, 0.04);
        fix("piglinbrute", LevelKind.COINS, 0.025, TripKind.YIELD, 0.03);
        fix("ravager", LevelKind.DAMAGE, 0.035, TripKind.KILLS, 0.045);
        fix("vindicator", LevelKind.DAMAGE, 0.025, TripKind.YIELD, 0.02);
        fix("pillager", LevelKind.COINS, 0.02, TripKind.LOOTMOB, 0.0005);
        fix("bogged", LevelKind.DEFENSE, 0.015, TripKind.XP, 0.03);
    }

    /** Aspekt im Level: die ersten 21 Arten haben feste Werte, alle anderen bekommen einen aus Art und Sternen. */
    public static LevelAspect level(Companion c) {
        LevelAspect fixed = LEVEL.get(c.key);
        if (fixed != null) {
            return fixed;
        }
        int h = Math.abs(c.key.hashCode());
        LevelKind kind = LevelKind.values()[h % LevelKind.values().length];
        double unit = switch (kind) {
            case DAMAGE -> 0.012;
            case DEFENSE -> 0.007;
            case COINS -> 0.008;
            case REGEN -> 0.07;
        };
        return new LevelAspect(kind, unit * (1 + 0.18 * c.stars));
    }

    public static TripAspect trip(Companion c) {
        TripAspect fixed = TRIP.get(c.key);
        if (fixed != null) {
            return fixed;
        }
        int h = Math.abs((c.key + "#trip").hashCode());
        TripKind kind = TripKind.values()[h % TripKind.values().length];
        double unit = switch (kind) {
            case KILLS -> 0.018;
            case YIELD -> 0.012;
            case SAFE -> 0.008;
            case COINMOB -> 0.006;
            case LOOTMOB -> 0.0003;
            case XP -> 0.03;
        };
        return new TripAspect(kind, unit * (1 + 0.18 * c.stars));
    }

    /** Wert eines Exemplars: wächst mit seinem Level (+5 % pro Level). */
    public static double scaled(double base, int level) {
        return base * (1.0 + 0.05 * (Math.max(1, level) - 1));
    }

    public static String percent(double fraction) {
        return String.format(Locale.GERMANY, "%.1f %%", fraction * 100);
    }

    public static String levelText(Companion c) {
        LevelAspect a = level(c);
        if (a.kind() == LevelKind.REGEN) {
            return "+" + String.format(Locale.GERMANY, "%.2f", a.base()) + " " + a.kind().text + " je Exemplar";
        }
        String note = "";
        if (a.kind() == LevelKind.DAMAGE || a.kind() == LevelKind.DEFENSE) {
            StringBuilder sb = new StringBuilder();
            CompanionTypes.MobType own = c.mobType();
            for (CompanionTypes.MobType t : CompanionTypes.MobType.values()) {
                if (own.strongAgainst().contains(t) || (a.kind() == LevelKind.DEFENSE && own == t)) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(t.label);
                }
            }
            note = " (voll gegen " + sb + "-Gegner, sonst schwächer)";
        }
        return "+" + percent(a.base()) + " " + a.kind().text + " je Exemplar" + note;
    }

    public static String tripText(Companion c) {
        TripAspect a = trip(c);
        if (a.kind() == TripKind.LOOTMOB) {
            return "+" + String.format(Locale.GERMANY, "%.2f", a.base() * 1000) + " ‰ " + a.kind().text + " je Exemplar";
        }
        return "+" + percent(a.base()) + " " + a.kind().text + " je Exemplar";
    }
}
