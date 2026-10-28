package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.Database.Ownership;
import de.coinshop.InvestmentManager;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Das Zwischenmenü der Investitionen. Von hier aus geht es weiter zu den drei Bereichen:
 *  - Item-Sets (Items kaufen, die täglich Einkommen bringen)
 *  - Börse (Aktien kaufen und verkaufen)
 *  - Einkommen (Übersicht, wer wie viel verdient)
 */
public final class InvestHubMenu extends Menu {

    public InvestHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.invest.hub-title"));
        Messages msg = plugin.messages();
        InvestmentManager invest = plugin.investments();
        UUID uuid = viewer.getUniqueId();
        Map<String, Ownership> owners = invest.ownershipMap();

        // ---- Werte für die Beschriftungen ----
        int ownedItems = 0;
        int totalItems = 0;
        for (SetDef set : invest.sets()) {
            totalItems += set.items().size();
            ownedItems += invest.ownedCount(set, uuid, owners);
        }
        long setsIncome = invest.setIncomeOf(uuid, owners);
        long rankIncome = plugin.rankings().currentBonuses().getOrDefault(uuid, 0L);
        long loginIncome = plugin.logins().bonusOf(uuid);
        long totalIncome = invest.baseIncome(uuid) + loginIncome + setsIncome + rankIncome;
        long shares = plugin.stock().holding(uuid).shares();

        // ---- Die drei Bereiche (Mitte) ----
        set(10, ItemUtil.icon(Material.GOLD_BLOCK, msg.get("gui.invest.hub-sets-name"),
                        msg.getList("gui.invest.hub-sets-lore",
                                "owned", ownedItems, "total", totalItems, "income", setsIncome)),
                (player, click) -> new InvestMenu(plugin, player).open(player));

        set(12, ItemUtil.icon(Material.DIAMOND, msg.get("gui.invest.hub-stock-name"),
                        msg.getList("gui.invest.hub-stock-lore",
                                "price", plugin.stock().price(), "shares", shares)),
                (player, click) -> new StockMenu(plugin, player).open(player));

        set(14, ItemUtil.icon(Material.AMETHYST_SHARD, msg.get("gui.invest.hub-crypto-name"),
                        msg.getList("gui.invest.hub-crypto-lore", "price", plugin.crypto().price(CryptoManager.KRYPTO),
                                "units", CryptoManager.fmt(plugin.crypto().holding(uuid, CryptoManager.KRYPTO).units()),
                                "events", plugin.events().activeText(viewer).isEmpty() ? "keins" : plugin.events().activeText(viewer))),
                (player, click) -> new CryptoHubMenu(plugin, player).open(player));

        set(16, ItemUtil.icon(Material.CLOCK, msg.get("gui.invest.hub-income-name"),
                        msg.getList("gui.invest.hub-income-lore",
                                "base", invest.baseIncome(uuid), "login", loginIncome, "sets", setsIncome, "rank", rankIncome,
                                "total", totalIncome, "hour", plugin.serverShop().resetHour())),
                (player, click) -> new IncomeMenu(plugin, player).open(player));

        // ---- Untere Reihe: Kontostand | Zurück ----
        set(29, balanceIcon(viewer), incomeClick());
        set(31, backButton(), (player, click) -> new MainMenu(plugin).open(player));

        fillEmpty();
    }
}
