package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.DragonFightManager;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Das Anmelde-Menü am End-Portal für den Endkampf. */
public final class EndFightMenu extends Menu {

    public EndFightMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.endfight.title"));
        Messages msg = plugin.messages();
        DragonFightManager fight = plugin.dragonFight();
        boolean signed = fight.isSignedUp(viewer.getUniqueId());
        long left = fight.millisUntilStart();

        set(11, ItemUtil.icon(Material.CLOCK, msg.get("gui.endfight.info-name"),
                msg.getList("gui.endfight.info-lore", "date", fight.startText(),
                        "left", left == Long.MAX_VALUE ? "unbekannt" : ItemUtil.formatDuration(left))), null);

        if (signed) {
            set(13, ItemUtil.icon(Material.BARRIER, msg.get("gui.endfight.unsign-name"),
                            msg.getList("gui.endfight.unsign-lore")),
                    (player, click) -> {
                        fight.signOff(player);
                        new EndFightMenu(plugin, player).open(player);
                    });
        } else {
            set(13, ItemUtil.icon(Material.DRAGON_HEAD, msg.get("gui.endfight.sign-name"),
                            msg.getList("gui.endfight.sign-lore")),
                    (player, click) -> {
                        fight.signUp(player);
                        new EndFightMenu(plugin, player).open(player);
                    });
        }

        set(15, ItemUtil.icon(Material.ENDER_EYE, msg.get("gui.endfight.count-name"),
                msg.getList("gui.endfight.count-lore", "count", fight.signupCount())), null);

        set(22, ItemUtil.icon(Material.OAK_DOOR, msg.get("gui.close-name"), List.of()),
                (player, click) -> player.closeInventory());
        fillEmpty();
    }
}
