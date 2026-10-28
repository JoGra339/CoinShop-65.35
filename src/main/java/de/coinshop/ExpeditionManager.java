package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Expeditionen: Kämpfe in einer eigenen Arena-Welt gegen Kreaturen, Level 1 bis 10.000.
 *
 * <ul>
 *   <li>Man startet nur mit der Ausrüstung, die man mit Coins gelevelt hat (Rüstung, Waffen, Nahrung, Blöcke, Utensilien).</li>
 *   <li>Der eigene Rucksack wird vorher gesichert (auch in einer Datei) und danach zurückgegeben.</li>
 *   <li>Tode in der Arena sind keine echten Tode: Der Schaden wird abgefangen, es gibt keine Statistik und keine Wette.</li>
 *   <li>Pro geschafftem Level gibt es die Levelzahl in Coins mal dem Belohnungs-Multiplikator.</li>
 * </ul>
 */
public final class ExpeditionManager implements Listener, CommandExecutor {

    public static final int MAX_LEVEL = 10000;
    private static final int LEVELS_PER_ARENA = 50;
    private static final int ARENA_Y = 100;
    private static final int ARENA_HEIGHT = 14;
    private static final int SLOT_SPACING = 160;
    private static final int THEME_COUNT = 17;
    private static final int OCEAN = 15;

    /** Ein echter Speer (nicht der Dreizack) als Symbol, falls diese Version ihn kennt. */
    private static Material spearIcon() {
        for (String n : new String[]{"IRON_SPEAR", "WOODEN_SPEAR", "STONE_SPEAR"}) {
            Material m = Material.matchMaterial(n);
            if (m != null) {
                return m;
            }
        }
        return Material.TRIDENT;
    }

    /** Ausrüstungsteile, die man mit Coins in kleinen Schritten levelt. */
    public enum Gear {
        HELMET("helmet", "Helm", 40, 60, 1.1, Material.NETHERITE_HELMET),
        CHESTPLATE("chestplate", "Brustpanzer", 40, 90, 1.1, Material.NETHERITE_CHESTPLATE),
        LEGGINGS("leggings", "Hose", 40, 80, 1.1, Material.NETHERITE_LEGGINGS),
        BOOTS("boots", "Schuhe", 40, 50, 1.1, Material.NETHERITE_BOOTS),
        SWORD("sword", "Schwert", 40, 100, 1.1, Material.NETHERITE_SWORD),
        AXE("axe", "Axt", 40, 100, 1.1, Material.NETHERITE_AXE),
        SPEAR("spear", "Speer", 40, 100, 1.1, spearIcon()),
        MACE("mace", "Streitkolben", 40, 150, 1.1, Material.MACE),
        TRIDENT("trident", "Dreizack", 40, 120, 1.1, Material.TRIDENT),
        BOW("bow", "Bogen", 40, 90, 1.1, Material.BOW),
        CROSSBOW("crossbow", "Armbrust", 40, 90, 1.1, Material.CROSSBOW),
        ARROWS("arrows", "Pfeile", 40, 30, 1.08, Material.ARROW),
        FOOD("food", "Nahrung", 30, 30, 1.09, Material.BREAD),
        GAPPLE("gapple", "Goldäpfel", 32, 40, 1.08, Material.GOLDEN_APPLE),
        EGAPPLE("egapple", "Verzauberte Goldäpfel", 16, 600, 1.15, Material.ENCHANTED_GOLDEN_APPLE),
        BLOCKS("blocks", "Blöcke", 64, 10, 1.05, Material.COBBLESTONE),
        PICKAXE("pickaxe", "Spitzhacke (Tiefenmine)", 40, 80, 1.1, Material.NETHERITE_PICKAXE);

        public final String key;
        public final String label;
        public final int max;
        public final long defaultBase;
        public final double defaultFactor;
        public final Material icon;

        Gear(String key, String label, int max, long defaultBase, double defaultFactor, Material icon) {
            this.key = key;
            this.label = label;
            this.max = max;
            this.defaultBase = defaultBase;
            this.defaultFactor = defaultFactor;
            this.icon = icon;
        }
    }

    /** Utensilien: einmalig kaufen, danach bekommt man sie zum Start jedes Levels. */
    public enum Util {
        SHIELD("shield", "Schild", 1000, Material.SHIELD),
        MILK("milk", "Milcheimer", 800, Material.MILK_BUCKET),
        COBWEB("cobweb", "Spinnweben (8)", 1500, Material.COBWEB),
        HEAL("heal", "Heiltränke (3)", 4000, Material.SPLASH_POTION),
        STRENGTH("strength", "Stärketrank", 6000, Material.POTION),
        TOTEM("totem", "Totem der Unsterblichkeit", 25000, Material.TOTEM_OF_UNDYING),
        PEARL("pearl", "Enderperlen (4)", 5000, Material.ENDER_PEARL),
        WIND("wind", "Windkugeln (8)", 3000, Material.WIND_CHARGE),
        FIRERES("fireres", "Feuerresistenz-Trank", 2500, Material.POTION),
        SPEED("speed", "Schnelligkeits-Trank", 2000, Material.POTION),
        REGEN("regen", "Regenerations-Wurftränke (2)", 6000, Material.SPLASH_POTION),
        HARM("harm", "Schadens-Wurftränke (3)", 7000, Material.SPLASH_POTION),
        SLOW("slow", "Verlangsamungs-Wurftränke (3)", 3500, Material.SPLASH_POTION),
        NIGHT("night", "Nachtsicht-Trank", 800, Material.POTION);

        public final String key;
        public final String label;
        public final long defaultPrice;
        public final Material icon;

        Util(String key, String label, long defaultPrice, Material icon) {
            this.key = key;
            this.label = label;
            this.defaultPrice = defaultPrice;
            this.icon = icon;
        }
    }

    /** Eine Kreatur: Name, Kosten im Level-Budget, friedlich (dann schlägt das Plugin zu), Höchstzahl pro Level, Themen (Bits). */
    private record MobDef(EntityType type, String name, int cost, boolean proxy, int maxEach, int themes, boolean water) { }

    /** Der feste Plan eines Levels (für die Vorschau und den Kampf gleich). */
    public record Plan(int level, Map<EntityType, Integer> mobs, int total, double hpMult, double dmgMult, int tier,
                       boolean boss, EntityType leader) { }

    private final CoinShopPlugin plugin;
    private final List<MobDef> pool = new ArrayList<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean spawning;
    private boolean internalTeleport;

    private static final class Session {
        final UUID player;
        int level;
        final int slot;
        int builtTier = -1;
        long prepUntil;
        /** Kaufphase vor dem Level (Ausrüstung zum halben Preis), 0 = keine. */
        long shopUntil;
        /** Mehrspieler-Verteidigung: Gast-Sitzungen verweisen auf den Gastgeber, der Gastgeber kennt seine Gäste. */
        UUID host;
        final List<UUID> guests = new ArrayList<>();
        /** Welche Ziele der eigenen Art ein Unterstützer schon geheilt hat (Begrenzung). */
        final Map<UUID, Set<UUID>> sameHealed = new HashMap<>();
        /** Heilvorrat der Unterstützer-Begleiter in diesem Level (Leben, das sie noch verteilen dürfen). */
        final Map<UUID, Double> healLeft = new HashMap<>();
        /** Geheilte Leben je Unterstützer-Exemplar (für Erfahrung durch Heilen). */
        final Map<Copy, Double> healPool = new HashMap<>();
        /** Aspekte nach Typ der Gegner (Schaden und Schutz). */
        final Map<CompanionTypes.MobType, Double> aDmgType = new java.util.EnumMap<>(CompanionTypes.MobType.class);
        final Map<CompanionTypes.MobType, Double> aDefType = new java.util.EnumMap<>(CompanionTypes.MobType.class);
        final Set<UUID> mobs = new HashSet<>();
        final Map<Copy, Integer> killGain = new HashMap<>();
        /** Aspekte der mitgenommenen Begleiter im Level. */
        double aDamage;
        double aDefense;
        double aCoins;
        double aRegen;
        final List<EntityType> queue = new ArrayList<>();
        final Set<Long> placed = new HashSet<>();
        double hpMult = 1;
        double dmgMult = 1;
        boolean cleared;
        boolean defeated;
        boolean finishing;
        long levelStart;
        long clearedAt;
        int respawnPending;
        /** In der Verteidigung gefallen: schaut bis zur nächsten Welle zu. */
        boolean downed;
        final List<UUID> companions = new ArrayList<>();
        final Map<UUID, Long> compHit = new HashMap<>();
        /** Handschlag-Schaden der Begleiter ohne eigenen Angriff. */
        final Map<UUID, Double> compDmg = new HashMap<>();
        /** Friedliche Kreaturen: naechster erlaubter Schlag (Plugin-Schaden). */
        final Map<UUID, Long> peaceful = new HashMap<>();
        long companionHeal;
        final ItemStack[] kitArmor = new ItemStack[4];
        ItemStack kitOffhand;
        boolean kitWarned;
        EntityType bossType;
        boolean bossSpawned;
        UUID bossId;
        /** CoinMobs: Kreatur -> Wert in Coins. Zählen nicht zum Level, die Münzen gibt es nach dem geschafften Level. */
        final Map<UUID, Long> coinMobs = new HashMap<>();
        /** Loot-Mob (äußerst selten): wird er erlegt und das Level geschafft, gibt es eine Shop-Belohnung. */
        final Set<UUID> lootMobs = new HashSet<>();
        long coinAt;
        long miniAt;
        UUID miniId;
        int coinCount;
        long lootAt;
        boolean lootKilled;
        int lootCost;
        int lootTier;
        long bonusCoins;
        /** Verteidigungsmodus (Tower Defense): Gegner laufen auf das Zielobjekt (den Kern) zu, Begleiter halten feste Posten. */
        boolean td;
        double coreHp;
        double coreMax;
        Location core;
        final Map<UUID, Location> posts = new HashMap<>();
        UUID selected;
        long coreHitAt;
        /** Wellen-Zustand im Verteidigungsmodus (s.level ist die laufende Welle). */
        long waveStart;
        long pauseUntil;
        long stragglerAt;
        int wavesDone;
        int tdKills;
        double tdCoins;
        double tdValue;
        /** Level und Mine: Coins, die jeder besiegte Gegner einbringt (wie in der Verteidigung). */
        double killCoins;
        /** Anzeige: besiegte Gegner im Level, in der Mine über den ganzen Lauf, und deren Coins in der Mine. */
        int levelKills;
        int mineKills;
        double mineKillCoins;
        boolean tdPaid;
        /** Gemietete Söldner im Level (für die Erfahrung der Besitzer). */
        final List<MercenaryManager.Offer> mercs = new ArrayList<>();
        /** Wellen-Ereignis (v61.0). */
        Ev evKind;
        long evEnd;
        long evNext;
        long evBar;
        long evAt;
        boolean evDone;
        UUID evNpc;
        final Set<UUID> evTemp = new HashSet<>();
        final Set<UUID> evRaid = new HashSet<>();
        final Set<UUID> evGuards = new HashSet<>();
        UUID evBounty;
        boolean evGold;
        /** Pruefung / Ereignisfolge (v61.0): -1 = normales Level. */
        int examKind = -1;
        int examPart;
        int examParts;
        long examBreak;
        double examBaseHp = 1;
        double examBaseDmg = 1;
        boolean examSpecial;
        boolean examHuntFast;
        UUID twinA;
        UUID twinB;
        /** Tiefenmine: Ebene (s.level ist dann die Stärke der Gegner), Beute, Adern, Ausgang. */
        boolean mine;
        int depth;
        double mineCoins;
        int ores;
        int specials;
        boolean guardian;
        boolean exitOpen;
        boolean minePaid;
        long exitMenuAt;
        long mineBoostUntil;
        Location exitPad;
        final Map<Long, Material> oreAt = new HashMap<>();
        final Set<Long> rock = new HashSet<>();

        Session(UUID player, int slot, int level) {
            this.player = player;
            this.slot = slot;
            this.level = level;
        }
    }

    public ExpeditionManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        CompanionPowers.setUseListener(this::creditPowerUse);
        // Themen: 0 Bauernhof, 1 Savanne, 2 Wald, 3 Kirschhain, 4 Bleicher Garten, 5 Wüstenruine, 6 Eisland, 7 Gruft,
        //         8 Prüfungskammer, 9 Dschungeltempel, 10 Sumpf, 11 Illager-Festung, 12 Nether-Bastion, 13 Tiefe Gruft,
        //         14 Endinsel, 15 Ozeanmonument (unter Wasser), 16 Arena der Legenden (alles außer Wasserwesen).
        // Die Themen folgen den natürlichen Spawn-Orten der Kreaturen. Ein "~" vor dem Namen = braucht Wasser.
        // Kosten ungefähr nach Gefährlichkeit. "proxy" = friedlich/ohne eigenen Angriff: harmlose Ziele ohne Zusatzfähigkeiten (Kosten höchstens 3).
        add("CHICKEN", "Huhn", 1, true, 99, 0);
        add("RABBIT", "Hase", 1, true, 99, 0, 3, 5, 6);
        add("BAT", "Fledermaus", 1, true, 99, 7, 13);
        add("~COD", "Kabeljau", 1, true, 99, 15);
        add("~SALMON", "Lachs", 1, true, 99, 15);
        add("~TROPICAL_FISH", "Tropenfisch", 1, true, 99, 15);
        add("~TADPOLE", "Kaulquappe", 1, true, 99, 15);
        add("PIG", "Schwein", 2, true, 99, 0, 3);
        add("SHEEP", "Schaf", 2, true, 99, 0, 3);
        add("PARROT", "Papagei", 2, true, 99, 9);
        add("CAT", "Katze", 2, true, 99, 0);
        add("VILLAGER", "Dorfbewohner", 2, true, 99, 0);
        add("ALLAY", "Hilfsgeist", 2, true, 99, 3, 11);
        add("~SQUID", "Tintenfisch", 2, true, 99, 15);
        add("~PUFFERFISH", "Kugelfisch", 2, true, 99, 15);
        add("COW", "Kuh", 3, true, 99, 0);
        add("ARMADILLO", "Gürteltier", 3, true, 99, 1);
        add("FROG", "Frosch", 3, true, 99, 10);
        add("WANDERING_TRADER", "Fahrender Händler", 3, true, 99, 0, 1);
        add("TURTLE", "Schildkröte", 3, true, 99, 15);
        add("~GLOW_SQUID", "Leuchttintenfisch", 3, true, 99, 15);
        add("MOOSHROOM|MUSHROOM_COW", "Pilzkuh", 4, true, 99, 0);
        add("DONKEY", "Esel", 4, true, 99, 0, 1);
        add("FOX", "Fuchs", 4, false, 99, 2, 6);
        add("BEE", "Biene", 4, false, 99, 2, 3);
        add("OCELOT", "Ozelot", 4, true, 99, 9);
        add("STRIDER", "Schreiter", 4, true, 99, 12);
        add("SILVERFISH", "Silberfischchen", 4, false, 99, 7, 13);
        add("~AXOLOTL", "Axolotl", 4, true, 99, 15);
        add("~DOLPHIN", "Delfin", 4, true, 99, 15);
        add("HORSE", "Pferd", 5, true, 99, 0, 1);
        add("MULE", "Maultier", 5, true, 99, 1);
        add("GOAT", "Ziege", 5, true, 99, 6);
        add("LLAMA", "Lama", 5, true, 99, 1, 6);
        add("TRADER_LLAMA", "Händlerlama", 5, true, 99, 1);
        add("CAMEL", "Kamel", 5, true, 99, 1, 5);
        add("SNOW_GOLEM|SNOWMAN", "Schneegolem", 5, true, 99, 6);
        add("ENDERMITE", "Endermite", 5, false, 99, 14);
        add("ZOMBIE", "Zombie", 6, false, 99, 0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 13);
        add("CAVE_SPIDER", "Höhlenspinne", 6, false, 99, 7, 8);
        add("WOLF", "Wolf", 6, false, 99, 2, 6);
        add("NAUTILUS", "Nautilus", 6, true, 99, 15);
        add("SULFUR_CUBE", "Schwefelwürfel", 3, true, 99, 7, 13);
        add("COPPER_GOLEM", "Kupfergolem", 3, true, 99, 0, 5);
        add("HUSK", "Wüstenzombie", 7, false, 99, 5);
        add("ZOMBIE_VILLAGER", "Zombie-Dorfbewohner", 7, false, 99, 0, 7);
        add("SKELETON", "Skelett", 7, false, 99, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 12, 13);
        add("SPIDER", "Spinne", 7, false, 99, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 13);
        add("DROWNED", "Ertrunkener", 8, false, 99, 10, 15);
        add("SLIME", "Schleim", 8, false, 99, 8, 10);
        add("MAGMA_CUBE", "Magmawürfel", 8, false, 99, 12);
        add("VEX", "Plagegeist", 8, false, 99, 11);
        add("PANDA", "Panda", 8, true, 99, 9);
        add("SNIFFER", "Schnüffler", 8, true, 99, 3);
        add("SKELETON_HORSE", "Skelettpferd", 8, true, 99, 7);
        add("ZOMBIE_HORSE", "Zombiepferd", 8, true, 99, 7);
        add("STRAY", "Eiswanderer", 9, false, 99, 6);
        add("BOGGED", "Morastskelett", 9, false, 99, 8, 10);
        add("PARCHED", "Verdorrter", 9, false, 99, 5);
        add("CREEPER", "Creeper", 10, false, 99, 0, 1, 2, 3, 4, 5, 7, 9, 13);
        add("PHANTOM", "Phantom", 10, false, 99, 3, 4);
        add("CREAKING", "Creaking", 30, false, 99, 4);
        add("POLAR_BEAR", "Eisbär", 10, false, 99, 6);
        add("ZOMBIFIED_PIGLIN|PIG_ZOMBIE", "Zombifizierter Piglin", 10, false, 99, 12);
        add("HAPPY_GHAST", "Glücklicher Ghast", 10, true, 4, 3);
        add("WITCH", "Hexe", 12, false, 99, 4, 10, 11);
        add("PILLAGER", "Plünderer", 12, false, 99, 11);
        add("PIGLIN", "Piglin", 12, false, 99, 12);
        add("CAMEL_HUSK", "Kamel-Zombie", 12, true, 99, 5);
        add("VINDICATOR", "Diener", 14, false, 99, 11);
        add("~GUARDIAN", "Wächter-Fisch", 14, false, 99, 15);
        add("~ZOMBIE_NAUTILUS", "Zombie-Nautilus", 14, false, 99, 15);
        add("BLAZE", "Lohe", 16, false, 99, 12);
        add("HOGLIN", "Hoglin", 18, false, 99, 12);
        add("SHULKER", "Shulker", 18, false, 6, 14);
        add("ENDERMAN", "Enderman", 20, false, 99, 0, 1, 2, 12, 14);
        add("ILLUSIONER", "Illusionist", 20, false, 99, 11);
        add("BREEZE", "Sturmwirbel", 20, false, 99, 8);
        add("PIGLIN_BRUTE", "Piglin-Barbar", 22, false, 99, 12);
        add("WITHER_SKELETON", "Witherskelett", 22, false, 99, 12);
        add("EVOKER", "Magier", 24, false, 6, 11);
        add("GHAST", "Ghast", 24, false, 4, 12);
        add("ZOGLIN", "Zoglin", 26, false, 99, 12);
        add("IRON_GOLEM", "Eisengolem", 35, false, 6, 0);
        add("RAVAGER", "Verwüster", 40, false, 4, 11);
        add("~ELDER_GUARDIAN", "Wächter-Ältester", 50, false, 2, 15);
        add("WARDEN", "Wächter", 150, false, 2, 13);
        add("WITHER", "Wither", 240, false, 1, 12);
    }

    private void add(String types, String name, int cost, boolean proxy, int maxEach, int... themes) {
        boolean water = types.startsWith("~");
        if (proxy) {
            cost = Math.min(cost, 3); // harmlose Kreaturen haben nur ihre normalen Fähigkeiten (keine): leichte Ziele
        }
        int bits = 0;
        for (int t : themes) {
            bits |= 1 << t;
        }
        for (String type : types.replace("~", "").split("\\|")) {
            try {
                pool.add(new MobDef(EntityType.valueOf(type), name, cost, proxy, maxEach, bits, water));
                return;
            } catch (IllegalArgumentException ignored) {
                // nächster Name
            }
        }
        plugin.getLogger().warning("Expedition: Kreatur " + types + " gibt es in dieser Version nicht.");
    }

    private MobDef defOf(EntityType type) {
        for (MobDef def : pool) {
            if (def.type() == type) {
                return def;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Einstellungen
    // ------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("expedition.enabled", true);
    }

    /**
     * Begleiter: jede Kreatur des Spiels (außer dem Enderdrachen) kann gekauft werden. Jedes gekaufte Exemplar ist ein eigenes
     * Wesen mit Level, Seltenheit und Eigenschaften. Der Preis des nächsten Exemplars einer Art verdoppelt sich jedes Mal.
     */
    public static final class Companion {
        private static final List<Companion> ALL = new ArrayList<>();
        private static final Map<String, Companion> BY_KEY = new HashMap<>();

        public final String key;
        public final String label;
        public final long defaultPrice;
        public final Material icon;
        public final String type;
        public final String text;
        /** Gefolge-Kosten (1 bis 10 Sterne) nach dem Grundpreis. */
        public final int stars;

        private Companion(String key, String label, long defaultPrice, Material icon, String type, String text) {
            this.key = key;
            this.label = label;
            this.defaultPrice = defaultPrice;
            this.icon = icon;
            this.type = type;
            this.text = text;
            long[] limits = {6000, 10000, 16000, 24000, 35000, 55000, 90000, 150000, 250000};
            int st = 10;
            for (int i = 0; i < limits.length; i++) {
                if (defaultPrice <= limits[i]) {
                    st = i + 1;
                    break;
                }
            }
            this.stars = st;
        }

        public static Companion[] values() {
            return ALL.toArray(new Companion[0]);
        }

        public static Companion byKey(String key) {
            return BY_KEY.get(key);
        }

        public String name() {
            return type;
        }

        public CompanionTypes.MobType mobType() {
            return CompanionTypes.of(type);
        }

        // Art, deutscher Name, Grundpreis, Symbol (leer = Spawn-Ei), Beschreibung (leer = Standardtext)
        private static final String[][] TABLE = {
                {"WOLF", "Wolf", "8000", "BONE", "Zahmer Wolf: beißt Gegner."},
                {"CAT", "Katze", "6000", "COD", "Zahme Katze: Creeper und Phantome fliehen vor ihr."},
                {"FOX", "Fuchs", "7000", "SWEET_BERRIES", "Flinker Fuchs: beißt Gegner."},
                {"BEE", "Biene", "5000", "HONEY_BOTTLE", "Sticht Gegner und vergiftet sie (einmal)."},
                {"IRON_GOLEM", "Eisengolem", "40000", "IRON_BLOCK", "Starker Kämpfer mit viel Leben."},
                {"POLAR_BEAR", "Eisbär", "25000", "SNOWBALL", "Wuchtige Tatzenhiebe."},
                {"SKELETON", "Skelett-Schütze", "15000", "BOW", "Schießt Pfeile auf Gegner."},
                {"WITCH", "Hexe", "30000", "SPLASH_POTION", "Wirft Tränke auf Gegner."},
                {"LLAMA", "Lama", "9000", "HAY_BLOCK", "Zahmes Lama: spuckt auf Gegner."},
                {"ALLAY", "Hilfsgeist", "12000", "AMETHYST_SHARD", "Unterstützer: heilt dich laufend."},
                {"GOAT", "Ziege", "7500", "GOAT_HORN", "Rammt Gegner mit den Hörnern."},
                {"PANDA", "Panda", "14000", "BAMBOO", "Kräftiger Bär, haut mit der Pranke zu."},
                {"RABBIT", "Killerkaninchen", "6500", "RABBIT_FOOT", "Klein, flink und bissig."},
                {"SNOW_GOLEM", "Schneegolem", "6000", "CARVED_PUMPKIN", "Wirft Schneebälle auf Gegner."},
                {"VEX", "Plagegeist", "18000", "IRON_SWORD", "Fliegt durch Wände und sticht zu."},
                {"WITHER_SKELETON", "Wither-Skelett", "22000", "WITHER_SKELETON_SKULL", "Schlägt hart zu und lässt Gegner verwittern."},
                {"PIGLIN_BRUTE", "Piglin-Brutalo", "24000", "GOLDEN_AXE", "Wuchtige Axthiebe."},
                {"RAVAGER", "Verwüster", "50000", "WHITE_BANNER", "Rammt und zertrampelt Gegner."},
                {"VINDICATOR", "Axtkämpfer", "20000", "DIAMOND_AXE", "Illager mit schwerer Axt."},
                {"PILLAGER", "Armbrustschütze", "18000", "CROSSBOW", "Schießt mit der Armbrust."},
                {"BOGGED", "Sumpfschütze", "17000", "MOSS_BLOCK", "Schießt Giftpfeile."},
                {"ARMADILLO", "Gürteltier", "5000", "", ""},
                {"AXOLOTL", "Axolotl", "7000", "", ""},
                {"BAT", "Fledermaus", "3000", "", ""},
                {"CAMEL", "Kamel", "9000", "", ""},
                {"CHICKEN", "Huhn", "3000", "", ""},
                {"COD", "Kabeljau", "2500", "", ""},
                {"COW", "Kuh", "4000", "", ""},
                {"DONKEY", "Esel", "4500", "", ""},
                {"FROG", "Frosch", "6000", "", ""},
                {"GLOW_SQUID", "Leuchttintenfisch", "4000", "", ""},
                {"HORSE", "Pferd", "6000", "", ""},
                {"MOOSHROOM", "Pilzkuh", "5000", "", ""},
                {"MULE", "Maultier", "5000", "", ""},
                {"OCELOT", "Ozelot", "6500", "", ""},
                {"PARROT", "Papagei", "4500", "", ""},
                {"PIG", "Schwein", "3500", "", ""},
                {"PUFFERFISH", "Kugelfisch", "4500", "", ""},
                {"SALMON", "Lachs", "2500", "", ""},
                {"SHEEP", "Schaf", "3500", "", ""},
                {"SKELETON_HORSE", "Skelettpferd", "9000", "", ""},
                {"SNIFFER", "Schnüffler", "12000", "", ""},
                {"SQUID", "Tintenfisch", "3000", "", ""},
                {"STRIDER", "Schreiter", "7000", "", ""},
                {"TADPOLE", "Kaulquappe", "2500", "", ""},
                {"TRADER_LLAMA", "Händlerlama", "7000", "", ""},
                {"TROPICAL_FISH", "Tropenfisch", "2500", "", ""},
                {"TURTLE", "Schildkröte", "6000", "", ""},
                {"VILLAGER", "Dorfbewohner", "5000", "", ""},
                {"WANDERING_TRADER", "Wandernder Händler", "8000", "", ""},
                {"DOLPHIN", "Delfin", "9000", "", ""},
                {"ZOMBIE_HORSE", "Zombiepferd", "9000", "", ""},
                {"COPPER_GOLEM", "Kupfergolem", "8000", "", ""},
                {"HAPPY_GHAST", "Glücklicher Ghast", "20000", "", ""},
                {"BLAZE", "Lohe", "22000", "", ""},
                {"BREEZE", "Windböe", "24000", "", ""},
                {"CAVE_SPIDER", "Höhlenspinne", "6000", "", ""},
                {"CREAKING", "Creaking", "20000", "", ""},
                {"CREEPER", "Creeper", "12000", "", ""},
                {"DROWNED", "Ertrunkener", "9000", "", ""},
                {"ELDER_GUARDIAN", "Großer Wächter", "60000", "", ""},
                {"ENDERMAN", "Enderman", "26000", "", ""},
                {"ENDERMITE", "Endermilbe", "5000", "", ""},
                {"EVOKER", "Magier", "35000", "", ""},
                {"GHAST", "Ghast", "30000", "", ""},
                {"GUARDIAN", "Wächter", "18000", "", ""},
                {"HOGLIN", "Hoglin", "18000", "", ""},
                {"HUSK", "Wüstenzombie", "8000", "", ""},
                {"ILLUSIONER", "Illusionist", "30000", "", ""},
                {"MAGMA_CUBE", "Magmawürfel", "11000", "", ""},
                {"PHANTOM", "Phantom", "12000", "", ""},
                {"PIGLIN", "Piglin", "10000", "", ""},
                {"SHULKER", "Shulker", "28000", "", ""},
                {"SILVERFISH", "Silberfischchen", "4000", "", ""},
                {"SLIME", "Schleim", "7000", "", ""},
                {"SPIDER", "Spinne", "8000", "", ""},
                {"STRAY", "Eiswanderer", "11000", "", ""},
                {"WARDEN", "Warden", "150000", "", ""},
                {"WITHER", "Wither", "250000", "NETHER_STAR", ""},
                {"ZOGLIN", "Zoglin", "20000", "", ""},
                {"ZOMBIE", "Zombie", "7000", "", ""},
                {"ZOMBIE_VILLAGER", "Zombie-Dorfbewohner", "8000", "", ""},
                {"ZOMBIFIED_PIGLIN", "Zombifizierter Piglin", "9000", "", ""},
                {"NAUTILUS", "Nautilus", "6000", "", ""},
                {"SULFUR_CUBE", "Schwefelwürfel", "5000", "", ""},
                {"ZOMBIE_NAUTILUS", "Zombie-Nautilus", "12000", "", ""},
                {"PARCHED", "Verdorrter", "9000", "", ""},
                {"CAMEL_HUSK", "Kamel-Zombie", "10000", "", ""},
        };

        static {
            for (String[] row : TABLE) {
                try {
                    EntityType.valueOf(row[0]);
                } catch (IllegalArgumentException e) {
                    continue; // diese Kreatur gibt es in dieser Version nicht
                }
                String key = row[0].equals("SNOW_GOLEM") ? "snowman" : row[0].toLowerCase().replace("_", "");
                Material icon = row[3].isEmpty() ? Material.matchMaterial(row[0] + "_SPAWN_EGG") : Material.matchMaterial(row[3]);
                if (icon == null) {
                    icon = Material.NAME_TAG;
                }
                String text = row[4].isEmpty() ? row[1] + ": kämpft selbstständig gegen die Gegner im Level." : row[4];
                Companion c = new Companion(key, row[1], Long.parseLong(row[2]), icon, row[0], text);
                ALL.add(c);
                BY_KEY.put(key, c);
            }
        }
    }

    private String worldName() {
        return plugin.getConfig().getString("expedition.world", "coinshop_expedition");
    }

    private double cfgD(String key, double fallback) {
        return plugin.getConfig().getDouble("expedition." + key, fallback);
    }

    private int cfgI(String key, int fallback) {
        return plugin.getConfig().getInt("expedition." + key, fallback);
    }

    public boolean hasLicense(Player player) {
        return plugin.licenses().has(player.getUniqueId(), "expedition");
    }

    public boolean isIn(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    // ------------------------------------------------------------------
    //  Ausrüstung leveln
    // ------------------------------------------------------------------

    public int level(UUID uuid, Gear gear) {
        return (int) Math.max(0, Math.min(gear.max, plugin.database().getMetaLong("ex_g_" + uuid + "_" + gear.key, 0)));
    }

    public long gearPrice(Gear gear, int toLevel) {
        double base = cfgD("gear." + gear.key + ".base", gear.defaultBase);
        double factor = Math.max(1.0, cfgD("gear." + gear.key + ".factor", gear.defaultFactor));
        return Math.max(1, Math.round(base * Math.pow(factor, Math.max(0, toLevel - 1))));
    }

    public boolean hasAnyGear(UUID uuid) {
        for (Gear gear : Gear.values()) {
            if (level(uuid, gear) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Kauft bis zu "times" Stufen (soweit die Coins reichen). */
    public void buyGear(Player player, Gear gear, int times) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if ((!hasLicense(player) && !(gear == Gear.PICKAXE && hasMineLicense(player))) || (isIn(uuid) && !inShopPhase(uuid))) {
            return;
        }
        int have = level(uuid, gear);
        if (have >= gear.max) {
            msg.send(player, "ex-gear-max");
            return;
        }
        long cost = 0;
        int bought = 0;
        long balance = db.getCoins(uuid);
        while (bought < times && have + bought < gear.max) {
            long next = gearPriceFor(uuid, gear, have + bought + 1);
            if (cost + next > balance) {
                break;
            }
            cost += next;
            bought++;
        }
        if (bought == 0) {
            msg.send(player, "ex-no-money", "price", gearPriceFor(uuid, gear, have + 1));
            return;
        }
        db.begin();
        try {
            if (!db.takeCoins(uuid, cost)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", cost);
                return;
            }
            db.addShopAccount(cost);
            db.setMetaLong("ex_g_" + uuid + "_" + gear.key, have + bought);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Ausrüstung kaufen fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -cost, gear.label + " Stufe " + (have + bought));
        msg.send(player, "ex-gear-bought", "name", gear.label, "level", have + bought, "price", cost);
    }

    public boolean hasUtility(UUID uuid, Util util) {
        return plugin.database().getMetaLong("ex_u_" + uuid + "_" + util.key, 0) > 0;
    }

    public long utilityPrice(Util util) {
        return Math.max(1, plugin.getConfig().getLong("expedition.utility." + util.key, util.defaultPrice));
    }

    public void buyUtility(Player player, Util util) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!hasLicense(player) || isIn(uuid)) {
            return;
        }
        if (hasUtility(uuid, util)) {
            msg.send(player, "ex-util-owned");
            return;
        }
        long price = utilityPrice(util);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.setMetaLong("ex_u_" + uuid + "_" + util.key, 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Utensil kaufen fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -price, util.label);
        msg.send(player, "ex-util-bought", "name", util.label, "price", price);
    }

    // ------------------------------------------------------------------
    //  Belohnungs-Multiplikator
    // ------------------------------------------------------------------

    public int maxMultStage() {
        return Math.max(1, cfgI("reward-mult-max", 10));
    }

    public int multStage(UUID uuid) {
        return (int) Math.max(1, Math.min(maxMultStage(), plugin.database().getMetaLong("ex_mult_" + uuid, 1)));
    }

    public double multFactor(int stage) {
        return Math.max(1, stage) * Math.max(0.1, cfgD("reward-mult-per-stage", 1.0));
    }

    public long multPrice(int stage) {
        return Math.max(1, Math.round(cfgD("reward-mult-base-price", 1000) * Math.pow(Math.max(1.0, cfgD("reward-mult-price-factor", 2.0)), Math.max(0, stage - 2))));
    }

    public void upgradeMult(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!hasLicense(player) || isIn(uuid)) {
            return;
        }
        int stage = multStage(uuid);
        if (stage >= maxMultStage()) {
            msg.send(player, "ex-mult-max");
            return;
        }
        long price = multPrice(stage + 1);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.setMetaLong("ex_mult_" + uuid, stage + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Multiplikator fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -price, "Belohnungs-Multiplikator " + (stage + 1));
        msg.send(player, "ex-mult-bought", "stage", stage + 1, "factor", multFactor(stage + 1), "price", price);
    }

    /** Das nächste Level, das der Spieler schaffen muss. */
    public int nextLevel(UUID uuid) {
        return (int) Math.max(1, Math.min(MAX_LEVEL + 1, plugin.database().getMetaLong("ex_lvl_" + uuid, 1)));
    }

    public long reward(UUID uuid, int level) {
        return Math.round(level * multFactor(multStage(uuid)) * plugin.playtime().factor(uuid)); // Spielzeit-Konto
    }

    // ------------------------------------------------------------------
    //  Level-Plan: Kreaturen und Stärke (für jedes Level fest)
    // ------------------------------------------------------------------

    /** Budget eines Levels: verdoppelt sich in den ersten 6 Leveln (1, 2, 4 ... 32), danach wächst es langsamer. */
    public double budgetOf(int level) {
        if (level <= 6) {
            return Math.pow(Math.max(1.05, cfgD("budget-early-growth", 1.5)), level - 1);
        }
        return Math.pow(Math.max(1.05, cfgD("budget-early-growth", 1.5)), 5) * Math.pow(level / 6.0, cfgD("budget-exponent", 0.45));
    }

    public int themeOf(int tier) {
        return tier % THEME_COUNT;
    }

    private static final int OVERWORLD_MASK = 0xFFF | (1 << 13);

    private boolean realmOk(MobDef def, int theme) {
        if (theme == THEME_COUNT - 1) {
            return !def.water();
        }
        if (theme == OCEAN) {
            return false;
        }
        int mask = theme == 12 ? 1 << 12 : theme == 14 ? 1 << 14 : OVERWORLD_MASK;
        return (def.themes() & mask) != 0;
    }

    private boolean isNative(MobDef def, int theme) {
        if (theme == THEME_COUNT - 1) {
            return !def.water();
        }
        return (def.themes() & (1 << theme)) != 0;
    }

    private MobDef choose(Random random, int theme, boolean wild, double remaining, double costCap, Map<EntityType, Integer> counts,
                          double power) {
        double limit = Math.min(wild ? remaining * 0.6 : remaining, costCap);
        List<MobDef> ok = new ArrayList<>();
        double sum = 0;
        for (MobDef def : pool) {
            if (def.cost() > limit || counts.getOrDefault(def.type(), 0) >= def.maxEach()) {
                continue;
            }
            if (def.water() != (theme == OCEAN) && (def.water() || wild)) {
                continue; // Wasserwesen nur unter Wasser; an Land mischt sich unter Wasser nichts Fremdes
            }
            if (!wild && !isNative(def, theme)) {
                continue;
            }
            if (wild && !realmOk(def, theme)) {
                continue; // fremde Kreaturen nur aus derselben Welt (Oberwelt, Nether, Ende)
            }
            ok.add(def);
            sum += Math.pow(def.cost(), power);
        }
        if (ok.isEmpty()) {
            return null;
        }
        double pick = random.nextDouble() * sum;
        for (MobDef def : ok) {
            pick -= Math.pow(def.cost(), power);
            if (pick < 0) {
                return def;
            }
        }
        return ok.get(ok.size() - 1);
    }

    public Plan plan(int level) {
        int lv = Math.max(1, Math.min(MAX_LEVEL, level));
        Random random = new Random(lv * 7919L + 104729L);
        int tier = (lv - 1) / LEVELS_PER_ARENA;
        int theme = themeOf(tier);
        // Jedes 50. Level (das letzte Level einer Arena) ist ein Bosslevel: mehr und schwerere Gegner, dazu ein Anführer
        boolean boss = lv % LEVELS_PER_ARENA == 0;
        int maxMobs = Math.max(1, cfgI("max-mobs", 40));
        double budget = budgetOf(lv);
        double capShare = cfgD("budget-cost-cap", 0.2);
        double power = 0.6;
        if (boss) {
            maxMobs = (int) Math.round(maxMobs * Math.max(1, cfgD("boss-mob-factor", 1.5)));
            budget *= Math.max(1, cfgD("boss-budget-factor", 2.0));
            capShare *= 1.5;
            power = 1.2;
        }
        // Kreaturenzahl und Budget schwanken von Level zu Level (fest pro Level, aber sehr unterschiedlich)
        Random vr = new Random(lv * 1_299_709L + 17L);
        double spread = Math.max(0, Math.min(0.9, cfgD("count-variation", 0.45)));
        if (!boss) {
            budget *= 1 - spread * 0.6 + spread * 1.2 * vr.nextDouble();
            if (vr.nextInt(3) == 0) {
                maxMobs = Math.max(1, (int) Math.round(maxMobs * (1 - spread * vr.nextDouble())));
            }
        }
        double costCap = Math.max(3, budget * capShare);
        // Je weiter in einer Arena, desto öfter mischen sich fremde Kreaturen darunter
        double wild = Math.min(0.65, 0.15 + 0.5 * ((lv - 1) % LEVELS_PER_ARENA) / (double) LEVELS_PER_ARENA);
        if (theme == THEME_COUNT - 1) {
            wild = 1;
        } else if (theme == OCEAN) {
            wild = 0;
        }
        Map<EntityType, Integer> counts = new LinkedHashMap<>();
        double remaining = budget;
        double spent = 0;
        int total = 0;
        EntityType leader = null;
        if (boss) {
            MobDef lead = choose(random, theme, false, remaining, costCap, counts, 4.0);
            if (lead != null) {
                leader = lead.type();
                counts.merge(lead.type(), 1, Integer::sum);
                remaining -= lead.cost();
                spent += lead.cost();
                total++;
            }
        }
        while (total < maxMobs) {
            boolean useWild = random.nextDouble() < wild;
            MobDef pick = choose(random, theme, useWild, remaining, costCap, counts, power);
            if (pick == null) {
                pick = choose(random, theme, !useWild, remaining, costCap, counts, power);
            }
            if (pick == null) {
                break;
            }
            counts.merge(pick.type(), 1, Integer::sum);
            remaining -= pick.cost();
            spent += pick.cost();
            total++;
        }
        if (total == 0) {
            MobDef cheapest = null;
            for (MobDef def : pool) {
                if (isNative(def, theme) && (cheapest == null || def.cost() < cheapest.cost())) {
                    cheapest = def;
                }
            }
            if (cheapest == null && !pool.isEmpty()) {
                cheapest = pool.get(0);
            }
            if (cheapest != null) {
                counts.put(cheapest.type(), 1);
                spent = cheapest.cost();
                total = 1;
            }
        }
        // Jedes Level hat mindestens ein Monster, das Schaden macht (friedliche Tiere allein reichen nicht)
        boolean armed = false;
        for (EntityType type : counts.keySet()) {
            MobDef d = defOf(type);
            if (d != null && !d.proxy()) {
                armed = true;
                break;
            }
        }
        if (!armed) {
            MobDef weakest = null;
            for (MobDef def : pool) {
                if (def.proxy() || def.water() != (theme == OCEAN) || !isNative(def, theme)) {
                    continue;
                }
                if (weakest == null || def.cost() < weakest.cost()) {
                    weakest = def;
                }
            }
            if (weakest != null) {
                counts.merge(weakest.type(), 1, Integer::sum);
                spent += weakest.cost();
                total++;
            }
        }
        // Was das Budget nicht in Kreaturen umsetzen konnte (Obergrenze), macht sie stärker
        double effort = Math.max(1, budget / Math.max(1, spent));
        double hp = (1 + Math.pow(lv - 1, cfgD("hp-exponent", 0.5)) * cfgD("hp-factor", 0.02)) * Math.min(6, Math.pow(effort, 0.5));
        double dmg = (1 + Math.pow(lv - 1, cfgD("dmg-exponent", 0.4)) * cfgD("dmg-factor", 0.015)) * Math.min(4, Math.pow(effort, 0.4));
        if (boss) {
            hp *= Math.max(1, cfgD("boss-hp-factor", 1.25));
            dmg *= Math.max(1, cfgD("boss-dmg-factor", 1.15));
        }
        return new Plan(lv, counts, total, hp, dmg, tier, boss, leader);
    }

    public String mobName(EntityType type) {
        for (MobDef def : pool) {
            if (def.type() == type) {
                return def.name();
            }
        }
        return type.name();
    }

    // ------------------------------------------------------------------
    //  Ausrüstung als Items
    // ------------------------------------------------------------------

    private static Enchantment ench(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    private static void enchant(ItemStack item, String key, int level) {
        Enchantment e = ench(key);
        if (e != null && level > 0) {
            item.editMeta(m -> m.addEnchant(e, level, true));
        }
    }

    private static ItemStack named(Material material, int amount, String name, String lore) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        item.editMeta(m -> {
            m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isEmpty()) {
                m.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            }
            m.setUnbreakable(true);
        });
        return item;
    }

    private static final String[] ARMOR_TIERS = {"LEATHER", "CHAINMAIL", "IRON", "DIAMOND", "NETHERITE"};
    private static final String[] ARMOR_NAMES = {"Leder", "Kette", "Eisen", "Diamant", "Netherit"};
    private static final String[] TOOL_TIERS = {"WOODEN", "STONE", "IRON", "DIAMOND", "NETHERITE"};
    private static final String[] TOOL_NAMES = {"Holz", "Stein", "Eisen", "Diamant", "Netherit"};

    private static int tierOf(int level) {
        return Math.min(4, (level - 1) / 8);
    }

    private static int stepOf(int level) {
        return (level - 1) % 8;
    }

    /** Stufe des Schutz- bzw. Schärfe-Zaubers auf dieser Stufe (0 bis 4 bzw. 5). */
    private static int enchantStep(int level, int top) {
        int e = stepOf(level) / 2;
        if (level >= 40) {
            e = top;
        }
        return Math.min(top, e);
    }

    /** Beschreibung einer Stufe für die Menüs. */
    public String describe(Gear gear, int level) {
        if (level <= 0) {
            return "nichts";
        }
        switch (gear) {
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS: {
                int p = enchantStep(level, 4);
                return ARMOR_NAMES[tierOf(level)] + (p > 0 ? ", Schutz " + roman(p) : "");
            }
            case SWORD, AXE: {
                int s = enchantStep(level, 5);
                return TOOL_NAMES[tierOf(level)] + (s > 0 ? ", Schärfe " + roman(s) : "");
            }
            case BOW:
                return "Stärke " + roman(Math.max(1, Math.min(5, level / 8))) + (level >= 20 ? ", Schlag" : "")
                        + (level >= 30 ? ", Flamme" : "") + " (Pfeile musst du kaufen)";
            case CROSSBOW:
                return "Schnellladen " + roman(Math.max(1, Math.min(3, level / 14 + 1))) + ", Durchbohren "
                        + roman(Math.max(1, Math.min(4, level / 10 + 1)));
            case ARROWS:
                return arrowCount(level) + " Pfeile pro Level (für Bogen und Armbrust)";
            case SPEAR: {
                int sp = enchantStep(level, 5);
                return TOOL_NAMES[tierOf(level)] + (sp > 0 ? ", Schärfe " + roman(sp) : "") + ", Sturmangriff";
            }
            case TRIDENT:
                return "Treue " + roman(Math.max(1, Math.min(3, level / 14 + 1))) + ", Spießen " + roman(Math.max(1, enchantStep(level, 5)))
                        + (level >= 30 ? ", Entladung" : "") + " (Wurfwaffe, kommt zurück)";
            case MACE:
                return "Dichte " + roman(Math.max(1, Math.min(5, level / 8))) + ", Durchbruch "
                        + roman(Math.max(1, Math.min(4, level / 10))) + (level >= 24 ? ", Windstoß" : "");
            case GAPPLE:
                return level + "x Goldapfel";
            case EGAPPLE:
                return level + "x Verzauberter Goldapfel";
            case FOOD: {
                ItemStack food = foodItem(level);
                return food.getAmount() + "x " + foodName(level);
            }
            case BLOCKS:
                return level + " Blöcke";
            case PICKAXE:
                return TOOL_NAMES[tierOf(level)] + ", Effizienz " + roman(Math.max(1, Math.min(5, 1 + level / 8))) + ", +"
                        + trimNum(level * Math.max(0, cfgD("mine.pickaxe-bonus-percent", 2.5))) + " % Erz-Coins";
            default:
                return "";
        }
    }

    /** Anzahl der Pfeile, die man zum Start jedes Levels bekommt. */
    public static int arrowCount(int level) {
        return level <= 0 ? 0 : 6 + 4 * level;
    }

    private static String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.GERMANY, "%.1f", v);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static String foodName(int level) {
        return switch ((level - 1) / 6) {
            case 0 -> "Brot";
            case 1 -> "Gebratenes Hähnchen";
            case 2 -> "Gebratener Lachs";
            case 3 -> "Steak";
            default -> "Goldene Karotten";
        };
    }

    private static ItemStack foodItem(int level) {
        int tier = Math.min(4, (level - 1) / 6);
        Material material = switch (tier) {
            case 0 -> Material.BREAD;
            case 1 -> Material.COOKED_CHICKEN;
            case 2 -> Material.COOKED_SALMON;
            case 3 -> Material.COOKED_BEEF;
            default -> Material.GOLDEN_CARROT;
        };
        // Mit jeder Stufe mehr Nahrung (8 + Stufe); alle 6 Stufen zusätzlich eine bessere Sorte
        int amount = 8 + level;
        return named(material, Math.min(64, amount), foodName(level), "Expeditions-Nahrung Stufe " + level);
    }

    /** Das Item einer Stufe (zum Anzeigen im Menü und als Start-Ausrüstung). Stufe 0 ergibt null. */
    public ItemStack itemFor(Gear gear, int level) {
        if (level <= 0) {
            return null;
        }
        String title = gear.label + " Stufe " + level;
        switch (gear) {
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS: {
                String piece = switch (gear) {
                    case HELMET -> "HELMET";
                    case CHESTPLATE -> "CHESTPLATE";
                    case LEGGINGS -> "LEGGINGS";
                    default -> "BOOTS";
                };
                ItemStack item = named(Material.valueOf(ARMOR_TIERS[tierOf(level)] + "_" + piece), 1, title, describe(gear, level));
                enchant(item, "protection", enchantStep(level, 4));
                return item;
            }
            case SWORD, AXE: {
                String tool = gear == Gear.SWORD ? "SWORD" : "AXE";
                ItemStack item = named(Material.valueOf(TOOL_TIERS[tierOf(level)] + "_" + tool), 1, title, describe(gear, level));
                enchant(item, "sharpness", enchantStep(level, 5));
                if (level >= 32) {
                    enchant(item, "fire_aspect", 1);
                }
                return item;
            }
            case BOW: {
                ItemStack item = named(Material.BOW, 1, title, describe(gear, level));
                enchant(item, "power", Math.max(1, Math.min(5, level / 8)));
                if (level >= 20) {
                    enchant(item, "punch", level >= 36 ? 2 : 1);
                }
                if (level >= 30) {
                    enchant(item, "flame", 1);
                }
                return item;
            }
            case CROSSBOW: {
                ItemStack item = named(Material.CROSSBOW, 1, title, describe(gear, level));
                enchant(item, "quick_charge", Math.max(1, Math.min(3, level / 14 + 1)));
                enchant(item, "piercing", Math.max(1, Math.min(4, level / 10 + 1)));
                return item;
            }
            case ARROWS:
                return named(Material.ARROW, Math.min(64, arrowCount(level)), "Pfeile", arrowCount(level) + " Pfeile");
            case SPEAR: {
                Material m = Material.matchMaterial(TOOL_TIERS[tierOf(level)] + "_SPEAR");
                ItemStack item = named(m == null ? Material.TRIDENT : m, 1, title, describe(gear, level));
                enchant(item, "sharpness", enchantStep(level, 5));
                enchant(item, "lunge", Math.max(1, Math.min(3, level / 14 + 1)));
                if (level >= 32) {
                    enchant(item, "fire_aspect", 1);
                }
                return item;
            }
            case TRIDENT: {
                ItemStack item = named(Material.TRIDENT, 1, title, describe(gear, level));
                enchant(item, "loyalty", Math.max(1, Math.min(3, level / 14 + 1)));
                enchant(item, "impaling", Math.max(1, enchantStep(level, 5)));
                if (level >= 30) {
                    enchant(item, "channeling", 1);
                }
                return item;
            }
            case MACE: {
                ItemStack item = named(Material.MACE, 1, title, describe(gear, level));
                enchant(item, "density", Math.max(1, Math.min(5, level / 8)));
                enchant(item, "breach", Math.max(1, Math.min(4, level / 10)));
                if (level >= 24) {
                    enchant(item, "wind_burst", Math.min(3, level / 12));
                }
                if (level >= 32) {
                    enchant(item, "fire_aspect", 1);
                }
                return item;
            }
            case PICKAXE: {
                ItemStack item = named(Material.valueOf(TOOL_TIERS[tierOf(level)] + "_PICKAXE"), 1, title, describe(gear, level));
                enchant(item, "efficiency", Math.max(1, Math.min(5, 1 + level / 8)));
                item.editMeta(m -> m.setUnbreakable(true));
                return item;
            }
            case GAPPLE:
                return named(Material.GOLDEN_APPLE, Math.min(64, level), "Goldapfel", level + " Goldäpfel");
            case EGAPPLE:
                return named(Material.ENCHANTED_GOLDEN_APPLE, Math.min(64, level), "Verzauberter Goldapfel", level + " Verzauberte Goldäpfel");
            case FOOD:
                return foodItem(level);
            case BLOCKS: {
                Material block = Material.matchMaterial(plugin.getConfig().getString("expedition.block-material", "COBBLESTONE"));
                return named(block == null ? Material.COBBLESTONE : block, level, "Expeditions-Blöcke", level + " Blöcke zum Bauen");
            }
            default:
                return null;
        }
    }

    private ItemStack utilityItem(Util util) {
        switch (util) {
            case SHIELD:
                return named(Material.SHIELD, 1, "Expeditions-Schild", "Utensil");
            case MILK:
                return named(Material.MILK_BUCKET, 1, "Milcheimer", "Entfernt alle Effekte");
            case COBWEB:
                return named(Material.COBWEB, 8, "Spinnweben", "Verlangsamen Kreaturen");
            case HEAL: {
                return potionItem(Material.SPLASH_POTION, 3, "strong_healing");
            }
            case STRENGTH: {
                return potionItem(Material.POTION, 1, "strong_strength");
            }
            case TOTEM:
                return named(Material.TOTEM_OF_UNDYING, 1, "Totem", "Rettet dich einmal");
            case PEARL:
                return named(Material.ENDER_PEARL, 4, "Enderperlen", "Teleport in der Arena");
            case WIND:
                return named(Material.WIND_CHARGE, 8, "Windkugeln", "Stoßen Kreaturen weg");
            case FIRERES: {
                return potionItem(Material.POTION, 1, "long_fire_resistance");
            }
            case SPEED: {
                return potionItem(Material.POTION, 1, "strong_swiftness");
            }
            case REGEN: {
                return potionItem(Material.SPLASH_POTION, 2, "strong_regeneration");
            }
            case HARM: {
                return potionItem(Material.SPLASH_POTION, 3, "strong_harming");
            }
            case SLOW: {
                return potionItem(Material.SPLASH_POTION, 3, "strong_slowness");
            }
            case NIGHT: {
                return potionItem(Material.POTION, 1, "long_night_vision");
            }
            default:
                return null;
        }
    }

    /** Ein echter Trank (mit dem Namen und den Wirkungen aus dem normalen Spiel). */
    private static ItemStack potionItem(Material material, int amount, String key) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        setPotion(item, key);
        return item;
    }

    /** Das Utensil als echtes Item (fuer die Anzeige im Menue). */
    public ItemStack utilityStack(Util util) {
        return utilityItem(util);
    }

    private static void setPotion(ItemStack item, String key) {
        try {
            org.bukkit.potion.PotionType type = Registry.POTION.get(NamespacedKey.minecraft(key));
            if (type != null) {
                item.editMeta(PotionMeta.class, m -> m.setBasePotionType(type));
            }
        } catch (RuntimeException ignored) {
            // Trank gibt es nicht: bleibt ein einfacher Trank
        }
    }

    /** Legt die gekaufte Ausrüstung in den Rucksack des Spielers (der vorher geleert wurde). */
    /** Ein Gegenstand der Start-Ausrüstung mit festem Namen (für die Sortierung). */
    public record KitItem(String key, ItemStack item) { }

    /** Alle Gegenstände, die in den Rucksack kommen (ohne Rüstung und Schild). */
    public List<KitItem> kitItems(UUID uuid) {
        List<KitItem> list = new ArrayList<>();
        for (Gear gear : new Gear[]{Gear.SWORD, Gear.AXE, Gear.SPEAR, Gear.MACE, Gear.TRIDENT, Gear.BOW, Gear.CROSSBOW, Gear.FOOD,
                Gear.GAPPLE, Gear.EGAPPLE, Gear.BLOCKS, Gear.PICKAXE}) {
            ItemStack item = itemFor(gear, level(uuid, gear));
            if (item != null) {
                list.add(new KitItem("g_" + gear.name(), item));
            }
        }
        int arrows = arrowCount(level(uuid, Gear.ARROWS));
        int n = 0;
        while (arrows > 0) {
            int part = Math.min(64, arrows);
            list.add(new KitItem("arrows_" + n++, named(Material.ARROW, part, "Pfeile", "Gekaufte Pfeile")));
            arrows -= part;
        }
        for (Util util : Util.values()) {
            if (!hasUtility(uuid, util)) {
                continue;
            }
            ItemStack item = utilityItem(util);
            if (item != null) {
                list.add(new KitItem("u_" + util.name(), item));
            }
        }
        return list;
    }

    /** Die Plätze 0 bis 35 im Rucksack: gespeicherte Wunschplätze zuerst, alles Neue auf den ersten freien Platz. */
    public KitItem[] kitLayout(UUID uuid) {
        KitItem[] slots = new KitItem[37]; // Platz 36 = zweite Hand (dort liegt das Schild, wenn nichts anderes gewünscht ist)
        List<KitItem> rest = new ArrayList<>();
        for (KitItem k : kitItems(uuid)) {
            int saved = (int) plugin.database().getMetaLong("ex_ks_" + uuid + "_" + k.key(), 0) - 1;
            if (saved < 0 && k.key().equals("u_SHIELD")) {
                saved = 36;
            }
            if (saved >= 0 && saved < 37 && slots[saved] == null) {
                slots[saved] = k;
            } else {
                rest.add(k);
            }
        }
        int idx = 0;
        for (KitItem k : rest) {
            while (idx < 36 && slots[idx] != null) {
                idx++;
            }
            if (idx >= 36) {
                break;
            }
            slots[idx++] = k;
        }
        return slots;
    }

    /** Tauscht zwei Plätze (oder verschiebt auf einen freien) und merkt sich die ganze Anordnung. */
    public void moveKitItem(UUID uuid, int from, int to) {
        if (from < 0 || to < 0 || from >= 37 || to >= 37 || from == to || sessions.containsKey(uuid)) {
            return;
        }
        KitItem[] layout = kitLayout(uuid);
        KitItem a = layout[from];
        layout[from] = layout[to];
        layout[to] = a;
        for (int i = 0; i < layout.length; i++) {
            if (layout[i] != null) {
                plugin.database().setMetaLong("ex_ks_" + uuid + "_" + layout[i].key(), i + 1);
            }
        }
    }

    private void giveKit(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();
        inv.clear();
        ItemStack helmet = itemFor(Gear.HELMET, level(uuid, Gear.HELMET));
        ItemStack chest = itemFor(Gear.CHESTPLATE, level(uuid, Gear.CHESTPLATE));
        ItemStack legs = itemFor(Gear.LEGGINGS, level(uuid, Gear.LEGGINGS));
        ItemStack boots = itemFor(Gear.BOOTS, level(uuid, Gear.BOOTS));
        if (helmet != null) {
            inv.setHelmet(helmet);
        }
        if (chest != null) {
            inv.setChestplate(chest);
        }
        if (legs != null) {
            inv.setLeggings(legs);
        }
        if (boots != null) {
            inv.setBoots(boots);
        }
        KitItem[] layout = kitLayout(uuid);
        for (int i = 0; i < layout.length; i++) {
            if (layout[i] == null) {
                continue;
            }
            if (i == 36) {
                inv.setItemInOffHand(layout[i].item().clone());
            } else {
                inv.setItem(i, layout[i].item().clone());
            }
        }
        Session session = sessions.get(uuid);
        if (session != null && session.mine) {
            boolean hasPick = false;
            for (ItemStack it : inv.getContents()) {
                if (it != null && it.getType().name().endsWith("_PICKAXE")) {
                    hasPick = true;
                    break;
                }
            }
            if (!hasPick) {
                ItemStack pick = itemFor(Gear.PICKAXE, 1); // ohne gekaufte Spitzhacke gibt es eine einfache
                if (pick != null) {
                    inv.addItem(pick);
                }
            }
        }
        if (session != null) {
            ItemStack[] armor = inv.getArmorContents(); // Stiefel, Hose, Brustpanzer, Helm
            for (int i = 0; i < 4; i++) {
                session.kitArmor[i] = armor[i] == null || armor[i].getType().isAir() ? null : armor[i].clone();
            }
            ItemStack off = inv.getItemInOffHand();
            session.kitOffhand = off == null || off.getType().isAir() ? null : off.clone();
            session.kitWarned = false;
        }
    }

    /** Rüstung und Schild gehen nicht verloren: fehlt etwas, kommt es zurück (und es steht im Log). */
    private void guardKit(Player player, Session s) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] now = inv.getArmorContents();
        boolean fixed = false;
        for (int i = 0; i < 4; i++) {
            if (s.kitArmor[i] != null && (now[i] == null || now[i].getType().isAir())) {
                now[i] = s.kitArmor[i].clone();
                fixed = true;
            }
        }
        if (fixed) {
            inv.setArmorContents(now);
        }
        ItemStack off = inv.getItemInOffHand();
        if (s.kitOffhand != null && (off == null || off.getType().isAir())) {
            // das Schild kann auch in der Haupthand oder im Rucksack liegen (Tausch mit F): dann nichts doppelt geben
            boolean elsewhere = inv.contains(s.kitOffhand.getType());
            if (!elsewhere) {
                inv.setItemInOffHand(s.kitOffhand.clone());
                fixed = true;
            }
        }
        if (fixed && !s.kitWarned) {
            s.kitWarned = true;
            plugin.getLogger().warning("Expedition: Rüstung oder Schild von " + player.getName() + " fehlte in Level "
                    + s.level + " und wurde ersetzt (letzte Ursache: "
                    + (player.getLastDamageCause() == null ? "unbekannt" : player.getLastDamageCause().getCause()) + ").");
        }
    }

    // ------------------------------------------------------------------
    //  Welt und Arena
    // ------------------------------------------------------------------

    /** Die Welt für die Live-Kämpfe (dieselbe wie die der Expeditionen). */
    World showWorld() {
        return world();
    }

    int showY() {
        return ARENA_Y;
    }

    /** Erzeugt eine Kreatur in der Kampfwelt (die Spawn-Sperre der Welt wird dafür kurz aufgehoben). */
    Entity spawnShow(Location at, EntityType type) {
        boolean old = spawning;
        spawning = true;
        try {
            return at.getWorld().spawnEntity(at, type);
        } finally {
            spawning = old;
        }
    }

    private World world() {
        World world = Bukkit.getWorld(worldName());
        if (world == null) {
            WorldCreator creator = new WorldCreator(worldName());
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(false);
            creator.generator(new ChunkGenerator() {
                @Override
                public Location getFixedSpawnLocation(World w, Random random) {
                    return new Location(w, 0.5, ARENA_Y + 1, 0.5);
                }
            });
            world = creator.createWorld();
            if (world != null) {
                world.setDifficulty(Difficulty.HARD);
                world.setStorm(false);
                world.setTime(18000);
                world.setSpawnLocation(0, ARENA_Y + 1, 0);
                for (Entity e : world.getEntities()) {
                    if (!(e instanceof Player)) {
                        e.remove();
                    }
                }
            }
        }
        return world;
    }

    private record Theme(Material floor, Material floor2, Material wall, Material pillar, Material light) { }

    private static final Theme[] THEMES = {
            new Theme(Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG, Material.GLOWSTONE),
            new Theme(Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.ACACIA_PLANKS, Material.ACACIA_LOG, Material.GLOWSTONE),
            new Theme(Material.PODZOL, Material.MOSS_BLOCK, Material.SPRUCE_LOG, Material.DARK_OAK_LOG, Material.SHROOMLIGHT),
            new Theme(Material.CHERRY_PLANKS, Material.PINK_TERRACOTTA, Material.CHERRY_PLANKS, Material.STRIPPED_CHERRY_LOG, Material.SEA_LANTERN),
            new Theme(Material.PALE_MOSS_BLOCK, Material.PALE_OAK_PLANKS, Material.PALE_OAK_LOG, Material.PALE_OAK_LOG, Material.SEA_LANTERN),
            new Theme(Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE, Material.SANDSTONE, Material.CHISELED_SANDSTONE, Material.GLOWSTONE),
            new Theme(Material.PACKED_ICE, Material.SNOW_BLOCK, Material.BLUE_ICE, Material.PACKED_ICE, Material.SEA_LANTERN),
            new Theme(Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.OCHRE_FROGLIGHT),
            new Theme(Material.TUFF_BRICKS, Material.POLISHED_TUFF, Material.COPPER_BLOCK, Material.CUT_COPPER, Material.OCHRE_FROGLIGHT),
            new Theme(Material.MOSSY_COBBLESTONE, Material.MOSS_BLOCK, Material.JUNGLE_PLANKS, Material.JUNGLE_LOG, Material.VERDANT_FROGLIGHT),
            new Theme(Material.MUD_BRICKS, Material.MUD, Material.MANGROVE_PLANKS, Material.MANGROVE_LOG, Material.VERDANT_FROGLIGHT),
            new Theme(Material.DARK_OAK_PLANKS, Material.COBBLESTONE, Material.COBBLESTONE, Material.DARK_OAK_LOG, Material.GLOWSTONE),
            new Theme(Material.NETHER_BRICKS, Material.RED_NETHER_BRICKS, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.SHROOMLIGHT),
            new Theme(Material.POLISHED_DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS, Material.OCHRE_FROGLIGHT),
            new Theme(Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.OBSIDIAN, Material.PURPUR_PILLAR, Material.SEA_LANTERN),
            new Theme(Material.PRISMARINE_BRICKS, Material.PRISMARINE, Material.DARK_PRISMARINE, Material.PRISMARINE_BRICKS, Material.SEA_LANTERN),
            new Theme(Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, Material.QUARTZ_BRICKS, Material.CHISELED_QUARTZ_BLOCK, Material.GLOWSTONE)
    };

    private static final String[] THEME_NAMES = {"Bauernhof", "Savanne", "Wald", "Kirschhain", "Bleicher Garten", "Wüstenruine",
            "Eisland", "Gruft", "Prüfungskammer", "Dschungeltempel", "Sumpf", "Illager-Festung", "Nether-Bastion",
            "Tiefe Gruft", "Endinsel", "Ozeanmonument", "Arena der Legenden"};

    public String themeName(int tier) {
        return THEME_NAMES[tier % THEME_COUNT] + " " + (tier / THEME_COUNT + 1);
    }

    /** Welcher Typ in welcher Arena gestärkt wird (null = kein Typ-Bonus). Reihenfolge wie THEME_NAMES. */
    private static final CompanionTypes.MobType[] THEME_TYPES = {
            CompanionTypes.MobType.NATUR,    // Bauernhof
            CompanionTypes.MobType.LUFT,     // Savanne
            CompanionTypes.MobType.NATUR,    // Wald
            CompanionTypes.MobType.LUFT,     // Kirschhain
            CompanionTypes.MobType.FINSTER,  // Bleicher Garten
            CompanionTypes.MobType.ERDE,     // Wüstenruine
            CompanionTypes.MobType.EIS,      // Eisland
            CompanionTypes.MobType.UNTOTE,   // Gruft
            CompanionTypes.MobType.ERDE,     // Prüfungskammer
            CompanionTypes.MobType.NATUR,    // Dschungeltempel
            CompanionTypes.MobType.WASSER,   // Sumpf
            CompanionTypes.MobType.FINSTER,  // Illager-Festung
            CompanionTypes.MobType.FEUER,    // Nether-Bastion
            CompanionTypes.MobType.UNTOTE,   // Tiefe Gruft
            CompanionTypes.MobType.FINSTER,  // Endinsel
            CompanionTypes.MobType.WASSER,   // Ozeanmonument
            null                             // Arena der Legenden
    };

    /** Typ, der in dieser Arena gestärkt ist (oder null). */
    public CompanionTypes.MobType biomeType(int tier) {
        return THEME_TYPES[Math.floorMod(tier, THEME_COUNT)];
    }

    /** Bonus-Faktor für Leben und Schaden, wenn der Typ der Kreatur zum Biom der Arena passt (Verbündete und Gegner). */
    private double biomeFactor(Session s, String entityTypeName) {
        if (s.mine) {
            return 1.0;
        }
        CompanionTypes.MobType t = biomeType(arenaTier(s));
        if (t == null || !cfgBool("biome.enabled", true)) {
            return 1.0;
        }
        return CompanionTypes.of(entityTypeName) == t ? 1.0 + Math.max(0, cfgD("biome.buff-percent", 25)) / 100.0 : 1.0;
    }

    /** Text für Menüs: welcher Typ in dieser Arena gestärkt ist. */
    public String biomeLine(int tier) {
        CompanionTypes.MobType t = biomeType(tier);
        if (t == null || !cfgBool("biome.enabled", true)) {
            return "Kein Typ-Bonus";
        }
        return t.label + "-Kreaturen sind hier +" + Math.round(Math.max(0, cfgD("biome.buff-percent", 25))) + " % stärker (Verbündete und Gegner)";
    }

    private boolean cfgBool(String key, boolean fallback) {
        return plugin.getConfig().getBoolean("expedition." + key, fallback);
    }

    // ------------------------------------------------------------------
    //  Rolle Verteidiger und sichtbare Rüstung
    // ------------------------------------------------------------------

    private static final Set<String> DEFENDER_NAMES = Set.of("IRON_GOLEM", "COPPER_GOLEM", "ARMADILLO", "TURTLE", "SNIFFER", "CAMEL",
            "RAVAGER", "POLAR_BEAR", "PANDA", "HOGLIN", "ZOGLIN", "SHULKER", "ELDER_GUARDIAN", "WARDEN", "MOOSHROOM", "COW",
            "HORSE", "DONKEY", "MULE", "SKELETON_HORSE", "ZOMBIE_HORSE", "HAPPY_GHAST", "PIGLIN_BRUTE", "CREAKING", "SLIME",
            "MAGMA_CUBE", "SULFUR_CUBE", "PIG", "STRIDER");

    /** Verteidiger: robust, ziehen die Aufmerksamkeit der Gegner auf sich, schlagen aber schwächer zu. */
    public boolean isDefender(EntityType type) {
        return type != null && !isSupporter(type) && DEFENDER_NAMES.contains(type.name());
    }

    public boolean isDefender(Companion c) {
        return c != null && !isSupporter(c) && DEFENDER_NAMES.contains(c.type);
    }

    /** Rolle mit Erklärung für Menüs (jeder Begleiter ist genau eins: Angreifer, Verteidiger oder Unterstützer). */
    public String roleText(Companion c) {
        return isSupporter(c) ? "Rolle: Unterstützer (heilt, kämpft nicht)"
                : isDefender(c) ? "Rolle: Verteidiger (robust, lenkt Gegner auf sich)" : "Rolle: Angreifer (kämpft)";
    }

    public NamedTextColor roleColor(Companion c) {
        return isSupporter(c) ? NamedTextColor.GREEN : isDefender(c) ? NamedTextColor.AQUA : NamedTextColor.RED;
    }

    /** 0 = Angreifer, 1 = Verteidiger, 2 = Unterstützer. */
    public int roleIndex(Companion c) {
        return isSupporter(c) ? 2 : isDefender(c) ? 1 : 0;
    }

    public double roleHpFactor(Companion c) {
        return isDefender(c) ? Math.max(1.0, cfgD("defender.hp-factor", 1.6)) : 1.0;
    }

    public double roleDmgFactor(Companion c) {
        return isDefender(c) ? Math.max(0.1, cfgD("defender.dmg-factor", 1.0)) : 1.0;
    }

    private final Map<UUID, Long> tauntAt = new HashMap<>();

    /** Verteidiger lenken nahe Gegner auf sich. */
    private void taunt(LivingEntity defender, List<LivingEntity> enemies) {
        // (Zusätzlich wählen Gegner in der Zielsuche jeden Verteidiger vor allen anderen.)
        long now = System.currentTimeMillis();
        if (now - tauntAt.getOrDefault(defender.getUniqueId(), 0L) < Math.max(1, cfgI("defender.taunt-seconds", 3)) * 1000L) {
            return;
        }
        tauntAt.put(defender.getUniqueId(), now);
        double radius = Math.max(2, cfgD("defender.taunt-radius", 8));
        for (LivingEntity foe : enemies) {
            if (foe instanceof Mob mob && foe.getWorld() == defender.getWorld()
                    && foe.getLocation().distanceSquared(defender.getLocation()) <= radius * radius) {
                mob.setTarget(defender);
            }
        }
    }

    private static final Set<String> ARMOR_WEARERS = Set.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY",
            "BOGGED", "WITHER_SKELETON", "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN", "PARCHED");
    private static final String[] VIS_TIER_NAMES = {"Leder", "Kette", "Eisen", "Diamant", "Netherit"};
    private static final String[] VIS_TIER_PREFIX = {"LEATHER", "CHAINMAIL", "IRON", "DIAMOND", "NETHERITE"};
    /** Reihenfolge, in der Teile dazukommen: erst Schuhe, dann Helm, dann Hose, dann Brustpanzer. */
    private static final String[] VIS_PIECES = {"BOOTS", "HELMET", "LEGGINGS", "CHESTPLATE"};
    private static final String[] VIS_PIECE_NAMES = {"Schuhe", "Helm", "Hose", "Brustpanzer"};
    private static final Set<String> SADDLE_ANIMALS = Set.of("HORSE", "DONKEY", "MULE", "CAMEL", "SKELETON_HORSE", "ZOMBIE_HORSE",
            "STRIDER", "PIG");
    private static final String[] HORSE_ARMOR = {"LEATHER_HORSE_ARMOR", "IRON_HORSE_ARMOR", "GOLDEN_HORSE_ARMOR", "DIAMOND_HORSE_ARMOR"};
    private static final String[] HORSE_ARMOR_NAMES = {"Lederrüstung (zufällige Farbe)", "Eisenrüstung", "Goldrüstung", "Diamantrüstung"};

    public boolean wearsArmor(Companion c) {
        return c != null && ARMOR_WEARERS.contains(c.type);
    }

    /** Tiere mit Sattel, Wolfsrüstung, Pferderüstung oder Lamateppich. */
    public boolean wearsAnimalGear(Companion c) {
        return c != null && (c.type.equals("WOLF") || c.type.equals("LLAMA") || c.type.equals("TRADER_LLAMA") || SADDLE_ANIMALS.contains(c.type));
    }

    /** Wie viele Rüstungsteile (0 bis 20) sieht man bei diesem Level? Teil 1 ab "first-level", dann alle "levels-per-step" Level eins mehr. */
    public int armorStep(int level) {
        int first = Math.max(1, cfgI("visual-armor.first-level", 4));
        int per = Math.max(1, cfgI("visual-armor.levels-per-step", 4));
        if (level < first) {
            return 0;
        }
        return Math.min(20, 1 + (level - first) / per);
    }

    /** Stufe (0 bis 5) von Sattel und Tierrüstung. Schwellen: expedition.visual-armor.animal-levels. */
    public int animalStage(int level) {
        List<Integer> steps = plugin.getConfig().getIntegerList("expedition.visual-armor.animal-levels");
        if (steps.isEmpty()) {
            steps = List.of(5, 15, 30, 50, 80);
        }
        int stage = 0;
        for (int need : steps) {
            if (level >= need && stage < 5) {
                stage++;
            }
        }
        return stage;
    }

    /** Material-Stufe (0 bis 4) eines Teils (0 Schuhe, 1 Helm, 2 Hose, 3 Brustpanzer) bei diesem Schritt; -1 = noch nichts. */
    private static int pieceTier(int step, int piece) {
        if (step <= 0) {
            return -1;
        }
        int tier = (step - 1) / 4;
        int done = (step - 1) % 4;
        return piece <= done ? tier : tier - 1;
    }

    public String armorLine(Companion c, int level) {
        if (!cfgBool("visual-armor.enabled", true)) {
            return null;
        }
        if (wearsArmor(c)) {
            int step = armorStep(level);
            if (step == 0) {
                return "Aussehen: noch keine Rüstung (erstes Teil ab Level " + Math.max(1, cfgI("visual-armor.first-level", 4)) + "), nur optisch";
            }
            StringBuilder sb = new StringBuilder("Aussehen: ");
            for (int piece = 0; piece < 4; piece++) {
                int tier = pieceTier(step, piece);
                if (tier >= 0) {
                    sb.append(VIS_PIECE_NAMES[piece]).append(' ').append(VIS_TIER_NAMES[tier]).append(piece < 3 ? ", " : "");
                }
            }
            return sb.toString().replaceAll(", $", "") + (step < 20 ? " (nächstes Teil ab Level "
                    + (Math.max(1, cfgI("visual-armor.first-level", 4)) + step * Math.max(1, cfgI("visual-armor.levels-per-step", 4))) + ")" : "") + ", nur optisch";
        }
        if (wearsAnimalGear(c)) {
            int stage = animalStage(level);
            String what;
            if (c.type.equals("WOLF")) {
                what = stage >= 1 ? "Wolfsrüstung in zufälliger Farbe" : "noch keine Rüstung";
            } else if (c.type.equals("LLAMA") || c.type.equals("TRADER_LLAMA")) {
                what = stage >= 1 ? "Teppich in zufälliger Farbe" : "noch kein Teppich";
            } else if (stage == 0) {
                what = "noch kein Sattel";
            } else if (c.type.equals("HORSE") && stage >= 2) {
                what = "Sattel und " + HORSE_ARMOR_NAMES[Math.min(3, stage - 2)];
            } else {
                what = "Sattel";
            }
            return "Aussehen: " + what + ", nur optisch";
        }
        return null;
    }

    private static ItemStack stripped(Material m) {
        ItemStack item = new ItemStack(m);
        // ohne jeden Schutz: die Standardwerte werden durch eine leere Liste ersetzt
        item.editMeta(meta -> meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of()));
        return item;
    }

    private static ItemStack dyed(Material m, org.bukkit.Color color) {
        ItemStack item = new ItemStack(m);
        item.editMeta(meta -> {
            if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm) {
                lm.setColor(color);
            }
            meta.setAttributeModifiers(com.google.common.collect.ImmutableMultimap.of());
        });
        return item;
    }

    /** Zieht dem Begleiter je nach Level Stück für Stück eine nur sichtbare Rüstung an (ohne Schutzwirkung); Tiere bekommen Sattel und Rüstung. */
    private void dressVisual(LivingEntity living, Companion c, UUID owner, int idx, int level) {
        if (!cfgBool("visual-armor.enabled", true)) {
            return;
        }
        EntityEquipment eq = living.getEquipment();
        if (wearsArmor(c) && eq != null) {
            int step = armorStep(level);
            ItemStack[] pieces = new ItemStack[4];
            for (int piece = 0; piece < 4; piece++) {
                int tier = pieceTier(step, piece);
                Material m = tier < 0 ? null : Material.matchMaterial(VIS_TIER_PREFIX[tier] + "_" + VIS_PIECES[piece]);
                pieces[piece] = m == null ? null : stripped(m);
            }
            if (pieces[0] != null) {
                eq.setBoots(pieces[0]);
                eq.setBootsDropChance(0f);
            }
            if (pieces[1] != null) {
                eq.setHelmet(pieces[1]);
                eq.setHelmetDropChance(0f);
            }
            if (pieces[2] != null) {
                eq.setLeggings(pieces[2]);
                eq.setLeggingsDropChance(0f);
            }
            if (pieces[3] != null) {
                eq.setChestplate(pieces[3]);
                eq.setChestplateDropChance(0f);
            }
            return;
        }
        if (!wearsAnimalGear(c)) {
            return;
        }
        int stage = animalStage(level);
        if (stage <= 0) {
            return;
        }
        // die Farbe ist zufällig, bleibt aber für jedes Exemplar gleich
        Random r = new Random(Objects.hash(owner, c.key, idx, "farbe"));
        org.bukkit.DyeColor dye = org.bukkit.DyeColor.values()[r.nextInt(org.bukkit.DyeColor.values().length)];
        if (living instanceof org.bukkit.entity.Wolf && eq != null) {
            Material armor = Material.matchMaterial("WOLF_ARMOR");
            if (armor != null) {
                eq.setItem(org.bukkit.inventory.EquipmentSlot.BODY, dyed(armor, dye.getColor()));
                eq.setDropChance(org.bukkit.inventory.EquipmentSlot.BODY, 0f);
            }
            return;
        }
        if (living instanceof org.bukkit.entity.Llama llama) {
            Material carpet = Material.matchMaterial(dye.name() + "_CARPET");
            if (carpet != null) {
                llama.getInventory().setDecor(new ItemStack(carpet));
            }
            return;
        }
        if (living instanceof org.bukkit.entity.Steerable steerable) {
            steerable.setSaddle(true); // Schwein und Schreiter
            return;
        }
        if (living instanceof org.bukkit.entity.AbstractHorse horse) {
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            if (living instanceof org.bukkit.entity.Horse h && stage >= 2) {
                int which = Math.min(3, stage - 2);
                Material armor = Material.matchMaterial(HORSE_ARMOR[which]);
                if (armor != null) {
                    h.getInventory().setArmor(which == 0 ? dyed(armor, dye.getColor()) : stripped(armor));
                }
            }
        }
    }

    private int radiusOf(int tier) {
        return 22 + Math.min(20, tier);
    }

    private Location center(World world, Session s) {
        return new Location(world, s.slot * SLOT_SPACING + 0.5, ARENA_Y + 1, 0.5);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Baut die Arena der Stufe neu (löscht vorher alles im Bereich). */
    private void buildArena(World world, Session s, int tier) {
        int cx = s.slot * SLOT_SPACING;
        int r = radiusOf(tier);
        Theme theme = THEMES[tier % THEMES.length];
        int variant = (tier / THEMES.length) % 4;
        int outer = 22 + 20 + 2;
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                for (int y = ARENA_Y - 1; y <= ARENA_Y + ARENA_HEIGHT + 2; y++) {
                    world.getBlockAt(cx + x, y, z).setType(Material.AIR, false);
                }
            }
        }
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean lamp = x % 6 == 0 && z % 6 == 0;
                Material m = lamp ? theme.light() : ((x + z) % 2 == 0 ? theme.floor() : theme.floor2());
                world.getBlockAt(cx + x, ARENA_Y, z).setType(m, false);
                world.getBlockAt(cx + x, ARENA_Y - 1, z).setType(theme.floor(), false);
                world.getBlockAt(cx + x, ARENA_Y + ARENA_HEIGHT + 1, z).setType(lamp ? theme.light() : theme.wall(), false); // Dach
            }
        }
        for (int i = -r - 1; i <= r + 1; i++) {
            for (int y = ARENA_Y + 1; y <= ARENA_Y + ARENA_HEIGHT; y++) {
                world.getBlockAt(cx + i, y, -r - 1).setType(theme.wall(), false);
                world.getBlockAt(cx + i, y, r + 1).setType(theme.wall(), false);
                world.getBlockAt(cx - r - 1, y, i).setType(theme.wall(), false);
                world.getBlockAt(cx + r + 1, y, i).setType(theme.wall(), false);
            }
            world.getBlockAt(cx + i, ARENA_Y + ARENA_HEIGHT + 1, -r - 1).setType(theme.wall(), false);
            world.getBlockAt(cx + i, ARENA_Y + ARENA_HEIGHT + 1, r + 1).setType(theme.wall(), false);
            world.getBlockAt(cx - r - 1, ARENA_Y + ARENA_HEIGHT + 1, i).setType(theme.wall(), false);
            world.getBlockAt(cx + r + 1, ARENA_Y + ARENA_HEIGHT + 1, i).setType(theme.wall(), false);
        }
        if (tier % THEME_COUNT == OCEAN) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    for (int y = 1; y <= 6; y++) {
                        world.getBlockAt(cx + x, ARENA_Y + y, z).setType(Material.WATER, false);
                    }
                }
            }
        }
        // Hindernisse je nach Bauart (immer abseits der Mitte und des Randes)
        Random random = new Random(tier * 31L + 7);
        switch (variant) {
            case 1 -> {
                int pillars = 6 + r / 3;
                for (int i = 0; i < pillars; i++) {
                    int px = random.nextInt(2 * (r - 5) + 1) - (r - 5);
                    int pz = random.nextInt(2 * (r - 5) + 1) - (r - 5);
                    if (Math.abs(px) < 4 && Math.abs(pz) < 4) {
                        continue;
                    }
                    for (int y = 1; y <= 5; y++) {
                        world.getBlockAt(cx + px, ARENA_Y + y, pz).setType(theme.pillar(), false);
                    }
                }
            }
            case 2 -> {
                int o = r - 5;
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        for (int dx = 0; dx < 3; dx++) {
                            for (int dz = 0; dz < 3; dz++) {
                                for (int y = 1; y <= 4; y++) {
                                    world.getBlockAt(cx + sx * o + dx, ARENA_Y + y, sz * o + dz).setType(theme.pillar(), false);
                                }
                            }
                        }
                    }
                }
            }
            case 3 -> {
                int walls = 5 + r / 3;
                for (int i = 0; i < walls; i++) {
                    int px = random.nextInt(2 * (r - 6) + 1) - (r - 6);
                    int pz = random.nextInt(2 * (r - 6) + 1) - (r - 6);
                    boolean alongX = random.nextBoolean();
                    int len = 3 + random.nextInt(3);
                    for (int d = 0; d < len; d++) {
                        int wx = px + (alongX ? d : 0);
                        int wz = pz + (alongX ? 0 : d);
                        if (Math.abs(wx) < 4 && Math.abs(wz) < 4) {
                            continue;
                        }
                        if (Math.abs(wx) > r - 3 || Math.abs(wz) > r - 3) {
                            continue;
                        }
                        world.getBlockAt(cx + wx, ARENA_Y + 1, wz).setType(theme.wall(), false);
                        world.getBlockAt(cx + wx, ARENA_Y + 2, wz).setType(theme.wall(), false);
                    }
                }
            }
            default -> {
                // schlichte Fläche
            }
        }
        s.builtTier = tier;
        s.placed.clear();
    }

    private void clearPlaced(World world, Session s) {
        int cx = s.slot * SLOT_SPACING;
        for (int dx = -40; dx <= 40; dx++) {
            for (int dz = -40; dz <= 40; dz++) {
                for (int y = ARENA_Y + 1; y <= ARENA_Y + ARENA_HEIGHT; y++) {
                    if (s.placed.contains(key(cx + dx, y, dz))) {
                        world.getBlockAt(cx + dx, y, dz).setType(Material.AIR, false);
                    }
                }
            }
        }
        s.placed.clear();
    }

    // ------------------------------------------------------------------
    //  Start, Level, Ende
    // ------------------------------------------------------------------

    /** Prüft, ob der Spieler starten darf; sendet sonst den Grund. */
    public boolean canStart(Player player, boolean quiet) {
        Messages msg = plugin.messages();
        UUID uuid = player.getUniqueId();
        if (!enabled()) {
            return false;
        }
        if (plugin.timeline().previewFor(player)) {
            if (!quiet) {
                msg.send(player, "ex-preview");
            }
            return false;
        }
        if (!hasLicense(player)) {
            if (!quiet) {
                msg.send(player, "ex-locked");
            }
            return false;
        }
        if (isIn(uuid)) {
            if (!quiet) {
                msg.send(player, "ex-already");
            }
            return false;
        }
        if (liveInstance != null && liveInstance.watching(uuid)) {
            return false;
        }
        if (plugin.freecam().isActive(player)) {
            if (!quiet) {
                msg.send(player, "ex-freecam");
            }
            return false;
        }
        if (nextLevel(uuid) > MAX_LEVEL) {
            if (!quiet) {
                msg.send(player, "ex-all-done");
            }
            return false;
        }
        return true;
    }

    public void start(Player player) {
        cancelPending(player.getUniqueId());
        begin(player, false);
    }

    /** Startet den Verteidigungsmodus (Tower Defense) mit dem nächsten Verteidigungs-Level des Spielers. */
    public void startDefense(Player player) {
        if (!defenseEnabled()) {
            return;
        }
        begin(player, true);
    }

    private record Invite(UUID host, long until) { }

    private final Map<UUID, Invite> invites = new HashMap<>();

    /** Die Wartestube vor einer gemeinsamen Verteidigung: Gastgeber lädt ein, Eingeladene wählen ihre Begleiter, dann startet der Gastgeber. */
    private static final class Lobby {
        final UUID host;
        Set<Copy> hostPick = new HashSet<>();
        final Set<UUID> invited = new HashSet<>();
        final Map<UUID, Set<Copy>> ready = new LinkedHashMap<>();

        Lobby(UUID host) {
            this.host = host;
        }
    }

    private final Map<UUID, Lobby> lobbies = new HashMap<>();

    /** Spieler, die der Gastgeber einladen kann (online, nicht schon in einer Expedition). */
    public List<Player> invitable(Player host) {
        List<Player> out = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.getUniqueId().equals(host.getUniqueId()) && !sessions.containsKey(o.getUniqueId())) {
                out.add(o);
            }
        }
        out.sort(java.util.Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** 0 = nicht eingeladen, 1 = eingeladen, 2 = bereit (Begleiter gewählt). */
    public int lobbyState(UUID host, UUID target) {
        Lobby l = lobbies.get(host);
        if (l == null) {
            return 0;
        }
        return l.ready.containsKey(target) ? 2 : l.invited.contains(target) ? 1 : 0;
    }

    public int lobbyReadyCount(UUID host) {
        Lobby l = lobbies.get(host);
        return l == null ? 0 : l.ready.size();
    }

    /** Öffnet das Einladungsmenü (nur vor dem Start, solange der Gastgeber in der Wartestube ist). */
    public void openInviteMenu(Player host) {
        if (!lobbies.containsKey(host.getUniqueId())) {
            plugin.messages().send(host, "ex-td-invite-host-only");
            return;
        }
        new de.coinshop.gui.DefenseInviteMenu(plugin, host).open(host);
    }

    /** Öffnet die Wartestube des Gastgebers. */
    private void openLobby(Player host, Set<Copy> picks) {
        Lobby l = new Lobby(host.getUniqueId());
        l.hostPick = new HashSet<>(picks);
        lobbies.put(host.getUniqueId(), l);
        new de.coinshop.gui.DefenseInviteMenu(plugin, host).open(host);
    }

    /** Lädt einen Spieler ein (nur vor dem Start). */
    public void invite(Player host, Player target) {
        Messages msg = plugin.messages();
        Lobby l = lobbies.get(host.getUniqueId());
        if (l == null) {
            msg.send(host, "ex-td-invite-host-only");
            return;
        }
        if (l.ready.size() + 1 >= Math.max(2, cfgI("defense.max-players", 4))) {
            msg.send(host, "ex-td-join-full");
            return;
        }
        if (!target.isOnline() || sessions.containsKey(target.getUniqueId())) {
            msg.send(host, "ex-td-invite-unavailable", "player", target.getName());
            return;
        }
        l.invited.add(target.getUniqueId());
        invites.put(target.getUniqueId(), new Invite(host.getUniqueId(), System.currentTimeMillis() + 600_000L));
        msg.send(host, "ex-td-invite-sent", "player", target.getName());
        msg.send(target, "ex-td-invite-got", "player", host.getName());
    }

    /** Der Eingeladene nimmt an (Klick im Chat oder /expedition annehmen): erst Begleiter wählen, dann warten. */
    public void acceptInvite(Player guest) {
        Invite in = invites.get(guest.getUniqueId());
        Lobby l = in == null ? null : lobbies.get(in.host());
        Player host = in == null ? null : Bukkit.getPlayer(in.host());
        if (in == null || in.until() < System.currentTimeMillis() || l == null || host == null) {
            invites.remove(guest.getUniqueId());
            plugin.messages().send(guest, "ex-td-invite-none");
            return;
        }
        if (sessions.containsKey(guest.getUniqueId())) {
            plugin.messages().send(guest, "ex-already");
            return;
        }
        if (!canStart(guest, false)) {
            return;
        }
        UUID gid = guest.getUniqueId();
        if (!takenReady(gid).isEmpty()) {
            pendingJoin.put(gid, in.host());
            new de.coinshop.gui.ExpeditionCopyMenu(plugin, guest, de.coinshop.gui.ExpeditionCopyMenu.Mode.PICK, 0).open(guest);
            return;
        }
        lobbyReady(guest, in.host(), new HashSet<>());
    }

    /** Ein Eingeladener hat seine Begleiter gewählt und wartet auf den Start. */
    private void lobbyReady(Player guest, UUID hostId, Set<Copy> picks) {
        Lobby l = lobbies.get(hostId);
        Player host = Bukkit.getPlayer(hostId);
        invites.remove(guest.getUniqueId());
        if (l == null || host == null) {
            plugin.messages().send(guest, "ex-td-invite-none");
            return;
        }
        l.ready.put(guest.getUniqueId(), picks);
        plugin.messages().send(guest, "ex-td-lobby-ready", "player", host.getName());
        plugin.messages().send(host, "ex-td-lobby-joined", "player", guest.getName(), "count", l.ready.size() + 1);
    }

    /** Der Gastgeber startet: alle bereiten Mitspieler werden gemeinsam in die Arena gebracht. */
    public void startLobby(Player host) {
        Lobby l = lobbies.remove(host.getUniqueId());
        if (l == null) {
            return;
        }
        host.closeInventory();
        pickOverride.put(host.getUniqueId(), l.hostPick);
        try {
            startDefense(host);
        } finally {
            pickOverride.remove(host.getUniqueId());
        }
        if (!sessions.containsKey(host.getUniqueId())) {
            for (UUID g : l.ready.keySet()) {
                Player gp = Bukkit.getPlayer(g);
                if (gp != null) {
                    plugin.messages().send(gp, "ex-td-lobby-cancel", "player", host.getName());
                }
            }
            return;
        }
        for (Map.Entry<UUID, Set<Copy>> e : l.ready.entrySet()) {
            Player gp = Bukkit.getPlayer(e.getKey());
            if (gp == null || sessions.containsKey(gp.getUniqueId())) {
                continue;
            }
            pickOverride.put(e.getKey(), e.getValue());
            try {
                doJoinDefense(gp, host.getUniqueId());
            } finally {
                pickOverride.remove(e.getKey());
            }
        }
    }

    /** Der Gastgeber bricht die Wartestube ab (oder geht). */
    public void cancelLobby(Player host) {
        Lobby l = lobbies.remove(host.getUniqueId());
        if (l == null) {
            return;
        }
        for (UUID g : l.invited) {
            invites.remove(g);
            Player gp = Bukkit.getPlayer(g);
            if (gp != null) {
                plugin.messages().send(gp, "ex-td-lobby-cancel", "player", host.getName());
            }
        }
        host.closeInventory();
    }

    /** Beitritt per Befehl ist nicht möglich: Mitspieler müssen vor dem Start eingeladen werden. */
    public void joinDefense(Player guest, String hostName) {
        plugin.messages().send(guest, "ex-td-join-late");
    }

    private void doJoinDefense(Player guest, UUID hostId) {
        Messages msg = plugin.messages();
        Session hs = sessions.get(hostId);
        Player hostPlayer = Bukkit.getPlayer(hostId);
        if (hs == null || hostPlayer == null || !hs.td || hs.host != null || hs.finishing || hs.defeated
                || hs.guests.size() + 1 >= Math.max(2, cfgI("defense.max-players", 4))) {
            msg.send(guest, "ex-td-join-none", "player", hostPlayer == null ? "?" : hostPlayer.getName());
            return;
        }
        if (!canStart(guest, false) || !saveState(guest)) {
            return;
        }
        World world = world();
        if (world == null) {
            msg.send(guest, "error");
            return;
        }
        UUID gid = guest.getUniqueId();
        Session gs = new Session(gid, hs.slot, hs.level);
        gs.td = true;
        gs.host = hostId;
        gs.core = hs.core;
        gs.builtTier = hs.builtTier;
        gs.hpMult = hs.hpMult;
        gs.dmgMult = hs.dmgMult;
        sessions.put(gid, gs);
        hs.guests.add(gid);
        guest.closeInventory();
        internalTeleport = true;
        try {
            guest.teleport(hostPlayer.getLocation());
        } finally {
            internalTeleport = false;
        }
        guest.setGameMode(GameMode.SURVIVAL);
        guest.setAllowFlight(false);
        guest.setFlying(false);
        guest.setFireTicks(0);
        guest.setFallDistance(0);
        for (PotionEffect effect : new ArrayList<>(guest.getActivePotionEffects())) {
            guest.removePotionEffect(effect.getType());
        }
        AttributeInstance gmax = guest.getAttribute(Attribute.MAX_HEALTH);
        guest.setHealth(gmax == null ? 20 : Math.min(20, gmax.getValue()));
        guest.setFoodLevel(20);
        guest.setSaturation(20f);
        guest.setLevel(0);
        guest.setExp(0f);
        giveKit(guest);
        for (long delay : new long[]{1L, 3L, 10L, 20L, 60L, 100L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Session now = sessions.get(gid);
                if (now == gs && !gs.finishing && guest.isOnline()) {
                    guardKit(guest, gs);
                    guest.updateInventory();
                }
            }, delay);
        }
        spawnCompanions(guest, gs);
        // Posten auf einem Ring um den Kern (versetzt zu denen des Gastgebers)
        int n = gs.companions.size();
        double ring = Math.max(5, cfgD("defense.free-radius", 4) + 3);
        int i = 0;
        for (UUID cid : new ArrayList<>(gs.companions)) {
            Entity e = Bukkit.getEntity(cid);
            if (e == null) {
                continue;
            }
            double angle = Math.PI / Math.max(1, n) + (Math.PI * 2 * i) / Math.max(1, n);
            Location at = new Location(world, hs.core.getBlockX() + 0.5 + Math.cos(angle) * ring, hs.core.getBlockY(),
                    hs.core.getBlockZ() + 0.5 + Math.sin(angle) * ring);
            e.teleport(at);
            gs.posts.put(cid, at);
            i++;
        }
        giveCommandRod(guest);
        msg.send(guest, "ex-td-help");
        msg.send(guest, "ex-td-joined", "player", hostPlayer.getName());
        msg.send(hostPlayer, "ex-td-guest-joined", "player", guest.getName(), "count", hs.guests.size() + 1);
    }

    /** Der Gastgeber geht: alle Gäste werden ausgezahlt und zurückgeschickt. */
    private void endGuests(Session hs, String messageKey) {
        for (UUID g : new ArrayList<>(hs.guests)) {
            Player gp = Bukkit.getPlayer(g);
            if (gp != null && sessions.containsKey(g)) {
                plugin.messages().send(gp, messageKey);
                finish(gp, null);
            } else {
                sessions.remove(g);
            }
        }
        hs.guests.clear();
    }

    /** Die Sitzung, in der die Gegner, der Kern und die Wellen liegen (bei Gästen die des Gastgebers). */
    private Session mobSession(Session s) {
        if (s.host == null) {
            return s;
        }
        Session hs = sessions.get(s.host);
        return hs == null ? s : hs;
    }

    private boolean inGroup(Session s, UUID uuid) {
        return s.player.equals(uuid) || s.guests.contains(uuid);
    }

    private void begin(Player player, boolean defense) {
        Messages msg = plugin.messages();
        if (!canStart(player, false)) {
            return;
        }
        World world = world();
        if (world == null) {
            msg.send(player, "error");
            return;
        }
        UUID uuid = player.getUniqueId();
        Stamina.Mode stamMode = defense ? Stamina.Mode.DEFENSE : Stamina.Mode.EXPEDITION;
        if (!plugin.stamina().has(uuid, stamMode)) {
            plugin.stamina().sendMissing(player, stamMode);
            return;
        }
        if (!defense && !pickOverride.containsKey(uuid) && needsPick(uuid, nextLevel(uuid))) {
            new de.coinshop.gui.ExpeditionCopyMenu(plugin, player, de.coinshop.gui.ExpeditionCopyMenu.Mode.PICK, nextLevel(uuid)).open(player);
            return;
        }
        if (!defense && !pickOverride.containsKey(uuid) && !substituteDone.remove(uuid) && canTakeMore(uuid, nextLevel(uuid))) {
            new de.coinshop.gui.ExpeditionSubstituteMenu(plugin, player, nextLevel(uuid)).open(player);
            return;
        }
        if (defense && !pickOverride.containsKey(uuid) && takenReady(uuid).isEmpty() && !invitable(player).isEmpty()
                && !lobbies.containsKey(uuid)) {
            openLobby(player, new HashSet<>()); // ohne Begleiter: trotzdem Freunde einladen können
            return;
        }
        if (defense && !pickOverride.containsKey(uuid) && !takenReady(uuid).isEmpty()) {
            // Vor dem Teleport: Begleiter nochmal ansehen und wählen (Level 0 = Verteidigung)
            pendingDefense.add(uuid);
            new de.coinshop.gui.ExpeditionCopyMenu(plugin, player, de.coinshop.gui.ExpeditionCopyMenu.Mode.PICK, 0).open(player);
            return;
        }
        int slot = 0;
        Set<Integer> used = new HashSet<>();
        for (Session other : sessions.values()) {
            used.add(other.slot);
        }
        while (used.contains(slot)) {
            slot++;
        }
        Session s = new Session(uuid, slot, defense ? 1 : nextLevel(uuid));
        s.td = defense;
        if (!plugin.stamina().has(uuid, stamMode)) {
            plugin.stamina().sendMissing(player, stamMode);
            return;
        }
        if (!saveState(player)) {
            msg.send(player, "error");
            return;
        }
        plugin.stamina().spend(uuid, stamMode); // Ausdauer wird beim Start abgezogen
        sessions.put(uuid, s);
        player.closeInventory();
        beginLevel(player, s, true);
    }

    private void beginLevel(Player player, Session s, boolean first) {
        World world = world();
        if (world == null) {
            finish(player, "error");
            return;
        }
        tip(player, s.td ? "td" : s.mine ? "mine" : "start");
        tip(player, "tags");
        Plan plan = plan(s.level);
        int tier = arenaTier(s);
        CompanionPowers.configure(cfgI("power.levels-per-use", 10), cfgI("power.max-uses", 8));
        if (s.mine) {
            buildMine(world, s);
        } else {
            buildArena(world, s, tier); // jedes Level eine frische Arena (kein Schnee, keine Reste)
        }
        s.sameHealed.clear();
        s.healLeft.clear();
        s.mobs.clear();
        s.peaceful.clear();
        s.queue.clear();
        for (Map.Entry<EntityType, Integer> e : plan.mobs().entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                s.queue.add(e.getKey());
            }
        }
        java.util.Collections.shuffle(s.queue, new Random(s.level * 31L));
        s.bossType = plan.leader();
        s.bossSpawned = false;
        if (plan.leader() != null && s.queue.remove(plan.leader())) {
            s.queue.add(0, plan.leader()); // der Anführer des Bosslevels kommt zuerst
        }
        if (s.mine) {
            mineQueue(s);
        }
        s.killCoins = 0;
        s.levelKills = 0;
        if (!s.td) {
            // Jeder Gegner bringt Coins wie in der Verteidigung (Wert nach Kosten; skaliert mit dem Belohnungs-Multiplikator)
            double queueCost = 0;
            for (EntityType t : s.queue) {
                MobDef d = defOf(t);
                queueCost += d == null ? 3 : d.cost();
            }
            double factor = Math.max(0, cfgD("kill-reward-factor", cfgD("defense.reward-factor", 0.6)));
            s.tdValue = queueCost <= 0 ? 0 : reward(s.player, s.level) * factor / queueCost;
        }
        removeCoinMobs(s);
        s.bonusCoins = 0;
        s.coinAt = 0;
        s.coinCount = 0;
        s.miniAt = 0;
        s.miniId = null;
        if (!s.td && !s.mine && new Random().nextInt(100) < cfgI("mini-boss-chance-percent", 8)) {
            s.miniAt = System.currentTimeMillis() + 3000L; // eine der Kreaturen des Levels wird zum Mini-Boss
        }
        s.lootAt = 0;
        s.lootKilled = false;
        if (!s.td && !s.mine && new Random().nextInt(10000) < cfgI("loot-mob.per-10000", 3)) {
            s.lootAt = System.currentTimeMillis() + 3000L; // wird aus den Kreaturen des Levels bestimmt
        }
        if (!s.td && new Random().nextInt(100) < cfgI("coin-mob-chance-percent", 8)) {
            // Zufallsereignis: irgendwann in den ersten Minuten des Levels taucht ein CoinMob auf
            s.coinCount = Math.max(1, Math.min(cfgI("coin-mob-max", 3), 1 + s.level / 1000));
            s.coinAt = System.currentTimeMillis() + 3000L; // wird aus den Kreaturen des Levels bestimmt
        }
        s.hpMult = plan.hpMult();
        s.dmgMult = plan.dmgMult();
        if (s.mine) {
            // Jede Ebene ist schwerer als die davor: Leben und Schaden wachsen mit der Tiefe gleichmäßig
            s.hpMult = 1 + s.depth * Math.max(0, cfgD("mine.hp-per-depth", 0.12));
            s.dmgMult = 1 + s.depth * Math.max(0, cfgD("mine.dmg-per-depth", 0.08));
        }
        if (s.td) {
            tdAdjust(s);
            s.waveStart = System.currentTimeMillis();
            s.pauseUntil = 0;
        }
        s.cleared = false;
        s.defeated = false;
        s.levelStart = System.currentTimeMillis();
        s.shopUntil = 0;
        if (!s.mine) {
            examSetup(player, s);
        }
        if (!s.td && !s.mine) {
            planEvent(s, false);
        }

        internalTeleport = true;
        try {
            player.teleport(center(world, s));
        } finally {
            internalTeleport = false;
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0);
        for (org.bukkit.potion.PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : Math.min(20, max.getValue()));
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setLevel(0);
        player.setExp(0f);
        if (tier % THEME_COUNT == OCEAN) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 60 * 15, 0, false, false, false));
        }
        giveKit(player);
        for (long delay : new long[]{1L, 3L, 10L, 20L, 60L, 100L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Session now = sessions.get(player.getUniqueId());
                if (now == s && !s.finishing && player.isOnline()) {
                    guardKit(player, s);
                    player.updateInventory(); // Rüstung und Schild werden sonst manchmal nicht angezeigt
                }
            }, delay);
        }
        spawnCompanions(player, s);
        if (s.td) {
            setupDefense(player, s);
        }
        if (s.mine) {
            mineAnnounce(player, s);
        } else {
            plugin.messages().send(player, s.td ? "ex-td-start" : "ex-level-start", "level", s.level,
                    "count", s.td ? s.queue.size() : plan.total(), "arena", themeName(tier));
        }
        if (!s.mine && biomeType(tier) != null && cfgBool("biome.enabled", true)) {
            plugin.messages().send(player, "ex-biome", "text", biomeLine(tier));
        }
        if (s.shopUntil > 0) {
            plugin.messages().send(player, "ex-shop-phase", "sec", Math.max(1, (s.shopUntil - System.currentTimeMillis()) / 1000));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Session now = sessions.get(player.getUniqueId());
                if (now == s && s.shopUntil > System.currentTimeMillis() && player.isOnline()) {
                    new de.coinshop.gui.ExpeditionGearMenu(plugin, player).open(player);
                }
            }, 15L);
        }
        if (plan.boss() && !s.td && !s.mine) {
            plugin.messages().send(player, "ex-boss-start", "level", s.level);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
        }
        if (first) {
            plugin.messages().send(player, s.mine ? "mine-rules" : "ex-rules");
        }
        if (s.td) {
            return; // Verteidigung: erst nach der Vorbereitungszeit
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Session now = sessions.get(player.getUniqueId());
            if (now == s && !s.cleared && !s.defeated && player.isOnline()) {
                spawnWave(player, s);
            }
        }, 60L);
    }

    private static final Set<String> DRESSED = Set.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY",
            "BOGGED", "WITHER_SKELETON", "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE", "PILLAGER", "VINDICATOR");
    private static final Set<String> KEEP_WEAPON = Set.of("SKELETON", "STRAY", "BOGGED", "WITHER_SKELETON", "PILLAGER",
            "VINDICATOR", "PIGLIN_BRUTE");
    private static final Set<String> FLYING = Set.of("BLAZE", "PHANTOM", "VEX", "PARROT", "GHAST", "HAPPY_GHAST", "BAT", "ALLAY", "BREEZE");

    /** Wie hoch über dem Boden eine Kreatur startet (Allays schweben tief, damit man sie erreicht). */
    private static int flyHeight(EntityType type) {
        if (type == EntityType.ALLAY) {
            return 1;
        }
        return FLYING.contains(type.name()) ? 3 : 0;
    }

    private void spawnWave(Player player, Session s) {
        World world = player.getWorld();
        Location c = center(world, s);
        int r = radiusOf(arenaTier(s));
        int waveSize = Math.min(s.queue.size(), Math.max(10, cfgI("wave-size", 20)));
        int retried = 0;
        Random random = new Random();
        spawning = true;
        try {
            for (int i = 0; i < waveSize; i++) {
                EntityType type = s.queue.remove(0);
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = r - 3 - random.nextInt(2);
                Location at = c.clone().add(Math.cos(angle) * dist, flyHeight(type), Math.sin(angle) * dist);
                LivingEntity living = spawnOne(player, s, type, at, random);
                if (living == null) {
                    // Die Kreatur ließ sich nicht erzeugen: einmal später noch einmal versuchen, damit keiner verloren geht
                    if (retried++ < 3) {
                        s.queue.add(type);
                    }
                    continue;
                }
                if (type == s.bossType && !s.bossSpawned) {
                    markBoss(s, living, type);
                }
                s.mobs.add(living.getUniqueId());
            }
        } finally {
            spawning = false;
        }
    }

    /** Macht eine Kreatur zum Anführer (Boss) des Levels. */
    private void markBoss(Session s, LivingEntity living, EntityType type) {
        s.bossSpawned = true;
        s.bossId = living.getUniqueId();
        scale(living, Attribute.MAX_HEALTH, 1.5, 1024);
        scale(living, Attribute.ATTACK_DAMAGE, 1.15, 2048);
        AttributeInstance bossHp = living.getAttribute(Attribute.MAX_HEALTH);
        if (bossHp != null) {
            living.setHealth(bossHp.getValue());
        }
        living.customName(Component.text("★ " + (s.mine ? "WÄCHTER" : "BOSS") + ": " + mobName(type) + " ★", NamedTextColor.RED, TextDecoration.BOLD));
        living.setCustomNameVisible(true);
        living.setGlowing(true);
    }

    /** Erzeugt eine Kreatur mit Ausrüstung und Stärke des Levels (der Aufrufer trägt sie in die Listen ein). */
    private LivingEntity spawnOne(Player player, Session s, EntityType type, Location at, Random random) {
        MobDef def = defOf(type);
        Entity entity = at.getWorld().spawnEntity(at, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return null;
        }
        living.setPersistent(false);
        living.setRemoveWhenFarAway(false);
        living.setCanPickupItems(false);
        living.setInvulnerable(false);
        living.customName(Component.text(mobName(type) + " Lv." + s.level, NamedTextColor.RED));
        living.setCustomNameVisible(true);
        widenSight(living);
        variant(living, s.level, random);
        dress(living, s.level, random);
        double foeBiome = biomeFactor(s, type.name());
        scale(living, Attribute.MAX_HEALTH, s.hpMult * foeBiome, 1024);
        scale(living, Attribute.ATTACK_DAMAGE, s.dmgMult * foeBiome, 2048);
        AttributeInstance follow = living.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(s.td ? Math.max(2, cfgD("defense.mob-follow-range", 12)) : Math.max(follow.getBaseValue(), 64));
        }
        AttributeInstance hp = living.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            living.setHealth(hp.getValue());
        }
        if (living instanceof Mob mob && !s.td) {
            mob.setTarget(player);
        }
        if (living instanceof org.bukkit.entity.Villager villager) {
            // erwachsen und mit Beruf, damit man mit ihm handeln kann
            villager.setAdult();
            org.bukkit.entity.Villager.Profession[] jobs = {org.bukkit.entity.Villager.Profession.FARMER,
                    org.bukkit.entity.Villager.Profession.LIBRARIAN, org.bukkit.entity.Villager.Profession.ARMORER,
                    org.bukkit.entity.Villager.Profession.WEAPONSMITH, org.bukkit.entity.Villager.Profession.TOOLSMITH,
                    org.bukkit.entity.Villager.Profession.CLERIC, org.bukkit.entity.Villager.Profession.BUTCHER,
                    org.bukkit.entity.Villager.Profession.FLETCHER, org.bukkit.entity.Villager.Profession.CARTOGRAPHER};
            villager.setProfession(jobs[random.nextInt(jobs.length)]);
            villager.setVillagerLevel(1 + random.nextInt(3));
            villager.setVillagerExperience(1);
        } else if (living instanceof org.bukkit.entity.WanderingTrader && living instanceof Ageable trader) {
            trader.setAdult();
        }
        return living;
    }

    /** Zufällige Varianten: Babys, Farben, Größen, geladene Creeper. */
    private void variant(LivingEntity e, int level, Random r) {
        try {
            if (e instanceof Slime slime) {
                slime.setSize(1 + r.nextInt(3));
            }
            if (e instanceof Zombie z && r.nextDouble() < 0.12) {
                z.setBaby(true);
            } else if (e instanceof Ageable a && !(e instanceof Zombie) && r.nextDouble() < 0.1) {
                a.setBaby();
            }
            if (e instanceof Creeper cr && level > 100 && r.nextDouble() < 0.15) {
                cr.setPowered(true);
            }
            if (e instanceof Sheep sh) {
                sh.setColor(DyeColor.values()[r.nextInt(DyeColor.values().length)]);
            }
            if (e instanceof Rabbit rb) {
                rb.setRabbitType(Rabbit.Type.values()[r.nextInt(Rabbit.Type.values().length)]);
            }
        } catch (RuntimeException ignored) {
            // Variante nicht möglich: Standard bleibt
        }
    }

    /** Rüstung und Waffen für Kreaturen, die so etwas tragen: mit dem Level wird es besser. */
    private void dress(LivingEntity e, int level, Random r) {
        String name = e.getType().name();
        EntityEquipment eq = e.getEquipment();
        if (eq == null || !DRESSED.contains(name)) {
            return;
        }
        int tier = level < 5 ? -1 : level < 20 ? 0 : level < 60 ? 1 : level < 150 ? 2 : level < 400 ? 3 : 4;
        double chance = Math.min(0.9, 0.15 + level / 150.0);
        String[] parts = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        for (int i = 0; tier >= 0 && i < parts.length; i++) {
            if (r.nextDouble() >= chance) {
                continue;
            }
            Material m = Material.matchMaterial(ARMOR_TIERS[Math.max(0, tier - r.nextInt(2))] + "_" + parts[i]);
            if (m == null) {
                continue;
            }
            ItemStack piece = new ItemStack(m);
            switch (i) {
                case 0 -> eq.setHelmet(piece);
                case 1 -> eq.setChestplate(piece);
                case 2 -> eq.setLeggings(piece);
                default -> eq.setBoots(piece);
            }
        }
        if (!KEEP_WEAPON.contains(name)) {
            int wt = Math.max(0, tier);
            double w = r.nextDouble();
            Material weapon = Material.matchMaterial(TOOL_TIERS[wt] + (w < 0.8 ? "_SWORD" : "_AXE"));
            if (weapon == null) {
                weapon = Material.matchMaterial(TOOL_TIERS[wt] + "_SWORD");
            }
            if (weapon != null) {
                eq.setItemInMainHand(new ItemStack(weapon));
            }
        }
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    /** Läuft alle halbe Sekunde: Funkeln um Loot-Mobs und Begleiter versorgen. Kreaturen haben nur ihre normalen Fähigkeiten. */
    public void proxyTick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            if (s.cleared || s.defeated || s.finishing) {
                continue;
            }
            Player p = Bukkit.getPlayer(s.player);
            if (p == null || !p.isOnline() || !p.getWorld().getName().equals(worldName())) {
                continue;
            }
            lootTick(s);
            try {
                creakingStep(s);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Expedition: Creaking-Schritt fehlgeschlagen: " + e);
            }
        }
    }

    /** Die ganze Arena soll in Sichtweite sein, sonst ignorieren Kreaturen Gegner, die weiter weg sind. */
    private static void widenSight(LivingEntity entity) {
        AttributeInstance follow = entity.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null && follow.getBaseValue() < 100) {
            follow.setBaseValue(100);
        }
    }

    private static void scale(LivingEntity entity, Attribute attribute, double factor, double cap) {
        AttributeInstance a = entity.getAttribute(attribute);
        if (a != null) {
            a.setBaseValue(Math.min(cap, a.getBaseValue() * factor));
        }
    }

    /** Läuft jede Sekunde: Wellen nachlegen, Level abschließen, Zeitlimit, Aufräumen. */
    public void tick() {
        for (Session s : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(s.player);
            if (player == null || !player.isOnline()) {
                continue;
            }
            try {
                tickSession(player, s);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Expedition: Fehler im Ablauf: " + e);
            }
        }
        World w = Bukkit.getWorld(worldName());
        if (w != null && w.getTime() < 12000) {
            w.setTime(18000); // immer Nacht, nichts brennt und nichts wächst
        }
        try {
            arenas().tick(); // Tagesertrag und Nachrichten der Untergrund-Arenen
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Arenen: Fehler im Ablauf: " + e);
        }
    }

    /** Ist der Spieler gerade in der Kaufphase vor einem Level? */
    public boolean inShopPhase(UUID uuid) {
        Session s = sessions.get(uuid);
        return s != null && !s.td && s.shopUntil > System.currentTimeMillis();
    }

    /** Preis der Ausrüstungsstufe: in der Kaufphase zum halben Preis. */
    public long gearPriceFor(UUID uuid, Gear gear, int toLevel) {
        long price = gearPrice(gear, toLevel);
        return inShopPhase(uuid) ? Math.max(1, price / 2) : price;
    }

    private void endShopPhase(Player player, Session s) {
        long shift = System.currentTimeMillis() - s.levelStart;
        s.shopUntil = 0;
        s.levelStart += shift; // die Zeit der Kaufphase zählt nicht zum Zeitlimit
        if (s.miniAt > 0) {
            s.miniAt += shift;
        }
        if (s.coinAt > 0) {
            s.coinAt += shift;
        }
        if (s.lootAt > 0) {
            s.lootAt += shift;
        }
        player.closeInventory();
        giveKit(player); // neue Ausrüstung anlegen
        player.updateInventory();
        plugin.messages().send(player, "ex-shop-end");
    }

    private void tickSession(Player player, Session s) {
        if (s.finishing) {
            return;
        }
        guardKit(player, s);
        if (s.aRegen > 0 && !s.cleared && !s.defeated) {
            AttributeInstance regenMax = player.getAttribute(Attribute.MAX_HEALTH);
            if (regenMax != null && player.getHealth() < regenMax.getValue()) {
                player.setHealth(Math.min(regenMax.getValue(), player.getHealth() + s.aRegen));
            }
        }
        if (s.host != null) {
            guestTick(player, s);
            return;
        }
        companionTick(player, s);
        enemyTick(player, s);
        if (s.cleared) {
            long wait = Math.max(10, cfgI("continue-seconds", 90)) * 1000L;
            if (System.currentTimeMillis() - s.clearedAt > wait) {
                finish(player, "ex-timeout-leave");
            }
            return;
        }
        if (s.defeated) {
            return;
        }
        s.mobs.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid() || e.isDead();
        });
        if (s.td) {
            defenseTick(player, s);
            return;
        }
        if (s.mine) {
            mineTick(player, s);
            return;
        }
        if (s.shopUntil > 0) {
            long left = s.shopUntil - System.currentTimeMillis();
            if (left > 0) {
                player.sendActionBar(plugin.messages().get("ex-shop-actionbar", "sec", (left + 999) / 1000));
                return;
            }
            endShopPhase(player, s);
        }
        if (examBreakTick(player, s)) {
            return;
        }
        examTick(player, s);
        eventTick(player, s);
        long limit = Math.max(1, cfgI("level-minutes", 10)) * 60_000L;
        if (System.currentTimeMillis() - s.levelStart > limit) {
            defeat(player, s, "ex-time-over");
            return;
        }
        if (s.miniAt > 0 && System.currentTimeMillis() >= s.miniAt) {
            s.miniAt = 0;
            spawnMiniBoss(player, s);
        }
        if (s.coinAt > 0 && System.currentTimeMillis() >= s.coinAt) {
            s.coinAt = 0;
            spawnSpecial(player, s, false);
        }
        if (s.lootAt > 0 && System.currentTimeMillis() >= s.lootAt) {
            s.lootAt = 0;
            spawnSpecial(player, s, true);
        }
        if (s.mobs.isEmpty() && !s.queue.isEmpty()) {
            spawnWave(player, s);
        } else if (s.mobs.isEmpty() && s.queue.isEmpty() && System.currentTimeMillis() - s.levelStart > 3000) {
            if (examPartDone(player, s)) {
                return;
            }
            complete(player, s);
            return;
        }
        long remaining = Math.max(0, limit - (System.currentTimeMillis() - s.levelStart)) / 1000;
        String time = String.format("%d:%02d", remaining / 60, remaining % 60);
        player.sendActionBar(plugin.messages().get("ex-actionbar", "level", s.level, "left", s.mobs.size() + s.queue.size(), "time", time,
                "kills", s.levelKills, "kcoins", Math.round(Math.max(0, s.killCoins) * (1 + s.aCoins)))
                .append(usesSummary(s, s.level)));
    }

    /** Gast in der Mehrspieler-Verteidigung: nur Begleiter und Anzeige, alles andere läuft beim Gastgeber. */
    private void guestTick(Player player, Session s) {
        Session hs = sessions.get(s.host);
        if (hs == null || hs.finishing) {
            plugin.messages().send(player, "ex-td-host-gone");
            finish(player, null);
            return;
        }
        companionTick(player, s);
        long now = System.currentTimeMillis();
        if (hs.prepUntil > 0 && now < hs.prepUntil) {
            player.sendActionBar(plugin.messages().get("ex-td-actionbar-prep", "sec", (hs.prepUntil - now + 999) / 1000));
        } else {
            player.sendActionBar(plugin.messages().get("ex-td-actionbar", "level", hs.level, "left", hs.mobs.size() + hs.queue.size(),
                    "hp", Math.max(0, Math.round(hs.coreHp)), "max", Math.round(hs.coreMax), "coins", Math.round(hs.tdCoins)));
        }
    }

    private void complete(Player player, Session s) {
        flushKills(player, s);
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        s.cleared = true;
        s.clearedAt = System.currentTimeMillis();
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.3f);
        long examMult = s.examKind >= 0 ? Math.max(1, cfgI("exam.reward-factor", 3)) : 1;
        long reward = Math.round(reward(uuid, s.level) * (1 + s.aCoins)) * examMult;
        long bonus = Math.round(Math.max(0, s.bonusCoins) * (1 + s.aCoins));
        s.bonusCoins = 0;
        long killPay = Math.round(Math.max(0, s.killCoins) * (1 + s.aCoins));
        s.killCoins = 0;
        removeCoinMobs(s);
        eventCleanup(s);
        db.begin();
        try {
            db.addCoins(uuid, reward + bonus + killPay);
            db.setMetaLong("ex_lvl_" + uuid, Math.max(nextLevel(uuid), s.level + 1));
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Belohnung fehlgeschlagen: " + e.getMessage());
        }
        db.logTx(uuid, player.getName(), "expedition", reward + bonus + killPay,
                "Level " + s.level + (bonus > 0 ? " (davon " + bonus + " von CoinMobs)" : "")
                        + (killPay > 0 ? " (davon " + killPay + " von Gegnern)" : ""));
        if (killPay > 0) {
            player.sendMessage(Component.text("+" + killPay + " Coins von besiegten Gegnern.", NamedTextColor.GOLD));
        }
        if (bonus > 0) {
            plugin.messages().send(player, "ex-coin-paid", "bonus", bonus);
        }
        if (s.lootKilled) {
            s.lootKilled = false;
            LootRewards.Result loot = loot().roll(player, s.level, s.lootTier);
            if (loot != null) {
                db.logTx(uuid, player.getName(), "expedition", 0, "Loot-Mob Level " + s.level + ": " + loot.text());
                plugin.messages().send(player, "ex-loot-paid", "reward", loot.text(), "tier", loot.tier());
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(uuid)) {
                        plugin.messages().send(online, "ex-loot-broadcast", "player", player.getName(),
                                "level", s.level, "reward", loot.text(), "tier", loot.tier());
                    }
                }
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.3f);
            }
        }
        if (!s.mercs.isEmpty()) {
            int xp = Math.max(0, cfgI("mercenary.owner-xp-per-level", 3));
            for (MercenaryManager.Offer o : s.mercs) {
                Copy k = o.copy();
                if (k != null && xp > 0) {
                    roster().addKills(o.owner, k.companion(), k.index(), xp);
                    roster().save(o.owner);
                }
            }
            s.mercs.clear();
        }
        if (s.examKind >= 0) {
            giveBox(player, "exam", LootRewards.tierOf(plugin, s.level, 0), "Prüfung bestanden: " + EXAM_NAMES[s.examKind]);
            db.setMetaLong("ex_exams_" + uuid, db.getMetaLong("ex_exams_" + uuid, 0) + 1);
            plugin.messages().send(player, "ex-exam-done", "name", EXAM_NAMES[s.examKind], "count", db.getMetaLong("ex_exams_" + uuid, 1));
            s.examKind = -1;
            db.setMetaLong("ex_exam_" + uuid, 0);
            db.setStats(uuid, Map.of("ex_exams", db.getMetaLong("ex_exams_" + uuid, 0)));
        }
        db.setStats(uuid, Map.of("ex_level", (long) Math.max(0, nextLevel(uuid) - 1)));
        if (s.level >= MAX_LEVEL) {
            plugin.messages().send(player, "ex-level-done", "level", s.level, "reward", reward);
            finish(player, "ex-all-done");
            return;
        }
        plugin.messages().send(player, "ex-level-done", "level", s.level, "reward", reward);
        plugin.messages().send(player, "ex-continue", "next", s.level + 1);
    }

    /** Befehl /expedition weiter. */
    private void next(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.cleared) {
            plugin.messages().send(player, "ex-not-cleared");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!pickOverride.containsKey(uuid) && needsPick(uuid, s.level + 1)) {
            new de.coinshop.gui.ExpeditionCopyMenu(plugin, player, de.coinshop.gui.ExpeditionCopyMenu.Mode.PICK, s.level + 1).open(player);
            return;
        }
        if (!pickOverride.containsKey(uuid) && !substituteDone.remove(uuid) && canTakeMore(uuid, s.level + 1)) {
            new de.coinshop.gui.ExpeditionSubstituteMenu(plugin, player, s.level + 1).open(player);
            return;
        }
        if (!plugin.stamina().spend(uuid, Stamina.Mode.EXPEDITION)) {
            plugin.stamina().sendMissing(player, Stamina.Mode.EXPEDITION);
            return;
        }
        s.level++;
        beginLevel(player, s, false);
    }

    private final Set<UUID> substituteDone = new HashSet<>();

    /** Sind weniger Begleiter dabei, als im Level möglich wären, und gibt es noch einsatzbereite Ersatz-Exemplare? */
    public boolean canTakeMore(UUID uuid, int level) {
        return !spareCopies(uuid, level).isEmpty();
    }

    /** Einsatzbereite, nicht gewählte Exemplare, die noch mitkommen dürften (die erfahrensten zuerst). */
    public List<Copy> spareCopies(UUID uuid, int level) {
        List<Copy> out = new ArrayList<>();
        // Gefallene, verletzte oder ruhende Begleiter zählen nicht: ihr Platz wird für Ersatz frei
        List<Copy> ready = takenReady(uuid);
        if (ready.size() >= pickLimit(uuid, level) || ready.size() >= slotsUnlocked(uuid)) {
            return out;
        }
        int used = 0;
        for (Copy r : ready) {
            used += copyCost(uuid, r.companion(), r.index());
        }
        int room = deckLimit(uuid) - used;
        for (Copy k : allCopies(uuid)) {
            if (!taken(uuid, k.companion(), k.index()) && busyReason(uuid, k.companion(), k.index()) == null
                    && copyCost(uuid, k.companion(), k.index()) <= room) {
                out.add(k);
            }
        }
        out.sort((a, b) -> Long.compare(roster().kills(uuid, b.companion(), b.index()), roster().kills(uuid, a.companion(), a.index())));
        return out;
    }

    /** Ersatz-Menü: ein Exemplar zusätzlich mitnehmen (oder keines) und das Level starten. */
    public void substituteChosen(Player player, Copy extra) {
        UUID uuid = player.getUniqueId();
        if (extra != null) {
            roster().setTaken(uuid, extra.companion(), extra.index(), true);
            roster().save(uuid);
        }
        substituteDone.add(uuid);
        player.closeInventory();
        if (isIn(uuid)) {
            next(player);
        } else {
            start(player);
        }
        substituteDone.remove(uuid);
    }

    /** Ersatz-Menü: Verlassen. */
    public void substituteLeave(Player player) {
        player.closeInventory();
        if (isIn(player.getUniqueId())) {
            finish(player, "ex-left");
        }
    }

    private void defeat(Player player, Session s, String reasonKey) {
        if (s.finishing) {
            return;
        }
        s.defeated = true;
        player.setInvulnerable(true);
        plugin.messages().send(player, reasonKey, "level", s.level);
        Bukkit.getScheduler().runTask(plugin, () -> finish(player, "ex-defeat"));
    }

    /** Beendet die Expedition und gibt dem Spieler alles zurück. */
    public void finish(Player player, String messageKey) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        s.finishing = true;
        saveExamProgress(player, s);
        if (s.mine && !s.defeated) {
            flushKills(player, s); // beim Aufsteigen zählen die Kills der Begleiter
        }
        s.killGain.clear(); // Kills zählen nur bei geschafftem Level
        if (s.mine) {
            payoutMine(player, s);
            messageKey = null; // die Abschlussmeldung kommt aus der Auszahlung
        }
        if (s.td) {
            payoutDefense(player, s);
            messageKey = null; // die Abschlussmeldung kommt aus der Auszahlung
            if (s.host == null && !s.guests.isEmpty()) {
                endGuests(s, s.defeated ? "ex-td-all-down" : "ex-td-host-gone");
            } else if (s.host != null) {
                Session hs = sessions.get(s.host);
                if (hs != null && !hs.finishing) {
                    hs.guests.remove(player.getUniqueId());
                }
            }
        }
        removeMobs(s);
        World world = Bukkit.getWorld(worldName());
        if (world != null) {
            clearPlaced(world, s);
        }
        sessions.remove(player.getUniqueId());
        player.setInvulnerable(false);
        restoreState(player);
        if (messageKey != null) {
            plugin.messages().send(player, messageKey, "level", nextLevel(player.getUniqueId()));
        }
    }

    // ------------------------------------------------------------------
    //  Begleiter
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  Tiefenmine
    // ------------------------------------------------------------------

    /** Stand der laufenden Tiefenmine für die Menüs. */
    public record MineInfo(int depth, long coins, int ores, boolean exitOpen, boolean guardian, String zone, String nextZone,
                           boolean zoneChange) { }

    public boolean mineEnabled() {
        return cfgBool("mine.enabled", true);
    }

    public boolean hasMineLicense(Player player) {
        return plugin.licenses().has(player.getUniqueId(), "bergbau");
    }

    private static long mineDay() {
        return java.time.LocalDate.now().toEpochDay();
    }

    public static final int MINE_MAX_RUNS = 10;

    /** Wie oft war der Spieler heute schon in der Mine? */
    public int mineRunsToday(UUID uuid) {
        if (plugin.database().getMetaLong("mine_day_" + uuid, -1) != mineDay()) {
            return 0;
        }
        return (int) Math.max(0, plugin.database().getMetaLong("mine_runs_" + uuid, 1));
    }

    /** Gekaufte Zusatz-Läufe pro Tag (0 bis 9). */
    public int mineExtraRuns(UUID uuid) {
        return (int) Math.max(0, Math.min(MINE_MAX_RUNS - 1, plugin.database().getMetaLong("mine_slots_" + uuid, 0)));
    }

    /** Wie oft darf der Spieler pro Tag in die Mine (1 grundlegend, höchstens 10)? */
    public int mineRunsAllowed(UUID uuid) {
        return 1 + mineExtraRuns(uuid);
    }

    public boolean mineUsedToday(UUID uuid) {
        return mineRunsToday(uuid) >= mineRunsAllowed(uuid) && mineBank(uuid) <= 0;
    }

    /** Höchstzahl gesparter Läufe. */
    public int mineBankMax() {
        return Math.max(0, plugin.getConfig().getInt("expedition.mine.bank-max", 3));
    }

    /**
     * Gesparte Läufe: Läufe, die an einem Tag nicht genutzt wurden, verfallen nicht ganz, sondern wandern in einen
     * Vorrat (höchstens bank-max). Die Abrechnung läuft beim ersten Zugriff eines neuen Tages.
     */
    public int mineBank(UUID uuid) {
        Database db = plugin.database();
        long today = mineDay();
        long settled = db.getMetaLong("mine_settled_" + uuid, 0);
        long bank = Math.max(0, db.getMetaLong("mine_bank_" + uuid, 0));
        if (settled == 0) {
            db.setMetaLong("mine_settled_" + uuid, today);
            return (int) bank;
        }
        if (settled < today) {
            long allowed = mineRunsAllowed(uuid);
            long lastRunDay = db.getMetaLong("mine_day_" + uuid, -1);
            long unused = lastRunDay == settled
                    ? Math.max(0, allowed - Math.max(0, db.getMetaLong("mine_runs_" + uuid, 1)))
                    : allowed;
            unused += (today - settled - 1) * allowed; // ganz verpasste Tage
            bank = Math.min(mineBankMax(), bank + Math.min(unused, 1000));
            db.setMetaLong("mine_bank_" + uuid, bank);
            db.setMetaLong("mine_settled_" + uuid, today);
        }
        return (int) Math.min(bank, mineBankMax());
    }

    /** Preis für den nächsten Zusatz-Lauf (Liste mine.slot-prices, der letzte Wert gilt weiter); -1 = Höchstzahl erreicht. */
    public long mineSlotPrice(UUID uuid) {
        int extra = mineExtraRuns(uuid);
        if (extra >= MINE_MAX_RUNS - 1) {
            return -1;
        }
        List<Long> prices = plugin.getConfig().getLongList("expedition.mine.slot-prices");
        if (prices.isEmpty()) {
            return 5000L * (1L << Math.min(extra, 10));
        }
        return Math.max(1, prices.get(Math.min(extra, prices.size() - 1)));
    }

    public void buyMineSlot(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!hasMineLicense(player) || isIn(uuid)) {
            return;
        }
        long price = mineSlotPrice(uuid);
        if (price < 0) {
            player.sendMessage(Component.text("Du hast schon die höchste Zahl an Läufen (" + MINE_MAX_RUNS + " pro Tag).", NamedTextColor.RED));
            return;
        }
        int extra = mineExtraRuns(uuid);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                plugin.messages().send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.setMetaLong("mine_slots_" + uuid, extra + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Tiefenmine: Slot kaufen fehlgeschlagen: " + e.getMessage());
            plugin.messages().send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -price, "Mine-Lauf pro Tag " + (extra + 2));
        player.sendMessage(Component.text("Gekauft: Du darfst jetzt " + (extra + 2) + " Mal pro Tag in die Tiefenmine.", NamedTextColor.GREEN));
    }

    /** Admin: die Tages-Sperre eines Spielers aufheben. */
    public void mineReset(UUID uuid) {
        plugin.database().setMetaLong("mine_day_" + uuid, -1);
        plugin.database().setMetaLong("mine_runs_" + uuid, 0);
    }

    /** Die tiefste erreichte Ebene. */
    public int mineBest(UUID uuid) {
        return (int) Math.max(0, plugin.database().getMetaLong("mine_best_" + uuid, 0));
    }

    /** Sekunden bis Mitternacht (Serverzeit): dann darf man wieder in die Mine. */
    public long mineResetSeconds() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return Math.max(1, java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).getSeconds());
    }

    public int guardianEvery() {
        return Math.max(2, cfgI("mine.guardian-every", 10));
    }

    public MineInfo mineInfo(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s == null || !s.mine) {
            return null;
        }
        return new MineInfo(s.depth, Math.round(s.mineCoins), s.ores, s.exitOpen, s.guardian, MineZones.zoneOf(s.depth).name(),
                MineZones.zoneOf(s.depth + 1).name(), MineZones.zoneIndex(s.depth + 1) != MineZones.zoneIndex(s.depth));
    }

    /** Ertragsfaktor der Tiefe: auf Ebene 1 kaum etwas, dann immer mehr. */
    public double mineDepthFactor(int depth) {
        double start = Math.max(0.001, cfgD("mine.start-factor", 0.05));
        double growth = Math.max(0, cfgD("mine.depth-growth", 0.15));
        double exp = Math.max(0.5, cfgD("mine.depth-exponent", 1.1));
        return start * Math.pow(1 + Math.max(0, depth - 1) * growth, exp);
    }

    /** Coins für ein Erz in dieser Tiefe mit der Spitzhacke und dem Multiplikator des Spielers (ohne Aspekte der Begleiter). */
    public double mineOreValue(UUID uuid, int depth, Material ore) {
        int pickLevel = level(uuid, Gear.PICKAXE);
        return MineZones.baseValue(ore) * mineDepthFactor(depth)
                * (1 + pickLevel * Math.max(0, cfgD("mine.pickaxe-bonus-percent", 2.5)) / 100.0)
                * multFactor(multStage(uuid)); // der Belohnungs-Multiplikator gilt auch für Erze
    }

    /** Stärke der Gegner auf einer Ebene (wie ein Expeditionslevel, Meeres-Arenen werden übersprungen, weil die Höhle trocken ist). */
    private int mineMobLevel(int depth) {
        int lv = Math.max(1, (int) Math.round(depth * Math.max(0.1, cfgD("mine.mob-level-factor", 2.0))));
        int guard = 0;
        while (guard++ < 20 && lv + LEVELS_PER_ARENA <= MAX_LEVEL
                && themeOf((Math.min(lv, MAX_LEVEL) - 1) / LEVELS_PER_ARENA) == OCEAN) {
            lv += LEVELS_PER_ARENA;
        }
        return Math.min(MAX_LEVEL, lv);
    }

    public boolean canStartMine(Player player, boolean quiet) {
        Messages msg = plugin.messages();
        UUID uuid = player.getUniqueId();
        if (!enabled() || !mineEnabled()) {
            return false;
        }
        if (plugin.timeline().previewFor(player)) {
            if (!quiet) {
                msg.send(player, "ex-preview");
            }
            return false;
        }
        if (!hasMineLicense(player)) {
            if (!quiet) {
                msg.send(player, "mine-locked");
            }
            return false;
        }
        if (isIn(uuid)) {
            if (!quiet) {
                msg.send(player, "ex-already");
            }
            return false;
        }
        if (liveInstance != null && liveInstance.watching(uuid)) {
            return false;
        }
        if (plugin.freecam().isActive(player)) {
            if (!quiet) {
                msg.send(player, "ex-freecam");
            }
            return false;
        }
        if (!plugin.stamina().has(uuid, Stamina.Mode.MINE)) {
            if (!quiet) {
                plugin.stamina().sendMissing(player, Stamina.Mode.MINE);
            }
            return false;
        }
        return true;
    }

    /** Startet die Tiefenmine (vorher wählt man die Begleiter, die mitkommen). */
    public void startMine(Player player) {
        if (!canStartMine(player, false)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cancelPending(uuid);
        if (!pickOverride.containsKey(uuid) && !takenReady(uuid).isEmpty()) {
            pendingMine.add(uuid);
            new de.coinshop.gui.ExpeditionCopyMenu(plugin, player, de.coinshop.gui.ExpeditionCopyMenu.Mode.PICK, 0).open(player);
            return;
        }
        World world = world();
        if (world == null) {
            plugin.messages().send(player, "error");
            return;
        }
        int slot = 0;
        Set<Integer> used = new HashSet<>();
        for (Session other : sessions.values()) {
            used.add(other.slot);
        }
        while (used.contains(slot)) {
            slot++;
        }
        Session s = new Session(uuid, slot, mineMobLevel(1));
        s.mine = true;
        s.depth = 1;
        if (!saveState(player)) {
            plugin.messages().send(player, "error");
            return;
        }
        plugin.stamina().spend(uuid, Stamina.Mode.MINE); // Ausdauer wird beim Start abgezogen
        sessions.put(uuid, s);
        plugin.database().setMetaLong("mine_day_" + uuid, mineDay());
        player.closeInventory();
        beginLevel(player, s, true);
    }

    private void mineAnnounce(Player player, Session s) {
        Database db = plugin.database();
        UUID uuid = s.player;
        if (s.depth > db.getMetaLong("mine_best_" + uuid, 0)) {
            db.setMetaLong("mine_best_" + uuid, s.depth);
            db.setStats(uuid, Map.of("mine_depth", (long) s.depth));
        }
        plugin.messages().send(player, "mine-start", "depth", s.depth, "zone", MineZones.zoneOf(s.depth).name(),
                "count", s.queue.size());
        if (s.guardian) {
            plugin.messages().send(player, "mine-guardian-start", "depth", s.depth,
                    "name", s.bossType == null ? "Wächter" : mobName(s.bossType));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_EMERGE, 0.5f, 1.2f);
        }
    }

    /** Anzahl der Gegner einer Ebene: wächst mit jeder Ebene (Wächter-Ebenen haben mehr). */
    private int mineMobCount(Session s) {
        int count = cfgI("mine.mobs-base", 3) + (int) Math.floor(s.depth * Math.max(0, cfgD("mine.mobs-per-depth", 1.0)));
        if (s.guardian) {
            count = (int) Math.round(count * 1.5);
        }
        return Math.max(1, Math.min(Math.max(1, cfgI("max-mobs", 40)) * (s.guardian ? 2 : 1), count));
    }

    /** Ein Wächter, falls der Plan der Ebene keinen hergibt: die stärkste Kreatur, die zum Budget passt. */
    private EntityType mineGuardianType(Session s) {
        double cap = Math.max(6, budgetOf(s.level) * 0.2);
        MobDef best = null;
        for (MobDef def : pool) {
            if (def.proxy() || def.water()) {
                continue;
            }
            if (def.cost() <= cap && (best == null || def.cost() > best.cost())) {
                best = def;
            }
        }
        return best == null ? EntityType.ZOMBIE : best.type();
    }

    /** Wächter-Ebene: der stärkste Gegner führt an und muss besiegt werden. Auf jeder Ebene wächst die Zahl der Gegner. */
    private void mineQueue(Session s) {
        List<EntityType> types = new ArrayList<>();
        for (EntityType type : s.queue) {
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        s.queue.clear();
        if (types.isEmpty()) {
            types.add(mineGuardianType(s));
        }
        int target = mineMobCount(s);
        for (int i = 0; i < target; i++) {
            s.queue.add(types.get(i % types.size()));
        }
        boolean hasAttacker = false;
        for (EntityType type : s.queue) {
            if (!isSupporter(type)) {
                hasAttacker = true;
                break;
            }
        }
        if (!hasAttacker) {
            s.queue.add(0, mineGuardianType(s)); // jede Ebene hat mindestens einen echten Angreifer
        }
        java.util.Collections.shuffle(s.queue, new Random(s.depth * 31L + 5));
        s.bossType = null;
        if (s.guardian) {
            EntityType best = null;
            int bestCost = -1;
            for (EntityType type : types) {
                MobDef d = defOf(type);
                if (d == null || d.proxy()) {
                    continue;
                }
                if (d.cost() > bestCost) {
                    bestCost = d.cost();
                    best = type;
                }
            }
            if (best == null) {
                best = mineGuardianType(s);
                s.queue.add(0, best);
            }
            s.bossType = best;
            if (s.queue.remove(best)) {
                s.queue.add(0, best);
            }
        }
    }

    /** Fehlt der Wächter (z. B. weil er sich nicht erzeugen ließ), kommt er jetzt. */
    private void mineForceGuardian(Player player, Session s) {
        EntityType type = s.bossType != null ? s.bossType : mineGuardianType(s);
        World w = player.getWorld();
        Location c = center(w, s);
        int r = radiusOf(2);
        Random rnd = new Random();
        LivingEntity living = null;
        spawning = true;
        try {
            for (int attempt = 0; attempt < 4 && living == null; attempt++) {
                double angle = rnd.nextDouble() * Math.PI * 2;
                Location at = c.clone().add(Math.cos(angle) * (r - 3), flyHeight(type), Math.sin(angle) * (r - 3));
                living = spawnOne(player, s, type, at, rnd);
                if (living == null && attempt == 1) {
                    type = EntityType.ZOMBIE;
                }
            }
        } finally {
            spawning = false;
        }
        if (living == null) {
            plugin.getLogger().warning("Tiefenmine: Wächter auf Ebene " + s.depth + " ließ sich nicht erzeugen, der Schacht wird geöffnet.");
            s.bossSpawned = true;
            s.bossId = null;
            return;
        }
        markBoss(s, living, type);
        s.mobs.add(living.getUniqueId());
        plugin.messages().send(player, "mine-guardian-start", "depth", s.depth, "name", mobName(type));
    }

    /** Baut die Höhle der Ebene: Boden, Wände, Dach, Ausgang und Adern aus Gestein mit Erzen. */
    private void buildMine(World world, Session s) {
        MineZones.Zone z = MineZones.zoneOf(s.depth);
        s.guardian = s.depth % guardianEvery() == 0;
        s.exitOpen = false; // der Schacht öffnet erst, wenn alle Gegner besiegt sind (kein Überspringen)
        s.exitMenuAt = 0;
        s.mineBoostUntil = 0;
        int cx = s.slot * SLOT_SPACING;
        int r = radiusOf(2);
        int outer = 22 + 20 + 2;
        for (int x = -outer; x <= outer; x++) {
            for (int zz = -outer; zz <= outer; zz++) {
                for (int y = ARENA_Y - 1; y <= ARENA_Y + ARENA_HEIGHT + 2; y++) {
                    world.getBlockAt(cx + x, y, zz).setType(Material.AIR, false);
                }
            }
        }
        for (int x = -r; x <= r; x++) {
            for (int zz = -r; zz <= r; zz++) {
                boolean lamp = x % 6 == 0 && zz % 6 == 0;
                Material m = lamp ? z.light() : ((x + zz) % 2 == 0 ? z.floor() : z.wall());
                world.getBlockAt(cx + x, ARENA_Y, zz).setType(m, false);
                world.getBlockAt(cx + x, ARENA_Y - 1, zz).setType(z.floor(), false);
                world.getBlockAt(cx + x, ARENA_Y + ARENA_HEIGHT + 1, zz).setType(lamp ? z.light() : z.wall(), false);
            }
        }
        for (int i = -r - 1; i <= r + 1; i++) {
            for (int y = ARENA_Y + 1; y <= ARENA_Y + ARENA_HEIGHT; y++) {
                world.getBlockAt(cx + i, y, -r - 1).setType(z.wall(), false);
                world.getBlockAt(cx + i, y, r + 1).setType(z.wall(), false);
                world.getBlockAt(cx - r - 1, y, i).setType(z.wall(), false);
                world.getBlockAt(cx + r + 1, y, i).setType(z.wall(), false);
            }
            world.getBlockAt(cx + i, ARENA_Y + ARENA_HEIGHT + 1, -r - 1).setType(z.wall(), false);
            world.getBlockAt(cx + i, ARENA_Y + ARENA_HEIGHT + 1, r + 1).setType(z.wall(), false);
            world.getBlockAt(cx - r - 1, ARENA_Y + ARENA_HEIGHT + 1, i).setType(z.wall(), false);
            world.getBlockAt(cx + r + 1, ARENA_Y + ARENA_HEIGHT + 1, i).setType(z.wall(), false);
        }
        s.oreAt.clear();
        s.rock.clear();
        s.placed.clear();
        Random rnd = new Random();
        double angle = rnd.nextDouble() * Math.PI * 2;
        int ex = (int) Math.round(Math.cos(angle) * (r - 5));
        int ez = (int) Math.round(Math.sin(angle) * (r - 5));
        s.exitPad = new Location(world, cx + ex + 0.5, ARENA_Y + 1, ez + 0.5);
        mineSetPad(world, s);
        int veins = Math.max(4, cfgI("mine.veins-base", 14) + Math.min(10, s.depth / 10));
        int orePct = Math.min(70, cfgI("mine.ore-percent", 30) + Math.min(25, s.depth / 4));
        List<int[]> centers = new ArrayList<>();
        for (int tries = 0; tries < 400 && centers.size() < veins; tries++) {
            int vx = rnd.nextInt(2 * (r - 7) + 1) - (r - 7);
            int vz = rnd.nextInt(2 * (r - 7) + 1) - (r - 7);
            if (Math.abs(vx) < 6 && Math.abs(vz) < 6) {
                continue; // Startplatz bleibt frei
            }
            if (Math.abs(vx - ex) < 7 && Math.abs(vz - ez) < 7) {
                continue; // Ausgang bleibt frei
            }
            boolean clash = false;
            for (int[] c : centers) {
                if (Math.abs(c[0] - vx) < 5 && Math.abs(c[1] - vz) < 5) {
                    clash = true; // zwischen den Adern bleibt ein Gang
                    break;
                }
            }
            if (clash) {
                continue;
            }
            centers.add(new int[]{vx, vz});
            int found = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        boolean ore = rnd.nextInt(100) < orePct;
                        Material m = ore ? MineZones.pick(z, rnd) : z.rock();
                        int bx = cx + vx + dx;
                        int by = ARENA_Y + dy;
                        int bz = vz + dz;
                        world.getBlockAt(bx, by, bz).setType(m, false);
                        long k = key(bx, by, bz);
                        if (ore) {
                            s.oreAt.put(k, m);
                            found++;
                        } else {
                            s.rock.add(k);
                        }
                    }
                }
            }
            if (found == 0) { // jede Ader hat mindestens ein Erz
                int bx = cx + vx;
                int by = ARENA_Y + 2;
                Material m = MineZones.pick(z, rnd);
                world.getBlockAt(bx, by, vz).setType(m, false);
                long k = key(bx, by, vz);
                s.rock.remove(k);
                s.oreAt.put(k, m);
            }
        }
        s.builtTier = 2;
    }

    /** Der Ausgang: grün, wenn offen, rot, solange der Wächter lebt. */
    private void mineSetPad(World world, Session s) {
        if (s.exitPad == null) {
            return;
        }
        int px = s.exitPad.getBlockX();
        int pz = s.exitPad.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean center = dx == 0 && dz == 0;
                Material m = s.exitOpen ? (center ? Material.SEA_LANTERN : Material.EMERALD_BLOCK)
                        : (center ? Material.OBSIDIAN : Material.RED_CONCRETE);
                world.getBlockAt(px + dx, ARENA_Y, pz + dz).setType(m, false);
            }
        }
    }

    private void mineTick(Player player, Session s) {
        long now = System.currentTimeMillis();
        long limit = Math.max(1, cfgI("mine.level-minutes", 6)) * 60_000L;
        long left = limit - (now - s.levelStart);
        if (left <= 0) {
            plugin.messages().send(player, "mine-time-over", "depth", s.depth);
            finish(player, null);
            return;
        }
        if (s.coinAt > 0 && now >= s.coinAt) {
            s.coinAt = 0;
            spawnSpecial(player, s, false); // CoinMob wie in Expeditionen (gleiche Chance)
        }
        if (s.mobs.isEmpty() && !s.queue.isEmpty()) {
            spawnWave(player, s);
        }
        boolean bossAlive = false;
        if (s.bossId != null) {
            Entity be = Bukkit.getEntity(s.bossId);
            bossAlive = be != null && be.isValid() && !be.isDead();
        }
        if (s.guardian && !s.exitOpen && !s.bossSpawned && s.queue.isEmpty() && now - s.levelStart > 4000) {
            mineForceGuardian(player, s);
        }
        if (!s.exitOpen && (!s.guardian || (s.bossSpawned && !bossAlive)) && s.queue.isEmpty() && s.mobs.isEmpty()
                && now - s.levelStart > 3000) {
            s.exitOpen = true;
            World w = player.getWorld();
            mineSetPad(w, s);
            if (s.guardian) {
                plugin.messages().send(player, "mine-guardian-down", "depth", s.depth);
            } else {
                player.sendMessage(Component.text("Alle Gegner besiegt: Der Schacht ist offen.", NamedTextColor.GREEN));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        boolean near = false;
        if (s.exitPad != null && s.exitPad.getWorld() != null) {
            World w = s.exitPad.getWorld();
            w.spawnParticle(s.exitOpen ? org.bukkit.Particle.END_ROD : org.bukkit.Particle.FLAME,
                    s.exitPad.clone().add(0, 1.2, 0), 6, 0.6, 0.8, 0.6, 0.01);
            Location pl = player.getLocation();
            near = pl.getWorld() == w && Math.abs(pl.getX() - s.exitPad.getX()) < 1.8 && Math.abs(pl.getZ() - s.exitPad.getZ()) < 1.8
                    && Math.abs(pl.getY() - ARENA_Y) < 4;
        }
        if (near && s.exitOpen && now >= s.exitMenuAt) {
            s.exitMenuAt = now + 8000;
            new de.coinshop.gui.MineExitMenu(plugin, player).open(player);
        }
        long remaining = Math.max(0, left) / 1000;
        String time = String.format("%d:%02d", remaining / 60, remaining % 60);
        String state = s.exitOpen ? "Schacht offen" : s.guardian ? "Wächter!" : "Gegner besiegen";
        player.sendActionBar(plugin.messages().get("mine-actionbar", "depth", s.depth, "coins", Math.round(s.mineCoins),
                "kills", s.mineKills, "kcoins", Math.round(s.mineKillCoins), "ores", Math.round(Math.max(0, s.mineCoins - s.mineKillCoins)),
                "time", time, "left", s.mobs.size() + s.queue.size(), "state", state));
    }

    private void mineOre(Player player, Session s, Material ore) {
        double v = mineOreValue(s.player, s.depth, ore) * (1 + s.aCoins);
        long now = System.currentTimeMillis();
        if (s.mineBoostUntil > now) {
            v *= Math.max(1, cfgD("mine.boost-factor", 3));
        }
        s.mineCoins += v;
        s.ores++;
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
        player.sendActionBar(plugin.messages().get("mine-ore", "ore", MineZones.oreName(ore),
                "value", String.format(java.util.Locale.GERMANY, "%.2f", v), "total", Math.round(s.mineCoins)));
        if (new Random().nextInt(1000) < cfgI("mine.special-per-1000", 15)) {
            mineSpecial(player, s, v);
        }
    }

    /** Sonderfund beim Abbau: Geode (viele Coins), Goldader (kurz mehr Ertrag) oder Fossil (Mystery Box). */
    private void mineSpecial(Player player, Session s, double oreValue) {
        s.specials++;
        int roll = new Random().nextInt(100);
        if (roll < 60) {
            double bonus = oreValue * Math.max(1, cfgD("mine.geode-factor", 25));
            s.mineCoins += bonus;
            plugin.messages().send(player, "mine-special-geode", "coins", Math.round(bonus));
        } else if (roll < 88) {
            int sec = Math.max(5, cfgI("mine.boost-seconds", 60));
            s.mineBoostUntil = System.currentTimeMillis() + sec * 1000L;
            plugin.messages().send(player, "mine-special-boost", "sec", sec, "factor", trimNum(Math.max(1, cfgD("mine.boost-factor", 3))));
        } else {
            int tier = LootRewards.tierOf(plugin, s.depth, 0);
            if (giveBox(player, "fossil", tier, "Tiefenmine: Fossil gefunden")) {
                plugin.messages().send(player, "mine-special-fossil", "tier", tier);
            }
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
    }

    /** Eine Ebene tiefer (nur wenn der Ausgang offen ist). */
    public void mineDescend(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.mine || !s.exitOpen || s.finishing || s.defeated) {
            return;
        }
        player.closeInventory();
        flushKills(player, s);
        removeMobs(s);
        s.depth++;
        s.level = mineMobLevel(s.depth);
        beginLevel(player, s, false);
    }

    /** Aufsteigen: die Beute wird ausgezahlt. */
    public void mineAscend(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.mine) {
            return;
        }
        player.closeInventory();
        finish(player, null);
    }

    private void payoutMine(Player player, Session s) {
        if (s.minePaid) {
            return;
        }
        s.minePaid = true;
        Database db = plugin.database();
        UUID uuid = s.player;
        int keep = s.defeated ? Math.max(0, Math.min(100, cfgI("mine.death-keep-percent", 50))) : 100;
        long coins = Math.round(s.mineCoins * keep / 100.0);
        db.begin();
        try {
            if (coins > 0) {
                db.addCoins(uuid, coins);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Tiefenmine: Auszahlung fehlgeschlagen: " + e.getMessage());
            return;
        }
        if (coins > 0) {
            db.logTx(uuid, player.getName(), "expedition", coins, "Tiefenmine: Ebene " + s.depth + ", " + s.ores + " Erze"
                    + (keep < 100 ? " (gefallen, " + keep + " %)" : ""));
        }
        if (player.isOnline()) {
            plugin.messages().send(player, keep < 100 ? "mine-end-fallen" : "mine-end", "depth", s.depth, "coins", coins,
                    "ores", s.ores, "specials", s.specials, "keep", keep, "best", mineBest(uuid));
        }
    }


    private static final java.util.Set<String> MANUAL_MELEE = java.util.Set.of("GOAT", "PANDA", "RABBIT", "FOX");
    /** Kreaturen, die von sich aus kämpfen, obwohl sie keine Feinde im Spiel sind. */
    private static final java.util.Set<String> AUTO_FIGHT = java.util.Set.of("WOLF", "POLAR_BEAR", "IRON_GOLEM", "BEE",
            "LLAMA", "TRADER_LLAMA", "SNOW_GOLEM");

    /** Schlägt der Begleiter von Hand zu (weil er sonst gar nicht oder nicht zuverlässig kämpft)? */
    private static boolean manualFighter(LivingEntity comp) {
        String name = comp.getType().name();
        return MANUAL_MELEE.contains(name) || !(comp instanceof org.bukkit.entity.Enemy || AUTO_FIGHT.contains(name));
    }
    /** Alle Begleiter-Kreaturen aller laufenden Expeditionen (damit sie sich gegenseitig und ihre Besitzer in Ruhe lassen). */
    private final Set<UUID> companionIds = new HashSet<>();

    /** Wie oft hat der Spieler diese Art gekauft (das bestimmt den Preis des nächsten Exemplars)? */
    public int companionPurchased(UUID uuid, Companion c) {
        return (int) plugin.database().getMetaLong("ex_cn_" + uuid + "_" + c.key, 0);
    }

    /** Alle je erhaltenen Exemplare einer Art (gekauft, gezüchtet, gefusioniert); die Nummern laufen von 0 bis owned-1. */
    public int companionOwned(UUID uuid, Companion c) {
        return companionPurchased(uuid, c) + roster().extra(uuid, c);
    }

    /** Gibt es dieses Exemplar noch (nicht geopfert oder verschmolzen)? */
    public boolean exists(UUID uuid, Companion c, int idx) {
        return idx >= 0 && idx < companionOwned(uuid, c) && !roster().isGone(uuid, c, idx);
    }

    /** Nummern aller noch vorhandenen Exemplare einer Art. */
    public List<Integer> existingCopies(UUID uuid, Companion c) {
        List<Integer> list = new ArrayList<>();
        int owned = companionOwned(uuid, c);
        for (int i = 0; i < owned; i++) {
            if (!roster().isGone(uuid, c, i)) {
                list.add(i);
            }
        }
        return list;
    }

    /** Alle vorhandenen Exemplare des Spielers (nach Art sortiert wie im Laden, dann nach Nummer). */
    public List<Copy> allCopies(UUID uuid) {
        List<Copy> list = new ArrayList<>();
        for (Companion c : Companion.values()) {
            for (int i : existingCopies(uuid, c)) {
                list.add(new Copy(c, i));
            }
        }
        return list;
    }

    private static final CompanionRoster.Info NO_INFO = new CompanionRoster.Info();

    public CompanionRoster.Info info(UUID uuid, Companion c, int idx) {
        CompanionRoster.Info i = roster().info(uuid, c, idx);
        return i == null ? NO_INFO : i;
    }

    public CompanionTraits.Rarity rarity(UUID uuid, Companion c, int idx) {
        return CompanionTraits.Rarity.of(info(uuid, c, idx).rarity);
    }

    public List<CompanionTraits.Trait> traits(UUID uuid, Companion c, int idx) {
        List<CompanionTraits.Trait> list = new ArrayList<>();
        for (String name : info(uuid, c, idx).traits) {
            CompanionTraits.Trait t = CompanionTraits.Trait.byName(name);
            if (t != null) {
                list.add(t);
            }
        }
        return list;
    }

    /** Zeitpunkt des nächsten Shopwechsels (so lange ruhen Begleiter). */
    public long restUntilShopReset() {
        return System.currentTimeMillis() + Math.max(60_000L, plugin.serverShop().timeUntilReset().toMillis());
    }

    /** Variante dieses Exemplars (z. B. Biom-Wolf); ältere Exemplare bekommen eine feste, aus ihrer Nummer abgeleitete. */
    public String variantOf(UUID uuid, Companion c, int idx) {
        CompanionRoster.Info info = roster().info(uuid, c, idx);
        if (info != null && info.variant != null && !info.variant.isEmpty()) {
            return info.variant;
        }
        return CompanionVariants.roll(new Random(Objects.hash(uuid, c.key, idx)), c.type);
    }

    /** Warum ein Exemplar gerade nicht einsatzbereit ist (null = frei). */
    public String busyReason(UUID uuid, Companion c, int idx) {
        return busyReason(uuid, c, idx, false);
    }

    /** Wie busyReason; mit ignoreWork zählt die Arbeit im Großhandel nicht (für die tägliche Lieferung). */
    public String busyReason(UUID uuid, Companion c, int idx, boolean ignoreWork) {
        return busyReason(uuid, c, idx, ignoreWork, false);
    }

    /** Grund, warum ein in einer Arena stationiertes Exemplar dort gerade nicht kämpfen kann (null = einsatzbereit). */
    public String guardUnfit(UUID uuid, Companion c, int idx) {
        return busyReason(uuid, c, idx, false, true);
    }

    private String busyReason(UUID uuid, Companion c, int idx, boolean ignoreWork, boolean ignoreArena) {
        if (plugin.mascot() != null && plugin.mascot().isMascot(uuid, c, idx)) {
            return "ist dein Maskottchen";
        }
        if (roster().away(uuid, c, idx)) {
            return "unterwegs auf Reise";
        }
        String guarding = ignoreArena ? null : arenas().stationedAt(uuid, c, idx);
        if (guarding != null) {
            return "bewacht die Arena " + guarding;
        }
        String training = training().busyText(uuid, c, idx);
        if (training != null) {
            return training;
        }
        String merc = mercs().busyText(uuid, c, idx);
        if (merc != null) {
            return merc;
        }
        if (roster().injured(uuid, c, idx)) {
            return "verletzt";
        }
        if (roster().resting(uuid, c, idx)) {
            return "ruht sich aus, noch " + ItemUtil.formatDuration(roster().restUntil(uuid, c, idx) - System.currentTimeMillis())
                    + " bis zum Shopwechsel";
        }
        if (!ignoreWork && roster().work(uuid, c, idx) != null) {
            return "im Großhandel";
        }
        return null;
    }

    /** Zeilen für Menüs: wer gerade verletzt ist, ruht oder trainiert (mit Restzeit), höchstens max Einträge. */
    public List<String> unavailableLines(UUID uuid, int max) {
        List<String> out = new ArrayList<>();
        int more = 0;
        for (Copy k : allCopies(uuid)) {
            String why = null;
            String training = training().busyText(uuid, k.companion(), k.index());
            if (training != null) {
                why = training;
            } else if (roster().injured(uuid, k.companion(), k.index())) {
                why = "verletzt";
            } else if (roster().resting(uuid, k.companion(), k.index())) {
                why = "ruht sich aus, noch " + ItemUtil.formatDuration(roster().restUntil(uuid, k.companion(), k.index())
                        - System.currentTimeMillis());
            }
            if (why == null) {
                continue;
            }
            if (out.size() < max) {
                out.add(trips().copyName(k.companion(), k.index()) + ": " + why);
            } else {
                more++;
            }
        }
        if (more > 0) {
            out.add("... und " + more + " weitere");
        }
        return out;
    }

    /** Ist das Exemplar verletzt oder ruht es sich aus (und könnte sofort wieder fit gemacht werden)? */
    public boolean canHealNow(UUID uuid, Companion c, int idx) {
        return roster().injured(uuid, c, idx) || roster().resting(uuid, c, idx);
    }

    /** Preis für "sofort wieder fit": Grundpreis plus Level, Ruhe ist günstiger als Verletzung. */
    public long healNowPrice(UUID uuid, Companion c, int idx) {
        long base = Math.max(1, plugin.getConfig().getLong("expedition.heal-now.base-price", 300));
        long perLevel = Math.max(0, plugin.getConfig().getLong("expedition.heal-now.per-level", 50));
        long price = base + perLevel * Math.max(1, copyLevel(uuid, c, idx));
        if (!roster().injured(uuid, c, idx)) {
            price = price * Math.max(1, Math.min(100, plugin.getConfig().getInt("expedition.heal-now.rest-percent", 50))) / 100;
        }
        return Math.max(1, price);
    }

    /** Macht ein verletztes oder ausruhendes Exemplar sofort wieder einsatzbereit (kostet Coins, geht an den Shop). */
    public void healNow(Player player, Companion c, int idx) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        if (!canHealNow(uuid, c, idx)) {
            return;
        }
        if (roster().away(uuid, c, idx)) {
            msg.send(player, "ex-copy-busy", "name", c.label + " #" + (idx + 1), "why", "unterwegs auf Reise");
            return;
        }
        long price = healNowPrice(uuid, c, idx);
        Database db = plugin.database();
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            roster().setInjured(uuid, c, idx, 0L);
            roster().setRest(uuid, c, idx, 0L);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Begleiter heilen fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition-heilen", -price, c.label + " #" + (idx + 1));
        msg.send(player, "ex-healnow-done", "name", c.label + " #" + (idx + 1), "price", price);
    }

    /** Frühere Auswahl (Anzahl je Art) wird einmalig auf einzelne Exemplare übertragen. */
    private void ensureTakenInit(UUID uuid) {
        if (roster().takenInit(uuid)) {
            return;
        }
        for (Companion c : Companion.values()) {
            long n = plugin.database().getMetaLong("ex_ct_" + uuid + "_" + c.key, 0);
            int done = 0;
            int owned = companionOwned(uuid, c);
            for (int i = 0; i < owned && done < n; i++) {
                if (!roster().isGone(uuid, c, i)) {
                    roster().setTaken(uuid, c, i, true);
                    done++;
                }
            }
        }
        roster().setTakenInit(uuid);
        roster().save(uuid);
    }

    /** Ist dieses Exemplar fürs nächste Level gewählt? */
    public boolean taken(UUID uuid, Companion c, int idx) {
        ensureTakenInit(uuid);
        return exists(uuid, c, idx) && roster().isTaken(uuid, c, idx);
    }

    /** Alle gewählten Exemplare (auch die gerade nicht einsatzbereiten). */
    public List<Copy> takenAll(UUID uuid) {
        ensureTakenInit(uuid);
        List<Copy> list = new ArrayList<>();
        for (Copy k : allCopies(uuid)) {
            if (roster().isTaken(uuid, k.companion(), k.index())) {
                list.add(k);
            }
        }
        return list;
    }

    /** Gewählte und einsatzbereite Exemplare einer Art. */
    public List<Integer> takenCopies(UUID uuid, Companion c) {
        List<Integer> list = new ArrayList<>();
        for (int i : existingCopies(uuid, c)) {
            if (taken(uuid, c, i) && busyReason(uuid, c, i) == null) {
                list.add(i);
            }
        }
        return list;
    }

    /** Gewählte und einsatzbereite Exemplare aller Arten. */
    public List<Copy> takenReady(UUID uuid) {
        List<Copy> list = new ArrayList<>();
        for (Copy k : takenAll(uuid)) {
            if (busyReason(uuid, k.companion(), k.index()) == null) {
                list.add(k);
            }
        }
        return list;
    }

    /** Wie viele Exemplare dieser Art sind im Moment verfügbar (nicht unterwegs, verletzt, ruhend, im Großhandel)? */
    public int companionAvailable(UUID uuid, Companion c) {
        return availableCopies(uuid, c).size();
    }

    private CompanionRoster rosterInstance;

    public CompanionRoster roster() {
        if (rosterInstance == null) {
            rosterInstance = new CompanionRoster(plugin);
        }
        return rosterInstance;
    }

    /** Verfügbare Exemplare (frei, nicht unterwegs, verletzt, ruhend oder im Großhandel): die stärksten zuerst. */
    public List<Integer> availableCopies(UUID uuid, Companion c) {
        List<Integer> list = new ArrayList<>();
        for (int i : existingCopies(uuid, c)) {
            if (busyReason(uuid, c, i) == null) {
                list.add(i);
            }
        }
        list.sort((x, y) -> {
            int cmp = Long.compare(roster().kills(uuid, c, y), roster().kills(uuid, c, x));
            return cmp != 0 ? cmp : Integer.compare(x, y);
        });
        return list;
    }

    private CompanionLife lifeInstance;

    public CompanionLife life() {
        if (lifeInstance == null) {
            lifeInstance = new CompanionLife(plugin, this);
        }
        return lifeInstance;
    }

    private NpcDuels npcInstance;
    private ArenaManager arenaInstance;

    /** Duelle gegen NPCs. */
    public NpcDuels npc() {
        if (npcInstance == null) {
            npcInstance = new NpcDuels(plugin, this);
        }
        return npcInstance;
    }

    /** Untergrund-Arenen der Reviere. */
    public ArenaManager arenas() {
        if (arenaInstance == null) {
            arenaInstance = new ArenaManager(plugin, this);
        }
        return arenaInstance;
    }

    private TrainingManager trainingInstance;
    private MercenaryManager mercInstance;

    /** Training der Begleiter (Trainingsplätze). */
    public TrainingManager training() {
        if (trainingInstance == null) {
            trainingInstance = new TrainingManager(plugin, this);
        }
        return trainingInstance;
    }

    /** Söldner: Begleiter mieten und vermieten. */
    public MercenaryManager mercs() {
        if (mercInstance == null) {
            mercInstance = new MercenaryManager(plugin, this);
        }
        return mercInstance;
    }

    private LiveFight liveInstance;

    /** Die Live-Ansicht für Duelle und Arenen. */
    public LiveFight live() {
        if (liveInstance == null) {
            liveInstance = new LiveFight(plugin, this);
        }
        return liveInstance;
    }

    private CompanionDuel duelInstance;

    public CompanionDuel duel() {
        if (duelInstance == null) {
            duelInstance = new CompanionDuel(plugin, this);
        }
        return duelInstance;
    }

    private CompanionTrips tripsInstance;

    public CompanionTrips trips() {
        if (tripsInstance == null) {
            tripsInstance = new CompanionTrips(plugin, this);
        }
        return tripsInstance;
    }

    /** So viele Kreaturen hat das Level (so viele Begleiter dürfen höchstens mit). */
    public int creaturesIn(int level) {
        return plan(Math.max(1, level)).total();
    }

    // ------------------------------------------------------------------
    //  Begleiter-Level: jedes Exemplar sammelt seine eigenen Kills (nur wenn das Level geschafft wird)
    // ------------------------------------------------------------------

    public long copyKills(UUID uuid, Companion c, int idx) {
        return roster().kills(uuid, c, idx);
    }

    public int companionMaxLevel() {
        return Math.max(1, cfgI("companion-max-level", 50));
    }

    /** Kills, die insgesamt für ein Level nötig sind (Level 2: 20, Level 3: 60, Level 4: 120 ...). */
    public long killsForLevel(int level) {
        return 10L * level * (level - 1);
    }

    public int levelForKills(long kills) {
        int level = (int) Math.floor((1 + Math.sqrt(1 + 0.4 * Math.max(0, kills))) / 2);
        level = Math.max(1, Math.min(companionMaxLevel(), level));
        while (level < companionMaxLevel() && kills >= killsForLevel(level + 1)) {
            level++;
        }
        while (level > 1 && kills < killsForLevel(level)) {
            level--;
        }
        return level;
    }

    public int copyLevel(UUID uuid, Companion c, int idx) {
        return levelForKills(copyKills(uuid, c, idx));
    }

    /**
     * Menge Leben einer Heilung eines Unterstützers: Alle fangen mit 4 Leben (zwei Herzen) an, danach wachsen sie mit
     * Level, Seltenheit und den Eigenschaften des Exemplars.
     */
    public double healAmount(UUID owner, Companion c, int idx) {
        double traits = 1;
        for (CompanionTraits.Trait t : traits(owner, c, idx)) {
            traits += t.hp;
        }
        return Math.max(1.0, cfgD("support.base-heal", 4.0)) * levelBonus(copyLevel(owner, c, idx)) * rarity(owner, c, idx).stats
                * Math.max(0.5, traits);
    }

    /** Faktor auf Leben und Schaden. */
    public double levelBonus(int level) {
        return 1.0 + Math.max(0, cfgD("companion-level-bonus", 0.08)) * (Math.max(1, level) - 1);
    }

    /** Ein einzelnes Begleiter-Exemplar (Art und Nummer ab 0). */
    public record Copy(Companion companion, int index) { }

    /** Welches Exemplar ist diese Kreatur (nur laufende Begleiter)? */
    private final Map<UUID, Copy> companionKinds = new HashMap<>();
    /** Wem gehört der laufende Begleiter (wichtig, wenn mehrere Spieler gemeinsam kämpfen). */
    private final Map<UUID, UUID> companionOwner = new HashMap<>();
    /** Söldner: Kreatur -> Besitzer des Exemplars (liefert Level und Werte). */
    private final Map<UUID, UUID> mercLender = new HashMap<>();

    /** Wessen Daten gelten für diese Begleiter-Kreatur? Normal der Spieler der Sitzung, bei Söldnern der Besitzer. */
    private UUID dataOwner(Session s, UUID entityId) {
        return mercLender.getOrDefault(entityId, s.player);
    }

    /** Verteidiger leveln für jeden Gegner, der fällt, während sie noch stehen (zählt bei geschafftem Level). */
    private void creditDefenders(Session s) {
        for (UUID cid : new ArrayList<>(s.companions)) {
            Copy copy = companionKinds.get(cid);
            if (copy == null || mercLender.containsKey(cid) || !isDefender(copy.companion())) {
                continue;
            }
            if (!(Bukkit.getEntity(cid) instanceof LivingEntity alive) || !alive.isValid() || alive.isDead()) {
                continue;
            }
            UUID ownerId = companionOwner.get(cid);
            Session os = ownerId == null ? s : sessions.getOrDefault(ownerId, s);
            os.killGain.merge(copy, 1, Integer::sum);
        }
    }

    /** Setzt ein Begleiter seine Fähigkeit ein, zählt das wie Kills für sein Level (gutgeschrieben bei geschafftem Level). */
    private void creditPowerUse(UUID entityId) {
        Copy copy = companionKinds.get(entityId);
        if (copy == null || mercLender.containsKey(entityId)) {
            return; // Gegner und Söldner bringen keine Erfahrung
        }
        int xp = Math.max(0, cfgI("power.xp-per-use", 2));
        UUID ownerId = companionOwner.get(entityId);
        Session os = ownerId == null ? null : sessions.get(ownerId);
        if (xp > 0 && os != null) {
            os.killGain.merge(copy, xp, Integer::sum);
        }
    }

    private void creditKill(Session s, LivingEntity dead) {
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent hit)) {
            return;
        }
        Entity damager = sourceOf(hit.getDamager());
        Copy copy = companionKinds.get(damager.getUniqueId());
        if (copy != null && mercLender.containsKey(damager.getUniqueId())) {
            return; // Söldner bringen dem Mieter keine Erfahrung (der Besitzer bekommt sie pro geschafftem Level)
        }
        if (copy != null && !isSupporter(copy.companion()) && !isDefender(copy.companion())) { // Unterstützer leveln durchs Heilen, Verteidiger durchs Überleben
            UUID ownerId = companionOwner.get(damager.getUniqueId());
            Session os = ownerId == null ? s : sessions.getOrDefault(ownerId, s);
            os.killGain.merge(copy, 1, Integer::sum);
        }
    }

    /** Nur bei geschafftem Level: die Kills werden den Exemplaren gutgeschrieben. */
    private void flushKills(Player player, Session s) {
        if (s.killGain.isEmpty()) {
            return;
        }
        UUID uuid = s.player;
        for (Map.Entry<Copy, Integer> e : new ArrayList<>(s.killGain.entrySet())) {
            Companion c = e.getKey().companion();
            int idx = e.getKey().index();
            int levelBefore = copyLevel(uuid, c, idx);
            roster().addKills(uuid, c, idx, e.getValue());
            int levelAfter = copyLevel(uuid, c, idx);
            if (levelAfter > levelBefore && player != null && player.isOnline()) {
                plugin.messages().send(player, "ex-comp-levelup", "name", trips().copyName(c, idx), "level", levelAfter);
            }
        }
        s.killGain.clear();
        roster().save(uuid);
    }

    // ------------------------------------------------------------------
    //  Abwahl-Menü, wenn mehr Begleiter gewählt sind als Kreaturen im Level
    // ------------------------------------------------------------------

    private final Map<UUID, Set<Copy>> pickOverride = new HashMap<>();

    /** Sind mehr Begleiter einsatzbereit gewählt, als im Level Kreaturen sind? */
    public boolean needsPick(UUID uuid, int level) {
        return takenReady(uuid).size() > creaturesIn(level);
    }

    /** So viele Begleiter dürfen ins Level: freigekaufte Plätze, aber nie mehr als Kreaturen im Level. */
    public int pickLimit(UUID uuid, int level) {
        if (level <= 0) {
            return Math.max(0, slotsUnlocked(uuid)); // Verteidigung: so viele wie Plätze
        }
        return Math.max(0, Math.min(slotsUnlocked(uuid), creaturesIn(level)));
    }

    /** Das Abwahl-Menü hat eine Auswahl bestätigt: Level starten (oder mit "weiter" fortfahren). */
    public void confirmPick(Player player, Set<Copy> chosen) {
        UUID uuid = player.getUniqueId();
        pickOverride.put(uuid, new HashSet<>(chosen));
        player.closeInventory();
        try {
            UUID joinHost = pendingJoin.remove(uuid);
            if (joinHost != null) {
                pendingDefense.remove(uuid);
                lobbyReady(player, joinHost, new HashSet<>(chosen));
            } else if (pendingMine.remove(uuid)) {
                startMine(player);
            } else if (pendingDefense.remove(uuid)) {
                if (invitable(player).isEmpty()) {
                    startDefense(player); // niemand zum Einladen online: gleich los
                } else {
                    openLobby(player, chosen);
                }
            } else if (isIn(uuid)) {
                next(player);
            } else {
                start(player);
            }
        } finally {
            pickOverride.remove(uuid);
        }
    }

    /** Wer gerade das Begleiter-Menü vor einer Verteidigung offen hat (eigener Start bzw. Beitritt zu einem Gastgeber). */
    private final Set<UUID> pendingDefense = new HashSet<>();
    private final Set<UUID> pendingMine = new HashSet<>();
    private final Map<UUID, UUID> pendingJoin = new HashMap<>();

    /** Das Menü wurde ohne Start geschlossen oder verlassen. */
    public void cancelPending(UUID uuid) {
        pendingMine.remove(uuid);
        pendingDefense.remove(uuid);
        pendingJoin.remove(uuid);
    }

    /** Preis des nächsten gekauften Exemplars: Grundpreis, danach jedes Mal das Doppelte (Zucht und Fusion zählen nicht mit). */
    public long companionPrice(UUID uuid, Companion c) {
        double base = Math.max(1, plugin.getConfig().getLong("expedition.companion." + c.key, c.defaultPrice))
                * Math.max(0.01, cfgD("companion-price-factor", 1.0));
        double growth = Math.max(1.0, cfgD("companion-copy-growth", 2.0));
        int owned = companionPurchased(uuid, c);
        double price = base * Math.pow(growth, Math.min(owned, 200));
        return Math.max(1, (long) Math.min(price, Long.MAX_VALUE / 4.0));
    }

    public int slotsUnlocked(UUID uuid) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, plugin.database().getMetaLong("ex_cs_" + uuid, 0)));
    }

    public long slotPrice(UUID uuid) {
        double base = Math.max(1, plugin.getConfig().getLong("expedition.companion-slot-price", 5000));
        double growth = Math.max(1.0, cfgD("companion-slot-growth", 1.5));
        int have = slotsUnlocked(uuid);
        double price = base * Math.pow(growth, Math.min(have, 200));
        return (long) Math.min(price, Long.MAX_VALUE / 4.0);
    }

    /** Wie viele Exemplare sind fürs Level gewählt? */
    public int slotsUsed(UUID uuid) {
        return takenAll(uuid).size();
    }

    // ------------------------------------------------------------------
    //  Gefolge-Limit: jedes Exemplar kostet Sterne, das Gefolge hat eine Obergrenze
    // ------------------------------------------------------------------

    /** Gefolge-Kosten eines Exemplars: Sterne der Art plus Zuschlag der Seltenheit. */
    public int copyCost(UUID uuid, Companion c, int idx) {
        return c.stars + rarity(uuid, c, idx).stars;
    }

    public int deckCost(UUID uuid) {
        int sum = 0;
        for (Copy k : takenAll(uuid)) {
            sum += copyCost(uuid, k.companion(), k.index());
        }
        return sum;
    }

    public int deckLimit(UUID uuid) {
        return Math.max(1, cfgI("deck.base", 16) + cfgI("deck.per-slot", 6) * slotsUnlocked(uuid));
    }

    /** So viele Begleiter dürfen höchstens mit: freigekaufte Plätze, aber nie mehr als Kreaturen im nächsten Level. */
    public int companionLimit(UUID uuid) {
        return Math.max(0, Math.min(slotsUnlocked(uuid), plan(nextLevel(uuid)).total()));
    }

    /** Wie viele Begleiter kommen tatsächlich mit? */
    public int companionsTaken(UUID uuid) {
        return Math.min(takenReady(uuid).size(), companionLimit(uuid));
    }

    /** Wählt ein Exemplar fürs Level ab oder an (Plätze und Gefolge-Limit werden geprüft). */
    public boolean toggleTake(Player player, Companion c, int idx) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        if (isIn(uuid) || !exists(uuid, c, idx)) {
            return false;
        }
        if (taken(uuid, c, idx)) {
            roster().setTaken(uuid, c, idx, false);
            roster().save(uuid);
            return true;
        }
        String busy = busyReason(uuid, c, idx);
        if (busy != null) {
            msg.send(player, "ex-copy-busy", "name", trips().copyName(c, idx), "why", busy);
            return false;
        }
        if (slotsUsed(uuid) >= slotsUnlocked(uuid)) {
            msg.send(player, "ex-comp-max", "max", slotsUnlocked(uuid));
            return false;
        }
        if (deckCost(uuid) + copyCost(uuid, c, idx) > deckLimit(uuid)) {
            msg.send(player, "ex-deck-full", "cost", copyCost(uuid, c, idx), "used", deckCost(uuid), "max", deckLimit(uuid));
            return false;
        }
        roster().setTaken(uuid, c, idx, true);
        roster().save(uuid);
        return true;
    }

    /** Wählt so viele einsatzbereite Exemplare an, wie Plätze und Gefolge-Limit erlauben (die stärksten zuerst). */
    public void takeAllPossible(Player player) {
        UUID uuid = player.getUniqueId();
        if (isIn(uuid)) {
            return;
        }
        List<Copy> candidates = new ArrayList<>();
        for (Copy k : allCopies(uuid)) {
            if (!taken(uuid, k.companion(), k.index()) && busyReason(uuid, k.companion(), k.index()) == null) {
                candidates.add(k);
            }
        }
        candidates.sort((a, b) -> Long.compare(roster().kills(uuid, b.companion(), b.index()), roster().kills(uuid, a.companion(), a.index())));
        for (Copy k : candidates) {
            if (slotsUsed(uuid) >= slotsUnlocked(uuid)) {
                break;
            }
            if (deckCost(uuid) + copyCost(uuid, k.companion(), k.index()) <= deckLimit(uuid)) {
                roster().setTaken(uuid, k.companion(), k.index(), true);
            }
        }
        roster().save(uuid);
    }

    public void clearTaken(Player player) {
        UUID uuid = player.getUniqueId();
        if (isIn(uuid)) {
            return;
        }
        for (Copy k : takenAll(uuid)) {
            roster().setTaken(uuid, k.companion(), k.index(), false);
        }
        roster().save(uuid);
    }

    // ------------------------------------------------------------------
    //  Kaufen (jedes Exemplar bekommt Seltenheit und Eigenschaften), Opfer für die ganz Großen
    // ------------------------------------------------------------------

    /** Muss man für diese Art ein anderes Exemplar opfern (als Opfer)? */
    public boolean needsTribute(Companion c) {
        return tributesNeeded(c) > 0;
    }

    /** Wie viele Exemplare müssen geopfert werden? Ab 5 Sternen eines, ab 7 Sternen zwei. */
    public int tributesNeeded(Companion c) {
        if (c.stars >= cfgI("tribute.two-from-stars", 7)) {
            return 2;
        }
        return c.stars >= cfgI("tribute.min-stars", 5) ? 1 : 0;
    }

    /** Darf dieses Exemplar als Opfer für die Art c geopfert werden? */
    public boolean tributeFits(UUID uuid, Companion c, Copy tribute) {
        if (tribute == null || !exists(uuid, tribute.companion(), tribute.index())) {
            return false;
        }
        if (tribute.companion() == c || busyReason(uuid, tribute.companion(), tribute.index()) != null) {
            return false;
        }
        return tribute.companion().stars >= cfgI("tribute.min-tribute-stars", 3)
                && tribute.companion().stars < c.stars;
    }

    public void buyCompanion(Player player, Companion c, Copy tribute) {
        buyCompanion(player, c, tribute == null ? List.<Copy>of() : List.of(tribute));
    }

    public void buyCompanion(Player player, Companion c, List<Copy> tributes) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!hasLicense(player) || isIn(uuid)) {
            return;
        }
        int need = tributesNeeded(c);
        boolean tributeNeeded = need > 0;
        if (tributeNeeded) {
            Set<Copy> distinct = new HashSet<>(tributes);
            boolean ok = distinct.size() == need && tributes.size() == need;
            for (Copy k : distinct) {
                ok = ok && tributeFits(uuid, c, k);
            }
            if (!ok) {
                msg.send(player, "ex-tribute-needed", "stars", cfgI("tribute.min-stars", 5), "count", need,
                        "min", cfgI("tribute.min-tribute-stars", 3));
                return;
            }
        }
        int purchased = companionPurchased(uuid, c);
        int idx = companionOwned(uuid, c);
        long price = companionPrice(uuid, c);
        Random random = new Random();
        CompanionTraits.Rarity rarity = CompanionTraits.rollRarity(random, plugin.getConfig().getIntegerList("expedition.rarity-weights"));
        List<CompanionTraits.Trait> traits = CompanionTraits.rollTraits(random, rarity);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.setMetaLong("ex_cn_" + uuid + "_" + c.key, purchased + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Begleiter kaufen fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        for (Copy gone : tributes) {
            if (tributeNeeded) {
                roster().markGone(uuid, gone.companion(), gone.index());
            }
        }
        CompanionRoster.Info info = new CompanionRoster.Info();
        info.rarity = rarity.ordinal();
        info.variant = CompanionVariants.roll(random, c.type); // zufällige Variante (z. B. Biom des Wolfs)
        for (CompanionTraits.Trait t : traits) {
            info.traits.add(t.name());
        }
        roster().setInfo(uuid, c, idx, info);
        boolean auto = slotsUsed(uuid) < slotsUnlocked(uuid) && deckCost(uuid) + copyCost(uuid, c, idx) <= deckLimit(uuid);
        if (auto) {
            roster().setTaken(uuid, c, idx, true);
        }
        roster().save(uuid);
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -price, "Begleiter " + c.label + " Nr. " + (idx + 1)
                + " (" + rarity.label + ")");
        msg.send(player, "ex-comp-bought", "name", c.label, "price", price, "count", idx + 1);
        msg.send(player, "ex-comp-rolled", "name", trips().copyName(c, idx), "rarity", rarity.label,
                "traits", traitText(traits));
        for (Copy gone : tributes) {
            if (tributeNeeded) {
                msg.send(player, "ex-tribute-paid", "name", trips().copyName(gone.companion(), gone.index()));
            }
        }
        if (rarity.ordinal() >= CompanionTraits.Rarity.EPISCH.ordinal()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
        }
    }

    public static String traitText(List<CompanionTraits.Trait> traits) {
        if (traits.isEmpty()) {
            return "keine";
        }
        StringBuilder sb = new StringBuilder();
        for (CompanionTraits.Trait t : traits) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(t.label);
        }
        return sb.toString();
    }

    /** Kauft den nächsten Begleiter-Platz. */
    public void buySlot(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!hasLicense(player) || isIn(uuid)) {
            return;
        }
        int have = slotsUnlocked(uuid);
        long price = slotPrice(uuid);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.setMetaLong("ex_cs_" + uuid, have + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Begleiter-Platz kaufen fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -price, "Begleiter-Platz " + (have + 1));
        msg.send(player, "ex-comp-slot-bought", "slot", have + 1, "price", price);
    }

    // ------------------------------------------------------------------
    //  Verteidigung (Tower Defense)
    // ------------------------------------------------------------------

    /** Arena-Stufe: im Verteidigungsmodus immer dieselbe Arena, sonst die des Levels. */
    private int arenaTier(Session s) {
        if (s.mine) {
            return 2; // Tiefenmine: feste Größe der Höhle (24 Blöcke), kein Biom
        }
        return s.td ? Math.max(0, cfgI("defense.arena-tier", 3)) : plan(s.level).tier();
    }

    public boolean defenseEnabled() {
        return plugin.getConfig().getBoolean("expedition.defense.enabled", true);
    }

    private static final Set<String> RANGED = Set.of("SKELETON", "STRAY", "BOGGED", "BLAZE", "GHAST", "HAPPY_GHAST", "PILLAGER",
            "WITCH", "EVOKER", "BREEZE", "SHULKER", "GUARDIAN", "ELDER_GUARDIAN", "WITHER", "LLAMA", "TRADER_LLAMA");

    /** Stellt Kern, Posten und Kommandostab auf. */
    /** Kommandostab auf den letzten freien Platz der Schnellleiste (ist alles voll: Platz 9, das alte Item geht in den Rucksack). */
    private void giveCommandRod(Player player) {
        ItemStack rod = new ItemStack(Material.BLAZE_ROD);
        rod.editMeta(m -> m.customName(Component.text("Kommandostab", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        PlayerInventory inv = player.getInventory();
        boolean placed = false;
        for (int slot = 8; slot >= 0 && !placed; slot--) {
            ItemStack at = inv.getItem(slot);
            if (at == null || at.getType().isAir()) {
                inv.setItem(slot, rod);
                placed = true;
            }
        }
        for (int slot = 9; slot < 36 && !placed; slot++) {
            ItemStack at = inv.getItem(slot);
            if (at == null || at.getType().isAir()) {
                // Schnellleiste voll: das letzte Feld wandert in den Rucksack, der Stab kommt auf Platz 9
                ItemStack old = inv.getItem(8);
                inv.setItem(slot, old);
                inv.setItem(8, rod);
                placed = true;
            }
        }
        if (!placed) {
            inv.setItem(8, rod);
        }
    }

    /** Markiert den Bereich um den Kern, in dem nicht gebaut werden darf (roter Boden, roter Rand). */
    private void markBuildFreeZone(World world, Session s) {
        if (s.core == null) {
            return;
        }
        double free = Math.max(2, cfgD("defense.free-radius", 4));
        int cx = s.core.getBlockX();
        int cz = s.core.getBlockZ();
        int fy = s.core.getBlockY() - 1;
        int reach = (int) Math.ceil(free);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.sqrt(Math.pow(dx, 2) + Math.pow(dz, 2));
                if (d < free) {
                    boolean edge = d >= free - 1;
                    world.getBlockAt(cx + dx, fy, cz + dz).setType(edge ? Material.RED_CONCRETE : Material.RED_TERRACOTTA, false);
                }
            }
        }
    }

    private void setupDefense(Player player, Session s) {
        World world = player.getWorld();
        Location c = center(world, s);
        s.core = c.clone();
        s.coreMax = Math.max(50, cfgD("defense.core-hp", 500));
        s.coreHp = s.coreMax;
        s.coreHitAt = 0;
        s.selected = null;
        s.posts.clear();
        // Freiraum um den Kern: Luft über dem Boden, Kern selbst als Block
        int cx = s.core.getBlockX();
        int cz = s.core.getBlockZ();
        int y = s.core.getBlockY();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    world.getBlockAt(cx + dx, y + dy, cz + dz).setType(Material.AIR, false);
                }
            }
        }
        world.getBlockAt(cx, y, cz).setType(Material.valueOf(plugin.getConfig().getString("expedition.defense.core-block", "BEACON")), false);
        // Begleiter auf einem Ring um den Kern: das sind ihre Posten
        int n = s.companions.size();
        double ring = Math.max(5, cfgD("defense.free-radius", 4) + 2);
        int i = 0;
        for (UUID id : new ArrayList<>(s.companions)) {
            Entity e = Bukkit.getEntity(id);
            if (e == null) {
                continue;
            }
            double angle = (Math.PI * 2 * i) / Math.max(1, n);
            Location at = new Location(world, cx + 0.5 + Math.cos(angle) * ring, y, cz + 0.5 + Math.sin(angle) * ring);
            e.teleport(at);
            s.posts.put(id, at);
            i++;
        }
        internalTeleport = true;
        try {
            player.teleport(new Location(world, cx + 0.5, y, cz + 0.5 + 2.5));
        } finally {
            internalTeleport = false;
        }
        markBuildFreeZone(world, s);
        giveCommandRod(player);
        s.prepUntil = System.currentTimeMillis() + Math.max(0, cfgI("defense.prep-seconds", 60)) * 1000L;
        s.waveStart = System.currentTimeMillis();
        plugin.messages().send(player, "ex-td-help");
        plugin.messages().send(player, "ex-td-prep", "sec", Math.max(0, cfgI("defense.prep-seconds", 60)));
    }

    /** Läuft jede Sekunde im Verteidigungsmodus: Gegner laufen zum Kern und greifen ihn an. */
    private void defenseTick(Player player, Session s) {
        if (s.core == null) {
            return;
        }
        eventTick(player, s);
        double factor = Math.max(0, cfgD("defense.core-damage-factor", 0.5));
        boolean hit = false;
        for (UUID id : new ArrayList<>(s.mobs)) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof LivingEntity le) || !le.isValid()) {
                continue;
            }
            double dist = le.getLocation().distance(s.core);
            boolean ranged = RANGED.contains(le.getType().name());
            double reach = ranged ? 14 : 3.2;
            if (le instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isValid()) && dist > reach - 0.5) {
                try {
                    mob.getPathfinder().moveTo(s.core, 1.0);
                } catch (RuntimeException ignored) {
                    // kann nicht laufen
                }
            }
            if (dist <= reach) {
                AttributeInstance atk = le.getAttribute(Attribute.ATTACK_DAMAGE);
                double dmg = atk != null ? atk.getValue() : (le instanceof org.bukkit.entity.Enemy ? 2.0 : 0.0);
                if (dmg > 0) {
                    s.coreHp -= dmg * factor;
                    hit = true;
                }
            }
        }
        if (hit) {
            s.core.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, s.core.clone().add(0, 1.2, 0), 12, 0.3, 0.3, 0.3, 0.01);
            if (System.currentTimeMillis() - s.coreHitAt > 4000) {
                s.coreHitAt = System.currentTimeMillis();
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
                plugin.messages().send(player, "ex-td-core-hit", "hp", Math.max(0, Math.round(s.coreHp)), "max", Math.round(s.coreMax));
            }
        }
        if (s.coreHp <= 0) {
            s.coreHp = 0;
            defeat(player, s, "ex-td-core-lost");
            return;
        }
        // Wer aus der Arena gefallen ist, kommt zurück (damit keine Gegner verloren gehen)
        for (UUID id : new ArrayList<>(s.mobs)) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && le.getLocation().getY() < ARENA_Y - 2) {
                le.teleport(ringAroundCore(s, 7));
                le.setFallDistance(0);
            }
        }
        wavesTick(player, s);
    }

    private Location ringAroundCore(Session s, double dist) {
        double angle = new Random().nextDouble() * Math.PI * 2;
        return s.core.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
    }

    /** Ablauf der Wellen: Gegner nachlegen, Welle beenden, Pause, nächste Welle. */
    private void wavesTick(Player player, Session s) {
        long now = System.currentTimeMillis();
        if (s.prepUntil > 0) {
            if (now < s.prepUntil) {
                player.sendActionBar(plugin.messages().get("ex-td-actionbar-prep", "sec", (s.prepUntil - now + 999) / 1000));
                return;
            }
            s.prepUntil = 0;
            s.waveStart = now;
            plugin.messages().send(player, "ex-td-prep-over");
            player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.5f, 1.4f);
            spawnWave(player, s);
            return;
        }
        if (s.pauseUntil > 0) {
            if (now < s.pauseUntil) {
                player.sendActionBar(plugin.messages().get("ex-td-actionbar-pause", "level", s.level + 1,
                        "sec", (s.pauseUntil - now + 999) / 1000, "hp", Math.max(0, Math.round(s.coreHp)), "max", Math.round(s.coreMax),
                        "coins", Math.round(s.tdCoins)));
                return;
            }
            if (!plugin.stamina().spend(player.getUniqueId(), Stamina.Mode.DEFENSE)) {
                plugin.stamina().sendMissing(player, Stamina.Mode.DEFENSE);
                finish(player, null); // keine Ausdauer für die nächste Welle: Verteidigung endet mit Auszahlung
                return;
            }
            s.pauseUntil = 0;
            startNextWave(player, s);
            return;
        }
        if (now - s.waveStart > 3500) {
            if (s.mobs.isEmpty() && s.queue.isEmpty()) {
                waveCleared(player, s);
                return;
            }
            if (s.mobs.isEmpty()) {
                spawnWave(player, s);
            }
        }
        // Nachzügler: Gegner, die zu lange brauchen, werden zum Kern geholt
        long timeout = Math.max(60, cfgI("defense.wave-timeout-seconds", 240)) * 1000L;
        if (now - s.waveStart > timeout && now - s.stragglerAt > 15000 && !s.mobs.isEmpty()) {
            s.stragglerAt = now;
            for (UUID id : new ArrayList<>(s.mobs)) {
                if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid()
                        && le.getLocation().distance(s.core) > 12) {
                    le.teleport(ringAroundCore(s, 8));
                }
            }
        }
        player.sendActionBar(plugin.messages().get("ex-td-actionbar", "level", s.level, "left", s.mobs.size() + s.queue.size(),
                "hp", Math.max(0, Math.round(s.coreHp)), "max", Math.round(s.coreMax), "coins", Math.round(s.tdCoins))
                .append(usesSummary(s, s.level)));
    }

    /** Macht die Welle zu einer Verteidigungs-Welle: nur Angreifer, jede Welle stärker, Wert pro Kill. */
    private void tdAdjust(Session s) {
        for (int i = 0; i < s.queue.size(); i++) {
            if (isSupporter(s.queue.get(i))) {
                s.queue.set(i, EntityType.ZOMBIE); // Unterstützer greifen nicht an: in Wellen kämpfen nur Angreifer
            }
        }
        if (s.queue.isEmpty()) {
            for (int i = 0; i < Math.max(3, s.level); i++) {
                s.queue.add(EntityType.ZOMBIE);
            }
        }
        if (s.bossType != null && !s.queue.contains(s.bossType)) {
            s.bossType = null;
        }
        double w = Math.max(0, s.level - 1);
        s.hpMult *= 1 + Math.max(0, cfgD("defense.coop-hp-growth", 0.3)) * s.guests.size(); // mehr Spieler, zähere Gegner
        s.hpMult *= 1 + Math.max(0, cfgD("defense.wave-hp-growth", 0.05)) * w;
        s.dmgMult *= 1 + Math.max(0, cfgD("defense.wave-dmg-growth", 0.03)) * w;
        double cost = 0;
        for (EntityType t : s.queue) {
            MobDef d = defOf(t);
            cost += d == null ? 3 : d.cost();
        }
        double target = reward(s.player, s.level) * Math.max(0, cfgD("defense.reward-factor", 0.6));
        s.tdValue = cost <= 0 ? 0 : target / cost;
    }

    private void startNextWave(Player player, Session s) {
        s.level++;
        CompanionPowers.configure(cfgI("power.levels-per-use", 10), cfgI("power.max-uses", 8));
        resetUsesFor(s); // neue Welle: Fähigkeiten und Heilplätze sind wieder da
        for (UUID gid : s.guests) {
            resetUsesFor(sessions.get(gid));
        }
        reviveAll(s);
        Plan plan = plan(s.level);
        s.mobs.clear();
        s.queue.clear();
        for (Map.Entry<EntityType, Integer> e : plan.mobs().entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                s.queue.add(e.getKey());
            }
        }
        java.util.Collections.shuffle(s.queue, new Random(s.level * 31L));
        s.bossType = plan.leader();
        s.bossSpawned = false;
        if (plan.leader() != null && s.queue.remove(plan.leader())) {
            s.queue.add(0, plan.leader());
        }
        s.hpMult = plan.hpMult();
        s.dmgMult = plan.dmgMult();
        tdAdjust(s);
        s.waveStart = System.currentTimeMillis();
        s.stragglerAt = 0;
        planEvent(s, true);
        plugin.messages().send(player, "ex-td-wave-start", "level", s.level, "count", s.queue.size());
        if (plan.boss()) {
            plugin.messages().send(player, "ex-boss-start", "level", s.level);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
        } else {
            player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.5f, 1.4f);
        }
        spawnWave(player, s);
    }

    private void waveCleared(Player player, Session s) {
        flushKills(player, s);
        for (UUID gid : new ArrayList<>(s.guests)) {
            Player gp = Bukkit.getPlayer(gid);
            Session gs = sessions.get(gid);
            if (gp != null && gs != null) {
                flushKills(gp, gs);
                AttributeInstance gm = gp.getAttribute(Attribute.MAX_HEALTH);
                if (gm != null && !gp.isDead()) {
                    gp.setHealth(gm.getValue());
                }
            }
        }
        s.wavesDone = s.level;
        s.coreMax += Math.max(0, cfgD("defense.core-hp-per-wave", 10));
        s.coreHp = Math.min(s.coreMax, s.coreHp + s.coreMax * Math.max(0, cfgD("defense.wave-core-heal", 0.1)));
        AttributeInstance maxHp = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            player.setHealth(maxHp.getValue());
        }
        s.pauseUntil = System.currentTimeMillis() + Math.max(3, cfgI("defense.wave-pause-seconds", 12)) * 1000L;
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.3f);
        plugin.messages().send(player, "ex-td-wave-done", "level", s.level, "kills", s.tdKills, "coins", Math.round(s.tdCoins),
                "sec", Math.max(3, cfgI("defense.wave-pause-seconds", 12)));
    }

    /** Die beste Welle des Spielers im Verteidigungsmodus. */
    public int defenseBest(UUID uuid) {
        return (int) plugin.database().getMetaLong("ex_tdbest_" + uuid, 0);
    }

    /** Am Ende (verloren, verlassen, abgemeldet): die gesammelten Coins werden ausgezahlt. */
    private void payoutDefense(Player player, Session s) {
        if (s.tdPaid) {
            return;
        }
        s.tdPaid = true;
        Session hs = s.host != null ? sessions.getOrDefault(s.host, s) : s;
        Database db = plugin.database();
        UUID uuid = s.player;
        int n = 1 + hs.guests.size();
        double bonus = 1 + Math.max(0, cfgD("defense.coop-bonus-percent", 20)) / 100.0 * (n - 1);
        long coins = Math.round(hs.tdCoins / n * bonus); // gemeinsam erspielt: gleichmäßig geteilt, dazu ein Mehrspieler-Bonus
        int waves = hs.wavesDone;
        long best = defenseBest(uuid);
        db.begin();
        try {
            if (coins > 0) {
                db.addCoins(uuid, coins);
            }
            if (waves > best) {
                db.setMetaLong("ex_tdbest_" + uuid, waves);
            }
            db.commit();
            if (waves > best) {
                db.setStats(uuid, Map.of("ex_tdbest", (long) waves));
            }
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Verteidigungs-Auszahlung fehlgeschlagen: " + e.getMessage());
            return;
        }
        if (coins > 0) {
            db.logTx(uuid, player.getName(), "expedition", coins, "Verteidigung: " + waves + " Wellen, " + hs.tdKills + " Kills"
                    + (n > 1 ? " (mit " + (n - 1) + " Mitspieler(n))" : ""));
        }
        if (player.isOnline()) {
            plugin.messages().send(player, "ex-td-end", "waves", waves, "reached", hs.level, "kills", hs.tdKills, "coins", coins,
                    "best", Math.max(best, waves));
        }
    }

    /** Hat der Spieler oder einer seiner Begleiter das Ziel erlegt (nicht Fall, Feuer, Ertrinken ...)? */
    private boolean friendlyKill(Session s, LivingEntity dead) {
        if (!(dead.getLastDamageCause() instanceof EntityDamageByEntityEvent hit)) {
            return true; // Feuer, Gift, Explosionen und Fähigkeiten deiner Begleiter zählen für dich
        }
        Entity damager = hit.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        return inGroup(s, damager.getUniqueId()) || companionIds.contains(damager.getUniqueId());
    }

    private boolean isTdMob(UUID id) {
        for (Session s : sessions.values()) {
            if (s.td && s.mobs.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prüft, ob die Spawn-Ringe der Gegner noch zu Fuß mit dem Kern verbunden sind. Ein Block auf Bodenhöhe oder eine Spalte
     * von zwei Blöcken Höhe sperrt die Stelle. So lässt sich das Zielobjekt nicht zubauen.
     */
    private boolean pathsOpen(Session s, int r) {
        int cx = s.slot * SLOT_SPACING;
        int size = 2 * r + 1;
        boolean[][] blocked = new boolean[size][size];
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                blocked[x + r][z + r] = s.placed.contains(key(cx + x, ARENA_Y + 1, z)) || s.placed.contains(key(cx + x, ARENA_Y + 2, z));
            }
        }
        boolean[][] seen = new boolean[size][size];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        int kx = s.core.getBlockX() - cx;
        int kz = s.core.getBlockZ();
        queue.add(new int[]{kx + r, kz + r});
        seen[kx + r][kz + r] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0];
                int nz = cur[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= size || nz >= size || seen[nx][nz] || blocked[nx][nz]) {
                    continue;
                }
                seen[nx][nz] = true;
                queue.add(new int[]{nx, nz});
            }
        }
        // Alle freien Stellen der Arena müssen erreichbar bleiben: keine abgeschlossenen Kammern, in denen Gegner
        // gefangen oder (wie bei einer Falle) gefahrlos erledigt werden könnten, und der Rand (dort erscheinen die Gegner) bleibt offen
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d <= r - 2 && !blocked[x + r][z + r] && !seen[x + r][z + r]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Kommandostab: Rechtsklick auf einen Begleiter wählt ihn aus. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandRodEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.td || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || player.getInventory().getItemInMainHand().getType() != Material.BLAZE_ROD) {
            return;
        }
        event.setCancelled(true);
        UUID id = event.getRightClicked().getUniqueId();
        if (!s.companions.contains(id)) {
            return;
        }
        s.selected = id;
        Copy kind = companionKinds.get(id);
        plugin.messages().send(player, "ex-td-selected", "name", kind == null ? "Begleiter" : kind.companion().label + " #" + (kind.index() + 1));
    }

    /** Kommandostab: Rechtsklick auf einen Block setzt den Posten des gewählten Begleiters. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandRodBlock(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Session s = sessions.get(player.getUniqueId());
        if (s == null || !s.td || player.getInventory().getItemInMainHand().getType() != Material.BLAZE_ROD) {
            return;
        }
        event.setCancelled(true);
        if (s.selected == null || !s.companions.contains(s.selected)) {
            plugin.messages().send(player, "ex-td-pick-first");
            return;
        }
        Block b = event.getClickedBlock();
        int cx = s.slot * SLOT_SPACING;
        int r = radiusOf(arenaTier(s));
        if (Math.abs(b.getX() - cx) > r - 2 || Math.abs(b.getZ()) > r - 2) {
            return;
        }
        Location post = b.getLocation().add(0.5, 1, 0.5);
        s.posts.put(s.selected, post);
        Entity e = Bukkit.getEntity(s.selected);
        if (e instanceof Mob mob) {
            try {
                mob.getPathfinder().moveTo(post, 1.2);
            } catch (RuntimeException ignored) {
                // kann nicht laufen
            }
        }
        plugin.messages().send(player, "ex-td-post-set");
    }

    /** Wie voll wirkt der Aspekt eines Begleiters vom Typ comp gegen Gegner vom Typ enemy? */
    private double typeShare(CompanionTypes.MobType comp, CompanionTypes.MobType enemy, boolean damage) {
        boolean fits = comp.strongAgainst().contains(enemy) || (!damage && comp == enemy);
        return fits ? 1.0 : Math.max(0, Math.min(1, cfgD("aspect.off-type-factor", 0.4)));
    }

    private void spawnCompanions(Player player, Session s) {
        removeCompanions(s);
        s.aDmgType.clear();
        s.aDefType.clear();
        s.healPool.clear();
        s.sameHealed.clear();
        s.healLeft.clear();
        s.aDamage = 0;
        s.aDefense = 0;
        s.aCoins = 0;
        s.aRegen = 0;
        UUID uuid = player.getUniqueId();
        // In der Verteidigung zählen alle freigekauften Plätze (im Level nie mehr als Kreaturen)
        int allowed = s.td || s.mine ? slotsUnlocked(uuid) : Math.min(slotsUnlocked(uuid), plan(s.level).total());
        Set<Copy> override = pickOverride.get(uuid);
        if (allowed <= 0) {
            return;
        }
        List<Copy> list = takenReady(uuid);
        if (override != null) {
            list.retainAll(override);
        }
        list.sort((a, b) -> Long.compare(roster().kills(uuid, b.companion(), b.index()), roster().kills(uuid, a.companion(), a.index())));
        spawning = true;
        try {
            int n = 0;
            s.mercs.clear();
            for (Copy k : list) {
                if (n >= allowed) {
                    break;
                }
                Companion c = k.companion();
                int idx = k.index();
                if (spawnCompanion(player, s, c, n, idx)) {
                    n++;
                    CompanionAspects.LevelAspect la = CompanionAspects.level(c);
                    CompanionTraits.Rarity rar = rarity(uuid, c, idx);
                    double v = CompanionAspects.scaled(la.base(), copyLevel(uuid, c, idx)) * rar.aspect;
                    switch (la.kind()) {
                        case DAMAGE -> {
                            s.aDamage += v;
                            for (CompanionTypes.MobType t : CompanionTypes.MobType.values()) {
                                s.aDmgType.merge(t, v * typeShare(c.mobType(), t, true), Double::sum);
                            }
                        }
                        case DEFENSE -> {
                            s.aDefense += v;
                            for (CompanionTypes.MobType t : CompanionTypes.MobType.values()) {
                                s.aDefType.merge(t, v * typeShare(c.mobType(), t, false), Double::sum);
                            }
                        }
                        case COINS -> s.aCoins += v;
                        case REGEN -> s.aRegen += v;
                    }
                    for (CompanionTraits.Trait t : traits(uuid, c, idx)) {
                        s.aCoins += t.levelCoins;
                    }
                }
            }
            if (cfgBool("mercenary.enabled", true) && mercs().enabled()) {
                int cap = plan(s.level).total();
                for (MercenaryManager.Offer o : mercs().activeFor(uuid)) {
                    Copy k = o.copy();
                    if (k == null || n >= cap || roster().isGone(o.owner, k.companion(), k.index())) {
                        continue;
                    }
                    if (spawnCompanion(player, s, k.companion(), n, k.index(), o.owner, o.ownerName)) {
                        n++;
                        s.mercs.add(o);
                    }
                }
            }
        } finally {
            spawning = false;
        }
        s.aDmgType.replaceAll((t, v) -> Math.min(CompanionAspects.LevelKind.DAMAGE.cap, v));
        s.aDefType.replaceAll((t, v) -> Math.min(CompanionAspects.LevelKind.DEFENSE.cap, v));
        s.aDamage = Math.min(CompanionAspects.LevelKind.DAMAGE.cap, s.aDamage);
        s.aDefense = Math.min(CompanionAspects.LevelKind.DEFENSE.cap, s.aDefense);
        s.aCoins = Math.min(CompanionAspects.LevelKind.COINS.cap, Math.max(0, s.aCoins));
        s.aRegen = Math.min(CompanionAspects.LevelKind.REGEN.cap, s.aRegen);
        StringBuilder sb = new StringBuilder();
        if (s.aDamage > 0) {
            sb.append("+").append(CompanionAspects.percent(s.aDamage)).append(" Schaden  ");
        }
        if (s.aDefense > 0) {
            sb.append("-").append(CompanionAspects.percent(s.aDefense)).append(" erlittener Schaden  ");
        }
        if (s.aCoins > 0) {
            sb.append("+").append(CompanionAspects.percent(s.aCoins)).append(" Coins  ");
        }
        if (s.aRegen > 0) {
            sb.append("+").append(String.format(java.util.Locale.GERMANY, "%.2f", s.aRegen)).append(" Leben/s");
        }
        if (sb.length() > 0) {
            plugin.messages().send(player, "ex-aspects", "text", sb.toString().trim());
        }
    }

    private boolean spawnCompanion(Player player, Session s, Companion c, int n, int idx) {
        return spawnCompanion(player, s, c, n, idx, player.getUniqueId(), null);
    }

    /** Wie oben; bei einem Söldner liefert dataOwner (der Besitzer) Level, Seltenheit und Eigenschaften, lenderName != null. */
    private boolean spawnCompanion(Player player, Session s, Companion c, int n, int idx, UUID dataOwner, String lenderName) {
        EntityType type;
        try {
            type = EntityType.valueOf(c.type);
        } catch (IllegalArgumentException e) {
            return false;
        }
        UUID uuid = dataOwner;
        double ring = 2.5 + (n / 8) * 1.5;
        Location at = player.getLocation().add(Math.cos(n * 0.79) * ring, 0, Math.sin(n * 0.79) * ring);
        Entity entity;
        try {
            entity = player.getWorld().spawnEntity(at, type);
        } catch (RuntimeException e) {
            return false; // diese Kreatur lässt sich hier nicht erzeugen
        }
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return false;
        }
        living.setPersistent(false);
        living.setRemoveWhenFarAway(false);
        living.setCanPickupItems(false);
        living.setInvulnerable(false);
        widenSight(living);
        if (!(living instanceof org.bukkit.entity.Rabbit && c.key.equals("rabbit"))) {
            CompanionVariants.apply(living, variantOf(uuid, c, idx)); // z. B. Wolf aus einem anderen Biom
        }
        // Bewusst nicht gezaehmt und ohne Besitzer: Begleiter laufen nicht hinter dir her und teleportieren sich nicht zu dir,
        // sondern kaempfen selbststaendig gegen die naechsten Gegner (Spieler und Begleiter bleiben durch Sperren verschont).
        if (living instanceof org.bukkit.entity.IronGolem golem) {
            golem.setPlayerCreated(true);
        }
        if (living instanceof org.bukkit.entity.Piglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (living instanceof org.bukkit.entity.PiglinBrute brute) {
            brute.setImmuneToZombification(true);
        }
        if (living instanceof org.bukkit.entity.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
        if (living instanceof org.bukkit.entity.Rabbit rabbit && c.key.equals("rabbit")) {
            rabbit.setRabbitType(org.bukkit.entity.Rabbit.Type.THE_KILLER_BUNNY);
        }
        if (living instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(2);
        }
        if (living instanceof Ageable a && !(living instanceof org.bukkit.entity.Allay)) {
            a.setAdult();
        }
        if (living instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setBaby(false); // Zombies sind keine Ageable: nie als Baby oder Reiter
        }
        if (living instanceof org.bukkit.entity.Goat goat) {
            goat.setScreaming(false);
        }
        EntityEquipment eq = living.getEquipment();
        if (eq != null) {
            // Zufällige Rüstung und Waffen aus dem normalen Spawn entfernen: Rüstung gibt es nur über das Level (dressVisual)
            eq.setHelmet(null);
            eq.setChestplate(null);
            eq.setLeggings(null);
            eq.setBoots(null);
            eq.setItemInMainHand(null);
            eq.setItemInOffHand(null);
            Material weapon = switch (c.key) {
                case "skeleton", "bogged", "stray" -> Material.BOW;
                case "pillager" -> Material.CROSSBOW;
                case "vindicator" -> Material.IRON_AXE;
                case "piglinbrute" -> Material.GOLDEN_AXE;
                case "piglin" -> Material.GOLDEN_SWORD;
                case "witherskeleton" -> Material.STONE_SWORD;
                case "vex" -> Material.IRON_SWORD;
                default -> null;
            };
            if (weapon != null) {
                eq.setItemInMainHand(new ItemStack(weapon));
                eq.setItemInMainHandDropChance(0f);
            }
            if (c.key.equals("skeleton")) {
                eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                eq.setHelmetDropChance(0f);
            }
        }
        // Stärke wächst mit dem Level, mit der Seltenheit und den Eigenschaften, aber langsamer als die der Gegner
        int clevel = copyLevel(uuid, c, idx);
        dressVisual(living, c, uuid, idx, clevel); // je höher das Level, desto mehr (nur sichtbare) Rüstung
        CompanionTraits.Rarity rar = rarity(uuid, c, idx);
        double tHp = 1;
        double tDmg = 1;
        double tSpeed = 1;
        for (CompanionTraits.Trait t : traits(uuid, c, idx)) {
            tHp += t.hp;
            tDmg += t.dmg;
            tSpeed += t.speed;
        }
        double allyBiome = biomeFactor(s, c.type);
        double hp = (1 + (s.hpMult - 1) * 0.7) * levelBonus(clevel) * rar.stats * Math.max(0.3, tHp) * allyBiome * roleHpFactor(c);
        double dmg = (1 + (s.dmgMult - 1) * 0.7) * levelBonus(clevel) * rar.stats * Math.max(0.3, tDmg) * allyBiome * roleDmgFactor(c);
        scale(living, Attribute.MAX_HEALTH, hp, 1024);
        scale(living, Attribute.ATTACK_DAMAGE, dmg, 2048);
        if (tSpeed != 1) {
            scale(living, Attribute.MOVEMENT_SPEED, Math.max(0.5, tSpeed), 4);
        }
        AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            living.setHealth(max.getValue());
        }
        // Wesen ohne eigenen Angriff schlagen von Hand zu (Schaden wächst mit Sternen und Stärke)
        s.compDmg.put(living.getUniqueId(), (2.0 + c.stars * 0.8) * dmg);
        living.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        living.customName(Component.text((lenderName == null ? player.getName() + "s " : "Söldner (" + lenderName + ") ") + c.label + " #" + (idx + 1)
                + " Lv." + clevel + " [" + rar.label + "]", rar == CompanionTraits.Rarity.NORMAL ? NamedTextColor.GREEN : rar.color));
        living.setCustomNameVisible(true);
        markCompanion(living);
        s.companions.add(living.getUniqueId());
        companionIds.add(living.getUniqueId());
        companionKinds.put(living.getUniqueId(), new Copy(c, idx));
        companionOwner.put(living.getUniqueId(), player.getUniqueId());
        if (lenderName != null) {
            mercLender.put(living.getUniqueId(), dataOwner);
        }
        return true;
    }

    private void removeCompanions(Session s) {
        for (UUID id : new ArrayList<>(s.companions)) {
            companionIds.remove(id);
            companionKinds.remove(id);
            companionOwner.remove(id);
            mercLender.remove(id);
            unmarkCompanion(id);
            CompanionPowers.forget(id);
            idleSpot.remove(id);
            idleSince.remove(id);
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        s.companions.clear();
    }

    /** Läuft jede Sekunde: Begleiter suchen sich Gegner, folgen dem Spieler und der Hilfsgeist heilt. */
    private void companionTick(Player player, Session s) {
        if (s.companions.isEmpty() || s.cleared || s.defeated) {
            return;
        }
        long now = System.currentTimeMillis();
        Session ms = mobSession(s); // Gegner, Kern und Wellen liegen beim Gastgeber
        List<LivingEntity> enemies = enemyEntities(ms);
        double dt = s.companionHeal == 0 ? 1.0 : Math.min(2.0, Math.max(0.2, (now - s.companionHeal) / 1000.0));
        s.companionHeal = now;
        double cap = Math.max(0.1, cfgD("support.max-heal", 3.0));
        List<LivingEntity> allyList = new ArrayList<>();
        for (UUID aid : s.companions) {
            if (Bukkit.getEntity(aid) instanceof LivingEntity ale && ale.isValid() && !ale.isDead()) {
                allyList.add(ale);
            }
        }
        Map<UUID, Double> healers = new HashMap<>(); // wer heilt den Spieler (Rate)
        Map<UUID, Double> coreHealers = new HashMap<>();
        Iterator<UUID> it = s.companions.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof LivingEntity comp) || !comp.isValid() || comp.isDead()) {
                it.remove();
                companionIds.remove(id);
                companionKinds.remove(id);
                unmarkCompanion(id);
                continue;
            }
            double dist = comp.getLocation().distance(player.getLocation());
            Copy powerKind = companionKinds.get(id);
            try {
                CompanionPowers.tick(comp, enemies, allyList, powerKind == null ? 1 : copyLevel(dataOwner(s, id), powerKind.companion(), powerKind.index()));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Begleiter-Fähigkeit fehlgeschlagen (" + comp.getType() + "): " + ex);
            }
            tag(comp, s, powerKind == null ? 1 : copyLevel(dataOwner(s, id), powerKind.companion(), powerKind.index()));
            if (powerKind != null ? isDefender(powerKind.companion()) : isDefender(comp.getType())) {
                taunt(comp, enemies);
            }
            if (comp.getType() == EntityType.CREAKING) {
                continue; // das Creaking wird in proxyTick bewegt (es erstarrt sonst, wenn man es ansieht)
            }
            if (isSupporterEntity(comp)) {
                // Unterstützer bewegen sich selbstständig. Sie prüfen nur, ob du (oder in der Verteidigung der Kern) Heilung brauchst:
                // dann kommen sie hin und heilen, solange sie nah genug sind.
                if (comp instanceof Mob sm) {
                    sm.setTarget(null);
                }
                AttributeInstance pmax = player.getAttribute(Attribute.MAX_HEALTH);
                boolean playerNeeds = pmax != null && player.getHealth() < pmax.getValue() - 0.5 && !player.isDead();
                boolean coreNeeds = s.td && s.core != null && ms.coreHp > 0 && ms.coreHp < ms.coreMax - 0.5;
                double rate = supportRate(s, id);
                double toCore = s.core == null ? 999 : comp.getLocation().distance(s.core);
                // Andere Begleiter heilen: der am schlimmsten Verletzte in der Nähe
                LivingEntity hurt = null;
                double worst = 0.9;
                for (LivingEntity ally : allyList) {
                    if (ally == comp || !canHeal(s, comp, ally)) {
                        continue;
                    }
                    AttributeInstance am = ally.getAttribute(Attribute.MAX_HEALTH);
                    if (am != null && am.getValue() > 0 && ally.getHealth() / am.getValue() < worst) {
                        worst = ally.getHealth() / am.getValue();
                        hurt = ally;
                    }
                }
                if (hurt != null && comp.getLocation().distance(hurt.getLocation()) <= 6) {
                    noteHeal(s, comp, hurt);
                    AttributeInstance hm = hurt.getAttribute(Attribute.MAX_HEALTH);
                    double before = hurt.getHealth();
                    hurt.setHealth(Math.min(hm.getValue(), before + Math.min(Math.min(cap, rate) * dt, healLeft(s, id))));
                    double gained = hurt.getHealth() - before;
                    if (gained > 0) {
                        spendHeal(s, id, gained);
                        healCredit(s, id, gained);
                        hurt.getWorld().spawnParticle(org.bukkit.Particle.HEART, hurt.getLocation().add(0, 1.8, 0), 1, 0.3, 0.2, 0.3);
                    }
                }
                if (s.host == null && !s.guests.isEmpty()) {
                    for (UUID gid : s.guests) {
                        Player gp = Bukkit.getPlayer(gid);
                        AttributeInstance gmx = gp == null ? null : gp.getAttribute(Attribute.MAX_HEALTH);
                        if (gp != null && gmx != null && gp.getWorld() == comp.getWorld() && !gp.isDead()
                                && gp.getHealth() < gmx.getValue() - 0.5 && comp.getLocation().distance(gp.getLocation()) <= 8
                                && canHealId(s, comp, gid)) {
                            noteHealId(s, comp, gid);
                            double gpBefore = gp.getHealth();
                            gp.setHealth(Math.min(gmx.getValue(), gpBefore + Math.min(Math.min(cap, rate) * dt, healLeft(s, id))));
                            spendHeal(s, id, gp.getHealth() - gpBefore);
                            gp.getWorld().spawnParticle(org.bukkit.Particle.HEART, gp.getLocation().add(0, 2, 0), 1, 0.3, 0.2, 0.3);
                        }
                    }
                }
                Location goal = null;
                if (playerNeeds && dist > 4) {
                    goal = player.getLocation();
                } else if (!playerNeeds && hurt != null && comp.getLocation().distance(hurt.getLocation()) > 4) {
                    goal = hurt.getLocation();
                } else if (!playerNeeds && hurt == null && coreNeeds && toCore > 5) {
                    goal = s.core;
                }
                if (goal != null) {
                    if (dist > 30 && goal == player.getLocation()) {
                        comp.teleport(player.getLocation().add(1, comp.getType() == EntityType.ALLAY ? 2 : 0, 1));
                    } else if (comp instanceof Mob sm) {
                        try {
                            sm.getPathfinder().moveTo(goal, 1.2);
                        } catch (RuntimeException ignored) {
                            // kann nicht laufen
                        }
                    }
                }
                if (dist <= 9 && playerNeeds && canHealId(s, comp, player.getUniqueId())) {
                    noteHealId(s, comp, player.getUniqueId()); // du belegst einen seiner Heilplätze
                    healers.put(id, rate);
                } else if (dist <= 9 && !playerNeeds && s.sameHealed.getOrDefault(id, Set.of()).contains(player.getUniqueId())) {
                    healers.put(id, rate);
                }
                if (s.td && toCore <= 7 && healLeft(s, id) > 0.01) {
                    coreHealers.put(id, rate);
                }
                continue;
            }
            if (!(comp instanceof Mob mob)) {
                continue;
            }
            LivingEntity best = null;
            double bestDist = 90 * 90;
            Location post = s.td ? s.posts.get(id) : null;
            double range = Math.max(4, cfgD("defense.post-range", 14));
            for (LivingEntity enemy : enemies) {
                if (post != null && enemy.getLocation().distanceSquared(post) > range * range) {
                    continue; // am Posten kämpfen sie nur gegen Gegner in Reichweite
                }
                double d = enemy.getLocation().distanceSquared(comp.getLocation());
                if (enemy.getType() == EntityType.ALLAY) {
                    d *= 0.1; // gegnerische Allays (Heiler) werden bevorzugt angegriffen
                }
                if (d < bestDist) {
                    bestDist = d;
                    best = enemy;
                }
            }
            if (best != null) {
                mob.setTarget(best);
                if (manualFighter(comp)) {
                    if (bestDist > 6.25) {
                        try {
                            mob.getPathfinder().moveTo(best.getLocation(), 1.2);
                        } catch (RuntimeException ignored) {
                            // kann nicht laufen
                        }
                    } else if (now - s.compHit.getOrDefault(id, 0L) >= 1000) {
                        s.compHit.put(id, now);
                        AttributeInstance atk = comp.getAttribute(Attribute.ATTACK_DAMAGE);
                        double dmg = atk == null ? Math.max(2, s.compDmg.getOrDefault(id, 3.0)) : Math.max(2, atk.getValue());
                        comp.swingMainHand();
                        best.damage(dmg, comp);
                    }
                } else if (bestDist > 36) {
                    try {
                        mob.getPathfinder().moveTo(best.getLocation(), 1.2); // weite Wege selbst laufen (Aggro auf alle Gegner)
                    } catch (RuntimeException ignored) {
                        // kann nicht laufen
                    }
                }
                if (!s.td) {
                    idleHunt(comp, mob, best, now);
                }
            } else if (post != null) {
                mob.setTarget(null);
                if (comp.getLocation().distanceSquared(post) > 4) { // zurück auf den Posten
                    try {
                        mob.getPathfinder().moveTo(post, 1.0);
                    } catch (RuntimeException ignored) {
                        // kann nicht laufen
                    }
                }
            } else {
                mob.setTarget(null);
                if (dist > 20) { // ohne Gegner locker in der Naehe bleiben
                    try {
                        mob.getPathfinder().moveTo(player.getLocation(), 1.0);
                    } catch (RuntimeException ignored) {
                        // kann nicht laufen
                    }
                }
            }
        }
        // Unterstützer heilen laufend: Leben pro Sekunde je nach Sternen, Level und Seltenheit (mit Obergrenze)
        double healRate = 0;
        for (double r : healers.values()) {
            healRate += r;
        }
        double coreRate = 0;
        for (double r : coreHealers.values()) {
            coreRate += r;
        }
        if (healRate > 0) {
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && player.getHealth() < max.getValue() && !player.isDead()) {
                double before = player.getHealth();
                player.setHealth(Math.min(max.getValue(), before + Math.min(cap, healRate) * dt));
                double gained = player.getHealth() - before;
                for (Map.Entry<UUID, Double> h : healers.entrySet()) {
                    spendHeal(s, h.getKey(), gained * h.getValue() / healRate);
                    healCredit(s, h.getKey(), gained * h.getValue() / healRate);
                }
                player.getWorld().spawnParticle(org.bukkit.Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0.3, 0.2, 0.3);
            }
        }
        if (coreRate > 0 && s.td && s.core != null && ms.coreHp > 0 && ms.coreHp < ms.coreMax) {
            double add = Math.min(cap, coreRate) * dt * Math.max(0, cfgD("defense.support-core-factor", 4.0));
            double before = ms.coreHp;
            ms.coreHp = Math.min(ms.coreMax, ms.coreHp + add);
            double gained = (ms.coreHp - before) / Math.max(1.0, cfgD("defense.support-core-factor", 4.0));
            for (Map.Entry<UUID, Double> h : coreHealers.entrySet()) {
                spendHeal(s, h.getKey(), gained * h.getValue() / coreRate);
                healCredit(s, h.getKey(), gained * h.getValue() / coreRate);
            }
            s.core.getWorld().spawnParticle(org.bukkit.Particle.HEART, s.core.clone().add(0, 2, 0), 1, 0.4, 0.2, 0.4);
        }
    }

    private final Map<UUID, Location> idleSpot = new HashMap<>();
    private final Map<UUID, Long> idleSince = new HashMap<>();

    /** Steht ein Begleiter länger herum, obwohl Gegner da sind, sucht er sich selbst einen und läuft hin (zuletzt wird er neben ihn gesetzt). */
    private void idleHunt(LivingEntity comp, Mob mob, LivingEntity target, long now) {
        UUID id = comp.getUniqueId();
        Location here = comp.getLocation();
        Location prev = idleSpot.get(id);
        if (prev == null || prev.getWorld() == null || !prev.getWorld().equals(here.getWorld()) || prev.distanceSquared(here) > 2.25) {
            idleSpot.put(id, here);
            idleSince.put(id, now);
            return;
        }
        long idle = now - idleSince.getOrDefault(id, now);
        if (idle < cfgI("hunt.idle-seconds", 5) * 1000L) {
            return;
        }
        if (here.distanceSquared(target.getLocation()) <= 9) {
            idleSince.put(id, now); // er ist schon dran
            return;
        }
        mob.setTarget(target);
        try {
            mob.getPathfinder().moveTo(target.getLocation(), 1.4);
        } catch (RuntimeException ignored) {
            // kann nicht laufen
        }
        if (idle > cfgI("hunt.idle-teleport-seconds", 14) * 1000L) {
            comp.teleport(target.getLocation().add(1.5, 0, 1.5));
            idleSince.put(id, now);
        }
    }

    /** Darf dieser Unterstützer das Ziel heilen? Von seiner eigenen Art nur eine begrenzte Anzahl verschiedener Ziele. */
    private final Map<UUID, Component> tagBase = new HashMap<>();
    private final Map<UUID, Component> tagLast = new HashMap<>();

    /** Zeigt über Begleitern und Gegnern: Leben (❤), übrige Einsätze der Fähigkeit (✦) und freie Heilplätze (✚). */
    private void tag(LivingEntity e, Session s, int level) {
        UUID id = e.getUniqueId();
        Component cur = e.customName();
        Component base = tagBase.get(id);
        Component last = tagLast.get(id);
        if (base == null || (cur != null && !cur.equals(last))) {
            base = cur != null ? cur : Component.translatable(e.getType().translationKey());
            tagBase.put(id, base);
        }
        AttributeInstance maxHp = e.getAttribute(Attribute.MAX_HEALTH);
        double ratio = maxHp == null || maxHp.getValue() <= 0 ? 1 : e.getHealth() / maxHp.getValue();
        NamedTextColor hpColor = ratio > 0.6 ? NamedTextColor.GREEN : ratio > 0.3 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        Component now = base.append(Component.text(" \u2764" + Math.max(1, (int) Math.ceil(e.getHealth())), hpColor));
        int left = CompanionPowers.usesLeft(e, level);
        if (left >= 0) {
            now = now.append(Component.text(" \u2726" + left, NamedTextColor.LIGHT_PURPLE));
        }
        if (isSupporterEntity(e)) {
            now = now.append(Component.text(" \u271A" + (companionKinds.containsKey(id) ? healCharges(s, id)
                    : Math.max(0, healSlots(s, e) - (s.sameHealed.get(id) == null ? 0 : s.sameHealed.get(id).size()))), NamedTextColor.AQUA));
        }
        if (!now.equals(cur)) {
            e.customName(now);
            e.setCustomNameVisible(true);
        }
        tagLast.put(id, now);
    }

    /** Zusammenfassung für die Aktionsleiste: übrige Fähigkeit-Einsätze und Heilplätze aller eigenen Begleiter. */
    private Component usesSummary(Session s, int level) {
        int uses = 0;
        int heals = 0;
        boolean any = false;
        for (UUID id : s.companions) {
            if (!(Bukkit.getEntity(id) instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                continue;
            }
            Copy k = companionKinds.get(id);
            int lv = k == null ? level : copyLevel(dataOwner(s, id), k.companion(), k.index());
            int left = CompanionPowers.usesLeft(le, lv);
            if (left >= 0) {
                uses += left;
                any = true;
            }
            if (isSupporterEntity(le)) {
                Set<UUID> used = s.sameHealed.get(id);
                heals += companionKinds.containsKey(id) ? healCharges(s, id) : Math.max(0, healSlots(s, le) - (used == null ? 0 : used.size()));
                any = true;
            }
        }
        if (!any) {
            return Component.empty();
        }
        return Component.text("  |  Fähigkeiten \u2726 " + uses + "  Heilungen \u271A " + heals, NamedTextColor.LIGHT_PURPLE);
    }

    /** Wie viele verschiedene Ziele ein Unterstützer dieser Stufe heilen darf (für Menüs). */
    public int healSlotsAt(int level) {
        return Math.max(1, cfgI("support.charges", 2));
    }

    /** Menge Leben, die eine Heilung dieses Unterstützers verteilt. */
    private double chargeAmount(Session s, UUID id) {
        Copy k = companionKinds.get(id);
        return k == null ? Math.max(1.0, cfgD("support.base-heal", 4.0)) : healAmount(dataOwner(s, id), k.companion(), k.index());
    }

    /** Wie viel Leben dieser Unterstützer-Begleiter in diesem Level noch heilen darf. */
    private double healLeft(Session s, UUID id) {
        return s.healLeft.computeIfAbsent(id, k -> chargeAmount(s, id) * healSlotsAt(1));
    }

    private void spendHeal(Session s, UUID id, double amount) {
        if (amount > 0) {
            s.healLeft.put(id, Math.max(0, healLeft(s, id) - amount));
        }
    }

    /** Wie viele Heilungen (angefangene Ladungen) dem Unterstützer in diesem Level noch bleiben. */
    private int healCharges(Session s, UUID id) {
        double left = healLeft(s, id);
        if (left <= 0.01) {
            return 0;
        }
        return (int) Math.ceil(left / chargeAmount(s, id) - 0.0001);
    }

    private void resetUsesFor(Session x) {
        if (x == null) {
            return;
        }
        x.sameHealed.clear();
        x.healLeft.clear();
        for (UUID cid : x.companions) {
            CompanionPowers.resetUses(cid);
        }
    }

    private boolean canHeal(Session s, LivingEntity healer, LivingEntity target) {
        if (companionKinds.containsKey(healer.getUniqueId())) {
            return healLeft(s, healer.getUniqueId()) > 0.01;
        }
        // Gegnerische Unterstützer heilen nur Gegner vom selben Typ (z. B. Finster heilt Finster), und nur so viele Ziele wie sie Heilplätze haben
        if (target.getType() == EntityType.ALLAY && allayHurt(target.getUniqueId())) {
            return false; // ein getroffener Allay kann nicht geheilt werden
        }
        CompanionTypes.MobType own = CompanionTypes.of(healer.getType().name());
        return own == CompanionTypes.of(target.getType().name()) && canHealId(s, healer, target.getUniqueId());
    }

    /** Gegnerische Allays, die gerade getroffen wurden: bis wann sie langsam sind und nicht regenerieren oder geheilt werden. */
    private final Map<UUID, Long> allayHurtUntil = new HashMap<>();

    private boolean allayHurt(UUID id) {
        Long until = allayHurtUntil.get(id);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            allayHurtUntil.remove(id);
            return false;
        }
        return true;
    }

    /** Quellen von Mystery Boxen in Expeditionen: Schlüssel, Anzeigename, Standard-Tageslimit. */
    public static final String[][] BOX_SOURCES = {
        {"boss", "Expeditions-Boss", "2"},
        {"mini", "Mini-Boss", "2"},
        {"exam", "Prüfung bestanden", "1"},
        {"treasure", "Schatzraum", "1"},
        {"fossil", "Fossil in der Tiefenmine", "1"}};

    private long boxDay() {
        return java.time.ZonedDateTime.now(plugin.serverShop().zone()).toLocalDate().toEpochDay();
    }

    /** Tageslimit dieser Quelle (0 = unbegrenzt): expedition.box-caps.&lt;Schlüssel&gt;. */
    public int boxSourceCap(String key) {
        return Math.max(0, cfgI("box-caps." + key, 0));
    }

    /** Gesamtlimit aller Expeditions-Quellen pro Tag (0 = unbegrenzt). */
    public int boxTotalCap() {
        return Math.max(0, cfgI("box-daily-cap", 0));
    }

    /** Wie viele Boxen aus dieser Quelle hat der Spieler heute schon bekommen? */
    public int boxGotToday(UUID uuid, String key) {
        return (int) plugin.database().getMetaLong("exbox_" + boxDay() + "_" + key + "_" + uuid, 0);
    }

    /** Wie viele Boxen aus allen Expeditions-Quellen zusammen hat der Spieler heute schon bekommen? */
    public int boxGotTodayTotal(UUID uuid) {
        return (int) plugin.database().getMetaLong("exbox_" + boxDay() + "_" + uuid, 0);
    }

    /** Wie viele Boxen kann der Spieler heute aus dieser Quelle noch bekommen (Quelle und Gesamtlimit zählen; -1 = unbegrenzt)? */
    public int boxLeftToday(UUID uuid, String key) {
        int left = -1;
        int cap = boxSourceCap(key);
        if (cap > 0) {
            left = Math.max(0, cap - boxGotToday(uuid, key));
        }
        int total = boxTotalCap();
        if (total > 0) {
            int t = Math.max(0, total - boxGotTodayTotal(uuid));
            left = left < 0 ? t : Math.min(left, t);
        }
        return left;
    }

    /**
     * Mystery Box aus einer Expedition: je Quelle ("boss", "mini", "exam", "treasure", "fossil") und insgesamt gibt es ein
     * Tageslimit pro Spieler und Kalendertag (Serverzeit, 0 = unbegrenzt). Gibt zurück, ob die Box vergeben wurde.
     */
    private boolean giveBox(Player player, String key, int tier, String source) {
        UUID uuid = player.getUniqueId();
        if (boxLeftToday(uuid, key) == 0) {
            player.sendMessage(Component.text("Für diese Quelle (" + source + ") gibt es heute keine Mystery Box mehr. Die Übersicht findest du im Box-Lager.",
                    NamedTextColor.GRAY));
            return false;
        }
        Database db = plugin.database();
        long day = boxDay();
        if (boxSourceCap(key) > 0) {
            String k = "exbox_" + day + "_" + key + "_" + uuid;
            db.setMetaLong(k, db.getMetaLong(k, 0) + 1);
        }
        if (boxTotalCap() > 0) {
            String k = "exbox_" + day + "_" + uuid;
            db.setMetaLong(k, db.getMetaLong(k, 0) + 1);
        }
        plugin.mysteryBoxes().give(player, tier, source);
        return true;
    }

    /** Ist das eine gegnerische Arena-Kreatur (in irgendeiner Sitzung)? */
    private boolean isEnemyId(UUID id) {
        for (Session s : sessions.values()) {
            if (s.mobs.contains(id) || s.coinMobs.containsKey(id) || s.lootMobs.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** Einem gegnerischen Allay kann man nichts in die Hand geben (Rechtsklick mit Item). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnemyAllayGive(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Entity e = event.getRightClicked();
        if (e.getType() == EntityType.ALLAY && !companionKinds.containsKey(e.getUniqueId()) && isEnemyId(e.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Wasserwesen und alle anderen Kreaturen der Expedition ersticken oder trocknen nicht aus (auch nicht an Land). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNoDrown(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause c = event.getCause();
        if ((c == EntityDamageEvent.DamageCause.DROWNING || c == EntityDamageEvent.DamageCause.DRYOUT)
                && !(event.getEntity() instanceof Player)
                && (companionKinds.containsKey(event.getEntity().getUniqueId()) || isEnemyId(event.getEntity().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    /** Ein gegnerischer Allay wurde getroffen: kurz langsam, ohne Regeneration und ohne Heilung (verhindert Wegfliegen und Dauerheilung). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnemyAllayHit(EntityDamageEvent event) {
        if (event.getEntityType() != EntityType.ALLAY || companionKinds.containsKey(event.getEntity().getUniqueId())) {
            return;
        }
        UUID id = event.getEntity().getUniqueId();
        if (!isEnemyId(id)) {
            return;
        }
        int seconds = Math.max(1, cfgI("enemy-allay.hurt-seconds", 6));
        allayHurtUntil.put(id, System.currentTimeMillis() + seconds * 1000L);
        if (event.getEntity() instanceof LivingEntity le) {
            le.removePotionEffect(PotionEffectType.REGENERATION);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 2, false, false));
        }
    }

    /** Wie viele verschiedene Ziele (auch du selbst zählst als eines) darf dieser Unterstützer heilen? Steigt mit seinem Level. */
    private int healSlots(Session s, LivingEntity healer) {
        Copy k = companionKinds.get(healer.getUniqueId());
        int lvl = k == null ? Math.max(1, mobSession(s).level) : copyLevel(dataOwner(s, healer.getUniqueId()), k.companion(), k.index());
        int per = Math.max(1, cfgI("support.levels-per-slot", 5));
        int slots = Math.max(1, cfgI("support.max-same-type", 2)) + Math.max(0, lvl - 1) / per;
        return Math.min(slots, Math.max(1, cfgI("support.max-slots", 4)));
    }

    private boolean canHealId(Session s, LivingEntity healer, UUID targetId) {
        if (companionKinds.containsKey(healer.getUniqueId())) {
            return healLeft(s, healer.getUniqueId()) > 0.01;
        }
        Set<UUID> set = s.sameHealed.get(healer.getUniqueId());
        return set == null || set.contains(targetId) || set.size() < healSlots(s, healer);
    }

    private void noteHealId(Session s, LivingEntity healer, UUID targetId) {
        s.sameHealed.computeIfAbsent(healer.getUniqueId(), k -> new HashSet<>()).add(targetId);
    }

    private void noteHeal(Session s, LivingEntity healer, LivingEntity target) {
        if (healer.getType() == target.getType() || !companionKinds.containsKey(healer.getUniqueId())) {
            noteHealId(s, healer, target.getUniqueId());
        }
    }

    /** Heilen bringt Erfahrung: alle X geheilten Leben zählt wie ein Kill (gutgeschrieben wird bei geschafftem Level). */
    private void healCredit(Session s, UUID supporter, double amount) {
        Copy k = companionKinds.get(supporter);
        if (k == null || amount <= 0) {
            return;
        }
        double per = Math.max(1, cfgD("support.heal-per-xp", 20));
        double v = s.healPool.getOrDefault(k, 0.0) + amount;
        while (v >= per) {
            v -= per;
            s.killGain.merge(k, 1, Integer::sum);
        }
        s.healPool.put(k, v);
    }

    private List<LivingEntity> enemyEntities(Session own) {
        Session s = mobSession(own);
        List<LivingEntity> list = new ArrayList<>();
        for (UUID id : s.mobs) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                list.add(le);
            }
        }
        for (UUID id : s.coinMobs.keySet()) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                list.add(le);
            }
        }
        for (UUID id : s.lootMobs) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && !le.isDead()) {
                list.add(le);
            }
        }
        return list;
    }

    /** Das ganze Begleiter-System gilt auch für Gegner: Unterstützer heilen, alle haben ihre Fähigkeit, und sie greifen auch Begleiter an. */
    private void enemyTick(Player player, Session s) {
        if (s.cleared || s.defeated || s.finishing) {
            return;
        }
        List<LivingEntity> foes = new ArrayList<>();
        if (player.isValid() && !player.isDead() && !s.downed) {
            foes.add(player);
        }
        for (UUID id : s.companions) {
            if (Bukkit.getEntity(id) instanceof LivingEntity c && c.isValid() && !c.isDead()) {
                foes.add(c);
            }
        }
        for (UUID gid : s.guests) {
            Session gs = sessions.get(gid);
            Player gp = Bukkit.getPlayer(gid);
            if (gp != null && gp.isValid() && !gp.isDead() && (gs == null || !gs.downed)) {
                foes.add(gp);
            }
            if (gs != null) {
                for (UUID id : gs.companions) {
                    if (Bukkit.getEntity(id) instanceof LivingEntity c && c.isValid() && !c.isDead()) {
                        foes.add(c);
                    }
                }
            }
        }
        List<LivingEntity> mobs = enemyEntities(s);
        double follow = s.td ? Math.max(4, cfgD("defense.mob-follow-range", 12)) : 40;
        for (LivingEntity m : mobs) {
            if (m.getType() == EntityType.CREAKING) {
                continue; // wird in proxyTick bewegt
            }
            if (isSupporter(m.getType())) {
                LivingEntity hurt = null;
                double worst = 0.9;
                for (LivingEntity o : mobs) {
                    AttributeInstance om = o.getAttribute(Attribute.MAX_HEALTH);
                    if (o != m && om != null && om.getValue() > 0 && o.getHealth() / om.getValue() < worst && canHeal(s, m, o)) {
                        worst = o.getHealth() / om.getValue();
                        hurt = o;
                    }
                }
                if (hurt != null) {
                    double d = m.getLocation().distance(hurt.getLocation());
                    if (d > 4 && m instanceof Mob sm) {
                        try {
                            sm.getPathfinder().moveTo(hurt.getLocation(), 1.1);
                        } catch (RuntimeException ignored) {
                            // kann nicht laufen
                        }
                    } else if (d <= 6) {
                        noteHeal(s, m, hurt);
                        AttributeInstance hm = hurt.getAttribute(Attribute.MAX_HEALTH);
                        double amount = Math.max(1.0, hm.getValue() * Math.max(0, cfgD("enemy-heal-percent", 4)) / 100.0);
                        hurt.setHealth(Math.min(hm.getValue(), hurt.getHealth() + amount));
                        hurt.getWorld().spawnParticle(org.bukkit.Particle.HEART, hurt.getLocation().add(0, 1.8, 0), 2, 0.3, 0.2, 0.3);
                    }
                }
            } else if (m instanceof Mob mob) {
                LivingEntity best = null;
                double bd = follow * follow;
                boolean bestDefender = false;
                for (LivingEntity f : foes) {
                    Copy fk = companionKinds.get(f.getUniqueId());
                    boolean fDef = fk != null && isDefender(fk.companion());
                    double d = f.getLocation().distanceSquared(m.getLocation());
                    // Verteidiger werden immer zuerst angegriffen (egal wie weit andere näher sind)
                    if ((fDef && !bestDefender) || (fDef == bestDefender && d < bd)) {
                        bd = d;
                        best = f;
                        bestDefender = fDef;
                    }
                }
                LivingEntity cur = mob.getTarget();
                Copy curKind = cur == null ? null : companionKinds.get(cur.getUniqueId());
                boolean curDefender = curKind != null && isDefender(curKind.companion());
                if (best != null && bestDefender && !curDefender) {
                    mob.setTarget(best);
                } else if (best != null && (cur == null || !cur.isValid()
                        || (!(best instanceof Player) && cur instanceof Player
                        && bd < cur.getLocation().distanceSquared(m.getLocation())))) {
                    mob.setTarget(best); // gegnerische Kreaturen greifen auch Begleiter an
                }
            }
            try {
                if (m.getType() == EntityType.ALLAY && allayHurt(m.getUniqueId())) {
                    m.removePotionEffect(PotionEffectType.REGENERATION); // ein getroffener Allay regeneriert nicht mehr
                }
                CompanionPowers.tick(m, foes, mobs, s.level);
                tag(m, s, s.level);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Gegner-Fähigkeit fehlgeschlagen (" + m.getType() + "): " + ex);
            }
        }
    }

    /** Creaking: erstarrt in Minecraft, wenn man es ansieht. Hier läuft es deshalb in kleinen Schritten selbst und schlägt zu. */
    private void creakingStep(Session s) {
        List<LivingEntity> enemies = enemyEntities(s);
        Player owner = Bukkit.getPlayer(s.player);
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(s.companions)) {
            if (Bukkit.getEntity(id) instanceof LivingEntity c && c.getType() == EntityType.CREAKING && c.isValid()) {
                stepCreaking(s, c, enemies, now);
            }
        }
        List<LivingEntity> foes = new ArrayList<>();
        if (owner != null && owner.isValid() && !owner.isDead()) {
            foes.add(owner);
        }
        for (UUID id : s.companions) {
            if (Bukkit.getEntity(id) instanceof LivingEntity c && c.isValid() && c.getType() != EntityType.CREAKING) {
                foes.add(c);
            }
        }
        for (LivingEntity m : enemies) {
            if (m.getType() == EntityType.CREAKING) {
                stepCreaking(s, m, foes, now);
            }
        }
    }

    private void stepCreaking(Session s, LivingEntity creaking, List<LivingEntity> targets, long now) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity t : targets) {
            double d = t.getLocation().distanceSquared(creaking.getLocation());
            if (t != creaking && d < bd) {
                bd = d;
                best = t;
            }
        }
        if (best == null) {
            return;
        }
        Location from = creaking.getLocation();
        Vector dir = best.getLocation().toVector().subtract(from.toVector());
        dir.setY(0);
        double len = dir.length();
        if (len > 2.4) {
            Location to = from.clone().add(dir.normalize().multiply(Math.min(1.3, len - 2.0)));
            to.setDirection(dir);
            creaking.teleport(to);
        } else if (now - s.compHit.getOrDefault(creaking.getUniqueId(), 0L) >= 1000) {
            s.compHit.put(creaking.getUniqueId(), now);
            AttributeInstance atk = creaking.getAttribute(Attribute.ATTACK_DAMAGE);
            double dmg = atk == null ? 3.0 : Math.max(2.0, atk.getValue());
            creaking.swingMainHand();
            best.damage(dmg, creaking);
        }
    }

    // ------------------------------------------------------------------
    //  Angriffs- und Unterstützer-Kreaturen
    // ------------------------------------------------------------------

    /** Kreaturen, die zwar als friedlich gelten, aber Schaden verursachen können. */
    private static final Set<EntityType> CAN_HURT = Set.of(EntityType.PANDA);

    /** Begleiter, die als Gegner harmlos sind, als Begleiter aber kämpfen (Killerkaninchen, Ziege, Lama, Delfin, Axolotl ...). */
    private static final Set<EntityType> COMPANION_FIGHTERS = Set.of(EntityType.RABBIT, EntityType.GOAT, EntityType.LLAMA,
            EntityType.TRADER_LLAMA, EntityType.DOLPHIN, EntityType.AXOLOTL);

    /**
     * Unterstützer: Kreaturen, die keinen Schaden verursachen können. Sie kämpfen nicht, sondern heilen.
     * Angreifer: alle, die Schaden verursachen können.
     */
    public boolean isSupporter(EntityType type) {
        if (type == null || CAN_HURT.contains(type)) {
            return false;
        }
        MobDef def = defOf(type);
        return def != null && def.proxy();
    }

    public boolean isSupporter(Companion c) {
        try {
            EntityType type = EntityType.valueOf(c.type);
            // Kann ein Begleiter Schaden verursachen, bleibt er ein Kämpfer (wie das Killerkaninchen)
            // Eine Rolle pro Begleiter: steht die Art auf der Verteidiger-Liste, ist sie Verteidiger und kein Unterstützer
            return !COMPANION_FIGHTERS.contains(type) && !DEFENDER_NAMES.contains(c.type) && isSupporter(type);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Ist diese laufende Kreatur ein Unterstützer? Bei Begleitern zählt die Begleiterart, bei Gegnern der Typ. */
    private boolean isSupporterEntity(Entity e) {
        Copy k = companionKinds.get(e.getUniqueId());
        return k != null ? isSupporter(k.companion()) : isSupporter(e.getType());
    }

    /** Rolle als Text für Menüs. */
    public String roleLabel(Companion c) {
        return isSupporter(c) ? "Unterstützer" : isDefender(c) ? "Verteidiger" : "Angreifer";
    }

    /** Wie viel Leben pro Sekunde ein Unterstützer heilt. */
    private double supportRate(Session s, UUID id) {
        Copy k = companionKinds.get(id);
        if (k == null) {
            return 0.25;
        }
        double base = cfgD("support.heal-base", 0.25) + cfgD("support.heal-per-star", 0.03) * k.companion().stars;
        if ("ALLAY".equals(k.companion().type)) {
            base *= Math.max(0.1, cfgD("support.allay-factor", 0.6)); // der Hilfsgeist heilt schwächer
        }
        UUID lender = dataOwner(s, id);
        return base * levelBonus(copyLevel(lender, k.companion(), k.index())) * rarity(lender, k.companion(), k.index()).stats;
    }

    private void removeCoinMobs(Session s) {
        for (UUID id : new ArrayList<>(s.coinMobs.keySet())) {
            unglow(id);
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        s.coinMobs.clear();
        for (UUID id : new ArrayList<>(s.lootMobs)) {
            unglow(id);
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        s.lootMobs.clear();
    }

    private LootRewards lootRewards;

    private LootRewards loot() {
        if (lootRewards == null) {
            lootRewards = new LootRewards(plugin);
        }
        return lootRewards;
    }

    /** Wert eines CoinMobs: Kosten der Kreatur mal Stärke im Level (je schwerer der Gegner, desto mehr). */
    private long coinValue(EntityType type, Session s, int tier) {
        MobDef def = defOf(type);
        int cost = def == null ? 3 : def.cost();
        double strength = Math.max(1, (s.hpMult + s.dmgMult) / 2.0);
        return Math.max(1, Math.round(cost * cfgD("coin-mob-cost-factor", 2.0) * strength * tierMult(tier)));
    }

    private static final String[] TIER_NAMES = {"Eisen", "Gold", "Smaragd", "Diamant", "Netherit"};
    private static final NamedTextColor[] TIER_COLORS = {NamedTextColor.GRAY, NamedTextColor.GOLD, NamedTextColor.GREEN,
            NamedTextColor.AQUA, NamedTextColor.DARK_PURPLE};

    /** Wertfaktor der Stufe eines CoinMobs (1 bis 5). */
    private double tierMult(int tier) {
        List<Double> list = plugin.getConfig().getDoubleList("expedition.coin-mob-tier-value");
        if (list.size() < 5) {
            list = List.of(1.0, 2.0, 4.0, 8.0, 16.0);
        }
        return list.get(Math.max(0, Math.min(4, tier - 1)));
    }

    /** Leuchtfarbe der Stufe über ein Team im Haupt-Scoreboard. */
    private void glow(LivingEntity e, int tier) {
        try {
            org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            String name = "csex_t" + tier;
            org.bukkit.scoreboard.Team team = sb.getTeam(name);
            if (team == null) {
                team = sb.registerNewTeam(name);
            }
            team.color(TIER_COLORS[Math.max(0, Math.min(4, tier - 1))]);
            team.addEntry(e.getUniqueId().toString());
        } catch (RuntimeException ignored) {
            // Farbe nicht möglich: weißes Leuchten
        }
    }

    /** Begleiter leuchten dunkelgrün und tragen ein Herz vor dem Namen. */
    private void markCompanion(LivingEntity e) {
        e.setGlowing(true);
        try {
            org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            org.bukkit.scoreboard.Team team = sb.getTeam("csex_comp");
            if (team == null) {
                team = sb.registerNewTeam("csex_comp");
            }
            team.color(NamedTextColor.DARK_GREEN);
            team.prefix(Component.text("♥ ", NamedTextColor.DARK_GREEN));
            team.addEntry(e.getUniqueId().toString());
        } catch (RuntimeException ignored) {
            // ohne Team: weißes Leuchten
        }
    }

    private void unmarkCompanion(UUID id) {
        try {
            org.bukkit.scoreboard.Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("csex_comp");
            if (team != null) {
                team.removeEntry(id.toString());
            }
        } catch (RuntimeException ignored) {
            // nichts zu entfernen
        }
    }

    private void unglow(UUID id) {
        try {
            for (org.bukkit.scoreboard.Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
                if (team.getName().startsWith("csex_t")) {
                    team.removeEntry(id.toString());
                }
            }
        } catch (RuntimeException ignored) {
            // nichts zu entfernen
        }
    }

    /**
     * Zufallsereignis: Eine ganz normale Kreatur dieses Levels erscheint zusätzlich, besonders gekennzeichnet.
     * CoinMob (golden): Wert nach Schwierigkeit, Auszahlung nach dem Level. Loot-Mob (violett, äußerst selten):
     * Belohnung aus dem Shop nach dem Level. Beide zählen nicht zu den Kreaturen, die man erledigen muss.
     */
    /** Mini-Boss: die stärkste Kreatur des Levels wird deutlich zäher und stärker. Er muss besiegt werden und lässt eine Mystery Box der Stufe 1 oder 2 fallen. */
    private void spawnMiniBoss(Player player, Session s) {
        LivingEntity best = null;
        int bestCost = -1;
        for (UUID id : s.mobs) {
            if (id.equals(s.bossId) || id.equals(s.miniId) || !(Bukkit.getEntity(id) instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                continue;
            }
            MobDef def = defOf(le.getType());
            int cost = def == null ? 1 : def.cost();
            if (cost > bestCost) {
                bestCost = cost;
                best = le;
            }
        }
        if (best == null) {
            return;
        }
        scale(best, Attribute.MAX_HEALTH, Math.max(1.5, cfgD("mini-boss-hp-factor", 3.0)), 2048);
        scale(best, Attribute.ATTACK_DAMAGE, Math.max(1.2, cfgD("mini-boss-dmg-factor", 1.5)), 2048);
        AttributeInstance hp = best.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            best.setHealth(hp.getValue());
        }
        best.setGlowing(true);
        best.customName(Component.text("☠ MINI-BOSS: " + mobName(best.getType()) + " Lv." + s.level + " ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        best.setCustomNameVisible(true);
        s.miniId = best.getUniqueId();
        plugin.messages().send(player, "ex-mini-spawn");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.2f);
    }

    private void spawnSpecial(Player player, Session s, boolean loot) {
        Random random = new Random();
        List<LivingEntity> pool = new ArrayList<>();
        for (UUID id : s.mobs) {
            if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && !le.isDead() && !id.equals(s.bossId)) {
                pool.add(le);
            }
        }
        java.util.Collections.shuffle(pool, random);
        int count = Math.min(pool.size(), loot ? 1 : s.coinCount);
        int spawned = 0;
        long shownValue = 0;
        int maxTier = 1;
        {
            for (int i = 0; i < count; i++) {
                LivingEntity living = pool.get(i);
                EntityType type = living.getType();
                s.mobs.remove(living.getUniqueId()); // wird zum Sondermob und zählt nicht mehr zu den Pflicht-Gegnern
                living.setGlowing(true);
                living.setCustomNameVisible(true);
                int tier = loot ? 0 : loot().coinTier(s.level);
                if (loot) {
                    scale(living, Attribute.MAX_HEALTH, 2.0, 1024);
                    AttributeInstance hp = living.getAttribute(Attribute.MAX_HEALTH);
                    if (hp != null) {
                        living.setHealth(hp.getValue());
                    }
                    MobDef lootDef = defOf(type);
                    s.lootCost = lootDef == null ? 3 : lootDef.cost();
                    s.lootTier = loot().shiftTier(LootRewards.tierOf(plugin, s.level, s.lootCost));
                    living.customName(Component.text("✧ LOOT-MOB Stufe " + s.lootTier + " (" + TIER_NAMES[s.lootTier - 1] + "): "
                            + mobName(type) + " ✧", TIER_COLORS[s.lootTier - 1], TextDecoration.BOLD));
                    glow(living, s.lootTier);
                    s.lootMobs.add(living.getUniqueId());
                } else {
                    long value = coinValue(type, s, tier);
                    shownValue = Math.max(shownValue, value);
                    if (tier > 1) {
                        scale(living, Attribute.MAX_HEALTH, 1.0 + 0.4 * (tier - 1), 1024);
                        AttributeInstance coinHp = living.getAttribute(Attribute.MAX_HEALTH);
                        if (coinHp != null) {
                            living.setHealth(coinHp.getValue());
                        }
                    }
                    living.customName(Component.text("✦ CoinMob Stufe " + tier + " (" + TIER_NAMES[tier - 1] + "): "
                            + mobName(type) + " ✦ +" + value + " Coins", TIER_COLORS[tier - 1], TextDecoration.BOLD));
                    glow(living, tier);
                    s.coinMobs.put(living.getUniqueId(), value);
                    maxTier = Math.max(maxTier, tier);
                }
                spawned++;
            }
        }
        if (spawned > 0) {
            if (loot) {
                plugin.messages().send(player, "ex-loot-spawn", "tier", s.lootTier);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
            } else {
                plugin.messages().send(player, "ex-coin-spawn", "count", spawned, "tier", maxTier);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
            }
        }
    }

    /** Funkeln um den Loot-Mob, damit man ihn gut sieht. */
    private void lootTick(Session s) {
        for (UUID id : s.lootMobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null && e.isValid()) {
                e.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, e.getLocation().add(0, 1, 0), 6, 0.4, 0.8, 0.4, 0.02);
            }
        }
    }

    private void removeMobs(Session s) {
        eventCleanup(s);
        removeCoinMobs(s);
        removeCompanions(s);
        for (UUID id : s.mobs) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        s.mobs.clear();
        s.peaceful.clear();
        s.queue.clear();
    }

    /** Beim Ausschalten: alle zurückschicken. */
    public void shutdown() {
        if (liveInstance != null) {
            liveInstance.abortAll();
        }
        if (arenaInstance != null) {
            arenaInstance.shutdown();
        }
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                finish(p, null);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Rucksack und Zustand sichern / zurückgeben
    // ------------------------------------------------------------------

    private File stateFile(UUID uuid) {
        File dir = new File(plugin.getDataFolder(), "expedition");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Ordner expedition konnte nicht angelegt werden.");
        }
        return new File(dir, uuid + ".yml");
    }

    private boolean saveState(Player player) {
        YamlConfiguration yc = new YamlConfiguration();
        PlayerInventory inv = player.getInventory();
        yc.set("inv", Arrays.asList(inv.getContents()));
        yc.set("level", player.getLevel());
        yc.set("exp", (double) player.getExp());
        yc.set("health", player.getHealth());
        yc.set("food", player.getFoodLevel());
        yc.set("saturation", (double) player.getSaturation());
        yc.set("mode", player.getGameMode().name());
        yc.set("allowFlight", player.getAllowFlight());
        List<String> stats = new ArrayList<>();
        for (Map.Entry<String, Integer> e : readStats(player).entrySet()) {
            stats.add(e.getKey() + "=" + e.getValue());
        }
        yc.set("stats", stats);
        yc.set("advSaved", true);
        int idx = 0;
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement adv = it.next();
            String key = adv.getKey().toString();
            if (key.startsWith("minecraft:recipes/")) {
                continue;
            }
            List<String> awarded = new ArrayList<>(player.getAdvancementProgress(adv).getAwardedCriteria());
            if (!awarded.isEmpty()) {
                yc.set("adv." + idx + ".k", key);
                yc.set("adv." + idx + ".c", awarded);
                idx++;
            }
        }
        Location l = player.getLocation();
        yc.set("world", l.getWorld() == null ? "" : l.getWorld().getName());
        yc.set("x", l.getX());
        yc.set("y", l.getY());
        yc.set("z", l.getZ());
        yc.set("yaw", (double) l.getYaw());
        yc.set("pitch", (double) l.getPitch());
        try {
            yc.save(stateFile(player.getUniqueId()));
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Expedition: Rucksack konnte nicht gesichert werden: " + e.getMessage());
            return false;
        }
    }

    /** Gibt den gesicherten Zustand zurück (falls vorhanden) und löscht die Datei. */
    private void restoreState(Player player) {
        File file = stateFile(player.getUniqueId());
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        List<?> raw = yc.getList("inv");
        if (raw != null) {
            ItemStack[] contents = new ItemStack[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                contents[i] = raw.get(i) instanceof ItemStack item ? item : null;
            }
            PlayerInventory inv = player.getInventory();
            inv.clear();
            try {
                inv.setContents(contents);
            } catch (IllegalArgumentException e) {
                for (ItemStack item : contents) {
                    if (item != null) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
            }
        }
        for (org.bukkit.potion.PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        player.setLevel(yc.getInt("level", 0));
        player.setExp((float) yc.getDouble("exp", 0));
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20 : max.getValue();
        player.setHealth(Math.max(1, Math.min(maxHealth, yc.getDouble("health", maxHealth))));
        player.setFoodLevel(yc.getInt("food", 20));
        player.setSaturation((float) yc.getDouble("saturation", 5));
        try {
            player.setGameMode(GameMode.valueOf(yc.getString("mode", "SURVIVAL")));
        } catch (IllegalArgumentException e) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(yc.getBoolean("allowFlight", false) || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR);
        World world = Bukkit.getWorld(yc.getString("world", ""));
        Location target = world == null ? Bukkit.getWorlds().get(0).getSpawnLocation()
                : new Location(world, yc.getDouble("x"), yc.getDouble("y"), yc.getDouble("z"),
                (float) yc.getDouble("yaw"), (float) yc.getDouble("pitch"));
        internalTeleport = true;
        try {
            player.teleport(target);
        } finally {
            internalTeleport = false;
        }
        if (yc.contains("stats")) {
            Map<String, Integer> snap = new HashMap<>();
            for (String line : yc.getStringList("stats")) {
                int eq = line.lastIndexOf('=');
                if (eq > 0) {
                    try {
                        snap.put(line.substring(0, eq), Integer.parseInt(line.substring(eq + 1)));
                    } catch (NumberFormatException ignored) {
                        // Zeile kaputt: überspringen
                    }
                }
            }
            applyStats(player, snap);
        }
        if (yc.getBoolean("advSaved", false)) {
            Map<String, Set<String>> keep = new HashMap<>();
            ConfigurationSection sec = yc.getConfigurationSection("adv");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    String k = sec.getString(id + ".k");
                    if (k != null) {
                        keep.put(k, new HashSet<>(sec.getStringList(id + ".c")));
                    }
                }
            }
            applyAdvancements(player, keep);
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Expedition: Sicherungsdatei konnte nicht gelöscht werden: " + file);
        }
    }

    // ------------------------------------------------------------------
    //  Statistiken: Alles in der Expedition zählt nicht
    // ------------------------------------------------------------------

    /** Zeitzähler, die immer weiterlaufen (echte Spielzeit usw.). */
    private static final Set<String> SKIP_STATS = Set.of("PLAY_ONE_MINUTE", "TOTAL_WORLD_TIME", "TIME_SINCE_DEATH", "TIME_SINCE_REST");

    private static int stat(Player p, Statistic st, Object arg) {
        try {
            if (arg instanceof Material m) {
                return p.getStatistic(st, m);
            }
            if (arg instanceof EntityType t) {
                return p.getStatistic(st, t);
            }
            return p.getStatistic(st);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Alle Statistiken des Spielers, die nicht 0 sind: Schlüssel "NAME", "NAME|M:MATERIAL" oder "NAME|E:KREATUR". */
    private Map<String, Integer> readStats(Player p) {
        Map<String, Integer> out = new HashMap<>();
        for (Statistic st : Statistic.values()) {
            if (SKIP_STATS.contains(st.name())) {
                continue;
            }
            switch (st.getType()) {
                case UNTYPED -> {
                    int v = stat(p, st, null);
                    if (v != 0) {
                        out.put(st.name(), v);
                    }
                }
                case BLOCK, ITEM -> {
                    boolean block = st.getType() == Statistic.Type.BLOCK;
                    for (Material m : Material.values()) {
                        if (m.isLegacy() || (block ? !m.isBlock() : !m.isItem())) {
                            continue;
                        }
                        int v = stat(p, st, m);
                        if (v != 0) {
                            out.put(st.name() + "|M:" + m.name(), v);
                        }
                    }
                }
                case ENTITY -> {
                    for (EntityType t : EntityType.values()) {
                        if (t == EntityType.UNKNOWN) {
                            continue;
                        }
                        int v = stat(p, st, t);
                        if (v != 0) {
                            out.put(st.name() + "|E:" + t.name(), v);
                        }
                    }
                }
                default -> { }
            }
        }
        return out;
    }

    /** Nimmt Erfolge zurück, die in der Expedition dazugekommen sind. Früherer Teilfortschritt bleibt. */
    private void applyAdvancements(Player p, Map<String, Set<String>> keep) {
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement adv = it.next();
            String key = adv.getKey().toString();
            if (key.startsWith("minecraft:recipes/")) {
                continue;
            }
            AdvancementProgress progress = p.getAdvancementProgress(adv);
            Set<String> wanted = keep.getOrDefault(key, Set.of());
            for (String criteria : new ArrayList<>(progress.getAwardedCriteria())) {
                if (!wanted.contains(criteria)) {
                    progress.revokeCriteria(criteria);
                }
            }
        }
    }

    /** Setzt alle Statistiken auf den gesicherten Stand zurück (auch die, die in der Expedition neu entstanden sind). */
    private void applyStats(Player p, Map<String, Integer> wanted) {
        Map<String, Integer> now = readStats(p);
        Set<String> keys = new HashSet<>(now.keySet());
        keys.addAll(wanted.keySet());
        for (String key : keys) {
            int want = wanted.getOrDefault(key, 0);
            if (now.getOrDefault(key, 0) == want) {
                continue;
            }
            try {
                String[] parts = key.split("\\|");
                Statistic st = Statistic.valueOf(parts[0]);
                if (parts.length == 1) {
                    p.setStatistic(st, want);
                } else if (parts[1].startsWith("M:")) {
                    p.setStatistic(st, Material.valueOf(parts[1].substring(2)), want);
                } else {
                    p.setStatistic(st, EntityType.valueOf(parts[1].substring(2)), want);
                }
            } catch (RuntimeException ignored) {
                // diese eine Statistik lässt sich nicht setzen
            }
        }
    }

    // ------------------------------------------------------------------
    //  Ereignisse
    // ------------------------------------------------------------------

    /** Aspekt Schaden: Treffer des Spielers auf Arena-Kreaturen werden stärker. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAspectDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }
        if (damager == null) {
            return;
        }
        Session s = sessions.get(damager.getUniqueId());
        if (s == null || s.aDamage <= 0 || s.defeated) {
            return;
        }
        UUID id = event.getEntity().getUniqueId();
        if (s.mobs.contains(id) || s.coinMobs.containsKey(id) || s.lootMobs.contains(id)) {
            CompanionTypes.MobType tt = CompanionTypes.of(event.getEntity().getType().name());
            event.setDamage(event.getDamage() * (1 + s.aDmgType.getOrDefault(tt, s.aDamage * 0.4)));
        }
    }

    /** Typen wie bei Pokémon: stark gegen bestimmte Typen (x1,5), schwach gegen andere (x0,7) - für Begleiter und Gegner. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTypeDamage(EntityDamageByEntityEvent event) {
        if (companionKinds.isEmpty() || event.getEntity() instanceof Player) {
            return;
        }
        Entity damager = sourceOf(event.getDamager());
        Entity target = event.getEntity();
        Copy att = companionKinds.get(damager.getUniqueId());
        Copy def = companionKinds.get(target.getUniqueId());
        if (att != null && def == null && isArenaMob(target.getUniqueId())) {
            event.setDamage(event.getDamage() * CompanionTypes.factor(att.companion().mobType(),
                    CompanionTypes.of(target.getType().name())));
        } else if (att == null && def != null && isArenaMob(damager.getUniqueId())) {
            event.setDamage(event.getDamage() * CompanionTypes.factor(CompanionTypes.of(damager.getType().name()),
                    def.companion().mobType()));
        }
    }

    /** Explosionen von Begleitern (Creeper, Ghast ...) zerstören keine Blöcke der Arena. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompanionExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (companionIds.isEmpty()) {
            return;
        }
        Entity source = event.getEntity();
        if (source instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (companionIds.contains(source.getUniqueId())) {
            event.blockList().clear();
        }
    }

    /** Aspekt Schutz: der Spieler erleidet weniger Schaden. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAspectDefense(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.aDefense <= 0 || s.defeated) {
            return;
        }
        double def = s.aDefense * Math.max(0, Math.min(1, cfgD("aspect.off-type-factor", 0.4)));
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity src = sourceOf(byEntity.getDamager());
            if (isArenaMob(src.getUniqueId())) {
                def = s.aDefType.getOrDefault(CompanionTypes.of(src.getType().name()), def);
            }
        }
        event.setDamage(event.getDamage() * (1 - Math.min(0.9, def)));
    }

    /** Wer steckt hinter dem Schaden? Geschosse, Reißzähne des Magiers, Gebräu-Wolken und Plagegeister gehören ihrem Besitzer. */
    private static Entity sourceOf(Entity damager) {
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        if (damager instanceof org.bukkit.entity.EvokerFangs fangs && fangs.getOwner() != null) {
            return fangs.getOwner();
        }
        if (damager instanceof org.bukkit.entity.AreaEffectCloud cloud && cloud.getSource() instanceof Entity src) {
            return src;
        }
        if (damager instanceof org.bukkit.entity.Vex vex && vex.getSummoner() != null) {
            return vex.getSummoner();
        }
        return damager;
    }

    /** Tränke und Wolken von Begleitern (z. B. der Hexe) treffen weder den Spieler noch andere Begleiter. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompanionPotion(org.bukkit.event.entity.PotionSplashEvent event) {
        if (companionIds.isEmpty() || !(event.getEntity().getShooter() instanceof Entity shooter)
                || !companionIds.contains(shooter.getUniqueId())) {
            return;
        }
        for (LivingEntity hit : new ArrayList<>(event.getAffectedEntities())) {
            if (hit instanceof Player || companionIds.contains(hit.getUniqueId())) {
                event.setIntensity(hit, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompanionCloud(org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        if (companionIds.isEmpty() || !(event.getEntity().getSource() instanceof Entity src)
                || !companionIds.contains(src.getUniqueId())) {
            return;
        }
        event.getAffectedEntities().removeIf(e -> e instanceof Player || companionIds.contains(e.getUniqueId()));
    }

    /** Statt zu sterben, verliert man nur die Expedition: Es gibt keinen echten Tod, keine Statistik und keine Wette. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        if (s.defeated || s.finishing) {
            event.setCancelled(true);
            return;
        }
        double left = player.getHealth() + player.getAbsorptionAmount();
        if (event.getFinalDamage() < left) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        if (inv.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || inv.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            return; // das Totem rettet (normales Minecraft)
        }
        event.setCancelled(true);
        if (s.td && goDown(player, s)) {
            return;
        }
        defeat(player, s, "ex-fallen");
    }

    /**
     * Verteidigung mit mehreren Spielern: Wer fällt, schaut bis zur nächsten Welle zu. Erst wenn alle gefallen sind, ist es vorbei.
     * Gibt true zurück, wenn der Fall hier schon behandelt wurde.
     */
    private boolean goDown(Player player, Session s) {
        Session hs = mobSession(s);
        UUID me = player.getUniqueId();
        boolean otherAlive = false;
        if (!hs.player.equals(me) && !hs.downed && Bukkit.getPlayer(hs.player) != null) {
            otherAlive = true;
        }
        for (UUID gid : hs.guests) {
            Session gs = sessions.get(gid);
            if (!gid.equals(me) && gs != null && !gs.downed && Bukkit.getPlayer(gid) != null) {
                otherAlive = true;
            }
        }
        if (!otherAlive) {
            if (s.host != null) {
                Player hostPlayer = Bukkit.getPlayer(s.host);
                if (hostPlayer != null) {
                    defeat(hostPlayer, hs, "ex-fallen"); // der letzte Gast fällt: für alle vorbei
                    return true;
                }
            }
            return false;
        }
        s.downed = true;
        player.closeInventory();
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setFireTicks(0);
        player.setGameMode(GameMode.SPECTATOR);
        plugin.messages().send(player, "ex-td-down");
        for (UUID gid : new ArrayList<>(hs.guests)) {
            Player gp = Bukkit.getPlayer(gid);
            if (gp != null && !gid.equals(me)) {
                plugin.messages().send(gp, "ex-td-mate-down", "player", player.getName());
            }
        }
        Player hostPlayer = Bukkit.getPlayer(hs.player);
        if (hostPlayer != null && !hs.player.equals(me)) {
            plugin.messages().send(hostPlayer, "ex-td-mate-down", "player", player.getName());
        }
        return true;
    }

    /** Zu Beginn einer neuen Welle spielen alle Gefallenen wieder mit. */
    private void reviveAll(Session hs) {
        List<UUID> all = new ArrayList<>();
        all.add(hs.player);
        all.addAll(hs.guests);
        for (UUID id : all) {
            Session ps = sessions.get(id);
            Player p = Bukkit.getPlayer(id);
            if (ps == null || p == null || !ps.downed) {
                continue;
            }
            ps.downed = false;
            internalTeleport = true;
            try {
                if (hs.core != null) {
                    p.teleport(hs.core.clone().add(2.5, 1, 2.5));
                }
            } finally {
                internalTeleport = false;
            }
            p.setGameMode(GameMode.SURVIVAL);
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(max == null ? 20 : max.getValue());
            p.setFallDistance(0);
            plugin.messages().send(p, "ex-td-revived");
        }
    }

    /** Falls doch ein echter Tod passiert (z. B. durch einen Admin): trotzdem nichts verlieren und nichts zählen. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        s.defeated = true;
        s.respawnPending = 1;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.respawnPending == 0) {
            return;
        }
        s.respawnPending = 0;
        Bukkit.getScheduler().runTask(plugin, () -> finish(player, "ex-defeat"));
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead instanceof Player) && dead.getWorld().getName().equals(worldName())) {
            // In der Expeditionswelt fällt nie etwas: auch nicht von Reitern, Jockeys und anderen Nebenfiguren
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        tagBase.remove(dead.getUniqueId());
        tagLast.remove(dead.getUniqueId());
        for (Session s : sessions.values()) {
            if (s.companions.remove(dead.getUniqueId())) {
                Copy fallen = companionKinds.get(dead.getUniqueId());
                UUID fallenData = dataOwner(s, dead.getUniqueId());
                mercLender.remove(dead.getUniqueId());
                companionIds.remove(dead.getUniqueId());
                companionKinds.remove(dead.getUniqueId());
                unmarkCompanion(dead.getUniqueId());
                event.getDrops().clear();
                event.setDroppedExp(0);
                int lvl = fallen == null ? 1 : copyLevel(fallenData, fallen.companion(), fallen.index());
                if (!s.finishing) {
                    CompanionPowers.onDeath(dead, enemyEntities(s), lvl);
                } else {
                    CompanionPowers.forget(dead.getUniqueId());
                }
                Player owner = Bukkit.getPlayer(s.player);
                if (fallen != null && !s.finishing) { // gefallene Begleiter müssen sich bis zum nächsten Shopwechsel ausruhen
                    roster().setRest(fallenData, fallen.companion(), fallen.index(), restUntilShopReset());
                    roster().save(fallenData);
                }
                if (owner != null) {
                    plugin.messages().send(owner, "ex-comp-died");
                }
                return;
            }
            if (s.mobs.contains(dead.getUniqueId()) || s.coinMobs.containsKey(dead.getUniqueId())
                    || s.lootMobs.contains(dead.getUniqueId())) {
                creditKill(s, dead);
                creditDefenders(s);
                if (!s.finishing && s.mobs.contains(dead.getUniqueId())) {
                    eventDeath(s, dead);
                }
                if (!s.finishing && !s.cleared) {
                    List<LivingEntity> targets = new ArrayList<>();
                    Player ownerNow = Bukkit.getPlayer(s.player);
                    if (ownerNow != null) {
                        targets.add(ownerNow);
                    }
                    for (UUID cid : s.companions) {
                        if (Bukkit.getEntity(cid) instanceof LivingEntity ce && ce.isValid()) {
                            targets.add(ce);
                        }
                    }
                    CompanionPowers.onDeath(dead, targets, s.level);
                }
            }
            if (s.bossId != null && s.bossId.equals(dead.getUniqueId()) && !s.defeated) {
                s.bossId = null;
                Player boxOwner = Bukkit.getPlayer(s.player);
                if (boxOwner != null) { // jeder Expeditions-Boss lässt eine Mystery Box fallen (Stufe nach Level)
                    giveBox(boxOwner, "boss", LootRewards.tierOf(plugin, s.level, 0), "Expeditions-Boss besiegt");
                }
            }
            if (s.miniId != null && s.miniId.equals(dead.getUniqueId())) {
                s.miniId = null;
                Player miniOwner = Bukkit.getPlayer(s.player);
                if (miniOwner != null && !s.finishing) {
                    int tier = new Random().nextInt(100) < Math.max(0, Math.min(100, cfgI("mini-boss-tier2-percent", 30))) ? 2 : 1;
                    if (giveBox(miniOwner, "mini", tier, "Mini-Boss besiegt")) {
                        plugin.messages().send(miniOwner, "ex-mini-got", "tier", tier);
                    }
                }
            }
            Long coin = s.coinMobs.remove(dead.getUniqueId());
            if (coin != null) {
                unglow(dead.getUniqueId());
                event.getDrops().clear();
                event.setDroppedExp(0);
                if (s.mine) {
                    s.mineCoins += coin * (1 + s.aCoins); // in der Mine zählt der CoinMob direkt zur Beute
                } else {
                    s.bonusCoins += coin;
                }
                Player owner = Bukkit.getPlayer(s.player);
                if (owner != null) {
                    plugin.messages().send(owner, "ex-coin-got", "value", coin, "total", s.mine ? Math.round(s.mineCoins) : s.bonusCoins);
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                }
                return;
            }
            if (s.lootMobs.remove(dead.getUniqueId())) {
                unglow(dead.getUniqueId());
                event.getDrops().clear();
                event.setDroppedExp(0);
                s.lootKilled = true;
                Player owner = Bukkit.getPlayer(s.player);
                if (owner != null) {
                    plugin.messages().send(owner, "ex-loot-got");
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
                }
                return;
            }
            if (!s.mobs.remove(dead.getUniqueId())) {
                continue;
            }
            event.getDrops().clear();
            event.setDroppedExp(0);
            if (s.td && !s.finishing && friendlyKill(s, dead)) {
                // Jeder besiegte Gegner zählt: Wert nach Kosten und Welle, ausgezahlt wird am Ende
                MobDef def = defOf(dead.getType());
                s.tdCoins += (def == null ? 3 : def.cost()) * s.tdValue * (1 + s.aCoins);
                s.tdKills++;
                Player owner = Bukkit.getPlayer(s.player);
                if (owner != null) {
                    owner.playSound(owner.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
                }
            } else if (!s.td && !s.finishing && s.tdValue > 0 && friendlyKill(s, dead)) {
                MobDef def = defOf(dead.getType());
                double value = (def == null ? 3 : def.cost()) * s.tdValue;
                if (s.mine) {
                    s.mineCoins += value * (1 + s.aCoins); // Mine: gleich zur Beute
                    s.mineKillCoins += value * (1 + s.aCoins);
                    s.mineKills++;
                } else {
                    s.killCoins += value; // Level: wird mit dem Level ausgezahlt
                    s.levelKills++;
                }
            }
            return;
        }
    }

    /** Treffer auf Arena-Kreaturen dürfen nicht von Sonderregeln wirkungslos werden. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHitMob(EntityDamageByEntityEvent event) {
        if (!event.isCancelled() || event.getEntity() instanceof Player) {
            return;
        }
        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }
        if (damager == null) {
            return;
        }
        Session s = sessions.get(damager.getUniqueId());
        if (s != null && !s.defeated && (s.mobs.contains(event.getEntity().getUniqueId())
                || s.coinMobs.containsKey(event.getEntity().getUniqueId())
                || s.lootMobs.contains(event.getEntity().getUniqueId()))) {
            event.setCancelled(false);
        }
    }

    /**
     * Das Creaking nimmt im Spiel normalerweise keinen Schaden. In der Arena soll es verwundbar sein:
     * Der Treffer wird abgefangen und die Lebenspunkte werden direkt abgezogen.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHitCreaking(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !"CREAKING".equals(target.getType().name())) {
            return;
        }
        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }
        if (damager == null) {
            return;
        }
        Session s = sessions.get(damager.getUniqueId());
        if (s == null || s.defeated || !s.mobs.contains(target.getUniqueId())) {
            return;
        }
        double amount = Math.max(event.getFinalDamage(), event.getDamage());
        event.setCancelled(true);
        if (amount <= 0) {
            return;
        }
        double left = target.getHealth() - amount;
        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_HURT, 1f, 0.7f);
        if (left <= 0) {
            target.setHealth(0);
        } else {
            target.setHealth(left);
        }
    }

    /** Kreaturen dürfen in der Arena keine Blöcke verändern (Enderman, Wither, Schaf, Silberfischchen ...). */
    @EventHandler(ignoreCancelled = true)
    public void onMobChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && event.getBlock().getWorld().getName().equals(worldName())) {
            event.setCancelled(true);
        }
    }

    /** In den Expeditionen, Duellen und Arenen gibt es keine Unsichtbarkeit (im normalen Minecraft schon). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNoInvisibility(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        PotionEffect now = event.getNewEffect();
        if (now == null || !now.getType().equals(PotionEffectType.INVISIBILITY)) {
            return;
        }
        World w = event.getEntity().getWorld();
        if (w.getName().equals(worldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        World w = event.getLocation().getWorld();
        if (w != null && w.getName().equals(worldName())) {
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
            if (reason == CreatureSpawnEvent.SpawnReason.JOCKEY || reason == CreatureSpawnEvent.SpawnReason.MOUNT
                    || reason == CreatureSpawnEvent.SpawnReason.TRAP || reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                    || reason == CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) {
                event.setCancelled(true); // keine Chicken Jockeys, Reiter, Fallen-Pferde, Verstärkung oder Schleim-Teilung
                return;
            }
        }
        if (w != null && w.getName().equals(worldName()) && !spawning) {
            event.setCancelled(true);
        }
    }

    /** Die Arena ist unzerstörbar: kein Feuer (Lohen, Ghasts ...), keine Ausbreitung, kein Abbrennen, keine Lava-Flüsse. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaIgnite(org.bukkit.event.block.BlockIgniteEvent event) {
        if (event.getBlock().getWorld().getName().equals(worldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBurn(org.bukkit.event.block.BlockBurnEvent event) {
        if (event.getBlock().getWorld().getName().equals(worldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        if (event.getBlock().getWorld().getName().equals(worldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaFlow(org.bukkit.event.block.BlockFromToEvent event) {
        if (event.getBlock().getWorld().getName().equals(worldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        if (event.getBlock().getWorld().getName().equals(worldName())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaDecay(org.bukkit.event.block.LeavesDecayEvent event) {
        if (event.getBlock().getWorld().getName().equals(worldName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent event) {
        World w = event.getLocation().getWorld();
        if (w != null && w.getName().equals(worldName())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!event.getBlock().getWorld().getName().equals(worldName())) {
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s != null) {
            Block b = event.getBlock();
            if (s.td && s.core != null && b.getX() == s.core.getBlockX() && b.getY() == s.core.getBlockY()
                    && b.getZ() == s.core.getBlockZ()) {
                event.setCancelled(true); // das Zielobjekt lässt sich nicht abbauen
                return;
            }
            long k = key(b.getX(), b.getY(), b.getZ());
            if (s.mine) {
                Material ore = s.oreAt.remove(k);
                if (ore != null) {
                    event.setDropItems(false);
                    event.setExpToDrop(0);
                    mineOre(player, s, ore);
                    return;
                }
                if (s.rock.remove(k) || s.placed.remove(k)) {
                    event.setDropItems(false);
                    event.setExpToDrop(0);
                    return;
                }
                event.setCancelled(true); // Boden, Wände und Ausgang bleiben heil
                return;
            }
            if (s.placed.remove(k)) {
                event.setDropItems(false);
                return;
            }
        }
        if (!player.isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!event.getBlock().getWorld().getName().equals(worldName())) {
            return;
        }
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            if (!player.isOp()) {
                event.setCancelled(true);
            }
            return;
        }
        Block b = event.getBlockPlaced();
        int cx = s.slot * SLOT_SPACING;
        int r = radiusOf(arenaTier(s));
        if (Math.abs(b.getX() - cx) > r || Math.abs(b.getZ()) > r || b.getY() <= ARENA_Y || b.getY() > ARENA_Y + ARENA_HEIGHT - 1) {
            event.setCancelled(true);
            return;
        }
        if (s.td && s.core != null) {
            double free = Math.max(2, cfgD("defense.free-radius", 4));
            double dx = b.getX() + 0.5 - s.core.getX();
            double dz = b.getZ() + 0.5 - s.core.getZ();
            if (dx * dx + dz * dz < free * free) {
                event.setCancelled(true);
                plugin.messages().send(player, "ex-td-no-build");
                return;
            }
            long k = key(b.getX(), b.getY(), b.getZ());
            s.placed.add(k);
            if (!pathsOpen(s, r)) {
                s.placed.remove(k);
                event.setCancelled(true);
                plugin.messages().send(player, "ex-td-blocked");
            }
            return;
        }
        s.placed.add(key(b.getX(), b.getY(), b.getZ()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && sessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || !sessions.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        Session s = sessions.get(event.getPlayer().getUniqueId());
        if (s.finishing) {
            return;
        }
        // Innerhalb der Arena (z. B. Enderperlen) ist erlaubt, rauszuteleportieren nicht
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !to.getWorld().getName().equals(worldName())) {
            event.setCancelled(true);
            return;
        }
        int r = radiusOf(arenaTier(s));
        if (Math.abs(to.getX() - s.slot * SLOT_SPACING) > r || Math.abs(to.getZ()) > r) {
            event.setCancelled(true); // Enderperlen bleiben in der eigenen Arena
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!sessions.containsKey(player.getUniqueId())) {
            return;
        }
        String[] parts = event.getMessage().substring(1).trim().split("\\s+");
        String first = parts[0].toLowerCase();
        if (first.contains(":")) {
            first = first.substring(first.indexOf(':') + 1);
        }
        if (first.equals("verlassen")) {
            return;
        }
        if (first.equals("coinsadmin") && player.hasPermission("coinshop.admin")) {
            return; // nur die Coins-Verwaltung bleibt für Admins offen
        }
        if ((first.equals("expedition") || first.equals("expeditionen") || first.equals("exp"))
                && parts.length >= 2 && (parts[1].equalsIgnoreCase("weiter") || parts[1].equalsIgnoreCase("verlassen")
                || parts[1].equalsIgnoreCase("ende") || parts[1].equalsIgnoreCase("einladen")
                || parts[1].equalsIgnoreCase("ausruestung") || parts[1].equalsIgnoreCase("aufsteigen"))) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "ex-command-blocked");
    }

    /** Im Level kein Handeln mit Dorfbewohnern. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTradeBlock(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (!sessions.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        Entity e = event.getRightClicked();
        if (e instanceof org.bukkit.inventory.Merchant || e instanceof org.bukkit.entity.AbstractVillager) {
            event.setCancelled(true);
            openTraderMenu(event.getPlayer(), e);
        }
    }

    /**
     * Erfolge in der Expedition: Jeder Erfolg wird höchstens einmal gemeldet (kein Dauer-Spam), im Chat nicht angezeigt,
     * bringt keine Coins und wird am Ende der Expedition zurückgenommen (applyAdvancements).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onNoAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            event.message(null);
        }
    }

    /** Elder-Guardian-Fluch (Abbaumüdigkeit) und Unheil sind in der Expedition nicht erlaubt. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBadEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player p) || !sessions.containsKey(p.getUniqueId())) {
            return;
        }
        if (event.getNewEffect() == null) {
            return;
        }
        PotionEffectType t = event.getNewEffect().getType();
        if (t.equals(PotionEffectType.MINING_FATIGUE)) {
            // Der Fluch ist nur dann verboten, wenn ein Begleiter ihn auslöst (Großer Wächter als Begleiter),
            // ein gegnerischer Großer Wächter darf ihn weiter verteilen.
            Session s = sessions.get(p.getUniqueId());
            boolean companionGuardian = false;
            for (UUID cid : s.companions) {
                Entity ce = Bukkit.getEntity(cid);
                if (ce != null && ce.getType() == EntityType.ELDER_GUARDIAN) {
                    companionGuardian = true;
                    break;
                }
            }
            boolean enemyGuardian = false;
            for (LivingEntity le : enemyEntities(s)) {
                if (le.getType() == EntityType.ELDER_GUARDIAN) {
                    enemyGuardian = true;
                    break;
                }
            }
            if (companionGuardian && !enemyGuardian) {
                event.setCancelled(true);
            }
        } else if (t.equals(PotionEffectType.BAD_OMEN) || t.equals(PotionEffectType.RAID_OMEN)
                || t.equals(PotionEffectType.HERO_OF_THE_VILLAGE)) {
            event.setCancelled(true);
        }
    }

    /** Schnüffler graben nichts aus: Items, die neben einem Schnüffler in der Arena entstehen, verschwinden. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSnifferDig(org.bukkit.event.entity.ItemSpawnEvent event) {
        World w = world();
        if (w == null || !event.getLocation().getWorld().equals(w)) {
            return;
        }
        for (Entity near : event.getLocation().getNearbyEntities(3, 3, 3)) {
            if (near.getType() == EntityType.SNIFFER) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Schneegolems hinterlassen keinen Schnee. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onNoSnow(org.bukkit.event.block.EntityBlockFormEvent event) {
        World w = world();
        if (w != null && event.getBlock().getWorld().equals(w)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelLobby(player); // wartende Mitspieler erfahren, dass abgebrochen wurde
        invites.remove(player.getUniqueId());
        for (Lobby l : lobbies.values()) {
            l.ready.remove(player.getUniqueId());
            l.invited.remove(player.getUniqueId());
        }
        Session s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        s.finishing = true;
        saveExamProgress(player, s);
        if (s.mine) {
            if (!s.defeated) {
                flushKills(player, s);
            }
            payoutMine(player, s);
        }
        s.killGain.clear(); // Kills zählen nur bei geschafftem Level
        cancelPending(player.getUniqueId());
        if (s.td) {
            sessions.put(player.getUniqueId(), s); // kurz zurück, damit Gäste und Auszahlung den Gastgeber noch finden
            payoutDefense(player, s);
            if (s.host == null && !s.guests.isEmpty()) {
                endGuests(s, "ex-td-host-gone");
            } else if (s.host != null) {
                Session hs = sessions.get(s.host);
                if (hs != null && !hs.finishing) {
                    hs.guests.remove(player.getUniqueId());
                }
            }
            sessions.remove(player.getUniqueId());
        }
        removeMobs(s);
        World world = Bukkit.getWorld(worldName());
        if (world != null) {
            clearPlaced(world, s);
        }
        player.setInvulnerable(false);
        restoreState(player); // gibt direkt zurück, damit nichts verloren geht
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && trips().finished(player.getUniqueId())) {
                plugin.messages().send(player, "ex-trip-ready");
            }
        }, 80L);
        if (!stateFile(player.getUniqueId()).exists() || sessions.containsKey(player.getUniqueId())) {
            return;
        }
        // Nach einem Absturz: Rucksack und Ort zurückgeben
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !sessions.containsKey(player.getUniqueId())) {
                restoreState(player);
                plugin.messages().send(player, "ex-restored");
            }
        }, 20L);
    }

    // ------------------------------------------------------------------
    //  Befehl
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("einladen")) {
            openInviteMenu(player);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("annehmen")) {
            acceptInvite(player);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("ausruestung") && inShopPhase(player.getUniqueId())) {
            new de.coinshop.gui.ExpeditionGearMenu(plugin, player).open(player);
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("hilfe") || args[0].equalsIgnoreCase("help"))) {
            sendHelp(player);
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("bestenliste") || args[0].equalsIgnoreCase("rangliste"))) {
            if (sessions.containsKey(player.getUniqueId())) {
                plugin.messages().send(player, "ex-already");
            } else {
                new de.coinshop.gui.ExpeditionRankMenu(plugin, player).open(player);
            }
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("training") || args[0].equalsIgnoreCase("soeldner"))) {
            if (sessions.containsKey(player.getUniqueId())) {
                plugin.messages().send(player, "ex-already");
            } else if (args[0].equalsIgnoreCase("training")) {
                new de.coinshop.gui.TrainingMenu(plugin, player).open(player);
            } else {
                new de.coinshop.gui.MercMenu(plugin, player, 0).open(player);
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("npc")) {
            if (sessions.containsKey(player.getUniqueId())) {
                plugin.messages().send(player, "ex-already");
            } else {
                new de.coinshop.gui.NpcDuelMenu(plugin, player).open(player);
            }
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("arena") || args[0].equalsIgnoreCase("arenen"))) {
            if (sessions.containsKey(player.getUniqueId())) {
                plugin.messages().send(player, "ex-already");
            } else {
                new de.coinshop.gui.ArenaListMenu(plugin, player).open(player);
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("aufsteigen")) {
            Session ms = sessions.get(player.getUniqueId());
            if (ms == null || !ms.mine) {
                plugin.messages().send(player, "mine-not-in");
            } else {
                finish(player, null);
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("minereset") && player.hasPermission("coinshop.admin")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Spieler nicht online.", NamedTextColor.RED));
            } else {
                mineReset(target.getUniqueId());
                player.sendMessage(Component.text("Tiefenmine für " + target.getName() + " wieder freigegeben.", NamedTextColor.GREEN));
            }
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("mine") || args[0].equalsIgnoreCase("tiefenmine"))) {
            if (sessions.containsKey(player.getUniqueId())) {
                plugin.messages().send(player, "ex-already");
            } else {
                new de.coinshop.gui.MineMenu(plugin, player).open(player);
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("weiter")) {
            next(player);
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("verteidigung") || args[0].equalsIgnoreCase("defense"))) {
            if (sessions.containsKey(player.getUniqueId())) {
                plugin.messages().send(player, "ex-already");
            } else if (args.length >= 2) {
                joinDefense(player, args[1]);
            } else {
                startDefense(player);
            }
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("duell") || args[0].equalsIgnoreCase("duel"))) {
            duel().command(player, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (label.equalsIgnoreCase("verlassen") || (args.length >= 1 && (args[0].equalsIgnoreCase("verlassen") || args[0].equalsIgnoreCase("ende")))) {
            if (!sessions.containsKey(player.getUniqueId())) {
                plugin.messages().send(player, "ex-not-in");
            } else {
                finish(player, "ex-left");
            }
            return true;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            plugin.messages().send(player, "ex-already");
            return true;
        }
        if (!enabled()) {
            return true;
        }
        if (plugin.timeline().previewFor(player)) {
            plugin.messages().send(player, "ex-preview");
            return true;
        }
        if (!hasLicense(player)) {
            plugin.messages().send(player, "ex-locked");
            return true;
        }
        new de.coinshop.gui.ExpeditionMenu(plugin, player).open(player);
        return true;
    }

    /** Eigene Begleiter werden nicht vom Spieler verletzt. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHitCompanion(EntityDamageByEntityEvent event) {
        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damager = p;
        }
        if (damager == null) {
            return;
        }
        Session s = sessions.get(damager.getUniqueId());
        if (s != null && (companionIds.contains(event.getEntity().getUniqueId())
                || (event.getEntity() instanceof Player other && sessions.containsKey(other.getUniqueId())))) {
            event.setCancelled(true); // kein Schaden an Begleitern (auch denen der Mitspieler) und Mitspielern
        }
    }

    private boolean isArenaMob(UUID id) {
        for (Session s : sessions.values()) {
            if (s.mobs.contains(id) || s.coinMobs.containsKey(id) || s.lootMobs.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /** Kreaturen sterben nicht an Biom oder Umgebung (Schneegolem schmilzt, Wassertiere trocknen aus ...). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player || !event.getEntity().getWorld().getName().equals(worldName())) {
            return;
        }
        switch (event.getCause()) {
            case MELTING, DRYOUT, FREEZE, SUFFOCATION, HOT_FLOOR, CRAMMING -> event.setCancelled(true);
            default -> { }
        }
        if (!event.isCancelled() && isTdMob(event.getEntity().getUniqueId())) {
            // Verteidigung: Gegner lassen sich nicht durch Fallschaden, Fallen, Feuer, Lava, Ertrinken, Explosionen ... erledigen
            switch (event.getCause()) {
                case FALL, DROWNING, LAVA, FIRE, FIRE_TICK, CONTACT, VOID, FLY_INTO_WALL, WORLD_BORDER, BLOCK_EXPLOSION,
                        ENTITY_EXPLOSION, STARVATION, CAMPFIRE, FALLING_BLOCK, DRAGON_BREATH, LIGHTNING -> {
                    // Schaden ja, aber nie tödlich: Was den Gegner erledigen würde, lässt ihn mit 1 Leben zurück
                    if (event.getEntity() instanceof LivingEntity le && event.getFinalDamage() >= le.getHealth()) {
                        event.setCancelled(true);
                        if (le.getHealth() > 1) {
                            le.setHealth(1);
                        }
                    }
                }
                default -> { }
            }
        }
    }

    /** Arena-Kreaturen greifen sich nicht gegenseitig an (weder Treffer noch Ziel). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaFriendlyFire(EntityDamageByEntityEvent event) {
        Entity damager = sourceOf(event.getDamager());
        if (isArenaMob(damager.getUniqueId()) && isArenaMob(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && isArenaMob(event.getEntity().getUniqueId())
                && isArenaMob(event.getTarget().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Begleiter greifen weder ihren Besitzer noch einander an (auch nicht mit Pfeilen). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompanionFriendlyFire(EntityDamageByEntityEvent event) {
        if (companionIds.isEmpty()) {
            return;
        }
        Entity damager = sourceOf(event.getDamager());
        if (companionIds.contains(damager.getUniqueId())
                && (event.getEntity() instanceof Player || companionIds.contains(event.getEntity().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    /** Begleiter suchen sich keine Spieler und keine anderen Begleiter als Ziel. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCompanionTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (companionIds.isEmpty() || !companionIds.contains(event.getEntity().getUniqueId())) {
            return;
        }
        if (event.getTarget() instanceof Player
                || (event.getTarget() != null && companionIds.contains(event.getTarget().getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    /** Rüstung und Schild lassen sich in der Expedition nicht aus ihren Plätzen ziehen. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKitClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !sessions.containsKey(player.getUniqueId())) {
            return;
        }
        boolean armorSlot = event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR;
        if (armorSlot) {
            event.setCancelled(true); // nur die Rüstung bleibt fest; Schild und Waffen darfst du verschieben
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        // Tausch mit F ist erlaubt (Schild und Waffe dürfen die Hand wechseln)
    }

    // ===================================================================================================
    // Wellen-Ereignisse (v61.0): kurze Zufallsereignisse im Level und in der Verteidigung
    // ===================================================================================================

    /** Art, Name, Beschreibung, gut/schlecht, Dauer in Sekunden und Gewicht (Auswahl-Chance) der Ereignisse. */
    private enum Ev {
        RAID("Überfall", "Zusätzliche Gegner stürmen die Arena. Sie zahlen doppelte Coins!", false, 45, 8),
        TRADER("Fahrender Händler", "Ein Händler steht kurz bei dir: Heilung und Tränke zum Festpreis (Rechtsklick).", true, 40, 10),
        TREASURE("Schatzraum", "Eine Gruppe Schatzwächter bewacht einen Schatz. Besiege alle für Coins und vielleicht eine Box!", true, 60, 5),
        THUNDER("Gewitter", "Blitze schlagen ein und verletzen Gegner und Begleiter, aber keine Spieler.", false, 30, 8),
        FOG("Nebelbank", "Dichter Nebel: Du siehst deutlich weniger.", false, 30, 8),
        REINFORCE("Verstärkung", "Ein Eisengolem hilft dir für kurze Zeit.", true, 35, 8),
        POISON("Giftregen", "Alle verlieren langsam Leben. Du fällst nie unter ein halbes Herz.", false, 30, 6),
        GOLD("Goldrausch", "Gegner lassen jetzt zusätzliche Coins fallen!", true, 30, 8),
        BOUNTY("Kopfgeld-Mob", "Ein Gegner leuchtet golden: Wer ihn besiegt, bekommt ein Kopfgeld.", true, 60, 8),
        QUAKE("Beben", "Der Boden bebt: Alle werden durchgeschüttelt.", false, 25, 4);

        final String title;
        final String text;
        final boolean good;
        final int secs;
        final int weight;

        Ev(String title, String text, boolean good, int secs, int weight) {
            this.title = title;
            this.text = text;
            this.good = good;
            this.secs = secs;
            this.weight = weight;
        }
    }

    private final Map<UUID, Ev> lastEvent = new HashMap<>();

    private List<Player> evPlayers(Session s) {
        List<Player> out = new ArrayList<>();
        Player host = Bukkit.getPlayer(s.player);
        if (host != null) {
            out.add(host);
        }
        for (UUID g : s.guests) {
            Player gp = Bukkit.getPlayer(g);
            if (gp != null) {
                out.add(gp);
            }
        }
        return out;
    }

    /** Plant für dieses Level (oder diese Welle) mit etwas Glück ein Ereignis. */
    private void planEvent(Session s, boolean wave) {
        eventCleanup(s);
        s.evDone = false;
        s.evAt = 0;
        if (!cfgBool("event.enabled", true) || s.examKind >= 0) {
            return;
        }
        int from = wave ? cfgI("event.td-from-wave", 5) : cfgI("event.min-level", 3);
        if (s.level < from || new Random().nextInt(100) >= cfgI("event.chance-percent", 35)) {
            return;
        }
        s.evAt = System.currentTimeMillis() + (12 + new Random().nextInt(40)) * 1000L;
    }

    private Ev pickEvent(Session s) {
        Ev last = lastEvent.get(s.player);
        List<Ev> pool = new ArrayList<>();
        int total = 0;
        for (Ev e : Ev.values()) {
            if (e == Ev.TRADER) {
                continue; // das Händler-Ereignis gibt es nicht mehr
            }
            if (last != null && e.good == last.good) {
                continue; // gute und schlechte Ereignisse wechseln sich ab
            }
            int w = Math.max(0, cfgI("event.weights." + e.name().toLowerCase(java.util.Locale.ROOT), e.weight));
            if (w > 0) {
                pool.add(e);
                total += w;
            }
        }
        if (pool.isEmpty() || total <= 0) {
            return null;
        }
        int roll = new Random().nextInt(total);
        for (Ev e : pool) {
            roll -= Math.max(0, cfgI("event.weights." + e.name().toLowerCase(java.util.Locale.ROOT), e.weight));
            if (roll < 0) {
                return e;
            }
        }
        return pool.get(0);
    }

    private void eventTick(Player player, Session s) {
        if (s.host != null || s.finishing) {
            return;
        }
        long now = System.currentTimeMillis();
        if (s.evKind == null) {
            if (s.evAt > 0 && now >= s.evAt && !s.mobs.isEmpty() && !s.evDone) {
                s.evAt = 0;
                startEvent(player, s);
            }
            return;
        }
        if (now >= s.evEnd) {
            endEvent(s, true);
            return;
        }
        Ev ev = s.evKind;
        List<Player> ps = evPlayers(s);
        switch (ev) {
            case THUNDER -> {
                if (now >= s.evNext) {
                    s.evNext = now + 3500;
                    List<Location> spots = new ArrayList<>();
                    for (UUID id : s.mobs) {
                        if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid()) {
                            spots.add(le.getLocation());
                        }
                    }
                    for (UUID id : s.companions) {
                        if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid()) {
                            spots.add(le.getLocation());
                        }
                    }
                    if (!spots.isEmpty()) {
                        Location at = spots.get(new Random().nextInt(spots.size()));
                        at.getWorld().strikeLightningEffect(at);
                        double dmg = Math.min(40, 3 + s.level * 0.05);
                        for (Entity near : at.getWorld().getNearbyEntities(at, 2.5, 3, 2.5)) {
                            if (near instanceof LivingEntity le && !(le instanceof Player) && le.isValid()) {
                                le.damage(dmg);
                            }
                        }
                    }
                }
            }
            case POISON -> {
                if (now >= s.evNext) {
                    s.evNext = now + 3000;
                    for (Player p : ps) {
                        if (p.getHealth() > 2) {
                            p.setHealth(p.getHealth() - 1);
                        }
                    }
                    for (UUID id : new ArrayList<>(s.mobs)) {
                        if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && le.getHealth() > 2) {
                            le.setHealth(le.getHealth() - 1);
                        }
                    }
                    for (UUID id : new ArrayList<>(s.companions)) {
                        if (Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid() && le.getHealth() > 2) {
                            le.setHealth(le.getHealth() - 1);
                        }
                    }
                }
            }
            case QUAKE -> {
                if (now >= s.evNext) {
                    s.evNext = now + 3000;
                    Random r = new Random();
                    List<Entity> shaken = new ArrayList<>(ps);
                    for (UUID id : s.mobs) {
                        Entity e = Bukkit.getEntity(id);
                        if (e != null) {
                            shaken.add(e);
                        }
                    }
                    for (Entity e : shaken) {
                        if (e.isValid()) {
                            e.setVelocity(new Vector((r.nextDouble() - 0.5) * 0.5, 0.45, (r.nextDouble() - 0.5) * 0.5));
                        }
                    }
                    for (Player p : ps) {
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 0.5f);
                    }
                }
            }
            case FOG -> {
                if (now >= s.evNext) {
                    s.evNext = now + 5000;
                    for (Player p : ps) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, false, false));
                    }
                }
            }
            default -> {
            }
        }
        if (now - s.evBar >= 1000) {
            s.evBar = now;
            String left = ((s.evEnd - now) / 1000) + " s";
            for (Player p : ps) {
                p.sendActionBar(Component.text("⚑ " + ev.title + " (" + left + ")", ev.good ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
    }

    private void startEvent(Player player, Session s) {
        Ev ev = pickEvent(s);
        if (ev == null) {
            return;
        }
        long now = System.currentTimeMillis();
        s.evKind = ev;
        s.evEnd = now + Math.max(10, cfgI("event.seconds." + ev.name().toLowerCase(java.util.Locale.ROOT), ev.secs)) * 1000L;
        s.evNext = now;
        s.evBar = 0;
        lastEvent.put(s.player, ev);
        for (Player p : evPlayers(s)) {
            plugin.messages().send(p, ev.good ? "ex-event-start-good" : "ex-event-start-bad", "title", ev.title, "text", ev.text);
            p.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(ev.title, ev.good ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Ereignis", NamedTextColor.GRAY)));
            tip(p, "event");
            p.playSound(p.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.5f, ev.good ? 1.6f : 0.8f);
        }
        spawning = true;
        try {
            switch (ev) {
                case RAID -> spawnExtra(player, s, Math.min(30, 5 + s.level / 10), s.evRaid, null);
                case TREASURE -> spawnExtra(player, s, Math.min(8, 3 + s.level / 40), s.evGuards, "Schatzwächter");
                case TRADER -> spawnTrader(player, s);
                case REINFORCE -> spawnHelpers(player, s);
                case BOUNTY -> markBounty(s);
                case GOLD -> s.evGold = true;
                case FOG -> {
                    for (Player p : evPlayers(s)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, false, false));
                    }
                }
                default -> {
                }
            }
        } finally {
            spawning = false;
        }
    }

    /** Beendet ein laufendes Ereignis (sichtbar, wenn die Zeit abgelaufen ist). */
    private void endEvent(Session s, boolean visible) {
        Ev ev = s.evKind;
        if (ev != null && visible) {
            for (Player p : evPlayers(s)) {
                plugin.messages().send(p, "ex-event-end", "title", ev.title);
            }
        }
        eventCleanup(s);
        s.evDone = true;
    }

    /** Räumt Händler, Helfer und Wirkungen eines Ereignisses weg (ohne Meldung). */
    private void eventCleanup(Session s) {
        if (s.evNpc != null) {
            Entity npc = Bukkit.getEntity(s.evNpc);
            if (npc != null) {
                npc.remove();
            }
            s.evNpc = null;
        }
        for (UUID id : s.evTemp) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        s.evTemp.clear();
        if (s.evKind == Ev.FOG) {
            for (Player p : evPlayers(s)) {
                p.removePotionEffect(PotionEffectType.DARKNESS);
            }
        }
        if (s.evBounty != null) {
            unglow(s.evBounty);
        }
        s.evBounty = null;
        s.evGold = false;
        s.evGuards.clear();
        s.evKind = null;
    }

    /** Zusätzliche Gegner aus der Mischung des Levels. */
    private void spawnExtra(Player player, Session s, int count, Set<UUID> into, String label) {
        Plan plan = plan(s.level);
        List<EntityType> types = new ArrayList<>();
        for (Map.Entry<EntityType, Integer> e : plan.mobs().entrySet()) {
            if (!isSupporter(e.getKey())) {
                types.add(e.getKey());
            }
        }
        if (types.isEmpty()) {
            types.add(EntityType.ZOMBIE);
        }
        World world = player.getWorld();
        Location c = center(world, s);
        int r = radiusOf(arenaTier(s));
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            EntityType type = types.get(random.nextInt(types.size()));
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = r - 3 - random.nextInt(2);
            Location at = c.clone().add(Math.cos(angle) * dist, flyHeight(type), Math.sin(angle) * dist);
            LivingEntity living = spawnOne(player, s, type, at, random);
            if (living == null) {
                continue;
            }
            if (label != null) {
                living.customName(Component.text(label + " " + mobName(type), NamedTextColor.GOLD));
                living.setGlowing(true);
            }
            s.mobs.add(living.getUniqueId());
            into.add(living.getUniqueId());
        }
    }

    private void spawnTrader(Player player, Session s) {
        Location at = player.getLocation().clone().add(2, 0, 2);
        Entity e = player.getWorld().spawnEntity(at, EntityType.WANDERING_TRADER);
        if (e instanceof LivingEntity le) {
            le.setAI(false);
            le.setInvulnerable(true);
            le.setPersistent(false);
            le.setRemoveWhenFarAway(false);
            le.customName(Component.text("Fahrender Händler (Rechtsklick)", NamedTextColor.GREEN));
            le.setCustomNameVisible(true);
            s.evNpc = le.getUniqueId();
        } else {
            e.remove();
        }
    }

    private void spawnHelpers(Player player, Session s) {
        int n = 1 + s.level / 300;
        for (int i = 0; i < Math.min(3, n); i++) {
            Entity e = player.getWorld().spawnEntity(player.getLocation().clone().add(2 - i, 0, -2), EntityType.IRON_GOLEM);
            if (e instanceof org.bukkit.entity.IronGolem golem) {
                golem.setPlayerCreated(true);
                golem.setPersistent(false);
                golem.setRemoveWhenFarAway(false);
                golem.customName(Component.text("Verstärkung", NamedTextColor.GREEN));
                golem.setCustomNameVisible(true);
                s.evTemp.add(golem.getUniqueId());
            } else {
                e.remove();
            }
        }
    }

    private void markBounty(Session s) {
        List<UUID> pool = new ArrayList<>();
        for (UUID id : s.mobs) {
            if (!id.equals(s.bossId) && !id.equals(s.miniId) && Bukkit.getEntity(id) instanceof LivingEntity le && le.isValid()) {
                pool.add(id);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        UUID pick = pool.get(new Random().nextInt(pool.size()));
        if (Bukkit.getEntity(pick) instanceof LivingEntity le) {
            s.evBounty = pick;
            le.setGlowing(true);
            le.customName(Component.text("KOPFGELD: " + mobName(le.getType()), NamedTextColor.GOLD, TextDecoration.BOLD));
            le.setCustomNameVisible(true);
        }
    }

    private void evPay(Session s, long coins) {
        if (coins <= 0) {
            return;
        }
        if (s.td) {
            s.tdCoins += coins;
        } else {
            s.bonusCoins += coins;
        }
    }

    /** Durchschnittlicher Wert eines Gegners dieses Levels (Belohnung geteilt durch Anzahl). */
    private long evUnit(Session s) {
        int total = Math.max(1, plan(s.level).total());
        return Math.max(1, Math.round((double) reward(s.player, s.level) / total));
    }

    /** Wird beim Tod eines Gegners aufgerufen (Überfall, Schatzwächter, Kopfgeld, Goldrausch). */
    private void eventDeath(Session s, LivingEntity dead) {
        UUID id = dead.getUniqueId();
        Player owner = Bukkit.getPlayer(s.player);
        if (s.evRaid.remove(id)) {
            evPay(s, evUnit(s) * 2);
        }
        if (s.evGold && s.evKind == Ev.GOLD && !s.coinMobs.containsKey(id)) {
            evPay(s, evUnit(s));
        }
        if (s.evBounty != null && s.evBounty.equals(id)) {
            s.evBounty = null;
            long pay = Math.max(20, Math.round(reward(s.player, s.level) * cfgD("event.bounty-factor", 0.4)));
            evPay(s, pay);
            if (owner != null) {
                plugin.messages().send(owner, "ex-event-bounty", "coins", pay);
            }
        }
        if (s.evGuards.remove(id) && s.evGuards.isEmpty() && s.evKind == Ev.TREASURE) {
            long pay = Math.max(30, Math.round(reward(s.player, s.level) * cfgD("event.treasure-factor", 0.6)));
            evPay(s, pay);
            boolean box = new Random().nextInt(100) < cfgI("event.treasure-box-percent", 25);
            if (owner != null) {
                if (box) {
                    giveBox(owner, "treasure", 1, "Schatzraum geplündert");
                }
                plugin.messages().send(owner, "ex-event-treasure", "coins", pay, "box", box ? " Dazu eine Mystery Box!" : "");
            }
            endEvent(s, false);
        }
    }

    /** Preis eines Angebots beim fahrenden Händler. */
    public long traderPrice(UUID player) {
        Session s = sessions.get(player);
        if (s == null) {
            return 0;
        }
        Session hs = mobSession(s);
        return Math.max(cfgI("event.trader-min-price", 20), Math.round(reward(hs.player, hs.level) * cfgD("event.trader-price-factor", 0.25)));
    }

    public boolean traderOpen(UUID player) {
        Session s = sessions.get(player);
        if (s == null) {
            return false;
        }
        Session hs = mobSession(s);
        return hs.evKind == Ev.TRADER && hs.evNpc != null;
    }

    /** Kauf beim fahrenden Händler: 0 = Heilung, 1 = Goldapfel, 2 = Kampftrank. */
    public void traderBuy(Player player, int offer) {
        UUID uuid = player.getUniqueId();
        if (!traderOpen(uuid)) {
            plugin.messages().send(player, "ex-event-trader-gone");
            return;
        }
        long price = traderPrice(uuid);
        Database db = plugin.database();
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                plugin.messages().send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.messages().send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "expedition-ereignis", -price, "Fahrender Händler, Angebot " + offer);
        switch (offer) {
            case 0 -> {
                AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    player.setHealth(max.getValue());
                }
            }
            case 1 -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 1));
            }
            default -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 90, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 90, 0));
            }
        }
        plugin.messages().send(player, "ex-event-trader-bought", "price", price);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1f, 1f);
    }

    /** Ist diese Kreatur der Händler eines Ereignisses? Dann öffnet sich das Händler-Menü. */
    private boolean openTraderMenu(Player player, Entity clicked) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return false;
        }
        Session hs = mobSession(s);
        if (hs.evNpc == null || !hs.evNpc.equals(clicked.getUniqueId())) {
            return false;
        }
        new de.coinshop.gui.EventTraderMenu(plugin, player, traderPrice(player.getUniqueId())).open(player);
        return true;
    }

    // ===================================================================================================
    // Ereignisfolgen / Prüfungen (v61.0): jedes zehnte Level besteht aus mehreren Teilen
    // ===================================================================================================

    private static final String[] EXAM_NAMES = {"Die Gauntlet", "Der Turm", "Wellensturm", "Jagd", "Zwillingsboss"};
    private static final int[] EXAM_PARTS = {5, 4, 3, 2, 2};
    private static final String[] EXAM_TEXT = {
        "Fünf Kämpfe hintereinander. Zwischen den Teilen wirst du nicht geheilt!",
        "Vier Stockwerke, jedes Stockwerk macht die Gegner stärker.",
        "Drei Wellen, jede größer als die davor. Du hast eine Minute zur Vorbereitung.",
        "Ein schneller Mini-Boss flüchtet vor dir. Fange ihn, bevor die Zeit abläuft!",
        "Zwei Mini-Bosse heilen sich gegenseitig. Besiege beide!"};

    public static boolean isExamLevel(int level) {
        return level >= 10 && level % 10 == 0;
    }

    public static int examParts(int level) {
        return EXAM_PARTS[((level / 10) - 1) % EXAM_NAMES.length];
    }

    public static String examText(int level) {
        return EXAM_TEXT[((level / 10) - 1) % EXAM_NAMES.length];
    }

    public static String examName(int level) {
        return EXAM_NAMES[((level / 10) - 1) % EXAM_NAMES.length];
    }

    private void examSetup(Player player, Session s) {
        s.examKind = -1;
        s.examPart = 0;
        s.examBreak = 0;
        if (s.td || !cfgBool("exam.enabled", true) || !isExamLevel(s.level)) {
            return;
        }
        s.examKind = ((s.level / 10) - 1) % EXAM_NAMES.length;
        s.examParts = EXAM_PARTS[s.examKind];
        s.examBaseHp = s.hpMult;
        s.examBaseDmg = s.dmgMult;
        s.queue.clear();
        s.miniAt = 0;
        s.coinAt = 0;
        s.lootAt = 0;
        s.bossType = null;
        long prep = (s.examKind == 2 ? 60 : Math.max(5, cfgI("exam.prep-seconds", 10))) * 1000L;
        s.examBreak = Math.max(System.currentTimeMillis(), s.shopUntil) + prep;
        plugin.messages().send(player, "ex-exam-start", "name", EXAM_NAMES[s.examKind], "text", EXAM_TEXT[s.examKind],
                "parts", s.examParts);
        tip(player, "exam");
        Database edb = plugin.database();
        long savedAt = edb.getMetaLong("ex_examt_" + player.getUniqueId(), 0);
        long saved = edb.getMetaLong("ex_exam_" + player.getUniqueId(), 0);
        if (saved / 100 == s.level && savedAt > System.currentTimeMillis() && saved % 100 >= 2 && saved % 100 <= s.examParts) {
            s.examPart = (int) (saved % 100) - 1; // nach der Vorbereitung geht es mit diesem Teil weiter
            plugin.messages().send(player, "ex-exam-resume", "part", saved % 100, "parts", s.examParts);
        }
        edb.setMetaLong("ex_exam_" + player.getUniqueId(), 0);
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Prüfung: " + EXAM_NAMES[s.examKind], NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(s.examParts + " Teile", NamedTextColor.GRAY)));
    }

    /** Pause zwischen zwei Teilen; true = die Pause läuft noch (der Aufrufer macht nichts weiter). */
    private boolean examBreakTick(Player player, Session s) {
        if (s.examKind < 0 || s.examBreak <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < s.examBreak) {
            player.sendActionBar(Component.text("Prüfung " + EXAM_NAMES[s.examKind] + " - Teil " + (s.examPart + 1) + " von " + s.examParts
                    + " beginnt in " + ((s.examBreak - now + 999) / 1000) + " s", NamedTextColor.GOLD));
            return true;
        }
        s.examBreak = 0;
        s.examPart++;
        examFill(s);
        s.levelStart = now;
        s.examSpecial = false;
        s.examHuntFast = false;
        player.playSound(player.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 0.5f, 1.2f);
        plugin.messages().send(player, "ex-exam-part", "part", s.examPart, "parts", s.examParts);
        return false;
    }

    /** Füllt die Warteschlange für den aktuellen Teil und setzt die Stärke. */
    private void examFill(Session s) {
        Plan plan = plan(s.level);
        List<EntityType> pool = new ArrayList<>();
        for (Map.Entry<EntityType, Integer> e : plan.mobs().entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                pool.add(e.getKey());
            }
        }
        if (pool.isEmpty()) {
            pool.add(EntityType.ZOMBIE);
        }
        java.util.Collections.shuffle(pool, new Random(s.level * 31L + s.examPart));
        double size = switch (s.examKind) {
            case 0 -> 0.6;
            case 1 -> 0.8;
            case 2 -> 0.6 * s.examPart;
            default -> 0.5;
        };
        int n = Math.max(3, (int) Math.round(pool.size() * size));
        s.queue.clear();
        for (int i = 0; i < n; i++) {
            s.queue.add(pool.get(i % pool.size()));
        }
        s.hpMult = s.examBaseHp;
        s.dmgMult = s.examBaseDmg;
        if (s.examKind == 1) {
            s.hpMult *= 1 + 0.15 * (s.examPart - 1);
            s.dmgMult *= 1 + 0.15 * (s.examPart - 1);
        }
        s.bossType = null;
        s.bossSpawned = false;
        if (s.examPart == s.examParts && plan.leader() != null) {
            s.bossType = plan.leader();
            s.queue.remove(plan.leader());
            s.queue.add(0, plan.leader());
        }
        // Jeder Teil braucht mindestens einen Angreifer (weder Unterstützer noch Verteidiger), sonst passiert nichts
        boolean attacker = false;
        for (EntityType t : s.queue) {
            if (!isSupporter(t) && !isDefender(t)) {
                attacker = true;
                break;
            }
        }
        if (!attacker) {
            EntityType fallback = EntityType.ZOMBIE;
            for (EntityType t : pool) {
                if (!isSupporter(t) && !isDefender(t)) {
                    fallback = t;
                    break;
                }
            }
            s.queue.add(s.queue.isEmpty() ? 0 : 1, fallback);
        }
        if (s.examKind == 3) {
            s.miniAt = System.currentTimeMillis() + 3000L;
        }
    }

    /** Ein Teil ist geschafft: Pause oder (beim letzten Teil) Belohnung. true = es geht noch weiter. */
    private boolean examPartDone(Player player, Session s) {
        if (s.examKind < 0 || s.examPart >= s.examParts) {
            return false;
        }
        long pause = Math.max(5, cfgI("exam.pause-seconds", 20)) * 1000L;
        s.examBreak = System.currentTimeMillis() + pause;
        if (s.examKind != 0) { // außer in der Gauntlet wird zwischen den Teilen geheilt
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                player.setHealth(max.getValue());
            }
        }
        plugin.messages().send(player, "ex-exam-part-done", "part", s.examPart, "parts", s.examParts, "sec", pause / 1000);
        return true;
    }

    /** Läuft jede Sekunde während einer Prüfung: Sonderregeln der Jagd und des Zwillingsboss. */
    private void examTick(Player player, Session s) {
        if (s.examKind < 0 || s.examBreak > 0 || s.mobs.isEmpty()) {
            return;
        }
        if (s.examKind == 3) {
            long limit = Math.max(30, cfgI("exam.hunt-seconds", 90)) * 1000L;
            if (System.currentTimeMillis() - s.levelStart > limit) {
                defeat(player, s, "ex-exam-time");
                return;
            }
            if (s.miniId != null && Bukkit.getEntity(s.miniId) instanceof Mob mob && mob.isValid()
                    && mob.getWorld().equals(player.getWorld())) {
                AttributeInstance speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null && !s.examHuntFast) {
                    s.examHuntFast = true;
                    speed.setBaseValue(Math.min(0.5, speed.getBaseValue() * 1.4));
                }
                Vector away = mob.getLocation().toVector().subtract(player.getLocation().toVector());
                if (away.lengthSquared() < 100) {
                    mob.setTarget(null);
                    if (away.lengthSquared() < 0.01) {
                        away = new Vector(1, 0, 0);
                    }
                    Location c = center(mob.getWorld(), s);
                    Location goal = mob.getLocation().clone().add(away.normalize().multiply(10));
                    double max = radiusOf(arenaTier(s)) - 4;
                    Vector off = goal.toVector().subtract(c.toVector()).setY(0);
                    if (off.length() > max) {
                        off.normalize().multiply(max);
                        goal = c.clone().add(off);
                    }
                    try {
                        mob.getPathfinder().moveTo(goal, 1.3);
                    } catch (RuntimeException ignored) {
                        // kann nicht laufen
                    }
                }
            }
        }
        if (s.examKind == 4) {
            if (!s.examSpecial) {
                s.examSpecial = true;
                spawnMiniBoss(player, s);
                s.twinA = s.miniId;
                spawnMiniBoss(player, s);
                s.twinB = s.miniId;
            }
            LivingEntity a = s.twinA != null && Bukkit.getEntity(s.twinA) instanceof LivingEntity la && la.isValid() && !la.isDead() ? la : null;
            LivingEntity b = s.twinB != null && Bukkit.getEntity(s.twinB) instanceof LivingEntity lb && lb.isValid() && !lb.isDead() ? lb : null;
            if (a != null && b != null) {
                for (LivingEntity x : new LivingEntity[] {a, b}) {
                    AttributeInstance hp = x.getAttribute(Attribute.MAX_HEALTH);
                    if (hp != null) {
                        x.setHealth(Math.min(hp.getValue(), x.getHealth() + hp.getValue() * cfgD("exam.twin-heal-percent", 3.0) / 100.0));
                    }
                }
            }
        }
    }

    // ===================================================================================================
    // Anleitung im Spiel, Prüfungs-Zwischenstand und Bestenliste (v62.0)
    // ===================================================================================================

    /** Ein Tipp, der jedem Spieler genau einmal gezeigt wird (abschaltbar mit expedition.tips.enabled). */
    private void tip(Player player, String key) {
        if (!cfgBool("tips.enabled", true)) {
            return;
        }
        Database db = plugin.database();
        String meta = "ex_tip_" + key + "_" + player.getUniqueId();
        if (db.getMetaLong(meta, 0) > 0) {
            return;
        }
        db.setMetaLong(meta, 1);
        plugin.messages().send(player, "ex-tip-" + key);
    }

    /** Kurze Anleitung (Befehl /expedition hilfe und Knopf im Expeditionsmenü). */
    public void sendHelp(Player player) {
        for (Component line : plugin.messages().getList("ex-help")) {
            player.sendMessage(line);
        }
    }

    /** Beim Verlassen mitten in einer Prüfung: den erreichten Teil merken (gilt eine begrenzte Zeit). */
    private void saveExamProgress(Player player, Session s) {
        if (s.examKind < 0 || s.td) {
            return;
        }
        int resume = s.examBreak > 0 && s.examPart > 0 ? s.examPart + 1 : s.examPart;
        if (resume < 2 || resume > s.examParts) {
            return;
        }
        Database db = plugin.database();
        db.setMetaLong("ex_exam_" + s.player, s.level * 100L + resume);
        db.setMetaLong("ex_examt_" + s.player, System.currentTimeMillis() + Math.max(1, cfgI("exam.resume-hours", 24)) * 3_600_000L);
    }

    /** Trägt die eigenen Werte für die Expeditions-Bestenliste in die Statistik ein (auch für ältere Spielstände). */
    public void refreshRankStats(UUID uuid) {
        Database db = plugin.database();
        Map<String, Long> values = new HashMap<>();
        values.put("ex_level", (long) Math.max(0, nextLevel(uuid) - 1));
        values.put("ex_exams", db.getMetaLong("ex_exams_" + uuid, 0));
        values.put("ex_tdbest", (long) defenseBest(uuid));
        values.put("mine_depth", (long) mineBest(uuid));
        db.setStats(uuid, values);
    }
}
