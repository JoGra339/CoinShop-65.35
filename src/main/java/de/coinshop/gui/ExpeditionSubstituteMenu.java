package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Copy;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Vor dem Level: Es sind weniger Begleiter dabei als möglich. Ersatz mitnehmen, ohne weiter oder verlassen. */
public final class ExpeditionSubstituteMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public ExpeditionSubstituteMenu(CoinShopPlugin plugin, Player viewer, int level) {
        super(plugin, 3, Component.text("Ersatz mitnehmen?"));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();
        int dabei = ex.takenReady(uuid).size();
        int limit = ex.pickLimit(uuid, level);
        List<Copy> spare = ex.spareCopies(uuid, level);
        set(4, ItemUtil.icon(Material.PAPER, ExpeditionMenu.line("Du hast noch Platz", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Dabei: " + dabei + " von " + limit + " möglichen Begleitern", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Klicke einen Ersatz, um ihn mitzunehmen.", NamedTextColor.GRAY))), null);
        for (int i = 0; i < SLOTS.length && i < spare.size(); i++) {
            Copy k = spare.get(i);
            int lvl = ex.copyLevel(uuid, k.companion(), k.index());
            List<Component> lore = new ArrayList<>();
            lore.addAll(CompanionInfo.lines(plugin, uuid, k));
            lore.add(ExpeditionMenu.line("Klicken: mitnehmen und Level starten", NamedTextColor.GREEN));
            set(SLOTS[i], ItemUtil.icon(k.companion().icon, ExpeditionMenu.line(k.companion().label + " #" + (k.index() + 1), NamedTextColor.GOLD), lore),
                    (p, c) -> ex.substituteChosen(p, k));
        }
        set(20, ItemUtil.icon(Material.LIME_DYE, ExpeditionMenu.line("Weiter ohne Ersatz", NamedTextColor.GREEN), List.of(
                ExpeditionMenu.line("Das Level mit " + dabei + " Begleiter(n) starten.", NamedTextColor.GRAY))),
                (p, c) -> ex.substituteChosen(p, null));
        set(24, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Verlassen", NamedTextColor.RED), List.of(
                ExpeditionMenu.line("Die Expedition beenden.", NamedTextColor.GRAY))),
                (p, c) -> ex.substituteLeave(p));
        fillEmpty();
    }
}
