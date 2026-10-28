package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.SecretAchievements;
import de.coinshop.SecretAchievements.Secret;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** Die geheimen Erfolge. Man sieht nur die, die man schon errungen hat - der Rest bleibt "???". */
public final class SecretMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public SecretMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.secret.title"));
        Messages msg = plugin.messages();
        SecretAchievements secrets = plugin.secrets();
        Map<String, Long> unlocked = secrets.unlocked(viewer.getUniqueId());
        Secret[] all = Secret.values();
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm");

        int found = 0;
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            Secret secret = all[i];
            Long at = unlocked.get(secret.id());
            if (at == null) {
                // Noch nicht entdeckt: nichts verraten
                set(SLOTS[i], ItemUtil.icon(Material.GRAY_DYE, msg.get("gui.secret.locked-name"),
                        msg.getList("gui.secret.locked-lore")), null);
                continue;
            }
            found++;
            List<Component> lore = new ArrayList<>();
            lore.add(secrets.description(secret));
            lore.add(Component.empty());
            lore.addAll(msg.getList("gui.secret.unlocked-lore", "tier", secrets.tier(secret),
                    "coins", secrets.reward(secret), "date", format.format(new Date(at))));
            set(SLOTS[i], ItemUtil.icon(secret.icon(), msg.get("gui.secret.unlocked-name",
                    "name", secrets.name(secret)), lore), null);
        }

        set(4, ItemUtil.icon(Material.ENDER_EYE, msg.get("gui.secret.summary-name"),
                msg.getList("gui.secret.summary-lore", "found", found, "total", all.length)), null);
        set(47, balanceIcon(viewer), incomeClick());
        set(49, backButton(), (player, click) -> new MainMenu(plugin).open(player));

        fillEmpty();
    }
}
