package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Gear;
import de.coinshop.ItemUtil;
import de.coinshop.MineZones;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Die Tiefenmine: einmal am Tag hinabsteigen, Erze abbauen, Coins sammeln. */
public final class MineMenu extends Menu {

    public MineMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, Component.text("Tiefenmine"));
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();
        int best = ex.mineBest(uuid);
        int pick = ex.level(uuid, Gear.PICKAXE);

        List<Component> lore = new ArrayList<>();
        Material icon = Material.IRON_PICKAXE;
        de.coinshop.Stamina st = plugin.stamina();
        lore.add(ExpeditionMenu.line("Ein Lauf kostet " + de.coinshop.Stamina.format(st.cost(de.coinshop.Stamina.Mode.MINE))
                + " Ausdauer (Abzug beim Start).", NamedTextColor.GRAY));
        lore.add(ExpeditionMenu.line("Deine Ausdauer: " + de.coinshop.Stamina.format(st.get(uuid)) + " von " + de.coinshop.Stamina.format(st.max(uuid)), NamedTextColor.AQUA));
        lore.add(ExpeditionMenu.line("Baue Erze ab: jedes bringt Coins, tiefer mehr.", NamedTextColor.GRAY));
        lore.add(ExpeditionMenu.line("Alle " + ex.guardianEvery() + " Ebenen wartet ein Wächter.", NamedTextColor.GRAY));
        lore.add(ExpeditionMenu.line("Deine tiefste Ebene: " + best, NamedTextColor.YELLOW));
        Action start;
        if (!ex.hasMineLicense(viewer)) {
            icon = Material.BARRIER;
            lore.add(ExpeditionMenu.line("Dafür brauchst du die Lizenz Bergbau.", NamedTextColor.RED));
            lore.add(ExpeditionMenu.line("Klicken: Lizenzen öffnen", NamedTextColor.GREEN));
            start = (p, c) -> new LicenseMenu(plugin, p).open(p);
        } else if (!st.has(uuid, de.coinshop.Stamina.Mode.MINE)) {
            icon = Material.BARRIER;
            lore.add(ExpeditionMenu.line("Zu wenig Ausdauer.", NamedTextColor.RED));
            lore.add(ExpeditionMenu.line("Reicht in " + de.coinshop.Stamina.duration(st.secondsUntil(uuid, de.coinshop.Stamina.Mode.MINE)) + ".", NamedTextColor.RED));
            start = null;
        } else {
            lore.add(ExpeditionMenu.line("Klicken: Begleiter wählen und hinabsteigen", NamedTextColor.GREEN));
            start = (p, c) -> {
                p.closeInventory();
                ex.startMine(p);
            };
        }
        set(10, ItemUtil.icon(icon, ExpeditionMenu.line("Hinabsteigen", NamedTextColor.GOLD), lore), start);

        List<Component> pl = new ArrayList<>();
        pl.add(ExpeditionMenu.line("Stufe " + pick + " von " + Gear.PICKAXE.max, NamedTextColor.YELLOW));
        pl.add(ExpeditionMenu.line("Jetzt: " + ex.describe(Gear.PICKAXE, Math.max(1, pick)), NamedTextColor.GRAY));
        if (pick < Gear.PICKAXE.max) {
            pl.add(ExpeditionMenu.line("Nächste: " + ex.describe(Gear.PICKAXE, pick + 1), NamedTextColor.GREEN));
            pl.add(ExpeditionMenu.line("Preis: " + ex.gearPrice(Gear.PICKAXE, pick + 1) + " Coins", NamedTextColor.GOLD));
            pl.add(ExpeditionMenu.line("Klick: 1 Stufe, Shift-Klick: bis zu 10", NamedTextColor.DARK_GRAY));
        } else {
            pl.add(ExpeditionMenu.line("Höchste Stufe erreicht", NamedTextColor.GREEN));
        }
        ItemStack shown = ex.itemFor(Gear.PICKAXE, Math.max(1, pick));
        set(12, ItemUtil.icon(shown == null ? Material.IRON_PICKAXE : shown.getType(),
                ExpeditionMenu.line("Spitzhacke", NamedTextColor.GOLD), pl), (p, c) -> {
            ex.buyGear(p, Gear.PICKAXE, c.isShiftClick() ? 10 : 1);
            new MineMenu(plugin, p).open(p);
        });

        set(14, ItemUtil.icon(Material.WOLF_ARMOR, ExpeditionMenu.line("Begleiter", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Deine Begleiter kommen mit und kämpfen", NamedTextColor.GRAY),
                ExpeditionMenu.line("wie in den Expeditionslevels.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Dabei: " + ex.companionsTaken(uuid) + " von " + ex.slotsUnlocked(uuid), NamedTextColor.YELLOW),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))),
                (p, c) -> new ExpeditionCompanionHubMenu(plugin, p).open(p));

        List<Component> rl = new ArrayList<>();
        List<Database.RankEntry> top = plugin.database().topStat("mine_depth", 10);
        if (top.isEmpty()) {
            rl.add(ExpeditionMenu.line("Noch keine Einträge.", NamedTextColor.GRAY));
        }
        int place = 1;
        for (Database.RankEntry e : top) {
            rl.add(ExpeditionMenu.line(place + ". " + e.name() + ": Ebene " + e.value(),
                    e.uuid().equals(uuid) ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            place++;
        }
        rl.add(ExpeditionMenu.line("Du: Ebene " + best, NamedTextColor.AQUA));
        set(16, ItemUtil.icon(Material.NETHER_STAR, ExpeditionMenu.line("Rangliste: tiefste Ebene", NamedTextColor.GOLD), rl), null);

        List<Component> zl = new ArrayList<>();
        int z = 0;
        for (MineZones.Zone zone : MineZones.ZONES) {
            int from = z * MineZones.DEPTHS_PER_ZONE + 1;
            StringBuilder ores = new StringBuilder();
            for (Material m : zone.ores()) {
                ores.append(ores.length() > 0 ? ", " : "").append(MineZones.oreName(m));
            }
            zl.add(ExpeditionMenu.line("Ebene " + from + "-" + (from + MineZones.DEPTHS_PER_ZONE - 1) + ": " + zone.name(), NamedTextColor.YELLOW));
            zl.add(ExpeditionMenu.line("   " + ores, NamedTextColor.GRAY));
            z++;
        }
        zl.add(ExpeditionMenu.line("Danach beginnt es von vorn, die Erze sind aber wertvoller.", NamedTextColor.DARK_GRAY));
        set(20, ItemUtil.icon(Material.MAP, ExpeditionMenu.line("Tiefenzonen", NamedTextColor.GOLD), zl), null);

        List<Component> vl = new ArrayList<>();
        int[] depths = {1, 10, 25, 50, 100};
        Material[] ores = {Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.ANCIENT_DEBRIS};
        vl.add(ExpeditionMenu.line("Coins pro Erz (mit Spitzhacke und Multiplikator x" + ExpeditionMenu.trim(ex.multFactor(ex.multStage(uuid))) + "):", NamedTextColor.GRAY));
        for (Material m : ores) {
            StringBuilder sb = new StringBuilder(MineZones.oreName(m) + ": ");
            for (int d : depths) {
                sb.append("E").append(d).append(" ").append(fmt(ex.mineOreValue(uuid, d, m))).append("  ");
            }
            vl.add(ExpeditionMenu.line(sb.toString().trim(), NamedTextColor.YELLOW));
        }
        vl.add(ExpeditionMenu.line("Die ersten Ebenen bringen kaum etwas, tiefer lohnt es sich.", NamedTextColor.DARK_GRAY));
        vl.add(ExpeditionMenu.line("Sonderfunde: Geoden, Goldadern, Fossilien (Mystery Box).", NamedTextColor.DARK_GRAY));
        set(22, ItemUtil.icon(Material.RAW_GOLD, ExpeditionMenu.line("Erträge", NamedTextColor.GOLD), vl), null);

        set(24, ItemUtil.icon(Material.BOOK, ExpeditionMenu.line("Regeln", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Eine Ebene: ein Schacht nach unten, Erzadern, Gegner.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Der Schacht (grün) bringt dich tiefer oder nach oben.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Alle " + ex.guardianEvery() + " Ebenen: erst den Wächter besiegen.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Aufsteigen: du behältst die ganze Beute.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Fällst du, behältst du nur einen Teil.", NamedTextColor.GRAY),
                ExpeditionMenu.line("Nichts aus der Mine nimmst du mit außer Coins.", NamedTextColor.DARK_GRAY))), null);

        set(30, ItemUtil.icon(Material.GOLDEN_CARROT, ExpeditionMenu.line("Ausdauer", NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line("Stufe " + st.level(uuid) + ", +" + de.coinshop.Stamina.format(st.perHour(uuid)) + " pro Stunde", NamedTextColor.YELLOW),
                ExpeditionMenu.line("Klicken: Ausdauer ansehen und steigern", NamedTextColor.GREEN))),
                (p, c) -> new StaminaMenu(plugin, p).open(p));

        set(27, backButton(), (p, c) -> new AdventureMenu(plugin, p).open(p));
        fillEmpty();
    }

    private static String fmt(double v) {
        return v >= 100 ? String.valueOf(Math.round(v)) : String.format(Locale.GERMANY, "%.2f", v);
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
