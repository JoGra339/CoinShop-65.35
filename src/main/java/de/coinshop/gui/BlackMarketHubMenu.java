package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Das Hauptmenü des Schwarzmarkts (nur mit der Lizenz Schmuggler). */
public final class BlackMarketHubMenu extends Menu {

    private final CoinShopPlugin pl;

    @Override
    public void open(Player player) {
        if (!pl.blackMarket().unlocked(player)) {
            pl.messages().send(player, "bm-locked");
            return; // ohne Schmuggler-Lizenz kommt man nicht hinein
        }
        super.open(player);
    }

    public BlackMarketHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.bmhub.title"));
        this.pl = plugin;
        Messages msg = plugin.messages();
        java.util.List<net.kyori.adventure.text.Component> none = java.util.List.of();

        set(10, ItemUtil.icon(Material.WITHER_SKELETON_SKULL, msg.get("gui.bmhub.shop"), none),
                (p, c) -> new BlackMarketMenu(plugin, p).open(p));
        set(12, ItemUtil.icon(Material.GOLD_NUGGET, msg.get("gui.bmhub.auction"), none),
                (p, c) -> new AuctionMenu(plugin, p).open(p));
        set(14, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.bmhub.exchange"), none),
                (p, c) -> new BackyardMenu(plugin, p).open(p));
        set(16, ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.bmhub.revier"), none),
                (p, c) -> new ReviereMenu(plugin, p).open(p));
        set(19, ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.bmhub.bounty"), none),
                (p, c) -> new BountyMenu(plugin, p).open(p));
        set(21, ItemUtil.icon(Material.WRITTEN_BOOK, msg.get("gui.bmhub.members"), none),
                (p, c) -> new BlackMarketMembersMenu(plugin, p).open(p));
        if (plugin.bets().enabled()) {
            set(23, ItemUtil.icon(Material.SPYGLASS, msg.get("gui.bmhub.illegal"), none),
                    (p, c) -> new IllegalBetsMenu(plugin, p).open(p));
        }
        set(25, ItemUtil.icon(Material.ENCHANTED_BOOK, msg.get("gui.bmhub.licenses"), none),
                (p, c) -> new LicenseMenu(plugin, p, true).open(p));
        if (plugin.drugs().enabled()) {
            set(31, ItemUtil.icon(Material.BREWING_STAND, msg.get("gui.bmhub.drugs"), none),
                    (p, c) -> {
                        if (plugin.licenses().has(p.getUniqueId(), "drogenhandel")) {
                            new DrugHubMenu(plugin, p).open(p);
                        } else {
                            plugin.messages().send(p, "drug-license");
                        }
                    });
        }
        if (plugin.creatureAuction().enabled()) {
            set(29, ItemUtil.icon(Material.LEAD, msg.get("gui.bmhub.beasts"), none),
                    (p, c) -> new CreatureAuctionMenu(plugin, p).open(p));
        }
        if (plugin.expeditions().enabled()) {
            set(33, ItemUtil.icon(Material.GOLDEN_HELMET, net.kyori.adventure.text.Component.text("Söldner-Markt")
                            .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                    java.util.List.of(ExpeditionMenu.line("Miete Begleiter anderer Spieler bis zum Shopwechsel", net.kyori.adventure.text.format.NamedTextColor.GRAY),
                            ExpeditionMenu.line("oder biete eigene an. Braucht die Expeditionslizenz.", net.kyori.adventure.text.format.NamedTextColor.GRAY),
                            ExpeditionMenu.line("Klicken: öffnen", net.kyori.adventure.text.format.NamedTextColor.GREEN))),
                    (p, c) -> {
                        if (plugin.expeditions().hasLicense(p)) {
                            new MercMenu(plugin, p, 0).open(p);
                        } else {
                            plugin.messages().send(p, "ex-locked");
                        }
                    });
        }
        set(27, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        set(35, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
