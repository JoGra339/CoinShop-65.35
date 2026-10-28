package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.CryptoManager.BuyQuote;
import de.coinshop.CryptoManager.SellQuote;
import de.coinshop.Database.CryptoHolding;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Die Krypto-Börse (kind = "krypto") und die Hinterhof-Börse für Habibi-Krypto (kind = "dunkel"). */
public final class CryptoMenu extends Menu {

    public CryptoMenu(CoinShopPlugin plugin, Player viewer, String kind) {
        super(plugin, 4, plugin.messages().get(kind.equals(CryptoManager.DUNKEL)
                ? "gui.crypto.title-dark" : "gui.crypto.title"));
        Messages msg = plugin.messages();
        CryptoManager crypto = plugin.crypto();
        UUID uuid = viewer.getUniqueId();
        boolean dark = kind.equals(CryptoManager.DUNKEL);
        String name = crypto.nameOf(kind);

        long price = crypto.price(kind);
        long previous = crypto.previousPrice(kind);
        long diff = price - previous;
        String percent = previous > 0
                ? String.format(Locale.GERMANY, "%.0f", Math.abs(diff) * 100.0 / previous) : "0";
        List<Component> priceLore = new ArrayList<>();
        if (diff > 0) {
            priceLore.add(msg.get("gui.crypto.change-up", "diff", diff, "percent", percent));
        } else if (diff < 0) {
            priceLore.add(msg.get("gui.crypto.change-down", "diff", -diff, "percent", percent));
        } else {
            priceLore.add(msg.get("gui.crypto.change-same"));
        }
        List<Long> hist = new ArrayList<>(crypto.history(kind, 7));
        Collections.reverse(hist);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hist.size(); i++) {
            sb.append(i > 0 ? " → " : "").append(hist.get(i));
        }
        priceLore.add(msg.get("gui.crypto.history-line", "history", sb.toString()));
        priceLore.addAll(msg.getList(dark ? "gui.crypto.price-foot-dark" : "gui.crypto.price-foot",
                "min", crypto.minPrice(), "max", crypto.maxPrice(kind), "hour", plugin.serverShop().resetHour(),
                "percent", String.format(Locale.GERMANY, "%.0f", crypto.feePercent(uuid, kind))));
        String events = plugin.events().activeText(viewer);
        if (!events.isEmpty()) {
            priceLore.add(msg.get("gui.crypto.events-line", "events", events));
        }
        set(4, ItemUtil.icon(dark ? Material.NETHER_STAR : Material.AMETHYST_SHARD,
                msg.get("gui.crypto.price-name", "name", name, "price", price), priceLore), null);

        List<Long> amounts = new ArrayList<>();
        for (long a : plugin.getConfig().getLongList("crypto.quick-amounts")) {
            amounts.add(a);
        }
        if (amounts.isEmpty()) {
            amounts.addAll(List.of(500L, 2000L, 10000L));
        }
        for (int i = 0; i < Math.min(3, amounts.size()); i++) {
            long coins = amounts.get(i);
            BuyQuote q = crypto.buyQuote(uuid, kind, coins);
            List<Component> lore = q.units() >= 1
                    ? msg.getList("gui.crypto.buy-lore", "name", name, "units", CryptoManager.fmt(q.units()),
                            "fee", q.fee(), "paid", q.paid())
                    : msg.getList("gui.crypto.buy-lore-too-little", "min", crypto.minInvest(uuid, kind));
            set(10 + i, ItemUtil.icon(Material.EMERALD, msg.get("gui.crypto.buy-name", "coins", coins), lore),
                    (player, click) -> {
                        crypto.buy(player, kind, coins);
                        new CryptoMenu(plugin, player, kind).open(player);
                    });
        }
        set(13, ItemUtil.icon(Material.LIME_DYE, msg.get("gui.crypto.buy-custom-name"),
                        msg.getList("gui.crypto.buy-custom-lore", "min", crypto.minInvest(uuid, kind))),
                (player, click) -> crypto.startBuyPrompt(player, kind));

        CryptoHolding h = crypto.holding(uuid, kind);
        List<Component> allLore;
        if (h.units() >= 1) {
            SellQuote q = crypto.sellQuote(uuid, kind, h.units());
            allLore = msg.getList("gui.crypto.sell-all-lore", "name", name, "units", CryptoManager.fmt(h.units()),
                    "gross", q.gross(), "fee", q.fee(), "tax", q.tax(), "payout", q.payout());
        } else {
            allLore = msg.getList("gui.crypto.sell-all-lore-empty", "name", name);
        }
        set(15, ItemUtil.icon(Material.REDSTONE, msg.get("gui.crypto.sell-all-name"), allLore),
                (player, click) -> {
                    crypto.sellAll(player, kind);
                    new CryptoMenu(plugin, player, kind).open(player);
                });
        set(16, ItemUtil.icon(Material.RED_DYE, msg.get("gui.crypto.sell-custom-name"),
                        msg.getList("gui.crypto.sell-custom-lore", "name", name)),
                (player, click) -> crypto.startSellPrompt(player, kind));

        List<Component> depot = new ArrayList<>();
        if (h.units() >= 1) {
            SellQuote q = crypto.sellQuote(uuid, kind, h.units());
            depot.addAll(msg.getList("gui.crypto.depot-lore", "name", name, "units", CryptoManager.fmt(h.units()),
                    "invested", h.invested(), "gross", q.gross(), "payout", q.payout()));
            long profit = q.gross() - h.invested();
            if (profit > 0) {
                depot.add(msg.get("gui.crypto.depot-profit", "diff", profit));
            } else if (profit < 0) {
                depot.add(msg.get("gui.crypto.depot-loss", "diff", -profit));
            }
        } else {
            depot.addAll(msg.getList("gui.crypto.depot-empty", "name", name));
        }
        set(22, ItemUtil.icon(Material.CHEST, msg.get("gui.crypto.depot-name", "name", name), depot), null);

        set(29, balanceIcon(viewer), incomeClick());
        set(31, backButton(), (player, click) -> {
            if (dark) {
                new BackyardMenu(plugin, player).open(player);
            } else {
                new CryptoHubMenu(plugin, player).open(player);
            }
        });
        fillEmpty();
    }
}
