package de.coinshop.gui;

import de.coinshop.ArenaManager;
import de.coinshop.CoinShopPlugin;
import de.coinshop.CombatSim;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Eine Untergrund-Arena: Verteidiger ansehen, herausfordern oder (als Besitzer) Begleiter stationieren. */
public final class ArenaMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14};

    public ArenaMenu(CoinShopPlugin plugin, Player viewer, int site) {
        super(plugin, 4, Component.text("Arena: " + plugin.expeditions().arenas().siteName(site)));
        ExpeditionManager ex = plugin.expeditions();
        ArenaManager arenas = ex.arenas();
        UUID uuid = viewer.getUniqueId();
        ArenaManager.Arena a = arenas.arena(site);
        List<CombatSim.Fighter> def = arenas.defenders(a);
        boolean mine = uuid.equals(a.owner);
        for (int i = 0; i < SLOTS.length && i < def.size(); i++) {
            CombatSim.Fighter f = def.get(i);
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line("Typ: " + f.type().label, f.type().color));
            lore.add(ExpeditionMenu.line("Leben " + Math.round(f.hp()) + ", Angriff " + Math.round(f.atk()), NamedTextColor.GRAY));
            if (f.defender()) {
                lore.add(ExpeditionMenu.line("Verteidiger", NamedTextColor.AQUA));
            }
            set(SLOTS[i], ItemUtil.icon(Material.IRON_HELMET, ExpeditionMenu.line(f.name(), NamedTextColor.GOLD), lore), null);
        }
        List<Component> info = new ArrayList<>();
        info.add(ExpeditionMenu.line(a.owner == null ? "Gehalten von: NPC" : "Besitzer: " + a.ownerName, NamedTextColor.YELLOW));
        info.add(ExpeditionMenu.line("Verteidiger: " + def.size() + " von höchstens " + arenas.maxStation() + ", Stärke " + CombatSim.strength(def), NamedTextColor.GRAY));
        if (a.owner != null) {
            info.add(ExpeditionMenu.line("Abgewehrt: " + a.defendedPeriod + " seit dem letzten Shopwechsel, insgesamt " + a.defendedTotal, NamedTextColor.GRAY));
            info.add(ExpeditionMenu.line("Tagesertrag aktuell: " + arenas.dailyPay(a) + " Coins (bei Halten bis zum Shopwechsel)", NamedTextColor.GOLD));
        }
        set(4, ItemUtil.icon(Material.MAP, ExpeditionMenu.line(arenas.siteName(site), NamedTextColor.GOLD), info), null);
        if (mine) {
            set(22, ItemUtil.icon(Material.ARMOR_STAND, ExpeditionMenu.line("Begleiter stationieren", NamedTextColor.GREEN), List.of(
                    ExpeditionMenu.line("Wähle 1 bis " + arenas.maxStation() + " Verteidiger. Du kannst sie jederzeit tauschen.", NamedTextColor.GRAY),
                    ExpeditionMenu.line("Mindestens einer muss bleiben.", NamedTextColor.GRAY))),
                    (p, c) -> new ArenaStationMenu(plugin, p, site, 0).open(p));
        } else {
            List<CombatSim.Fighter> mineTeam = new ArrayList<>();
            for (var k : arenas.challengerTeam(uuid)) {
                mineTeam.add(CombatSim.of(ex, uuid, k));
            }
            if (ex.duel().noneAvailable(uuid)) {
                List<Component> why = new ArrayList<>();
                why.add(ExpeditionMenu.line("Alle deine Begleiter sind gerade verhindert:", NamedTextColor.RED));
                for (String t : ex.unavailableLines(uuid, 5)) {
                    why.add(ExpeditionMenu.line(" " + t, NamedTextColor.GRAY));
                }
                set(22, ItemUtil.icon(Material.BARRIER, ExpeditionMenu.line("Nicht möglich", NamedTextColor.RED), why), null);
            } else
            set(22, ItemUtil.icon(Material.NETHERITE_SWORD, ExpeditionMenu.line("Herausfordern", NamedTextColor.RED), List.of(
                    ExpeditionMenu.line("Deine Gruppe (bis zu " + arenas.maxStation() + " Begleiter): Stärke " + CombatSim.strength(mineTeam), NamedTextColor.GRAY),
                    ExpeditionMenu.line(a.owner == null ? "Gewinnst du, gehört die Arena dir." : "Gewinnst du, nimmst du sie " + a.ownerName + " ab.", NamedTextColor.GRAY),
                    ExpeditionMenu.line("Klicken: kämpfen", NamedTextColor.GREEN))),
                    (p, c) -> {
                        p.closeInventory();
                        arenas.challenge(p, site);
                    });
        }
        set(27, backButton(), (p, c) -> new ArenaListMenu(plugin, p).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
