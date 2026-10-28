package de.coinshop;

import de.coinshop.gui.LedgerMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** Die Buchhaltung (/buchhaltung): zeigt, wann man was eingenommen und ausgegeben hat. */
public final class Ledger implements CommandExecutor {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("einkommen", "Tägliches Einkommen"),
            Map.entry("expedition", "Expedition (Belohnung)"),
            Map.entry("expedition-ausruestung", "Expedition (Ausrüstung)"),
            Map.entry("startbonus", "Startguthaben"),
            Map.entry("erfolg", "Erfolg"),
            Map.entry("geheimer-erfolg", "Geheimer Erfolg"),
            Map.entry("kollektion", "Kollektion"),
            Map.entry("aufgabe", "Aufgabe"),
            Map.entry("aufgaben-bonus", "Aufgaben-Bonus"),
            Map.entry("endkampf", "Endkampf"),
            Map.entry("pay", "Überweisung"),
            Map.entry("handel", "Handel"),
            Map.entry("servershop-kauf", "Server-Shop"),
            Map.entry("spielershop-kauf", "Spieler-Shop (Kauf)"),
            Map.entry("spielershop-verkauf", "Spieler-Shop (Verkauf)"),
            Map.entry("angebot-gebuehr", "Einstellgebühr"),
            Map.entry("slot-kauf", "Verkaufs-Slot"),
            Map.entry("invest-kauf", "Item-Set gekauft"),
            Map.entry("boerse-kauf", "Aktien gekauft"),
            Map.entry("boerse-verkauf", "Aktien verkauft"),
            Map.entry("krypto-kauf", "Kryptocoin gekauft"),
            Map.entry("krypto-verkauf", "Kryptocoin verkauft"),
            Map.entry("dunkel-kauf", "Habibi-Krypto gekauft"),
            Map.entry("dunkel-verkauf", "Habibi-Krypto verkauft"),
            Map.entry("miner-kauf", "Miner gekauft"),
            Map.entry("miner-stufe", "Miner-Stufe"),
            Map.entry("miner-quest", "Miner-Quest"),
            Map.entry("schwarzmarkt", "Schwarzmarkt-Kauf"),
            Map.entry("schwarzmarkt-verkauf", "Händler-Ankauf"),
            Map.entry("drogen-kosten", "Drogenhandel: Ausgaben"),
            Map.entry("drogen-erloes", "Drogenhandel: Einnahmen"),
            Map.entry("drogen-abgabe", "Drogenhandel: Revierabgabe"),
            Map.entry("mystery-box", "Mystery Box"),
            Map.entry("welt-fund", "Glücksfund (Angeln/Erze)"),
            Map.entry("kreaturen-gebot", "Sklavenhandel: Gebot"),
            Map.entry("kreaturen-zurueck", "Sklavenhandel: Gebot zurück"),
            Map.entry("kreaturen-verkauf", "Sklavenhandel: versteigert"),
            Map.entry("kreaturen-shop", "Sklavenhandel: an den Shop verkauft"),
            Map.entry("kreaturen-angebot", "Sklavenhandel: Angebot eingestellt"),
            Map.entry("revier-kauf", "Revier freigekauft"),
            Map.entry("revier-slot", "Revier-Slot"),
            Map.entry("pate-gebuehren", "Pate: Gebühren-Anteil"),
            Map.entry("pate-ankauf", "Pate: Ankauf-Anteil"),
            Map.entry("kopfgeld", "Kopfgeld ausgesetzt"),
            Map.entry("kopfgeld-zurueck", "Kopfgeld zurückgezogen"),
            Map.entry("kopfgeld-abgelaufen", "Kopfgeld abgelaufen"),
            Map.entry("kopfgeld-kassiert", "Kopfgeld kassiert"),
            Map.entry("lizenz", "Lizenz"),
            Map.entry("lizenz-stufe", "Lizenz-Stufe"),
            Map.entry("spielautomat", "Spielautomat"),
            Map.entry("rubbellos", "Rubbellos"),
            Map.entry("gluecksrad", "Glücksrad"),
            Map.entry("wheel", "Glücksrad"),
            Map.entry("doppelt-einsatz", "Doppelt oder Nichts (Einsatz)"),
            Map.entry("doppelt-gewinn", "Doppelt oder Nichts (Gewinn)"),
            Map.entry("schutzgeld-zahlt", "Schutzgeld bezahlt"),
            Map.entry("schutzgeld-erhalten", "Schutzgeld erhalten"),
            Map.entry("auktion-gebot", "Auktion: Gebot"),
            Map.entry("auktion-zurueck", "Auktion: Gebot zurück"),
            Map.entry("auktion-gebuehr", "Auktion: Gebühr"),
            Map.entry("boss-gebuehren", "Unterhändler: Gebühren-Anteil"),
            Map.entry("dividende", "Dividende"),
            Map.entry("firmenrang", "Firmenrang-Prämie"),
            Map.entry("firmenrang-anteil", "Anteil am Firmenrang"),
            Map.entry("gewerbe", "Gewerbe"),
            Map.entry("gewerbe-lohn", "Lohn im Gewerbe"),
            Map.entry("grosshandel", "Großhandel"),
            Map.entry("grosshandel-abkommen", "Großhandel: Abkommen"),
            Map.entry("grosshandel-bonus", "Großhandel: Bonus"),
            Map.entry("grosshandel-branche", "Großhandel: Branche freigeschaltet"),
            Map.entry("grosshandel-vertragsstrafe", "Großhandel: Vertragsstrafe"),
            Map.entry("insider-tipp", "Insider-Tipp"),
            Map.entry("quoten-anteil", "Anteil an getürkter Quote"),
            Map.entry("quoten-tuerken", "Quote türken"),
            Map.entry("revier-einstellgebuehr", "Revier: Einstellgebühr"),
            Map.entry("route-ertrag", "Schmugglerroute: Ertrag"),
            Map.entry("route-kauf", "Schmugglerroute gekauft"),
            Map.entry("route-upgrade", "Schmugglerroute: Stufe"),
            Map.entry("wette", "Wette"),
            Map.entry("wette-aufgedeckt", "Schwarze Wette aufgedeckt"),
            Map.entry("wette-gewinn", "Wettgewinn"),
            Map.entry("wette-klein", "Kleine Wette"),
            Map.entry("wette-klein-gewinn", "Kleine Wette gewonnen"),
            Map.entry("wette-rueckzahlung", "Wette zurückgezahlt"),
            Map.entry("wettmanipulation", "Wettmanipulation"),
            Map.entry("wettbaron-anteil", "Wettbaron: Gebühren-Anteil"),
            Map.entry("gratis-rad", "Gratis-Glücksrad"));

    private final CoinShopPlugin plugin;

    public Ledger(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** Lesbarer Name einer Art von Buchung. */
    public static String label(String type) {
        String label = LABELS.get(type);
        if (label != null) {
            return label;
        }
        if (type.startsWith("admin-")) {
            return "Admin (" + type.substring(6) + ")";
        }
        return type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        try {
            new LedgerMenu(plugin, player, 0, 0).open(player);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Fehler bei /buchhaltung: " + e);
            plugin.messages().send(player, "error");
        }
        return true;
    }
}
