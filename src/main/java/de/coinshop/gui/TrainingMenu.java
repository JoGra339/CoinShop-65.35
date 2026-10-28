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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Trainingsplätze: Begleiter schicken, Ergebnis abholen, Plätze kaufen. */
public final class TrainingMenu extends Menu {

    private static final int[] SLOTS = {11, 12, 13, 14, 15};

    public TrainingMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Training"));
        ExpeditionManager ex = plugin.expeditions();
        TrainingManager tr = ex.training();
        UUID uuid = viewer.getUniqueId();
        tr.clean(uuid);
        List<TrainingManager.Slot> used = tr.slots(uuid);
        int count = tr.slotCount(uuid);
        set(4, ItemUtil.icon(Material.PAPER, ExpeditionMenu.line("Trainingsplätze: " + used.size() + " von " + count + " belegt", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Ein Begleiter sammelt pro Stunde Erfahrung (bei hohem Level weniger).", NamedTextColor.GRAY),
                ExpeditionMenu.line("Er ist in der Zeit nicht für Level verfügbar.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Das Ergebnis holst du hier ab (auch vorzeitig, dann anteilig).", NamedTextColor.GRAY))), null);
        for (int i = 0; i < count && i < SLOTS.length; i++) {
            if (i < used.size()) {
                TrainingManager.Slot s = used.get(i);
                Copy k = s.copy();
                if (k == null) {
                    continue;
                }
                boolean done = tr.finished(s);
                long gain = tr.earned(uuid, s);
                List<Component> lore = new ArrayList<>();
                lore.addAll(CompanionInfo.lines(plugin, uuid, k));
                lore.add(ExpeditionMenu.line(exercise(ex, k) + " (" + s.hours() + " Std.)", NamedTextColor.GRAY));
                lore.add(ExpeditionMenu.line(done ? "Fertig!" : "Noch " + ItemUtil.formatDuration(s.end() - System.currentTimeMillis()),
                        done ? NamedTextColor.GREEN : NamedTextColor.AQUA));
                lore.add(ExpeditionMenu.line("Erfahrung bisher: " + gain, NamedTextColor.GOLD));
                lore.add(ExpeditionMenu.line(done ? "Klicken: abholen" : "Klicken: jetzt abholen (anteilig)", NamedTextColor.GREEN));
                ItemStack it = ItemUtil.icon(k.companion().icon, ExpeditionMenu.line(ex.trips().copyName(k.companion(), k.index()), NamedTextColor.GOLD), lore);
                if (done) {
                    it.editMeta(m -> m.setEnchantmentGlintOverride(true));
                }
                set(SLOTS[i], it, (p, c) -> {
                    tr.collect(p, s);
                    new TrainingMenu(plugin, p).open(p);
                });
            } else {
                set(SLOTS[i], ItemUtil.icon(Material.LIME_STAINED_GLASS_PANE, ExpeditionMenu.line("Freier Trainingsplatz", NamedTextColor.GREEN), List.of(
                        ExpeditionMenu.line("Klicken: Begleiter zum Training schicken", NamedTextColor.GRAY))),
                        (p, c) -> new TrainingPickMenu(plugin, p, 0, null).open(p));
            }
        }
        if (count < tr.maxSlots()) {
            long price = tr.slotPrice(uuid);
            set(22, ItemUtil.icon(Material.EMERALD, ExpeditionMenu.line("Weiteren Trainingsplatz kaufen", NamedTextColor.GOLD), List.of(
                    ExpeditionMenu.line("Preis: " + price + " Coins (jeder Platz doppelt so teuer)", NamedTextColor.YELLOW),
                    ExpeditionMenu.line("Klicken: kaufen", NamedTextColor.GREEN))), (p, c) -> {
                tr.buySlot(p);
                new TrainingMenu(plugin, p).open(p);
            });
        }
        set(27, backButton(), (p, c) -> new ExpeditionCompanionHubMenu(plugin, p).open(p));
        set(35, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    /** Unterstützer üben Heilen, Verteidiger Schildübungen, Angreifer Kampfübungen. */
    static String exercise(ExpeditionManager ex, Copy k) {
        if (ex.isSupporter(k.companion())) {
            return "Heilübungen";
        }
        return ex.isDefender(k.companion()) ? "Schildübungen" : "Kampfübungen";
    }
}
