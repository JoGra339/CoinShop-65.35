package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.ProtectionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Auswahl, welchen Schutzgeld-Deal ein Pate/Boss einem Spieler anbietet: Pate-Deal, Boss-Deal(s) oder alle. */
public final class ProtectionChoiceMenu extends Menu {

        public ProtectionChoiceMenu(CoinShopPlugin plugin, Player viewer, UUID target) {
            super(plugin, 3, plugin.messages().get("gui.protect.choice-title"));
            Messages msg = plugin.messages();
            ProtectionManager prot = plugin.protection();
            List<Integer> deals = prot.myDeals(viewer.getUniqueId());
            int slot = 10;
            for (int d : deals) {
                if (slot > 15) {
                    break;
                }
                Material icon = d == 0 ? Material.WITHER_SKELETON_SKULL : plugin.reviere().icon(d);
                Component name = d == 0 ? msg.get("gui.protect.deal-pate")
                        : msg.get("gui.protect.deal-boss", "revier", plugin.reviere().name(d));
                final int deal = d;
                set(slot++, ItemUtil.icon(icon, name, msg.getList("gui.protect.deal-lore",
                        "percent", Messages.number(Math.round(prot.percent(d))), "chance", prot.chance(d))), (p, c) -> {
                    prot.offerDeals(p, target, deal);
                    p.closeInventory();
                });
            }
            set(16, ItemUtil.icon(Material.EMERALD, msg.get("gui.protect.both-name"), msg.getList("gui.protect.both-lore")),
                    (p, c) -> {
                        prot.offerDeals(p, target, -1);
                        p.closeInventory();
                    });
            set(18, backButton(), (p, c) -> new ProtectionMenu(plugin, p).open(p));
            fillEmpty();
        }
    }
