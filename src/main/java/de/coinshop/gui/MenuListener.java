package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Sorgt dafür, dass man Items aus unseren Menüs NICHT herausnehmen kann und dass Klicks
 * die richtige Aktion auslösen.
 */
public final class MenuListener implements Listener {

    private final CoinShopPlugin plugin;

    public MenuListener(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return; // kein CoinShop-Menü
        }

        // JEDEN Klick sperren (auch Shift-Klick, Zahlentasten, Doppelklick ...),
        // damit nichts aus dem Menü genommen oder hineingelegt werden kann.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Menüs mit Ablage-Funktion (Revier): Item mit dem Cursor ablegen oder per Shift-Klick hineinschieben
        if (menu.acceptsDrops()) {
            ItemStack dropped = null;
            boolean fromCursor = false;
            int invSlot = event.getSlot();
            if (event.getClickedInventory() == top) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir()
                        && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
                    dropped = cursor.clone();
                    fromCursor = true;
                }
            } else if (event.getClickedInventory() != null && event.getClick().isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && !current.getType().isAir()) {
                    dropped = current.clone();
                }
            }
            if (dropped != null) {
                final ItemStack item = dropped;
                final boolean cursorItem = fromCursor;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() != menu) {
                        return;
                    }
                    ItemStack now = cursorItem ? player.getItemOnCursor() : player.getInventory().getItem(invSlot);
                    if (now == null || !now.isSimilar(item) || now.getAmount() != item.getAmount()) {
                        return; // hat sich inzwischen verändert
                    }
                    boolean taken = menu.handleDrop(player, item);
                    if (taken) {
                        if (cursorItem) {
                            player.setItemOnCursor(new ItemStack(Material.AIR));
                        } else {
                            player.getInventory().setItem(invSlot, null);
                        }
                        menu.refresh(player);
                    }
                });
                return;
            }
        }

        // Nur Klicks auf das Menü selbst (oben) lösen Aktionen aus
        if (event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getSlot();
        ClickType click = event.getClick();
        // Die Aktion erst einen Moment später ausführen (sicherer beim Öffnen neuer Menüs).
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Ist das Menü noch offen? Sonst ignorieren.
            if (player.getOpenInventory().getTopInventory().getHolder() == menu) {
                menu.handleClick(player, slot, click);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu) {
            event.setCancelled(true);
            // Ein Stapel, der über Revier-Felder gezogen wird, zählt als Ablegen des ganzen Stapels
            if (menu.acceptsDrops() && event.getWhoClicked() instanceof Player player) {
                int top = event.getView().getTopInventory().getSize();
                boolean overMenu = event.getRawSlots().stream().anyMatch(slot -> slot < top);
                ItemStack old = event.getOldCursor();
                if (overMenu && old != null && !old.getType().isAir()) {
                    final ItemStack item = old.clone();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.getOpenInventory().getTopInventory().getHolder() == menu) {
                            menu.handleDrop(player, item);
                        }
                    });
                }
            }
        }
    }
}
