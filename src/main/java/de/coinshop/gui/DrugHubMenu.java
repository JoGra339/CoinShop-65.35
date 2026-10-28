package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.DrugTrade;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Drogenhandel (nur im Schwarzmarkt): Auswahl des Reviers. Nur freigekaufte Reviere sind Standorte. */
public final class DrugHubMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public DrugHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Drogenhandel - Reviere"));
        DrugTrade drugs = plugin.drugs();
        UUID uuid = viewer.getUniqueId();
        long stockAll = 0;
        for (int r = 0; r < DrugTrade.SITES; r++) {
            int site = r;
            if (!drugs.siteOpen(uuid, r)) {
                set(SLOTS[r], ItemUtil.icon(Material.GRAY_DYE, line(drugs.siteName(r), NamedTextColor.DARK_GRAY), List.of(
                        line("Dieses Revier hast du noch nicht freigekauft.", NamedTextColor.RED),
                        line("Reviere kaufst du im Revier-Menü des Schwarzmarkts.", NamedTextColor.GRAY),
                        line("Kunden dort: " + drugs.customersOf(r).size(), NamedTextColor.DARK_GRAY))), null);
                continue;
            }
            stockAll += drugs.stockTotal(uuid, r);
            double heat = drugs.heat(uuid, r);
            int done = drugs.finishedBatches(uuid, r);
            set(SLOTS[r], ItemUtil.icon(drugs.siteIcon(r), line(drugs.siteName(r), NamedTextColor.GOLD), List.of(
                    line("Labor-Stufe " + drugs.lab(uuid, r) + ", Dealer " + drugs.dealers(uuid, r), NamedTextColor.GRAY),
                    line("Lager: " + drugs.stockTotal(uuid, r) + " Einheiten", NamedTextColor.YELLOW),
                    line("Hitze: " + Math.round(heat) + " (" + drugs.heatState(heat) + ")", heat < 60 ? NamedTextColor.GREEN : NamedTextColor.RED),
                    line(done > 0 ? done + " Charge(n) fertig!" : "Nichts fertig.", done > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY),
                    line("Klicken: Standort öffnen", NamedTextColor.GREEN))),
                    (p, c) -> new DrugSiteMenu(plugin, p, site).open(p));
        }
        set(22, ItemUtil.icon(Material.BOOK, line("Gesamtübersicht", NamedTextColor.AQUA), List.of(
                line("Einnahmen bisher: " + drugs.total(uuid) + " Coins, Abgaben: " + drugs.feesPaid(uuid), NamedTextColor.GOLD),
                line("Gesamtes Lager: " + stockAll + " Einheiten", NamedTextColor.YELLOW))), null);
        set(27, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        if (!plugin.licenses().has(player.getUniqueId(), "drogenhandel")) {
            plugin.messages().send(player, "drug-license");
            return;
        }
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
