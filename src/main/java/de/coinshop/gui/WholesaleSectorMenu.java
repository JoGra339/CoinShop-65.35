package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.WsOrder;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Deine offenen Großaufträge einer Branche (jeder Spieler hat eigene). */
public final class WholesaleSectorMenu extends Menu {

    private static final int PER_PAGE = 45;

    public WholesaleSectorMenu(CoinShopPlugin plugin, Player viewer, int sector, int page) {
        super(plugin, 6, plugin.messages().get("gui.wholesale.sector-title",
                "name", WholesaleManager.SECTORS.get(sector).name()));
        Messages msg = plugin.messages();
        WholesaleManager wh = plugin.wholesale();
        UUID uuid = viewer.getUniqueId();
        List<WsOrder> orders = wh.orders(uuid, sector);
        int pages = Math.max(1, (orders.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= orders.size()) {
                break;
            }
            WsOrder order = orders.get(index);
            Material material = Material.matchMaterial(order.material());
            if (material == null) {
                continue;
            }
            long left = order.amount() - order.delivered();
            double unit = wh.effectiveUnit(uuid, material);
            long gross = wh.grossFor(uuid, order);
            long[] bonus = wh.bonuses(uuid, sector, gross);
            java.util.List<net.kyori.adventure.text.Component> lore = msg.getList("gui.wholesale.order-lore",
                    "amount", order.amount(), "done", order.delivered(), "left", left,
                    "unit", Math.round(unit * wh.sizeFactor(order.amount()) * 100) / 100.0,
                    "gross", gross, "bonus", bonus[0] + bonus[1]);
            long rush = wh.treaties().rushExpiry(order.id());
            if (rush > 0) {
                lore.add(msg.get("gui.wholesale.rush-line", "hours",
                        Math.max(0, (rush - System.currentTimeMillis()) / 3_600_000L)));
            }
            java.util.UUID partner = wh.treaties().partnerOf(uuid);
            if (partner != null) {
                lore.add(msg.get("gui.wholesale.coop-line", "partner", plugin.database().getName(partner)));
            }
            set(i, ItemUtil.icon(material, ItemUtil.name(material), lore),
                    (player, click) -> {
                        wh.deliver(player, order.id(), !click.isRightClick());
                        new WholesaleSectorMenu(plugin, player, sector, current).open(player);
                    });
        }
        if (orders.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.wholesale.no-orders"),
                    msg.getList("gui.wholesale.no-orders-lore")), null);
        }
        set(45, backButton(), (player, click) -> new WholesaleMenu(plugin, player).open(player));
        if (current > 0) {
            set(48, ItemUtil.icon(Material.ARROW, msg.get("gui.prev-page"), List.of()),
                    (player, click) -> new WholesaleSectorMenu(plugin, player, sector, current - 1).open(player));
        }
        if (current < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, msg.get("gui.next-page"), List.of()),
                    (player, click) -> new WholesaleSectorMenu(plugin, player, sector, current + 1).open(player));
        }
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
