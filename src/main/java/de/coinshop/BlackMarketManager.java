package de.coinshop;

import de.coinshop.Database.BlackOffer;
import de.coinshop.Database.Bounty;
import de.coinshop.Database.Offer;
import de.coinshop.gui.BlackMarketHubMenu;
import de.coinshop.gui.BlackMarketMenu;
import de.coinshop.gui.BountyMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Der Schwarzmarkt: jeden Tag genau EIN wertvolles Item zu einem günstigen Preis, aber nur mit
 * (standardmäßig) 80 % Chance, dass man es wirklich bekommt. Keine Steuern, nur Gebühren.
 * Dazu das Kopfgeld-Menü: Coins auf andere Spieler aussetzen, wer sie tötet, kassiert.
 */
public final class BlackMarketManager implements CommandExecutor, Listener {

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public BlackMarketManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Einstellungen und Freischaltung
    // ------------------------------------------------------------------

    public boolean enabled() {
        return plugin.getConfig().getBoolean("blackmarket.enabled", true);
    }

    /** Darf dieser Spieler den Schwarzmarkt benutzen? (Lizenz Schmuggler; Admins immer.) */
    public boolean unlocked(Player player) {
        return enabled() && ((plugin.getConfig().getBoolean("admin-bypass-licenses", false) && player.hasPermission("coinshop.admin"))
                || plugin.licenses().blackMarketUnlocked(player.getUniqueId()));
    }

    /** Hat der Spieler angegeben, dass er kein Kopfgeld auf sich möchte? (dann keine Vorzüge des Schwarzmarkts) */
    public boolean bountyOptOut(UUID uuid) {
        return plugin.database().getMetaLong("bounty_optout_" + uuid, 0) != 0;
    }

    /** Darf er die Vorzüge nutzen (kaufen, verkaufen, bieten ...)? Sonst geht eine Nachricht an ihn. */
    public boolean allowed(Player player) {
        if (!unlocked(player)) {
            plugin.messages().send(player, "bm-locked");
            return false;
        }
        if (bountyOptOut(player.getUniqueId())) {
            plugin.messages().send(player, "bm-optout");
            return false;
        }
        return true;
    }

    /** Auf wen darf ein Kopfgeld ausgesetzt werden? Schwarzmarkt freigeschaltet und nicht abgemeldet. */
    public boolean bountyTargetOk(UUID target) {
        return plugin.licenses().blackMarketUnlocked(target) && !bountyOptOut(target);
    }

    /** Schaltet "kein Kopfgeld auf mich" um. Nicht möglich, solange ein Kopfgeld auf dem Spieler liegt. */
    public void toggleBountyOptOut(Player player) {
        UUID uuid = player.getUniqueId();
        Messages msg = plugin.messages();
        if (bountyOptOut(uuid)) {
            plugin.database().setMetaLong("bounty_optout_" + uuid, 0);
            msg.send(player, "bounty-optout-off");
            return;
        }
        if (!plugin.database().getBountiesOn(uuid).isEmpty()) {
            msg.send(player, "bounty-optout-has");
            return;
        }
        plugin.database().setMetaLong("bounty_optout_" + uuid, 1);
        msg.send(player, "bounty-optout-on");
    }

    /** Kaufchance in Prozent: Grundchance + Schmuggler + Glückspilz/2. */
    public int chancePercent(UUID uuid) {
        double chance = plugin.getConfig().getInt("blackmarket.chance-percent", 60);
        chance += plugin.licenses().value(uuid, "schmuggler");
        chance += plugin.licenses().value(uuid, "glueck") / 2.0;
        chance += plugin.events().blackChanceBonus(); // Ereignisse "Schmugglernacht" / "Razzia"
        chance += plugin.titles().effect(uuid, "black-chance", ""); // Titel-Wirkung
        int max = plugin.getConfig().getInt("blackmarket.max-chance-percent", 95);
        return (int) Math.max(1, Math.min(max, Math.round(chance)));
    }

    /** Kleine Gebühr, die in allen Schwarzmarkt-Preisen ohne eigene Gebühr schon enthalten ist. */
    public int includedFeePercent() {
        return Math.max(0, plugin.getConfig().getInt("blackmarket.included-fee-percent", 3));
    }

    /** Preis mit der enthaltenen Gebühr. */
    public long withFee(long base) {
        return base + Math.round(base * includedFeePercent() / 100.0);
    }

    /** Der Gebührenanteil in einem Preis, der die Gebühr schon enthält. */
    public long feePart(long total) {
        return Math.max(0, total - Math.round(total * 100.0 / (100 + includedFeePercent())));
    }

    public int feePercent() {
        return Math.max(0, plugin.getConfig().getInt("blackmarket.fee-percent", 12));
    }

    /** Preis für diesen Spieler: Rabatt (Stammkunde) und Hintertür. */
    public long priceFor(UUID uuid, long base) {
        long price = plugin.licenses().discounted(uuid, base);
        double off = plugin.licenses().value(uuid, "hintertuer");
        if (off > 0) {
            price = Math.max(1, price - (long) Math.floor(price * off / 100.0));
        }
        return price;
    }

    /** Gebühr: keine mit Gebührenfrei, sonst weniger mit Bestechung. */
    public long feeFor(UUID uuid, long amount, int percent) {
        double keep = (1.0 - Math.min(100.0, plugin.licenses().value(uuid, "bestechung")) / 100.0)
                * (1.0 - Math.min(100.0, plugin.licenses().value(uuid, "gebuehrenfrei")) / 100.0);
        return (long) Math.ceil(amount * percent / 100.0 * keep * plugin.events().feeFactor());
    }

    public int bountyFeePercent() {
        return Math.max(0, plugin.getConfig().getInt("blackmarket.bounty.fee-percent", 10));
    }

    public long bountyMin() {
        return Math.max(1, plugin.getConfig().getLong("blackmarket.bounty.min-amount", 250));
    }

    // ------------------------------------------------------------------
    //  Die täglichen Angebote
    // ------------------------------------------------------------------

    /** Neuer Tag: neue Angebote, der Händler kauft Items von Spielern, alte Kopfgelder verfallen. */
    public void rotate() {
        Database db = plugin.database();
        try {
            buySellItems();
            raidConfiscate();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Schwarzmarkt-Ankauf fehlgeschlagen: " + e);
        }
        int count = Math.max(1, plugin.getConfig().getInt("blackmarket.offers", 10));
        int candidates = Math.max(count, plugin.getConfig().getInt("blackmarket.candidates", 60));
        java.util.Map<org.bukkit.Material, Offer> unique = new java.util.LinkedHashMap<>();
        for (int i = 0; i < candidates; i++) {
            Offer o = plugin.serverShop().generator().generateOne(0, List.of());
            if (o != null) {
                Offer old = unique.get(o.item().getType());
                if (old == null || o.price() > old.price()) {
                    unique.put(o.item().getType(), o);
                }
            }
        }
        List<Offer> sorted = new java.util.ArrayList<>(unique.values());
        sorted.sort((x, y) -> Long.compare(y.price(), x.price()));
        double percent = Math.max(5, plugin.getConfig().getDouble("blackmarket.price-percent", 45)) / 100.0;
        List<Offer> chosen = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size() && i < count; i++) {
            Offer o = sorted.get(i);
            chosen.add(new Offer(i, o.item(), Math.max(1, Math.round(o.price() * percent))));
        }
        if (!chosen.isEmpty()) {
            db.replaceBlackOffers(chosen, System.currentTimeMillis());
            plugin.getLogger().info("Schwarzmarkt: " + chosen.size() + " neue Angebote.");
        }
        expireBounties();
    }

    /** Die aktuellen Angebote (lost bei Bedarf neue aus). */
    public List<BlackOffer> offers() {
        List<BlackOffer> offers = plugin.database().getBlackOffers();
        if (offers.isEmpty()) {
            rotate();
            offers = plugin.database().getBlackOffers();
        }
        return offers;
    }

    // ------------------------------------------------------------------
    //  Kaufen
    // ------------------------------------------------------------------

    public void buy(Player player, int slot, long stamp) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "bm-preview");
            return;
        }
        if (!allowed(player)) {
            return;
        }
        BlackOffer offer = null;
        for (BlackOffer o : db.getBlackOffers()) {
            if (o.slot() == slot && o.stamp() == stamp) {
                offer = o;
            }
        }
        if (offer == null) {
            msg.send(player, "bm-outdated");
            return;
        }
        if (offer.sold()) {
            msg.send(player, "bm-sold");
            return;
        }
        ItemStack stack = offer.item().clone();
        if (!ItemUtil.canFit(player.getInventory(), stack)) {
            msg.send(player, "inventory-full");
            return;
        }
        long price = priceFor(uuid, offer.price());
        long fee = feeFor(uuid, price, feePercent());
        long total = price + fee;
        boolean success = random.nextInt(100) < chancePercent(uuid);
        db.begin();
        try {
            if (!db.takeCoins(uuid, total)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            if (success && !db.markBlackSold(slot, stamp)) {
                db.rollback();
                msg.send(player, "bm-sold");
                return;
            }
            db.addShopAccount(total);
            plugin.reviere().payPate(fee); // 5 % der Gebühr gehen an den Paten
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Schwarzmarkt-Kauf: " + e.getMessage());
            msg.send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "schwarzmarkt", -total,
                (success ? "erhalten: " : "Ware nie angekommen: ") + stack.getAmount() + "x " + stack.getType().name());
        if (success) {
            player.getInventory().addItem(stack);
            msg.send(player, "bm-success", "item", ItemUtil.name(stack), "price", price, "fee", fee);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1f, 0.8f);
        } else {
            msg.send(player, "bm-fail", "price", price, "fee", fee);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
        }
    }

    // ------------------------------------------------------------------
    //  An den Händler verkaufen
    // ------------------------------------------------------------------

    public int sellBuyChance() {
        if (plugin.events().dealerOff()) {
            return 0; // Ereignis "Händler im Urlaub"
        }
        int chance = plugin.getConfig().getInt("blackmarket.sell.buy-chance-percent", 20) + plugin.events().dealerChanceBonus();
        return Math.max(0, Math.min(100, chance));
    }

    /** Kaufchance für einen bestimmten Slot: Grundchance plus Schutzgeld-Verträge (nie bei "Händler im Urlaub"). */
    public int sellBuyChance(UUID owner, int revier) {
        if (plugin.events().dealerOff()) {
            return 0;
        }
        return Math.max(0, Math.min(100, sellBuyChance() + plugin.protection().bonusChance(owner, revier)));
    }

    public int sellPricePercent() {
        return Math.max(1, plugin.getConfig().getInt("blackmarket.sell.price-percent", 25));
    }

    /** Der feste (schlechte) Preis des Händlers für einen Stapel. */
    public long sellPrice(ItemStack stack) {
        long each = Math.max(1, plugin.priceBook().price(stack.getType()));
        return Math.max(1, Math.round(each * stack.getAmount() * sellPricePercent() / 100.0));
    }

    /**
     * Razzia-Spiel: Am Tag der Razzia beschlagnahmt die Polizei jedes noch eingestellte Item mit einer Chance
     * (Standard 15 %). Die Lizenz Bestechung senkt sie (bis zu 80 % weniger).
     */
    private void raidConfiscate() {
        if (!plugin.events().isActive(EventManager.Event.RAID)) {
            return;
        }
        Database db = plugin.database();
        double base = plugin.getConfig().getDouble("events.raid-confiscate-percent", 15);
        java.util.Map<UUID, Integer> lost = new java.util.HashMap<>();
        for (Database.BlackSell sell : db.getAllBlackSells()) {
            double keep = Math.min(80.0, plugin.licenses().value(sell.owner(), "bestechung")) / 100.0;
            if (random.nextDouble() * 100.0 >= base * (1.0 - keep)) {
                continue;
            }
            if (db.deleteBlackSell(sell.id())) {
                lost.merge(sell.owner(), 1, Integer::sum);
            }
        }
        for (java.util.Map.Entry<UUID, Integer> e : lost.entrySet()) {
            Player online = Bukkit.getPlayer(e.getKey());
            if (online != null) {
                plugin.messages().send(online, "razzia-lost", "amount", e.getValue());
            } else {
                db.setMetaLong("raid_lost_" + e.getKey(), db.getMetaLong("raid_lost_" + e.getKey(), 0) + e.getValue());
            }
        }
        // Spieler-Shop: ein zufälliges Angebot wird beschlagnahmt
        int count = db.countListings();
        if (count > 0) {
            java.util.List<Database.Listing> one = db.getListings(random.nextInt(count), 1);
            if (!one.isEmpty()) {
                Database.Listing l = one.get(0);
                if (db.deleteListing(l.id())) {
                    String item = l.item().getAmount() + "x " + l.item().getType().name();
                    Player seller = Bukkit.getPlayer(l.seller());
                    if (seller != null) {
                        plugin.messages().send(seller, "razzia-shop-lost", "item", item);
                    } else {
                        db.setMetaLong("raid_shop_lost_" + l.seller(), db.getMetaLong("raid_shop_lost_" + l.seller(), 0) + 1);
                    }
                }
            }
        }
        plugin.getLogger().info("Razzia: " + lost.values().stream().mapToInt(Integer::intValue).sum() + " Items beschlagnahmt.");
    }

    /** Wer während einer Razzia offline war, erfährt es beim nächsten Beitritt. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Database db = plugin.database();
        long n = db.getMetaLong("raid_lost_" + player.getUniqueId(), 0);
        long shopLost = db.getMetaLong("raid_shop_lost_" + player.getUniqueId(), 0);
        if (shopLost > 0) {
            db.setMetaLong("raid_shop_lost_" + player.getUniqueId(), 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.messages().send(player, "razzia-shop-lost", "item", shopLost + " Angebot(e)");
                }
            }, 80L);
        }
        if (n > 0) {
            db.setMetaLong("raid_lost_" + player.getUniqueId(), 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.messages().send(player, "razzia-lost", "amount", n);
                }
            }, 60L);
        }
    }

    /** Beim täglichen Wechsel: jedes eingestellte Item wird mit x % Chance vom Händler gekauft. */
    private void buySellItems() {
        Database db = plugin.database();
        for (Database.BlackSell sell : db.getAllBlackSells()) {
            if (bountyOptOut(sell.owner())) {
                continue; // ohne Kopfgeld-Zustimmung kauft der Händler nichts
            }
            if (random.nextInt(100) >= sellBuyChance(sell.owner(), sell.revier())) {
                continue;
            }
            long coins = Math.max(1, Math.round(sell.price() * plugin.events().dealerPayFactor())); // Schnäppchenjäger
            double bonus = plugin.licenses().value(sell.owner(), "hehler");
            if (bonus > 0) {
                coins += (long) Math.floor(coins * bonus / 100.0);
            }
            db.begin();
            try {
                if (db.deleteBlackSell(sell.id())) {
                    db.addCoins(sell.owner(), coins);
                    db.addRevierSale(sell.revier(), sell.owner(), coins); // zählt für Revier-Rangliste und Pate
                    plugin.reviere().payPateSale(coins, db.getName(sell.owner())); // Pate bekommt zusätzlich 5 %
                    plugin.protection().payShares(sell.owner(), sell.revier(), coins); // Schutzgeld-Verträge
                    db.addSaleNotice(sell.owner(), sell.item().getType(), sell.item().getAmount(), coins);
                    db.logTx(sell.owner(), db.getName(sell.owner()), "schwarzmarkt-verkauf", coins,
                            sell.item().getAmount() + "x " + sell.item().getType().name());
                }
                db.commit();
            } catch (RuntimeException e) {
                db.rollback();
                continue;
            }
            Player online = Bukkit.getPlayer(sell.owner());
            if (online != null) {
                plugin.messages().send(online, "bm-sell-sold", "amount", sell.item().getAmount(),
                        "item", ItemUtil.name(sell.item()), "coins", coins);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Kopfgeld
    // ------------------------------------------------------------------

    /** Setzt ein Kopfgeld aus. amount ist das, was der Kopf wert ist; die Gebühr kommt obendrauf. */
    public void placeBounty(Player placer, UUID target, String targetName, long amount) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (plugin.timeline().previewFor(placer)) {
            msg.send(placer, "bm-preview");
            return;
        }
        if (!allowed(placer)) {
            return;
        }
        if (target.equals(placer.getUniqueId())) {
            msg.send(placer, "bounty-self");
            return;
        }
        if (!bountyTargetOk(target)) {
            msg.send(placer, "bounty-target-closed", "player", targetName);
            return;
        }
        if (amount < bountyMin()) {
            msg.send(placer, "bounty-min", "min", bountyMin());
            return;
        }
        if (target.equals(plugin.reviere().pate()) && amount < plugin.reviere().pateBountyMin()) {
            msg.send(placer, "bounty-pate-min", "min", plugin.reviere().pateBountyMin());
            return;
        }
        if (plugin.reviere().isBoss(target) && amount < plugin.reviere().bossBountyMin()) {
            msg.send(placer, "bounty-boss-min", "min", plugin.reviere().bossBountyMin());
            return;
        }
        long fee = feeFor(placer.getUniqueId(), amount, bountyFeePercent());
        db.begin();
        try {
            if (!db.takeCoins(placer.getUniqueId(), amount + fee)) {
                db.rollback();
                msg.send(placer, "not-enough");
                return;
            }
            db.addShopAccount(fee);
            plugin.reviere().payPate(fee); // 5 % der Gebühr gehen an den Paten
            db.addBounty(target, targetName, placer.getUniqueId(), placer.getName(), amount);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler beim Kopfgeld: " + e.getMessage());
            msg.send(placer, "error");
            return;
        }
        db.logTx(placer.getUniqueId(), placer.getName(), "kopfgeld", -(amount + fee),
                "Kopfgeld auf " + targetName);
        msg.send(placer, "bounty-placed", "player", targetName, "amount", amount, "fee", fee);
        long total = db.getBountyTotalOn(target);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (bountyNotify(online.getUniqueId()) && unlocked(online)) {
                msg.send(online, "bounty-announce", "player", targetName, "total", total);
            }
        }
    }

    /** Zieht ein eigenes Kopfgeld zurück (die Gebühr ist weg). */
    public void withdrawBounty(Player player, long id) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        Bounty found = null;
        for (Bounty b : db.getBounties()) {
            if (b.id() == id) {
                found = b;
                break;
            }
        }
        if (found == null || !found.placer().equals(player.getUniqueId())) {
            msg.send(player, "bounty-gone");
            return;
        }
        long refund = found.amount() * Math.max(0, Math.min(100,
                plugin.getConfig().getInt("blackmarket.bounty.refund-percent", 100))) / 100;
        db.begin();
        try {
            if (!db.deleteBounty(id)) {
                db.rollback();
                msg.send(player, "bounty-gone");
                return;
            }
            db.addCoins(player.getUniqueId(), refund);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            msg.send(player, "error");
            return;
        }
        db.logTx(player.getUniqueId(), player.getName(), "kopfgeld-zurueck", refund,
                "Kopfgeld auf " + found.targetName() + " zurückgezogen");
        msg.send(player, "bounty-withdrawn", "player", found.targetName(), "amount", refund);
    }

    /** Kopfgelder gelten nur bis zum nächsten Shop-Wechsel: alle gehen komplett zurück an den Auftraggeber. */
    /** Will der Spieler Chat-Hinweise über neue Kopfgelder? (Standard: ja) */
    public boolean bountyNotify(UUID uuid) {
        return plugin.database().getMetaLong("bounty_notify_" + uuid, 1) != 0;
    }

    public boolean toggleBountyNotify(UUID uuid) {
        boolean now = !bountyNotify(uuid);
        plugin.database().setMetaLong("bounty_notify_" + uuid, now ? 1 : 0);
        return now;
    }

    private void expireBounties() {
        Database db = plugin.database();
        db.bumpBountyResets();
        int count = Math.max(1, plugin.getConfig().getInt("blackmarket.bounty.reset-count", 3));
        for (Bounty b : db.getBountiesWithResets(count)) {
            db.begin();
            try {
                if (db.deleteBounty(b.id())) {
                    db.addCoins(b.placer(), b.amount());
                    db.logTx(b.placer(), b.placerName(), "kopfgeld-abgelaufen", b.amount(),
                            "Kopfgeld auf " + b.targetName() + " abgelaufen");
                }
                db.commit();
            } catch (RuntimeException e) {
                db.rollback();
                continue;
            }
            Player online = Bukkit.getPlayer(b.placer());
            if (online != null) {
                plugin.messages().send(online, "bounty-expired", "player", b.targetName(), "amount", b.amount());
            }
        }
    }

    /** Wer einen Spieler mit Kopfgeld tötet, bekommt alle Kopfgelder auf ihn. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || !enabled()) {
            return;
        }
        Database db = plugin.database();
        List<Bounty> bounties = db.getBountiesOn(victim.getUniqueId());
        if (bounties.isEmpty()) {
            return;
        }
        if (!unlocked(killer) || bountyOptOut(killer.getUniqueId())) {
            plugin.messages().send(killer, "bounty-locked-killer", "player", victim.getName());
            return;
        }
        long sum = 0;
        db.begin();
        try {
            for (Bounty b : bounties) {
                if (db.deleteBounty(b.id())) {
                    sum += b.amount();
                }
            }
            if (sum > 0) {
                db.addCoins(killer.getUniqueId(), sum);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Kopfgeld-Auszahlung: " + e.getMessage());
            return;
        }
        if (sum > 0) {
            long bonus = 0;
            double percent = plugin.licenses().value(killer.getUniqueId(), "kopfgeldjaeger")
                    + plugin.titles().effect(killer.getUniqueId(), "bounty-bonus", "");
            if (percent > 0) {
                bonus = Math.min(plugin.database().getShopAccount(), (long) Math.floor(sum * percent / 100.0));
                if (bonus > 0) {
                    db.addShopAccount(-bonus);
                    db.addCoins(killer.getUniqueId(), bonus);
                    sum += bonus;
                }
            }
            db.logTx(killer.getUniqueId(), killer.getName(), "kopfgeld-kassiert", sum,
                    "Kopfgeld auf " + victim.getName());
            if (sum >= Math.max(1, plugin.getConfig().getLong("blackmarket.bounty-box-min", 3000))
                    && java.util.concurrent.ThreadLocalRandom.current().nextInt(100)
                    < Math.max(0, Math.min(100, plugin.getConfig().getInt("blackmarket.bounty-box-percent", 30)))) {
                plugin.mysteryBoxes().give(killer, 2, "Kopfgeld kassiert");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                // nur Eingeweihte (und die Beteiligten selbst) lesen vom Kopfgeld
                if (!unlocked(online) && !online.getUniqueId().equals(killer.getUniqueId())
                        && !online.getUniqueId().equals(victim.getUniqueId())) {
                    continue;
                }
                plugin.messages().send(online, "bounty-collected", "killer", killer.getName(),
                        "player", victim.getName(), "amount", sum);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Befehle
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        try {
            if (!enabled()) {
                plugin.messages().send(player, "bm-disabled");
            } else if (command.getName().equalsIgnoreCase("kopfgeld")) {
                if (unlocked(player)) {
                    new BountyMenu(plugin, player).open(player);
                } else {
                    plugin.messages().send(player, "bm-locked");
                }
            } else if (unlocked(player)) {
                new BlackMarketHubMenu(plugin, player).open(player);
            } else {
                plugin.messages().send(player, "bm-locked");
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler beim Schwarzmarkt: " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }
}
