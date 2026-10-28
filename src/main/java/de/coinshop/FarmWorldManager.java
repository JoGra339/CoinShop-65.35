package de.coinshop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.time.ZonedDateTime;
import java.util.Random;
import java.util.Set;

/**
 * /farmwelt: Ein Ziel mindestens 100.000 Blöcke vom Spawn entfernt, damit man dort unberührte Gegend zum Farmen hat.
 * Das Ziel ist für alle Spieler gleich (ein fester Punkt) und wechselt jeden Tag zusammen mit dem Shop-Wechsel.
 *
 * Der Punkt wird aus dem Tag und dem Welt-Seed ausgewürfelt, ist also nach einem Neustart derselbe. Liegt er in
 * einem See, im Meer oder auf Lava, wird in der Nähe ein sicherer Platz gesucht.
 */
public final class FarmWorldManager implements org.bukkit.event.Listener {

    private static final Set<Material> DANGEROUS = Set.of(Material.LAVA, Material.WATER, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.BUBBLE_COLUMN, Material.KELP, Material.SEAGRASS,
            Material.TALL_SEAGRASS);

    private final CoinShopPlugin plugin;
    private long cachedDay = Long.MIN_VALUE;
    private Location cached;

    public FarmWorldManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public int minDistance() {
        return Math.max(1000, plugin.getConfig().getInt("farmworld.min-distance", 100000));
    }

    public int maxDistance() {
        return Math.max(minDistance(), plugin.getConfig().getInt("farmworld.max-distance", 120000));
    }

    /** Die Oberwelt. */
    private World overworld() {
        for (World w : org.bukkit.Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return null;
    }

    /** Welcher Tag ist heute? (wechselt zur Shop-Wechsel-Uhrzeit) */
    private long today() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return now.minusHours(plugin.serverShop().resetHour()).toLocalDate().toEpochDay();
    }

    /** Das heutige Ziel (sicherer Standplatz), oder null ohne Oberwelt. Das Ergebnis wird für den Tag gemerkt. */
    public synchronized Location today(boolean unused) {
        World world = overworld();
        if (world == null) {
            return null;
        }
        long day = today();
        if (cached != null && cachedDay == day && cached.getWorld() == world) {
            return cached.clone();
        }
        Random random = new Random(day * 341873128712L + world.getSeed());
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = minDistance() + random.nextDouble() * (maxDistance() - minDistance());
        Location spawn = world.getSpawnLocation();
        int x = (int) Math.round(spawn.getX() + Math.cos(angle) * distance);
        int z = (int) Math.round(spawn.getZ() + Math.sin(angle) * distance);

        Location found = null;
        // Vom Wunschpunkt aus in Ringen nach einem sicheren Platz suchen (immer in derselben Reihenfolge)
        search:
        for (int ring = 0; ring <= 12; ring++) {
            int steps = ring == 0 ? 1 : 8;
            for (int i = 0; i < steps; i++) {
                double a = Math.PI * 2 * i / 8;
                int cx = x + (int) Math.round(Math.cos(a) * ring * 8);
                int cz = z + (int) Math.round(Math.sin(a) * ring * 8);
                Location candidate = safeSpot(world, cx, cz);
                if (candidate != null) {
                    found = candidate;
                    break search;
                }
            }
        }
        if (found == null) { // alles Wasser: trotzdem dorthin, auf die Wasseroberfläche
            found = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
        }
        cached = found;
        cachedDay = day;
        return found.clone();
    }

    private Location safeSpot(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight()) {
            return null;
        }
        Block ground = world.getBlockAt(x, y, z);
        if (ground.isLiquid() || DANGEROUS.contains(ground.getType()) || !ground.getType().isSolid()) {
            return null;
        }
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
            return null;
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    // ------------------------------------------------------------------
    //  Schutz gegen Fallen am heutigen Farm-Punkt
    // ------------------------------------------------------------------

    private final java.util.Map<java.util.UUID, Long> lastWarn = new java.util.HashMap<>();

    public int protectRadius() {
        return Math.max(0, plugin.getConfig().getInt("farmworld.protect-radius", 12));
    }

    /** Liegt der Ort im geschützten Bereich um den heutigen Farm-Punkt? */
    public boolean nearSpot(Location l) {
        int radius = protectRadius();
        if (radius <= 0 || l == null || l.getWorld() == null || l.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        Location spot = today(false);
        if (spot == null || spot.getWorld() != l.getWorld()) {
            return false;
        }
        double dx = l.getX() - spot.getX();
        double dz = l.getZ() - spot.getZ();
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private boolean blocked(Player player, Location l) {
        if (player.hasPermission("coinshop.admin") || !nearSpot(l)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastWarn.getOrDefault(player.getUniqueId(), 0L) > 3000) {
            lastWarn.put(player.getUniqueId(), now);
            plugin.messages().send(player, "farm-protected", "radius", protectRadius());
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Explosionen (TNT, Creeper) zerstören dort keine Blöcke. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (nearSpot(event.getLocation())) {
            event.blockList().clear();
        }
    }
}
