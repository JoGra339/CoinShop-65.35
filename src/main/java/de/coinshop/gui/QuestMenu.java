package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.Database.MinerQuest;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.QuestManager;
import de.coinshop.QuestManager.Progress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Das Aufgaben-Menü: Login-Serie, 3 leichte und 3 schwere Tagesaufgaben und alle laufenden Miner-Quests auf einen Blick.
 */
public final class QuestMenu extends Menu {

    private static final int[] EASY_SLOTS = {10, 11, 12};
    private static final int[] HARD_SLOTS = {14, 15, 16};
    private static final int MINER_FIRST = 27;
    private static final int MINER_LAST = 44;

    public QuestMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.quest.title"));
        Messages msg = plugin.messages();
        QuestManager quests = plugin.quests();
        UUID uuid = viewer.getUniqueId();

        // Login-Serie (oben Mitte)
        int streak = plugin.logins().currentStreak(uuid);
        int best = plugin.database().getStreak(uuid).map(s -> s.best()).orElse(0);
        set(4, ItemUtil.icon(Material.CLOCK, msg.get("gui.quest.streak-name"),
                msg.getList("gui.quest.streak-lore", "streak", streak, "best", best,
                        "bonus", plugin.logins().bonusFor(uuid, streak),
                        "next", plugin.logins().bonusFor(uuid, streak + 1))), null);

        if (!quests.enabled()) {
            set(13, ItemUtil.icon(Material.BARRIER, msg.get("gui.quest.disabled"), List.of()), null);
        } else {
            List<Progress> progress = quests.check(viewer);
            int done = 0;
            int easyIdx = 0;
            int hardIdx = 0;
            for (Progress p : progress) {
                if (p.done()) {
                    done++;
                }
                List<Component> lore = new ArrayList<>(msg.getList("gui.quest.lore",
                        "goal", QuestManager.amountText(p.quest()), "reward", p.quest().reward(),
                        "have", p.current(), "need", p.quest().amount(), "bar", bar(p.current(), p.quest().amount())));
                lore.add(p.done() ? msg.get("gui.quest.done") : msg.get("gui.quest.open"));
                final int questIdx = p.quest().idx();
                boolean isPinned = quests.pinned(uuid) == questIdx;
                lore.add(ExpeditionMenu.line(isPinned ? "\u2714 Wird dir oben angezeigt - Klick: ausschalten"
                        : "Klick: dauerhaft oben anzeigen", isPinned ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                int slot;
                if (p.quest().hard()) {
                    if (hardIdx >= HARD_SLOTS.length) {
                        continue;
                    }
                    slot = HARD_SLOTS[hardIdx++];
                } else {
                    if (easyIdx >= EASY_SLOTS.length) {
                        continue;
                    }
                    slot = EASY_SLOTS[easyIdx++];
                }
                set(slot, ItemUtil.icon(p.quest().icon(),
                        msg.get(p.quest().hard() ? "gui.quest.name-hard" : "gui.quest.name-easy", "name", p.quest().name()), lore),
                        (player, click) -> {
                            quests.togglePin(player, questIdx);
                            new QuestMenu(plugin, player).open(player);
                        });
            }
            int[] carrySlots = {19, 20, 21, 23, 24, 25};
            int ci = 0;
            for (QuestManager.Carried c : quests.carried(uuid)) {
                if (ci >= carrySlots.length) {
                    break;
                }
                List<Component> lore = new ArrayList<>();
                lore.add(ExpeditionMenu.line("Von " + c.day() + " übernommen", NamedTextColor.GOLD));
                lore.add(ExpeditionMenu.line("Ziel: " + QuestManager.amountText(c.quest()), NamedTextColor.GRAY));
                lore.add(ExpeditionMenu.line("Belohnung: " + c.quest().reward() + " Coins", NamedTextColor.GRAY));
                lore.add(ExpeditionMenu.line("Fortschritt: " + c.current() + " / " + c.quest().amount(), NamedTextColor.GRAY));
                lore.add(bar(c.current(), c.quest().amount()).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(ExpeditionMenu.line("Zählt nicht für den Bonus „Alle Aufgaben“", NamedTextColor.DARK_GRAY));
                set(carrySlots[ci++], ItemUtil.icon(c.quest().icon(),
                        ExpeditionMenu.line("Offen: " + c.quest().name(), NamedTextColor.YELLOW), lore), null);
            }
            set(13, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.quest.bonus-name"),
                    msg.getList("gui.quest.bonus-lore", "done", done, "total", progress.size(),
                            "bonus", quests.bonusAll(),
                            "time", ItemUtil.formatDuration(plugin.serverShop().timeUntilReset().toMillis()))), null);
        }

        // Miner-Aufgaben: jeder Miner hat seine eigene Quest, alle laufen gleichzeitig
        List<MinerQuest> miners = plugin.crypto().activeQuests(uuid);
        int open = 0;
        for (MinerQuest q : miners) {
            if (!q.finished()) {
                open++;
            }
        }
        set(22, ItemUtil.icon(Material.IRON_PICKAXE, msg.get("gui.quest.miner-head"),
                msg.getList("gui.quest.miner-head-lore", "open", open, "total", miners.size())), null);
        int slot = MINER_FIRST;
        for (MinerQuest q : miners) {
            if (slot > MINER_LAST) {
                break;
            }
            final int miner = q.miner();
            int doneBlocks = CryptoManager.completedCount(q);
            set(slot++, ItemUtil.icon(q.finished() ? Material.EMERALD_BLOCK : Material.DIAMOND_PICKAXE,
                    msg.get("gui.quest.miner-name", "name", plugin.crypto().minerName(miner)),
                    msg.getList(q.finished() ? "gui.quest.miner-done-lore" : "gui.quest.miner-lore",
                            "done", doneBlocks, "total", q.blocks().size(),
                            "bar", bar(doneBlocks, q.blocks().size()),
                            "units", CryptoManager.fmt(plugin.crypto().payout(uuid, Math.max(1, q.reward()))))),
                    (player, click) -> new MinerQuestMenu(plugin, player, miner).open(player));
        }

        set(45, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    /** Ein Fortschrittsbalken aus 10 Kästchen. */
    private static Component bar(long have, long need) {
        int filled = (int) Math.max(0, Math.min(10, need <= 0 ? 10 : have * 10 / need));
        return Component.text("\u2588".repeat(filled), NamedTextColor.GREEN)
                .append(Component.text("\u2588".repeat(10 - filled), NamedTextColor.DARK_GRAY));
    }
}
