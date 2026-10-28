package de.coinshop;

import de.coinshop.Database.Offer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Der Server-Shop: 10 zufällige Angebote, feste Preise, täglicher Wechsel um 12:00 Uhr.
 * Beim planmäßigen Wechsel wird auch das tägliche Einkommen ausgezahlt.
 */
public final class ServerShopManager {

    private static final String META_LAST_RESET = "server_shop_last_reset";

    private final CoinShopPlugin plugin;
    private final ShopItemGenerator generator;

    public ServerShopManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
        this.generator = new ShopItemGenerator(plugin);
    }

    public List<Offer> getOffers() {
        return plugin.database().getOffers();
    }

    // ------------------------------------------------------------------
    //  Zeitplan
    // ------------------------------------------------------------------

    public ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("server-shop.timezone", "Europe/Berlin"));
        } catch (Exception e) {
            return ZoneId.of("Europe/Berlin");
        }
    }

    public ShopItemGenerator generator() {
        return generator;
    }

    public int resetHour() {
        int hour = plugin.getConfig().getInt("server-shop.reset-hour", 12);
        return Math.max(0, Math.min(23, hour));
    }

    /** Der letzte Zeitpunkt (heute oder gestern 12:00 Uhr), an dem der Shop gewechselt haben sollte. */
    private ZonedDateTime lastScheduledReset() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime reset = now.withHour(resetHour()).withMinute(0).withSecond(0).withNano(0);
        if (reset.isAfter(now)) {
            reset = reset.minusDays(1);
        }
        return reset;
    }

    /** Wie lange der letzte Wechsel her ist. */
    public Duration timeSinceReset() {
        return Duration.between(lastScheduledReset(), ZonedDateTime.now(zone()));
    }

    /** Wie lange es noch bis zum nächsten Wechsel dauert. */
    public Duration timeUntilReset() {
        ZonedDateTime next = lastScheduledReset().plusDays(1);
        return Duration.between(ZonedDateTime.now(zone()), next);
    }

    /** Wird alle 30 Sekunden aufgerufen: wechselt die Angebote, wenn 12:00 Uhr überschritten wurde. */
    public void checkReset() {
        try {
            long lastDone = plugin.database().getMetaLong(META_LAST_RESET, 0L);
            long shouldHave = lastScheduledReset().toInstant().toEpochMilli();
            boolean scheduled = lastDone < shouldHave;
            if (scheduled || plugin.database().getOffers().isEmpty()) {
                if (scheduled) {
                    fullReset(); // Angebote + Börsenkurs + tägliches Einkommen
                } else {
                    rotate();    // nur der Notfall: noch keine Angebote da
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Server-Shop-Wechsel fehlgeschlagen: " + e);
        }
    }

    /**
     * Der komplette Shop-Reset: neue Angebote, neuer Börsenkurs und das tägliche Einkommen.
     * Wird um 12:00 Uhr automatisch ausgeführt und vom Admin-Befehl /shopreset.
     */
    public void fullReset() {
        plugin.database().setMetaLong("shop_reset_no", plugin.database().getMetaLong("shop_reset_no", 0) + 1); // beendet alle Ruhezeiten
        rotate();
        plugin.events().roll();           // Ereignisse des Tages (je Bereich eines)
        plugin.stock().updatePrice();
        plugin.crypto().updatePrices();   // Krypto und Habibi-Krypto
        plugin.investments().payDailyIncome();
        plugin.stock().payDividends();      // Dividenden-Tag
        plugin.routes().payAll();           // Schmugglerrouten bringen Habibi-Krypto
        plugin.titles().updateRankTitles(); // Platz-1-Titel neu vergeben
        plugin.rankings().weeklyBoxes();    // sonntags: Mystery Boxen für die Spitze der Ranglisten
        plugin.blackMarket().rotate();      // neues Schwarzmarkt-Angebot, alte Kopfgelder abräumen
        plugin.reviere().weeklyCheck();
        plugin.auctions().check();          // Hehlerwaren-Auktion: Ende am Sonntag, dann kommt die nächste
        plugin.bets().dailyReset();         // Wettbüro: Wochenauswertung (sonntags)
        plugin.creatureAuction().dailyReset(); // Kreaturen-Auktion: Gewinner bekommt die Kreatur, neue Versteigerung
        plugin.wholesale().dailyReset();    // Großhandel: neue Großaufträge, Wochenauswertung (Boss)
        plugin.titles().updateRankTitles(); // Rollen-Titel (Unterhändler, Gesellschafter, CEO, Pate) sofort nach der Auswertung
    }

    private void rotate() {
        List<Offer> offers = generator.generate();
        plugin.database().replaceOffers(offers);
        plugin.database().setMetaLong(META_LAST_RESET, System.currentTimeMillis());
        plugin.getLogger().info("Server-Shop: " + offers.size() + " neue Angebote ausgelost.");
    }

    // ------------------------------------------------------------------
    //  Bestand
    // ------------------------------------------------------------------

    /** Wie oft ein Angebot insgesamt (von allen Spielern zusammen) gekauft werden kann. */
    public int stockPerOffer() {
        return Math.max(1, plugin.getConfig().getInt("server-shop.stock-per-offer", 3));
    }

    /** Wie viele Käufe dieses Angebots noch möglich sind. */
    public int remaining(int slot) {
        return Math.max(0, stockPerOffer() - plugin.database().getTotalPurchases(slot));
    }

    private boolean isSoldOut() {
        List<Offer> offers = plugin.database().getOffers();
        if (offers.isEmpty()) {
            return false;
        }
        for (Offer offer : offers) {
            if (remaining(offer.slot()) > 0) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Kaufen
    // ------------------------------------------------------------------

    /** Ist dieses Angebot noch genau so aktuell? (Falls der Shop gewechselt hat, während das Menü offen war.) */
    private boolean isCurrent(Offer offer) {
        for (Offer current : plugin.database().getOffers()) {
            if (current.slot() == offer.slot() && current.price() == offer.price()
                    && current.item().equals(offer.item())) {
                return true;
            }
        }
        return false;
    }

    public void buy(Player player, Offer offer) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();

        if (!isCurrent(offer)) {
            msg.send(player, "server-offer-outdated");
            return;
        }

        if (remaining(offer.slot()) <= 0) {
            msg.send(player, "server-sold-out");
            return;
        }

        int limit = plugin.getConfig().getInt("server-shop.purchase-limit-per-offer", 0);
        if (limit > 0 && db.getPurchases(uuid, offer.slot()) >= limit) {
            msg.send(player, "server-limit-reached", "limit", limit);
            return;
        }

        ItemStack stack = offer.item().clone();
        if (!ItemUtil.canFit(player.getInventory(), stack)) {
            msg.send(player, "inventory-full");
            return;
        }

        // Preis mit Rabatt (Lizenz "rabatt")
        long price = plugin.licenses().discounted(uuid, offer.price());

        // Bezahlen, Kauf zählen und den Preis aufs Shop-Konto buchen: alles oder nichts
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addPurchase(uuid, offer.slot());
            db.addShopAccount(price);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Kauf im Server-Shop: " + e.getMessage());
            msg.send(player, "error");
            return;
        }

        db.logTx(uuid, player.getName(), "servershop-kauf", -price,
                stack.getAmount() + "x " + stack.getType().name());
        player.getInventory().addItem(stack);
        msg.send(player, "server-bought", "amount", stack.getAmount(),
                "item", ItemUtil.name(stack), "coins", price);

        // Ist dieses Angebot jetzt ausverkauft? Dann kommt an seinen Platz sofort ein neues Item.
        try {
            if (remaining(offer.slot()) <= 0) {
                Offer fresh = generator.generateOne(offer.slot(), db.getOffers());
                if (fresh != null) {
                    db.replaceOffer(fresh);
                    for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                        msg.send(online, "server-slot-replaced", "item", ItemUtil.name(offer.item()),
                                "new", ItemUtil.name(fresh.item()));
                    }
                } else if (isSoldOut()) {
                    rotate();
                    for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                        msg.send(online, "server-shop-soldout-reset");
                    }
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Neues Angebot nach Ausverkauf fehlgeschlagen: " + e);
        }
    }
}
