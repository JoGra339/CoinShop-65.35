package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RouteManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Die Schmugglerrouten (bis zu 32): nacheinander kaufen, je Route 5 Stufen, bringen täglich Habibi-Krypto. */
public final class RoutesMenu extends Menu {

    public RoutesMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.routes.title"));
        Messages msg = plugin.messages();
        RouteManager routes = plugin.routes();
        UUID uuid = viewer.getUniqueId();
        int have = routes.count(uuid);
        for (int idx = 1; idx <= routes.max() && idx <= 32; idx++) {
            int slot = idx - 1;
            String name = routes.name(idx);
            final int index = idx;
            if (idx <= have) {
                int level = routes.level(uuid, idx);
                if (level >= RouteManager.MAX_LEVEL) {
                    set(slot, ItemUtil.icon(Material.DIAMOND_BLOCK, msg.get("gui.routes.owned-name", "name", name),
                            msg.getList("gui.routes.maxed-lore", "level", level,
                                    "units", CryptoManager.fmt(routes.rewardAt(idx, level)))), null);
                } else {
                    set(slot, ItemUtil.icon(Material.EMERALD_BLOCK, msg.get("gui.routes.owned-name", "name", name),
                                    msg.getList("gui.routes.owned-lore", "level", level,
                                            "units", CryptoManager.fmt(routes.rewardAt(idx, level)),
                                            "next", CryptoManager.fmt(routes.rewardAt(idx, level + 1)),
                                            "price", routes.upgradePrice(idx, level))),
                            (player, click) -> {
                                routes.upgrade(player, index);
                                new RoutesMenu(plugin, player).open(player);
                            });
                }
            } else if (idx == have + 1) {
                set(slot, ItemUtil.icon(Material.CHEST_MINECART, msg.get("gui.routes.buy-name", "name", name),
                                msg.getList("gui.routes.buy-lore", "price", routes.price(idx),
                                        "units", CryptoManager.fmt(routes.rewardAt(idx, 1)), "catch", routes.catchPercent())),
                        (player, click) -> {
                            routes.buy(player);
                            new RoutesMenu(plugin, player).open(player);
                        });
            } else {
                set(slot, ItemUtil.icon(Material.BARRIER, msg.get("gui.routes.locked-name", "name", name),
                        msg.getList("gui.routes.locked-lore", "price", routes.price(idx))), null);
            }
        }
        set(45, backButton(), (player, click) -> new BackyardMenu(plugin, player).open(player));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
