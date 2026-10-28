package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CompanionTraits;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Copy;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Erscheint, wenn für einen Kampf zu viele oder zu wenige Begleiter dabei sind: Begleiter abwählen oder dazunehmen,
 * bis die Zahl passt, dann geht es mit "Weiter" im Kampf weiter.
 */
public final class TeamPickMenu extends Menu {

    private static final int PAGE = 36;

    public TeamPickMenu(CoinShopPlugin plugin, Player viewer, int min, int max, int page, Runnable done) {
        super(plugin, 6, Component.text("Begleiter wählen: " + min + " bis " + max));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();
        List<Copy> all = new ArrayList<>(ex.allCopies(uuid));
        all.sort((a, b) -> {
            int ta = ex.taken(uuid, a.companion(), a.index()) ? 0 : 1;
            int tb = ex.taken(uuid, b.companion(), b.index()) ? 0 : 1;
            if (ta != tb) {
                return Integer.compare(ta, tb);
            }
            return Integer.compare(ex.copyLevel(uuid, b.companion(), b.index()), ex.copyLevel(uuid, a.companion(), a.index()));
        });
        int count = ex.takenReady(uuid).size();
        int pages = Math.max(1, (all.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        for (int i = 0; i < PAGE && pg * PAGE + i < all.size(); i++) {
            Copy k = all.get(pg * PAGE + i);
            boolean in = ex.taken(uuid, k.companion(), k.index());
            String busy = ex.busyReason(uuid, k.companion(), k.index());
            CompanionTraits.Rarity rar = ex.rarity(uuid, k.companion(), k.index());
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line(in ? "Im Team" : "Nicht im Team", in ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            lore.addAll(CompanionInfo.lines(plugin, uuid, k));
            if (busy != null) {
                lore.add(ExpeditionMenu.line("Nicht einsatzbereit: " + busy, NamedTextColor.RED));
            } else {
                lore.add(ExpeditionMenu.line(in ? "Klicken: abwählen" : "Klicken: dazunehmen", NamedTextColor.GREEN));
            }
            String name = (in ? "[Team] " : "") + k.companion().label + " #" + (k.index() + 1) + " Lv." + ex.copyLevel(uuid, k.companion(), k.index());
            ItemStack item = ItemUtil.icon(busy != null ? Material.GRAY_DYE : k.companion().icon,
                    ExpeditionMenu.line(name, in ? NamedTextColor.GREEN : rar.color), lore);
            if (in) {
                item.editMeta(m -> m.setEnchantmentGlintOverride(true));
            }
            set(i, item, (p, c) -> {
                ex.toggleTake(p, k.companion(), k.index());
                new TeamPickMenu(plugin, p, min, max, pg, done).open(p);
            });
        }
        boolean ok = count >= min && count <= max;
        String state = count > max ? "Zu viele: wähle " + (count - max) + " ab" : count < min ? "Zu wenige: nimm mindestens " + (min - count) + " dazu" : "Passt";
        set(49, ItemUtil.icon(ok ? Material.LIME_DYE : Material.RED_DYE, ExpeditionMenu.line("Weiter (" + count + " Begleiter)", ok ? NamedTextColor.GREEN : NamedTextColor.RED), List.of(
                ExpeditionMenu.line("Erlaubt: " + min + " bis " + max + " Begleiter.", NamedTextColor.GRAY),
                ExpeditionMenu.line(state, ok ? NamedTextColor.GREEN : NamedTextColor.RED),
                ExpeditionMenu.line(ok ? "Klicken: weiter zum Kampf" : "Noch nicht möglich", ok ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))),
                ok ? (p, c) -> {
                    p.closeInventory();
                    done.run();
                } : null);
        set(45, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Abbrechen", NamedTextColor.RED), List.of()), (p, c) -> p.closeInventory());
        if (pg > 0) {
            set(47, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Vorherige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new TeamPickMenu(plugin, p, min, max, pg - 1, done).open(p));
        }
        if (pg < pages - 1) {
            set(51, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Nächste Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new TeamPickMenu(plugin, p, min, max, pg + 1, done).open(p));
        }
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
