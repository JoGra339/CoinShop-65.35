package de.coinshop.gui;

import de.coinshop.ArenaManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.CombatSim;
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

/** Begleiter in der eigenen Arena stationieren oder abziehen (1 bis 5, mindestens einer bleibt). */
public final class ArenaStationMenu extends Menu {

    private static final int PER_PAGE = 27;

    public ArenaStationMenu(CoinShopPlugin plugin, Player viewer, int site, int page) {
        super(plugin, 6, Component.text("Verteidiger: " + plugin.expeditions().arenas().siteName(site)));
        ExpeditionManager ex = plugin.expeditions();
        ArenaManager arenas = ex.arenas();
        UUID uuid = viewer.getUniqueId();
        ArenaManager.Arena a = arenas.arena(site);
        set(4, ItemUtil.icon(Material.PAPER, ExpeditionMenu.line("Stationiert: " + a.station.size() + " von " + arenas.maxStation(), NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Oben klicken: abziehen. Unten klicken: stationieren.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Mindestens ein Begleiter muss bleiben.", NamedTextColor.GRAY))), null);
        int[] top = {10, 11, 12, 13, 14};
        List<Copy> placed = arenas.stationed(a);
        for (int i = 0; i < top.length && i < placed.size(); i++) {
            Copy k = placed.get(i);
            set(top[i], item(ex, uuid, k, true), (p, c) -> {
                arenas.toggle(p, site, k);
                new ArenaStationMenu(plugin, p, site, page).open(p);
            });
        }
        List<Copy> candidates = new ArrayList<>();
        for (Copy k : ex.allCopies(uuid)) {
            if (!arenas.isStationedHere(a, k) && ex.busyReason(uuid, k.companion(), k.index()) == null) {
                candidates.add(k);
            }
        }
        candidates.sort((x, y) -> Double.compare(power(ex, uuid, y), power(ex, uuid, x)));
        int pages = Math.max(1, (candidates.size() + PER_PAGE - 1) / PER_PAGE);
        int cur = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = cur * PER_PAGE + i;
            if (idx >= candidates.size()) {
                break;
            }
            Copy k = candidates.get(idx);
            set(18 + i, item(ex, uuid, k, false), (p, c) -> {
                arenas.toggle(p, site, k);
                new ArenaStationMenu(plugin, p, site, cur).open(p);
            });
        }
        if (cur > 0) {
            set(48, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Vorige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new ArenaStationMenu(plugin, p, site, cur - 1).open(p));
        }
        if (cur < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Nächste Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new ArenaStationMenu(plugin, p, site, cur + 1).open(p));
        }
        set(45, backButton(), (p, c) -> new ArenaMenu(plugin, p, site).open(p));
        fillEmpty();
    }

    private static double power(ExpeditionManager ex, UUID uuid, Copy k) {
        CombatSim.Fighter f = CombatSim.of(ex, uuid, k);
        return f.hp() * f.atk();
    }

    private ItemStack item(ExpeditionManager ex, UUID uuid, Copy k, boolean placed) {
        CombatSim.Fighter f = CombatSim.of(ex, uuid, k);
        List<Component> lore = new ArrayList<>();
        lore.addAll(CompanionInfo.lines(plugin, uuid, k));
        lore.add(ExpeditionMenu.line(placed ? "Klicken: abziehen" : "Klicken: stationieren", placed ? NamedTextColor.RED : NamedTextColor.GREEN));
        ItemStack it = ItemUtil.icon(k.companion().icon, ExpeditionMenu.line(f.name(), placed ? NamedTextColor.GREEN : NamedTextColor.GOLD), lore);
        if (placed) {
            it.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        return it;
    }
}
