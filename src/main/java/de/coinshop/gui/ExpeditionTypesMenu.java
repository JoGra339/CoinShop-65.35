package de.coinshop.gui;

import de.coinshop.CompanionLife;
import de.coinshop.CompanionTypes;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Übersicht: die acht Typen (wie bei Pokémon) und die Fusions-Rezepte. */
public final class ExpeditionTypesMenu extends Menu {

    public ExpeditionTypesMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Typen und Rezepte"));
        CompanionTypes.MobType[] types = CompanionTypes.MobType.values();
        for (int i = 0; i < types.length; i++) {
            CompanionTypes.MobType t = types[i];
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line(CompanionTypes.describe(t), NamedTextColor.GRAY));
            lore.add(ExpeditionMenu.line("Stark = 1,5-facher, schwach = 0,7-facher Schaden.", NamedTextColor.DARK_GRAY));
            lore.add(ExpeditionMenu.line("Gilt im Level und auf Reisen (Typ des Ziels).", NamedTextColor.DARK_GRAY));
            set(10 + i + (i >= 4 ? 5 : 0), ItemUtil.icon(Material.NAME_TAG, ExpeditionMenu.line(t.label, t.color), lore), null);
        }
        List<Component> rec = new ArrayList<>();
        rec.add(ExpeditionMenu.line("Zwei gleiche Arten ergeben eine Veredelung", NamedTextColor.GRAY));
        rec.add(ExpeditionMenu.line("(eine Seltenheit höher).", NamedTextColor.GRAY));
        for (String s : plugin.expeditions().life().recipeTexts()) {
            rec.add(ExpeditionMenu.line(s, NamedTextColor.YELLOW));
        }
        set(22, ItemUtil.icon(Material.KNOWLEDGE_BOOK, ExpeditionMenu.line("Fusions-Rezepte", NamedTextColor.GOLD), rec), null);
        set(27, backButton(), (p, c) -> new ExpeditionCompanionHubMenu(plugin, p).open(p));
        fillEmpty();
    }
}
