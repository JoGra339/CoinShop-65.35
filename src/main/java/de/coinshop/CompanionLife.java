package de.coinshop;

import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Das Leben der Begleiter neben den Reisen: Aufträge im Großhandel (je nach Level und Branche), Zucht (Eltern vererben
 * Eigenschaften und Erfahrung), Fusion und Veredelung.
 */
public final class CompanionLife {

    /** Fusionsrezepte: Art A + Art B ergibt Art C (A und B dürfen in beliebiger Reihenfolge stehen). */
    private static final String[][] RECIPES = {
            {"zombie", "skeleton", "witherskeleton"},
            {"zombie", "drowned", "husk"},
            {"pig", "zombie", "zombifiedpiglin"},
            {"hoglin", "zombie", "zoglin"},
            {"blaze", "magmacube", "ghast"},
            {"irongolem", "polarbear", "ravager"},
            {"vindicator", "pillager", "evoker"},
            {"evoker", "vex", "illusioner"},
            {"guardian", "squid", "elderguardian"},
            {"enderman", "endermite", "shulker"},
            {"wolf", "cat", "fox"},
            {"spider", "cavespider", "creaking"},
            {"witherskeleton", "ghast", "wither"},
            {"piglin", "hoglin", "piglinbrute"},
            {"horse", "skeleton", "skeletonhorse"},
            {"horse", "zombie", "zombiehorse"},
            {"horse", "donkey", "mule"},
    };

    private final CoinShopPlugin plugin;
    private final ExpeditionManager ex;

    CompanionLife(CoinShopPlugin plugin, ExpeditionManager ex) {
        this.plugin = plugin;
        this.ex = ex;
    }

    private double cfgD(String key, double def) {
        return plugin.getConfig().getDouble("expedition." + key, def);
    }

    private int cfgI(String key, int def) {
        return plugin.getConfig().getInt("expedition." + key, def);
    }

    // ------------------------------------------------------------------
    //  Großhandel: Begleiter bearbeiten Aufträge
    // ------------------------------------------------------------------

    /** Kreaturen, die zum Thema der Branche besonders gut passen (Faktor 1,6). Reihenfolge wie WholesaleManager.SECTORS. */
    private static final String[][] STRONG = {
            // Landwirtschaft (Feldfrüchte und Saatgut)
            {"villager", "cow", "mooshroom", "pig", "rabbit", "bee", "parrot", "sheep", "chicken", "allay", "wanderingtrader"},
            // Bergbau (Erze und Gestein)
            {"irongolem", "creeper", "silverfish", "endermite", "warden", "magmacube", "piglin", "dolphinless", "ravager", "vindicator"},
            // Forstwirtschaft (Hölzer)
            {"panda", "fox", "ocelot", "cat", "creaking", "sniffer", "allay", "spider", "cavespider", "bee", "camel"},
            // Viehzucht (Tierprodukte)
            {"wolf", "horse", "donkey", "mule", "llama", "traderllama", "cow", "sheep", "pig", "chicken", "goat", "camel", "rabbit", "cat"},
            // Baustoffe (Steine, Ziegel, Glas)
            {"irongolem", "snowgolem", "ravager", "villager", "shulker", "creeper", "silverfish", "pillager", "vindicator", "armadillo"},
            // Fischerei (Fische und Meeresgüter)
            {"squid", "glowsquid", "dolphin", "guardian", "elderguardian", "turtle", "axolotl", "cod", "salmon", "pufferfish",
                    "tropicalfish", "drowned", "frog", "polarbear"},
            // Textil und Farben (Fäden, Farben, Papier)
            {"sheep", "spider", "cavespider", "llama", "traderllama", "parrot", "silverfish", "allay", "wanderingtrader", "witch", "bat"},
            // Nether (Netherwaren)
            {"blaze", "ghast", "hoglin", "piglin", "piglinbrute", "zoglin", "strider", "magmacube", "witherskeleton", "wither",
                    "zombifiedpiglin"},
            // Alchemie (Tränke und Zutaten)
            {"witch", "evoker", "illusioner", "vex", "slime", "bat", "bee", "enderman", "allay", "cat", "frog", "creaking"},
            // Bäckerei (Backwaren)
            {"bee", "chicken", "cow", "mooshroom", "villager", "rabbit", "pig", "sheep", "allay", "wanderingtrader"},
    };

    /** Welche Typen zu einer Branche passen (Faktor 1,0). */
    private static final CompanionTypes.MobType[][] FITS = {
            {CompanionTypes.MobType.NATUR},
            {CompanionTypes.MobType.ERDE, CompanionTypes.MobType.FEUER},
            {CompanionTypes.MobType.NATUR},
            {CompanionTypes.MobType.NATUR},
            {CompanionTypes.MobType.ERDE},
            {CompanionTypes.MobType.WASSER, CompanionTypes.MobType.EIS},
            {CompanionTypes.MobType.NATUR, CompanionTypes.MobType.LUFT},
            {CompanionTypes.MobType.FEUER, CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.UNTOTE},
            {CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.LUFT},
            {CompanionTypes.MobType.NATUR, CompanionTypes.MobType.FEUER},
    };

    /** Kompatibilität einer Kreatur mit einer Branche: 1,6 (Experte), 1,0 (passt) oder 0,5 (fachfremd). */
    public double compat(Companion c, int sector) {
        if (sector < 0 || sector >= STRONG.length) {
            return 0.5;
        }
        String key = c.key.replace("_", "").toLowerCase(java.util.Locale.ROOT);
        for (String s : STRONG[sector]) {
            if (s.equals(key)) {
                return Math.max(1.0, cfgD("wholesale.compat-strong", 1.6));
            }
        }
        for (CompanionTypes.MobType t : FITS[sector]) {
            if (c.mobType() == t) {
                return 1.0;
            }
        }
        return Math.max(0.1, cfgD("wholesale.compat-weak", 0.5));
    }

    // ------------------------------------------------------------------
    //  Passung zu Tagesaufgaben (für das Maskottchen)
    // ------------------------------------------------------------------

    /** Welche Aufgabenart zu welcher Branche gehört (Reihenfolge der Branchen wie WholesaleManager.SECTORS). */
    private static final java.util.Map<String, Integer> QUEST_SECTOR = java.util.Map.ofEntries(
            java.util.Map.entry("mined", 1), java.util.Map.entry("stone", 1), java.util.Map.entry("diamonds", 1),
            java.util.Map.entry("debris", 1), java.util.Map.entry("wood", 2), java.util.Map.entry("fishing", 5),
            java.util.Map.entry("harvest", 0), java.util.Map.entry("bred", 3), java.util.Map.entry("placed", 4),
            java.util.Map.entry("brewed", 8), java.util.Map.entry("enchanted", 8));

    private static final String[] FIGHT_STRONG = {"wolf", "irongolem", "ravager", "vindicator", "piglinbrute", "warden", "blaze",
            "zoglin", "hoglin", "vex", "wither", "witherskeleton", "evoker", "pillager", "zombie", "skeleton", "enderman"};
    private static final String[] WALK_STRONG = {"horse", "donkey", "mule", "camel", "llama", "traderllama", "strider", "fox",
            "goat", "rabbit", "ocelot", "allay", "parrot", "bat", "bee", "wolf"};
    private static final String[] TRADE_STRONG = {"villager", "wanderingtrader", "traderllama", "allay", "parrot", "cat", "piglin"};

    /** Wie gut passt die Kreatur zu dieser Aufgabenart? 2 = Experte, 1 = passt, 0 = fachfremd. */
    public int questFit(Companion c, String stat) {
        Integer sector = QUEST_SECTOR.get(stat);
        if (sector != null) {
            double compat = compat(c, sector);
            return compat >= 1.5 ? 2 : compat >= 1.0 ? 1 : 0;
        }
        String[] strong;
        CompanionTypes.MobType[] fits;
        switch (stat) {
            case "creatures" -> {
                strong = FIGHT_STRONG;
                fits = new CompanionTypes.MobType[] {CompanionTypes.MobType.FEUER, CompanionTypes.MobType.FINSTER, CompanionTypes.MobType.UNTOTE};
            }
            case "walked" -> {
                strong = WALK_STRONG;
                fits = new CompanionTypes.MobType[] {CompanionTypes.MobType.LUFT, CompanionTypes.MobType.NATUR};
            }
            case "trades" -> {
                strong = TRADE_STRONG;
                fits = new CompanionTypes.MobType[] {CompanionTypes.MobType.LUFT};
            }
            default -> {
                return 0;
            }
        }
        String key = c.key.replace("_", "").toLowerCase(java.util.Locale.ROOT);
        for (String s : strong) {
            if (s.equals(key)) {
                return 2;
            }
        }
        for (CompanionTypes.MobType t : fits) {
            if (c.mobType() == t) {
                return 1;
            }
        }
        return 0;
    }

    public String compatLabel(double compat) {
        if (compat >= 1.5) {
            return "Experte";
        }
        return compat >= 1.0 ? "Passt" : "Fachfremd";
    }

    /** Wie viele Begleiter dürfen gleichzeitig im Großhandel arbeiten? */
    public int wholesaleLimit(UUID uuid) {
        return Integer.MAX_VALUE; // beliebig viele Begleiter dürfen im Großhandel arbeiten
    }

    public List<Copy> assigned(UUID uuid) {
        List<Copy> list = new ArrayList<>();
        for (Copy k : ex.allCopies(uuid)) {
            if (ex.roster().work(uuid, k.companion(), k.index()) != null) {
                list.add(k);
            }
        }
        return list;
    }

    /** Items pro Tag: Grundmenge mal Level, Kompatibilität, Seltenheit und Eigenschaften. */
    public long dailyItems(UUID uuid, Companion c, int idx, int sector) {
        double base = Math.max(0, cfgD("wholesale.items-per-day", 6));
        double level = 1 + cfgD("wholesale.level-bonus", 0.08) * (ex.copyLevel(uuid, c, idx) - 1);
        double rar = ex.rarity(uuid, c, idx).stats;
        double trait = 1;
        for (CompanionTraits.Trait t : ex.traits(uuid, c, idx)) {
            trait += t.work;
        }
        return Math.max(0, Math.round(base * level * compat(c, sector) * rar * Math.max(0.2, trait)));
    }

    /** Ordnet ein Exemplar einem Großhandels-Auftrag des Spielers zu. */
    public void assign(Player player, Copy k, long orderId) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        if (ex.isIn(uuid) || !ex.exists(uuid, k.companion(), k.index())) {
            return;
        }
        Database.WsOrder order = plugin.database().getWsOrder(orderId);
        if (order == null || !order.owner().equals(uuid) || order.delivered() >= order.amount()) {
            msg.send(player, "ex-ws-order-gone");
            return;
        }
        boolean already = ex.roster().work(uuid, k.companion(), k.index()) != null;
        String busy = already ? null : ex.busyReason(uuid, k.companion(), k.index());
        if (busy != null) {
            msg.send(player, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busy);
            return;
        }
        ex.roster().setTaken(uuid, k.companion(), k.index(), false);
        ex.roster().setWork(uuid, k.companion(), k.index(), orderId, System.currentTimeMillis());
        ex.roster().save(uuid);
        msg.send(player, "ex-ws-assigned", "name", ex.trips().copyName(k.companion(), k.index()),
                "items", dailyItems(uuid, k.companion(), k.index(), order.sector()),
                "sector", WholesaleManager.SECTORS.get(order.sector()).name());
    }

    public void unassign(Player player, Copy k) {
        UUID uuid = player.getUniqueId();
        ex.roster().clearWork(uuid, k.companion(), k.index());
        ex.roster().save(uuid);
        plugin.messages().send(player, "ex-ws-unassigned", "name", ex.trips().copyName(k.companion(), k.index()));
    }

    /** Täglich beim Shop-Wechsel: Alle zugeteilten Begleiter liefern ihre Tagesmenge an ihren Auftrag. */
    public void dailyWholesale() {
        File dir = new File(plugin.getDataFolder(), "companions");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        Database db = plugin.database();
        for (File f : files) {
            UUID uuid;
            try {
                uuid = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
            } catch (IllegalArgumentException e) {
                continue;
            }
            try {
                boolean changed = false;
                for (Copy k : assigned(uuid)) {
                    CompanionRoster.Work w = ex.roster().work(uuid, k.companion(), k.index());
                    Database.WsOrder order = w == null ? null : db.getWsOrder(w.order);
                    if (order == null || !order.owner().equals(uuid) || order.delivered() >= order.amount()) {
                        ex.roster().clearWork(uuid, k.companion(), k.index());
                        changed = true;
                        continue;
                    }
                    if (ex.busyReason(uuid, k.companion(), k.index(), true) != null) {
                        continue; // verletzt, auf Reise oder Maskottchen: liefert heute nichts
                    }
                    // Gegen "kurz vor dem Shop-Wechsel zuteilen": geliefert wird anteilig nach der Arbeitszeit (bis zu einem
                    // vollen Tag), und wer noch keine Mindestzeit gearbeitet hat, liefert noch nichts (die Zeit bleibt gutgeschrieben).
                    double hours = Math.max(0, (System.currentTimeMillis() - w.since) / 3_600_000.0);
                    double minHours = Math.max(0, cfgD("wholesale.min-hours", 6));
                    if (hours < minHours) {
                        Player waiting = Bukkit.getPlayer(uuid);
                        if (waiting != null) {
                            plugin.messages().send(waiting, "ex-ws-training", "name", ex.trips().copyName(k.companion(), k.index()),
                                    "hours", Math.round(hours), "min", Math.round(minHours));
                        }
                        continue;
                    }
                    double share = Math.min(1.0, hours / Math.max(1.0, cfgD("wholesale.full-day-hours", 24)));
                    long want = Math.round(dailyItems(uuid, k.companion(), k.index(), order.sector()) * share);
                    long made = Math.min(want, Math.max(0, order.amount() - order.delivered()));
                    if (made <= 0) {
                        continue;
                    }
                    plugin.wholesale().aiDeliver(uuid, order, want);
                    ex.roster().setWork(uuid, k.companion(), k.index(), order.id(), System.currentTimeMillis()); // nächster Arbeitstag beginnt
                    int levelBefore = ex.copyLevel(uuid, k.companion(), k.index());
                    int xp = Math.max(0, cfgI("wholesale.xp-per-day", 3));
                    ex.roster().addKills(uuid, k.companion(), k.index(), xp); // Arbeit bringt auch ein wenig Erfahrung
                    int levelAfter = ex.copyLevel(uuid, k.companion(), k.index());
                    changed = true;
                    Player owner = Bukkit.getPlayer(uuid);
                    if (owner != null) {
                        plugin.messages().send(owner, "ex-ws-delivered", "name", ex.trips().copyName(k.companion(), k.index()),
                                "items", made, "item", order.material());
                        if (xp > 0) {
                            owner.sendMessage(net.kyori.adventure.text.Component.text("   +" + xp + " Erfahrung für die Arbeit.",
                                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
                        }
                        if (levelAfter > levelBefore) {
                            plugin.messages().send(owner, "ex-comp-levelup", "name", ex.trips().copyName(k.companion(), k.index()),
                                    "level", levelAfter);
                        }
                    }
                }
                if (changed) {
                    ex.roster().save(uuid);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Begleiter im Großhandel (" + uuid + "): " + e);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Zucht
    // ------------------------------------------------------------------

    /** Ergebnis einer Zucht oder Fusion: Art, Seltenheit, Eigenschaften, Erfahrung. */
    public record Offspring(Companion companion, int rarity, int generation, List<String> traits, long kills) { }

    public boolean breedPossible(UUID uuid, Copy a, Copy b) {
        if (a.equals(b) || !ex.exists(uuid, a.companion(), a.index()) || !ex.exists(uuid, b.companion(), b.index())) {
            return false;
        }
        return ex.busyReason(uuid, a.companion(), a.index()) == null && ex.busyReason(uuid, b.companion(), b.index()) == null;
    }

    public long breedCost(Copy a, Copy b) {
        double avg = (a.companion().defaultPrice + b.companion().defaultPrice) / 2.0;
        return Math.max(1, Math.round(Math.max(0, cfgD("breed.cost-base", 1500)) + avg * Math.max(0, cfgD("breed.cost-percent", 20)) / 100.0));
    }

    /** Vorschau (ohne Zufall): welche Art, Mindest-Seltenheit, Startlevel. */
    public String breedPreview(UUID uuid, Copy a, Copy b) {
        String species = a.companion() == b.companion() ? a.companion().label : a.companion().label + " oder " + b.companion().label;
        int gen = Math.max(ex.info(uuid, a.companion(), a.index()).generation, ex.info(uuid, b.companion(), b.index()).generation) + 1;
        long kills = Math.round((ex.roster().kills(uuid, a.companion(), a.index()) + ex.roster().kills(uuid, b.companion(), b.index()))
                / 2.0 * Math.max(0, cfgD("breed.inherit-percent", 30)) / 100.0);
        return species + ", Generation " + gen + ", Startlevel " + ex.levelForKills(kills);
    }

    public void breed(Player player, Copy a, Copy b) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (ex.isIn(uuid) || !breedPossible(uuid, a, b)) {
            msg.send(player, "ex-copy-none");
            return;
        }
        long cost = breedCost(a, b);
        Random r = new Random();
        Companion child = a.companion() == b.companion() || r.nextBoolean() ? a.companion() : b.companion();
        CompanionRoster.Info ia = ex.info(uuid, a.companion(), a.index());
        CompanionRoster.Info ib = ex.info(uuid, b.companion(), b.index());
        int rarity = Math.max(ia.rarity, ib.rarity);
        if (rarity < CompanionTraits.Rarity.LEGENDAER.ordinal() && r.nextInt(100) < cfgI("breed.rarity-up-percent", 15)) {
            rarity++;
        } else if (rarity > 0 && r.nextInt(100) < cfgI("breed.rarity-down-percent", 25)) {
            rarity--;
        }
        List<String> traits = new ArrayList<>();
        List<String> pool = new ArrayList<>(ia.traits);
        pool.addAll(ib.traits);
        for (String t : pool) {
            if (!traits.contains(t) && r.nextBoolean() && traits.size() < 3) {
                traits.add(t);
            }
        }
        if (traits.size() < 3 && r.nextInt(100) < cfgI("breed.new-trait-percent", 20)) {
            List<CompanionTraits.Trait> rolled = CompanionTraits.rollTraits(r, CompanionTraits.Rarity.of(rarity));
            if (!rolled.isEmpty() && !traits.contains(rolled.get(0).name())) {
                traits.add(rolled.get(0).name());
            }
        }
        long kills = Math.round((ex.roster().kills(uuid, a.companion(), a.index()) + ex.roster().kills(uuid, b.companion(), b.index()))
                / 2.0 * Math.max(0, cfgD("breed.inherit-percent", 30)) / 100.0);
        int gen = Math.max(ia.generation, ib.generation) + 1;
        db.begin();
        try {
            if (!db.takeCoins(uuid, cost)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", cost);
                return;
            }
            db.addShopAccount(cost);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Zucht fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        int idx = createCopy(uuid, new Offspring(child, rarity, gen, traits, kills));
        long rest = ex.restUntilShopReset(); // Eltern ruhen bis zum nächsten Shopwechsel
        ex.roster().setRest(uuid, a.companion(), a.index(), rest);
        ex.roster().setRest(uuid, b.companion(), b.index(), rest);
        ex.roster().setTaken(uuid, a.companion(), a.index(), false);
        ex.roster().setTaken(uuid, b.companion(), b.index(), false);
        ex.roster().save(uuid);
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -cost, "Zucht " + child.label + " Nr. " + (idx + 1));
        msg.send(player, "ex-breed-done", "name", ex.trips().copyName(child, idx), "rarity", CompanionTraits.Rarity.of(rarity).label,
                "gen", gen, "cost", cost);
    }

    /** Legt ein neues Exemplar an (zusätzlich zu den gekauften) und gibt seine Nummer zurück. */
    public int createCopy(UUID uuid, Offspring o) {
        int idx = ex.companionOwned(uuid, o.companion());
        ex.roster().addExtra(uuid, o.companion());
        CompanionRoster.Info info = new CompanionRoster.Info();
        info.rarity = o.rarity();
        info.generation = o.generation();
        info.variant = CompanionVariants.roll(new java.util.Random(), o.companion().type);
        info.traits.addAll(o.traits());
        ex.roster().setInfo(uuid, o.companion(), idx, info);
        if (o.kills() > 0) {
            ex.roster().addKills(uuid, o.companion(), idx, o.kills());
        }
        return idx;
    }

    // ------------------------------------------------------------------
    //  Fusion und Veredelung
    // ------------------------------------------------------------------

    /** Die Art, die aus A und B entsteht (null = keine Fusion möglich). Zwei Exemplare derselben Art ergeben eine Veredelung. */
    public Companion fusionResult(Companion a, Companion b) {
        if (a == b) {
            return a;
        }
        for (String[] r : RECIPES) {
            boolean match = (a.key.equals(r[0]) && b.key.equals(r[1])) || (a.key.equals(r[1]) && b.key.equals(r[0]));
            if (match) {
                return Companion.byKey(r[2]);
            }
        }
        return null;
    }

    /** Alle Rezepte, die es in dieser Version gibt (für die Anzeige). */
    public List<String> recipeTexts() {
        List<String> list = new ArrayList<>();
        for (String[] r : RECIPES) {
            Companion a = Companion.byKey(r[0]);
            Companion b = Companion.byKey(r[1]);
            Companion c = Companion.byKey(r[2]);
            if (a != null && b != null && c != null) {
                list.add(a.label + " + " + b.label + " = " + c.label);
            }
        }
        return list;
    }

    public long fusionCost(Companion result) {
        return Math.max(1, Math.round(result.defaultPrice * Math.max(0, cfgD("fusion.cost-percent", 10)) / 100.0));
    }

    public boolean fusionPossible(UUID uuid, Copy a, Copy b) {
        if (!breedPossible(uuid, a, b)) {
            return false;
        }
        Companion result = fusionResult(a.companion(), b.companion());
        if (result == null) {
            return false;
        }
        if (a.companion() == b.companion()) {
            return ex.info(uuid, a.companion(), a.index()).rarity < CompanionTraits.Rarity.LEGENDAER.ordinal()
                    || ex.info(uuid, b.companion(), b.index()).rarity < CompanionTraits.Rarity.LEGENDAER.ordinal();
        }
        return true;
    }

    public void fuse(Player player, Copy a, Copy b) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (ex.isIn(uuid) || !fusionPossible(uuid, a, b)) {
            msg.send(player, "ex-fusion-none");
            return;
        }
        Companion result = fusionResult(a.companion(), b.companion());
        boolean refine = a.companion() == b.companion();
        long cost = refine ? Math.max(1, Math.round(a.companion().defaultPrice * Math.max(0, cfgD("fusion.refine-cost-percent", 15)) / 100.0))
                : fusionCost(result);
        CompanionRoster.Info ia = ex.info(uuid, a.companion(), a.index());
        CompanionRoster.Info ib = ex.info(uuid, b.companion(), b.index());
        int rarity = Math.max(ia.rarity, ib.rarity);
        if (refine) {
            rarity = Math.min(CompanionTraits.Rarity.LEGENDAER.ordinal(), rarity + 1);
        }
        List<String> traits = new ArrayList<>(ia.traits);
        for (String t : ib.traits) {
            if (!traits.contains(t) && traits.size() < 3) {
                traits.add(t);
            }
        }
        long kills = Math.round((ex.roster().kills(uuid, a.companion(), a.index()) + ex.roster().kills(uuid, b.companion(), b.index()))
                * Math.max(0, cfgD("fusion.inherit-percent", 50)) / 100.0);
        int gen = Math.max(ia.generation, ib.generation);
        db.begin();
        try {
            if (!db.takeCoins(uuid, cost)) {
                db.rollback();
                msg.send(player, "ex-no-money", "price", cost);
                return;
            }
            db.addShopAccount(cost);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Expedition: Fusion fehlgeschlagen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        ex.roster().markGone(uuid, a.companion(), a.index());
        ex.roster().markGone(uuid, b.companion(), b.index());
        int idx = createCopy(uuid, new Offspring(result, rarity, gen, traits, kills));
        ex.roster().save(uuid);
        db.logTx(uuid, player.getName(), "expedition-ausruestung", -cost, (refine ? "Veredelung " : "Fusion ") + result.label + " Nr. " + (idx + 1));
        msg.send(player, refine ? "ex-refine-done" : "ex-fusion-done", "name", ex.trips().copyName(result, idx),
                "rarity", CompanionTraits.Rarity.of(rarity).label, "cost", cost);
    }
}
