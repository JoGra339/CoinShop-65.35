package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CompanyManager;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mein Gewerbe: anmelden, benennen, Angestellte, Aufträge freigeben, ausbauen. */
public final class CompanyMenu extends Menu {

    public CompanyMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.company.title"));
        Messages msg = plugin.messages();
        CompanyManager cm = plugin.companies();
        UUID uuid = viewer.getUniqueId();
        Database.Company company = cm.company(uuid);
        if (company == null) {
            set(13, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.company.found-name"),
                            msg.getList("gui.company.found-lore", "price", cm.foundPrice(), "max", 24)),
                    (p, c) -> {
                        p.closeInventory();
                        msg.send(p, "co-ask-name");
                        plugin.prompts().start(p, text -> {
                            if (text.equalsIgnoreCase("abbrechen")) {
                                msg.send(p, "ask-cancelled");
                                return;
                            }
                            cm.found(p, text);
                            if (cm.company(p.getUniqueId()) != null) {
                                new CompanyMenu(plugin, p).open(p);
                            }
                        });
                    });
        } else {
            StringBuilder sectors = new StringBuilder();
            for (int s = 0; s < WholesaleManager.SECTORS.size(); s++) {
                if (plugin.wholesale().hasSector(uuid, s)) {
                    if (sectors.length() > 0) {
                        sectors.append(", ");
                    }
                    sectors.append(WholesaleManager.SECTORS.get(s).name());
                }
            }
            int staff = plugin.database().getStaffOf(uuid).size();
            set(10, ItemUtil.icon(Material.OAK_SIGN, msg.get("gui.company.info-name", "name", company.name()),
                            msg.getList("gui.company.info-lore", "staff", staff, "slots", cm.slots(),
                                    "wage", cm.wagePercent(), "maxwage", cm.wageMaxPercent(), "cap", cm.wageCap(),
                                    "sectors", sectors.length() == 0 ? "-" : sectors.toString(),
                                    "price", cm.renamePrice())),
                    (p, c) -> {
                        p.closeInventory();
                        msg.send(p, "co-ask-rename", "price", cm.renamePrice());
                        plugin.prompts().start(p, text -> {
                            if (text.equalsIgnoreCase("abbrechen")) {
                                msg.send(p, "ask-cancelled");
                                return;
                            }
                            cm.rename(p, text);
                            new CompanyMenu(plugin, p).open(p);
                        });
                    });
            set(12, ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.company.staff-name"),
                            msg.getList("gui.company.staff-lore")),
                    (p, c) -> new CompanyStaffMenu(plugin, p).open(p));
            int open = 0;
            int total = 0;
            for (Database.WsOrder order : cm.workOrders(uuid)) {
                total++;
                if (plugin.database().isOrderOpen(order.id())) {
                    open++;
                }
            }
            set(14, ItemUtil.icon(Material.BARREL, msg.get("gui.company.orders-name"),
                            msg.getList("gui.company.orders-lore", "open", open, "total", total)),
                    (p, c) -> new CompanyOrdersMenu(plugin, p, 0).open(p));
            set(20, ItemUtil.icon(Material.COMPARATOR, msg.get("gui.company.ai-name"),
                            msg.getList("gui.company.ai-lore", "count", plugin.database().getAis(uuid).size(),
                                    "max", cm.aiMax())),
                    (p, c) -> new CompanyAiMenu(plugin, p).open(p));
            set(16, ItemUtil.icon(Material.TNT, net.kyori.adventure.text.Component.text("Insolvenz anmelden",
                            net.kyori.adventure.text.format.NamedTextColor.RED),
                            List.of(net.kyori.adventure.text.Component.text("Gewerbe auflösen (mit Rückfrage)",
                                    net.kyori.adventure.text.format.NamedTextColor.GRAY))),
                    (p, c) -> new CompanyDissolveMenu(plugin, p).open(p));
        }
        set(18, backButton(), (p, c) -> new WholesaleHubMenu(plugin, p).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
