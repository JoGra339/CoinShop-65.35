package de.coinshop.gui;

import de.coinshop.BetManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Illegale Wetten (Schwarzmarkt): schwarze Wette, Insider-Tipp, Quote türken und Wettmanipulation. */
public final class IllegalBetsMenu extends Menu {

    public IllegalBetsMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.illegal.title"));
        Messages msg = plugin.messages();
        BetManager bets = plugin.bets();
        set(10, ItemUtil.icon(Material.BLACK_CONCRETE, msg.get("gui.illegal.black-name"),
                        msg.getList("gui.illegal.black-lore", "odds", Math.round(bets.blackOddsFactor() * 100),
                                "fee", bets.blackFeePercent(), "raid", bets.blackRaidChance(),
                                "max", bets.maxStakeFor(viewer.getUniqueId(), true))),
                (p, c) -> {
                    if (bets.closed()) {
                        msg.send(p, "bet-closed");
                        return;
                    }
                    if (bets.optedOut(p.getUniqueId())) {
                        msg.send(p, "bet-you-optout");
                        return;
                    }
                    bets.setBlackSession(p.getUniqueId(), true);
                    new BetTargetMenu(plugin, p, false).open(p);
                });
        set(12, ItemUtil.icon(Material.ENDER_EYE, msg.get("gui.illegal.tip-name"),
                        msg.getList("gui.illegal.tip-lore", "price", bets.tipTotal(viewer.getUniqueId()))),
                (p, c) -> {
                    bets.buyTip(p);
                    p.closeInventory();
                });
        set(14, ItemUtil.icon(Material.SPYGLASS, msg.get("gui.bmhub.rig"),
                        msg.getList("gui.illegal.rig-lore")),
                (p, c) -> {
                    bets.setBlackSession(p.getUniqueId(), false);
                    new BetTargetMenu(plugin, p, true).open(p);
                });
        set(16, ItemUtil.icon(Material.WITHER_SKELETON_SKULL, msg.get("gui.illegal.manip-name"),
                        msg.getList("gui.illegal.manip-lore", "price", plugin.blackMarket().priceFor(viewer.getUniqueId(), bets.manipCost()),
                                "chance", bets.manipChance(viewer.getUniqueId()))),
                (p, c) -> new ManipulationMenu(plugin, p).open(p));
        set(18, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
