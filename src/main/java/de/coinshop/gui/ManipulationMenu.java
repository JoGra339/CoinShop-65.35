package de.coinshop.gui;

import de.coinshop.BetManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Wettmanipulation (Schwarzmarkt): Spieler wählen, dann einen Tod mehr oder weniger im Wochenergebnis kaufen. */
public final class ManipulationMenu extends Menu {

    public ManipulationMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.manip.target-title"));
        Messages msg = plugin.messages();
        BetManager bets = plugin.bets();
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(viewer) || slot >= 45 || bets.optedOut(online.getUniqueId())) {
                continue;
            }
            UUID id = online.getUniqueId();
            String name = online.getName();
            set(slot++, BountyMenu.head(id, msg.get("gui.bets.target-name", "player", name),
                            msg.getList("gui.manip.target-lore", "now", bets.deathsThisWeek(id))),
                    (p, c) -> new Choice(plugin, p, id, name).open(p));
        }
        set(49, backButton(), (p, c) -> new IllegalBetsMenu(plugin, p).open(p));
        fillEmpty();
    }

    /** Ein Tod mehr oder ein Tod weniger. */
    public static final class Choice extends Menu {
        public Choice(CoinShopPlugin plugin, Player viewer, UUID target, String targetName) {
            super(plugin, 3, plugin.messages().get("gui.manip.title", "player", targetName));
            Messages msg = plugin.messages();
            BetManager bets = plugin.bets();
            UUID uuid = viewer.getUniqueId();
            long price = plugin.blackMarket().priceFor(uuid, bets.manipCost());
            Object[] pairs = {"price", price, "chance", bets.manipChance(uuid), "caught", bets.manipCaughtPercent(),
                    "now", bets.deathsThisWeek(target)};
            set(11, ItemUtil.icon(Material.SKELETON_SKULL, msg.get("gui.manip.plus-name"), msg.getList("gui.manip.plus-lore", pairs)),
                    (p, c) -> {
                        bets.manipulate(p, target, targetName, 1);
                        new IllegalBetsMenu(plugin, p).open(p);
                    });
            set(15, ItemUtil.icon(Material.TOTEM_OF_UNDYING, msg.get("gui.manip.minus-name"), msg.getList("gui.manip.minus-lore", pairs)),
                    (p, c) -> {
                        bets.manipulate(p, target, targetName, -1);
                        new IllegalBetsMenu(plugin, p).open(p);
                    });
            set(18, backButton(), (p, c) -> new ManipulationMenu(plugin, p).open(p));
            fillEmpty();
        }
    }
}
