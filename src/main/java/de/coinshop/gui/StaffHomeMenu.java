package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Der Arbeitsplatz eines Angestellten: nur die Aufträge des Arbeitgebers, sonst nichts vom Großhandel. */
public final class StaffHomeMenu extends Menu {

    public StaffHomeMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.staff.title"));
        Messages msg = plugin.messages();
        UUID uuid = viewer.getUniqueId();
        Database.Staff staff = plugin.database().getStaff(uuid);
        Database.Company company = staff == null ? null : plugin.companies().company(staff.owner());
        if (staff == null || company == null) {
            set(13, ItemUtil.icon(Material.BARRIER, msg.get("gui.staff.none"), java.util.List.of()), null);
        } else {
            set(11, ItemUtil.icon(Material.BARREL, msg.get("gui.staff.orders-name"),
                            msg.getList("gui.staff.orders-lore")),
                    (p, c) -> new StaffOrdersMenu(plugin, p, 0).open(p));
            set(13, ItemUtil.icon(Material.OAK_SIGN, msg.get("gui.staff.info-name", "name", company.name()),
                    msg.getList("gui.staff.info-lore", "owner", plugin.database().getName(company.owner()),
                            "items", staff.items(), "wages", staff.wages(),
                            "wage", plugin.companies().wagePercent())), null);
            set(15, ItemUtil.icon(Material.RED_DYE, msg.get("gui.staff.quit-name"), msg.getList("gui.staff.quit-lore")),
                    (p, c) -> {
                        if (c.isShiftClick() && c.isRightClick()) {
                            plugin.companies().leave(p);
                            p.closeInventory();
                        }
                    });
        }
        set(18, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
