package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Schutzgeld: Der Pate (Revier 0 = alle Reviere) und die Unterhändlere (nur ihr eigenes Revier) können Spielern
 * Schutz anbieten. Wer annimmt, gibt einen Teil dessen ab, was der Händler ihm zahlt, und dafür kauft der Händler
 * seine Items mit höherer Chance:
 *  - Pate: 5 % der Einnahmen, +10 Prozentpunkte Kaufchance (in allen Revieren)
 *  - Unterhändler: 2 % der Einnahmen, +5 Prozentpunkte Kaufchance (nur in seinem Revier)
 * Die Verträge enden bei der nächsten Auswertung (Sonntag 12:00) oder wenn einer sie beendet.
 */
public final class ProtectionManager implements CommandExecutor, TabCompleter {

    private static final long OFFER_MILLIS = 5L * 60_000L;

    private final CoinShopPlugin plugin;
    /** Offene Angebote: Schlüssel "anbieter|revier|ziel" -> Ablaufzeit. */
    private final Map<String, Long> pending = new HashMap<>();

    public ProtectionManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Werte
    // ------------------------------------------------------------------

    public double percent(int revier) {
        return Math.max(0, revier == 0
                ? plugin.getConfig().getDouble("schutzgeld.pate-percent", 5)
                : plugin.getConfig().getDouble("schutzgeld.boss-percent", 2));
    }

    public int chance(int revier) {
        return Math.max(0, revier == 0
                ? plugin.getConfig().getInt("schutzgeld.pate-chance", 10)
                : plugin.getConfig().getInt("schutzgeld.boss-chance", 5));
    }

    /** Ist der Anbieter jetzt noch Pate (revier 0) bzw. Boss dieses Reviers? */
    private boolean valid(UUID offerer, int revier) {
        UUID holder = revier == 0 ? plugin.reviere().pate() : plugin.reviere().boss(revier);
        return holder != null && holder.equals(offerer);
    }

    private String scope(int revier) {
        return revier == 0 ? "allen Revieren" : "dem Revier " + plugin.reviere().name(revier);
    }

    // ------------------------------------------------------------------
    //  Wirkung beim Ankauf durch den Händler
    // ------------------------------------------------------------------

    /** Aktive, noch gültige Verträge, die für einen Slot in diesem Revier gelten (Pate und Unterhändler). */
    private List<Database.Protection> active(UUID owner, int revier) {
        List<Database.Protection> result = new ArrayList<>();
        for (Database.Protection p : plugin.database().protectionsOf(owner)) {
            if ((p.revier() == 0 || p.revier() == revier) && valid(p.offerer(), p.revier())) {
                result.add(p);
            }
        }
        return result;
    }

    /** Zusätzliche Kaufchance (Prozentpunkte) für einen Verkaufs-Slot. */
    public int bonusChance(UUID owner, int revier) {
        int bonus = 0;
        for (Database.Protection p : active(owner, revier)) {
            bonus += chance(p.revier());
        }
        return bonus;
    }

    /**
     * Zieht das Schutzgeld vom Erlös ab und zahlt es dem Anbieter. Läuft in der laufenden Transaktion; der Verkäufer
     * hat die Coins zuvor schon bekommen. Gibt zurück, wie viel insgesamt abgegeben wurde.
     */
    public long payShares(UUID owner, int revier, long coins) {
        Database db = plugin.database();
        long total = 0;
        for (Database.Protection p : active(owner, revier)) {
            long share = (long) Math.floor(coins * percent(p.revier()) / 100.0);
            if (share < 1 || !db.takeCoins(owner, share)) {
                continue;
            }
            db.addCoins(p.offerer(), share);
            String ownerName = db.getName(owner);
            String offererName = db.getName(p.offerer());
            db.logTx(owner, ownerName, "schutzgeld-zahlt", -share, "an " + offererName);
            db.logTx(p.offerer(), offererName, "schutzgeld-erhalten", share, "von " + ownerName);
            total += share;
            Player boss = Bukkit.getPlayer(p.offerer());
            if (boss != null) {
                plugin.messages().send(boss, "protect-income", "player", ownerName, "coins", share);
            }
        }
        return total;
    }

    /**
     * Nach der wöchentlichen Auswertung: Verträge bleiben bestehen, wenn der Anbieter immer noch Pate bzw. Boss im
     * selben Revier ist. Alle anderen enden.
     */
    public void pruneInvalid() {
        Database db = plugin.database();
        for (Database.Protection p : db.allProtections()) {
            if (!valid(p.offerer(), p.revier())) {
                db.removeProtection(p.offerer(), p.revier(), p.target());
                Player target = Bukkit.getPlayer(p.target());
                if (target != null) {
                    plugin.messages().send(target, "protect-lapsed", "player", db.getName(p.offerer()),
                            "scope", scope(p.revier()));
                }
            }
        }
        pending.clear();
    }

    /** Beendet einen Vertrag; der Spieler muss Anbieter oder Kunde sein. */
    public void endContract(Player actor, UUID offerer, int revier, UUID target) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID me = actor.getUniqueId();
        if (!me.equals(offerer) && !me.equals(target)) {
            msg.send(actor, "protect-none-found");
            return;
        }
        if (!db.removeProtection(offerer, revier, target)) {
            msg.send(actor, "protect-none-found");
            return;
        }
        msg.send(actor, "protect-ended");
        UUID other = me.equals(offerer) ? target : offerer;
        Player online = Bukkit.getPlayer(other);
        if (online != null) {
            msg.send(online, "protect-ended-other", "player", actor.getName());
        }
    }

    public String scopeText(int revier) {
        return revier == 0 ? "allen Revieren" : plugin.reviere().name(revier);
    }

    // ------------------------------------------------------------------
    //  Befehl /schutzgeld
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();
        if (!(sender instanceof Player player)) {
            msg.send(sender, "only-players");
            return true;
        }
        if (!plugin.blackMarket().allowed(player)) {
            return true;
        }
        try {
            if (args.length == 0) {
                new de.coinshop.gui.ContractsMenu(plugin, player).open(player);
            } else {
                switch (args[0].toLowerCase()) {
                    case "anbieten" -> offer(player, args);
                    case "annehmen" -> accept(player, args);
                    case "ablehnen" -> {
                        if (parseKey(player, args) != null) {
                            pending.remove(key(args[1], Integer.parseInt(args[2]), player.getUniqueId()));
                            msg.send(player, "protect-declined");
                        }
                    }
                    case "beenden" -> end(player, args);
                    case "menu", "menü" -> {
                        if (myDeals(player.getUniqueId()).isEmpty()) {
                            msg.send(player, "protect-not-allowed");
                        } else {
                            new de.coinshop.gui.ProtectionMenu(plugin, player).open(player);
                        }
                    }
                    default -> msg.send(player, "protect-usage");
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /schutzgeld: " + e);
            msg.send(player, "error");
        }
        return true;
    }

    private String key(String offererName, int revier, UUID target) {
        return offererName.toLowerCase() + "|" + revier + "|" + target;
    }

    /** Prüft "<Name> <Revier>" in args[1], args[2]; null bei Fehler (Nachricht ist dann schon gesendet). */
    private Object parseKey(Player player, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(player, "protect-usage");
            return null;
        }
        try {
            int revier = Integer.parseInt(args[2]);
            if (revier < 0 || revier > RevierManager.COUNT) {
                throw new NumberFormatException();
            }
            return revier;
        } catch (NumberFormatException e) {
            plugin.messages().send(player, "protect-usage");
            return null;
        }
    }

    private void status(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID me = player.getUniqueId();
        msg.sendPlain(player, "protect-header");
        boolean any = false;
        for (Database.Protection p : db.protectionsOf(me)) {
            any = true;
            msg.sendPlain(player, "protect-line-paying", "player", db.getName(p.offerer()), "scope", scope(p.revier()),
                    "percent", Messages.number(Math.round(percent(p.revier()))), "chance", chance(p.revier()),
                    "valid", valid(p.offerer(), p.revier()) ? "" : " (nicht mehr gültig)");
        }
        for (Database.Protection p : db.protectionsBy(me)) {
            any = true;
            msg.sendPlain(player, "protect-line-selling", "player", db.getName(p.target()), "scope", scope(p.revier()));
        }
        if (!any) {
            msg.sendPlain(player, "protect-none");
        }
        if (plugin.reviere().pate() != null && plugin.reviere().pate().equals(me)) {
            msg.sendPlain(player, "protect-hint-pate");
        } else if (plugin.reviere().isBoss(me)) {
            msg.sendPlain(player, "protect-hint-boss");
        } else {
            msg.sendPlain(player, "protect-hint-none");
        }
    }

    /** Alle Deals, die dieser Spieler anbieten darf: 0 = Pate (alle Reviere), 1-7 = Boss dieses Reviers. */
    public List<Integer> myDeals(UUID me) {
        List<Integer> deals = new ArrayList<>();
        if (valid(me, 0)) {
            deals.add(0);
        }
        for (int r = 1; r <= RevierManager.COUNT; r++) {
            if (valid(me, r)) {
                deals.add(r);
            }
        }
        return deals;
    }

    private void offer(Player player, String[] args) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID me = player.getUniqueId();
        if (args.length < 2) {
            msg.send(player, "protect-usage");
            return;
        }
        Optional<UUID> target = db.findUuidByName(args[1]);
        if (target.isEmpty()) {
            msg.send(player, "player-unknown", "player", args[1]);
            return;
        }
        UUID targetId = target.get();
        List<Integer> deals = myDeals(me);
        if (deals.isEmpty()) {
            msg.send(player, "protect-not-allowed");
            return;
        }
        int revier;
        if (args.length >= 3) {
            if (args[2].equalsIgnoreCase("beide") || args[2].equalsIgnoreCase("alle")) {
                revier = -1;
            } else {
                try {
                    revier = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    msg.send(player, "protect-usage");
                    return;
                }
            }
        } else if (deals.size() == 1) {
            revier = deals.get(0);
        } else {
            sendChoose(player, targetId);
            return;
        }
        offerDeals(player, targetId, revier);
    }

    /** Pate/Boss, die mehrere Deals anbieten dürfen, wählen im Menü. */
    private void sendChoose(Player player, UUID targetId) {
        new de.coinshop.gui.ProtectionChoiceMenu(plugin, player, targetId).open(player);
    }

    /** Bietet einem Spieler einen Deal an: revier 0 = Pate, 1-7 = Boss, -1 = alle Deals, die man anbieten darf. */
    public void offerDeals(Player player, UUID targetId, int revier) {
        if (revier == -1) {
            for (int d : myDeals(player.getUniqueId())) {
                sendOffer(player, targetId, d);
            }
        } else {
            sendOffer(player, targetId, revier);
        }
    }

    private void sendOffer(Player player, UUID targetId, int revier) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID me = player.getUniqueId();
        if (targetId.equals(me)) {
            msg.send(player, "protect-self");
            return;
        }
        if (revier < 0 || revier > RevierManager.COUNT || !valid(me, revier)) {
            msg.send(player, "protect-not-allowed");
            return;
        }
        if (!plugin.licenses().has(targetId, "schmuggler")) {
            msg.send(player, "protect-target-locked", "player", db.getName(targetId));
            return;
        }
        if (db.hasProtection(me, revier, targetId)) {
            msg.send(player, "protect-already", "player", db.getName(targetId));
            return;
        }
        Player online = Bukkit.getPlayer(targetId);
        if (online == null) {
            msg.send(player, "protect-offline", "player", db.getName(targetId));
            return;
        }
        pending.put(key(player.getName(), revier, targetId), System.currentTimeMillis() + OFFER_MILLIS);
        msg.send(player, "protect-offered", "player", online.getName(), "scope", scope(revier),
                "percent", Messages.number(Math.round(percent(revier))), "chance", chance(revier));
        String base = "/schutzgeld %s " + player.getName() + " " + revier;
        Component yes = Component.text("[ANNEHMEN]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(String.format(base, "annehmen")))
                .hoverEvent(HoverEvent.showText(Component.text("Schutzgeld-Vertrag abschließen", NamedTextColor.GREEN)));
        Component no = Component.text("[ABLEHNEN]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(String.format(base, "ablehnen")))
                .hoverEvent(HoverEvent.showText(Component.text("Angebot ablehnen", NamedTextColor.RED)));
        msg.send(online, "protect-received", "player", player.getName(), "scope", scope(revier),
                "percent", Messages.number(Math.round(percent(revier))), "chance", chance(revier),
                "yes", yes, "no", no);
    }

    private void accept(Player player, String[] args) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        Object parsed = parseKey(player, args);
        if (parsed == null) {
            return;
        }
        int revier = (Integer) parsed;
        String k = key(args[1], revier, player.getUniqueId());
        Long until = pending.get(k);
        Optional<UUID> offerer = db.findUuidByName(args[1]);
        if (until == null || until < System.currentTimeMillis() || offerer.isEmpty()) {
            msg.send(player, "protect-expired");
            pending.remove(k);
            return;
        }
        pending.remove(k);
        if (!valid(offerer.get(), revier)) {
            msg.send(player, "protect-expired");
            return;
        }
        db.addProtection(offerer.get(), revier, player.getUniqueId());
        msg.send(player, "protect-accepted", "player", db.getName(offerer.get()), "scope", scope(revier),
                "percent", Messages.number(Math.round(percent(revier))), "chance", chance(revier));
        Player boss = Bukkit.getPlayer(offerer.get());
        if (boss != null) {
            msg.send(boss, "protect-accepted-boss", "player", player.getName(), "scope", scope(revier));
        }
    }

    /** /schutzgeld beenden <Name> <Revier>: der Anbieter nennt den Kunden, der Kunde den Anbieter. */
    private void end(Player player, String[] args) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        Object parsed = parseKey(player, args);
        if (parsed == null) {
            return;
        }
        int revier = (Integer) parsed;
        Optional<UUID> other = db.findUuidByName(args[1]);
        if (other.isEmpty()) {
            msg.send(player, "player-unknown", "player", args[1]);
            return;
        }
        UUID me = player.getUniqueId();
        boolean done = db.removeProtection(me, revier, other.get()) || db.removeProtection(other.get(), revier, me);
        msg.send(player, done ? "protect-ended" : "protect-none-found");
        if (done) {
            Player online = Bukkit.getPlayer(other.get());
            if (online != null) {
                msg.send(online, "protect-ended-other", "player", player.getName());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("anbieten", "annehmen", "ablehnen", "beenden", "menu"));
        } else if (args.length == 2) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                options.add(online.getName());
            }
        }
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        options.removeIf(o -> !o.toLowerCase().startsWith(typed));
        return options;
    }
}
