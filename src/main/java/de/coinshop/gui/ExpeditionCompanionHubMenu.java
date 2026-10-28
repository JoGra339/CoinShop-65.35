package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Begleiter-Hauptmenü: kaufen, Gefolge, Sammlung, Reisen, Großhandel, Zucht, Fusion, Typen und Training. */
public final class ExpeditionCompanionHubMenu extends Menu {

    public ExpeditionCompanionHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Begleiter"));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();

        set(10, ItemUtil.icon(Material.EMERALD, ExpeditionMenu.line("Begleiter kaufen", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line(de.coinshop.ExpeditionManager.Companion.values().length + " Kreaturen zur Auswahl.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Jedes Exemplar ist einzigartig:", NamedTextColor.GRAY),
                ExpeditionMenu.line("Seltenheit, Eigenschaften, eigenes Level.", NamedTextColor.GRAY))),
                (p, c) -> new ExpeditionCompanionShopMenu(plugin, p, 0).open(p));

        int next = ex.nextLevel(uuid);
        List<Component> takeLore = new ArrayList<>(List.of(
                ExpeditionMenu.line("Alle deine Exemplare mit allen Werten.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicke einzeln, wer dich ins Level begleitet.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Du hast " + ex.allCopies(uuid).size() + " Begleiter.", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Plätze: " + ex.slotsUsed(uuid) + " von " + ex.slotsUnlocked(uuid), NamedTextColor.YELLOW),
                ExpeditionMenu.line("Gefolge: " + ex.deckCost(uuid) + " von " + ex.deckLimit(uuid) + " Sternen", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Nächstes Level: höchstens " + ex.companionLimit(uuid) + " (Kreaturen: " + ex.creaturesIn(next) + ")", NamedTextColor.GRAY)));
        List<String> away = ex.unavailableLines(uuid, 6);
        if (!away.isEmpty()) {
            takeLore.add(ExpeditionMenu.line("Gerade nicht verfügbar:", NamedTextColor.RED));
            for (String t : away) {
                takeLore.add(ExpeditionMenu.line(" " + t, NamedTextColor.GRAY));
            }
        }
        set(11, ItemUtil.icon(Material.WRITABLE_BOOK, ExpeditionMenu.line("Meine Begleiter und Gefolge", NamedTextColor.GOLD), takeLore),
                (p, c) -> new ExpeditionCopyMenu(plugin, p, ExpeditionCopyMenu.Mode.TAKE).open(p));

        var trips = ex.trips();
        List<Component> tripLore = new ArrayList<>();
        tripLore.add(ExpeditionMenu.line("Schicke Begleiter selbstständig auf Reisen.", NamedTextColor.GRAY));
        tripLore.add(ExpeditionMenu.line("Du wählst Ziel, Dauer und Mitreisende.", NamedTextColor.GRAY));
        if (trips.finished(uuid)) {
            tripLore.add(ExpeditionMenu.line("Deine Begleiter sind zurück: Beute abholen!", NamedTextColor.GREEN));
        } else if (trips.active(uuid)) {
            tripLore.add(ExpeditionMenu.line("Unterwegs: " + trips.totalAway(uuid) + " Begleiter nach "
                    + trips.name(trips.trip(uuid).dest), NamedTextColor.YELLOW));
        }
        set(13, ItemUtil.icon(Material.FILLED_MAP, ExpeditionMenu.line("Begleiterreisen", NamedTextColor.GOLD), tripLore),
                (p, c) -> new ExpeditionTripMenu(plugin, p).open(p));

        var life = ex.life();
        set(14, ItemUtil.icon(Material.IRON_PICKAXE, ExpeditionMenu.line("Großhandel", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Teile Begleiter Aufträgen im Großhandel zu.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Sie liefern täglich Items - je nach Level", NamedTextColor.GRAY),
                ExpeditionMenu.line("und Eignung für die Branche.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Zugeteilt: " + life.assigned(uuid).size() + " (beliebig viele möglich)", NamedTextColor.YELLOW))),
                (p, c) -> new ExpeditionCopyMenu(plugin, p, ExpeditionCopyMenu.Mode.WHOLESALE).open(p));

        set(15, ItemUtil.icon(Material.EGG, ExpeditionMenu.line("Zucht", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Zwei Eltern bekommen ein Kind, das", NamedTextColor.GRAY),
                ExpeditionMenu.line("Eigenschaften und Erfahrung erbt.", NamedTextColor.GRAY))),
                (p, c) -> new ExpeditionCopyMenu(plugin, p, ExpeditionCopyMenu.Mode.BREED).open(p));

        set(16, ItemUtil.icon(Material.ANVIL, ExpeditionMenu.line("Fusion und Veredelung", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Verschmelze zwei Begleiter zu einem neuen", NamedTextColor.GRAY),
                ExpeditionMenu.line("oder veredle zwei gleiche zu höherer Seltenheit.", NamedTextColor.GRAY))),
                (p, c) -> new ExpeditionCopyMenu(plugin, p, ExpeditionCopyMenu.Mode.FUSION).open(p));

        set(20, ItemUtil.icon(Material.NAME_TAG, ExpeditionMenu.line("Typen und Rezepte", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Wer ist stark gegen wen? Welche Fusionen gibt es?", NamedTextColor.GRAY))),
                (p, c) -> new ExpeditionTypesMenu(plugin, p).open(p));

        set(22, ItemUtil.icon(Material.IRON_BOOTS, ExpeditionMenu.line("Training", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Schicke Begleiter auf Trainingsplätze:", NamedTextColor.GRAY),
                ExpeditionMenu.line("Sie sammeln pro Stunde Erfahrung, auch ohne Level.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Plätze: " + ex.training().slots(uuid).size() + " von " + ex.training().slotCount(uuid) + " belegt", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (p, c) -> new TrainingMenu(plugin, p).open(p));

        set(24, ItemUtil.icon(Material.LEAD, ExpeditionMenu.line("Begleiter-Platz freikaufen", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Freigekauft: " + ex.slotsUnlocked(uuid) + " (unbegrenzt)", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Mehr Plätze = mehr Begleiter, mehr Gefolge, mehr Zuteilungen im Großhandel.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Nächster Platz: " + ex.slotPrice(uuid) + " Coins", NamedTextColor.GOLD),
                ExpeditionMenu.line("Klicken zum Freikaufen", NamedTextColor.GREEN))), (p, c) -> {
            ex.buySlot(p);
            new ExpeditionCompanionHubMenu(plugin, p).open(p);
        });

        set(27, backButton(), (p, c) -> new ExpeditionMenu(plugin, p).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
