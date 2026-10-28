package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import de.coinshop.TitleManager;
import de.coinshop.TitleManager.Kind;
import de.coinshop.TitleManager.TitleDef;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Titel-Auswahl. Alle Titel sind sichtbar: besessene lassen sich anklicken (dann trägt man sie),
 * die anderen sind grau und zeigen, wie man sie bekommt.
 */
public final class TitleMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    /** Titel zu Schwarzmarkt oder Großhandel sieht nur, wer den Bereich freigeschaltet hat (oder den Titel besitzt). */
    private static boolean visible(CoinShopPlugin plugin, TitleManager.TitleDef def, Player viewer) {
        String type = def.effect().type();
        boolean black = def.id().equals("pate") || def.id().equals("unterhaendler")
                || type.equals("black-chance") || type.equals("bounty-bonus");
        boolean wholesale = def.id().equals("ceo") || def.id().equals("gesellschafter") || type.equals("ws-order-size");
        if (black && !plugin.blackMarket().unlocked(viewer)) {
            return false;
        }
        return !wholesale || plugin.wholesale().unlocked(viewer);
    }

    public TitleMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 6, plugin.messages().get("gui.title.title"));
        Messages msg = plugin.messages();
        TitleManager titles = plugin.titles();
        UUID uuid = viewer.getUniqueId();

        Set<String> owned = titles.owned(uuid);
        String choice = titles.choice(uuid);
        TitleDef active = titles.effective(uuid);
        Map<String, Set<UUID>> holders = plugin.database().getRankTitleHolders();

        int i = 0;
        for (TitleDef def : titles.all()) {
            if (i >= SLOTS.length) {
                break;
            }
            boolean has = owned.contains(def.id());
            if (!has && !visible(plugin, def, viewer)) {
                continue; // Titel zu Bereichen, die der Spieler noch nicht freigeschaltet hat, bleiben unsichtbar
            }
            List<Component> lore = new ArrayList<>();
            lore.add(description(def));
            if (!def.effect().text().isEmpty()) {
                lore.add(msg.get("gui.title.effect-line", "text", def.effect().text()));
            }
            if (def.kind() != Kind.PERMANENT) {
                lore.add(holderLine(def, holders));
            }
            boolean sizeTitle = def.effect().type().equals("ws-order-size");
            if (sizeTitle && has) {
                boolean larger = plugin.wholesale().orderSizeLarger(uuid);
                lore.add(msg.get(larger ? "gui.title.size-larger" : "gui.title.size-smaller"));
                lore.add(msg.get("gui.title.size-hint"));
            }
            lore.add(Component.empty());

            if (has) {
                boolean isActive = active != null && active.id().equals(def.id());
                lore.add(isActive ? msg.get("gui.title.active") : msg.get("gui.title.click-to-wear"));
                set(SLOTS[i++], ItemUtil.icon(def.icon(), titles.nameOf(def).decoration(
                                net.kyori.adventure.text.format.TextDecoration.ITALIC, false), lore),
                        (player, click) -> {
                            if (sizeTitle && click.isRightClick()) {
                                plugin.wholesale().toggleOrderSize(player.getUniqueId());
                                titles.refresh(player);
                            } else {
                                titles.select(player, def.id());
                            }
                            new TitleMenu(plugin, player).open(player);
                        });
            } else {
                lore.add(msg.get("gui.title.locked"));
                set(SLOTS[i++], ItemUtil.icon(Material.GRAY_DYE,
                        msg.get("gui.title.locked-name", "title", titles.nameOf(def)), lore), null);
            }
        }

        // Unten: Automatisch | Zurück | Kein Titel
        set(47, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.title.auto-name"),
                msg.getList(choice.equals("auto") ? "gui.title.auto-lore-active" : "gui.title.auto-lore")),
                (player, click) -> {
                    titles.select(player, "auto");
                    new TitleMenu(plugin, player).open(player);
                });
        set(49, backButton(), (player, click) -> new MainMenu(plugin).open(player));
        set(51, ItemUtil.icon(Material.BARRIER, msg.get("gui.title.none-name"),
                msg.getList(choice.equals("none") ? "gui.title.none-lore-active" : "gui.title.none-lore")),
                (player, click) -> {
                    titles.select(player, "none");
                    new TitleMenu(plugin, player).open(player);
                });
        set(45, balanceIcon(viewer), incomeClick());

        // Kurzer Überblick oben
        set(4, ItemUtil.icon(Material.NAME_TAG, msg.get("gui.title.summary-name"),
                msg.getList("gui.title.summary-lore", "have", owned.size(), "total", titles.all().size(),
                        "current", active == null ? Component.text("-") : titles.nameOf(active))), null);
        fillEmpty();
    }

    private Component description(TitleDef def) {
        Messages msg = plugin.messages();
        return switch (def.kind()) {
            case RICHEST -> msg.get("titles.desc-richest");
            case RANK -> msg.get("titles.desc-rank", "rank", msg.get("gui.rank.cat." + def.rankId() + ".name"));
            case PERMANENT -> msg.get("titles.desc-perm-" + def.type(), "value", def.value());
            case PATE -> msg.get("titles.desc-pate");
            case ROLE -> msg.get("titles.desc-" + def.id());
        };
    }

    private Component holderLine(TitleDef def, Map<String, Set<UUID>> holders) {
        Messages msg = plugin.messages();
        Set<UUID> set = holders.get(def.id());
        if (set == null || set.isEmpty()) {
            return msg.get("gui.title.holder-none");
        }
        List<String> names = new ArrayList<>();
        for (UUID holder : set) {
            names.add(plugin.database().getName(holder));
        }
        return msg.get("gui.title.holder", "player", String.join(", ", names));
    }
}
