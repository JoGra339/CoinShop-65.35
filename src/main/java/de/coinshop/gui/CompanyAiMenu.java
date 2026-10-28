package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CompanyManager;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** KI-Mitarbeiter der Firma: kaufen, leveln und einem Auftrag zuordnen. */
public final class CompanyAiMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public CompanyAiMenu(CoinShopPlugin plugin, Player viewer) {
        this(plugin, viewer, 0);
    }

    public CompanyAiMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, plugin.messages().get("gui.ai.title"));
        Messages msg = plugin.messages();
        CompanyManager cm = plugin.companies();
        UUID uuid = viewer.getUniqueId();
        List<Database.Ai> ais = plugin.database().getAis(uuid);
        int pages = Math.max(1, (cm.aiMax() + SLOTS.length - 1) / SLOTS.length);
        int from = Math.max(0, Math.min(page, pages - 1)) * SLOTS.length;
        int shown = 0;
        for (int index = from; index < ais.size() && shown < SLOTS.length; index++, shown++) {
            Database.Ai ai = ais.get(index);
            Database.WsOrder order = ai.orderId() == 0 ? null : plugin.database().getWsOrder(ai.orderId());
            String work = "-";
            if (order != null) {
                Material m = Material.matchMaterial(order.material());
                work = (m == null ? order.material() : m.name()) + " (" + order.delivered() + " von " + order.amount() + ")";
            }
            set(SLOTS[shown], ItemUtil.icon(Material.COMPARATOR, msg.get("gui.ai.name", "id", index + 1, "level", ai.level()),
                            msg.getList("gui.ai.lore", "level", ai.level(), "items", cm.aiItems(ai.level()),
                                    "work", work, "made", ai.made())),
                    (p, c) -> new Detail(plugin, p, ai.id()).open(p));
        }
        if (ais.size() < cm.aiMax() && shown < SLOTS.length && from <= ais.size()) {
            set(SLOTS[shown], ItemUtil.icon(Material.LIME_DYE, msg.get("gui.ai.buy-name"),
                            msg.getList("gui.ai.buy-lore", "price", cm.aiSlotPrice(ais.size()),
                                    "items", cm.aiItems(1))),
                    (p, c) -> {
                        cm.buyAi(p);
                        new CompanyAiMenu(plugin, p, page).open(p);
                    });
        }
        if (page > 0) {
            set(46, ItemUtil.icon(Material.ARROW, msg.get("gui.ai.prev"), List.of()),
                    (p, c) -> new CompanyAiMenu(plugin, p, page - 1).open(p));
        }
        if (page < pages - 1) {
            set(52, ItemUtil.icon(Material.ARROW, msg.get("gui.ai.next"), List.of()),
                    (p, c) -> new CompanyAiMenu(plugin, p, page + 1).open(p));
        }
        set(45, backButton(), (p, c) -> new CompanyMenu(plugin, p).open(p));
        set(49, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    /** Eine KI: Stufe erhöhen und Auftrag zuordnen. */
    public static final class Detail extends Menu {
        public Detail(CoinShopPlugin plugin, Player viewer, long id) {
            super(plugin, 3, plugin.messages().get("gui.ai.detail-title"));
            Messages msg = plugin.messages();
            CompanyManager cm = plugin.companies();
            Database.Ai ai = plugin.database().getAi(id);
            if (ai != null) {
                List<Component> lore = new ArrayList<>();
                if (ai.level() >= 10) {
                    set(11, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.ai.top"), lore), null);
                } else {
                    set(11, ItemUtil.icon(Material.EXPERIENCE_BOTTLE, msg.get("gui.ai.upgrade-name"),
                                    msg.getList("gui.ai.upgrade-lore", "next", ai.level() + 1,
                                            "items", cm.aiItems(ai.level() + 1), "price", cm.aiLevelPrice(ai.level() + 1))),
                            (p, c) -> {
                                cm.upgradeAi(p, id);
                                new Detail(plugin, p, id).open(p);
                            });
                }
                set(13, ItemUtil.icon(Material.BARREL, msg.get("gui.ai.assign-name"),
                                msg.getList("gui.ai.assign-lore")),
                        (p, c) -> new OrderPick(plugin, p, id).open(p));
                set(15, ItemUtil.icon(Material.BARRIER, msg.get("gui.ai.unassign-name"), lore),
                        (p, c) -> {
                            cm.assignAi(p, id, 0);
                            new Detail(plugin, p, id).open(p);
                        });
            }
            set(18, backButton(), (p, c) -> new CompanyAiMenu(plugin, p).open(p));
            set(22, balanceIcon(viewer), incomeClick());
            fillEmpty();
        }
    }

    /** Auftrag wählen, den die KI bearbeitet (nur eigene Aufträge). */
    public static final class OrderPick extends Menu {
        public OrderPick(CoinShopPlugin plugin, Player viewer, long id) {
            super(plugin, 6, plugin.messages().get("gui.ai.pick-title"));
            Messages msg = plugin.messages();
            int i = 0;
            for (int s = 0; s < de.coinshop.WholesaleManager.SECTORS.size(); s++) {
                for (Database.WsOrder order : plugin.wholesale().orders(viewer.getUniqueId(), s)) {
                    Material material = Material.matchMaterial(order.material());
                    if (material == null || i >= 45) {
                        continue;
                    }
                    set(i++, ItemUtil.icon(material, ItemUtil.name(material),
                                    msg.getList("gui.ai.pick-lore", "amount", order.amount(), "done", order.delivered(),
                                            "sector", de.coinshop.WholesaleManager.SECTORS.get(s).name())),
                            (p, c) -> {
                                plugin.companies().assignAi(p, id, order.id());
                                new Detail(plugin, p, id).open(p);
                            });
                }
            }
            if (i == 0) {
                set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.wholesale.no-orders"),
                        msg.getList("gui.wholesale.no-orders-lore")), null);
            }
            set(45, backButton(), (p, c) -> new Detail(plugin, p, id).open(p));
            fillEmpty();
        }
    }
}
