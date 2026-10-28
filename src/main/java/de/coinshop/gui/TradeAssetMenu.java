package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CryptoManager;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.TradeManager;
import de.coinshop.TradeManager.Offer;
import de.coinshop.TradeManager.Session;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Börsenwerte ins Handelsangebot legen: Aktien, Krypto und Habibi-Krypto (Linksklick: Menge eingeben, Rechtsklick: entfernen). */
public final class TradeAssetMenu extends Menu {

    public TradeAssetMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.trade.asset-title"));
        Messages msg = plugin.messages();
        TradeManager trade = plugin.trade();
        UUID me = viewer.getUniqueId();
        Session session = trade.sessionOf(me);
        if (session == null) {
            fillEmpty();
            return;
        }
        Offer mine = session.offerOf(me);
        asset(10, Material.GOLD_NUGGET, "aktie", "gui.trade.asset-shares", trade, me, mine, false);
        asset(12, Material.AMETHYST_SHARD, "krypto", "gui.trade.asset-krypto", trade, me, mine, true);
        if (trade.habibiAllowed(me) && trade.habibiAllowed(session.other(me))) {
            asset(14, Material.ECHO_SHARD, "habibi", "gui.trade.asset-habibi", trade, me, mine, true);
        }
        set(18, backButton(), (p, c) -> trade.show(p));
        fillEmpty();
    }

    private void asset(int slot, Material icon, String kind, String key, TradeManager trade, UUID me, Offer mine,
                       boolean crypto) {
        Messages msg = plugin.messages();
        long held = trade.held(me, kind);
        long offered = trade.offered(mine, kind);
        String heldText = crypto ? CryptoManager.fmt(held) : String.valueOf(held);
        String offeredText = crypto ? CryptoManager.fmt(offered) : String.valueOf(offered);
        set(slot, ItemUtil.icon(icon, msg.get(key + "-name"),
                        msg.getList("gui.trade.asset-lore", "held", heldText, "offered", offeredText)),
                (p, c) -> {
                    if (c.isRightClick()) {
                        trade.setAsset(p, kind, 0);
                        trade.show(p);
                    } else {
                        trade.askAsset(p, kind);
                    }
                });
    }
}
