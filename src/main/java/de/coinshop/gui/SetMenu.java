package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Ownership;
import de.coinshop.InvestmentManager;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Die Items eines Sets mit Besitzer.
 *  - freies Item        -> Klick kauft es
 *  - eigenes Item       -> Klick startet die Übertragung an einen anderen Spieler
 *  - Item eines anderen -> nur Anzeige (wem es gehört)
 */
public final class SetMenu extends Menu {

    public SetMenu(CoinShopPlugin plugin, Player viewer, SetDef set) {
        super(plugin, 3, plugin.messages().get("gui.invest.set-title", "set", set.name()));
        Messages msg = plugin.messages();
        InvestmentManager invest = plugin.investments();
        UUID uuid = viewer.getUniqueId();
        Map<String, Ownership> owners = invest.ownershipMap();

        int count = Math.min(set.items().size(), 9);
        int start = 9 + (9 - count) / 2; // mittig in der mittleren Reihe

        for (int n = 0; n < count; n++) {
            final int itemIndex = n;
            Material material = set.items().get(n);
            Ownership owner = owners.get(InvestmentManager.itemId(set, n));

            List<Component> lore;
            Menu.Action action;
            if (owner == null) {
                lore = msg.getList("gui.invest.item-lore-free", "set", set.name(), "price", invest.itemPrice(set));
                action = (player, click) -> {
                    invest.buy(player, set, itemIndex);
                    new SetMenu(plugin, player, set).open(player);
                };
            } else if (owner.owner().equals(uuid)) {
                lore = msg.getList("gui.invest.item-lore-mine", "set", set.name());
                action = (player, click) -> invest.startTransfer(player, set, itemIndex);
            } else {
                lore = msg.getList("gui.invest.item-lore-other", "set", set.name(), "owner", owner.ownerName());
                action = null;
            }

            // Der Name des Items kommt automatisch vom Spiel (in der Sprache des Spielers)
            ItemStack icon = new ItemStack(material);
            List<Component> finalLore = lore;
            icon.editMeta(meta -> meta.lore(finalLore));
            set(start + n, icon, action);
        }

        set(18, backButton(), (player, click) -> new InvestMenu(plugin, player).open(player));
        set(26, balanceIcon(viewer), incomeClick());

        fillEmpty();
    }
}
