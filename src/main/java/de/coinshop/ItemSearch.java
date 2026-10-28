package de.coinshop;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;
import java.util.Map;

/**
 * Die Suche im Spieler-Shop. Ein Item passt, wenn JEDES eingegebene Wort im Namen, im eigenen Namen
 * oder in den Verzauberungen des Items vorkommt. Weil der Server die Item-Namen nur auf Englisch kennt,
 * werden die wichtigsten deutschen Wörter übersetzt (z. B. "Diamant" -> "diamond").
 */
public final class ItemSearch {

    private static final Map<String, String> GERMAN = Map.ofEntries(
            Map.entry("diamant", "diamond"), Map.entry("eisen", "iron"), Map.entry("gold", "gold"),
            Map.entry("golden", "golden"), Map.entry("netherit", "netherite"), Map.entry("schwert", "sword"),
            Map.entry("spitzhacke", "pickaxe"), Map.entry("axt", "axe"), Map.entry("schaufel", "shovel"),
            Map.entry("hacke", "hoe"), Map.entry("helm", "helmet"), Map.entry("brustplatte", "chestplate"),
            Map.entry("beinschutz", "leggings"), Map.entry("hose", "leggings"), Map.entry("stiefel", "boots"),
            Map.entry("bogen", "bow"), Map.entry("armbrust", "crossbow"), Map.entry("stein", "stone"),
            Map.entry("holz", "log"), Map.entry("bretter", "planks"), Map.entry("erz", "ore"),
            Map.entry("barren", "ingot"), Map.entry("apfel", "apple"), Map.entry("brot", "bread"),
            Map.entry("fleisch", "beef"), Map.entry("buch", "book"), Map.entry("smaragd", "emerald"),
            Map.entry("kohle", "coal"), Map.entry("quarz", "quartz"), Map.entry("trank", "potion"),
            Map.entry("pfeil", "arrow"), Map.entry("fackel", "torch"), Map.entry("eimer", "bucket"),
            Map.entry("leiter", "ladder"), Map.entry("tür", "door"), Map.entry("kiste", "chest"),
            Map.entry("ofen", "furnace"), Map.entry("bett", "bed"), Map.entry("glas", "glass"),
            Map.entry("kies", "gravel"), Map.entry("lehm", "clay"), Map.entry("wolle", "wool"),
            Map.entry("samen", "seeds"), Map.entry("weizen", "wheat"), Map.entry("karotte", "carrot"),
            Map.entry("kartoffel", "potato"), Map.entry("fisch", "fish"), Map.entry("schild", "shield"),
            Map.entry("schutt", "debris"), Map.entry("fass", "barrel"), Map.entry("angel", "fishing rod"),
            Map.entry("kupfer", "copper"), Map.entry("verzaubert", "enchanted"), Map.entry("blume", "flower"),
            Map.entry("laterne", "lantern"), Map.entry("knochen", "bone"), Map.entry("faden", "string"),
            Map.entry("feder", "feather"), Map.entry("schienen", "rail"), Map.entry("lore", "minecart"),
            Map.entry("elytra", "elytra"), Map.entry("totem", "totem"), Map.entry("perle", "pearl"),
            Map.entry("kürbis", "pumpkin"), Map.entry("melone", "melon"), Map.entry("zucker", "sugar"),
            Map.entry("ei", "egg"), Map.entry("milch", "milk"), Map.entry("wasser", "water"),
            Map.entry("lava", "lava"), Map.entry("schwarz", "black"), Map.entry("weiß", "white"),
            Map.entry("weiss", "white"), Map.entry("rot", "red"), Map.entry("blau", "blue"),
            Map.entry("grün", "green"), Map.entry("gelb", "yellow"));

    private ItemSearch() {
    }

    /** Passt das Item zur Suche? Eine leere Suche passt immer. */
    public static boolean matches(ItemStack item, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = haystack(item);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            String english = GERMAN.get(word);
            boolean found = haystack.contains(word) || (english != null && haystack.contains(english));
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static String haystack(ItemStack item) {
        StringBuilder sb = new StringBuilder(item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        try {
            if (item.hasItemMeta()) {
                var meta = item.getItemMeta();
                if (meta.hasDisplayName() && meta.displayName() != null) {
                    sb.append(' ').append(PlainTextComponentSerializer.plainText()
                            .serialize(meta.displayName()).toLowerCase(Locale.ROOT));
                }
                for (Enchantment e : meta.getEnchants().keySet()) {
                    sb.append(" enchanted ").append(e.getKey().getKey().replace('_', ' '));
                }
                if (meta instanceof EnchantmentStorageMeta storage) {
                    for (Enchantment e : storage.getStoredEnchants().keySet()) {
                        sb.append(" enchanted ").append(e.getKey().getKey().replace('_', ' '));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // dann zählt nur der Material-Name
        }
        return sb.toString();
    }
}
