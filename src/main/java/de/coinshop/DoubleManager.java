package de.coinshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * "Doppelt oder Nichts": Du zahlst einen Einsatz und darfst ihn immer wieder verdoppeln.
 * Jede Verdopplung klappt nur mit einer bestimmten Chance (Standard 40 %). Klappt sie nicht, ist alles weg
 * (ab einer bestimmten Stufe gibt es einen kleinen Trostpreis). Du kannst jederzeit auszahlen lassen.
 *
 * Der Einsatz geht sofort aufs Konto des Shops, Gewinne zahlt das Konto des Shops (höchstens so viel, wie
 * darauf ist). Wer den Server verlässt, bekommt seinen aktuellen Gewinn automatisch ausgezahlt.
 */
public final class DoubleManager {

    /** Ein laufendes Spiel: Einsatz und wie oft schon verdoppelt wurde. */
    public record Session(String name, long stake, int step) {
        public long pot() {
            return stake << step; // Einsatz x 2^step
        }
    }

    /** Ergebnis eines Verdopplungs-Versuchs. */
    public record Result(boolean won, boolean finished, long payout, Session session) { }

    private final CoinShopPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Session> sessions = new HashMap<>();

    public DoubleManager(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    //  Einstellungen (config.yml, casino.double)
    // ------------------------------------------------------------------

    public List<Long> stakes() {
        List<Long> list = new ArrayList<>();
        for (long stake : plugin.getConfig().getLongList("casino.double.stakes")) {
            if (stake > 0 && list.size() < 3) {
                list.add(stake);
            }
        }
        if (list.isEmpty()) {
            list.add(10L);
        }
        return list;
    }

    /** Chance in Prozent, dass eine Verdopplung klappt. */
    public double winChance() {
        return Math.max(1, Math.min(99, plugin.getConfig().getDouble("casino.double.win-chance-percent", 40)));
    }

    /** Nach so vielen Verdopplungen ist Schluss und es wird automatisch ausgezahlt (höchstens 9). */
    public int maxSteps() {
        return Math.max(1, Math.min(9, plugin.getConfig().getInt("casino.double.max-steps", 7)));
    }

    /** Ab dieser Stufe gibt es bei einer Niederlage einen Trostpreis (0 = nie). */
    public int consolationFromStep() {
        return Math.max(0, plugin.getConfig().getInt("casino.double.consolation-from-step", 3));
    }

    /** Trostpreis in Prozent des Einsatzes. */
    public int consolationPercent() {
        return Math.max(0, plugin.getConfig().getInt("casino.double.consolation-percent", 50));
    }

    public Session session(UUID uuid) {
        return sessions.get(uuid);
    }

    // ------------------------------------------------------------------
    //  Spielen
    // ------------------------------------------------------------------

    /** Zahlt den Einsatz. Gibt false zurück, wenn es nicht geht (die Nachricht wurde schon gesendet). */
    public boolean start(Player player, long stake) {
        Messages msg = plugin.messages();
        UUID uuid = player.getUniqueId();
        if (sessions.containsKey(uuid) || !stakes().contains(stake)) {
            return false;
        }
        Database db = plugin.database();
        boolean free = false;
        db.begin();
        try {
            // Lizenz "freispiel": der erste Einsatz des Tages ist gratis
            if (plugin.licenses().consumeFreePlay(uuid)) {
                free = true;
            } else if (!db.takeCoins(uuid, stake)) {
                db.rollback();
                msg.send(player, "wheel-not-enough", "price", stake);
                return false;
            }
            if (!free) {
                db.addShopAccount(stake);
            }
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei Doppelt oder Nichts: " + e.getMessage());
            msg.send(player, "error");
            return false;
        }
        db.logTx(uuid, player.getName(), "doppelt-einsatz", free ? 0 : -stake, "Einsatz " + stake + (free ? " (Freispiel)" : ""));
        if (free) {
            msg.send(player, "freeplay-used");
        }
        sessions.put(uuid, new Session(player.getName(), stake, 0));
        plugin.secrets().onCasinoStart(uuid);
        return true;
    }

    /** Versucht zu verdoppeln. Gibt null zurück, wenn kein Spiel läuft. */
    public Result gamble(Player player) {
        UUID uuid = player.getUniqueId();
        Session s = sessions.get(uuid);
        if (s == null) {
            return null;
        }
        boolean won = random.nextDouble() * 100.0 < winChance();
        if (won) {
            Session next = new Session(s.name(), s.stake(), s.step() + 1);
            if (next.step() >= maxSteps()) {
                sessions.remove(uuid);
                long paid = payout(uuid, s.name(), next.pot(), "Höchststufe");
                plugin.secrets().onCasinoResult(uuid, false);
                return new Result(true, true, paid, next);
            }
            sessions.put(uuid, next);
            return new Result(true, false, 0, next);
        }
        // Verloren
        sessions.remove(uuid);
        long consolation = 0;
        if (consolationFromStep() > 0 && s.step() >= consolationFromStep()) {
            consolation = s.stake() * consolationPercent() / 100;
        }
        long paid = consolation > 0 ? payout(uuid, s.name(), consolation, "Trostpreis") : 0;
        plugin.secrets().onCasinoResult(uuid, true);
        return new Result(false, true, paid, s);
    }

    /** Zahlt den aktuellen Gewinn aus und beendet das Spiel. Gibt den ausgezahlten Betrag zurück. */
    public long cashOut(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s == null || s.step() <= 0) {
            return 0; // ohne eine Verdopplung gibt es nichts auszuzahlen
        }
        sessions.remove(uuid);
        plugin.secrets().onCasinoResult(uuid, false);
        return payout(uuid, s.name(), s.pot(), "Auszahlung");
    }

    /** Spieler verlässt den Server: laufende Spiele werden ausgezahlt (Stufe 0 = Einsatz bleibt beim Shop). */
    public void leave(UUID uuid) {
        if (sessions.containsKey(uuid)) {
            cashOut(uuid);
            sessions.remove(uuid);
        }
    }

    /** Beim Ausschalten des Servers. */
    public void cashOutAll() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            try {
                leave(uuid);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Doppelt oder Nichts: Auszahlung fehlgeschlagen: " + e);
            }
        }
    }

    /** Zahlt vom Shop-Konto an den Spieler (höchstens so viel, wie auf dem Konto ist). */
    private long payout(UUID uuid, String name, long amount, String detail) {
        Database db = plugin.database();
        db.begin();
        try {
            long pay = Math.min(amount, db.getShopAccount());
            if (pay > 0) {
                db.takeShopAccount(pay);
                db.addCoins(uuid, pay);
            }
            db.commit();
            if (pay > 0) {
                db.logTx(uuid, name, "doppelt-gewinn", pay, detail);
            }
            return pay;
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Fehler bei der Auszahlung (Doppelt oder Nichts): " + e.getMessage());
            return 0;
        }
    }

    /** Für die Anzeige im Menü: Gewinn nach "step" Verdopplungen. */
    public long potAt(long stake, int step) {
        return stake << step;
    }

    public Random random() {
        return random;
    }

    /** Für die Ansage an alle bei einer großen Serie. */
    public void broadcastMax(String playerName, long coins) {
        Messages msg = plugin.messages();
        Bukkit.getServer().broadcast(msg.get("prefix").append(
                msg.get("double-max-broadcast", "player", playerName, "coins", coins)));
    }
}
