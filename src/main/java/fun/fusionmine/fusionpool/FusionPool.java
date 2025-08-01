package fun.fusionmine.fusionpool;

import net.raidstone.wgevents.WorldGuardEvents;
import net.raidstone.wgevents.events.RegionEnteredEvent;
import net.raidstone.wgevents.events.RegionLeftEvent;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FusionPool extends JavaPlugin implements Listener {

    private final Map<Player, Long> activePlayers = new ConcurrentHashMap<>();

    private BukkitTask progressTask;

    // Configuration values
    private String worldName;
    private String regionName;
    private int requiredStaySeconds;
    private int rewardAmount;
    private int updateIntervalSeconds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();
        startProgressTracking();

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (progressTask != null) {
            progressTask.cancel();
        }
        activePlayers.clear();
    }

    private void loadConfiguration() {
        worldName = getConfig().getString("world");
        regionName = getConfig().getString("region");
        requiredStaySeconds = getConfig().getInt("time");
        rewardAmount = getConfig().getInt("ruble_reward");
        updateIntervalSeconds = getConfig().getInt("period");
    }

    private void startProgressTracking() {
        progressTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if(activePlayers.isEmpty()) return;

            long currentTime = System.currentTimeMillis();
            activePlayers.forEach((player, time) -> {
                long elapsedSeconds = (currentTime - time) / 1000;
                double progressPercent = Math.min(100, (double) elapsedSeconds / requiredStaySeconds * 100);

                updatePlayerProgress(player, progressPercent);

                if (elapsedSeconds >= requiredStaySeconds) {
                    PlayerPoints.getInstance().getAPI().give(player.getUniqueId(), rewardAmount);
                    // Resetting the timer for the next cycle
                    activePlayers.put(player, currentTime);
                }
            });
        }, 0L, updateIntervalSeconds * 20L);
    }

    private void updatePlayerProgress(Player player, double progressPercent) {
        int filledSections = (int) (progressPercent / 10);
        StringBuilder progressBar = new StringBuilder("§8[");

        for (int i = 0; i < 10; i++) {
            progressBar.append(i < filledSections ? "§a" : "§7").append("|");
        }

        progressBar.append("§8]");
        String percentageText = String.format("§c%.0f%%", progressPercent);

        player.sendTitle(progressBar.toString(), percentageText, 0, updateIntervalSeconds * 20 + 10, 0);
    }

    // WorldGuard Events
    @EventHandler
    public void onRegionEnter(RegionEnteredEvent event) {
        Player player = event.getPlayer();
        if (event.getRegionName().equalsIgnoreCase(regionName) && player.getWorld().getName().equalsIgnoreCase(worldName)) {
            activePlayers.put(player, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onRegionLeave(RegionLeftEvent event) {
        Player player = event.getPlayer();
        if (event.getRegionName().equalsIgnoreCase(regionName) && player.getWorld().getName().equalsIgnoreCase(worldName)) {
            activePlayers.remove(player);
        }
    }

    // Player Events
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getName().equalsIgnoreCase(worldName) && WorldGuardEvents.isPlayerInAllRegions(player.getUniqueId(), regionName))
            this.activePlayers.put(player, System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activePlayers.remove(event.getPlayer());
    }

}

