package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tagesereignisse. Jeden Tag (außer am Starttag) gibt es bis zu zwei davon, jeweils eines pro Bereich:
 *  - ein allgemeines Ereignis für alle Spieler (15 Stück: 8 gute und 7 schlechte),
 *  - ein Schwarzmarkt-Ereignis (5 Stück): Eingeweihte lesen es ganz, alle anderen nur die Wirkung im normalen Shop.
 * Die Ereignisse gelten bis zum nächsten Shop-Wechsel.
 */
public final class EventManager {

    public enum Event {
        // ---- allgemeine gute Ereignisse ----
        SALE("ausverkauf", "Großer Ausverkauf", true),
        INVEST("dividendentag", "Dividenden-Tag", true),
        TAX("steuerfrei", "Steuerfreier Tag", true),
        FEE_FREE("gebuehrenfrei", "Gebührenfreier Tag", true),
        PAYDAY("zahltag", "Zahltag", true),
        MARKET_FREE("marktplatztag", "Marktplatz-Tag", true),
        // ---- allgemeine schlechte Ereignisse ----
        INFLATION("inflation", "Inflation", false),
        CRISIS("wirtschaftskrise", "Wirtschaftskrise", false),
        TAX_HIGH("steuerpruefung", "Steuerprüfung", false),
        FEE_HIGH("gebuehrenerhoehung", "Gebührenerhöhung", false),
        CUTS("sparhaushalt", "Sparhaushalt", false),
        MARKET_HIGH("marktplatzwucher", "Marktplatz-Wucher", false),
        // ---- Schwarzmarkt-Ereignisse ----
        BLACK("schmugglernacht", "Schmugglernacht", true),
        DEALER_HIGH("hehlerhochsaison", "Hehler-Hochsaison", true),
        RAID("razzia", "Razzia", false),
        DEALER_OFF("haendlerurlaub", "Händler im Urlaub", false),
        DEALER_LOW("schnaeppchenjaeger", "Schnäppchenjäger", false),
        // ---- Großhandel-Ereignisse ----
        WS_SLUMP("absatzflaute", "Absatzflaute", false),
        WS_SMALL("lagerueberschuss", "Lagerüberschuss", true),
        WS_AID("branchenfoerderung", "Branchenförderung", true);

        public final String id;
        public final String title;
        public final boolean positive;

        Event(String id, String title, boolean positive) {
            this.id = id;
            this.title = title;
            this.positive = positive;
        }
    }

    private static final Event[] GENERAL = {Event.SALE, Event.INVEST, Event.TAX,
            Event.FEE_FREE, Event.PAYDAY, Event.MARKET_FREE, Event.INFLATION, Event.CRISIS,
            Event.TAX_HIGH, Event.FEE_HIGH, Event.CUTS, Event.MARKET_HIGH};
    private static final Event[] BLACKMARKET = {Event.BLACK, Event.DEALER_HIGH, Event.RAID, Event.DEALER_OFF,
            Event.DEALER_LOW};

    private static final Event[] WHOLESALE = {Event.WS_SLUMP, Event.WS_SMALL,
            Event.WS_AID};

    private static final String META_WHOLESALE = "event_ws5";
    private static final String META_GENERAL = "event_gen4";
    private static final String META_BLACK = "event_blk4";

    /** Neutrale Namen der Schwarzmarkt-Ereignisse für Spieler ohne Schwarzmarkt (sie sehen nur die Wirkung im Shop). */
    public static String shopTitle(Event e) {
        return switch (e) {
            case BLACK -> "Gebührenrabatt";
            case RAID -> "Polizeikontrolle";
            case DEALER_HIGH -> "Steuerrabatt";
            case DEALER_OFF -> "Ruhiger Markt";
            case DEALER_LOW -> "Preissenkung";
            case WS_SLUMP -> "Marktflaute";
            case WS_SMALL -> "Überangebot";
            case WS_AID -> "Steuerförderung";
            default -> e.title;
        };
    }

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public EventManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public int chancePercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("events.chance-percent", 100)));
    }

    /**
     * Würfelt das Ereignis für den neuen Tag (vor Einkommen und Belohnungen aufrufen) und meldet es.
     * Es gibt höchstens EIN Ereignis pro Tag: erst wird der Bereich (allgemein / Schwarzmarkt / Großhandel)
     * zufällig gewählt, dann ein Ereignis daraus.
     */
    public void roll() {
        boolean allowed = !plugin.timeline().isLaunchDay() && random.nextInt(100) < chancePercent();
        long gen = -1;
        long blk = -1;
        long whs = -1;
        if (allowed) {
            switch (random.nextInt(3)) {
                case 0 -> gen = random.nextInt(GENERAL.length);
                case 1 -> blk = random.nextInt(BLACKMARKET.length);
                default -> whs = random.nextInt(WHOLESALE.length);
            }
        }
        plugin.database().setMetaLong(META_GENERAL, gen);
        plugin.database().setMetaLong(META_BLACK, blk);
        plugin.database().setMetaLong(META_WHOLESALE, whs);
        Event general = current();
        Event black = currentBlack();
        Event wholesale = currentWholesale();
        plugin.getLogger().info("Ereignisse heute: " + (general == null ? "-" : general.id) + " / "
                + (black == null ? "-" : black.id) + " / " + (wholesale == null ? "-" : wholesale.id));
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean bm = plugin.blackMarket().unlocked(online);
            boolean ws = plugin.wholesale().unlocked(online);
            if (general != null) {
                plugin.messages().send(online, "event-" + general.id);
                if (bm && hasExtra(general, "bm")) {
                    plugin.messages().send(online, "event-" + general.id + "-bm");
                }
                if (ws && hasExtra(general, "wh")) {
                    plugin.messages().send(online, "event-" + general.id + "-wh");
                }
            }
            if (black != null) {
                // Eingeweihte lesen das ganze Ereignis, alle anderen nur die Wirkung im normalen Shop
                plugin.messages().send(online, bm ? "event-" + black.id : "event-" + black.id + "-shop");
            }
            if (wholesale != null) {
                // Großhandelskunden lesen das ganze Ereignis; alle anderen nur die Wirkung im Shop (und Eingeweihte im Schwarzmarkt)
                if (ws) {
                    plugin.messages().send(online, "event-" + wholesale.id);
                } else {
                    plugin.messages().send(online, "event-" + wholesale.id + "-shop");
                    if (bm) {
                        plugin.messages().send(online, "event-" + wholesale.id + "-bm");
                    }
                }
            }
        }
    }

    /** Gibt es für das allgemeine Ereignis eine Zusatzzeile für den Schwarzmarkt ("bm") bzw. den Großhandel ("wh")? */
    public static boolean hasExtra(Event e, String kind) {
        if (kind.equals("bm")) {
            return e == Event.SALE || e == Event.INFLATION || e == Event.FEE_FREE || e == Event.FEE_HIGH
                    || e == Event.CRISIS;
        }
        return e == Event.SALE || e == Event.INFLATION || e == Event.CRISIS || e == Event.TAX
                || e == Event.TAX_HIGH || e == Event.FEE_FREE || e == Event.FEE_HIGH;
    }

    private Event read(String key, Event[] list) {
        long value = plugin.database().getMetaLong(key, -1);
        return value >= 0 && value < list.length ? list[(int) value] : null;
    }

    /** Das allgemeine Ereignis von heute oder null. */
    public Event current() {
        return read(META_GENERAL, GENERAL);
    }

    /** Das Schwarzmarkt-Ereignis von heute oder null. */
    public Event currentBlack() {
        return read(META_BLACK, BLACKMARKET);
    }

    /** Das Großhandel-Ereignis von heute oder null. */
    public Event currentWholesale() {
        return read(META_WHOLESALE, WHOLESALE);
    }

    public boolean isActive(Event event) {
        return event == current() || event == currentBlack() || event == currentWholesale();
    }

    public List<Event> active() {
        List<Event> list = new ArrayList<>();
        for (Event e : new Event[]{current(), currentBlack(), currentWholesale()}) {
            if (e != null) {
                list.add(e);
            }
        }
        return list;
    }

    /** Anzeigename für Menüs: jeder sieht nur die Ereignisse seiner Bereiche. Leer, wenn heute nichts los ist. */
    public String activeText(Player viewer) {
        List<String> parts = new ArrayList<>();
        if (current() != null) {
            parts.add(current().title);
        }
        if (currentBlack() != null) {
            boolean bm = viewer != null && plugin.blackMarket().unlocked(viewer);
            parts.add(bm ? currentBlack().title : shopTitle(currentBlack()));
        }
        if (currentWholesale() != null) {
            boolean ws = viewer != null && plugin.wholesale().unlocked(viewer);
            parts.add(ws ? currentWholesale().title : shopTitle(currentWholesale()));
        }
        FlashEventManager.Flash f = plugin.flash().current();
        if (f != null) {
            parts.add(f.title);
        }
        return String.join(" + ", parts);
    }

    private double factor(String key, double fallback) {
        return plugin.getConfig().getDouble("events." + key, fallback);
    }

    /** Faktor auf alle Preise, die Spieler dem System zahlen (Inflation). Gilt für Shop, Lizenzen, Reviere, Miner, Sets. */
    public double inflation() {
        return current() == Event.INFLATION ? factor("inflation-factor", 1.25) : 1.0;
    }

    /** Faktor auf alle Erträge (Wirtschaftskrise): Einkommen, Sets, Aufgaben, Dividenden, Händler, Routen, Großhandel. */
    public double earnings() {
        return current() == Event.CRISIS ? factor("crisis-factor", 0.7) : 1.0;
    }

    /** Preisfaktor im Server-Shop und im Schwarzmarkt: Ausverkauf günstiger, Inflation teurer. */
    public double saleFactor() {
        Event e = current();
        double flash = plugin.flash().saleFactor();
        double bargain = currentBlack() == Event.DEALER_LOW ? factor("bargain-shop-factor", 0.9) : 1.0; // Schnäppchenjäger
        Event w = currentWholesale();
        if (w == Event.WS_SMALL) {
            bargain *= factor("ws-small-shop-factor", 0.95);  // Lagerüberschuss: Überangebot drückt die Preise
        }
        if (e == Event.SALE) {
            return factor("sale-factor", 0.8) * flash * bargain;
        }
        if (e == Event.INFLATION) {
            return factor("inflation-factor", 1.25) * flash * bargain;
        }
        return flash * bargain;
    }

    /** Dividenden-Tag: so viele Coins zahlt heute jede gehaltene Aktie. */
    public long dividendPerShare() {
        return current() == Event.INVEST ? Math.max(0, (long) factor("dividend-per-share", 1)) : 0;
    }

    /** Faktor auf Lizenz-Bonusarten und Erträge: Set-Einkommen (Dividenden-Tag), Login (Treuefest), Aufgaben. */
    public double boostFactor(String licenseId) {
        Event e = current();
        double result = earnings();
        if (e == Event.INVEST && licenseId.equals("investor")) {
            result *= factor("invest-factor", 1.5);
        }
        if (currentBlack() == Event.DEALER_LOW && licenseId.equals("investor")) {
            result *= factor("bargain-set-factor", 0.9); // Schnäppchenjäger: Item-Sets bringen weniger
        }
        return result;
    }

    /** Prozent auf Casino-Gewinne (Goldene Stunde und Wirtschaftskrise). Jackpot ausgenommen. */
    public double casinoBonusPercent() {
        double flashBonus = plugin.flash().casinoBonusPercent(); // Goldene Stunde
        return flashBonus + (earnings() - 1.0) * 100.0;
    }

    /** Faktor auf alle Steuern (Steuerfreier Tag, Steuerprüfung, dazu Razzia: mehr, Hehler-Hochsaison: weniger). */
    public double taxFactor() {
        Event e = current();
        if (e == Event.TAX) {
            return 0;
        }
        double result = e == Event.TAX_HIGH ? factor("tax-high-factor", 2.0) : 1.0;
        Event b = currentBlack();
        if (b == Event.DEALER_HIGH) {
            result *= factor("dealer-high-tax-factor", 0.75);
        }
        if (currentWholesale() == Event.WS_AID) {
            result *= factor("ws-aid-shop-tax-factor", 0.9); // Branchenförderung: Verkaufssteuer etwas niedriger
        }
        return result;
    }

    /** Faktor auf alle Gebühren (Gebührenfreier Tag / Gebührenerhöhung, dazu die Gebührenfreie Stunde). */
    public double feeFactor() {
        Event e = current();
        double flashFactor = plugin.flash().feeFactor(); // Gebührenfreie Stunde
        if (e == Event.FEE_FREE) {
            return 0;
        }
        if (e == Event.FEE_HIGH) {
            return factor("fee-high-factor", 2.0) * flashFactor;
        }
        return flashFactor;
    }

    /** Faktor auf die Einstellgebühr im Spieler-Shop: Marktplatz-Tag / -Wucher und alle Gebühren-Ereignisse. */
    public double listingFeeFactor() {
        Event e = current();
        double result = feeFactor();
        if (e == Event.MARKET_FREE) {
            return 0;
        }
        if (e == Event.MARKET_HIGH) {
            result *= factor("market-high-factor", 2.0);
        }
        Event b = currentBlack();
        if (b == Event.BLACK) {
            result *= factor("black-fee-factor", 0.75);    // Schmugglernacht
        } else if (b == Event.DEALER_OFF) {
            result *= factor("holiday-fee-factor", 0.5);   // Händler im Urlaub
        }
        if (currentWholesale() == Event.WS_SLUMP) {
            result *= factor("ws-slump-fee-factor", 0.75); // Absatzflaute: Einstellgebühr niedriger
        }
        return result;
    }

    /** Faktor auf das tägliche Grundeinkommen (Zahltag / Sparhaushalt / Wirtschaftskrise). */
    public double incomeFactor() {
        Event e = current();
        double result = earnings();
        if (e == Event.PAYDAY) {
            result *= factor("payday-factor", 2.0);
        }
        if (e == Event.CUTS) {
            result *= factor("cuts-factor", 0.5);
        }
        return result;
    }

    // ---------------- Schwarzmarkt ----------------

    /** Zusätzliche Prozentpunkte auf die Kaufchance des Händlers bei den Verkaufs-Slots (Hehler-Hochsaison). */
    public int dealerChanceBonus() {
        int result = currentBlack() == Event.DEALER_HIGH ? (int) factor("dealer-high-bonus", 30) : 0;
        if (currentWholesale() == Event.WS_SLUMP) {
            result -= Math.abs((int) factor("ws-slump-chance-malus", 10)); // Absatzflaute: Händler kaufen weniger
        }
        return result;
    }

    /** Faktor auf das, was der Händler zahlt (Schnäppchenjäger, Wirtschaftskrise). */
    public double dealerPayFactor() {
        return (currentBlack() == Event.DEALER_LOW ? factor("dealer-low-factor", 0.5) : 1.0) * earnings();
    }

    /** Prozentpunkte auf die Kaufchance im Schwarzmarkt (Schmugglernacht: plus, Razzia: minus). */
    public int blackChanceBonus() {
        Event e = currentBlack();
        if (e == Event.BLACK) {
            return (int) factor("black-chance-bonus", 30);
        }
        if (e == Event.RAID) {
            return -Math.abs((int) factor("raid-chance-malus", 30));
        }
        return 0;
    }

    /** Händler im Urlaub: kauft heute nichts von den Verkaufs-Slots. */
    public boolean dealerOff() {
        return currentBlack() == Event.DEALER_OFF;
    }

    // ---------------- Großhandel ----------------

    /** Faktor auf die Prämien im Großhandel (Absatzflaute). */
    public double wholesalePayFactor() {
        Event e = currentWholesale();
        if (e == Event.WS_SLUMP) {
            return factor("ws-slump-factor", 0.7);
        }
        return 1.0;
    }

    /** Faktor auf die Menge der Aufträge, die heute neu entstehen (Lagerüberschuss). */
    public double wholesaleAmountFactor() {
        Event e = currentWholesale();
        if (e == Event.WS_SMALL) {
            return factor("ws-small-factor", 0.5);
        }
        return 1.0;
    }

    /** Faktor auf die Steuer der Großhandel-Prämie (Branchenförderung). */
    public double wholesaleTaxFactor() {
        return currentWholesale() == Event.WS_AID ? factor("ws-aid-factor", 0.5) : 1.0;
    }
}
