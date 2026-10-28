package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.Database.MinerQuest;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.UUID;

/** Alle 32 Krypto-Miner: kaufen (nacheinander), leveln (Rechtsklick) und die Tages-Quest starten (Linksklick). */
public final class MinerMenu extends Menu {

    public MinerMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.miner.title"));
        Messages msg = plugin.messages();
        CryptoManager crypto = plugin.crypto();
        UUID uuid = viewer.getUniqueId();

        for (int idx = 1; idx <= CryptoManager.MINERS; idx++) {
            final int miner = idx;
            int level = crypto.minerLevel(uuid, idx);
            String name = crypto.minerName(idx);
            int slot = idx - 1;
            if (level < 1) {
                boolean next = idx == 1 || crypto.minerLevel(uuid, idx - 1) >= 1;
                if (next) {
                    set(slot, ItemUtil.icon(Material.COAL, msg.get("gui.miner.buy-name", "name", name),
                                    msg.getList("gui.miner.buy-lore", "price", crypto.minerPrice(idx),
                                            "blocks", crypto.blocksFor(idx, 1),
                                            "units", CryptoManager.fmt(crypto.reward(uuid, idx, 1)))),
                            (player, click) -> {
                                crypto.buyMiner(player, miner);
                                new MinerMenu(plugin, player).open(player);
                            });
                } else {
                    set(slot, ItemUtil.icon(Material.BARRIER, msg.get("gui.miner.locked-name", "name", name),
                            msg.getList("gui.miner.locked-lore", "price", crypto.minerPrice(idx))), null);
                }
                continue;
            }
            MinerQuest quest = crypto.questToday(uuid, idx) ? crypto.quest(uuid, idx) : null;
            boolean active = quest != null;
            List<Component> lore = msg.getList("gui.miner.owned-lore", "level", level, "max", CryptoManager.MAX_LEVEL,
                    "blocks", crypto.blocksFor(idx, level), "units", CryptoManager.fmt(crypto.reward(uuid, idx, level)));
            if (level < CryptoManager.MAX_LEVEL) {
                lore.addAll(msg.getList("gui.miner.upgrade-lore", "price", crypto.levelPrice(idx, level),
                        "blocks", crypto.blocksFor(idx, level + 1)));
            } else {
                lore.addAll(msg.getList("gui.miner.maxed-lore"));
            }
            if (active) {
                lore.addAll(msg.getList(quest.finished() ? "gui.miner.quest-done-lore" : "gui.miner.quest-active-lore",
                        "done", CryptoManager.completedCount(quest), "total", quest.blocks().size()));
            } else {
                lore.addAll(msg.getList("gui.miner.quest-start-lore"));
            }
            Material icon = active ? (quest.finished() ? Material.EMERALD_BLOCK : Material.DIAMOND_PICKAXE)
                    : Material.IRON_PICKAXE;
            set(slot, ItemUtil.icon(icon, msg.get("gui.miner.owned-name", "name", name, "level", level), lore),
                    (player, click) -> {
                        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                            crypto.upgradeMiner(player, miner);
                            new MinerMenu(plugin, player).open(player);
                            return;
                        }
                        if (!crypto.questToday(player.getUniqueId(), miner)) {
                            crypto.startQuest(player, miner);
                        }
                        if (crypto.questToday(player.getUniqueId(), miner)) {
                            new MinerQuestMenu(plugin, player, miner).open(player);
                        }
                    });
        }
        set(45, backButton(), (player, click) -> new CryptoHubMenu(plugin, player).open(player));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
