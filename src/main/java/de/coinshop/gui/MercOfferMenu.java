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

/** Eigenen Begleiter als Söldner anbieten: erst wählen, dann den Preis. */
public final class MercOfferMenu extends Menu {

    private static final int PER_PAGE = 36;
    private static final long[] PRESETS = {100, 500, 2000, 10000, 50000};

    public MercOfferMenu(CoinShopPlugin plugin, Player viewer, int page, Copy chosen) {
        super(plugin, 6, Component.text(chosen == null ? "Söldner anbieten: Begleiter wählen" : "Söldner anbieten: Preis wählen"));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();
        if (chosen != null) {
            set(13, ItemUtil.icon(chosen.companion().icon, ExpeditionMenu.line(ex.trips().copyName(chosen.companion(), chosen.index()), NamedTextColor.GOLD), List.of(
                    ExpeditionMenu.line("Level " + ex.copyLevel(uuid, chosen.companion(), chosen.index()), NamedTextColor.YELLOW))), null);
            int[] slots = {20, 21, 22, 23, 24};
            for (int i = 0; i < PRESETS.length; i++) {
                long price = PRESETS[i];
                set(slots[i], ItemUtil.icon(Material.GOLD_INGOT, ExpeditionMenu.line(price + " Coins", NamedTextColor.GOLD), List.of(
                        ExpeditionMenu.line("Klicken: für diesen Preis anbieten", NamedTextColor.GREEN))), (p, c) -> {
                    ex.mercs().offer(p, chosen, price);
                    new MercMenu(plugin, p, 0).open(p);
                });
            }
            set(31, ItemUtil.icon(Material.WRITABLE_BOOK, ExpeditionMenu.line("Eigener Preis", NamedTextColor.GOLD), List.of(
                    ExpeditionMenu.line("Klicken, dann den Preis in den Chat schreiben.", NamedTextColor.GRAY),
                    ExpeditionMenu.line("„abbrechen“ bricht ab.", NamedTextColor.DARK_GRAY))), (p, c) -> {
                p.closeInventory();
                plugin.messages().send(p, "ex-merc-ask");
                plugin.prompts().start(p, text -> {
                    if (text.equalsIgnoreCase("abbrechen")) {
                        plugin.messages().send(p, "ask-cancelled");
                        return;
                    }
                    try {
                        ex.mercs().offer(p, chosen, Long.parseLong(text.replace(".", "").replace(",", "").trim()));
                    } catch (NumberFormatException e) {
                        plugin.messages().send(p, "ask-invalid");
                    }
                    new MercMenu(plugin, p, 0).open(p);
                });
            });
            set(45, backButton(), (p, c) -> new MercOfferMenu(plugin, p, page, null).open(p));
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
            List<net.kyori.adventure.text.Component> ml = new java.util.ArrayList<>(CompanionInfo.lines(plugin, uuid, k));
            ml.add(ExpeditionMenu.line("Klicken: wählen", NamedTextColor.GREEN));
            set(i, ItemUtil.icon(k.companion().icon, ExpeditionMenu.line(ex.trips().copyName(k.companion(), k.index()), NamedTextColor.GOLD), ml),
                    (p, c) -> new MercOfferMenu(plugin, p, cur, k).open(p));
        }
        if (cur > 0) {
            set(48, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Vorige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new MercOfferMenu(plugin, p, cur - 1, null).open(p));
        }
        if (cur < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Nächste Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new MercOfferMenu(plugin, p, cur + 1, null).open(p));
        }
        set(45, backButton(), (p, c) -> new MercMenu(plugin, p, 0).open(p));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
