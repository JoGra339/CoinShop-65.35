package de.coinshop.gui;

import de.coinshop.CollectionManager;
import de.coinshop.CollectionManager.Collection;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Alle Kollektionen mit deinem Fortschritt. */
public final class CollectionsMenu extends Menu {

    private static final int[] SLOTS = {1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public CollectionsMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.collection.title"));
        Messages msg = plugin.messages();
        CollectionManager manager = plugin.collections();
        UUID uuid = viewer.getUniqueId();
        manager.scan(viewer); // Stand auffrischen
        Set<String> known = plugin.database().getCollected(uuid);
        Set<String> done = plugin.database().getDoneCollections(uuid);

        int i = 0;
        for (Collection c : manager.all()) {
            if (i >= SLOTS.length) {
                break;
            }
            int have = manager.progress(c, known);
            List<Component> lore = new ArrayList<>(msg.getList("gui.collection.lore",
                    "have", have, "total", c.entries().size(), "coins", c.reward()));
            lore.add(done.contains(c.id()) ? msg.get("gui.collection.done") : msg.get("gui.collection.open"));
            set(SLOTS[i++], ItemUtil.icon(c.icon(), msg.get("gui.collection.name", "name", c.name()), lore),
                    (player, click) -> new CollectionDetailMenu(plugin, player, c).open(player));
        }

        set(47, balanceIcon(viewer), incomeClick());
        set(49, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        fillEmpty();
    }
}
