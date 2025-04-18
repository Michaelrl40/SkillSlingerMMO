package com.michael.mmorpg.skills.skyknight;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CloudbornSkill extends Skill {
    private static final int MAX_FLIGHT_TICKS = 10 * 20; // 10 seconds in ticks
    private static final Map<UUID, BukkitTask> flightTasks = new HashMap<>();
    private static final Map<UUID, Integer> flightTimers = new HashMap<>();
    private static final ItemStack ELYTRA = createIndestructibleElytra();

    public CloudbornSkill(ConfigurationSection config) {
        super(config);
    }

    @Override
    protected void performSkill(Player player) {
        // This is a passive skill, so this method won't be directly called
    }

    public static void initializePassive(Player player) {
        // Ensure player has elytra equipped
        if (player.getInventory().getChestplate() == null ||
                player.getInventory().getChestplate().getType() != Material.ELYTRA) {
            player.getInventory().setChestplate(ELYTRA);
        }

        // Start monitoring flight if not already monitoring
        if (!flightTasks.containsKey(player.getUniqueId())) {
            startFlightMonitoring(player);
        }
    }

    public static void removePassive(Player player) {
        // Clean up flight monitoring
        stopFlightMonitoring(player);

        // Remove elytra if it was equipped by this skill
        if (player.getInventory().getChestplate() != null &&
                player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            player.getInventory().setChestplate(null);
        }
    }

    private static void startFlightMonitoring(Player player) {
        UUID playerId = player.getUniqueId();
        flightTimers.put(playerId, 0);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    stopFlightMonitoring(player);
                    return;
                }

                if (player.isGliding()) {
                    // Increment flight timer
                    int currentTime = flightTimers.getOrDefault(playerId, 0);
                    flightTimers.put(playerId, currentTime + 1);

                    // Check if exceeded max flight time
                    // Check for 3-second warning
                    if (currentTime == MAX_FLIGHT_TICKS - (3 * 20)) { // 3 seconds before end
                        player.sendMessage("§c⚠ §eWarning: Flight time ending in 3 seconds!");
                    }

                    if (currentTime >= MAX_FLIGHT_TICKS) {
                        forceEndFlight(player);
                    }
                } else if (player.isOnGround()) {
                    // Reset timer when on ground
                    flightTimers.put(playerId, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        flightTasks.put(playerId, task);
    }

    private static void stopFlightMonitoring(Player player) {
        UUID playerId = player.getUniqueId();
        if (flightTasks.containsKey(playerId)) {
            flightTasks.get(playerId).cancel();
            flightTasks.remove(playerId);
        }
        flightTimers.remove(playerId);
    }

    private static void forceEndFlight(Player player) {
        // Only force end if in survival or adventure mode
        if (player.getGameMode() == GameMode.SURVIVAL ||
                player.getGameMode() == GameMode.ADVENTURE) {
            player.setGliding(false);
            player.sendMessage("§c✦ Your flight time has expired!");
        }
    }

    public static boolean hasRemainingFlightTime(Player player) {
        return flightTimers.getOrDefault(player.getUniqueId(), 0) < MAX_FLIGHT_TICKS;
    }

    public static int getRemainingFlightTime(Player player) {
        int usedTime = flightTimers.getOrDefault(player.getUniqueId(), 0);
        return Math.max(0, MAX_FLIGHT_TICKS - usedTime);
    }

    // Clean up method for plugin disable
    private static ItemStack createIndestructibleElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        org.bukkit.inventory.meta.ItemMeta meta = elytra.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.setDisplayName("§b❋ Cloudborn Wings");
            elytra.setItemMeta(meta);
        }
        return elytra;
    }

    public static void cleanup() {
        for (BukkitTask task : flightTasks.values()) {
            task.cancel();
        }
        flightTasks.clear();
        flightTimers.clear();
    }
}