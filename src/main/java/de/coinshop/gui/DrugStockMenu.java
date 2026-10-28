package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.DrugTrade;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/** Lager: alle Waren nach Qualität. Ein Klick mischt eine Menge mit dem gewählten Zusatzstoff zur nächsten Qualitätsstufe. */
public final class DrugStockMenu extends Menu {

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public DrugStockMenu(CoinShopPlugin plugin, Player viewer, int revier, int additive) {
        super(plugin, 6, Component.text("Lager und Mischen - " + plugin.drugs().siteName(revier)));
        DrugTrade drugs = plugin.drugs();
        UUID uuid = viewer.getUniqueId();
        int r = revier;
        int add = Math.max(0, Math.min(DrugTrade.ADDITIVES.length - 1, additive));
        for (int p = 0; p < DrugTrade.PRODUCTS.length; p++) {
            for (int q = 0; q < DrugTrade.QUALITIES.length; q++) {
                int product = p;
                int quality = q;
                int have = drugs.stock(uuid, r, p, q);
                int slot = p * 9 + q;
                ItemStack item = ItemUtil.icon(have > 0 ? DrugTrade.ICONS[p] : Material.GRAY_DYE,
                        line(DrugTrade.PRODUCTS[p] + " (" + DrugTrade.QUALITIES[q] + ")", have > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY), List.of(
                                line("Im Lager: " + have, NamedTextColor.YELLOW),
                                line("Wert je Einheit: " + Math.round(DrugTrade.BASE_PRICE[p] * DrugTrade.QUALITY_FACTOR[q]) + " Coins", NamedTextColor.GRAY),
                                q < DrugTrade.QUALITIES.length - 1
                                        ? line("Klicken: " + drugs.mixUnits() + " mischen mit " + DrugTrade.ADDITIVES[add] + " ("
                                        + drugs.mixChance(q, add) + " %, " + drugs.mixCost(p, add) + " Coins)", NamedTextColor.GREEN)
                                        : line("Höchste Qualität", NamedTextColor.LIGHT_PURPLE)));
                if (have > 0) {
                    item.setAmount(Math.max(1, Math.min(64, have)));
                }
                set(slot, item, q < DrugTrade.QUALITIES.length - 1 && have > 0 ? (pl, c) -> {
                    drugs.mix(pl, r, product, quality, add);
                    new DrugStockMenu(plugin, pl, r, add).open(pl);
                } : null);
            }
        }
        int nextAdd = (add + 1) % DrugTrade.ADDITIVES.length;
        set(49, ItemUtil.icon(Material.SUGAR, line("Zusatzstoff: " + DrugTrade.ADDITIVES[add], NamedTextColor.AQUA), List.of(
                line("Bessere Zusatzstoffe gelingen öfter, kosten aber mehr.", NamedTextColor.GRAY),
                line("Höhere Qualität macht das Mischen schwerer.", NamedTextColor.GRAY),
                line("Misslingt es, ist nur der Zusatzstoff weg.", NamedTextColor.GRAY),
                line("Klicken: " + DrugTrade.ADDITIVES[nextAdd], NamedTextColor.GREEN))),
                (p, c) -> new DrugStockMenu(plugin, p, r, nextAdd).open(p));
        set(45, backButton(), (p, c) -> new DrugSiteMenu(plugin, p, r).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
