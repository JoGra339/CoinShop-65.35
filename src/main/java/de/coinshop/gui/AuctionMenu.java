package de.coinshop.gui;

import de.coinshop.AuctionManager;
import de.coinshop.AuctionManager.Auction;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Die Hehlerwaren-Auktion: das Item der Woche, das höchste Gebot und die Knöpfe zum Bieten. */
public final class AuctionMenu extends Menu {

    public AuctionMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.auction.title"));
        Messages msg = plugin.messages();
        AuctionManager auctions = plugin.auctions();
        Auction a = auctions.current();

        if (a == null) {
            set(13, ItemUtil.icon(Material.BARRIER, msg.get("gui.auction.none-name"), List.of()), null);
        } else {
            long left = Math.max(0, a.ends() - System.currentTimeMillis());
            long min = auctions.minNextBid(a);
            List<Component> lore = new ArrayList<>(msg.getList("gui.auction.item-lore",
                    "bid", a.bidder() == null ? msg.get("gui.auction.no-bid", "min", a.minBid()) : msg.get("gui.auction.top-bid",
                            "coins", a.bid(), "player", a.bidderName() == null ? "?" : a.bidderName()),
                    "time", ItemUtil.formatDuration(left), "min", min));
            String bundle = auctions.bundleText();
            if (bundle != null) {
                lore.add(Component.text("Zugabe für den Gewinner: " + bundle, net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
            }
            // Der Gegenstand selbst mit seiner Menge
            ItemStack real = a.item().clone();
            set(13, ItemUtil.icon(real.getType(), msg.get("gui.auction.item-name", "item", ItemUtil.name(a.item()),
                    "amount", a.item().getAmount()), lore), null);
            set(4, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.auction.header-name"),
                    msg.getList("gui.auction.header-lore")), null);

            set(11, ItemUtil.icon(Material.EMERALD, msg.get("gui.auction.bid-min-name", "coins", min),
                            msg.getList("gui.auction.bid-min-lore")),
                    (p, c) -> {
                        plugin.auctions().bid(p, plugin.auctions().minNextBid(plugin.auctions().current()));
                        new AuctionMenu(plugin, p).open(p);
                    });
            set(15, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.auction.bid-custom-name"),
                            msg.getList("gui.auction.bid-custom-lore", "min", min)),
                    (p, c) -> {
                        p.closeInventory();
                        msg.send(p, "auction-ask", "min", plugin.auctions().minNextBid(plugin.auctions().current()));
                        plugin.prompts().start(p, text -> {
                            if (text.equalsIgnoreCase("abbrechen")) {
                                msg.send(p, "ask-cancelled");
                                return;
                            }
                            try {
                                plugin.auctions().bid(p, Long.parseLong(text.replace(".", "").replace(",", "").trim()));
                            } catch (NumberFormatException e) {
                                msg.send(p, "ask-invalid");
                            }
                            new AuctionMenu(plugin, p).open(p);
                        });
                    });
        }

        int pending = auctions.pendingClaims(viewer.getUniqueId());
        if (pending > 0) {
            set(22, ItemUtil.icon(Material.CHEST, msg.get("gui.auction.claim-name"),
                    msg.getList("gui.auction.claim-lore", "count", pending)), (p, c) -> {
                plugin.auctions().claim(p);
                new AuctionMenu(plugin, p).open(p);
            });
        }
        set(18, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(26, balanceIcon(viewer), null);
        fillEmpty();
    }
}
