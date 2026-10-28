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

/** Herstellung: neue Chargen starten, fertige abholen, das Labor ausbauen. */
public final class DrugProductionMenu extends Menu {

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public DrugProductionMenu(CoinShopPlugin plugin, Player viewer, int revier) {
        super(plugin, 4, Component.text("Labor - " + plugin.drugs().siteName(revier)));
        DrugTrade drugs = plugin.drugs();
        UUID uuid = viewer.getUniqueId();
        int r = revier;
        for (int p = 0; p < DrugTrade.PRODUCTS.length; p++) {
            int product = p;
            boolean open = drugs.productUnlocked(uuid, r, p);
            if (!open) {
                set(10 + p, ItemUtil.icon(Material.GRAY_DYE, line(DrugTrade.PRODUCTS[p], NamedTextColor.DARK_GRAY), List.of(
                        line("Braucht Labor-Stufe " + Math.max(1, p), NamedTextColor.RED))), null);
                continue;
            }
            set(10 + p, ItemUtil.icon(DrugTrade.ICONS[p], line(DrugTrade.PRODUCTS[p], NamedTextColor.GOLD), List.of(
                    line("Eine Charge: " + plugin.getConfig().getInt("drugs.batch-units", 10) + " Einheiten", NamedTextColor.GRAY),
                    line("Kosten: " + drugs.batchCost(p) + " Coins", NamedTextColor.GOLD),
                    line("Dauer: " + drugs.batchMinutes(p) + " Min.", NamedTextColor.YELLOW),
                    line("Normalpreis: " + DrugTrade.BASE_PRICE[p] + " Coins je Einheit", NamedTextColor.GRAY),
                    line("Die Qualität wird zufällig gewürfelt (besseres Labor = besser).", NamedTextColor.DARK_GRAY),
                    line("Klicken: Charge starten", NamedTextColor.GREEN))), (pl, c) -> {
                drugs.startBatch(pl, r, product);
                new DrugProductionMenu(plugin, pl, r).open(pl);
            });
        }
        List<DrugTrade.Batch> batches = drugs.batches(uuid, r);
        long now = System.currentTimeMillis();
        for (int i = 0; i < drugs.labSlots(uuid, r) && i < 5; i++) {
            if (i < batches.size()) {
                DrugTrade.Batch b = batches.get(i);
                boolean done = b.end <= now;
                String left = done ? "fertig" : "noch " + Math.max(1, (b.end - now) / 60_000L) + " Min.";
                set(19 + i, ItemUtil.icon(done ? Material.LIME_DYE : Material.CLOCK,
                        line(DrugTrade.PRODUCTS[b.product] + " (" + DrugTrade.QUALITIES[b.quality] + ")", done ? NamedTextColor.GREEN : NamedTextColor.YELLOW), List.of(
                                line(b.units + " Einheiten, " + left, NamedTextColor.GRAY),
                                line(done ? "Klicken: alle fertigen abholen" : "Läuft noch ...", done ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))),
                        done ? (pl, c) -> {
                            drugs.collectBatches(pl, r);
                            new DrugProductionMenu(plugin, pl, r).open(pl);
                        } : null);
            } else {
                set(19 + i, ItemUtil.icon(Material.GLASS_BOTTLE, line("Freier Platz", NamedTextColor.DARK_GRAY), List.of()), null);
            }
        }
        if (drugs.lab(uuid, r) < 5) {
            set(25, ItemUtil.icon(Material.ANVIL, line("Labor ausbauen auf Stufe " + (drugs.lab(uuid, r) + 1), NamedTextColor.GOLD), List.of(
                    line("Mehr Plätze, bessere Qualität, neue Waren.", NamedTextColor.GRAY),
                    line("Kosten: " + drugs.labUpgradeCost(uuid, r) + " Coins", NamedTextColor.GOLD),
                    line("Klicken zum Ausbauen", NamedTextColor.GREEN))), (pl, c) -> {
                drugs.upgradeLab(pl, r);
                new DrugProductionMenu(plugin, pl, r).open(pl);
            });
        } else {
            set(25, ItemUtil.icon(Material.ANVIL, line("Labor auf Höchststufe", NamedTextColor.GREEN), List.of()), null);
        }
        set(27, backButton(), (p, c) -> new DrugSiteMenu(plugin, p, r).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
