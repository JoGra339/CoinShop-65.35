package de.coinshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.WorldBorder;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Termine des Servers: Serverstart (bis dahin dürfen nur Admins beitreten) und
 * Öffnung des Nethers (bis dahin sind Nether-Portale gesperrt, mit Countdown-Menü).
 * Die Zeiten stehen in der config.yml unter "schedule:".
 */
public final class TimelineManager implements Listener {

    private static final String NETHER_ANNOUNCED = "nether_announced";
    private static final String LAUNCH_DONE = "launch_done";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final CoinShopPlugin plugin;
    private final Map<UUID, Long> menuCooldown = new HashMap<>();
    private final Set<UUID> hasBorder = new HashSet<>();
    /** Admins, die sich die Border zum Testen anzeigen lassen (/coinsadmin border). */
    private final Set<UUID> testBorder = new HashSet<>();

    public TimelineManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Prüft jede halbe Minute, ob der Nether jetzt aufgeht. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 10, 20L * 10);
    }

    /** Zeigt Testphase-Spielern die Grenze (nur für sie sichtbar) und nimmt sie zum Serverstart wieder weg. */
    private void updateBorder(Player p) {
        World main = Bukkit.getWorlds().get(0);
        if ((borderFor(p) || testBorder.contains(p.getUniqueId())) && p.getWorld() == main) {
            WorldBorder border = Bukkit.createWorldBorder();
            border.setCenter(main.getSpawnLocation());
            // Test-Border der Admins zeigt die Safe-Zone, die Testphase-Border den Bewegungsradius
            double radius = borderFor(p) ? previewRadius() : Math.max(10, plugin.getConfig().getInt("safe-zone.radius", 150));
            border.setSize(radius * 2.0);
            border.setWarningDistance(10);
            border.setDamageAmount(0);
            p.setWorldBorder(border);
            hasBorder.add(p.getUniqueId());
        } else if (hasBorder.remove(p.getUniqueId())) {
            p.setWorldBorder(null);
        }
    }

    /** Schaltet die Test-Border für einen Admin ein oder aus. Gibt zurück, ob sie jetzt an ist. */
    public boolean toggleTestBorder(Player p) {
        boolean on = testBorder.add(p.getUniqueId());
        if (!on) {
            testBorder.remove(p.getUniqueId());
        }
        updateBorder(p);
        return on;
    }

    @EventHandler
    public void onBorderWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> updateBorder(event.getPlayer()));
    }

    @EventHandler
    public void onBorderRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> updateBorder(event.getPlayer()), 5L);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateBorder(p);
        }
        if (parse("schedule.server-start") != null && millisUntilLaunch() <= 0
                && plugin.database().getMetaLong(LAUNCH_DONE, 0) == 0) {
            plugin.database().setMetaLong(LAUNCH_DONE, 1);
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.messages().send(p, "server-opened");
                payStartBonus(p);
                plugin.logins().onJoin(p);
            }
        }
        if (!netherLockEnabled() || netherLocked()) {
            return;
        }
        if (plugin.database().getMetaLong(NETHER_ANNOUNCED, 0) == 0) {
            plugin.database().setMetaLong(NETHER_ANNOUNCED, 1);
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.messages().send(p, "nether-opened");
            }
        }
    }

    // ------------------------------------------------------------------
    //  Zeiten
    // ------------------------------------------------------------------

    private ZonedDateTime parse(String path) {
        String text = plugin.getConfig().getString(path, "");
        try {
            return LocalDateTime.parse(text.trim().replace(' ', 'T')).atZone(plugin.serverShop().zone());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Ist heute (Shop-Tag, Wechsel zur Shop-Wechsel-Uhrzeit) der Starttag des Servers? */
    public boolean isLaunchDay() {
        ZonedDateTime start = parse("schedule.server-start");
        if (start == null) {
            return false;
        }
        int hour = plugin.serverShop().resetHour();
        long launchDay = start.minusHours(hour).toLocalDate().toEpochDay();
        long today = ZonedDateTime.now(plugin.serverShop().zone()).minusHours(hour).toLocalDate().toEpochDay();
        return launchDay == today;
    }

    private long millisUntil(ZonedDateTime t) {
        return t == null ? Long.MIN_VALUE : Duration.between(ZonedDateTime.now(plugin.serverShop().zone()), t).toMillis();
    }

    private String text(ZonedDateTime t) {
        return t == null ? "nicht festgelegt" : t.format(FORMAT) + " Uhr";
    }

    public boolean launchLockEnabled() {
        return plugin.getConfig().getBoolean("schedule.server-start-enabled", true) && parse("schedule.server-start") != null;
    }

    public long millisUntilLaunch() {
        return millisUntil(parse("schedule.server-start"));
    }

    public String launchText() {
        return text(parse("schedule.server-start"));
    }

    public boolean netherLockEnabled() {
        return plugin.getConfig().getBoolean("schedule.nether-enabled", true) && parse("schedule.nether-open") != null;
    }

    public long millisUntilNether() {
        return millisUntil(parse("schedule.nether-open"));
    }

    public boolean netherLocked() {
        return netherLockEnabled() && millisUntilNether() > 0;
    }

    public String netherText() {
        return text(parse("schedule.nether-open"));
    }

    // ------------------------------------------------------------------
    //  Testphase (ab 3. Dezember bis zum Serverstart)
    // ------------------------------------------------------------------

    /** Läuft gerade die Testphase? (Spieler dürfen schauen, aber nichts verändern oder erreichen.) */
    public boolean previewActive() {
        if (!launchLockEnabled() || !plugin.getConfig().getBoolean("schedule.preview-enabled", true)) {
            return false;
        }
        ZonedDateTime start = parse("schedule.preview-start");
        return start != null && millisUntil(start) <= 0 && millisUntilLaunch() > 0;
    }

    /** Gilt die Spawn-Sperre? Von Anfang an bis zum Serverstart (Spieler ohne Adminrechte kommen nicht aus dem Spawn). */
    public boolean borderActive() {
        return launchLockEnabled() && millisUntilLaunch() > 0;
    }

    public boolean borderFor(Player player) {
        return borderActive() && !player.isOp() && !player.hasPermission("coinshop.admin");
    }

    /** Gilt für diesen Spieler die Testphase? (Admins/OPs sind ausgenommen.) */
    public boolean previewFor(Player player) {
        return previewActive() && !player.isOp();
    }

    public boolean previewFor(UUID uuid) {
        return previewActive() && !Bukkit.getOfflinePlayer(uuid).isOp();
    }

    public int previewRadius() {
        return Math.max(50, plugin.getConfig().getInt("schedule.preview-radius", 150));
    }

    /** Tage seit dem Serverstart (0 vor dem Start oder ohne festgelegten Start). */
    public long daysSinceLaunch() {
        ZonedDateTime start = parse("schedule.server-start");
        if (start == null) {
            return 0;
        }
        int hour = plugin.serverShop().resetHour();
        long launchDay = start.minusHours(hour).toLocalDate().toEpochDay();
        long today = ZonedDateTime.now(plugin.serverShop().zone()).minusHours(hour).toLocalDate().toEpochDay();
        return Math.max(0, today - launchDay);
    }

    /** Startguthaben für neue Spieler: Grundwert plus ein Zuschlag je Tag seit dem Serverstart (mit Obergrenze). */
    public long startBonusNow() {
        long base = Math.max(0, plugin.getConfig().getLong("coins.start-bonus", 100));
        long perDay = Math.max(0, plugin.getConfig().getLong("coins.start-bonus-per-day", 15));
        long max = Math.max(base, plugin.getConfig().getLong("coins.start-bonus-max", 1500));
        return Math.min(max, base + perDay * daysSinceLaunch());
    }

    /** Zahlt einen vorgemerkten Startbonus aus (nach dem Serverstart). */
    public void payStartBonus(Player player) {
        Database db = plugin.database();
        String key = "startbonus_pending_" + player.getUniqueId();
        if (previewFor(player) || db.getMetaLong(key, 0) != 1) {
            return;
        }
        db.setMetaLong(key, 0);
        long bonus = startBonusNow();
        if (bonus > 0) {
            db.addCoins(player.getUniqueId(), bonus);
            db.logTx(player.getUniqueId(), player.getName(), "startbonus", bonus, "Willkommen");
            plugin.messages().send(player, "welcome-bonus", "coins", bonus);
        }
    }

    private boolean outside(Location l) {
        World main = Bukkit.getWorlds().get(0);
        if (l.getWorld() != main) {
            return true;
        }
        Location c = main.getSpawnLocation();
        double dx = l.getX() - c.getX();
        double dz = l.getZ() - c.getZ();
        int r = previewRadius();
        return dx * dx + dz * dz > (double) r * r;
    }

    @EventHandler
    public void onPreviewJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (borderFor(p) && outside(p.getLocation())) {
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> updateBorder(p), 5L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        Location to = event.getTo();
        if (to == null || !borderFor(p)) {
            return;
        }
        if (!outside(to)) {
            return;
        }
        if (outside(event.getFrom())) {
            // schon außerhalb (z. B. durch Fahrzeug oder Lag): zurück zum Spawn
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (now - menuCooldown.getOrDefault(p.getUniqueId(), 0L) > 3000) {
            menuCooldown.put(p.getUniqueId(), now);
            plugin.messages().send(p, "preview-border", "radius", previewRadius());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (event.getTo() == null || !borderFor(p)) {
            return;
        }
        if (outside(event.getTo())) {
            event.setCancelled(true);
            plugin.messages().send(p, "preview-border", "radius", previewRadius());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewSpawn(CreatureSpawnEvent event) {
        if (!previewActive()) {
            return;
        }
        switch (event.getSpawnReason()) {
            case COMMAND, CUSTOM, SPAWNER_EGG -> { } // nur Admins können das auslösen
            default -> event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && previewFor(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player p && previewFor(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewBreak(BlockBreakEvent event) {
        if (previewFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewPlace(BlockPlaceEvent event) {
        if (previewFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewBucketEmpty(PlayerBucketEmptyEvent event) {
        if (previewFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewBucketFill(PlayerBucketFillEvent event) {
        if (previewFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewDrop(PlayerDropItemEvent event) {
        if (previewFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && previewFor(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player p && previewFor(p)) {
            event.setCancelled(true);
        }
    }

    /** Truhen, Händler und Co. bleiben zu - die Menüs von CoinShop aber nicht. */
    @EventHandler(ignoreCancelled = true)
    public void onPreviewInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p) || !previewFor(p)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockInventoryHolder || holder instanceof org.bukkit.block.Container
                || holder instanceof org.bukkit.entity.Entity) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    //  Serverstart: vorher nur Admins
    // ------------------------------------------------------------------

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!launchLockEnabled()) {
            return;
        }
        long left = millisUntilLaunch();
        if (left <= 0 || previewActive()) {
            return;
        }
        if (Bukkit.getOfflinePlayer(event.getUniqueId()).isOp()) {
            return;
        }
        boolean hasPreview = plugin.getConfig().getBoolean("schedule.preview-enabled", true)
                && parse("schedule.preview-start") != null;
        long untilPreview = hasPreview ? millisUntil(parse("schedule.preview-start")) : left;
        Component kick = plugin.messages().get("server-not-open",
                "date", hasPreview ? text(parse("schedule.preview-start")) : launchText(),
                "left", ItemUtil.formatDuration(untilPreview),
                "launch", launchText());
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kick);
    }

    // ------------------------------------------------------------------
    //  Nether-Sperre
    // ------------------------------------------------------------------

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL || !netherLocked()
                || event.getPlayer().hasPermission("coinshop.netherbypass")) {
            return;
        }
        // Nur der Weg IN den Nether ist gesperrt
        if (event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getEnvironment() == World.Environment.NETHER) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now - menuCooldown.getOrDefault(player.getUniqueId(), 0L) > 3000) {
            menuCooldown.put(player.getUniqueId(), now);
            // Wichtig: Solange der Spieler IM Portal steht, schließt der Minecraft-Client jedes Menü sofort
            // wieder (deshalb war es nur ein Blitz). Also erst aus dem Portal schieben, dann das Menü öffnen.
            Bukkit.getScheduler().runTask(plugin, () -> {
                pushOutOfPortal(player);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        new de.coinshop.gui.TimelineMenu(plugin, player).open(player);
                    }
                }, 10L);
            });
        }
    }

    /** Setzt den Spieler 2-4 Blöcke neben das Portal (auf freien Boden), damit er nicht mehr im Portal steht. */
    private void pushOutOfPortal(Player player) {
        org.bukkit.Location base = player.getLocation();
        org.bukkit.util.Vector facing = base.getDirection().setY(0);
        if (facing.lengthSquared() < 0.01) {
            facing = new org.bukkit.util.Vector(0, 0, 1);
        }
        facing.normalize().multiply(-1); // rückwärts, weg vom Portal
        org.bukkit.util.Vector[] dirs = {
                facing,
                new org.bukkit.util.Vector(1, 0, 0), new org.bukkit.util.Vector(-1, 0, 0),
                new org.bukkit.util.Vector(0, 0, 1), new org.bukkit.util.Vector(0, 0, -1)
        };
        for (int dist = 2; dist <= 4; dist++) {
            for (org.bukkit.util.Vector dir : dirs) {
                org.bukkit.Location target = base.clone().add(dir.clone().multiply(dist));
                org.bukkit.block.Block feet = target.getBlock();
                org.bukkit.block.Block head = feet.getRelative(0, 1, 0);
                org.bukkit.block.Block ground = feet.getRelative(0, -1, 0);
                boolean free = feet.isPassable() && head.isPassable()
                        && feet.getType() != org.bukkit.Material.NETHER_PORTAL
                        && head.getType() != org.bukkit.Material.NETHER_PORTAL
                        && !feet.isLiquid() && ground.getType().isSolid()
                        && ground.getType() != org.bukkit.Material.NETHER_PORTAL;
                if (free) {
                    target.setX(feet.getX() + 0.5);
                    target.setZ(feet.getZ() + 0.5);
                    target.setY(feet.getY());
                    target.setYaw(base.getYaw());
                    target.setPitch(base.getPitch());
                    player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    return;
                }
            }
        }
        // Notfall: eine Position zurück im Raum (Spawn)
        player.teleport(player.getWorld().getSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (!netherLocked() || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getTo().getWorld().getEnvironment() == World.Environment.NETHER
                && event.getFrom().getWorld().getEnvironment() != World.Environment.NETHER) {
            event.setCancelled(true);
        }
    }

    /** Auch /tp und andere Wege in den Nether sind vor der Öffnung gesperrt. */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent || !netherLocked() || event.getTo() == null
                || event.getTo().getWorld() == null || event.getPlayer().hasPermission("coinshop.netherbypass")) {
            return;
        }
        if (event.getTo().getWorld().getEnvironment() == World.Environment.NETHER
                && event.getFrom().getWorld() != event.getTo().getWorld()) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "nether-locked", "date", netherText());
        }
    }
}
