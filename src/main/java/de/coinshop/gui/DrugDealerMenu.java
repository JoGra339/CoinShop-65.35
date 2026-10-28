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

/** Dealer: Angestellte verkaufen vom Lager, auch wenn du nicht da bist (sie behalten einen Anteil). */
public final class DrugDealerMenu extends Menu {

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public DrugDealerMenu(CoinShopPlugin plugin, Player viewer, int revier) {
        super(plugin, 3, Component.text("Dealer - " + plugin.drugs().siteName(revier)));
        DrugTrade drugs = plugin.drugs();
        UUID uuid = viewer.getUniqueId();
        int r = revier;
        drugs.settleDealers(viewer, r, false);
        int n = drugs.dealers(uuid, r);
        set(11, ItemUtil.icon(Material.EMERALD, line("Dealer anstellen", NamedTextColor.GREEN), List.of(
                line("Angestellt: " + n + " von " + drugs.dealerMax(), NamedTextColor.YELLOW),
                n < drugs.dealerMax() ? line("Kosten: " + drugs.dealerCost(uuid, r) + " Coins", NamedTextColor.GOLD) : line("Mehr geht nicht.", NamedTextColor.RED),
                line("Jeder verkauft etwa " + drugs.dealerUnitsPerHour() + " Einheiten pro Stunde,", NamedTextColor.GRAY),
                line("schlechteste Qualität zuerst, zum Normalpreis.", NamedTextColor.GRAY),
                line("Sie behalten " + plugin.getConfig().getInt("drugs.dealer-commission-percent", 25) + " % und erhöhen die Hitze.", NamedTextColor.GRAY))),
                (p, c) -> {
                    drugs.hireDealer(p, r);
                    new DrugDealerMenu(plugin, p, r).open(p);
                });
        set(13, ItemUtil.icon(Material.CHEST, line("Jetzt abrechnen", NamedTextColor.GOLD), List.of(
                line("Verkäufe der letzten Stunden werden gutgeschrieben", NamedTextColor.GRAY),
                line("(höchstens " + plugin.getConfig().getInt("drugs.dealer-max-hours", 24) + " Std. rückwirkend).", NamedTextColor.GRAY),
                line("Das passiert auch beim Öffnen dieses Menüs.", NamedTextColor.DARK_GRAY))), (p, c) -> {
            drugs.settleDealers(p, r, true);
            new DrugDealerMenu(plugin, p, r).open(p);
        });
        set(15, ItemUtil.icon(Material.BARRIER, line("Einen Dealer entlassen", NamedTextColor.RED), List.of(
                line("Spart nichts zurück, aber senkt die Hitze-Quelle.", NamedTextColor.GRAY))), n > 0 ? (p, c) -> {
            drugs.fireDealer(p, r);
            new DrugDealerMenu(plugin, p, r).open(p);
        } : null);
        set(18, backButton(), (p, c) -> new DrugSiteMenu(plugin, p, r).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(26, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
