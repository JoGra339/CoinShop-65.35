package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kollektionen: Wer ALLE Items einer Kollektion (z. B. alle Schallplatten) schon einmal besessen hat,
 * bekommt eine Belohnung - einmalig.
 *
 * "Besessen" heißt: das Item war in deinem Inventar oder deiner Endertruhe, oder Minecraft hat gezählt,
 * dass du es aufgesammelt oder hergestellt hast (Statistik). Du musst die Items also nicht behalten.
 *
 * Die Kollektionen stehen in der config.yml unter "collections".
 */
public final class CollectionManager {

    /** Ein Eintrag einer Kollektion: ein Item (key = Materialname) oder eine Kreatur (key = "ENTITY:NAME"). */
    public record Entry(String key, Material icon, EntityType entityType, String label, String translation) {
        /** Kurzform ohne eigenen Namen. */
        public Entry(String key, Material icon, EntityType entityType) {
            this(key, icon, entityType, null, null);
        }

        /** Mit eigenem (deutschen) Namen. */
        public Entry(String key, Material icon, EntityType entityType, String label) {
            this(key, icon, entityType, label, null);
        }
    }

    public record Collection(String id, String name, Material icon, long reward, List<Entry> entries) { }

    private final CoinShopPlugin plugin;
    private Map<String, Collection> collections = new LinkedHashMap<>();
    private Set<Material> tracked = new HashSet<>();

    public CollectionManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public java.util.Collection<Collection> all() {
        return collections.values();
    }

    public Collection get(String id) {
        return collections.get(id);
    }

    /**
     * Liest die Kollektionen aus der config.yml. Muster mit * (z. B. "MUSIC_DISC_*") werden aufgelöst;
     * Einträge mit "entity:" davor (z. B. "entity:ZOMBIE") sind Kreaturen.
     */
    public void reload() {
        Map<String, Collection> loaded = new LinkedHashMap<>();
        Set<Material> all = new HashSet<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("collections");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection c = section.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                Map<String, Entry> entries = new LinkedHashMap<>();
                for (String pattern : c.getStringList("items")) {
                    expand(pattern.trim(), entries);
                }
                if (entries.isEmpty()) {
                    plugin.getLogger().warning("Kollektion '" + id + "' hat keine gültigen Einträge und wird ignoriert.");
                    continue;
                }
                Material icon = Material.matchMaterial(c.getString("icon", ""));
                if (icon == null) {
                    icon = entries.values().iterator().next().icon();
                }
                loaded.put(id, new Collection(id, c.getString("name", id), icon,
                        Math.max(0, c.getLong("reward", 0)), new ArrayList<>(entries.values())));
                for (Entry e : entries.values()) {
                    if (e.entityType() == null) {
                        all.add(e.icon());
                    }
                }
            }
        }
        collections = loaded;
        tracked = all;
        boolean hasEnchants = false;
        for (Collection c : loaded.values()) {
            for (Entry e : c.entries()) {
                if (e.key().startsWith("ENCHANT:")) {
                    hasEnchants = true;
                }
            }
        }
        enchantEntries = hasEnchants;
        boolean hasPotions = false;
        for (Collection c : loaded.values()) {
            for (Entry e : c.entries()) {
                if (e.key().startsWith("POTION:")) {
                    hasPotions = true;
                }
            }
        }
        potionEntries = hasPotions;
    }

    /** Varianten von Tieren: Art -> (Variante -> deutscher Name). Die Namen der Varianten sind die Spielnamen. */
    private static final Map<String, Map<String, String>> VARIANTS = new LinkedHashMap<>();

    static {
        Map<String, String> wolf = new LinkedHashMap<>();
        wolf.put("pale", "Heller Wolf");
        wolf.put("spotted", "Gefleckter Wolf");
        wolf.put("snowy", "Schnee-Wolf");
        wolf.put("black", "Schwarzer Wolf");
        wolf.put("ashen", "Aschen-Wolf");
        wolf.put("rusty", "Rostroter Wolf");
        wolf.put("woods", "Wald-Wolf");
        wolf.put("chestnut", "Kastanien-Wolf");
        wolf.put("striped", "Gestreifter Wolf");
        VARIANTS.put("wolf", wolf);
        Map<String, String> cat = new LinkedHashMap<>();
        cat.put("tabby", "Getigerte Katze");
        cat.put("black", "Smoking-Katze (Schwarz-Weiß)");
        cat.put("red", "Rote Katze");
        cat.put("siamese", "Siamkatze");
        cat.put("british_shorthair", "Britisch Kurzhaar");
        cat.put("calico", "Kalikokatze");
        cat.put("persian", "Perserkatze");
        cat.put("ragdoll", "Ragdoll-Katze");
        cat.put("white", "Weiße Katze");
        cat.put("jellie", "Jellie-Katze");
        cat.put("all_black", "Komplett schwarze Katze");
        VARIANTS.put("cat", cat);
        Map<String, String> parrot = new LinkedHashMap<>();
        parrot.put("red", "Roter Papagei");
        parrot.put("blue", "Blauer Papagei");
        parrot.put("green", "Grüner Papagei");
        parrot.put("cyan", "Cyan-Papagei");
        parrot.put("gray", "Grauer Papagei");
        VARIANTS.put("parrot", parrot);
        Map<String, String> frog = new LinkedHashMap<>();
        frog.put("temperate", "Gemäßigter Frosch");
        frog.put("warm", "Warmer Frosch");
        frog.put("cold", "Kalter Frosch");
        VARIANTS.put("frog", frog);
        Map<String, String> rabbit = new LinkedHashMap<>();
        rabbit.put("brown", "Braunes Kaninchen");
        rabbit.put("white", "Weißes Kaninchen");
        rabbit.put("black", "Schwarzes Kaninchen");
        rabbit.put("black_and_white", "Scheckiges Kaninchen");
        rabbit.put("gold", "Goldenes Kaninchen");
        rabbit.put("salt", "Salz-und-Pfeffer-Kaninchen");
        VARIANTS.put("rabbit", rabbit);
    }

    public static String variantKey(String kind, String variant) {
        return "VARIANT:" + kind + ":" + variant;
    }

    /** Der Name einer Variante, egal ob sie ein Enum oder ein Registry-Eintrag ist. */
    private static String variantName(Object variant) {
        if (variant instanceof org.bukkit.Keyed keyed) {
            return keyed.getKey().getKey();
        }
        if (variant instanceof Enum<?> e) {
            return e.name().toLowerCase(java.util.Locale.ROOT);
        }
        return String.valueOf(variant).toLowerCase(java.util.Locale.ROOT);
    }

    /** Welche Variante hat diese Kreatur (Schlüssel für die Kollektion), oder null? */
    private static String variantKeyOf(org.bukkit.entity.Entity entity) {
        if (entity instanceof org.bukkit.entity.Wolf wolf) {
            return variantKey("wolf", variantName(wolf.getVariant()));
        }
        if (entity instanceof org.bukkit.entity.Cat cat) {
            return variantKey("cat", variantName(cat.getCatType()));
        }
        if (entity instanceof org.bukkit.entity.Parrot parrot) {
            return variantKey("parrot", variantName(parrot.getVariant()));
        }
        if (entity instanceof org.bukkit.entity.Frog frog) {
            return variantKey("frog", variantName(frog.getVariant()));
        }
        if (entity instanceof org.bukkit.entity.Rabbit rabbit) {
            return variantKey("rabbit", variantName(rabbit.getRabbitType()));
        }
        if (entity instanceof org.bukkit.entity.Axolotl axolotl) {
            return axolotlKey(axolotl.getVariant());
        }
        return null;
    }

    /** Gibt es mindestens eine Verzauberungs-Kollektion? (dann werden Inventare auf Verzauberungen geprüft) */
    private boolean enchantEntries = false;

    /** Gibt es Tränke-Einträge? (dann wird die Trankart im Inventar erkannt) */
    private boolean potionEntries = false;

    private static void expand(String pattern, Map<String, Entry> out) {
        if (pattern.isEmpty()) {
            return;
        }
        if (pattern.toLowerCase().startsWith("variants:")) {
            String kind = pattern.substring(9).trim().toLowerCase();
            Map<String, String> table = VARIANTS.get(kind);
            if (table != null) {
                Material egg = Material.matchMaterial(kind.toUpperCase() + "_SPAWN_EGG");
                for (Map.Entry<String, String> v : table.entrySet()) {
                    String key = variantKey(kind, v.getKey());
                    out.put(key, new Entry(key, egg == null ? Material.PAPER : egg, null, v.getValue()));
                }
            }
            return;
        }
        if (pattern.equalsIgnoreCase("enchantments:all")) {
            for (org.bukkit.enchantments.Enchantment e : org.bukkit.Registry.ENCHANTMENT) {
                String key = "ENCHANT:" + e.getKey().getKey();
                out.put(key, new Entry(key, Material.ENCHANTED_BOOK, null, null, e.translationKey()));
            }
            return;
        }
        if (pattern.toLowerCase().startsWith("entity:")) {
            try {
                EntityType type = EntityType.valueOf(pattern.substring(7).trim().toUpperCase());
                Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
                out.put(entityKey(type), new Entry(entityKey(type), egg == null ? Material.PAPER : egg, type));
            } catch (IllegalArgumentException ignored) {
                // diese Kreatur gibt es in dieser Version nicht
            }
            return;
        }
        if (pattern.equalsIgnoreCase("potions:*")) {
            // Alle Tränke (jede Trankart zählt, ob getrunken, Wurf- oder Verweiltrank: wenn man so einen Trank im Inventar hatte)
            for (org.bukkit.potion.PotionType type : org.bukkit.Registry.POTION) {
                String id = type.getKey().getKey();
                out.put(potionKey(id), new Entry(potionKey(id), Material.POTION, null, "Trank: " + potionName(id)));
            }
            return;
        }
        if (pattern.equalsIgnoreCase("horns:*")) {
            // Alle Ziegenhörner (jeder Ruf zählt, wenn man so ein Horn im Inventar hatte)
            for (org.bukkit.MusicInstrument instrument : org.bukkit.Registry.INSTRUMENT) {
                String id = instrument.getKey().getKey();
                out.put(hornKey(id), new Entry(hornKey(id), Material.GOAT_HORN, null, "Ziegenhorn: " + hornName(id)));
            }
            return;
        }
        if (pattern.equalsIgnoreCase("axolotl:*")) {
            // Alle Axolotl-Farben (zählt, wenn man einen Axolotl-Eimer der Farbe hatte oder so einen Axolotl gesehen/getötet hat)
            for (org.bukkit.entity.Axolotl.Variant v : org.bukkit.entity.Axolotl.Variant.values()) {
                String name = switch (v) {
                    case LUCY -> "Rosa Axolotl (Lucy)";
                    case WILD -> "Brauner Axolotl";
                    case GOLD -> "Goldener Axolotl";
                    case CYAN -> "Cyan-Axolotl";
                    case BLUE -> "Blauer Axolotl (selten)";
                };
                out.put(axolotlKey(v), new Entry(axolotlKey(v), Material.AXOLOTL_BUCKET, null, name));
            }
            return;
        }
        if (!pattern.contains("*")) {
            Material m = Material.matchMaterial(pattern);
            if (m != null && m.isItem() && !m.isLegacy()) {
                out.put(m.name(), new Entry(m.name(), m, null));
            }
            return;
        }
        String regex = java.util.regex.Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.isItem() && m.name().matches(regex)) {
                out.put(m.name(), new Entry(m.name(), m, null));
            }
        }
    }

    public static String potionKey(String id) {
        return "POTION:" + id;
    }

    private static String potionName(String id) {
        String base = id;
        String suffix = "";
        if (id.startsWith("long_")) {
            base = id.substring(5);
            suffix = " (lang)";
        } else if (id.startsWith("strong_")) {
            base = id.substring(7);
            suffix = " (stark)";
        }
        String name = switch (base) {
            case "water" -> "Wasserflasche";
            case "mundane" -> "Gewöhnlicher Trank";
            case "thick" -> "Dickflüssiger Trank";
            case "awkward" -> "Seltsamer Trank";
            case "night_vision" -> "Nachtsicht";
            case "invisibility" -> "Unsichtbarkeit";
            case "leaping" -> "Sprungkraft";
            case "fire_resistance" -> "Feuerresistenz";
            case "swiftness" -> "Schnelligkeit";
            case "slowness" -> "Langsamkeit";
            case "water_breathing" -> "Wasseratmung";
            case "healing" -> "Heilung";
            case "harming" -> "Schaden";
            case "poison" -> "Gift";
            case "regeneration" -> "Regeneration";
            case "strength" -> "Stärke";
            case "weakness" -> "Schwäche";
            case "luck" -> "Glück";
            case "turtle_master" -> "Schildkrötenmeister";
            case "slow_falling" -> "Langsames Fallen";
            case "wind_charged" -> "Windgeladen";
            case "weaving" -> "Webend";
            case "oozing" -> "Triefend";
            case "infested" -> "Befallen";
            default -> base.replace('_', ' ');
        };
        return name + suffix;
    }

    public static String hornKey(String id) {
        return "HORN:" + id;
    }

    private static String hornName(String id) {
        return switch (id) {
            case "ponder_goat_horn" -> "Grübeln";
            case "sing_goat_horn" -> "Singen";
            case "seek_goat_horn" -> "Suchen";
            case "feel_goat_horn" -> "Fühlen";
            case "admire_goat_horn" -> "Bewundern";
            case "call_goat_horn" -> "Rufen";
            case "yearn_goat_horn" -> "Sehnen";
            case "dream_goat_horn" -> "Träumen";
            default -> id.replace("_goat_horn", "").replace('_', ' ');
        };
    }

    public static String axolotlKey(org.bukkit.entity.Axolotl.Variant variant) {
        return "AXOLOTL:" + variant.name();
    }

    public static String entityKey(EntityType type) {
        return "ENTITY:" + type.name();
    }

    // ------------------------------------------------------------------
    //  Einsammeln prüfen
    // ------------------------------------------------------------------

    /** Prüft, welche Items der Spieler hat/hatte, speichert neue und zahlt fertige Kollektionen aus. */
    public void scan(Player player) {
        if (collections.isEmpty() || plugin.timeline().previewFor(player) || plugin.expeditions().isIn(player.getUniqueId())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Database db = plugin.database();
        try {
            Set<String> known = db.getCollected(uuid);
            Set<String> fresh = new HashSet<>();

            // 1. Was liegt gerade im Inventar / in der Endertruhe?
            for (ItemStack stack : player.getInventory().getContents()) {
                note(stack, known, fresh);
            }
            for (ItemStack stack : player.getEnderChest().getContents()) {
                note(stack, known, fresh);
            }
            note(player.getItemOnCursor(), known, fresh);

            // 2. Was hat Minecraft als aufgesammelt oder hergestellt gezählt?
            for (Material m : tracked) {
                if (known.contains(m.name()) || fresh.contains(m.name())) {
                    continue;
                }
                if (stat(player, Statistic.PICKUP, m) > 0 || stat(player, Statistic.CRAFT_ITEM, m) > 0) {
                    fresh.add(m.name());
                }
            }
            if (!fresh.isEmpty()) {
                db.addCollected(uuid, fresh);
                known.addAll(fresh);
            }

            // 3. Ist eine Kollektion fertig?
            checkCompletion(player, known);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Kollektionen von " + player.getName() + " konnten nicht geprüft werden: " + e);
        }
    }

    /** Zahlt jede Kollektion aus, deren Einträge alle gesammelt sind (und die noch nicht belohnt wurde). */
    private void checkCompletion(Player player, Set<String> known) {
        Set<String> done = plugin.database().getDoneCollections(player.getUniqueId());
        for (Collection c : collections.values()) {
            if (done.contains(c.id())) {
                continue;
            }
            boolean complete = true;
            for (Entry e : c.entries()) {
                if (!known.contains(e.key())) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                complete(player, c);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Kreaturen: getötet oder durch das Fernglas angesehen
    // ------------------------------------------------------------------

    /** Merkt sich pro Spieler, welche Kreaturen er schon eingetragen hat (spart Datenbank-Abfragen). */
    private final Map<UUID, Set<String>> seenCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Der Spieler hat diese Kreatur getötet oder durch ein Fernglas angesehen. */
    public void noteEntity(Player player, EntityType type) {
        if (collections.isEmpty() || type == EntityType.PLAYER || plugin.timeline().previewFor(player)
                || plugin.expeditions().isIn(player.getUniqueId())) {
            return;
        }
        String key = entityKey(type);
        UUID uuid = player.getUniqueId();
        try {
            Set<String> seen = seenCache.computeIfAbsent(uuid, u -> {
                Set<String> set = new HashSet<>();
                for (String k : plugin.database().getCollected(u)) {
                    if (k.startsWith("ENTITY:")) {
                        set.add(k);
                    }
                }
                return set;
            });
            if (!seen.add(key)) {
                return; // schon eingetragen
            }
            plugin.database().addCollected(uuid, List.of(key));
            checkCompletion(player, plugin.database().getCollected(uuid));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Kreatur konnte nicht eingetragen werden: " + e);
        }
    }

    /** Wie noteEntity(Typ), trägt bei Wolf, Katze, Papagei, Frosch, Kaninchen und Axolotl zusätzlich die Variante ein. */
    public void noteEntity(Player player, org.bukkit.entity.Entity entity) {
        if (plugin.timeline().previewFor(player) || plugin.expeditions().isIn(player.getUniqueId())) {
            return;
        }
        noteEntity(player, entity.getType());
        if (collections.isEmpty()) {
            return;
        }
        try {
            String key = variantKeyOf(entity);
            if (key == null) {
                return;
            }
            Set<String> known = plugin.database().getCollected(player.getUniqueId());
            if (!known.contains(key)) {
                plugin.database().addCollected(player.getUniqueId(), List.of(key));
                known.add(key);
                checkCompletion(player, known);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Variante konnte nicht eingetragen werden: " + e);
        }
    }

    public void forget(UUID uuid) {
        seenCache.remove(uuid);
    }

    private void note(ItemStack stack, Set<String> known, Set<String> fresh) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        // Verzauberungen (auf Items oder in Büchern)
        if (enchantEntries) {
            for (org.bukkit.enchantments.Enchantment e : stack.getEnchantments().keySet()) {
                addEnchant(e, known, fresh);
            }
            if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta book) {
                for (org.bukkit.enchantments.Enchantment e : book.getStoredEnchants().keySet()) {
                    addEnchant(e, known, fresh);
                }
            }
        }
        // Tränke: die Trankart steckt im Item (trinkbar, Wurf- oder Verweiltrank, Pfeile zählen nicht)
        if (potionEntries && (stack.getType() == Material.POTION || stack.getType() == Material.SPLASH_POTION
                || stack.getType() == Material.LINGERING_POTION)
                && stack.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion
                && potion.getBasePotionType() != null) {
            String key = potionKey(potion.getBasePotionType().getKey().getKey());
            if (!known.contains(key)) {
                fresh.add(key);
            }
        }
        if (!tracked.contains(stack.getType())) {
            return;
        }
        // Axolotl-Eimer: die Farbe steckt im Item
        if (stack.getType() == Material.AXOLOTL_BUCKET
                && stack.getItemMeta() instanceof org.bukkit.inventory.meta.AxolotlBucketMeta bucket
                && bucket.hasVariant()) {
            String key = axolotlKey(bucket.getVariant());
            if (!known.contains(key)) {
                fresh.add(key);
            }
        }
        // Ziegenhörner: der Ruf steckt im Item
        if (stack.getType() == Material.GOAT_HORN
                && stack.getItemMeta() instanceof org.bukkit.inventory.meta.MusicInstrumentMeta horn
                && horn.getInstrument() != null) {
            String key = hornKey(horn.getInstrument().getKey().getKey());
            if (!known.contains(key)) {
                fresh.add(key);
            }
        }
        String name = stack.getType().name();
        if (!known.contains(name)) {
            fresh.add(name);
        }
    }

    private static void addEnchant(org.bukkit.enchantments.Enchantment e, Set<String> known, Set<String> fresh) {
        String key = "ENCHANT:" + e.getKey().getKey();
        if (!known.contains(key)) {
            fresh.add(key);
        }
    }

    private static int stat(Player player, Statistic statistic, Material material) {
        try {
            return player.getStatistic(statistic, material);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private void complete(Player player, Collection c) {
        Database db = plugin.database();
        long reward;
        db.begin();
        try {
            if (!db.markCollectionDone(player.getUniqueId(), c.id())) {
                db.rollback();
                return;
            }
            reward = plugin.licenses().boost(player.getUniqueId(), "sammler", c.reward());
            if (reward > 0) {
                db.addCoins(player.getUniqueId(), reward);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().warning("Kollektions-Belohnung fehlgeschlagen: " + e);
            return;
        }
        db.logTx(player.getUniqueId(), player.getName(), "kollektion", reward, c.id());
        Messages msg = plugin.messages();
        msg.send(player, "collection-complete", "name", c.name(), "coins", reward);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                msg.send(online, "collection-broadcast", "player", player.getName(), "name", c.name());
            }
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    public void scanAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scan(player);
        }
    }

    /** Wie viele Items der Kollektion hat der Spieler schon? */
    public int progress(Collection c, Set<String> known) {
        int n = 0;
        for (Entry e : c.entries()) {
            if (known.contains(e.key())) {
                n++;
            }
        }
        return n;
    }
}
