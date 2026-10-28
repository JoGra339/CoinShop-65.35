package de.coinshop.gui;

import de.coinshop.BlackMarketManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.Database.BlackOffer;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Der Schwarzmarkt: 10 Angebote (je nur ein Stück), Verkaufsmenü und Kopfgelder. */
public final class BlackMarketMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public BlackMarketMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.blackmarket.title"));
        Messages msg = plugin.messages();
        BlackMarketManager market = plugin.blackMarket();
        UUID uuid = viewer.getUniqueId();

        if (!market.unlocked(viewer)) {
            set(22, ItemUtil.icon(Material.BARRIER, msg.get("gui.blackmarket.locked-title"),
                    msg.getList("gui.blackmarket.locked-lore")), (p, c) -> new LicenseMenu(plugin, p).open(p));
        } else {
            List<BlackOffer> offers = market.offers();
            for (int i = 0; i < offers.size() && i < SLOTS.length; i++) {
                BlackOffer offer = offers.get(i);
                ItemStack shown = offer.item().clone();
                List<Component> lore = new ArrayList<>();
                List<Component> old = shown.lore();
                if (old != null) {
                    lore.addAll(old);
                }
                if (offer.sold()) {
                    lore.addAll(msg.getList("gui.blackmarket.sold-lore"));
                    shown.editMeta(meta -> {
                        meta.displayName(msg.get("gui.blackmarket.sold"));
                        meta.lore(lore);
                    });
                    set(SLOTS[i], shown, null);
                } else {
                    long price = market.priceFor(uuid, offer.price());
                    long fee = market.feeFor(uuid, price, market.feePercent());
                    lore.addAll(msg.getList("gui.blackmarket.offer-lore", "price", price, "fee", fee,
                            "chance", market.chancePercent(uuid)));
                    shown.editMeta(meta -> meta.lore(lore));
                    set(SLOTS[i], shown, (p, c) -> {
                        market.buy(p, offer.slot(), offer.stamp());
                        new BlackMarketMenu(plugin, p).open(p);
                    });
                }
            }
        }
        set(36, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(44, balanceIcon(viewer), null);
        fillEmpty();
    }
}
