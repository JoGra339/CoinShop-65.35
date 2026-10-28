package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Firmenrang: die Firmen mit dem höchsten Umsatz dieser Woche. Die Besten bekommen sonntags eine Prämie. */
public final class CompanyRankMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public CompanyRankMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.company.rank-title"));
        Messages msg = plugin.messages();
        WholesaleManager wh = plugin.wholesale();
        List<WholesaleManager.Standing> board = wh.companyBoard();
        List<Long> prizes = plugin.getConfig().getLongList("company.rank-prizes");
        for (int i = 0; i < board.size() && i < SLOTS.length; i++) {
            WholesaleManager.Standing entry = board.get(i);
            Database.Company company = plugin.database().getCompany(entry.uuid());
            if (company == null) {
                continue;
            }
            Material icon = i == 0 ? Material.NETHER_STAR : i == 1 ? Material.GOLD_BLOCK : i == 2 ? Material.IRON_BLOCK
                    : Material.COPPER_BLOCK;
            set(SLOTS[i], ItemUtil.icon(icon, msg.get("gui.company.rank-name", "rank", i + 1, "name", company.name()),
                    msg.getList("gui.company.rank-lore", "owner", plugin.database().getName(company.owner()),
                            "week", entry.value(), "total", wh.totalTurnover(company.owner()),
                            "wins", wh.companyWins(company.owner()),
                            "last", wh.companyLastRank(company.owner()) == 0 ? "-" : "Platz " + wh.companyLastRank(company.owner()),
                            "prize", i < prizes.size()
                                    ? prizes.get(i) + prizes.get(i) * wh.reputationBonus(wh.reputation(company.owner())) / 100 : 0,
                            "rep", wh.reputation(company.owner()),
                            "repbonus", wh.reputationBonus(wh.reputation(company.owner())),
                            "staff", plugin.database().getStaffOf(company.owner()).size())), null);
        }
        if (board.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.company.rank-none"),
                    msg.getList("gui.company.rank-none-lore")), null);
        }
        set(45, backButton(), (p, c) -> new WholesaleHubMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
