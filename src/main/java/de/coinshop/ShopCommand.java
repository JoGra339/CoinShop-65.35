package de.coinshop;

import de.coinshop.gui.MainMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /shop            -> öffnet das Hauptmenü
 * /shop sell [Preis] -> bietet das Item in deiner Hand an (Alternative zum Menü)
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final CoinShopPlugin plugin;

    public ShopCommand(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "only-players");
            return true;
        }
        try {
            if (args.length >= 1 && args[0].equalsIgnoreCase("sell")) {
                if (args.length == 1) {
                    plugin.playerShop().startPricePrompt(player);
                } else {
                    long price;
                    try {
                        price = Long.parseLong(args[1].trim());
                    } catch (NumberFormatException e) {
                        msg.send(player, "ask-invalid");
                        return true;
                    }
                    plugin.playerShop().createListing(player, price);
                }
                return true;
            }
            new MainMenu(plugin).open(player);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /shop: " + e);
            msg.send(player, "error");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "sell".startsWith(args[0].toLowerCase())) {
            return List.of("sell");
        }
        return List.of();
    }
}
