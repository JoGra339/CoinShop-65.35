package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Messages;
import de.coinshop.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Auswahl, auf wen ein Kopfgeld ausgesetzt wird: Spieler, die online sind, oder per Namen. */
public final class BountyTargetMenu extends Menu {

    public BountyTargetMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.bounty.target-title"));
        Messages msg = plugin.messages();
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(viewer) || slot >= 45 || !plugin.blackMarket().bountyTargetOk(online.getUniqueId())) {
                continue;
            }
            UUID id = online.getUniqueId();
            String name = online.getName();
            set(slot++, BountyMenu.head(id, msg.get("gui.bounty.target-name", "player", name),
                    msg.getList("gui.bounty.target-lore")), (p, c) -> askAmount(p, id, name));
        }
        set(49, ItemUtil.icon(Material.NAME_TAG, msg.get("gui.bounty.by-name"), msg.getList("gui.bounty.by-name-lore")),
                (p, c) -> {
                    p.closeInventory();
                    msg.send(p, "bounty-ask-name");
                    plugin.prompts().start(p, text -> {
                        if (text.equalsIgnoreCase("abbrechen")) {
                            msg.send(p, "ask-cancelled");
                            return;
                        }
                        Optional<UUID> found = plugin.database().findUuidByName(text.trim());
                        if (found.isEmpty()) {
                            msg.send(p, "bounty-unknown");
                            return;
                        }
                        askAmount(p, found.get(), plugin.database().getName(found.get()));
                    });
                });
        set(45, backButton(), (p, c) -> new BountyMenu(plugin, p).open(p));
        fillEmpty();
    }

    private void askAmount(Player player, UUID target, String name) {
        Messages msg = plugin.messages();
        player.closeInventory();
        msg.send(player, "bounty-ask-amount", "player", name, "min", plugin.blackMarket().bountyMin(),
                "percent", plugin.blackMarket().bountyFeePercent());
        plugin.prompts().start(player, text -> {
            if (text.equalsIgnoreCase("abbrechen")) {
                msg.send(player, "ask-cancelled");
                return;
            }
            try {
                long amount = Long.parseLong(text.trim().replace(".", ""));
                plugin.blackMarket().placeBounty(player, target, name, amount);
            } catch (NumberFormatException e) {
                msg.send(player, "ask-invalid");
            }
            new BountyMenu(plugin, player).open(player);
        });
    }
}
