package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.TradeManager;
import de.coinshop.TradeManager.Offer;
import de.coinshop.TradeManager.Session;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Das Handelsfenster: links dein Angebot, rechts das Angebot des anderen, in der Mitte die Zustimmung. */
public final class TradeMenu extends Menu {

    private static final int[] MINE = {9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] THEIRS = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};

    public TradeMenu(CoinShopPlugin plugin, Player viewer, Session session) {
        super(plugin, 6, plugin.messages().get("gui.trade.title"));
        Messages msg = plugin.messages();
        TradeManager trade = plugin.trade();
        UUID me = viewer.getUniqueId();
        UUID otherId = session.other(me);
        String otherName = nameOf(otherId);
        Offer mine = session.offerOf(me);
        Offer theirs = session.offerOf(otherId);

        set(0, ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.trade.your-offer"), List.of()), null);
        set(8, ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.trade.their-offer", "player", otherName), List.of()), null);

        for (int i = 0; i < MINE.length; i++) {
            if (i < mine.items.size()) {
                String id = new ArrayList<>(mine.items).get(i);
                set(MINE[i], itemIcon(trade, id, msg.get("gui.trade.click-remove")), (p, c) -> trade.removeItem(p, id));
            }
        }
        for (int i = 0; i < THEIRS.length; i++) {
            if (i < theirs.items.size()) {
                String id = new ArrayList<>(theirs.items).get(i);
                set(THEIRS[i], itemIcon(trade, id, null), null);
            }
        }

        set(36, ItemUtil.icon(Material.RED_STAINED_GLASS_PANE, msg.get("gui.trade.minus", "amount", 1000), List.of()),
                (p, c) -> trade.addCoins(p, -1000));
        set(37, ItemUtil.icon(Material.ORANGE_STAINED_GLASS_PANE, msg.get("gui.trade.minus", "amount", 100), List.of()),
                (p, c) -> trade.addCoins(p, -100));
        set(38, ItemUtil.icon(Material.LIME_STAINED_GLASS_PANE, msg.get("gui.trade.plus", "amount", 100), List.of()),
                (p, c) -> trade.addCoins(p, 100));
        set(39, ItemUtil.icon(Material.GREEN_STAINED_GLASS_PANE, msg.get("gui.trade.plus", "amount", 1000), List.of()),
                (p, c) -> trade.addCoins(p, 1000));

        set(45, ItemUtil.icon(Material.CHEST, msg.get("gui.trade.pick"), msg.getList("gui.trade.pick-lore", "max", TradeManager.MAX_ITEMS)),
                (p, c) -> trade.openMenu(p, new TradePickMenu(plugin, p, 0)));
        set(46, ItemUtil.icon(Material.SUNFLOWER, msg.get("gui.trade.coins", "coins", mine.coins),
                msg.getList("gui.trade.coins-lore", "balance", plugin.database().getCoins(me))), (p, c) -> trade.askCoins(p));
        set(47, ItemUtil.icon(Material.BARRIER, msg.get("gui.trade.clear"), List.of()), (p, c) -> trade.clearOffer(p));

        set(42, ItemUtil.icon(Material.SUNFLOWER, msg.get("gui.trade.their-coins", "coins", theirs.coins), List.of()), null);
        set(40, ItemUtil.icon(Material.AMETHYST_SHARD, msg.get("gui.trade.assets"),
                        msg.getList("gui.trade.assets-lore", "offer", trade.assetText(mine))),
                (p, c) -> trade.openMenu(p, new TradeAssetMenu(plugin, p)));
        set(43, ItemUtil.icon(Material.AMETHYST_SHARD, msg.get("gui.trade.their-assets"),
                msg.getList("gui.trade.their-assets-lore", "offer", trade.assetText(theirs))), null);

        set(4, ItemUtil.icon(Material.REDSTONE_BLOCK, msg.get("gui.trade.cancel"), List.of()),
                (p, c) -> trade.cancel(p.getUniqueId(), "trade-cancelled-by"));

        boolean theirConfirm = session.confirmed(otherId);
        set(22, ItemUtil.icon(theirConfirm ? Material.LIME_DYE : Material.GRAY_DYE,
                msg.get(theirConfirm ? "gui.trade.partner-yes" : "gui.trade.partner-no", "player", otherName), List.of()), null);

        boolean myConfirm = session.confirmed(me);
        set(49, ItemUtil.icon(myConfirm ? Material.LIME_CONCRETE : Material.EMERALD_BLOCK,
                msg.get(myConfirm ? "gui.trade.confirmed" : "gui.trade.confirm"),
                msg.getList("gui.trade.confirm-lore")), (p, c) -> trade.toggleConfirm(p));

        fillEmpty();
    }

    private static String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : "?";
    }

    private ItemStack itemIcon(TradeManager trade, String itemId, Component extra) {
        SetDef set = trade.setOf(itemId);
        if (set == null) {
            return ItemUtil.icon(Material.BARRIER, Component.text(itemId), List.of());
        }
        Material material = set.items().get(Math.min(trade.indexOf(itemId), set.items().size() - 1));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.messages().get("gui.trade.from-set", "set", set.name()));
        if (extra != null) {
            lore.add(extra);
        }
        return ItemUtil.icon(material, ItemUtil.name(material), lore);
    }
}
