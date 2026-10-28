package de.coinshop;

import de.coinshop.Database.Ownership;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.gui.Menu;
import de.coinshop.gui.TradeMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handel zwischen zwei Spielern (/handel Spieler).
 *
 * Beide legen in einem gemeinsamen Fenster ihr Angebot hinein: Coins und/oder Set-Items aus den Investitionen.
 * Jede Seite sieht das Angebot der anderen. Der Handel passiert nur, wenn BEIDE zustimmen. Ändert einer sein
 * Angebot, müssen beide neu zustimmen. Alles läuft in einer Datenbank-Transaktion: ganz oder gar nicht.
 */
public final class TradeManager implements CommandExecutor, TabCompleter, Listener {

    public static final int MAX_ITEMS = 12;

    /** Ein Angebot einer Seite. */
    public static final class Offer {
        public long coins;
        public long shares;   // Aktien
        public long krypto;   // Krypto in Hundertsteln
        public long habibi;   // Habibi-Krypto in Hundertsteln
        public final Set<String> items = new LinkedHashSet<>();
    }

    /** Ein laufender Handel. */
    public static final class Session {
        public final UUID a;
        public final UUID b;
        public final Offer offerA = new Offer();
        public final Offer offerB = new Offer();
        public boolean confirmedA;
        public boolean confirmedB;

        Session(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        public UUID other(UUID me) {
            return me.equals(a) ? b : a;
        }

        public Offer offerOf(UUID uuid) {
            return uuid.equals(a) ? offerA : offerB;
        }

        public boolean confirmed(UUID uuid) {
            return uuid.equals(a) ? confirmedA : confirmedB;
        }

        void setConfirmed(UUID uuid, boolean value) {
            if (uuid.equals(a)) {
                confirmedA = value;
            } else {
                confirmedB = value;
            }
        }
    }

    private record Request(UUID from, long expires) { }

    private final CoinShopPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();   // je Spieler
    private final Map<UUID, Request> requests = new HashMap<>();   // Empfänger -> Anfrage
    private final Set<UUID> switching = new HashSet<>();           // Spieler, deren Fenster gerade wechselt

    public TradeManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private long requestSeconds() {
        return Math.max(10, plugin.getConfig().getLong("trade.request-seconds", 60));
    }

    private long sessionSeconds() {
        return Math.max(60, plugin.getConfig().getLong("trade.session-seconds", 300));
    }

    public Session sessionOf(UUID uuid) {
        return sessions.get(uuid);
    }

    // ------------------------------------------------------------------
    //  Befehl
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "only-players");
            return true;
        }
        try {
            if (plugin.timeline().previewFor(player)) {
                msg.send(player, "trade-preview");
                return true;
            }
            if (args.length == 0) {
                Session session = sessions.get(player.getUniqueId());
                if (session == null) {
                    msg.send(player, "trade-usage");
                } else {
                    show(player);
                }
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "annehmen", "accept" -> accept(player);
                case "ablehnen", "deny" -> deny(player);
                case "abbrechen", "cancel" -> cancel(player.getUniqueId(), "trade-cancelled-by");
                default -> request(player, args[0]);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /handel: " + e);
            msg.send(player, "error");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String option : List.of("annehmen", "ablehnen", "abbrechen")) {
                if (option.startsWith(prefix)) {
                    result.add(option);
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix) && !p.equals(sender)) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Anfrage
    // ------------------------------------------------------------------

    private void request(Player from, String targetName) {
        Messages msg = plugin.messages();
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            msg.send(from, "tpa-not-found");
            return;
        }
        if (target.equals(from)) {
            msg.send(from, "trade-self");
            return;
        }
        if (plugin.timeline().previewFor(target)) {
            msg.send(from, "trade-preview-other", "player", target.getName());
            return;
        }
        if (sessions.containsKey(from.getUniqueId())) {
            msg.send(from, "trade-already");
            return;
        }
        if (sessions.containsKey(target.getUniqueId())) {
            msg.send(from, "trade-busy", "player", target.getName());
            return;
        }
        requests.put(target.getUniqueId(), new Request(from.getUniqueId(),
                System.currentTimeMillis() + requestSeconds() * 1000L));
        msg.send(from, "trade-sent", "player", target.getName(), "seconds", requestSeconds());
        msg.send(target, "trade-received", "player", from.getName());
    }

    private void deny(Player target) {
        Messages msg = plugin.messages();
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expires() < System.currentTimeMillis()) {
            msg.send(target, "trade-none");
            return;
        }
        msg.send(target, "trade-denied");
        Player from = Bukkit.getPlayer(request.from());
        if (from != null) {
            msg.send(from, "trade-denied-sender", "player", target.getName());
        }
    }

    private void accept(Player target) {
        Messages msg = plugin.messages();
        Request request = requests.remove(target.getUniqueId());
        if (request == null || request.expires() < System.currentTimeMillis()) {
            msg.send(target, "trade-none");
            return;
        }
        Player from = Bukkit.getPlayer(request.from());
        if (from == null) {
            msg.send(target, "tpa-requester-offline");
            return;
        }
        if (sessions.containsKey(from.getUniqueId()) || sessions.containsKey(target.getUniqueId())) {
            msg.send(target, "trade-busy", "player", from.getName());
            return;
        }
        Session session = new Session(from.getUniqueId(), target.getUniqueId());
        sessions.put(from.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);
        msg.send(from, "trade-started", "player", target.getName());
        msg.send(target, "trade-started", "player", from.getName());
        show(from);
        show(target);
        // Nach einer Weile läuft der Handel von selbst ab
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(from.getUniqueId()) == session) {
                cancel(from.getUniqueId(), "trade-expired");
            }
        }, sessionSeconds() * 20L);
    }

    // ------------------------------------------------------------------
    //  Fenster
    // ------------------------------------------------------------------

    /** Öffnet (oder aktualisiert) das Handelsfenster für einen Spieler. */
    public void show(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        openMenu(player, new TradeMenu(plugin, player, session));
    }

    /** Öffnet ein beliebiges Menü, ohne dass der Handel dabei als "abgebrochen" gilt. */
    public void openMenu(Player player, Menu menu) {
        switching.add(player.getUniqueId());
        try {
            menu.open(player);
        } finally {
            Bukkit.getScheduler().runTask(plugin, () -> switching.remove(player.getUniqueId()));
        }
    }

    /** Zeigt beiden Spielern das Fenster neu (nach einer Änderung). */
    public void refreshBoth(Session session) {
        for (UUID id : new UUID[]{session.a, session.b}) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenu) {
                show(p);
            }
        }
    }

    /** Das Fenster wurde mit Esc geschlossen: Handel abbrechen. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeMenu)
                || !(event.getPlayer() instanceof Player player)
                || switching.contains(player.getUniqueId())) {
            return;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> cancel(player.getUniqueId(), "trade-cancelled-by"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        requests.remove(id);
        if (sessions.containsKey(id)) {
            cancel(id, "trade-cancelled-by");
        }
    }

    // ------------------------------------------------------------------
    //  Angebot ändern
    // ------------------------------------------------------------------

    private void changed(Session session) {
        boolean had = session.confirmedA || session.confirmedB;
        session.confirmedA = false;
        session.confirmedB = false;
        if (had) {
            for (UUID id : new UUID[]{session.a, session.b}) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    plugin.messages().send(p, "trade-changed");
                }
            }
        }
    }

    public void setCoins(Player player, long coins) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        long balance = plugin.database().getCoins(player.getUniqueId());
        long value = Math.max(0, Math.min(coins, balance));
        Offer offer = session.offerOf(player.getUniqueId());
        if (offer.coins != value) {
            offer.coins = value;
            changed(session);
        }
        refreshBoth(session);
    }

    public void addCoins(Player player, long delta) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            setCoins(player, session.offerOf(player.getUniqueId()).coins + delta);
        }
    }

    public void addItem(Player player, String itemId) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Offer offer = session.offerOf(player.getUniqueId());
        if (offer.items.size() >= MAX_ITEMS) {
            plugin.messages().send(player, "trade-full", "max", MAX_ITEMS);
        } else if (offer.items.add(itemId)) {
            changed(session);
        }
        show(player);
        refreshBoth(session);
    }

    public void removeItem(Player player, String itemId) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.offerOf(player.getUniqueId()).items.remove(itemId)) {
            changed(session);
        }
        refreshBoth(session);
    }

    public void clearOffer(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Offer offer = session.offerOf(player.getUniqueId());
        offer.coins = 0;
        offer.shares = 0;
        offer.krypto = 0;
        offer.habibi = 0;
        offer.items.clear();
        changed(session);
        refreshBoth(session);
    }

    public static boolean isEmpty(Offer o) {
        return o.coins == 0 && o.items.isEmpty() && o.shares == 0 && o.krypto == 0 && o.habibi == 0;
    }

    /** Welche Art von Börsenwert: "aktie", "krypto" oder "habibi". */
    public long held(UUID uuid, String kind) {
        Database db = plugin.database();
        return switch (kind) {
            case "aktie" -> db.getStockHolding(uuid).shares();
            case "krypto" -> db.getCryptoHolding(uuid, CryptoManager.KRYPTO).units();
            default -> db.getCryptoHolding(uuid, CryptoManager.DUNKEL).units();
        };
    }

    public long offered(Offer o, String kind) {
        return switch (kind) {
            case "aktie" -> o.shares;
            case "krypto" -> o.krypto;
            default -> o.habibi;
        };
    }

    /** Darf der Spieler Habibi-Krypto handeln (Schwarzmarkt freigeschaltet und nicht abgemeldet)? */
    public boolean habibiAllowed(UUID uuid) {
        return plugin.licenses().blackMarketUnlocked(uuid) && !plugin.blackMarket().bountyOptOut(uuid);
    }

    public void setAsset(Player player, String kind, long amount) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        UUID me = player.getUniqueId();
        if (kind.equals("habibi") && !(habibiAllowed(me) && habibiAllowed(session.other(me)))) {
            plugin.messages().send(player, "trade-habibi-denied");
            return;
        }
        long value = Math.max(0, Math.min(amount, held(me, kind)));
        Offer offer = session.offerOf(me);
        if (offered(offer, kind) != value) {
            switch (kind) {
                case "aktie" -> offer.shares = value;
                case "krypto" -> offer.krypto = value;
                default -> offer.habibi = value;
            }
            changed(session);
        }
        refreshBoth(session);
    }

    /** Frage die Menge eines Börsenwerts per Chat ab (Krypto mit Komma, z. B. 1,5). */
    public void askAsset(Player player, String kind) {
        Messages msg = plugin.messages();
        switching.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> switching.remove(player.getUniqueId()));
        msg.send(player, kind.equals("aktie") ? "trade-ask-shares" : "trade-ask-crypto");
        plugin.prompts().start(player, text -> {
            if (!text.equalsIgnoreCase("abbrechen")) {
                Long value = kind.equals("aktie")
                        ? parseWhole(text)
                        : CryptoManager.parseUnits(text);
                if (value == null) {
                    msg.send(player, "ask-invalid");
                } else {
                    setAsset(player, kind, value);
                }
            }
            show(player);
        });
    }

    private static Long parseWhole(String text) {
        try {
            return Long.parseLong(text.trim().replace(".", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Verschiebt Aktien samt anteiligem Einstandswert. Läuft innerhalb der Handels-Transaktion. */
    private boolean moveShares(UUID from, UUID to, long shares) {
        if (shares <= 0) {
            return true;
        }
        Database db = plugin.database();
        Database.StockHolding a = db.getStockHolding(from);
        if (a.shares() < shares) {
            return false;
        }
        long part = a.invested() * shares / a.shares();
        db.setStockHolding(from, a.shares() - shares, a.invested() - part);
        Database.StockHolding b = db.getStockHolding(to);
        db.setStockHolding(to, b.shares() + shares, b.invested() + part);
        return true;
    }

    private boolean moveCrypto(UUID from, UUID to, String kind, long units) {
        if (units <= 0) {
            return true;
        }
        Database db = plugin.database();
        Database.CryptoHolding a = db.getCryptoHolding(from, kind);
        if (a.units() < units) {
            return false;
        }
        long part = a.invested() * units / a.units();
        db.setCryptoHolding(from, kind, a.units() - units, a.invested() - part);
        Database.CryptoHolding b = db.getCryptoHolding(to, kind);
        db.setCryptoHolding(to, kind, b.units() + units, b.invested() + part);
        return true;
    }

    private boolean moveAssets(Offer offer, UUID from, UUID to) {
        if (offer.habibi > 0 && !(habibiAllowed(from) && habibiAllowed(to))) {
            return false;
        }
        return moveShares(from, to, offer.shares)
                && moveCrypto(from, to, CryptoManager.KRYPTO, offer.krypto)
                && moveCrypto(from, to, CryptoManager.DUNKEL, offer.habibi);
    }

    /** Kurzbeschreibung der Börsenwerte eines Angebots. */
    public String assetText(Offer o) {
        List<String> parts = new ArrayList<>();
        if (o.shares > 0) {
            parts.add(o.shares + " Aktien");
        }
        if (o.krypto > 0) {
            parts.add(CryptoManager.fmt(o.krypto) + " Kryptocoin");
        }
        if (o.habibi > 0) {
            parts.add(CryptoManager.fmt(o.habibi) + " Habibi-Krypto");
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    /** Frage den Betrag per Chat ab. */
    public void askCoins(Player player) {
        Messages msg = plugin.messages();
        switching.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> switching.remove(player.getUniqueId()));
        msg.send(player, "trade-ask-coins");
        plugin.prompts().start(player, text -> {
            if (!text.equalsIgnoreCase("abbrechen")) {
                try {
                    long value = Long.parseLong(text.trim().replace(".", ""));
                    setCoins(player, value);
                } catch (NumberFormatException e) {
                    msg.send(player, "ask-invalid");
                }
            }
            show(player);
        });
    }

    // ------------------------------------------------------------------
    //  Zustimmung und Ausführung
    // ------------------------------------------------------------------

    public void toggleConfirm(Player player) {
        Messages msg = plugin.messages();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        UUID me = player.getUniqueId();
        Offer mine = session.offerOf(me);
        Offer theirs = session.offerOf(session.other(me));
        if (isEmpty(mine) && isEmpty(theirs)) {
            msg.send(player, "trade-empty");
            return;
        }
        boolean now = !session.confirmed(me);
        session.setConfirmed(me, now);
        Player other = Bukkit.getPlayer(session.other(me));
        if (other != null) {
            msg.send(other, now ? "trade-partner-confirmed" : "trade-partner-unconfirmed", "player", player.getName());
        }
        if (session.confirmedA && session.confirmedB) {
            execute(session);
            return;
        }
        refreshBoth(session);
    }

    private void execute(Session session) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        Player pa = Bukkit.getPlayer(session.a);
        Player pb = Bukkit.getPlayer(session.b);
        if (pa == null || pb == null) {
            cancel(session.a, "trade-cancelled-by");
            return;
        }
        boolean ok = false;
        db.begin();
        try {
            ok = true;
            for (String id : session.offerA.items) {
                ok &= db.transferOwnership(id, session.a, session.b);
            }
            for (String id : session.offerB.items) {
                ok &= db.transferOwnership(id, session.b, session.a);
            }
            ok = ok && moveAssets(session.offerA, session.a, session.b)
                    && moveAssets(session.offerB, session.b, session.a);
            if (ok && session.offerA.coins > 0) {
                ok = db.takeCoins(session.a, session.offerA.coins);
            }
            if (ok && session.offerB.coins > 0) {
                ok = db.takeCoins(session.b, session.offerB.coins);
            }
            if (ok) {
                if (session.offerA.coins > 0) {
                    db.addCoins(session.b, session.offerA.coins);
                }
                if (session.offerB.coins > 0) {
                    db.addCoins(session.a, session.offerB.coins);
                }
                db.commit();
            } else {
                db.rollback();
            }
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Handel: " + e.getMessage());
            ok = false;
        }
        Offer offerA = session.offerA;
        Offer offerB = session.offerB;
        if (!ok) {
            // Etwas stimmt nicht mehr (Item weg, zu wenig Coins): Handel beenden
            sessions.remove(session.a);
            sessions.remove(session.b);
            for (Player p : new Player[]{pa, pb}) {
                msg.send(p, "trade-failed");
                switching.add(p.getUniqueId());
                p.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> switching.remove(p.getUniqueId()));
            }
            return;
        }
        sessions.remove(session.a);
        sessions.remove(session.b);
        db.logTx(session.a, pa.getName(), "handel", offerB.coins - offerA.coins,
                "Handel mit " + pb.getName() + ": " + offerA.items.size() + " Items gegeben (" + assetText(offerA) + "), "
                        + offerB.items.size() + " erhalten (" + assetText(offerB) + ")");
        db.logTx(session.b, pb.getName(), "handel", offerA.coins - offerB.coins,
                "Handel mit " + pa.getName() + ": " + offerB.items.size() + " Items gegeben (" + assetText(offerB) + "), "
                        + offerA.items.size() + " erhalten (" + assetText(offerA) + ")");
        for (Player p : new Player[]{pa, pb}) {
            msg.send(p, "trade-done", "player", p.equals(pa) ? pb.getName() : pa.getName());
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            switching.add(p.getUniqueId());
            p.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> switching.remove(p.getUniqueId()));
        }
    }

    /** Bricht den Handel ab. "key" ist die Nachricht an den ANDEREN Spieler (mit dem Namen des Abbrechenden). */
    public void cancel(UUID who, String key) {
        Session session = sessions.remove(who);
        if (session == null) {
            return;
        }
        UUID otherId = session.other(who);
        sessions.remove(otherId);
        Player me = Bukkit.getPlayer(who);
        Player other = Bukkit.getPlayer(otherId);
        for (Player p : new Player[]{me, other}) {
            if (p == null) {
                continue;
            }
            if (key.equals("trade-expired") || p != me) {
                plugin.messages().send(p, key, "player", me == null ? "?" : me.getName());
            } else {
                plugin.messages().send(p, "trade-cancelled");
            }
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof TradeMenu) {
                switching.add(p.getUniqueId());
                p.closeInventory();
                Player pp = p;
                Bukkit.getScheduler().runTask(plugin, () -> switching.remove(pp.getUniqueId()));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Hilfen für die Fenster
    // ------------------------------------------------------------------

    /** Alle Set-Items, die dem Spieler gehören (itemId, in der Reihenfolge der Sets). */
    public List<String> ownedItems(UUID uuid) {
        List<String> list = new ArrayList<>();
        Map<String, Ownership> map = plugin.investments().ownershipMap();
        for (SetDef set : plugin.investments().sets()) {
            for (int i = 0; i < set.items().size(); i++) {
                Ownership o = map.get(InvestmentManager.itemId(set, i));
                if (o != null && o.owner().equals(uuid)) {
                    list.add(InvestmentManager.itemId(set, i));
                }
            }
        }
        return list;
    }

    /** Das Set zu einer Item-Kennung, oder null. */
    public SetDef setOf(String itemId) {
        int colon = itemId.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String setId = itemId.substring(0, colon);
        for (SetDef set : plugin.investments().sets()) {
            if (set.id().equals(setId)) {
                return set;
            }
        }
        return null;
    }

    public int indexOf(String itemId) {
        try {
            return Integer.parseInt(itemId.substring(itemId.indexOf(':') + 1));
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
