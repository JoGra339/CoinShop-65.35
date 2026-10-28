package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Copy;
import de.coinshop.ItemUtil;
import de.coinshop.MercenaryManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Söldner-Markt: fremde Begleiter mieten, eigene anbieten. */
public final class MercMenu extends Menu {

    private static final int PER_PAGE = 36;

    public MercMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, Component.text("Söldner-Markt"));
        ExpeditionManager ex = plugin.expeditions();
        MercenaryManager mm = ex.mercs();
        UUID uuid = viewer.getUniqueId();
        List<MercenaryManager.Offer> open = mm.open();
        int pages = Math.max(1, (open.size() + PER_PAGE - 1) / PER_PAGE);
        int cur = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = cur * PER_PAGE + i;
            if (idx >= open.size()) {
                break;
            }
            MercenaryManager.Offer o = open.get(idx);
            Copy k = o.copy();
            if (k == null) {
                continue;
            }
            boolean mine = o.owner.equals(uuid);
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line("Besitzer: " + o.ownerName, NamedTextColor.GRAY));
            lore.addAll(CompanionInfo.lines(plugin, o.owner, k));
            lore.add(ExpeditionMenu.line("Miete: " + o.price + " Coins bis zum nächsten Shopwechsel", NamedTextColor.GOLD));
            lore.add(ExpeditionMenu.line(mine ? "Dein Angebot (Klicken: zurücknehmen)" : "Klicken: mieten", mine ? NamedTextColor.RED : NamedTextColor.GREEN));
            set(i, ItemUtil.icon(k.companion().icon, ExpeditionMenu.line(ex.trips().copyName(k.companion(), k.index()), NamedTextColor.GOLD), lore), (p, c) -> {
                if (mine) {
                    mm.withdraw(p, o.id);
                } else {
                    mm.rent(p, o.id);
                }
                new MercMenu(plugin, p, cur).open(p);
            });
        }
        if (open.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Zurzeit keine Söldner im Angebot", NamedTextColor.RED), List.of()), null);
        }
        List<MercenaryManager.Offer> rented = mm.rentedBy(uuid);
        List<Component> myLore = new ArrayList<>();
        myLore.add(ExpeditionMenu.line("Von dir gemietet: " + rented.size() + ", sie kommen im nächsten Level mit.", NamedTextColor.GRAY));
        for (MercenaryManager.Offer o : rented) {
            Copy k = o.copy();
            if (k != null) {
                myLore.add(ExpeditionMenu.line("- " + ex.trips().copyName(k.companion(), k.index()) + " von " + o.ownerName, NamedTextColor.YELLOW));
            }
        }
        set(46, ItemUtil.icon(Material.WRITTEN_BOOK, ExpeditionMenu.line("Meine gemieteten Söldner", NamedTextColor.GOLD), myLore), null);
        set(49, ItemUtil.icon(Material.EMERALD, ExpeditionMenu.line("Eigenen Begleiter anbieten", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Wähle einen Begleiter und deinen Preis.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Du bekommst die Miete (minus Anteil für den Shop)", NamedTextColor.GRAY),
                ExpeditionMenu.line("und Erfahrung für jedes geschaffte Level.", NamedTextColor.GRAY))),
                (p, c) -> new MercOfferMenu(plugin, p, 0, null).open(p));
        if (cur > 0) {
            set(48, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Vorige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new MercMenu(plugin, p, cur - 1).open(p));
        }
        if (cur < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Nächste Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new MercMenu(plugin, p, cur + 1).open(p));
        }
        set(45, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
