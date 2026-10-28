package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WheelManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Casino: Hier gibt es das Glücksrad, den Spielautomaten und das Rubbellos.
 * Alle drei zahlen vom Konto des Shops und sind absichtlich schwer zu gewinnen.
 */
public final class CasinoMenu extends Menu {

    public CasinoMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.casino.title"));
        Messages msg = plugin.messages();

        set(10, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.casino.wheel-name"),
                        msg.getList("gui.casino.wheel-lore", "price", plugin.wheel().price(),
                                "odds", plugin.wheel().jackpotOdds())),
                (player, click) -> new WheelMenu(plugin, player).open(player));

        set(12, ItemUtil.icon(Material.AMETHYST_CLUSTER, msg.get("gui.casino.slots-name"),
                        msg.getList("gui.casino.slots-lore", "price", plugin.slots().price(),
                                "odds", plugin.slots().jackpotOdds())),
                (player, click) -> new SlotsMenu(plugin, player).open(player));

        set(14, ItemUtil.icon(Material.PAPER, msg.get("gui.casino.scratch-name"),
                        msg.getList("gui.casino.scratch-lore", "price", plugin.scratch().price(),
                                "odds", plugin.scratch().jackpotOdds())),
                (player, click) -> new ScratchMenu(plugin, player).open(player));

        set(16, ItemUtil.icon(Material.TNT, msg.get("gui.casino.double-name"),
                        msg.getList("gui.casino.double-lore", "chance", (long) plugin.doubleGame().winChance(),
                                "steps", plugin.doubleGame().maxSteps())),
                (player, click) -> new DoubleMenu(plugin, player).open(player));

        // Gratis-Glücksrad (einmal pro Tag)
        set(22, ItemUtil.icon(Material.HEART_OF_THE_SEA, msg.get("gui.main.dailywheel-name"),
                        msg.getList("gui.main.dailywheel-lore")),
                (player, click) -> plugin.dailyWheel().spin(player));

        set(27, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        set(30, accountIcon(plugin), null);
        set(32, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    // ---- gemeinsame Anzeigen für alle Casino-Spiele ----

    /** Das Konto des Shops (= der Jackpot). */
    static ItemStack accountIcon(CoinShopPlugin plugin) {
        Messages msg = plugin.messages();
        return ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.wheel.account-name"),
                msg.getList("gui.wheel.account-lore", "coins", plugin.database().getShopAccount()));
    }

    /** Die Tabelle mit den Gewinnchancen eines Spiels. */
    static ItemStack tableIcon(CoinShopPlugin plugin, WheelManager game) {
        Messages msg = plugin.messages();
        List<Component> lore = new ArrayList<>();
        for (WheelManager.Tier tier : game.tiers()) {
            StringBuilder amounts = new StringBuilder();
            for (long amount : tier.amounts()) {
                if (amounts.length() > 0) {
                    amounts.append(" / ");
                }
                amounts.append(Messages.number(amount));
            }
            lore.add(msg.get("gui.wheel.table-tier", "odds", tier.odds(), "amounts", amounts.toString()));
        }
        lore.add(msg.get("gui.wheel.table-jackpot", "odds", game.jackpotOdds()));
        return ItemUtil.icon(Material.BOOK, msg.get("gui.wheel.table-name"), lore);
    }
}
