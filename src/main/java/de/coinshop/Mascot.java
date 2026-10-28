package de.coinshop;

import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;

import java.util.UUID;

/**
 * Maskottchen: Ein Begleiter, den man im Profil auswählt. Er bringt Rabatt im Shop (Server-Shop und Schwarzmarkt-Preise),
 * mehr Belohnung bei Tagesaufgaben und etwas Glück bei seltenen Funden und Box-Chancen. Solange er Maskottchen ist, kann er
 * nichts anderes tun (keine Expedition, Reise, Großhandel, Zucht, Verkauf). Der Bonus wächst mit Seltenheit und Level.
 */
public final class Mascot {

    private final CoinShopPlugin plugin;

    Mascot(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mascot.enabled", true) && plugin.expeditions().enabled();
    }

    /** Das gewählte Exemplar (null, wenn keines gewählt ist oder es nicht mehr existiert). */
    public Copy get(UUID uuid) {
        long c = plugin.database().getMetaLong("mascot_c_" + uuid, 0L);
        if (c <= 0) {
            return null;
        }
        Companion[] all = Companion.values();
        if (c > all.length) {
            return null;
        }
        int idx = (int) plugin.database().getMetaLong("mascot_i_" + uuid, 0L);
        Companion companion = all[(int) c - 1];
        return plugin.expeditions().exists(uuid, companion, idx) ? new Copy(companion, idx) : null;
    }

    public boolean isMascot(UUID uuid, Companion c, int idx) {
        if (!enabled()) {
            return false;
        }
        Copy copy = get(uuid);
        return copy != null && copy.companion() == c && copy.index() == idx;
    }

    /** Glücksfaktor für Wahrscheinlichkeiten (1,0 ohne Maskottchen): Drops, Funde, Box-Chancen. */
    public double luck(UUID uuid) {
        double pct = discountPercent(uuid);
        return 1.0 + pct * Math.max(0, plugin.getConfig().getDouble("mascot.luck-factor", 5.0)) / 100.0;
    }

    /** Namen der Aufgabenarten (Schlüssel wie in quests.pool). */
    private static final java.util.Map<String, String> STAT_NAMES = new java.util.LinkedHashMap<>();

    static {
        STAT_NAMES.put("mined", "Blöcke abbauen");
        STAT_NAMES.put("stone", "Stein abbauen");
        STAT_NAMES.put("diamonds", "Diamanterze");
        STAT_NAMES.put("wood", "Holz sammeln");
        STAT_NAMES.put("fishing", "Angeln");
        STAT_NAMES.put("creatures", "Kreaturen besiegen");
        STAT_NAMES.put("walked", "Wandern");
        STAT_NAMES.put("placed", "Bauen");
        STAT_NAMES.put("trades", "Handeln");
        STAT_NAMES.put("enchanted", "Verzaubern");
        STAT_NAMES.put("brewed", "Tränke brauen");
        STAT_NAMES.put("bred", "Tiere züchten");
        STAT_NAMES.put("harvest", "Ernte");
    }

    /** Faktor, mit dem der Fortschritt einer Aufgabenart zählt: Experte x2, passt x1,5, fachfremd x1 (Config mascot.task-*). */
    public double taskMultiplier(UUID uuid, String stat) {
        if (!enabled()) {
            return 1.0;
        }
        Copy copy = get(uuid);
        return copy == null ? 1.0 : multiplierFor(copy.companion(), stat);
    }

    public double multiplierFor(Companion c, String stat) {
        int fit = plugin.expeditions().life().questFit(c, stat);
        if (fit == 2) {
            return Math.max(1.0, plugin.getConfig().getDouble("mascot.task-expert", 2.0));
        }
        return fit == 1 ? Math.max(1.0, plugin.getConfig().getDouble("mascot.task-fit", 1.5)) : 1.0;
    }

    /** Zeilen für Menüs: bei welchen Aufgaben dieser Begleiter als Maskottchen den Fortschritt verstärkt. */
    public java.util.List<String> buffLines(Companion c) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : STAT_NAMES.entrySet()) {
            double m = multiplierFor(c, e.getKey());
            if (m > 1.0) {
                lines.add(e.getValue() + ": x" + (m == Math.rint(m) ? String.valueOf((int) m) : String.valueOf(m)));
            }
        }
        return lines;
    }

    /** Prozent mehr Belohnung bei Tagesaufgaben (0 ohne Maskottchen). */
    public double questPercent(UUID uuid) {
        return discountPercent(uuid) * Math.max(0, plugin.getConfig().getDouble("mascot.quest-factor", 2.0));
    }

    public void choose(UUID uuid, Copy copy) {
        Companion[] all = Companion.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i] == copy.companion()) {
                plugin.database().setMetaLong("mascot_c_" + uuid, i + 1L);
                plugin.database().setMetaLong("mascot_i_" + uuid, copy.index());
                plugin.expeditions().roster().setTaken(uuid, copy.companion(), copy.index(), false); // Maskottchen geht nicht mit
                plugin.expeditions().roster().save(uuid);
                return;
            }
        }
    }

    public void clear(UUID uuid) {
        plugin.database().setMetaLong("mascot_c_" + uuid, 0L);
    }

    /** Rabatt in Prozent, den das Maskottchen im Shop bringt (0 ohne Maskottchen). */
    public double discountPercent(UUID uuid) {
        if (!enabled()) {
            return 0;
        }
        Copy copy = get(uuid);
        if (copy == null) {
            return 0;
        }
        return percentOf(uuid, copy);
    }

    public double percentOf(UUID uuid, Copy copy) {
        ExpeditionManager ex = plugin.expeditions();
        double base = Math.max(0, plugin.getConfig().getDouble("mascot.base-percent", 1.0));
        double perRarity = Math.max(0, plugin.getConfig().getDouble("mascot.rarity-percent", 0.5));
        double perLevel = Math.max(0, plugin.getConfig().getDouble("mascot.level-percent", 0.03));
        double max = Math.max(0, plugin.getConfig().getDouble("mascot.max-percent", 5.0));
        int level = ex.copyLevel(uuid, copy.companion(), copy.index());
        double pct = base + perRarity * ex.rarity(uuid, copy.companion(), copy.index()).ordinal() + perLevel * (level - 1);
        return Math.min(max, Math.round(pct * 10.0) / 10.0);
    }
}
