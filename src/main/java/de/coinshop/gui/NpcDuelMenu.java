package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CombatSim;
import de.coinshop.CompanionTypes;
import de.coinshop.ExpeditionManager;
import de.coinshop.ItemUtil;
import de.coinshop.NpcDuels;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Duelle gegen NPCs: Rangleiter, Einsatz, Risikokampf und das tägliche Turnier. */
public final class NpcDuelMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28};

    private static Material iconOf(CompanionTypes.MobType t) {
        return switch (t) {
            case FEUER -> Material.BLAZE_ROD;
            case WASSER -> Material.PRISMARINE_CRYSTALS;
            case NATUR -> Material.OAK_LEAVES;
            case ERDE -> Material.GRASS_BLOCK;
            case EIS -> Material.BLUE_ICE;
            case LUFT -> Material.FEATHER;
            case UNTOTE -> Material.ROTTEN_FLESH;
            case FINSTER -> Material.ENDER_PEARL;
        };
    }

    public NpcDuelMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 5, Component.text("Duelle gegen NPCs"));
        ExpeditionManager ex = plugin.expeditions();
        NpcDuels npcs = ex.npc();
        UUID uuid = viewer.getUniqueId();
        int progress = npcs.progress(uuid);
        List<CombatSim.Fighter> mine = npcs.myTeam(uuid);
        long myStrength = CombatSim.strength(mine);
        for (int i = 0; i < NpcDuels.NPCS.size(); i++) {
            NpcDuels.Npc n = NpcDuels.NPCS.get(i);
            boolean beaten = i < progress;
            boolean open = npcs.unlocked(uuid, i);
            List<Component> lore = new ArrayList<>();
            lore.add(ExpeditionMenu.line(n.title(), NamedTextColor.YELLOW));
            lore.add(ExpeditionMenu.line(n.story(), NamedTextColor.GRAY));
            lore.add(ExpeditionMenu.line("Typ: " + n.type().label + " (" + CompanionTypes.describe(n.type()) + ")", n.type().color));
            if (open || beaten) {
                lore.add(ExpeditionMenu.line("Gegnerstärke: " + CombatSim.strength(npcs.team(n)) + ", deine Gruppe: " + myStrength, NamedTextColor.GRAY));
                lore.add(ExpeditionMenu.line((beaten ? "Wiederholung" : "Erster Sieg") + ": " + (beaten ? npcs.reward(i) * 20 / 100 : npcs.reward(i))
                        + " Coins", NamedTextColor.GOLD));
                lore.add(ExpeditionMenu.line(beaten ? "Besiegt - Klicken: erneut kämpfen" : "Klicken: herausfordern", NamedTextColor.GREEN));
            } else if (n.black() && !npcs.blackAllowed(uuid)) {
                lore.add(ExpeditionMenu.line("Nur mit Schwarzmarkt-Lizenz „Schmuggler“.", NamedTextColor.RED));
            } else {
                lore.add(ExpeditionMenu.line("Besiege zuerst den Gegner davor.", NamedTextColor.RED));
            }
            ItemStack item = ItemUtil.icon(open || beaten ? iconOf(n.type()) : Material.GRAY_DYE,
                    ExpeditionMenu.line(n.name(), open || beaten ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY), lore);
            if (beaten) {
                item.editMeta(m -> m.setEnchantmentGlintOverride(true));
            }
            final int rank = i;
            set(SLOTS[i], item, open ? (p, c) -> {
                npcs.challenge(p, rank); // öffnet die Aufstellung bzw. startet den Kampf
            } : null);
        }
        long stake = npcs.stake(uuid);
        set(38, ItemUtil.icon(Material.GOLD_NUGGET, ExpeditionMenu.line("Einsatz: " + stake + " Coins", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Verlierst du, geht der Einsatz an den Shop.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Gewinnst du, bekommst du ihn zusätzlich aus dem Shop-Konto.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: Einsatz ändern", NamedTextColor.GREEN))), (p, c) -> {
            npcs.cycleStake(p.getUniqueId());
            new NpcDuelMenu(plugin, p).open(p);
        });
        boolean risk = npcs.risk(uuid);
        set(40, ItemUtil.icon(risk ? Material.TNT : Material.SHIELD, ExpeditionMenu.line("Risikokampf: " + (risk ? "an" : "aus"),
                risk ? NamedTextColor.RED : NamedTextColor.GREEN), List.of(
                ExpeditionMenu.line("An: Dein stärkstes Exemplar ruht bei einer Niederlage bis zum Shopwechsel.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Bei einem Sieg gibt es +50 % Belohnung.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: umschalten", NamedTextColor.GREEN))), (p, c) -> {
            npcs.toggleRisk(p.getUniqueId());
            new NpcDuelMenu(plugin, p).open(p);
        });
        boolean done = npcs.tournamentDone(uuid);
        set(42, ItemUtil.icon(Material.GOLDEN_HELMET, ExpeditionMenu.line("Tägliches Turnier", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Vier Runden gegen steigende Gegner, einmal pro Shop-Tag.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Verlierst du, scheidest du aus. Alle vier Siege: doppeltes Preisgeld und eine Box.", NamedTextColor.GRAY),
                ExpeditionMenu.line(done ? "Heute schon teilgenommen" : "Klicken: teilnehmen", done ? NamedTextColor.RED : NamedTextColor.GREEN))),
                done ? null : (p, c) -> {
                    npcs.tournament(p); // öffnet die Aufstellung bzw. startet das Turnier
                });
        set(36, backButton(), (p, c) -> new DuelMenu(plugin, p).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(44, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
