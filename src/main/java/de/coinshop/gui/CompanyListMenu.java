package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CompanyManager;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Alle Firmen im Großhandel: Name, Inhaber, Stufe und freigeschaltete Branchen des Inhabers. */
public final class CompanyListMenu extends Menu {

    private static final int PER_PAGE = 45;

    public CompanyListMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, plugin.messages().get("gui.company.list-title"));
        Messages msg = plugin.messages();
        CompanyManager cm = plugin.companies();
        List<Database.Company> all = plugin.database().getCompanies();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= all.size()) {
                break;
            }
            Database.Company company = all.get(index);
            StringBuilder sectors = new StringBuilder();
            for (int s = 0; s < WholesaleManager.SECTORS.size(); s++) {
                if (plugin.wholesale().hasSector(company.owner(), s)) {
                    if (sectors.length() > 0) {
                        sectors.append(", ");
                    }
                    sectors.append(WholesaleManager.SECTORS.get(s).name());
                }
            }
            int staffCount = plugin.database().getStaffOf(company.owner()).size();
            Material icon = staffCount >= cm.slots() ? Material.NETHER_STAR
                    : staffCount >= 8 ? Material.GOLD_BLOCK : Material.IRON_BLOCK;
            set(i, ItemUtil.icon(icon, msg.get("gui.company.list-name", "name", company.name()),
                    msg.getList("gui.company.list-lore", "owner", plugin.database().getName(company.owner()),
                            "max", cm.slots(),
                            "staff", staffCount,
                            "sectors", sectors.length() == 0 ? "-" : sectors.toString())), null);
        }
        if (all.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.company.list-none"), List.of()), null);
        }
        set(45, backButton(), (p, c) -> new WholesaleHubMenu(plugin, p).open(p));
        if (current > 0) {
            set(48, ItemUtil.icon(Material.ARROW, msg.get("gui.prev-page"), List.of()),
                    (p, c) -> new CompanyListMenu(plugin, p, current - 1).open(p));
        }
        if (current < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, msg.get("gui.next-page"), List.of()),
                    (p, c) -> new CompanyListMenu(plugin, p, current + 1).open(p));
        }
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
