package de.coinshop;

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

/** Die Befehle /coins, /pay, /baltop und /coinsadmin. */
public final class CoinCommands implements CommandExecutor, TabCompleter {

    // Größter Betrag, den ein Admin auf einmal eingeben kann (nur Schutz vor Zahlen-Überlauf)
    private static final long ADMIN_MAX = 1_000_000_000_000L;

    private final CoinShopPlugin plugin;

    public CoinCommands(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            switch (command.getName().toLowerCase()) {
                case "coins" -> coins(sender, args);
                case "pay" -> pay(sender, args);
                case "baltop" -> baltop(sender);
                case "coinsadmin" -> admin(sender, args);
                case "shopreset" -> {
                    if (!sender.hasPermission("coinshop.admin")) {
                        plugin.messages().send(sender, "no-permission");
                    } else {
                        plugin.serverShop().fullReset();
                        plugin.messages().send(sender, "admin-shop-reset");
                    }
                }
                default -> { }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /" + command.getName() + ": " + e);
            e.printStackTrace();
            plugin.messages().send(sender, "error");
            if (sender.hasPermission("coinshop.admin")) {
                Throwable root = e;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                sender.sendMessage(net.kyori.adventure.text.Component.text("Ursache (nur für Admins): " + root,
                        net.kyori.adventure.text.format.NamedTextColor.GRAY));
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  /coins [Spieler]
    // ------------------------------------------------------------------
    private void coins(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        Database db = plugin.database();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "only-players");
                return;
            }
            msg.send(player, "balance-self", "coins", db.getCoins(player.getUniqueId()));
            return;
        }
        Optional<UUID> target = db.findUuidByName(args[0]);
        if (target.isEmpty()) {
            msg.send(sender, "player-unknown", "player", args[0]);
            return;
        }
        msg.send(sender, "balance-other", "player", db.getName(target.get()), "coins", db.getCoins(target.get()));
    }

    // ------------------------------------------------------------------
    //  /pay [Spieler] [Betrag]
    // ------------------------------------------------------------------
    private void pay(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        Database db = plugin.database();

        if (!(sender instanceof Player player)) {
            msg.send(sender, "only-players");
            return;
        }
        if (args.length != 2) {
            msg.send(player, "pay-usage");
            return;
        }
        Optional<UUID> target = db.findUuidByName(args[0]);
        if (target.isEmpty()) {
            msg.send(player, "player-unknown", "player", args[0]);
            return;
        }
        if (target.get().equals(player.getUniqueId())) {
            msg.send(player, "pay-self");
            return;
        }
        long max = plugin.getConfig().getLong("coins.max-transfer", 1_000_000_000L);
        long amount = parseAmount(args[1], max);
        if (amount < 1) {
            msg.send(player, "invalid-amount", "max", max);
            return;
        }
        if (!db.transfer(player.getUniqueId(), target.get(), amount)) {
            msg.send(player, "not-enough");
            return;
        }

        String targetName = db.getName(target.get());
        db.logTx(player.getUniqueId(), player.getName(), "pay", -amount, "an " + targetName);
        db.logTx(target.get(), targetName, "pay", amount, "von " + player.getName());
        msg.send(player, "pay-sent", "coins", amount, "player", targetName);
        Player online = Bukkit.getPlayer(target.get());
        if (online != null) {
            msg.send(online, "pay-received", "coins", amount, "player", player.getName());
        }
    }

    // ------------------------------------------------------------------
    //  /baltop
    // ------------------------------------------------------------------
    private void baltop(CommandSender sender) {
        Messages msg = plugin.messages();
        List<Database.TopEntry> top = plugin.database().top(10);
        if (top.isEmpty()) {
            msg.send(sender, "baltop-empty");
            return;
        }
        msg.sendPlain(sender, "baltop-header");
        int rank = 1;
        for (Database.TopEntry entry : top) {
            msg.sendPlain(sender, "baltop-line", "rank", rank++, "player", entry.name(), "coins", entry.coins());
        }
    }

    // ------------------------------------------------------------------
    //  /coinsadmin give|take|set [Spieler] [Betrag] | resetshop | reload
    // ------------------------------------------------------------------
    private void admin(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        Database db = plugin.database();

        if (!sender.hasPermission("coinshop.admin")) {
            msg.send(sender, "no-permission");
            return;
        }
        if (args.length == 0) {
            msg.send(sender, "admin-usage");
            return;
        }
        String sub = args[0].toLowerCase();
        db.ensureAutoCommit();

        if (sub.equals("reload")) {
            plugin.reloadAll();
            msg.send(sender, "admin-reload");
            return;
        }
        if (sub.equals("border")) {
            if (sender instanceof Player admin) {
                boolean on = plugin.timeline().toggleTestBorder(admin);
                msg.send(sender, on ? "admin-border-on" : "admin-border-off", "radius", plugin.getConfig().getInt("safe-zone.radius", 150));
            } else {
                msg.send(sender, "admin-usage");
            }
            return;
        }
        if (sub.equals("preise")) {
            plugin.priceBook().reload();
            int missing = plugin.priceBook().writeFiles();
            msg.send(sender, "admin-prices", "missing", missing);
            return;
        }
        if (sub.equals("resetshop")) {
            plugin.serverShop().fullReset();
            msg.send(sender, "admin-shop-reset");
            return;
        }
        if (sub.equals("flash")) {
            // /coinsadmin flash blitz|gold|fee  - startet ein Kurzzeit-Ereignis zum Testen
            FlashEventManager.Flash chosen = null;
            if (args.length >= 2) {
                switch (args[1].toLowerCase()) {
                    case "blitz" -> chosen = FlashEventManager.Flash.BLITZ;
                    case "gold" -> chosen = FlashEventManager.Flash.GOLD;
                    case "fee" -> chosen = FlashEventManager.Flash.FEE;
                    default -> { }
                }
            }
            if (chosen == null) {
                msg.send(sender, "admin-usage");
            } else {
                plugin.flash().begin(chosen);
            }
            return;
        }
        if (sub.equals("stats")) {
            adminStats(sender);
            return;
        }
        if (sub.equals("log")) {
            adminLog(sender, args);
            return;
        }
        if (sub.equals("backup")) {
            java.io.File file = plugin.backups().run();
            if (file == null) {
                msg.send(sender, "admin-backup-failed");
            } else {
                msg.send(sender, "admin-backup-done", "file", file.getName());
            }
            return;
        }
        if (!(sub.equals("give") || sub.equals("take") || sub.equals("set")) || args.length != 3) {
            msg.send(sender, "admin-usage");
            return;
        }

        Optional<UUID> target = db.findUuidByName(args[1]);
        if (target.isEmpty()) {
            msg.send(sender, "player-unknown", "player", args[1]);
            return;
        }
        // "set" darf auch 0 sein, give/take müssen mindestens 1 sein
        long amount = parseAmount(args[2], ADMIN_MAX);
        boolean zeroAllowed = sub.equals("set") && args[2].trim().equals("0");
        if (amount < 1 && !zeroAllowed) {
            msg.send(sender, "invalid-amount", "max", ADMIN_MAX);
            return;
        }
        amount = Math.max(0, amount);

        String name = db.getName(target.get());
        db.logTx(target.get(), name, "admin-" + sub, sub.equals("take") ? -amount : amount,
                "durch " + sender.getName());
        switch (sub) {
            case "give" -> {
                db.addCoins(target.get(), amount);
                msg.send(sender, "admin-given", "player", name, "coins", amount);
            }
            case "take" -> {
                if (!db.takeCoins(target.get(), amount)) {
                    msg.send(sender, "admin-target-too-poor", "player", name);
                    return;
                }
                msg.send(sender, "admin-taken", "player", name, "coins", amount);
            }
            default -> {
                db.setCoins(target.get(), amount);
                msg.send(sender, "admin-set", "player", name, "coins", amount);
            }
        }
    }

    // ------------------------------------------------------------------
    //  /coinsadmin stats  - Überblick über die Wirtschaft des Servers
    // ------------------------------------------------------------------
    private void adminStats(CommandSender sender) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        long players = db.countAccounts();
        long total = db.sumCoins();
        String richest = "-";
        long richestCoins = 0;
        for (Database.Account a : db.allAccounts()) {
            if (a.coins() > richestCoins) {
                richestCoins = a.coins();
                richest = a.name();
            }
        }
        msg.sendPlain(sender, "admin-stats-header");
        msg.sendPlain(sender, "admin-stats-main", "players", players, "total", total,
                "average", players == 0 ? 0 : total / players, "richest", richest, "richestcoins", richestCoins);
        msg.sendPlain(sender, "admin-stats-shop", "account", db.getShopAccount(),
                "invest", db.countOwnedInvestments(), "price", plugin.stock().price());
        long since = System.currentTimeMillis() - 24L * 3_600_000L;
        java.util.List<Database.TypeSum> sums = db.txSummarySince(since);
        if (sums.isEmpty()) {
            msg.sendPlain(sender, "admin-stats-none");
            return;
        }
        msg.sendPlain(sender, "admin-stats-24h");
        for (Database.TypeSum t : sums) {
            msg.sendPlain(sender, "admin-stats-line", "type", t.type(), "count", t.count(), "sum", t.sum());
        }
    }

    // ------------------------------------------------------------------
    //  /coinsadmin log [Spieler] [Anzahl]  - die letzten Coin-Bewegungen
    // ------------------------------------------------------------------
    private void adminLog(CommandSender sender, String[] args) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        java.util.UUID uuid = null;
        int limit = 15;
        for (int i = 1; i < args.length; i++) {
            try {
                limit = Math.max(1, Math.min(50, Integer.parseInt(args[i])));
            } catch (NumberFormatException e) {
                Optional<UUID> target = db.findUuidByName(args[i]);
                if (target.isEmpty()) {
                    msg.send(sender, "player-unknown", "player", args[i]);
                    return;
                }
                uuid = target.get();
            }
        }
        java.util.List<Database.TxEntry> entries = db.recentTx(uuid, limit);
        if (entries.isEmpty()) {
            msg.send(sender, "admin-log-empty");
            return;
        }
        msg.sendPlain(sender, "admin-log-header");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("dd.MM. HH:mm");
        for (Database.TxEntry e : entries) {
            msg.sendPlain(sender, "admin-log-line", "time", format.format(new java.util.Date(e.at())),
                    "player", e.name(), "type", e.type(), "amount", e.amount(), "detail", e.detail());
        }
    }

    /** Wandelt Text in eine Zahl um. Gibt -1 zurück, wenn ungültig oder größer als "max". */
    private static long parseAmount(String text, long max) {
        try {
            long value = Long.parseLong(text.trim());
            return (value < 0 || value > max) ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    //  Tab-Vervollständigung (Vorschläge beim Tippen)
    // ------------------------------------------------------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase();
        List<String> options = new ArrayList<>();

        if ((name.equals("coins") || name.equals("pay")) && args.length == 1) {
            addOnlineNames(options);
        } else if (name.equals("coinsadmin") && sender.hasPermission("coinshop.admin")) {
            if (args.length == 1) {
                options.addAll(List.of("give", "take", "set", "resetshop", "reload", "stats", "log", "backup", "preise", "border", "flash"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("flash")) {
                options.addAll(List.of("blitz", "gold", "fee"));
            } else if (args.length == 2 && List.of("give", "take", "set", "log").contains(args[0].toLowerCase())) {
                addOnlineNames(options);
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        options.removeIf(option -> !option.toLowerCase().startsWith(typed));
        return options;
    }

    private static void addOnlineNames(List<String> options) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            options.add(online.getName());
        }
    }
}
