package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /tp spawn, /spawn, /sethome, /home, /tpa [Spieler], /tpaccept, /tpdeny
 *
 * Achtung: /tp gibt es auch im normalen Minecraft (für Admins). Alles außer "/tp spawn" wird deshalb
 * an den normalen Befehl weitergereicht, wenn der Spieler dazu berechtigt ist.
 */
public final class TeleportCommands implements CommandExecutor, TabCompleter {

    private final CoinShopPlugin plugin;

    public TeleportCommands(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        String name = command.getName().toLowerCase();

        // Normales Minecraft-/tp für Admins (alles außer "spawn")
        if (name.equals("tp") && !(args.length == 1 && args[0].equalsIgnoreCase("spawn"))) {
            if (sender.hasPermission("minecraft.command.teleport") || sender.isOp()) {
                Bukkit.dispatchCommand(sender, "minecraft:tp " + String.join(" ", args));
            } else {
                msg.send(sender, "tp-usage");
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            msg.send(sender, "only-players");
            return true;
        }
        try {
            switch (name) {
                case "tp", "spawn" -> plugin.teleports().goSpawn(player);
                case "sethome" -> plugin.teleports().setHome(player);
                case "home" -> plugin.teleports().goHome(player);
                case "tpa" -> {
                    if (args.length != 1) {
                        msg.send(player, "tpa-usage");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        msg.send(player, "tpa-not-found");
                        return true;
                    }
                    plugin.teleports().request(player, target);
                }
                case "tpaccept" -> plugin.teleports().accept(player);
                case "tpdeny" -> plugin.teleports().deny(player);
                default -> msg.send(player, "error");
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /" + name + ": " + e);
            msg.send(player, "error");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        List<String> result = new ArrayList<>();
        if (name.equals("tp") && args.length == 1) {
            if ("spawn".startsWith(args[0].toLowerCase())) {
                result.add("spawn");
            }
            return result;
        }
        if (name.equals("tpa") && args.length == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    result.add(p.getName());
                }
            }
        }
        return result;
    }
}
