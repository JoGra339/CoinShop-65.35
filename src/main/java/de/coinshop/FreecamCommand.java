package de.coinshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /freecam schaltet die Freecam ein und aus. */
public final class FreecamCommand implements CommandExecutor {

    private final CoinShopPlugin plugin;

    public FreecamCommand(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        try {
            plugin.freecam().toggle(player);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /freecam: " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }
}
