package de.coinshop;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Verknüpfung mit dem normalen Minecraft: seltene Coins (und Boxen) beim Angeln und beim Abbauen wertvoller Erze,
 * dazu die Zähler für Tränke brauen und Ernte einbringen (für die Tagesaufgaben).
 */
public final class WorldActivity implements Listener {

    private static final Set<Material> HARVEST = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH);
    private static final Set<Material> ORES = Set.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.ANCIENT_DEBRIS);

    private final CoinShopPlugin plugin;
    /** Von Spielern platzierte Erze (damit Platzieren und Abbauen nicht belohnt wird). */
    private final Set<String> placedOres = new LinkedHashSet<>();
    /** Wer hat dieses Brauständer zuletzt geöffnet. */
    private final Map<String, UUID> brewers = new HashMap<>();

    WorldActivity(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Zähler eines Spielers ("brew" oder "harvest"). */
    public static long count(CoinShopPlugin plugin, UUID uuid, String what) {
        return plugin.database().getMetaLong("act_" + what + "_" + uuid, 0L);
    }

    private void bump(UUID uuid, String what, long amount) {
        plugin.database().setMetaLong("act_" + what + "_" + uuid, count(plugin, uuid, what) + amount);
    }

    private boolean counts(Player player) {
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && !plugin.expeditions().isIn(player.getUniqueId()) && !plugin.timeline().previewFor(player);
    }

    private static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    /** 1 zu "odds" (mit dem Glück des Maskottchens des Spielers). */
    private boolean roll(Player player, String path, int odds) {
        double luck = plugin.mascot().luck(player.getUniqueId());
        return ThreadLocalRandom.current().nextDouble() * Math.max(1, plugin.getConfig().getInt("activity." + path, odds)) < luck;
    }

    private long range(String path, long min, long max) {
        List<Long> r = plugin.getConfig().getLongList("activity." + path);
        long lo = Math.max(1, r.size() > 0 ? r.get(0) : min);
        long hi = Math.max(lo, r.size() > 1 ? r.get(1) : max);
        return lo + ThreadLocalRandom.current().nextLong(hi - lo + 1);
    }

    private void payCoins(Player player, long coins, String detail) {
        plugin.database().addCoins(player.getUniqueId(), coins);
        plugin.database().logTx(player.getUniqueId(), player.getName(), "welt-fund", coins, detail);
        plugin.messages().send(player, "world-find", "coins", coins, "what", detail);
    }

    // ------------------------------------------------------------------
    //  Angeln
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !plugin.getConfig().getBoolean("activity.enabled", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!counts(player)) {
            return;
        }
        if (roll(player, "fishing.box-odds", 3000) && plugin.mysteryBoxes().enabled()) {
            payCoins(player, range("fishing.coins-epic", 300, 800), "Schatz im Netz");
            plugin.mysteryBoxes().give(player, 1, "Angeln: Schatzfund");
        } else if (roll(player, "fishing.odds", 50)) {
            payCoins(player, range("fishing.coins", 20, 80), "Glücksfang");
        }
    }

    // ------------------------------------------------------------------
    //  Erze und Ernte
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (ORES.contains(event.getBlockPlaced().getType())) {
            placedOres.add(key(event.getBlockPlaced().getLocation()));
            if (placedOres.size() > 5000) {
                placedOres.remove(placedOres.iterator().next());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        boolean ore = ORES.contains(type);
        boolean crop = HARVEST.contains(type) || type == Material.MELON || type == Material.PUMPKIN;
        if (!ore && !crop) {
            return;
        }
        if (ore && placedOres.remove(key(block.getLocation()))) {
            return; // selbst platziert
        }
        if (!counts(player)) {
            return;
        }
        if (crop) {
            boolean ripe = !(block.getBlockData() instanceof Ageable age) || age.getAge() >= age.getMaximumAge();
            if (ripe) {
                bump(player.getUniqueId(), "harvest", 1);
            }
            return;
        }
        if (!plugin.getConfig().getBoolean("activity.enabled", true) || !roll(player, "mining.odds", 15)) {
            return;
        }
        String path = type == Material.ANCIENT_DEBRIS ? "mining.coins-debris"
                : (type == Material.EMERALD_ORE || type == Material.DEEPSLATE_EMERALD_ORE) ? "mining.coins-emerald" : "mining.coins-diamond";
        payCoins(player, range(path, 40, 120), "Erzfund");
    }

    // ------------------------------------------------------------------
    //  Tränke brauen
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof BrewerInventory inv && inv.getLocation() != null
                && event.getPlayer() instanceof Player player) {
            brewers.put(key(inv.getLocation()), player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        UUID who = brewers.get(key(event.getBlock().getLocation()));
        if (who == null) {
            return;
        }
        int potions = 0;
        for (int slot = 0; slot < 3; slot++) {
            ItemStack item = event.getContents().getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                potions++;
            }
        }
        if (potions > 0) {
            bump(who, "brew", potions);
        }
    }
}
