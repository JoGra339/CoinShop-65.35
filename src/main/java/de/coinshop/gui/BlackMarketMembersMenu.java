package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Wer alles den Schwarzmarkt freigeschaltet hat (Lizenz Schmuggler). */
public final class BlackMarketMembersMenu extends Menu {

    private static final int MAX_SHOWN = 45;

    public BlackMarketMembersMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.members.title"));
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (!plugin.blackMarket().unlocked(viewer)) {
            set(22, ItemUtil.icon(Material.BARRIER, msg.get("gui.blackmarket.locked-title"),
                    msg.getList("gui.blackmarket.locked-lore")), null);
            set(45, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
            fillEmpty();
            return;
        }
        UUID pate = plugin.reviere().pate();
        int shown = 0;
        int total = 0;
        for (Database.Account a : db.allAccounts()) {
            if (!plugin.licenses().blackMarketUnlocked(a.uuid())) {
                continue;
            }
            total++;
            if (shown >= MAX_SHOWN) {
                continue;
            }
            List<Component> lore = new ArrayList<>();
            if (a.uuid().equals(pate)) {
                lore.add(msg.get("gui.members.pate"));
            } else if (plugin.reviere().isBoss(a.uuid())) {
                lore.add(msg.get("gui.members.boss"));
            }
            boolean bountyOk = !plugin.blackMarket().bountyOptOut(a.uuid());
            lore.add(msg.get(bountyOk ? "gui.members.bounty-ok" : "gui.members.bounty-closed"));
            lore.add(msg.get(Bukkit.getPlayer(a.uuid()) != null ? "gui.members.online" : "gui.members.offline"));
            set(shown++, BountyMenu.head(a.uuid(), msg.get("gui.members.entry", "name", db.getName(a.uuid())), lore), null);
        }
        if (total == 0) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.members.empty"), List.of()), null);
        }
        set(49, ItemUtil.icon(Material.BOOK, msg.get("gui.members.count", "count", total), msg.getList("gui.members.count-lore")), null);
        set(45, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), null);
        fillEmpty();
    }
}
