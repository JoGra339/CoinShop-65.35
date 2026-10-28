package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Mystery Boxen: reine Menü-Einträge (Stufe 1 bis 5), gelagert im Profil unter "Mystery Boxen". Es gibt kein Item.
 * Der Inhalt wird beim Öffnen wie beim Loot-Mob aus allem Shopbezogenen gewürfelt (Coins, Aktien, Krypto, Lizenzen,
 * Miner, Begleiter, Firmen-KI-Plätze, Schwarzmarkt-Dinge). Normale Mobs können seltene Coins und Boxen fallen lassen.
 * Für die Wochenauktion gibt es ein reines Anzeige-Item, das beim Abholen in das Box-Lager wandert.
 */
public final class MysteryBox implements Listener {

    private static final NamedTextColor[] COLORS = {NamedTextColor.WHITE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD};

    private final CoinShopPlugin plugin;
    private final NamespacedKey key;
    private final Random random = new Random();
    private LootRewards loot;

    MysteryBox(CoinShopPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "mysterybox");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mystery-box.enabled", true);
    }

    private LootRewards loot() {
        if (loot == null) {
            loot = new LootRewards(plugin);
        }
        return loot;
    }

    public static NamedTextColor colorOf(int tier) {
        return COLORS[Math.max(1, Math.min(5, tier)) - 1];
    }

    // ------------------------------------------------------------------
    //  Lager
    // ------------------------------------------------------------------

    private static String storeKey(UUID uuid, int tier) {
        return "mbox_" + uuid + "_" + tier;
    }

    public int count(UUID uuid, int tier) {
        return (int) plugin.database().getMetaLong(storeKey(uuid, tier), 0L);
    }

    public int total(UUID uuid) {
        int sum = 0;
        for (int t = 1; t <= 5; t++) {
            sum += count(uuid, t);
        }
        return sum;
    }

    private void add(UUID uuid, int tier, int amount) {
        plugin.database().setMetaLong(storeKey(uuid, tier), Math.max(0, count(uuid, tier) + amount));
    }

    /** Das reine Anzeige-Item einer Box (für die Wochenauktion). Es ist kein Spielgegenstand. */
    public ItemStack create(int tier, int amount) {
        int t = Math.max(1, Math.min(5, tier));
        ItemStack item = new ItemStack(Material.ENDER_CHEST, Math.max(1, amount));
        item.editMeta(m -> {
            m.displayName(Component.text("Mystery Box (Stufe " + t + " von 5)", colorOf(t))
                    .decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD));
            m.lore(List.of(
                    Component.text("Kein Gegenstand: der Gewinn landet im Profil", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("unter \"Mystery Boxen\" und wird dort geöffnet.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Inhalt: Coins, Aktien, Krypto, Lizenzen, Miner, Begleiter ...", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            m.setEnchantmentGlintOverride(true);
            m.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, t);
        });
        return item;
    }

    /** Stufe einer Auktions-Box, 0 wenn das Item keine ist. */
    public int tierOf(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_CHEST || !item.hasItemMeta()) {
            return 0;
        }
        Integer t = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return t == null ? 0 : Math.max(1, Math.min(5, t));
    }

    /** Legt Boxen ins Lager des Spielers (auch offline). */
    public void give(UUID uuid, String name, int tier, int amount, String source) {
        if (!enabled()) {
            return;
        }
        int t = Math.max(1, Math.min(5, tier));
        add(uuid, t, amount);
        plugin.database().logTx(uuid, name, "mystery-box", 0, amount + "x Mystery Box Stufe " + t + " (" + source + ")");
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            plugin.messages().send(online, "box-got", "tier", t, "source", source);
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        }
    }

    /** Legt eine Box ins Lager des Spielers. */
    public void give(Player player, int tier, String source) {
        give(player.getUniqueId(), player.getName(), tier, 1, source);
    }

    /** Ab welchem Level die Belohnung dieser Stufe gewürfelt wird (bestimmt Wert und Auswahl). */
    private int levelFor(int tier) {
        List<Integer> levels = plugin.getConfig().getIntegerList("mystery-box.levels");
        if (levels.isEmpty()) {
            levels = List.of(30, 150, 600, 2500, 6000);
        }
        return Math.max(1, levels.get(Math.min(levels.size(), tier) - 1));
    }

    // ------------------------------------------------------------------
    //  Öffnen
    // ------------------------------------------------------------------

    /** Öffnet bis zu "amount" Boxen einer Stufe. Gibt zurück, wie viele geöffnet wurden. */
    public int open(Player player, int tier, int amount) {
        UUID uuid = player.getUniqueId();
        if (!enabled() || plugin.expeditions().isIn(uuid) || plugin.timeline().previewFor(player)) {
            plugin.messages().send(player, "box-not-now");
            return 0;
        }
        int opened = 0;
        for (int i = 0; i < amount && count(uuid, tier) > 0; i++) {
            add(uuid, tier, -1);
            LootRewards.Result r = loot().roll(player, levelFor(tier), tier, true);
            if (r == null) {
                add(uuid, tier, 1); // schiefgegangen: die Box bleibt erhalten
                plugin.messages().send(player, "error");
                break;
            }
            opened++;
            plugin.database().logTx(uuid, player.getName(), "mystery-box", 0, "Box Stufe " + tier + ": " + r.text());
            plugin.messages().send(player, "box-opened", "reward", r.text(), "tier", tier);
            if (tier >= 4) {
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        plugin.messages().send(online, "box-broadcast", "player", player.getName(), "tier", tier, "reward", r.text());
                    }
                }
            }
        }
        if (opened > 0) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        return opened;
    }

    /** Wandelt einen Auktions-Gewinn in Lager-Boxen um (true, wenn das Item eine Box war). */
    public boolean redeem(Player player, ItemStack item) {
        int tier = tierOf(item);
        if (tier == 0) {
            return false;
        }
        give(player.getUniqueId(), player.getName(), tier, item.getAmount(), "Wochenauktion");
        return true;
    }

    // ------------------------------------------------------------------
    //  Verkaufen: an den Shop (feste Preise) oder als Gegenstand an andere Spieler
    // ------------------------------------------------------------------

    /** Was der Shop für eine Box dieser Stufe zahlt. */
    public long sellPrice(int tier) {
        List<Long> prices = plugin.getConfig().getLongList("mystery-box.sell-prices");
        if (prices.isEmpty()) {
            prices = List.of(100L, 400L, 1600L, 6000L, 20000L);
        }
        return Math.max(0, prices.get(Math.max(0, Math.min(prices.size(), tier) - 1)));
    }

    /** Verkauft bis zu "amount" Boxen an den Shop. Gibt die verkaufte Anzahl zurück. */
    public int sellToShop(Player player, int tier, int amount) {
        UUID uuid = player.getUniqueId();
        if (!enabled() || plugin.expeditions().isIn(uuid) || plugin.timeline().previewFor(player)) {
            plugin.messages().send(player, "box-not-now");
            return 0;
        }
        int n = Math.min(amount, count(uuid, tier));
        long price = sellPrice(tier);
        if (n <= 0 || price <= 0) {
            return 0;
        }
        Database db = plugin.database();
        db.begin();
        try {
            add(uuid, tier, -n);
            db.addCoins(uuid, price * n);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Box-Verkauf fehlgeschlagen: " + e.getMessage());
            plugin.messages().send(player, "error");
            return 0;
        }
        db.logTx(uuid, player.getName(), "mystery-box", price * n, n + "x Mystery Box Stufe " + tier + " an den Shop verkauft");
        plugin.messages().send(player, "box-sold", "count", n, "tier", tier, "coins", price * n);
        return n;
    }

    /** Macht aus Lager-Boxen echte Gegenstände (zum Verkaufen im Spieler-Shop oder Tauschen). */
    public int toItems(Player player, int tier, int amount) {
        UUID uuid = player.getUniqueId();
        if (!enabled() || plugin.expeditions().isIn(uuid) || plugin.timeline().previewFor(player)) {
            plugin.messages().send(player, "box-not-now");
            return 0;
        }
        int n = Math.min(amount, count(uuid, tier));
        if (n <= 0) {
            return 0;
        }
        add(uuid, tier, -n);
        ItemStack item = create(tier, n);
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        plugin.database().logTx(uuid, player.getName(), "mystery-box", 0, n + "x Mystery Box Stufe " + tier + " als Gegenstand ausgegeben");
        plugin.messages().send(player, "box-item", "count", n, "tier", tier);
        return n;
    }

    /** Rechtsklick auf eine Box als Gegenstand legt sie (wieder) ins Box-Lager. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUseItem(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        int tier = tierOf(hand);
        if (tier == 0) {
            return;
        }
        event.setCancelled(true);
        if (plugin.expeditions().isIn(player.getUniqueId())) {
            plugin.messages().send(player, "box-not-now");
            return;
        }
        int n = player.isSneaking() ? hand.getAmount() : 1;
        hand.setAmount(hand.getAmount() - n);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        give(player.getUniqueId(), player.getName(), tier, n, "Gegenstand eingelöst");
    }

    // ------------------------------------------------------------------
    //  Drops normaler Mobs
    // ------------------------------------------------------------------

    private boolean rollOdds(Player player, String path, long fallback) {
        long odds = Math.max(1, plugin.getConfig().getLong("mystery-box.mob-drop." + path, fallback));
        return random.nextDouble() * odds < plugin.mascot().luck(player.getUniqueId()); // Maskottchen-Glück
    }

    private long range(String path, long min, long max) {
        long lo = Math.max(1, plugin.getConfig().getLong("mystery-box.mob-drop." + path + "-min", min));
        long hi = Math.max(lo, plugin.getConfig().getLong("mystery-box.mob-drop." + path + "-max", max));
        return lo + random.nextLong(hi - lo + 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!enabled() || !plugin.getConfig().getBoolean("mystery-box.mob-drop.enabled", true)
                || dead instanceof Player || dead instanceof ArmorStand || dead.getType() == EntityType.ENDER_DRAGON) {
            return;
        }
        Player killer = dead.getKiller();
        if (killer == null || plugin.expeditions().isIn(killer.getUniqueId()) || plugin.timeline().previewFor(killer)) {
            return;
        }
        if (dead.fromMobSpawner() && plugin.getConfig().getBoolean("mystery-box.mob-drop.no-spawner", true)) {
            return; // gegen Mob-Farmen am Spawner
        }
        UUID uuid = killer.getUniqueId();
        boolean epic = rollOdds(killer, "epic-odds", 8000);
        boolean rare = !epic && rollOdds(killer, "rare-odds", 100);
        if (!epic && !rare) {
            return;
        }
        long coins = epic ? range("epic-coins", 1500, 4000) : range("rare-coins", 60, 200);
        Database db = plugin.database();
        db.addCoins(uuid, coins);
        db.logTx(uuid, killer.getName(), "mystery-box", coins, (epic ? "Sehr seltener" : "Seltener") + " Mob-Drop");
        plugin.messages().send(killer, epic ? "mob-coins-epic" : "mob-coins-rare", "coins", coins);
        int boxTier = epic ? 2 : 1;
        boolean boxHit = epic || random.nextInt(100) < Math.max(0, Math.min(100, plugin.getConfig().getInt("mystery-box.mob-drop.rare-box-percent", 2)));
        if (boxHit) {
            long today = LocalDate.now().toEpochDay();
            String dayKey = "mbox_dropday_" + uuid;
            String cntKey = "mbox_dropcnt_" + uuid;
            if (db.getMetaLong(dayKey, 0L) != today) {
                db.setMetaLong(dayKey, today);
                db.setMetaLong(cntKey, 0L);
            }
            long cap = Math.max(0, plugin.getConfig().getLong("mystery-box.mob-drop.max-boxes-per-day", 3));
            if (db.getMetaLong(cntKey, 0L) < cap) {
                db.setMetaLong(cntKey, db.getMetaLong(cntKey, 0L) + 1);
                give(killer, boxTier, epic ? "sehr seltener Mob-Drop" : "seltener Mob-Drop"); // nur eine Chat-Meldung, kein Item
            }
        }
    }

    // ------------------------------------------------------------------
    //  Erster Sieg über einen Minecraft-Boss (einmalig)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        EntityType type = dead.getType();
        int tier;
        switch (type) {
            case ENDER_DRAGON -> tier = 4;
            case WITHER, WARDEN -> tier = 3;
            case ELDER_GUARDIAN -> tier = 2;
            default -> { return; }
        }
        if (!enabled() || !plugin.getConfig().getBoolean("mystery-box.first-boss-kill", true)) {
            return;
        }
        Player p = dead.getKiller(); // nur wer den Boss getötet hat
        if (p == null || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || plugin.expeditions().isIn(p.getUniqueId())
                || plugin.timeline().previewFor(p)) {
            return;
        }
        String flag = "mbox_first_" + p.getUniqueId() + "_" + type.name();
        if (plugin.database().getMetaLong(flag, 0L) != 0L) {
            return;
        }
        plugin.database().setMetaLong(flag, 1L);
        give(p, tier, "erster Sieg über " + bossName(type));
    }

    private static String bossName(EntityType type) {
        return switch (type) {
            case ENDER_DRAGON -> "den Enderdrachen";
            case WITHER -> "den Wither";
            case WARDEN -> "den Warden";
            default -> "den Wächter-Boss";
        };
    }
}
