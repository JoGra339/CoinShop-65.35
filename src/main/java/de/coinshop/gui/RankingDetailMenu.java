package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RankingManager;
import de.coinshop.RankingManager.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Die Top 10 einer Rangliste. */
public final class RankingDetailMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public RankingDetailMenu(CoinShopPlugin plugin, Player viewer, Rank rank) {
        super(plugin, 4, plugin.messages().get("gui.rank.detail-title",
                "name", plugin.messages().get("gui.rank.cat." + rank.id() + ".name")));
        Messages msg = plugin.messages();
        RankingManager rankings = plugin.rankings();
        UUID uuid = viewer.getUniqueId();
        Component unit = msg.get("gui.rank.cat." + rank.id() + ".unit");

        List<RankingManager.Placed> top = rankings.ranked(rank, 200);

        // Oben Mitte: Beschreibung + dein eigener Platz
        List<Component> infoLore = new ArrayList<>(msg.getList("gui.rank.cat." + rank.id() + ".desc"));
        infoLore.add(Component.empty());
        RankingManager.Placed own = rankings.info(rank, uuid);
        if (own != null) {
            infoLore.add(msg.get("gui.rank.own-lore", "place", own.place(), "value", own.entry().value(),
                    "unit", unit));
        } else {
            infoLore.add(msg.get("gui.rank.not-listed"));
        }
        set(4, ItemUtil.icon(rank.icon(), msg.get("gui.rank.cat-name",
                "name", msg.get("gui.rank.cat." + rank.id() + ".name")), infoLore), null);

        if (top.isEmpty()) {
            set(13, ItemUtil.icon(Material.BARRIER, msg.get("gui.rank.empty"), List.of()), null);
        }
        for (int i = 0; i < top.size() && i < SLOTS.length; i++) {
            RankingManager.Placed entry = top.get(i);
            int place = entry.place();
            Material material = switch (place) {
                case 1 -> Material.GOLD_INGOT;
                case 2 -> Material.IRON_INGOT;
                case 3 -> Material.COPPER_INGOT;
                default -> Material.PAPER;
            };
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get("gui.rank.entry-value", "value", entry.entry().value(), "unit", unit));
            if (entry.bonus() > 0) {
                lore.add(entry.tied() > 1
                        ? msg.get("gui.rank.entry-bonus-shared", "bonus", entry.bonus(), "tied", entry.tied())
                        : msg.get("gui.rank.entry-bonus", "bonus", entry.bonus()));
            }
            ItemStack icon = ItemUtil.icon(material, msg.get("gui.rank.entry-name", "place", place,
                    "name", entry.entry().name()), lore);
            icon.setAmount(Math.max(1, Math.min(64, place)));
            set(SLOTS[i], icon, null);
        }

        set(29, balanceIcon(viewer), incomeClick());
        set(31, backButton(), (player, click) -> new RankingMenu(plugin, player).open(player));

        fillEmpty();
    }
}
