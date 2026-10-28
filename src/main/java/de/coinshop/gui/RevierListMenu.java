package de.coinshop.gui;

import de.coinshop.BlackMarketManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Vorschau vor dem Einstellen im Revier: Du wählst die Menge (auch ganze Stapel) und siehst vorher,
 * für wie viel der Händler es kauft, wie hoch die Einstellgebühr ist und wie wahrscheinlich ein Kauf ist.
 */
public final class RevierListMenu extends Menu {

    private static Component t(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public RevierListMenu(CoinShopPlugin plugin, Player viewer, int revier, ItemStack template, int wanted) {
        super(plugin, 3, Component.text("Einstellen: " + plugin.reviere().name(revier)));
        BlackMarketManager market = plugin.blackMarket();
        ItemStack one = template.clone();
        one.setAmount(1);
        int have = 0;
        for (ItemStack stack : viewer.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(one)) {
                have += stack.getAmount();
            }
        }
        int max = Math.max(1, Math.min(have, one.getMaxStackSize()));
        final int amount = Math.max(1, Math.min(wanted, max));

        final int haveTotal = have;
        ItemStack part = one.clone();
        part.setAmount(amount);
        long price = market.sellPrice(part);
        int percent = Math.max(0, plugin.getConfig().getInt("revier.list-fee-percent", 10));
        long fee = market.feeFor(viewer.getUniqueId(), price, percent);
        int chance = market.sellBuyChance(viewer.getUniqueId(), revier);

        ItemStack shown = part.clone();
        shown.editMeta(meta -> {
            java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
            List<Component> old = meta.lore();
            if (old != null) {
                lore.addAll(old);
            }
            lore.add(t("Menge: " + amount + " von " + haveTotal + " im Inventar", NamedTextColor.GRAY));
            meta.lore(lore);
        });
        set(13, shown, null);

        set(4, ItemUtil.icon(Material.GOLD_INGOT, t("Preis, wenn der Händler es kauft", NamedTextColor.GOLD),
                List.of(t(price + " Coins fuer " + amount + " Stueck", NamedTextColor.YELLOW),
                        t("Einstellgebuehr (jetzt faellig): " + fee + " Coins", NamedTextColor.GRAY),
                        t("Kaufchance pro Tag: " + chance + " %", NamedTextColor.GRAY),
                        t("Der Preis ist fest und nicht verhandelbar.", NamedTextColor.DARK_GRAY))), null);

        set(10, ItemUtil.icon(Material.RED_CONCRETE, t("Weniger (-1)", NamedTextColor.RED),
                List.of(t("Shift-Klick: -8", NamedTextColor.GRAY))), (p, c) ->
                new RevierListMenu(plugin, p, revier, template, amount - (c.isShiftClick() ? 8 : 1)).open(p));
        set(11, ItemUtil.icon(Material.ORANGE_CONCRETE, t("Nur 1 Stueck", NamedTextColor.GOLD), List.of()), (p, c) ->
                new RevierListMenu(plugin, p, revier, template, 1).open(p));
        set(15, ItemUtil.icon(Material.YELLOW_CONCRETE, t("Ganzer Stapel", NamedTextColor.YELLOW),
                List.of(t("Maximal " + max + " Stueck", NamedTextColor.GRAY))), (p, c) ->
                new RevierListMenu(plugin, p, revier, template, max).open(p));
        set(16, ItemUtil.icon(Material.LIME_CONCRETE, t("Mehr (+1)", NamedTextColor.GREEN),
                List.of(t("Shift-Klick: +8", NamedTextColor.GRAY))), (p, c) ->
                new RevierListMenu(plugin, p, revier, template, amount + (c.isShiftClick() ? 8 : 1)).open(p));

        set(22, ItemUtil.icon(Material.EMERALD_BLOCK, t("Jetzt einstellen", NamedTextColor.GREEN),
                List.of(t(amount + "x fuer " + price + " Coins (Gebuehr " + fee + ")", NamedTextColor.GRAY))), (p, c) -> {
            ItemStack toList = one.clone();
            toList.setAmount(amount);
            if (!p.getInventory().containsAtLeast(one, amount)) {
                p.sendMessage(Component.text("Du hast das nicht mehr in dieser Menge.", NamedTextColor.RED));
                new RevierMenu(plugin, p, revier).open(p);
                return;
            }
            if (plugin.reviere().list(p, revier, toList)) {
                p.getInventory().removeItem(toList);
            }
            new RevierMenu(plugin, p, revier).open(p);
        });
        set(18, backButton(), (p, c) -> new RevierMenu(plugin, p, revier).open(p));
        fillEmpty();
    }
}
