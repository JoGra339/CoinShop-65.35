package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Fahrender Händler (Wellen-Ereignis): drei Angebote zum Festpreis. */
public final class EventTraderMenu extends Menu {

    public EventTraderMenu(CoinShopPlugin plugin, Player viewer, long price) {
        super(plugin, 3, Component.text("Fahrender Händler"));
        offer(11, Material.GLISTERING_MELON_SLICE, "Heiltrank", "Du bist sofort wieder bei vollem Leben.", price, 0);
        offer(13, Material.GOLDEN_APPLE, "Goldapfel", "Schutzherzen für 2 Minuten und kurze Regeneration.", price, 1);
        offer(15, Material.BLAZE_POWDER, "Kampftrank", "Stärke und Tempo für 90 Sekunden.", price, 2);
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    private void offer(int slot, Material icon, String name, String text, long price, int index) {
        set(slot, ItemUtil.icon(icon, ExpeditionMenu.line(name, NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line(text, NamedTextColor.GRAY),
                ExpeditionMenu.line("Preis: " + price + " Coins", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Klicken: kaufen", NamedTextColor.GREEN))), (p, c) -> {
            plugin.expeditions().traderBuy(p, index);
            p.closeInventory();
        });
    }
}
