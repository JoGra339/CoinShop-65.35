package de.coinshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Freecam: Der Spieler schaut im Zuschauer-Modus durch die Gegend, während sein Körper (eine Figur)
 * an der alten Stelle stehen bleibt - und dort verwundbar ist. Im Zuschauer-Modus kann man nicht durch
 * Blöcke fliegen (nur durch Glas) und sich nicht zu anderen Spielern teleportieren.
 *
 * So funktioniert es:
 *  - Beim Start wird an deiner Stelle eine Figur ("Körper") erzeugt, die deine Rüstung trägt und deine
 *    Lebenspunkte hat. Nahe Monster greifen sie an.
 *  - Wird die Figur getötet, endet die Freecam und du stirbst ganz normal an der Stelle des Körpers.
 *  - Mit /freecam kehrst du zurück.
 */
public final class FreecamManager implements Listener {

    private static final class Session {
        final UUID player;
        final Location origin;
        final GameMode mode;
        final boolean allowFlight;
        final boolean flying;
        LivingEntity body;
        long lastHitMessage = 0;

        Session(Player p, Location origin) {
            this.player = p.getUniqueId();
            this.origin = origin;
            this.mode = p.getGameMode();
            this.allowFlight = p.getAllowFlight();
            this.flying = p.isFlying();
        }
    }

    private final CoinShopPlugin plugin;
    private final NamespacedKey leftoverKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** Körper-Entity -> Spieler (um Schaden und Tod dem richtigen Spieler zuzuordnen). */
    private final Map<UUID, UUID> bodies = new HashMap<>();
    private int counter = 0;

    public FreecamManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        this.leftoverKey = new NamespacedKey(plugin, "freecam_origin");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("freecam.enabled", true);
    }

    private double maxDistance() {
        return Math.max(8, plugin.getConfig().getDouble("freecam.max-distance", 1000));
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    //  Ein- und ausschalten
    // ------------------------------------------------------------------

    public void toggle(Player player) {
        if (isActive(player)) {
            stop(player, false);
        } else {
            start(player);
        }
    }

    private void start(Player player) {
        Messages msg = plugin.messages();
        if (!enabled()) {
            msg.send(player, "freecam-disabled");
            return;
        }
        if (player.isDead() || player.isSleeping() || player.isInsideVehicle()
                || player.getGameMode() == GameMode.SPECTATOR) {
            msg.send(player, "freecam-cannot");
            return;
        }
        Session session = new Session(player, player.getLocation().clone());
        LivingEntity body = spawnBody(player);
        if (body == null) {
            msg.send(player, "freecam-cannot");
            return;
        }
        session.body = body;
        sessions.put(player.getUniqueId(), session);
        bodies.put(body.getUniqueId(), player.getUniqueId());
        body.getChunk().addPluginChunkTicket(plugin); // Chunk mit dem Körper geladen halten

        // Für den Fall eines Absturzes: merken, wohin der Spieler zurückgehört
        Location o = session.origin;
        player.getPersistentDataContainer().set(leftoverKey, PersistentDataType.STRING,
                o.getWorld().getName() + "|" + o.getX() + "|" + o.getY() + "|" + o.getZ() + "|"
                        + o.getYaw() + "|" + o.getPitch() + "|" + session.mode.name());

        player.setGameMode(GameMode.SPECTATOR);
        msg.send(player, "freecam-start");
    }

    /** Beendet die Freecam. "killed" = der Körper wurde getötet, der Spieler stirbt jetzt. */
    public void stop(Player player, boolean killed) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        Location target = session.origin;
        double health = player.getHealth();
        if (session.body != null) {
            bodies.remove(session.body.getUniqueId());
            if (session.body.getWorld() != null) {
                target = session.body.getLocation(); // der Körper wurde evtl. etwas verschoben
            }
            if (session.body.isValid()) {
                health = session.body.getHealth();
            }
            session.body.remove();
        }
        session.origin.getChunk().removePluginChunkTicket(plugin);
        player.getPersistentDataContainer().remove(leftoverKey);

        player.setGameMode(session.mode);
        player.teleport(target);
        if (session.mode == GameMode.SURVIVAL || session.mode == GameMode.ADVENTURE) {
            player.setAllowFlight(session.allowFlight);
            player.setFlying(session.allowFlight && session.flying);
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        if (!killed) {
            player.setHealth(Math.max(0.5, Math.min(maxHealth, health)));
            plugin.messages().send(player, "freecam-stop");
        } else {
            player.setHealth(0);
        }
    }

    public void stopAll() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                stop(p, false);
            } else {
                Session s = sessions.remove(uuid);
                if (s != null && s.body != null) {
                    s.body.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Der Körper
    // ------------------------------------------------------------------

    private LivingEntity spawnBody(Player p) {
        Location loc = p.getLocation();
        World world = loc.getWorld();
        Entity entity;
        try {
            // Die "Schaufensterpuppe" gibt es in neuen Minecraft-Versionen
            entity = world.spawnEntity(loc, EntityType.valueOf("MANNEQUIN"));
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            entity = world.spawnEntity(loc, EntityType.VILLAGER);
            if (entity instanceof Mob mob) {
                mob.setAI(false);
            }
        }
        if (!(entity instanceof LivingEntity body)) {
            entity.remove();
            return null;
        }
        body.setPersistent(false);         // wird nie gespeichert (kein Rest nach einem Absturz)
        body.setRemoveWhenFarAway(false);
        body.setInvulnerable(false);
        body.setCanPickupItems(false);
        body.customName(Component.text(p.getName()));
        body.setCustomNameVisible(true);

        AttributeInstance bodyMax = body.getAttribute(Attribute.MAX_HEALTH);
        AttributeInstance playerMax = p.getAttribute(Attribute.MAX_HEALTH);
        if (bodyMax != null && playerMax != null) {
            bodyMax.setBaseValue(playerMax.getValue());
        }
        body.setHealth(Math.min(p.getHealth(), bodyMax == null ? 20 : bodyMax.getValue()));

        // Rüstung und Item in der Hand übernehmen (damit die Figur auch so viel Schutz hat wie du)
        EntityEquipment eq = body.getEquipment();
        if (eq != null) {
            PlayerInventory inv = p.getInventory();
            eq.setHelmet(inv.getHelmet() == null ? null : inv.getHelmet().clone());
            eq.setChestplate(inv.getChestplate() == null ? null : inv.getChestplate().clone());
            eq.setLeggings(inv.getLeggings() == null ? null : inv.getLeggings().clone());
            eq.setBoots(inv.getBoots() == null ? null : inv.getBoots().clone());
            eq.setItemInMainHand(inv.getItemInMainHand().clone());
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                try {
                    eq.setDropChance(slot, 0f); // nichts verdoppeln, wenn die Figur stirbt
                } catch (RuntimeException ignored) {
                    // Slot nicht unterstützt
                }
            }
        }
        // Optionale Feinheiten (nur bei der Schaufensterpuppe vorhanden) - Fehler sind egal
        tryInvoke(body, "setImmovable", boolean.class, true);
        tryInvoke(body, "setDescription", Component.class, Component.empty());
        return body;
    }

    private static void tryInvoke(Object target, String method, Class<?> type, Object arg) {
        try {
            target.getClass().getMethod(method, type).invoke(target, arg);
        } catch (Exception ignored) {
            // gibt es in dieser Version nicht
        }
    }

    // ------------------------------------------------------------------
    //  Regelmäßige Aufgaben (alle halbe Sekunde)
    // ------------------------------------------------------------------

    private void tick() {
        counter++;
        for (Session session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.player);
            if (player == null) {
                continue;
            }
            LivingEntity body = session.body;
            if (body == null || !body.isValid()) {
                stop(player, false); // Körper ist verschwunden
                continue;
            }
            // Andere Wesen "ansehen" (Linksklick im Zuschauer-Modus) ist gesperrt: sofort wieder lösen
            if (player.getSpectatorTarget() != null) {
                player.setSpectatorTarget(null);
            }
            // Monster in der Nähe greifen den Körper an
            for (Entity near : body.getNearbyEntities(16, 8, 16)) {
                if (near instanceof Monster monster && monster.hasLineOfSight(body)) {
                    LivingEntity current = monster.getTarget();
                    if (current == null || current.getUniqueId().equals(player.getUniqueId())) {
                        monster.setTarget(body);
                    }
                }
            }
            // Lebenspunkte des Spielers = Lebenspunkte des Körpers
            if (counter % 2 == 0) {
                syncHealth(player, body);
            }
        }
    }

    private void syncHealth(Player player, LivingEntity body) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        double health = Math.max(0.1, Math.min(maxHealth, body.getHealth()));
        player.setHealth(health);
    }

    // ------------------------------------------------------------------
    //  Ereignisse
    // ------------------------------------------------------------------

    /** Körper wurde verletzt: dem Spieler Bescheid sagen und die Lebenspunkte angleichen. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBodyDamage(EntityDamageEvent event) {
        UUID owner = bodies.get(event.getEntity().getUniqueId());
        if (owner == null) {
            return;
        }
        Session session = sessions.get(owner);
        Player player = Bukkit.getPlayer(owner);
        if (session == null || player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastHitMessage > 2000) {
            session.lastHitMessage = now;
            plugin.messages().send(player, "freecam-body-hit");
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session.body != null && session.body.isValid() && player.isOnline()) {
                syncHealth(player, session.body);
            }
        });
    }

    /** Körper getötet: Freecam endet und der Spieler stirbt (mit seinen normalen Drops). */
    @EventHandler
    public void onBodyDeath(EntityDeathEvent event) {
        UUID owner = bodies.get(event.getEntity().getUniqueId());
        if (owner == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(owner);
            if (player != null) {
                stop(player, true);
            }
        });
    }

    /** Nicht durch Blöcke fliegen (außer Glas) und nicht zu weit weg. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        Location to = event.getTo();
        if (session == null || to == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return; // nur umgeschaut
        }
        double dx = to.getX() - session.origin.getX();
        double dz = to.getZ() - session.origin.getZ();
        // Nur waagerecht begrenzt - in Höhe und Tiefe gibt es keine Grenze
        if (to.getWorld() != session.origin.getWorld()
                || dx * dx + dz * dz > maxDistance() * maxDistance()) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "freecam-distance", "max", (int) maxDistance());
            return;
        }
        if (collides(from.getWorld(), from.getX(), from.getY(), from.getZ())) {
            return; // steckt schon in einem Block: erlauben, damit man nicht festhängt
        }
        // Den Weg in kleinen Schritten prüfen, damit man auch bei schnellem Flug nicht durch Wände "springt"
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.2));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            if (collides(from.getWorld(), from.getX() + delta.getX() * t,
                    from.getY() + delta.getY() * t, from.getZ() + delta.getZ() * t)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Würde der Spieler (0,6 breit, 1,8 hoch) an dieser Stelle in einem festen Block stecken? Glas zählt nicht. */
    private boolean collides(World world, double x, double y, double z) {
        BoundingBox box = new BoundingBox(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.floor(box.getMaxX() - 1.0E-7);
        int minY = (int) Math.floor(box.getMinY());
        int maxY = (int) Math.floor(box.getMaxY() - 1.0E-7);
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.floor(box.getMaxZ() - 1.0E-7);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    return true; // ungeladenes Gebiet ist Tabu
                }
                for (int by = minY; by <= maxY; by++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (block.isPassable()) {
                        continue;
                    }
                    Material type = block.getType();
                    if (type.name().contains("GLASS")) {
                        continue; // durch Glas darf man fliegen
                    }
                    for (BoundingBox part : block.getCollisionShape().getBoundingBoxes()) {
                        if (part.clone().shift(bx, by, bz).overlaps(box)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Das Teleport-Menü des Zuschauer-Modus (zu anderen Spielern) ist gesperrt. */
    @EventHandler
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        if (isActive(event.getPlayer()) && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }

    /** Nach einem Absturz mitten in der Freecam: den Spieler zurück an seinen Ort setzen. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String data = player.getPersistentDataContainer().get(leftoverKey, PersistentDataType.STRING);
        if (data == null || isActive(player)) {
            return;
        }
        player.getPersistentDataContainer().remove(leftoverKey);
        try {
            String[] p = data.split("\\|");
            World world = Bukkit.getWorld(p[0]);
            if (world != null) {
                player.teleport(new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                        Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5])));
            }
            player.setGameMode(GameMode.valueOf(p[6]));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Freecam-Rest von " + player.getName() + " konnte nicht hergestellt werden: " + e);
            player.setGameMode(GameMode.SURVIVAL);
        }
    }
}
