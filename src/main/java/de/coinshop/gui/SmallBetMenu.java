package de.coinshop.gui;

import de.coinshop.BetManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Kleine Wetten: Spieler wählen, dann Zeitfenster und Todesart festlegen und den Einsatz in den Chat schreiben. */
public final class SmallBetMenu extends Menu {

    private static final int[] CAUSE_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
    private static final int[] HOUR_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    /** Auswahl des Spielers (nur Spieler, die gerade online sind). */
    public static final class Target extends Menu {
        public Target(CoinShopPlugin plugin, Player viewer) {
            super(plugin, 6, plugin.messages().get("gui.small.target-title"));
            Messages msg = plugin.messages();
            BetManager bets = plugin.bets();
            int slot = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(viewer) || slot >= 45 || bets.optedOut(online.getUniqueId())) {
                    continue;
                }
                UUID id = online.getUniqueId();
                String name = online.getName();
                int hours = bets.smallHours().get(0);
                set(slot++, BountyMenu.head(id, msg.get("gui.bets.target-name", "player", name),
                                msg.getList("gui.small.target-lore",
                                        "rate", String.format(java.util.Locale.GERMANY, "%.1f", bets.weeklyRate(id)))),
                        (p, c) -> new SmallBetMenu(plugin, p, id, name, hours, -1).open(p));
            }
            set(49, backButton(), (p, c) -> new BetMenu(plugin, p).open(p));
            fillEmpty();
        }
    }

    public SmallBetMenu(CoinShopPlugin plugin, Player viewer, UUID target, String targetName, int hours, int cause) {
        super(plugin, 6, plugin.messages().get("gui.small.title", "player", targetName));
        Messages msg = plugin.messages();
        BetManager bets = plugin.bets();
        List<Integer> windows = bets.smallHours();
        for (int i = 0; i < windows.size() && i < HOUR_SLOTS.length; i++) {
            final int h = windows.get(i);
            boolean on = h == hours;
            set(HOUR_SLOTS[i], ItemUtil.icon(on ? Material.LIME_CONCRETE : Material.CLOCK,
                            msg.get(on ? "gui.small.hours-on" : "gui.small.hours-off", "hours", h),
                            msg.getList("gui.small.hours-lore", "odds", BetManager.oddsText(bets.smallOdds(target, h, cause)))),
                    (p, c) -> new SmallBetMenu(plugin, p, target, targetName, h, cause).open(p));
        }
        set(19, ItemUtil.icon(cause < 0 ? Material.LIME_CONCRETE : Material.PAPER,
                        msg.get(cause < 0 ? "gui.small.cause-any-on" : "gui.small.cause-any-off"),
                        msg.getList("gui.small.cause-any-lore")),
                (p, c) -> new SmallBetMenu(plugin, p, target, targetName, hours, -1).open(p));
        for (int cat = 0; cat < BetManager.CAUSES.length && cat < CAUSE_SLOTS.length; cat++) {
            final int k = cat;
            boolean on = cat == cause;
            set(CAUSE_SLOTS[cat], ItemUtil.icon(on ? Material.LIME_CONCRETE : BetManager.CAUSE_ICONS[cat],
                            msg.get(on ? "gui.small.cause-on" : "gui.small.cause-off", "cause", BetManager.CAUSES[cat]),
                            msg.getList("gui.small.cause-lore", "odds", BetManager.oddsText(bets.smallOdds(target, hours, k)))),
                    (p, c) -> new SmallBetMenu(plugin, p, target, targetName, hours, k).open(p));
        }
        double odds = bets.smallOdds(target, hours, cause);
        set(40, ItemUtil.icon(Material.EMERALD, msg.get("gui.small.confirm-name"),
                        msg.getList("gui.small.confirm-lore", "player", targetName, "hours", hours,
                                "cause", cause < 0 ? "egal" : BetManager.CAUSES[cause], "odds", BetManager.oddsText(odds),
                                "min", bets.minStake(), "max", bets.smallMaxStake(viewer.getUniqueId()),
                                "fee", bets.feePercent())),
                (p, c) -> {
                    p.closeInventory();
                    msg.send(p, "bet-small-ask", "min", bets.minStake(), "max", bets.smallMaxStake(p.getUniqueId()));
                    plugin.prompts().start(p, text -> {
                        if (text.equalsIgnoreCase("abbrechen")) {
                            msg.send(p, "ask-cancelled");
                            return;
                        }
                        long stake;
                        try {
                            stake = Long.parseLong(text.trim().replace(".", ""));
                        } catch (NumberFormatException e) {
                            msg.send(p, "ask-invalid");
                            return;
                        }
                        bets.placeSmallBet(p, target, targetName, hours, cause, stake);
                    });
                });
        set(45, backButton(), (p, c) -> new Target(plugin, p).open(p));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
