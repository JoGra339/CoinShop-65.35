package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.DragonFightManager;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.TimelineManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Termine-Menü: Countdown zu Serverstart, Nether und Endkampf (öffnet sich auch am gesperrten Nether-Portal). */
public final class TimelineMenu extends Menu {

    public TimelineMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.timeline.title"));
        Messages msg = plugin.messages();
        TimelineManager time = plugin.timeline();
        DragonFightManager fight = plugin.dragonFight();

        long launch = time.millisUntilLaunch();
        set(10, ItemUtil.icon(Material.GRASS_BLOCK, msg.get("gui.timeline.launch-name"),
                msg.getList(launch > 0 ? "gui.timeline.launch-lore" : "gui.timeline.done-lore",
                        "date", time.launchText(), "left", ItemUtil.formatDuration(launch))), null);

        long nether = time.millisUntilNether();
        set(13, ItemUtil.icon(nether > 0 ? Material.OBSIDIAN : Material.NETHERRACK, msg.get("gui.timeline.nether-name"),
                msg.getList(nether > 0 ? "gui.timeline.nether-lore" : "gui.timeline.done-lore",
                        "date", time.netherText(), "left", ItemUtil.formatDuration(nether))), null);

        long end = fight.millisUntilStart();
        set(16, ItemUtil.icon(Material.DRAGON_HEAD, msg.get("gui.timeline.end-name"),
                msg.getList(end > 0 && end != Long.MAX_VALUE ? "gui.timeline.end-lore" : "gui.timeline.done-lore",
                        "date", fight.startText(),
                        "left", end == Long.MAX_VALUE ? "unbekannt" : ItemUtil.formatDuration(end))), null);

        set(22, ItemUtil.icon(Material.OAK_DOOR, msg.get("gui.close-name"), List.of()),
                (player, click) -> player.closeInventory());
        fillEmpty();
    }
}
