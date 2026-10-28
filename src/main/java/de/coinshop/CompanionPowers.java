package de.coinshop;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Einzigartige Fähigkeiten: Jede Kreatur hat eine eigene Spezialfähigkeit mit Abklingzeit.
 * Gilt für Begleiter und genauso für gegnerische Kreaturen (die jeweils anderen sind die "Feinde").
 */
public final class CompanionPowers {

    private CompanionPowers() {
    }

    /** Wann welche Kreatur zuletzt ihre Fähigkeit eingesetzt hat. */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private record Power(String name, String text, int cooldown) { }

    private static final Map<String, Power> POWERS = new HashMap<>();

    private static void p(String type, String name, String text, int cd) {
        POWERS.put(type, new Power(name, text, cd));
    }

    static {
        p("WOLF", "Rudelheulen", "Heult und bekommt kurz Tempo und Stärke", 25);
        p("CAT", "Fauchen", "Schwächt Feinde in der Nähe", 20);
        p("FOX", "Beutezug", "Flitzt mit Tempo auf den Feind zu", 18);
        p("BEE", "Giftstachel", "Vergiftet den nächsten Feind", 15);
        p("IRON_GOLEM", "Erdstoß", "Schleudert Feinde in die Luft", 25);
        p("POLAR_BEAR", "Frostatem", "Verlangsamt Feinde in der Nähe", 20);
        p("SKELETON", "Knochenpanzer", "Wird kurz widerstandsfähiger", 25);
        p("WITCH", "Gebräu", "Heilt Verbündete und vergiftet Feinde", 18);
        p("LLAMA", "Spuckschwall", "Verlangsamt und schwächt den Feind", 18);
        p("ALLAY", "Segen", "Verbündete in der Nähe regenerieren", 14);
        p("GOAT", "Wuchtrammen", "Stößt den Feind weit weg", 15);
        p("PANDA", "Nieser", "Wirft Feinde zurück und heilt sich", 20);
        p("RABBIT", "Hoppeln", "Springt schnell und hoch", 12);
        p("SNOW_GOLEM", "Schneesturm", "Friert Feinde in der Nähe ein", 20);
        p("VEX", "Phasenstich", "Wird kurz sehr schnell", 16);
        p("WITHER_SKELETON", "Verwitterung", "Feinde in der Nähe verwittern", 20);
        p("PIGLIN_BRUTE", "Goldrausch", "Bei wenig Leben: große Stärke", 25);
        p("RAVAGER", "Brüllen", "Wirft Feinde zurück und schwächt sie", 22);
        p("VINDICATOR", "Raserei", "Bei wenig Leben: Stärke und Tempo", 25);
        p("PILLAGER", "Schnellfeuer", "Wird kurz sehr schnell", 20);
        p("BOGGED", "Sporenwolke", "Vergiftet Feinde in der Nähe", 20);
        p("ARMADILLO", "Einrollen", "Bei wenig Leben: sehr hoher Schutz", 25);
        p("AXOLOTL", "Totstellen", "Regeneriert stark bei wenig Leben", 30);
        p("BAT", "Ultraschall", "Feinde in der Nähe werden geblendet", 22);
        p("CAMEL", "Wüstenspurt", "Sprintet kurz los", 18);
        p("CHICKEN", "Eierwurf", "Heilt einen Verbündeten ein wenig", 14);
        p("COD", "Flossenschlag", "Wird kurz schneller", 15);
        p("COW", "Milch", "Entfernt Schwächungen und heilt Verbündete", 22);
        p("DONKEY", "Packesel", "Verbündete in der Nähe bekommen Schutz", 25);
        p("FROG", "Zungenhieb", "Zieht den Feind zu sich heran", 15);
        p("GLOW_SQUID", "Leuchtwolke", "Lässt Feinde leuchten (Schaden steigt)", 22);
        p("HORSE", "Galopp", "Galoppiert mit hohem Tempo", 16);
        p("MOOSHROOM", "Pilzsuppe", "Heilt Verbündete in der Nähe", 20);
        p("MULE", "Lastenträger", "Verbündete in der Nähe bekommen Schutz", 25);
        p("OCELOT", "Anschleichen", "Wird kurz sehr schnell", 18);
        p("PARROT", "Nachahmen", "Verbündete in der Nähe werden schneller", 20);
        p("PIG", "Speckschicht", "Bekommt Extraleben", 22);
        p("PUFFERFISH", "Aufblasen", "Vergiftet Feinde in der Nähe", 20);
        p("SALMON", "Strom-Sprung", "Wird kurz schneller und springt", 15);
        p("SHEEP", "Wollschutz", "Verbündete in der Nähe bekommen Extraleben", 25);
        p("SKELETON_HORSE", "Knochengalopp", "Galoppiert und ist schwer zu treffen", 20);
        p("SNIFFER", "Schnüffeln", "Verbündete in der Nähe regenerieren", 22);
        p("SQUID", "Tintenwolke", "Blendet Feinde in der Nähe", 22);
        p("STRIDER", "Lavaglut", "Setzt Feinde in der Nähe in Brand", 18);
        p("TADPOLE", "Zappeln", "Wird kurz schneller", 12);
        p("TRADER_LLAMA", "Handelsgeschick", "Verbündete in der Nähe bekommen Schutz", 25);
        p("TROPICAL_FISH", "Farbenspiel", "Feinde in der Nähe sind verwirrt", 22);
        p("TURTLE", "Panzer", "Bekommt kurz sehr hohen Schutz", 22);
        p("VILLAGER", "Segnung", "Heilt Verbündete in der Nähe", 18);
        p("WANDERING_TRADER", "Zaubertrank", "Verbündete in der Nähe bekommen Schutz", 25);
        p("DOLPHIN", "Delfinsprung", "Verbündete in der Nähe werden schnell", 20);
        p("ZOMBIE_HORSE", "Untoter Galopp", "Galoppiert und ist zäh", 20);
        p("COPPER_GOLEM", "Blitzableiter", "Ein Blitz trifft den nächsten Feind", 20);
        p("HAPPY_GHAST", "Wolkenreise", "Verbündete in der Nähe regenerieren", 18);
        p("BLAZE", "Feuerball", "Setzt den nächsten Feind in Brand", 12);
        p("BREEZE", "Windstoß", "Wirft Feinde zurück", 14);
        p("CAVE_SPIDER", "Giftbiss", "Vergiftet den nächsten Feind", 12);
        p("SPIDER", "Spinnennetz", "Verlangsamt den nächsten Feind stark", 14);
        p("CREAKING", "Knarren", "Verlangsamt Feinde und bewegt sich auch bei Blicken", 18);
        p("CREEPER", "Explosion", "Explodiert beim Tod und verletzt Feinde", 0);
        p("DROWNED", "Sog", "Zieht den Feind heran", 16);
        p("ELDER_GUARDIAN", "Fluch", "Feinde in der Nähe werden müde und langsam", 25);
        p("ENDERMAN", "Teleportsprung", "Springt hinter den Feind", 12);
        p("ENDERMITE", "Perlenkitzel", "Feinde in der Nähe werden verwirrt", 18);
        p("EVOKER", "Zahnfalle", "Fügt dem Feind magischen Schaden zu", 14);
        p("GHAST", "Feuersturm", "Setzt Feinde in der Nähe in Brand", 18);
        p("GUARDIAN", "Laserstrahl", "Verletzt und bremst den nächsten Feind", 12);
        p("HOGLIN", "Hornstoß", "Stößt den Feind weg", 14);
        p("HUSK", "Hunger", "Feinde in der Nähe bekommen Hunger und Schwäche", 20);
        p("ILLUSIONER", "Trugbild", "Feinde in der Nähe werden geblendet", 20);
        p("MAGMA_CUBE", "Glutsprung", "Setzt Feinde in der Nähe in Brand", 16);
        p("PHANTOM", "Sturzflug", "Wird schnell und stürzt sich auf den Feind", 14);
        p("PIGLIN", "Goldgier", "Wird kurz stärker", 22);
        p("SHULKER", "Schwebegeschoss", "Der nächste Feind schwebt kurz", 15);
        p("SILVERFISH", "Wuselei", "Wird kurz schneller", 12);
        p("SLIME", "Schleimspur", "Verlangsamt Feinde in der Nähe", 18);
        p("STRAY", "Eissplitter", "Verlangsamt Feinde in der Nähe", 18);
        p("WARDEN", "Schallwelle", "Schickt dem Feind eine Schallwelle (viel Schaden)", 12);
        p("WITHER", "Welkaura", "Alle Feinde in der Nähe verwittern", 14);
        p("ZOGLIN", "Wutrausch", "Wird stärker und schneller", 20);
        p("ZOMBIE", "Fäule", "Schwächt Feinde in der Nähe", 20);
        p("ZOMBIE_VILLAGER", "Heilung der Toten", "Heilt sich selbst ein wenig", 20);
        p("ZOMBIFIED_PIGLIN", "Rachsucht", "Bei wenig Leben: Stärke", 25);
    }

    /** Hat diese Art eine Fähigkeit, die man einsetzt (nicht nur eine passive)? */
    public static boolean usable(String typeName) {
        Power pw = POWERS.get(typeName);
        return pw == null || pw.cooldown() > 0;
    }

    /** Name und Beschreibung für Menüs. */
    public static String describe(String typeName) {
        Power pw = POWERS.get(typeName);
        if (pw == null) {
            return "Kampfgeist: wird bei wenig Leben stärker";
        }
        return pw.name() + ": " + pw.text() + " (Einsätze wachsen mit dem Level)";
    }

    /** Art der Wirkung einer Fähigkeit (für die berechneten Duelle und die Menü-Hinweise). */
    public enum Kind {
        HEAL_ALLIES("Verbündete (heilt sie)"),
        BUFF_ALLIES("Verbündete in der Nähe (Schutz oder Tempo)"),
        DAMAGE("den nächsten Feind (Schaden)"),
        WEAKEN("Feinde in der Nähe (schwächt oder bremst sie)"),
        SELF_GUARD("nur den Begleiter selbst (Schutz)"),
        SELF_HEAL("nur den Begleiter selbst (heilt ihn)"),
        SELF_ATK("nur den Begleiter selbst (Tempo und Stärke)");

        public final String target;

        Kind(String target) {
            this.target = target;
        }
    }

    private static final java.util.Set<String> K_HEAL = java.util.Set.of("WITCH", "ALLAY", "COW", "MOOSHROOM", "VILLAGER", "CHICKEN",
            "SNIFFER", "HAPPY_GHAST");
    private static final java.util.Set<String> K_BUFF = java.util.Set.of("DONKEY", "MULE", "TRADER_LLAMA", "WANDERING_TRADER", "SHEEP",
            "PARROT", "DOLPHIN");
    private static final java.util.Set<String> K_DAMAGE = java.util.Set.of("BEE", "CAVE_SPIDER", "BLAZE", "EVOKER", "GUARDIAN", "WARDEN",
            "COPPER_GOLEM", "IRON_GOLEM", "HOGLIN", "BREEZE", "GOAT", "RAVAGER", "FROG", "DROWNED", "STRIDER", "GHAST", "MAGMA_CUBE",
            "SHULKER", "ENDERMAN", "PHANTOM");
    private static final java.util.Set<String> K_WEAKEN = java.util.Set.of("CAT", "POLAR_BEAR", "SNOW_GOLEM", "STRAY", "SLIME", "ZOMBIE",
            "HUSK", "ELDER_GUARDIAN", "CREAKING", "WITHER", "WITHER_SKELETON", "BOGGED", "PUFFERFISH", "SQUID", "BAT", "ILLUSIONER",
            "TROPICAL_FISH", "ENDERMITE", "GLOW_SQUID", "LLAMA", "SPIDER");
    private static final java.util.Set<String> K_GUARD = java.util.Set.of("SKELETON", "TURTLE", "ARMADILLO", "PIG", "SKELETON_HORSE",
            "ZOMBIE_HORSE");
    private static final java.util.Set<String> K_SELFHEAL = java.util.Set.of("ZOMBIE_VILLAGER", "PANDA", "AXOLOTL");

    /** Wen oder was die Fähigkeit dieser Art betrifft. */
    public static Kind kind(String typeName) {
        if (K_HEAL.contains(typeName)) {
            return Kind.HEAL_ALLIES;
        }
        if (K_BUFF.contains(typeName)) {
            return Kind.BUFF_ALLIES;
        }
        if (K_DAMAGE.contains(typeName)) {
            return Kind.DAMAGE;
        }
        if (K_WEAKEN.contains(typeName)) {
            return Kind.WEAKEN;
        }
        if (K_GUARD.contains(typeName)) {
            return Kind.SELF_GUARD;
        }
        if (K_SELFHEAL.contains(typeName)) {
            return Kind.SELF_HEAL;
        }
        return Kind.SELF_ATK;
    }

    /** Hinweis für Menüs: wofür die Fähigkeit ist und was sie betrifft. */
    public static String targetText(String typeName) {
        return "Betrifft: " + kind(typeName).target;
    }

    public static void forget(UUID id) {
        LAST.remove(id);
        USES.remove(id);
    }

    /** Wie oft eine Kreatur ihre Fähigkeit in diesem Level (bzw. dieser Welle) schon eingesetzt hat. */
    private static final Map<UUID, Integer> USES = new HashMap<>();

    private static int levelsPerUse = 10;
    private static int maxUsesCap = 8;

    /** Einstellungen aus der config.yml: alle so viele Level ein Einsatz mehr, höchstens maxUses. */
    public static void configure(int perUse, int cap) {
        levelsPerUse = Math.max(1, perUse);
        maxUsesCap = Math.max(1, cap);
    }

    /** Wie viele Einsätze pro Level/Welle eine Kreatur dieser Stufe hat. */
    public static int maxUses(int level) {
        return Math.min(maxUsesCap, 1 + Math.max(0, level - 1) / levelsPerUse);
    }

    /** Verbleibende Einsätze, oder -1 wenn die Kreatur keine einsetzbare Fähigkeit hat. */
    public static int usesLeft(LivingEntity self, int level) {
        Power pw = POWERS.get(self.getType().name());
        if (pw != null && pw.cooldown() == 0) {
            return -1;
        }
        return Math.max(0, maxUses(level) - USES.getOrDefault(self.getUniqueId(), 0));
    }

    /** Neue Welle: alle Einsätze sind wieder da. */
    public static void resetUses(UUID id) {
        USES.remove(id);
        LAST.remove(id);
    }

    private static boolean ready(LivingEntity self, Power pw, long now, int level) {
        int used = USES.getOrDefault(self.getUniqueId(), 0);
        if (used >= maxUses(level)) {
            return false;
        }
        Long last = LAST.get(self.getUniqueId());
        if (last != null && now - last < pw.cooldown() * 1000L) {
            return false;
        }
        LAST.put(self.getUniqueId(), now);
        USES.put(self.getUniqueId(), used + 1);
        if (useListener != null) {
            try {
                useListener.accept(self.getUniqueId());
            } catch (RuntimeException ignored) {
                // Erfahrung ist nur ein Bonus, ein Fehler darf die Fähigkeit nicht stoppen
            }
        }
        return true;
    }

    private static java.util.function.Consumer<UUID> useListener;

    /** Wird bei jedem Fähigkeits-Einsatz aufgerufen (damit Begleiter dafür Erfahrung bekommen). */
    public static void setUseListener(java.util.function.Consumer<UUID> listener) {
        useListener = listener;
    }

    private static LivingEntity nearest(LivingEntity self, List<LivingEntity> list, double radius) {
        LivingEntity best = null;
        double bd = radius * radius;
        for (LivingEntity e : list) {
            if (e == self || !e.isValid() || e.isDead()) {
                continue;
            }
            double d = e.getLocation().distanceSquared(self.getLocation());
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static void eff(LivingEntity target, PotionEffectType type, double seconds, int amp) {
        if (target instanceof Player) {
            String n = type.getKey().getKey();
            if (n.equals("blindness") || n.equals("nausea") || n.equals("mining_fatigue") || n.equals("levitation")
                    || n.equals("hunger") || n.equals("darkness")) {
                return; // Spieler werden nicht mit lästigen Effekten geplagt
            }
        }
        target.addPotionEffect(new PotionEffect(type, (int) (seconds * 20), amp, false, true, true));
    }

    private static void aura(LivingEntity self, List<LivingEntity> list, double radius, PotionEffectType type, double seconds, int amp) {
        for (LivingEntity e : list) {
            if (e != self && e.isValid() && e.getLocation().distanceSquared(self.getLocation()) <= radius * radius) {
                eff(e, type, seconds, amp);
            }
        }
    }

    private static void knock(LivingEntity self, LivingEntity foe, double power, double up) {
        Vector dir = foe.getLocation().toVector().subtract(self.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(1, 0, 0);
        }
        foe.setVelocity(foe.getVelocity().add(dir.normalize().multiply(power).setY(up)));
    }

    private static void heal(LivingEntity target, double amount) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || target.isDead()) {
            return;
        }
        target.setHealth(Math.min(max.getValue(), target.getHealth() + amount));
        target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 1.5, 0), 1, 0.2, 0.2, 0.2);
    }

    private static boolean anyHurt(LivingEntity self, List<LivingEntity> allies, double ratio) {
        for (LivingEntity a : allies) {
            if (a.isValid() && !a.isDead() && hpRatio(a) < ratio
                    && a.getLocation().distanceSquared(self.getLocation()) <= 100) {
                return true;
            }
        }
        return false;
    }

    private static double hpRatio(LivingEntity e) {
        AttributeInstance max = e.getAttribute(Attribute.MAX_HEALTH);
        return max == null || max.getValue() <= 0 ? 1 : e.getHealth() / max.getValue();
    }

    private static void hit(LivingEntity self, LivingEntity foe, double dmg) {
        if (foe.isValid() && !foe.isDead()) {
            foe.damage(dmg, self);
        }
    }

    /**
     * Ruft die Fähigkeit auf (etwa einmal pro Sekunde). foes = Feinde, allies = Verbündete (ohne Spieler bei Gegnern).
     * level = Stärke (wächst mit Level bzw. Welle), mindestens 1.
     */
    public static void tick(LivingEntity self, List<LivingEntity> foes, List<LivingEntity> allies, int level) {
        if (!self.isValid() || self.isDead()) {
            return;
        }
        String type = self.getType().name();
        Power pw = POWERS.get(type);
        long now = System.currentTimeMillis();
        double lv = 1 + Math.min(40, Math.max(0, level - 1)) * 0.05; // Wirkung wächst mit dem Level
        double low = hpRatio(self);
        LivingEntity foe = nearest(self, foes, 12);
        if (pw == null) {
            if (low < 0.4 && foe != null && ready(self, new Power("Kampfgeist", "", 30), now, level)) {
                eff(self, PotionEffectType.STRENGTH, 6, 0);
            }
            return;
        }
        if (pw.cooldown() == 0) {
            return; // passive Fähigkeit (reagiert auf den Tod)
        }
        LivingEntity close = nearest(self, foes, 6);
        // Die Fähigkeit wird erst eingesetzt, wenn sie sich lohnt (so geht sie bei Begleitern, die nur einen Einsatz haben, nicht verloren)
        boolean worth = switch (type) {
            case "PIGLIN_BRUTE", "VINDICATOR", "ZOMBIFIED_PIGLIN" -> low < 0.6 && foe != null;
            case "ARMADILLO", "AXOLOTL" -> low < 0.5 && foe != null;
            case "ZOMBIE_VILLAGER" -> low < 0.7;
            case "ALLAY", "SNIFFER", "HAPPY_GHAST", "WITCH", "COW", "MOOSHROOM", "VILLAGER", "CHICKEN" -> anyHurt(self, allies, 0.85);
            case "DONKEY", "MULE", "TRADER_LLAMA", "SHEEP", "PARROT", "DOLPHIN", "WANDERING_TRADER" -> foe != null && !allies.isEmpty();
            default -> foe != null;
        };
        if (!worth) {
            return;
        }
        if (!ready(self, pw, now, level)) {
            return;
        }
        Location at = self.getLocation().add(0, 1, 0);
        switch (type) {
            case "WOLF" -> {
                eff(self, PotionEffectType.SPEED, 8, 1);
                eff(self, PotionEffectType.STRENGTH, 8, 0);
                self.getWorld().playSound(at, org.bukkit.Sound.ENTITY_WOLF_GROWL, 1f, 1f);
            }
            case "CAT" -> {
                aura(self, foes, 5, PotionEffectType.WEAKNESS, 6 * lv, 0);
                self.getWorld().playSound(at, org.bukkit.Sound.ENTITY_CAT_HISS, 1f, 1f);
            }
            case "FOX", "COD", "TADPOLE", "SILVERFISH", "CAMEL", "SALMON" -> {
                eff(self, PotionEffectType.SPEED, 6, 2);
                if (type.equals("SALMON")) {
                    eff(self, PotionEffectType.JUMP_BOOST, 4, 2);
                }
            }
            case "BEE", "CAVE_SPIDER" -> {
                if (foe != null) {
                    eff(foe, PotionEffectType.POISON, 6 * lv, 1);
                }
            }
            case "IRON_GOLEM" -> {
                for (LivingEntity e : foes) {
                    if (e.isValid() && e.getLocation().distanceSquared(self.getLocation()) <= 25) {
                        e.setVelocity(e.getVelocity().setY(0.9));
                        hit(self, e, 3 * lv);
                    }
                }
                self.getWorld().spawnParticle(Particle.EXPLOSION, self.getLocation(), 1);
            }
            case "POLAR_BEAR", "STRAY", "SLIME" -> aura(self, foes, 5, PotionEffectType.SLOWNESS, 6, 1);
            case "SKELETON", "TURTLE", "ARMADILLO" -> {
                if (!type.equals("ARMADILLO") || low < 0.5) {
                    eff(self, PotionEffectType.RESISTANCE, 8, type.equals("SKELETON") ? 0 : 2);
                }
            }
            case "WITCH" -> {
                for (LivingEntity a : allies) {
                    if (a.getLocation().distanceSquared(self.getLocation()) <= 49) {
                        heal(a, 3 * lv);
                    }
                }
                if (foe != null) {
                    eff(foe, PotionEffectType.POISON, 5, 0);
                }
            }
            case "LLAMA" -> {
                if (foe != null) {
                    eff(foe, PotionEffectType.SLOWNESS, 5, 1);
                    eff(foe, PotionEffectType.WEAKNESS, 5, 0);
                }
            }
            case "ALLAY", "SNIFFER", "HAPPY_GHAST" -> {
                for (LivingEntity a : allies) {
                    if (a.getLocation().distanceSquared(self.getLocation()) <= 100) {
                        // Der Hilfsgeist ist schwächer (Regeneration I, kürzer), Schnüffler und Glücklicher Ghast bleiben wie vorher
                        boolean allay = type.equals("ALLAY");
                        eff(a, PotionEffectType.REGENERATION, allay ? 4 : 5, allay ? 0 : 1);
                    }
                }
            }
            case "GOAT", "HOGLIN", "BREEZE" -> {
                if (foe != null) {
                    knock(self, foe, type.equals("GOAT") ? 1.6 : 1.2, 0.4);
                    if (!type.equals("BREEZE")) {
                        hit(self, foe, 3 * lv);
                    }
                }
            }
            case "PANDA" -> {
                for (LivingEntity e : foes) {
                    if (e.isValid() && e.getLocation().distanceSquared(self.getLocation()) <= 16) {
                        knock(self, e, 1.0, 0.3);
                    }
                }
                heal(self, 3 * lv);
            }
            case "RABBIT" -> {
                eff(self, PotionEffectType.JUMP_BOOST, 5, 2);
                eff(self, PotionEffectType.SPEED, 5, 1);
            }
            case "SNOW_GOLEM" -> aura(self, foes, 6, PotionEffectType.SLOWNESS, 5, 2);
            case "VEX", "OCELOT" -> {
                eff(self, PotionEffectType.SPEED, 5, 2);
            }
            case "WITHER_SKELETON" -> aura(self, foes, 5, PotionEffectType.WITHER, 5, 0);
            case "PIGLIN_BRUTE", "VINDICATOR", "ZOMBIFIED_PIGLIN" -> {
                if (low < 0.6) {
                    eff(self, PotionEffectType.STRENGTH, 7, 1);
                    eff(self, PotionEffectType.SPEED, 7, 0);
                }
            }
            case "RAVAGER" -> {
                for (LivingEntity e : foes) {
                    if (e.isValid() && e.getLocation().distanceSquared(self.getLocation()) <= 36) {
                        knock(self, e, 1.4, 0.4);
                        eff(e, PotionEffectType.WEAKNESS, 5, 0);
                    }
                }
            }
            case "PILLAGER", "HORSE", "ZOMBIE_HORSE", "SKELETON_HORSE", "PHANTOM", "ZOGLIN" -> {
                eff(self, PotionEffectType.SPEED, 7, 2);
                if (type.equals("ZOGLIN") || type.equals("ZOMBIE_HORSE")) {
                    eff(self, PotionEffectType.STRENGTH, 6, 0);
                }
                if (type.equals("SKELETON_HORSE")) {
                    eff(self, PotionEffectType.RESISTANCE, 6, 0);
                }
            }
            case "BOGGED", "PUFFERFISH" -> aura(self, foes, 5, PotionEffectType.POISON, 5, 0);
            case "AXOLOTL" -> {
                if (low < 0.5) {
                    eff(self, PotionEffectType.REGENERATION, 8, 2);
                }
            }
            case "BAT", "SQUID", "ILLUSIONER" -> {
                aura(self, foes, 6, PotionEffectType.BLINDNESS, 3, 0);
            }
            case "CHICKEN" -> {
                for (LivingEntity a : allies) {
                    if (a != self && hpRatio(a) < 0.9) {
                        heal(a, 2 * lv);
                        break;
                    }
                }
            }
            case "COW" -> {
                for (LivingEntity a : allies) {
                    if (a.getLocation().distanceSquared(self.getLocation()) <= 36) {
                        a.removePotionEffect(PotionEffectType.WEAKNESS);
                        a.removePotionEffect(PotionEffectType.SLOWNESS);
                        a.removePotionEffect(PotionEffectType.POISON);
                        heal(a, 2 * lv);
                    }
                }
            }
            case "DONKEY", "MULE", "TRADER_LLAMA" -> aura(self, allies, 8, PotionEffectType.RESISTANCE, 8, 0);
            case "FROG", "DROWNED" -> {
                if (foe != null) {
                    Vector pull = self.getLocation().toVector().subtract(foe.getLocation().toVector()).setY(0.2);
                    if (pull.lengthSquared() > 0.01) {
                        foe.setVelocity(pull.normalize().multiply(0.9).setY(0.25));
                    }
                }
            }
            case "GLOW_SQUID" -> {
                if (foe != null) {
                    eff(foe, PotionEffectType.GLOWING, 6, 0);
                    eff(foe, PotionEffectType.WEAKNESS, 6, 0);
                }
            }
            case "MOOSHROOM", "VILLAGER", "ZOMBIE_VILLAGER" -> {
                if (type.equals("ZOMBIE_VILLAGER")) {
                    heal(self, 4 * lv);
                } else {
                    for (LivingEntity a : allies) {
                        if (a.getLocation().distanceSquared(self.getLocation()) <= 64) {
                            heal(a, 3 * lv);
                        }
                    }
                }
            }
            case "PARROT", "DOLPHIN" -> aura(self, allies, 8, PotionEffectType.SPEED, 8, 1);
            case "PIG", "SHEEP" -> {
                if (type.equals("PIG")) {
                    eff(self, PotionEffectType.ABSORPTION, 10, 2);
                } else {
                    aura(self, allies, 8, PotionEffectType.ABSORPTION, 10, 1);
                }
            }
            case "STRIDER", "MAGMA_CUBE", "GHAST" -> {
                for (LivingEntity e : foes) {
                    if (e.isValid() && e.getLocation().distanceSquared(self.getLocation()) <= 36) {
                        e.setFireTicks(80);
                    }
                }
            }
            case "TROPICAL_FISH", "ENDERMITE" -> aura(self, foes, 6, PotionEffectType.NAUSEA, 5, 0);
            case "WANDERING_TRADER" -> aura(self, allies, 8, PotionEffectType.RESISTANCE, 6, 0);
            case "COPPER_GOLEM" -> {
                if (foe != null) {
                    foe.getWorld().strikeLightningEffect(foe.getLocation());
                    hit(self, foe, 5 * lv);
                }
            }
            case "BLAZE" -> {
                if (foe != null) {
                    foe.setFireTicks(100);
                    hit(self, foe, 2 * lv);
                }
            }
            case "SPIDER" -> {
                if (foe != null) {
                    eff(foe, PotionEffectType.SLOWNESS, 5, 3);
                }
            }
            case "CREAKING" -> aura(self, foes, 6, PotionEffectType.SLOWNESS, 4, 1);
            case "ELDER_GUARDIAN" -> {
                aura(self, foes, 10, PotionEffectType.MINING_FATIGUE, 8, 1);
                aura(self, foes, 10, PotionEffectType.SLOWNESS, 5, 0);
            }
            case "ENDERMAN" -> {
                if (foe != null) {
                    Location behind = foe.getLocation().clone().subtract(foe.getLocation().getDirection().setY(0).normalize().multiply(1.5));
                    behind.setY(foe.getLocation().getY());
                    self.teleport(behind);
                    self.getWorld().playSound(behind, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                }
            }
            case "EVOKER" -> {
                if (foe != null) {
                    foe.getWorld().spawnParticle(Particle.WITCH, foe.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4);
                    hit(self, foe, 4 * lv);
                }
            }
            case "GUARDIAN" -> {
                if (foe != null) {
                    hit(self, foe, 3 * lv);
                    eff(foe, PotionEffectType.SLOWNESS, 3, 1);
                }
            }
            case "HUSK" -> {
                aura(self, foes, 5, PotionEffectType.HUNGER, 6, 1);
                aura(self, foes, 5, PotionEffectType.WEAKNESS, 6, 0);
            }
            case "PIGLIN", "ZOMBIE" -> {
                if (type.equals("PIGLIN")) {
                    eff(self, PotionEffectType.STRENGTH, 6, 0);
                } else {
                    aura(self, foes, 5, PotionEffectType.WEAKNESS, 6, 0);
                }
            }
            case "SHULKER" -> {
                if (foe != null) {
                    eff(foe, PotionEffectType.LEVITATION, 3, 0);
                }
            }
            case "WARDEN" -> {
                if (foe != null) {
                    foe.getWorld().spawnParticle(Particle.SONIC_BOOM, self.getLocation().add(0, 1.6, 0), 1);
                    hit(self, foe, 8 * lv);
                    knock(self, foe, 1.0, 0.3);
                }
            }
            case "WITHER" -> aura(self, foes, 8, PotionEffectType.WITHER, 6, 1);
            default -> { }
        }
    }

    /** Beim Tod: Creeper explodieren ohne Blockschaden und verletzen Feinde in der Nähe. */
    public static void onDeath(LivingEntity self, List<LivingEntity> foes, int level) {
        try {
            if (self.getType().name().equals("CREEPER")) {
                double dmg = 6 * (1 + Math.min(40, Math.max(0, level - 1)) * 0.05);
                self.getWorld().spawnParticle(Particle.EXPLOSION, self.getLocation(), 2);
                self.getWorld().playSound(self.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                for (LivingEntity e : foes) {
                    if (e.isValid() && e.getLocation().distanceSquared(self.getLocation()) <= 16) {
                        e.damage(dmg, self);
                    }
                }
            }
        } finally {
            LAST.remove(self.getUniqueId());
            USES.remove(self.getUniqueId());
        }
    }
}
