package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CompanionTraits;
import de.coinshop.CreatureAuction;
import de.coinshop.ExpeditionManager.Copy;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Entscheidung für einen eigenen Begleiter: an den Shop verkaufen oder versteigern (mit gewähltem Startgebot). */
public final class CreatureSellMenu extends Menu {

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public CreatureSellMenu(CoinShopPlugin plugin, Player viewer, Copy k) {
        super(plugin, 3, Component.text("Begleiter verkaufen"));
        CreatureAuction auction = plugin.creatureAuction();
        UUID uuid = viewer.getUniqueId();
        long value = auction.valueOf(uuid, k);
        long shop = auction.shopPrice(uuid, k);
        CompanionTraits.Rarity rar = CompanionTraits.Rarity.of(plugin.expeditions().info(uuid, k.companion(), k.index()).rarity);
        String name = plugin.expeditions().trips().copyName(k.companion(), k.index());
        List<Component> info = new java.util.ArrayList<>(CompanionInfo.lines(plugin, uuid, k));
        info.add(line("Wert: etwa " + value + " Coins", NamedTextColor.GOLD));
        set(4, ItemUtil.icon(k.companion().icon, line(name, rar.color), info), null);

        set(11, ItemUtil.icon(Material.GOLD_INGOT, line("An den Shop verkaufen: " + shop + " Coins", NamedTextColor.GOLD), List.of(
                line("Der Shop zahlt die Hälfte des Werts.", NamedTextColor.GRAY),
                line("Sofort, aber der Begleiter ist weg.", NamedTextColor.GRAY),
                line("Klicken zum Verkaufen", NamedTextColor.GREEN))), (p, c) -> {
            auction.sellToShop(p, k);
            new CreatureAuctionMenu(plugin, p).open(p);
        });

        int[] percents = {50, 100, 150};
        int[] slots = {14, 15, 16};
        for (int i = 0; i < percents.length; i++) {
            long start = Math.max(1, Math.round(value * percents[i] / 100.0));
            set(slots[i], ItemUtil.icon(Material.EMERALD, line("Versteigern ab " + start + " Coins (" + percents[i] + " % vom Wert)", NamedTextColor.GREEN), List.of(
                    line("Andere Spieler bieten bis zum Shop-Wechsel.", NamedTextColor.GRAY),
                    line("Das Höchstgebot geht an dich", NamedTextColor.GRAY),
                    line("(abzüglich " + plugin.getConfig().getInt("creature-auction.fee-percent", 5) + " % Gebühr).", NamedTextColor.GRAY),
                    line("Ohne Gebot bekommst du ihn zurück.", NamedTextColor.GRAY),
                    line("Klicken zum Einstellen", NamedTextColor.GREEN))), (p, c) -> {
                auction.listCopy(p, k, start);
                new CreatureAuctionMenu(plugin, p).open(p);
            });
        }
        set(18, backButton(), (p, c) -> new ExpeditionCopyMenu(plugin, p, ExpeditionCopyMenu.Mode.SELL).open(p));
        fillEmpty();
    }
}
