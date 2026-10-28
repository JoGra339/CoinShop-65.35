package de.coinshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geheime Erfolge. Sie stehen nicht in den normalen Minecraft-Erfolgen, geben aber Coins
 * (je nach Schwierigkeit, einstellbar in der config.yml unter "secret-achievements").
 * Man sieht sie im Menü erst, wenn man sie errungen hat.
 *
 * Die Auslöser (z. B. "Schwein getötet") stehen in SecretListener.
 */
public final class SecretAchievements {

    /** Alle geheimen Erfolge. Die id ist der Schlüssel in Datenbank, config.yml und messages.yml. */
    public enum Secret {
        SMALL_FISH("kleiner-fisch", Material.TROPICAL_FISH),
        BIG_FISH("grosser-fisch", Material.PUFFERFISH),
        FLOWER_POT("blumentopf", Material.FLOWER_POT),
        PHANTOM("phantom", Material.PHANTOM_MEMBRANE),
        AWKWARD_POTION("bier", Material.BREWING_STAND),
        BAT("fledermaus", Material.BAT_SPAWN_EGG),
        BABY_ZOMBIE("baby-zombie", Material.ZOMBIE_HEAD),
        EGG("ei", Material.EGG),
        BLAZE_ROD("lohenrute", Material.BLAZE_ROD),
        BABY_VILLAGER("baby-villager", Material.VILLAGER_SPAWN_EGG),
        PIG("schwein", Material.PORKCHOP),
        GREEN_CARPET("teppich", Material.LIME_CARPET),
        FROGS("frosch", Material.SLIME_BALL),
        FALL_SURVIVE("kante", Material.SLIME_BLOCK),
        COUNTER_GAMBLE("gegengamble", Material.GOLD_NUGGET),
        EMMA("emma", Material.COD),
        LEAPING_TEA("vanilletee", Material.RABBIT_FOOT),
        COCOA("schokolade", Material.COCOA_BEANS),
        NOTE_BLOCK("mayonnaise", Material.NOTE_BLOCK);

        private final String id;
        private final Material icon;

        Secret(String id, Material icon) {
            this.id = id;
            this.icon = icon;
        }

        public String id() {
            return id;
        }

        public Material icon() {
            return icon;
        }
    }

    private final CoinShopPlugin plugin;
    /** Spieler, die den Fledermaus-Erfolg schon haben (spart Datenbank-Abfragen alle 3 Sekunden). */
    private final Set<UUID> batDone = ConcurrentHashMap.newKeySet();

    /** Wann der Spieler zuletzt im Casino verloren hat (für "Gegengamble"). */
    private final Map<UUID, Long> lastCasinoLoss = new ConcurrentHashMap<>();

    public SecretAchievements(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public long reward(Secret secret) {
        return Math.max(0, plugin.getConfig().getLong("secret-achievements." + secret.id() + ".coins", 10));
    }

    public String tier(Secret secret) {
        return plugin.getConfig().getString("secret-achievements." + secret.id() + ".tier", "leicht");
    }

    /**
     * Schaltet den Erfolg frei (nur beim ersten Mal) und zahlt die Belohnung.
     * Funktioniert auch für Offline-Spieler; sie bekommen die Meldung beim nächsten Beitreten.
     */
    public void unlock(UUID uuid, Secret secret) {
        if (plugin.timeline().previewFor(uuid)) {
            return; // Testphase: nichts wird gewertet
        }
        Database db = plugin.database();
        long coins = plugin.licenses().boost(uuid, "erfolge", reward(secret)); // Lizenz "erfolge"
        db.begin();
        try {
            if (!db.unlockSecret(uuid, secret.id())) {
                db.rollback();
                return; // hatte er schon
            }
            if (coins > 0) {
                db.addCoins(uuid, coins);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().warning("Geheimer Erfolg konnte nicht gespeichert werden: " + e);
            return;
        }
        db.logTx(uuid, db.getName(uuid), "geheimer-erfolg", coins, secret.id());
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            announce(online, secret);
            db.markSecretSeen(uuid, secret.id());
        }
    }

    public void unlock(Player player, Secret secret) {
        if (plugin.expeditions().isIn(player.getUniqueId())) {
            return; // in Expeditionslevels gibt es keine Erfolge
        }
        unlock(player.getUniqueId(), secret);
    }

    private void announce(Player player, Secret secret) {
        Messages msg = plugin.messages();
        msg.send(player, "secret-unlocked", "name", name(secret), "tier", tier(secret), "coins", plugin.licenses().boost(player.getUniqueId(), "erfolge", reward(secret)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    /** Meldet Erfolge, die freigeschaltet wurden, während der Spieler offline war. */
    public void notifyUnseen(Player player) {
        for (String id : plugin.database().popUnseenSecrets(player.getUniqueId())) {
            for (Secret secret : Secret.values()) {
                if (secret.id().equals(id)) {
                    announce(player, secret);
                }
            }
        }
    }

    public Component name(Secret secret) {
        return plugin.messages().get("secret." + secret.id() + ".name");
    }

    public Component description(Secret secret) {
        return plugin.messages().get("secret." + secret.id() + ".description");
    }

    // ------------------------------------------------------------------
    //  Spezielle Auslöser
    // ------------------------------------------------------------------

    /** Wird aufgerufen, wenn über den Spieler-Shop etwas verkauft wurde (Verkäufer bekam "earned" Coins). */
    public void onSale(UUID seller, long earned) {
        Database db = plugin.database();
        unlock(seller, Secret.SMALL_FISH);
        long total = db.addStat(seller, "shop_earned", earned);
        if (total >= 1000) {
            unlock(seller, Secret.BIG_FISH);
        }
    }

    /**
     * Mehrmals pro Sekunde: schaut ein Spieler gerade durch ein Fernglas auf eine Kreatur?
     * Dann zählt sie für die Kreaturen-Kollektionen, und eine Fledermaus schaltet den geheimen Erfolg frei.
     * (Nur wer das Fernglas wirklich benutzt, wird geprüft - das spart Rechenzeit.)
     */
    public void checkBatSightings() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isHandRaised() || player.getActiveItem().getType() != Material.SPYGLASS) {
                continue;
            }
            RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                    player.getEyeLocation().getDirection(), 64, 0.6,
                    entity -> entity instanceof LivingEntity && !(entity instanceof Player)
                            && !(entity instanceof ArmorStand));
            if (hit == null || hit.getHitEntity() == null || !player.hasLineOfSight(hit.getHitEntity())) {
                continue;
            }
            Entity seen = hit.getHitEntity();
            plugin.collections().noteEntity(player, seen);
            if (seen instanceof Bat && !batDone.contains(player.getUniqueId())) {
                unlock(player, Secret.BAT);
                batDone.add(player.getUniqueId());
            }
        }
    }

    /**
     * Casino: Ein Spiel beginnt. War das letzte Spiel eine Niederlage und liegt sie nur kurz zurück
     * (einstellbar: secret-achievements.gegengamble-seconds, Standard 60), gibt es "Gegengamble".
     */
    public void onCasinoStart(UUID uuid) {
        Long loss = lastCasinoLoss.get(uuid);
        long window = Math.max(5, plugin.getConfig().getLong("secret-achievements.gegengamble-seconds", 60)) * 1000L;
        if (loss != null && System.currentTimeMillis() - loss <= window) {
            unlock(uuid, Secret.COUNTER_GAMBLE);
        }
    }

    /** Casino: Ein Spiel ist zu Ende (lost = der Spieler hat verloren). */
    public void onCasinoResult(UUID uuid, boolean lost) {
        if (lost) {
            lastCasinoLoss.put(uuid, System.currentTimeMillis());
        } else {
            lastCasinoLoss.remove(uuid);
        }
    }

    /** Für die Menü-Anzeige. */
    public Map<String, Long> unlocked(UUID uuid) {
        return plugin.database().getSecrets(uuid);
    }
}
