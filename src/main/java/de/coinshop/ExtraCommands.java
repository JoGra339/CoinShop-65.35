package de.coinshop;

import de.coinshop.gui.LicenseMenu;
import de.coinshop.gui.ProfileMenu;
import de.coinshop.gui.QuestMenu;
import de.coinshop.gui.TimelineMenu;
import de.coinshop.gui.TitleMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Die Befehle /titel, /aufgaben und /profil [Spieler]. */
public final class ExtraCommands implements CommandExecutor, TabCompleter {

    private final CoinShopPlugin plugin;

    public ExtraCommands(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("endkampf") && !(sender instanceof Player)) {
            endFight(sender, args); // Admins dürfen den Endkampf auch von der Konsole steuern
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        try {
            switch (command.getName().toLowerCase()) {
                case "titel" -> new TitleMenu(plugin, player).open(player);
                case "aufgaben" -> new QuestMenu(plugin, player).open(player);
                case "profil" -> profile(player, args);
                case "fluestern" -> whisper(player, args);
                case "ruhemodus" -> plugin.logins().toggleRest(player);
                case "ausdauer" -> new de.coinshop.gui.StaminaMenu(plugin, player).open(player);
                case "umlauf" -> new de.coinshop.gui.MoneySupplyMenu(plugin, player).open(player);
                case "heute" -> new de.coinshop.gui.TodayMenu(plugin, player).open(player);
                case "endkampf" -> endFight(player, args);
                case "farmwelt" -> farm(player);
                case "termine" -> new TimelineMenu(plugin, player).open(player);
                case "lizenzen" -> new LicenseMenu(plugin, player).open(player);
                default -> { }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /" + command.getName() + ": " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }

    /** /flüstern Name Text: eine private Nachricht, die nur die genannte Person sieht. */
    private void whisper(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Benutzung: /flüstern [Spieler] [Nachricht]", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text(args[0] + " ist nicht online.", NamedTextColor.RED));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text("Du kannst nicht zu dir selbst flüstern.", NamedTextColor.RED));
            return;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        player.sendMessage(Component.text("[Flüstern an " + target.getName() + "] ", NamedTextColor.GRAY)
                .append(Component.text(text, NamedTextColor.WHITE)));
        target.sendMessage(Component.text("[Flüstern von " + player.getName() + "] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(text, NamedTextColor.WHITE)));
        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
    }

    /** /profil zeigt das eigene Profil, /profil Name das eines anderen Spielers (auch wenn er offline ist). */
    private void profile(Player player, String[] args) {
        UUID target = player.getUniqueId();
        if (args.length > 0) {
            Optional<UUID> found = plugin.database().findUuidByName(args[0]);
            if (found.isEmpty()) {
                plugin.messages().send(player, "player-unknown", "player", args[0]);
                return;
            }
            target = found.get();
        }
        new ProfileMenu(plugin, player, target).open(player);
    }

    /** /farmwelt: zum heutigen Farm-Punkt (mindestens 100.000 Blöcke vom Spawn entfernt). */
    private void farm(Player player) {
        Messages msg = plugin.messages();
        org.bukkit.Location target = plugin.farmWorld().today(true);
        if (target == null) {
            msg.send(player, "tp-failed");
            return;
        }
        msg.send(player, "farm-info", "x", target.getBlockX(), "z", target.getBlockZ());
        // Mit Wartezeit wie bei allen Teleports; das Ziel steht für den ganzen Tag fest
        plugin.teleports().teleportDelayed(player, () -> plugin.farmWorld().today(true));
    }

    /**
     * /endkampf            - Status (Datum, Anmeldung)
     * /endkampf abmelden   - Abmelden
     * /endkampf start|stop|reset - nur für Admins
     * (Angemeldet wird am End-Portal, nicht per Befehl.)
     */
    private void endFight(CommandSender sender, String[] args) {
        DragonFightManager fight = plugin.dragonFight();
        Messages msg = plugin.messages();
        String sub = args.length > 0 ? args[0].toLowerCase() : "status";
        switch (sub) {
            case "abmelden" -> {
                if (sender instanceof Player p) {
                    if (fight.isSignedUp(p.getUniqueId())) {
                        fight.signOff(p);
                    } else {
                        msg.send(p, "endfight-not-signed");
                    }
                }
            }
            case "start", "stop", "reset" -> {
                if (!sender.hasPermission("coinshop.admin")) {
                    msg.send(sender, "no-permission");
                    return;
                }
                if (sub.equals("start")) {
                    boolean ok = fight.beginFight(true);
                    msg.send(sender, ok ? "endfight-admin-started" : "endfight-admin-failed");
                } else if (sub.equals("stop")) {
                    fight.stop();
                    msg.send(sender, "endfight-admin-stopped");
                } else {
                    fight.reset();
                    msg.send(sender, "endfight-admin-reset");
                }
            }
            default -> {
                String state = switch (fight.state()) {
                    case 0 -> "wartet auf den Start";
                    case 1 -> "läuft gerade";
                    default -> "ist vorbei";
                };
                msg.send(sender, "endfight-status", "date", fight.startText(), "state", state,
                        "count", fight.signupCount());
                if (sender instanceof Player p && fight.state() == 0) {
                    msg.send(sender, fight.isSignedUp(p.getUniqueId()) ? "endfight-you-signed" : "endfight-you-not-signed");
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("endkampf") && args.length == 1) {
            for (String sub : new String[]{"status", "abmelden", "start", "stop", "reset"}) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    options.add(sub);
                }
            }
        }
        if ((command.getName().equalsIgnoreCase("profil") || command.getName().equalsIgnoreCase("fluestern")) && args.length == 1) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    options.add(online.getName());
                }
            }
        }
        return options;
    }
}
