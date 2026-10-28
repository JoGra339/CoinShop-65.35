package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.DrugTrade;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stammkunden: Kontakte freikaufen und verkaufen. Wer treu ist, zahlt mehr und nimmt mehr. */
public final class DrugCustomerMenu extends Menu {

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public DrugCustomerMenu(CoinShopPlugin plugin, Player viewer, int revier) {
        super(plugin, 4, Component.text("Stammkunden - " + plugin.drugs().siteName(revier)));
        DrugTrade drugs = plugin.drugs();
        UUID uuid = viewer.getUniqueId();
        int r = revier;
        int[] slots = {11, 13, 15, 21, 23};
        int shown = 0;
        for (int i : drugs.customersOf(r)) {
            if (shown >= slots.length) {
                break;
            }
            int slot = slots[shown++];
            int customer = i;
            DrugTrade.Customer cu = DrugTrade.CUSTOMERS[i];
            List<Component> lore = new ArrayList<>();
            StringBuilder wants = new StringBuilder();
            for (int p : cu.products()) {
                if (wants.length() > 0) {
                    wants.append(", ");
                }
                wants.append(DrugTrade.PRODUCTS[p]);
            }
            lore.add(line("Kauft: " + wants + " ab Qualität " + DrugTrade.QUALITIES[cu.minQuality()], NamedTextColor.GRAY));
            if (!drugs.customerUnlocked(uuid, i)) {
                lore.add(line("Kontakt knüpfen: " + cu.unlockCost() + " Coins", NamedTextColor.GOLD));
                lore.add(line("Klicken zum Freikaufen", NamedTextColor.GREEN));
                set(slot, ItemUtil.icon(Material.GRAY_DYE, line(cu.name(), NamedTextColor.DARK_GRAY), lore), (p, c) -> {
                    drugs.unlockCustomer(p, customer);
                    new DrugCustomerMenu(plugin, p, r).open(p);
                });
                continue;
            }
            int loyal = drugs.loyalty(uuid, i);
            lore.add(line("Treue: " + loyal + " von 10", NamedTextColor.YELLOW));
            lore.add(line("Zahlt " + Math.round(drugs.priceFactor(uuid, i) * 100) + " % des Wertes", NamedTextColor.GOLD));
            lore.add(line("Revierabgabe: " + drugs.bossCutPercent() + " % Unterhändler, " + drugs.pateCutPercent() + " % Pate", NamedTextColor.DARK_GRAY));
            lore.add(line("Nimmt heute noch: " + drugs.demandLeft(uuid, i) + " von " + drugs.dailyDemand(uuid, i) + " Einheiten", drugs.demandLeft(uuid, i) > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(line("Verkäufe erhöhen die Hitze.", NamedTextColor.DARK_GRAY));
            lore.add(line("Klicken: verkaufen (beste Qualität zuerst)", NamedTextColor.GREEN));
            set(slot, ItemUtil.icon(Material.PLAYER_HEAD, line(cu.name(), NamedTextColor.GOLD), lore), (p, c) -> {
                drugs.sellTo(p, customer);
                new DrugCustomerMenu(plugin, p, r).open(p);
            });
        }
        double heat = drugs.heat(uuid, r);
        set(31, ItemUtil.icon(Material.CAMPFIRE, line("Hitze: " + Math.round(heat) + " (" + drugs.heatState(heat) + ")", NamedTextColor.GOLD), List.of(
                line("Im Lager: " + drugs.stockTotal(uuid, r) + " Einheiten", NamedTextColor.YELLOW))), null);
        set(27, backButton(), (p, c) -> new DrugSiteMenu(plugin, p, r).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
