package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Login-Serie, fest verbunden mit dem täglichen Einkommen (Grundeinkommen).
 *
 * Ein "Tag" geht von Shop-Wechsel zu Shop-Wechsel (12:00 bis 12:00 Uhr). Wer in einem Tag mindestens einmal
 * auf dem Server war, setzt seine Serie fort. Verpasst man einen ganzen Tag, beginnt sie wieder bei 1.
 *
 * Ausgezahlt wird NICHTS beim Beitreten. Stattdessen wächst das tägliche Einkommen mit der Serie:
 *   Einkommen = Grundeinkommen + Serien-Bonus + Sets + Ranglisten
 * Der Serien-Bonus wird zusammen mit allem anderen beim Shop-Wechsel ausgezahlt (siehe InvestmentManager).
 * Er wächst pro Tag der Serie ("per-day"), bis "max-days" Tage; jeder 7. Tag gibt zusätzlich "weekly-bonus".
 * Einstellungen: "login-streak" in der config.yml.
 */
public final class LoginRewards {

    private final CoinShopPlugin plugin;

    public LoginRewards(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("login-streak.enabled", true);
    }

    /** Der aktuelle "Tag" (wechselt zur Shop-Wechsel-Uhrzeit). */
    private LocalDate today() {
        return ZonedDateTime.now(plugin.serverShop().zone())
                .minusHours(plugin.serverShop().resetHour()).toLocalDate();
    }

    /** Serien-Bonus für einen bestimmten Tag der Serie (1 = erster Tag, Bonus 0). */
    public long bonusFor(int streak) {
        if (!enabled() || streak < 1) {
            return 0;
        }
        var cfg = plugin.getConfig();
        long perDay = Math.max(0, cfg.getLong("login-streak.per-day", 5));
        int maxDays = Math.max(1, cfg.getInt("login-streak.max-days", 7));
        long bonus = perDay * (Math.min(streak, maxDays) - 1);
        if (streak % 7 == 0) {
            bonus += Math.max(0, cfg.getLong("login-streak.weekly-bonus", 100));
        }
        return bonus;
    }

    private long todayEpoch() {
        return today().toEpochDay();
    }

    /** Ist der Ruhemodus an (und noch nicht über die Höchstdauer)? */
    public boolean resting(UUID uuid) {
        if (!plugin.getConfig().getBoolean("rest-mode.enabled", true)) {
            return false;
        }
        long start = plugin.database().getMetaLong("rest_start_" + uuid, 0);
        return start > 0 && todayEpoch() - start <= Math.max(1, plugin.getConfig().getInt("rest-mode.max-days", 30));
    }

    /** Ist der Ruhemodus überhaupt eingeschaltet (Server-Einstellung)? */
    public boolean restEnabled() {
        return plugin.getConfig().getBoolean("rest-mode.enabled", true);
    }

    /** Seit wie vielen Tagen läuft der Ruhemodus (0 = heute gestartet)? */
    public long restDay(UUID uuid) {
        return Math.max(0, todayEpoch() - plugin.database().getMetaLong("rest_start_" + uuid, todayEpoch()));
    }

    /** Wie viele Tage muss der Spieler noch warten, bis er den Ruhemodus wieder einschalten darf (0 = jetzt)? */
    public long restCooldownLeft(UUID uuid) {
        int cooldown = Math.max(0, plugin.getConfig().getInt("rest-mode.cooldown-days", 30));
        long ended = plugin.database().getMetaLong("rest_cd_" + uuid, -100000);
        return Math.max(0, cooldown - (todayEpoch() - ended));
    }

    /** Darf ein Gnadentag (ein verpasster Tag ohne Serien-Abbruch) genutzt werden? */
    public boolean graceOk(UUID uuid) {
        if (!plugin.getConfig().getBoolean("login-streak-grace.enabled", true)) {
            return false;
        }
        long last = plugin.database().getMetaLong("grace_day_" + uuid, -100000);
        return todayEpoch() - last >= Math.max(1, plugin.getConfig().getInt("login-streak-grace.min-days-between", 7));
    }

    /** Ruhemodus ein- oder ausschalten (Befehl /ruhemodus). */
    public void toggleRest(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!plugin.getConfig().getBoolean("rest-mode.enabled", true)) {
            player.sendMessage(Component.text("Der Ruhemodus ist ausgeschaltet.", NamedTextColor.RED));
            return;
        }
        long start = db.getMetaLong("rest_start_" + uuid, 0);
        if (start > 0) {
            endRest(uuid);
            Optional<Database.Streak> s = db.getStreak(uuid);
            if (s.isPresent() && todayEpoch() - start <= Math.max(1, plugin.getConfig().getInt("rest-mode.max-days", 30))) {
                db.setStreak(uuid, today().toString(), s.get().streak(), s.get().best());
            }
            player.sendMessage(Component.text("Ruhemodus beendet. Deine Login-Serie läuft weiter.", NamedTextColor.GREEN));
            return;
        }
        int cooldown = Math.max(0, plugin.getConfig().getInt("rest-mode.cooldown-days", 30));
        long ended = db.getMetaLong("rest_cd_" + uuid, -100000);
        long left = cooldown - (todayEpoch() - ended);
        if (left > 0) {
            player.sendMessage(Component.text("Den Ruhemodus kannst du erst in " + left + " Tagen wieder einschalten.",
                    NamedTextColor.RED));
            return;
        }
        db.setMetaLong("rest_start_" + uuid, todayEpoch());
        player.sendMessage(Component.text("Ruhemodus an (höchstens " + plugin.getConfig().getInt("rest-mode.max-days", 30)
                + " Tage): Deine Login-Serie reißt nicht, der Serien-Bonus ist halbiert. "
                + "Wenn du wieder spielst, endet er von selbst.", NamedTextColor.AQUA));
    }

    private void endRest(UUID uuid) {
        plugin.database().setMetaLong("rest_start_" + uuid, 0);
        plugin.database().setMetaLong("rest_cd_" + uuid, todayEpoch());
    }

    /** Wie viele Tage hintereinander ist der Spieler aktuell dabei? (0 = Serie ist gerissen) */
    public int currentStreak(UUID uuid) {
        Optional<Database.Streak> s = plugin.database().getStreak(uuid);
        if (s.isEmpty()) {
            return 0;
        }
        if (resting(uuid)) {
            return s.get().streak(); // eingefroren
        }
        try {
            LocalDate last = LocalDate.parse(s.get().lastDay());
            LocalDate today = today();
            if (last.equals(today) || last.plusDays(1).equals(today)
                    || (last.plusDays(2).equals(today) && graceOk(uuid))) {
                return s.get().streak();
            }
        } catch (RuntimeException ignored) {
            // kaputtes Datum -> Serie gilt als gerissen
        }
        return 0;
    }

    /** Serien-Bonus mit Lizenz "treue" (Prozent mehr). */
    public long bonusFor(UUID uuid, int streak) {
        return plugin.licenses().boost(uuid, "treue", bonusFor(streak));
    }

    /** Der Serien-Bonus, den der Spieler beim nächsten Shop-Wechsel zusätzlich bekommt. */
    public long bonusOf(UUID uuid) {
        long bonus = bonusFor(uuid, currentStreak(uuid));
        if (resting(uuid)) {
            bonus = bonus * Math.max(0, Math.min(100, plugin.getConfig().getInt("rest-mode.bonus-percent", 50))) / 100;
        }
        return bonus;
    }

    /** Willkommen zurück: nach langer Pause gibt es Coins und eine Mystery Box. */
    private void welcomeBack(Player player, long daysAway) {
        var cfg = plugin.getConfig();
        int min = Math.max(2, cfg.getInt("welcome-back.min-days", 7));
        if (!cfg.getBoolean("welcome-back.enabled", true) || daysAway < min) {
            return;
        }
        long days = Math.min(daysAway, Math.max(1, cfg.getInt("welcome-back.max-days", 30)));
        long coins = Math.max(0, cfg.getLong("welcome-back.base", 100)) + Math.max(0, cfg.getLong("welcome-back.per-day", 10)) * days;
        Database db = plugin.database();
        if (coins > 0) {
            db.addCoins(player.getUniqueId(), coins);
            db.logTx(player.getUniqueId(), player.getName(), "willkommen-zurueck", coins, daysAway + " Tage weg");
        }
        plugin.mysteryBoxes().give(player, Math.max(1, cfg.getInt("welcome-back.box-tier", 1)), "Willkommen zurück");
        player.sendMessage(Component.text("Willkommen zurück! Du warst " + daysAway + " Tage weg: +" + coins
                + " Coins und eine Mystery Box.", NamedTextColor.GOLD));
    }

    /** Wird beim Beitreten aufgerufen: führt die Serie weiter (zahlt aber nichts aus). */
    public void onJoin(Player player) {
        if (!enabled() || plugin.timeline().previewFor(player)) {
            return;
        }
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        String todayText = today().toString();
        Optional<Database.Streak> old = db.getStreak(uuid);

        int streak = 1;
        int best = 1;
        if (old.isPresent()) {
            if (old.get().lastDay().equals(todayText)) {
                return; // heute schon gezählt
            }
            best = old.get().best();
            LocalDate last = null;
            try {
                last = LocalDate.parse(old.get().lastDay());
            } catch (RuntimeException ignored) {
                // kaputtes Datum -> neu anfangen
            }
            if (last != null) {
                long away = today().toEpochDay() - last.toEpochDay();
                welcomeBack(player, away);
                if (db.getMetaLong("rest_start_" + uuid, 0) > 0) {
                    boolean keep = resting(uuid);
                    endRest(uuid);
                    if (keep) {
                        away = 1; // Ruhemodus: die Serie läuft einfach weiter
                        player.sendMessage(Component.text("Der Ruhemodus ist beendet. Willkommen zurück!", NamedTextColor.GREEN));
                    }
                }
                if (away == 1) {
                    streak = old.get().streak() + 1;
                } else if (away == 2 && graceOk(uuid)) {
                    streak = old.get().streak() + 1;
                    db.setMetaLong("grace_day_" + uuid, todayEpoch());
                    player.sendMessage(Component.text("Gnadentag: Ein verpasster Tag hat deine Serie nicht unterbrochen.",
                            NamedTextColor.GREEN));
                }
            }
        }
        best = Math.max(best, streak);
        try {
            db.setStreak(uuid, todayText, streak, best);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Login-Serie konnte nicht gespeichert werden: " + e);
            return;
        }
        plugin.messages().send(player, "login-streak", "days", streak, "bonus", bonusFor(uuid, streak),
                "next", bonusFor(uuid, streak + 1));
        if (streak % 7 == 0) {
            plugin.messages().send(player, "login-streak-weekly");
            // Alle 7 Tage eine Mystery Box, die mit der Zeit besser wird (Liste: eine Stufe je Woche, danach bleibt die letzte)
            List<Integer> tiers = plugin.getConfig().getIntegerList("login-streak.box-tiers");
            if (tiers.isEmpty()) {
                tiers = List.of(1, 1, 2, 2, 3, 3, 4, 4, 5);
            }
            int week = streak / 7;
            plugin.mysteryBoxes().give(player, tiers.get(Math.min(tiers.size(), week) - 1), "Loginserie " + streak + " Tage");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
    }
}
