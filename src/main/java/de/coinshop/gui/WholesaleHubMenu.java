package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Das Hauptmenü des Großhandels (nur mit der Großhandellizenz). */
public final class WholesaleHubMenu extends Menu {

    private final CoinShopPlugin pl;

    @Override
    public void open(Player player) {
        if (!pl.wholesale().allowed(player)) {
            return; // ohne Grosshandellizenz kommt man nicht hinein
        }
        super.open(player);
    }

    public WholesaleHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.whhub.title"));
        this.pl = plugin;
        Messages msg = plugin.messages();
        java.util.List<net.kyori.adventure.text.Component> none = java.util.List.of();
        set(11, ItemUtil.icon(Material.BARREL, msg.get("gui.whhub.orders"), none),
                (p, c) -> new WholesaleMenu(plugin, p).open(p));
        set(13, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.whhub.treaties"), none),
                (p, c) -> new TreatyMenu(plugin, p).open(p));
        set(15, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.whhub.rank"), none),
                (p, c) -> new WholesaleRankMenu(plugin, p, -1).open(p));
        set(20, ItemUtil.icon(Material.OAK_SIGN, msg.get("gui.whhub.company"), none),
                (p, c) -> {
                    if (plugin.companies().employerOf(p.getUniqueId()) != null) {
                        new StaffHomeMenu(plugin, p).open(p);
                    } else {
                        new CompanyMenu(plugin, p).open(p);
                    }
                });
        set(22, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.whhub.company-rank"), none),
                (p, c) -> new CompanyRankMenu(plugin, p).open(p));
        set(24, ItemUtil.icon(Material.BOOKSHELF, msg.get("gui.whhub.companies"), none),
                (p, c) -> new CompanyListMenu(plugin, p, 0).open(p));
        if (plugin.expeditions().enabled()) {
            set(29, ItemUtil.icon(Material.LEAD, msg.get("gui.whhub.companions"), none),
                    (p, c) -> new ExpeditionCopyMenu(plugin, p, ExpeditionCopyMenu.Mode.WHOLESALE).open(p));
        }
        set(27, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        set(31, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
