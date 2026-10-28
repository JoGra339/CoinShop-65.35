package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Insolvenz: das Gewerbe auflösen (mit Rückfrage). */
public final class CompanyDissolveMenu extends Menu {

    public CompanyDissolveMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, Component.text("Insolvenz anmelden?"));
        set(11, de.coinshop.ItemUtil.icon(Material.RED_CONCRETE, ExpeditionMenu.line("Ja, Insolvenz anmelden", NamedTextColor.RED),
                List.of(ExpeditionMenu.line("Endgültig: Gewerbe wird aufgelöst.", NamedTextColor.GRAY))), (p, c) -> {
            plugin.companies().dissolve(p);
            p.closeInventory();
        });
        set(13, de.coinshop.ItemUtil.icon(Material.PAPER, ExpeditionMenu.line("Was passiert?", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Das Gewerbe wird aufgelöst.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Alle Angestellten werden entlassen.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Alle KI-Mitarbeiter gehen verloren.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Gezahlte Preise gibt es nicht zurück.", NamedTextColor.RED),
                ExpeditionMenu.line("Du kannst später neu gründen.", NamedTextColor.GRAY))), null);
        set(15, de.coinshop.ItemUtil.icon(Material.LIME_CONCRETE, ExpeditionMenu.line("Abbrechen", NamedTextColor.GREEN),
                List.of(ExpeditionMenu.line("Das Gewerbe bleibt bestehen.", NamedTextColor.GRAY))),
                (p, c) -> new CompanyMenu(plugin, p).open(p));
        fillEmpty();
    }
}
