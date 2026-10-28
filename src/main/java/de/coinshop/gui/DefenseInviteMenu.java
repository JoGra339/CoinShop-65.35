package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Wartestube der Verteidigung: Freunde einladen, dann starten. Nach dem Start kommt niemand mehr dazu. */
public final class DefenseInviteMenu extends Menu {

    public DefenseInviteMenu(CoinShopPlugin plugin, Player host) {
        super(plugin, 5, Component.text("Verteidigung: Freunde einladen"));
        ExpeditionManager ex = plugin.expeditions();
        UUID me = host.getUniqueId();
        int slot = 0;
        for (Player target : ex.invitable(host)) {
            if (slot >= 36) {
                break;
            }
            int state = ex.lobbyState(me, target.getUniqueId());
            List<Component> lore = switch (state) {
                case 2 -> List.of(ExpeditionMenu.line("Bereit: Begleiter gewählt, wartet auf den Start.", NamedTextColor.GREEN));
                case 1 -> List.of(ExpeditionMenu.line("Eingeladen, hat noch nicht angenommen.", NamedTextColor.YELLOW),
                        ExpeditionMenu.line("Klicken: erneut einladen", NamedTextColor.GRAY));
                default -> List.of(ExpeditionMenu.line("Klicken: einladen", NamedTextColor.GREEN));
            };
            NamedTextColor color = state == 2 ? NamedTextColor.GREEN : state == 1 ? NamedTextColor.YELLOW : NamedTextColor.GOLD;
            set(slot++, BountyMenu.head(target.getUniqueId(), ExpeditionMenu.line(target.getName(), color), lore), (p, c) -> {
                ex.invite(p, target);
                new DefenseInviteMenu(plugin, p).open(p);
            });
        }
        int ready = ex.lobbyReadyCount(me);
        set(38, ItemUtil.icon(Material.LIME_DYE, ExpeditionMenu.line("Starten", NamedTextColor.GREEN), List.of(
                ExpeditionMenu.line("Mit dir und " + ready + " Mitspieler(n) losgehen.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Wer nicht bereit ist, bleibt draußen.", NamedTextColor.GRAY))), (p, c) -> ex.startLobby(p));
        set(40, ItemUtil.icon(Material.CLOCK, ExpeditionMenu.line("Aktualisieren", NamedTextColor.AQUA), List.of(
                ExpeditionMenu.line("Zeigt, wer angenommen hat.", NamedTextColor.GRAY))), (p, c) -> new DefenseInviteMenu(plugin, p).open(p));
        set(42, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Abbrechen", NamedTextColor.RED), List.of(
                ExpeditionMenu.line("Die Verteidigung nicht starten.", NamedTextColor.GRAY))), (p, c) -> ex.cancelLobby(p));
        fillEmpty();
    }
}
