package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Offer;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Der Server-Shop: bis zu 14 Angebote in zwei Reihen à 7 Felder. */
public final class ServerShopMenu extends Menu {

    // Die Felder der 14 Angebote (Reihe 2 und Reihe 3 des 5-zeiligen Menüs)
    private static final int[] OFFER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public ServerShopMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.server.title"));
        Messages msg = plugin.messages();
        UUID uuid = viewer.getUniqueId();
        int limit = plugin.getConfig().getInt("server-shop.purchase-limit-per-offer", 0);

        List<Offer> offers = plugin.serverShop().getOffers();
        if (offers.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, msg.get("gui.server.empty-name"), List.of()), null);
        }

        for (int i = 0; i < offers.size() && i < OFFER_SLOTS.length; i++) {
            Offer offer = offers.get(i);
            int bought = plugin.database().getPurchases(uuid, offer.slot());

            long shownPrice = plugin.licenses().discounted(uuid, offer.price());
            List<Component> lore = limit > 0
                    ? msg.getList("gui.server.offer-lore", "price", shownPrice, "bought", bought, "limit", limit)
                    : msg.getList("gui.server.offer-lore-unlimited", "price", shownPrice);
            lore = new java.util.ArrayList<>(lore);
            int left = plugin.serverShop().remaining(offer.slot());
            lore.add(left > 0
                    ? msg.get("gui.server.stock-line", "left", left, "total", plugin.serverShop().stockPerOffer())
                    : msg.get("gui.server.sold-out-line"));
            ItemStack display = ItemUtil.withExtraLore(offer.item(), lore);

            set(OFFER_SLOTS[i], display, (player, click) -> {
                plugin.serverShop().buy(player, offer);
                // Menü neu aufbauen, damit Kontostand und Kauf-Zähler stimmen
                new ServerShopMenu(plugin, player).open(player);
            });
        }

        // Unten: Zurück | Kontostand | Wechsel-Timer
        set(40, backButton(), (player, click) -> new ShopHubMenu(plugin, player).open(player));
        set(38, balanceIcon(viewer), incomeClick());

        Duration left = plugin.serverShop().timeUntilReset();
        set(42, ItemUtil.icon(Material.CLOCK, msg.get("gui.server.timer-name"),
                msg.getList("gui.server.timer-lore", "time", ItemUtil.formatDuration(left.toMillis()))), null);

        fillEmpty();
    }
}
