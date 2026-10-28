package de.coinshop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Die Datenbank des Plugins (eine einzelne Datei: plugins/CoinShop/coinshop.db).
 *
 * Hier wird ALLES gespeichert: Kontostände, Server-Shop-Angebote, Spieler-Angebote,
 * Abholung und Verkaufs-Benachrichtigungen. Dadurch gehen beim Neustart keine Daten verloren.
 *
 * Wichtig für die Sicherheit (keine Duplikate / kein Coin-Verlust):
 * Alles, was zusammengehört (z. B. "Coins abziehen UND Angebot löschen"), läuft in einer
 * Transaktion: begin() ... commit(). Geht etwas schief, macht rollback() alles rückgängig.
 */
public final class Database {

    // ---- Kleine Datenpakete ("records"), die wir aus der Datenbank bekommen ----
    public record Offer(int slot, ItemStack item, long price) { }
    public record BlackOffer(int slot, ItemStack item, long price, long stamp, boolean sold) { }
    public record CryptoHolding(long units, long invested) { }
    public record MinerQuest(UUID uuid, int miner, long day, List<String> blocks, List<String> done, boolean finished, long reward) { }
    public record BlackSell(long id, UUID owner, ItemStack item, long price, int revier) { }
    public record RevierEntry(UUID uuid, long coins) { }
    public record RevierBoss(int revier, UUID uuid, long coins) { }
    public record Bounty(long id, UUID target, String targetName, UUID placer, String placerName, long amount, long at) { }
    public record Ownership(String itemId, UUID owner, String ownerName) { }
    public record StockHolding(long shares, long invested) { }
    public record StockOwner(UUID uuid, long shares) { }
    public record Listing(long id, UUID seller, String sellerName, ItemStack item, long price, long expires) { }
    public record Claim(long id, UUID owner, ItemStack item, String reason) { }
    public record SaleNotice(Material material, int amount, long coins) { }
    public record TopEntry(String name, long coins) { }
    public record RankEntry(UUID uuid, String name, long value) { }
    public record Account(UUID uuid, String name, long coins) { }
    public record Home(String world, double x, double y, double z, float yaw, float pitch) { }

    /** Wird geworfen, wenn die Datenbank einen Fehler meldet. */
    public static final class DbException extends RuntimeException {
        public DbException(Throwable cause) {
            super(cause);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Connection conn;
    private final Logger log;

    public Database(File file, Logger log) throws SQLException {
        this.log = log;
        try {
            Class.forName("org.sqlite.JDBC"); // SQLite-Treiber (bringt der Server mit)
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite-Treiber wurde nicht gefunden", e);
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            // Kontostände
            st.execute("CREATE TABLE IF NOT EXISTS balances ("
                    + "uuid TEXT PRIMARY KEY, name TEXT NOT NULL, coins INTEGER NOT NULL DEFAULT 0)");
            // Allerlei Merkzettel (z. B. wann der Server-Shop zuletzt gewechselt hat)
            st.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            // Die 10 aktuellen Angebote des Server-Shops
            st.execute("CREATE TABLE IF NOT EXISTS server_offers ("
                    + "slot INTEGER PRIMARY KEY, material TEXT NOT NULL, amount INTEGER NOT NULL, price INTEGER NOT NULL)");
            // Wie oft hat welcher Spieler welches Server-Angebot gekauft?
            st.execute("CREATE TABLE IF NOT EXISTS server_purchases ("
                    + "uuid TEXT NOT NULL, slot INTEGER NOT NULL, count INTEGER NOT NULL, PRIMARY KEY (uuid, slot))");
            // Angebote im Spieler-Shop (das Item steckt als Bytes in der Spalte "item")
            st.execute("CREATE TABLE IF NOT EXISTS listings ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, seller TEXT NOT NULL, seller_name TEXT NOT NULL, "
                    + "item BLOB NOT NULL, price INTEGER NOT NULL, created INTEGER NOT NULL, expires INTEGER NOT NULL)");
            // Abholung: Items aus abgelaufenen/abgebrochenen Angeboten
            st.execute("CREATE TABLE IF NOT EXISTS claims ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, item BLOB NOT NULL, "
                    + "reason TEXT NOT NULL, created INTEGER NOT NULL)");
            // Verkäufe, die passiert sind, während der Verkäufer offline war
            st.execute("CREATE TABLE IF NOT EXISTS sale_notices ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, material TEXT NOT NULL, "
                    + "amount INTEGER NOT NULL, coins INTEGER NOT NULL)");
            // Server-Shop-Angebote (neue Version: das ganze Item steckt als Bytes drin, damit auch
            // verzauberte Rüstung und Bücher gespeichert werden können)
            st.execute("CREATE TABLE IF NOT EXISTS server_offers2 ("
                    + "slot INTEGER PRIMARY KEY, item BLOB NOT NULL, price INTEGER NOT NULL)");
            // Gekaufte Verkaufs-Slots im Spieler-Shop
            st.execute("CREATE TABLE IF NOT EXISTS player_slots ("
                    + "uuid TEXT PRIMARY KEY, slots INTEGER NOT NULL)");
            // Investitionen: wem gehört welches Item aus welchem Set? (item_id = "setname:nummer")
            st.execute("CREATE TABLE IF NOT EXISTS investments ("
                    + "item_id TEXT PRIMARY KEY, owner TEXT NOT NULL, bought INTEGER NOT NULL)");
            // Tägliches Einkommen, das ein Spieler noch nicht "gesehen" hat (z. B. weil er offline war)
            st.execute("CREATE TABLE IF NOT EXISTS income_pending ("
                    + "uuid TEXT PRIMARY KEY, amount INTEGER NOT NULL)");
            // Börse: Aktien pro Spieler (invested = wofür der Spieler sie insgesamt gekauft hat)
            st.execute("CREATE TABLE IF NOT EXISTS stock_holdings ("
                    + "uuid TEXT PRIMARY KEY, shares INTEGER NOT NULL, invested INTEGER NOT NULL)");
            // Börse: Kursverlauf (ein Eintrag pro Tag)
            st.execute("CREATE TABLE IF NOT EXISTS stock_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, price INTEGER NOT NULL, at INTEGER NOT NULL)");
            // Statistiken für die Ranglisten (stat = z. B. "diamonds", "mobs", "walked")
            st.execute("CREATE TABLE IF NOT EXISTS stats ("
                    + "uuid TEXT NOT NULL, stat TEXT NOT NULL, value INTEGER NOT NULL, PRIMARY KEY (uuid, stat))");
            // Kollektionen: welche Items hat ein Spieler schon einmal besessen, welche Kollektionen sind fertig?
            st.execute("CREATE TABLE IF NOT EXISTS collection_items ("
                    + "uuid TEXT NOT NULL, item TEXT NOT NULL, PRIMARY KEY (uuid, item))");
            st.execute("CREATE TABLE IF NOT EXISTS collection_done ("
                    + "uuid TEXT NOT NULL, id TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (uuid, id))");
            // Zuhause pro Spieler (/sethome, /home)
            st.execute("CREATE TABLE IF NOT EXISTS homes ("
                    + "uuid TEXT PRIMARY KEY, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, "
                    + "z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL)");
            // Geheime Erfolge (seen = 0: der Spieler hat die Meldung noch nicht gesehen)
            st.execute("CREATE TABLE IF NOT EXISTS secret_achievements ("
                    + "uuid TEXT NOT NULL, id TEXT NOT NULL, at INTEGER NOT NULL, seen INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (uuid, id))");
            // Protokoll aller Coin-Bewegungen (für Admins: wer hat wann was bezahlt/bekommen)
            st.execute("CREATE TABLE IF NOT EXISTS tx_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, uuid TEXT NOT NULL, "
                    + "name TEXT NOT NULL, type TEXT NOT NULL, amount INTEGER NOT NULL, detail TEXT NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_uuid ON tx_log (uuid, at)");
            // Schutzgeld-Verträge (offerer = Pate/Revier-Boss, revier 0 = Pate = alle Reviere)
            st.execute("CREATE TABLE IF NOT EXISTS protection ("
                    + "offerer TEXT NOT NULL, revier INTEGER NOT NULL, target TEXT NOT NULL, at INTEGER NOT NULL, "
                    + "PRIMARY KEY (offerer, revier, target))");
            // Hehlerwaren-Auktion
            st.execute("CREATE TABLE IF NOT EXISTS auction ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, item BLOB NOT NULL, min_bid INTEGER NOT NULL, "
                    + "bid INTEGER NOT NULL DEFAULT 0, bidder TEXT, bidder_name TEXT, ends INTEGER NOT NULL, "
                    + "done INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS auction_claim ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, item BLOB NOT NULL, at INTEGER NOT NULL)");
            // Login-Serie: wie viele Tage hintereinander war der Spieler online?
            st.execute("CREATE TABLE IF NOT EXISTS login_streak ("
                    + "uuid TEXT PRIMARY KEY, last_day TEXT NOT NULL, streak INTEGER NOT NULL, best INTEGER NOT NULL)");
            // Tägliche Aufgaben: baseline = Statistik-Wert bei Beginn, done = 1 wenn geschafft
            st.execute("CREATE TABLE IF NOT EXISTS quest_progress ("
                    + "uuid TEXT NOT NULL, day TEXT NOT NULL, idx INTEGER NOT NULL, baseline INTEGER NOT NULL, "
                    + "done INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (uuid, day, idx))");
            // Titel: dauerhaft erspielte Titel, Titel für Platz 1 (nur solange man Erster ist) und die Auswahl
            st.execute("CREATE TABLE IF NOT EXISTS player_titles ("
                    + "uuid TEXT NOT NULL, id TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (uuid, id))");
            st.execute("CREATE TABLE IF NOT EXISTS rank_titles ("
                    + "title_id TEXT NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY (title_id, uuid))");
            st.execute("CREATE TABLE IF NOT EXISTS title_choice ("
                    + "uuid TEXT PRIMARY KEY, choice TEXT NOT NULL)");
            // Lizenzen (einmal gekauft, gelten für immer)
            st.execute("CREATE TABLE IF NOT EXISTS licenses ("
                    + "uuid TEXT NOT NULL, id TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (uuid, id))");
            // Schwarzmarkt: genau ein Angebot, und Kopfgelder
            st.execute("CREATE TABLE IF NOT EXISTS black_offer ("
                    + "id INTEGER PRIMARY KEY, item BLOB NOT NULL, price INTEGER NOT NULL, stamp INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS bm_offers ("
                    + "slot INTEGER PRIMARY KEY, item BLOB NOT NULL, price INTEGER NOT NULL, stamp INTEGER NOT NULL, "
                    + "sold INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS bm_sell ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, item BLOB NOT NULL, "
                    + "price INTEGER NOT NULL, at INTEGER NOT NULL, revier INTEGER NOT NULL DEFAULT 1)");
            try {
                st.execute("ALTER TABLE bm_sell ADD COLUMN revier INTEGER NOT NULL DEFAULT 1"); // alte Datenbank
            } catch (SQLException ignored) {
                // Spalte gibt es schon
            }
            // Reviere: Umsatz der Woche pro Revier und Spieler, die Revier-Bosse (revier 0 = Pate)
            st.execute("CREATE TABLE IF NOT EXISTS revier_sales ("
                    + "revier INTEGER NOT NULL, uuid TEXT NOT NULL, coins INTEGER NOT NULL, PRIMARY KEY (revier, uuid))");
            st.execute("CREATE TABLE IF NOT EXISTS revier_boss ("
                    + "revier INTEGER PRIMARY KEY, uuid TEXT NOT NULL, coins INTEGER NOT NULL)");
            // Krypto: Kursverlauf, Bestände (in Hundertstel), Miner und die tägliche Miner-Quest
            st.execute("CREATE TABLE IF NOT EXISTS crypto_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, price INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS crypto_holdings ("
                    + "uuid TEXT NOT NULL, kind TEXT NOT NULL, units INTEGER NOT NULL, invested INTEGER NOT NULL, "
                    + "PRIMARY KEY (uuid, kind))");
            st.execute("CREATE TABLE IF NOT EXISTS miners ("
                    + "uuid TEXT NOT NULL, idx INTEGER NOT NULL, level INTEGER NOT NULL, PRIMARY KEY (uuid, idx))");
            st.execute("CREATE TABLE IF NOT EXISTS miner_quest ("
                    + "uuid TEXT PRIMARY KEY, miner INTEGER NOT NULL, day INTEGER NOT NULL, "
                    + "blocks TEXT NOT NULL, done TEXT NOT NULL, finished INTEGER NOT NULL DEFAULT 0, reward INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS miner_quests ("
                    + "uuid TEXT NOT NULL, miner INTEGER NOT NULL, day INTEGER NOT NULL, "
                    + "blocks TEXT NOT NULL, done TEXT NOT NULL, finished INTEGER NOT NULL DEFAULT 0, reward INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (uuid, miner))");
            // alte Einzel-Quest (bis Version 39) übernehmen
            st.execute("INSERT OR IGNORE INTO miner_quests (uuid, miner, day, blocks, done, finished, reward) "
                    + "SELECT uuid, miner, day, blocks, done, finished, reward FROM miner_quest");
            st.execute("CREATE TABLE IF NOT EXISTS bounties ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, target TEXT NOT NULL, target_name TEXT NOT NULL, "
                    + "placer TEXT NOT NULL, placer_name TEXT NOT NULL, amount INTEGER NOT NULL, at INTEGER NOT NULL)");
            try {
                st.execute("ALTER TABLE bounties ADD COLUMN resets INTEGER NOT NULL DEFAULT 0"); // alte Datenbank
            } catch (SQLException ignored) {
                // Spalte gibt es schon
            }
            // Anmeldungen für den Endkampf
            st.execute("CREATE TABLE IF NOT EXISTS wh_orders ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, sector INTEGER NOT NULL, "
                    + "material TEXT NOT NULL, amount INTEGER NOT NULL, delivered INTEGER NOT NULL DEFAULT 0, "
                    + "at INTEGER NOT NULL)");
            // Gewerbe (Firmen im Großhandel): Firma, Angestellte, freigegebene Aufträge, Beiträge
            st.execute("CREATE TABLE IF NOT EXISTS companies ("
                    + "owner TEXT PRIMARY KEY, name TEXT NOT NULL, level INTEGER NOT NULL DEFAULT 1, "
                    + "at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS company_staff ("
                    + "member TEXT PRIMARY KEY, owner TEXT NOT NULL, items INTEGER NOT NULL DEFAULT 0, "
                    + "wages INTEGER NOT NULL DEFAULT 0, at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS company_open (order_id INTEGER PRIMARY KEY)");
            st.execute("CREATE TABLE IF NOT EXISTS company_ai ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, level INTEGER NOT NULL DEFAULT 1, "
                    + "order_id INTEGER NOT NULL DEFAULT 0, made INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS company_contrib ("
                    + "order_id INTEGER NOT NULL, member TEXT NOT NULL, amount INTEGER NOT NULL, "
                    + "PRIMARY KEY (order_id, member))");
            // Wettbüro: Wetten auf die Tode eines Spielers in dieser Woche, und manipulierte Quoten
            st.execute("CREATE TABLE IF NOT EXISTS bets ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, bettor TEXT NOT NULL, target TEXT NOT NULL, "
                    + "target_name TEXT NOT NULL, outcome INTEGER NOT NULL, stake INTEGER NOT NULL, "
                    + "odds INTEGER NOT NULL, rigged INTEGER NOT NULL DEFAULT 0, at INTEGER NOT NULL, "
                    + "causes TEXT NOT NULL DEFAULT '', cmult INTEGER NOT NULL DEFAULT 100)");
            try {
                // ältere Datenbanken: Spalten für die Todesnachrichten nachrüsten
                st.execute("ALTER TABLE bets ADD COLUMN causes TEXT NOT NULL DEFAULT ''");
                st.execute("ALTER TABLE bets ADD COLUMN cmult INTEGER NOT NULL DEFAULT 100");
            } catch (SQLException ignored) {
                // Spalte gibt es schon
            }
            try {
                st.execute("ALTER TABLE bets ADD COLUMN black INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // Spalten gibt es schon
            }
            // Kleine Wetten: auf den nächsten Tod eines Spielers (cause -1 = egal woran), gültig bis "until"
            st.execute("CREATE TABLE IF NOT EXISTS small_bets ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, bettor TEXT NOT NULL, target TEXT NOT NULL, "
                    + "target_name TEXT NOT NULL, stake INTEGER NOT NULL, odds INTEGER NOT NULL, "
                    + "cause INTEGER NOT NULL, until INTEGER NOT NULL, at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS bet_rigs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, rigger TEXT NOT NULL, target TEXT NOT NULL, "
                    + "outcome INTEGER NOT NULL, at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS end_signup ("
                    + "uuid TEXT PRIMARY KEY, name TEXT NOT NULL)");
        }
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            log.warning("Datenbank konnte nicht sauber geschlossen werden: " + e.getMessage());
        }
    }

    // =====================================================================
    //  Transaktionen
    // =====================================================================

    /** Falls eine frühere Transaktion hängen geblieben ist: zurückrollen und wieder normal arbeiten. */
    public void ensureAutoCommit() {
        try {
            if (!conn.getAutoCommit()) {
                log.warning("Hängende Transaktion gefunden und zurückgerollt.");
                conn.rollback();
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public void begin() {
        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public void commit() {
        try {
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public void rollback() {
        try {
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            log.warning("Rollback fehlgeschlagen: " + e.getMessage());
        }
    }

    // =====================================================================
    //  Kleine Hilfsmethoden, damit wir nicht überall dasselbe schreiben müssen
    // =====================================================================

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /** Führt ein INSERT/UPDATE/DELETE aus und gibt zurück, wie viele Zeilen betroffen waren. */
    private int update(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    /** Führt eine Abfrage aus und wandelt jede Zeile mit dem "mapper" um. */
    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        List<T> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    T value = mapper.map(rs);
                    if (value != null) {
                        result.add(value);
                    }
                }
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
        return result;
    }

    private ItemStack readItem(ResultSet rs, String column) throws SQLException {
        try {
            return ItemStack.deserializeBytes(rs.getBytes(column));
        } catch (Exception e) {
            log.warning("Ein gespeichertes Item konnte nicht gelesen werden und wird übersprungen: " + e.getMessage());
            return null;
        }
    }

    // =====================================================================
    //  Kontostände
    // =====================================================================

    /** Legt den Spieler an (falls neu) und merkt sich seinen aktuellen Namen. Startguthaben: 0. */
    public void ensurePlayer(UUID uuid, String name) {
        update("INSERT INTO balances (uuid, name, coins) VALUES (?, ?, 0) "
                + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name", uuid.toString(), name);
    }

    public long getCoins(UUID uuid) {
        List<Long> r = query("SELECT coins FROM balances WHERE uuid = ?", rs -> rs.getLong(1), uuid.toString());
        return r.isEmpty() ? 0L : r.get(0);
    }

    public void addCoins(UUID uuid, long amount) {
        update("UPDATE balances SET coins = coins + ? WHERE uuid = ?", amount, uuid.toString());
    }

    /** Zieht Coins ab, aber nur wenn genug da sind. Gibt true zurück, wenn es geklappt hat. */
    public boolean takeCoins(UUID uuid, long amount) {
        return update("UPDATE balances SET coins = coins - ? WHERE uuid = ? AND coins >= ?",
                amount, uuid.toString(), amount) == 1;
    }

    public void setCoins(UUID uuid, long amount) {
        update("UPDATE balances SET coins = ? WHERE uuid = ?", amount, uuid.toString());
    }

    /** Überweisung von A nach B. Alles oder nichts. */
    public boolean transfer(UUID from, UUID to, long amount) {
        begin();
        try {
            if (!takeCoins(from, amount)) {
                rollback();
                return false;
            }
            addCoins(to, amount);
            commit();
            return true;
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    public Optional<UUID> findUuidByName(String name) {
        List<String> r = query("SELECT uuid FROM balances WHERE name = ? COLLATE NOCASE",
                rs -> rs.getString(1), name);
        return r.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(r.get(0)));
    }

    public String getName(UUID uuid) {
        List<String> r = query("SELECT name FROM balances WHERE uuid = ?", rs -> rs.getString(1), uuid.toString());
        return r.isEmpty() ? uuid.toString() : r.get(0);
    }

    public List<TopEntry> top(int limit) {
        return query("SELECT name, coins FROM balances WHERE coins > 0 ORDER BY coins DESC, name ASC LIMIT ?",
                rs -> new TopEntry(rs.getString(1), rs.getLong(2)), limit);
    }

    // =====================================================================
    //  Merkzettel (meta)
    // =====================================================================

    public long getMetaLong(String key, long fallback) {
        List<String> r = query("SELECT value FROM meta WHERE key = ?", rs -> rs.getString(1), key);
        if (r.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(r.get(0));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void setMetaLong(String key, long value) {
        update("INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                key, Long.toString(value));
    }

    // =====================================================================
    //  Server-Shop
    // =====================================================================

    /** Ersetzt alle Angebote durch neue und setzt die Kauf-Zähler aller Spieler zurück. */
    public void replaceOffers(List<Offer> offers) {
        begin();
        try {
            update("DELETE FROM server_offers2");
            update("DELETE FROM server_purchases");
            for (Offer o : offers) {
                update("INSERT INTO server_offers2 (slot, item, price) VALUES (?, ?, ?)",
                        o.slot(), o.item().serializeAsBytes(), o.price());
            }
            commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    /** Ersetzt nur das Angebot eines Platzes und setzt dessen Kauf-Zähler zurück. */
    public void replaceOffer(Offer offer) {
        begin();
        try {
            update("DELETE FROM server_offers2 WHERE slot = ?", offer.slot());
            update("DELETE FROM server_purchases WHERE slot = ?", offer.slot());
            update("INSERT INTO server_offers2 (slot, item, price) VALUES (?, ?, ?)",
                    offer.slot(), offer.item().serializeAsBytes(), offer.price());
            commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    public List<Offer> getOffers() {
        return query("SELECT slot, item, price FROM server_offers2 ORDER BY slot",
                rs -> {
                    ItemStack item = readItem(rs, "item");
                    return item == null ? null : new Offer(rs.getInt("slot"), item, rs.getLong("price"));
                });
    }

    public int getPurchases(UUID uuid, int slot) {
        List<Integer> r = query("SELECT count FROM server_purchases WHERE uuid = ? AND slot = ?",
                rs -> rs.getInt(1), uuid.toString(), slot);
        return r.isEmpty() ? 0 : r.get(0);
    }

    /** Wie oft wurde dieses Angebot von ALLEN Spielern zusammen gekauft? */
    public int getTotalPurchases(int slot) {
        List<Integer> r = query("SELECT COALESCE(SUM(count), 0) FROM server_purchases WHERE slot = ?",
                rs -> rs.getInt(1), slot);
        return r.isEmpty() ? 0 : r.get(0);
    }

    public void addPurchase(UUID uuid, int slot) {
        update("INSERT INTO server_purchases (uuid, slot, count) VALUES (?, ?, 1) "
                + "ON CONFLICT(uuid, slot) DO UPDATE SET count = count + 1", uuid.toString(), slot);
    }

    // =====================================================================
    //  Spieler-Shop: Angebote
    // =====================================================================

    public void insertListing(UUID seller, String sellerName, ItemStack item, long price, long expires) {
        update("INSERT INTO listings (seller, seller_name, item, price, created, expires) VALUES (?, ?, ?, ?, ?, ?)",
                seller.toString(), sellerName, item.serializeAsBytes(), price, System.currentTimeMillis(), expires);
    }

    private Listing mapListing(ResultSet rs) throws SQLException {
        ItemStack item = readItem(rs, "item");
        if (item == null) {
            return null;
        }
        return new Listing(rs.getLong("id"), UUID.fromString(rs.getString("seller")),
                rs.getString("seller_name"), item, rs.getLong("price"), rs.getLong("expires"));
    }

    /** Neueste Angebote zuerst; "offset" und "limit" dienen zum Blättern. */
    public List<Listing> getListings(int offset, int limit) {
        return query("SELECT * FROM listings ORDER BY id DESC LIMIT ? OFFSET ?", this::mapListing, limit, offset);
    }

    public int countListings() {
        List<Integer> r = query("SELECT COUNT(*) FROM listings", rs -> rs.getInt(1));
        return r.isEmpty() ? 0 : r.get(0);
    }

    public List<Listing> getListingsOf(UUID seller) {
        return query("SELECT * FROM listings WHERE seller = ? ORDER BY id DESC", this::mapListing, seller.toString());
    }

    public int countListingsOf(UUID seller) {
        List<Integer> r = query("SELECT COUNT(*) FROM listings WHERE seller = ?", rs -> rs.getInt(1), seller.toString());
        return r.isEmpty() ? 0 : r.get(0);
    }

    public Optional<Listing> getListing(long id) {
        List<Listing> r = query("SELECT * FROM listings WHERE id = ?", this::mapListing, id);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    /** Löscht ein Angebot. true = es existierte und wurde gelöscht (schützt vor doppeltem Kauf). */
    public boolean deleteListing(long id) {
        return update("DELETE FROM listings WHERE id = ?", id) == 1;
    }

    public void makeListingsPermanent(long value) {
        update("UPDATE listings SET expires = ? WHERE expires < ?", value, value);
    }

    public List<Listing> getExpired(long now) {
        return query("SELECT * FROM listings WHERE expires <= ?", this::mapListing, now);
    }

    // =====================================================================
    //  Abholung (claims)
    // =====================================================================

    public void addClaim(UUID owner, ItemStack item, String reason) {
        update("INSERT INTO claims (owner, item, reason, created) VALUES (?, ?, ?, ?)",
                owner.toString(), item.serializeAsBytes(), reason, System.currentTimeMillis());
    }

    private Claim mapClaim(ResultSet rs) throws SQLException {
        ItemStack item = readItem(rs, "item");
        if (item == null) {
            return null;
        }
        return new Claim(rs.getLong("id"), UUID.fromString(rs.getString("owner")), item, rs.getString("reason"));
    }

    public List<Claim> getClaims(UUID owner) {
        return query("SELECT * FROM claims WHERE owner = ? ORDER BY id ASC", this::mapClaim, owner.toString());
    }

    public int countClaims(UUID owner) {
        List<Integer> r = query("SELECT COUNT(*) FROM claims WHERE owner = ?", rs -> rs.getInt(1), owner.toString());
        return r.isEmpty() ? 0 : r.get(0);
    }

    public Optional<Claim> getClaim(long id) {
        List<Claim> r = query("SELECT * FROM claims WHERE id = ?", this::mapClaim, id);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public boolean deleteClaim(long id) {
        return update("DELETE FROM claims WHERE id = ?", id) == 1;
    }

    // =====================================================================
    //  Verkaufs-Benachrichtigungen für Offline-Verkäufer
    // =====================================================================

    public void addSaleNotice(UUID uuid, Material material, int amount, long coins) {
        update("INSERT INTO sale_notices (uuid, material, amount, coins) VALUES (?, ?, ?, ?)",
                uuid.toString(), material.name(), amount, coins);
    }

    /** Holt alle Benachrichtigungen eines Spielers und löscht sie gleichzeitig. */
    public List<SaleNotice> popSaleNotices(UUID uuid) {
        List<SaleNotice> list = query("SELECT material, amount, coins FROM sale_notices WHERE uuid = ? ORDER BY id",
                rs -> {
                    Material m = Material.matchMaterial(rs.getString(1));
                    return m == null ? null : new SaleNotice(m, rs.getInt(2), rs.getLong(3));
                }, uuid.toString());
        update("DELETE FROM sale_notices WHERE uuid = ?", uuid.toString());
        return list;
    }

    // =====================================================================
    //  Konto des Shops (alle Einnahmen des Shops landen hier)
    // =====================================================================

    private static final String META_SHOP_ACCOUNT = "shop_account";

    public long getShopAccount() {
        return getMetaLong(META_SHOP_ACCOUNT, 0L);
    }

    public void addShopAccount(long amount) {
        setMetaLong(META_SHOP_ACCOUNT, getShopAccount() + amount);
    }

    /** Zieht Coins vom Shop-Konto ab, aber nur wenn genug drauf sind. */
    public boolean takeShopAccount(long amount) {
        long current = getShopAccount();
        if (current < amount) {
            return false;
        }
        setMetaLong(META_SHOP_ACCOUNT, current - amount);
        return true;
    }

    // =====================================================================
    //  Verkaufs-Slots im Spieler-Shop
    // =====================================================================

    /** Wie viele Slots hat der Spieler schon GEKAUFT? */
    public int getSlots(UUID uuid) {
        List<Integer> r = query("SELECT slots FROM player_slots WHERE uuid = ?", rs -> rs.getInt(1), uuid.toString());
        return r.isEmpty() ? 0 : r.get(0);
    }

    public void addSlot(UUID uuid) {
        update("INSERT INTO player_slots (uuid, slots) VALUES (?, 1) "
                + "ON CONFLICT(uuid) DO UPDATE SET slots = slots + 1", uuid.toString());
    }

    // =====================================================================
    //  Investitionen (Sets)
    // =====================================================================

    private Ownership mapOwnership(ResultSet rs) throws SQLException {
        return new Ownership(rs.getString(1), UUID.fromString(rs.getString(2)), rs.getString(3));
    }

    private static final String OWNERSHIP_SELECT =
            "SELECT i.item_id, i.owner, COALESCE(b.name, i.owner) FROM investments i "
                    + "LEFT JOIN balances b ON b.uuid = i.owner";

    public List<Ownership> getOwnerships() {
        return query(OWNERSHIP_SELECT, this::mapOwnership);
    }

    public Optional<Ownership> getOwnership(String itemId) {
        List<Ownership> r = query(OWNERSHIP_SELECT + " WHERE i.item_id = ?", this::mapOwnership, itemId);
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    /** Trägt den Besitzer ein. false = das Item gehört schon jemandem (schützt vor Doppelkauf). */
    public boolean insertOwnership(String itemId, UUID owner) {
        return update("INSERT OR IGNORE INTO investments (item_id, owner, bought) VALUES (?, ?, ?)",
                itemId, owner.toString(), System.currentTimeMillis()) == 1;
    }

    /** Überträgt ein Item, aber nur wenn "from" wirklich noch der Besitzer ist. */
    public boolean transferOwnership(String itemId, UUID from, UUID to) {
        return update("UPDATE investments SET owner = ? WHERE item_id = ? AND owner = ?",
                to.toString(), itemId, from.toString()) == 1;
    }

    // =====================================================================
    //  Tägliches Einkommen
    // =====================================================================

    /** Gibt ALLEN bekannten Spielern (auch Offline-Spielern) den Grundbetrag. */
    public void payToAll(long amount) {
        update("UPDATE balances SET coins = coins + ?", amount);
        update("INSERT INTO income_pending (uuid, amount) SELECT uuid, ? FROM balances WHERE true "
                + "ON CONFLICT(uuid) DO UPDATE SET amount = amount + excluded.amount", amount);
    }

    /** Gibt einem Spieler Extra-Einkommen (z. B. aus Sets). */
    public void payExtra(UUID uuid, long amount) {
        update("UPDATE balances SET coins = coins + ? WHERE uuid = ?", amount, uuid.toString());
        update("INSERT INTO income_pending (uuid, amount) VALUES (?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET amount = amount + excluded.amount", uuid.toString(), amount);
    }

    /** Holt den noch nicht angezeigten Einkommens-Betrag eines Spielers und setzt ihn auf 0. */
    public long popPendingIncome(UUID uuid) {
        List<Long> r = query("SELECT amount FROM income_pending WHERE uuid = ?", rs -> rs.getLong(1), uuid.toString());
        update("DELETE FROM income_pending WHERE uuid = ?", uuid.toString());
        return r.isEmpty() ? 0L : r.get(0);
    }

    // =====================================================================
    //  Börse
    // =====================================================================

    public List<StockOwner> allStockHoldings() {
        return query("SELECT uuid, shares FROM stock_holdings WHERE shares > 0",
                rs -> new StockOwner(UUID.fromString(rs.getString(1)), rs.getLong(2)));
    }

    /** Alle Merkzettel-Einträge, deren Schlüssel mit dem Präfix beginnt (Schlüssel -> Wert). */
    // ------------------------------------------------------------------
    //  Großhandel: persönliche Aufträge
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    //  Wettbüro
    // ------------------------------------------------------------------

    /** odds = Quote mal 100 (250 = 2,5-fach). */
    public record Bet(long id, UUID bettor, UUID target, String targetName, int outcome, long stake, int odds,
                      boolean rigged, long at, String causes, int causeMult, boolean black) { }

    public record Rig(long id, UUID rigger, UUID target, int outcome) { }

    public void addBet(UUID bettor, UUID target, String targetName, int outcome, long stake, int odds, boolean rigged,
                       String causes, int causeMult, boolean black) {
        update("INSERT INTO bets (bettor, target, target_name, outcome, stake, odds, rigged, at, causes, cmult, black) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bettor.toString(), target.toString(), targetName, outcome, stake, odds, rigged ? 1 : 0,
                System.currentTimeMillis(), causes, causeMult, black ? 1 : 0);
    }

    // ---- Kleine Wetten ----
    public record SmallBet(long id, UUID bettor, UUID target, String targetName, long stake, int odds, int cause,
                           long until) { }

    private static SmallBet smallOf(java.sql.ResultSet rs) throws SQLException {
        return new SmallBet(rs.getLong(1), UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)),
                rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getInt(7), rs.getLong(8));
    }

    public void addSmallBet(UUID bettor, UUID target, String targetName, long stake, int odds, int cause, long until) {
        update("INSERT INTO small_bets (bettor, target, target_name, stake, odds, cause, until, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                bettor.toString(), target.toString(), targetName, stake, odds, cause, until, System.currentTimeMillis());
    }

    public List<SmallBet> getSmallBets() {
        return query("SELECT id, bettor, target, target_name, stake, odds, cause, until FROM small_bets ORDER BY id ASC",
                Database::smallOf);
    }

    public void deleteSmallBet(long id) {
        update("DELETE FROM small_bets WHERE id = ?", id);
    }

    public void deleteBet(long id) {
        update("DELETE FROM bets WHERE id = ?", id);
    }

    public List<Bet> getBets() {
        return query("SELECT id, bettor, target, target_name, outcome, stake, odds, rigged, at, causes, cmult, black FROM bets ORDER BY id ASC",
                rs -> new Bet(rs.getLong(1), UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)),
                        rs.getString(4), rs.getInt(5), rs.getLong(6), rs.getInt(7), rs.getInt(8) == 1, rs.getLong(9),
                        rs.getString(10), rs.getInt(11), rs.getInt(12) == 1));
    }

    public List<Bet> getBetsBy(UUID bettor) {
        List<Bet> own = new java.util.ArrayList<>();
        for (Bet bet : getBets()) {
            if (bet.bettor().equals(bettor)) {
                own.add(bet);
            }
        }
        return own;
    }

    public void clearBets() {
        update("DELETE FROM bets");
        update("DELETE FROM bet_rigs");
    }

    public void addRig(UUID rigger, UUID target, int outcome) {
        update("INSERT INTO bet_rigs (rigger, target, outcome, at) VALUES (?, ?, ?, ?)",
                rigger.toString(), target.toString(), outcome, System.currentTimeMillis());
    }

    public List<Rig> getRigs() {
        return query("SELECT id, rigger, target, outcome FROM bet_rigs ORDER BY id ASC",
                rs -> new Rig(rs.getLong(1), UUID.fromString(rs.getString(2)), UUID.fromString(rs.getString(3)),
                        rs.getInt(4)));
    }

    public record WsOrder(long id, UUID owner, int sector, String material, long amount, long delivered) { }

    public void addWsOrder(UUID uuid, int sector, String material, long amount) {
        update("INSERT INTO wh_orders (uuid, sector, material, amount, delivered, at) VALUES (?, ?, ?, ?, 0, ?)",
                uuid.toString(), sector, material, amount, System.currentTimeMillis());
    }

    public List<WsOrder> getWsOrders(UUID uuid, int sector) {
        return query("SELECT id, uuid, sector, material, amount, delivered FROM wh_orders "
                        + "WHERE uuid = ? AND sector = ? ORDER BY id ASC",
                rs -> new WsOrder(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getString(4),
                        rs.getLong(5), rs.getLong(6)), uuid.toString(), sector);
    }

    public WsOrder getWsOrder(long id) {
        List<WsOrder> r = query("SELECT id, uuid, sector, material, amount, delivered FROM wh_orders WHERE id = ?",
                rs -> new WsOrder(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getString(4),
                        rs.getLong(5), rs.getLong(6)), id);
        return r.isEmpty() ? null : r.get(0);
    }

    public void setWsDelivered(long id, long delivered) {
        update("UPDATE wh_orders SET delivered = ? WHERE id = ?", delivered, id);
    }

    public void deleteWsOrder(long id) {
        update("DELETE FROM wh_orders WHERE id = ?", id);
        update("DELETE FROM company_open WHERE order_id = ?", id);
        update("DELETE FROM company_contrib WHERE order_id = ?", id);
    }

    // =====================================================================
    //  Gewerbe
    // =====================================================================

    public record Company(UUID owner, String name, int level) { }

    public record Staff(UUID member, UUID owner, long items, long wages) { }

    public record Contrib(UUID member, long amount) { }

    public Company getCompany(UUID owner) {
        List<Company> r = query("SELECT owner, name, level FROM companies WHERE owner = ?",
                rs -> new Company(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3)), owner.toString());
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Company> getCompanies() {
        return query("SELECT owner, name, level FROM companies ORDER BY level DESC, name ASC",
                rs -> new Company(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3)));
    }

    public boolean companyNameTaken(String name) {
        return !query("SELECT 1 FROM companies WHERE lower(name) = lower(?)", rs -> 1, name).isEmpty();
    }

    public void insertCompany(UUID owner, String name) {
        update("INSERT INTO companies (owner, name, level, at) VALUES (?, ?, 1, ?)",
                owner.toString(), name, System.currentTimeMillis());
    }

    public void renameCompany(UUID owner, String name) {
        update("UPDATE companies SET name = ? WHERE owner = ?", name, owner.toString());
    }

    public void setCompanyLevel(UUID owner, int level) {
        update("UPDATE companies SET level = ? WHERE owner = ?", level, owner.toString());
    }

    public Staff getStaff(UUID member) {
        List<Staff> r = query("SELECT member, owner, items, wages FROM company_staff WHERE member = ?",
                rs -> new Staff(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        rs.getLong(3), rs.getLong(4)), member.toString());
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Staff> getStaffOf(UUID owner) {
        return query("SELECT member, owner, items, wages FROM company_staff WHERE owner = ? ORDER BY at ASC",
                rs -> new Staff(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                        rs.getLong(3), rs.getLong(4)), owner.toString());
    }

    public void addStaff(UUID member, UUID owner) {
        update("INSERT OR REPLACE INTO company_staff (member, owner, items, wages, at) VALUES (?, ?, 0, 0, ?)",
                member.toString(), owner.toString(), System.currentTimeMillis());
    }

    public void removeStaff(UUID member) {
        update("DELETE FROM company_staff WHERE member = ?", member.toString());
    }

    public void addStaffItems(UUID member, long items) {
        update("UPDATE company_staff SET items = items + ? WHERE member = ?", items, member.toString());
    }

    public void addStaffWages(UUID member, long wages) {
        update("UPDATE company_staff SET wages = wages + ? WHERE member = ?", wages, member.toString());
    }

    public boolean isOrderOpen(long orderId) {
        return !query("SELECT 1 FROM company_open WHERE order_id = ?", rs -> 1, orderId).isEmpty();
    }

    public void setOrderOpen(long orderId, boolean open) {
        if (open) {
            update("INSERT OR IGNORE INTO company_open (order_id) VALUES (?)", orderId);
        } else {
            update("DELETE FROM company_open WHERE order_id = ?", orderId);
        }
    }

    public void addContrib(long orderId, UUID member, long amount) {
        update("INSERT INTO company_contrib (order_id, member, amount) VALUES (?, ?, ?) "
                + "ON CONFLICT(order_id, member) DO UPDATE SET amount = amount + excluded.amount",
                orderId, member.toString(), amount);
    }

    public List<Contrib> getContribs(long orderId) {
        return query("SELECT member, amount FROM company_contrib WHERE order_id = ? ORDER BY amount DESC",
                rs -> new Contrib(UUID.fromString(rs.getString(1)), rs.getLong(2)), orderId);
    }

    public record Ai(long id, UUID owner, int level, long orderId, long made) { }

    public List<Ai> getAis(UUID owner) {
        return query("SELECT id, owner, level, order_id, made FROM company_ai WHERE owner = ? ORDER BY id ASC",
                rs -> new Ai(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getLong(4), rs.getLong(5)),
                owner.toString());
    }

    public List<Ai> getAllAis() {
        return query("SELECT id, owner, level, order_id, made FROM company_ai ORDER BY id ASC",
                rs -> new Ai(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getLong(4), rs.getLong(5)));
    }

    public Ai getAi(long id) {
        List<Ai> r = query("SELECT id, owner, level, order_id, made FROM company_ai WHERE id = ?",
                rs -> new Ai(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getLong(4), rs.getLong(5)), id);
        return r.isEmpty() ? null : r.get(0);
    }

    public void addAi(UUID owner) {
        update("INSERT INTO company_ai (owner, level, order_id, made) VALUES (?, 1, 0, 0)", owner.toString());
    }

    public void setAiLevel(long id, int level) {
        update("UPDATE company_ai SET level = ? WHERE id = ?", level, id);
    }

    public void setAiOrder(long id, long orderId) {
        update("UPDATE company_ai SET order_id = ? WHERE id = ?", orderId, id);
    }

    public void addAiMade(long id, long items) {
        update("UPDATE company_ai SET made = made + ? WHERE id = ?", items, id);
    }

    /** Löscht die Firma, ihre Angestellten und die Freigaben. */
    public void deleteCompany(UUID owner) {
        update("DELETE FROM company_staff WHERE owner = ?", owner.toString());
        update("DELETE FROM company_ai WHERE owner = ?", owner.toString());
        update("DELETE FROM companies WHERE owner = ?", owner.toString());
    }

    /** Löscht alle Merkzettel-Einträge, deren Schlüssel mit prefix beginnt. */
    public void deleteMetaPrefix(String prefix) {
        update("DELETE FROM meta WHERE substr(key, 1, ?) = ?", prefix.length(), prefix);
    }

    public java.util.Map<String, Long> getMetaPrefix(String prefix) {
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();
        List<String[]> rows = query("SELECT key, value FROM meta WHERE key LIKE ?",
                rs -> new String[]{rs.getString(1), rs.getString(2)}, prefix + "%");
        for (String[] row : rows) {
            try {
                result.put(row[0], Long.parseLong(row[1]));
            } catch (NumberFormatException ignored) {
                // kein Zahlenwert
            }
        }
        return result;
    }

    public StockHolding getStockHolding(UUID uuid) {
        List<StockHolding> r = query("SELECT shares, invested FROM stock_holdings WHERE uuid = ?",
                rs -> new StockHolding(rs.getLong(1), rs.getLong(2)), uuid.toString());
        return r.isEmpty() ? new StockHolding(0, 0) : r.get(0);
    }

    public void setStockHolding(UUID uuid, long shares, long invested) {
        update("INSERT INTO stock_holdings (uuid, shares, invested) VALUES (?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET shares = excluded.shares, invested = excluded.invested",
                uuid.toString(), shares, invested);
    }

    public void deleteStockHolding(UUID uuid) {
        update("DELETE FROM stock_holdings WHERE uuid = ?", uuid.toString());
    }

    public void addStockHistory(long price) {
        update("INSERT INTO stock_history (price, at) VALUES (?, ?)", price, System.currentTimeMillis());
    }

    /** Die letzten Kurse, der neueste zuerst. */
    public List<Long> getStockHistory(int limit) {
        return query("SELECT price FROM stock_history ORDER BY id DESC LIMIT ?", rs -> rs.getLong(1), limit);
    }

    // =====================================================================
    //  Statistiken / Ranglisten
    // =====================================================================

    /** Speichert mehrere Statistiken eines Spielers auf einmal (überschreibt alte Werte). */
    public void setStats(UUID uuid, java.util.Map<String, Long> values) {
        begin();
        try {
            for (java.util.Map.Entry<String, Long> e : values.entrySet()) {
                update("INSERT INTO stats (uuid, stat, value) VALUES (?, ?, ?) "
                        + "ON CONFLICT(uuid, stat) DO UPDATE SET value = excluded.value",
                        uuid.toString(), e.getKey(), e.getValue());
            }
            commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    /** Zählt eine Statistik hoch und gibt den neuen Wert zurück. */
    public long addStat(UUID uuid, String stat, long delta) {
        update("INSERT INTO stats (uuid, stat, value) VALUES (?, ?, ?) "
                + "ON CONFLICT(uuid, stat) DO UPDATE SET value = value + excluded.value",
                uuid.toString(), stat, delta);
        return getStat(uuid, stat);
    }

    public long getStat(UUID uuid, String stat) {
        List<Long> r = query("SELECT value FROM stats WHERE uuid = ? AND stat = ?",
                rs -> rs.getLong(1), uuid.toString(), stat);
        return r.isEmpty() ? 0L : r.get(0);
    }

    /** Die Besten einer Statistik (höchster Wert zuerst; Werte von 0 zählen nicht). */
    public List<RankEntry> topStat(String stat, int limit) {
        return query("SELECT s.uuid, b.name, s.value FROM stats s JOIN balances b ON b.uuid = s.uuid "
                        + "WHERE s.stat = ? AND s.value > 0 ORDER BY s.value DESC, b.name ASC LIMIT ?",
                rs -> new RankEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)), stat, limit);
    }

    /**
     * Die wenigsten Tode: nur Spieler mit mindestens "minPlaytimeMinutes" Spielzeit (sonst würde jeder
     * Neuling mit 0 Toden gewinnen). Bei gleich vielen Toden gewinnt, wer länger gespielt hat.
     */
    public List<RankEntry> fewestDeaths(long minPlaytimeMinutes, int limit) {
        return query("SELECT d.uuid, b.name, d.value FROM stats d "
                        + "JOIN stats p ON p.uuid = d.uuid AND p.stat = 'playtime' "
                        + "JOIN balances b ON b.uuid = d.uuid "
                        + "WHERE d.stat = 'deaths' AND p.value >= ? "
                        + "ORDER BY d.value ASC, p.value DESC, b.name ASC LIMIT ?",
                rs -> new RankEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)),
                minPlaytimeMinutes, limit);
    }

    // =====================================================================
    //  Geheime Erfolge
    // =====================================================================

    /** Trägt den Erfolg ein. Gibt true zurück, wenn er NEU war (false = hatte der Spieler schon). */
    public boolean unlockSecret(UUID uuid, String id) {
        return update("INSERT OR IGNORE INTO secret_achievements (uuid, id, at, seen) VALUES (?, ?, ?, 0)",
                uuid.toString(), id, System.currentTimeMillis()) == 1;
    }

    /** id -> Zeitpunkt der Freischaltung. */
    public java.util.Map<String, Long> getSecrets(UUID uuid) {
        java.util.Map<String, Long> map = new java.util.LinkedHashMap<>();
        query("SELECT id, at FROM secret_achievements WHERE uuid = ? ORDER BY at", rs -> {
            map.put(rs.getString(1), rs.getLong(2));
            return Boolean.TRUE;
        }, uuid.toString());
        return map;
    }

    public void markSecretSeen(UUID uuid, String id) {
        update("UPDATE secret_achievements SET seen = 1 WHERE uuid = ? AND id = ?", uuid.toString(), id);
    }

    /** IDs der Erfolge, die der Spieler noch nicht gemeldet bekommen hat (werden danach als gesehen markiert). */
    public List<String> popUnseenSecrets(UUID uuid) {
        List<String> ids = query("SELECT id FROM secret_achievements WHERE uuid = ? AND seen = 0 ORDER BY at",
                rs -> rs.getString(1), uuid.toString());
        update("UPDATE secret_achievements SET seen = 1 WHERE uuid = ?", uuid.toString());
        return ids;
    }

    // =====================================================================
    //  Zuhause
    // =====================================================================

    public void setHome(UUID uuid, Home h) {
        update("INSERT INTO homes (uuid, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET world = excluded.world, x = excluded.x, y = excluded.y, "
                + "z = excluded.z, yaw = excluded.yaw, pitch = excluded.pitch",
                uuid.toString(), h.world(), h.x(), h.y(), h.z(), (double) h.yaw(), (double) h.pitch());
    }

    public Optional<Home> getHome(UUID uuid) {
        List<Home> r = query("SELECT world, x, y, z, yaw, pitch FROM homes WHERE uuid = ?",
                rs -> new Home(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        (float) rs.getDouble(5), (float) rs.getDouble(6)), uuid.toString());
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    /** Alle bekannten Spieler mit Kontostand. */
    public List<Account> allAccounts() {
        return query("SELECT uuid, name, coins FROM balances",
                rs -> new Account(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
    }

    // =====================================================================
    //  Kollektionen
    // =====================================================================

    public java.util.Set<String> getCollected(UUID uuid) {
        return new java.util.HashSet<>(query("SELECT item FROM collection_items WHERE uuid = ?",
                rs -> rs.getString(1), uuid.toString()));
    }

    public void addCollected(UUID uuid, java.util.Collection<String> items) {
        begin();
        try {
            for (String item : items) {
                update("INSERT OR IGNORE INTO collection_items (uuid, item) VALUES (?, ?)", uuid.toString(), item);
            }
            commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    /** Trägt die fertige Kollektion ein. true = war neu (dann darf die Belohnung ausgezahlt werden). */
    public boolean markCollectionDone(UUID uuid, String id) {
        return update("INSERT OR IGNORE INTO collection_done (uuid, id, at) VALUES (?, ?, ?)",
                uuid.toString(), id, System.currentTimeMillis()) == 1;
    }

    public java.util.Set<String> getDoneCollections(UUID uuid) {
        return new java.util.HashSet<>(query("SELECT id FROM collection_done WHERE uuid = ?",
                rs -> rs.getString(1), uuid.toString()));
    }

    // =====================================================================
    //  Lizenzen und Endkampf-Anmeldungen
    // =====================================================================

    public java.util.Set<String> getLicenses(UUID uuid) {
        return new java.util.HashSet<>(query("SELECT id FROM licenses WHERE uuid = ?",
                rs -> rs.getString(1), uuid.toString()));
    }

    public void addLicense(UUID uuid, String id) {
        update("INSERT OR IGNORE INTO licenses (uuid, id, at) VALUES (?, ?, ?)",
                uuid.toString(), id, System.currentTimeMillis());
    }

    public boolean isSignedUp(UUID uuid) {
        return !query("SELECT 1 FROM end_signup WHERE uuid = ?", rs -> rs.getInt(1), uuid.toString()).isEmpty();
    }

    public void signUp(UUID uuid, String name) {
        update("INSERT INTO end_signup (uuid, name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name",
                uuid.toString(), name);
    }

    public void signOff(UUID uuid) {
        update("DELETE FROM end_signup WHERE uuid = ?", uuid.toString());
    }

    public List<UUID> signedUp() {
        return query("SELECT uuid FROM end_signup", rs -> UUID.fromString(rs.getString(1)));
    }

    // =====================================================================
    //  Protokoll (tx_log), Statistik und Backup für Admins
    // =====================================================================

    public record TxEntry(long at, String name, String type, long amount, String detail) { }
    public record TypeSum(String type, long count, long sum) { }

    /** Schreibt einen Eintrag ins Protokoll. Positive Beträge = Spieler bekommt Coins, negative = zahlt. */
    public void logTx(UUID uuid, String name, String type, long amount, String detail) {
        try {
            update("INSERT INTO tx_log (at, uuid, name, type, amount, detail) VALUES (?, ?, ?, ?, ?, ?)",
                    System.currentTimeMillis(), uuid.toString(), name, type, amount, detail);
        } catch (RuntimeException e) {
            // Das Protokoll darf nie einen Kauf verhindern
            log.warning("Protokoll-Eintrag fehlgeschlagen: " + e.getMessage());
        }
    }

    /** Die letzten Einträge eines Spielers (uuid == null: von allen), neueste zuerst. */
    public List<TxEntry> recentTx(UUID uuid, int limit) {
        RowMapper<TxEntry> mapper = rs -> new TxEntry(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getString(5));
        if (uuid == null) {
            return query("SELECT at, name, type, amount, detail FROM tx_log ORDER BY id DESC LIMIT ?", mapper, limit);
        }
        return query("SELECT at, name, type, amount, detail FROM tx_log WHERE uuid = ? ORDER BY id DESC LIMIT ?",
                mapper, uuid.toString(), limit);
    }

    /** Pro Art (Server-Shop, Spieler-Shop ...): Anzahl und Summe seit einem Zeitpunkt. */
    public List<TypeSum> txSummarySince(long since) {
        return query("SELECT type, COUNT(*), SUM(ABS(amount)) FROM tx_log WHERE at >= ? GROUP BY type ORDER BY 3 DESC",
                rs -> new TypeSum(rs.getString(1), rs.getLong(2), rs.getLong(3)), since);
    }

    /** Löscht alte Protokoll-Einträge. */
    /** Eine Seite der Buchhaltung eines Spielers. mode: 0 alles, 1 nur Einnahmen, 2 nur Ausgaben (Einträge mit 0 Coins fehlen). */
    public List<TxEntry> txPage(UUID uuid, int mode, int offset, int limit) {
        String where = mode == 1 ? " AND amount > 0" : mode == 2 ? " AND amount < 0" : " AND amount != 0";
        return query("SELECT at, name, type, amount, detail FROM tx_log WHERE uuid = ?" + where
                + " ORDER BY id DESC LIMIT ? OFFSET ?",
                rs -> new TxEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5)),
                uuid.toString(), limit, offset);
    }

    public int txCount(UUID uuid, int mode) {
        String where = mode == 1 ? " AND amount > 0" : mode == 2 ? " AND amount < 0" : " AND amount != 0";
        List<Integer> r = query("SELECT COUNT(*) FROM tx_log WHERE uuid = ?" + where, rs -> rs.getInt(1), uuid.toString());
        return r.isEmpty() ? 0 : r.get(0);
    }

    /** Einnahmen und Ausgaben (als positive Zahl) seit einem Zeitpunkt: {Einnahmen, Ausgaben}. */
    public long[] txSums(UUID uuid, long since) {
        List<long[]> r = query("SELECT COALESCE(SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN amount < 0 THEN -amount ELSE 0 END), 0) FROM tx_log WHERE uuid = ? AND at >= ?",
                rs -> new long[]{rs.getLong(1), rs.getLong(2)}, uuid.toString(), since);
        return r.isEmpty() ? new long[2] : r.get(0);
    }

    /** Einnahmen seit einem Zeitpunkt, nach Art gruppiert (größte zuerst). */
    public List<TxEntry> txIncomeByType(UUID uuid, long since, int limit) {
        return query("SELECT 0, '', type, SUM(amount) AS s, '' FROM tx_log WHERE uuid = ? AND amount > 0 AND at >= ? "
                + "GROUP BY type ORDER BY s DESC LIMIT ?",
                rs -> new TxEntry(0, "", rs.getString(3), rs.getLong(4), ""), uuid.toString(), since, limit);
    }

    /** Alle Buchungen seit einem Zeitpunkt als {Zeit, Betrag} (für die Wochenübersicht). */
    public List<long[]> txSince(UUID uuid, long since) {
        return query("SELECT at, amount FROM tx_log WHERE uuid = ? AND at >= ? AND amount != 0",
                rs -> new long[]{rs.getLong(1), rs.getLong(2)}, uuid.toString(), since);
    }

    /** Einnahmen (income = true) oder Ausgaben seit einem Zeitpunkt, nach Art gruppiert (größte zuerst, Betrag positiv). */
    public List<TxEntry> txByType(UUID uuid, long since, boolean income, int limit) {
        String cond = income ? "amount > 0" : "amount < 0";
        return query("SELECT 0, '', type, ABS(SUM(amount)) AS s, '' FROM tx_log WHERE uuid = ? AND " + cond
                + " AND at >= ? GROUP BY type ORDER BY s DESC LIMIT ?",
                rs -> new TxEntry(0, "", rs.getString(3), rs.getLong(4), ""), uuid.toString(), since, limit);
    }

    // ------------------------------------------------------------------
    //  Schutzgeld
    // ------------------------------------------------------------------

    public record Protection(UUID offerer, int revier, UUID target) { }

    private Protection mapProtection(ResultSet rs) throws SQLException {
        return new Protection(UUID.fromString(rs.getString("offerer")), rs.getInt("revier"), UUID.fromString(rs.getString("target")));
    }

    /** Verträge, bei denen dieser Spieler Schutzgeld zahlt. */
    public List<Protection> protectionsOf(UUID target) {
        return query("SELECT * FROM protection WHERE target = ?", this::mapProtection, target.toString());
    }

    /** Verträge, bei denen dieser Spieler (Pate/Boss) Schutzgeld bekommt. */
    public List<Protection> protectionsBy(UUID offerer) {
        return query("SELECT * FROM protection WHERE offerer = ?", this::mapProtection, offerer.toString());
    }

    public List<Protection> allProtections() {
        return query("SELECT * FROM protection", this::mapProtection);
    }

    public boolean hasProtection(UUID offerer, int revier, UUID target) {
        return !query("SELECT 1 FROM protection WHERE offerer = ? AND revier = ? AND target = ?", rs -> 1,
                offerer.toString(), revier, target.toString()).isEmpty();
    }

    public void addProtection(UUID offerer, int revier, UUID target) {
        update("INSERT OR REPLACE INTO protection (offerer, revier, target, at) VALUES (?, ?, ?, ?)",
                offerer.toString(), revier, target.toString(), System.currentTimeMillis());
    }

    public boolean removeProtection(UUID offerer, int revier, UUID target) {
        return update("DELETE FROM protection WHERE offerer = ? AND revier = ? AND target = ?",
                offerer.toString(), revier, target.toString()) > 0;
    }

    public void clearProtections() {
        update("DELETE FROM protection");
    }

    // ------------------------------------------------------------------
    //  Hehlerwaren-Auktion
    // ------------------------------------------------------------------

    public <T> List<T> auctionsOpen(AuctionMapper<T> mapper) {
        return query("SELECT * FROM auction WHERE done = 0 ORDER BY id DESC LIMIT 1", mapper::map);
    }

    @FunctionalInterface
    public interface AuctionMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    public void auctionCreate(ItemStack item, long minBid, long ends) {
        update("INSERT INTO auction (item, min_bid, bid, ends, done) VALUES (?, ?, 0, ?, 0)",
                item.serializeAsBytes(), minBid, ends);
    }

    public void auctionSetBid(long id, long bid, UUID bidder, String bidderName) {
        update("UPDATE auction SET bid = ?, bidder = ?, bidder_name = ? WHERE id = ?", bid, bidder.toString(), bidderName, id);
    }

    public void auctionClose(long id) {
        update("UPDATE auction SET done = 1 WHERE id = ?", id);
    }

    public void claimAdd(UUID uuid, ItemStack item) {
        update("INSERT INTO auction_claim (uuid, item, at) VALUES (?, ?, ?)", uuid.toString(), item.serializeAsBytes(),
                System.currentTimeMillis());
    }

    public <T> List<T> claims(UUID uuid, AuctionMapper<T> mapper) {
        return query("SELECT * FROM auction_claim WHERE uuid = ?", mapper::map, uuid.toString());
    }

    public boolean claimRemove(long id) {
        return update("DELETE FROM auction_claim WHERE id = ?", id) > 0;
    }

    public void pruneTx(long olderThan) {
        update("DELETE FROM tx_log WHERE at < ?", olderThan);
    }

    public long sumCoins() {
        List<Long> r = query("SELECT COALESCE(SUM(coins), 0) FROM balances", rs -> rs.getLong(1));
        return r.isEmpty() ? 0 : r.get(0);
    }

    public long countAccounts() {
        List<Long> r = query("SELECT COUNT(*) FROM balances", rs -> rs.getLong(1));
        return r.isEmpty() ? 0 : r.get(0);
    }

    public long countOwnedInvestments() {
        List<Long> r = query("SELECT COUNT(*) FROM investments", rs -> rs.getLong(1));
        return r.isEmpty() ? 0 : r.get(0);
    }

    public boolean playerExists(UUID uuid) {
        return !query("SELECT 1 FROM balances WHERE uuid = ?", rs -> 1, uuid.toString()).isEmpty();
    }

    /** Kopiert die komplette Datenbank in eine neue Datei (VACUUM INTO = sauberer, konsistenter Abzug). */
    public void backupTo(File target) {
        try (Statement st = conn.createStatement()) {
            st.execute("VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'");
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    // =====================================================================
    //  Login-Serie
    // =====================================================================

    public record Streak(String lastDay, int streak, int best) { }

    public Optional<Streak> getStreak(UUID uuid) {
        List<Streak> r = query("SELECT last_day, streak, best FROM login_streak WHERE uuid = ?",
                rs -> new Streak(rs.getString(1), rs.getInt(2), rs.getInt(3)), uuid.toString());
        return r.isEmpty() ? Optional.empty() : Optional.of(r.get(0));
    }

    public void setStreak(UUID uuid, String lastDay, int streak, int best) {
        update("INSERT INTO login_streak (uuid, last_day, streak, best) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET last_day = excluded.last_day, streak = excluded.streak, "
                + "best = excluded.best", uuid.toString(), lastDay, streak, best);
    }

    // =====================================================================
    //  Tägliche Aufgaben
    // =====================================================================

    public record QuestRow(int idx, long baseline, boolean done) { }

    public List<QuestRow> getQuests(UUID uuid, String day) {
        return query("SELECT idx, baseline, done FROM quest_progress WHERE uuid = ? AND day = ? ORDER BY idx",
                rs -> new QuestRow(rs.getInt(1), rs.getLong(2), rs.getInt(3) == 1), uuid.toString(), day);
    }

    public void insertQuest(UUID uuid, String day, int idx, long baseline) {
        update("INSERT OR IGNORE INTO quest_progress (uuid, day, idx, baseline, done) VALUES (?, ?, ?, ?, 0)",
                uuid.toString(), day, idx, baseline);
    }

    /** true = die Aufgabe war noch nicht als erledigt eingetragen (dann darf die Belohnung ausgezahlt werden). */
    public boolean markQuestDone(UUID uuid, String day, int idx) {
        return update("UPDATE quest_progress SET done = 1 WHERE uuid = ? AND day = ? AND idx = ? AND done = 0",
                uuid.toString(), day, idx) == 1;
    }

    public record OldQuestRow(String day, int idx, long baseline) { }

    /** Nicht geschaffte Aufgaben früherer Tage (die neuesten zuerst), sie bleiben bestehen. */
    public List<OldQuestRow> getOpenOldQuests(UUID uuid, String today, int limit) {
        return query("SELECT day, idx, baseline FROM quest_progress WHERE uuid = ? AND day < ? AND done = 0 AND idx < 99 "
                        + "ORDER BY day DESC, idx ASC LIMIT ?",
                rs -> new OldQuestRow(rs.getString(1), rs.getInt(2), rs.getLong(3)), uuid.toString(), today, limit);
    }

    /** Alte Aufgaben-Einträge aufräumen: erledigte von früher, und offene, die älter als 60 Tage sind. */
    public void pruneQuests(String keepDay) {
        String cutoff;
        try {
            cutoff = java.time.LocalDate.parse(keepDay).minusDays(60).toString();
        } catch (RuntimeException e) {
            cutoff = "0000-00-00";
        }
        update("DELETE FROM quest_progress WHERE day < ? AND (done = 1 OR day < ?)", keepDay, cutoff);
    }

    // =====================================================================
    //  Titel
    // =====================================================================

    /** Dauerhafter Titel. true = war neu. */
    public boolean addTitle(UUID uuid, String id) {
        return update("INSERT OR IGNORE INTO player_titles (uuid, id, at) VALUES (?, ?, ?)",
                uuid.toString(), id, System.currentTimeMillis()) == 1;
    }

    public java.util.Set<String> getTitles(UUID uuid) {
        return new java.util.HashSet<>(query("SELECT id FROM player_titles WHERE uuid = ?",
                rs -> rs.getString(1), uuid.toString()));
    }

    /** Alle aktuellen Halter von Platz-1-Titeln: Titel-Id -> Spieler. */
    public java.util.Map<String, java.util.Set<UUID>> getRankTitleHolders() {
        java.util.Map<String, java.util.Set<UUID>> result = new java.util.LinkedHashMap<>();
        query("SELECT title_id, uuid FROM rank_titles", rs -> {
            result.computeIfAbsent(rs.getString(1), k -> new java.util.HashSet<>())
                    .add(UUID.fromString(rs.getString(2)));
            return null;
        });
        return result;
    }

    public java.util.Set<String> getRankTitles(UUID uuid) {
        return new java.util.HashSet<>(query("SELECT title_id FROM rank_titles WHERE uuid = ?",
                rs -> rs.getString(1), uuid.toString()));
    }

    /** Ersetzt alle Platz-1-Titel auf einmal. */
    public void replaceRankTitles(java.util.Map<String, java.util.Set<UUID>> holders) {
        begin();
        try {
            update("DELETE FROM rank_titles");
            for (java.util.Map.Entry<String, java.util.Set<UUID>> e : holders.entrySet()) {
                for (UUID uuid : e.getValue()) {
                    update("INSERT OR IGNORE INTO rank_titles (title_id, uuid) VALUES (?, ?)",
                            e.getKey(), uuid.toString());
                }
            }
            commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    /** "auto" (Standard), "none" oder eine Titel-Id. */
    public String getTitleChoice(UUID uuid) {
        List<String> r = query("SELECT choice FROM title_choice WHERE uuid = ?", rs -> rs.getString(1), uuid.toString());
        return r.isEmpty() ? "auto" : r.get(0);
    }

    public void setTitleChoice(UUID uuid, String choice) {
        update("INSERT INTO title_choice (uuid, choice) VALUES (?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET choice = excluded.choice", uuid.toString(), choice);
    }

    // =====================================================================
    //  Schwarzmarkt und Kopfgelder
    // =====================================================================

    /** Ersetzt alle Schwarzmarkt-Angebote durch neue (jedes Angebot gibt es nur einmal). */
    public void replaceBlackOffers(List<Offer> offers, long stamp) {
        begin();
        try {
            update("DELETE FROM bm_offers");
            for (Offer o : offers) {
                update("INSERT INTO bm_offers (slot, item, price, stamp, sold) VALUES (?, ?, ?, ?, 0)",
                        o.slot(), o.item().serializeAsBytes(), o.price(), stamp);
            }
            commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    public List<BlackOffer> getBlackOffers() {
        return query("SELECT slot, item, price, stamp, sold FROM bm_offers ORDER BY slot", rs -> {
            ItemStack item = readItem(rs, "item");
            return item == null ? null : new BlackOffer(rs.getInt(1), item, rs.getLong(3), rs.getLong(4), rs.getInt(5) != 0);
        });
    }

    /** Markiert ein Angebot als verkauft. true, wenn es noch zu haben war. */
    public boolean markBlackSold(int slot, long stamp) {
        return update("UPDATE bm_offers SET sold = 1 WHERE slot = ? AND stamp = ? AND sold = 0", slot, stamp) == 1;
    }

    public void insertBlackSell(UUID owner, ItemStack item, long price, int revier) {
        update("INSERT INTO bm_sell (owner, item, price, at, revier) VALUES (?, ?, ?, ?, ?)",
                owner.toString(), item.serializeAsBytes(), price, System.currentTimeMillis(), revier);
    }

    private BlackSell mapSell(ResultSet rs) throws SQLException {
        ItemStack item = readItem(rs, "item");
        return item == null ? null
                : new BlackSell(rs.getLong(1), UUID.fromString(rs.getString(2)), item, rs.getLong(4), rs.getInt(5));
    }

    /** Alle eingestellten Items eines Spielers in einem Revier. */
    public List<BlackSell> getBlackSells(UUID owner, int revier) {
        return query("SELECT id, owner, item, price, revier FROM bm_sell WHERE owner = ? AND revier = ? ORDER BY id",
                this::mapSell, owner.toString(), revier);
    }

    public List<BlackSell> getAllBlackSells() {
        return query("SELECT id, owner, item, price, revier FROM bm_sell ORDER BY id", this::mapSell);
    }

    public int countBlackSells(UUID owner, int revier) {
        List<Integer> r = query("SELECT COUNT(*) FROM bm_sell WHERE owner = ? AND revier = ?",
                rs -> rs.getInt(1), owner.toString(), revier);
        return r.isEmpty() ? 0 : r.get(0);
    }

    // ---- Reviere ----

    public void addRevierSale(int revier, UUID uuid, long coins) {
        update("INSERT INTO revier_sales (revier, uuid, coins) VALUES (?, ?, ?) "
                + "ON CONFLICT(revier, uuid) DO UPDATE SET coins = coins + excluded.coins",
                revier, uuid.toString(), coins);
    }

    /** Die besten Verkäufer eines Reviers (revier 0 = alle Reviere zusammen). */
    public List<RevierEntry> topRevier(int revier, int limit) {
        if (revier == 0) {
            return query("SELECT uuid, SUM(coins) AS c FROM revier_sales GROUP BY uuid HAVING c > 0 "
                    + "ORDER BY c DESC, uuid LIMIT ?", rs -> new RevierEntry(UUID.fromString(rs.getString(1)), rs.getLong(2)), limit);
        }
        return query("SELECT uuid, coins FROM revier_sales WHERE revier = ? AND coins > 0 "
                + "ORDER BY coins DESC, uuid LIMIT ?", rs -> new RevierEntry(UUID.fromString(rs.getString(1)), rs.getLong(2)),
                revier, limit);
    }

    /** Umsatz eines Spielers in einem Revier (revier 0 = zusammen). */
    public long revierSalesOf(UUID uuid, int revier) {
        List<Long> r = revier == 0
                ? query("SELECT COALESCE(SUM(coins), 0) FROM revier_sales WHERE uuid = ?", rs -> rs.getLong(1), uuid.toString())
                : query("SELECT coins FROM revier_sales WHERE uuid = ? AND revier = ?", rs -> rs.getLong(1), uuid.toString(), revier);
        return r.isEmpty() ? 0 : r.get(0);
    }

    public void clearRevierSales() {
        update("DELETE FROM revier_sales");
    }

    public List<RevierBoss> getRevierBosses() {
        return query("SELECT revier, uuid, coins FROM revier_boss ORDER BY revier",
                rs -> new RevierBoss(rs.getInt(1), UUID.fromString(rs.getString(2)), rs.getLong(3)));
    }

    public void replaceRevierBosses(List<RevierBoss> bosses) {
        update("DELETE FROM revier_boss");
        for (RevierBoss b : bosses) {
            update("INSERT INTO revier_boss (revier, uuid, coins) VALUES (?, ?, ?)", b.revier(), b.uuid().toString(), b.coins());
        }
    }

    public boolean deleteBlackSell(long id) {
        return update("DELETE FROM bm_sell WHERE id = ?", id) == 1;
    }

    public void addBounty(UUID target, String targetName, UUID placer, String placerName, long amount) {
        update("INSERT INTO bounties (target, target_name, placer, placer_name, amount, at) VALUES (?, ?, ?, ?, ?, ?)",
                target.toString(), targetName, placer.toString(), placerName, amount, System.currentTimeMillis());
    }

    private static Bounty mapBounty(ResultSet rs) throws SQLException {
        return new Bounty(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3),
                UUID.fromString(rs.getString(4)), rs.getString(5), rs.getLong(6), rs.getLong(7));
    }

    public List<Bounty> getBounties() {
        return query("SELECT id, target, target_name, placer, placer_name, amount, at FROM bounties "
                + "ORDER BY amount DESC, id ASC", Database::mapBounty);
    }

    public List<Bounty> getBountiesOn(UUID target) {
        return query("SELECT id, target, target_name, placer, placer_name, amount, at FROM bounties "
                + "WHERE target = ? ORDER BY id ASC", Database::mapBounty, target.toString());
    }

    /** Zählt bei allen Kopfgeldern einen Shop-Reset dazu. */
    public void bumpBountyResets() {
        update("UPDATE bounties SET resets = resets + 1");
    }

    /** Kopfgelder, die mindestens so viele Shop-Resets erlebt haben. */
    public List<Bounty> getBountiesWithResets(int minResets) {
        return query("SELECT id, target, target_name, placer, placer_name, amount, at FROM bounties "
                + "WHERE resets >= ? ORDER BY id ASC", Database::mapBounty, minResets);
    }

    /** Wie viele Resets ein Kopfgeld schon erlebt hat. */
    public int bountyResets(long id) {
        List<Integer> r = query("SELECT resets FROM bounties WHERE id = ?", rs -> rs.getInt(1), id);
        return r.isEmpty() ? 0 : r.get(0);
    }

    /** Löscht ein Kopfgeld. true, wenn es noch da war. */
    public boolean deleteBounty(long id) {
        return update("DELETE FROM bounties WHERE id = ?", id) == 1;
    }

    public long getBountyTotalOn(UUID target) {
        long sum = 0;
        for (Bounty b : getBountiesOn(target)) {
            sum += b.amount();
        }
        return sum;
    }

    // =====================================================================
    //  Krypto und Miner
    // =====================================================================

    public void addCryptoHistory(String kind, long price) {
        update("INSERT INTO crypto_history (kind, price) VALUES (?, ?)", kind, price);
    }

    /** Kursverlauf, der neueste zuerst. */
    public List<Long> getCryptoHistory(String kind, int days) {
        return query("SELECT price FROM crypto_history WHERE kind = ? ORDER BY id DESC LIMIT ?",
                rs -> rs.getLong(1), kind, days);
    }

    /** Rug Pull: alle Bestände dieser Währung schrumpfen um "percent" Prozent. */
    public int wipeCryptoHoldings(String kind, int percent) {
        int keep = Math.max(0, Math.min(100, 100 - percent));
        return update("UPDATE crypto_holdings SET units = units * ? / 100 WHERE kind = ?", keep, kind);
    }

    public CryptoHolding getCryptoHolding(UUID uuid, String kind) {
        List<CryptoHolding> r = query("SELECT units, invested FROM crypto_holdings WHERE uuid = ? AND kind = ?",
                rs -> new CryptoHolding(rs.getLong(1), rs.getLong(2)), uuid.toString(), kind);
        return r.isEmpty() ? new CryptoHolding(0, 0) : r.get(0);
    }

    public void setCryptoHolding(UUID uuid, String kind, long units, long invested) {
        if (units <= 0) {
            update("DELETE FROM crypto_holdings WHERE uuid = ? AND kind = ?", uuid.toString(), kind);
            return;
        }
        update("INSERT INTO crypto_holdings (uuid, kind, units, invested) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid, kind) DO UPDATE SET units = excluded.units, invested = excluded.invested",
                uuid.toString(), kind, units, invested);
    }

    /** Stufe eines Miners (0 = nicht gekauft). */
    public int getMinerLevel(UUID uuid, int idx) {
        List<Integer> r = query("SELECT level FROM miners WHERE uuid = ? AND idx = ?",
                rs -> rs.getInt(1), uuid.toString(), idx);
        return r.isEmpty() ? 0 : r.get(0);
    }

    public void setMinerLevel(UUID uuid, int idx, int level) {
        update("INSERT INTO miners (uuid, idx, level) VALUES (?, ?, ?) "
                + "ON CONFLICT(uuid, idx) DO UPDATE SET level = excluded.level", uuid.toString(), idx, level);
    }

    public int countMiners(UUID uuid) {
        List<Integer> r = query("SELECT COUNT(*) FROM miners WHERE uuid = ?", rs -> rs.getInt(1), uuid.toString());
        return r.isEmpty() ? 0 : r.get(0);
    }

    private static List<String> splitList(String text) {
        List<String> list = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    list.add(part);
                }
            }
        }
        return list;
    }

    public List<MinerQuest> getMinerQuests(UUID uuid) {
        return query("SELECT miner, day, blocks, done, finished, reward FROM miner_quests WHERE uuid = ?",
                rs -> new MinerQuest(uuid, rs.getInt(1), rs.getLong(2), splitList(rs.getString(3)),
                        splitList(rs.getString(4)), rs.getInt(5) != 0, rs.getLong(6)), uuid.toString());
    }

    public void saveMinerQuest(MinerQuest q) {
        update("INSERT INTO miner_quests (uuid, miner, day, blocks, done, finished, reward) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(uuid, miner) DO UPDATE SET day = excluded.day, blocks = excluded.blocks, "
                + "done = excluded.done, finished = excluded.finished, reward = excluded.reward",
                q.uuid().toString(), q.miner(), q.day(), String.join(",", q.blocks()), String.join(",", q.done()),
                q.finished() ? 1 : 0, q.reward());
    }
}
