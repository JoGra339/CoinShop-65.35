package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Das tägliche Gratis-Glücksrad: Jeder darf pro Tag (Wechsel zur Shop-Zeit) einmal gratis drehen und bekommt
 * eine kleine Belohnung. Die Gewinne und ihre Gewichte stehen in der config.yml unter daily-wheel.prizes.
 */
public final class DailyWheel implements CommandExecutor {

    private final CoinShopPlugin plugin;
    private final Random random = new Random();

    public DailyWheel(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private long dayNumber() {
        ZonedDateTime now = ZonedDateTime.now(plugin.serverShop().zone());
        return now.minusHours(plugin.serverShop().resetHour()).toLocalDate().toEpochDay();
    }

    public boolean available(UUID uuid) {
        return plugin.database().getMetaLong("dailywheel_" + uuid, -1) != dayNumber();
    }

    /** Gewinne als {Betrag, Gewicht}. */
    private List<long[]> prizes() {
        List<long[]> list = new ArrayList<>();
        for (Map<?, ?> map : plugin.getConfig().getMapList("daily-wheel.prizes")) {
            try {
                long amount = Long.parseLong(String.valueOf(map.get("amount")));
                long weight = Long.parseLong(String.valueOf(map.get("weight")));
                if (amount >= 0 && weight > 0) {
                    list.add(new long[]{amount, weight});
                }
            } catch (NumberFormatException ignored) {
                // kaputter Eintrag wird übersprungen
            }
        }
        if (list.isEmpty()) {
            list.add(new long[]{5, 1});
        }
        return list;
    }

    private long roll(List<long[]> prizes) {
        long total = 0;
        for (long[] p : prizes) {
            total += p[1];
        }
        long pick = (long) (random.nextDouble() * total);
        for (long[] p : prizes) {
            pick -= p[1];
            if (pick < 0) {
                return p[0];
            }
        }
        return prizes.get(0)[0];
    }

    public void spin(Player player) {
        Messages msg = plugin.messages();
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (plugin.timeline().previewFor(player)) {
            msg.send(player, "bm-preview");
            return;
        }
        if (!available(uuid)) {
            msg.send(player, "dailywheel-used", "time", ItemUtil.formatDuration(plugin.serverShop().timeUntilReset().toMillis()));
            return;
        }
        db.setMetaLong("dailywheel_" + uuid, dayNumber()); // sofort merken: kein zweites Drehen
        List<long[]> prizes = prizes();
        long prize = roll(prizes);
        // Sehr selten statt Coins: eine einfache Mystery Box
        final boolean boxWon = random.nextDouble() * 1000 < Math.max(0, plugin.getConfig().getInt("daily-wheel.box-per-1000", 25)) * plugin.mascot().luck(uuid);
        if (boxWon) {
            prize = 0;
        }
        if (prize > 0) {
            prize = Math.max(prize, plugin.licenses().boost(uuid, "glueck", prize)); // Glückspilz: mehr Gewinn
        }
        final long won = prize;
        player.closeInventory();
        msg.send(player, "dailywheel-spinning");
        int[] step = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            step[0]++;
            Player online = Bukkit.getPlayer(uuid);
            if (step[0] <= 8) {
                if (online != null) {
                    // Die letzten Bilder zeigen schon den echten Gewinn: was zuletzt steht, wird auch ausgezahlt
                    long shown = step[0] >= 7 ? won : prizes.get(random.nextInt(prizes.size()))[0];
                    online.sendActionBar(Component.text("Gratis-Glücksrad: "
                            + (boxWon && step[0] >= 7 ? "Mystery Box" : Messages.number(shown) + (step[0] >= 7 ? " Coins" : " ...")),
                            NamedTextColor.YELLOW));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f + step[0] * 0.08f);
                }
                return;
            }
            task.cancel();
            payout(uuid, won);
            Player target = Bukkit.getPlayer(uuid);
            if (boxWon && target != null) {
                target.showTitle(Title.title(Component.text("Mystery Box!", NamedTextColor.GOLD),
                        Component.text("Dein Gratis-Glücksrad", NamedTextColor.YELLOW)));
                plugin.mysteryBoxes().give(target, 1, "Gratis-Glücksrad");
            } else if (target != null) {
                target.showTitle(Title.title(Component.text(Messages.number(won) + " Coins!", NamedTextColor.GOLD),
                        Component.text("Dein Gratis-Glücksrad", NamedTextColor.YELLOW)));
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                plugin.messages().send(target, "dailywheel-win", "coins", won);
            }
        }, 6L, 6L);
    }

    /** Zahlt den Gewinn: zuerst aus dem Shop-Konto, den Rest gibt es neu dazu. */
    private void payout(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        Database db = plugin.database();
        db.begin();
        try {
            long fromShop = Math.min(db.getShopAccount(), amount);
            if (fromShop > 0) {
                db.takeShopAccount(fromShop);
            }
            db.addCoins(uuid, amount);
            db.commit();
            db.logTx(uuid, db.getName(uuid), "gratis-rad", amount, "Tägliches Gratis-Glücksrad");
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Gratis-Glücksrad: Auszahlung fehlgeschlagen: " + e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        try {
            spin(player);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /gluecksrad: " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }
}
