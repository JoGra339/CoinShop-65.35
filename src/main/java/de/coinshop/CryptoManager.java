package de.coinshop;

import de.coinshop.Database.CryptoHolding;
import de.coinshop.Database.MinerQuest;
import de.coinshop.gui.CryptoMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.GameMode;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Krypto: eine Währung mit stark schwankendem Kurs (1 bis 10.000 Coins), der sich täglich zum Shop-Wechsel ändert,
 * dazu die "Habibi-Krypto"-Variante in der Hinterhof-Börse des Schwarzmarkts (nur mit Schmuggler-Lizenz,
 * noch wilder und mit Rug-Pull-Risiko). Mengen werden in Hundertsteln gespeichert (100 = 1,00 Krypto).
 *
 * Miner: Man kann bis zu 32 verschiedene Miner kaufen (progressiv teurer, jeder bis Stufe 10). Ein Miner gibt eine
 * Quest vor: viele verschiedene Blöcke müssen abgebaut werden. Danach gibt es Krypto. Eine Quest bleibt bestehen, bis sie geschafft ist; danach gibt es pro Tag eine neue.
 * Je höher die Stufe, desto weniger Blöcke sind nötig.
 */
public final class CryptoManager implements CommandExecutor, Listener {

    public static final String KRYPTO = "krypto";
    public static final String DUNKEL = "dunkel";
    public static final int MINERS = 32;
    public static final int MAX_LEVEL = 10;

    public record BuyQuote(long units, long paid, long fee) { }
    public record SellQuote(long gross, long fee, long tax, long payout) { }

    private static final String[] MINER_NAMES = {
            "Kupfer-Kumpel", "Staubfresser", "Kiesbagger", "Erdbohrer", "Steinbeißer", "Funkenwühler", "Eisenzahn",
            "Tiefenläufer", "Goldgräber", "Lapis-Lurch", "Rotstein-Rakete", "Schachtfuchs", "Granitbrecher",
            "Dioritdrache", "Tuffkönig", "Maulwurf 3000", "Hashmaster", "Blockchain-Bert", "Nonce-Nick",
            "Satoshi-Sammler", "Bitwühler", "Kernschmelzer", "Minenmeister", "Diamantsauger", "Smaragd-Sultan",
            "Obsidian-Ofen", "Lava-Lord", "Netherknacker", "Basalt-Baron", "Schattenschürfer", "Quantenpicke",
            "Der Große Miner"};

    /** Blöcke der Oberwelt, die man abbauen kann. */
    private static final String[] POOL_OVERWORLD = {
            "STONE", "DIRT", "GRASS_BLOCK", "SAND", "RED_SAND", "GRAVEL", "ANDESITE", "DIORITE", "GRANITE", "DEEPSLATE",
            "TUFF", "CALCITE", "CLAY", "SANDSTONE", "SNOW_BLOCK", "ICE", "MUD", "PODZOL", "COARSE_DIRT", "MOSS_BLOCK",
            "COAL_ORE", "IRON_ORE", "COPPER_ORE", "GOLD_ORE", "REDSTONE_ORE", "LAPIS_ORE", "DIAMOND_ORE", "EMERALD_ORE",
            "DEEPSLATE_COAL_ORE", "DEEPSLATE_IRON_ORE", "DEEPSLATE_COPPER_ORE", "DEEPSLATE_GOLD_ORE",
            "DEEPSLATE_REDSTONE_ORE", "DEEPSLATE_LAPIS_ORE", "DEEPSLATE_DIAMOND_ORE", "DEEPSLATE_EMERALD_ORE",
            "OAK_LOG", "BIRCH_LOG", "SPRUCE_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG",
            "CHERRY_LOG", "DRIPSTONE_BLOCK", "TERRACOTTA", "MYCELIUM", "PUMPKIN", "MELON", "OAK_LEAVES"};

    /** Zusätzlich für die höheren Miner: Nether-Blöcke. */
    private static final String[] POOL_NETHER = {
            "NETHERRACK", "SOUL_SAND", "SOUL_SOIL", "NETHER_QUARTZ_ORE", "NETHER_GOLD_ORE", "ANCIENT_DEBRIS", "BASALT",
            "BLACKSTONE", "MAGMA_BLOCK", "GLOWSTONE", "CRIMSON_STEM", "WARPED_STEM", "NETHER_WART_BLOCK",
            "WARPED_WART_BLOCK", "GILDED_BLACKSTONE"};

    private final CoinShopPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Map<Integer, MinerQuest>> questCache = new HashMap<>();
    /** Vom Spieler gesetzte Blöcke (zählen nicht für Quests). */
    private final Set<String> placed = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 60000;
        }
    });

    public CryptoManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        for (String kind : new String[]{KRYPTO, DUNKEL}) {
            if (plugin.database().getCryptoHistory(kind, 1).isEmpty()) {
                plugin.database().setMetaLong("crypto_price_" + kind, startPrice(kind));
                plugin.database().addCryptoHistory(kind, startPrice(kind));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Kurse
    // ------------------------------------------------------------------

    public long minPrice() {
        return Math.max(1, plugin.getConfig().getLong("crypto.min-price", 1));
    }

    public long maxPrice() {
        return maxPrice(KRYPTO);
    }

    /** Höchstkurs je Währung (Habibi-Krypto darf höher steigen). */
    public long maxPrice(String kind) {
        long fallback = plugin.getConfig().getLong("crypto.max-price", 10000);
        return Math.max(minPrice(), plugin.getConfig().getLong("crypto." + kind + ".max-price", fallback));
    }

    private long startPrice(String kind) {
        return clamp(kind, plugin.getConfig().getLong("crypto." + kind + ".start-price", kind.equals(KRYPTO) ? 500 : 300));
    }

    private long clamp(String kind, long price) {
        return Math.max(minPrice(), Math.min(maxPrice(kind), price));
    }

    public long price(String kind) {
        return clamp(kind, plugin.database().getMetaLong("crypto_price_" + kind, startPrice(kind)));
    }

    public List<Long> history(String kind, int days) {
        return plugin.database().getCryptoHistory(kind, days);
    }

    public long previousPrice(String kind) {
        List<Long> h = history(kind, 2);
        return h.size() >= 2 ? h.get(1) : price(kind);
    }

    public String nameOf(String kind) {
        return kind.equals(DUNKEL) ? "Habibi-Krypto" : "Krypto";
    }

    /** Täglicher Kurswechsel (zusammen mit dem Shop-Reset, nach den Ereignissen). */
    public void updatePrices() {
        for (String kind : new String[]{KRYPTO, DUNKEL}) {
            try {
                long old = price(kind);
                double vol = Math.max(0, plugin.getConfig().getDouble("crypto." + kind + ".volatility",
                        kind.equals(KRYPTO) ? 0.9 : 1.3));
                double anchor = Math.max(1, plugin.getConfig().getDouble("crypto." + kind + ".anchor-price",
                        kind.equals(KRYPTO) ? 600 : 500));
                double reversion = Math.max(0, plugin.getConfig().getDouble("crypto." + kind + ".reversion", 0.06));
                double ln = Math.log(old);
                double next = Math.exp(ln + reversion * (Math.log(anchor) - ln) + random.nextGaussian() * vol);
                boolean rug = false;
                boolean moon = false;
                int wipe = 0;
                if (kind.equals(DUNKEL)
                        && random.nextInt(100) < plugin.getConfig().getInt("crypto.dunkel.rugpull-percent", 5)) {
                    // Harte Folgen: Kurs fällt auf den Mindestpreis, ein Teil aller Bestände ist weg
                    next = minPrice();
                    rug = true;
                    wipe = Math.max(0, Math.min(100, plugin.getConfig().getInt("crypto.dunkel.rugpull-wipe-percent", 50)));
                    plugin.database().wipeCryptoHoldings(kind, wipe);
                } else if (kind.equals(DUNKEL)
                        && random.nextInt(100) < plugin.getConfig().getInt("crypto.dunkel.moon-percent", 12)) {
                    // Mondflug: der Kurs springt nach oben (macht die Habibi-Krypto attraktiv)
                    next = next * Math.max(1.0, plugin.getConfig().getDouble("crypto.dunkel.moon-multiplier", 2.0));
                    moon = true;
                }
                long price = clamp(kind, Math.round(next));
                plugin.database().setMetaLong("crypto_price_" + kind, price);
                plugin.database().addCryptoHistory(kind, price);
                plugin.getLogger().info(nameOf(kind) + ": neuer Kurs " + price + " (vorher " + old + ")");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (kind.equals(DUNKEL) && !plugin.blackMarket().unlocked(online)) {
                        continue;
                    }
                    if (moon) {
                        plugin.messages().send(online, "crypto-moon", "name", nameOf(kind), "price", price);
                    } else if (rug) {
                        plugin.messages().send(online, "crypto-rugpull", "name", nameOf(kind), "price", price, "wipe", wipe);
                    } else {
                        plugin.messages().send(online, price >= old ? "crypto-update-up" : "crypto-update-down",
                                "name", nameOf(kind), "price", price, "diff", Math.abs(price - old));
                    }
                }
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Krypto-Kurswechsel fehlgeschlagen: " + e);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Gebühren und Berechnung (Mengen in Hundertsteln)
    // ------------------------------------------------------------------

    /** Habibi-Krypto: Gebühr wie im Schwarzmarkt (Gebührenfrei und Bestechung wirken). Krypto: wie die Börse. */
    private long feeOf(UUID uuid, String kind, long amount) {
        if (kind.equals(DUNKEL)) {
            return plugin.blackMarket().feeFor(uuid, amount, plugin.blackMarket().feePercent());
        }
        return (long) Math.floor(amount * normalFeePercent(uuid) / 100.0);
    }

    /** Gebühr der normalen Krypto (höher als bei den Aktien; Börsenhändler und Ereignisse wirken). */
    private double normalFeePercent(UUID uuid) {
        return plugin.stock().feePercent(uuid, Math.max(0, plugin.getConfig().getDouble("crypto.fee-percent", 10.0)));
    }

    public double feePercent(UUID uuid, String kind) {
        if (kind.equals(DUNKEL)) {
            return plugin.blackMarket().feePercent() * plugin.events().feeFactor()
                    * (1.0 - Math.min(100.0, plugin.licenses().value(uuid, "gebuehrenfrei")) / 100.0);
        }
        return normalFeePercent(uuid);
    }

    public BuyQuote buyQuote(UUID uuid, String kind, long coins) {
        if (coins < 1) {
            return new BuyQuote(0, 0, 0);
        }
        long price = price(kind);
        long fee = feeOf(uuid, kind, coins);
        long net = coins - fee;
        long units = net * 100 / price;
        if (units < 1) {
            return new BuyQuote(0, 0, fee);
        }
        long cost = (units * price + 99) / 100;
        return new BuyQuote(units, cost + fee, fee);
    }

    public long minInvest(UUID uuid, String kind) {
        long x = 1;
        while (buyQuote(uuid, kind, x).units() < 1 && x < 1_000_000) {
            x++;
        }
        return x;
    }

    public SellQuote sellQuote(UUID uuid, String kind, long units) {
        if (units < 1) {
            return new SellQuote(0, 0, 0, 0);
        }
        long gross = units * price(kind) / 100;
        long fee = feeOf(uuid, kind, gross);
        long tax = 0;
        CryptoHolding h = holding(uuid, kind);
        // Auf die Habibi-Krypto (Schwarzmarkt) zahlt man keine Steuer, nur die Gebühr
        if (!kind.equals(DUNKEL) && h.units() > 0 && plugin.getConfig().getBoolean("crypto.tax-enabled", true)) {
            long investedPart = h.invested() * Math.min(units, h.units()) / h.units();
            tax = plugin.stock().taxOn(uuid, gross - fee - investedPart);
        }
        return new SellQuote(gross, fee, tax, gross - fee - tax);
    }

    public CryptoHolding holding(UUID uuid, String kind) {
        return plugin.database().getCryptoHolding(uuid, kind);
    }

    /** 150 -> "1,50" */
    public static String fmt(long units) {
        return String.format(java.util.Locale.GERMANY, "%d,%02d", units / 100, units % 100);
    }

    private boolean allowed(Player player, String kind) {
        Messages msg = plugin.messages();
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return false;
        }
        if (kind.equals(DUNKEL) && !plugin.blackMarket().allowed(player)) {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Kaufen und Verkaufen
    // ------------------------------------------------------------------

    public void buy(Player player, String kind, long coins) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!allowed(player, kind)) {
            return;
        }
        if (coins < 1 || coins > 1_000_000_000_000L) {
            msg.send(player, "ask-invalid");
            return;
        }
        BuyQuote q = buyQuote(uuid, kind, coins);
        if (q.units() < 1) {
            msg.send(player, "crypto-too-little", "name", nameOf(kind), "min", minInvest(uuid, kind));
            return;
        }
        long price = price(kind);
        db.begin();
        try {
            if (!db.takeCoins(uuid, q.paid())) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            if (q.fee() > 0) {
                db.addShopAccount(q.fee());
                if (kind.equals(DUNKEL)) {
                    plugin.reviere().payPate(q.fee());
                }
            }
            CryptoHolding h = db.getCryptoHolding(uuid, kind);
            db.setCryptoHolding(uuid, kind, h.units() + q.units(), h.invested() + (q.paid() - q.fee()));
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Krypto-Kauf: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), kind + "-kauf", -q.paid(), fmt(q.units()) + " zu " + price);
        msg.send(player, "crypto-bought", "name", nameOf(kind), "units", fmt(q.units()), "price", price,
                "paid", q.paid(), "fee", q.fee());
    }

    public void sell(Player player, String kind, long units) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!allowed(player, kind)) {
            return;
        }
        CryptoHolding h = db.getCryptoHolding(uuid, kind);
        if (units < 1) {
            msg.send(player, "ask-invalid");
            return;
        }
        if (h.units() < 1) {
            msg.send(player, "crypto-none", "name", nameOf(kind));
            return;
        }
        if (units > h.units()) {
            msg.send(player, "crypto-not-enough", "name", nameOf(kind), "units", fmt(h.units()));
            return;
        }
        SellQuote q = sellQuote(uuid, kind, units);
        long left = h.units() - units;
        long investedLeft = left <= 0 ? 0 : h.invested() - h.invested() * units / h.units();
        db.begin();
        try {
            db.setCryptoHolding(uuid, kind, left, investedLeft);
            db.addCoins(uuid, q.payout());
            if (q.fee() + q.tax() > 0) {
                db.addShopAccount(q.fee() + q.tax());
                if (kind.equals(DUNKEL) && q.fee() > 0) {
                    plugin.reviere().payPate(q.fee());
                }
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Krypto-Verkauf: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), kind + "-verkauf", q.payout(), fmt(units) + " zu " + price(kind));
        msg.send(player, "crypto-sold", "name", nameOf(kind), "units", fmt(units), "price", price(kind),
                "coins", q.payout(), "fee", q.fee(), "tax", q.tax());
    }

    public void sellAll(Player player, String kind) {
        long units = holding(player.getUniqueId(), kind).units();
        if (units < 1) {
            plugin.messages().send(player, "crypto-none", "name", nameOf(kind));
            return;
        }
        sell(player, kind, units);
    }

    private static Long parseCoins(String text) {
        try {
            return Long.parseLong(text.replace(".", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "1,5" oder "2" Krypto -> Hundertstel. */
    public static Long parseUnits(String text) {
        try {
            BigDecimal d = new BigDecimal(text.trim().replace(',', '.'));
            return d.multiply(BigDecimal.valueOf(100)).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void startBuyPrompt(Player player, String kind) {
        Messages msg = plugin.messages();
        player.closeInventory();
        msg.send(player, "crypto-buy-prompt", "name", nameOf(kind), "min", minInvest(player.getUniqueId(), kind),
                "price", price(kind));
        plugin.prompts().start(player, text -> {
            Long value = parseCoins(text);
            if (value == null) {
                msg.send(player, text.equalsIgnoreCase("abbrechen") ? "ask-cancelled" : "ask-invalid");
                return;
            }
            buy(player, kind, value);
            new CryptoMenu(plugin, player, kind).open(player);
        });
    }

    public void startSellPrompt(Player player, String kind) {
        Messages msg = plugin.messages();
        long owned = holding(player.getUniqueId(), kind).units();
        if (owned < 1) {
            msg.send(player, "crypto-none", "name", nameOf(kind));
            return;
        }
        player.closeInventory();
        msg.send(player, "crypto-sell-prompt", "name", nameOf(kind), "units", fmt(owned));
        plugin.prompts().start(player, text -> {
            Long value = parseUnits(text);
            if (value == null) {
                msg.send(player, text.equalsIgnoreCase("abbrechen") ? "ask-cancelled" : "ask-invalid");
                return;
            }
            sell(player, kind, value);
            new CryptoMenu(plugin, player, kind).open(player);
        });
    }

    // ------------------------------------------------------------------
    //  Miner
    // ------------------------------------------------------------------

    public String minerName(int idx) {
        return MINER_NAMES[Math.max(1, Math.min(MINERS, idx)) - 1];
    }

    public long minerPrice(int idx) {
        double base = Math.max(1, plugin.getConfig().getDouble("crypto.miner.base-price", 2000));
        double factor = Math.max(1.0, plugin.getConfig().getDouble("crypto.miner.price-factor", 2.0));
        return Math.round(base * Math.pow(factor, idx - 1) * plugin.events().inflation());
    }

    /** Preis, um von Stufe "level" auf "level + 1" zu kommen. */
    public long levelPrice(int idx, int level) {
        double f = Math.max(0.01, plugin.getConfig().getDouble("crypto.miner.level-price-factor", 0.4));
        return Math.max(1, Math.round(minerPrice(idx) * f * level));
    }

    /** So viele verschiedene Blocksorten verlangt der Miner (je höherer Miner, desto mehr Sorten). */
    public int blocksFor(int idx, int level) {
        int n = plugin.getConfig().getInt("crypto.miner.blocks-base", 5) + idx / 4;
        return Math.max(3, Math.min(poolFor(idx).size(), n));
    }

    /** Wie viel Prozent der Grundmenge pro Sorte auf dieser Stufe nötig sind (je höher die Stufe, desto weniger). */
    public int amountPercent(int idx, int level) {
        double cut = Math.max(0, plugin.getConfig().getDouble("crypto.miner.level-reduction", 0.06));
        double perMiner = Math.max(0, plugin.getConfig().getDouble("crypto.miner.amount-per-miner", 0.02));
        double p = (1.0 + perMiner * idx) * Math.max(0.2, 1.0 - cut * (level - 1));
        return (int) Math.round(p * 100);
    }

    /** Grundmenge einer Blocksorte (Erde und Stein viel, Erze wenig, Antiker Schutt sehr wenig). */
    private int baseAmount(Material m, Random rnd) {
        String n = m.name();
        if (n.equals("ANCIENT_DEBRIS")) {
            return 4 + rnd.nextInt(5);
        }
        if (n.contains("EMERALD_ORE") || n.contains("DIAMOND_ORE")) {
            return 8 + rnd.nextInt(13);
        }
        if (n.endsWith("_ORE")) {
            return 30 + rnd.nextInt(31);
        }
        if (n.endsWith("_LOG") || n.endsWith("_STEM")) {
            return 60 + rnd.nextInt(61);
        }
        if (n.endsWith("LEAVES")) {
            return 150 + rnd.nextInt(101);
        }
        if (n.equals("PUMPKIN") || n.equals("MELON") || n.equals("GLOWSTONE") || n.endsWith("_WART_BLOCK")) {
            return 20 + rnd.nextInt(21);
        }
        return 80 + rnd.nextInt(171);
    }

    // Eintrag der Quest: "BLOCK:Menge" (Ziel) bzw. "BLOCK:Fortschritt". Alte Einträge ohne Zahl zählen als 1 bzw. erledigt.
    public static String typeOf(String entry) {
        int i = entry.indexOf(':');
        return i < 0 ? entry : entry.substring(0, i);
    }

    public static int numberOf(String entry, int fallback) {
        int i = entry.indexOf(':');
        if (i < 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(entry.substring(i + 1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int required(MinerQuest q, String type) {
        for (String b : q.blocks()) {
            if (typeOf(b).equals(type)) {
                return Math.max(1, numberOf(b, 1));
            }
        }
        return 0;
    }

    public static int progress(MinerQuest q, String type) {
        for (String d : q.done()) {
            if (typeOf(d).equals(type)) {
                return d.indexOf(':') < 0 ? required(q, type) : Math.min(numberOf(d, 0), required(q, type));
            }
        }
        return 0;
    }

    public static int completedCount(MinerQuest q) {
        int n = 0;
        for (String b : q.blocks()) {
            if (progress(q, typeOf(b)) >= required(q, typeOf(b))) {
                n++;
            }
        }
        return n;
    }

    /**
     * Belohnung einer Miner-Quest in Hundertsteln Krypto (ohne Ereignisse): Grundwert (reward-base) mal Minerwachstum
     * (reward-growth je Miner) mal Stufenfaktor (level-multipliers, Stufe 1 bis 10).
     */
    public long baseReward(int idx, int level) {
        double base = Math.max(1, plugin.getConfig().getDouble("crypto.miner.reward-base", 16));
        double growth = Math.max(1.0, plugin.getConfig().getDouble("crypto.miner.reward-growth", 1.5));
        List<Double> mult = plugin.getConfig().getDoubleList("crypto.miner.level-multipliers");
        int l = Math.max(1, Math.min(MAX_LEVEL, level));
        double m = mult.size() >= l ? mult.get(l - 1) : 1.0 + 0.2 * (l - 1);
        return Math.max(1, Math.round(base * Math.pow(growth, Math.max(0, idx - 1)) * m));
    }

    /** Durchschnittliche Belohnung dieses Miners auf dieser Stufe (mit Titel und Ereignissen). */
    public long reward(UUID uuid, int idx, int level) {
        return payout(uuid, baseReward(idx, level));
    }

    /** Aus dem Grundwert einer Quest (Hundertstel Krypto) wird mit Titel und Ereignissen die Auszahlung. */
    public long payout(UUID uuid, long base) {
        double titleBonus = 1.0 + plugin.titles().effect(uuid, "miner-reward", "") / 100.0;
        double aiBonus = 1.0 + aiBonusPercent(uuid) / 100.0;
        return Math.max(1, Math.round(base * titleBonus * aiBonus * plugin.events().earnings()));
    }

    /** Firma und Krypto: jeder KI-Mitarbeiter der eigenen Firma beschleunigt die Miner (Prozent mehr bei der Miner-Quest). */
    public double aiBonusPercent(UUID uuid) {
        double each = Math.max(0, plugin.getConfig().getDouble("company.ai-miner-bonus-percent", 3.0));
        double max = Math.max(0, plugin.getConfig().getDouble("company.ai-miner-bonus-max", 15.0));
        if (each <= 0 || plugin.companies().company(uuid) == null) {
            return 0;
        }
        return Math.min(max, each * plugin.database().getAis(uuid).size());
    }

    /** Wie aufwendig ist ein Block? (Erze zählen mehr als Erde, Laub weniger.) */
    private static double workWeight(Material m) {
        String n = m.name();
        if (n.equals("ANCIENT_DEBRIS")) {
            return 40;
        }
        if (n.contains("EMERALD_ORE") || n.contains("DIAMOND_ORE")) {
            return 12;
        }
        if (n.endsWith("_ORE")) {
            return 4;
        }
        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.equals("PUMPKIN") || n.equals("MELON")
                || n.equals("GLOWSTONE") || n.endsWith("_WART_BLOCK")) {
            return 2;
        }
        if (n.endsWith("LEAVES")) {
            return 0.5;
        }
        return 1;
    }

    /**
     * Grundwert der Belohnung einer bestimmten Quest: Der Durchschnittswert des Miners (baseReward) wird mit dem
     * Aufwand der Quest verrechnet. Eine Quest mit vielen Erzen bringt mehr als eine mit viel Erde und Laub
     * (zwischen 60 % und 180 % des Durchschnitts).
     */
    public long questReward(int idx, int level, List<String> blocks) {
        double work = 0;
        for (String b : blocks) {
            Material m = Material.matchMaterial(typeOf(b));
            if (m != null) {
                work += numberOf(b, 1) * workWeight(m);
            }
        }
        double factor = Math.max(0.2, amountPercent(idx, level) / 100.0);
        double reference = Math.max(1, blocks.size()) * 160.0 * factor;
        double ratio = Math.max(0.6, Math.min(1.8, work / reference));
        return Math.max(1, Math.round(baseReward(idx, level) * ratio));
    }

    private List<Material> poolFor(int idx) {
        List<Material> pool = new ArrayList<>();
        for (String name : POOL_OVERWORLD) {
            Material m = Material.matchMaterial(name);
            if (m != null && m.isItem()) {
                pool.add(m);
            }
        }
        if (idx >= 12) {
            for (String name : POOL_NETHER) {
                Material m = Material.matchMaterial(name);
                if (m != null && m.isItem()) {
                    pool.add(m);
                }
            }
        }
        return pool;
    }

    public int minerLevel(UUID uuid, int idx) {
        return plugin.database().getMinerLevel(uuid, idx);
    }

    public int ownedMiners(UUID uuid) {
        return plugin.database().countMiners(uuid);
    }

    /** Der Tag wechselt zur Shop-Wechsel-Uhrzeit. */
    public long today() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return now.minusHours(plugin.serverShop().resetHour()).toLocalDate().toEpochDay();
    }

    public void buyMiner(Player player, int idx) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return;
        }
        if (idx < 1 || idx > MINERS || minerLevel(uuid, idx) > 0) {
            return;
        }
        if (idx > 1 && minerLevel(uuid, idx - 1) < 1) {
            msg.send(player, "miner-order");
            return;
        }
        long price = minerPrice(idx);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(price);
            db.setMinerLevel(uuid, idx, 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "miner-kauf", -price, "Miner " + idx);
        msg.send(player, "miner-bought", "name", minerName(idx), "price", price);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    public void upgradeMiner(Player player, int idx) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        int level = minerLevel(uuid, idx);
        if (level < 1) {
            return;
        }
        if (level >= MAX_LEVEL) {
            msg.send(player, "miner-maxed");
            return;
        }
        long price = levelPrice(idx, level);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(price);
            db.setMinerLevel(uuid, idx, level + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "miner-stufe", -price, "Miner " + idx + " Stufe " + (level + 1));
        msg.send(player, "miner-upgraded", "name", minerName(idx), "level", level + 1,
                "blocks", blocksFor(idx, level + 1));
    }

    // ------------------------------------------------------------------
    //  Die tägliche Quest
    // ------------------------------------------------------------------

    /** Alle Quests eines Spielers (je Miner eine). */
    private Map<Integer, MinerQuest> quests(UUID uuid) {
        Map<Integer, MinerQuest> m = questCache.get(uuid);
        if (m == null) {
            m = new HashMap<>();
            for (MinerQuest q : plugin.database().getMinerQuests(uuid)) {
                m.put(q.miner(), q);
            }
            questCache.put(uuid, m);
        }
        return m;
    }

    public MinerQuest quest(UUID uuid, int miner) {
        return quests(uuid).get(miner);
    }

    /**
     * Gibt es für diesen Miner eine Quest, die angezeigt wird? Eine angefangene Quest bleibt bestehen (auch über Tage),
     * bis sie geschafft ist. Eine geschaffte Quest zählt nur am selben Tag (danach gibt es eine neue).
     */
    public boolean questToday(UUID uuid, int miner) {
        MinerQuest q = quest(uuid, miner);
        return q != null && (!q.finished() || q.day() == today());
    }

    /** Alle laufenden bzw. heute erledigten Miner-Quests, nach Miner sortiert. */
    public List<MinerQuest> activeQuests(UUID uuid) {
        List<MinerQuest> list = new ArrayList<>();
        for (MinerQuest q : quests(uuid).values()) {
            if (!q.finished() || q.day() == today()) {
                list.add(q);
            }
        }
        list.sort(java.util.Comparator.comparingInt(MinerQuest::miner));
        return list;
    }

    public void startQuest(Player player, int idx) {
        Messages msg = plugin.messages();
        UUID uuid = player.getUniqueId();
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return;
        }
        int level = minerLevel(uuid, idx);
        if (level < 1) {
            return;
        }
        if (questToday(uuid, idx)) {
            MinerQuest open = quest(uuid, idx);
            msg.send(player, open != null && !open.finished() ? "miner-open" : "miner-once");
            return;
        }
        List<Material> pool = poolFor(idx);
        Collections.shuffle(pool, new Random(uuid.hashCode() * 31L + today() * 7919L + idx));
        int n = blocksFor(idx, level);
        Random rnd = new Random(uuid.hashCode() * 17L + today() * 104729L + idx);
        double factor = amountPercent(idx, level) / 100.0;
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < n && i < pool.size(); i++) {
            int amount = Math.max(1, (int) Math.round(baseAmount(pool.get(i), rnd) * factor));
            blocks.add(pool.get(i).name() + ":" + amount);
        }
        MinerQuest q = new MinerQuest(uuid, idx, today(), blocks, new ArrayList<>(), false, questReward(idx, level, blocks));
        plugin.database().saveMinerQuest(q);
        quests(uuid).put(idx, q);
        msg.send(player, "miner-started", "name", minerName(idx), "blocks", blocks.size(), "percent", amountPercent(idx, level));
    }

    private String key(org.bukkit.block.Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        placed.add(key(event.getBlockPlaced()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String k = key(event.getBlock());
        if (placed.remove(k)) {
            return; // selbst gesetzter Block
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || plugin.timeline().previewFor(player)) {
            return;
        }
        // jeder Miner hat seine eigene Quest; alle laufenden zählen gleichzeitig mit
        for (MinerQuest q : new ArrayList<>(quests(player.getUniqueId()).values())) {
            if (!q.finished()) {
                countBlock(player, q, event.getBlock().getType());
            }
        }
    }

    private void countBlock(Player player, MinerQuest q, Material blockType) {
        String type = blockType.name();
        int need = required(q, type);
        int have = progress(q, type);
        if (need == 0 || have >= need) {
            return;
        }
        // Titel-Wirkung "miner-double": mit einer Chance zählt der Block doppelt
        int step = 1;
        double doubleChance = plugin.titles().effect(player.getUniqueId(), "miner-double", "");
        if (doubleChance > 0 && random.nextInt(100) < doubleChance) {
            step = 2;
        }
        int now = Math.min(need, have + step);
        List<String> done = new ArrayList<>();
        for (String d : q.done()) {
            if (!typeOf(d).equals(type)) {
                done.add(typeOf(d) + ":" + progress(q, typeOf(d)));
            }
        }
        done.add(type + ":" + now);
        MinerQuest check = new MinerQuest(q.uuid(), q.miner(), q.day(), q.blocks(), done, false, q.reward());
        boolean finished = completedCount(check) >= q.blocks().size();
        long reward = 0;
        if (finished) {
            long base = q.reward() > 0 ? q.reward()
                    : baseReward(q.miner(), Math.max(1, minerLevel(player.getUniqueId(), q.miner())));
            reward = payout(player.getUniqueId(), base);
        }
        MinerQuest updated = new MinerQuest(q.uuid(), q.miner(), q.day(), q.blocks(), done, finished,
                q.reward() > 0 ? q.reward() : reward);
        if (finished || now >= need || now % 10 == 0) { // nicht bei jedem Block in die Datenbank schreiben
            plugin.database().saveMinerQuest(updated);
        }
        quests(player.getUniqueId()).put(q.miner(), updated);
        Messages msg = plugin.messages();
        player.sendActionBar(msg.get("miner-progress", "done", now, "total", need,
                "block", ItemUtil.name(blockType)));
        if (now >= need && have < need && !finished) {
            // Eine Aufgabe (Blocksorte) ist geschafft: Hinweis im Chat
            msg.send(player, "miner-task-done", "block", ItemUtil.name(blockType), "amount", need,
                    "left", q.blocks().size() - completedCount(updated));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
        if (finished) {
            Database db = plugin.database();
            CryptoHolding h = db.getCryptoHolding(player.getUniqueId(), KRYPTO);
            db.setCryptoHolding(player.getUniqueId(), KRYPTO, h.units() + reward, h.invested());
            db.logTx(player.getUniqueId(), player.getName(), "miner-quest", 0,
                    fmt(reward) + " Kryptocoin von " + minerName(q.miner()));
            msg.send(player, "miner-done", "name", minerName(q.miner()), "units", fmt(reward));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Map<Integer, MinerQuest> cached = questCache.remove(event.getPlayer().getUniqueId());
        if (cached != null) {
            for (MinerQuest q : cached.values()) {
                plugin.database().saveMinerQuest(q); // Fortschritt sichern
            }
        }
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
        try {
            new CryptoMenu(plugin, player, KRYPTO).open(player);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /krypto: " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }
}
