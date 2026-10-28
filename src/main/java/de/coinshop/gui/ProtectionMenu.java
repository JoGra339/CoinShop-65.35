package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.ProtectionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exklusives Menü für den Paten und die Unterhändlere: Schutzgeld per Klick anbieten. */
public final class ProtectionMenu extends Menu {

    private static final int MAX_SHOWN = 36;

    public ProtectionMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.protect.title"));
        Messages msg = plugin.messages();
        Database db = plugin.database();
        ProtectionManager prot = plugin.protection();
        UUID me = viewer.getUniqueId();
        List<Integer> deals = prot.myDeals(me);

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(me) || slot >= MAX_SHOWN) {
                continue;
            }
            UUID target = online.getUniqueId();
            boolean licensed = plugin.licenses().has(target, "schmuggler");
            List<Component> lore = new ArrayList<>(msg.getList(licensed ? "gui.protect.player-lore" : "gui.protect.player-locked"));
            set(slot++, BountyMenu.head(target, msg.get("gui.protect.player-name", "name", online.getName()), lore),
                    licensed ? (p, c) -> {
                        if (prot.myDeals(p.getUniqueId()).size() > 1) {
                            new ProtectionChoiceMenu(plugin, p, target).open(p);
                        } else {
                            prot.offerDeals(p, target, -1);
                            p.closeInventory();
                        }
                    } : null);
        }
        if (slot == 0) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.protect.nobody"), List.of()), null);
        }

        // Meine Deals (Info)
        int infoSlot = 37;
        for (int d : deals) {
            if (infoSlot > 43) {
                break;
            }
            Material icon = d == 0 ? Material.WITHER_SKELETON_SKULL : plugin.reviere().icon(d);
            Component name = d == 0 ? msg.get("gui.protect.deal-pate")
                    : msg.get("gui.protect.deal-boss", "revier", plugin.reviere().name(d));
            set(infoSlot++, ItemUtil.icon(icon, name, msg.getList("gui.protect.deal-lore",
                    "percent", Messages.number(Math.round(prot.percent(d))), "chance", prot.chance(d))), null);
        }

        // Aktuelle Kunden
        List<Component> customers = new ArrayList<>(msg.getList("gui.protect.customers-lore"));
        int count = 0;
        for (Database.Protection p : db.protectionsBy(me)) {
            if (count++ < 10) {
                customers.add(msg.get("gui.protect.customer-line", "player", db.getName(p.target()),
                        "scope", p.revier() == 0 ? "alle Reviere" : plugin.reviere().name(p.revier())));
            }
        }
        set(49, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.protect.customers-name", "count", count), customers),
                (p, c) -> new ContractsMenu(plugin, p).open(p));
        set(45, backButton(), (p, c) -> new ContractsMenu(plugin, p).open(p));
        set(53, balanceIcon(viewer), null);
        fillEmpty();
    }
}
