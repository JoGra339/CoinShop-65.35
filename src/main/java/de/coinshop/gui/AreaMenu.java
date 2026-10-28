package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Untermenü des Hauptmenüs: bündelt zusammengehörige Bereiche (Anlegen und Handeln, Ich und Fortschritt, Info). */
public final class AreaMenu extends Menu {

    /** Die Bereiche des Hauptmenüs. */
    public enum Area {
        MONEY("Anlegen und Handeln"),
        ME("Ich und Fortschritt"),
        INFO("Info und Konto");

        final String title;

        Area(String title) {
            this.title = title;
        }
    }

    private static final int[] SLOTS = {10, 12, 14, 16, 20, 22, 24};

    private int next;

    public AreaMenu(CoinShopPlugin plugin, Player viewer, Area area) {
        super(plugin, 4, Component.text(area.title));
        switch (area) {
            case MONEY -> {
                entry(Material.GOLD_BLOCK, "Investitionen", "Aktien, Sets und mehr.", "Geld arbeiten lassen.",
                        p -> new InvestHubMenu(plugin, p).open(p));
                if (plugin.bets().enabled()) {
                    entry(Material.SPYGLASS, "Wettbüro", "Wetten auf Ereignisse und Spieler.", "Große und kleine Wetten.",
                            p -> {
                                if (plugin.bets().allowed(p)) {
                                    new BetMenu(plugin, p).open(p);
                                }
                            });
                }
                entry(Material.BARREL, "Großhandel", "Aufträge, Branchen, Gesellschafter.", "Braucht die Lizenz Großhandel.",
                        p -> plugin.wholesale().openFor(p));
                entry(Material.PAPER, "Lizenzen", "Schalte Bereiche frei:", "Großhandel, Expeditionen, Schwarzmarkt ...",
                        p -> new LicenseMenu(plugin, p).open(p));
            }
            case ME -> {
                entry(Material.PLAYER_HEAD, "Profil", "Deine Werte, Begleiter-Maskottchen,", "Boxen und Verknüpfungen.",
                        p -> new ProfileMenu(plugin, p, p.getUniqueId()).open(p));
                entry(Material.NAME_TAG, "Titel", "Wähle deinen Titel.", "Titel können Vorteile bringen.",
                        p -> new TitleMenu(plugin, p).open(p));
                entry(Material.GOLDEN_HELMET, "Ranglisten", "Wer ist der Beste?", "Platz 1 bringt Titel und Boxen.",
                        p -> new RankingMenu(plugin, p).open(p));
                entry(Material.BOOKSHELF, "Sammlungen", "Sammle Gegenstände zu Sets.", "Belohnungen für fertige Sammlungen.",
                        p -> new CollectionsMenu(plugin, p).open(p));
                entry(Material.ENDER_EYE, "Geheime Erfolge", "Finde versteckte Erfolge.", "Manche bringen Belohnungen.",
                        p -> new SecretMenu(plugin, p).open(p));
            }
            case INFO -> {
                entry(Material.BELL, "Ereignisse", "Was gerade auf dem Server los ist.", "Blitzverkauf, Goldene Stunde ...",
                        p -> new EventsMenu(plugin, p).open(p));
                entry(Material.SUNFLOWER, "Einkommen", "Woher dein Geld kommt.", "Dein Kontostand im Überblick.",
                        p -> new MyIncomeMenu(plugin, p).open(p));
                entry(Material.WRITABLE_BOOK, "Buchhaltung", "Was du eingenommen und ausgegeben hast.", "Auch als Wochenübersicht.",
                        p -> new LedgerMenu(plugin, p, 0, 0).open(p));
                entry(Material.GOLD_BLOCK, "Geldumlauf", "Wie viele Coins sind im Umlauf?", "Summe, Durchschnitt, Verlauf, Reichste.",
                        p -> new MoneySupplyMenu(plugin, p, true).open(p));
                entry(Material.CLOCK, "Zeitplan", "Start, Nether und Endkampf.", "Mit Countdown.",
                        p -> new TimelineMenu(plugin, p).open(p));
            }
        }
        set(27, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        fillEmpty();
    }

    @Override
    public void open(Player player) {
        set(35, balanceIcon(player), incomeClick());
        super.open(player);
    }

    private void entry(Material icon, String name, String line1, String line2, java.util.function.Consumer<Player> action) {
        if (next >= SLOTS.length) {
            return;
        }
        set(SLOTS[next++], ItemUtil.icon(icon, ExpeditionMenu.line(name, NamedTextColor.GOLD), List.of(
                ExpeditionMenu.line(line1, NamedTextColor.GRAY),
                ExpeditionMenu.line(line2, NamedTextColor.GRAY),
                ExpeditionMenu.line("Klicken: öffnen", NamedTextColor.GREEN))), (p, c) -> action.accept(p));
    }
}
