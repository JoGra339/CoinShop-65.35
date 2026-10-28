package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.Database.MinerQuest;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Die heutige Miner-Quest: welche Blöcke fehlen noch, welche sind schon abgebaut. */
public final class MinerQuestMenu extends Menu {

    public MinerQuestMenu(CoinShopPlugin plugin, Player viewer, int miner) {
        super(plugin, 6, plugin.messages().get("gui.minerquest.title"));
        Messages msg = plugin.messages();
        CryptoManager crypto = plugin.crypto();
        MinerQuest q = crypto.questToday(viewer.getUniqueId(), miner) ? crypto.quest(viewer.getUniqueId(), miner) : null;
        if (q == null) {
            set(22, ItemUtil.icon(Material.BARRIER, msg.get("gui.minerquest.none-name"), msg.getList("gui.minerquest.none-lore")), null);
        } else {
            List<String> blocks = q.blocks();
            for (int i = 0; i < blocks.size() && i < 45; i++) {
                String type = CryptoManager.typeOf(blocks.get(i));
                int need = CryptoManager.required(q, type);
                int have = CryptoManager.progress(q, type);
                Material m = Material.matchMaterial(type);
                if (m == null) {
                    continue;
                }
                boolean done = have >= need;
                set(i, ItemUtil.icon(done ? Material.LIME_STAINED_GLASS_PANE : m,
                        msg.get(done ? "gui.minerquest.done-name" : "gui.minerquest.todo-name", "block", ItemUtil.name(m)),
                        msg.getList(done ? "gui.minerquest.done-lore" : "gui.minerquest.todo-lore",
                                "have", have, "need", need)), null);
            }
            set(49, ItemUtil.icon(Material.IRON_PICKAXE,
                    msg.get("gui.minerquest.info-name", "name", crypto.minerName(q.miner())),
                    msg.getList("gui.minerquest.info-lore", "done", CryptoManager.completedCount(q), "total", q.blocks().size(),
                    "units", CryptoManager.fmt(q.reward() > 0 ? crypto.payout(viewer.getUniqueId(), q.reward())
                            : crypto.reward(viewer.getUniqueId(), q.miner(), Math.max(1, crypto.minerLevel(viewer.getUniqueId(), q.miner())))))), null);
        }
        set(45, backButton(), (player, click) -> new MinerMenu(plugin, player).open(player));
        fillEmpty();
    }
}
