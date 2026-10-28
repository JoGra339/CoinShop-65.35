package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Drogenhandel im Schwarzmarkt, verknüpft mit den Revieren: Jedes freigekaufte Revier ist ein eigener Standort mit
 * eigenem Labor, Lager, Dealern und eigener Hitze. Die Stammkunden wohnen in bestimmten Revieren. Der Unterhändler
 * (Boss) des Reviers und der Pate bekommen eine Abgabe von den Verkäufen, und der Umsatz zählt für die Revier-Rangliste.
 * Alles hier ist erfunden (Fantasie-Waren).
 */
public final class DrugTrade {

    public static final String[] PRODUCTS = {"Grünkraut", "Blaupilz", "Funkelstaub", "Nebelharz", "Glutöl"};
    public static final Material[] ICONS = {Material.FERN, Material.BROWN_MUSHROOM, Material.GLOWSTONE_DUST, Material.SLIME_BALL,
            Material.BLAZE_POWDER};
    public static final int[] BASE_PRICE = {20, 50, 120, 300, 700};
    public static final String[] QUALITIES = {"Mies", "Normal", "Gut", "Premium", "Legendär"};
    public static final double[] QUALITY_FACTOR = {0.6, 1.0, 1.5, 2.2, 3.5};
    public static final String[] ADDITIVES = {"Zucker", "Honig", "Gold-Extrakt"};
    public static final double[] ADDITIVE_COST = {1, 2, 4};
    public static final int[] ADDITIVE_CHANCE = {55, 70, 85};
    public static final int SITES = RevierManager.COUNT;

    /** Stammkunden: Name, Revier (1 bis 7), Produkte, Mindestqualität, Zahlungsfaktor, Kosten für den Kontakt. */
    public record Customer(String name, int revier, int[] products, int minQuality, double budget, long unlockCost, int demand) { }

    public static final Customer[] CUSTOMERS = {
            new Customer("Kneipen-Karl", 1, new int[]{0}, 0, 1.0, 0, 3),
            new Customer("Studentin Lea", 1, new int[]{0, 1}, 1, 1.05, 0, 5),
            new Customer("Taxifahrer Ali", 2, new int[]{1}, 1, 1.1, 2000, 4),
            new Customer("Barkeeperin Mona", 3, new int[]{1, 2}, 1, 1.15, 4000, 6),
            new Customer("Galerist Ben", 4, new int[]{2}, 2, 1.2, 8000, 3),
            new Customer("Anwältin Dr. Vogt", 5, new int[]{2, 3}, 2, 1.3, 16000, 5),
            new Customer("Clubbesitzer Rico", 6, new int[]{3}, 2, 1.4, 30000, 9),
            new Customer("Hafenboss Madame X", 7, new int[]{3, 4}, 3, 1.6, 60000, 12),
    };

    /** Eine laufende Charge. */
    public static final class Batch {
        public int product;
        public int quality;
        public long end;
        public int units;
    }

    /** Ein Standort (ein Revier). */
    private static final class Site {
        final int[][] stock = new int[PRODUCTS.length][QUALITIES.length];
        final List<Batch> batches = new ArrayList<>();
        int lab = 1;
        int dealers;
        long lastDealer;
        double heat;
        long heatAt;
        int raids;
    }

    private static final class Data {
        final Site[] sites = new Site[SITES];
        final Map<Integer, Integer> sold = new HashMap<>();
        final Map<Integer, Boolean> unlocked = new HashMap<>();
        final Map<Integer, long[]> today = new HashMap<>(); // {Tag, verkauft}
        long total;
        long fees;

        Data() {
            for (int i = 0; i < SITES; i++) {
                sites[i] = new Site();
            }
        }
    }

    private final CoinShopPlugin plugin;
    private final Map<UUID, Data> cache = new HashMap<>();
    private final Random random = new Random();

    DrugTrade(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private double cfgD(String key, double def) {
        return plugin.getConfig().getDouble("drugs." + key, def);
    }

    private int cfgI(String key, int def) {
        return plugin.getConfig().getInt("drugs." + key, def);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("drugs.enabled", true);
    }

    // ------------------------------------------------------------------
    //  Reviere
    // ------------------------------------------------------------------

    /** Standort r (0 bis 6) gehört zu Revier r + 1. Darf der Spieler dort handeln? */
    public boolean siteOpen(UUID uuid, int r) {
        return r >= 0 && r < SITES && plugin.reviere().isUnlocked(uuid, r + 1);
    }

    public String siteName(int r) {
        return plugin.reviere().name(r + 1);
    }

    public Material siteIcon(int r) {
        return plugin.reviere().icon(r + 1);
    }

    private Site site(UUID uuid, int r) {
        return data(uuid).sites[Math.max(0, Math.min(SITES - 1, r))];
    }

    // ------------------------------------------------------------------
    //  Speicher
    // ------------------------------------------------------------------

    private File file(UUID uuid) {
        File dir = new File(plugin.getDataFolder(), "drugs");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Ordner drugs konnte nicht angelegt werden.");
        }
        return new File(dir, uuid + ".yml");
    }

    private Data data(UUID uuid) {
        Data d = cache.get(uuid);
        if (d != null) {
            return d;
        }
        d = new Data();
        File f = file(uuid);
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            for (int r = 0; r < SITES; r++) {
                Site s = d.sites[r];
                String base = "sites." + r;
                for (int p = 0; p < PRODUCTS.length; p++) {
                    for (int q = 0; q < QUALITIES.length; q++) {
                        s.stock[p][q] = Math.max(0, y.getInt(base + ".stock." + p + "." + q));
                    }
                }
                ConfigurationSection b = y.getConfigurationSection(base + ".batches");
                if (b != null) {
                    for (String key : b.getKeys(false)) {
                        Batch batch = new Batch();
                        batch.product = Math.max(0, Math.min(PRODUCTS.length - 1, b.getInt(key + ".p")));
                        batch.quality = Math.max(0, Math.min(QUALITIES.length - 1, b.getInt(key + ".q")));
                        batch.end = b.getLong(key + ".end");
                        batch.units = Math.max(1, b.getInt(key + ".units", 10));
                        s.batches.add(batch);
                    }
                }
                s.lab = Math.max(1, Math.min(5, y.getInt(base + ".lab", 1)));
                s.dealers = Math.max(0, y.getInt(base + ".dealers"));
                s.lastDealer = y.getLong(base + ".last-dealer");
                s.heat = Math.max(0, y.getDouble(base + ".heat"));
                s.heatAt = y.getLong(base + ".heat-at");
                s.raids = y.getInt(base + ".raids");
            }
            for (int c = 0; c < CUSTOMERS.length; c++) {
                d.sold.put(c, y.getInt("customers." + c + ".sold"));
                d.unlocked.put(c, y.getBoolean("customers." + c + ".unlocked", CUSTOMERS[c].unlockCost() <= 0));
                d.today.put(c, new long[]{y.getLong("customers." + c + ".day"), y.getLong("customers." + c + ".today")});
            }
            d.total = y.getLong("total");
            d.fees = y.getLong("fees");
        } else {
            for (int c = 0; c < CUSTOMERS.length; c++) {
                d.unlocked.put(c, CUSTOMERS[c].unlockCost() <= 0);
            }
        }
        cache.put(uuid, d);
        return d;
    }

    private void save(UUID uuid) {
        Data d = data(uuid);
        YamlConfiguration y = new YamlConfiguration();
        for (int r = 0; r < SITES; r++) {
            Site s = d.sites[r];
            String base = "sites." + r;
            for (int p = 0; p < PRODUCTS.length; p++) {
                for (int q = 0; q < QUALITIES.length; q++) {
                    if (s.stock[p][q] > 0) {
                        y.set(base + ".stock." + p + "." + q, s.stock[p][q]);
                    }
                }
            }
            for (int i = 0; i < s.batches.size(); i++) {
                Batch b = s.batches.get(i);
                y.set(base + ".batches." + i + ".p", b.product);
                y.set(base + ".batches." + i + ".q", b.quality);
                y.set(base + ".batches." + i + ".end", b.end);
                y.set(base + ".batches." + i + ".units", b.units);
            }
            y.set(base + ".lab", s.lab);
            y.set(base + ".dealers", s.dealers);
            y.set(base + ".last-dealer", s.lastDealer);
            y.set(base + ".heat", s.heat);
            y.set(base + ".heat-at", s.heatAt);
            y.set(base + ".raids", s.raids);
        }
        for (int c = 0; c < CUSTOMERS.length; c++) {
            y.set("customers." + c + ".sold", d.sold.getOrDefault(c, 0));
            y.set("customers." + c + ".unlocked", d.unlocked.getOrDefault(c, false));
            long[] t = d.today.get(c);
            if (t != null) {
                y.set("customers." + c + ".day", t[0]);
                y.set("customers." + c + ".today", t[1]);
            }
        }
        y.set("total", d.total);
        y.set("fees", d.fees);
        try {
            y.save(file(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("Drogenhandel-Daten konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Abfragen
    // ------------------------------------------------------------------

    public int stock(UUID uuid, int r, int product, int quality) {
        return site(uuid, r).stock[product][quality];
    }

    public int stockTotal(UUID uuid, int r) {
        int sum = 0;
        for (int p = 0; p < PRODUCTS.length; p++) {
            for (int q = 0; q < QUALITIES.length; q++) {
                sum += site(uuid, r).stock[p][q];
            }
        }
        return sum;
    }

    public int lab(UUID uuid, int r) {
        return site(uuid, r).lab;
    }

    public int dealers(UUID uuid, int r) {
        return site(uuid, r).dealers;
    }

    public int raids(UUID uuid, int r) {
        return site(uuid, r).raids;
    }

    public long total(UUID uuid) {
        return data(uuid).total;
    }

    public long feesPaid(UUID uuid) {
        return data(uuid).fees;
    }

    public List<Batch> batches(UUID uuid, int r) {
        return site(uuid, r).batches;
    }

    public boolean productUnlocked(UUID uuid, int r, int product) {
        return site(uuid, r).lab >= Math.max(1, product);
    }

    public int labSlots(UUID uuid, int r) {
        return site(uuid, r).lab;
    }

    public long labUpgradeCost(UUID uuid, int r) {
        int lab = site(uuid, r).lab;
        return Math.max(1, Math.round(cfgD("lab-upgrade-base", 5000) * lab * lab * (1 + 0.5 * r)));
    }

    public long batchCost(int product) {
        return Math.max(1, Math.round(BASE_PRICE[product] * cfgI("batch-units", 10) * cfgD("batch-cost-factor", 0.6)));
    }

    public int batchMinutes(int product) {
        return Math.max(1, (int) Math.round(cfgD("production-minutes", 30) * (1 + product * 0.5)));
    }

    public long mixCost(int product, int additive) {
        return Math.max(1, Math.round(BASE_PRICE[product] * ADDITIVE_COST[additive] * cfgD("mix-cost-factor", 1.5)));
    }

    public int mixChance(int quality, int additive) {
        return Math.max(10, ADDITIVE_CHANCE[additive] - 10 * quality);
    }

    public int mixUnits() {
        return Math.max(1, cfgI("mix-units", 5));
    }

    public int bossCutPercent() {
        return Math.max(0, cfgI("boss-cut-percent", 5));
    }

    public int pateCutPercent() {
        return Math.max(0, cfgI("pate-cut-percent", 2));
    }

    // ------------------------------------------------------------------
    //  Hitze
    // ------------------------------------------------------------------

    public double heat(UUID uuid, int r) {
        Site s = site(uuid, r);
        long now = System.currentTimeMillis();
        if (s.heatAt > 0 && s.heat > 0) {
            double hours = (now - s.heatAt) / 3_600_000.0;
            s.heat = Math.max(0, s.heat - hours * cfgD("heat-decay-per-hour", 6));
        }
        s.heatAt = now;
        return s.heat;
    }

    public String heatState(double heat) {
        if (heat < 30) {
            return "Ruhig";
        }
        if (heat < 60) {
            return "Aufmerksam";
        }
        if (heat < 85) {
            return "Gefährlich";
        }
        return "Razzia-Gefahr";
    }

    private void addHeat(UUID uuid, int r, double amount) {
        heat(uuid, r);
        Site s = site(uuid, r);
        s.heat = Math.min(100, s.heat + amount);
    }

    public long bribeCost(UUID uuid, int r) {
        return Math.max(1, Math.round(cfgD("bribe-cost", 2000) * site(uuid, r).lab));
    }

    public void bribe(Player player, int r) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        long cost = bribeCost(uuid, r);
        if (heat(uuid, r) < 5) {
            msg.send(player, "drug-cool");
            return;
        }
        if (!spend(player, cost, "Schmiergeld in " + siteName(r))) {
            return;
        }
        Site s = site(uuid, r);
        s.heat = Math.max(0, s.heat - cfgD("bribe-heat", 30));
        save(uuid);
        msg.send(player, "drug-bribed", "cost", cost, "heat", Math.round(s.heat));
    }

    /** Nach einem Verkauf: kann es in diesem Revier eine Razzia geben? */
    private void maybeRaid(Player player, int r) {
        UUID uuid = player.getUniqueId();
        double heat = heat(uuid, r);
        double chance = Math.max(0, (heat - 50)) * cfgD("raid-chance-per-heat", 0.6);
        if (random.nextDouble() * 100 >= chance) {
            return;
        }
        Site s = site(uuid, r);
        double lossShare = Math.max(0, cfgD("raid-loss-percent", 40)) / 100.0;
        int lost = 0;
        for (int p = 0; p < PRODUCTS.length; p++) {
            for (int q = 0; q < QUALITIES.length; q++) {
                int l = (int) Math.floor(s.stock[p][q] * lossShare);
                s.stock[p][q] -= l;
                lost += l;
            }
        }
        long fine = Math.min(plugin.database().getCoins(uuid), Math.round(cfgD("raid-fine", 800) * s.lab));
        boolean fined = false;
        if (fine > 0) {
            Database db = plugin.database();
            db.begin();
            try {
                if (db.takeCoins(uuid, fine)) {
                    db.addShopAccount(fine);
                    db.commit();
                    fined = true;
                } else {
                    db.rollback();
                }
            } catch (RuntimeException e) {
                db.rollback();
                plugin.getLogger().severe("Drogenhandel: Strafe fehlgeschlagen: " + e.getMessage());
            }
            if (fined) {
                db.logTx(uuid, player.getName(), "drogen-kosten", -fine, "Razzia in " + siteName(r) + ": Strafe");
            }
        }
        if (!fined) {
            fine = 0;
        }
        boolean dealerGone = false;
        if (s.dealers > 0 && random.nextInt(100) < cfgI("raid-dealer-percent", 25)) {
            s.dealers--;
            dealerGone = true;
        }
        s.heat = Math.min(s.heat, cfgD("raid-heat-after", 30));
        s.raids++;
        save(uuid);
        plugin.messages().send(player, "drug-raid", "lost", lost, "fine", fine, "revier", siteName(r));
        if (dealerGone) {
            plugin.messages().send(player, "drug-raid-dealer");
        }
    }

    // ------------------------------------------------------------------
    //  Zahlen
    // ------------------------------------------------------------------

    private boolean spend(Player player, long cost, String why) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        db.begin();
        try {
            if (!db.takeCoins(uuid, cost)) {
                db.rollback();
                plugin.messages().send(player, "drug-no-money", "price", cost);
                return false;
            }
            db.addShopAccount(cost);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Drogenhandel: Zahlung fehlgeschlagen: " + e.getMessage());
            plugin.messages().send(player, "error");
            return false;
        }
        db.logTx(uuid, player.getName(), "drogen-kosten", -cost, why);
        return true;
    }

    /**
     * Zahlt den Erlös eines Verkaufs in einem Revier aus: Der Unterhändler des Reviers und der Pate bekommen ihre Abgabe
     * (vom Erlös des Verkäufers, nicht vom Shop-Konto), der Rest gehört dem Verkäufer. Der Umsatz zählt für die Revier-Rangliste.
     */
    private long earn(Player player, int r, long gross, String why) {
        if (gross <= 0) {
            return 0;
        }
        UUID uuid = player.getUniqueId();
        Database db = plugin.database();
        RevierManager rv = plugin.reviere();
        UUID boss = rv.boss(r + 1);
        UUID pate = rv.pate();
        long bossCut = boss != null && !boss.equals(uuid) ? gross * bossCutPercent() / 100 : 0;
        long pateCut = pate != null && !pate.equals(uuid) && !pate.equals(boss) ? gross * pateCutPercent() / 100 : 0;
        long net = gross - bossCut - pateCut;
        db.addCoins(uuid, net);
        db.logTx(uuid, player.getName(), "drogen-erloes", net, why);
        db.addRevierSale(r + 1, uuid, gross);
        if (bossCut > 0) {
            db.addCoins(boss, bossCut);
            db.logTx(boss, db.getName(boss), "drogen-abgabe", bossCut, "Abgabe aus dem Drogenhandel in " + siteName(r));
            Player online = Bukkit.getPlayer(boss);
            if (online != null) {
                plugin.messages().send(online, "drug-cut-boss", "coins", bossCut, "revier", siteName(r), "name", player.getName());
            }
        }
        if (pateCut > 0) {
            db.addCoins(pate, pateCut);
            db.logTx(pate, db.getName(pate), "drogen-abgabe", pateCut, "Abgabe aus dem Drogenhandel in " + siteName(r));
            Player online = Bukkit.getPlayer(pate);
            if (online != null) {
                plugin.messages().send(online, "drug-cut-pate", "coins", pateCut, "revier", siteName(r), "name", player.getName());
            }
        }
        Data d = data(uuid);
        d.total += net;
        d.fees += bossCut + pateCut;
        return bossCut + pateCut;
    }

    // ------------------------------------------------------------------
    //  Herstellung
    // ------------------------------------------------------------------

    private int rollQuality(int lab) {
        int r = random.nextInt(100) + lab * cfgI("lab-quality-bonus", 5);
        if (r < 20) {
            return 0;
        }
        if (r < 75) {
            return 1;
        }
        if (r < 95) {
            return 2;
        }
        return r < 110 ? 3 : 4;
    }

    public void startBatch(Player player, int r, int product) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        Site s = site(uuid, r);
        if (!siteOpen(uuid, r)) {
            return;
        }
        if (!productUnlocked(uuid, r, product)) {
            msg.send(player, "drug-locked", "lab", Math.max(1, product));
            return;
        }
        if (s.batches.size() >= labSlots(uuid, r)) {
            msg.send(player, "drug-lab-full");
            return;
        }
        if (!spend(player, batchCost(product), "Charge " + PRODUCTS[product] + " in " + siteName(r))) {
            return;
        }
        Batch b = new Batch();
        b.product = product;
        b.quality = rollQuality(s.lab);
        b.units = Math.max(1, cfgI("batch-units", 10));
        b.end = System.currentTimeMillis() + batchMinutes(product) * 60_000L;
        s.batches.add(b);
        save(uuid);
        msg.send(player, "drug-batch-started", "product", PRODUCTS[product], "minutes", batchMinutes(product));
    }

    public int finishedBatches(UUID uuid, int r) {
        int n = 0;
        long now = System.currentTimeMillis();
        for (Batch b : site(uuid, r).batches) {
            if (b.end <= now) {
                n++;
            }
        }
        return n;
    }

    public void collectBatches(Player player, int r) {
        UUID uuid = player.getUniqueId();
        Site s = site(uuid, r);
        long now = System.currentTimeMillis();
        int units = 0;
        List<Batch> done = new ArrayList<>();
        for (Batch b : s.batches) {
            if (b.end <= now) {
                s.stock[b.product][b.quality] += b.units;
                units += b.units;
                done.add(b);
            }
        }
        if (done.isEmpty()) {
            plugin.messages().send(player, "drug-nothing");
            return;
        }
        s.batches.removeAll(done);
        save(uuid);
        plugin.messages().send(player, "drug-collected", "units", units, "batches", done.size());
    }

    public void upgradeLab(Player player, int r) {
        UUID uuid = player.getUniqueId();
        Site s = site(uuid, r);
        if (s.lab >= 5) {
            plugin.messages().send(player, "drug-lab-max");
            return;
        }
        long cost = labUpgradeCost(uuid, r);
        if (!spend(player, cost, "Labor-Stufe " + (s.lab + 1) + " in " + siteName(r))) {
            return;
        }
        s.lab++;
        save(uuid);
        plugin.messages().send(player, "drug-lab-up", "lab", s.lab, "cost", cost);
    }

    // ------------------------------------------------------------------
    //  Mischen
    // ------------------------------------------------------------------

    public void mix(Player player, int r, int product, int quality, int additive) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        Site s = site(uuid, r);
        if (quality >= QUALITIES.length - 1) {
            msg.send(player, "drug-mix-max");
            return;
        }
        int units = mixUnits();
        if (s.stock[product][quality] < units) {
            msg.send(player, "drug-mix-few", "units", units);
            return;
        }
        if (!spend(player, mixCost(product, additive), "Mischen " + PRODUCTS[product])) {
            return;
        }
        if (random.nextInt(100) < mixChance(quality, additive)) {
            s.stock[product][quality] -= units;
            s.stock[product][quality + 1] += units;
            msg.send(player, "drug-mix-ok", "product", PRODUCTS[product], "quality", QUALITIES[quality + 1], "units", units);
        } else {
            msg.send(player, "drug-mix-fail", "additive", ADDITIVES[additive]);
        }
        save(uuid);
    }

    // ------------------------------------------------------------------
    //  Kunden (jeder wohnt in einem Revier)
    // ------------------------------------------------------------------

    public List<Integer> customersOf(int r) {
        List<Integer> list = new ArrayList<>();
        for (int c = 0; c < CUSTOMERS.length; c++) {
            if (CUSTOMERS[c].revier() == r + 1) {
                list.add(c);
            }
        }
        return list;
    }

    public boolean customerUnlocked(UUID uuid, int c) {
        return data(uuid).unlocked.getOrDefault(c, false);
    }

    public int loyalty(UUID uuid, int c) {
        return Math.min(10, data(uuid).sold.getOrDefault(c, 0) / Math.max(1, cfgI("loyalty-units", 25)));
    }

    public int demandLeft(UUID uuid, int c) {
        long day = LocalDate.now().toEpochDay();
        long[] t = data(uuid).today.get(c);
        long sold = t == null || t[0] != day ? 0 : t[1];
        return (int) Math.max(0, dailyDemand(uuid, c) - sold);
    }

    /**
     * Wie viel ein Kunde heute insgesamt nimmt: jeder Kunde hat seine eigene Grundmenge (Rico nimmt mehr als Karl),
     * dazu schwankt sie von Tag zu Tag um bis zu 30 % (fest für Spieler, Kunde und Tag) und wächst mit der Treue.
     */
    public int dailyDemand(UUID uuid, int c) {
        double scale = Math.max(0.1, cfgI("demand-base", 3) / 3.0);
        double swing = 0.7 + 0.6 * new Random(java.util.Objects.hash(uuid, c, LocalDate.now().toEpochDay())).nextDouble();
        return Math.max(1, (int) Math.round(CUSTOMERS[c].demand() * scale * swing) + loyalty(uuid, c) / 2);
    }

    public double priceFactor(UUID uuid, int c) {
        return CUSTOMERS[c].budget() * (1 + 0.04 * loyalty(uuid, c));
    }

    public void unlockCustomer(Player player, int c) {
        UUID uuid = player.getUniqueId();
        Data d = data(uuid);
        if (d.unlocked.getOrDefault(c, false)) {
            return;
        }
        if (!siteOpen(uuid, CUSTOMERS[c].revier() - 1)) {
            return;
        }
        if (!spend(player, CUSTOMERS[c].unlockCost(), "Kontakt " + CUSTOMERS[c].name())) {
            return;
        }
        d.unlocked.put(c, true);
        save(uuid);
        plugin.messages().send(player, "drug-customer-new", "name", CUSTOMERS[c].name());
    }

    /** Verkauft an einen Stammkunden, so viel er heute noch nimmt (bestes zuerst), aus dem Lager seines Reviers. */
    public void sellTo(Player player, int c) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        Customer cu = CUSTOMERS[c];
        int r = cu.revier() - 1;
        Data d = data(uuid);
        Site s = d.sites[r];
        if (!customerUnlocked(uuid, c) || !siteOpen(uuid, r)) {
            return;
        }
        int left = demandLeft(uuid, c);
        if (left <= 0) {
            msg.send(player, "drug-demand-done", "name", cu.name());
            return;
        }
        long coins = 0;
        int units = 0;
        double heat = 0;
        for (int q = QUALITIES.length - 1; q >= cu.minQuality() && left > 0; q--) {
            for (int p : cu.products()) {
                int n = Math.min(left, s.stock[p][q]);
                if (n <= 0) {
                    continue;
                }
                s.stock[p][q] -= n;
                left -= n;
                units += n;
                coins += Math.round(n * BASE_PRICE[p] * QUALITY_FACTOR[q] * priceFactor(uuid, c));
                heat += n * (cfgD("heat-per-unit", 0.5) + p * 0.3);
            }
        }
        if (units == 0) {
            msg.send(player, "drug-no-goods", "name", cu.name(), "revier", siteName(r));
            return;
        }
        long day = LocalDate.now().toEpochDay();
        long[] t = d.today.get(c);
        long before = t == null || t[0] != day ? 0 : t[1];
        d.today.put(c, new long[]{day, before + units});
        d.sold.merge(c, units, Integer::sum);
        long cut = earn(player, r, coins, "Verkauf an " + cu.name() + " (" + units + " Einheiten, " + siteName(r) + ")");
        addHeat(uuid, r, heat);
        save(uuid);
        msg.send(player, "drug-sold", "name", cu.name(), "units", units, "coins", coins - cut, "cut", cut);
        maybeRaid(player, r);
    }

    // ------------------------------------------------------------------
    //  Dealer (je Revier)
    // ------------------------------------------------------------------

    public long dealerCost(UUID uuid, int r) {
        return Math.max(1, Math.round(cfgD("dealer-hire", 8000) * (site(uuid, r).dealers + 1)));
    }

    public int dealerMax() {
        return Math.max(0, cfgI("dealer-max", 5));
    }

    public int dealerUnitsPerHour() {
        return Math.max(1, cfgI("dealer-units-per-hour", 6));
    }

    public void hireDealer(Player player, int r) {
        UUID uuid = player.getUniqueId();
        Site s = site(uuid, r);
        if (!siteOpen(uuid, r)) {
            return;
        }
        if (s.dealers >= dealerMax()) {
            plugin.messages().send(player, "drug-dealer-max", "max", dealerMax());
            return;
        }
        settleDealers(player, r, false);
        long cost = dealerCost(uuid, r);
        if (!spend(player, cost, "Dealer angestellt in " + siteName(r))) {
            return;
        }
        s.dealers++;
        if (s.lastDealer <= 0) {
            s.lastDealer = System.currentTimeMillis();
        }
        save(uuid);
        plugin.messages().send(player, "drug-dealer-hired", "count", s.dealers, "cost", cost);
    }

    public void fireDealer(Player player, int r) {
        UUID uuid = player.getUniqueId();
        Site s = site(uuid, r);
        if (s.dealers <= 0) {
            return;
        }
        settleDealers(player, r, false);
        s.dealers--;
        save(uuid);
        plugin.messages().send(player, "drug-dealer-fired", "count", s.dealers);
    }

    /** Wie viel haben die Dealer des Reviers seit dem letzten Mal verkauft? Verkauft vom Lager (schlechteste Qualität zuerst). */
    public void settleDealers(Player player, int r, boolean report) {
        UUID uuid = player.getUniqueId();
        Site s = site(uuid, r);
        long now = System.currentTimeMillis();
        if (s.dealers <= 0 || !siteOpen(uuid, r)) {
            s.lastDealer = now;
            return;
        }
        if (s.lastDealer <= 0) {
            s.lastDealer = now;
        }
        double hours = Math.min(Math.max(1, cfgD("dealer-max-hours", 24)), (now - s.lastDealer) / 3_600_000.0);
        int capacity = (int) Math.floor(hours * s.dealers * dealerUnitsPerHour());
        if (capacity <= 0) {
            if (report) {
                plugin.messages().send(player, "drug-dealer-nothing");
            }
            return;
        }
        long gross = 0;
        int units = 0;
        double heat = 0;
        for (int q = 0; q < QUALITIES.length && capacity > 0; q++) {
            for (int p = 0; p < PRODUCTS.length && capacity > 0; p++) {
                int n = Math.min(capacity, s.stock[p][q]);
                if (n <= 0) {
                    continue;
                }
                s.stock[p][q] -= n;
                capacity -= n;
                units += n;
                gross += Math.round(n * BASE_PRICE[p] * QUALITY_FACTOR[q]);
                heat += n * cfgD("dealer-heat-per-unit", 0.2);
            }
        }
        s.lastDealer = now;
        if (units == 0) {
            if (report) {
                plugin.messages().send(player, "drug-dealer-nothing");
            }
            save(uuid);
            return;
        }
        long afterDealers = Math.round(gross * (1 - Math.max(0, cfgD("dealer-commission-percent", 25)) / 100.0));
        long cut = earn(player, r, afterDealers, "Dealer-Verkauf (" + units + " Einheiten, " + siteName(r) + ")");
        addHeat(uuid, r, heat);
        save(uuid);
        plugin.messages().send(player, "drug-dealer-sold", "units", units, "coins", afterDealers - cut, "revier", siteName(r));
        maybeRaid(player, r);
    }
}
