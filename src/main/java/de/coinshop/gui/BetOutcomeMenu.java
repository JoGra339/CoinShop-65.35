package de.coinshop.gui;

import de.coinshop.BetManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Auswahl des Ergebnisses (0 bis 10 oder mehr Tode) mit den aktuellen Quoten. Im Schwarzmarkt-Modus wird die Quote getürkt. */
public final class BetOutcomeMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};

    public BetOutcomeMenu(CoinShopPlugin plugin, Player viewer, UUID target, String targetName, boolean rig) {
        super(plugin, 5, plugin.messages().get(rig ? "gui.bets.rig-outcome-title" : "gui.bets.outcome-title",
                "player", targetName));
        Messages msg = plugin.messages();
        BetManager bets = plugin.bets();
        final boolean baron = rig && bets.isBaronSession(viewer.getUniqueId());
        for (int outcome = 0; outcome <= BetManager.MAX_OUTCOME; outcome++) {
            final int o = outcome;
            double odds = rig ? bets.adjustedOdds(target, o) : bets.odds(target, o) * bets.sessionFactor(viewer.getUniqueId());
            Material icon = outcome == 0 ? Material.TOTEM_OF_UNDYING : Material.SKELETON_SKULL;
            int amount = Math.max(1, Math.min(64, outcome));
            if (odds <= 0) {
                set(SLOTS[o], ItemUtil.icon(Material.BARRIER,
                        msg.get("gui.bets.outcome-name", "outcome", BetManager.outcomeText(o)),
                        msg.getList("gui.bets.outcome-impossible")), null);
                continue;
            }
            if (rig) {
                set(SLOTS[o], ItemUtil.icon(icon, msg.get("gui.bets.outcome-name", "outcome", BetManager.outcomeText(o)),
                                msg.getList(bets.isRigged(target, o) ? "gui.bets.rig-already"
                                                : baron ? "gui.bets.baron-rig-lore" : "gui.bets.rig-lore",
                                        "odds", BetManager.oddsText(odds),
                                        "new", BetManager.oddsText(odds * bets.rigFactor()),
                                        "price", plugin.blackMarket().priceFor(viewer.getUniqueId(), bets.rigCost()),
                                        "chance", bets.rigChance(viewer.getUniqueId()),
                                        "percent", bets.rigSharePercent())),
                        (p, c) -> {
                            bets.rig(p, target, targetName, o);
                            if (baron) {
                                new BetMenu(plugin, p).open(p);
                            } else {
                                new BlackMarketHubMenu(plugin, p).open(p);
                            }
                        });
            } else {
                set(SLOTS[o], ItemUtil.icon(icon, msg.get("gui.bets.outcome-name", "outcome", BetManager.outcomeText(o)),
                                msg.getList("gui.bets.outcome-lore", "odds", BetManager.oddsText(odds),
                                        "now", bets.deathsThisWeek(target), "count", bets.countOn(target, o),
                                        "sum", bets.stakeOn(target, o), "max", bets.maxCauses(o))),
                        (p, c) -> new BetCauseMenu(plugin, p, target, targetName, o, List.of()).open(p));
            }
        }
        set(31, ItemUtil.icon(Material.PAPER, msg.get("gui.bets.outcome-info-name"),
                msg.getList(rig ? "gui.bets.rig-info-lore" : "gui.bets.outcome-info-lore", "player", targetName,
                        "now", bets.deathsThisWeek(target))), null);
        set(36, backButton(), (p, c) -> new BetTargetMenu(plugin, p, rig).open(p));
        fillEmpty();
    }
}
