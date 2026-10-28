package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.Database.TxEntry;
import de.coinshop.ItemUtil;
import de.coinshop.Ledger;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Die Wochenübersicht: die letzten 7 Tage (jeweils von Shop-Wechsel zu Shop-Wechsel) und woher dein Geld kommt. */
public final class WeekMenu extends Menu {

    public WeekMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.week.title"));
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = viewer.getUniqueId();
        ZoneId zone = plugin.serverShop().zone();
        long now = System.currentTimeMillis();
        long lastReset = now - plugin.serverShop().timeSinceReset().toMillis();
        long day = Duration.ofHours(24).toMillis();
        long since = lastReset - 6 * day;

        // Tage: Index 0 = heute (seit dem letzten Shop-Wechsel), 6 = vor 6 Tagen
        long[] in = new long[7];
        long[] out = new long[7];
        for (long[] row : db.txSince(uuid, since)) {
            int idx = (int) Math.max(0, Math.min(6, (lastReset - row[0] + day - 1) / day));
            if (row[0] >= lastReset) {
                idx = 0;
            }
            if (row[1] > 0) {
                in[idx] += row[1];
            } else {
                out[idx] -= row[1];
            }
        }
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEEE, dd.MM.", Locale.GERMAN);
        long totalIn = 0;
        long totalOut = 0;
        for (int i = 0; i < 7; i++) {
            int d = 6 - i; // links der älteste Tag, rechts heute
            totalIn += in[d];
            totalOut += out[d];
            ZonedDateTime start = Instant.ofEpochMilli(lastReset - (long) d * day).atZone(zone);
            long net = in[d] - out[d];
            Material icon = (in[d] == 0 && out[d] == 0) ? Material.GRAY_STAINED_GLASS_PANE
                    : net >= 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            set(10 + i, ItemUtil.icon(icon, msg.get(d == 0 ? "gui.week.today-name" : "gui.week.day-name",
                            "day", format.format(start)),
                    msg.getList("gui.week.day-lore", "in", in[d], "out", out[d], "net", (net >= 0 ? "+" : "") + net)), null);
        }
        long netTotal = totalIn - totalOut;
        set(4, ItemUtil.icon(Material.BOOK, msg.get("gui.week.total-name"),
                msg.getList("gui.week.total-lore", "in", totalIn, "out", totalOut,
                        "net", (netTotal >= 0 ? "+" : "") + netTotal)), null);

        // Woher kommt das Geld, wohin geht es?
        set(18, ItemUtil.icon(Material.EMERALD, msg.get("gui.week.income-name"), List.of()), null);
        int slot = 19;
        for (TxEntry e : db.txByType(uuid, since, true, 7)) {
            set(slot++, ItemUtil.icon(Material.LIME_DYE, msg.get("gui.week.type-name", "type", Ledger.label(e.type())),
                    List.of(msg.get("gui.week.income-line", "coins", e.amount()))), null);
        }
        set(27, ItemUtil.icon(Material.REDSTONE, msg.get("gui.week.expense-name"), List.of()), null);
        slot = 28;
        for (TxEntry e : db.txByType(uuid, since, false, 7)) {
            set(slot++, ItemUtil.icon(Material.RED_DYE, msg.get("gui.week.type-name", "type", Ledger.label(e.type())),
                    List.of(msg.get("gui.week.expense-line", "coins", e.amount()))), null);
        }
        set(39, backButton(), (p, c) -> new LedgerMenu(plugin, p, 0, 0).open(p));
        set(41, balanceIcon(viewer), null);
        fillEmpty();
    }
}
