package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database.WsOrder;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.WholesaleManager;
import de.coinshop.WholesaleTreaties;
import de.coinshop.WholesaleTreaties.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handelsabkommen im Großhandel: Liefervertrag, Exklusivabkommen, Steuerabkommen, Lagerabkommen, Kooperationsvertrag und Eilauftrag.
 * Pro Woche kann nur ein Abkommen genutzt werden. Die Untermenüs sind als verschachtelte Klassen enthalten.
 */
public final class TreatyMenu extends Menu {

    public TreatyMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.treaty.title"));
        Messages msg = plugin.messages();
        WholesaleTreaties tr = plugin.wholesale().treaties();
        UUID uuid = viewer.getUniqueId();

        set(10, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.treaty.supply-name"),
                        msg.getList("gui.treaty.supply-lore", "need", tr.supplyOrders(), "bonus", tr.supplyBonus(),
                                "penalty", tr.supplyPenalty())),
                (player, click) -> new SectorPick(plugin, player, Type.SUPPLY).open(player));
        set(11, ItemUtil.icon(Material.NAME_TAG, msg.get("gui.treaty.exclusive-name"),
                        msg.getList("gui.treaty.exclusive-lore", "bonus", tr.exclusiveBonus())),
                (player, click) -> new SectorPick(plugin, player, Type.EXCLUSIVE).open(player));
        set(12, ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.treaty.tax-name"),
                        msg.getList("gui.treaty.tax-lore", "fee", tr.taxFee(),
                                "percent", Math.round((1.0 - tr.taxFactor()) * 100))),
                (player, click) -> {
                    tr.sign(player, Type.TAX, -1);
                    new TreatyMenu(plugin, player).open(player);
                });
        set(14, ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.treaty.coop-name"),
                        msg.getList("gui.treaty.coop-lore")),
                (player, click) -> {
                    if (plugin.wholesale().treaties().partnerOf(player.getUniqueId()) != null) {
                        new CoopList(plugin, player).open(player);
                    } else {
                        new CoopPartnerPick(plugin, player).open(player);
                    }
                });
        set(16, ItemUtil.icon(Material.BARREL, msg.get("gui.treaty.storage-name"),
                        msg.getList("gui.treaty.storage-lore", "fee", tr.storageFee(), "orders", tr.ordersPerReset(uuid) < 2 ? 2 : tr.ordersPerReset(uuid))),
                (player, click) -> {
                    tr.sign(player, Type.STORAGE, -1);
                    new TreatyMenu(plugin, player).open(player);
                });
        set(15, ItemUtil.icon(Material.CLOCK, msg.get("gui.treaty.rush-name"),
                        msg.getList("gui.treaty.rush-lore", "fee", tr.rushFee(), "days", tr.rushDays(),
                                "factor", tr.rushFactor())),
                (player, click) -> new SectorPick(plugin, player, Type.RUSH).open(player));

        List<WsOrder> shared = tr.coopOrders(uuid);
        set(22, ItemUtil.icon(Material.CHEST, msg.get("gui.treaty.shared-name"),
                        msg.getList("gui.treaty.shared-lore", "count", shared.size())),
                (player, click) -> new CoopList(plugin, player).open(player));

        Type active = tr.active(uuid);
        if (active == null) {
            set(13, ItemUtil.icon(Material.PAPER, msg.get("gui.treaty.status-name"),
                    msg.getList("gui.treaty.status-none-lore")), null);
        } else {
            int sector = tr.activeSector(uuid);
            String extra = sector >= 0 && sector < WholesaleManager.SECTORS.size()
                    ? WholesaleManager.SECTORS.get(sector).name() : "-";
            List<net.kyori.adventure.text.Component> statusLore = new ArrayList<>(msg.getList("gui.treaty.status-lore",
                    "type", active.label(), "sector", extra, "done", tr.supplyDone(uuid), "need", tr.supplyOrders()));
            UUID partner = tr.partnerOf(uuid);
            if (partner != null) {
                statusLore.add(msg.get("gui.treaty.status-partner", "player", plugin.database().getName(partner)));
            }
            set(13, ItemUtil.icon(Material.PAPER, msg.get("gui.treaty.status-name"), statusLore), null);
        }
        set(27, backButton(), (player, click) -> new WholesaleHubMenu(plugin, player).open(player));
        set(35, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    // ------------------------------------------------------------------
    //  Branche wählen (Liefervertrag, Exklusivabkommen, Eilauftrag)
    // ------------------------------------------------------------------

    public static final class SectorPick extends Menu {
        private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

        public SectorPick(CoinShopPlugin plugin, Player viewer, Type type) {
            super(plugin, 4, plugin.messages().get("gui.treaty.pick-title", "type", type.label()));
            Messages msg = plugin.messages();
            WholesaleManager wh = plugin.wholesale();
            UUID uuid = viewer.getUniqueId();
            for (int s = 0; s < WholesaleManager.SECTORS.size(); s++) {
                final int sector = s;
                WholesaleManager.Sector sec = WholesaleManager.SECTORS.get(s);
                if (!wh.hasSector(uuid, s)) {
                    set(SLOTS[s], ItemUtil.icon(Material.BARRIER, msg.get("gui.treaty.pick-locked", "name", sec.name()),
                            msg.getList("gui.treaty.pick-locked-lore")), null);
                    continue;
                }
                set(SLOTS[s], ItemUtil.icon(sec.icon(), msg.get("gui.treaty.pick-name", "name", sec.name()),
                                msg.getList("gui.treaty.pick-lore", "theme", sec.theme(), "type", type.label())),
                        (player, click) -> {
                            plugin.wholesale().treaties().sign(player, type, sector);
                            new TreatyMenu(plugin, player).open(player);
                        });
            }
            set(27, backButton(), (player, click) -> new TreatyMenu(plugin, player).open(player));
            fillEmpty();
        }
    }

    // ------------------------------------------------------------------
    //  Kooperation: Partner für diese Woche wählen
    // ------------------------------------------------------------------

    public static final class CoopPartnerPick extends Menu {
        public CoopPartnerPick(CoinShopPlugin plugin, Player viewer) {
            super(plugin, 6, plugin.messages().get("gui.treaty.coop-partner-title"));
            Messages msg = plugin.messages();
            WholesaleManager wh = plugin.wholesale();
            int i = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (i >= 45) {
                    break;
                }
                if (online.equals(viewer) || !wh.unlocked(online) || wh.treaties().usedThisWeek(online.getUniqueId())) {
                    continue;
                }
                set(i++, ItemUtil.icon(Material.PLAYER_HEAD,
                                msg.get("gui.treaty.coop-partner-name", "player", online.getName()),
                                msg.getList("gui.treaty.coop-partner-lore")),
                        (player, click) -> {
                            wh.treaties().propose(player, online);
                            player.closeInventory();
                        });
            }
            if (i == 0) {
                set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.treaty.coop-nobody"),
                        msg.getList("gui.treaty.coop-nobody-lore")), null);
            }
            set(45, backButton(), (player, click) -> new TreatyMenu(plugin, player).open(player));
            fillEmpty();
        }
    }

    // ------------------------------------------------------------------
    //  Gemeinsame Aufträge: beide Spieler geben hier ab
    // ------------------------------------------------------------------

    public static final class CoopList extends Menu {
        public CoopList(CoinShopPlugin plugin, Player viewer) {
            super(plugin, 6, plugin.messages().get("gui.treaty.shared-title"));
            Messages msg = plugin.messages();
            WholesaleManager wh = plugin.wholesale();
            UUID uuid = viewer.getUniqueId();
            List<WsOrder> orders = wh.treaties().coopOrders(uuid);
            for (int i = 0; i < orders.size() && i < 45; i++) {
                WsOrder order = orders.get(i);
                Material material = Material.matchMaterial(order.material());
                if (material == null) {
                    continue;
                }
                UUID partner = order.owner();
                long mine = wh.treaties().contribution(order.id(), uuid);
                set(i, ItemUtil.icon(material, ItemUtil.name(material),
                                msg.getList("gui.treaty.shared-order-lore",
                                        "partner", partner == null ? "-" : plugin.database().getName(partner),
                                        "sector", WholesaleManager.SECTORS.get(order.sector()).name(),
                                        "amount", order.amount(), "done", order.delivered(),
                                        "left", order.amount() - order.delivered(), "mine", mine)),
                        (player, click) -> {
                            wh.deliver(player, order.id(), !click.isRightClick());
                            new CoopList(plugin, player).open(player);
                        });
            }
            if (orders.isEmpty()) {
                set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.treaty.shared-none"), List.of()), null);
            }
            set(45, backButton(), (player, click) -> new TreatyMenu(plugin, player).open(player));
            fillEmpty();
        }
    }
}
