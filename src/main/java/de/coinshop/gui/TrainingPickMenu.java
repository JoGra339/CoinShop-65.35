package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Copy;
import de.coinshop.ItemUtil;
import de.coinshop.TrainingManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Begleiter fürs Training wählen, danach die Dauer. */
public final class TrainingPickMenu extends Menu {

    private static final int PER_PAGE = 36;

    public TrainingPickMenu(CoinShopPlugin plugin, Player viewer, int page, Copy chosen) {
        super(plugin, 6, Component.text(chosen == null ? "Training: Begleiter wählen" : "Training: Dauer wählen"));
        ExpeditionManager ex = plugin.expeditions();
        TrainingManager tr = ex.training();
        UUID uuid = viewer.getUniqueId();
        if (chosen != null) {
            set(13, ItemUtil.icon(chosen.companion().icon, ExpeditionMenu.line(ex.trips().copyName(chosen.companion(), chosen.index()), NamedTextColor.GOLD), List.of(
                    ExpeditionMenu.line("Level " + ex.copyLevel(uuid, chosen.companion(), chosen.index()), NamedTextColor.YELLOW),
                    ExpeditionMenu.line("Etwa " + String.format(java.util.Locale.ROOT, "%.1f", tr.perHour(uuid, chosen)) + " Erfahrung pro Stunde", NamedTextColor.GRAY),
                    ExpeditionMenu.line("Preis pro Stunde: " + tr.hourPrice(uuid, chosen) + " Coins", NamedTextColor.GRAY))), null);
            int[] slots = {20, 21, 22, 23, 24};
            for (int i = 0; i < TrainingManager.HOURS.length; i++) {
                int h = TrainingManager.HOURS[i];
                if (h > Math.max(1, plugin.getConfig().getInt("expedition.training.max-hours", 24))) {
                    continue;
                }
                long cost = tr.hourPrice(uuid, chosen) * h;
                set(slots[i], ItemUtil.icon(Material.CLOCK, ExpeditionMenu.line(h + (h == 1 ? " Stunde" : " Stunden"), NamedTextColor.GOLD), List.of(
                        ExpeditionMenu.line("Erfahrung: etwa " + Math.round(tr.perHour(uuid, chosen) * h), NamedTextColor.GREEN),
                        ExpeditionMenu.line("Preis: " + cost + " Coins", NamedTextColor.YELLOW),
                        ExpeditionMenu.line("Klicken: Training starten", NamedTextColor.GREEN))), (p, c) -> {
                    tr.start(p, chosen, h);
                    new TrainingMenu(plugin, p).open(p);
                });
            }
            set(45, backButton(), (p, c) -> new TrainingPickMenu(plugin, p, page, null).open(p));
            set(53, balanceIcon(viewer), incomeClick());
            fillEmpty();
            return;
        }
        List<Copy> candidates = new ArrayList<>();
        for (Copy k : ex.allCopies(uuid)) {
            if (ex.busyReason(uuid, k.companion(), k.index()) == null) {
                candidates.add(k);
            }
        }
        candidates.sort((a, b) -> Integer.compare(ex.copyLevel(uuid, b.companion(), b.index()), ex.copyLevel(uuid, a.companion(), a.index())));
        int pages = Math.max(1, (candidates.size() + PER_PAGE - 1) / PER_PAGE);
        int cur = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = cur * PER_PAGE + i;
            if (idx >= candidates.size()) {
                break;
            }
            Copy k = candidates.get(idx);
            List<net.kyori.adventure.text.Component> tl = new java.util.ArrayList<>(CompanionInfo.lines(plugin, uuid, k));
            tl.add(ExpeditionMenu.line(TrainingMenu.exercise(ex, k), NamedTextColor.GRAY));
            tl.add(ExpeditionMenu.line("Klicken: wählen", NamedTextColor.GREEN));
            set(i, ItemUtil.icon(k.companion().icon, ExpeditionMenu.line(ex.trips().copyName(k.companion(), k.index()), NamedTextColor.GOLD), tl),
                    (p, c) -> new TrainingPickMenu(plugin, p, cur, k).open(p));
        }
        if (candidates.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Kein Begleiter frei", NamedTextColor.RED), List.of(
                    ExpeditionMenu.line("Verletzte, ruhende, vermietete oder reisende Begleiter können nicht trainieren.", NamedTextColor.GRAY))), null);
        }
        if (cur > 0) {
            set(48, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Vorige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new TrainingPickMenu(plugin, p, cur - 1, null).open(p));
        }
        if (cur < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Nächste Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new TrainingPickMenu(plugin, p, cur + 1, null).open(p));
        }
        set(45, backButton(), (p, c) -> new TrainingMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
