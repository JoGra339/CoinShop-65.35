package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Claim;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** "Abholung": Items aus abgelaufenen oder abgebrochenen Angeboten. Klick = ins Inventar. */
public final class ClaimsMenu extends Menu {

    public ClaimsMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.claims.title"));
        Messages msg = plugin.messages();

        List<Claim> claims = plugin.database().getClaims(viewer.getUniqueId());
        if (claims.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, msg.get("gui.claims.empty-name"), List.of()), null);
        }

        for (int i = 0; i < claims.size() && i < 36; i++) {
            Claim claim = claims.get(i);
            String reasonText = claim.reason().equals("expired")
                    ? plainReason(msg, "gui.claims.reason-expired")
                    : plainReason(msg, "gui.claims.reason-cancelled");
            ItemStack display = ItemUtil.withExtraLore(claim.item(),
                    msg.getList("gui.claims.lore", "reason", reasonText));

            set(i, display, (player, click) -> {
                plugin.playerShop().claim(player, claim.id());
                new ClaimsMenu(plugin, player).open(player);
            });
        }

        set(40, backButton(), (player, click) -> new MyListingsMenu(plugin, player).open(player));

        fillEmpty();
    }

    /** Holt einen Grund-Text als einfachen String aus messages.yml. */
    private static String plainReason(Messages msg, String key) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.get(key));
    }
}
