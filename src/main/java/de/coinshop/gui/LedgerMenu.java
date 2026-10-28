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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Die Buchhaltung: wann hast du was eingenommen oder ausgegeben? Mit Filter, Seiten und Zusammenfassung. */
public final class LedgerMenu extends Menu {

    private static final int PER_PAGE = 45;

    /** mode: 0 alles, 1 nur Einnahmen, 2 nur Ausgaben. */
    public LedgerMenu(CoinShopPlugin plugin, Player viewer, int mode, int page) {
        super(plugin, 6, plugin.messages().get("gui.ledger.title"));
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = viewer.getUniqueId();
        int count = db.txCount(uuid, mode);
        int pages = Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        SimpleDateFormat format = new SimpleDateFormat("dd.MM. HH:mm");
        format.setTimeZone(java.util.TimeZone.getTimeZone(plugin.serverShop().zone()));
        List<TxEntry> entries = db.txPage(uuid, mode, current * PER_PAGE, PER_PAGE);
        for (int i = 0; i < entries.size(); i++) {
            TxEntry e = entries.get(i);
            boolean income = e.amount() > 0;
            List<Component> lore = new ArrayList<>();
            lore.add(msg.get(income ? "gui.ledger.income-line" : "gui.ledger.expense-line",
                    "coins", Math.abs(e.amount())));
            if (e.detail() != null && !e.detail().isBlank()) {
                String detail = e.detail().length() > 70 ? e.detail().substring(0, 67) + "..." : e.detail();
                lore.add(msg.get("gui.ledger.detail-line", "detail", detail));
            }
            set(i, ItemUtil.icon(income ? Material.EMERALD : Material.REDSTONE,
                    msg.get("gui.ledger.entry-name", "time", format.format(new Date(e.at())), "type", Ledger.label(e.type())),
                    lore), null);
        }
        if (entries.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, msg.get("gui.ledger.empty-name"), msg.getList("gui.ledger.empty-lore")), null);
        }

        // Filter
        set(46, ItemUtil.icon(mode == 0 ? Material.LIME_DYE : Material.GRAY_DYE, msg.get("gui.ledger.filter-all"), List.of()),
                (p, c) -> new LedgerMenu(plugin, p, 0, 0).open(p));
        set(47, ItemUtil.icon(mode == 1 ? Material.LIME_DYE : Material.GRAY_DYE, msg.get("gui.ledger.filter-income"), List.of()),
                (p, c) -> new LedgerMenu(plugin, p, 1, 0).open(p));
        set(48, ItemUtil.icon(mode == 2 ? Material.LIME_DYE : Material.GRAY_DYE, msg.get("gui.ledger.filter-expense"), List.of()),
                (p, c) -> new LedgerMenu(plugin, p, 2, 0).open(p));

        // Zusammenfassung: heute (seit Shop-Wechsel), 7 Tage, ganzer Zeitraum
        long now = System.currentTimeMillis();
        long sinceReset = now - plugin.serverShop().timeSinceReset().toMillis();
        long[] today = db.txSums(uuid, sinceReset);
        long[] week = db.txSums(uuid, now - 7L * 24 * 3_600_000L);
        long[] all = db.txSums(uuid, 0);
        set(49, ItemUtil.icon(Material.BOOK, msg.get("gui.ledger.summary-name"), msg.getList("gui.ledger.summary-lore",
                "in1", today[0], "out1", today[1], "in7", week[0], "out7", week[1], "inall", all[0], "outall", all[1])), null);

        // Woher kommt das Geld? (7 Tage)
        List<Component> sources = new ArrayList<>(msg.getList("gui.ledger.sources-lore"));
        for (TxEntry e : db.txIncomeByType(uuid, now - 7L * 24 * 3_600_000L, 6)) {
            sources.add(msg.get("gui.ledger.sources-line", "type", Ledger.label(e.type()), "coins", e.amount()));
        }
        set(50, ItemUtil.icon(Material.GOLD_NUGGET, msg.get("gui.ledger.sources-name"), sources),
                (p, c) -> new WeekMenu(plugin, p).open(p));

        final int m = mode;
        set(45, current > 0 ? ItemUtil.icon(Material.ARROW, msg.get("gui.ledger.prev"), List.of())
                : ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE, msg.get("gui.ledger.prev"), List.of()),
                current > 0 ? (p, c) -> new LedgerMenu(plugin, p, m, current - 1).open(p) : null);
        set(53, current < pages - 1 ? ItemUtil.icon(Material.ARROW, msg.get("gui.ledger.next", "page", current + 2, "pages", pages), List.of())
                : ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE, msg.get("gui.ledger.next", "page", current + 1, "pages", pages), List.of()),
                current < pages - 1 ? (p, c) -> new LedgerMenu(plugin, p, m, current + 1).open(p) : null);
        set(51, backButton(), (p, c) -> new MyIncomeMenu(plugin, p).open(p));
        set(52, balanceIcon(viewer), null);
        fillEmpty();
    }
}
