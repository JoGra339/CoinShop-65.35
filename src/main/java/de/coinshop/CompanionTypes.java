package de.coinshop;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Typen der Kreaturen und Begleiter (wie bei Pokémon): Manche Typen sind gegen andere stark oder schwach. */
public final class CompanionTypes {

    private CompanionTypes() {
    }

    public enum MobType {
        FEUER("Feuer", NamedTextColor.RED),
        WASSER("Wasser", NamedTextColor.BLUE),
        NATUR("Natur", NamedTextColor.GREEN),
        ERDE("Erde", NamedTextColor.GOLD),
        EIS("Eis", NamedTextColor.AQUA),
        LUFT("Luft", NamedTextColor.WHITE),
        UNTOTE("Untot", NamedTextColor.DARK_GREEN),
        FINSTER("Finster", NamedTextColor.DARK_PURPLE);

        public final String label;
        public final NamedTextColor color;

        MobType(String label, NamedTextColor color) {
            this.label = label;
            this.color = color;
        }

        /** Gegen diese Typen ist man stark (1,5-facher Schaden). */
        public Set<MobType> strongAgainst() {
            return switch (this) {
                case FEUER -> EnumSet.of(NATUR, EIS, UNTOTE);
                case WASSER -> EnumSet.of(FEUER, ERDE);
                case NATUR -> EnumSet.of(WASSER, ERDE);
                case ERDE -> EnumSet.of(FEUER, LUFT);
                case EIS -> EnumSet.of(NATUR, LUFT, ERDE);
                case LUFT -> EnumSet.of(NATUR, FINSTER);
                case UNTOTE -> EnumSet.of(WASSER, LUFT);
                case FINSTER -> EnumSet.of(UNTOTE, ERDE);
            };
        }
    }

    private static final Map<String, MobType> TYPES = new HashMap<>();

    private static void put(MobType type, String... names) {
        for (String n : names) {
            TYPES.put(n, type);
        }
    }

    static {
        put(MobType.FEUER, "BLAZE", "GHAST", "MAGMA_CUBE", "STRIDER", "HOGLIN", "ZOGLIN", "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN");
        put(MobType.WASSER, "COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH", "SQUID", "GLOW_SQUID", "DOLPHIN", "GUARDIAN",
                "ELDER_GUARDIAN", "AXOLOTL", "TURTLE", "TADPOLE", "FROG", "NAUTILUS");
        put(MobType.NATUR, "BEE", "CAT", "OCELOT", "FOX", "WOLF", "RABBIT", "PANDA", "COW", "SHEEP", "PIG", "HORSE", "DONKEY",
                "MULE", "LLAMA", "TRADER_LLAMA", "CAMEL", "SNIFFER", "MOOSHROOM", "VILLAGER", "WANDERING_TRADER", "SLIME",
                "SPIDER", "CAVE_SPIDER", "CREEPER", "COPPER_GOLEM");
        put(MobType.ERDE, "IRON_GOLEM", "GOAT", "ARMADILLO", "RAVAGER", "SILVERFISH", "SULFUR_CUBE");
        put(MobType.EIS, "POLAR_BEAR", "SNOW_GOLEM", "STRAY");
        put(MobType.LUFT, "BAT", "PARROT", "CHICKEN", "ALLAY", "VEX", "BREEZE", "HAPPY_GHAST");
        put(MobType.UNTOTE, "ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "BOGGED", "PHANTOM", "SKELETON_HORSE",
                "ZOMBIE_HORSE", "WITHER_SKELETON", "ZOMBIE_NAUTILUS", "PARCHED", "CAMEL_HUSK");
        put(MobType.FINSTER, "ENDERMAN", "ENDERMITE", "SHULKER", "WARDEN", "WITCH", "EVOKER", "ILLUSIONER", "PILLAGER",
                "VINDICATOR", "WITHER", "CREAKING");
    }

    public static MobType of(String entityTypeName) {
        return TYPES.getOrDefault(entityTypeName, MobType.ERDE);
    }

    /** Schadensfaktor des Angreifers gegen den Verteidiger: 1,5 stark, 0,7 schwach, sonst 1. */
    public static double factor(MobType attacker, MobType defender) {
        if (attacker.strongAgainst().contains(defender)) {
            return 1.5;
        }
        if (defender.strongAgainst().contains(attacker)) {
            return 0.7;
        }
        return 1.0;
    }

    public static String describe(MobType t) {
        StringBuilder sb = new StringBuilder("stark gegen ");
        boolean first = true;
        for (MobType s : t.strongAgainst()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(s.label);
            first = false;
        }
        return sb.toString();
    }
}
