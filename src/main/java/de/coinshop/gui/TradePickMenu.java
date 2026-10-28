package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.TradeManager;
import de.coinshop.TradeManager.Session;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Auswahl: welche deiner Set-Items willst du ins Angebot legen? */
public final class TradePickMenu extends Menu {

    private static final int PER_PAGE = 45;

    public TradePickMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, plugin.messages().get("gui.trade.pick-title"));
        Messages msg = plugin.messages();
        TradeManager trade = plugin.trade();
        Session session = trade.sessionOf(viewer.getUniqueId());
        List<String> available = new ArrayList<>();
        for (String id : trade.ownedItems(viewer.getUniqueId())) {
            if (session == null || !session.offerOf(viewer.getUniqueId()).items.contains(id)) {
                available.add(id);
            }
        }
        int pages = Math.max(1, (available.size() + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= available.size()) {
                break;
            }
            String id = available.get(index);
            SetDef set = trade.setOf(id);
            if (set == null) {
                continue;
            }
            Material material = set.items().get(Math.min(trade.indexOf(id), set.items().size() - 1));
            set(i, ItemUtil.icon(material, ItemUtil.name(material),
                    List.of(msg.get("gui.trade.from-set", "set", set.name()), msg.get("gui.trade.click-add"))),
                    (p, c) -> trade.addItem(p, id));
        }
        set(45, backButton(), (p, c) -> trade.show(p));
        if (current > 0) {
            set(48, ItemUtil.icon(Material.ARROW, msg.get("gui.prev-page"), List.of()),
                    (p, c) -> trade.openMenu(p, new TradePickMenu(plugin, p, current - 1)));
        }
        if (current < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, msg.get("gui.next-page"), List.of()),
                    (p, c) -> trade.openMenu(p, new TradePickMenu(plugin, p, current + 1)));
        }
        fillEmpty();
    }
}
