package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Zeigt das nächste Expeditionslevel mit seinen Kreaturen und startet die Expedition. */
public final class ExpeditionLevelMenu extends Menu {

    public ExpeditionLevelMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, Component.text("Expeditionslevel"));
        ExpeditionManager ex = plugin.expeditions();
        var uuid = viewer.getUniqueId();
        int level = ex.nextLevel(uuid);
        int shown = Math.min(level, ExpeditionManager.MAX_LEVEL);
        ExpeditionManager.Plan plan = ex.plan(shown);

        List<Component> info = new ArrayList<>();
        info.add(ExpeditionMenu.line("Kreaturen: " + plan.total(), NamedTextColor.GRAY));
        int supporters = 0;
        for (Map.Entry<EntityType, Integer> e : plan.mobs().entrySet()) {
            if (ex.isSupporter(e.getKey())) {
                supporters += e.getValue();
            }
        }
        info.add(ExpeditionMenu.line("Angreifer: " + (plan.total() - supporters) + ", Unterstützer (harmlos): " + supporters, NamedTextColor.GRAY));
        info.add(ExpeditionMenu.line("Leben x" + ExpeditionMenu.trim(Math.round(plan.hpMult() * 10) / 10.0)
                + ", Schaden x" + ExpeditionMenu.trim(Math.round(plan.dmgMult() * 10) / 10.0), NamedTextColor.GRAY));
        info.add(ExpeditionMenu.line("Arena: " + ex.themeName(plan.tier()), NamedTextColor.GRAY));
        info.add(ExpeditionMenu.line(ex.biomeLine(plan.tier()), NamedTextColor.AQUA));
        if (plan.boss()) {
            info.add(ExpeditionMenu.line("BOSSLEVEL: mehr und schwerere Gegner, dazu ein Anführer", NamedTextColor.RED));
        }
        if (ExpeditionManager.isExamLevel(shown)) {
            info.add(ExpeditionMenu.line("PRÜFUNG: " + ExpeditionManager.examName(shown) + " (" + ExpeditionManager.examParts(shown) + " Teile)", NamedTextColor.LIGHT_PURPLE));
            info.add(ExpeditionMenu.line(ExpeditionManager.examText(shown), NamedTextColor.GRAY));
            info.add(ExpeditionMenu.line("Dreifache Belohnung und eine Mystery Box", NamedTextColor.GOLD));
        }
        info.add(ExpeditionMenu.line("Belohnung: " + ex.reward(uuid, shown) + " Coins", NamedTextColor.GOLD));
        set(4, ItemUtil.icon(Material.MAP, ExpeditionMenu.line("Level " + shown, NamedTextColor.GOLD), info), null);

        int slot = 9;
        for (Map.Entry<EntityType, Integer> e : plan.mobs().entrySet()) {
            if (slot > 35) {
                break;
            }
            Material egg = Material.matchMaterial(e.getKey().name() + "_SPAWN_EGG");
            set(slot++, ItemUtil.icon(egg == null ? Material.EGG : egg,
                    ExpeditionMenu.line(e.getValue() + "x " + ex.mobName(e.getKey()), NamedTextColor.YELLOW),
                    List.of(ExpeditionMenu.line(ex.isSupporter(e.getKey()) ? "Unterstützer: kann keinen Schaden machen" : "Angreifer: kann Schaden machen",
                            ex.isSupporter(e.getKey()) ? NamedTextColor.GREEN : NamedTextColor.RED))), null);
        }

        List<Component> go = new ArrayList<>();
        if (level > ExpeditionManager.MAX_LEVEL) {
            go.add(ExpeditionMenu.line("Du hast alle Level geschafft!", NamedTextColor.GREEN));
        } else {
            if (!ex.hasAnyGear(uuid)) {
                go.add(ExpeditionMenu.line("Achtung: Du startest ohne Ausrüstung!", NamedTextColor.RED));
            }
            go.add(ExpeditionMenu.line("Dein Rucksack wird sicher verwahrt", NamedTextColor.GRAY));
            go.add(ExpeditionMenu.line("und danach zurückgegeben.", NamedTextColor.GRAY));
            go.add(ExpeditionMenu.line("Klicken zum Starten", NamedTextColor.GREEN));
        }
        set(40, ItemUtil.icon(Material.LIME_DYE, ExpeditionMenu.line("Expedition starten", NamedTextColor.GREEN), go), (p, c) -> {
            p.closeInventory();
            ex.start(p);
        });
        set(36, backButton(), (p, c) -> new AdventureMenu(plugin, p).open(p));
        fillEmpty();
    }
}
