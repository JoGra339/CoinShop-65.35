package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Safe-Zone um den Spawn der Hauptwelt: Dort kann (auch nach dem Serverstart) nichts abgebaut werden.
 * Admins (Recht coinshop.admin) sind ausgenommen. Einstellungen: "safe-zone:" in der config.yml.
 */
public final class SafeZone implements Listener {

    private final CoinShopPlugin plugin;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public SafeZone(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Räumt alle 10 Sekunden Monster aus der Zone (z. B. solche, die schon vor dem Start da waren). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!enabled()) {
                return;
            }
            World main = Bukkit.getWorlds().get(0);
            // Alle Kreaturen raus (auch friedliche), außer Dorfbewohner/Händler, Benannte und Gezähmte
            for (Mob mob : main.getEntitiesByClass(Mob.class)) {
                if (!inside(mob.getLocation())) {
                    continue;
                }
                if (mob instanceof Villager || mob instanceof WanderingTrader || mob.customName() != null
                        || (mob instanceof Tameable tame && tame.isTamed()) || mob.isLeashed()) {
                    continue;
                }
                mob.remove();
            }
        }, 20L * 20, 20L * 10);
    }

    /** Keine neuen Monster und Tiere in der Zone (nur Admin-Befehle dürfen etwas erschaffen). */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!inside(event.getLocation())) {
            return;
        }
        switch (event.getSpawnReason()) {
            case COMMAND, CUSTOM -> { }
            default -> event.setCancelled(true);
        }
    }

    /** In der Zone kann niemand schlagen: weder Spieler noch Tiere noch Monster (Admins ausgenommen). */
    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGH)
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        org.bukkit.entity.Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player p && !p.hasPermission("coinshop.admin")
                && (inside(p.getLocation()) || inside(event.getEntity().getLocation()))) {
            event.setCancelled(true);
            warn(p);
        }
    }

    /** Spieler nehmen in der Zone keinen Schaden und werden nicht hungrig. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && inside(p.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && inside(p.getLocation())
                && event.getFoodLevel() < p.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("safe-zone.enabled", true);
    }

    public int radius() {
        return Math.max(10, plugin.getConfig().getInt("safe-zone.radius", 150));
    }

    /** Liegt dieser Ort in der Safe-Zone? (waagerechter Abstand zum Spawn der Hauptwelt) */
    public boolean inside(Location l) {
        if (!enabled() || l == null || l.getWorld() == null) {
            return false;
        }
        World main = Bukkit.getWorlds().get(0);
        if (l.getWorld() != main) {
            return false;
        }
        Location c = main.getSpawnLocation();
        double dx = l.getX() - c.getX();
        double dz = l.getZ() - c.getZ();
        double r = radius();
        return dx * dx + dz * dz <= r * r;
    }

    private boolean protectedFor(Player player, Location l) {
        return inside(l) && !player.hasPermission("coinshop.admin");
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastMessage.getOrDefault(player.getUniqueId(), 0L) > 3000) {
            lastMessage.put(player.getUniqueId(), now);
            plugin.messages().send(player, "safe-zone", "radius", radius());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protectedFor(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getConfig().getBoolean("safe-zone.block-place", true)
                && protectedFor(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (plugin.getConfig().getBoolean("safe-zone.block-place", true)
                && protectedFor(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHanging(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player p && protectedFor(p, event.getEntity().getLocation())) {
            event.setCancelled(true);
            warn(p);
        }
    }

    /** Explosionen (Creeper, TNT, Wither ...) zerstören in der Zone keine Blöcke. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (enabled()) {
            event.blockList().removeIf((Block b) -> inside(b.getLocation()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (enabled()) {
            event.blockList().removeIf((Block b) -> inside(b.getLocation()));
        }
    }

    /** Monster (Enderman, Wither ...) verändern in der Zone keine Blöcke. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && inside(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (inside(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}
