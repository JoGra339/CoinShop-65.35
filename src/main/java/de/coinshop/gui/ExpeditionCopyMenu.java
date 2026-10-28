package de.coinshop.gui;

import de.coinshop.CompanionAspects;
import de.coinshop.CompanionLife;
import de.coinshop.CompanionRoster;
import de.coinshop.CompanionTraits;
import de.coinshop.CompanionTrips;
import de.coinshop.CompanionTypes;
import de.coinshop.CompanionVariants;
import de.coinshop.CoinShopPlugin;
import de.coinshop.Database;
import de.coinshop.WholesaleManager;
import de.coinshop.ExpeditionManager;
import de.coinshop.ExpeditionManager.Companion;
import de.coinshop.ExpeditionManager.Copy;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Eine Seite voller einzelner Begleiter-Exemplare (45 pro Seite). Je nach Zweck (Mode) bedeutet ein Klick etwas anderes:
 * mitnehmen, auf Reise schicken, im Großhandel einsetzen, züchten, fusionieren, opfern ...
 */
public final class ExpeditionCopyMenu extends Menu {

    public enum Mode {
        TAKE("Gefolge: Wer kommt mit?"),
        VIEW("Meine Begleiter"),
        TRIP("Reise: Wer geht mit?"),
        PICK("Wer kommt mit?"),
        WHOLESALE("Großhandel: Begleiter zuteilen"),
        BREED("Zucht"),
        FUSION("Fusion"),
        TRIBUTE("Opfer wählen"),
        SELL("Sklavenhandel: Wen verkaufen?"),
        MASCOT("Maskottchen: Wer begleitet dich im Shop?");

        final String title;

        Mode(String title) {
            this.title = title;
        }
    }

    /** Sortierung der Liste. */
    public enum Sort {
        TEAM("Im Team zuerst"),
        LEVEL("Level"),
        KILLS("Kills"),
        RARITY("Seltenheit"),
        TYPE("Typ"),
        ROLE("Rolle (Angreifer/Verteidiger/Unterstützer)"),
        NAME("Name (A-Z)");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Merkt sich Auswahl und Einstellungen über das Neuöffnen des Menüs hinweg. */
    public static final class State {
        Sort sort = Sort.TEAM;
        String search = "";
        final Set<Copy> chosen = new LinkedHashSet<>();
        int page;
        int dest;
        int hours;
        int level;
        long order;
        Companion buy;
    }

    private static final int PAGE = 44;

    private final Mode mode;
    private final State state;

    public ExpeditionCopyMenu(CoinShopPlugin plugin, Player viewer, Mode mode) {
        this(plugin, viewer, mode, new State());
    }

    /** Für das Abwahl-Menü vor dem Levelstart. */
    public ExpeditionCopyMenu(CoinShopPlugin plugin, Player viewer, Mode mode, int level) {
        this(plugin, viewer, mode, initial(plugin, viewer, mode, level));
    }

    private static State initial(CoinShopPlugin plugin, Player viewer, Mode mode, int level) {
        State st = new State();
        st.level = level;
        if (mode == Mode.PICK) {
            st.chosen.addAll(plugin.expeditions().takenReady(viewer.getUniqueId()));
        }
        return st;
    }

    public static ExpeditionCopyMenu forTrip(CoinShopPlugin plugin, Player viewer, int dest) {
        State st = new State();
        st.dest = dest;
        st.hours = plugin.expeditions().trips().durations().get(0);
        return new ExpeditionCopyMenu(plugin, viewer, Mode.TRIP, st);
    }

    public static ExpeditionCopyMenu forTribute(CoinShopPlugin plugin, Player viewer, Companion buy) {
        State st = new State();
        st.buy = buy;
        return new ExpeditionCopyMenu(plugin, viewer, Mode.TRIBUTE, st);
    }

    private ExpeditionCopyMenu(CoinShopPlugin plugin, Player viewer, Mode mode, State state) {
        super(plugin, 6, Component.text(titleFor(plugin, mode, state)));
        this.mode = mode;
        this.state = state;
        build(viewer);
    }

    private static String titleFor(CoinShopPlugin plugin, Mode mode, State st) {
        if (mode == Mode.TRIP) {
            return "Reise: " + plugin.expeditions().trips().name(st.dest);
        }
        if (mode == Mode.PICK) {
            return st.level <= 0 ? "Verteidigung: Wer kommt mit?" : "Wer kommt mit? Level " + st.level;
        }
        if (mode == Mode.TRIBUTE && st.buy != null) {
            return "Opfer für " + st.buy.label;
        }
        return mode.title;
    }

    private void reopen(Player p) {
        new ExpeditionCopyMenu(plugin, p, mode, state).open(p);
    }

    private static Component line(String text, NamedTextColor color) {
        return ExpeditionMenu.line(text, color);
    }

    private List<Copy> listed(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        List<Copy> list;
        switch (mode) {
            case PICK -> list = ex.takenReady(uuid);
            case TRIBUTE -> {
                list = new ArrayList<>();
                for (Copy k : ex.allCopies(uuid)) {
                    if (state.buy != null && ex.tributeFits(uuid, state.buy, k)) {
                        list.add(k);
                    }
                }
            }
            default -> list = ex.allCopies(uuid);
        }
        String q = state.search == null ? "" : state.search.trim().toLowerCase();
        if (!q.isEmpty()) {
            list.removeIf(k -> !matches(uuid, k, q));
        }
        list.sort((a, b) -> {
            int cmp = 0;
            switch (state.sort) {
                case TEAM -> {
                    boolean ta = ex.taken(uuid, a.companion(), a.index());
                    boolean tb = ex.taken(uuid, b.companion(), b.index());
                    cmp = ta == tb ? 0 : ta ? -1 : 1;
                }
                case LEVEL -> cmp = Integer.compare(ex.copyLevel(uuid, b.companion(), b.index()), ex.copyLevel(uuid, a.companion(), a.index()));
                case RARITY -> cmp = Integer.compare(ex.info(uuid, b.companion(), b.index()).rarity, ex.info(uuid, a.companion(), a.index()).rarity);
                case TYPE -> cmp = a.companion().mobType().compareTo(b.companion().mobType());
                case ROLE -> cmp = Integer.compare(ex.roleIndex(a.companion()), ex.roleIndex(b.companion()));
                case NAME -> cmp = a.companion().label.compareToIgnoreCase(b.companion().label);
                default -> { }
            }
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(ex.roster().kills(uuid, b.companion(), b.index()), ex.roster().kills(uuid, a.companion(), a.index()));
        });
        return list;
    }

    /** Passt das Exemplar zum Suchtext (Name, Typ, Seltenheit, Rolle, Variante, "team")? */
    private boolean matches(UUID uuid, Copy k, String q) {
        ExpeditionManager ex = plugin.expeditions();
        Companion c = k.companion();
        StringBuilder sb = new StringBuilder();
        sb.append(c.label).append(' ').append(c.mobType().label).append(' ')
                .append(CompanionTraits.Rarity.of(ex.info(uuid, c, k.index()).rarity).label).append(' ')
                .append(ex.roleLabel(c)).append(' ').append(CompanionVariants.label(ex.variantOf(uuid, c, k.index()))).append(' ')
                .append('#').append(k.index() + 1);
        if (ex.taken(uuid, c, k.index())) {
            sb.append(" team");
        }
        return sb.toString().toLowerCase().contains(q);
    }

    private void build(Player viewer) {
        ExpeditionManager ex = plugin.expeditions();
        UUID uuid = viewer.getUniqueId();
        List<Copy> list = listed(uuid);
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        state.page = Math.max(0, Math.min(pages - 1, state.page));
        // Auswahl bereinigen (Exemplare, die es nicht mehr gibt oder die beschäftigt sind)
        state.chosen.removeIf(k -> !ex.exists(uuid, k.companion(), k.index())
                || (mode != Mode.PICK && ex.busyReason(uuid, k.companion(), k.index()) != null));
        int from = state.page * PAGE;
        for (int i = 0; i < PAGE && from + i < list.size(); i++) {
            Copy k = list.get(from + i);
            boolean selected = mode == Mode.TAKE ? ex.taken(uuid, k.companion(), k.index()) : state.chosen.contains(k);
            if (mode == Mode.WHOLESALE) {
                selected = ex.roster().work(uuid, k.companion(), k.index()) != null;
            }
            set(i, copyItem(uuid, k, selected), clickFor(k));
        }
        if (list.isEmpty()) {
            set(22, ItemUtil.icon(Material.BARRIER, line(mode == Mode.TRIBUTE ? "Kein passendes Exemplar" : "Noch keine Begleiter", NamedTextColor.RED),
                    List.of(line(mode == Mode.TRIBUTE ? "Du brauchst ein Exemplar einer anderen Art mit mindestens "
                            + plugin.getConfig().getInt("expedition.tribute.min-tribute-stars", 3) + " Sternen" : "Kaufe zuerst Begleiter im Laden.", NamedTextColor.GRAY),
                            line(mode == Mode.TRIBUTE ? "(und weniger Sternen als " + state.buy.label + ")." : "", NamedTextColor.GRAY))), null);
        }
        // Untere Reihe
        set(44, sortSearchItem(), (p, click) -> {
            if (click.isShiftClick()) {
                state.search = "";
                state.sort = Sort.TEAM;
                state.page = 0;
                reopen(p);
            } else if (click.isRightClick()) {
                p.closeInventory();
                plugin.messages().send(p, "ex-search-ask");
                plugin.prompts().start(p, text -> {
                    state.search = text.equalsIgnoreCase("abbrechen") ? state.search : (text.equals("-") ? "" : text.trim());
                    state.page = 0;
                    reopen(p);
                });
            } else {
                state.sort = state.sort.next();
                state.page = 0;
                reopen(p);
            }
        });
        set(45, backButton(), (p, c) -> back(p));
        if (state.page > 0) {
            set(48, ItemUtil.icon(Material.ARROW, line("Vorherige Seite", NamedTextColor.YELLOW), List.of()), (p, c) -> {
                state.page--;
                reopen(p);
            });
        }
        if (state.page < pages - 1) {
            set(50, ItemUtil.icon(Material.ARROW, line("Nächste Seite (" + (state.page + 2) + "/" + pages + ")", NamedTextColor.YELLOW), List.of()), (p, c) -> {
                state.page++;
                reopen(p);
            });
        }
        switch (mode) {
            case TAKE -> buildTake(uuid);
            case PICK -> buildPick(uuid);
            case TRIP -> buildTrip(uuid);
            case WHOLESALE -> buildWholesale(uuid);
            case BREED -> buildBreed(uuid);
            case FUSION -> buildFusion(uuid);
            case VIEW -> buildView(uuid);
            case SELL -> set(49, ItemUtil.icon(Material.PAPER, line("Wen willst du loswerden?", NamedTextColor.GOLD), List.of(
                    line("Klicke einen Begleiter an. Dann wählst du:", NamedTextColor.GRAY),
                    line("versteigern oder an den Shop verkaufen.", NamedTextColor.GRAY),
                    line("Nur Begleiter, die nicht unterwegs sind.", NamedTextColor.GRAY))), null);
            case MASCOT -> {
                set(49, ItemUtil.icon(Material.PAPER, line("Dein Shop-Maskottchen", NamedTextColor.GOLD), List.of(
                        line("Klicke einen Begleiter an. Er bringt dir", NamedTextColor.GRAY),
                        line("einen kleinen Rabatt im Shop (nicht in der Welt).", NamedTextColor.GRAY),
                        line("Je seltener und höher im Level, desto mehr.", NamedTextColor.GRAY))), null);
                set(53, ItemUtil.icon(Material.BARRIER, line("Kein Maskottchen", NamedTextColor.RED),
                        List.of(line("Klicken: Maskottchen entlassen", NamedTextColor.GRAY))), (p, c) -> {
                    plugin.mascot().clear(p.getUniqueId());
                    new ProfileMenu(plugin, p, p.getUniqueId()).open(p);
                });
            }
            case TRIBUTE -> set(49, ItemUtil.icon(Material.PAPER, line("Opfer für " + (state.buy == null ? "?" : state.buy.label), NamedTextColor.GOLD), List.of(
                    line("Zu opfern: " + (state.buy == null ? 1 : plugin.expeditions().tributesNeeded(state.buy)) + " Exemplar(e), gewählt: " + state.chosen.size(), NamedTextColor.GRAY),
                    line("Klicke die Exemplare an. Mit dem letzten wird gekauft.", NamedTextColor.GRAY))), null);
        }
        fillEmpty();
    }

    private ItemStack sortSearchItem() {
        List<Component> lore = new ArrayList<>();
        lore.add(line("Sortierung: " + state.sort.label, NamedTextColor.YELLOW));
        lore.add(line(state.search == null || state.search.isBlank() ? "Suche: keine" : "Suche: \"" + state.search + "\"", NamedTextColor.AQUA));
        lore.add(line("Linksklick: Sortierung wechseln", NamedTextColor.GREEN));
        lore.add(line("Rechtsklick: suchen (Name, Typ, Seltenheit,", NamedTextColor.GREEN));
        lore.add(line("Rolle, Variante oder \"team\")", NamedTextColor.GREEN));
        lore.add(line("Umschalt-Klick: Suche und Sortierung zurücksetzen", NamedTextColor.GRAY));
        return ItemUtil.icon(Material.COMPASS, line("Sortieren und Suchen", NamedTextColor.GOLD), lore);
    }

    private void back(Player p) {
        if (mode == Mode.PICK) {
            if (plugin.expeditions().isIn(p.getUniqueId())) {
                p.closeInventory();
            } else if (state.level <= 0) {
                plugin.expeditions().cancelPending(p.getUniqueId());
                p.closeInventory();
            } else {
                new ExpeditionLevelMenu(plugin, p).open(p);
            }
        } else if (mode == Mode.TRIP) {
            new ExpeditionTripMenu(plugin, p).open(p);
        } else if (mode == Mode.TRIBUTE) {
            new ExpeditionCompanionShopMenu(plugin, p, 0).open(p);
        } else if (mode == Mode.SELL) {
            new CreatureAuctionMenu(plugin, p).open(p);
        } else if (mode == Mode.MASCOT) {
            new ProfileMenu(plugin, p, p.getUniqueId()).open(p);
        } else {
            new ExpeditionCompanionHubMenu(plugin, p).open(p);
        }
    }

    // ------------------------------------------------------------------
    //  Ein Exemplar als Item
    // ------------------------------------------------------------------

    private ItemStack copyItem(UUID uuid, Copy k, boolean selected) {
        ExpeditionManager ex = plugin.expeditions();
        Companion c = k.companion();
        int idx = k.index();
        CompanionRoster.Info info = ex.info(uuid, c, idx);
        CompanionTraits.Rarity rar = CompanionTraits.Rarity.of(info.rarity);
        int level = ex.copyLevel(uuid, c, idx);
        long kills = ex.roster().kills(uuid, c, idx);
        String busy = ex.busyReason(uuid, c, idx);
        List<Component> lore = new ArrayList<>();
        boolean inTeam = ex.taken(uuid, c, idx);
        if (mode == Mode.TAKE || mode == Mode.VIEW || mode == Mode.PICK) {
            lore.add(inTeam ? line("✔✔ IM TEAM FÜR DIE EXPEDITION ✔✔", NamedTextColor.GREEN).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true)
                    : line("✘ Nicht im Team (bleibt zuhause)", NamedTextColor.DARK_GRAY));
        }
        lore.addAll(CompanionInfo.lines(plugin, uuid, k));
        CompanionRoster.Work w = ex.roster().work(uuid, c, idx);
        if (w != null) {
            Database.WsOrder wo = plugin.database().getWsOrder(w.order);
            lore.add(line("Im Großhandel: " + (wo == null ? "Auftrag erledigt" : wo.material() + " (" + wo.delivered() + " von " + wo.amount() + ")"), NamedTextColor.GOLD));
        } else if (busy != null) {
            lore.add(line("Gerade nicht einsatzbereit: " + busy, NamedTextColor.RED));
        }
        if (ex.canHealNow(uuid, c, idx) && !ex.roster().away(uuid, c, idx)) {
            lore.add(line("Shift+Klick: sofort wieder fit machen (" + ex.healNowPrice(uuid, c, idx) + " Coins)", NamedTextColor.AQUA));
        }
        switch (mode) {
            case TAKE -> lore.add(line(selected ? "Klicken: nicht mehr mitnehmen" : "Klicken: mitnehmen", NamedTextColor.GREEN));
            case PICK -> lore.add(line(selected ? "Klicken: abwählen" : "Klicken: wieder dazunehmen", NamedTextColor.GREEN));
            case TRIP -> {
                CompanionTrips trips = ex.trips();
                lore.add(line("Stärke auf dieser Reise: " + String.format("%.1f", trips.power(uuid, c, idx, state.dest)), NamedTextColor.GOLD));
                lore.add(line("Typ gegen das Ziel: x" + String.format("%.1f", CompanionTypes.factor(c.mobType(), trips.destType(state.dest))), NamedTextColor.GRAY));
                lore.add(line(selected ? "Klicken: bleibt zuhause" : "Klicken: auf die Reise schicken", NamedTextColor.GREEN));
            }
            case WHOLESALE -> {
                Database.WsOrder cur = state.order == 0 ? null : plugin.database().getWsOrder(state.order);
                if (cur != null) {
                    double cp = ex.life().compat(c, cur.sector());
                    lore.add(line("Branche " + WholesaleManager.SECTORS.get(cur.sector()).name() + ": " + ex.life().compatLabel(cp)
                            + " (x" + String.format("%.1f", cp) + "), " + ex.life().dailyItems(uuid, c, idx, cur.sector()) + " Items pro Tag",
                            cp >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.RED));
                }
                lore.add(line(w != null ? "Klicken: vom Auftrag abziehen" : "Klicken: dem gewählten Auftrag zuteilen", NamedTextColor.GREEN));
            }
            case BREED, FUSION -> lore.add(line(selected ? "Klicken: abwählen" : "Klicken: auswählen", NamedTextColor.GREEN));
            case TRIBUTE -> lore.add(line("Klicken: dieses Exemplar opfern und kaufen", NamedTextColor.RED));
            case MASCOT -> {
                lore.add(line("Shop-Rabatt als Maskottchen: " + plugin.mascot().percentOf(uuid, k) + " %", NamedTextColor.GOLD));
                lore.add(line("Dazu mehr Belohnung bei Aufgaben und etwas Glück bei seltenen Funden.", NamedTextColor.GRAY));
                java.util.List<String> buffs = plugin.mascot().buffLines(k.companion());
                lore.add(line(buffs.isEmpty() ? "Aufgaben-Buff: keiner (passt zu keiner Aufgabenart)" : "Aufgaben-Buff (Fortschritt zählt mehr):",
                        buffs.isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA));
                for (String b : buffs) {
                    lore.add(line("  " + b, NamedTextColor.AQUA));
                }
                lore.add(line("Als Maskottchen kann er sonst nichts anderes tun.", NamedTextColor.RED));
                lore.add(line(plugin.mascot().isMascot(uuid, k.companion(), k.index()) ? "Klicken: Maskottchen entlassen" : "Klicken: als Maskottchen wählen", NamedTextColor.GREEN));
            }
            case SELL -> {
                lore.add(line("Wert: etwa " + plugin.creatureAuction().valueOf(uuid, k) + " Coins, Shop zahlt "
                        + plugin.creatureAuction().shopPrice(uuid, k), NamedTextColor.GOLD));
                lore.add(line(busy != null ? "Gerade nicht verkaufbar" : "Klicken: verkaufen oder versteigern", busy != null ? NamedTextColor.RED : NamedTextColor.GREEN));
            }
            default -> { }
        }
        boolean marked = (mode == Mode.TAKE || mode == Mode.VIEW || mode == Mode.PICK) ? inTeam || (mode == Mode.PICK && selected) : selected;
        String name = (marked ? "[TEAM] ✔ " : "") + c.label + " #" + (idx + 1) + " Lv." + level;
        ItemStack item = ItemUtil.icon(busy != null && w == null ? Material.GRAY_DYE : c.icon,
                line(name, marked ? NamedTextColor.GREEN : rar.color), lore);
        if (marked) {
            item.editMeta(m -> m.setEnchantmentGlintOverride(true));
        }
        return item;
    }

    private Action clickFor(Copy k) {
        ExpeditionManager ex = plugin.expeditions();
        return (p, click) -> {
            UUID uuid = p.getUniqueId();
            if (click.isShiftClick() && ex.canHealNow(uuid, k.companion(), k.index())
                    && (mode == Mode.TAKE || mode == Mode.VIEW || mode == Mode.PICK)) {
                ex.healNow(p, k.companion(), k.index());
                reopen(p);
                return;
            }
            switch (mode) {
                case TAKE -> {
                    ex.toggleTake(p, k.companion(), k.index());
                    reopen(p);
                }
                case PICK -> {
                    if (state.chosen.contains(k)) {
                        state.chosen.remove(k);
                    } else if (state.chosen.size() < ex.pickLimit(uuid, state.level)) {
                        state.chosen.add(k);
                    }
                    reopen(p);
                }
                case TRIP -> {
                    String busy = ex.busyReason(uuid, k.companion(), k.index());
                    if (busy != null) {
                        plugin.messages().send(p, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busy);
                    } else if (!state.chosen.remove(k)) {
                        state.chosen.add(k);
                    }
                    reopen(p);
                }
                case WHOLESALE -> {
                    if (ex.roster().work(uuid, k.companion(), k.index()) != null) {
                        ex.life().unassign(p, k);
                    } else if (state.order == 0) {
                        plugin.messages().send(p, "ex-ws-pick-first");
                    } else {
                        ex.life().assign(p, k, state.order);
                    }
                    reopen(p);
                }
                case BREED, FUSION -> {
                    String busy = ex.busyReason(uuid, k.companion(), k.index());
                    if (busy != null) {
                        plugin.messages().send(p, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busy);
                    } else if (!state.chosen.remove(k)) {
                        if (state.chosen.size() >= 2) {
                            state.chosen.clear();
                        }
                        state.chosen.add(k);
                    }
                    reopen(p);
                }
                case SELL -> {
                    String busy = ex.busyReason(uuid, k.companion(), k.index());
                    if (busy != null) {
                        plugin.messages().send(p, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busy);
                        reopen(p);
                    } else {
                        new CreatureSellMenu(plugin, p, k).open(p);
                    }
                }
                case MASCOT -> {
                    if (plugin.mascot().isMascot(uuid, k.companion(), k.index())) {
                        plugin.mascot().clear(uuid); // erneut anklicken: entlassen
                        new ProfileMenu(plugin, p, uuid).open(p);
                        return;
                    }
                    String busyMascot = ex.busyReason(uuid, k.companion(), k.index());
                    if (busyMascot != null) {
                        plugin.messages().send(p, "ex-copy-busy", "name", ex.trips().copyName(k.companion(), k.index()), "why", busyMascot);
                        reopen(p);
                        return;
                    }
                    plugin.mascot().choose(uuid, k);
                    plugin.messages().send(p, "mascot-set", "name", ex.trips().copyName(k.companion(), k.index()),
                            "percent", plugin.mascot().percentOf(uuid, k));
                    new ProfileMenu(plugin, p, uuid).open(p);
                }
                case TRIBUTE -> {
                    int need = ex.tributesNeeded(state.buy);
                    if (!state.chosen.remove(k)) {
                        state.chosen.add(k);
                    }
                    if (state.chosen.size() >= need) {
                        ex.buyCompanion(p, state.buy, new ArrayList<>(state.chosen));
                        new ExpeditionCompanionShopMenu(plugin, p, 0).open(p);
                    } else {
                        reopen(p);
                    }
                }
                default -> { }
            }
        };
    }

    // ------------------------------------------------------------------
    //  Untere Reihe je nach Zweck
    // ------------------------------------------------------------------

    private void buildTake(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        int next = ex.nextLevel(uuid);
        set(49, ItemUtil.icon(Material.WRITABLE_BOOK, line("Dein Gefolge", NamedTextColor.GOLD), List.of(
                line("Plätze: " + ex.slotsUsed(uuid) + " von " + ex.slotsUnlocked(uuid) + " freigekauft", NamedTextColor.YELLOW),
                line("Gefolge-Kosten: " + ex.deckCost(uuid) + " von " + ex.deckLimit(uuid) + " Sternen", NamedTextColor.YELLOW),
                line("Im nächsten Level höchstens " + ex.companionLimit(uuid) + " Begleiter (Kreaturen: " + ex.creaturesIn(next) + ")", NamedTextColor.GRAY),
                line("Sind es mehr, wählst du vor dem Start ab.", NamedTextColor.GRAY),
                line("Mehr Plätze erhöhen auch das Gefolge-Limit.", NamedTextColor.GRAY))), null);
        set(46, ItemUtil.icon(Material.HOPPER, line("So viele wie möglich wählen", NamedTextColor.AQUA), List.of(
                line("Die erfahrensten zuerst, bis Plätze oder Gefolge voll sind.", NamedTextColor.GRAY))), (p, c) -> {
            ex.takeAllPossible(p);
            reopen(p);
        });
        set(47, ItemUtil.icon(Material.BARRIER, line("Alle abwählen", NamedTextColor.RED), List.of()), (p, c) -> {
            ex.clearTaken(p);
            reopen(p);
        });
        set(51, ItemUtil.icon(Material.LEAD, line("Begleiter-Platz freikaufen", NamedTextColor.GOLD), List.of(
                line("Freigekauft: " + ex.slotsUnlocked(uuid) + " (unbegrenzt)", NamedTextColor.YELLOW),
                line("Nächster Platz: " + ex.slotPrice(uuid) + " Coins", NamedTextColor.GOLD),
                line("Klicken zum Freikaufen", NamedTextColor.GREEN))), (p, c) -> {
            ex.buySlot(p);
            reopen(p);
        });
    }

    private void buildPick(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        int limit = ex.pickLimit(uuid, state.level);
        int over = Math.max(0, state.chosen.size() - limit);
        set(49, ItemUtil.icon(Material.PAPER, line(state.level <= 0 ? "Verteidigung" : "Level " + state.level + " hat nur " + ex.creaturesIn(state.level) + " Kreaturen", NamedTextColor.GOLD), List.of(
                line("Es können höchstens " + limit + " Begleiter mit.", NamedTextColor.YELLOW),
                line("Gewählt: " + state.chosen.size(), over > 0 ? NamedTextColor.RED : NamedTextColor.GREEN),
                line(state.level <= 0 ? "Die Auswahl gilt nur für diese Verteidigung." : "Die Auswahl gilt nur für dieses Level.", NamedTextColor.DARK_GRAY))), null);
        if (over > 0) {
            set(52, ItemUtil.icon(Material.BARRIER, line("Noch " + over + " abwählen", NamedTextColor.RED), List.of(
                    line("Erst wenn nicht mehr als " + limit + " gewählt sind, geht es los.", NamedTextColor.GRAY))), null);
        } else {
            set(52, ItemUtil.icon(Material.LIME_DYE, line(state.level <= 0 ? "Los geht's" : "Level starten", NamedTextColor.GREEN), List.of(
                    line("Mit " + state.chosen.size() + " Begleiter(n) losgehen.", NamedTextColor.GRAY))),
                    (p, c) -> ex.confirmPick(p, state.chosen));
        }
    }

    private void buildTrip(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        CompanionTrips trips = ex.trips();
        int milestone = trips.milestone(state.dest);
        CompanionTrips.Selection sel = trips.select(uuid, state.dest, state.chosen);
        double ratio = sel.power() / trips.required(milestone);
        CompanionTypes.MobType enemy = trips.destType(state.dest);
        List<Component> sum = new ArrayList<>();
        sum.add(line("Ausgewählt: " + sel.size() + " Begleiter, Dauer " + state.hours + " Std.", NamedTextColor.YELLOW));
        sum.add(line("Gegner dort: Typ " + enemy.label, enemy.color));
        sum.add(line("Stärke: " + Math.round(sel.power()) + " von nötigen " + Math.round(trips.required(milestone)), ratio >= 1 ? NamedTextColor.GREEN : NamedTextColor.RED));
        sum.add(line("Erwartet: etwa " + trips.expectedKills(state.dest, state.hours, sel) + " Kreaturen, "
                + trips.expectedCoins(uuid, state.dest, state.hours, sel) + " Coins", NamedTextColor.GOLD));
        sum.add(line("Verletzungsrisiko je Begleiter: " + Math.round(Math.max(0, trips.injuryChance(ratio, state.hours) - sel.aSafe()) * 100) + " %", NamedTextColor.GRAY));
        sum.add(line("Dazu können Coins- und Loot-Mobs auftauchen.", NamedTextColor.DARK_GRAY));
        sum.add(line("Passende Typen (stark gegen " + enemy.label + ") zählen mehr.", NamedTextColor.DARK_GRAY));
        set(49, ItemUtil.icon(Material.PAPER, line(trips.name(state.dest) + " (Level " + milestone + ")", NamedTextColor.GOLD), sum), null);
        List<Integer> durations = trips.durations();
        int at = Math.max(0, durations.indexOf(state.hours));
        int nextHours = durations.get((at + 1) % durations.size());
        set(51, ItemUtil.icon(Material.CLOCK, line("Dauer: " + state.hours + " Std.", NamedTextColor.AQUA), List.of(
                line("Länger = mehr Beute, mehr Erfahrung.", NamedTextColor.GRAY),
                line("Klicken: " + nextHours + " Std.", NamedTextColor.GREEN))), (p, c) -> {
            state.hours = nextHours;
            reopen(p);
        });
        set(46, ItemUtil.icon(Material.HOPPER, line("Alle Verfügbaren wählen", NamedTextColor.AQUA), List.of()), (p, c) -> {
            state.chosen.clear();
            for (Copy k : ex.allCopies(uuid)) {
                if (ex.busyReason(uuid, k.companion(), k.index()) == null) {
                    state.chosen.add(k);
                }
            }
            reopen(p);
        });
        set(47, ItemUtil.icon(Material.BARRIER, line("Auswahl leeren", NamedTextColor.RED), List.of()), (p, c) -> {
            state.chosen.clear();
            reopen(p);
        });
        if (sel.size() > 0) {
            set(52, ItemUtil.icon(Material.LIME_DYE, line("Losschicken", NamedTextColor.GREEN), List.of(
                    line("Die Begleiter sind " + state.hours + " Std. unterwegs.", NamedTextColor.GRAY))), (p, c) -> {
                trips.start(p, state.dest, state.hours, new ArrayList<>(state.chosen));
                new ExpeditionTripMenu(plugin, p).open(p);
            });
        } else {
            set(52, ItemUtil.icon(Material.GRAY_DYE, line("Losschicken", NamedTextColor.DARK_GRAY),
                    List.of(line("Wähle erst Begleiter aus.", NamedTextColor.GRAY))), null);
        }
    }

    private void buildWholesale(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        CompanionLife life = ex.life();
        List<Database.WsOrder> open = new ArrayList<>();
        for (int sec = 0; sec < WholesaleManager.SECTORS.size(); sec++) {
            for (Database.WsOrder o : plugin.wholesale().orders(uuid, sec)) {
                if (o.delivered() < o.amount()) {
                    open.add(o);
                }
            }
        }
        Database.WsOrder cur = state.order == 0 ? null : plugin.database().getWsOrder(state.order);
        if (cur != null && (!cur.owner().equals(uuid) || cur.delivered() >= cur.amount())) {
            cur = null;
            state.order = 0;
        }
        List<Component> lore = new ArrayList<>();
        if (cur == null) {
            lore.add(line("Noch kein Auftrag gewählt.", NamedTextColor.RED));
        } else {
            lore.add(line(cur.material() + ": " + cur.delivered() + " von " + cur.amount(), NamedTextColor.YELLOW));
            lore.add(line("Branche: " + WholesaleManager.SECTORS.get(cur.sector()).name(), NamedTextColor.GRAY));
        }
        lore.add(line(open.isEmpty() ? "Du hast keine offenen Aufträge." : "Klicken: nächsten Auftrag wählen", open.isEmpty() ? NamedTextColor.RED : NamedTextColor.GREEN));
        Material mat = cur == null ? null : Material.matchMaterial(cur.material());
        set(46, ItemUtil.icon(mat == null ? Material.WRITABLE_BOOK : mat, line("Auftrag wählen", NamedTextColor.AQUA), lore), (p, c) -> {
            if (!open.isEmpty()) {
                int at = -1;
                for (int i = 0; i < open.size(); i++) {
                    if (open.get(i).id() == state.order) {
                        at = i;
                    }
                }
                state.order = open.get((at + 1) % open.size()).id();
            }
            reopen(p);
        });
        set(49, ItemUtil.icon(Material.PAPER, line("Begleiter im Großhandel", NamedTextColor.GOLD), List.of(
                line("Zugeteilt: " + life.assigned(uuid).size() + " (beliebig viele möglich)", NamedTextColor.YELLOW),
                line("Jeder liefert täglich beim Shop-Wechsel Items an seinen Auftrag.", NamedTextColor.GRAY),
                line("Die Menge hängt von Level, Seltenheit und Eigenschaften ab", NamedTextColor.GRAY),
                line("und davon, wie gut die Kreatur zum Thema der Branche passt.", NamedTextColor.GRAY),
                line("Experte x1,6, passt x1,0, fachfremd x0,5.", NamedTextColor.GRAY),
                line("Zugeteilte sind auf Reisen und in Leveln nicht dabei.", NamedTextColor.DARK_GRAY))), null);
    }

    private void buildBreed(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        CompanionLife life = ex.life();
        List<Copy> sel = new ArrayList<>(state.chosen);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Wähle zwei Eltern. Das Kind erbt Eigenschaften,", NamedTextColor.GRAY));
        lore.add(line("etwas Erfahrung und oft die Seltenheit.", NamedTextColor.GRAY));
        if (sel.size() == 2 && life.breedPossible(uuid, sel.get(0), sel.get(1))) {
            lore.add(line("Kind: " + life.breedPreview(uuid, sel.get(0), sel.get(1)), NamedTextColor.YELLOW));
            lore.add(line("Kosten: " + life.breedCost(sel.get(0), sel.get(1)) + " Coins", NamedTextColor.GOLD));
            lore.add(line("Beide Eltern ruhen danach " + plugin.getConfig().getInt("expedition.breed.rest-hours", 6) + " Std.", NamedTextColor.GRAY));
            set(52, ItemUtil.icon(Material.EGG, line("Züchten", NamedTextColor.GREEN), List.of(
                    line("Klicken zum Züchten", NamedTextColor.GREEN))), (p, c) -> {
                life.breed(p, sel.get(0), sel.get(1));
                state.chosen.clear();
                reopen(p);
            });
        } else {
            lore.add(line(sel.size() + " von 2 Eltern gewählt", NamedTextColor.RED));
            set(52, ItemUtil.icon(Material.GRAY_DYE, line("Züchten", NamedTextColor.DARK_GRAY), List.of()), null);
        }
        set(49, ItemUtil.icon(Material.EGG, line("Zucht", NamedTextColor.GOLD), lore), null);
    }

    private void buildFusion(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        CompanionLife life = ex.life();
        List<Copy> sel = new ArrayList<>(state.chosen);
        List<Component> lore = new ArrayList<>();
        lore.add(line("Zwei Exemplare werden zu einem neuen verschmolzen", NamedTextColor.GRAY));
        lore.add(line("(Rezepte siehe Knopf links). Zwei gleiche Arten", NamedTextColor.GRAY));
        lore.add(line("ergeben eine Veredelung: eine Seltenheit höher.", NamedTextColor.GRAY));
        lore.add(line("Beide Ausgangs-Exemplare sind danach weg!", NamedTextColor.RED));
        if (sel.size() == 2 && life.fusionPossible(uuid, sel.get(0), sel.get(1))) {
            Companion result = life.fusionResult(sel.get(0).companion(), sel.get(1).companion());
            boolean refine = sel.get(0).companion() == sel.get(1).companion();
            lore.add(line((refine ? "Veredelung zu: " : "Ergebnis: ") + result.label, NamedTextColor.YELLOW));
            set(52, ItemUtil.icon(Material.ANVIL, line(refine ? "Veredeln" : "Fusionieren", NamedTextColor.GREEN), List.of(
                    line("Klicken zum Verschmelzen", NamedTextColor.GREEN))), (p, c) -> {
                life.fuse(p, sel.get(0), sel.get(1));
                state.chosen.clear();
                reopen(p);
            });
        } else {
            lore.add(line(sel.size() == 2 ? "Diese Kombination ergibt nichts." : sel.size() + " von 2 gewählt", NamedTextColor.RED));
            set(52, ItemUtil.icon(Material.GRAY_DYE, line("Fusionieren", NamedTextColor.DARK_GRAY), List.of()), null);
        }
        set(49, ItemUtil.icon(Material.ANVIL, line("Fusion", NamedTextColor.GOLD), lore), null);
        set(46, ItemUtil.icon(Material.KNOWLEDGE_BOOK, line("Rezepte und Typen", NamedTextColor.AQUA), List.of()),
                (p, c) -> new ExpeditionTypesMenu(plugin, p).open(p));
    }

    private void buildView(UUID uuid) {
        ExpeditionManager ex = plugin.expeditions();
        int[] counts = new int[4];
        for (Copy k : ex.allCopies(uuid)) {
            counts[ex.info(uuid, k.companion(), k.index()).rarity]++;
        }
        set(49, ItemUtil.icon(Material.BOOK, line("Deine Sammlung", NamedTextColor.GOLD), List.of(
                line("Exemplare: " + ex.allCopies(uuid).size(), NamedTextColor.YELLOW),
                line("Normal: " + counts[0] + ", Selten: " + counts[1], NamedTextColor.GRAY),
                line("Episch: " + counts[2] + ", Legendär: " + counts[3], NamedTextColor.LIGHT_PURPLE),
                line("Jedes Exemplar sammelt eigene Erfahrung.", NamedTextColor.DARK_GRAY))), null);
    }

    @Override
    public void open(Player player) {
        set(53, balanceIcon(player), incomeClick());
        super.open(player);
    }
}
