package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WheelManager;
import de.coinshop.WheelManager.Prize;
import de.coinshop.WheelManager.SpinResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Das Glücksrad-Menü. In der mittleren Reihe laufen die Felder wie bei einem Spielautomaten
 * von rechts nach links durch; das Feld unter dem Pfeil (Mitte) ist der Gewinn.
 */
public final class WheelMenu extends Menu {

    private final WheelManager wheel;

    public WheelMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.wheel.title"));
        this.wheel = plugin.wheel();
        Messages msg = plugin.messages();
        long account = plugin.database().getShopAccount();

        // Mittlere Reihe (Felder 9-17): zufällige Felder als Deko
        for (int i = 0; i < 9; i++) {
            getInventory().setItem(9 + i, wheel.segmentItem(wheel.decorativeRoll(), account));
        }

        // Pfeil über dem mittleren Feld
        set(4, ItemUtil.icon(Material.HOPPER, msg.get("gui.wheel.pointer-name"), List.of()), null);

        // Untere Reihe
        set(18, backButton(), (player, click) -> new CasinoMenu(plugin, player).open(player));
        set(20, accountIcon(), null);
        set(22, ItemUtil.icon(Material.EMERALD, msg.get("gui.wheel.spin-name"),
                msg.getList("gui.wheel.spin-lore", "price", wheel.price())), (player, click) -> spin(player));
        set(24, tableIcon(), null);
        set(26, balanceIcon(viewer), incomeClick());

        fillEmpty();
    }

    private ItemStack accountIcon() {
        Messages msg = plugin.messages();
        return ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.wheel.account-name"),
                msg.getList("gui.wheel.account-lore", "coins", plugin.database().getShopAccount()));
    }

    /** Anzeige der Gewinnchancen. */
    private ItemStack tableIcon() {
        Messages msg = plugin.messages();
        List<Component> lore = new ArrayList<>();
        for (WheelManager.Tier tier : wheel.tiers()) {
            StringBuilder amounts = new StringBuilder();
            for (long amount : tier.amounts()) {
                if (amounts.length() > 0) {
                    amounts.append(" / ");
                }
                amounts.append(Messages.number(amount));
            }
            lore.add(msg.get("gui.wheel.table-tier", "odds", tier.odds(), "amounts", amounts.toString()));
        }
        lore.add(msg.get("gui.wheel.table-jackpot", "odds", wheel.jackpotOdds()));
        return ItemUtil.icon(Material.BOOK, msg.get("gui.wheel.table-name"), lore);
    }

    // ------------------------------------------------------------------
    //  Drehen + Animation
    // ------------------------------------------------------------------

    private void spin(Player player) {
        UUID uuid = player.getUniqueId();
        if (wheel.isSpinning(uuid)) {
            return;
        }
        // Bezahlt, entscheidet und zahlt den Gewinn sofort aus (null = geht nicht, Nachricht ist gesendet)
        SpinResult result = wheel.trySpin(player);
        if (result == null) {
            return;
        }

        // Der "Streifen" aller Felder, der durchläuft. Das Ergebnis liegt so, dass es am Ende in der Mitte steht.
        int last = 36 + wheel.random().nextInt(12);
        long shown = plugin.database().getShopAccount() + result.payout();
        List<ItemStack> strip = new ArrayList<>();
        for (int i = 0; i < last + 9; i++) {
            strip.add(wheel.segmentItem(wheel.decorativeRoll(), shown));
        }
        // Das Gewinnfeld zeigt genau das, was wirklich ausgezahlt wurde (auch wenn das Shop-Konto zu leer war)
        WheelManager.Prize landing = result.prize().jackpot() ? result.prize()
                : new WheelManager.Prize(false, result.payout(), 1);
        strip.set(last + 4, wheel.segmentItem(landing, shown));

        Runnable finish = () -> {
            wheel.endSpin(uuid);
            wheel.announce(player, result);
            if (player.isOnline()) {
                Sound sound = result.prize().jackpot() ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                        : (result.payout() > 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
            // Nur die Anzeigen für Shop-Konto und Kontostand auffrischen - das Gewinnfeld bleibt stehen
            getInventory().setItem(20, accountIcon());
            getInventory().setItem(26, balanceIcon(player));
        };

        frame(player, strip, 0, last, finish);
    }

    /** Zeigt Bild "f" der Animation und plant das nächste (mit immer längerer Pause = Auslaufen). */
    private void frame(Player player, List<ItemStack> strip, int f, int last, Runnable finish) {
        // Wenn der Spieler das Menü schließt oder geht, sofort fertig (der Gewinn ist ja schon bezahlt)
        if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) {
            finish.run();
            return;
        }
        for (int i = 0; i < 9; i++) {
            getInventory().setItem(9 + i, strip.get(f + i));
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);

        if (f >= last) {
            finish.run();
            return;
        }
        int remaining = last - f;
        int delay = remaining > 20 ? 1 : remaining > 10 ? 2 : remaining > 4 ? 4 : 7;
        Bukkit.getScheduler().runTaskLater(plugin, () -> frame(player, strip, f + 1, last, finish), delay);
    }
}
