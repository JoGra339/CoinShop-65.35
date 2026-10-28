package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Wähle einen Spieler für ein Begleiter-Duell; der Einsatz lässt sich durchschalten. */
public final class DuelChallengeMenu extends Menu {

    private static final int PAGE = 36;
    private static final long[] STAKES = {0, 100, 500, 1000, 2500, 5000};
    private static final Map<UUID, Integer> STAKE_INDEX = new HashMap<>();

    public DuelChallengeMenu(CoinShopPlugin plugin, Player viewer, int page) {
        super(plugin, 6, Component.text("Spieler herausfordern"));
        ExpeditionManager ex = plugin.expeditions();
        UUID me = viewer.getUniqueId();
        long maxStake = Math.max(0, plugin.getConfig().getInt("expedition.duel.max-stake", 5000));
        int si = Math.min(STAKE_INDEX.getOrDefault(me, 0), STAKES.length - 1);
        long stake = Math.min(STAKES[si], maxStake);

        List<Player> others = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.getUniqueId().equals(me)) {
                others.add(o);
            }
        }
        others.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pages = Math.max(1, (others.size() + PAGE - 1) / PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        for (int i = 0; i < PAGE && pg * PAGE + i < others.size(); i++) {
            Player o = others.get(pg * PAGE + i);
            UUID id = o.getUniqueId();
            boolean ready = ex.hasLicense(o) && !ex.isIn(id) && !ex.duel().deck(id).isEmpty();
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line(ready ? "Bereit zum Duell" : "Gerade nicht bereit (Lizenz, Expedition oder kein Gefolge)",
                    ready ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(ExpeditionMenu.line("Gruppenstärke: " + Math.round(ex.duel().strength(id)) + " (deine: " + Math.round(ex.duel().strength(me)) + ")", NamedTextColor.GRAY));
            lore.add(ExpeditionMenu.line("Einsatz: " + stake + " Coins", NamedTextColor.GOLD));
            if (ready) {
                lore.add(ExpeditionMenu.line("Klicken: herausfordern", NamedTextColor.GREEN));
            }
            ItemStack head = ItemUtil.icon(Material.PLAYER_HEAD, ExpeditionMenu.line(o.getName(), NamedTextColor.GOLD), lore);
            if (head.getItemMeta() instanceof SkullMeta sm) {
                sm.setOwningPlayer(o);
                head.setItemMeta(sm);
            }
            final long useStake = stake;
            set(i, head, ready ? (p, c) -> {
                p.closeInventory();
                ex.duel().challenge(p, o, useStake);
            } : null);
        }
        set(45, backButton(), (p, c) -> new DuelMenu(plugin, p).open(p));
        if (pg > 0) {
            set(47, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Vorherige Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new DuelChallengeMenu(plugin, p, pg - 1).open(p));
        }
        set(49, ItemUtil.icon(Material.GOLD_NUGGET, ExpeditionMenu.line("Einsatz: " + stake + " Coins", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Der Verlierer zahlt, der Sieger bekommt ihn", NamedTextColor.GRAY),
                ExpeditionMenu.line("(abzüglich Gebühr). Höchstens " + maxStake + " Coins.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: Einsatz ändern", NamedTextColor.GREEN))), (p, c) -> {
            STAKE_INDEX.put(me, (si + 1) % STAKES.length);
            new DuelChallengeMenu(plugin, p, pg).open(p);
        });
        if (pg < pages - 1) {
            set(51, ItemUtil.icon(Material.ARROW, ExpeditionMenu.line("Nächste Seite", NamedTextColor.YELLOW), List.of()),
                    (p, c) -> new DuelChallengeMenu(plugin, p, pg + 1).open(p));
        }
        if (others.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Kein anderer Spieler online", NamedTextColor.RED), List.of()), null);
        }
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
