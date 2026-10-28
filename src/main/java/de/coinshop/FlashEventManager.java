package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * Spontane Kurzzeit-Ereignisse: Aus heiterem Himmel startet ein Ereignis, das höchstens eine Stunde dauert.
 * Es gibt drei: Blitzverkauf (Preise im Server-Shop und Schwarzmarkt günstiger), Goldene Stunde (Casino-Gewinne
 * höher) und Gebührenfreie Stunde (keine Gebühren an den Börsen und im Schwarzmarkt). Sie kommen zusätzlich zum
 * Tagesereignis und nur, wenn jemand online ist.
 */
public final class FlashEventManager {

    public enum Flash {
        BLITZ("blitzverkauf", "Blitzverkauf", "blitz-minutes", 30),
        GOLD("goldene-stunde", "Goldene Stunde", "gold-minutes", 20),
        FEE("gebuehrenfreie-stunde", "Gebührenfreie Stunde", "fee-minutes", 45);

        public final String id;
        public final String title;
        final String durationKey;
        final int defaultMinutes;

        Flash(String id, String title, String durationKey, int defaultMinutes) {
            this.id = id;
            this.title = title;
            this.durationKey = durationKey;
            this.defaultMinutes = defaultMinutes;
        }
    }

    private static final String META_ID = "flash_id";
    private static final String META_END = "flash_end";
    private static final long TICKS = 400L; // alle 20 Sekunden prüfen

    private final CoinShopPlugin plugin;
    private final Random random = new Random();
    private boolean announcedEnd = true;

    public FlashEventManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICKS, TICKS);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("flash.enabled", true);
    }

    /** Das gerade laufende Kurzzeit-Ereignis oder null. */
    public Flash current() {
        Database db = plugin.database();
        if (db.getMetaLong(META_END, 0) <= System.currentTimeMillis()) {
            return null;
        }
        long id = db.getMetaLong(META_ID, -1);
        Flash[] all = Flash.values();
        return id >= 0 && id < all.length ? all[(int) id] : null;
    }

    public long millisLeft() {
        return Math.max(0, plugin.database().getMetaLong(META_END, 0) - System.currentTimeMillis());
    }

    // ---- Wirkungen ----

    /** Preisfaktor im Server-Shop und im Schwarzmarkt (Blitzverkauf). */
    public double saleFactor() {
        return current() == Flash.BLITZ ? plugin.getConfig().getDouble("flash.blitz-factor", 0.7) : 1.0;
    }

    /** Zusätzliche Prozent auf Casino-Gewinne (Goldene Stunde). Jackpot ausgenommen. */
    public double casinoBonusPercent() {
        return current() == Flash.GOLD ? plugin.getConfig().getDouble("flash.gold-bonus-percent", 100) : 0;
    }

    /** Faktor auf Gebühren an den Börsen und im Schwarzmarkt (Gebührenfreie Stunde). */
    public double feeFactor() {
        return current() == Flash.FEE ? 0 : 1.0;
    }

    // ---- Ablauf ----

    private void tick() {
        try {
            if (!enabled()) {
                return;
            }
            Flash active = current();
            if (active != null) {
                return;
            }
            if (!announcedEnd) {
                announcedEnd = true;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    plugin.messages().send(online, "flash-end");
                }
            }
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
            double average = Math.max(10, plugin.getConfig().getDouble("flash.average-minutes", 240));
            double perTick = (TICKS / 20.0) / (average * 60.0);
            if (random.nextDouble() < perTick) {
                begin(Flash.values()[random.nextInt(Flash.values().length)]);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Kurzzeit-Ereignis fehlgeschlagen: " + e);
        }
    }

    /** Startet ein Kurzzeit-Ereignis (auch für Admins zum Testen). */
    public void begin(Flash flash) {
        int minutes = Math.max(1, Math.min(60, plugin.getConfig().getInt("flash." + flash.durationKey, flash.defaultMinutes)));
        Database db = plugin.database();
        db.setMetaLong(META_ID, flash.ordinal());
        db.setMetaLong(META_END, System.currentTimeMillis() + minutes * 60_000L);
        announcedEnd = false;
        plugin.getLogger().info("Kurzzeit-Ereignis: " + flash.id + " (" + minutes + " Minuten)");
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(online, "flash-" + flash.id, "minutes", minutes);
            online.showTitle(Title.title(Component.text(flash.title + "!", NamedTextColor.GOLD),
                    Component.text("Nur " + minutes + " Minuten lang", NamedTextColor.YELLOW)));
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        }
    }
}
