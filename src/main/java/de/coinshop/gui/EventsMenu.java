package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.EventManager;
import de.coinshop.EventManager.Event;
import de.coinshop.FlashEventManager;
import de.coinshop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Alle heute aktiven Ereignisse zum Nachlesen (jeder sieht nur, was er auch im Chat gelesen hätte). */
public final class EventsMenu extends Menu {

    public EventsMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, plugin.messages().get("gui.events.title"));
        EventManager events = plugin.events();
        boolean bm = plugin.blackMarket().unlocked(viewer);
        boolean ws = plugin.wholesale().unlocked(viewer);

        List<Object[]> entries = new ArrayList<>(); // {Material, Titel, positiv, Texte}
        Event general = events.current();
        if (general != null) {
            List<String> texts = new ArrayList<>();
            texts.add(plain("event-" + general.id));
            if (bm && EventManager.hasExtra(general, "bm")) {
                texts.add(plain("event-" + general.id + "-bm"));
            }
            if (ws && EventManager.hasExtra(general, "wh")) {
                texts.add(plain("event-" + general.id + "-wh"));
            }
            entries.add(new Object[]{Material.GOLD_INGOT, general.title, general.positive, texts});
        }
        Event black = events.currentBlack();
        if (black != null) {
            entries.add(new Object[]{Material.WITHER_SKELETON_SKULL, bm ? black.title : EventManager.shopTitle(black),
                    black.positive, List.of(plain(bm ? "event-" + black.id : "event-" + black.id + "-shop"))});
        }
        Event wholesale = events.currentWholesale();
        if (wholesale != null) {
            List<String> texts = new ArrayList<>();
            if (ws) {
                texts.add(plain("event-" + wholesale.id));
            } else {
                texts.add(plain("event-" + wholesale.id + "-shop"));
                if (bm) {
                    texts.add(plain("event-" + wholesale.id + "-bm"));
                }
            }
            entries.add(new Object[]{Material.BARREL, ws ? wholesale.title : EventManager.shopTitle(wholesale),
                    wholesale.positive, texts});
        }
        FlashEventManager.Flash flash = plugin.flash().current();
        if (flash != null) {
            entries.add(new Object[]{Material.LIGHTNING_ROD, flash.title, true,
                    List.of("Blitz-Ereignis: gilt nur für kurze Zeit.")});
        }

        if (entries.isEmpty()) {
            set(13, ItemUtil.icon(Material.BARRIER, plugin.messages().get("gui.events.none-name"),
                    plugin.messages().getList("gui.events.none-lore")), null);
        }
        int[] slots = entries.size() <= 2 ? new int[]{11, 15} : new int[]{10, 12, 14, 16};
        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            Object[] e = entries.get(i);
            NamedTextColor color = (Boolean) e[2] ? NamedTextColor.GREEN : NamedTextColor.RED;
            List<Component> lore = new ArrayList<>();
            @SuppressWarnings("unchecked")
            List<String> texts = (List<String>) e[3];
            for (String text : texts) {
                for (String line : wrap(text, 42)) {
                    lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
            }
            set(slots[i], ItemUtil.icon((Material) e[0],
                    Component.text((String) e[1], color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                    lore), null);
        }
        set(18, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }

    private String plain(String key) {
        return PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(key));
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > width && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
