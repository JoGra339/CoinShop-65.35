package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Das Preisbuch: EIN Wert für wirklich jedes Item.
 * Reihenfolge:
 *  1. Eintrag in der config.yml (server-shop.prices, erster passender gewinnt)
 *  2. Herstellung: Preis = Zutaten (aus den Rezepten des Servers) + 10 % Aufschlag
 *  3. Standardpreis (server-shop.default-price)
 * Die fertige Liste steht in plugins/CoinShop/preisliste.txt, Items nur mit Standardpreis in preise-fehlen.txt.
 */
public final class PriceBook {

    public enum Source { CONFIG, RECIPE, DEFAULT }

    private final CoinShopPlugin plugin;
    private final Map<String, Long> rules = new LinkedHashMap<>();
    private final Map<Material, Long> cache = new HashMap<>();
    private final Map<Material, Source> sources = new HashMap<>();
    private final Map<Material, List<Recipe>> recipes = new HashMap<>();
    private final Set<Material> visiting = new HashSet<>();
    private final Map<Material, Long> memo = new HashMap<>();
    private long defaultPrice = 15;
    private boolean loaded;

    public PriceBook(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Liest die config neu und indiziert alle Rezepte des Servers. */
    public void reload() {
        loaded = true;
        rules.clear();
        cache.clear();
        sources.clear();
        recipes.clear();
        visiting.clear();
        memo.clear();
        ConfigurationSection prices = plugin.getConfig().getConfigurationSection("server-shop.prices");
        if (prices != null) {
            for (String key : prices.getKeys(false)) {
                rules.put(key.toUpperCase(Locale.ROOT), Math.max(1, prices.getLong(key)));
            }
        }
        defaultPrice = Math.max(1, plugin.getConfig().getLong("server-shop.default-price", 15));
        try {
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe recipe = it.next();
                Material result = recipe.getResult().getType();
                if (!result.isAir()) {
                    recipes.computeIfAbsent(result, k -> new ArrayList<>()).add(recipe);
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Rezepte konnten nicht gelesen werden: " + e);
        }
    }

    public long price(Material material) {
        if (!loaded) {
            reload();
        }
        Long cached = cache.get(material);
        if (cached != null) {
            return cached;
        }
        long value = compute(material);
        if (value < 0) {
            value = defaultPrice;
            sources.put(material, Source.DEFAULT);
        }
        cache.put(material, value);
        return value;
    }

    public Source source(Material material) {
        price(material);
        return sources.getOrDefault(material, Source.DEFAULT);
    }

    /** -1 = unbekannt oder Schleife. Zwischenergebnisse werden gemerkt (sonst rechnet der Server ewig). */
    private long compute(Material material) {
        Long known = memo.get(material);
        if (known != null) {
            return known;
        }
        long result = computeUncached(material);
        // Ergebnisse mitten in einer Rezept-Schleife nicht dauerhaft merken (außer sie sind gültig)
        if (result >= 0 || visiting.isEmpty()) {
            memo.put(material, result);
        }
        return result;
    }

    private long computeUncached(Material material) {
        for (Map.Entry<String, Long> rule : rules.entrySet()) {
            if (ShopItemGenerator.matches(rule.getKey(), material.name())) {
                sources.put(material, Source.CONFIG);
                return rule.getValue();
            }
        }
        Long cached = cache.get(material);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(material)) {
            return -1;
        }
        try {
            long best = Long.MAX_VALUE;
            for (Recipe recipe : recipes.getOrDefault(material, List.of())) {
                long cost = recipeCost(recipe);
                if (cost > 0 && cost < best) {
                    best = cost;
                }
            }
            if (best == Long.MAX_VALUE) {
                return -1;
            }
            sources.put(material, Source.RECIPE);
            return Math.max(1, Math.round(best * 1.10));
        } finally {
            visiting.remove(material);
        }
    }

    private long recipeCost(Recipe recipe) {
        long total = 0;
        try {
            if (recipe instanceof ShapedRecipe shaped) {
                Map<Character, RecipeChoice> map = shaped.getChoiceMap();
                for (String row : shaped.getShape()) {
                    for (char c : row.toCharArray()) {
                        if (c == ' ') {
                            continue;
                        }
                        long p = choicePrice(map.get(c));
                        if (p < 0) {
                            return -1;
                        }
                        total += p;
                    }
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                for (RecipeChoice choice : shapeless.getChoiceList()) {
                    long p = choicePrice(choice);
                    if (p < 0) {
                        return -1;
                    }
                    total += p;
                }
            } else if (recipe instanceof CookingRecipe<?> cooking) {
                long p = choicePrice(cooking.getInputChoice());
                if (p < 0) {
                    return -1;
                }
                total = p + 3;
            } else if (recipe instanceof StonecuttingRecipe stone) {
                long p = choicePrice(stone.getInputChoice());
                if (p < 0) {
                    return -1;
                }
                total = p;
            } else if (recipe instanceof SmithingTransformRecipe smithing) {
                long a = choicePrice(smithing.getTemplate());
                long b = choicePrice(smithing.getBase());
                long c = choicePrice(smithing.getAddition());
                if (a < 0 || b < 0 || c < 0) {
                    return -1;
                }
                total = a + b + c;
            } else {
                return -1;
            }
        } catch (RuntimeException e) {
            return -1;
        }
        int amount = Math.max(1, recipe.getResult().getAmount());
        return (long) Math.ceil(total / (double) amount);
    }

    /** Preis einer Zutat; bei mehreren erlaubten Items das billigste. null = leerer Platz (kostet nichts). */
    private long choicePrice(RecipeChoice choice) {
        if (choice == null) {
            return 0;
        }
        List<Material> options = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            options.addAll(mc.getChoices());
        } else if (choice instanceof RecipeChoice.ExactChoice ec) {
            ec.getChoices().forEach(i -> options.add(i.getType()));
        } else {
            options.add(choice.getItemStack().getType());
        }
        long best = Long.MAX_VALUE;
        for (Material m : options) {
            long p = compute(m);
            if (p >= 0 && p < best) {
                best = p;
            }
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    // ------------------------------------------------------------------
    //  Listen als Dateien
    // ------------------------------------------------------------------

    /** Schreibt preisliste.txt (alle Items) und preise-fehlen.txt (nur Standardpreis). Gibt die Zahl der Standardpreise zurück. */
    public int writeFiles() {
        List<Material> all = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_")) {
                all.add(m);
            }
        }
        all.sort((a, b) -> Long.compare(price(b), price(a)));
        StringBuilder full = new StringBuilder("# Preisliste (teuerste zuerst). Quelle: config = aus der config.yml, rezept = aus den Zutaten, standard = Standardpreis\n");
        StringBuilder missing = new StringBuilder("# Diese Items haben nur den Standardpreis (default-price). Trage sie bei server-shop.prices in der config.yml ein.\n");
        int defaults = 0;
        for (Material m : all) {
            Source s = source(m);
            full.append(String.format(Locale.ROOT, "%-45s %9d  %s%n", m.name(), price(m), s.name().toLowerCase(Locale.ROOT)));
            if (s == Source.DEFAULT) {
                defaults++;
                missing.append(m.name()).append('\n');
            }
        }
        try {
            Path dir = plugin.getDataFolder().toPath();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("preisliste.txt"), full.toString(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("preise-fehlen.txt"), missing.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Preislisten konnten nicht geschrieben werden: " + e);
        }
        return defaults;
    }
}
