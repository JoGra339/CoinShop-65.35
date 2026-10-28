package de.coinshop;

import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Bestimmt, wie viele Coins ein Erfolg wert ist (je schwieriger, desto mehr).
 *
 * Reihenfolge der Prüfung:
 *  1. Steht der Erfolg unter "achievements.overrides"?  -> genau dieser Betrag
 *  2. Steht er in der Liste einer Stufe ("advancements")? -> Belohnung dieser Stufe (bei mehreren die höchste)
 *  3. Sonst entscheidet die Art des Erfolgs im Spiel (Aufgabe / Ziel / Herausforderung)
 */
public final class AchievementRewards {

    /** Belohnung samt Name der Stufe (für die Chat-Nachricht). */
    public record Reward(long coins, String tierName) { }

    private final CoinShopPlugin plugin;

    public AchievementRewards(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public Reward rewardFor(Advancement advancement) {
        FileConfiguration cfg = plugin.getConfig();
        String fullKey = advancement.getKey().toString(); // z. B. "minecraft:story/mine_diamond"

        // 1. Einzelner Sonderbetrag
        ConfigurationSection overrides = cfg.getConfigurationSection("achievements.overrides");
        if (overrides != null && overrides.contains(fullKey)) {
            return new Reward(Math.max(0, overrides.getLong(fullKey)), "Sonderbelohnung");
        }

        // 2. Handverlesene Listen schwieriger Erfolge
        ConfigurationSection tiers = cfg.getConfigurationSection("achievements.tiers");
        if (tiers != null) {
            Reward best = null;
            for (String id : tiers.getKeys(false)) {
                ConfigurationSection tier = tiers.getConfigurationSection(id);
                if (tier == null || !tier.getStringList("advancements").contains(fullKey)) {
                    continue;
                }
                long coins = Math.max(0, tier.getLong("reward", 0));
                if (best == null || coins > best.coins()) {
                    best = new Reward(coins, tier.getString("name", id));
                }
            }
            if (best != null) {
                return best;
            }
        }

        // 3. Nach Art des Erfolgs: task / goal / challenge
        String type = frameType(advancement);
        String tierId = cfg.getString("achievements.type-tiers." + type, "leicht");
        ConfigurationSection tier = tiers == null ? null : tiers.getConfigurationSection(tierId);
        if (tier == null) {
            return new Reward(10, "Leicht");
        }
        return new Reward(Math.max(0, tier.getLong("reward", 10)), tier.getString("name", tierId));
    }

    /**
     * Liest die Art des Erfolgs ("task", "goal" oder "challenge"). Paper hat dafür in neueren
     * Versionen eine neue Klasse (frame()), in älteren die alte (getType()). Damit es mit beiden
     * funktioniert, fragen wir vorsichtig ab und nehmen im Zweifel "task".
     */
    private static String frameType(Advancement advancement) {
        Object display = advancement.getDisplay();
        if (display == null) {
            return "task";
        }
        String[][] candidates = {
                {"io.papermc.paper.advancement.AdvancementDisplay", "frame"},
                {"org.bukkit.advancement.AdvancementDisplay", "getType"}
        };
        for (String[] candidate : candidates) {
            try {
                Class<?> type = Class.forName(candidate[0]);
                if (!type.isInstance(display)) {
                    continue;
                }
                Object value = type.getMethod(candidate[1]).invoke(display);
                if (value != null) {
                    String name = value.toString().toLowerCase(Locale.ROOT);
                    if (name.contains("challenge")) {
                        return "challenge";
                    }
                    if (name.contains("goal")) {
                        return "goal";
                    }
                    return "task";
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // nächsten Weg probieren
            }
        }
        return "task";
    }
}
