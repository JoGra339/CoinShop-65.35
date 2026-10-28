package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Die Hinterhof-Börse: Habibi-Kryptobörse oder Schmuggelrouten. */
public final class BackyardMenu extends Menu {

    public BackyardMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.backyard.title"));
        Messages msg = plugin.messages();
        set(11, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.backyard.exchange"), java.util.List.of()),
                (p, c) -> new CryptoMenu(plugin, p, de.coinshop.CryptoManager.DUNKEL).open(p));
        set(15, ItemUtil.icon(Material.CHEST_MINECART, msg.get("gui.backyard.routes"), java.util.List.of()),
                (p, c) -> new RoutesMenu(plugin, p).open(p));
        set(18, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
