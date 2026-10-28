package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Der Großhandelsmarkt: zehn Branchen. Ein Klick öffnet die persönlichen Aufträge der Branche. */
public final class WholesaleMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public WholesaleMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.wholesale.title"));
        Messages msg = plugin.messages();
        WholesaleManager wh = plugin.wholesale();
        UUID uuid = viewer.getUniqueId();
        for (int s = 0; s < WholesaleManager.SECTORS.size(); s++) {
            final int sector = s;
            WholesaleManager.Sector sec = WholesaleManager.SECTORS.get(s);
            int slot = SLOTS[s];
            if (!wh.hasSector(uuid, s)) {
                set(slot, ItemUtil.icon(Material.BARRIER, msg.get("gui.wholesale.locked-name", "name", sec.name()),
                                msg.getList("gui.wholesale.locked-lore", "theme", sec.theme(),
                                        "price", wh.priceText(wh.unlockPrice(uuid)))),
                        (player, click) -> {
                            wh.unlockSector(player, sector);
                            new WholesaleMenu(plugin, player).open(player);
                        });
                continue;
            }
            UUID holder = wh.shareholder(s);
            set(slot, ItemUtil.icon(sec.icon(), msg.get("gui.wholesale.sector-name", "name", sec.name()),
                            msg.getList("gui.wholesale.sector-lore",
                                    "theme", sec.theme(),
                                    "open", wh.orders(uuid, s).size(),
                                    "done", wh.weeklyDone(uuid, s),
                                    "holder", holder == null ? "-" : plugin.database().getName(holder))),
                    (player, click) -> new WholesaleSectorMenu(plugin, player, sector, 0).open(player));
        }
        set(27, backButton(), (player, click) -> new WholesaleHubMenu(plugin, player).open(player));
        set(35, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
