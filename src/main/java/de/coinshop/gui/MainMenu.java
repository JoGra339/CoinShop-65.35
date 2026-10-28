package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Das Hauptmenü, in drei Zonen geordnet:
 * oben der Schnellzugriff auf das Häufigste (Shop, Aufgaben, Expeditionen, Casino), in der Mitte der Schwarzmarkt,
 * darunter drei Bereiche, die Zusammengehöriges bündeln (Anlegen und Handeln, Ich und Fortschritt, Info und Konto).
 * Ganz unten: Kontostand, Schließen, Shop-Konto.
 */
public final class MainMenu extends Menu {

    private static final List<Component> NONE = List.of();

    public MainMenu(CoinShopPlugin plugin) {
        super(plugin, 5, plugin.messages().get("gui.main.title"));
        Messages msg = plugin.messages();

        // Schnellzugriff (Reihe 2)
        set(10, ItemUtil.icon(Material.ENDER_CHEST, msg.get("gui.main.shop-name"), NONE),
                (player, click) -> new ShopHubMenu(plugin, player).open(player));
        set(12, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.main.quest-name"), NONE),
                (player, click) -> new QuestMenu(plugin, player).open(player));
        set(16, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.main.casino-name"), NONE),
                (player, click) -> new CasinoMenu(plugin, player).open(player));

        // Schwarzmarkt (Mitte)
        set(22, ItemUtil.icon(Material.WITHER_SKELETON_SKULL, msg.get("gui.main.blackmarket-name"), msg.getList("gui.main.blackmarket-lore")),
                (player, click) -> {
                    if (plugin.blackMarket().unlocked(player)) {
                        new BlackMarketHubMenu(plugin, player).open(player);
                    } else {
                        msg.send(player, "bm-locked");
                    }
                });

        // Bereiche (Reihe 4)
        set(29, ItemUtil.icon(Material.GOLD_BLOCK, ExpeditionMenu.line("Anlegen und Handeln", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Investitionen, Wettbüro, Großhandel, Lizenzen", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (player, click) -> new AreaMenu(plugin, player, AreaMenu.Area.MONEY).open(player));
        set(31, ItemUtil.icon(Material.PLAYER_HEAD, ExpeditionMenu.line("Ich und Fortschritt", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Profil, Titel, Ranglisten, Sammlungen, Erfolge", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (player, click) -> new AreaMenu(plugin, player, AreaMenu.Area.ME).open(player));
        set(33, ItemUtil.icon(Material.BELL, ExpeditionMenu.line("Info und Konto", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Ereignisse, Einkommen, Buchhaltung, Zeitplan", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (player, click) -> new AreaMenu(plugin, player, AreaMenu.Area.INFO).open(player));

        // Untere Reihe: Schließen (Kontostand und Shop-Konto kommen beim Öffnen dazu)
        set(40, ItemUtil.icon(Material.BARRIER, msg.get("gui.close-name"), NONE),
                (player, click) -> player.closeInventory());

        fillEmpty();
    }

    @Override
    public void open(Player player) {
        Messages msg = plugin.messages();
        // Diese Anzeigen ändern sich ständig, deshalb erst beim Öffnen einsetzen
        set(39, balanceIcon(player), incomeClick());
        if (plugin.expeditions().enabled()) {
            set(14, ItemUtil.icon(Material.DIAMOND_SWORD, ExpeditionMenu.line("Expeditionen", NamedTextColor.GOLD),
                    List.of(ExpeditionMenu.line("Kämpfe in der Arena gegen Kreaturen.", NamedTextColor.GRAY),
                            ExpeditionMenu.line("Braucht die Expeditionslizenz.", NamedTextColor.DARK_GRAY))),
                    (p, click) -> {
                        if (!plugin.expeditions().hasLicense(p)) {
                            msg.send(p, "ex-locked");
                        } else {
                            new ExpeditionMenu(plugin, p).open(p);
                        }
                    });
        }
        set(41, ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.main.shop-account-name"),
                msg.getList("gui.main.shop-account-lore", "coins", plugin.database().getShopAccount())), null);
        super.open(player);
    }
}
