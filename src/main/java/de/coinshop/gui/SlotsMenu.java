package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WheelManager;
import de.coinshop.WheelManager.Prize;
import de.coinshop.WheelManager.SpinResult;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Der Spielautomat: drei Walzen drehen sich nacheinander. Gewonnen wird nur, wenn alle drei Symbole
 * gleich sind. Das Ergebnis wird - wie beim Glücksrad - sofort entschieden und bezahlt; die Walzen sind nur Show.
 */
public final class SlotsMenu extends Menu {

    /** Symbole der Walzen. Das erste gehört zur häufigsten Gewinnstufe, ein Nether-Stern ist der Jackpot. */
    static final Material[] SYMBOLS = {
            Material.COAL, Material.COPPER_INGOT, Material.IRON_INGOT, Material.GOLD_INGOT,
            Material.EMERALD, Material.DIAMOND, Material.AMETHYST_SHARD, Material.QUARTZ
    };

    private static final int[] REELS = {11, 13, 15};
    private static final int[] STOP_FRAME = {12, 20, 28};

    private final WheelManager game;

    public SlotsMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.slots.title"));
        this.game = plugin.slots();
        Messages msg = plugin.messages();

        for (int slot : REELS) {
            getInventory().setItem(slot, symbolItem(Material.BARRIER));
        }
        set(4, ItemUtil.icon(Material.HOPPER, msg.get("gui.slots.pointer-name"), List.of()), null);

        set(18, backButton(), (player, click) -> new CasinoMenu(plugin, player).open(player));
        set(20, CasinoMenu.accountIcon(plugin), null);
        set(22, ItemUtil.icon(Material.EMERALD, msg.get("gui.slots.spin-name"),
                msg.getList("gui.slots.spin-lore", "price", game.price())), (player, click) -> spin(player));
        set(24, CasinoMenu.tableIcon(plugin, game), null);
        set(26, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    /** Das Item einer Walze; Name = Name des Materials. */
    private ItemStack symbolItem(Material material) {
        if (material == Material.BARRIER) {
            return ItemUtil.icon(material, plugin.messages().get("gui.slots.reel-name"), List.of());
        }
        return ItemUtil.icon(material, net.kyori.adventure.text.Component.translatable(material.translationKey())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false), List.of());
    }

    /** Welches Symbol gehört zu diesem Gewinn? (häufigste Stufe = erstes Symbol, Jackpot = Nether-Stern) */
    private Material symbolFor(Prize prize) {
        if (prize.jackpot()) {
            return Material.NETHER_STAR;
        }
        List<WheelManager.Tier> tiers = game.tiers(); // seltenste zuerst
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).amounts().contains(prize.amount())) {
                int fromCommon = tiers.size() - 1 - i;
                return SYMBOLS[Math.min(fromCommon, SYMBOLS.length - 1)];
            }
        }
        return SYMBOLS[0];
    }

    private Material randomSymbol() {
        return SYMBOLS[game.random().nextInt(SYMBOLS.length)];
    }

    private void spin(Player player) {
        UUID uuid = player.getUniqueId();
        if (game.isSpinning(uuid)) {
            return;
        }
        SpinResult result = game.trySpin(player);
        if (result == null) {
            return;
        }

        // Endstand der drei Walzen
        Material[] end = new Material[3];
        boolean win = result.prize().jackpot() || result.prize().amount() > 0;
        if (win) {
            Material symbol = symbolFor(result.prize());
            end[0] = end[1] = end[2] = symbol;
        } else {
            do {
                for (int i = 0; i < 3; i++) {
                    end[i] = randomSymbol();
                }
            } while (end[0] == end[1] && end[1] == end[2]); // bei einer Niete nie drei gleiche
        }

        long shown = plugin.database().getShopAccount() + result.payout();
        Runnable finish = () -> {
            game.endSpin(uuid);
            for (int i = 0; i < 3; i++) {
                getInventory().setItem(REELS[i], symbolItem(end[i]));
            }
            // Ergebnis oben in der Mitte (zeigt, was wirklich ausgezahlt wurde)
            Prize landing = result.prize().jackpot() ? result.prize() : new Prize(false, result.payout(), 1);
            getInventory().setItem(4, game.segmentItem(landing, shown));
            getInventory().setItem(20, CasinoMenu.accountIcon(plugin));
            if (player.isOnline()) {
                game.announce(player, result);
                getInventory().setItem(26, balanceIcon(player));
                Sound sound = result.prize().jackpot() ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                        : (result.payout() > 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        };
        getInventory().setItem(4, ItemUtil.icon(Material.HOPPER, plugin.messages().get("gui.slots.pointer-name"),
                List.of()));
        frame(player, end, 0, finish);
    }

    /** Ein Bild der Animation: Walzen, die noch nicht stehen, zeigen zufällige Symbole. */
    private void frame(Player player, Material[] end, int f, Runnable finish) {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != this) {
            finish.run();
            return;
        }
        for (int i = 0; i < 3; i++) {
            getInventory().setItem(REELS[i], symbolItem(f >= STOP_FRAME[i] ? end[i] : randomSymbol()));
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + 0.2f * (f % 4));
        if (f >= STOP_FRAME[2]) {
            finish.run();
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> frame(player, end, f + 1, finish), 2L);
    }
}
