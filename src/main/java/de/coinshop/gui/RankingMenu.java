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

/** Übersicht aller Ranglisten. Ein Klick öffnet die Top 10 der Liste. */
public final class RankingMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    public RankingMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.rank.title"));
        Messages msg = plugin.messages();
        RankingManager rankings = plugin.rankings();
        UUID uuid = viewer.getUniqueId();

        StringBuilder bonusList = new StringBuilder();
        for (long b : rankings.bonuses()) {
            if (bonusList.length() > 0) {
                bonusList.append(" / ");
            }
            bonusList.append(b);
        }

        long totalBonus = 0;
        List<Component> held = new ArrayList<>();
        Rank[] ranks = Rank.values();
        for (int i = 0; i < ranks.length && i < SLOTS.length; i++) {
            Rank rank = ranks[i];
            RankingManager.Placed info = rankings.info(rank, uuid);
            int place = info == null ? 0 : info.place();
            long bonus = info == null ? 0 : info.bonus();
            Component name = msg.get("gui.rank.cat." + rank.id() + ".name");

            List<Component> lore = new ArrayList<>(msg.getList("gui.rank.cat." + rank.id() + ".desc"));
            lore.add(Component.empty());
            lore.add(msg.get("gui.rank.bonus-line", "list", bonusList.toString()));
            lore.add(place > 0
                    ? msg.get("gui.rank.your-place", "place", place)
                    : msg.get("gui.rank.not-listed"));
            lore.add(Component.empty());
            lore.add(msg.get("gui.rank.click"));

            if (bonus > 0) {
                totalBonus += bonus;
                held.add(msg.get("gui.rank.bonus-total-line", "name", name, "place", place, "bonus", bonus));
            }
            set(SLOTS[i], ItemUtil.icon(rank.icon(), msg.get("gui.rank.cat-name", "name", name), lore),
                    (player, click) -> new RankingDetailMenu(plugin, player, rank).open(player));
        }

        // Unten: Kontostand | Zurück | dein Gesamt-Bonus
        set(38, balanceIcon(viewer), incomeClick());
        set(31, ItemUtil.icon(Material.SPYGLASS, msg.get("gui.rank.mine-button"), msg.getList("gui.rank.mine-button-lore")),
                (player, click) -> new MyRanksMenu(plugin, player).open(player));
        set(40, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        List<Component> bonusLore = new ArrayList<>(msg.getList("gui.rank.bonus-total-lore", "coins", totalBonus));
        bonusLore.addAll(held);
        set(42, ItemUtil.icon(Material.GOLD_NUGGET, msg.get("gui.rank.bonus-total-name"), bonusLore), null);

        fillEmpty();
    }
}
