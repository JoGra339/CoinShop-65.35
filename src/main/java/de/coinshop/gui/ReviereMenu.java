package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RevierManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Die Übersicht der 7 Reviere: freikaufen, öffnen, Ranglisten und der Pate. */
public final class ReviereMenu extends Menu {

    public ReviereMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.revier.title"));
        Messages msg = plugin.messages();
        RevierManager reviere = plugin.reviere();
        Database db = plugin.database();
        UUID uuid = viewer.getUniqueId();
        int unlocked = reviere.unlockedCount(uuid);

        for (int r = 1; r <= RevierManager.COUNT; r++) {
            final int revier = r;
            int slot = 9 + r; // 10 bis 16
            if (r <= unlocked) {
                UUID boss = reviere.boss(r);
                List<Component> lore = new ArrayList<>(msg.getList("gui.revier.open-lore",
                        "slots", reviere.slots(uuid, r), "max", RevierManager.MAX_SLOTS,
                        "listed", db.countBlackSells(uuid, r), "sales", db.revierSalesOf(uuid, r),
                        "boss", boss == null ? "-" : db.getName(boss)));
                set(slot, ItemUtil.icon(reviere.icon(r), msg.get("gui.revier.open-name", "name", reviere.name(r), "nr", r), lore),
                        (p, c) -> new RevierMenu(plugin, p, revier).open(p));
            } else if (r == unlocked + 1) {
                set(slot, ItemUtil.icon(Material.RED_STAINED_GLASS_PANE,
                        msg.get("gui.revier.buy-name", "name", reviere.name(r), "nr", r),
                        msg.getList("gui.revier.buy-lore", "price", reviere.unlockPrice(r),
                                "start", reviere.slotStartPrice(r))),
                        (p, c) -> {
                            reviere.buyRevier(p);
                            new ReviereMenu(plugin, p).open(p);
                        });
            } else {
                set(slot, ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE,
                        msg.get("gui.revier.locked-name", "name", reviere.name(r), "nr", r),
                        msg.getList("gui.revier.locked-lore", "price", reviere.unlockPrice(r))), null);
            }
        }

        set(21, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.revier.rank-name"),
                msg.getList("gui.revier.rank-lore")), (p, c) -> new RevierRankMenu(plugin, p, 0).open(p));

        UUID pate = reviere.pate();
        List<Component> pateLore = new ArrayList<>(msg.getList("gui.revier.pate-lore",
                "pate", pate == null ? "-" : db.getName(pate),
                "income", reviere.pateIncomeTotal(),
                "share", plugin.getConfig().getDouble("revier.pate-fee-share-percent", 5),
                                "time", ItemUtil.formatDuration(reviere.timeUntilEvaluation().toMillis())));
        set(23, ItemUtil.icon(Material.WITHER_SKELETON_SKULL, msg.get("gui.revier.pate-name"), pateLore), null);


        set(31, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.revier.protect-name"),
                msg.getList("gui.revier.protect-lore")), (p, c) -> new ContractsMenu(plugin, p).open(p));

        set(36, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(44, balanceIcon(viewer), null);
        fillEmpty();
    }
}
