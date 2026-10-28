package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.StockHolding;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.StockManager;
import de.coinshop.StockManager.BuyQuote;
import de.coinshop.StockManager.SellQuote;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Die Börse: aktueller Kurs und Verlauf, Depot, Aktien kaufen (feste Beträge oder eigener Betrag)
 * und verkaufen (alle oder eigene Anzahl).
 */
public final class StockMenu extends Menu {

    public StockMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.stock.title"));
        Messages msg = plugin.messages();
        StockManager stock = plugin.stock();
        UUID uuid = viewer.getUniqueId();

        long price = stock.price();
        long previous = stock.previousPrice();
        long diff = price - previous;

        // ---- Kurs-Anzeige (oben Mitte) ----
        String percent = previous > 0
                ? String.format(Locale.GERMANY, "%.0f", Math.abs(diff) * 100.0 / previous) : "0";
        List<Component> priceLore = new ArrayList<>();
        if (diff > 0) {
            priceLore.add(msg.get("gui.stock.change-up", "diff", diff, "percent", percent));
        } else if (diff < 0) {
            priceLore.add(msg.get("gui.stock.change-down", "diff", -diff, "percent", percent));
        } else {
            priceLore.add(msg.get("gui.stock.change-same"));
        }
        priceLore.add(msg.get("gui.stock.history-line", "history", historyText(stock)));
        priceLore.addAll(msg.getList("gui.stock.price-lore-foot",
                "min", stock.minPrice(), "max", stock.maxPrice(),
                "hour", plugin.serverShop().resetHour(), "percent", stock.feePercentText(viewer.getUniqueId())));
        set(4, ItemUtil.icon(Material.PAPER, msg.get("gui.stock.price-name", "price", price), priceLore), null);

        // ---- Kaufen: bis zu 3 feste Beträge + eigener Betrag ----
        List<Long> amounts = stock.quickAmounts();
        for (int i = 0; i < Math.min(3, amounts.size()); i++) {
            long coins = amounts.get(i);
            BuyQuote quote = stock.buyQuote(viewer.getUniqueId(), coins);
            List<Component> lore = quote.shares() >= 1
                    ? msg.getList("gui.stock.buy-lore", "shares", quote.shares(), "fee", quote.fee(),
                            "paid", quote.paid())
                    : msg.getList("gui.stock.buy-lore-too-little", "min", stock.minInvest(viewer.getUniqueId()));
            set(10 + i, ItemUtil.icon(Material.EMERALD, msg.get("gui.stock.buy-name", "coins", coins), lore),
                    (player, click) -> {
                        stock.buy(player, coins);
                        new StockMenu(plugin, player).open(player);
                    });
        }
        set(13, ItemUtil.icon(Material.LIME_DYE, msg.get("gui.stock.buy-custom-name"),
                        msg.getList("gui.stock.buy-custom-lore", "min", stock.minInvest(viewer.getUniqueId()))),
                (player, click) -> stock.startBuyPrompt(player));

        // ---- Verkaufen ----
        StockHolding holding = stock.holding(uuid);
        List<Component> sellAllLore;
        if (holding.shares() >= 1) {
            SellQuote q = stock.sellQuote(viewer.getUniqueId(), holding.shares());
            sellAllLore = msg.getList("gui.stock.sell-all-lore", "shares", holding.shares(),
                    "gross", q.gross(), "fee", q.fee(), "tax", q.tax(), "payout", q.payout());
        } else {
            sellAllLore = msg.getList("gui.stock.sell-all-lore-empty");
        }
        set(15, ItemUtil.icon(Material.REDSTONE, msg.get("gui.stock.sell-all-name"), sellAllLore),
                (player, click) -> {
                    stock.sellAll(player);
                    new StockMenu(plugin, player).open(player);
                });
        set(16, ItemUtil.icon(Material.RED_DYE, msg.get("gui.stock.sell-custom-name"),
                        msg.getList("gui.stock.sell-custom-lore")),
                (player, click) -> stock.startSellPrompt(player));

        // ---- Depot (Mitte unten) ----
        List<Component> depotLore = new ArrayList<>();
        if (holding.shares() >= 1) {
            SellQuote q = stock.sellQuote(viewer.getUniqueId(), holding.shares());
            depotLore.addAll(msg.getList("gui.stock.depot-lore", "shares", holding.shares(),
                    "invested", holding.invested(), "gross", q.gross(), "payout", q.payout()));
            long profit = q.gross() - holding.invested();
            if (profit > 0) {
                depotLore.add(msg.get("gui.stock.depot-profit", "diff", profit));
            } else if (profit < 0) {
                depotLore.add(msg.get("gui.stock.depot-loss", "diff", -profit));
            } else {
                depotLore.add(msg.get("gui.stock.depot-even"));
            }
        } else {
            depotLore.addAll(msg.getList("gui.stock.depot-empty"));
        }
        set(22, ItemUtil.icon(Material.CHEST, msg.get("gui.stock.depot-name"), depotLore), null);

        // ---- Untere Reihe ----
        set(29, balanceIcon(viewer), incomeClick());
        set(31, backButton(), (player, click) -> new InvestHubMenu(plugin, player).open(player));

        fillEmpty();
    }

    /** "10 → 12 → 8" (älteste zuerst). */
    private static String historyText(StockManager stock) {
        List<Long> history = new ArrayList<>(stock.history(stock.historyDays()));
        Collections.reverse(history);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) {
                sb.append(" → ");
            }
            sb.append(history.get(i));
        }
        return sb.toString();
    }
}
