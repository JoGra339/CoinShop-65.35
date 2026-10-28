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

/** Abenteuer: alles, wobei du kämpfst (Expeditionslevel, Verteidigung, Duelle, Untergrund-Arenen). */
public final class AdventureMenu extends Menu {

    public AdventureMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Abenteuer"));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();

        set(10, ItemUtil.icon(Material.DIAMOND_SWORD, ExpeditionMenu.line("Expeditionslevel", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Nächstes Level: " + ex.nextLevel(uuid) + " von " + ExpeditionManager.MAX_LEVEL, NamedTextColor.YELLOW),
                ExpeditionMenu.line("Sieh, welche Kreaturen dich erwarten,", NamedTextColor.GRAY),
                ExpeditionMenu.line("und starte die Expedition.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (p, c) -> new ExpeditionLevelMenu(plugin, p).open(p));

        if (ex.defenseEnabled()) {
            set(12, ItemUtil.icon(Material.BEACON, ExpeditionMenu.line("Verteidigung", NamedTextColor.GOLD), List.of(
                    ExpeditionMenu.line("Schütze den Kern vor Welle um Welle.", NamedTextColor.GRAY),
                    ExpeditionMenu.line("Du kannst Spieler einladen und mehr Begleiter mitnehmen.", NamedTextColor.GRAY),
                    ExpeditionMenu.line("Deine beste Welle: " + ex.defenseBest(uuid), NamedTextColor.YELLOW),
                    ExpeditionMenu.line("Klicken: Vorauswahl öffnen", NamedTextColor.GREEN))),
                    (p, c) -> {
                        p.closeInventory();
                        ex.startDefense(p);
                    });
        }

        set(14, ItemUtil.icon(Material.IRON_SWORD, ExpeditionMenu.line("Duelle", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Fordere Spieler heraus oder tritt gegen NPCs an.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Deine Begleiter kämpfen live in einer Arena.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Siege: " + ex.roster().duelWins(uuid) + ", Niederlagen: " + ex.roster().duelLosses(uuid), NamedTextColor.YELLOW),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (p, c) -> new DuelMenu(plugin, p).open(p));

        int open = 0;
        for (int site = 0; site < ex.arenas().sites(); site++) {
            if (ex.arenas().siteUnlocked(uuid, site)) {
                open++;
            }
        }
        set(16, ItemUtil.icon(Material.NETHERITE_SWORD, ExpeditionMenu.line("Untergrund-Arenen", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Jedes Revier hat eine Kampfarena.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Sie öffnet sich mit dem Kauf des Reviers.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Offen: " + open + " von " + ex.arenas().sites(), NamedTextColor.YELLOW),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (p, c) -> new ArenaListMenu(plugin, p).open(p));

        if (ex.mineEnabled()) {
            set(22, ItemUtil.icon(Material.IRON_PICKAXE, ExpeditionMenu.line("Tiefenmine", NamedTextColor.GOLD), List.of(
                    ExpeditionMenu.line("Erze abbauen, Ebene für Ebene tiefer (kostet Ausdauer).", NamedTextColor.GRAY),
                    ExpeditionMenu.line("Alle " + ex.guardianEvery() + " Ebenen wartet ein Wächter.", NamedTextColor.GRAY),
                    ExpeditionMenu.line("Tiefste Ebene: " + ex.mineBest(uuid), NamedTextColor.YELLOW),
                    ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                    (p, c) -> new MineMenu(plugin, p).open(p));
        }

        set(24, ItemUtil.icon(Material.GOLDEN_CARROT, ExpeditionMenu.line("Ausdauer", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Vorrat: " + de.coinshop.Stamina.format(plugin.stamina().get(uuid)), NamedTextColor.YELLOW),
                ExpeditionMenu.line("Mine, Level und Verteidigung kosten Ausdauer.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: ansehen und steigern", NamedTextColor.GREEN))),
                (p, c) -> new StaminaMenu(plugin, p).open(p));

        set(27, backButton(), (p, c) -> new ExpeditionMenu(plugin, p).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
