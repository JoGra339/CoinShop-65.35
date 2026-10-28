package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RankingManager;
import de.coinshop.RankingManager.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** "Meine Platzierungen": in jeder Rangliste auf einen Blick Platz, Wert, Bonus und Abstand zum nächsten Platz. */
public final class MyRanksMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public MyRanksMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.rank.mine-title"));
        Messages msg = plugin.messages();
        RankingManager rankings = plugin.rankings();
        UUID uuid = viewer.getUniqueId();

        long totalBonus = 0;
        Rank[] ranks = Rank.values();
        for (int i = 0; i < ranks.length && i < SLOTS.length; i++) {
            Rank rank = ranks[i];
            Component name = msg.get("gui.rank.cat." + rank.id() + ".name");
            Component unit = msg.get("gui.rank.cat." + rank.id() + ".unit");
            List<RankingManager.Placed> list = rankings.ranked(rank, 200);

            RankingManager.Placed own = null;
            int index = -1;
            for (int j = 0; j < list.size(); j++) {
                if (list.get(j).entry().uuid().equals(uuid)) {
                    own = list.get(j);
                    index = j;
                    break;
                }
            }

            List<Component> lore = new ArrayList<>();
            Material icon = rank.icon();
            if (own == null) {
                lore.add(msg.get("gui.rank.not-listed"));
            } else {
                lore.add(msg.get("gui.rank.mine-place", "place", own.place(), "total", list.size()));
                lore.add(msg.get("gui.rank.mine-value", "value", own.entry().value(), "unit", unit));
                if (own.bonus() > 0) {
                    totalBonus += own.bonus();
                    lore.add(msg.get("gui.rank.mine-bonus", "bonus", own.bonus()));
                } else {
                    lore.add(msg.get("gui.rank.mine-no-bonus"));
                }
                // Abstand zum Spieler direkt vor dir (mit besserem Platz)
                int before = -1;
                for (int j = index - 1; j >= 0; j--) {
                    if (list.get(j).place() < own.place()) {
                        before = j;
                        break;
                    }
                }
                if (before >= 0) {
                    long gap = Math.abs(list.get(before).entry().value() - own.entry().value());
                    lore.add(msg.get("gui.rank.mine-gap", "gap", gap, "unit", unit,
                            "place", list.get(before).place(), "name", list.get(before).entry().name()));
                } else {
                    lore.add(msg.get("gui.rank.mine-first"));
                }
            }
            lore.add(Component.empty());
            lore.add(msg.get("gui.rank.click"));
            set(SLOTS[i], ItemUtil.icon(icon, msg.get("gui.rank.cat-name", "name", name), lore),
                    (player, click) -> new RankingDetailMenu(plugin, player, rank).open(player));
        }

        set(38, balanceIcon(viewer), incomeClick());
        set(40, backButton(), (player, click) -> new RankingMenu(plugin, player).open(player));
        set(42, ItemUtil.icon(Material.GOLD_NUGGET, msg.get("gui.rank.bonus-total-name"),
                msg.getList("gui.rank.bonus-total-lore", "coins", totalBonus)), null);
        fillEmpty();
    }
}
