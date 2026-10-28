package de.coinshop;

import de.coinshop.gui.AuctionMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Die Hehlerwaren-Auktion: Jede Woche wird ein seltenes Item versteigert. Wer am Ende (Sonntag zur Shop-Wechsel-Zeit,
 * zusammen mit der Revier-Auswertung) am meisten geboten hat, bekommt das Item. Das Gebot wird beim Bieten sofort
 * abgebucht; wer überboten wird, bekommt sein Geld zurück. Das Geld des Gewinners geht aufs Shop-Konto.
 * Nur mit der Lizenz Schmuggler.
 */
public final class AuctionManager implements CommandExecutor, Listener {

    public record Auction(long id, ItemStack item, long minBid, long bid, UUID bidder, String bidderName, long ends) { }

    public record Claim(long id, ItemStack item) { }

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public AuctionManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("auction.enabled", true);
    }

    // ------------------------------------------------------------------
    //  Die laufende Auktion
    // ------------------------------------------------------------------

    private Auction map(ResultSet rs) throws SQLException {
        String bidder = rs.getString("bidder");
        return new Auction(rs.getLong("id"), ItemStack.deserializeBytes(rs.getBytes("item")), rs.getLong("min_bid"),
                rs.getLong("bid"), bidder == null ? null : UUID.fromString(bidder), rs.getString("bidder_name"),
                rs.getLong("ends"));
    }

    /** Die laufende Auktion; legt bei Bedarf eine neue an. */
    public Auction current() {
        List<Auction> list = plugin.database().auctionsOpen(this::map);
        if (!list.isEmpty()) {
            return list.get(0);
        }
        createNew();
        list = plugin.database().auctionsOpen(this::map);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Sucht ein Item aus der Liste "auction.items" ("MATERIAL:Anzahl:Mindestgebot") und startet die Auktion. */
    private void createNew() {
        List<String> entries = plugin.getConfig().getStringList("auction.items");
        List<Object[]> valid = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.split(":");
            try {
                Material material = Material.valueOf(parts[0].trim().toUpperCase());
                int amount = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1].trim())) : 1;
                long min = parts.length > 2 ? Math.max(1, Long.parseLong(parts[2].trim())) : 500;
                valid.add(new Object[]{material, amount, min});
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Auktion: Eintrag '" + entry + "' ist ungültig.");
            }
        }
        if (valid.isEmpty()) {
            valid.add(new Object[]{Material.NETHERITE_INGOT, 2, 1500L});
        }
        // Manchmal wird statt eines Items eine hochstufige Mystery Box versteigert (Stufe 4 oder 5)
        if (plugin.mysteryBoxes().enabled()
                && random.nextInt(100) < Math.max(0, plugin.getConfig().getInt("auction.box-chance-percent", 25))) {
            int tier = random.nextInt(100) < 30 ? 5 : 4;
            List<Long> mins = plugin.getConfig().getLongList("auction.box-min-bids");
            long min = mins.size() >= tier - 3 ? mins.get(tier - 4) : (tier == 5 ? 10000L : 4000L);
            long boxEnds = System.currentTimeMillis() + plugin.reviere().timeUntilEvaluation().toMillis();
            plugin.database().auctionCreate(plugin.mysteryBoxes().create(tier, 1), min, boxEnds);
            clearBundle();
            return;
        }
        Object[] pick = valid.get(random.nextInt(valid.size()));
        ItemStack item = new ItemStack((Material) pick[0], (Integer) pick[1]);
        long ends = System.currentTimeMillis() + plugin.reviere().timeUntilEvaluation().toMillis();
        plugin.database().auctionCreate(item, (Long) pick[2], ends);
        rollBundle();
    }

    // ------------------------------------------------------------------
    //  Bündel: manchmal gehört zum Item eine Zugabe (Mystery Box oder Begleiter)
    // ------------------------------------------------------------------

    private void clearBundle() {
        plugin.database().setMetaLong("auction_bundle_box", 0L);
        plugin.database().setMetaLong("auction_bundle_beast", 0L);
        plugin.database().setMetaLong("auction_bundle_rarity", 0L);
    }

    private void rollBundle() {
        clearBundle();
        if (random.nextInt(100) >= Math.max(0, plugin.getConfig().getInt("auction.bundle-percent", 35))) {
            return;
        }
        ExpeditionManager.Companion[] beasts = ExpeditionManager.Companion.values();
        if (plugin.expeditions().enabled() && beasts.length > 0 && random.nextBoolean()) {
            plugin.database().setMetaLong("auction_bundle_beast", 1L + random.nextInt(beasts.length));
            plugin.database().setMetaLong("auction_bundle_rarity", random.nextInt(100) < 20 ? 2L : 1L);
        } else if (plugin.mysteryBoxes().enabled()) {
            plugin.database().setMetaLong("auction_bundle_box", random.nextInt(100) < 30 ? 3L : 2L);
        }
    }

    /** Beschreibung der Zugabe zur aktuellen Auktion (null, wenn es keine gibt). */
    public String bundleText() {
        long box = plugin.database().getMetaLong("auction_bundle_box", 0L);
        if (box > 0) {
            return "Mystery Box Stufe " + box;
        }
        long beast = plugin.database().getMetaLong("auction_bundle_beast", 0L);
        ExpeditionManager.Companion[] all = ExpeditionManager.Companion.values();
        if (beast > 0 && beast <= all.length) {
            return "Begleiter " + all[(int) beast - 1].label + " (" + CompanionTraits.Rarity.of((int) plugin.database().getMetaLong("auction_bundle_rarity", 1L)).label + ")";
        }
        return null;
    }

    /** Gibt dem Gewinner die Zugabe (falls es eine gab) und löscht sie. */
    private void payBundle(UUID winner) {
        long box = plugin.database().getMetaLong("auction_bundle_box", 0L);
        long beast = plugin.database().getMetaLong("auction_bundle_beast", 0L);
        ExpeditionManager.Companion[] all = ExpeditionManager.Companion.values();
        if (box > 0) {
            plugin.mysteryBoxes().give(winner, plugin.database().getName(winner), (int) box, 1, "Wochenauktion: Zugabe");
        } else if (beast > 0 && beast <= all.length) {
            int rarity = (int) plugin.database().getMetaLong("auction_bundle_rarity", 1L);
            List<String> traits = new ArrayList<>();
            for (CompanionTraits.Trait t : CompanionTraits.rollTraits(random, CompanionTraits.Rarity.of(rarity))) {
                traits.add(t.name());
            }
            int idx = plugin.expeditions().life().createCopy(winner, new CompanionLife.Offspring(all[(int) beast - 1], rarity, 1, traits, 0));
            plugin.expeditions().roster().save(winner);
            Player online = Bukkit.getPlayer(winner);
            if (online != null) {
                online.sendMessage(net.kyori.adventure.text.Component.text("Zugabe der Wochenauktion: " + all[(int) beast - 1].label
                        + " (Nr. " + (idx + 1) + ")", net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
            }
        }
        clearBundle();
    }

    public long minNextBid(Auction a) {
        if (a.bidder() == null) {
            return a.minBid();
        }
        long step = Math.max(1, (long) Math.ceil(a.bid() * Math.max(1, plugin.getConfig().getInt("auction.min-raise-percent", 10)) / 100.0));
        return a.bid() + step;
    }

    // ------------------------------------------------------------------
    //  Bieten
    // ------------------------------------------------------------------

    public void bid(Player player, long amount) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        if (!plugin.blackMarket().allowed(player)) {
            return;
        }
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "bm-preview");
            return;
        }
        Auction a = current();
        if (a == null || a.ends() <= System.currentTimeMillis()) {
            msg.send(player, "auction-closed");
            return;
        }
        UUID uuid = player.getUniqueId();
        long min = minNextBid(a);
        if (amount < min) {
            msg.send(player, "auction-too-low", "min", min);
            return;
        }
        UUID previous = a.bidder();
        long previousBid = a.bid();
        db.begin();
        try {
            if (previous != null) {
                db.addCoins(previous, previousBid);
            }
            if (!db.takeCoins(uuid, amount)) {
                db.rollback();
                msg.send(player, "not-enough");
                return;
            }
            db.auctionSetBid(a.id(), amount, uuid, player.getName());
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Auktion: Gebot fehlgeschlagen: " + e);
            msg.send(player, "error");
            return;
        }
        String itemName = a.item().getAmount() + "x " + ItemUtil.name(a.item());
        if (previous != null) {
            db.logTx(previous, db.getName(previous), "auktion-zurueck", previousBid, "überboten bei " + itemName);
            Player old = Bukkit.getPlayer(previous);
            if (old != null && !previous.equals(uuid)) {
                msg.send(old, "auction-outbid", "item", itemName, "player", player.getName(), "coins", amount);
            }
        }
        db.logTx(uuid, player.getName(), "auktion-gebot", -amount, "Gebot auf " + itemName);
        msg.send(player, "auction-bid-ok", "item", itemName, "coins", amount);
    }

    // ------------------------------------------------------------------
    //  Ende der Auktion (wöchentlich, beim Shop-Wechsel)
    // ------------------------------------------------------------------

    /** Wird nach jedem Shop-Wechsel aufgerufen: beendet die Auktion, wenn ihre Zeit um ist, und startet die nächste. */
    public void check() {
        if (!enabled()) {
            return;
        }
        try {
            Auction a = current();
            if (a == null || a.ends() > System.currentTimeMillis() + 60_000L) {
                return;
            }
            finish(a);
            createNew();
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Auktion: Abschluss fehlgeschlagen: " + e);
        }
    }

    private void finish(Auction a) {
        Database db = plugin.database();
        String itemName = a.item().getAmount() + "x " + ItemUtil.name(a.item());
        db.begin();
        try {
            db.auctionClose(a.id());
            if (a.bidder() != null) {
                db.addShopAccount(a.bid());
                db.claimAdd(a.bidder(), a.item());
                // kleine Schwarzmarkt-Gebühr auf das Höchstgebot, soweit der Gewinner sie noch hat
                long fee = plugin.blackMarket().feeFor(a.bidder(), a.bid(), plugin.blackMarket().includedFeePercent());
                if (fee > 0 && db.takeCoins(a.bidder(), fee)) {
                    db.addShopAccount(fee);
                    plugin.reviere().payPate(fee);
                    db.logTx(a.bidder(), db.getName(a.bidder()), "auktion-gebuehr", -fee, "Gebühr auf " + itemName);
                }
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            throw e;
        }
        if (a.bidder() != null) {
            payBundle(a.bidder());
        } else {
            clearBundle();
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!plugin.blackMarket().unlocked(online)) {
                continue;
            }
            if (a.bidder() == null) {
                plugin.messages().send(online, "auction-nobody", "item", itemName);
            } else {
                plugin.messages().send(online, "auction-won-all", "item", itemName, "player", a.bidderName(), "coins", a.bid());
            }
        }
        if (a.bidder() != null) {
            Player winner = Bukkit.getPlayer(a.bidder());
            if (winner != null) {
                plugin.messages().send(winner, "auction-won", "item", itemName);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Gewinn abholen
    // ------------------------------------------------------------------

    public int pendingClaims(UUID uuid) {
        return plugin.database().claims(uuid, this::mapClaim).size();
    }

    private Claim mapClaim(ResultSet rs) throws SQLException {
        return new Claim(rs.getLong("id"), ItemStack.deserializeBytes(rs.getBytes("item")));
    }

    public void claim(Player player) {
        Database db = plugin.database();
        List<Claim> claims = db.claims(player.getUniqueId(), this::mapClaim);
        if (claims.isEmpty()) {
            plugin.messages().send(player, "auction-nothing");
            return;
        }
        for (Claim c : claims) {
            if (!db.claimRemove(c.id())) {
                continue;
            }
            if (!plugin.mysteryBoxes().redeem(player, c.item())) { // Mystery Boxen wandern ins Profil-Lager
                for (ItemStack left : player.getInventory().addItem(c.item()).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
            plugin.messages().send(player, "auction-claimed", "item", c.item().getAmount() + "x " + ItemUtil.name(c.item()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && pendingClaims(player.getUniqueId()) > 0) {
                plugin.messages().send(player, "auction-pending");
            }
        }, 120L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        if (!enabled()) {
            plugin.messages().send(player, "auction-closed");
            return true;
        }
        if (!plugin.blackMarket().unlocked(player)) {
            plugin.messages().send(player, "bm-locked");
            return true;
        }
        try {
            new AuctionMenu(plugin, player).open(player);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /auktion: " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }
}
