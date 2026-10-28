package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Der Inhaber legt fest, welche seiner Aufträge die Angestellten bearbeiten dürfen. */
public final class CompanyOrdersMenu extends Menu {

    private static final int PER_PAGE = 45;

    public CompanyOrdersMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, plugin.messages().get("gui.company.orders-title"));
        Messages msg = plugin.messages();
        UUID uuid = viewer.getUniqueId();
        List<Database.WsOrder> orders = new ArrayList<>(plugin.companies().workOrders(uuid));
        int pages = Math.max(1, (orders.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= orders.size()) {
                break;
            }
            Database.WsOrder order = orders.get(index);
            Material material = Material.matchMaterial(order.material());
            if (material == null) {
                continue;
            }
            boolean open = plugin.database().isOrderOpen(order.id());
            List<Component> lore = new ArrayList<>(msg.getList("gui.company.order-lore",
                    "sector", WholesaleManager.SECTORS.get(order.sector()).name(),
                    "amount", order.amount(), "done", order.delivered(),
                    "state", open ? "freigegeben" : "gesperrt"));
            if (!order.owner().equals(uuid)) {
                lore.add(msg.get("gui.company.order-partner", "player", plugin.database().getName(order.owner())));
            }
            for (Database.Contrib c : plugin.database().getContribs(order.id())) {
                lore.add(msg.get("gui.company.order-contrib", "player", plugin.database().getName(c.member()),
                        "amount", c.amount()));
            }
            set(i, ItemUtil.icon(material, msg.get(open ? "gui.company.order-open" : "gui.company.order-closed",
                    "item", ItemUtil.name(material)), lore),
                    (p, c) -> {
                        plugin.database().setOrderOpen(order.id(), !open);
                        new CompanyOrdersMenu(plugin, p, current).open(p);
                    });
        }
        if (orders.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.wholesale.no-orders"),
                    msg.getList("gui.wholesale.no-orders-lore")), null);
        }
        set(45, backButton(), (p, c) -> new CompanyMenu(plugin, p).open(p));
        set(46, ItemUtil.icon(Material.LIME_CONCRETE, msg.get("gui.company.open-all"), List.of()), (p, c) -> {
            for (Database.WsOrder order : orders) {
                plugin.database().setOrderOpen(order.id(), true);
            }
            new CompanyOrdersMenu(plugin, p, current).open(p);
        });
        set(47, ItemUtil.icon(Material.RED_CONCRETE, msg.get("gui.company.close-all"), List.of()), (p, c) -> {
            for (Database.WsOrder order : orders) {
                plugin.database().setOrderOpen(order.id(), false);
            }
            new CompanyOrdersMenu(plugin, p, current).open(p);
        });
        if (current > 0) {
            set(48, ItemUtil.icon(Material.ARROW, msg.get("gui.prev-page"), List.of()),
                    (p, c) -> new CompanyOrdersMenu(plugin, p, current - 1).open(p));
        }
        if (current < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, msg.get("gui.next-page"), List.of()),
                    (p, c) -> new CompanyOrdersMenu(plugin, p, current + 1).open(p));
        }
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
