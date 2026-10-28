package de.coinshop;

import de.coinshop.SecretAchievements.Secret;
import org.bukkit.Material;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Frog;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

/** Die Auslöser der geheimen Erfolge. (Verkäufe und Fledermaus-Sichtung stehen in SecretAchievements.) */
public final class SecretListener implements Listener {

    private final CoinShopPlugin plugin;

    public SecretListener(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Blumentopf oder grünen Teppich platziert. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.expeditions().isIn(event.getPlayer().getUniqueId())) {
            return;
        }
        Material type = event.getBlockPlaced().getType();
        if (type == Material.FLOWER_POT) {
            plugin.secrets().unlock(event.getPlayer(), Secret.FLOWER_POT);
        } else if (type == Material.LIME_CARPET || type == Material.GREEN_CARPET) {
            plugin.secrets().unlock(event.getPlayer(), Secret.GREEN_CARPET);
        } else if (type == Material.NOTE_BLOCK) {
            plugin.secrets().unlock(event.getPlayer(), Secret.NOTE_BLOCK);
        }
    }

    /** Zwei Frösche gepaart (Variante 1: Minecraft meldet die Paarung). */
    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player
                && (event.getMother() instanceof Frog || event.getFather() instanceof Frog)) {
            plugin.secrets().unlock(player, Secret.FROGS);
        }
    }

    /**
     * Zwei Frösche gepaart (Variante 2, falls Variante 1 bei Fröschen nicht gemeldet wird): Ein Frosch wurde
     * mit einem Schleimball gefüttert, und ein zweiter Frosch in der Nähe ist ebenfalls im Liebesmodus.
     */
    @EventHandler
    public void onFeedFrog(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Frog frog)
                || event.getPlayer().getInventory().getItemInMainHand().getType() != Material.SLIME_BALL) {
            return;
        }
        Player player = event.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!frog.isValid() || !frog.isLoveMode()) {
                return;
            }
            for (org.bukkit.entity.Entity near : frog.getNearbyEntities(10, 5, 10)) {
                if (near instanceof Frog other && other.isLoveMode()) {
                    plugin.secrets().unlock(player, Secret.FROGS);
                    return;
                }
            }
        }, 2L);
    }

    /** Phantom / Fledermaus / Babyzombie / Zombifizierten Piglin getötet. */
    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || plugin.expeditions().isIn(killer.getUniqueId())) {
            return; // Expeditions-Kämpfe zählen nicht
        }
        EntityType type = event.getEntityType();
        plugin.collections().noteEntity(killer, event.getEntity()); // für die Kreaturen-Kollektionen
        if (type == EntityType.PHANTOM) {
            plugin.secrets().unlock(killer, Secret.PHANTOM);
        } else if (type == EntityType.BAT) {
            plugin.secrets().unlock(killer, Secret.BAT);
        } else if (type == EntityType.ZOMBIFIED_PIGLIN) {
            plugin.secrets().unlock(killer, Secret.PIG);
        }
        if (event.getEntity() instanceof Cat) {
            plugin.secrets().unlock(killer, Secret.EMMA);
        }
        if (event.getEntity() instanceof Zombie zombie && zombie.isBaby()) {
            plugin.secrets().unlock(killer, Secret.BABY_ZOMBIE);
        }
    }

    /** Einen anderen Spieler mit einem Ei getroffen. */
    @EventHandler
    public void onEggHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Egg egg
                && event.getHitEntity() instanceof Player hit
                && egg.getShooter() instanceof Player shooter
                && !shooter.getUniqueId().equals(hit.getUniqueId())) {
            plugin.secrets().unlock(shooter, Secret.EGG);
        }
    }

    /** Eine Lohenrute aufgesammelt. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            Material picked = event.getItem().getItemStack().getType();
            if (picked == Material.BLAZE_ROD) {
                plugin.secrets().unlock(player, Secret.BLAZE_ROD);
            } else if (picked == Material.COCOA_BEANS) {
                plugin.secrets().unlock(player, Secret.COCOA); // Schokolaaade!
            }
        }
    }

    /** Versucht, mit einem Baby-Villager zu handeln (Rechtsklick). */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getRightClicked() instanceof Villager villager && !villager.isAdult()) {
            plugin.secrets().unlock(event.getPlayer(), Secret.BABY_VILLAGER);
        }
    }

    /** Kakaobohne aus einer Kiste, einem Händler-Menü o. Ä. genommen (Aufsammeln am Boden steht oben). */
    @EventHandler(ignoreCancelled = true)
    public void onCocoaTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() == player.getInventory()
                || event.getInventory().getType() == InventoryType.CRAFTING) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == Material.COCOA_BEANS
                && event.getInventory().getHolder() != null
                && !(event.getInventory().getHolder() instanceof de.coinshop.gui.Menu)) {
            plugin.secrets().unlock(player, Secret.COCOA);
        }
    }

    /** Einen "Komischen Trank" aus dem Braustand genommen. */
    @EventHandler(ignoreCancelled = true)
    public void onBrewTake(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.BREWING
                || event.getClickedInventory() != event.getInventory()
                || event.getRawSlot() > 2
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == Material.POTION
                && item.getItemMeta() instanceof PotionMeta meta
                && meta.getBasePotionType() == PotionType.AWKWARD) {
            plugin.secrets().unlock(player, Secret.AWKWARD_POTION);
        }
        // Trank der Sprungkraft (auch stark oder lang, auch als Wurf- oder Verweiltrank)
        if (item != null && item.getItemMeta() instanceof PotionMeta leap
                && leap.getBasePotionType() != null
                && leap.getBasePotionType().getKey().getKey().contains("leaping")) {
            plugin.secrets().unlock(player, Secret.LEAPING_TEA);
        }
    }

    /** Aus mindestens 20 Blöcken Höhe gefallen, Fallschaden bekommen und überlebt. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (plugin.expeditions().isIn(player.getUniqueId())) {
            return;
        }
        double damage = event.getFinalDamage();
        // Fallstrecke steht beim Schaden noch im Spieler; überlebt = der Schaden ist kleiner als die Lebenspunkte
        if (player.getFallDistance() >= 20 && damage > 0 && damage < player.getHealth()) {
            plugin.secrets().unlock(player, Secret.FALL_SURVIVE);
        }
    }
}
