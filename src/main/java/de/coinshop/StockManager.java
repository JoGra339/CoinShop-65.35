package de.coinshop;

import de.coinshop.Database.StockHolding;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Die Börse: ein Kurs (1 bis 100 Coins), der sich einmal am Tag zusammen mit dem Shop ändert.
 *
 * - Kaufen: Man zahlt einen beliebigen Coin-Betrag. 10 % davon sind Gebühr (gehen aufs Shop-Konto),
 *   vom Rest werden ganze Aktien zum aktuellen Kurs gekauft. Was übrig bleibt, wird nicht abgebucht.
 * - Verkaufen: Aktien x Kurs, davon gehen 10 % Gebühr aufs Shop-Konto, den Rest bekommt man.
 * - Gewinne und Verluste entstehen durch den Kurs: Steigt der Kurs, ist das Depot mehr wert.
 *   (Die Coins aus Kursgewinnen werden neu erzeugt, bei Verlusten verschwinden Coins.)
 */
public final class StockManager {

    private static final String META_PRICE = "stock_price";
    private static final long MAX_INPUT = 1_000_000_000_000L;

    /** Was ein Kauf ergeben würde: Aktien, tatsächlich gezahlter Betrag, Gebühr. */
    public record BuyQuote(long shares, long paid, long fee) { }

    /** Was ein Verkauf ergeben würde: Wert, Gebühr, Auszahlung. */
    public record SellQuote(long gross, long fee, long tax, long payout) { }

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public StockManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        // Beim allerersten Start: Startkurs festlegen
        if (plugin.database().getMetaLong(META_PRICE, 0) <= 0) {
            long start = clamp(plugin.getConfig().getLong("stock.start-price", 10));
            plugin.database().setMetaLong(META_PRICE, start);
            plugin.database().addStockHistory(start);
        }
    }

    // ------------------------------------------------------------------
    //  Einstellungen und Kurs
    // ------------------------------------------------------------------

    public long minPrice() {
        return Math.max(1, plugin.getConfig().getLong("stock.min-price", 1));
    }

    public long maxPrice() {
        return Math.max(minPrice(), plugin.getConfig().getLong("stock.max-price", 1000));
    }

    private long clamp(long price) {
        return Math.max(minPrice(), Math.min(maxPrice(), price));
    }

    public double feePercent() {
        return Math.max(0, plugin.getConfig().getDouble("stock.fee-percent", 10.0));
    }

    /** Mit der Lizenz "boersen" ist die Gebühr niedriger. */
    public double feePercent(UUID uuid) {
        return feePercent(uuid, feePercent());
    }

    /** Wie feePercent(uuid), aber mit eigener Grundgebühr (Krypto hat eine höhere als die Aktien). */
    public double feePercent(UUID uuid, double base) {
        double percent = plugin.licenses().has(uuid, "boersen") ? Math.min(base, plugin.licenses().value(uuid, "boersen")) : base;
        return percent * plugin.events().feeFactor(); // Ereignisse: Gebührenfreier Tag / Gebührenerhöhung
    }

    /** "10" oder "7,5" - für Texte. */
    public String feePercentText(UUID uuid) {
        double p = feePercent(uuid);
        return p == Math.floor(p) ? Long.toString((long) p) : String.format(Locale.GERMANY, "%.1f", p);
    }

    public int historyDays() {
        return Math.max(2, plugin.getConfig().getInt("stock.history-days", 7));
    }

    public long price() {
        return clamp(plugin.database().getMetaLong(META_PRICE, plugin.getConfig().getLong("stock.start-price", 10)));
    }

    /** Kursverlauf, der neueste zuerst. */
    public List<Long> history(int days) {
        return plugin.database().getStockHistory(days);
    }

    /** Kurs vom Vortag (oder der aktuelle, falls es noch keinen gibt). */
    public long previousPrice() {
        List<Long> history = history(2);
        return history.size() >= 2 ? history.get(1) : price();
    }

    public List<Long> quickAmounts() {
        List<Long> list = new ArrayList<>();
        for (Long value : plugin.getConfig().getLongList("stock.quick-amounts")) {
            if (value != null && value > 0) {
                list.add(value);
            }
        }
        if (list.isEmpty()) {
            list.addAll(List.of(10L, 100L, 1000L));
        }
        return list;
    }

    // ------------------------------------------------------------------
    //  Täglicher Kurswechsel (wird zusammen mit dem Shop-Wechsel aufgerufen)
    // ------------------------------------------------------------------

    /**
     * Neuer Kurs = alter Kurs mit einer zufälligen Schwankung (auch große Sprünge sind möglich,
     * z. B. von 2 auf 4). Ein leichter Zug zum "Ankerpreis" verhindert, dass der Kurs dauerhaft
     * bei 1 oder 100 klebt. Der Kurs bleibt immer zwischen min-price und max-price.
     */
    public void updatePrice() {
        long old = price();
        double volatility = Math.max(0.0, plugin.getConfig().getDouble("stock.volatility", 0.45));
        double anchor = Math.max(1.0, plugin.getConfig().getDouble("stock.anchor-price", 20));
        double reversion = Math.max(0.0, plugin.getConfig().getDouble("stock.reversion", 0.08));

        double lnOld = Math.log(old);
        double drift = reversion * (Math.log(anchor) - lnOld);
        double shock = random.nextGaussian() * volatility;
        long next = clamp(Math.round(Math.exp(lnOld + drift + shock)));

        plugin.database().setMetaLong(META_PRICE, next);
        plugin.database().addStockHistory(next);
        plugin.getLogger().info("Börse: neuer Kurs " + next + " (vorher " + old + ")");

        // Spieler, die gerade online sind, bekommen den neuen Kurs gemeldet
        long diff = next - old;
        String key = diff > 0 ? "stock-update-up" : (diff < 0 ? "stock-update-down" : "stock-update-same");
        for (Player online : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(online, key, "price", next, "diff", Math.abs(diff));
        }
    }

    /** Dividenden-Tag: jede gehaltene Aktie zahlt Coins aus dem Nichts (Shop-Konto wird nicht belastet). */
    public void payDividends() {
        long perShare = plugin.events().dividendPerShare();
        if (perShare <= 0 || plugin.timeline().previewActive()) {
            return;
        }
        Database db = plugin.database();
        for (Database.StockOwner owner : db.allStockHoldings()) {
            long coins = Math.round(owner.shares() * perShare * plugin.events().earnings());
            if (coins < 1) {
                continue;
            }
            db.begin();
            try {
                db.addCoins(owner.uuid(), coins);
                db.commit();
            } catch (RuntimeException e) {
                db.rollback();
                continue;
            }
            db.logTx(owner.uuid(), db.getName(owner.uuid()), "dividende", coins, owner.shares() + " Aktien");
            Player online = Bukkit.getPlayer(owner.uuid());
            if (online != null) {
                plugin.messages().send(online, "stock-dividend", "shares", owner.shares(), "coins", coins);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Berechnungen
    // ------------------------------------------------------------------

    private long feeOf(UUID uuid, long amount) {
        return (long) Math.floor(amount * feePercent(uuid) / 100.0);
    }

    /** Was bekommt man für diesen Coin-Betrag? */
    public BuyQuote buyQuote(UUID uuid, long coins) {
        if (coins < 1) {
            return new BuyQuote(0, 0, 0);
        }
        long price = price();
        long fee = feeOf(uuid, coins);
        long net = coins - fee;
        long shares = net / price;
        if (shares < 1) {
            return new BuyQuote(0, 0, fee);
        }
        long leftover = net - shares * price; // wird nicht abgebucht
        return new BuyQuote(shares, coins - leftover, fee);
    }

    /** Wie viele Coins muss man mindestens zahlen, um einen ganze Aktie zu bekommen? */
    public long minInvest(UUID uuid) {
        long x = 1;
        while (buyQuote(uuid, x).shares() < 1 && x < 1_000_000) {
            x++;
        }
        return x;
    }

    public SellQuote sellQuote(UUID uuid, long shares) {
        if (shares < 1) {
            return new SellQuote(0, 0, 0, 0);
        }
        long gross = shares * price();
        long fee = feeOf(uuid, gross);
        long tax = 0;
        StockHolding h = holding(uuid);
        if (h.shares() > 0 && plugin.getConfig().getBoolean("stock.tax-enabled", true)) {
            long investedPart = BigInteger.valueOf(h.invested()).multiply(BigInteger.valueOf(Math.min(shares, h.shares())))
                    .divide(BigInteger.valueOf(h.shares())).longValue();
            tax = taxOn(uuid, gross - fee - investedPart);
        }
        return new SellQuote(gross, fee, tax, gross - fee - tax);
    }

    /** Steuer auf den Gewinn (wie die Verkaufssteuer im Spieler-Shop: Lizenz Steuerfrei, Ereignisse und Titel wirken). */
    public long taxOn(UUID uuid, long profit) {
        if (profit <= 0) {
            return 0;
        }
        return (long) Math.floor(profit * plugin.playerShop().taxPercent(uuid) / 100.0);
    }

    public StockHolding holding(UUID uuid) {
        return plugin.database().getStockHolding(uuid);
    }

    // ------------------------------------------------------------------
    //  Kaufen
    // ------------------------------------------------------------------

    public void buy(Player player, long coins) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();

        if (coins < 1 || coins > MAX_INPUT) {
            msg.send(player, "ask-invalid");
            return;
        }
        BuyQuote quote = buyQuote(uuid, coins);
        if (quote.shares() < 1) {
            msg.send(player, "stock-too-little", "price", price(), "min", minInvest(uuid), "percent", feePercentText(uuid));
            return;
        }

        long price = price();
        db.begin();
        try {
            if (!db.takeCoins(uuid, quote.paid())) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            if (quote.fee() > 0) {
                db.addShopAccount(quote.fee()); // Die Gebühr geht aufs Konto des Shops
            }
            StockHolding h = db.getStockHolding(uuid);
            db.setStockHolding(uuid, h.shares() + quote.shares(), h.invested() + quote.shares() * price);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Börsen-Kauf: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "boerse-kauf", -quote.paid(), quote.shares() + " Aktien zu " + price);
        msg.send(player, "stock-bought", "shares", quote.shares(), "price", price,
                "paid", quote.paid(), "fee", quote.fee());
    }

    // ------------------------------------------------------------------
    //  Verkaufen
    // ------------------------------------------------------------------

    public void sellAll(Player player) {
        long shares = holding(player.getUniqueId()).shares();
        if (shares < 1) {
            plugin.messages().send(player, "stock-no-shares");
            return;
        }
        sell(player, shares);
    }

    public void sell(Player player, long shares) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();

        if (shares < 1) {
            msg.send(player, "ask-invalid");
            return;
        }
        StockHolding h = db.getStockHolding(uuid);
        if (h.shares() < 1) {
            msg.send(player, "stock-no-shares");
            return;
        }
        if (shares > h.shares()) {
            msg.send(player, "stock-not-enough-shares", "shares", h.shares());
            return;
        }

        long price = price();
        SellQuote quote = sellQuote(uuid, shares);
        // Der eingezahlte Betrag verkleinert sich im gleichen Verhältnis wie die Aktien
        long investedLeft = h.invested() - BigInteger.valueOf(h.invested())
                .multiply(BigInteger.valueOf(shares)).divide(BigInteger.valueOf(h.shares())).longValue();
        long sharesLeft = h.shares() - shares;

        db.begin();
        try {
            if (sharesLeft <= 0) {
                db.deleteStockHolding(uuid);
            } else {
                db.setStockHolding(uuid, sharesLeft, investedLeft);
            }
            db.addCoins(uuid, quote.payout());
            if (quote.fee() + quote.tax() > 0) {
                db.addShopAccount(quote.fee() + quote.tax()); // Gebühr und Steuer gehen aufs Konto des Shops
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Börsen-Verkauf: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "boerse-verkauf", quote.payout(), shares + " Aktien zu " + price);
        msg.send(player, "stock-sold", "shares", shares, "price", price,
                "coins", quote.payout(), "fee", quote.fee(), "tax", quote.tax());
    }

    // ------------------------------------------------------------------
    //  Eingabe per Chat (beliebiger Betrag)
    // ------------------------------------------------------------------

    public void startBuyPrompt(Player player) {
        Messages msg = plugin.messages();
        player.closeInventory();
        msg.send(player, "stock-buy-prompt", "min", minInvest(player.getUniqueId()), "price", price(),
                "percent", feePercentText(player.getUniqueId()));
        plugin.prompts().start(player, text -> {
            Long value = parse(text);
            if (value == null) {
                if (!text.equalsIgnoreCase("abbrechen")) {
                    msg.send(player, "ask-invalid");
                } else {
                    msg.send(player, "ask-cancelled");
                }
                return;
            }
            buy(player, value);
            new de.coinshop.gui.StockMenu(plugin, player).open(player);
        });
    }

    public void startSellPrompt(Player player) {
        Messages msg = plugin.messages();
        long owned = holding(player.getUniqueId()).shares();
        if (owned < 1) {
            msg.send(player, "stock-no-shares");
            return;
        }
        player.closeInventory();
        msg.send(player, "stock-sell-prompt", "shares", owned);
        plugin.prompts().start(player, text -> {
            Long value = parse(text);
            if (value == null) {
                if (!text.equalsIgnoreCase("abbrechen")) {
                    msg.send(player, "ask-invalid");
                } else {
                    msg.send(player, "ask-cancelled");
                }
                return;
            }
            sell(player, value);
            new de.coinshop.gui.StockMenu(plugin, player).open(player);
        });
    }

    /** Wandelt eine Chat-Eingabe in eine Zahl um (Punkte und Kommas als Tausendertrenner sind erlaubt). */
    private static Long parse(String text) {
        try {
            return Long.parseLong(text.replace(".", "").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
