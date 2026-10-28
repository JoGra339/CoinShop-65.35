package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Am Schacht: tiefer gehen oder aufsteigen und die Beute einstreichen. */
public final class MineExitMenu extends Menu {

    public MineExitMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, Component.text("Schacht"));
        ExpeditionManager ex = plugin.expeditions();
        ExpeditionManager.MineInfo info = ex.mineInfo(viewer.getUniqueId());
        if (info == null) {
            fillEmpty();
            return;
        }
        List<Component> down = new ArrayList<>();
        down.add(ExpeditionMenu.line("Weiter auf Ebene " + (info.depth() + 1) + ".", NamedTextColor.GRAY));
        down.add(ExpeditionMenu.line("Mehr Ertrag, stärkere Gegner.", NamedTextColor.GRAY));
        if (info.zoneChange()) {
            down.add(ExpeditionMenu.line("Neue Zone: " + info.nextZone(), NamedTextColor.AQUA));
        }
        if ((info.depth() + 1) % ex.guardianEvery() == 0) {
            down.add(ExpeditionMenu.line("Dort wartet ein Wächter!", NamedTextColor.RED));
        }
        down.add(ExpeditionMenu.line("Fällst du, behältst du nur einen Teil der Beute.", NamedTextColor.DARK_GRAY));
        down.add(ExpeditionMenu.line("Klicken: tiefer", NamedTextColor.GREEN));
        set(11, ItemUtil.icon(Material.DIAMOND_PICKAXE, ExpeditionMenu.line("Tiefer gehen", NamedTextColor.GOLD), down),
                (p, c) -> ex.mineDescend(p));

        set(13, ItemUtil.icon(Material.RAW_GOLD, ExpeditionMenu.line("Deine Beute", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Ebene " + info.depth() + " (" + info.zone() + ")", NamedTextColor.YELLOW),
                ExpeditionMenu.line(info.coins() + " Coins, " + info.ores() + " Erze", NamedTextColor.YELLOW))), null);

        set(15, ItemUtil.icon(Material.LADDER, ExpeditionMenu.line("Aufsteigen", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Du behältst die ganze Beute: " + info.coins() + " Coins.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Heute kommst du nicht mehr in die Mine.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: aufsteigen", NamedTextColor.GREEN))),
                (p, c) -> ex.mineAscend(p));
        fillEmpty();
    }
}
