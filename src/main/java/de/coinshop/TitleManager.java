package de.coinshop;

import de.coinshop.RankingManager.Rank;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Titel, die vor dem Namen im Chat und in der Tab-Liste stehen, z. B. "[Diamantkönig] Steve".
 *
 * Es gibt drei Arten von Titeln:
 *  - "Der Reichste": gehört dem Spieler mit den meisten Coins (solange er der Reichste ist)
 *  - Platz-1-Titel: einer pro Rangliste, gehört dem Ersten dieser Rangliste (solange er Erster ist)
 *  - dauerhafte Titel: für Leistungen wie 30 Tage Login-Serie oder 10 Kollektionen - die behält man für immer
 *
 * Ein Spieler kann mehrere Titel besitzen, trägt aber immer nur EINEN. Welchen, bestimmt er selbst im
 * Titel-Menü (/titel):
 *  - "Automatisch" (Standard): der wichtigste Titel, den er gerade besitzt (Reihenfolge wie in diesem Menü)
 *  - ein bestimmter Titel: den trägt er, solange er ihn besitzt. Verliert er ihn (z. B. weil ihn ein anderer als
 *    Erster ablöst), wird wieder automatisch gewählt.
 *  - "Kein Titel"
 */
public final class TitleManager implements Listener {

    public enum Kind { RICHEST, RANK, PERMANENT, PATE, ROLE }

    /** Ein Titel. "type" und "value" gelten nur für dauerhafte Titel (z. B. type=streak, value=30). */
    public record TitleDef(String id, Kind kind, String name, String color, Material icon,
                           String type, long value, String rankId, Effect effect) { }

    /**
     * Die Wirkung eines Titels, solange man ihn trägt. type z. B. "task-boost", "shop-discount", "miner-double";
     * stat nur bei "task-boost" (leer = alle Aufgaben); value meist in Prozent; text steht im Titel-Menü.
     */
    public record Effect(String type, String stat, double value, String text) {
        public static final Effect NONE = new Effect("", "", 0, "");
    }

    private final CoinShopPlugin plugin;
    private final List<TitleDef> defs = new ArrayList<>();
    /**
     * Der fertige Titel-Text jedes Online-Spielers. Der Chat läuft nicht im Haupt-Thread (dort darf die
     * Datenbank nicht angefasst werden), deshalb liest er nur aus dieser Merkliste.
     */
    private final Map<UUID, Component> shown = new ConcurrentHashMap<>();
    /** Welchen Titel (Id, "" = keinen) trägt ein Online-Spieler gerade? Für schnelle Effekt-Abfragen. */
    private final Map<UUID, String> worn = new ConcurrentHashMap<>();

    public TitleManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    // ------------------------------------------------------------------
    //  Titel-Liste aus der config.yml
    // ------------------------------------------------------------------

    public void reload() {
        defs.clear();
        var cfg = plugin.getConfig();

        ConfigurationSection richest = cfg.getConfigurationSection("titles.richest");
        if (richest != null) {
            defs.add(new TitleDef("richest", Kind.RICHEST, richest.getString("name", "Der Reichste"),
                    richest.getString("color", "gold"), icon(richest.getString("icon"), Material.GOLD_INGOT),
                    "", 0, "", effect(richest)));
        }
        for (Rank rank : Rank.values()) {
            ConfigurationSection s = cfg.getConfigurationSection("titles.rank." + rank.id());
            if (s == null) {
                continue;
            }
            defs.add(new TitleDef("rank-" + rank.id(), Kind.RANK, s.getString("name", rank.id()),
                    s.getString("color", "white"), rank.icon(), "", 0, rank.id(), effect(s)));
        }
        ConfigurationSection permanent = cfg.getConfigurationSection("titles.permanent");
        if (permanent != null) {
            for (String key : permanent.getKeys(false)) {
                ConfigurationSection s = permanent.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                defs.add(new TitleDef("perm-" + key, Kind.PERMANENT, s.getString("name", key),
                        s.getString("color", "white"), icon(s.getString("icon"), Material.NAME_TAG),
                        s.getString("type", ""), s.getLong("value", 1), "", effect(s)));
            }
        }
        ConfigurationSection pate = cfg.getConfigurationSection("titles.pate");
        if (pate != null) {
            defs.add(0, new TitleDef("pate", Kind.PATE, pate.getString("name", "Pate"),
                    pate.getString("color", "dark_red"), icon(pate.getString("icon"), Material.WITHER_SKELETON_SKULL),
                    "", 0, "", effect(pate)));
        }
        // Rollen-Titel: gehören den Unterhändlern, Gesellschaftern und dem CEO (Reihenfolge: wichtigster zuerst)
        int at = defs.isEmpty() || defs.get(0).kind() != Kind.PATE ? 0 : 1;
        for (String role : new String[]{"ceo", "wettbaron", "unterhaendler", "gesellschafter"}) {
            ConfigurationSection s = cfg.getConfigurationSection("titles." + role);
            if (s != null) {
                defs.add(at++, new TitleDef(role, Kind.ROLE, s.getString("name", role),
                        s.getString("color", "white"), icon(s.getString("icon"), Material.NAME_TAG),
                        "", 0, "", effect(s)));
            }
        }
    }

    private static Effect effect(ConfigurationSection section) {
        ConfigurationSection e = section == null ? null : section.getConfigurationSection("effect");
        if (e == null) {
            return Effect.NONE;
        }
        return new Effect(e.getString("type", ""), e.getString("stat", ""), e.getDouble("value", 0),
                e.getString("text", ""));
    }

    /**
     * Wirkung des getragenen Titels: Wert, wenn der Spieler einen Titel mit dieser Wirkung trägt, sonst 0.
     * Bei "task-boost" muss stat passen (oder der Titel gilt für alle Aufgaben).
     */
    public double effect(UUID uuid, String type, String stat) {
        String id = worn.get(uuid);
        TitleDef def;
        if (id == null) {
            def = effective(uuid);
            if (Bukkit.getPlayer(uuid) != null) {
                worn.put(uuid, def == null ? "" : def.id());
            }
        } else {
            def = id.isEmpty() ? null : byId(id);
        }
        double result = def == null ? 0 : matching(def, type, stat);
        // Die Wirkung des Paten gilt, solange man Pate ist - auch wenn ein anderer Titel getragen wird
        if ((def == null || !def.id().equals("pate")) && uuid.equals(plugin.reviere().pate())) {
            TitleDef pateDef = byId("pate");
            if (pateDef != null) {
                result += matching(pateDef, type, stat);
            }
        }
        return result;
    }

    private static double matching(TitleDef def, String type, String stat) {
        if (!def.effect().type().equals(type)) {
            return 0;
        }
        String wanted = def.effect().stat();
        if (!wanted.isEmpty() && !wanted.equals(stat)) {
            // "mined" (Erdenbrecher) gilt für alle Abbau-Aufgaben: Blöcke, Stein, Holz, Diamanterze und Antiker Schrott
            boolean allMining = wanted.equals("mined") && stat != null
                    && (stat.equals("stone") || stat.equals("wood") || stat.equals("diamonds") || stat.equals("debris"));
            if (!allMining) {
                return 0;
            }
        }
        return def.effect().value();
    }

    private static Material icon(String name, Material fallback) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null ? fallback : m;
    }

    public List<TitleDef> all() {
        return defs;
    }

    public TitleDef byId(String id) {
        for (TitleDef def : defs) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /** Der Titel als farbiger Text, z. B. "Diamantkönig" (ohne Klammern). */
    public Component nameOf(TitleDef def) {
        TextColor color = NamedTextColor.NAMES.value(def.color());
        return Component.text(def.name(), color == null ? NamedTextColor.WHITE : color);
    }

    // ------------------------------------------------------------------
    //  Besitz und Auswahl
    // ------------------------------------------------------------------

    /** Alle Titel (Ids), die der Spieler gerade besitzt. */
    public Set<String> owned(UUID uuid) {
        Set<String> result = new HashSet<>(plugin.database().getTitles(uuid));
        result.addAll(plugin.database().getRankTitles(uuid));
        return result;
    }

    /** Die gewählte Einstellung: "auto", "none" oder eine Titel-Id. */
    public String choice(UUID uuid) {
        return plugin.database().getTitleChoice(uuid);
    }

    /** Welchen Titel trägt der Spieler wirklich? (null = keinen) */
    public TitleDef effective(UUID uuid) {
        String choice = choice(uuid);
        if (choice.equals("none")) {
            return null;
        }
        Set<String> owned = owned(uuid);
        if (!choice.equals("auto") && owned.contains(choice)) {
            TitleDef chosen = byId(choice);
            if (chosen != null) {
                return chosen;
            }
        }
        for (TitleDef def : defs) { // automatisch: der wichtigste besessene Titel
            if (owned.contains(def.id())) {
                return def;
            }
        }
        return null;
    }

    public void select(Player player, String choice) {
        plugin.database().setTitleChoice(player.getUniqueId(), choice);
        refresh(player);
    }

    // ------------------------------------------------------------------
    //  Anzeige (Chat und Tab-Liste)
    // ------------------------------------------------------------------

    /** Der Titel in eckigen Klammern oder null. */
    private Component bracketed(TitleDef def) {
        if (def == null) {
            return null;
        }
        return Component.text("[", NamedTextColor.DARK_GRAY).append(nameOf(def))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }

    /** Der Titel des Spielers als Text (ohne Klammern) für Menüs; null = keiner. */
    public Component currentName(UUID uuid) {
        TitleDef def = effective(uuid);
        return def == null ? null : nameOf(def);
    }

    /** Berechnet den Titel neu und trägt ihn in Merkliste und Tab-Liste ein. */
    public void refresh(Player player) {
        try {
            TitleDef now = effective(player.getUniqueId());
            worn.put(player.getUniqueId(), now == null ? "" : now.id());
            Component prefix = bracketed(now);
            if (prefix == null) {
                shown.remove(player.getUniqueId());
                player.playerListName(null); // zurück zum Standard
            } else {
                shown.put(player.getUniqueId(), prefix);
                player.playerListName(prefix.append(Component.text(player.getName())));
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Titel von " + player.getName() + " konnte nicht gesetzt werden: " + e);
        }
    }

    public void forget(UUID uuid) {
        shown.remove(uuid);
        worn.remove(uuid);
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /** Setzt den Titel vor den Namen im Chat. Läuft nicht im Haupt-Thread - fasst deshalb nur die Merkliste an. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Component prefix = shown.get(event.getPlayer().getUniqueId());
        if (prefix == null) {
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.translatable("chat.type.text", prefix.append(sourceDisplayName), message));
    }

    // ------------------------------------------------------------------
    //  Platz-1-Titel aktualisieren (alle paar Minuten und nach jedem Shop-Reset)
    // ------------------------------------------------------------------

    public void updateRankTitles() {
        try {
            Database db = plugin.database();
            Map<String, Set<UUID>> holders = new LinkedHashMap<>();

            // Der Reichste (bei gleichem Vermögen alle)
            long max = 0;
            for (Database.Account a : db.allAccounts()) {
                max = Math.max(max, a.coins());
            }
            if (max > 0) {
                for (Database.Account a : db.allAccounts()) {
                    if (a.coins() == max) {
                        holders.computeIfAbsent("richest", k -> new HashSet<>()).add(a.uuid());
                    }
                }
            }
            // Erster jeder Rangliste (bei Gleichstand alle auf Platz 1)
            for (Rank rank : Rank.values()) {
                for (RankingManager.Placed p : plugin.rankings().ranked(rank, 10)) {
                    if (p.place() == 1 && (p.entry().value() > 0 || rank.ascending())) {
                        holders.computeIfAbsent("rank-" + rank.id(), k -> new HashSet<>()).add(p.entry().uuid());
                    }
                }
            }

            UUID pate = plugin.reviere().pate();
            if (pate != null) {
                holders.computeIfAbsent("pate", k -> new HashSet<>()).add(pate);
            }
            for (int revier = 1; revier <= 6; revier++) {
                UUID boss = plugin.reviere().boss(revier);
                if (boss != null) {
                    holders.computeIfAbsent("unterhaendler", k -> new HashSet<>()).add(boss);
                }
            }
            for (int sector = 0; sector < WholesaleManager.SECTORS.size(); sector++) {
                UUID shareholder = plugin.wholesale().shareholder(sector);
                if (shareholder != null) {
                    holders.computeIfAbsent("gesellschafter", k -> new HashSet<>()).add(shareholder);
                }
            }
            UUID baron = plugin.bets().baron();
            if (baron != null) {
                holders.computeIfAbsent("wettbaron", k -> new HashSet<>()).add(baron);
            }
            UUID ceo = plugin.wholesale().ceo();
            if (ceo != null) {
                holders.computeIfAbsent("ceo", k -> new HashSet<>()).add(ceo);
            }

            Map<String, Set<UUID>> old = db.getRankTitleHolders();
            db.replaceRankTitles(holders);

            // Neue Titelträger ankündigen (beim allerersten Durchlauf nicht, sonst gäbe es eine Meldungsflut)
            if (!old.isEmpty()) {
                for (Map.Entry<String, Set<UUID>> e : holders.entrySet()) {
                    TitleDef def = byId(e.getKey());
                    if (def == null) {
                        continue;
                    }
                    for (UUID uuid : e.getValue()) {
                        if (!old.getOrDefault(e.getKey(), Set.of()).contains(uuid)) {
                            announce(uuid, def, e.getKey());
                        }
                    }
                }
            }
            refreshAll();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Platz-1-Titel konnten nicht aktualisiert werden: " + e);
        }
    }

    private void announce(UUID uuid, TitleDef def, String key) {
        String name = plugin.database().getName(uuid);
        Messages msg = plugin.messages();
        for (Player online : Bukkit.getOnlinePlayers()) {
            msg.send(online, "title-new-holder", "player", name, "title", nameOf(def));
        }
        Player holder = Bukkit.getPlayer(uuid);
        if (holder != null && def.kind() != Kind.PATE && def.kind() != Kind.ROLE) {
            msg.send(holder, "title-you-first", "title", nameOf(def));
        }
    }

    // ------------------------------------------------------------------
    //  Dauerhafte Titel prüfen (einmal pro Minute für alle Online-Spieler)
    // ------------------------------------------------------------------

    public void checkPermanentAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                checkPermanent(player);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Titel-Prüfung für " + player.getName() + " fehlgeschlagen: " + e);
            }
        }
    }

    public void checkPermanent(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        Set<String> have = db.getTitles(uuid);
        boolean changed = false;
        for (TitleDef def : defs) {
            if (def.kind() != Kind.PERMANENT || have.contains(def.id()) || !reached(uuid, def)) {
                continue;
            }
            if (db.addTitle(uuid, def.id())) {
                plugin.messages().send(player, "title-earned", "title", nameOf(def));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                changed = true;
            }
        }
        if (changed) {
            refresh(player);
        }
    }

    /** Hat der Spieler die Bedingung eines dauerhaften Titels erfüllt? */
    private boolean reached(UUID uuid, TitleDef def) {
        Database db = plugin.database();
        return switch (def.type()) {
            case "streak" -> db.getStreak(uuid).map(Database.Streak::best).orElse(0) >= def.value();
            case "collections" -> db.getDoneCollections(uuid).size() >= def.value();
            case "secrets" -> db.getSecrets(uuid).size() >= def.value();
            case "quests" -> db.getStat(uuid, "quests_done") >= def.value();
            case "coins" -> db.getCoins(uuid) >= def.value();
            case "playtime" -> db.getStat(uuid, "playtime") >= def.value();
            default -> false;
        };
    }
}
