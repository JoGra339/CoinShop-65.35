package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Die Angestellten des Gewerbes: wer arbeitet für dich, was wurde abgegeben. Dazu Einladen und Details. */
public final class CompanyStaffMenu extends Menu {

    public CompanyStaffMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.company.staff-title"));
        Messages msg = plugin.messages();
        UUID uuid = viewer.getUniqueId();
        List<Database.Staff> staff = plugin.database().getStaffOf(uuid);
        int i = 0;
        for (Database.Staff member : staff) {
            if (i >= 45) {
                break;
            }
            String name = plugin.database().getName(member.member());
            boolean online = Bukkit.getPlayer(member.member()) != null;
            set(i++, ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.company.member-name", "player", name),
                            msg.getList("gui.company.member-lore", "items", member.items(), "wages", member.wages(),
                                    "online", online ? "ja" : "nein")),
                    (p, c) -> {
                        if (c.isShiftClick() && c.isRightClick()) {
                            plugin.companies().fire(p, member.member());
                            new CompanyStaffMenu(plugin, p).open(p);
                        } else {
                            new CompanyStaffDetailMenu(plugin, p, member.member()).open(p);
                        }
                    });
        }
        if (staff.isEmpty()) {
            set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.company.no-staff"),
                    msg.getList("gui.company.no-staff-lore")), null);
        }
        set(45, backButton(), (p, c) -> new CompanyMenu(plugin, p).open(p));
        set(49, ItemUtil.icon(Material.LIME_DYE, msg.get("gui.company.invite-name"),
                msg.getList("gui.company.invite-lore")), (p, c) -> new Invite(plugin, p).open(p));
        set(53, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    /** Einen Spieler einladen (nur Spieler, die online sind und noch kein Gewerbe haben). */
    public static final class Invite extends Menu {
        public Invite(CoinShopPlugin plugin, Player viewer) {
            super(plugin, 6, plugin.messages().get("gui.company.invite-title"));
            Messages msg = plugin.messages();
            int i = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (i >= 45) {
                    break;
                }
                UUID id = online.getUniqueId();
                if (online.equals(viewer) || plugin.database().getCompany(id) != null
                        || plugin.database().getStaff(id) != null) {
                    continue;
                }
                set(i++, ItemUtil.icon(Material.PLAYER_HEAD, msg.get("gui.company.invite-player", "player", online.getName()),
                                msg.getList("gui.company.invite-player-lore")),
                        (p, c) -> {
                            plugin.companies().invite(p, online);
                            p.closeInventory();
                        });
            }
            if (i == 0) {
                set(22, ItemUtil.icon(Material.PAPER, msg.get("gui.company.invite-nobody"),
                        msg.getList("gui.company.invite-nobody-lore")), null);
            }
            set(45, backButton(), (p, c) -> new CompanyStaffMenu(plugin, p).open(p));
            fillEmpty();
        }
    }
}
