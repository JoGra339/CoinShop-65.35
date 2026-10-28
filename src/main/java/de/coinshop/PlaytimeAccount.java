package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.UUID;

/**
 * Spielzeit-Konto (Nachteilsausgleich): Wer in der laufenden Woche wenig gespielt hat, bekommt auf Belohnungen
 * (Tagesaufgaben, Expeditions-Belohnung) einen Zuschlag. Ab "full-hours" Stunden pro Woche gibt es keinen mehr.
 * Der Zuschlag sinkt gleichmäßig von "max-percent" (0 Stunden) auf 0 (full-hours).
 */
public final class PlaytimeAccount {

    private final CoinShopPlugin plugin;

    public PlaytimeAccount(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private static int weekKey() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private static long totalTicks(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE);
    }

    /** Beim Beitreten: ist eine neue Woche, merkt sich den Stand der Spielzeit. */
    public void onJoin(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (db.getMetaLong("pt_week_" + uuid, 0) != weekKey()) {
            db.setMetaLong("pt_week_" + uuid, weekKey());
            db.setMetaLong("pt_start_" + uuid, totalTicks(player));
        }
    }

    /** Stunden gespielt in dieser Woche. */
    public double hoursThisWeek(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (db.getMetaLong("pt_week_" + uuid, 0) != weekKey()) {
            onJoin(player);
        }
        long played = Math.max(0, totalTicks(player) - db.getMetaLong("pt_start_" + uuid, totalTicks(player)));
        return played / 72000.0;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("playtime-account.enabled", true);
    }

    /** Zuschlag in Prozent (0 bis max-percent), 0 wenn der Spieler nicht online ist. */
    public double percent(UUID uuid) {
        if (!enabled()) {
            return 0;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return 0;
        }
        double full = Math.max(1.0, plugin.getConfig().getDouble("playtime-account.full-hours", 10));
        double max = Math.max(0, plugin.getConfig().getDouble("playtime-account.max-percent", 50));
        double left = Math.max(0, 1.0 - hoursThisWeek(player) / full);
        return max * left;
    }

    /** Faktor für Belohnungen (1,0 = kein Zuschlag). */
    public double factor(UUID uuid) {
        return 1.0 + percent(uuid) / 100.0;
    }
}
