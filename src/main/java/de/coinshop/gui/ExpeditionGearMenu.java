package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Gear;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Ausrüstung leveln. Klick = 1 Stufe, Shift-Klick = bis zu 10 Stufen. */
public final class ExpeditionGearMenu extends Menu {

    private static final Gear[] ORDER = {Gear.HELMET, Gear.CHESTPLATE, Gear.LEGGINGS, Gear.BOOTS,
            Gear.SWORD, Gear.AXE, Gear.SPEAR, Gear.MACE, Gear.TRIDENT, Gear.BOW, Gear.CROSSBOW,
            Gear.ARROWS, Gear.FOOD, Gear.GAPPLE, Gear.EGAPPLE, Gear.BLOCKS, Gear.PICKAXE};
    private static final int[] SLOTS = {10, 11, 12, 13, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 15};

    public ExpeditionGearMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, Component.text("Expeditions-Ausrüstung"));
        ExpeditionManager ex = plugin.expeditions();
        var uuid = viewer.getUniqueId();
        boolean phase = ex.inShopPhase(uuid);
        for (int i = 0; i < ORDER.length; i++) {
            Gear gear = ORDER[i];
            int level = ex.level(uuid, gear);
            ItemStack shown = ex.itemFor(gear, level);
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line("Stufe " + level + " von " + gear.max, NamedTextColor.YELLOW));
            lore.add(ExpeditionMenu.line("Jetzt: " + ex.describe(gear, level), NamedTextColor.GRAY));
            if (level < gear.max) {
                lore.add(ExpeditionMenu.line("Nächste: " + ex.describe(gear, level + 1), NamedTextColor.GREEN));
                lore.add(ExpeditionMenu.line("Preis: " + ex.gearPriceFor(uuid, gear, level + 1) + " Coins"
                        + (ex.inShopPhase(uuid) ? " (Kaufphase: halber Preis)" : ""), NamedTextColor.GOLD));
                lore.add(ExpeditionMenu.line("Klick: 1 Stufe, Shift-Klick: bis zu 10", NamedTextColor.DARK_GRAY));
            } else {
                lore.add(ExpeditionMenu.line("Höchste Stufe erreicht", NamedTextColor.GREEN));
            }
            Material base = shown == null ? gear.icon : shown.getType();
            set(SLOTS[i], ItemUtil.icon(base, ExpeditionMenu.line(gear.label, NamedTextColor.GOLD), lore), (p, click) -> {
                ex.buyGear(p, gear, click.isShiftClick() ? 10 : 1);
                new ExpeditionGearMenu(plugin, p).open(p);
            });
        }
        set(34, ItemUtil.icon(Material.WOLF_ARMOR, ExpeditionMenu.line("Begleiter", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Wölfe, Golems, Katzen und mehr", NamedTextColor.GRAY),
                ExpeditionMenu.line("helfen dir im Level.", NamedTextColor.GRAY))),
                (p, c) -> new ExpeditionCompanionHubMenu(plugin, p).open(p));
        set(33, ItemUtil.icon(Material.SHIELD, ExpeditionMenu.line("Utensilien", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Einmalig kaufen, danach bekommst du", NamedTextColor.GRAY),
                ExpeditionMenu.line("sie zum Start jedes Levels.", NamedTextColor.GRAY))),
                (p, c) -> new ExpeditionUtilMenu(plugin, p).open(p));
        set(36, backButton(), (p, c) -> {
            if (ex.inShopPhase(p.getUniqueId())) {
                p.closeInventory();
            } else {
                new ExpeditionMenu(plugin, p).open(p);
            }
        });
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(44, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
