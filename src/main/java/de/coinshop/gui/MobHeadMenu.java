package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.MobHeadSale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Mobköpfe verkaufen: ein Klick verkauft alle Köpfe dieser Art aus deinem Inventar. */
public final class MobHeadMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};

    private static Component t(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    private static String name(Material m) {
        return switch (m) {
            case ZOMBIE_HEAD -> "Zombiekopf";
            case SKELETON_SKULL -> "Skelettschädel";
            case CREEPER_HEAD -> "Creeperkopf";
            case PIGLIN_HEAD -> "Piglinkopf";
            case WITHER_SKELETON_SKULL -> "Wither-Skelett-Schädel";
            case DRAGON_HEAD -> "Drachenkopf";
            default -> m.name();
        };
    }

    public MobHeadMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Mobköpfe verkaufen"));
        MobHeadSale sale = plugin.mobHeads();
        long all = 0;
        int left = sale.remainingToday(viewer);
        int limit = sale.dailyLimit();
        for (int i = 0; i < MobHeadSale.HEADS.length; i++) {
            Material head = MobHeadSale.HEADS[i];
            int have = sale.count(viewer, head);
            long price = sale.price(head);
            int sellable = Math.min(have, left);
            set(SLOTS[i], ItemUtil.icon(head, t(name(head), NamedTextColor.GOLD), List.of(
                    t(price + " Coins pro Stück", NamedTextColor.YELLOW),
                    t("Im Inventar: " + have, NamedTextColor.GRAY),
                    t(sellable > 0 ? "Klicken: " + sellable + " verkaufen (" + (sellable * price) + " Coins)"
                            : (have > 0 ? "Heute ist das Limit erreicht." : "Du hast keinen."),
                            sellable > 0 ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))),
                    (p, click) -> {
                        long got = plugin.mobHeads().sell(p, head);
                        if (got > 0) {
                            p.sendMessage(Component.text("Verkauft für " + got + " Coins.", NamedTextColor.GOLD));
                        }
                        new MobHeadMenu(plugin, p).open(p);
                    });
        }
        // Die teuersten Köpfe werden zuerst verkauft, bis das Tageslimit erreicht ist
        int rest = left;
        java.util.List<Material> byPrice = new java.util.ArrayList<>(java.util.List.of(MobHeadSale.HEADS));
        byPrice.sort((a, b) -> Long.compare(sale.price(b), sale.price(a)));
        for (Material m : byPrice) {
            int n = Math.min(sale.count(viewer, m), rest);
            all += n * sale.price(m);
            rest -= n;
        }
        set(4, ItemUtil.icon(Material.CLOCK, t("Heute noch " + left + " von " + limit, NamedTextColor.YELLOW), List.of(
                t("So viele Mobköpfe darfst du pro Tag verkaufen.", NamedTextColor.GRAY),
                t("Das Limit gilt für alle Sorten zusammen.", NamedTextColor.GRAY))), null);
        set(22, ItemUtil.icon(Material.GOLD_INGOT, t("Alle Mobköpfe verkaufen", NamedTextColor.GOLD), List.of(
                t(all > 0 ? "Zusammen " + all + " Coins (teuerste zuerst)" : "Nichts zu verkaufen.", NamedTextColor.YELLOW),
                t("Spielerköpfe werden nicht angenommen.", NamedTextColor.GRAY))),
                (p, click) -> {
                    long got = plugin.mobHeads().sell(p, null);
                    if (got > 0) {
                        p.sendMessage(Component.text("Verkauft für " + got + " Coins.", NamedTextColor.GOLD));
                    }
                    new MobHeadMenu(plugin, p).open(p);
                });
        set(27, backButton(), (p, click) -> new BountyMenu(plugin, p).open(p));
        set(35, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
