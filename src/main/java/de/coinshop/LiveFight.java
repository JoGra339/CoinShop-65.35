package de.coinshop;

import de.coinshop.CombatSim.Fighter;
import de.coinshop.CombatSim.Script;
import de.coinshop.CombatSim.Frame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Live-Ansicht für Duelle und Untergrund-Arenen: Die Zuschauer werden in eine Kampfbühne gebracht und sehen, wie die
 * Kreaturen beider Seiten gegeneinander antreten. Der Ausgang steht vorher fest (er wird berechnet wie bisher); die
 * Kreaturen sind nur die Darsteller. Danach geht es zurück und das Ergebnis wird ausgezahlt.
 */
public final class LiveFight implements Listener {

    private static final int HALF = 16;
    private static final int STAGE_TICKS = 120;
    private static final int INTRO_TICKS = 60;
    private static final int OUTRO_TICKS = 80;
    private static final int STEP = 10;

    private static final Material[][] THEMES = {
            {Material.STONE_BRICKS, Material.POLISHED_ANDESITE},
            {Material.DEEPSLATE_TILES, Material.POLISHED_DEEPSLATE},
            {Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE},
            {Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS},
            {Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE},
            {Material.POLISHED_BLACKSTONE_BRICKS, Material.POLISHED_BLACKSTONE},
            {Material.MOSSY_STONE_BRICKS, Material.MOSS_BLOCK}
    };

    /** Fliegende Kreaturen: sie starten in der Luft und werden aktiv zum Gegner geschoben (die Bühne hat eine Decke). */
    private static final Set<String> FLIERS = Set.of("BAT", "GHAST", "HAPPY_GHAST", "PHANTOM", "ALLAY", "PARROT", "BLAZE", "VEX", "BREEZE");

    /** Höhe der Bühne (Wände aus Barrieren, darüber eine Decke, damit Flieger nicht entkommen). */
    private static final int ROOF = 14;

    /** Wie hoch über dem Boden ein Kämpfer startet. */
    private static double airOf(String name) {
        if (name == null || !FLIERS.contains(name)) {
            return 0;
        }
        return name.equals("GHAST") || name.equals("HAPPY_GHAST") ? 4 : 2;
    }

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;
    private final Map<UUID, Show> watching = new HashMap<>();
    private final Set<UUID> actors = new HashSet<>();
    private final Set<Integer> usedSlots = new HashSet<>();
    private boolean registered;

    LiveFight(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
    }

    /** Sieht dieser Spieler gerade einem Kampf zu? */
    public boolean watching(UUID uuid) {
        return watching.containsKey(uuid);
    }

    private static final class Viewer {
        final UUID id;
        Location back;
        GameMode mode;

        Viewer(UUID id) {
            this.id = id;
        }
    }

    private final class Show {
        final int slot;
        final List<Viewer> viewers = new ArrayList<>();
        final List<LivingEntity> sideA = new ArrayList<>();
        final List<LivingEntity> sideB = new ArrayList<>();
        final List<Fighter> fa;
        final List<Fighter> fb;
        final Script script;
        final Runnable after;
        final Location center;
        final String title;
        int tick;
        int errors;
        int stageNo = -1;
        boolean ended;
        BukkitTask task;

        Show(int slot, List<Fighter> fa, List<Fighter> fb, Script script, Runnable after, Location center, String title) {
            this.slot = slot;
            this.fa = fa;
            this.fb = fb;
            this.script = script;
            this.after = after;
            this.center = center;
            this.title = title;
        }
    }

    /**
     * Zeigt den Kampf. viewers sind die Zuschauer (bei einem Spieler-Duell beide Spieler). Ist die Bühne nicht
     * verfügbar, wird gleich ohne Anzeige fortgefahren. after wird am Ende ausgeführt.
     */
    public void start(List<Player> viewers, String title, int theme, List<Fighter> a, List<Fighter> b, Script script, Runnable after) {
        World world = ex.showWorld();
        if (world == null || script.frames().isEmpty() || viewers.isEmpty()) {
            after.run();
            return;
        }
        if (!registered) {
            registered = true;
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
        int slot = 0;
        while (usedSlots.contains(slot)) {
            slot++;
        }
        usedSlots.add(slot);
        int cx = 40000;
        int cz = 40000 + slot * 100;
        int y = ex.showY();
        build(world, cx, cz, y, Math.floorMod(theme, THEMES.length), slot);
        Show s = new Show(slot, a, b, script, after, new Location(world, cx + 0.5, y + 1, cz + 0.5), title);
        for (Player p : viewers) {
            Viewer v = new Viewer(p.getUniqueId());
            v.back = p.getLocation().clone();
            v.mode = p.getGameMode();
            s.viewers.add(v);
            watching.put(p.getUniqueId(), s);
            p.closeInventory();
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(viewPoint(s));
            p.showTitle(Title.title(Component.text(title, NamedTextColor.GOLD),
                    Component.text("Du siehst zu - gleich geht es los", NamedTextColor.GRAY)));
        }
        spawnAll(s, world);
        int shown = 0;
        for (LivingEntity e : s.sideA) {
            shown += e == null ? 0 : 1;
        }
        for (LivingEntity e : s.sideB) {
            shown += e == null ? 0 : 1;
        }
        plugin.getLogger().info("Live-Kampf (" + title + "): " + shown + " von " + (a.size() + b.size()) + " Kämpfern erschienen.");
        for (Player p : viewers) {
            p.sendMessage(Component.text("Seite A (grün): ", NamedTextColor.GREEN).append(Component.text(names(a), NamedTextColor.WHITE)));
            p.sendMessage(Component.text("Seite B (rot): ", NamedTextColor.RED).append(Component.text(names(b), NamedTextColor.WHITE)));
            p.sendMessage(Component.text("Über jedem Kämpfer: ❤ Leben, ✦ übrige Fähigkeits-Einsätze, ✚ übrige Heil-Einsätze.", NamedTextColor.GRAY));
        }
        if (shown == 0) {
            plugin.getLogger().warning("Live-Kampf (" + title + "): keine Kämpfer konnten erzeugt werden, der Kampf wird ohne Anzeige ausgewertet.");
            for (Player p : viewers) {
                p.sendMessage(Component.text("Die Kämpfer konnten nicht erscheinen. Der Kampf wird ohne Anzeige ausgewertet.", NamedTextColor.RED));
            }
            finish(s);
            return;
        }
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                step(s);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Live-Kampf (" + title + "): Fehler im Ablauf: " + e);
                if (++s.errors >= 3) {
                    finish(s); // lieber sauber beenden, als den Spieler im Zuschauermodus festzuhalten
                }
            }
        }, STEP, STEP);
    }

    private static String names(List<Fighter> list) {
        StringBuilder sb = new StringBuilder();
        for (Fighter f : list) {
            sb.append(sb.length() > 0 ? ", " : "").append(f.name()).append(f.support() ? " (Heiler)" : f.defender() ? " (Verteidiger)" : "")
                    .append(f.back() ? " [hinten]" : "");
        }
        return sb.toString();
    }

    private Location viewPoint(Show s) {
        Location l = s.center.clone().add(0, 5, -(HALF + 2));
        l.setYaw(0f);
        l.setPitch(25f);
        return l;
    }

    // ------------------------------------------------------------------
    //  Bühne
    // ------------------------------------------------------------------

    private void build(World w, int cx, int cz, int y, int theme, int slot) {
        // Die Bühne wird bei jedem Kampf neu gebaut (sonst fehlt sie, falls der Boden inzwischen weg ist und die Kämpfer ins Leere fallen)
        Material floor = THEMES[theme][0];
        Material floor2 = THEMES[theme][1];
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                boolean lamp = dx % 8 == 0 && dz % 8 == 0;
                Material m = lamp ? Material.SEA_LANTERN : edge || (dx + dz) % 2 == 0 ? floor2 : floor;
                w.getBlockAt(cx + dx, y, cz + dz).setType(m, false);
                for (int h = 1; h <= ROOF + 1; h++) {
                    Material up = Material.AIR;
                    if (h == ROOF + 1) {
                        up = Material.BARRIER; // Decke
                    } else if (edge) {
                        up = h == 1 ? floor2 : Material.BARRIER;
                    }
                    w.getBlockAt(cx + dx, y + h, cz + dz).setType(up, false);
                }
            }
        }
    }

    /** Vor dem Kampf: wer fehlt oder verschwunden ist, wird noch einmal erzeugt. */
    private void respawnMissing(Show s) {
        World world = s.center.getWorld();
        if (world == null) {
            return;
        }
        int missing = 0;
        for (int side = 0; side < 2; side++) {
            List<LivingEntity> ents = side == 0 ? s.sideA : s.sideB;
            List<Fighter> list = side == 0 ? s.fa : s.fb;
            int dir = side == 0 ? -1 : 1;
            for (int i = 0; i < ents.size() && i < list.size(); i++) {
                LivingEntity e = ents.get(i);
                if (e != null && e.isValid() && !e.isDead()) {
                    continue;
                }
                Fighter f = list.get(i);
                Location at = s.center.clone().add(rowX(f, dir, false), airOf(f.entity()), rowZ(list, i));
                at.setYaw(dir < 0 ? -90f : 90f);
                LivingEntity n = spawnOne(world, at, f);
                ents.set(i, n);
                missing++;
                if (n != null) {
                    actors.add(n.getUniqueId());
                    label(n, f, dir < 0 ? NamedTextColor.GREEN : NamedTextColor.RED, 1.0, 2, 2);
                }
            }
        }
        if (missing > 0) {
            plugin.getLogger().warning("Live-Kampf (" + s.title + "): " + missing + " Kämpfer waren verschwunden und wurden neu erzeugt.");
        }
    }

    private void spawnAll(Show s, World world) {
        spawnSide(s, world, s.fa, s.sideA, -1, NamedTextColor.GREEN);
        spawnSide(s, world, s.fb, s.sideB, 1, NamedTextColor.RED);
    }

    /** x-Abstand zur Mitte: vordere Reihe näher am Gegner, hintere weiter weg. */
    private static double rowX(Fighter f, int dir, boolean fighting) {
        double d = fighting ? (f.back() ? 8 : 3) : (f.back() ? 12 : 7);
        return dir * d;
    }

    /** z-Position innerhalb der eigenen Reihe (gleichmäßig verteilt). */
    private static double rowZ(List<Fighter> list, int index) {
        boolean back = list.get(index).back();
        int n = 0;
        int pos = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).back() == back) {
                if (i < index) {
                    pos++;
                }
                n++;
            }
        }
        double spacing = n <= 1 ? 0 : Math.min(3.0, (HALF * 2 - 6.0) / (n - 1));
        return n <= 1 ? 0 : (pos - (n - 1) / 2.0) * spacing;
    }

    private void spawnSide(Show s, World world, List<Fighter> list, List<LivingEntity> out, int dir, NamedTextColor color) {
        for (int i = 0; i < list.size(); i++) {
            Fighter f = list.get(i);
            Location at = s.center.clone().add(rowX(f, dir, false), airOf(f.entity()), rowZ(list, i));
            at.setYaw(dir < 0 ? -90f : 90f);
            LivingEntity e = spawnOne(world, at, f);
            out.add(e);
            if (e != null) {
                actors.add(e.getUniqueId());
                label(e, f, color, 1.0, 2, 2);
                if (e instanceof Mob m) {
                    m.setAware(false);
                }
            }
        }
    }

    private LivingEntity spawnOne(World world, Location at, Fighter f) {
        String name = f.entity();
        if (name == null) {
            name = "ZOMBIE"; // nur wenn keine Kreatur bekannt ist
        }
        for (String tryName : new String[]{name, "ZOMBIE"}) {
            try {
                Entity e = ex.spawnShow(at, EntityType.valueOf(tryName));
                if (e instanceof LivingEntity le) {
                    AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) {
                        max.setBaseValue(1000);
                        le.setHealth(1000);
                    }
                    AttributeInstance atk = le.getAttribute(Attribute.ATTACK_DAMAGE);
                    if (atk != null) {
                        atk.setBaseValue(1.0);
                    }
                    le.setRemoveWhenFarAway(false);
                    le.setPersistent(false);
                    if (le instanceof org.bukkit.entity.Ageable ag && !ag.isAdult()) {
                        ag.setAdult();
                    }
                    return le;
                }
                e.remove();
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // nächste Möglichkeit
            }
        }
        return null;
    }

    private void label(LivingEntity e, Fighter f, NamedTextColor color, double frac, int uses, int animal) {
        int pct = (int) Math.max(1, Math.round(frac * 100));
        NamedTextColor hp = pct > 60 ? NamedTextColor.GREEN : pct > 30 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        // Übrige Einsätze: ✚ Heilungen (Unterstützer) oder ✦ Fähigkeiten
        Component left = f.support()
                ? Component.text(" ✚" + uses, NamedTextColor.AQUA).append(Component.text(" ✦" + animal, NamedTextColor.LIGHT_PURPLE))
                : Component.text(" ✦" + uses, NamedTextColor.LIGHT_PURPLE);
        e.customName(Component.text(f.name() + " ", color).append(Component.text("❤ " + pct + " %", hp)).append(left));
        e.setCustomNameVisible(true);
    }

    // ------------------------------------------------------------------
    //  Ablauf
    // ------------------------------------------------------------------

    private void step(Show s) {
        if (s.ended) {
            return;
        }
        s.tick += STEP;
        // Zuschauer prüfen
        s.viewers.removeIf(v -> {
            Player p = Bukkit.getPlayer(v.id);
            if (p == null || !p.isOnline()) {
                return true;
            }
            if (!p.getWorld().equals(s.center.getWorld()) || p.getLocation().distanceSquared(s.center) > 45 * 45) {
                p.teleport(viewPoint(s));
            }
            return false;
        });
        if (s.viewers.isEmpty()) {
            finish(s);
            return;
        }
        int frames = s.script.frames().size();
        int fightTicks = Math.max(400, Math.min(900, frames * 8));
        int total = INTRO_TICKS + fightTicks + OUTRO_TICKS;
        if (s.tick > total + 400) {
            finish(s);
            return;
        }
        for (Viewer v : s.viewers) {
            Player p = Bukkit.getPlayer(v.id);
            if (p != null && p.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                p.closeInventory(); // Menüs verschwinden, sobald man beim Kampf ist
            }
        }
        if (s.tick < INTRO_TICKS) {
            return;
        }
        int fightTick = s.tick - INTRO_TICKS;
        if (fightTick >= fightTicks) {
            if (s.tick >= total) {
                finish(s);
            } else if (s.stageNo != Integer.MAX_VALUE) {
                s.stageNo = Integer.MAX_VALUE;
                String text = s.script.aWins() ? "Seite A siegt" : "Seite B siegt";
                for (Viewer v : s.viewers) {
                    Player p = Bukkit.getPlayer(v.id);
                    if (p != null) {
                        p.sendActionBar(Component.text(text, NamedTextColor.GOLD));
                    }
                }
            }
            return;
        }
        if (s.stageNo != 0) {
            s.stageNo = 0;
            respawnMissing(s);
            beginFight(s);
        }
        int fi = Math.min(frames - 1, (int) ((long) fightTick * frames / fightTicks));
        Frame fr = s.script.frames().get(fi);
        applyFrame(s.sideA, s.fa, fr.a(), fr.usesA(), fr.animalA(), NamedTextColor.GREEN);
        applyFrame(s.sideB, s.fb, fr.b(), fr.usesB(), fr.animalB(), NamedTextColor.RED);
        boolean late = fightTick > fightTicks / 3;
        boolean advA = late || !frontAlive(s.sideA, s.fa);
        boolean advB = late || !frontAlive(s.sideB, s.fb);
        for (int i = 0; i < s.sideA.size(); i++) {
            LivingEntity e = s.sideA.get(i);
            fightStep(e, nearest(e, s.sideB), advA || !s.fa.get(i).back());
        }
        for (int i = 0; i < s.sideB.size(); i++) {
            LivingEntity e = s.sideB.get(i);
            fightStep(e, nearest(e, s.sideA), advB || !s.fb.get(i).back());
        }
        for (Viewer v : s.viewers) {
            Player p = Bukkit.getPlayer(v.id);
            if (p != null) {
                p.sendActionBar(Component.text(s.title + ": " + alive(fr.a()) + " gegen " + alive(fr.b()) + " Kämpfer", NamedTextColor.GOLD)
                        .append(Component.text("  ✦ " + (usesLeft(s.fa, fr.a(), fr.usesA(), false) + usesLeft(s.fa, fr.a(), fr.animalA(), true)) + ":"
                                + (usesLeft(s.fb, fr.b(), fr.usesB(), false) + usesLeft(s.fb, fr.b(), fr.animalB(), true)),
                                NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text("  ✚ " + usesLeft(s.fa, fr.a(), fr.usesA(), true) + ":" + usesLeft(s.fb, fr.b(), fr.usesB(), true),
                                NamedTextColor.AQUA)));
            }
        }
    }

    /** Übrige Fähigkeits-Einsätze (support = false) bzw. Heil-Einsätze (support = true) der noch lebenden Kämpfer einer Seite. */
    private static int usesLeft(List<Fighter> fighters, double[] hp, int[] uses, boolean support) {
        int n = 0;
        for (int i = 0; i < fighters.size() && i < hp.length && i < uses.length; i++) {
            if (hp[i] > 0 && fighters.get(i).support() == support) {
                n += Math.max(0, uses[i]);
            }
        }
        return n;
    }

    private static int alive(double[] hp) {
        int n = 0;
        for (double h : hp) {
            if (h > 0) {
                n++;
            }
        }
        return n;
    }

    /** Lebensanzeige aktualisieren; wer im Ablauf bei 0 ist, fällt um. */
    private void applyFrame(List<LivingEntity> side, List<Fighter> fighters, double[] fractions, int[] uses, int[] animal,
                            NamedTextColor color) {
        for (int i = 0; i < side.size() && i < fractions.length; i++) {
            LivingEntity e = side.get(i);
            if (e == null) {
                continue;
            }
            if (!e.isValid() || e.isDead()) {
                side.set(i, null);
            } else if (fractions[i] <= 0) {
                knockOut(e, side, i);
            } else {
                label(e, fighters.get(i), color, fractions[i], i < uses.length ? uses[i] : 0, i < animal.length ? animal[i] : 0);
            }
        }
    }

    private static LivingEntity nearest(LivingEntity from, List<LivingEntity> foes) {
        if (from == null) {
            return null;
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity f : foes) {
            if (f == null || !f.isValid() || f.isDead()) {
                continue;
            }
            double d = f.getLocation().distanceSquared(from.getLocation());
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        return best;
    }

    /** Alle Kämpfer werden wach; die vordere Reihe läuft los, die hintere hält zuerst die Stellung. */
    private void beginFight(Show s) {
        for (int side = 0; side < 2; side++) {
            List<LivingEntity> ents = side == 0 ? s.sideA : s.sideB;
            List<Fighter> list = side == 0 ? s.fa : s.fb;
            int dir = side == 0 ? -1 : 1;
            for (int i = 0; i < ents.size(); i++) {
                LivingEntity e = ents.get(i);
                if (e == null) {
                    continue;
                }
                if (e instanceof Mob m) {
                    m.setAware(true);
                }
                e.teleport(s.center.clone().add(rowX(list.get(i), dir, true), airOf(list.get(i).entity()), rowZ(list, i)));
            }
        }
    }

    /** Hat die Seite noch jemanden in der vorderen Reihe? */
    private static boolean frontAlive(List<LivingEntity> ents, List<Fighter> list) {
        for (int i = 0; i < ents.size() && i < list.size(); i++) {
            if (ents.get(i) != null && !list.get(i).back()) {
                return true;
            }
        }
        return false;
    }

    private void fightStep(LivingEntity self, LivingEntity foe, boolean advance) {
        if (self == null || foe == null) {
            return;
        }
        if (self instanceof Mob m) {
            m.setTarget(foe);
            if (advance) {
                try {
                    m.getPathfinder().moveTo(foe.getLocation());
                } catch (RuntimeException ignored) {
                    // manche Kreaturen können nicht laufen
                }
            }
        }
        if (advance && FLIERS.contains(self.getType().name())) {
            // Flieger folgen dem Pfad oft nicht: sanft in Richtung Gegner schieben
            Location from = self.getLocation();
            Location to = foe.getLocation().add(0, Math.min(2.0, foe.getHeight() / 2), 0);
            org.bukkit.util.Vector dir = to.toVector().subtract(from.toVector());
            if (dir.lengthSquared() > 9) {
                self.setVelocity(dir.normalize().multiply(0.45));
            }
        }
        if (self.getLocation().distanceSquared(foe.getLocation()) < 12) {
            self.swingMainHand();
            foe.damage(1.0, self);
        }
    }

    private void knockOut(LivingEntity e, List<LivingEntity> side, int index) {
        if (e == null) {
            return;
        }
        Location at = e.getLocation().add(0, 1, 0);
        at.getWorld().strikeLightningEffect(at);
        at.getWorld().spawnParticle(Particle.POOF, at, 25, 0.4, 0.6, 0.4, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        actors.remove(e.getUniqueId());
        e.remove();
        side.set(index, null);
    }

    private void finish(Show s) {
        if (s.ended) {
            return;
        }
        s.ended = true;
        if (s.task != null) {
            s.task.cancel();
        }
        for (List<LivingEntity> side : List.of(s.sideA, s.sideB)) {
            for (LivingEntity e : side) {
                if (e != null) {
                    actors.remove(e.getUniqueId());
                    e.remove();
                }
            }
        }
        usedSlots.remove(s.slot);
        for (Viewer v : s.viewers) {
            Player p = Bukkit.getPlayer(v.id);
            watching.remove(v.id);
            if (p != null && p.isOnline()) {
                restore(p, v);
            }
        }
        for (Map.Entry<UUID, Show> e : new ArrayList<>(watching.entrySet())) {
            if (e.getValue() == s) {
                watching.remove(e.getKey());
            }
        }
        try {
            s.after.run();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Live-Kampf: Auswertung fehlgeschlagen: " + e.getMessage());
        }
    }

    private void restore(Player p, Viewer v) {
        p.setGameMode(v.mode == null || v.mode == GameMode.SPECTATOR ? GameMode.SURVIVAL : v.mode);
        if (v.back != null && v.back.getWorld() != null) {
            p.teleport(v.back);
        }
    }

    /** Server stoppt: alle Zuschauer zurückbringen, Darsteller entfernen (Ergebnisse werden dabei nicht ausgezahlt). */
    public void abortAll() {
        for (Show s : new ArrayList<>(new HashSet<>(watching.values()))) {
            if (s.ended) {
                continue;
            }
            s.ended = true;
            if (s.task != null) {
                s.task.cancel();
            }
            for (List<LivingEntity> side : List.of(s.sideA, s.sideB)) {
                for (LivingEntity e : side) {
                    if (e != null) {
                        e.remove();
                    }
                }
            }
            for (Viewer v : s.viewers) {
                Player p = Bukkit.getPlayer(v.id);
                if (p != null) {
                    restore(p, v);
                }
            }
        }
        watching.clear();
        actors.clear();
        usedSlots.clear();
    }

    // ------------------------------------------------------------------
    //  Ereignisse
    // ------------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Show s = watching.remove(p.getUniqueId());
        if (s == null) {
            return;
        }
        for (Viewer v : new ArrayList<>(s.viewers)) {
            if (v.id.equals(p.getUniqueId())) {
                restore(p, v);
                s.viewers.remove(v);
            }
        }
        if (s.viewers.isEmpty()) {
            finish(s);
        }
    }

    /** Falls der Server mitten im Kampf abstürzte: Wer in der Kampfwelt steht und nirgends mitmacht, wird zurückgeschickt. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = ex.showWorld();
            if (p.isOnline() && w != null && p.getWorld().equals(w) && !ex.isIn(p.getUniqueId()) && !watching.containsKey(p.getUniqueId())
                    && p.getLocation().getX() > 30000) {
                p.setGameMode(GameMode.SURVIVAL);
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }, 40L);
    }

    /** Darsteller ersticken oder trocknen nie aus (Wasserwesen an Land). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNoDrown(org.bukkit.event.entity.EntityDamageEvent event) {
        org.bukkit.event.entity.EntityDamageEvent.DamageCause c = event.getCause();
        if ((c == org.bukkit.event.entity.EntityDamageEvent.DamageCause.DROWNING
                || c == org.bukkit.event.entity.EntityDamageEvent.DamageCause.DRYOUT)
                && actors.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(EntityDeathEvent event) {
        if (actors.contains(event.getEntity().getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /** Gehört die Entität einem Darsteller (oder wurde sie von einem abgeschossen, z. B. Ghast-Feuerball)? */
    private boolean actorOwned(org.bukkit.entity.Entity e) {
        if (actors.contains(e.getUniqueId())) {
            return true;
        }
        return e instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity shooter
                && actors.contains(shooter.getUniqueId());
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        if (actorOwned(event.getEntity())) {
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onPrime(ExplosionPrimeEvent event) {
        if (actorOwned(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
