package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Phantome erscheinen nur noch bei Vollmond (in der Oberwelt) und im End.
 *
 * - In der Oberwelt wird das natürliche Auftauchen von Phantomen (durch Schlaflosigkeit) verhindert,
 *   solange kein Vollmond ist.
 * - Im End gibt es normalerweise keine Phantome. Deshalb lässt das Plugin dort in der Nähe von Spielern
 *   ab und zu welche erscheinen (Einstellungen in der config.yml unter "phantoms").
 */
public final class PhantomRules implements Listener {

    private final CoinShopPlugin plugin;

    public PhantomRules(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // alle 10 Sekunden im End nachsehen
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnInEnd, 200L, 200L);
    }

    /** Vollmond? Der Mondzyklus dauert 8 Tage, Tag 0 ist Vollmond. */
    private static boolean fullMoon(World world) {
        return (world.getFullTime() / 24000L) % 8L == 0L;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.PHANTOM
                || !plugin.getConfig().getBoolean("phantoms.only-full-moon-and-end", true)) {
            return;
        }
        // Nur das natürliche Auftauchen blockieren (Eier, Befehle, unser End-Spawn bleiben erlaubt)
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        World world = event.getLocation().getWorld();
        if (world.getEnvironment() == World.Environment.THE_END) {
            return;
        }
        if (!fullMoon(world)) {
            event.setCancelled(true);
        }
    }

    /** Im End erscheinen Phantome in der Nähe von Spielern (nicht mehr als 3 gleichzeitig). */
    private void spawnInEnd() {
        if (!plugin.getConfig().getBoolean("phantoms.spawn-in-end", true)) {
            return;
        }
        double chance = plugin.getConfig().getDouble("phantoms.end-spawn-chance", 0.35);
        int max = Math.max(1, plugin.getConfig().getInt("phantoms.end-max-nearby", 3));
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.THE_END
                    || world.getDifficulty() == Difficulty.PEACEFUL
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || Math.random() >= chance) {
                continue;
            }
            int nearby = player.getNearbyEntities(64, 64, 64).stream().filter(e -> e instanceof Phantom).toList().size();
            if (nearby >= max) {
                continue;
            }
            double angle = Math.random() * Math.PI * 2;
            double distance = 18 + Math.random() * 14;
            Location at = player.getLocation().add(Math.cos(angle) * distance, 10 + Math.random() * 8,
                    Math.sin(angle) * distance);
            world.spawnEntity(at, EntityType.PHANTOM, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
    }
}
