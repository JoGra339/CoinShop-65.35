package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.CombatSim;
import de.coinshop.CompanionPowers;
import de.coinshop.CompanionRoster;
import de.coinshop.CompanionTraits;
import de.coinshop.CompanionTypes;
import de.coinshop.CompanionVariants;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Einheitliche Werte-Zeilen für Begleiter in allen Menüs: alle Stats, Rolle, Fähigkeit und was sie betrifft. */
public final class CompanionInfo {

    private CompanionInfo() {
    }

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    private static String fmt(double v) {
        return v >= 100 ? String.valueOf(Math.round(v)) : String.format(Locale.GERMANY, "%.1f", v);
    }

    /** Rolle, Fähigkeit mit Ziel und Typ-Stärken einer Art (ohne Exemplar-Werte). */
    public static List<Component> species(CoinShopPlugin plugin, Companion c) {
        ExpeditionManager ex = plugin.expeditions();
        List<Component> out = new ArrayList<>();
        CompanionTypes.MobType type = c.mobType();
        out.add(line("Typ: " + type.label + " (" + CompanionTypes.describe(type) + ")", type.color));
        out.add(line(ex.roleText(c), ex.roleColor(c)));
        out.add(line("Fähigkeit: " + CompanionPowers.describe(c.type), NamedTextColor.LIGHT_PURPLE));
        out.add(line(CompanionPowers.targetText(c.type), NamedTextColor.LIGHT_PURPLE));
        if (ex.isSupporter(c)) {
            out.add(line("Zusätzlich: heilt Verbündete (Menge unten bei den Werten)", NamedTextColor.AQUA));
        }
        return out;
    }

    /** Alle Werte eines Exemplars von "uuid". */
    public static List<Component> lines(CoinShopPlugin plugin, UUID uuid, Copy k) {
        ExpeditionManager ex = plugin.expeditions();
        Companion c = k.companion();
        int idx = k.index();
        List<Component> out = new ArrayList<>();
        CompanionRoster.Info info = ex.info(uuid, c, idx);
        CompanionTraits.Rarity rar = CompanionTraits.Rarity.of(info.rarity);
        int level = ex.copyLevel(uuid, c, idx);
        long kills = ex.roster().kills(uuid, c, idx);
        CombatSim.Fighter f = CombatSim.of(ex, uuid, k);

        String variantLabel = CompanionVariants.label(ex.variantOf(uuid, c, idx));
        out.add(line(rar.label + " | " + c.mobType().label + " | " + ex.roleText(c)
                + (variantLabel.isEmpty() ? "" : " | " + variantLabel), rar.color));
        out.add(line("Leben: " + fmt(f.hp()) + "   Schaden: " + fmt(f.atk()), NamedTextColor.WHITE));
        if (ex.isSupporter(c)) {
            out.add(line("Heilt " + fmt(ex.healAmount(uuid, c, idx)) + " Leben (" + fmt(ex.healAmount(uuid, c, idx) / 2) + " Herzen), "
                    + ex.healSlotsAt(level) + "x pro Level", NamedTextColor.AQUA));
            out.add(line("Betrifft: dich, Mitspieler, Begleiter, Kern", NamedTextColor.AQUA));
        }
        out.add(line("Fähigkeit: " + CompanionPowers.describe(c.type), NamedTextColor.LIGHT_PURPLE));
        out.add(line(CompanionPowers.targetText(c.type), NamedTextColor.LIGHT_PURPLE));
        for (CompanionTraits.Trait t : ex.traits(uuid, c, idx)) {
            out.add(line((t.negative() ? "✘ " : "✔ ") + t.label + ": " + t.text, t.negative() ? NamedTextColor.RED : NamedTextColor.GREEN));
        }
        String xpWord = ex.isSupporter(c) ? "Heil-Erfahrung" : ex.isDefender(c) ? "überlebte Gegner" : "Kills";
        if (level < ex.companionMaxLevel()) {
            out.add(line("Level " + level + ", " + xpWord + " " + kills + " von " + ex.killsForLevel(level + 1), NamedTextColor.YELLOW));
        } else {
            out.add(line("Level " + level + " (Höchststufe), " + xpWord + " " + kills, NamedTextColor.YELLOW));
        }
        out.add(line("Gefolge-Kosten: " + ex.copyCost(uuid, c, idx) + " Sterne", NamedTextColor.GRAY));
        return out;
    }
}
