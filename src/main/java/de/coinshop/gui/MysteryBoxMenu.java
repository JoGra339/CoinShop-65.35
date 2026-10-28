package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.MysteryBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.UUID;

/**
 * Das Box-Lager: alle Mystery Boxen des Spielers, getrennt nach Stufe 1 bis 5. Linksklick öffnet eine Box,
 * Shift-Klick öffnet bis zu 10 auf einmal. Erreichbar über den Knopf oben rechts im Profil.
 */
public final class MysteryBoxMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15};

    public MysteryBoxMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Mystery Boxen"));
        UUID uuid = viewer.getUniqueId();
        MysteryBox boxes = plugin.mysteryBoxes();
        int total = boxes.total(uuid);

        set(4, ItemUtil.icon(Material.ENDER_EYE, Component.text("Dein Box-Lager", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(line("Du hast insgesamt " + total + " Mystery Boxen.", NamedTextColor.GRAY),
                        line("Der Inhalt wird beim Öffnen gewürfelt:", NamedTextColor.GRAY),
                        line("Coins, Aktien, Krypto, Lizenzen, Miner,", NamedTextColor.GRAY),
                        line("Begleiter, KI-Plätze und mehr.", NamedTextColor.GRAY),
                        line("Je höher die Stufe, desto besser.", NamedTextColor.GRAY))), null);

        for (int i = 0; i < 5; i++) {
            int tier = i + 1;
            int have = boxes.count(uuid, tier);
            NamedTextColor color = MysteryBox.colorOf(tier);
            Material icon = have > 0 ? Material.ENDER_CHEST : Material.CHEST;
            var item = ItemUtil.icon(icon, Component.text("Mystery Box Stufe " + tier + " von 5", color)
                            .decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD),
                    List.of(line("Vorrat: " + have, NamedTextColor.WHITE),
                            line(tier >= 5 ? "Belohnungen: 3" : tier >= 3 ? "Belohnungen: 2" : "Belohnung: 1", NamedTextColor.GRAY),
                            have > 0 ? line("Linksklick: eine öffnen", NamedTextColor.GREEN) : line("Du hast keine Box dieser Stufe.", NamedTextColor.DARK_GRAY),
                            have > 1 ? line("Shift-Linksklick: bis zu 10 öffnen", NamedTextColor.GREEN) : Component.empty(),
                            have > 0 ? line("Rechtsklick: an den Shop verkaufen (" + boxes.sellPrice(tier) + " Coins)", NamedTextColor.GOLD) : Component.empty(),
                            have > 1 ? line("Shift-Rechtsklick: bis zu 10 verkaufen", NamedTextColor.GOLD) : Component.empty()));
            set(SLOTS[i], item, (player, click) -> {
                if (boxes.count(player.getUniqueId(), tier) <= 0) {
                    return;
                }
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    boxes.sellToShop(player, tier, click == ClickType.SHIFT_RIGHT ? 10 : 1);
                } else {
                    boxes.open(player, tier, click == ClickType.SHIFT_LEFT ? 10 : 1);
                }
                new MysteryBoxMenu(plugin, player).open(player);
            });
            set(20 + i, ItemUtil.icon(Material.ENDER_CHEST, Component.text("Als Gegenstand ausgeben", color).decoration(TextDecoration.ITALIC, false),
                    List.of(line("Zum Verkaufen im Spieler-Shop oder Tauschen.", NamedTextColor.GRAY),
                            line("Wer die Box hat, klickt sie rechts an,", NamedTextColor.GRAY),
                            line("dann liegt sie in seinem Box-Lager.", NamedTextColor.GRAY),
                            line("Klick: 1 Box, Shift-Klick: bis zu 10", NamedTextColor.GREEN))), (player, click) -> {
                boxes.toItems(player, tier, click.isShiftClick() ? 10 : 1);
                new MysteryBoxMenu(plugin, player).open(player);
            });
        }

        set(35, ItemUtil.icon(Material.COMPASS, Component.text("Woher bekomme ich Boxen?", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(line("Zeigt, welche Quelle heute noch", NamedTextColor.GRAY),
                        line("wie viele Boxen gibt.", NamedTextColor.GRAY),
                        line("Klick: Übersicht öffnen", NamedTextColor.GREEN))),
                (player, click) -> new BoxSourcesMenu(plugin, player).open(player));
        set(27, balanceIcon(viewer), incomeClick());
        set(31, backButton(), (player, click) -> new ProfileMenu(plugin, player, player.getUniqueId()).open(player));
        fillEmpty();
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
