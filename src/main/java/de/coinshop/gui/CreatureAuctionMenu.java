package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CreatureAuction;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sklavenhandel: alle laufenden Versteigerungen (Kreatur des Tages und Angebote der Spieler) und der Knopf zum Verkaufen. */
public final class CreatureAuctionMenu extends Menu {

    private static final int PAGE = 45;

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    public CreatureAuctionMenu(CoinShopPlugin plugin, Player viewer) {
        this(plugin, viewer, 0);
    }

    public CreatureAuctionMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, Component.text("Sklavenhandel"));
        CreatureAuction auction = plugin.creatureAuction();
        UUID me = viewer.getUniqueId();
        List<CreatureAuction.Lot> lots = auction.lots();
        int pages = Math.max(1, (lots.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        if (lots.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, line("Heute wird nichts versteigert", NamedTextColor.RED), List.of()), null);
        }
        for (int i = 0; i < PAGE && pg * PAGE + i < lots.size(); i++) {
            CreatureAuction.Lot lot = lots.get(pg * PAGE + i);
            long min = auction.minNextBid(lot);
            long base = lot.bidder == null ? lot.minBid : lot.bid;
            long plus25 = Math.max(min, Math.round(base * 1.25));
            long plus100 = Math.max(min, base * 2);
            List<Component> lore = new ArrayList<>();
            lore.add(line(lot.system() ? "Kreatur des Tages (vom Shop)" : "Angebot von " + lot.sellerName,
                    lot.system() ? NamedTextColor.GOLD : NamedTextColor.AQUA));
            net.kyori.adventure.text.format.NamedTextColor rc = de.coinshop.CompanionTraits.Rarity.of(lot.rarity).color;
            lore.add(line("Seltenheit: " + de.coinshop.CompanionTraits.Rarity.of(lot.rarity).label
                    + (lot.generation > 1 ? " (Generation " + lot.generation + ")" : ""), rc));
            lore.addAll(CompanionInfo.species(plugin, lot.companion)); // Typ, Rolle und Fähigkeit mit Ziel
            lore.add(line("Eigenschaften: " + auction.traitText(lot), NamedTextColor.RED));
            if (lot.bonusBox > 0) {
                lore.add(line("Zugabe für den Gewinner: Mystery Box Stufe " + lot.bonusBox, NamedTextColor.LIGHT_PURPLE));
            }
            if (lot.kills > 0) {
                lore.add(line("Erfahrung: " + lot.kills + " Kills", NamedTextColor.YELLOW));
            }
            lore.add(line(lot.bidder == null ? "Noch kein Gebot, Start bei " + lot.minBid + " Coins"
                    : "Höchstgebot: " + lot.bid + " Coins von " + lot.bidderName, NamedTextColor.GOLD));
            lore.add(line("Endet in " + ItemUtil.formatDuration(auction.millisLeft()) + " (beim Shop-Wechsel)", NamedTextColor.YELLOW));
            if (me.equals(lot.seller)) {
                lore.add(line("Das ist dein eigenes Angebot.", NamedTextColor.GRAY));
                if (lot.bidder == null) {
                    lore.add(line("Ohne Gebot bekommst du sie am Ende zurück.", NamedTextColor.GRAY));
                }
            } else {
                lore.add(line("Linksklick: " + min + " Coins bieten", NamedTextColor.GREEN));
                lore.add(line("Rechtsklick: " + plus25 + " Coins (+25 %)", NamedTextColor.GOLD));
                lore.add(line("Shift-Klick: " + plus100 + " Coins (doppelt)", NamedTextColor.AQUA));
            }
            final long lotId = lot.id;
            final int thisPage = pg;
            set(i, ItemUtil.icon(lot.companion.icon, line(lot.companion.label, rc), lore), (p, c) -> {
                if (!me.equals(lot.seller)) {
                    CreatureAuction.Lot fresh = auction.byId(lotId);
                    if (fresh != null) {
                        long m = auction.minNextBid(fresh);
                        long b = fresh.bidder == null ? fresh.minBid : fresh.bid;
                        long amount = c == ClickType.RIGHT ? Math.max(m, Math.round(b * 1.25))
                                : c.isShiftClick() ? Math.max(m, b * 2) : m;
                        auction.bid(p, lotId, amount);
                    } else {
                        plugin.messages().send(p, "beast-gone");
                    }
                }
                new CreatureAuctionMenu(plugin, p, thisPage).open(p);
            });
        }
        set(45, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        if (pg > 0) {
            set(48, ItemUtil.icon(Material.ARROW, line("Vorherige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new CreatureAuctionMenu(plugin, p, pg - 1).open(p));
        }
        set(49, ItemUtil.icon(Material.LEAD, line("Eigenen Begleiter verkaufen", NamedTextColor.GOLD), List.of(
                line("Begleiter, die du nicht mehr willst:", NamedTextColor.GRAY),
                line("a) an andere Spieler versteigern", NamedTextColor.GRAY),
                line("b) an den Shop verkaufen (halber Wert)", NamedTextColor.GRAY),
                line("Deine Angebote: " + auction.lotsOf(me) + " von " + auction.maxLots(), NamedTextColor.YELLOW),
                line("Klicken zum Auswählen", NamedTextColor.GREEN))),
                (p, c) -> new ExpeditionCopyMenu(plugin, p, ExpeditionCopyMenu.Mode.SELL).open(p));
        if (pg < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, line("Nächste Seite (" + (pg + 2) + "/" + pages + ")", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new CreatureAuctionMenu(plugin, p, pg + 1).open(p));
        }
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
