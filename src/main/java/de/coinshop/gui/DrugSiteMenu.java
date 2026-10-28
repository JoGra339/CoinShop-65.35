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

/** Ein Standort des Drogenhandels (ein Revier): Labor, Lager, Stammkunden, Dealer und Hitze dieses Reviers. */
public final class DrugSiteMenu extends Menu {

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public DrugSiteMenu(CoinShopPlugin plugin, Player viewer, int revier) {
        super(plugin, 4, Component.text("Drogenhandel - " + plugin.drugs().siteName(revier)));
        DrugTrade drugs = plugin.drugs();
        UUID uuid = viewer.getUniqueId();
        int r = revier;
        drugs.settleDealers(viewer, r, false);

        set(10, ItemUtil.icon(Material.BREWING_STAND, line("Labor und Herstellung", NamedTextColor.GOLD), List.of(
                line("Stelle Waren in Chargen her.", NamedTextColor.GRAY),
                line("Labor-Stufe " + drugs.lab(uuid, r) + " von 5, " + drugs.batches(uuid, r).size() + " von " + drugs.labSlots(uuid, r) + " Plätzen belegt", NamedTextColor.YELLOW),
                line(drugs.finishedBatches(uuid, r) > 0 ? drugs.finishedBatches(uuid, r) + " Charge(n) fertig!" : "Nichts fertig.",
                        drugs.finishedBatches(uuid, r) > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))),
                (p, c) -> new DrugProductionMenu(plugin, p, r).open(p));

        set(12, ItemUtil.icon(Material.BARREL, line("Lager und Mischen", NamedTextColor.GOLD), List.of(
                line("Die Waren dieses Reviers nach Qualität.", NamedTextColor.GRAY),
                line("Mischen verbessert die Qualität - und den Preis.", NamedTextColor.GRAY),
                line("Im Lager: " + drugs.stockTotal(uuid, r) + " Einheiten", NamedTextColor.YELLOW))),
                (p, c) -> new DrugStockMenu(plugin, p, r, 0).open(p));

        int unlocked = 0;
        List<Integer> here = drugs.customersOf(r);
        for (int i : here) {
            if (drugs.customerUnlocked(uuid, i)) {
                unlocked++;
            }
        }
        set(14, ItemUtil.icon(Material.VILLAGER_SPAWN_EGG, line("Stammkunden", NamedTextColor.GOLD), List.of(
                line("Die Kunden, die in diesem Revier wohnen.", NamedTextColor.GRAY),
                line("Je mehr du verkaufst, desto treuer und zahlungskräftiger.", NamedTextColor.GRAY),
                line("Kontakte: " + unlocked + " von " + here.size(), NamedTextColor.YELLOW))),
                (p, c) -> new DrugCustomerMenu(plugin, p, r).open(p));

        set(16, ItemUtil.icon(Material.LEATHER_CHESTPLATE, line("Dealer", NamedTextColor.GOLD), List.of(
                line("Angestellte verkaufen auch, wenn du fort bist.", NamedTextColor.GRAY),
                line("Sie behalten einen Anteil.", NamedTextColor.GRAY),
                line("Dealer: " + drugs.dealers(uuid, r) + " von " + drugs.dealerMax(), NamedTextColor.YELLOW))),
                (p, c) -> new DrugDealerMenu(plugin, p, r).open(p));

        double heat = drugs.heat(uuid, r);
        NamedTextColor hc = heat < 30 ? NamedTextColor.GREEN : heat < 60 ? NamedTextColor.YELLOW : heat < 85 ? NamedTextColor.GOLD : NamedTextColor.RED;
        set(22, ItemUtil.icon(Material.CAMPFIRE, line("Hitze hier: " + Math.round(heat) + " von 100 (" + drugs.heatState(heat) + ")", hc), List.of(
                line("Jeder Verkauf erhöht die Hitze, sie sinkt mit der Zeit.", NamedTextColor.GRAY),
                line("Über 50 droht eine Razzia: Ware weg, Strafe, evtl. ein Dealer.", NamedTextColor.GRAY),
                line("Razzien hier bisher: " + drugs.raids(uuid, r), NamedTextColor.DARK_GRAY),
                line("Klicken: Schmiergeld zahlen (" + drugs.bribeCost(uuid, r) + " Coins)", NamedTextColor.GREEN))),
                (p, c) -> {
                    drugs.bribe(p, r);
                    new DrugSiteMenu(plugin, p, r).open(p);
                });

        set(27, backButton(), (p, c) -> new DrugHubMenu(plugin, p).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
