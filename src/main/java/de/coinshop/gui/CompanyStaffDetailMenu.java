package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Was hat ein Angestellter zu welchem offenen Auftrag beigetragen? */
public final class CompanyStaffDetailMenu extends Menu {

    public CompanyStaffDetailMenu(CoinShopPlugin plugin, Player viewer, UUID member) {
        super(plugin, 6, plugin.messages().get("gui.company.detail-title", "player", plugin.database().getName(member)));
        Messages msg = plugin.messages();
        UUID uuid = viewer.getUniqueId();
        int i = 0;
        for (Database.WsOrder order : plugin.companies().workOrders(uuid)) {
            long mine = 0;
            for (Database.Contrib c : plugin.database().getContribs(order.id())) {
                if (c.member().equals(member)) {
                    mine = c.amount();
                }
            }
            Material material = Material.matchMaterial(order.material());
            if (mine <= 0 || material == null || i >= 45) {
                continue;
            }
            set(i++, ItemUtil.icon(material, ItemUtil.name(material),
                    msg.getList("gui.company.detail-lore", "sector", WholesaleManager.SECTORS.get(order.sector()).name(),
                            "mine", mine, "amount", order.amount(), "done", order.delivered())), null);
        }
        Database.Staff staff = plugin.database().getStaff(member);
        if (i == 0) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.company.detail-none"),
                    msg.getList("gui.company.detail-none-lore")), null);
        }
        if (staff != null) {
            set(49, ItemUtil.icon(Material.BOOK, msg.get("gui.company.detail-total"),
                    msg.getList("gui.company.detail-total-lore", "items", staff.items(), "wages", staff.wages())), null);
        }
        set(45, backButton(), (p, c) -> new CompanyStaffMenu(plugin, p).open(p));
        fillEmpty();
    }
}
