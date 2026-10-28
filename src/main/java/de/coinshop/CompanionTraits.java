package de.coinshop;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seltenheit und Eigenschaften einzelner Begleiter-Exemplare. */
public final class CompanionTraits {

    private CompanionTraits() {
    }

    public enum Rarity {
        NORMAL("Normal", NamedTextColor.WHITE, 1.0, 1.0, 0, 0),
        SELTEN("Selten", NamedTextColor.AQUA, 1.1, 1.2, 0, 0),
        EPISCH("Episch", NamedTextColor.LIGHT_PURPLE, 1.25, 1.5, 1, 1),
        LEGENDAER("Legendär", NamedTextColor.GOLD, 1.5, 2.0, 1, 2);

        public final String label;
        public final NamedTextColor color;
        /** Faktor auf Leben und Schaden. */
        public final double stats;
        /** Faktor auf die Aspekt-Werte. */
        public final double aspect;
        /** Zusätzliche Eigenschaften-Plätze. */
        public final int extraTraits;
        /** Zusätzliche Gefolge-Kosten (Sterne). */
        public final int stars;

        Rarity(String label, NamedTextColor color, double stats, double aspect, int extraTraits, int stars) {
            this.label = label;
            this.color = color;
            this.stats = stats;
            this.aspect = aspect;
            this.extraTraits = extraTraits;
            this.stars = stars;
        }

        public static Rarity of(int ordinal) {
            Rarity[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, ordinal))];
        }
    }

    public enum Trait {
        MUTIG("Mutig", "+10 % Schaden", 0.10, 0, 0, 0, 0, 0, 0, 0, 0),
        ZAEH("Zäh", "+15 % Leben", 0, 0.15, 0, 0, 0, 0, 0, 0, 0),
        FLINK("Flink", "+10 % Tempo", 0, 0, 0.10, 0, 0, 0, 0, 0, 0),
        GLUECKSPILZ("Glückspilz", "+1,5 % Coins pro Level", 0, 0, 0, 0.015, 0, 0, 0, 0, 0),
        FLEISSIG("Fleißig", "+25 % Großhandels-Lieferung", 0, 0, 0, 0, 0.25, 0, 0, 0, 0),
        ABENTEURER("Abenteurer", "+25 % Erfahrung und +3 % Beute auf Reisen", 0, 0, 0, 0, 0, 0.25, 0, 0.03, 0),
        WACHSAM("Wachsam", "-3 Prozentpunkte Verletzungsrisiko auf Reisen", 0, 0, 0, 0, 0, 0, 0.03, 0, 0),
        KAEMPFER("Kämpfer", "+5 % Schaden, +5 % Kreaturen auf Reisen", 0.05, 0, 0, 0, 0, 0, 0, 0, 0.05),
        RIESE("Riese", "+25 % Leben, -5 % Tempo", 0, 0.25, -0.05, 0, 0, 0, 0, 0, 0),
        SAMMLER("Sammler", "+10 % Großhandels-Lieferung, +2 % Beute auf Reisen", 0, 0, 0, 0, 0.10, 0, 0, 0.02, 0),
        SCHWAECHLICH("Schwächlich", "-10 % Leben", 0, -0.10, 0, 0, 0, 0, 0, 0, 0),
        FAUL("Faul", "-20 % Großhandels-Lieferung", 0, 0, 0, 0, -0.20, 0, 0, 0, 0);

        public final String label;
        public final String text;
        public final double dmg;
        public final double hp;
        public final double speed;
        public final double levelCoins;
        public final double work;
        public final double tripXp;
        public final double tripSafe;
        public final double tripYield;
        public final double tripKills;

        Trait(String label, String text, double dmg, double hp, double speed, double levelCoins, double work,
              double tripXp, double tripSafe, double tripYield, double tripKills) {
            this.label = label;
            this.text = text;
            this.dmg = dmg;
            this.hp = hp;
            this.speed = speed;
            this.levelCoins = levelCoins;
            this.work = work;
            this.tripXp = tripXp;
            this.tripSafe = tripSafe;
            this.tripYield = tripYield;
            this.tripKills = tripKills;
        }

        public boolean negative() {
            return this == SCHWAECHLICH || this == FAUL;
        }

        public static Trait byName(String name) {
            for (Trait t : values()) {
                if (t.name().equals(name)) {
                    return t;
                }
            }
            return null;
        }
    }

    /** Würfelt die Seltenheit (Gewichte in der Config unter expedition.rarity-weights). */
    public static Rarity rollRarity(Random r, List<Integer> weights) {
        int[] w = {70, 22, 7, 1};
        if (weights != null && weights.size() >= 4) {
            for (int i = 0; i < 4; i++) {
                w[i] = Math.max(0, weights.get(i));
            }
        }
        int sum = w[0] + w[1] + w[2] + w[3];
        if (sum <= 0) {
            return Rarity.NORMAL;
        }
        int x = r.nextInt(sum);
        for (int i = 0; i < 4; i++) {
            if (x < w[i]) {
                return Rarity.of(i);
            }
            x -= w[i];
        }
        return Rarity.NORMAL;
    }

    /** Würfelt die Eigenschaften eines neuen Exemplars: 0 bis 2 (bei hoher Seltenheit eine mehr). */
    public static List<Trait> rollTraits(Random r, Rarity rarity) {
        int roll = r.nextInt(100);
        int count = roll < 45 ? 0 : roll < 80 ? 1 : 2;
        count = Math.min(3, count + rarity.extraTraits);
        List<Trait> pool = new ArrayList<>(List.of(Trait.values()));
        List<Trait> out = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Trait t = pool.remove(r.nextInt(pool.size()));
            if (t.negative() && r.nextInt(100) >= 25) {
                i--; // schlechte Eigenschaften sind seltener
                continue;
            }
            out.add(t);
        }
        return out;
    }
}
