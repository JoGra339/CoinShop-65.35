package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

/** Zeigt die Anzahl der Tode jedes Spielers in der Tab-Liste neben dem Namen. */
public final class TabDeaths implements Listener {

    private static final String NAME = "coinshop_tode";
    private final CoinShopPlugin plugin;

    public TabDeaths(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private Objective objective() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = board.getObjective(NAME);
        if (objective == null) {
            objective = board.registerNewObjective(NAME, Criteria.DUMMY, Component.text("Tode", NamedTextColor.RED), RenderType.INTEGER);
        }
        objective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        return objective;
    }

    private void update(Player player) {
        if (!plugin.getConfig().getBoolean("tab-deaths.enabled", true)) {
            return;
        }
        objective().getScore(player.getName()).setScore(player.getStatistic(Statistic.DEATHS));
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("tab-deaths.enabled", true)) {
            return;
        }
        objective();
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> update(player));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(player), 2L);
    }
}
