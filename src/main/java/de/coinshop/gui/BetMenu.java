package de.coinshop.gui;

import de.coinshop.BetManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Bet;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Das Wettbüro: eigene offene Wetten und der Weg zu einer neuen Wette. */
public final class BetMenu extends Menu {

    public BetMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.bets.title"));
        Messages msg = plugin.messages();
        BetManager bets = plugin.bets();

        List<Bet> own = plugin.database().getBetsBy(viewer.getUniqueId());
        for (int i = 0; i < own.size() && i < 18; i++) {
            Bet bet = own.get(i);
            set(9 + i, BountyMenu.head(bet.target(), msg.get("gui.bets.bet-name", "player", bet.targetName()),
                    msg.getList("gui.bets.bet-lore", "outcome", BetManager.outcomeText(bet.outcome()),
                            "stake", bet.stake(), "odds", BetManager.oddsText(bet.odds() / 100.0),
                            "win", bet.stake() * bet.odds() / 100 * bet.causeMult() / 100,
                            "causes", BetManager.causesText(bet.causes()),
                            "now", bets.deathsThisWeek(bet.target()))), null);
        }
        List<de.coinshop.Database.SmallBet> small = bets.smallBetsBy(viewer.getUniqueId());
        for (int i = 0; i < small.size() && i < 9; i++) {
            de.coinshop.Database.SmallBet sb = small.get(i);
            long minutes = Math.max(0, (sb.until() - System.currentTimeMillis()) / 60_000L);
            set(27 + i, BountyMenu.head(sb.target(), msg.get("gui.bets.small-name", "player", sb.targetName()),
                    msg.getList("gui.bets.small-lore", "stake", sb.stake(), "odds", BetManager.oddsText(sb.odds() / 100.0),
                            "win", sb.stake() * sb.odds() / 100, "minutes", minutes,
                            "cause", sb.cause() < 0 ? "egal" : BetManager.CAUSES[sb.cause()])), null);
        }
        if (own.isEmpty() && small.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.bets.none"), msg.getList("gui.bets.none-lore")), null);
        }
        int hours = (int) Math.floor(bets.hoursLeft());
        int openHours = (int) Math.ceil(bets.openHoursLeft());
        set(37, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.bets.new-name"),
                        msg.getList("gui.bets.new-lore", "min", bets.minStake(), "max", bets.maxStakeFor(viewer.getUniqueId(), false),
                                "fee", bets.feePercent(), "open", openHours > 0 ? "Jetzt offen, noch " + openHours + " Std." : "Geschlossen (nur Sonntag 12:00 bis Montag 12:00)")),
                (p, c) -> {
                    if (bets.closed()) {
                        msg.send(p, "bet-closed");
                        return;
                    }
                    if (bets.optedOut(p.getUniqueId())) {
                        msg.send(p, "bet-you-optout");
                        return;
                    }
                    bets.setBlackSession(p.getUniqueId(), false);
                    new BetTargetMenu(plugin, p, false).open(p);
                });
        set(38, ItemUtil.icon(Material.CLOCK, msg.get("gui.bets.small-new-name"),
                        msg.getList("gui.bets.small-new-lore", "min", bets.minStake(),
                                "max", bets.smallMaxStake(viewer.getUniqueId()), "fee", bets.feePercent(),
                                "open", small.size(), "limit", bets.smallMaxOpen())),
                (p, c) -> {
                    if (bets.optedOut(p.getUniqueId())) {
                        msg.send(p, "bet-you-optout");
                        return;
                    }
                    new SmallBetMenu.Target(plugin, p).open(p);
                });
        java.util.UUID baronId = bets.baron();
        boolean isBaron = baronId != null && baronId.equals(viewer.getUniqueId());
        String baronName = baronId == null ? "-" : plugin.database().getName(baronId);
        set(40, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.bets.baron-name"),
                        msg.getList(isBaron ? (bets.baronUsed() ? "gui.bets.baron-lore-used" : "gui.bets.baron-lore-you") : "gui.bets.baron-lore",
                                "baron", baronName == null ? "?" : baronName, "percent", bets.baronFeePercent(),
                                "min", bets.baronMinStake())),
                (p, c) -> {
                    if (!isBaron) {
                        return;
                    }
                    if (bets.closed()) {
                        msg.send(p, "bet-closed");
                        return;
                    }
                    if (bets.baronUsed()) {
                        msg.send(p, "bet-baron-used");
                        return;
                    }
                    bets.setBaronSession(p.getUniqueId(), true);
                    new BetTargetMenu(plugin, p, true).open(p);
                });
        set(39, ItemUtil.icon(Material.BOOKSHELF, msg.get("gui.bets.board-name"), msg.getList("gui.bets.board-lore")),
                (p, c) -> new BetBoardMenu(plugin, p).open(p));
        boolean out = bets.optedOut(viewer.getUniqueId());
        set(42, ItemUtil.icon(out ? Material.RED_DYE : Material.LIME_DYE,
                        msg.get(out ? "gui.bets.optout-on-name" : "gui.bets.optout-off-name"),
                        msg.getList(out ? "gui.bets.optout-on-lore" : "gui.bets.optout-off-lore")),
                (p, c) -> {
                    bets.toggleOptOut(p);
                    new BetMenu(plugin, p).open(p);
                });
        set(36, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        set(44, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
