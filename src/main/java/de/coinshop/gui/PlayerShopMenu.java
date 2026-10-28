package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Listing;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Der Spieler-Shop: alle Angebote, 45 pro Seite, unten die Knöpfe. */
public final class PlayerShopMenu extends Menu {

    private static final int PER_PAGE = 45;

    public PlayerShopMenu(CoinShopPlugin plugin, Player viewer, int page) {
        this(plugin, viewer, page, null);
    }

    /** "search" = Suchbegriff (null oder leer = alle Angebote zeigen). */
    public PlayerShopMenu(CoinShopPlugin plugin, Player viewer, int page, String search) {
        super(plugin, 6, plugin.messages().get("gui.player.title", "page", page + 1));
        Messages msg = plugin.messages();
        var shop = plugin.playerShop();
        boolean searching = search != null && !search.isBlank();

        int total;
        List<Listing> listings;
        if (searching) {
            List<Listing> found = new java.util.ArrayList<>();
            for (Listing l : plugin.database().getListings(0, Integer.MAX_VALUE)) {
                if (de.coinshop.ItemSearch.matches(l.item(), search)) {
                    found.add(l);
                }
            }
            total = found.size();
            int from = Math.min(found.size(), page * PER_PAGE);
            listings = found.subList(from, Math.min(found.size(), from + PER_PAGE));
        } else {
            total = plugin.database().countListings();
            listings = plugin.database().getListings(page * PER_PAGE, PER_PAGE);
        }

        if (listings.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, msg.get("gui.player.empty-name"), List.of()), null);
        }

        for (int i = 0; i < listings.size(); i++) {
            Listing listing = listings.get(i);
            boolean own = listing.seller().equals(viewer.getUniqueId());

            var lore = own
                    ? msg.getList("gui.player.own-listing-lore", "price", listing.price())
                    : msg.getList("gui.player.listing-lore", "price", listing.price(),
                            "seller", listing.sellerName(), "time", "dauerhaft");
            ItemStack display = ItemUtil.withExtraLore(listing.item(), lore);

            set(i, display, (player, click) -> {
                shop.buy(player, listing.id());
                new PlayerShopMenu(plugin, player, page, search).open(player);
            });
        }

        // ---- Untere Reihe (Felder 45-53) ----
        if (page > 0) {
            set(45, ItemUtil.icon(Material.ARROW, msg.get("gui.player.prev-name"), List.of()),
                    (player, click) -> new PlayerShopMenu(plugin, player, page - 1, search).open(player));
        }
        set(46, backButton(), (player, click) -> new ShopHubMenu(plugin, player).open(player));

        // Suche: Linksklick = Suchbegriff eingeben, Rechtsklick = Suche löschen
        set(47, ItemUtil.icon(Material.SPYGLASS, msg.get("gui.player.search-name"),
                        searching ? msg.getList("gui.player.search-lore-active", "term", search)
                                : msg.getList("gui.player.search-lore")),
                (player, click) -> {
                    if (click.isRightClick()) {
                        new PlayerShopMenu(plugin, player, 0, null).open(player);
                        return;
                    }
                    player.closeInventory();
                    plugin.messages().send(player, "search-prompt");
                    plugin.prompts().start(player, text -> {
                        if (text.equalsIgnoreCase("abbrechen")) {
                            plugin.messages().send(player, "ask-cancelled");
                            new PlayerShopMenu(plugin, player, 0, search).open(player);
                        } else {
                            new PlayerShopMenu(plugin, player, 0, text).open(player);
                        }
                    });
                });

        set(48, ItemUtil.icon(Material.CHEST, msg.get("gui.player.mine-name"), msg.getList("gui.player.mine-lore")),
                (player, click) -> new MyListingsMenu(plugin, player).open(player));

        set(49, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.player.sell-name"),
                        msg.getList("gui.player.sell-lore",
                                "fee_percent", shop.feePercent(viewer.getUniqueId()),
                                "min_fee", shop.minFee(viewer.getUniqueId()),
                                "tax_percent", shop.taxPercent(viewer.getUniqueId()))),
                (player, click) -> shop.startPricePrompt(player));

        set(50, balanceIcon(viewer), incomeClick());

        if ((page + 1) * PER_PAGE < total) {
            set(53, ItemUtil.icon(Material.ARROW, msg.get("gui.player.next-name"), List.of()),
                    (player, click) -> new PlayerShopMenu(plugin, player, page + 1, search).open(player));
        }

        fillEmpty();
    }
}
