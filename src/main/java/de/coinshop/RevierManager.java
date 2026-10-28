package de.coinshop;

import de.coinshop.Database.BlackSell;
import de.coinshop.Database.RevierBoss;
import de.coinshop.Database.RevierEntry;
import de.coinshop.gui.ReviereMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die Reviere des Schwarzmarkts. Es gibt 7 Reviere, die man nacheinander freikauft. In jedem Revier kann man bis zu
 * 32 Verkaufs-Slots für den Händler freikaufen. Der Umsatz (was der Händler einem zahlt) zählt für Ranglisten.
 *
 * Jeden Sonntag um 12:00 (zusammen mit dem Shop-Wechsel) wird ausgewertet:
 *  - Wer in einem Revier am meisten verkauft hat, ist dort Unterhändler (10 % Rabatt im Server-Shop bis zur nächsten Auswertung).
 *  - Wer in den meisten Revieren Boss ist (bei Gleichstand: der größte Gesamtumsatz), wird Pate und trägt den Titel Pate.
 *  - Der Pate bekommt 5 % aller Schwarzmarkt-Gebühren (aus dem Shop-Konto).
 * Danach beginnt die Woche von vorn.
 */
public final class RevierManager implements CommandExecutor, org.bukkit.event.Listener {

    public static final int COUNT = 7;
    public static final int MAX_SLOTS = 32;

    private static final String[] NAMES = {"Neue Heimat", "Schmugglerhafen", "Diebesgasse", "Nebelmarkt",
            "Meppen", "Villa der Paten", "Frankfurt"};
    private static final Material[] ICONS = {Material.BARREL, Material.MINECART, Material.BELL,
            Material.BLAST_FURNACE, Material.REDSTONE_LAMP, Material.GOLD_BLOCK, Material.ENDER_CHEST};

    private final CoinShopPlugin plugin;
    private volatile Map<Integer, RevierBoss> bossCache; // 0 = Pate

    public RevierManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Einstellungen
    // ------------------------------------------------------------------

    public String name(int revier) {
        return NAMES[Math.max(1, Math.min(COUNT, revier)) - 1];
    }

    public Material icon(int revier) {
        return ICONS[Math.max(1, Math.min(COUNT, revier)) - 1];
    }

    public long unlockPrice(int revier) {
        List<Long> list = plugin.getConfig().getLongList("revier.unlock-prices");
        long base = revier - 1 < list.size() ? Math.max(0, list.get(revier - 1))
                : (revier <= 1 ? 0 : (long) (2000 * Math.pow(5, revier - 2)));
        return plugin.blackMarket().withFee(Math.round(base * plugin.events().inflation()));
    }

    /** Startpreis des ersten Slots in diesem Revier: verdoppelt sich von Revier zu Revier. */
    public long slotStartPrice(int revier) {
        long base = Math.max(1, plugin.getConfig().getLong("revier.slot-base-price", 300));
        return base * (1L << (revier - 1));
    }

    public long pateBountyMin() {
        return Math.max(1, plugin.getConfig().getLong("revier.pate-bounty-min", 10000));
    }

    // ------------------------------------------------------------------
    //  Freischaltung
    // ------------------------------------------------------------------

    /** Wie viele Reviere hat der Spieler freigekauft? Mit der Lizenz Schmuggler ist Revier 1 gratis dabei. */
    public int unlockedCount(UUID uuid) {
        int count = (int) plugin.database().getMetaLong("rv_unlocked_" + uuid, 0);
        if (count < 1 && plugin.licenses().has(uuid, "schmuggler")) {
            count = 1;
        }
        return Math.max(0, Math.min(COUNT, count));
    }

    public boolean isUnlocked(UUID uuid, int revier) {
        return revier <= unlockedCount(uuid);
    }

    public void buyRevier(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!plugin.blackMarket().allowed(player)) {
            return;
        }
        int next = unlockedCount(uuid) + 1;
        if (next > COUNT) {
            return;
        }
        long price = unlockPrice(next);
        db.begin();
        try {
            if (price > 0 && !db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            if (price > 0) {
                db.addShopAccount(price);
                payPateAndBoss(next, price);
                db.addRevierSale(next, uuid, price); // zählt zum Umsatz im Revier
            }
            db.setMetaLong("rv_unlocked_" + uuid, next);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Freikaufen eines Reviers: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "revier-kauf", -price, "Revier " + next);
        msg.send(player, "revier-unlocked", "name", name(next), "price", price);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // ------------------------------------------------------------------
    //  Slots
    // ------------------------------------------------------------------

    /** Wie viele Slots wurden in diesem Revier schon gekauft? */
    public int bought(UUID uuid, int revier) {
        int b = (int) plugin.database().getMetaLong("rv_bought_" + uuid + "_" + revier, 0);
        if (revier == 1) {
            b = Math.max(b, (int) plugin.database().getMetaLong("bm_sell_bought_" + uuid, 0)); // Stand von Version 24
        }
        return b;
    }

    /** Kostenlose Slots: der erste in Revier 1 und die Slots der Lizenz Schwarzlager (in jedem Revier). */
    public int freeSlots(UUID uuid, int revier) {
        int free = revier == 1 ? Math.max(0, plugin.getConfig().getInt("revier.free-first-slot", 1)) : 0;
        return free + (int) plugin.licenses().value(uuid, "schwarzlager");
    }

    public int slots(UUID uuid, int revier) {
        return Math.min(MAX_SLOTS, freeSlots(uuid, revier) + bought(uuid, revier));
    }

    /** Preis des nächsten Slots: Startpreis des Reviers, jeder weitere um den Faktor revier.slot-growth teurer (höchstens slot-max-price). */
    public long nextSlotPrice(UUID uuid, int revier) {
        double growth = Math.max(1.0, plugin.getConfig().getDouble("revier.slot-growth", 1.3));
        double maxPrice = Math.max(1, plugin.getConfig().getDouble("revier.slot-max-price", 2_000_000));
        double price = Math.min(maxPrice, slotStartPrice(revier) * Math.pow(growth, bought(uuid, revier)));
        double cut = Math.min(90, plugin.titles().effect(uuid, "slot-discount", ""));
        return plugin.blackMarket().withFee(Math.max(1, Math.round(price * (1.0 - cut / 100.0) * plugin.events().inflation())));
    }

    public void buySlot(Player player, int revier) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!plugin.blackMarket().allowed(player)) {
            return;
        }
        if (!isUnlocked(uuid, revier)) {
            msg.send(player, "revier-locked");
            return;
        }
        if (slots(uuid, revier) >= MAX_SLOTS) {
            msg.send(player, "bm-sell-slot-max", "max", MAX_SLOTS);
            return;
        }
        long price = nextSlotPrice(uuid, revier);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.addShopAccount(price);
            payPateAndBoss(revier, price);
            db.addRevierSale(revier, uuid, price); // zählt zum Umsatz im Revier
            db.setMetaLong("rv_bought_" + uuid + "_" + revier, bought(uuid, revier) + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "revier-slot", -price, name(revier) + " Slot");
        msg.send(player, "bm-sell-slot-bought", "slots", slots(uuid, revier));
    }

    /** Stellt das Item aus der Haupthand in den nächsten freien Slot des Reviers. */
    public void listFromHand(Player player, int revier) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            plugin.messages().send(player, "bm-sell-hold");
            return;
        }
        openListing(player, revier, hand);
    }

    /** Öffnet die Vorschau (Menge wählen, Preis sehen) für dieses Item. */
    public void openListing(Player player, int revier, ItemStack item) {
        if (!isUnlocked(player.getUniqueId(), revier) || !plugin.blackMarket().allowed(player)) {
            return;
        }
        new de.coinshop.gui.RevierListMenu(plugin, player, revier, item, item.getAmount()).open(player);
    }

    /**
     * Stellt ein Item (Preis ist fest) in den nächsten freien Slot des Reviers ein.
     * Gibt true zurück, wenn es eingestellt wurde; dann muss der Aufrufer das Item aus dem Inventar entfernen.
     */
    public boolean list(Player player, int revier, ItemStack item) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (!plugin.blackMarket().allowed(player)) {
            return false;
        }
        if (!isUnlocked(uuid, revier)) {
            msg.send(player, "revier-locked");
            return false;
        }
        if (item == null || item.getType().isAir()) {
            return false;
        }
        for (String entry : plugin.getConfig().getStringList("player-shop.blacklist")) {
            if (entry.equalsIgnoreCase(item.getType().name())) {
                msg.send(player, "bm-sell-blocked");
                return false;
            }
        }
        if (db.countBlackSells(uuid, revier) >= slots(uuid, revier)) {
            msg.send(player, "bm-sell-noslot");
            return false;
        }
        ItemStack copy = item.clone();
        long price = plugin.blackMarket().sellPrice(copy);
        int percent = Math.max(0, plugin.getConfig().getInt("revier.list-fee-percent", 10));
        long fee = plugin.blackMarket().feeFor(uuid, price, percent);
        db.begin();
        try {
            if (fee > 0 && !db.takeCoins(uuid, fee)) {
                db.rollback();
                msg.send(player, "bm-sell-fee-missing", "fee", fee);
                return false;
            }
            if (fee > 0) {
                db.addShopAccount(fee);
                payPateAndBoss(revier, fee);
                db.addRevierSale(revier, uuid, fee); // zählt zum Umsatz im Revier
            }
            db.insertBlackSell(uuid, copy, price, revier);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Einstellen im Revier: " + e.getMessage());
            msg.send(player, "error");
            return false;
        }
        if (fee > 0) {
            db.logTx(uuid, player.getName(), "revier-einstellgebuehr", -fee, name(revier) + ": " + copy.getType().name());
        }
        msg.send(player, "bm-sell-listed", "amount", copy.getAmount(), "item", ItemUtil.name(copy), "price", price, "fee", fee);
        return true;
    }

    public void takeBack(Player player, long id, int revier) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        for (BlackSell sell : db.getBlackSells(player.getUniqueId(), revier)) {
            if (sell.id() == id) {
                if (!ItemUtil.canFit(player.getInventory(), sell.item())) {
                    msg.send(player, "inventory-full");
                    return;
                }
                if (db.deleteBlackSell(id)) {
                    player.getInventory().addItem(sell.item().clone());
                    msg.send(player, "bm-sell-back", "amount", sell.item().getAmount(),
                            "item", ItemUtil.name(sell.item()));
                    return;
                }
            }
        }
        msg.send(player, "bm-sell-gone");
    }

    // ------------------------------------------------------------------
    //  Boss und Pate
    // ------------------------------------------------------------------

    private Map<Integer, RevierBoss> bosses() {
        Map<Integer, RevierBoss> cache = bossCache;
        if (cache == null) {
            cache = new HashMap<>();
            for (RevierBoss b : plugin.database().getRevierBosses()) {
                cache.put(b.revier(), b);
            }
            bossCache = cache;
        }
        return cache;
    }

    /** Boss des Reviers (1 bis 7) oder null. */
    public UUID boss(int revier) {
        RevierBoss b = bosses().get(revier);
        return b == null ? null : b.uuid();
    }

    public UUID pate() {
        RevierBoss b = bosses().get(0);
        return b == null ? null : b.uuid();
    }

    /** Ist der Spieler Boss in mindestens einem Revier? (Dann gibt es Rabatt im Server-Shop.) */
    public boolean isBoss(UUID uuid) {
        for (Map.Entry<Integer, RevierBoss> e : bosses().entrySet()) {
            if (e.getKey() > 0 && e.getValue().uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    /** Einstellgebühr: Pate und Unterhändler bekommen ihre Anteile vom Shop-Konto. */
    private void payPateAndBoss(int revier, long fee) {
        payPate(fee);
        payBossShare(revier, fee);
    }

    public long bossBountyMin() {
        return Math.max(1, plugin.getConfig().getLong("revier.boss-bounty-min", 2000));
    }

    /** Ist der Spieler Boss in einem Revier (egal welches)? Siehe isBoss. */
    public boolean isBossOfAny(UUID uuid) {
        return isBoss(uuid);
    }

    /**
     * Der Unterhändler bekommt x % der Gebühren (Revier-Freikauf, Slot-Käufe und Einstellgebühren), die in seinem Revier anfallen.
     * Das Geld kommt vom Shop-Konto. Läuft innerhalb der laufenden Transaktion.
     */
    public void payBossShare(int revier, long fee) {
        UUID boss = boss(revier);
        if (boss == null || fee <= 0) {
            return;
        }
        long share = (long) Math.floor(fee * Math.max(0, plugin.getConfig().getDouble("revier.boss-fee-share-percent", 2)) / 100.0);
        if (share < 1) {
            return;
        }
        Database db = plugin.database();
        if (db.takeShopAccount(share)) {
            db.addCoins(boss, share);
            db.logTx(boss, db.getName(boss), "boss-gebuehren", share, "Anteil an Gebühren in " + name(revier));
            Player online = Bukkit.getPlayer(boss);
            if (online != null) {
                plugin.messages().send(online, "boss-fee-income", "coins", share);
            }
        }
    }

    public long pateIncomeTotal() {
        return plugin.database().getMetaLong("pate_income", 0);
    }

    /** 5 % der Schwarzmarkt-Gebühr gehen vom Shop-Konto an den Paten. Läuft innerhalb der laufenden Transaktion. */
    public void payPate(long fee) {
        UUID pate = pate();
        if (pate == null || fee <= 0) {
            return;
        }
        long share = (long) Math.floor(fee * Math.max(0, plugin.getConfig().getDouble("revier.pate-fee-share-percent", 5)) / 100.0);
        if (share < 1) {
            return;
        }
        Database db = plugin.database();
        if (db.takeShopAccount(share)) {
            db.addCoins(pate, share);
            db.setMetaLong("pate_income", pateIncomeTotal() + share);
            db.logTx(pate, db.getName(pate), "pate-gebuehren", share, "Anteil an Schwarzmarkt-Gebühren");
        }
    }

    /**
     * Der Pate bekommt zusätzlich 5 % von dem, was der Händler einem Spieler abkauft. Das Geld wird zusätzlich
     * ausgezahlt: der Verkäufer bekommt nicht weniger. Läuft innerhalb der laufenden Transaktion.
     */
    public void payPateSale(long coins, String sellerName) {
        UUID pate = pate();
        if (pate == null || coins <= 0) {
            return;
        }
        long share = (long) Math.floor(coins * Math.max(0, plugin.getConfig().getDouble("revier.pate-sale-share-percent", 0)) / 100.0);
        if (share < 1) {
            return;
        }
        Database db = plugin.database();
        db.addCoins(pate, share);
        db.setMetaLong("pate_income", pateIncomeTotal() + share);
        db.logTx(pate, db.getName(pate), "pate-ankauf", share, "Anteil an Händler-Ankauf von " + sellerName);
    }

    // ------------------------------------------------------------------
    //  Wöchentliche Auswertung (Sonntag, zur Shop-Wechsel-Zeit)
    // ------------------------------------------------------------------

    /** Wird nach dem Shop-Wechsel aufgerufen und wertet nur sonntags aus (einmal pro Sonntag). */
    public void weeklyCheck() {
        try {
            ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
            if (now.getDayOfWeek() != DayOfWeek.SUNDAY) {
                return;
            }
            long day = now.toLocalDate().toEpochDay();
            Database db = plugin.database();
            if (db.getMetaLong("rv_last_eval", -1) == day) {
                return;
            }
            db.setMetaLong("rv_last_eval", day);
            evaluate();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Revier-Auswertung fehlgeschlagen: " + e);
        }
    }

    /** Wie lange es bis zur nächsten Auswertung dauert (nächster Sonntag zur Shop-Wechsel-Zeit). */
    public Duration timeUntilEvaluation() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(plugin.serverShop().resetHour()).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusWeeks(1);
        }
        return Duration.between(now, next);
    }

    private void evaluate() {
        Database db = plugin.database();
        List<RevierBoss> result = new ArrayList<>();
        Map<UUID, Integer> wins = new HashMap<>();
        for (int r = 1; r <= COUNT; r++) {
            List<RevierEntry> top = db.topRevier(r, 1);
            if (!top.isEmpty()) {
                result.add(new RevierBoss(r, top.get(0).uuid(), top.get(0).coins()));
                wins.merge(top.get(0).uuid(), 1, Integer::sum);
            }
        }
        if (result.isEmpty()) {
            plugin.getLogger().info("Revier-Auswertung: diese Woche wurde nichts verkauft, alles bleibt wie es ist.");
            return;
        }
        Map<UUID, Long> total = new HashMap<>();
        for (RevierEntry e : db.topRevier(0, 1000)) {
            total.put(e.uuid(), e.coins());
        }
        UUID pate = null;
        for (UUID candidate : wins.keySet()) {
            if (pate == null) {
                pate = candidate;
                continue;
            }
            int a = wins.get(candidate);
            int b = wins.get(pate);
            if (a > b || (a == b && total.getOrDefault(candidate, 0L) > total.getOrDefault(pate, 0L))) {
                pate = candidate;
            }
        }
        UUID oldPate = pate();
        List<RevierBoss> all = new ArrayList<>(result);
        all.add(new RevierBoss(0, pate, total.getOrDefault(pate, 0L)));
        db.replaceRevierBosses(all);
        db.clearRevierSales();
        bossCache = null;
        plugin.protection().pruneInvalid(); // Schutzgeld-Verträge bleiben, wenn Pate/Boss im selben Revier gleich bleibt

        Messages msg = plugin.messages();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.blackMarket().unlocked(online)) {
                continue; // Revier-Meldungen lesen nur Eingeweihte
            }
            for (RevierBoss b : result) {
                msg.send(online, "revier-boss-new", "revier", name(b.revier()), "player", db.getName(b.uuid()),
                        "coins", b.coins());
            }
            msg.send(online, pate.equals(oldPate) ? "revier-pate-stays" : "revier-pate-new",
                    "player", db.getName(pate), "revier", wins.get(pate));
        }
        plugin.getLogger().info("Revier-Auswertung: neuer Pate ist " + db.getName(pate));
        plugin.titles().updateRankTitles();
        for (Player online : Bukkit.getOnlinePlayers()) {
            personalNotice(online);
        }
    }


    // ------------------------------------------------------------------
    //  Hinweise im Chat
    // ------------------------------------------------------------------

    private long lastReminderFor = -1;

    /** Sagt Unterhändleren und dem Paten (einmal pro Auswertung), dass sie es geworden sind. */
    private void personalNotice(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        long eval = db.getMetaLong("rv_last_eval", -1);
        if (eval < 0 || db.getMetaLong("rv_noticed_" + uuid, -2) == eval) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (int r = 1; r <= COUNT; r++) {
            if (uuid.equals(boss(r))) {
                names.add(name(r));
            }
        }
        boolean isPate = uuid.equals(pate());
        if (names.isEmpty() && !isPate) {
            return;
        }
        db.setMetaLong("rv_noticed_" + uuid, eval);
        Messages msg = plugin.messages();
        if (isPate) {
            msg.send(player, "revier-you-pate", "percent", plugin.getConfig().getDouble("revier.pate-fee-share-percent", 5));
            player.showTitle(net.kyori.adventure.title.Title.title(
                    net.kyori.adventure.text.Component.text("Du bist der Pate!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED),
                    net.kyori.adventure.text.Component.text("Biete Schutzgeld an: /schutzgeld", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (!names.isEmpty()) {
            msg.send(player, "revier-you-boss", "reviere", String.join(", ", names),
                    "discount", plugin.getConfig().getInt("revier.boss-discount-percent", 10));
            if (!isPate) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                try {
                    personalNotice(player);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Revier-Hinweis fehlgeschlagen: " + e);
                }
            }
        }, 100L);
    }

    /** Alle 30 Sekunden prüfen: kurz vor der Sonntags-Auswertung gibt es eine Erinnerung an alle Schmuggler. */
    public void reminderTick() {
        long minutes = Math.max(1, plugin.getConfig().getLong("revier.reminder-minutes", 30));
        long left = timeUntilEvaluation().toMillis();
        if (left > minutes * 60_000L || left < (minutes * 60_000L) - 120_000L) {
            return;
        }
        long target = System.currentTimeMillis() + left;
        long slot = Math.round(target / 600_000.0); // dieselbe Auswertung nur einmal ankündigen
        if (lastReminderFor == slot) {
            return;
        }
        lastReminderFor = slot;
        Database db = plugin.database();
        List<RevierEntry> top = db.topRevier(0, 1);
        String leader = top.isEmpty() ? "-" : db.getName(top.get(0).uuid());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.blackMarket().unlocked(online)) {
                plugin.messages().send(online, "revier-reminder", "minutes", minutes,
                        "sales", db.revierSalesOf(online.getUniqueId(), 0), "leader", leader);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Befehl /revier
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        if (!plugin.blackMarket().unlocked(player)) {
            plugin.messages().send(player, "bm-locked");
            return true;
        }
        try {
            new ReviereMenu(plugin, player).open(player);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /revier: " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }
}
