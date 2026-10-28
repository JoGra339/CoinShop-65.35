package de.coinshop;

import de.coinshop.Database.WsOrder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handelsabkommen im Großhandel. Jeder Spieler kann pro Woche (Wochenwechsel: Sonntag zur Shop-Wechsel-Zeit)
 * nur EIN Abkommen nutzen:
 *  - Liefervertrag: eine Branche, X Aufträge diese Woche erledigen, dafür Prämienbonus; sonst Vertragsstrafe
 *  - Exklusivabkommen: eine Branche mit höherer Prämie, in den anderen Branchen kommen diese Woche keine neuen Aufträge
 *  - Steuerabkommen: gegen Gebühr weniger Steuer auf Großhandel-Prämien
 *  - Lagerabkommen: gegen Gebühr eine Woche lang 2 statt 1 neuer Auftrag pro Branche und Tageswechsel
 *  - Kooperationsvertrag: zwei Spieler teilen sich eine Woche lang ALLE Aufträge und arbeiten sie gemeinsam ab;
 *    die Prämie wird je Auftrag nach abgegebener Menge aufgeteilt
 *  - Eilauftrag: gegen Gebühr ein zusätzlicher Auftrag mit höherer Prämie, aber nur wenige Tage gültig
 *
 * Gespeichert wird alles im Merkzettel (meta): wh_tr_w_UUID (Woche), wh_tr_t_UUID (Art), wh_tr_s_UUID (Branche),
 * wh_tr_c_UUID (erledigte Aufträge), wh_tr_o_UUID (Liefervertrag noch nicht abgerechnet), wh_rush_ID (Ablauf des
 * Eilauftrags), wh_cp_m_UUID / wh_cp_l_UUID / wh_cp_w_UUID (Kooperations-Partner und Woche), wh_co_d_ID_UUID (Beitrag zu einem Auftrag).
 */
public final class WholesaleTreaties {

    public enum Type {
        SUPPLY(1, "Liefervertrag"), EXCLUSIVE(2, "Exklusivabkommen"), TAX(3, "Steuerabkommen"),
        COOP(4, "Kooperationsvertrag"), RUSH(5, "Eilauftrag"), STORAGE(6, "Lagerabkommen");

        private final int code;
        private final String label;

        Type(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int code() {
            return code;
        }

        public String label() {
            return label;
        }

        public static Type of(int code) {
            for (Type t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            return null;
        }
    }

    private record Proposal(UUID owner, long expires) { }

    private final CoinShopPlugin plugin;
    /** Offene Kooperationsanfragen: Ziel -> Anfrage. */
    private final Map<UUID, Proposal> proposals = new HashMap<>();

    public WholesaleTreaties(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Werte aus der config
    // ------------------------------------------------------------------

    private long cfgLong(String key, long fallback) {
        return plugin.getConfig().getLong("wholesale.treaty." + key, fallback);
    }

    private double cfgDouble(String key, double fallback) {
        return plugin.getConfig().getDouble("wholesale.treaty." + key, fallback);
    }

    public int supplyOrders() {
        return (int) Math.max(1, cfgLong("supply-orders", 3));
    }

    public long supplyBonus() {
        return Math.max(0, cfgLong("supply-bonus", 10));
    }

    public long supplyPenalty() {
        return Math.max(0, cfgLong("supply-penalty", 10000));
    }

    public long exclusiveBonus() {
        return Math.max(0, cfgLong("exclusive-bonus", 15));
    }

    public long taxFee() {
        return Math.max(0, cfgLong("tax-fee", 5000));
    }

    public double taxFactor() {
        return Math.max(0, cfgDouble("tax-factor", 0.5));
    }

    public long rushFee() {
        return Math.max(0, cfgLong("rush-fee", 5000));
    }

    public double rushFactor() {
        return Math.max(1.0, cfgDouble("rush-factor", 2.0));
    }

    public long storageFee() {
        return Math.max(0, cfgLong("storage-fee", 4000));
    }

    /** Wie viele neue Aufträge pro Branche und Tageswechsel? Normal 1, mit Lagerabkommen mehr. */
    public int ordersPerReset(UUID uuid) {
        return active(uuid) == Type.STORAGE ? (int) Math.max(2, cfgLong("storage-orders", 2)) : 1;
    }

    public int rushDays() {
        return (int) Math.max(1, cfgLong("rush-days", 3));
    }

    // ------------------------------------------------------------------
    //  Woche und aktives Abkommen
    // ------------------------------------------------------------------

    /** Kennung der aktuellen Woche (Datum des letzten Wochenwechsels: Sonntag zur Shop-Wechsel-Zeit). */
    public long weekId() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return now.minusHours(plugin.serverShop().resetHour())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toLocalDate().toEpochDay();
    }

    public boolean usedThisWeek(UUID uuid) {
        return plugin.database().getMetaLong("wh_tr_w_" + uuid, -1) == weekId();
    }

    /** Das Abkommen dieser Woche oder null. */
    public Type active(UUID uuid) {
        return usedThisWeek(uuid) ? Type.of((int) plugin.database().getMetaLong("wh_tr_t_" + uuid, 0)) : null;
    }

    public int activeSector(UUID uuid) {
        return (int) plugin.database().getMetaLong("wh_tr_s_" + uuid, -1);
    }

    public int supplyDone(UUID uuid) {
        return (int) plugin.database().getMetaLong("wh_tr_c_" + uuid, 0);
    }

    private void record(UUID uuid, Type type, int sector) {
        Database db = plugin.database();
        db.setMetaLong("wh_tr_w_" + uuid, weekId());
        db.setMetaLong("wh_tr_t_" + uuid, type.code());
        db.setMetaLong("wh_tr_s_" + uuid, sector);
        db.setMetaLong("wh_tr_c_" + uuid, 0);
        db.setMetaLong("wh_tr_o_" + uuid, type == Type.SUPPLY ? 1 : 0);
    }

    // ------------------------------------------------------------------
    //  Wirkung auf die Prämie
    // ------------------------------------------------------------------

    /** Prämienfaktor für diesen Auftrag (Liefervertrag, Exklusivabkommen, Eilauftrag). */
    public double payFactor(UUID uuid, WsOrder order) {
        double factor = 1.0;
        Type type = active(uuid);
        if (type == Type.SUPPLY && activeSector(uuid) == order.sector()) {
            factor += supplyBonus() / 100.0;
        } else if (type == Type.EXCLUSIVE && activeSector(uuid) == order.sector()) {
            factor += exclusiveBonus() / 100.0;
        }
        if (rushExpiry(order.id()) > 0) {
            factor *= rushFactor();
        }
        return factor;
    }

    /** Faktor auf die Steuer der Prämie (Steuerabkommen). */
    public double taxFactorFor(UUID uuid) {
        return active(uuid) == Type.TAX ? taxFactor() : 1.0;
    }

    /** Darf der Spieler in dieser Branche neue Aufträge bekommen? (Exklusivabkommen sperrt die anderen.) */
    public boolean mayGetOrders(UUID uuid, int sector) {
        return active(uuid) != Type.EXCLUSIVE || activeSector(uuid) == sector;
    }

    /** Ein Auftrag in dieser Branche wurde erledigt (zählt für den Liefervertrag). */
    public void onOrderDone(UUID uuid, int sector) {
        if (active(uuid) == Type.SUPPLY && activeSector(uuid) == sector) {
            plugin.database().setMetaLong("wh_tr_c_" + uuid, supplyDone(uuid) + 1);
        }
    }

    // ------------------------------------------------------------------
    //  Abschließen
    // ------------------------------------------------------------------

    private boolean precheck(Player player) {
        Messages msg = plugin.messages();
        if (!plugin.wholesale().allowed(player)) {
            return false;
        }
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "crypto-preview");
            return false;
        }
        if (usedThisWeek(player.getUniqueId())) {
            msg.send(player, "tr-used", "type", active(player.getUniqueId()) == null ? "-" : active(player.getUniqueId()).label());
            return false;
        }
        return true;
    }

    /** Liefervertrag, Exklusivabkommen und Eilauftrag brauchen eine Branche; das Steuerabkommen nicht (sector = -1). */
    public void sign(Player player, Type type, int sector) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        WholesaleManager wh = plugin.wholesale();
        UUID uuid = player.getUniqueId();
        if (type == Type.COOP || !precheck(player)) {
            return;
        }
        String sectorName = "";
        if (type != Type.TAX && type != Type.STORAGE) {
            if (sector < 0 || sector >= WholesaleManager.SECTORS.size() || !wh.hasSector(uuid, sector)) {
                msg.send(player, "wh-sector-locked");
                return;
            }
            sectorName = WholesaleManager.SECTORS.get(sector).name();
        }
        long fee = type == Type.TAX ? taxFee() : type == Type.RUSH ? rushFee() : type == Type.STORAGE ? storageFee() : 0;
        if (type == Type.RUSH && db.getWsOrders(uuid, sector).size() >= wh.maxOrders()) {
            msg.send(player, "tr-rush-full");
            return;
        }
        db.begin();
        try {
            if (fee > 0 && !db.takeCoins(uuid, fee)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            if (fee > 0) {
                db.addShopAccount(fee);
            }
            record(uuid, type, sector);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Handelsabkommen: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        if (fee > 0) {
            db.logTx(uuid, player.getName(), "grosshandel-abkommen", -fee, type.label());
        }
        switch (type) {
            case SUPPLY -> msg.send(player, "tr-signed-supply", "sector", sectorName, "need", supplyOrders(),
                    "bonus", supplyBonus(), "penalty", supplyPenalty());
            case EXCLUSIVE -> msg.send(player, "tr-signed-exclusive", "sector", sectorName, "bonus", exclusiveBonus());
            case TAX -> msg.send(player, "tr-signed-tax", "fee", fee, "percent", Math.round((1.0 - taxFactor()) * 100));
            case STORAGE -> msg.send(player, "tr-signed-storage", "fee", fee, "orders", ordersPerReset(uuid));
            case RUSH -> {
                long id = wh.createRushOrder(uuid, sector);
                db.setMetaLong("wh_rush_" + id, System.currentTimeMillis() + rushDays() * 86_400_000L);
                msg.send(player, "tr-signed-rush", "sector", sectorName, "fee", fee, "days", rushDays(),
                        "factor", rushFactor());
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    //  Eilaufträge
    // ------------------------------------------------------------------

    /** Ablaufzeit (Millisekunden) eines Eilauftrags oder 0, wenn es keiner ist. */
    public long rushExpiry(long orderId) {
        return plugin.database().getMetaLong("wh_rush_" + orderId, 0);
    }

    public boolean rushExpired(long orderId) {
        long expiry = rushExpiry(orderId);
        return expiry > 0 && expiry < System.currentTimeMillis();
    }

    /** Löscht abgelaufene Eilaufträge (täglich beim Shop-Wechsel). */
    private void cleanRush() {
        Database db = plugin.database();
        for (Map.Entry<String, Long> e : db.getMetaPrefix("wh_rush_").entrySet()) {
            if (e.getValue() <= 0 || e.getValue() >= System.currentTimeMillis()) {
                continue;
            }
            try {
                long id = Long.parseLong(e.getKey().substring("wh_rush_".length()));
                WsOrder order = db.getWsOrder(id);
                db.deleteWsOrder(id);
                db.setMetaLong(e.getKey(), 0);
                if (order != null) {
                    Player owner = Bukkit.getPlayer(order.owner());
                    if (owner != null) {
                        plugin.messages().send(owner, "tr-rush-expired", "sector",
                                WholesaleManager.SECTORS.get(order.sector()).name());
                    }
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Eilauftrag konnte nicht aufgeräumt werden: " + ex);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Kooperation: eine Woche lang teilen sich zwei Spieler alle Aufträge
    // ------------------------------------------------------------------

    /** Der Kooperations-Partner dieser Woche oder null. */
    public UUID partnerOf(UUID uuid) {
        Database db = plugin.database();
        if (db.getMetaLong("wh_cp_w_" + uuid, -1) != weekId()) {
            return null;
        }
        long most = db.getMetaLong("wh_cp_m_" + uuid, 0);
        long least = db.getMetaLong("wh_cp_l_" + uuid, 0);
        return most == 0 && least == 0 ? null : new UUID(most, least);
    }

    public long contribution(long orderId, UUID uuid) {
        return plugin.database().getMetaLong("wh_co_d_" + orderId + "_" + uuid, 0);
    }

    public void addContribution(long orderId, UUID uuid, long amount) {
        plugin.database().setMetaLong("wh_co_d_" + orderId + "_" + uuid, contribution(orderId, uuid) + amount);
    }

    /** Setzt die Beiträge eines erledigten Auftrags zurück. */
    public void clearContribution(long orderId, UUID a, UUID b) {
        plugin.database().setMetaLong("wh_co_d_" + orderId + "_" + a, 0);
        plugin.database().setMetaLong("wh_co_d_" + orderId + "_" + b, 0);
    }

    /** Alle Aufträge des Spielers UND seines Partners (in allen Branchen). */
    public List<WsOrder> coopOrders(UUID uuid) {
        List<WsOrder> result = new ArrayList<>();
        UUID partner = partnerOf(uuid);
        if (partner == null) {
            return result;
        }
        for (UUID who : new UUID[]{uuid, partner}) {
            for (int s = 0; s < WholesaleManager.SECTORS.size(); s++) {
                result.addAll(plugin.database().getWsOrders(who, s));
            }
        }
        return result;
    }

    /** Ein Spieler schlägt einem anderen einen Kooperationsvertrag für diese Woche vor. */
    public void propose(Player owner, Player target) {
        Messages msg = plugin.messages();
        WholesaleManager wh = plugin.wholesale();
        if (!precheck(owner)) {
            return;
        }
        if (target == null || !target.isOnline() || target.equals(owner) || !wh.unlocked(target)
                || plugin.timeline().previewFor(target)) {
            msg.send(owner, "tr-coop-target");
            return;
        }
        if (usedThisWeek(target.getUniqueId())) {
            msg.send(owner, "tr-coop-target-used", "player", target.getName());
            return;
        }
        long minutes = Math.max(1, cfgLong("coop-minutes", 2));
        proposals.put(target.getUniqueId(), new Proposal(owner.getUniqueId(),
                System.currentTimeMillis() + minutes * 60_000L));
        msg.send(owner, "tr-coop-sent", "player", target.getName());
        msg.send(target, "tr-coop-request", "player", owner.getName());
    }

    public void accept(Player target) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        Proposal p = proposals.remove(target.getUniqueId());
        if (p == null || p.expires() < System.currentTimeMillis()) {
            msg.send(target, "tr-coop-none");
            return;
        }
        Player owner = Bukkit.getPlayer(p.owner());
        if (owner == null) {
            msg.send(target, "tr-coop-none");
            return;
        }
        if (!precheck(target)) {
            return;
        }
        if (usedThisWeek(owner.getUniqueId())) {
            msg.send(target, "tr-coop-none");
            return;
        }
        long week = weekId();
        UUID a = owner.getUniqueId();
        UUID b = target.getUniqueId();
        db.begin();
        try {
            db.setMetaLong("wh_cp_m_" + a, b.getMostSignificantBits());
            db.setMetaLong("wh_cp_l_" + a, b.getLeastSignificantBits());
            db.setMetaLong("wh_cp_w_" + a, week);
            db.setMetaLong("wh_cp_m_" + b, a.getMostSignificantBits());
            db.setMetaLong("wh_cp_l_" + b, a.getLeastSignificantBits());
            db.setMetaLong("wh_cp_w_" + b, week);
            record(a, Type.COOP, -1);
            record(b, Type.COOP, -1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Kooperation: " + e.getMessage());
            msg.send(target, "error");
            return;
        }
        msg.send(owner, "tr-coop-accepted", "player", target.getName());
        msg.send(target, "tr-coop-accepted", "player", owner.getName());
    }

    public void decline(Player target) {
        Proposal p = proposals.remove(target.getUniqueId());
        if (p == null) {
            plugin.messages().send(target, "tr-coop-none");
            return;
        }
        Player owner = Bukkit.getPlayer(p.owner());
        if (owner != null) {
            plugin.messages().send(owner, "tr-coop-declined", "player", target.getName());
        }
        plugin.messages().send(target, "tr-coop-declined-self");
    }

    // ------------------------------------------------------------------
    //  Wochenabrechnung und Beitritt
    // ------------------------------------------------------------------

    /** Täglich beim Shop-Wechsel: Liefer-Verträge der Vorwochen abrechnen, abgelaufene Eilaufträge löschen. */
    public void dailyReset() {
        Database db = plugin.database();
        try {
            long week = weekId();
            for (Map.Entry<String, Long> e : db.getMetaPrefix("wh_tr_o_").entrySet()) {
                if (e.getValue() != 1) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(e.getKey().substring("wh_tr_o_".length()));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (db.getMetaLong("wh_tr_w_" + uuid, -1) >= week) {
                    continue; // läuft noch
                }
                db.setMetaLong(e.getKey(), 0);
                if (supplyDone(uuid) >= supplyOrders()) {
                    continue; // erfüllt
                }
                long penalty = Math.min(supplyPenalty(), Math.max(0, db.getCoins(uuid)));
                if (penalty > 0 && db.takeCoins(uuid, penalty)) {
                    db.addShopAccount(penalty);
                    db.logTx(uuid, db.getName(uuid), "grosshandel-vertragsstrafe", -penalty, "Liefervertrag nicht erfüllt");
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null) {
                        plugin.messages().send(online, "tr-penalty", "penalty", penalty, "done", supplyDone(uuid),
                                "need", supplyOrders());
                    } else {
                        db.setMetaLong("wh_tr_pen_" + uuid, db.getMetaLong("wh_tr_pen_" + uuid, 0) + penalty);
                    }
                }
            }
            cleanRush();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Handelsabkommen-Abrechnung fehlgeschlagen: " + e);
        }
    }

    /** Wer offline eine Vertragsstrafe bekam, erfährt es beim Beitritt. */
    public void onJoin(Player player) {
        Database db = plugin.database();
        long pen = db.getMetaLong("wh_tr_pen_" + player.getUniqueId(), 0);
        if (pen > 0) {
            db.setMetaLong("wh_tr_pen_" + player.getUniqueId(), 0);
            plugin.messages().send(player, "tr-penalty-offline", "penalty", pen);
        }
    }
}
