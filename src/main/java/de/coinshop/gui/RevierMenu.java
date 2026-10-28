package de.coinshop.gui;

import de.coinshop.BlackMarketManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.BlackSell;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.RevierManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ein Revier: bis zu 32 Verkaufs-Slots für den Händler. Item aus der Hand einstellen, zurücknehmen, Slots kaufen. */
public final class RevierMenu extends Menu {

    private final int revierNr;

    @Override
    public boolean acceptsDrops() {
        return true;
    }

    @Override
    public boolean handleDrop(Player player, ItemStack item) {
        plugin.reviere().openListing(player, revierNr, item);
        return false; // erst die Vorschau, das Item bleibt beim Spieler
    }

    @Override
    public void refresh(Player player) {
        new RevierMenu(plugin, player, revierNr).open(player);
    }

    public RevierMenu(CoinShopPlugin plugin, Player viewer, int revier) {
        super(plugin, 6, plugin.messages().get("gui.revier.slots-title",
                "name", plugin.reviere().name(revier)));
        this.revierNr = revier;
        Messages msg = plugin.messages();
        RevierManager reviere = plugin.reviere();
        BlackMarketManager market = plugin.blackMarket();
        UUID uuid = viewer.getUniqueId();

        List<BlackSell> sells = plugin.database().getBlackSells(uuid, revier);
        int owned = reviere.slots(uuid, revier);
        boolean lockedShown = false;
        for (int i = 0; i < RevierManager.MAX_SLOTS; i++) {
            if (i < sells.size()) {
                BlackSell sell = sells.get(i);
                ItemStack shown = sell.item().clone();
                List<Component> lore = new ArrayList<>();
                List<Component> old = shown.lore();
                if (old != null) {
                    lore.addAll(old);
                }
                lore.addAll(msg.getList("gui.sell.entry-lore", "price", sell.price()));
                shown.editMeta(meta -> meta.lore(lore));
                set(i, shown, (p, c) -> {
                    reviere.takeBack(p, sell.id(), revier);
                    new RevierMenu(plugin, p, revier).open(p);
                });
            } else if (i < owned) {
                set(i, ItemUtil.icon(Material.LIME_STAINED_GLASS_PANE, msg.get("gui.sell.free-name"),
                        msg.getList("gui.sell.free-lore", "percent", market.sellPricePercent())), (p, c) -> {
                    reviere.listFromHand(p, revier);
                });
            } else if (!lockedShown) {
                lockedShown = true;
                set(i, ItemUtil.icon(Material.RED_STAINED_GLASS_PANE, msg.get("gui.sell.locked-name"),
                        msg.getList("gui.sell.locked-lore", "price", reviere.nextSlotPrice(uuid, revier))), (p, c) -> {
                    reviere.buySlot(p, revier);
                    new RevierMenu(plugin, p, revier).open(p);
                });
            } else {
                set(i, ItemUtil.icon(Material.GRAY_STAINED_GLASS_PANE, msg.get("gui.sell.locked-name"),
                        List.of()), null);
            }
        }
        set(49, ItemUtil.icon(Material.BOOK, msg.get("gui.sell.info-name"),
                msg.getList("gui.sell.info-lore", "slots", owned, "max", RevierManager.MAX_SLOTS,
                        "chance", market.sellBuyChance())), null);
        set(47, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.revier.rank-name"),
                msg.getList("gui.revier.rank-lore")), (p, c) -> new RevierRankMenu(plugin, p, revier).open(p));
        set(45, backButton(), (p, c) -> new ReviereMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), null);
        fillEmpty();
    }
}
