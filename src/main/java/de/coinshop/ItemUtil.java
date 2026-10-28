package de.coinshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Kleine Helfer rund um Items. */
public final class ItemUtil {

    private ItemUtil() {
    }

    /** Ein Menü-Symbol mit Name und Beschreibung. */
    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
        });
        return item;
    }

    /** Graue Glasscheibe als "leerer" Platzhalter in Menüs. */
    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.text(" ")));
        return item;
    }

    /** Kopie eines Items, an dessen Beschreibung weitere Zeilen angehängt werden (für die Menü-Anzeige). */
    public static ItemStack withExtraLore(ItemStack base, List<Component> extra) {
        ItemStack copy = base.clone();
        copy.editMeta(meta -> {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.addAll(extra);
            meta.lore(lore);
        });
        return copy;
    }

    /**
     * Name eines Items als Text-Baustein. Der Minecraft-Client übersetzt ihn selbst in die
     * Sprache des Spielers (z. B. "Diamond" -> "Diamant").
     */
    public static Component name(Material material) {
        NamespacedKey key = material.getKey();
        String prefix = material.isBlock() ? "block." : "item.";
        return Component.translatable(prefix + key.getNamespace() + "." + key.getKey());
    }

    /** Name eines Items; hat es einen eigenen Namen (Amboss-Umbenennung), wird dieser benutzt. */
    public static Component name(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().displayName();
        }
        return name(item.getType());
    }

    /** Passt das Item komplett in das Inventar? (Prüfung VOR dem Bezahlen) */
    public static boolean canFit(Inventory inventory, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                remaining -= item.getMaxStackSize();
            } else if (slot.isSimilar(item)) {
                remaining -= slot.getMaxStackSize() - slot.getAmount();
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    /** Millisekunden als "3 Std. 12 Min." */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long totalMinutes = millis / 60_000L;
        long days = totalMinutes / (60 * 24);
        long hours = (totalMinutes / 60) % 24;
        long minutes = totalMinutes % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" Tg. ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append(" Std. ");
        }
        sb.append(minutes).append(" Min.");
        return sb.toString();
    }
}
