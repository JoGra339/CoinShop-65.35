package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.ProtectionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Alle eigenen Schutzgeld-Verträge: an wen man zahlt und wer einem zahlt. Ein Klick beendet den Vertrag. */
public final class ContractsMenu extends Menu {

    private static final int MAX_SHOWN = 21;

    public ContractsMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, plugin.messages().get("gui.contracts.title"));
        Messages msg = plugin.messages();
        Database db = plugin.database();
        ProtectionManager prot = plugin.protection();
        UUID me = viewer.getUniqueId();

        // Oben: ich zahle Schutzgeld an ... (Reihe 2)
        set(0, ItemUtil.icon(Material.GOLD_INGOT, msg.get("gui.contracts.paying-header"),
                msg.getList("gui.contracts.paying-header-lore")), null);
        int slot = 9;
        for (Database.Protection p : db.protectionsOf(me)) {
            if (slot >= 9 + 8) {
                break;
            }
            set(slot++, BountyMenu.head(p.offerer(), msg.get("gui.contracts.paying-name",
                            "player", db.getName(p.offerer())),
                    msg.getList("gui.contracts.paying-lore", "scope", prot.scopeText(p.revier()),
                            "percent", Messages.number(Math.round(prot.percent(p.revier()))),
                            "chance", prot.chance(p.revier()))),
                    (pl, c) -> {
                        prot.endContract(pl, p.offerer(), p.revier(), p.target());
                        new ContractsMenu(plugin, pl).open(pl);
                    });
        }
        if (slot == 9) {
            set(9, ItemUtil.icon(Material.PAPER, msg.get("gui.contracts.paying-none"), List.of()), null);
        }

        // Mitte: mir zahlen Kunden Schutzgeld (Reihe 3 und 4)
        set(18, ItemUtil.icon(Material.EMERALD, msg.get("gui.contracts.receiving-header"),
                msg.getList("gui.contracts.receiving-header-lore")), null);
        int slot2 = 19;
        int shown = 0;
        for (Database.Protection p : db.protectionsBy(me)) {
            if (shown++ >= MAX_SHOWN || slot2 > 33) {
                break;
            }
            set(slot2++, BountyMenu.head(p.target(), msg.get("gui.contracts.receiving-name",
                            "player", db.getName(p.target())),
                    msg.getList("gui.contracts.receiving-lore", "scope", prot.scopeText(p.revier()))),
                    (pl, c) -> {
                        prot.endContract(pl, p.offerer(), p.revier(), p.target());
                        new ContractsMenu(plugin, pl).open(pl);
                    });
        }
        if (slot2 == 19) {
            set(19, ItemUtil.icon(Material.PAPER, msg.get("gui.contracts.receiving-none"), List.of()), null);
        }

        // Unten
        if (!prot.myDeals(me).isEmpty()) {
            set(40, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.contracts.offer-name"),
                    msg.getList("gui.contracts.offer-lore")), (pl, c) -> new ProtectionMenu(plugin, pl).open(pl));
        }
        set(36, backButton(), (p, c) -> new ReviereMenu(plugin, p).open(p));
        set(44, balanceIcon(viewer), null);
        fillEmpty();
    }
}
