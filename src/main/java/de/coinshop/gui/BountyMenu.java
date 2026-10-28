package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.Bounty;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/** Alle ausgesetzten Kopfgelder, dazu der Knopf für ein neues. Eigene Kopfgelder lassen sich zurückziehen. */
public final class BountyMenu extends Menu {

    private static final int MAX_SHOWN = 36;

    public BountyMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.bounty.title"));
        Messages msg = plugin.messages();
        List<Bounty> bounties = plugin.database().getBounties();
        UUID me = viewer.getUniqueId();

        for (int i = 0; i < bounties.size() && i < MAX_SHOWN; i++) {
            Bounty b = bounties.get(i);
            boolean own = b.placer().equals(me);
            ItemStack head = head(b.target(), msg.get("gui.bounty.entry-name", "target", b.targetName()),
                    msg.getList("gui.bounty.entry-lore", "amount", b.amount(),
                            "placer", own ? b.placerName() : "???"));
            if (own) {
                List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(head.lore());
                lore.add(msg.get("gui.bounty.entry-own"));
                head.editMeta(meta -> meta.lore(lore));
                set(i, head, (p, c) -> {
                    plugin.blackMarket().withdrawBounty(p, b.id());
                    new BountyMenu(plugin, p).open(p);
                });
            } else {
                set(i, head, null);
            }
        }
        if (bounties.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.bounty.empty"), List.of()), null);
        }

        set(49, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.bounty.new"),
                        msg.getList("gui.bounty.new-lore", "min", plugin.blackMarket().bountyMin(),
                                "percent", plugin.blackMarket().bountyFeePercent())),
                (p, c) -> new BountyTargetMenu(plugin, p).open(p));
        boolean notify = plugin.blackMarket().bountyNotify(me);
        set(47, ItemUtil.icon(notify ? Material.LIME_DYE : Material.GRAY_DYE,
                msg.get(notify ? "gui.bounty.notify-on" : "gui.bounty.notify-off"),
                msg.getList("gui.bounty.notify-lore")), (p, c) -> {
            plugin.blackMarket().toggleBountyNotify(p.getUniqueId());
            new BountyMenu(plugin, p).open(p);
        });
        boolean optOut = plugin.blackMarket().bountyOptOut(me);
        set(51, ItemUtil.icon(optOut ? Material.RED_DYE : Material.SHIELD,
                msg.get(optOut ? "gui.bounty.optout-on" : "gui.bounty.optout-off"),
                msg.getList(optOut ? "gui.bounty.optout-on-lore" : "gui.bounty.optout-off-lore")), (p, c) -> {
            plugin.blackMarket().toggleBountyOptOut(p);
            new BountyMenu(plugin, p).open(p);
        });
        set(48, ItemUtil.icon(Material.ZOMBIE_HEAD, net.kyori.adventure.text.Component.text("Mobköpfe verkaufen",
                        net.kyori.adventure.text.format.NamedTextColor.GOLD).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                List.of(ExpeditionMenu.line("Zombie, Skelett, Creeper, Piglin,", net.kyori.adventure.text.format.NamedTextColor.GRAY),
                        ExpeditionMenu.line("Wither-Skelett und Drachenkopf.", net.kyori.adventure.text.format.NamedTextColor.GRAY),
                        ExpeditionMenu.line("Klicken: öffnen", net.kyori.adventure.text.format.NamedTextColor.GREEN))),
                (p, c) -> new MobHeadMenu(plugin, p).open(p));
        set(45, backButton(), (p, c) -> new BlackMarketHubMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), null);
        fillEmpty();
    }

    static ItemStack head(UUID owner, net.kyori.adventure.text.Component name, List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = ItemUtil.icon(Material.PLAYER_HEAD, name, lore);
        OfflinePlayer off = Bukkit.getOfflinePlayer(owner);
        item.editMeta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(off);
            }
        });
        return item;
    }
}
