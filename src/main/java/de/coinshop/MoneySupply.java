package de.coinshop;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Geldumlauf: wie viele Coins gerade auf allen Spielerkonten liegen.
 *
 * Alle zehn Minuten wird die Gesamtsumme gemerkt (der letzte Wert eines Tages zählt), so lässt sich
 * der Verlauf pro Tag anzeigen. Das Casino-Konto wird getrennt ausgewiesen, weil es kein Spielerkonto ist.
 */
public final class MoneySupply {

    /** Momentaufnahme aller Kontostände. */
    public record Stats(long total, int accounts, long average, long median, List<Database.Account> top) { }

    /** Ein Tag im Verlauf. */
    public record Day(long epochDay, long total) { }

    private final CoinShopPlugin plugin;

    public MoneySupply(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private long today() {
        return ZonedDateTime.now(plugin.serverShop().zone()).toLocalDate().toEpochDay();
    }

    public Stats stats() {
        List<Database.Account> all = new ArrayList<>(plugin.database().allAccounts());
        long total = 0;
        for (Database.Account a : all) {
            total += a.coins();
        }
        int n = all.size();
        long average = n == 0 ? 0 : total / n;
        List<Long> sorted = new ArrayList<>();
        for (Database.Account a : all) {
            sorted.add(a.coins());
        }
        sorted.sort(Comparator.naturalOrder());
        long median = 0;
        if (n > 0) {
            median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
        }
        all.sort(Comparator.comparingLong(Database.Account::coins).reversed());
        List<Database.Account> top = new ArrayList<>(all.subList(0, Math.min(5, all.size())));
        return new Stats(total, n, average, median, top);
    }

    /** Casino-Konto (liegt nicht auf Spielerkonten). */
    public long casinoAccount() {
        return plugin.database().getShopAccount();
    }

    /** Merkt die aktuelle Gesamtsumme für den heutigen Tag. */
    public void snapshot() {
        try {
            plugin.database().setMetaLong("supply_" + today(), stats().total());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Geldumlauf-Messung fehlgeschlagen: " + e);
        }
    }

    /** Die letzten Tage (älteste zuerst); Tage ohne Messung fehlen. */
    public List<Day> history(int days) {
        long today = today();
        List<Day> out = new ArrayList<>();
        for (long d = today - days + 1; d <= today; d++) {
            long v = plugin.database().getMetaLong("supply_" + d, -1L);
            if (v >= 0) {
                out.add(new Day(d, v));
            }
        }
        return out;
    }

    public void start() {
        long ten = 20L * 60 * 10;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::snapshot, 200L, ten);
    }
}
