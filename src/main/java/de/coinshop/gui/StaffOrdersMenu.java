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

/** Die vom Arbeitgeber freigegebenen Aufträge: hier gibt der Angestellte die geforderten Items ab. */
public final class StaffOrdersMenu extends Menu {

    private static final int PER_PAGE = 45;

    public StaffOrdersMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, plugin.messages().get("gui.staff.orders-title"));
        Messages msg = plugin.messages();
        WholesaleManager wh = plugin.wholesale();
        UUID uuid = viewer.getUniqueId();
        UUID employer = plugin.companies().employerOf(uuid);
        Database.Company company = employer == null ? null : plugin.companies().company(employer);
        List<Database.WsOrder> orders = new ArrayList<>();
        if (employer != null && company != null) {
            for (Database.WsOrder order : plugin.companies().workOrders(employer)) {
                if (plugin.database().isOrderOpen(order.id())) {
                    orders.add(order);
                }
            }
        }
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
            long mine = 0;
            for (Database.Contrib c : plugin.database().getContribs(order.id())) {
                if (c.member().equals(uuid)) {
                    mine = c.amount();
                }
            }
            List<Component> lore = new ArrayList<>(msg.getList("gui.staff.order-lore",
                    "sector", WholesaleManager.SECTORS.get(order.sector()).name(),
                    "amount", order.amount(), "done", order.delivered(), "left", order.amount() - order.delivered(),
                    "mine", mine,
                    "wage", plugin.companies().wageFor(material, 1000)));
            if (!order.owner().equals(employer)) {
                lore.add(msg.get("gui.company.order-partner", "player", plugin.database().getName(order.owner())));
            }
            set(i, ItemUtil.icon(material, ItemUtil.name(material), lore), (p, c) -> {
                wh.deliver(p, order.id(), !c.isRightClick());
                new StaffOrdersMenu(plugin, p, current).open(p);
            });
        }
        if (orders.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.staff.no-orders"),
                    msg.getList("gui.staff.no-orders-lore")), null);
        }
        set(45, backButton(), (p, c) -> wh.openFor(p));
        if (current > 0) {
            set(48, ItemUtil.icon(Material.ARROW, msg.get("gui.prev-page"), List.of()),
                    (p, c) -> new StaffOrdersMenu(plugin, p, current - 1).open(p));
        }
        if (current < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, msg.get("gui.next-page"), List.of()),
                    (p, c) -> new StaffOrdersMenu(plugin, p, current + 1).open(p));
        }
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
