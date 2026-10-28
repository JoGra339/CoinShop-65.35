package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.DoubleManager;
import de.coinshop.DoubleManager.Result;
import de.coinshop.DoubleManager.Session;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Doppelt oder Nichts. Ohne laufendes Spiel wählst du deinen Einsatz; danach kannst du verdoppeln
 * (Risiko!) oder auszahlen. Das Spiel merkt sich dein Spiel, auch wenn du das Menü schließt.
 */
public final class DoubleMenu extends Menu {

    private final DoubleManager game;

    public DoubleMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.double.title"));
        this.game = plugin.doubleGame();
        Messages msg = plugin.messages();
        Session session = game.session(viewer.getUniqueId());

        if (session == null) {
            // ---- Einsatz wählen ----
            int[] slots = {11, 13, 15};
            List<Long> stakes = game.stakes();
            for (int i = 0; i < stakes.size(); i++) {
                final long stake = stakes.get(i);
                set(slots[i], ItemUtil.icon(Material.EMERALD, msg.get("gui.double.stake-name", "stake", stake),
                                msg.getList("gui.double.stake-lore", "stake", stake,
                                        "max", game.potAt(stake, game.maxSteps()),
                                        "chance", (long) game.winChance())),
                        (player, click) -> {
                            if (game.start(player, stake)) {
                                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 1f);
                                new DoubleMenu(plugin, player).open(player);
                            }
                        });
            }
        } else {
            // ---- Leiter: welche Stufe ist erreicht? ----
            int steps = game.maxSteps();
            for (int i = 1; i <= steps; i++) {
                boolean reached = i <= session.step();
                Material pane = reached ? Material.LIME_STAINED_GLASS_PANE
                        : (i == session.step() + 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
                set(i - 1 + (9 - steps) / 2, ItemUtil.icon(pane,
                        msg.get("gui.double.ladder-name", "step", i, "coins", game.potAt(session.stake(), i)),
                        List.of()), null);
            }
            // Aktueller Gewinn in der Mitte
            set(13, ItemUtil.icon(Material.GOLD_BLOCK, msg.get("gui.double.pot-name", "coins", session.pot()),
                    msg.getList("gui.double.pot-lore", "stake", session.stake(), "step", session.step())), null);

            long next = session.pot() * 2;
            set(11, ItemUtil.icon(Material.TNT, msg.get("gui.double.gamble-name"),
                            msg.getList("gui.double.gamble-lore", "chance", (long) game.winChance(),
                                    "now", session.pot(), "next", next)),
                    (player, click) -> gamble(player));

            if (session.step() > 0) {
                set(15, ItemUtil.icon(Material.EMERALD_BLOCK, msg.get("gui.double.cash-name"),
                                msg.getList("gui.double.cash-lore", "coins", session.pot())),
                        (player, click) -> cashOut(player));
            } else {
                set(15, ItemUtil.icon(Material.BARRIER, msg.get("gui.double.cash-locked-name"),
                        msg.getList("gui.double.cash-locked-lore")), null);
            }
        }

        set(18, backButton(), (player, click) -> new CasinoMenu(plugin, player).open(player));
        set(20, CasinoMenu.accountIcon(plugin), null);
        set(24, ItemUtil.icon(Material.BOOK, msg.get("gui.double.rules-name"),
                msg.getList("gui.double.rules-lore", "chance", (long) game.winChance(),
                        "steps", game.maxSteps(), "from", game.consolationFromStep(),
                        "percent", game.consolationPercent())), null);
        set(26, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    private void gamble(Player player) {
        Messages msg = plugin.messages();
        Result r = game.gamble(player);
        if (r == null) {
            return;
        }
        if (r.won() && !r.finished()) {
            msg.send(player, "double-win-step", "step", r.session().step(), "coins", r.session().pot());
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f + 0.1f * r.session().step());
        } else if (r.won()) {
            msg.send(player, "double-maxed", "coins", r.payout());
            game.broadcastMax(player.getName(), r.payout());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else if (r.payout() > 0) {
            msg.send(player, "double-lost-consolation", "coins", r.payout());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        } else {
            msg.send(player, "double-lost");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        }
        new DoubleMenu(plugin, player).open(player);
    }

    private void cashOut(Player player) {
        long paid = game.cashOut(player.getUniqueId());
        if (paid > 0) {
            plugin.messages().send(player, "double-cashout", "coins", paid);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        new DoubleMenu(plugin, player).open(player);
    }
}
