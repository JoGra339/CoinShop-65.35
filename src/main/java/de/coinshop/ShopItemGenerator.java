package de.coinshop;

import de.coinshop.Database.Offer;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.TropicalFish;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Lost die 10 Angebote des Server-Shops aus:
 *  - normale Items (jedes Item, das es im Survival gibt - Sperrliste und Preise stehen in der config.yml)
 *  - verzauberte Rüstungsteile, Werkzeuge und Waffen
 *  - Verzauberungsbücher
 */
public final class ShopItemGenerator {

    // Verzauberungen, die als Buch im Shop auftauchen können (Namen wie im Spiel, ohne "minecraft:").
    // Namen, die es in deiner Version nicht gibt, werden automatisch übersprungen.
    private static final List<String> BOOK_ENCHANTS = List.of(
            "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection",
            "respiration", "aqua_affinity", "thorns", "depth_strider", "frost_walker", "soul_speed",
            "swift_sneak", "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping_edge", "efficiency", "silk_touch", "unbreaking", "fortune", "power",
            "punch", "flame", "infinity", "luck_of_the_sea", "lure", "loyalty", "impaling", "riptide",
            "channeling", "multishot", "quick_charge", "piercing", "mending", "density", "breach",
            "wind_burst");

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    // Werden bei jedem Auslosen neu aus der config.yml gelesen
    private final Map<String, Long> priceRules = new LinkedHashMap<>();
    private List<String> excludeRules = new ArrayList<>();
    // Items, die immer nur einzeln angeboten werden (z. B. Karten)
    private List<String> singleRules = new ArrayList<>();
    // Wie stark Verzauberungen je nach Material zählen (Leder billig, Netherit voll)
    private final Map<String, Double> materialFactors = new LinkedHashMap<>();
    private long defaultPrice = 3;

    public ShopItemGenerator(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Hauptmethode
    // ------------------------------------------------------------------

    public List<Offer> generate() {
        return generate(plugin.getConfig().getInt("server-shop.offers", 10));
    }

    /**
     * Lost EIN neues Angebot für einen Platz aus, das sich von den übrigen Angeboten unterscheidet.
     * Wird benutzt, wenn ein Angebot ausverkauft ist.
     */
    public Offer generateOne(int slot, List<Offer> others) {
        for (int tries = 0; tries < 25; tries++) {
            List<Offer> one = generate(1);
            if (one.isEmpty()) {
                continue;
            }
            Offer candidate = one.get(0);
            boolean duplicate = false;
            for (Offer other : others) {
                if (other.slot() != slot && other.item().isSimilar(candidate.item())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                return new Offer(slot, candidate.item(), candidate.price());
            }
        }
        return null;
    }

    private List<Offer> generate(int count) {
        FileConfiguration cfg = plugin.getConfig();
        loadRules(cfg);

        // Jede der vier Spezialgruppen hat pro Angebotsplatz dieselbe Chance (Standard 10 %).
        double special = Math.max(0.0, Math.min(0.25, cfg.getDouble("server-shop.special-chance", 0.10)));

        // Anteil der unverzauberten Stücke in den Gruppen Rüstung und Werkzeuge/Waffen
        double plainShare = Math.max(0.0, Math.min(1.0, cfg.getDouble("server-shop.unenchanted-share", 0.4)));

        List<Material> armorPieces = new ArrayList<>();
        List<Material> toolPieces = new ArrayList<>();   // Werkzeuge und Waffen
        List<Material> others = new ArrayList<>();       // alles andere: jedes Item gleich wahrscheinlich
        for (Material material : Material.values()) {
            if (!isAllowed(material)) {
                continue;
            }
            String name = material.name();
            if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
                armorPieces.add(material);
            } else if (isTool(name)) {
                toolPieces.add(material);
            } else if (!name.endsWith("SHULKER_BOX") && material != Material.ENCHANTED_BOOK) {
                others.add(material);
            }
        }
        // Variations-Items zählen wie jedes andere Item (je eine Stimme), auch wenn sie nicht in der Liste stehen
        for (Material variant : VARIANT_MATERIALS) {
            if (isAllowed(variant) && !others.contains(variant) && !armorPieces.contains(variant)) {
                others.add(variant);
            }
        }

        Set<String> used = new HashSet<>();
        List<Offer> offers = new ArrayList<>();
        int attempts = 0;
        while (offers.size() < count && attempts++ < 500) {
            double roll = random.nextDouble();
            Offer offer;
            int slot = offers.size();
            if (roll < special && !armorPieces.isEmpty()) {
                offer = gearOffer(slot, armorPieces, used, plainShare);
            } else if (roll < special * 2 && !toolPieces.isEmpty()) {
                offer = gearOffer(slot, toolPieces, used, plainShare);
            } else if (roll < special * 3) {
                offer = shulkerOffer(slot, used);
            } else if (roll < special * 4) {
                offer = enchantedBook(slot, used);
            } else {
                offer = uniformItem(slot, others, armorPieces, used);
            }
            if (offer != null) {
                offers.add(offer);
            }
        }
        return offers;
    }

    private static final List<Material> VARIANT_MATERIALS = List.of(Material.POTION, Material.SPLASH_POTION,
            Material.LINGERING_POTION, Material.TIPPED_ARROW, Material.GOAT_HORN, Material.SUSPICIOUS_STEW,
            Material.OMINOUS_BOTTLE, Material.FIREWORK_ROCKET, Material.AXOLOTL_BUCKET,
            Material.TROPICAL_FISH_BUCKET, Material.LEATHER_HORSE_ARMOR);

    /** Wählt ein Item gleichverteilt; Variations-Items (Tränke, Ziegenhörner ...) werden passend erzeugt. */
    private Offer uniformItem(int slot, List<Material> others, List<Material> armorPieces, Set<String> used) {
        if (others.isEmpty()) {
            return null;
        }
        for (int tries = 0; tries < 60; tries++) {
            Material material = others.get(random.nextInt(others.size()));
            String name = material.name();
            Offer offer = null;
            try {
                if (name.endsWith("ARMOR_TRIM_SMITHING_TEMPLATE")) {
                    offer = trimOffer(slot, used, armorPieces);
                } else {
                    switch (material) {
                        case POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW -> offer = potionOffer(slot, used, material);
                        case GOAT_HORN -> offer = hornOffer(slot, used);
                        case SUSPICIOUS_STEW -> offer = stewOffer(slot, used);
                        case OMINOUS_BOTTLE -> offer = ominousOffer(slot, used);
                        case FIREWORK_ROCKET -> offer = fireworkOffer(slot, used);
                        case AXOLOTL_BUCKET -> offer = bucketOffer(slot, used, true);
                        case TROPICAL_FISH_BUCKET -> offer = bucketOffer(slot, used, false);
                        case LEATHER_HORSE_ARMOR -> offer = dyedOffer(slot, used);
                        default -> offer = plainOffer(slot, material, used);
                    }
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Item konnte nicht erzeugt werden: " + e);
            }
            if (offer != null) {
                return offer;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Regeln aus der config.yml
    // ------------------------------------------------------------------

    private void loadRules(FileConfiguration cfg) {
        priceRules.clear();
        ConfigurationSection prices = cfg.getConfigurationSection("server-shop.prices");
        if (prices != null) {
            for (String key : prices.getKeys(false)) {
                priceRules.put(key.toUpperCase(Locale.ROOT), Math.max(1, prices.getLong(key)));
            }
        }
        excludeRules = new ArrayList<>();
        for (String rule : cfg.getStringList("server-shop.exclude")) {
            excludeRules.add(rule.toUpperCase(Locale.ROOT));
        }
        defaultPrice = Math.max(1, cfg.getLong("server-shop.default-price", 3));

        singleRules = new ArrayList<>();
        for (String rule : cfg.getStringList("server-shop.single-only")) {
            singleRules.add(rule.toUpperCase(Locale.ROOT));
        }
        materialFactors.clear();
        ConfigurationSection factors = cfg.getConfigurationSection("server-shop.enchant-material-factor");
        if (factors != null) {
            for (String key : factors.getKeys(false)) {
                materialFactors.put(key.toUpperCase(Locale.ROOT), Math.max(0.0, factors.getDouble(key)));
            }
        }
    }

    /** Wird dieses Item immer nur einzeln angeboten? */
    private boolean isSingleOnly(Material material) {
        for (String rule : singleRules) {
            if (matches(rule, material.name())) {
                return true;
            }
        }
        return false;
    }

    /** Faktor für Verzauberungs-Preise je nach Material (erster passender Eintrag, sonst 1.0). */
    private double materialFactor(Material material) {
        for (Map.Entry<String, Double> rule : materialFactors.entrySet()) {
            if (matches(rule.getKey(), material.name())) {
                return rule.getValue();
            }
        }
        return 1.0;
    }

    /** Passt der Name zum Muster? Mit * am Anfang und/oder Ende sind Gruppen möglich. */
    static boolean matches(String pattern, String name) {
        boolean star = pattern.contains("*");
        if (!star) {
            return pattern.equals(name);
        }
        String core = pattern.replace("*", "");
        boolean start = pattern.startsWith("*");
        boolean end = pattern.endsWith("*");
        if (start && end) {
            return name.contains(core);
        }
        if (start) {
            return name.endsWith(core);
        }
        return name.startsWith(core);
    }

    private boolean isAllowed(Material material) {
        if (material.name().startsWith("LEGACY_") || !material.isItem() || material.isAir()) {
            return false;
        }
        for (String rule : excludeRules) {
            if (matches(rule, material.name())) {
                return false;
            }
        }
        return true;
    }

    /** Preis eines einzelnen Items (erster passender Eintrag in der config). */
    private long unitPrice(Material material) {
        return plugin.priceBook().price(material);
    }

    // ------------------------------------------------------------------
    //  Normale Items
    // ------------------------------------------------------------------

    private Offer plainOffer(int slot, Material material, Set<String> used) {
        if (!used.add("item:" + material.name())) {
            return null;
        }
        long unit = unitPrice(material);
        int[] range = amountRange(unit);
        int amount = range[0] + random.nextInt(range[1] - range[0] + 1);
        amount = Math.max(1, Math.min(amount, material.getMaxStackSize()));
        if (isSingleOnly(material)) {
            amount = 1;   // z. B. Karten gibt es nur einzeln
        }
        return new Offer(slot, new ItemStack(material, amount), unit * amount);
    }

    /** Teure Items gibt es nur einzeln, billige Items in großen Mengen. */
    private static int[] amountRange(long unitPrice) {
        if (unitPrice >= 1000) {
            return new int[]{1, 1};
        }
        if (unitPrice >= 300) {
            return new int[]{1, 2};
        }
        if (unitPrice >= 100) {
            return new int[]{1, 4};
        }
        if (unitPrice >= 25) {
            return new int[]{2, 8};
        }
        if (unitPrice >= 8) {
            return new int[]{4, 16};
        }
        if (unitPrice >= 3) {
            return new int[]{8, 32};
        }
        return new int[]{16, 64};
    }

    // ------------------------------------------------------------------
    //  Verzauberungen
    // ------------------------------------------------------------------

    private Enchantment lookup(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    /** Kosten PRO STUFE einer Verzauberung (aus der config). */
    private long costPerLevel(String key) {
        FileConfiguration cfg = plugin.getConfig();
        long fallback = cfg.getLong("server-shop.enchant-cost-per-level.default", 100);
        return Math.max(1, cfg.getLong("server-shop.enchant-cost-per-level." + key, fallback));
    }

    /**
     * Preis einer Verzauberung in einer bestimmten Stufe. Hohe Stufen werden überproportional teurer:
     * Preis = Kosten pro Stufe x Stufe hoch "level-exponent" (Standard 1.4; Stufe 1 = 1x, Stufe 3 = ca. 4,7x,
     * Stufe 5 = ca. 9,5x).
     */
    private long enchantCost(String key, int level) {
        double exponent = Math.max(1.0, plugin.getConfig().getDouble("server-shop.enchant-level-exponent", 1.4));
        return Math.round(costPerLevel(key) * Math.pow(level, exponent));
    }

    private boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    /** Fügt eine Verzauberung mit zufälliger Stufe zur Liste hinzu (falls es sie gibt). */
    private void addEnchant(Map<Enchantment, Integer> target, Map<String, Integer> levelsByKey, String key) {
        Enchantment enchantment = lookup(key);
        if (enchantment == null) {
            return;
        }
        int level = 1 + random.nextInt(Math.max(1, enchantment.getMaxLevel()));
        target.put(enchantment, level);
        levelsByKey.put(key, level);
    }

    /** Ist das Item ein Werkzeug oder eine Waffe, die verzaubert angeboten werden darf? */
    private static boolean isTool(String name) {
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT")
                || name.equals("MACE") || name.equals("FISHING_ROD");
    }

    /** Wählt zufällig EINE der genannten Verzauberungen (sie schließen sich im Spiel gegenseitig aus). */
    private void addOneOf(Map<Enchantment, Integer> enchants, Map<String, Integer> levels, String... keys) {
        addEnchant(enchants, levels, keys[random.nextInt(keys.length)]);
    }

    /** Würfelt passende Verzauberungen für Rüstung. */
    private void rollArmorEnchants(String name, Map<Enchantment, Integer> enchants, Map<String, Integer> levels) {
        // Immer genau ein Schutz-Typ
        addOneOf(enchants, levels, "protection", "fire_protection", "blast_protection", "projectile_protection");
        if (chance(0.7)) {
            addEnchant(enchants, levels, "unbreaking");
        }
        if (chance(0.45)) {
            addEnchant(enchants, levels, "mending");
        }
        if (chance(0.3)) {
            addEnchant(enchants, levels, "thorns");
        }
        if (name.endsWith("_HELMET")) {
            if (chance(0.4)) {
                addEnchant(enchants, levels, "respiration");
            }
            if (chance(0.4)) {
                addEnchant(enchants, levels, "aqua_affinity");
            }
        } else if (name.endsWith("_LEGGINGS")) {
            if (chance(0.4)) {
                addEnchant(enchants, levels, "swift_sneak");
            }
        } else if (name.endsWith("_BOOTS")) {
            if (chance(0.6)) {
                addEnchant(enchants, levels, "feather_falling");
            }
            // Tiefseeläufer und Frostläufer schließen sich aus
            if (chance(0.3)) {
                addEnchant(enchants, levels, "depth_strider");
            } else if (chance(0.2)) {
                addEnchant(enchants, levels, "frost_walker");
            }
            if (chance(0.2)) {
                addEnchant(enchants, levels, "soul_speed");
            }
        }
    }

    /** Würfelt passende Verzauberungen für Werkzeuge und Waffen. */
    private void rollToolEnchants(String name, Map<Enchantment, Integer> enchants, Map<String, Integer> levels) {
        boolean mending = chance(0.4);
        if (name.endsWith("_SWORD")) {
            addOneOf(enchants, levels, "sharpness", "smite", "bane_of_arthropods");
            if (chance(0.4)) {
                addEnchant(enchants, levels, "knockback");
            }
            if (chance(0.4)) {
                addEnchant(enchants, levels, "fire_aspect");
            }
            if (chance(0.5)) {
                addEnchant(enchants, levels, "looting");
            }
            if (chance(0.4)) {
                addEnchant(enchants, levels, "sweeping_edge");
            }
        } else if (name.endsWith("_AXE") && !name.endsWith("_PICKAXE")) {
            addEnchant(enchants, levels, "efficiency");
            if (chance(0.5)) {
                addOneOf(enchants, levels, "sharpness", "smite", "bane_of_arthropods");
            }
            // Behutsamkeit und Glück schließen sich aus
            if (chance(0.3)) {
                addOneOf(enchants, levels, "silk_touch", "fortune");
            }
        } else if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            addEnchant(enchants, levels, "efficiency");
            if (chance(0.5)) {
                addOneOf(enchants, levels, "silk_touch", "fortune");
            }
        } else if (name.equals("BOW")) {
            addEnchant(enchants, levels, "power");
            if (chance(0.4)) {
                addEnchant(enchants, levels, "punch");
            }
            if (chance(0.4)) {
                addEnchant(enchants, levels, "flame");
            }
            // Unendlichkeit und Reparatur schließen sich aus
            if (chance(0.4)) {
                addEnchant(enchants, levels, "infinity");
                mending = false;
            }
        } else if (name.equals("CROSSBOW")) {
            addOneOf(enchants, levels, "multishot", "piercing");
            if (chance(0.7)) {
                addEnchant(enchants, levels, "quick_charge");
            }
        } else if (name.equals("TRIDENT")) {
            addEnchant(enchants, levels, "impaling");
            // Treue und Sturmlauf (Riptide) schließen sich aus; Blitzschlag geht nicht mit Sturmlauf
            if (chance(0.4)) {
                addEnchant(enchants, levels, "riptide");
            } else {
                if (chance(0.6)) {
                    addEnchant(enchants, levels, "loyalty");
                }
                if (chance(0.4)) {
                    addEnchant(enchants, levels, "channeling");
                }
            }
        } else if (name.equals("MACE")) {
            addOneOf(enchants, levels, "density", "breach");
            if (chance(0.5)) {
                addEnchant(enchants, levels, "wind_burst");
            }
            if (chance(0.3)) {
                addEnchant(enchants, levels, "fire_aspect");
            }
        } else if (name.equals("FISHING_ROD")) {
            addEnchant(enchants, levels, "luck_of_the_sea");
            if (chance(0.6)) {
                addEnchant(enchants, levels, "lure");
            }
        }
        if (chance(0.7)) {
            addEnchant(enchants, levels, "unbreaking");
        }
        if (mending) {
            addEnchant(enchants, levels, "mending");
        }
    }

    /**
     * Ein verzaubertes Rüstungsteil, Werkzeug oder eine Waffe.
     * Preis = Preis des Items + (Summe der Verzauberungen x Material-Faktor aus der config).
     * Dadurch ist eine verzauberte Lederrüstung viel billiger als eine verzauberte Diamantrüstung.
     */
    /** Ein Rüstungsteil oder Werkzeug: verzaubert, oder (mit der Chance plainShare) unverzaubert. */
    private Offer gearOffer(int slot, List<Material> pieces, Set<String> used, double plainShare) {
        if (random.nextDouble() < plainShare) {
            for (int tries = 0; tries < 20; tries++) {
                Offer plain = plainOffer(slot, pieces.get(random.nextInt(pieces.size())), used);
                if (plain != null) {
                    return plain;
                }
            }
        }
        return enchantedGear(slot, pieces, used);
    }

    private Offer enchantedGear(int slot, List<Material> pieces, Set<String> used) {
        for (int tries = 0; tries < 30; tries++) {
            Material piece = pieces.get(random.nextInt(pieces.size()));
            String name = piece.name();

            Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
            Map<String, Integer> levels = new LinkedHashMap<>();
            boolean armor = name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
            if (armor) {
                rollArmorEnchants(name, enchants, levels);
            } else {
                rollToolEnchants(name, enchants, levels);
            }
            if (enchants.isEmpty()) {
                continue;
            }

            String signature = "gear:" + name + levels;
            if (!used.add(signature)) {
                continue;
            }

            ItemStack item = new ItemStack(piece);
            item.editMeta(meta -> {
                for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                    meta.addEnchant(e.getKey(), e.getValue(), true);
                }
            });

            long enchantTotal = 0;
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
                enchantTotal += enchantCost(e.getKey(), e.getValue());
            }
            long price = unitPrice(piece) + Math.round(enchantTotal * materialFactor(piece));
            return new Offer(slot, item, Math.max(1, price));
        }
        return null;
    }

    private Offer enchantedBook(int slot, Set<String> used) {
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            all.add(e);
        }
        if (all.isEmpty()) {
            return null;
        }
        for (int tries = 0; tries < 30; tries++) {
            Enchantment enchantment = all.get(random.nextInt(all.size()));
            String key = enchantment.getKey().getKey();
            int level = 1 + random.nextInt(Math.max(1, enchantment.getMaxLevel()));
            if (!used.add("book:" + key + ":" + level)) {
                continue;
            }

            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, level, true));
            long price = Math.max(250, enchantCost(key, level));
            return new Offer(slot, book, price);
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Item-Variationen: Tränke, Pfeile, Ziegenhörner, Eintopf, Feuerwerk, Eimer, Rüstungsbesätze ...
    // ------------------------------------------------------------------

    private long variantPrice(String key, long fallback) {
        return Math.max(1, plugin.getConfig().getLong("server-shop.variant-prices." + key, fallback));
    }


    /** Alle Trank-Arten (auch stark/lang), als Trank, Wurftrank, Verweiltrank und Pfeil. */
    private Offer potionOffer(int slot, Set<String> used, Material form) {
        List<PotionType> types = new ArrayList<>();
        for (PotionType t : Registry.POTION) {
            types.add(t);
        }
        if (types.isEmpty()) {
            return null;
        }
        PotionType type = types.get(random.nextInt(types.size()));
        String signature = "potion:" + form.name() + ":" + type.getKey().getKey();
        if (!used.add(signature)) {
            return null;
        }
        String key = type.getKey().getKey();
        double factor = 1.0;
        if (key.startsWith("strong_")) {
            factor = 2.2;
            key = key.substring(7);
        } else if (key.startsWith("long_")) {
            factor = 1.6;
            key = key.substring(5);
        }
        long base = Math.max(1, plugin.getConfig().getLong("server-shop.potion-base." + key,
                plugin.getConfig().getLong("server-shop.potion-base.default", 250)));
        double formFactor = switch (form) {
            case SPLASH_POTION -> 1.4;
            case LINGERING_POTION -> 2.2;
            case TIPPED_ARROW -> 0.3;
            default -> 1.0;
        };
        long unit = Math.max(1, Math.round(base * factor * formFactor));
        int amount = form == Material.TIPPED_ARROW ? 8 : 1;
        ItemStack item = new ItemStack(form, amount);
        item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return new Offer(slot, item, unit * amount);
    }

    private Offer hornOffer(int slot, Set<String> used) {
        List<MusicInstrument> all = new ArrayList<>();
        for (MusicInstrument i : Registry.INSTRUMENT) {
            all.add(i);
        }
        if (all.isEmpty()) {
            return null;
        }
        MusicInstrument instrument = all.get(random.nextInt(all.size()));
        if (!used.add("horn:" + instrument.getKey())) {
            return null;
        }
        ItemStack item = new ItemStack(Material.GOAT_HORN);
        item.editMeta(MusicInstrumentMeta.class, meta -> meta.setInstrument(instrument));
        return new Offer(slot, item, variantPrice("goat_horn", 1500));
    }

    private Offer stewOffer(int slot, Set<String> used) {
        String[][] effects = {{"night_vision", "100"}, {"jump_boost", "120"}, {"weakness", "180"}, {"blindness", "160"},
                {"poison", "240"}, {"saturation", "7"}, {"fire_resistance", "80"}, {"regeneration", "160"}};
        String[] pick = effects[random.nextInt(effects.length)];
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(pick[0]));
        if (type == null || !used.add("stew:" + pick[0])) {
            return null;
        }
        ItemStack item = new ItemStack(Material.SUSPICIOUS_STEW);
        item.editMeta(SuspiciousStewMeta.class,
                meta -> meta.addCustomEffect(new PotionEffect(type, Integer.parseInt(pick[1]), 0), true));
        return new Offer(slot, item, variantPrice("suspicious_stew", 300));
    }

    private Offer ominousOffer(int slot, Set<String> used) {
        int amplifier = random.nextInt(5);
        if (!used.add("ominous:" + amplifier)) {
            return null;
        }
        ItemStack item = new ItemStack(Material.OMINOUS_BOTTLE);
        item.editMeta(OminousBottleMeta.class, meta -> meta.setAmplifier(amplifier));
        return new Offer(slot, item, variantPrice("ominous_bottle", 600) + 300L * amplifier);
    }

    private Offer fireworkOffer(int slot, Set<String> used) {
        int power = 1 + random.nextInt(3);
        if (!used.add("rocket:" + power)) {
            return null;
        }
        int amount = 4 + random.nextInt(13);
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET, amount);
        item.editMeta(FireworkMeta.class, meta -> meta.setPower(power));
        return new Offer(slot, item, (variantPrice("firework_rocket", 30) + 30L * power) * amount);
    }

    /** Axolotl-Eimer in allen Farben und Eimer mit zufälligen Tropenfischen. */
    private Offer bucketOffer(int slot, Set<String> used, boolean axolotl) {
        if (axolotl) {
            Axolotl.Variant[] all = Axolotl.Variant.values();
            Axolotl.Variant variant = all[random.nextInt(all.length)];
            if (!used.add("axolotl:" + variant.name())) {
                return null;
            }
            ItemStack item = new ItemStack(Material.AXOLOTL_BUCKET);
            item.editMeta(AxolotlBucketMeta.class, meta -> meta.setVariant(variant));
            long price = variant == Axolotl.Variant.BLUE ? variantPrice("axolotl_blue", 1500) : variantPrice("axolotl", 300);
            return new Offer(slot, item, price);
        }
        TropicalFish.Pattern[] patterns = TropicalFish.Pattern.values();
        DyeColor[] colors = DyeColor.values();
        TropicalFish.Pattern pattern = patterns[random.nextInt(patterns.length)];
        DyeColor body = colors[random.nextInt(colors.length)];
        DyeColor patternColor = colors[random.nextInt(colors.length)];
        if (!used.add("fishbucket:" + pattern + body + patternColor)) {
            return null;
        }
        ItemStack item = new ItemStack(Material.TROPICAL_FISH_BUCKET);
        item.editMeta(TropicalFishBucketMeta.class, meta -> {
            meta.setPattern(pattern);
            meta.setBodyColor(body);
            meta.setPatternColor(patternColor);
        });
        return new Offer(slot, item, variantPrice("tropical_fish_bucket", 250));
    }

    /** Rüstung mit Rüstungsbesatz (zufälliges Muster und Material). */
    private Offer trimOffer(int slot, Set<String> used, List<Material> armorPieces) {
        List<TrimMaterial> materials = new ArrayList<>();
        for (TrimMaterial m : Registry.TRIM_MATERIAL) {
            materials.add(m);
        }
        List<TrimPattern> patterns = new ArrayList<>();
        for (TrimPattern t : Registry.TRIM_PATTERN) {
            patterns.add(t);
        }
        if (materials.isEmpty() || patterns.isEmpty() || armorPieces.isEmpty()) {
            return null;
        }
        Material piece = armorPieces.get(random.nextInt(armorPieces.size()));
        TrimMaterial material = materials.get(random.nextInt(materials.size()));
        TrimPattern pattern = patterns.get(random.nextInt(patterns.size()));
        if (!used.add("trim:" + piece + material.getKey() + pattern.getKey())) {
            return null;
        }
        ItemStack item = new ItemStack(piece);
        item.editMeta(ArmorMeta.class, meta -> meta.setTrim(new ArmorTrim(material, pattern)));
        return new Offer(slot, item, unitPrice(piece) + variantPrice("armor_trim", 2500));
    }

    /** Gefärbte Lederrüstung in allen 16 Farben. */
    private Offer dyedOffer(int slot, Set<String> used) {
        Material[] leather = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS, Material.LEATHER_HORSE_ARMOR};
        Material piece = leather[random.nextInt(leather.length)];
        DyeColor[] colors = DyeColor.values();
        DyeColor dye = colors[random.nextInt(colors.length)];
        if (!used.add("dyed:" + piece + dye)) {
            return null;
        }
        Color color = dye.getColor();
        ItemStack item = new ItemStack(piece);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
        return new Offer(slot, item, unitPrice(piece) + variantPrice("dyed_leather", 20));
    }

    /** Eine Shulkerbox in einer zufälligen Farbe (die ungefärbte und alle 16 Farben). */
    private Offer shulkerOffer(int slot, Set<String> used) {
        List<Material> boxes = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().endsWith("SHULKER_BOX") && material.isItem()) {
                boxes.add(material);
            }
        }
        if (boxes.isEmpty()) {
            return null;
        }
        for (int tries = 0; tries < 10; tries++) {
            Material box = boxes.get(random.nextInt(boxes.size()));
            if (used.add("shulker:" + box.name())) {
                return new Offer(slot, new ItemStack(box), unitPrice(box));
            }
        }
        return null;
    }
}
