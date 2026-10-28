package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Teleportieren: Spawn, Zuhause und Teleport-Anfragen an andere Spieler.
 * Jede Teleportation startet erst nach einer kurzen Wartezeit (Standard: 3 Sekunden).
 */
public final class TeleportManager {

    /** Eine offene Anfrage "von" möchte sich zum Empfänger teleportieren. */
    private record Request(UUID from, long expiresAt) { }

    private final CoinShopPlugin plugin;
    /** Spieler, die gerade auf ihre Teleportation warten. */
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    /** Offene Anfragen, nach dem Empfänger sortiert (ein Empfänger hat höchstens eine). */
    private final Map<UUID, Request> requests = new HashMap<>();

    public TeleportManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public int delaySeconds() {
        return Math.max(0, plugin.getConfig().getInt("teleport.delay-seconds", 3));
    }

    public int requestTimeoutSeconds() {
        return Math.max(5, plugin.getConfig().getInt("teleport.request-timeout-seconds", 60));
    }

    /**
     * Teleportiert den Spieler nach der Wartezeit. Das Ziel wird erst ganz am Ende ermittelt
     * (so folgt man bei einer Anfrage dem Ziel, falls es sich noch bewegt hat). Gibt "null" zurück,
     * wenn das Ziel nicht mehr verfügbar ist.
     */
    public void teleportDelayed(Player player, Supplier<Location> destination) {
        Messages msg = plugin.messages();
        if (plugin.freecam().isActive(player)) {
            msg.send(player, "tp-freecam");
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            msg.send(player, "tp-pending");
            return;
        }
        int delay = delaySeconds();
        msg.send(player, "tp-countdown", "seconds", delay);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            Location target = destination.get();
            if (target == null || target.getWorld() == null) {
                msg.send(player, "tp-failed");
                return;
            }
            player.teleport(target);
            msg.send(player, "tp-done");
        }, 20L * delay);
        pending.put(player.getUniqueId(), task);
    }

    public void cancel(Player player) {
        BukkitTask task = pending.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        requests.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    //  Spawn und Zuhause
    // ------------------------------------------------------------------

    public Location spawnLocation() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation().clone();
        return spawn.add(0.5, 0, 0.5);
    }

    public void goSpawn(Player player) {
        teleportDelayed(player, this::spawnLocation);
    }

    public void setHome(Player player) {
        Location l = player.getLocation();
        plugin.database().setHome(player.getUniqueId(), new Database.Home(l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        plugin.messages().send(player, "home-set");
    }

    public void goHome(Player player) {
        Messages msg = plugin.messages();
        var home = plugin.database().getHome(player.getUniqueId());
        if (home.isEmpty()) {
            msg.send(player, "home-none");
            return;
        }
        Database.Home h = home.get();
        if (Bukkit.getWorld(h.world()) == null) {
            msg.send(player, "home-world-missing");
            return;
        }
        teleportDelayed(player, () -> {
            var world = Bukkit.getWorld(h.world());
            return world == null ? null : new Location(world, h.x(), h.y(), h.z(), h.yaw(), h.pitch());
        });
    }

    // ------------------------------------------------------------------
    //  Teleport-Anfragen
    // ------------------------------------------------------------------

    /** "from" möchte sich zu "target" teleportieren. */
    public void request(Player from, Player target) {
        Messages msg = plugin.messages();
        if (from.getUniqueId().equals(target.getUniqueId())) {
            msg.send(from, "tpa-self");
            return;
        }
        requests.put(target.getUniqueId(),
                new Request(from.getUniqueId(), System.currentTimeMillis() + requestTimeoutSeconds() * 1000L));
        msg.send(from, "tpa-sent", "player", target.getName(), "seconds", requestTimeoutSeconds());
        msg.send(target, "tpa-received", "player", from.getName());
    }

    /** Holt die (noch gültige) Anfrage an den Empfänger und entfernt sie. */
    private Player takeRequest(Player receiver) {
        Request r = requests.remove(receiver.getUniqueId());
        if (r == null || r.expiresAt() < System.currentTimeMillis()) {
            plugin.messages().send(receiver, "tpa-none");
            return null;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            plugin.messages().send(receiver, "tpa-requester-offline");
            return null;
        }
        return from;
    }

    public void accept(Player receiver) {
        Player from = takeRequest(receiver);
        if (from == null) {
            return;
        }
        Messages msg = plugin.messages();
        msg.send(receiver, "tpa-accepted", "player", from.getName());
        msg.send(from, "tpa-accepted-sender", "player", receiver.getName());
        teleportDelayed(from, () -> receiver.isOnline() ? receiver.getLocation() : null);
    }

    public void deny(Player receiver) {
        Player from = takeRequest(receiver);
        if (from == null) {
            return;
        }
        Messages msg = plugin.messages();
        msg.send(receiver, "tpa-denied", "player", from.getName());
        msg.send(from, "tpa-denied-sender", "player", receiver.getName());
    }
}
