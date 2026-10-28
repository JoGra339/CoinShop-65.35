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

/**
 * Aufstellung vor einem Kampf (wie bei einem Auto-Battler): Vordere Reihe fängt die Angriffe ab, die hintere Reihe wird
 * erst getroffen, wenn vorne niemand mehr steht. Klick auf einen Begleiter wechselt die Reihe.
 */
public final class FormationMenu extends Menu {

    private static final int[] FRONT = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] BACK = {28, 29, 30, 31, 32, 33, 34};

    public FormationMenu(CoinShopPlugin plugin, Player viewer, int max, Runnable start) {
        super(plugin, 6, Component.text("Aufstellung"));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();
        List<Copy> team = ex.duel().team(uuid, Math.min(max, FRONT.length * 2));

        set(4, ItemUtil.icon(Material.PAPER, ExpeditionMenu.line("So funktioniert die Aufstellung", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Die vordere Reihe wird zuerst angegriffen.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Die hintere Reihe wird kaum getroffen,", NamedTextColor.GRAY),
                ExpeditionMenu.line("solange vorne noch jemand steht.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klick auf einen Begleiter: Reihe wechseln.", NamedTextColor.YELLOW))), null);
        set(9, ItemUtil.icon(Material.IRON_SWORD, ExpeditionMenu.line("Vordere Reihe", NamedTextColor.RED), List.of()), null);
        set(27, ItemUtil.icon(Material.BOW, ExpeditionMenu.line("Hintere Reihe", NamedTextColor.AQUA), List.of()), null);

        int f = 0;
        int b = 0;
        for (Copy k : team) {
            boolean back = ex.duel().isBack(uuid, k);
            if (back && b >= BACK.length) {
                back = false;
            }
            if (!back && f >= FRONT.length) {
                back = true;
            }
            List<Component> lore = new ArrayList<>();
            lore.addAll(CompanionInfo.lines(plugin, uuid, k));
            lore.add(ExpeditionMenu.line(back ? "Klicken: nach vorne" : "Klicken: nach hinten", NamedTextColor.GREEN));
            String name = ex.trips().copyName(k.companion(), k.index()) + " Lv." + ex.copyLevel(uuid, k.companion(), k.index());
            int slot = back ? BACK[b++] : FRONT[f++];
            set(slot, ItemUtil.icon(k.companion().icon, ExpeditionMenu.line(name, back ? NamedTextColor.AQUA : NamedTextColor.RED), lore),
                    (p, c) -> {
                        ex.duel().toggleRow(uuid, k);
                        new FormationMenu(plugin, p, max, start).open(p);
                    });
        }
        if (team.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Keine einsatzbereiten Begleiter", NamedTextColor.RED), List.of()), null);
        }
        if (start != null) {
            set(49, ItemUtil.icon(Material.LIME_DYE, ExpeditionMenu.line("Kampf starten", NamedTextColor.GREEN), List.of(
                    ExpeditionMenu.line("Gruppenstärke: " + Math.round(ex.duel().strength(uuid)), NamedTextColor.YELLOW))),
                    (p, c) -> {
                        p.closeInventory();
                        start.run();
                    });
            set(45, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Abbrechen", NamedTextColor.RED), List.of()), (p, c) -> p.closeInventory());
        } else {
            set(45, backButton(), (p, c) -> new DuelMenu(plugin, p).open(p));
        }
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
