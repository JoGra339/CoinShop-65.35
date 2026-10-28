package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gewerbe im Großhandel: Wer die Großhandellizenz hat, kann ein Gewerbe (Firma) anmelden und benennen (nicht ausbaubar).
 * Er kann Spieler als Angestellte einladen (auch ohne eigene Lizenz). Angestellte dürfen die vom Inhaber freigegebenen
 * Aufträge bearbeiten und Items abgeben. Wird ein Auftrag fertig, bekommt jeder Angestellte einen Lohn nach Menge
 * (ein gleicher Bruchteil der Prämie pro Item für alle; jeder höchstens 10.000 Coins, zusammen höchstens 80 %, sonst nach Beitrag geteilt). Der Rest und der Umsatz zählen nur dem Inhaber
 * (Auswertung für Gesellschafter und CEO).
 */
public final class CompanyManager {

    private record Invite(UUID owner, long expires) { }

    private final CoinShopPlugin plugin;
    /** Offene Einladungen: Eingeladener -> Einladung. */
    private final Map<UUID, Invite> invites = new HashMap<>();

    public CompanyManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Werte aus der config
    // ------------------------------------------------------------------

    private long cfgLong(String key, long fallback) {
        return plugin.getConfig().getLong("company." + key, fallback);
    }

    private long listValue(String key, int level, long fallback) {
        List<Long> list = plugin.getConfig().getLongList("company." + key);
        if (list.isEmpty()) {
            return fallback;
        }
        return list.get(Math.max(0, Math.min(list.size() - 1, level - 1)));
    }

    /** So viele Angestellte darf jede Firma höchstens haben (die Firma ist nicht ausbaubar). */
    public int slots() {
        return (int) Math.max(1, cfgLong("staff-max", 32));
    }

    /** Lohn in Prozent der Prämie pro Item (ohne Boni); für alle Angestellten gleich. */
    public long wagePercent() {
        return Math.max(0, cfgLong("wage-percent", 30));
    }

    /** Höchster Anteil der Auftragsprämie, den alle Angestellten zusammen bekommen (Prozent). */
    public long wageMaxPercent() {
        return Math.max(0, Math.min(100, cfgLong("wage-max-percent", 80)));
    }

    /** Höchster Lohn, den ein einzelner Angestellter für einen Auftrag bekommen kann. */
    public long wageCap() {
        return Math.max(1, cfgLong("wage-cap-per-order", 10000));
    }

    /** Alle Aufträge, die der Inhaber freigeben kann: seine eigenen und (beim Kooperationsvertrag) die seines Partners. */
    public List<Database.WsOrder> workOrders(UUID owner) {
        WholesaleManager wh = plugin.wholesale();
        if (wh.treaties().partnerOf(owner) != null) {
            return wh.treaties().coopOrders(owner);
        }
        List<Database.WsOrder> result = new java.util.ArrayList<>();
        for (int s = 0; s < WholesaleManager.SECTORS.size(); s++) {
            result.addAll(wh.orders(owner, s));
        }
        return result;
    }

    public long foundPrice() {
        return Math.max(0, cfgLong("found-price", 5000));
    }

    public long renamePrice() {
        return Math.max(0, cfgLong("rename-price", 1000));
    }

    // ------------------------------------------------------------------
    //  Abfragen
    // ------------------------------------------------------------------

    public Database.Company company(UUID owner) {
        return plugin.database().getCompany(owner);
    }

    /** Der Inhaber, bei dem der Spieler angestellt ist, oder null. */
    public UUID employerOf(UUID member) {
        Database.Staff staff = plugin.database().getStaff(member);
        return staff == null ? null : staff.owner();
    }

    public boolean isStaffOf(UUID member, UUID owner) {
        UUID employer = employerOf(member);
        return employer != null && employer.equals(owner);
    }

    /** Darf dieser Angestellte den Auftrag bearbeiten (Auftrag gehört dem Arbeitgeber und ist freigegeben)? */
    public boolean mayWork(UUID member, Database.WsOrder order) {
        if (order == null || !plugin.database().isOrderOpen(order.id())) {
            return false;
        }
        UUID employer = employerOf(member);
        if (employer == null) {
            return false;
        }
        // eigene Aufträge des Arbeitgebers, oder die seines Partners aus dem Kooperationsvertrag
        return order.owner().equals(employer) || order.owner().equals(plugin.wholesale().treaties().partnerOf(employer));
    }

    /** Lohn für die Menge eines Items. */
    public long wageFor(Material material, long amount) {
        double unit = plugin.wholesale().unitPrice(material) * wagePercent() / 100.0;
        return Math.max(0, Math.round(unit * amount));
    }

    public static String cleanName(String raw) {
        return raw == null ? "" : raw.replaceAll("[<>&§]", "").trim().replaceAll("\\s+", " ");
    }

    private int nameMax() {
        return (int) Math.max(4, cfgLong("name-max", 24));
    }

    // ------------------------------------------------------------------
    //  Gründen, umbenennen, ausbauen
    // ------------------------------------------------------------------

    public void found(Player player, String rawName) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!plugin.wholesale().allowed(player)) {
            return;
        }
        String name = cleanName(rawName);
        if (name.length() < 3 || name.length() > nameMax()) {
            msg.send(player, "co-name-invalid", "max", nameMax());
            return;
        }
        if (db.getCompany(uuid) != null) {
            msg.send(player, "co-has-company");
            return;
        }
        if (db.getStaff(uuid) != null) {
            msg.send(player, "co-is-staff");
            return;
        }
        if (db.companyNameTaken(name)) {
            msg.send(player, "co-name-taken");
            return;
        }
        long price = foundPrice();
        db.begin();
        try {
            if (price > 0 && !db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "co-no-money", "price", price);
                return;
            }
            if (price > 0) {
                db.addShopAccount(price);
            }
            db.insertCompany(uuid, name);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Gründen eines Gewerbes: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        if (price > 0) {
            db.logTx(uuid, player.getName(), "gewerbe", -price, "Gewerbe gegründet: " + name);
        }
        msg.send(player, "co-founded", "name", name, "price", price);
    }

    public void rename(Player player, String rawName) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        Database.Company company = db.getCompany(uuid);
        if (company == null) {
            return;
        }
        String name = cleanName(rawName);
        if (name.length() < 3 || name.length() > nameMax()) {
            msg.send(player, "co-name-invalid", "max", nameMax());
            return;
        }
        if (!name.equalsIgnoreCase(company.name()) && db.companyNameTaken(name)) {
            msg.send(player, "co-name-taken");
            return;
        }
        long price = renamePrice();
        db.begin();
        try {
            if (price > 0 && !db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "co-no-money", "price", price);
                return;
            }
            if (price > 0) {
                db.addShopAccount(price);
            }
            db.renameCompany(uuid, name);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Umbenennen eines Gewerbes: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        if (price > 0) {
            db.logTx(uuid, player.getName(), "gewerbe", -price, "Gewerbe umbenannt: " + name);
        }
        msg.send(player, "co-renamed", "name", name, "price", price);
    }

    // ------------------------------------------------------------------
    //  KI-Mitarbeiter
    // ------------------------------------------------------------------

    public int aiMax() {
        return (int) Math.max(1, cfgLong("ai-max", 6));
    }

    /** Preis für den nächsten KI-Platz: der erste kostet ai-slot-price, jeder weitere um den Faktor ai-slot-growth teurer (höchstens ai-slot-max-price). */
    public long aiSlotPrice(int owned) {
        long base = Math.max(0, cfgLong("ai-slot-price", 5000));
        double growth = Math.max(1.0, plugin.getConfig().getDouble("company.ai-slot-growth", 1.25));
        double max = Math.max(base, plugin.getConfig().getDouble("company.ai-slot-max-price", 1_500_000));
        return Math.round(Math.min(max, base * Math.pow(growth, Math.max(0, owned))));
    }

    /** Preis für die Stufe (2 bis 10). */
    public long aiLevelPrice(int level) {
        return listValue("ai-level-price", level - 1, 5000);
    }

    /** Items pro Tag auf dieser Stufe (1 bis 10). */
    public long aiItems(int level) {
        return listValue("ai-items", level, 100);
    }

    public void buyAi(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (db.getCompany(uuid) == null) {
            return;
        }
        int owned = db.getAis(uuid).size();
        if (owned >= aiMax()) {
            msg.send(player, "co-ai-max");
            return;
        }
        long price = aiSlotPrice(owned);
        db.begin();
        try {
            if (price > 0 && !db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "co-no-money", "price", price);
                return;
            }
            if (price > 0) {
                db.addShopAccount(price);
            }
            db.addAi(uuid);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Kauf eines KI-Mitarbeiters: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        if (price > 0) {
            db.logTx(uuid, player.getName(), "gewerbe", -price, "KI-Mitarbeiter gekauft");
        }
        msg.send(player, "co-ai-bought", "price", price);
    }

    public void upgradeAi(Player player, long id) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        Database.Ai ai = db.getAi(id);
        if (ai == null || !ai.owner().equals(uuid)) {
            return;
        }
        if (ai.level() >= 10) {
            msg.send(player, "co-ai-top");
            return;
        }
        long price = aiLevelPrice(ai.level() + 1);
        db.begin();
        try {
            if (price > 0 && !db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "co-no-money", "price", price);
                return;
            }
            if (price > 0) {
                db.addShopAccount(price);
            }
            db.setAiLevel(id, ai.level() + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Level einer KI: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        if (price > 0) {
            db.logTx(uuid, player.getName(), "gewerbe", -price, "KI-Mitarbeiter auf Stufe " + (ai.level() + 1));
        }
        msg.send(player, "co-ai-upgraded", "level", ai.level() + 1, "items", aiItems(ai.level() + 1));
    }

    /** Ordnet der KI einen Auftrag des Inhabers zu (orderId 0 = keinen). */
    public void assignAi(Player player, long id, long orderId) {
        Database db = plugin.database();
        Database.Ai ai = db.getAi(id);
        if (ai == null || !ai.owner().equals(player.getUniqueId())) {
            return;
        }
        if (orderId != 0) {
            Database.WsOrder order = db.getWsOrder(orderId);
            if (order == null || !order.owner().equals(player.getUniqueId())) {
                plugin.messages().send(player, "wh-order-gone");
                return;
            }
        }
        db.setAiOrder(id, orderId);
        plugin.messages().send(player, orderId == 0 ? "co-ai-unassigned" : "co-ai-assigned");
    }

    /** Zum Tageswechsel: jede KI liefert die Items ihres Auftrags (Stufe 1: 100, Stufe 10: 500). */
    public void dailyAi() {
        Database db = plugin.database();
        WholesaleManager wh = plugin.wholesale();
        for (Database.Ai ai : db.getAllAis()) {
            try {
                if (ai.orderId() == 0) {
                    continue;
                }
                Database.WsOrder order = db.getWsOrder(ai.orderId());
                if (order == null || !order.owner().equals(ai.owner()) || db.getCompany(ai.owner()) == null) {
                    db.setAiOrder(ai.id(), 0);
                    continue;
                }
                long want = aiItems(ai.level());
                long made = Math.min(want, Math.max(0, order.amount() - order.delivered()));
                wh.aiDeliver(ai.owner(), order, want);
                db.addAiMade(ai.id(), made);
                Player owner = Bukkit.getPlayer(ai.owner());
                if (owner != null) {
                    plugin.messages().send(owner, "co-ai-delivered", "ai", ai.id(), "items", made,
                            "item", order.material());
                }
            } catch (RuntimeException e) {
                plugin.getLogger().severe("KI-Mitarbeiter " + ai.id() + " konnte nicht liefern: " + e);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Einladen, annehmen, kündigen
    // ------------------------------------------------------------------

    public void invite(Player owner, Player target) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = owner.getUniqueId();
        Database.Company company = db.getCompany(uuid);
        if (company == null) {
            return;
        }
        if (target == null || !target.isOnline() || target.equals(owner) || plugin.timeline().previewFor(target)
                || db.getCompany(target.getUniqueId()) != null || db.getStaff(target.getUniqueId()) != null) {
            msg.send(owner, "co-invite-target");
            return;
        }
        if (db.getStaffOf(uuid).size() >= slots()) {
            msg.send(owner, "co-no-slot", "slots", slots());
            return;
        }
        long minutes = Math.max(1, cfgLong("invite-minutes", 3));
        invites.put(target.getUniqueId(), new Invite(uuid, System.currentTimeMillis() + minutes * 60_000L));
        msg.send(owner, "co-invite-sent", "player", target.getName());
        msg.send(target, "co-invite-request", "player", owner.getName(), "name", company.name());
    }

    public void accept(Player target) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        Invite invite = invites.remove(target.getUniqueId());
        if (invite == null || invite.expires() < System.currentTimeMillis()) {
            msg.send(target, "co-invite-none");
            return;
        }
        Database.Company company = db.getCompany(invite.owner());
        if (company == null || db.getCompany(target.getUniqueId()) != null || db.getStaff(target.getUniqueId()) != null) {
            msg.send(target, "co-invite-none");
            return;
        }
        if (db.getStaffOf(invite.owner()).size() >= slots()) {
            msg.send(target, "co-no-slot", "slots", slots());
            return;
        }
        db.addStaff(target.getUniqueId(), invite.owner());
        msg.send(target, "co-joined", "name", company.name());
        Player owner = Bukkit.getPlayer(invite.owner());
        if (owner != null) {
            msg.send(owner, "co-staff-joined", "player", target.getName());
        }
    }

    public void decline(Player target) {
        Invite invite = invites.remove(target.getUniqueId());
        if (invite == null) {
            plugin.messages().send(target, "co-invite-none");
            return;
        }
        Player owner = Bukkit.getPlayer(invite.owner());
        if (owner != null) {
            plugin.messages().send(owner, "co-invite-declined", "player", target.getName());
        }
        plugin.messages().send(target, "co-invite-declined-you");
    }

    /** Der Inhaber entlässt einen Angestellten. */
    public void fire(Player owner, UUID member) {
        Database db = plugin.database();
        if (!isStaffOf(member, owner.getUniqueId())) {
            return;
        }
        db.removeStaff(member);
        String name = db.getName(member);
        plugin.messages().send(owner, "co-fired", "player", name);
        Player online = Bukkit.getPlayer(member);
        if (online != null) {
            plugin.messages().send(online, "co-fired-you");
        }
    }

    /** Insolvenz: löst das Gewerbe des Spielers auf (Angestellte entlassen, KI-Mitarbeiter weg). */
    public void dissolve(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        Database.Company company = db.getCompany(uuid);
        if (company == null) {
            return;
        }
        List<Database.Staff> staff = db.getStaffOf(uuid);
        db.begin();
        try {
            db.deleteCompany(uuid);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Insolvenz eines Gewerbes: " + e.getMessage());
            plugin.messages().send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "gewerbe", 0, "Gewerbe aufgelöst (Insolvenz): " + company.name());
        player.sendMessage(net.kyori.adventure.text.Component.text("Insolvenz: Dein Gewerbe \"" + company.name()
                + "\" ist aufgelöst. Du kannst jederzeit neu gründen.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        for (Database.Staff st : staff) {
            Player online = Bukkit.getPlayer(st.member());
            if (online != null) {
                online.sendMessage(net.kyori.adventure.text.Component.text("Das Gewerbe \"" + company.name()
                        + "\" ist insolvent. Du bist nicht mehr angestellt.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            }
        }
    }

    /** Der Angestellte kündigt selbst. */
    public void leave(Player member) {
        UUID owner = employerOf(member.getUniqueId());
        if (owner == null) {
            return;
        }
        plugin.database().removeStaff(member.getUniqueId());
        plugin.messages().send(member, "co-left");
        Player boss = Bukkit.getPlayer(owner);
        if (boss != null) {
            plugin.messages().send(boss, "co-staff-left", "player", member.getName());
        }
    }
}
