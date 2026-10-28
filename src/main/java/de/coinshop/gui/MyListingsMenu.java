package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Listing;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * "Meine Angebote": eigene Angebote ansehen/abbrechen, Verkaufs-Slots kaufen und zur Abholung wechseln.
 * Es werden 36 Angebote pro Seite gezeigt (man kann unbegrenzt viele Slots kaufen).
 */
public final class MyListingsMenu extends Menu {

    private static final int PER_PAGE = 36;

    public MyListingsMenu(CoinShopPlugin plugin, Player viewer) {
        this(plugin, viewer, 0);
    }

    public MyListingsMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 5, plugin.messages().get("gui.mine.title", "page", page + 1));
        Messages msg = plugin.messages();
        UUID uuid = viewer.getUniqueId();

        List<Listing> all = plugin.database().getListingsOf(uuid);
        int from = Math.min(page * PER_PAGE, all.size());
        int to = Math.min(from + PER_PAGE, all.size());
        List<Listing> mine = all.subList(from, to);

        if (all.isEmpty()) {
            set(13, ItemUtil.icon(Material.BARRIER, msg.get("gui.mine.empty-name"), List.of()), null);
        }

        for (int i = 0; i < mine.size(); i++) {
            Listing listing = mine.get(i);
            ItemStack display = ItemUtil.withExtraLore(listing.item(),
                    msg.getList("gui.mine.listing-lore", "price", listing.price(),
                            "time", "dauerhaft"));

            set(i, display, (player, click) -> {
                plugin.playerShop().cancel(player, listing.id());
                new MyListingsMenu(plugin, player, page).open(player);
            });
        }

        // ---- Untere Reihe (Felder 36-44) ----
        if (page > 0) {
            set(36, ItemUtil.icon(Material.ARROW, msg.get("gui.player.prev-name"), List.of()),
                    (player, click) -> new MyListingsMenu(plugin, player, page - 1).open(player));
        }
        set(38, backButton(), (player, click) -> new PlayerShopMenu(plugin, player, 0).open(player));

        // Slot kaufen
        set(39, ItemUtil.icon(Material.EMERALD_BLOCK, msg.get("gui.mine.slots-name"),
                        plugin.playerShop().canBuySlot(uuid)
                                ? msg.getList("gui.mine.slots-lore",
                                        "used", all.size(),
                                        "total", plugin.playerShop().totalSlots(uuid),
                                        "price", plugin.playerShop().nextSlotPrice(uuid))
                                : msg.getList("gui.mine.slots-lore-max",
                                        "used", all.size(),
                                        "total", plugin.playerShop().totalSlots(uuid))),
                (player, click) -> {
                    plugin.playerShop().buySlot(player);
                    new MyListingsMenu(plugin, player, page).open(player);
                });

        // Abholung
        int claims = plugin.database().countClaims(uuid);
        set(40, ItemUtil.icon(Material.ENDER_CHEST, msg.get("gui.mine.claims-name"),
                        msg.getList("gui.mine.claims-lore", "count", claims)),
                (player, click) -> new ClaimsMenu(plugin, player).open(player));

        set(42, balanceIcon(viewer), incomeClick());

        if (to < all.size()) {
            set(44, ItemUtil.icon(Material.ARROW, msg.get("gui.player.next-name"), List.of()),
                    (player, click) -> new MyListingsMenu(plugin, player, page + 1).open(player));
        }

        fillEmpty();
    }
}
