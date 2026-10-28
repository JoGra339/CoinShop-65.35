package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lädt die Texte aus messages.yml und baut daraus Nachrichten.
 *
 * Benutzung im Code, zum Beispiel:
 *   messages.send(player, "pay-sent", "coins", 50L, "player", "Steve");
 * Die Paare am Ende sind "Platzhalter-Name", "Wert". In messages.yml steht dann z. B. <coins>.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    // Zahlen deutsch formatieren: 1234567 -> 1.234.567
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.GERMANY);

    private final CoinShopPlugin plugin;
    private FileConfiguration config = new YamlConfiguration();

    public Messages(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Neu) laden. Fehlende Einträge werden aus der Standard-Datei im Plugin ergänzt. */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
    }

    public static String number(long value) {
        return NUMBERS.format(value);
    }

    /** Eine Nachricht ohne Präfix (z. B. für Menü-Namen). */
    public Component get(String key, Object... placeholders) {
        String text = config.getString(key);
        if (text == null) {
            return Component.text("[fehlender Text: " + key + "]");
        }
        return MM.deserialize(text, resolvers(placeholders));
    }

    /** Mehrere Zeilen (für Beschreibungen im Menü). */
    public List<Component> getList(String key, Object... placeholders) {
        List<Component> result = new ArrayList<>();
        TagResolver[] resolvers = resolvers(placeholders);
        for (String line : config.getStringList(key)) {
            result.add(MM.deserialize(line, resolvers));
        }
        return result;
    }

    /** Sendet eine Nachricht mit Präfix ("Coins » ...") an einen Spieler oder die Konsole. */
    public void send(CommandSender to, String key, Object... placeholders) {
        to.sendMessage(get("prefix").append(get(key, placeholders)));
    }

    /** Sendet eine Nachricht ohne Präfix. */
    public void sendPlain(CommandSender to, String key, Object... placeholders) {
        to.sendMessage(get(key, placeholders));
    }

    private static TagResolver[] resolvers(Object[] placeholders) {
        List<TagResolver> list = new ArrayList<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];
            if (value instanceof Component component) {
                list.add(Placeholder.component(name, component));
            } else if (value instanceof Long || value instanceof Integer) {
                list.add(Placeholder.unparsed(name, number(((Number) value).longValue())));
            } else {
                list.add(Placeholder.unparsed(name, String.valueOf(value)));
            }
        }
        return list.toArray(new TagResolver[0]);
    }
}
