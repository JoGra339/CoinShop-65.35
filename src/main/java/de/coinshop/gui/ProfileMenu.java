package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.InvestmentManager;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RankingManager;
import de.coinshop.RankingManager.Rank;
import de.coinshop.SecretAchievements;
import de.coinshop.TitleManager;
import de.coinshop.TitleManager.TitleDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Das Spieler-Profil: Kopf, Kontostand, Titel, Spielzeit, Login-Serie, Einkommen, Erfolge, Kollektionen,
 * Aufgaben, Statistiken und Ranglisten-Platzierungen. Funktioniert auch für Spieler, die gerade offline sind
 * (dann zeigt es die zuletzt gespeicherten Werte). Aufruf: /profil [Spieler] oder im Hauptmenü.
 */
public final class ProfileMenu extends Menu {

    /** Welche Statistiken angezeigt werden: Schlüssel in der Datenbank, Symbol, Feld im Menü. */
    private static final String[] STAT_KEYS = {"mined", "placed", "walked", "creatures", "bosses", "fishing", "trades"};
    private static final Material[] STAT_ICONS = {Material.DIAMOND_PICKAXE, Material.BRICKS, Material.LEATHER_BOOTS,
            Material.IRON_SWORD, Material.DRAGON_HEAD, Material.FISHING_ROD, Material.EMERALD};
    private static final int[] STAT_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    public ProfileMenu(CoinShopPlugin plugin, Player viewer, UUID target) {
        super(plugin, 6, plugin.messages().get("gui.profile.title", "name", plugin.database().getName(target)));
        Messages msg = plugin.messages();
        Database db = plugin.database();
        String name = db.getName(target);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            plugin.rankings().sync(online); // aktuelle Zahlen speichern, bevor wir sie lesen
        }

        // ---------------- Kopf (oben Mitte) ----------------
        long coins = db.getCoins(target);
        List<Database.Account> accounts = new ArrayList<>(db.allAccounts());
        accounts.sort(Comparator.comparingLong(Database.Account::coins).reversed());
        int place = 1;
        for (Database.Account a : accounts) {
            if (a.coins() > coins) {
                place++;
            }
        }
        Component status = online != null ? msg.get("gui.profile.online") : msg.get("gui.profile.offline");
        ItemStack head = ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.profile.head-name", "name", name),
                msg.getList("gui.profile.head-lore", "status", status, "coins", coins,
                        "place", place, "players", accounts.size()));
        try {
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(target)));
        } catch (RuntimeException ignored) {
            // dann eben ein Standard-Kopf
        }
        set(4, head, null);

        // ---------------- Reihe 2: Titel, Spielzeit, Serie, Einkommen, Erfolge, Kollektionen, Aufgaben ----------------
        TitleManager titles = plugin.titles();
        Set<String> owned = titles.owned(target);
        TitleDef current = titles.effective(target);
        List<Component> titleLore = new ArrayList<>(msg.getList("gui.profile.title-lore",
                "current", current == null ? Component.text("-") : titles.nameOf(current),
                "have", owned.size(), "total", titles.all().size()));
        for (TitleDef def : titles.all()) {
            if (owned.contains(def.id())) {
                titleLore.add(Component.text("- ", NamedTextColor.DARK_GRAY).append(titles.nameOf(def)));
            }
        }
        set(10, ItemUtil.icon(Material.NAME_TAG, msg.get("gui.profile.title-name"), titleLore), null);

        long minutes = db.getStat(target, "playtime");
        set(11, ItemUtil.icon(Material.CLOCK, msg.get("gui.profile.playtime-name"),
                msg.getList("gui.profile.playtime-lore", "time", ItemUtil.formatDuration(minutes * 60_000L))), null);

        int streak = plugin.logins().currentStreak(target);
        int best = db.getStreak(target).map(s -> s.best()).orElse(0);
        set(12, ItemUtil.icon(Material.SUNFLOWER, msg.get("gui.profile.streak-name"),
                msg.getList("gui.profile.streak-lore", "streak", streak, "best", best)), null);

        InvestmentManager invest = plugin.investments();
        var owners = invest.ownershipMap();
        long login = plugin.logins().bonusOf(target);
        long sets = invest.setIncomeOf(target, owners);
        long rank = plugin.rankings().currentBonuses().getOrDefault(target, 0L);
        long base = invest.baseIncome(target);
        set(13, ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.profile.income-name"),
                msg.getList("gui.profile.income-lore", "total", base + login + sets + rank, "base", base,
                        "login", login, "sets", sets, "rank", rank)), null);

        set(14, ItemUtil.icon(Material.ENDER_EYE, msg.get("gui.profile.secret-name"),
                msg.getList("gui.profile.secret-lore", "have", db.getSecrets(target).size(),
                        "total", SecretAchievements.Secret.values().length)), null);

        set(15, ItemUtil.icon(Material.BOOKSHELF, msg.get("gui.profile.collection-name"),
                msg.getList("gui.profile.collection-lore", "have", db.getDoneCollections(target).size(),
                        "total", plugin.collections().all().size())), null);

        set(16, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.profile.quest-name"),
                msg.getList("gui.profile.quest-lore", "done", db.getStat(target, "quests_done"))), null);

        // ---------------- Reihe 3: Statistiken ----------------
        for (int i = 0; i < STAT_KEYS.length; i++) {
            String key = STAT_KEYS[i];
            set(STAT_SLOTS[i], ItemUtil.icon(STAT_ICONS[i], msg.get("gui.profile.stat." + key + ".name"),
                    msg.getList("gui.profile.stat-lore", "value", db.getStat(target, key),
                            "unit", plainUnit(msg, key))), null);
        }

        // ---------------- Reihe 4: Tode, Ranglisten, Investitionen ----------------
        set(29, ItemUtil.icon(Material.SKELETON_SKULL, msg.get("gui.profile.deaths-name"),
                msg.getList("gui.profile.deaths-lore", "value", db.getStat(target, "deaths"))), null);

        set(31, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.profile.ranks-name"), rankLines(target)), null);

        int ownedItems = 0;
        int totalItems = 0;
        for (SetDef set : invest.sets()) {
            totalItems += set.items().size();
            ownedItems += invest.ownedCount(set, target, owners);
        }
        set(33, ItemUtil.icon(Material.GOLD_BLOCK, msg.get("gui.profile.invest-name"),
                msg.getList("gui.profile.invest-lore", "have", ownedItems, "total", totalItems)), null);

        // ---------------- Schutzgeld: an wen zahlt der Spieler? ----------------
        List<Component> protLore = new ArrayList<>();
        List<Database.Protection> paying = db.protectionsOf(target);
        if (paying.isEmpty()) {
            protLore.add(msg.get("gui.profile.protect-none"));
        } else {
            protLore.addAll(msg.getList("gui.profile.protect-header"));
            for (Database.Protection pr : paying) {
                protLore.add(msg.get("gui.profile.protect-line", "player", db.getName(pr.offerer()),
                        "scope", plugin.protection().scopeText(pr.revier())));
            }
        }
        if (plugin.blackMarket().unlocked(viewer)) { // Schutzgeld ist nur etwas für Eingeweihte
            set(40, ItemUtil.icon(Material.WITHER_SKELETON_SKULL, msg.get("gui.profile.protect-name"), protLore), null);
        }

        // ---------------- Ecke oben links: Shop-Maskottchen (nur im eigenen Profil) ----------------
        if (viewer.getUniqueId().equals(target) && plugin.mascot().enabled()) {
            var mascot = plugin.mascot().get(target);
            List<Component> mascotLore = new ArrayList<>();
            Material mascotIcon = Material.SADDLE;
            if (mascot == null) {
                mascotLore.add(Component.text("Noch kein Maskottchen gewählt.", NamedTextColor.GRAY));
            } else {
                mascotIcon = mascot.companion().icon;
                mascotLore.add(Component.text(plugin.expeditions().trips().copyName(mascot.companion(), mascot.index()), NamedTextColor.WHITE));
                mascotLore.add(Component.text("Rabatt im Shop: " + plugin.mascot().percentOf(target, mascot) + " %", NamedTextColor.GREEN));
                mascotLore.add(Component.text("Aufgaben: " + Math.round(plugin.mascot().questPercent(target)) + " % mehr Belohnung", NamedTextColor.GREEN));
                for (String b : plugin.mascot().buffLines(mascot.companion())) {
                    mascotLore.add(Component.text("Aufgaben-Buff " + b, NamedTextColor.AQUA));
                }
                mascotLore.add(Component.text("Glück bei seltenen Funden: +" + Math.round((plugin.mascot().luck(target) - 1) * 100) + " %", NamedTextColor.GREEN));
            }
            mascotLore.add(Component.text("Als Maskottchen kann er sonst nichts tun.", NamedTextColor.RED));
            mascotLore.add(Component.text("Klick: Maskottchen wählen", NamedTextColor.GRAY));
            set(0, ItemUtil.icon(mascotIcon, Component.text("Shop-Maskottchen", NamedTextColor.GOLD), mascotLore),
                    (player, click) -> new ExpeditionCopyMenu(plugin, player, ExpeditionCopyMenu.Mode.MASCOT).open(player));
        }

        // ---------------- Ecke oben rechts: Mystery Boxen (nur im eigenen Profil) ----------------
        if (viewer.getUniqueId().equals(target) && plugin.mysteryBoxes().enabled()) {
            int stored = plugin.mysteryBoxes().total(target);
            set(8, ItemUtil.icon(Material.ENDER_CHEST, Component.text("Mystery Boxen", NamedTextColor.GOLD),
                    List.of(Component.text("Vorrat: " + stored + " Boxen", NamedTextColor.WHITE),
                            Component.text("Klick: Boxen ansehen und öffnen", NamedTextColor.GRAY))),
                    (player, click) -> new MysteryBoxMenu(plugin, player).open(player));
        }

        // ---------------- Ruhemodus (nur im eigenen Profil) ----------------
        if (viewer.getUniqueId().equals(target) && plugin.logins().restEnabled()) {
            boolean resting = plugin.logins().resting(target);
            long wait = plugin.logins().restCooldownLeft(target);
            List<Component> restLore = new ArrayList<>();
            restLore.add(Component.text("Für Urlaub oder Pausen: Deine Login-Serie reißt nicht.", NamedTextColor.GRAY));
            restLore.add(Component.text("Der Serien-Bonus ist dabei halbiert.", NamedTextColor.GRAY));
            restLore.add(Component.text("Höchstens " + plugin.getConfig().getInt("rest-mode.max-days", 30)
                    + " Tage, er endet beim nächsten Login von selbst.", NamedTextColor.GRAY));
            if (resting) {
                restLore.add(Component.text("Status: AN seit " + plugin.logins().restDay(target) + " Tagen", NamedTextColor.GREEN));
                restLore.add(Component.text("Klick: Ruhemodus beenden", NamedTextColor.YELLOW));
            } else if (wait > 0) {
                restLore.add(Component.text("Status: aus. Wieder möglich in " + wait + " Tagen.", NamedTextColor.RED));
            } else {
                restLore.add(Component.text("Status: aus", NamedTextColor.WHITE));
                restLore.add(Component.text("Klick: Ruhemodus einschalten", NamedTextColor.YELLOW));
            }
            set(38, ItemUtil.icon(resting ? Material.RED_BED : Material.WHITE_BED,
                    Component.text("Ruhemodus", resting ? NamedTextColor.GREEN : NamedTextColor.AQUA), restLore),
                    (player, click) -> {
                        plugin.logins().toggleRest(player);
                        new ProfileMenu(plugin, player, player.getUniqueId()).open(player);
                    });
        }

        // ---------------- Unten ----------------
        set(47, balanceIcon(viewer), incomeClick());
        set(49, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        fillEmpty();
    }

    /** Die Einheit einer Statistik als Text (z. B. "Blöcke"). */
    private static String plainUnit(Messages msg, String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.get("gui.profile.stat." + key + ".unit"));
    }

    /** Die besten Platzierungen in den Ranglisten (Platz 1 bis 10), beste zuerst. */
    private List<Component> rankLines(UUID target) {
        Messages msg = plugin.messages();
        RankingManager rankings = plugin.rankings();
        List<RankingManager.Placed> placed = new ArrayList<>();
        List<Rank> ranks = new ArrayList<>();
        for (Rank r : Rank.values()) {
            RankingManager.Placed info = rankings.info(r, target);
            if (info != null && info.place() <= 10) {
                placed.add(info);
                ranks.add(r);
            }
        }
        List<Component> lines = new ArrayList<>();
        if (placed.isEmpty()) {
            lines.add(msg.get("gui.profile.ranks-none"));
            return lines;
        }
        // nach Platz sortieren (Indexliste, damit Platz und Rangliste zusammenbleiben)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> placed.get(i).place()));
        for (int idx : order) {
            lines.add(msg.get("gui.profile.rank-line", "place", placed.get(idx).place(),
                    "name", msg.get("gui.rank.cat." + ranks.get(idx).id() + ".name")));
        }
        return lines;
    }
}
