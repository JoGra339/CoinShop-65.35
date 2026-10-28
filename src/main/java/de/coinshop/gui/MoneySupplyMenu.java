package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.MoneySupply;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Geldumlauf für alle: Gesamtsumme, Durchschnitt, Median, Verlauf pro Tag und die reichsten Spieler. */
public final class MoneySupplyMenu extends Menu {

    private static Component t(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    private static String num(long value) {
        return String.format(java.util.Locale.GERMANY, "%,d", value);
    }

    public MoneySupplyMenu(CoinShopPlugin plugin, Player viewer) {
        this(plugin, viewer, false);
    }

    /** fromArea: true, wenn das Menü aus dem Hauptmenü (Info und Konto) geöffnet wurde (dann führt Zurück wieder dorthin). */
    public MoneySupplyMenu(CoinShopPlugin plugin, Player viewer, boolean fromArea) {
        super(plugin, 3, Component.text("Geldumlauf"));
        MoneySupply supply = plugin.moneySupply();
        MoneySupply.Stats s = supply.stats();

        set(4, ItemUtil.icon(Material.GOLD_BLOCK, t("Coins im Umlauf: " + num(s.total()), NamedTextColor.GOLD),
                List.of(t("Summe aller Spielerkonten", NamedTextColor.GRAY),
                        t(s.accounts() + " Konten", NamedTextColor.GRAY),
                        t("Casino-Konto extra: " + num(supply.casinoAccount()), NamedTextColor.GRAY))), null);

        set(11, ItemUtil.icon(Material.SUNFLOWER, t("Durchschnitt pro Konto", NamedTextColor.YELLOW),
                List.of(t(num(s.average()) + " Coins", NamedTextColor.WHITE))), null);
        set(12, ItemUtil.icon(Material.CLOCK, t("Median (die Mitte)", NamedTextColor.YELLOW),
                List.of(t(num(s.median()) + " Coins", NamedTextColor.WHITE),
                        t("Die Hälfte der Spieler hat weniger.", NamedTextColor.GRAY))), null);

        List<Component> hist = new ArrayList<>();
        List<MoneySupply.Day> days = supply.history(7);
        if (days.isEmpty()) {
            hist.add(t("Noch keine Messungen.", NamedTextColor.GRAY));
        }
        long previous = -1;
        for (MoneySupply.Day d : days) {
            LocalDate date = LocalDate.ofEpochDay(d.epochDay());
            String diff = "";
            if (previous >= 0) {
                long delta = d.total() - previous;
                diff = "  (" + (delta >= 0 ? "+" : "") + num(delta) + ")";
            }
            hist.add(t(date.getDayOfMonth() + "." + date.getMonthValue() + ".: " + num(d.total()) + diff,
                    NamedTextColor.WHITE));
            previous = d.total();
        }
        set(14, ItemUtil.icon(Material.MAP, t("Verlauf der letzten 7 Tage", NamedTextColor.YELLOW), hist), null);

        List<Component> top = new ArrayList<>();
        int rank = 1;
        for (var a : s.top()) {
            top.add(t(rank + ". " + a.name() + ": " + num(a.coins()), NamedTextColor.WHITE));
            rank++;
        }
        if (top.isEmpty()) {
            top.add(t("Noch keine Konten.", NamedTextColor.GRAY));
        }
        set(15, ItemUtil.icon(Material.DIAMOND, t("Die reichsten Spieler", NamedTextColor.AQUA), top), null);

        set(22, backButton(), (player, click) -> {
            if (fromArea) {
                new AreaMenu(plugin, player, AreaMenu.Area.INFO).open(player);
            } else {
                player.closeInventory();
            }
        });
        fillEmpty();
    }
}
