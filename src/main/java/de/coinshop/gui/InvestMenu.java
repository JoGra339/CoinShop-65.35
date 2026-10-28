package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Ownership;
import de.coinshop.InvestmentManager;
import de.coinshop.InvestmentManager.SetDef;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Übersicht aller Item-Sets (erreichbar über das Investitions-Menü). Ein Klick öffnet die Items des Sets. */
public final class InvestMenu extends Menu {

    public InvestMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.invest.title"));
        Messages msg = plugin.messages();
        InvestmentManager invest = plugin.investments();
        UUID uuid = viewer.getUniqueId();
        Map<String, Ownership> owners = invest.ownershipMap();

        List<SetDef> sets = invest.sets();
        for (int i = 0; i < sets.size() && i < 27; i++) {
            SetDef set = sets.get(i);
            int total = set.items().size();
            int owned = invest.ownedCount(set, uuid, owners);

            List<Component> lore = new ArrayList<>(msg.getList("gui.invest.set-lore-head",
                    "price", invest.itemPrice(set),
                    "income", invest.setIncome(set, total),
                    "owned", owned, "total", total,
                    "yours", invest.setIncome(set, owned)));

            for (int n = 0; n < total; n++) {
                Ownership o = owners.get(InvestmentManager.itemId(set, n));
                Component itemName = ItemUtil.name(set.items().get(n));
                if (o == null) {
                    lore.add(msg.get("gui.invest.item-line-free", "item", itemName));
                } else if (o.owner().equals(uuid)) {
                    lore.add(msg.get("gui.invest.item-line-mine", "item", itemName, "owner", o.ownerName()));
                } else {
                    lore.add(msg.get("gui.invest.item-line-owned", "item", itemName, "owner", o.ownerName()));
                }
            }
            lore.addAll(msg.getList("gui.invest.set-lore-foot"));

            ItemStack icon = ItemUtil.icon(set.items().get(0), msg.get("gui.invest.set-name", "set", set.name()), lore);
            set(i, icon, (player, click) -> new SetMenu(plugin, player, set).open(player));
        }

        // Untere Reihe: Kontostand | Zurück (zum Investitions-Menü)
        set(29, balanceIcon(viewer), incomeClick());
        set(31, backButton(), (player, click) -> new InvestHubMenu(plugin, player).open(player));

        fillEmpty();
    }
}
