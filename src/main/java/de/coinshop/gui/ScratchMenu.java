package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WheelManager;
import de.coinshop.WheelManager.Prize;
import de.coinshop.WheelManager.SpinResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Das Rubbellos: Du kaufst ein Los und deckst sechs Felder auf. Drei gleiche Symbole = Gewinn.
 * Das Ergebnis wird beim Kauf entschieden und sofort bezahlt; das Aufdecken ist nur die Spannung.
 * Wer nicht alle Felder aufdeckt, bekommt nach 30 Sekunden automatisch alles aufgedeckt.
 */
public final class ScratchMenu extends Menu {

    private static final int[] FIELDS = {10, 11, 12, 14, 15, 16};
    private static final int BUTTON = 22;

    private final WheelManager game;
    private final Player viewer;

    // ---- Zustand des aktuellen Loses ----
    private boolean active = false;
    private int round = 0;
    private Material[] symbols = new Material[6];
    private final boolean[] revealed = new boolean[6];
    private SpinResult result;

    public ScratchMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.scratch.title"));
        this.game = plugin.scratch();
        this.viewer = viewer;
        Messages msg = plugin.messages();

        for (int i = 0; i < FIELDS.length; i++) {
            final int index = i;
            set(FIELDS[i], coveredItem(), (player, click) -> reveal(player, index));
        }
        showBuyButton();
        set(18, backButton(), (player, click) -> new CasinoMenu(plugin, player).open(player));
        set(20, CasinoMenu.accountIcon(plugin), null);
        set(24, CasinoMenu.tableIcon(plugin, game), null);
        set(26, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    private ItemStack coveredItem() {
        Messages msg = plugin.messages();
        return ItemUtil.icon(Material.PAPER, msg.get("gui.scratch.field-name"),
                msg.getList(active ? "gui.scratch.field-lore" : "gui.scratch.field-lore-locked"));
    }

    private void showBuyButton() {
        Messages msg = plugin.messages();
        set(BUTTON, ItemUtil.icon(Material.EMERALD, msg.get("gui.scratch.buy-name"),
                msg.getList("gui.scratch.buy-lore", "price", game.price())), (player, click) -> buy(player));
    }

    private ItemStack symbolItem(Material material) {
        return ItemUtil.icon(material, Component.translatable(material.translationKey())
                .decoration(TextDecoration.ITALIC, false), List.of());
    }

    // ------------------------------------------------------------------
    //  Los kaufen
    // ------------------------------------------------------------------

    private void buy(Player player) {
        UUID uuid = player.getUniqueId();
        if (active || game.isSpinning(uuid)) {
            return;
        }
        SpinResult res = game.trySpin(player); // bezahlt, entscheidet und zahlt sofort
        if (res == null) {
            return;
        }
        result = res;
        active = true;
        round++;
        for (int i = 0; i < 6; i++) {
            revealed[i] = false;
        }
        symbols = layout(res.prize());

        for (int i = 0; i < FIELDS.length; i++) {
            getInventory().setItem(FIELDS[i], coveredItem());
        }
        getInventory().setItem(20, CasinoMenu.accountIcon(plugin));
        getInventory().setItem(26, balanceIcon(player));
        Messages msg = plugin.messages();
        set(BUTTON, ItemUtil.icon(Material.GOLDEN_PICKAXE, msg.get("gui.scratch.all-name"),
                msg.getList("gui.scratch.all-lore")), (p, click) -> revealAll());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);

        // Falls jemand nicht alles aufdeckt: nach 30 Sekunden automatisch fertig
        final int thisRound = round;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active && round == thisRound) {
                revealAll();
            }
        }, 600L);
    }

    /**
     * Legt die sechs Symbole so, dass sie zum Ergebnis passen:
     * Gewinn = genau dreimal das Gewinn-Symbol (sonst höchstens zweimal gleiche), Niete = nie dreimal gleich.
     */
    private Material[] layout(Prize prize) {
        List<Material> pool = new ArrayList<>(List.of(SlotsMenu.SYMBOLS));
        Collections.shuffle(pool, game.random());
        List<Material> list = new ArrayList<>();
        boolean win = prize.jackpot() || prize.amount() > 0;
        if (win) {
            Material winSymbol = prize.jackpot() ? Material.NETHER_STAR : winSymbol(prize);
            for (int i = 0; i < 3; i++) {
                list.add(winSymbol);
            }
            pool.remove(winSymbol);
            for (int i = 0; i < 3; i++) {
                list.add(pool.get(i)); // drei verschiedene andere
            }
        } else {
            for (int i = 0; i < 3; i++) {
                list.add(pool.get(i)); // drei Symbole, jedes zweimal
                list.add(pool.get(i));
            }
        }
        Collections.shuffle(list, game.random());
        return list.toArray(new Material[0]);
    }

    private Material winSymbol(Prize prize) {
        List<WheelManager.Tier> tiers = game.tiers(); // seltenste zuerst
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).amounts().contains(prize.amount())) {
                return SlotsMenu.SYMBOLS[Math.min(tiers.size() - 1 - i, SlotsMenu.SYMBOLS.length - 1)];
            }
        }
        return SlotsMenu.SYMBOLS[0];
    }

    // ------------------------------------------------------------------
    //  Aufdecken
    // ------------------------------------------------------------------

    private void reveal(Player player, int index) {
        if (!active || revealed[index]) {
            return;
        }
        revealed[index] = true;
        getInventory().setItem(FIELDS[index], symbolItem(symbols[index]));
        player.playSound(player.getLocation(), Sound.BLOCK_SAND_BREAK, 0.8f, 1.4f);
        for (boolean r : revealed) {
            if (!r) {
                return;
            }
        }
        finish();
    }

    private void revealAll() {
        if (!active) {
            return;
        }
        for (int i = 0; i < FIELDS.length; i++) {
            revealed[i] = true;
            getInventory().setItem(FIELDS[i], symbolItem(symbols[i]));
        }
        finish();
    }

    /** Alle Felder sind offen: Ergebnis ansagen und ein neues Los anbieten. */
    private void finish() {
        if (!active) {
            return;
        }
        active = false;
        UUID uuid = viewer.getUniqueId();
        game.endSpin(uuid);
        getInventory().setItem(20, CasinoMenu.accountIcon(plugin));
        if (viewer.isOnline()) {
            game.announce(viewer, result);
            getInventory().setItem(26, balanceIcon(viewer));
            Sound sound = result.prize().jackpot() ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                    : (result.payout() > 0 ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS);
            viewer.playSound(viewer.getLocation(), sound, 1f, 1f);
        }
        showBuyButton();
        // Felder bleiben zum Anschauen offen, bis ein neues Los gekauft wird
    }
}
