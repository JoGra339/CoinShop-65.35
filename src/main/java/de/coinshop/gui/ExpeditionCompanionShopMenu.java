package de.coinshop.gui;

import de.coinshop.CompanionAspects;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Der Begleiter-Laden: jede Kreatur des Spiels außer dem Enderdrachen, nach Preis sortiert, auf mehreren Seiten. */
public final class ExpeditionCompanionShopMenu extends Menu {

    private static final int PAGE = 45;

    private static final String[] ROLES = {"Alle Rollen", "Angreifer", "Verteidiger", "Unterstützer"};

    public ExpeditionCompanionShopMenu(CoinShopPlugin plugin, Player viewer, int page) {
        this(plugin, viewer, page, 0);
    }

    /** role: 0 = alle, 1 = Angreifer, 2 = Verteidiger, 3 = Unterstützer. */
    public ExpeditionCompanionShopMenu(CoinShopPlugin plugin, Player viewer, int page, int role) {
        super(plugin, 6, Component.text("Begleiter kaufen"));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();
        List<Companion> all = new ArrayList<>(Arrays.asList(Companion.values()));
        if (role > 0) {
            all.removeIf(c -> ex.roleIndex(c) != role - 1);
        }
        all.sort(Comparator.comparingLong((Companion c) -> c.defaultPrice).thenComparing(c -> c.label));
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        for (int i = 0; i < PAGE && pg * PAGE + i < all.size(); i++) {
            Companion c = all.get(pg * PAGE + i);
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line(ex.isSupporter(c) && c.text.contains("kämpft selbstständig")
                    ? c.label + ": kämpft nicht, heilt dich laufend." : c.text, NamedTextColor.GRAY));
            lore.addAll(CompanionInfo.species(plugin, c));
            lore.add(ExpeditionMenu.line("Gefolge-Kosten: " + c.stars + " Sterne", NamedTextColor.GRAY));
            lore.add(ExpeditionMenu.line("Im Level: " + CompanionAspects.levelText(c), NamedTextColor.DARK_GREEN));
            lore.add(ExpeditionMenu.line("Auf Reisen: " + CompanionAspects.tripText(c), NamedTextColor.AQUA));
            lore.add(ExpeditionMenu.line("Du hast: " + ex.existingCopies(uuid, c).size() + " (gekauft: " + ex.companionPurchased(uuid, c) + ")", NamedTextColor.YELLOW));
            lore.add(ExpeditionMenu.line("Nächstes Exemplar: " + ex.companionPrice(uuid, c) + " Coins", NamedTextColor.GOLD));
            lore.add(ExpeditionMenu.line("Jedes Exemplar hat zufällig Seltenheit und Eigenschaften.", NamedTextColor.DARK_GRAY));
            if (ex.needsTribute(c)) {
                lore.add(ExpeditionMenu.line("Opfer: " + ex.tributesNeeded(c) + " Exemplar(e) schwächerer Arten werden geopfert.", NamedTextColor.RED));
            }
            lore.add(ExpeditionMenu.line(ex.needsTribute(c) ? "Klicken: Opfer wählen und kaufen" : "Klicken zum Kaufen", NamedTextColor.GREEN));
            set(i, ItemUtil.icon(c.icon, ExpeditionMenu.line(c.label, NamedTextColor.GOLD), lore), (p, click) -> {
                if (ex.needsTribute(c)) {
                    ExpeditionCopyMenu.forTribute(plugin, p, c).open(p);
                } else {
                    ex.buyCompanion(p, c, java.util.List.<de.coinshop.ExpeditionManager.Copy>of());
                    new ExpeditionCompanionShopMenu(plugin, p, pg, role).open(p);
                }
            });
        }
        set(45, backButton(), (p, c) -> new ExpeditionCompanionHubMenu(plugin, p).open(p));
        if (pg > 0) {
            set(48, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Vorherige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new ExpeditionCompanionShopMenu(plugin, p, pg - 1, role).open(p));
        }
        set(49, ItemUtil.icon(Material.PAPER, ExpeditionMenu.line("Seite " + (pg + 1) + " von " + pages, NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line(all.size() + " Kreaturen, nach Preis sortiert.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Jedes weitere Exemplar einer Art kostet das Doppelte.", NamedTextColor.GRAY))), null);
        if (pg < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Nächste Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new ExpeditionCompanionShopMenu(plugin, p, pg + 1, role).open(p));
        }
        set(47, ItemUtil.icon(Material.COMPARATOR, ExpeditionMenu.line("Filter: " + ROLES[role], NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Angreifer kämpfen, Verteidiger sind robust und", NamedTextColor.GRAY),
                ExpeditionMenu.line("lenken Gegner auf sich, Unterstützer heilen.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: nächste Rolle", NamedTextColor.GREEN))),
                (p, c) -> new ExpeditionCompanionShopMenu(plugin, p, 0, (role + 1) % ROLES.length).open(p));
        set(51, ItemUtil.icon(Material.LEAD, ExpeditionMenu.line("Begleiter-Platz freikaufen", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Freigekauft: " + ex.slotsUnlocked(uuid) + " (unbegrenzt kaufbar)", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Nächster Platz: " + ex.slotPrice(uuid) + " Coins", NamedTextColor.GOLD),
                ExpeditionMenu.line("Klicken zum Freikaufen", NamedTextColor.GREEN))), (p, c) -> {
            ex.buySlot(p);
            new ExpeditionCompanionShopMenu(plugin, p, pg, role).open(p);
        });
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
