package de.coinshop;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Sichert die Datenbank regelmäßig in den Ordner plugins/CoinShop/backups.
 * Es werden nur die neuesten Sicherungen behalten (Einstellung "backup.keep" in der config.yml).
 * Zum Zurückspielen: Server ausschalten, die gewünschte Sicherung nach plugins/CoinShop kopieren,
 * in "coinshop.db" umbenennen (die alte Datei vorher wegsichern) und den Server wieder starten.
 */
public final class BackupManager {

    private final CoinShopPlugin plugin;

    public BackupManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("backup.enabled", true);
    }

    /** Startet die Sicherung beim Einschalten und danach im eingestellten Abstand. */
    public void start() {
        if (!enabled()) {
            return;
        }
        long hours = Math.max(1, plugin.getConfig().getLong("backup.interval-hours", 6));
        long ticks = hours * 60L * 60L * 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::run, 20L * 30, ticks);
    }

    /** Macht jetzt eine Sicherung. Gibt die Datei zurück oder null, wenn es nicht geklappt hat. */
    public File run() {
        try {
            File dir = new File(plugin.getDataFolder(), "backups");
            if (!dir.exists() && !dir.mkdirs()) {
                plugin.getLogger().warning("Der Ordner für Sicherungen konnte nicht angelegt werden.");
                return null;
            }
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File target = new File(dir, "coinshop-" + stamp + ".db");
            plugin.database().backupTo(target);
            prune(dir);
            plugin.getLogger().info("Datenbank gesichert: " + target.getName());
            return target;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Sicherung fehlgeschlagen: " + e);
            return null;
        }
    }

    /** Löscht die ältesten Sicherungen, damit nur "keep" Stück übrig bleiben. */
    private void prune(File dir) {
        int keep = Math.max(1, plugin.getConfig().getInt("backup.keep", 20));
        File[] files = dir.listFiles((d, name) -> name.startsWith("coinshop-") && name.endsWith(".db"));
        if (files == null || files.length <= keep) {
            return;
        }
        Arrays.sort(files); // der Name enthält Datum und Uhrzeit: alt zuerst
        for (int i = 0; i < files.length - keep; i++) {
            if (!files[i].delete()) {
                plugin.getLogger().warning("Alte Sicherung konnte nicht gelöscht werden: " + files[i].getName());
            }
        }
    }
}
