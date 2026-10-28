package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import de.coinshop.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Heute: ein Überblick über das, was jetzt offen ist (Aufgaben, Tiefenmine, Großhandel, Expedition). */
public final class TodayMenu extends Menu {

    public TodayMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, Component.text("Heute"));
        UUID uuid = viewer.getUniqueId();
        ExpeditionManager ex = plugin.expeditions();

        List<Component> quest = new ArrayList<>();
        int done = 0;
        int total = 0;
        try {
            for (QuestManager.Progress pr : plugin.quests().check(viewer)) {
                total++;
                if (pr.done()) {
                    done++;
                }
            }
        } catch (RuntimeException ignored) {
            // Aufgaben gerade nicht verfügbar
        }
        quest.add(ExpeditionMenu.line(total == 0 ? "Heute gibt es keine Aufgaben." : done + " von " + total + " erledigt",
                done == total && total > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        quest.add(ExpeditionMenu.line("Klicken: Aufgaben öffnen", NamedTextColor.GREEN));
        set(10, ItemUtil.icon(Material.WRITABLE_BOOK, ExpeditionMenu.line("Tagesaufgaben", NamedTextColor.GOLD), quest),
                (p, c) -> new QuestMenu(plugin, p).open(p));

        List<Component> mine = new ArrayList<>();
        if (!ex.hasMineLicense(viewer)) {
            mine.add(ExpeditionMenu.line("Du brauchst die Lizenz Bergbau.", NamedTextColor.RED));
        } else {
            boolean can = plugin.stamina().has(uuid, de.coinshop.Stamina.Mode.MINE);
            mine.add(ExpeditionMenu.line(can ? "Du hast genug Ausdauer für einen Lauf" : "Zu wenig Ausdauer für einen Lauf",
                    can ? NamedTextColor.GREEN : NamedTextColor.RED));
            mine.add(ExpeditionMenu.line("Beste Ebene: " + ex.mineBest(uuid), NamedTextColor.YELLOW));
        }
        mine.add(ExpeditionMenu.line("Klicken: Tiefenmine öffnen", NamedTextColor.GREEN));
        set(12, ItemUtil.icon(Material.IRON_PICKAXE, ExpeditionMenu.line("Tiefenmine", NamedTextColor.GOLD), mine),
                (p, c) -> new MineMenu(plugin, p).open(p));

        List<Component> exp = new ArrayList<>();
        exp.add(ExpeditionMenu.line("Nächstes Level: " + ex.nextLevel(uuid) + " von " + ExpeditionManager.MAX_LEVEL, NamedTextColor.YELLOW));
        exp.add(ExpeditionMenu.line("Klicken: Abenteuer öffnen", NamedTextColor.GREEN));
        set(14, ItemUtil.icon(Material.DIAMOND_SWORD, ExpeditionMenu.line("Abenteuer", NamedTextColor.GOLD), exp),
                (p, c) -> new AdventureMenu(plugin, p).open(p));

        List<Component> wh = new ArrayList<>();
        int working = ex.life().assigned(uuid).size();
        wh.add(ExpeditionMenu.line(working + " Begleiter arbeiten im Großhandel", working > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        wh.add(ExpeditionMenu.line("Sie liefern beim Shop-Wechsel.", NamedTextColor.GRAY));
        wh.add(ExpeditionMenu.line("Klicken: Großhandel öffnen", NamedTextColor.GREEN));
        set(16, ItemUtil.icon(Material.BARREL, ExpeditionMenu.line("Großhandel", NamedTextColor.GOLD), wh),
                (p, c) -> new WholesaleHubMenu(plugin, p).open(p));

        set(18, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
