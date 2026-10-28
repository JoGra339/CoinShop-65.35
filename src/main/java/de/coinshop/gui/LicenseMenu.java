package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.LicenseManager;
import de.coinshop.LicenseManager.License;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Das Lizenz-Menü: 20 Lizenzen mit Stufen: kaufen, behalten und verbessern. */
public final class LicenseMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};

    public LicenseMenu(CoinShopPlugin plugin, Player viewer) {
        this(plugin, viewer, false);
    }

    /**
     * black = false: die normalen Lizenzen (dazu der Schmuggler zum Kaufen).
     * black = true: die Schwarzmarkt-Verbindungen (nur aus dem Schwarzmarkt erreichbar), hier wird auch der Schmuggler verbessert.
     */
    public LicenseMenu(CoinShopPlugin plugin, Player viewer, boolean black) {
        super(plugin, 6, plugin.messages().get(black ? "gui.license.black-title" : "gui.license.title"));
        Messages msg = plugin.messages();
        LicenseManager manager = plugin.licenses();
        UUID uuid = viewer.getUniqueId();

        List<License> all = new ArrayList<>();
        for (License l : manager.all()) {
            boolean isBlack = LicenseManager.BLACK_IDS.contains(l.id());
            if (black ? isBlack : (!isBlack || l.id().equals("schmuggler"))) {
                all.add(l);
            }
        }
        for (int i = 0; i < all.size() && i < SLOTS.length; i++) {
            License l = all.get(i);
            int level = manager.level(uuid, l.id());
            int max = manager.maxLevel(l.id());
            boolean owned = level > 0;
            List<LicenseManager.Level> levels = manager.levelsOf(l.id());
            double shownValue = owned ? levels.get(Math.min(level, levels.size()) - 1).value() : l.value();
            List<Component> lore = new ArrayList<>(msg.getList("gui.license.desc." + l.id(),
                    "value", Math.round(shownValue)));
            lore.add(Component.empty());
            // Den Schmuggler verbessert man nur im Schwarzmarkt
            boolean upgradeHere = black || !l.id().equals("schmuggler");
            boolean canUpgrade = owned && level < levels.size() && upgradeHere;
            if (owned) {
                lore.add(msg.get("gui.license.owned-line"));
                if (max > 1) {
                    lore.add(msg.get("gui.license.level-line", "level", level, "max", max));
                }
                if (l.id().equals("freispiel")) {
                    lore.add(msg.get(manager.freePlayAvailable(uuid)
                            ? "gui.license.freeplay-ready" : "gui.license.freeplay-used"));
                }
                if (canUpgrade) {
                    LicenseManager.Level next = levels.get(level);
                    lore.add(msg.get("gui.license.next-line", "next", Math.round(next.value()), "price", plugin.licenses().priceOf(l.id(), next.price())));
                    lore.add(msg.get("gui.license.upgrade-line"));
                } else if (!upgradeHere && level < levels.size()) {
                    lore.add(msg.get("gui.license.schmuggler-hint"));
                } else if (max > 1) {
                    lore.add(msg.get("gui.license.maxed-line"));
                }
            } else {
                lore.add(msg.get("gui.license.price-line", "price", plugin.licenses().priceOf(l.id(), l.price())));
                lore.add(msg.get("gui.license.click-line"));
            }
            ItemStack item = ItemUtil.icon(l.icon(), msg.get(owned ? "gui.license.name-owned" : "gui.license.name",
                    "name", l.name()), lore);
            if (owned) {
                item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            set(SLOTS[i], item, owned && !canUpgrade ? null : (player, click) -> {
                if (manager.has(player.getUniqueId(), l.id())) {
                    manager.upgrade(player, l.id());
                } else {
                    manager.buy(player, l.id());
                }
                new LicenseMenu(plugin, player, black).open(player);
            });
        }

        set(47, balanceIcon(viewer), incomeClick());
        set(49, backButton(), (player, click) -> {
            if (black) {
                new BlackMarketHubMenu(plugin, player).open(player);
            } else {
                new MainMenu(plugin).open(player);
            }
        });
        fillEmpty();
    }
}
