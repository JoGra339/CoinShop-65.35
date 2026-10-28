package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nacht überspringen, wenn genug (Standard: 10 %) der aktiven Spieler schlafen.
 *
 * Statt die Nacht sofort zu überspringen, läuft die Tageszeit schnell vorwärts - man sieht Sonne
 * und Mond über den Himmel ziehen. Es wird NUR die Tageszeit beschleunigt (nicht Mobs, Pflanzen, Redstone
 * oder Wetter), damit die Beschleunigung keine weiteren Auswirkungen hat.
 *
 * "Aktiv" heißt: nicht im Zuschauer-Modus und nicht AFK (länger als X Minuten bewegungslos).
 */
public final class SleepManager implements Listener {

    private final CoinShopPlugin plugin;
    /** Welten, in denen die Nacht gerade beschleunigt wird. */
    private final Set<UUID> skipping = new HashSet<>();
    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private final Map<UUID, Long> lastMoved = new HashMap<>();
    private int counter = 0;

    public SleepManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("sleep.enabled", true);
    }

    private int percent() {
        return Math.max(1, Math.min(100, plugin.getConfig().getInt("sleep.percent", 10)));
    }

    private long speed() {
        return Math.max(1, plugin.getConfig().getLong("sleep.speed", 120));
    }

    private long afkMillis() {
        return Math.max(1, plugin.getConfig().getLong("sleep.afk-minutes", 5)) * 60_000L;
    }

    // ------------------------------------------------------------------

    private void tick() {
        if (!enabled()) {
            skipping.clear();
            return;
        }
        counter++;
        if (counter % 20 == 0) {
            updateActivity();
            for (World world : Bukkit.getWorlds()) {
                checkWorld(world);
            }
        }
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            if (skipping.contains(world.getUID())) {
                advance(world);
            }
        }
    }

    /** Merkt sich, wer sich zuletzt bewegt hat (für die AFK-Erkennung). */
    private void updateActivity() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            Location last = lastPosition.get(p.getUniqueId());
            boolean moved = last == null || last.getWorld() != loc.getWorld()
                    || last.distanceSquared(loc) > 0.0001
                    || Math.abs(last.getYaw() - loc.getYaw()) > 0.5
                    || Math.abs(last.getPitch() - loc.getPitch()) > 0.5;
            if (moved || p.isSleeping()) {
                lastMoved.put(p.getUniqueId(), now);
            }
            lastPosition.put(p.getUniqueId(), loc);
        }
    }

    private boolean isActive(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (p.isSleeping()) {
            return true;
        }
        long last = lastMoved.getOrDefault(p.getUniqueId(), System.currentTimeMillis());
        return System.currentTimeMillis() - last < afkMillis();
    }

    private boolean isNight(World world) {
        long t = world.getTime();
        return t >= 12542 && t < 23460;
    }

    private void checkWorld(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL
                || skipping.contains(world.getUID()) || !isNight(world)) {
            return;
        }
        int active = 0;
        int sleeping = 0;
        for (Player p : world.getPlayers()) {
            if (isActive(p)) {
                active++;
                if (p.isDeeplySleeping()) {
                    sleeping++;
                }
            }
        }
        if (active == 0 || sleeping == 0 || sleeping * 100 < percent() * active) {
            return;
        }
        skipping.add(world.getUID());
        for (Player p : world.getPlayers()) {
            plugin.messages().send(p, "sleep-skip", "sleeping", sleeping, "active", active);
        }
    }

    /** Lässt die Tageszeit ein Stück weiterlaufen, bis es Morgen ist. */
    private void advance(World world) {
        if (world.getPlayers().isEmpty()) {
            skipping.remove(world.getUID());
            return;
        }
        long time = world.getTime();
        if (time < 12000) { // Morgen erreicht
            skipping.remove(world.getUID());
            return;
        }
        long step = speed();
        long untilMorning = 24000 - time;
        if (step >= untilMorning) {
            world.setFullTime(world.getFullTime() + untilMorning);
            skipping.remove(world.getUID());
        } else {
            world.setFullTime(world.getFullTime() + step);
        }
    }

    /** Die normale Minecraft-Nachtüberspringung (sofort) wird verhindert - wir machen es mit Animation. */
    @EventHandler
    public void onTimeSkip(TimeSkipEvent event) {
        if (enabled() && event.getSkipReason() == TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastPosition.remove(event.getPlayer().getUniqueId());
        lastMoved.remove(event.getPlayer().getUniqueId());
    }
}
