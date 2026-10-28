package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CompanionRoster;
import de.coinshop.CompanionTrips;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Begleiterreisen: Ziele (nach Meilensteinen freigeschaltet), Status der laufenden Reise, Beute abholen. */
public final class ExpeditionTripMenu extends Menu {

    static final int[] SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32};

    public ExpeditionTripMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, Component.text("Begleiterreisen"));
        ExpeditionManager ex = plugin.expeditions();
        CompanionTrips trips = ex.trips();
        UUID uuid = viewer.getUniqueId();

        // Status oben in der Mitte
        List<Component> status = new ArrayList<>();
        CompanionRoster.Trip trip = trips.trip(uuid);
        if (trip != null && System.currentTimeMillis() >= trip.end) {
            CompanionTrips.Outcome o = trips.outcome(uuid, trip);
            status.add(ExpeditionMenu.line("Ziel: " + trips.name(trip.dest) + ", Dauer " + trip.hours + " Std.", NamedTextColor.YELLOW));
            status.add(ExpeditionMenu.line("Mitgereist: " + o.sent() + " Begleiter (Stärke " + Math.round(o.power()) + " von " + Math.round(o.required()) + ")", NamedTextColor.GRAY));
            status.add(ExpeditionMenu.line("Besiegte Kreaturen: " + o.kills(), NamedTextColor.WHITE));
            status.add(ExpeditionMenu.line("CoinMobs erlegt: " + o.coinMobs() + (o.coinMobs() > 0 ? " (+" + o.coinMobCoins() + " Coins)" : ""), o.coinMobs() > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            status.add(ExpeditionMenu.line("Loot-Mobs erlegt: " + o.lootMobs() + (o.lootMobs() > 0 ? " (Belohnung wird beim Abholen gewürfelt)" : ""), o.lootMobs() > 0 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY));
            List<String> hurt = trips.hurtNames(o);
            status.add(ExpeditionMenu.line(hurt.isEmpty() ? "Verletzte: keine" : "Verletzte (" + hurt.size() + "): " + String.join(", ", hurt.size() > 6 ? hurt.subList(0, 6) : hurt) + (hurt.size() > 6 ? " ..." : ""), hurt.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED));
            status.add(ExpeditionMenu.line("Erfahrung: +" + o.xpEach() + " Kills für jeden Mitgereisten", NamedTextColor.AQUA));
            status.add(ExpeditionMenu.line("Grundbeute: " + o.baseCoins() + " Coins", NamedTextColor.GRAY));
            status.add(ExpeditionMenu.line("Beute gesamt: " + o.totalCoins() + " Coins", NamedTextColor.GOLD));
            status.add(ExpeditionMenu.line("Klicken zum Abholen", NamedTextColor.GREEN));
            set(4, ItemUtil.icon(Material.CHEST, ExpeditionMenu.line("Beute abholen", NamedTextColor.GREEN), status), (p, c) -> {
                trips.collect(p);
                new ExpeditionTripMenu(plugin, p).open(p);
            });
        } else if (trip != null) {
            status.add(ExpeditionMenu.line(trip.sent() + " Begleiter sind unterwegs nach " + trips.name(trip.dest) + ".", NamedTextColor.YELLOW));
            status.add(ExpeditionMenu.line("Zurück in " + trips.timeText(trip.end - System.currentTimeMillis()) + " (Dauer " + trip.hours + " Std.).", NamedTextColor.GRAY));
            set(4, ItemUtil.icon(Material.CLOCK, ExpeditionMenu.line("Reise läuft", NamedTextColor.YELLOW), status), null);
        } else {
            status.add(ExpeditionMenu.line("Wähle ein Ziel und die Dauer. Unterwegs sind die", NamedTextColor.GRAY));
            status.add(ExpeditionMenu.line("Begleiter nicht in deinen Leveln dabei.", NamedTextColor.GRAY));
            if (trips.totalInjured(uuid) > 0) {
                status.add(ExpeditionMenu.line(trips.totalInjured(uuid) + " Begleiter sind verletzt.", NamedTextColor.RED));
            }
            set(4, ItemUtil.icon(Material.COMPASS, ExpeditionMenu.line("Keine Reise unterwegs", NamedTextColor.GOLD), status), null);
        }
        boolean active = trip != null;

        for (int d = 0; d < trips.destinations() && d < SLOTS.length; d++) {
            int dest = d;
            int milestone = trips.milestone(d);
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line("Meilenstein: Boss-Level " + milestone + " schaffen", NamedTextColor.YELLOW));
            lore.add(ExpeditionMenu.line("Gegner-Typ: " + trips.destType(d).label, trips.destType(d).color));
            if (!trips.unlocked(uuid, d)) {
                lore.add(ExpeditionMenu.line("Gesperrt: dein höchstes geschafftes Level ist " + trips.cleared(uuid) + ".", NamedTextColor.RED));
                set(SLOTS[d], ItemUtil.icon(Material.GRAY_DYE, ExpeditionMenu.line(trips.name(d), NamedTextColor.DARK_GRAY), lore), null);
                continue;
            }
            lore.add(ExpeditionMenu.line("Beute bei voller Stärke und 24 Std.: etwa " + trips.potential(uuid, milestone) * Math.round(plugin.getConfig().getDouble("expedition.trip.yield-percent", 10)) / 100 + " Coins", NamedTextColor.GOLD));
            lore.add(ExpeditionMenu.line("Nötige Stärke: " + Math.round(trips.required(milestone)) + " (deine: " + Math.round(availablePower(ex, uuid, d)) + ")", NamedTextColor.GRAY));
            lore.add(ExpeditionMenu.line("Unterwegs können Coins- und Loot-Mobs auftauchen.", NamedTextColor.DARK_GRAY));
            if (!active) {
                lore.add(ExpeditionMenu.line("Klicken zum Auswählen", NamedTextColor.GREEN));
            }
            set(SLOTS[d], ItemUtil.icon(trips.icon(d), ExpeditionMenu.line(trips.name(d), NamedTextColor.GOLD), lore), active ? null
                    : (p, c) -> ExpeditionCopyMenu.forTrip(plugin, p, dest).open(p));
        }
        set(45, backButton(), (p, c) -> new ExpeditionCompanionHubMenu(plugin, p).open(p));
        fillEmpty();
    }

    private static double availablePower(ExpeditionManager ex, UUID uuid, int dest) {
        List<ExpeditionManager.Copy> free = new ArrayList<>();
        for (ExpeditionManager.Copy k : ex.allCopies(uuid)) {
            if (ex.busyReason(uuid, k.companion(), k.index()) == null) {
                free.add(k);
            }
        }
        return ex.trips().select(uuid, dest, free).power();
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
