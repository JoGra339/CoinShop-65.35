package de.coinshop;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anbindung an die Voice-Chat-Mod (Simple Voice Chat):
 *  - Wer im Proximity-Chat spricht, löst Vibrationen aus, auf die Sculk-Sensoren reagieren.
 *  - Monster hören Sprechende in einem erweiterten Radius und laufen zur Quelle.
 * Flüstern ist deutlich leiser, Schleichen etwas leiser. Alles ist unter "voicechat:" in der config.yml einstellbar.
 * Diese Klasse wird nur geladen, wenn die Mod auf dem Server ist.
 */
public final class VoiceChatBridge implements VoicechatPlugin {

    private final CoinShopPlugin plugin;
    private final Map<UUID, Long> lastSculk = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMobs = new ConcurrentHashMap<>();

    private VoiceChatBridge(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Verbindet CoinShop mit der Voice-Chat-Mod. Gibt false zurück, wenn sie nicht da ist. */
    public static boolean register(CoinShopPlugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            return false;
        }
        service.registerPlugin(new VoiceChatBridge(plugin));
        return true;
    }

    @Override
    public String getPluginId() {
        return "coinshop";
    }

    @Override
    public void initialize(VoicechatApi api) {
        plugin.getLogger().info("Voice-Chat-Anbindung aktiv (Sculk-Sensoren und Monster reagieren auf Sprache).");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
    }

    /** Läuft im Thread der Mod (nicht im Haupt-Thread): nur filtern, die Arbeit übergeben wir an den Server. */
    private void onMicrophone(MicrophonePacketEvent event) {
        if (!plugin.getConfig().getBoolean("voicechat.enabled", true)) {
            return;
        }
        VoicechatConnection connection = event.getSenderConnection();
        if (connection == null || !(connection.getPlayer().getPlayer() instanceof Player player)) {
            return;
        }
        boolean whispering = event.getPacket().isWhispering();
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();

        boolean doSculk = plugin.getConfig().getBoolean("voicechat.sculk.enabled", true)
                && now - lastSculk.getOrDefault(id, 0L) >= plugin.getConfig().getLong("voicechat.sculk.cooldown-ticks", 20) * 50;
        boolean doMobs = plugin.getConfig().getBoolean("voicechat.mobs.enabled", true)
                && now - lastMobs.getOrDefault(id, 0L) >= plugin.getConfig().getLong("voicechat.mobs.cooldown-ticks", 40) * 50;
        if (!doSculk && !doMobs) {
            return;
        }
        if (doSculk) {
            lastSculk.put(id, now);
        }
        if (doMobs) {
            lastMobs.put(id, now);
        }
        final boolean sculk = doSculk;
        final boolean mobs = doMobs;
        Bukkit.getScheduler().runTask(plugin, () -> react(player, whispering, sculk, mobs));
    }

    private void react(Player player, boolean whispering, boolean sculk, boolean mobs) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        var cfg = plugin.getConfig();
        boolean sneaking = player.isSneaking();

        // Sculk-Sensoren: Flüstern bleibt unhörbar, Schleichen auf Wunsch auch
        if (sculk && !whispering && !(sneaking && cfg.getBoolean("voicechat.sculk.sneak-silent", true))) {
            GameEvent gameEvent = Registry.GAME_EVENT.get(NamespacedKey.minecraft(
                    cfg.getString("voicechat.sculk.event", "note_block_play")));
            if (gameEvent != null) {
                player.getWorld().sendGameEvent(player, gameEvent, player.getLocation().toVector());
            }
        }

        // Monster und Wächter: erweiterter Hörradius
        if (mobs && player.getGameMode() != GameMode.CREATIVE) {
            double factor = 1.0;
            if (whispering) {
                factor = cfg.getDouble("voicechat.mobs.whisper-factor", 0.15);
            } else if (sneaking) {
                factor = cfg.getDouble("voicechat.mobs.sneak-factor", 0.6);
            }
            Location location = player.getLocation();

            // Der Wächter (Warden) wird wütender, je öfter er dich hört
            if (cfg.getBoolean("voicechat.warden.enabled", true)) {
                double wardenRadius = cfg.getDouble("voicechat.warden.radius", 32) * factor;
                int anger = Math.max(1, cfg.getInt("voicechat.warden.anger", 30));
                if (wardenRadius >= 2) {
                    for (Warden warden : player.getWorld().getNearbyEntitiesByType(Warden.class, location, wardenRadius)) {
                        warden.increaseAnger(player, anger);
                    }
                }
            }

            double radius = cfg.getDouble("voicechat.mobs.radius", 40) * factor;
            if (radius < 2) {
                return;
            }
            int max = Math.max(1, cfg.getInt("voicechat.mobs.max-mobs", 10));
            double aggroRadius = cfg.getDouble("voicechat.mobs.aggro-radius", 24);
            int handled = 0;
            for (Monster monster : player.getWorld().getNearbyEntitiesByType(Monster.class, location, radius)) {
                if (handled >= max) {
                    break;
                }
                if (monster instanceof Warden || monster.getTarget() != null) {
                    continue;
                }
                handled++;
                if (monster.getLocation().distance(location) <= aggroRadius) {
                    monster.setTarget(player);
                } else {
                    monster.getPathfinder().moveTo(location, 1.0);
                }
            }
        }
    }
}
