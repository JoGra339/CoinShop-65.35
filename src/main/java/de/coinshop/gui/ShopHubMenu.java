package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Der Shop: ein Klick zum Server-Shop oder zum Spieler-Shop. */
public final class ShopHubMenu extends Menu {

    public ShopHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.shophub.title"));
        Messages msg = plugin.messages();
        set(11, ItemUtil.icon(Material.ENDER_CHEST, msg.get("gui.main.server-name"), java.util.List.of()),
                (player, click) -> new ServerShopMenu(plugin, player).open(player));
        set(15, ItemUtil.icon(Material.EMERALD, msg.get("gui.main.player-name"), java.util.List.of()),
                (player, click) -> new PlayerShopMenu(plugin, player, 0).open(player));
        set(18, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
