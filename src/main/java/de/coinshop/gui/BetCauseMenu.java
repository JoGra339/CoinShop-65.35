package de.coinshop.gui;

import de.coinshop.BetManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Todesnachrichten tippen: Links-Klick fügt eine Art hinzu, Rechts-Klick nimmt die letzte weg.
 * Je mehr und je unwahrscheinlicher die genannten Arten, desto höher die Quote. Stimmt die Anzahl der Tode,
 * aber nicht alle Arten, gibt es weniger Gewinn.
 */
public final class BetCauseMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22};

    public BetCauseMenu(CoinShopPlugin plugin, Player viewer, UUID target, String targetName, int outcome,
                        List<Integer> chosen) {
        super(plugin, 5, plugin.messages().get("gui.bets.cause-title", "player", targetName));
        Messages msg = plugin.messages();
        BetManager bets = plugin.bets();
        int max = bets.maxCauses(outcome);
        double base = bets.odds(target, outcome) * bets.sessionFactor(viewer.getUniqueId());
        double mult = bets.causeMultiplier(target, chosen);

        for (int cat = 0; cat < BetManager.CAUSES.length; cat++) {
            final int c = cat;
            int already = (int) chosen.stream().filter(x -> x == c).count();
            List<Component> lore = new ArrayList<>(msg.getList("gui.bets.cause-lore",
                    "chance", Math.round(bets.causeProbability(target, cat) * 100), "count", already));
            org.bukkit.inventory.ItemStack item = ItemUtil.icon(BetManager.CAUSE_ICONS[cat],
                    msg.get("gui.bets.cause-name", "cause", BetManager.CAUSES[cat]), lore);
            if (already > 0) {
                item.setAmount(Math.min(64, already));
            }
            set(SLOTS[cat], item, (p, click) -> {
                List<Integer> next = new ArrayList<>(chosen);
                if (click.isRightClick()) {
                    int idx = next.lastIndexOf(c);
                    if (idx >= 0) {
                        next.remove(idx);
                    }
                } else if (next.size() < max) {
                    next.add(c);
                } else {
                    msg.send(p, "bet-too-many-causes", "max", max);
                }
                new BetCauseMenu(plugin, p, target, targetName, outcome, next).open(p);
            });
        }
        set(31, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.bets.cause-sum-name"),
                msg.getList("gui.bets.cause-sum-lore", "outcome", BetManager.outcomeText(outcome),
                        "causes", BetManager.causesText(joined(chosen)), "max", max,
                        "odds", BetManager.oddsText(base * mult),
                        "partial", Math.round(plugin.getConfig().getDouble("bets.cause-partial-factor", 0.6) * 100))), null);
        set(33, ItemUtil.icon(Material.LIME_DYE, msg.get("gui.bets.cause-confirm-name"),
                        msg.getList("gui.bets.cause-confirm-lore", "min", bets.minStake(), "max", bets.maxStakeFor(viewer.getUniqueId(), bets.isBlackSession(viewer.getUniqueId())))),
                (p, click) -> {
                    p.closeInventory();
                    msg.send(p, "bet-ask-stake", "player", targetName, "outcome", BetManager.outcomeText(outcome),
                            "min", bets.minStake(), "max", bets.maxStakeFor(p.getUniqueId(), bets.isBlackSession(p.getUniqueId())));
                    plugin.prompts().start(p, text -> {
                        if (text.equalsIgnoreCase("abbrechen")) {
                            msg.send(p, "ask-cancelled");
                            return;
                        }
                        try {
                            bets.placeBet(p, target, targetName, outcome, Long.parseLong(text.trim().replace(".", "")),
                                    chosen);
                        } catch (NumberFormatException e) {
                            msg.send(p, "ask-invalid");
                        }
                        new BetMenu(plugin, p).open(p);
                    });
                });
        set(36, backButton(), (p, c) -> new BetOutcomeMenu(plugin, p, target, targetName, false).open(p));
        fillEmpty();
    }

    private static String joined(List<Integer> list) {
        StringBuilder sb = new StringBuilder();
        for (int x : list) {
            sb.append(sb.length() > 0 ? "," : "").append(x);
        }
        return sb.toString();
    }
}
