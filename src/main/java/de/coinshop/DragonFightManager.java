package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Der Endkampf.
 *
 * - Bis zum vereinbarten Datum ist das End gesperrt. Wer ins End-Portal geht, bekommt stattdessen das
 *   Anmelde-Menü (EndFightMenu).
 * - Zur Startzeit werden alle angemeldeten Spieler, die online sind, auf die End-Insel teleportiert. Dort wird der
 *   Drache mit einer Beschwörung (Partikel-Strahlen, Blitze, Geräusche) erweckt.
 * - Je mehr Kämpfer, desto stärker der Drache: mehr Leben, mehr Schaden und häufigere, zahlreichere Angriffe.
 * - Der Drache bekommt neue Angriffe: Feuerball-Salven, Blitzschläge, Phantom-Schwärme und einen Schrei.
 * - Danach ist das End für alle offen. Wer beim Tod des Drachen im End ist, bekommt eine Belohnung.
 *
 * Der Zustand (0 = wartet, 1 = läuft, 2 = vorbei) steht in der Datenbank und überlebt Neustarts.
 */
public final class DragonFightManager implements Listener {

    private static final String STATE_KEY = "endfight_state";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    /** Spieler, die gerade vom Plugin ins End teleportiert werden (dürfen die Sperre umgehen). */
    private final Set<UUID> teleporting = new HashSet<>();
    /** Wer beim Start dabei war. */
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Long> menuCooldown = new HashMap<>();
    /** Phantome und Co., die der Drache gerufen hat. */
    private final Set<UUID> minions = new HashSet<>();
    private final Set<Integer> announced = new HashSet<>();

    private UUID dragonId;
    /** Unsere eigene Lebensanzeige des Drachen (mit genauen Lebenspunkten). */
    private BossBar healthBar;
    // Leistung im Kampf: Schaden am Drachen und zerstörte End-Kristalle je Spieler
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final Map<UUID, Integer> crystalsDestroyed = new HashMap<>();
    private final Set<UUID> countedCrystals = new HashSet<>();
    private BukkitTask loop;
    private BukkitTask hideLoop;
    private boolean revealed;
    private int fighters = 1;
    private int abilityTimer;
    private boolean enraged;
    private boolean lastStand;
    private long lastNoPlayerLog;

    public DragonFightManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Einstellungen und Zustand
    // ------------------------------------------------------------------

    private boolean enabled() {
        return plugin.getConfig().getBoolean("end-fight.enabled", true);
    }

    public int state() {
        return (int) plugin.database().getMetaLong(STATE_KEY, 0);
    }

    public void setState(int state) {
        plugin.database().setMetaLong(STATE_KEY, state);
    }

    /** Ist das End gerade gesperrt? */
    public boolean locked() {
        return enabled() && state() == 0;
    }

    /** Startzeitpunkt aus der config ("2026-12-24 20:00", Zeitzone wie beim Shop). */
    public ZonedDateTime startTime() {
        String text = plugin.getConfig().getString("end-fight.start", "");
        try {
            return LocalDateTime.parse(text.trim().replace(' ', 'T')).atZone(plugin.serverShop().zone());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String startText() {
        ZonedDateTime t = startTime();
        return t == null ? "noch nicht festgelegt" : t.format(FORMAT) + " Uhr";
    }

    /** Millisekunden bis zum Start (negativ = Zeit ist schon erreicht), oder Long.MAX_VALUE ohne Datum. */
    public long millisUntilStart() {
        ZonedDateTime t = startTime();
        return t == null ? Long.MAX_VALUE : Duration.between(ZonedDateTime.now(plugin.serverShop().zone()), t).toMillis();
    }

    public int signupCount() {
        return plugin.database().signedUp().size();
    }

    private World endWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.THE_END) {
                return w;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Start / Takt
    // ------------------------------------------------------------------

    /** Wird beim Einschalten des Plugins aufgerufen. */
    public void start() {
        // alle 5 Sekunden: Startzeit prüfen und Erinnerungen senden
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
        // Nach einem Neustart mitten im Kampf: Drachen wiederfinden
        Bukkit.getScheduler().runTaskLater(plugin, this::resume, 20L * 15);
    }

    private void tick() {
        if (!enabled() || state() != 0) {
            return;
        }
        long left = millisUntilStart();
        if (left == Long.MAX_VALUE) {
            return;
        }
        long seconds = left / 1000;
        for (int mark : new int[]{3600, 600, 60, 10}) {
            if (seconds <= mark && seconds > mark - 6 && announced.add(mark)) {
                remind(mark);
            }
        }
        if (left <= 0) {
            beginFight(false);
        }
    }

    private void remind(int seconds) {
        String text = seconds >= 3600 ? "1 Stunde" : seconds >= 60 ? (seconds / 60) + " Minuten" : seconds + " Sekunden";
        Set<UUID> signed = new HashSet<>(plugin.database().signedUp());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (signed.contains(p.getUniqueId())) {
                plugin.messages().send(p, "endfight-reminder", "time", text);
            } else if (seconds >= 600) {
                plugin.messages().send(p, "endfight-reminder-all", "time", text);
            }
        }
    }

    /** Beginnt den Kampf. "force" = auch ohne angemeldete Spieler (Admin-Befehl). Gibt true zurück, wenn er begonnen hat. */
    public boolean beginFight(boolean force) {
        World end = endWorld();
        if (end == null) {
            plugin.getLogger().warning("Endkampf: keine End-Welt gefunden.");
            return false;
        }
        List<Player> team = new ArrayList<>();
        Set<UUID> signed = new HashSet<>(plugin.database().signedUp());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (signed.contains(p.getUniqueId())) {
                team.add(p);
            }
        }
        if (team.isEmpty() && !force) {
            long now = System.currentTimeMillis();
            if (now - lastNoPlayerLog > 600_000L) {
                lastNoPlayerLog = now;
                plugin.getLogger().info("Endkampf: Die Startzeit ist erreicht, aber kein angemeldeter Spieler ist online. Warte ...");
            }
            return false;
        }

        setState(1);
        participants.clear();
        minions.clear();
        revealed = false;
        enraged = false;
        lastStand = false;
        abilityTimer = 0;
        dragonId = null;

        // Team auf einen Ring um die Mitte der Insel verteilen
        int index = 0;
        for (Player p : team) {
            double angle = Math.PI * 2 * index / Math.max(1, team.size());
            Location target = ringLocation(end, angle, 26);
            teleporting.add(p.getUniqueId());
            try {
                p.teleport(target);
            } finally {
                teleporting.remove(p.getUniqueId());
            }
            participants.add(p.getUniqueId());
            index++;
        }
        fighters = Math.max(1, team.size());
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(p, "endfight-begin", "count", fighters);
        }
        for (Player p : team) {
            p.showTitle(Title.title(plugin.messages().get("endfight-title"),
                    plugin.messages().get("endfight-subtitle"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))));
        }
        runCeremony(end);
        return true;
    }

    private Location ringLocation(World end, double angle, double radius) {
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        int y = end.getHighestBlockYAt((int) x, (int) z);
        if (y < 10) { // dort ist nichts (Leere) -> näher an die Mitte
            x *= 0.4;
            z *= 0.4;
            y = end.getHighestBlockYAt((int) x, (int) z);
        }
        Location loc = new Location(end, x + 0.5, Math.max(y, 60) + 1, z + 0.5);
        loc.setDirection(new Vector(-x, 0, -z)); // zur Mitte schauen
        return loc;
    }

    // ------------------------------------------------------------------
    //  Beschwörung
    // ------------------------------------------------------------------

    private void runCeremony(World end) {
        // Glockenschläge: alle 3 Sekunden einer, nach dem 10. erscheint der Drache
        final int bells = Math.max(1, plugin.getConfig().getInt("end-fight.bells", 10));
        final int bellTicks = Math.max(20, plugin.getConfig().getInt("end-fight.bell-interval-seconds", 3) * 20);
        int total = bells * bellTicks;
        final int[] t = {0};
        final double[][] beams = {{12, 12}, {-12, 12}, {12, -12}, {-12, -12}};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (t[0] % 20 == 0) {
                freezeDragon(end); // Drache festhalten, unsichtbar und unverwundbar, bis es so weit ist
            }
            double progress = t[0] / (double) total;
            if (t[0] % 2 == 0) {
                // Vier Strahlen wie Leuchtfeuer
                for (double[] b : beams) {
                    int base = end.getHighestBlockYAt((int) b[0], (int) b[1]);
                    base = Math.max(base, 55);
                    for (int dy = 0; dy < 70; dy += 2) {
                        fx(end, Particle.END_ROD, b[0], base + dy, b[1], 1, 0.05);
                    }
                }
                // Spirale, die sich zusammenzieht und nach oben windet
                for (int arm = 0; arm < 3; arm++) {
                    double angle = t[0] * 0.18 + arm * Math.PI * 2 / 3;
                    double radius = 20 * (1 - progress) + 2;
                    double height = 70 + 40 * progress;
                    fx(end, Particle.PORTAL, Math.cos(angle) * radius, height, Math.sin(angle) * radius,
                            12, 0.4);
                    fx(end, Particle.END_ROD, Math.cos(angle) * radius, height, Math.sin(angle) * radius, 1, 0.1);
                }
                // Bodenring
                if (t[0] % 20 == 0) {
                    double r = 4 + 20 * progress;
                    for (int i = 0; i < 40; i++) {
                        double a = Math.PI * 2 * i / 40;
                        fx(end, Particle.REVERSE_PORTAL, Math.cos(a) * r, 66, Math.sin(a) * r, 2, 0.1);
                    }
                }
            }
            // Bis zum Erscheinen des Drachen sind NUR die Glockenschläge zu hören (keine anderen Geräusche).
            // Glockenschlag alle 3 Sekunden (der erste nach 3 Sekunden, der letzte lässt den Drachen erwachen)
            if (t[0] > 0 && t[0] % bellTicks == 0) {
                int number = t[0] / bellTicks;
                float pitch = 0.6f + 0.1f * number;
                broadcastSound(end, Sound.BLOCK_BELL_USE, 2.0f, Math.min(2.0f, pitch));
                broadcastSound(end, Sound.BLOCK_BELL_RESONATE, 1.5f, Math.min(2.0f, pitch));
                for (Player p : end.getPlayers()) {
                    p.sendActionBar(plugin.messages().get("endfight-bell", "number", number, "total", bells));
                    if (number >= bells - 2) {
                        p.showTitle(Title.title(Component.text(bells - number == 0 ? "\u2620" : String.valueOf(bells - number)),
                                plugin.messages().get("endfight-countdown-sub"),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(200))));
                    }
                }
            }
            t[0]++;
            if (t[0] > total) { // erst nach dem letzten Glockenschlag
                holder[0].cancel();
                reveal(end);
            }
        }, 20L, 1L);
    }

    /** Sucht den Drachen der End-Welt (oder erzeugt einen, wenn es keinen gibt). */
    private EnderDragon findDragon(World end, boolean create) {
        for (EnderDragon d : end.getEntitiesByClass(EnderDragon.class)) {
            return d;
        }
        if (create) {
            return end.spawn(new Location(end, 0.5, 100, 0.5), EnderDragon.class);
        }
        return null;
    }

    private void freezeDragon(World end) {
        EnderDragon dragon = findDragon(end, false);
        if (dragon == null) {
            return;
        }
        dragon.setInvulnerable(true);
        dragon.setInvisible(true);
        dragon.setSilent(true); // kein Flügelschlagen oder Brüllen vor dem Erwachen
        dragon.setAI(false);
        dragon.teleport(new Location(end, 0.5, 105, 0.5));
        try {
            DragonBattle battle = end.getEnderDragonBattle();
            if (battle != null) {
                battle.getBossBar().setVisible(false);
            }
        } catch (RuntimeException ignored) {
            // ohne Kampf-Anzeige geht es auch
        }
    }

    /** Der große Moment: Der Drache erwacht, bekommt seine Stärke und der Kampf beginnt. */
    private void reveal(World end) {
        Location center = new Location(end, 0.5, 105, 0.5);
        EnderDragon dragon = findDragon(end, true);
        dragonId = dragon.getUniqueId();

        // Knall: Explosion, Blitze, Schrei
        fx(end, Particle.EXPLOSION_EMITTER, 0.5, 105, 0.5, 3, 2.0);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i;
            end.strikeLightningEffect(new Location(end, Math.cos(a) * 12, end.getHighestBlockYAt((int) (Math.cos(a) * 12),
                    (int) (Math.sin(a) * 12)) + 1, Math.sin(a) * 12));
        }
        broadcastSound(end, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f);
        broadcastSound(end, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        // Stärke nach Anzahl der Kämpfer
        double hp = plugin.getConfig().getDouble("end-fight.health-base", 300)
                + (fighters - 1) * plugin.getConfig().getDouble("end-fight.health-per-player", 150);
        hp = Math.min(hp, plugin.getConfig().getDouble("end-fight.health-max", 1000));
        AttributeInstance max = dragon.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(hp);
        }
        dragon.setHealth(Math.min(hp, max == null ? hp : max.getValue()));
        dragon.setInvulnerable(false);
        dragon.setInvisible(false);
        dragon.setSilent(false);
        dragon.setAI(true);
        dragon.teleport(center);
        try {
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
        } catch (RuntimeException ignored) {
            // egal
        }
        try {
            DragonBattle battle = end.getEnderDragonBattle();
            if (battle != null) {
                battle.getBossBar().setVisible(false); // wir zeigen unsere eigene, genauere Anzeige
            }
        } catch (RuntimeException ignored) {
            // egal
        }
        revealed = true;
        damageDealt.clear();
        crystalsDestroyed.clear();
        countedCrystals.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(p, "endfight-reveal", "count", fighters, "health", Math.round(hp));
        }
        startLoop();
    }

    // ------------------------------------------------------------------
    //  Kampf
    // ------------------------------------------------------------------

    private void startLoop() {
        if (loop != null) {
            loop.cancel();
        }
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::fightTick, 20L, 10L);
        if (hideLoop != null) {
            hideLoop.cancel();
        }
        // Der Server blendet seine eigene Leiste staendig wieder ein: deshalb alle 2 Ticks wieder verstecken
        hideLoop = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World end = endWorld();
            if (end != null && revealed) {
                hideVanillaBar(end);
            }
        }, 2L, 2L);
    }

    private void stopHideLoop() {
        if (hideLoop != null) {
            hideLoop.cancel();
            hideLoop = null;
        }
    }

    /** Nach einem Neustart: läuft der Kampf noch? Dann den Drachen wiederfinden und weitermachen. */
    private void resume() {
        if (!enabled() || state() != 1) {
            return;
        }
        World end = endWorld();
        if (end == null) {
            return;
        }
        EnderDragon dragon = findDragon(end, false);
        if (dragon == null || dragon.isDead()) {
            return;
        }
        dragonId = dragon.getUniqueId();
        revealed = true;
        fighters = Math.max(1, end.getPlayers().size());
        startLoop();
    }

    private List<Player> fightersOnline(World end) {
        List<Player> list = new ArrayList<>();
        for (Player p : end.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                list.add(p);
            }
        }
        return list;
    }

    private EnderDragon currentDragon() {
        if (dragonId == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(dragonId);
        return e instanceof EnderDragon d && !d.isDead() ? d : null;
    }

    /** Lebensanzeige des Drachen: Balken mit genauen Lebenspunkten für alle im End. */
    private void updateHealthBar(World end, EnderDragon dragon) {
        hideVanillaBar(end); // sonst gibt es zwei Lebensleisten
        AttributeInstance maxAttr = dragon.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? 200 : maxAttr.getValue();
        double hp = Math.max(0, dragon.getHealth());
        double ratio = Math.max(0.0, Math.min(1.0, hp / max));
        if (healthBar == null) {
            healthBar = Bukkit.createBossBar("", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        }
        healthBar.setProgress(ratio);
        healthBar.setColor(ratio > 0.5 ? BarColor.PURPLE : ratio > 0.25 ? BarColor.YELLOW : BarColor.RED);
        healthBar.setTitle("§5§lEnderdrache §f" + Math.round(hp) + " §7/ §f" + Math.round(max)
                + " §c\u2764 §7(" + Math.round(ratio * 100) + " %)");
        for (Player p : end.getPlayers()) {
            if (!healthBar.getPlayers().contains(p)) {
                healthBar.addPlayer(p);
            }
        }
        for (Player p : new ArrayList<>(healthBar.getPlayers())) {
            if (p.getWorld() != end) {
                healthBar.removePlayer(p);
            }
        }
    }

    /** Versteckt die Standard-Leiste des Drachenkampfs (immer wieder, weil der Server sie sonst zurückholt). */
    private void hideVanillaBar(World end) {
        try {
            DragonBattle battle = end.getEnderDragonBattle();
            if (battle != null) {
                BossBar vanilla = battle.getBossBar();
                vanilla.setVisible(false);
                vanilla.removeAll();
            }
        } catch (RuntimeException ignored) {
            // ohne geht es auch
        }
    }

    private void removeHealthBar() {
        if (healthBar != null) {
            healthBar.removeAll();
            healthBar = null;
        }
    }

    private void fightTick() {
        World end = endWorld();
        EnderDragon dragon = currentDragon();
        if (end == null || dragon == null) {
            return; // Tod wird über das EntityDeathEvent erkannt
        }
        updateHealthBar(end, dragon);
        List<Player> targets = fightersOnline(end);
        if (targets.isEmpty()) {
            return;
        }
        AttributeInstance maxAttr = dragon.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? 200 : maxAttr.getValue();
        double ratio = dragon.getHealth() / max;

        // Wut bei 50 % und letzte Wut bei 25 %
        if (!enraged && ratio < 0.5) {
            enraged = true;
            announceToEnd(end, "endfight-enrage");
            broadcastSound(end, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.4f);
        }
        if (!lastStand && ratio < 0.25) {
            lastStand = true;
            announceToEnd(end, "endfight-laststand");
            broadcastSound(end, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);
            swarm(end, dragon, targets, 4 + fighters); // Verstärkung
        }

        // Angriffe: je mehr Spieler und je wütender, desto häufiger
        int interval = (int) (160 / (1.0 + 0.2 * (fighters - 1)));
        interval = Math.max(50, enraged ? interval / 2 : interval);
        abilityTimer += 10;
        if (abilityTimer >= interval) {
            abilityTimer = 0;
            switch (random.nextInt(4)) {
                case 0 -> fireballs(end, dragon, targets);
                case 1 -> lightning(end, dragon, targets);
                case 2 -> swarm(end, dragon, targets, 2 + fighters / 2);
                default -> roar(end, dragon, targets);
            }
        }
    }

    /** Feuerball-Salve auf zufällige Kämpfer. */
    private void fireballs(World end, EnderDragon dragon, List<Player> targets) {
        int shots = Math.min(8, 2 + fighters / 2 + (enraged ? 2 : 0));
        Location from = dragon.getLocation().add(0, 2, 0);
        broadcastSound(end, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.0f);
        for (int i = 0; i < shots; i++) {
            Player target = targets.get(random.nextInt(targets.size()));
            Vector dir = target.getLocation().add(random.nextDouble() * 4 - 2, 1, random.nextDouble() * 4 - 2)
                    .toVector().subtract(from.toVector()).normalize();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (dragon.isDead()) {
                    return;
                }
                end.spawn(from, DragonFireball.class, fb -> {
                    fb.setShooter(dragon);
                    fb.setDirection(dir);
                });
            }, i * 6L);
        }
    }

    /** Blitzschläge: erst eine Warnung am Boden, dann schlägt es ein. */
    private void lightning(World end, EnderDragon dragon, List<Player> targets) {
        int strikes = Math.min(targets.size(), 1 + fighters / 3 + (enraged ? 1 : 0));
        List<Player> pool = new ArrayList<>(targets);
        double damage = 6 * damageMultiplier();
        for (int i = 0; i < strikes && !pool.isEmpty(); i++) {
            Player target = pool.remove(random.nextInt(pool.size()));
            Location mark = target.getLocation();
            for (int k = 0; k < 24; k++) {
                double a = Math.PI * 2 * k / 24;
                fx(end, Particle.ELECTRIC_SPARK, mark.getX() + Math.cos(a) * 3, mark.getY() + 0.2,
                        mark.getZ() + Math.sin(a) * 3, 2, 0.1);
            }
            target.playSound(mark, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.6f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                end.strikeLightningEffect(mark);
                broadcastSound(end, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);
                for (Player p : end.getPlayers()) {
                    if (p.getLocation().distanceSquared(mark) <= 9 && !dragon.isDead()) {
                        p.damage(damage, dragon);
                    }
                }
            }, 30L);
        }
    }

    /** Ein Schwarm Phantome. */
    private void swarm(World end, EnderDragon dragon, List<Player> targets, int count) {
        int cap = 6 + fighters * 2;
        minions.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || e.isDead();
        });
        Player around = targets.get(random.nextInt(targets.size()));
        for (int i = 0; i < count && minions.size() < cap; i++) {
            Location at = around.getLocation().add(random.nextDouble() * 16 - 8, 6 + random.nextDouble() * 5,
                    random.nextDouble() * 16 - 8);
            Phantom phantom = end.spawn(at, Phantom.class, ph -> ph.setSize(2));
            minions.add(phantom.getUniqueId());
        }
        broadcastSound(end, Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.5f);
    }

    /** Der Schrei: wirft alle Kämpfer in der Nähe zurück und macht sie kurz blind und schwach. */
    private void roar(World end, EnderDragon dragon, List<Player> targets) {
        broadcastSound(end, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);
        Location center = dragon.getLocation();
        fx(end, Particle.EXPLOSION_EMITTER, center.getX(), center.getY(), center.getZ(), 1, 0.5);
        for (Player p : targets) {
            if (p.getLocation().distanceSquared(center) > 45 * 45) {
                continue;
            }
            Vector away = p.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (away.lengthSquared() < 0.01) {
                away = new Vector(1, 0, 0);
            }
            p.setVelocity(away.normalize().multiply(1.1).setY(0.45));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 140, 0));
        }
    }

    /** Wie viel mehr Schaden der Drache anrichtet (je Kämpfer mehr). */
    private double damageMultiplier() {
        double perPlayer = plugin.getConfig().getDouble("end-fight.damage-per-player-percent", 12) / 100.0;
        double max = plugin.getConfig().getDouble("end-fight.damage-max-percent", 250) / 100.0;
        return Math.min(max, 1.0 + (fighters - 1) * perPlayer);
    }

    @EventHandler
    public void onDragonDamage(EntityDamageByEntityEvent event) {
        if (!revealed || dragonId == null) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager.getUniqueId().equals(dragonId)) {
            event.setDamage(event.getDamage() * damageMultiplier());
        }
    }

    /** Auch der Drachenatem (Giftwolken) wird mit jedem Kämpfer stärker. */
    @EventHandler
    public void onBreathDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!revealed || dragonId == null || event instanceof EntityDamageByEntityEvent
                || event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.DRAGON_BREATH
                || !(event.getEntity() instanceof Player)) {
            return;
        }
        event.setDamage(event.getDamage() * damageMultiplier());
    }

    /** Merkt sich, wer wie viel Schaden am Drachen macht und wer End-Kristalle zerstört. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContribution(EntityDamageByEntityEvent event) {
        if (state() != 1) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!(damager instanceof Player player)) {
            return;
        }
        Entity target = event.getEntity();
        if (target instanceof EnderDragon && target.getUniqueId().equals(dragonId)) {
            damageDealt.merge(player.getUniqueId(), Math.max(0, event.getFinalDamage()), Double::sum);
        } else if (target instanceof EnderCrystal crystal && countedCrystals.add(crystal.getUniqueId())) {
            crystalsDestroyed.merge(player.getUniqueId(), 1, Integer::sum);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || state() != 1) {
            return;
        }
        if (dragon.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        victory(dragon.getWorld());
    }

    private void victory(World end) {
        removeHealthBar();
        setState(2);
        revealed = false;
        stopHideLoop();
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        for (UUID id : new ArrayList<>(minions)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        minions.clear();
        // Belohnung: Jeder Überlebende bekommt mindestens "reward-min". Dazu wird ein Topf (pool-per-player x Überlebende)
        // nach Leistung verteilt: Schaden am Drachen und zerstörte Kristalle (Gewichtung in der config.yml).
        long minReward = Math.max(0, plugin.getConfig().getLong("end-fight.reward-min", 1000));
        long poolPerPlayer = Math.max(0, plugin.getConfig().getLong("end-fight.reward-pool-per-player", 4000));
        double damageWeight = Math.max(0, plugin.getConfig().getDouble("end-fight.reward-damage-weight", 60));
        double crystalWeight = Math.max(0, plugin.getConfig().getDouble("end-fight.reward-crystal-weight", 40));
        if (damageWeight + crystalWeight <= 0) {
            damageWeight = 1;
        }
        List<Player> survivors = new ArrayList<>();
        for (Player p : end.getPlayers()) {
            if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                survivors.add(p);
            }
        }
        double totalDamage = 0;
        double totalCrystals = 0;
        for (Player p : survivors) {
            totalDamage += damageDealt.getOrDefault(p.getUniqueId(), 0.0);
            totalCrystals += crystalsDestroyed.getOrDefault(p.getUniqueId(), 0);
        }
        long pool = poolPerPlayer * survivors.size();
        // Hat niemand Schaden (oder Kristalle) gemacht, verfällt dieser Teil nicht, sondern geht in den anderen
        double dw = totalDamage > 0 ? damageWeight : 0;
        double cw = totalCrystals > 0 ? crystalWeight : 0;
        double weightSum = dw + cw;

        List<String> names = new ArrayList<>();
        Database db = plugin.database();
        for (Player p : survivors) {
            names.add(p.getName());
            double damage = damageDealt.getOrDefault(p.getUniqueId(), 0.0);
            int crystals = crystalsDestroyed.getOrDefault(p.getUniqueId(), 0);
            double share;
            if (weightSum <= 0) {
                share = 1.0 / survivors.size(); // gar keine Zahlen: gleichmäßig
            } else {
                share = (dw * (damage / totalDamage) + cw * (totalCrystals > 0 ? crystals / totalCrystals : 0)) / weightSum;
            }
            long reward = minReward + Math.round(pool * share);
            if (reward > 0) {
                try {
                    db.addCoins(p.getUniqueId(), reward);
                    db.logTx(p.getUniqueId(), p.getName(), "endkampf", reward,
                            "Drache besiegt (Schaden " + Math.round(damage) + ", Kristalle " + crystals + ")");
                    plugin.messages().send(p, "endfight-reward", "coins", reward,
                            "damage", Math.round(damage), "crystals", crystals);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Endkampf-Belohnung fehlgeschlagen: " + e);
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(p, "endfight-victory", "players", String.join(", ", names));
        }
        broadcastSound(end, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        try {
            for (UUID id : db.signedUp()) {
                db.signOff(id);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Anmeldungen konnten nicht gelöscht werden: " + e);
        }
        participants.clear();
        damageDealt.clear();
        crystalsDestroyed.clear();
        countedCrystals.clear();
    }

    /** Admin: Kampf abbrechen (End bleibt offen) oder zurücksetzen. */
    public void stop() {
        removeHealthBar();
        setState(2);
        revealed = false;
        stopHideLoop();
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        for (UUID id : new ArrayList<>(minions)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        minions.clear();
        World end = endWorld();
        EnderDragon dragon = end == null ? null : findDragon(end, false);
        if (dragon != null) {
            dragon.setInvulnerable(false);
            dragon.setInvisible(false);
            dragon.setAI(true);
        }
    }

    public void reset() {
        stop();
        setState(0);
        announced.clear();
    }

    // ------------------------------------------------------------------
    //  Anmeldung
    // ------------------------------------------------------------------

    public boolean isSignedUp(UUID uuid) {
        return plugin.database().isSignedUp(uuid);
    }

    public void signUp(Player player) {
        plugin.database().signUp(player.getUniqueId(), player.getName());
        plugin.messages().send(player, "endfight-signed", "date", startText());
    }

    public void signOff(Player player) {
        plugin.database().signOff(player.getUniqueId());
        plugin.messages().send(player, "endfight-unsigned");
    }

    // ------------------------------------------------------------------
    //  Sperre des Endes
    // ------------------------------------------------------------------

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL || !locked()
                || event.getPlayer().hasPermission("coinshop.endbypass")) {
            return;
        }
        // Nur der Weg IN das End ist gesperrt (der Weg zurück nicht)
        if (event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getEnvironment() == World.Environment.THE_END) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now - menuCooldown.getOrDefault(player.getUniqueId(), 0L) > 3000) {
            menuCooldown.put(player.getUniqueId(), now);
            Bukkit.getScheduler().runTask(plugin, () -> new de.coinshop.gui.EndFightMenu(plugin, player).open(player));
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (!locked() || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        if (event.getTo().getWorld().getEnvironment() == World.Environment.THE_END
                && event.getFrom().getWorld().getEnvironment() != World.Environment.THE_END) {
            event.setCancelled(true);
        }
    }

    /** Auch /tp, /home und andere Wege ins End sind gesperrt, solange der Kampf nicht begonnen hat. */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent || !locked() || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (teleporting.contains(player.getUniqueId()) || player.hasPermission("coinshop.endbypass")) {
            return;
        }
        if (event.getTo().getWorld().getEnvironment() == World.Environment.THE_END
                && event.getFrom().getWorld() != event.getTo().getWorld()) {
            event.setCancelled(true);
            plugin.messages().send(player, "endfight-locked", "date", startText());
        }
    }

    // ------------------------------------------------------------------
    //  Hilfsmethoden
    // ------------------------------------------------------------------

    private void fx(World world, Particle particle, double x, double y, double z, int count, double spread) {
        try {
            world.spawnParticle(particle, x, y, z, count, spread, spread, spread, 0.0, null, true);
        } catch (RuntimeException ignored) {
            // Partikel gibt es in dieser Version nicht - dann eben ohne
        }
    }

    private void broadcastSound(World world, Sound sound, float volume, float pitch) {
        for (Player p : world.getPlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    private void announceToEnd(World end, String key) {
        for (Player p : end.getPlayers()) {
            plugin.messages().send(p, key);
        }
    }
}
