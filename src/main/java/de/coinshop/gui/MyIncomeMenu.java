package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.InvestmentManager;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RankingManager.Placed;
import de.coinshop.RankingManager.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Deine persönliche Einkommens-Aufstellung (Klick auf den Kontostand):
 * Grundeinkommen, Login-Serie, jedes Set mit Einkommen und jede Rangliste mit Bonus - und die Summe.
 */
public final class MyIncomeMenu extends Menu {

    public MyIncomeMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.myincome.title"));
        Messages msg = plugin.messages();
        InvestmentManager invest = plugin.investments();
        UUID uuid = viewer.getUniqueId();
        var owners = invest.ownershipMap();

        int slot = 0;
        long total = 0;

        // 1. Grundeinkommen
        long base = invest.baseIncome();
        total += base;
        set(slot++, ItemUtil.icon(Material.GOLD_NUGGET, msg.get("gui.myincome.base-name"),
                msg.getList("gui.myincome.base-lore", "coins", base)), null);

        // 1b. Rente aus der Lizenz
        long rente = plugin.licenses().dailyIncome(uuid);
        if (rente > 0) {
            total += rente;
            set(slot++, ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.myincome.rente-name"),
                    msg.getList("gui.myincome.rente-lore", "coins", rente)), null);
        }

        // 2. Login-Serie
        long login = plugin.logins().bonusOf(uuid);
        total += login;
        set(slot++, ItemUtil.icon(Material.SUNFLOWER, msg.get("gui.myincome.login-name"),
                msg.getList("gui.myincome.login-lore", "coins", login,
                        "days", plugin.logins().currentStreak(uuid))), null);

        // 3. Jedes Set, das etwas einbringt
        long setSum = 0;
        for (SetDef set : invest.sets()) {
            int owned = invest.ownedCount(set, uuid, owners);
            long income = invest.setIncome(set, owned);
            if (owned <= 0 || income <= 0 || slot >= 45) {
                continue;
            }
            total += income;
            setSum += income;
            Material icon = set.items().isEmpty() ? Material.GOLD_BLOCK : set.items().get(0);
            set(slot++, ItemUtil.icon(icon, msg.get("gui.myincome.set-name", "name", set.name()),
                    msg.getList("gui.myincome.set-lore", "owned", owned, "total", set.items().size(),
                            "coins", income)), null);
        }

        long investorBonus = plugin.licenses().boost(uuid, "investor", setSum) - setSum;
        if (investorBonus > 0 && slot < 45) {
            total += investorBonus;
            set(slot++, ItemUtil.icon(Material.GOLD_BLOCK, msg.get("gui.myincome.investor-name"),
                    msg.getList("gui.myincome.investor-lore", "coins", investorBonus)), null);
        }

        // 4. Jede Rangliste, in der du einen Bonus bekommst
        for (Rank rank : Rank.values()) {
            Placed info = plugin.rankings().info(rank, uuid);
            if (info == null || info.bonus() <= 0 || slot >= 45) {
                continue;
            }
            total += info.bonus();
            Component name = msg.get("gui.rank.cat." + rank.id() + ".name");
            set(slot++, ItemUtil.icon(rank.icon(), msg.get("gui.myincome.rank-name", "name", name),
                    msg.getList("gui.myincome.rank-lore", "place", info.place(), "coins", info.bonus())), null);
        }

        long rankSum = 0;
        for (Rank rank : Rank.values()) {
            Placed info = plugin.rankings().info(rank, uuid);
            if (info != null && info.bonus() > 0) {
                rankSum += info.bonus();
            }
        }
        long rankBonus = plugin.licenses().boost(uuid, "ranglisten", rankSum) - rankSum;
        if (rankBonus > 0 && slot < 45) {
            total += rankBonus;
            set(slot++, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.myincome.ranglisten-name"),
                    msg.getList("gui.myincome.ranglisten-lore", "coins", rankBonus)), null);
        }

        // Unten: Zurück | Summe | Kontostand
        set(47, ItemUtil.icon(Material.MAP, msg.get("gui.main.ledger-name"), msg.getList("gui.main.ledger-lore")),
                (player, click) -> new LedgerMenu(plugin, player, 0, 0).open(player));
        set(48, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        set(49, ItemUtil.icon(Material.EMERALD, msg.get("gui.myincome.total-name"),
                msg.getList("gui.myincome.total-lore", "coins", total,
                        "hour", plugin.serverShop().resetHour())), null);
        set(50, balanceIcon(viewer), null);
        fillEmpty();
    }
}
