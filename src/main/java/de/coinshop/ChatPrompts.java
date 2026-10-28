package de.coinshop;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Wartet auf die nächste Chat-Nachricht eines Spielers (z. B. für den Preis beim Einstellen
 * oder den Spielernamen beim Übertragen eines Investment-Items).
 *
 * Ablauf: start(...) merkt sich, was mit der nächsten Nachricht passieren soll. Der Chat-Listener
 * fängt die Nachricht ab (sie erscheint nicht im öffentlichen Chat) und ruft handle(...) auf.
 */
public final class ChatPrompts {

    private static final long TIMEOUT_MS = 60_000L;

    private record Prompt(long expires, Consumer<String> handler) { }

    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    /** Ab jetzt wird die nächste Chat-Nachricht des Spielers an "handler" übergeben (60 Sekunden lang). */
    public void start(Player player, Consumer<String> handler) {
        prompts.put(player.getUniqueId(), new Prompt(System.currentTimeMillis() + TIMEOUT_MS, handler));
    }

    /** Darf von jedem Thread aufgerufen werden (nur Lesezugriff). */
    public boolean isWaiting(Player player) {
        Prompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null) {
            return false;
        }
        if (prompt.expires() < System.currentTimeMillis()) {
            prompts.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void cancel(Player player) {
        prompts.remove(player.getUniqueId());
    }

    /** Muss im Haupt-Thread des Servers aufgerufen werden. */
    public void handle(Player player, String text) {
        Prompt prompt = prompts.remove(player.getUniqueId());
        if (prompt == null || prompt.expires() < System.currentTimeMillis()) {
            return;
        }
        prompt.handler().accept(text);
    }
}
