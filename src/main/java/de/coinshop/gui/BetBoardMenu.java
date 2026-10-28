package de.coinshop.gui;

import de.coinshop.BetManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Bet;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wettübersicht: Auf wen wie oft und mit wie viel Coins gewettet wurde (das beeinflusst die Quoten). */
public final class BetBoardMenu extends Menu {

    public BetBoardMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.bets.board-title"));
        Messages msg = plugin.messages();
        BetManager bets = plugin.bets();
        Map<UUID, String> targets = new LinkedHashMap<>();
        for (Bet bet : plugin.database().getBets()) {
            targets.putIfAbsent(bet.target(), bet.targetName());
        }
        int slot = 0;
        for (Map.Entry<UUID, String> entry : targets.entrySet()) {
            if (slot >= 45) {
                break;
            }
            UUID id = entry.getKey();
            List<Component> lore = new ArrayList<>(msg.getList("gui.bets.board-lore-head",
                    "count", bets.countOn(id, -1), "sum", bets.stakeOn(id, -1)));
            for (int o = 0; o <= BetManager.MAX_OUTCOME; o++) {
                int n = bets.countOn(id, o);
                if (n == 0) {
                    continue;
                }
                double odds = bets.odds(id, o);
                lore.add(msg.get("gui.bets.board-line", "outcome", BetManager.outcomeText(o), "count", n,
                        "sum", bets.stakeOn(id, o),
                        "odds", odds > 0 ? BetManager.oddsText(odds) : "-"));
            }
            set(slot++, BountyMenu.head(id, msg.get("gui.bets.target-name", "player", entry.getValue()), lore), null);
        }
        if (targets.isEmpty()) {
            set(22, de.coinshop.ItemUtil.icon(org.bukkit.Material.PAPER, msg.get("gui.bets.board-empty"),
                    msg.getList("gui.bets.board-empty-lore")), null);
        }
        set(45, backButton(), (p, c) -> new BetMenu(plugin, p).open(p));
        fillEmpty();
    }
}
