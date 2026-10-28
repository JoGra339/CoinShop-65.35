package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import de.coinshop.WholesaleManager.Standing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/**
 * Rangliste des Großhandels: Reiter "Gesamt" (sector = -1: alle Tätigen, der CEO) und je Branche (Gesellschafter).
 * Gezählt werden die erledigten Aufträge dieser Woche. Zeigt auch den Abstand zum Gesellschafter bzw. CEO.
 */
public final class WholesaleRankMenu extends Menu {

    private static final int[] TABS = {2, 3, 4, 5, 6, 7, 8, 11, 12, 13};
    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

    public WholesaleRankMenu(CoinShopPlugin plugin, Player viewer, int sector) {
        super(plugin, 6, plugin.messages().get("gui.wholesale.rank-title"));
        Messages msg = plugin.messages();
        WholesaleManager wh = plugin.wholesale();
        UUID me = viewer.getUniqueId();

        set(1, ItemUtil.icon(sector < 0 ? Material.NETHER_STAR : Material.GRAY_DYE, msg.get("gui.wholesale.rank-tab-all"),
                msg.getList("gui.wholesale.rank-tab-lore")), (p, c) -> new WholesaleRankMenu(plugin, p, -1).open(p));
        for (int s = 0; s < WholesaleManager.SECTORS.size(); s++) {
            final int target = s;
            WholesaleManager.Sector sec = WholesaleManager.SECTORS.get(s);
            set(TABS[s], ItemUtil.icon(sector == s ? sec.icon() : Material.GRAY_DYE,
                            msg.get("gui.wholesale.rank-tab-name", "name", sec.name()),
                            msg.getList("gui.wholesale.rank-tab-lore")),
                    (p, c) -> new WholesaleRankMenu(plugin, p, target).open(p));
        }

        List<Standing> top = sector < 0 ? wh.activeBoard() : wh.board(sector);
        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= top.size()) {
                if (i == 0) {
                    set(SLOTS[i], ItemUtil.icon(Material.BARRIER, msg.get("gui.wholesale.rank-empty"), List.of()), null);
                }
                continue;
            }
            Standing st = top.get(i);
            ItemStack head = ItemUtil.icon(Material.PLAYER_HEAD,
                    msg.get("gui.wholesale.rank-entry", "place", i + 1, "player", plugin.database().getName(st.uuid())),
                    msg.getList("gui.wholesale.rank-entry-lore", "orders", st.value(),
                            "role", role(wh, st.uuid(), sector)));
            OfflinePlayer off = Bukkit.getOfflinePlayer(st.uuid());
            head.editMeta(meta -> {
                if (meta instanceof SkullMeta skull) {
                    skull.setOwningPlayer(off);
                }
            });
            set(SLOTS[i], head, null);
        }

        long mine = sector < 0 ? wh.weeklyTotal(me) : wh.weeklyDone(me, sector);
        long leader = top.isEmpty() ? 0 : top.get(0).value();
        UUID holder = sector < 0 ? wh.ceo() : wh.shareholder(sector);
        set(40, ItemUtil.icon(Material.PAPER, msg.get("gui.wholesale.rank-mine-name"),
                msg.getList("gui.wholesale.rank-mine-lore", "orders", mine,
                        "gap", Math.max(0, leader - mine), "leader", leader)), null);
        set(41, ItemUtil.icon(Material.GOLDEN_HELMET,
                msg.get(sector < 0 ? "gui.wholesale.rank-ceo-name" : "gui.wholesale.rank-share-name"),
                msg.getList("gui.wholesale.rank-holder-lore",
                        "player", holder == null ? "-" : plugin.database().getName(holder),
                        "time", ItemUtil.formatDuration(plugin.reviere().timeUntilEvaluation().toMillis()))), null);
        set(45, backButton(), (p, c) -> new WholesaleHubMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), null);
        fillEmpty();
    }

    private static String role(WholesaleManager wh, UUID uuid, int sector) {
        if (wh.isCeo(uuid)) {
            return "CEO";
        }
        if (sector >= 0 && wh.isShareholder(uuid, sector)) {
            return "Gesellschafter";
        }
        if (sector < 0 && wh.seatsOf(uuid) > 0) {
            return "Gesellschafter (" + wh.seatsOf(uuid) + ")";
        }
        return "-";
    }
}
