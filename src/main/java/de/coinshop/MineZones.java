package de.coinshop;

import org.bukkit.Material;

import java.util.List;
import java.util.Random;

/**
 * Tiefenzonen der Tiefenmine. Alle 10 Ebenen wechselt die Zone (Gestein, Aussehen, Erze); nach der letzten Zone beginnt die erste
 * von vorn, die Erze werden aber mit der Tiefe immer wertvoller.
 */
public final class MineZones {

    /** Eine Zone: Name, Gestein der Adern, Boden/Wand/Licht der Höhle, Erze mit Gewichten. */
    public record Zone(String name, Material rock, Material floor, Material wall, Material light, Material[] ores, int[] weights) {
    }

    public static final int DEPTHS_PER_ZONE = 10;

    public static final List<Zone> ZONES = List.of(
            new Zone("Kohlestollen", Material.STONE, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.GLOWSTONE,
                    new Material[]{Material.COAL_ORE, Material.COPPER_ORE}, new int[]{60, 40}),
            new Zone("Eisenschacht", Material.ANDESITE, Material.POLISHED_ANDESITE, Material.STONE_BRICKS, Material.GLOWSTONE,
                    new Material[]{Material.IRON_ORE, Material.COAL_ORE, Material.LAPIS_ORE}, new int[]{45, 25, 30}),
            new Zone("Goldader", Material.GRANITE, Material.POLISHED_GRANITE, Material.BRICKS, Material.GLOWSTONE,
                    new Material[]{Material.GOLD_ORE, Material.REDSTONE_ORE, Material.IRON_ORE}, new int[]{40, 30, 30}),
            new Zone("Tiefenschiefer", Material.DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.SEA_LANTERN,
                    new Material[]{Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_GOLD_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                            Material.DEEPSLATE_LAPIS_ORE}, new int[]{30, 30, 20, 20}),
            new Zone("Diamantkammern", Material.DEEPSLATE, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS, Material.SEA_LANTERN,
                    new Material[]{Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_COPPER_ORE},
                    new int[]{55, 20, 25}),
            new Zone("Sculk-Tiefe", Material.SCULK, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS, Material.SEA_LANTERN,
                    new Material[]{Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_GOLD_ORE},
                    new int[]{40, 35, 25}),
            new Zone("Netherader", Material.NETHERRACK, Material.BLACKSTONE, Material.NETHER_BRICKS, Material.SHROOMLIGHT,
                    new Material[]{Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS}, new int[]{45, 40, 15})
    );

    private MineZones() {
    }

    public static int zoneIndex(int depth) {
        return Math.max(0, (depth - 1) / DEPTHS_PER_ZONE);
    }

    public static Zone zoneOf(int depth) {
        return ZONES.get(zoneIndex(depth) % ZONES.size());
    }

    /** Wählt ein Erz der Zone nach Gewicht. */
    public static Material pick(Zone zone, Random random) {
        int total = 0;
        for (int w : zone.weights()) {
            total += w;
        }
        int roll = random.nextInt(Math.max(1, total));
        for (int i = 0; i < zone.ores().length; i++) {
            roll -= zone.weights()[i];
            if (roll < 0) {
                return zone.ores()[i];
            }
        }
        return zone.ores()[0];
    }

    private static String plain(Material ore) {
        String n = ore.name();
        return n.startsWith("DEEPSLATE_") ? n.substring("DEEPSLATE_".length()) : n;
    }

    /** Grundwert eines Erzes in Coins (vor Tiefe, Spitzhacke und Aspekten). */
    public static double baseValue(Material ore) {
        return switch (plain(ore)) {
            case "COAL_ORE" -> 2;
            case "COPPER_ORE" -> 3;
            case "IRON_ORE" -> 6;
            case "LAPIS_ORE" -> 8;
            case "REDSTONE_ORE" -> 7;
            case "NETHER_QUARTZ_ORE" -> 8;
            case "NETHER_GOLD_ORE" -> 10;
            case "GOLD_ORE" -> 14;
            case "DIAMOND_ORE" -> 40;
            case "EMERALD_ORE" -> 50;
            case "ANCIENT_DEBRIS" -> 150;
            default -> 1;
        };
    }

    public static String oreName(Material ore) {
        return switch (plain(ore)) {
            case "COAL_ORE" -> "Kohle";
            case "COPPER_ORE" -> "Kupfer";
            case "IRON_ORE" -> "Eisen";
            case "LAPIS_ORE" -> "Lapis";
            case "REDSTONE_ORE" -> "Redstone";
            case "NETHER_QUARTZ_ORE" -> "Quarz";
            case "NETHER_GOLD_ORE" -> "Nethergold";
            case "GOLD_ORE" -> "Gold";
            case "DIAMOND_ORE" -> "Diamant";
            case "EMERALD_ORE" -> "Smaragd";
            case "ANCIENT_DEBRIS" -> "Antiker Schrott";
            default -> "Erz";
        };
    }
}
