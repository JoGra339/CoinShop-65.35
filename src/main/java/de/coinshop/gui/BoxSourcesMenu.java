package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Übersicht: Woher kommen Mystery Boxen, und wie viele sind heute noch erhältlich?
 * Für Expeditionen gelten Tageslimits (je Quelle und insgesamt); die übrigen Quellen haben ihre eigenen Regeln.
 */
public final class BoxSourcesMenu extends Menu {

    private static final Material[] ICONS = {Material.WITHER_SKELETON_SKULL, Material.ZOMBIE_HEAD, Material.GOLDEN_HELMET,
            Material.CHEST, Material.BONE};
    private static final int[] SLOTS = {10, 11, 12, 13, 14};

    private static Component t(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    /** Eine Quelle ohne Zähler: Name, Symbol, Regel. */
    private record Other(Material icon, String name, String rule) { }

    private static final List<Other> OTHERS = List.of(
            new Other(Material.SUNFLOWER, "Login-Serie", "Jeden 7. Tag der Serie, die Stufe steigt mit den Wochen."),
            new Other(Material.WRITABLE_BOOK, "Tagesaufgaben", "Einmal pro Tag, wenn du alle Aufgaben geschafft hast."),
            new Other(Material.AMETHYST_SHARD, "Gratis-Glücksrad", "Gelegentlich als Preis beim täglichen Gratis-Glücksrad."),
            new Other(Material.GOLDEN_HELMET, "Wochen-Ranglisten", "Sonntags für die Plätze 1 bis 3 jeder Rangliste."),
            new Other(Material.FISHING_ROD, "Angeln", "Selten als Schatzfund, kein festes Limit."),
            new Other(Material.PLAYER_HEAD, "Kopfgeld und große Wetten", "Pro erfülltem Kopfgeld bzw. gewonnener großer Wette."),
            new Other(Material.IRON_SWORD, "NPC-Duelle und Turnier", "Für Siege, Stufe nach Gegner."));

    public BoxSourcesMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Mystery Boxen heute"));
        UUID uuid = viewer.getUniqueId();
        ExpeditionManager ex = plugin.expeditions();
        int totalCap = ex.boxTotalCap();
        int gotTotal = ex.boxGotTodayTotal(uuid);
        set(4, ItemUtil.icon(Material.ENDER_EYE, t("Aus Expeditionen heute", NamedTextColor.GOLD), List.of(
                t(totalCap > 0 ? "Noch " + Math.max(0, totalCap - gotTotal) + " von " + totalCap + " Boxen" : "Kein Gesamtlimit",
                        NamedTextColor.WHITE),
                t("Dazu hat jede Quelle ihr eigenes Limit.", NamedTextColor.GRAY),
                t("Der Tag wechselt um Mitternacht (Serverzeit).", NamedTextColor.DARK_GRAY))), null);
        for (int i = 0; i < ExpeditionManager.BOX_SOURCES.length; i++) {
            String key = ExpeditionManager.BOX_SOURCES[i][0];
            String name = ExpeditionManager.BOX_SOURCES[i][1];
            int cap = ex.boxSourceCap(key);
            int got = ex.boxGotToday(uuid, key);
            int left = ex.boxLeftToday(uuid, key);
            List<Component> lore = new java.util.ArrayList<>();
            lore.add(t(left < 0 ? "Heute: unbegrenzt" : "Heute noch: " + left, left == 0 ? NamedTextColor.RED : NamedTextColor.GREEN));
            if (cap > 0) {
                lore.add(t("Limit dieser Quelle: " + cap + " pro Tag (heute schon " + got + ")", NamedTextColor.GRAY));
            }
            if (totalCap > 0 && gotTotal >= totalCap) {
                lore.add(t("Das Gesamtlimit für heute ist erreicht.", NamedTextColor.DARK_GRAY));
            }
            set(SLOTS[i], ItemUtil.icon(ICONS[i], t(name, NamedTextColor.GOLD), lore), null);
        }
        set(18, ItemUtil.icon(Material.PAPER, t("Andere Quellen", NamedTextColor.AQUA), List.of(
                t("Rechts siehst du Quellen ohne Expeditions-Limit.", NamedTextColor.GRAY),
                t("Dort gelten die genannten Regeln.", NamedTextColor.GRAY))), null);
        for (int i = 0; i < OTHERS.size() && i < 7; i++) {
            Other o = OTHERS.get(i);
            set(19 + i, ItemUtil.icon(o.icon(), t(o.name(), NamedTextColor.AQUA), List.of(
                    t(o.rule(), NamedTextColor.GRAY))), null);
        }
        set(27, balanceIcon(viewer), incomeClick());
        set(31, backButton(), (player, click) -> new MysteryBoxMenu(plugin, player).open(player));
        fillEmpty();
    }
}
