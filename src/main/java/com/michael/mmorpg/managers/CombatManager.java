package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CombatManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Long> combatTimers;
    private final Map<UUID, Set<UUID>> combatTargets;
    private static final long COMBAT_DURATION = 10000; // 10 seconds in milliseconds

    public CombatManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.combatTimers = new HashMap<>();
        this.combatTargets = new HashMap<>();
        startCombatCheckTask();
    }

    public void enterCombat(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();

        // Check if player was already in combat
        boolean wasInCombat = isInCombat(player);

        // Update combat timer
        combatTimers.put(playerId, System.currentTimeMillis() + COMBAT_DURATION);

        // Update combat targets
        combatTargets.computeIfAbsent(playerId, k -> new HashSet<>()).add(targetId);

        // Only send message if player wasn't already in combat
        if (!wasInCombat) {
            player.sendMessage("§c§l⚔ You have entered combat!");
        }

    }

    public void exitCombat(Player player) {
        UUID playerId = player.getUniqueId();
        combatTimers.remove(playerId);
        combatTargets.remove(playerId);
        player.sendMessage("§a§l⚔ You have left combat!");
    }

    public boolean isInCombat(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (combatTimers.containsKey(playerId)) {
            if (currentTime < combatTimers.get(playerId)) {
                return true;
            } else {
                exitCombat(player);
            }
        }
        return false;
    }

    /**
     * Check if a player has another player as an active combat target
     * Used to determine if PvP should be allowed in safe zones
     *
     * @param player The player to check
     * @param target The potential target
     * @return true if player has target tagged in combat
     */
    public boolean hasTarget(Player player, Player target) {
        UUID playerId = player.getUniqueId();
        UUID targetId = target.getUniqueId();

        // Get player's combat targets
        Set<UUID> targets = combatTargets.get(playerId);
        if (targets == null || targets.isEmpty()) {
            return false;
        }

        // Check if target is in the combat targets
        return targets.contains(targetId);
    }

    /**
     * Gets the remaining combat time for a player in seconds
     * Useful for displaying countdown to players
     *
     * @param player The player to check
     * @return Remaining combat time in seconds, rounded up
     */
    public int getRemainingCombatTimeSeconds(Player player) {
        UUID playerId = player.getUniqueId();
        Long endTime = combatTimers.get(playerId);

        if (endTime != null) {
            long currentTime = System.currentTimeMillis();
            long remainingMillis = Math.max(0, endTime - currentTime);
            // Convert to seconds, rounding up
            return (int) Math.ceil(remainingMillis / 1000.0);
        }

        return 0;
    }


    public boolean areMutuallyInCombat(Player player1, Player player2) {
        return hasTarget(player1, player2) || hasTarget(player2, player1);
    }

    public void handleTargetDeath(LivingEntity target) {
        UUID targetId = target.getUniqueId();

        for (Map.Entry<UUID, Set<UUID>> entry : new HashMap<>(combatTargets).entrySet()) {
            UUID playerId = entry.getKey();
            Set<UUID> targets = entry.getValue();

            targets.remove(targetId);

            if (targets.isEmpty()) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    exitCombat(player);
                }
            }
        }
    }

    public void updateCombatTimer(Player player) {
        combatTimers.put(player.getUniqueId(), System.currentTimeMillis() + COMBAT_DURATION);
    }

    /**
     * Get the remaining combat time for a player in milliseconds
     *
     * @param player The player to check
     * @return Remaining combat time in milliseconds, or 0 if not in combat
     */
    public long getRemainingCombatTime(Player player) {
        UUID playerId = player.getUniqueId();
        Long endTime = combatTimers.get(playerId);

        if (endTime != null) {
            long currentTime = System.currentTimeMillis();
            return Math.max(0, endTime - currentTime);
        }

        return 0;
    }

    private void startCombatCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Set<UUID> toRemove = new HashSet<>();

                new HashMap<>(combatTimers).forEach((uuid, endTime) -> {
                    if (currentTime >= endTime) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player != null) {
                            exitCombat(player);
                        } else {
                            toRemove.add(uuid);
                        }
                    }
                });

                // Remove any remaining entries
                toRemove.forEach(combatTimers::remove);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }


}