package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.Database.RevierEntry;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RevierManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/** Rangliste der Verkäufer dieser Woche: pro Revier (1 bis 7) und gesamt (0). */
public final class RevierRankMenu extends Menu {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};

    public RevierRankMenu(CoinShopPlugin plugin, Player viewer, int revier) {
        super(plugin, 6, plugin.messages().get("gui.revier.rank-title",
                "name", revier == 0 ? plugin.messages().get("gui.revier.overall") : plugin.messages().get("gui.revier.rank-of",
                        "name", plugin.reviere().name(revier))));
        Messages msg = plugin.messages();
        RevierManager reviere = plugin.reviere();
        Database db = plugin.database();

        // Reiter: Gesamt und die 7 Reviere
        set(1, ItemUtil.icon(revier == 0 ? Material.NETHER_STAR : Material.GRAY_DYE,
                msg.get("gui.revier.tab-overall"), msg.getList("gui.revier.tab-lore")),
                (p, c) -> new RevierRankMenu(plugin, p, 0).open(p));
        for (int r = 1; r <= RevierManager.COUNT; r++) {
            final int target = r;
            set(1 + r, ItemUtil.icon(revier == r ? reviere.icon(r) : Material.GRAY_DYE,
                    msg.get("gui.revier.tab-name", "name", reviere.name(r)), msg.getList("gui.revier.tab-lore")),
                    (p, c) -> new RevierRankMenu(plugin, p, target).open(p));
        }

        List<RevierEntry> top = db.topRevier(revier, SLOTS.length);
        for (int i = 0; i < SLOTS.length; i++) {
            if (i < top.size()) {
                RevierEntry e = top.get(i);
                ItemStack head = ItemUtil.icon(Material.PLAYER_HEAD,
                        msg.get("gui.revier.rank-entry", "place", i + 1, "player", db.getName(e.uuid())),
                        msg.getList("gui.revier.rank-entry-lore", "coins", e.coins()));
                OfflinePlayer off = Bukkit.getOfflinePlayer(e.uuid());
                head.editMeta(meta -> {
                    if (meta instanceof SkullMeta skull) {
                        skull.setOwningPlayer(off);
                    }
                });
                set(SLOTS[i], head, null);
            } else if (i == 0) {
                set(SLOTS[i], ItemUtil.icon(Material.BARRIER, msg.get("gui.revier.rank-empty"), List.of()), null);
            }
        }

        UUID viewerId = viewer.getUniqueId();
        set(40, ItemUtil.icon(Material.PAPER, msg.get("gui.revier.mine-name"),
                msg.getList("gui.revier.mine-lore", "coins", db.revierSalesOf(viewerId, revier),
                        "gap", Math.max(0, (top.isEmpty() ? 0 : top.get(0).coins()) - db.revierSalesOf(viewerId, revier)),
                        "leader", top.isEmpty() ? 0 : top.get(0).coins())), null);
        UUID boss = revier == 0 ? reviere.pate() : reviere.boss(revier);
        set(41, ItemUtil.icon(revier == 0 ? Material.WITHER_SKELETON_SKULL : Material.GOLDEN_HELMET,
                msg.get(revier == 0 ? "gui.revier.pate-name" : "gui.revier.boss-name"),
                msg.getList("gui.revier.boss-lore", "player", boss == null ? "-" : db.getName(boss),
                        "time", ItemUtil.formatDuration(reviere.timeUntilEvaluation().toMillis()))), null);

        set(45, backButton(), (p, c) -> {
            if (revier == 0) {
                new ReviereMenu(plugin, p).open(p);
            } else {
                new RevierMenu(plugin, p, revier).open(p);
            }
        });
        set(53, balanceIcon(viewer), null);
        fillEmpty();
    }
}
