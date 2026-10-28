package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Auswahl des Spielers, auf den gewettet wird (rig = true: dessen Quote wird getürkt, Schwarzmarkt). */
public final class BetTargetMenu extends Menu {

    public BetTargetMenu(CoinShopPlugin plugin, Player viewer, boolean rig) {
        super(plugin, 6, plugin.messages().get(rig && plugin.bets().isBaronSession(viewer.getUniqueId()) ? "gui.bets.baron-target-title"
                : rig ? "gui.bets.rig-target-title"
                : plugin.bets().isBlackSession(viewer.getUniqueId()) ? "gui.bets.black-target-title" : "gui.bets.target-title"));
        Messages msg = plugin.messages();
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(viewer) || slot >= 45 || plugin.bets().optedOut(online.getUniqueId())) {
                continue;
            }
            UUID id = online.getUniqueId();
            String name = online.getName();
            set(slot++, BountyMenu.head(id, msg.get("gui.bets.target-name", "player", name),
                    msg.getList("gui.bets.target-lore", "rate",
                            String.format(java.util.Locale.GERMANY, "%.1f", plugin.bets().weeklyRate(id)),
                            "now", plugin.bets().deathsThisWeek(id), "count", plugin.bets().countOn(id, -1),
                            "sum", plugin.bets().stakeOn(id, -1))),
                    (p, c) -> new BetOutcomeMenu(plugin, p, id, name, rig).open(p));
        }
        set(49, ItemUtil.icon(Material.NAME_TAG, msg.get("gui.bets.by-name"), msg.getList("gui.bets.by-name-lore")),
                (p, c) -> {
                    p.closeInventory();
                    msg.send(p, "bounty-ask-name");
                    plugin.prompts().start(p, text -> {
                        if (text.equalsIgnoreCase("abbrechen")) {
                            msg.send(p, "ask-cancelled");
                            return;
                        }
                        Optional<UUID> found = plugin.database().findUuidByName(text.trim());
                        if (found.isEmpty() || found.get().equals(p.getUniqueId())) {
                            msg.send(p, "bounty-unknown");
                            return;
                        }
                        new BetOutcomeMenu(plugin, p, found.get(), plugin.database().getName(found.get()), rig).open(p);
                    });
                });
        set(45, backButton(), (p, c) -> {
            if (rig && plugin.bets().isBaronSession(p.getUniqueId())) {
                plugin.bets().setBaronSession(p.getUniqueId(), false);
                new BetMenu(plugin, p).open(p);
            } else if (rig) {
                new BlackMarketHubMenu(plugin, p).open(p);
            } else {
                new BetMenu(plugin, p).open(p);
            }
        });
        fillEmpty();
    }
}
