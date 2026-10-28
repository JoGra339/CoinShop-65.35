package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Account;
import de.coinshop.InvestmentManager;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Einkommens-Übersicht: wer verdient wie viel pro Tag - und wer ist der reichste Spieler? */
public final class IncomeMenu extends Menu {

    private record Row(Account account, long base, long login, long sets, long rank) {
        long total() {
            return base + login + sets + rank;
        }
    }

    public IncomeMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.income.title"));
        Messages msg = plugin.messages();
        InvestmentManager invest = plugin.investments();
        var owners = invest.ownershipMap();
        Map<UUID, Long> rankBonus = plugin.rankings().currentBonuses();

        List<Row> rows = new ArrayList<>();
        Account richest = null;
        for (Account account : plugin.database().allAccounts()) {
            rows.add(new Row(account, invest.baseIncome(account.uuid()), plugin.logins().bonusOf(account.uuid()),
                    invest.setIncomeOf(account.uuid(), owners),
                    rankBonus.getOrDefault(account.uuid(), 0L)));
            if (richest == null || account.coins() > richest.coins()) {
                richest = account;
            }
        }
        rows.sort(Comparator.comparingLong(Row::total).reversed()
                .thenComparing(Comparator.comparingLong((Row r) -> r.account().coins()).reversed()));

        for (int i = 0; i < rows.size() && i < 45; i++) {
            Row row = rows.get(i);
            List<Component> lore = msg.getList("gui.income.entry-lore",
                    "total", row.total(), "base", row.base(), "login", row.login(), "sets", row.sets(),
                    "rank", row.rank(), "coins", row.account().coins());
            ItemStack head = ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.income.entry-name",
                    "place", i + 1, "name", row.account().name()), lore);
            try {
                head.editMeta(SkullMeta.class, meta ->
                        meta.setOwningPlayer(Bukkit.getOfflinePlayer(row.account().uuid())));
            } catch (RuntimeException ignored) {
                // dann eben ein Standard-Kopf
            }
            set(i, head, (player, click) -> new ProfileMenu(plugin, player, row.account().uuid()).open(player));
        }

        // Unten: Zurück | dein Einkommen | Reichster Spieler | Kontostand
        set(46, backButton(), (player, click) -> new InvestHubMenu(plugin, player).open(player));
        for (Row row : rows) {
            if (row.account().uuid().equals(viewer.getUniqueId())) {
                set(48, ItemUtil.icon(Material.CLOCK, msg.get("gui.income.own-name"),
                        msg.getList("gui.income.own-lore", "total", row.total(), "base", row.base(),
                                "login", row.login(), "sets", row.sets(), "rank", row.rank(),
                                "hour", plugin.serverShop().resetHour())), null);
            }
        }
        if (richest != null) {
            set(49, ItemUtil.icon(Material.GOLD_BLOCK, msg.get("gui.income.richest-name"),
                    msg.getList("gui.income.richest-lore", "name", richest.name(), "coins", richest.coins())), null);
        }
        set(50, balanceIcon(viewer), incomeClick());

        fillEmpty();
    }
}
