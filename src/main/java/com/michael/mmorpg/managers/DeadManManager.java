package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class DeadManManager {
    private final MinecraftMMORPG plugin;
    // Map to track which players are hunting which targets
    private final Map<UUID, LivingEntity> activeTargets;

    // Constant for damage multiplier that can be configured here and accessed globally
    public static final double DAMAGE_MULTIPLIER = 1.5;  // 50% increased damage

    public DeadManManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.activeTargets = new HashMap<>();
    }

    /**
     * Marks a target for a player's Dead Man Walking effect.
     * Sets up the targeting relationship and schedules its expiration.
     *
     * @param hunter The player hunting the target
     * @param target The target being hunted
     * @param duration How long the mark should last (in ticks)
     */
    public void markTarget(Player hunter, LivingEntity target, int duration) {
        // Remove any existing target first
        removeTarget(hunter);

        // Store in our tracking map
        activeTargets.put(hunter.getUniqueId(), target);

        // Set metadata for quick checks
        hunter.setMetadata("dead_man_target", new FixedMetadataValue(plugin, target));

        // Send feedback message
        hunter.sendMessage("§6✦ The dead sailor marks " +
                (target instanceof Player ? ((Player)target).getName() : "their prey") +
                " for death!");

        // Schedule cleanup
        new BukkitRunnable() {
            @Override
            public void run() {
                removeTarget(hunter);
            }
        }.runTaskLater(plugin, duration);
    }

    /**
     * Removes a player's Dead Man Walking target.
     * Cleans up all associated metadata and tracking.
     *
     * @param hunter The player whose target should be removed
     */
    public void removeTarget(Player hunter) {
        activeTargets.remove(hunter.getUniqueId());
        if (hunter.isValid() && hunter.hasMetadata("dead_man_target")) {
            hunter.removeMetadata("dead_man_target", plugin);
            hunter.sendMessage("§6✦ The dead sailor's influence fades...");
        }
    }

    /**
     * Gets the marked target for a player.
     *
     * @param hunter The player to check
     * @return The marked target, or null if none exists
     */
    public LivingEntity getMarkedTarget(Player hunter) {
        return activeTargets.get(hunter.getUniqueId());
    }

    /**
     * Checks if a player has a valid marked target.
     * Validates both that a target exists and is still valid.
     *
     * @param hunter The player to check
     * @return true if they have a valid target
     */
    public boolean hasValidTarget(Player hunter) {
        LivingEntity target = activeTargets.get(hunter.getUniqueId());
        return target != null && target.isValid() && !target.isDead();
    }

    /**
     * Calculates the damage multiplier that should be applied when the given hunter
     * attacks the given target. Returns the DAMAGE_MULTIPLIER if the target is marked,
     * otherwise returns 1.0 for normal damage.
     *
     * @param hunter The attacking player
     * @param target The entity being attacked
     * @return The damage multiplier to apply
     */
    public double getDamageMultiplier(Player hunter, LivingEntity target) {
        LivingEntity markedTarget = getMarkedTarget(hunter);
        if (markedTarget != null && markedTarget.equals(target)) {
            return DAMAGE_MULTIPLIER;
        }
        return 1.0;
    }

    /**
     * Checks if an entity is currently marked as anyone's target.
     * Useful for determining if special effects or protections should apply.
     *
     * @param entity The entity to check
     * @return true if the entity is someone's target
     */
    public boolean isAnyonesTarget(LivingEntity entity) {
        return activeTargets.containsValue(entity);
    }

    /**
     * Gets all players who are currently targeting the given entity.
     * Useful for updating all hunters if their target dies or becomes invalid.
     *
     * @param entity The entity to check
     * @return List of players targeting this entity
     */
    public List<Player> getHuntersTargeting(LivingEntity entity) {
        return activeTargets.entrySet().stream()
                .filter(entry -> entry.getValue().equals(entity))
                .map(entry -> plugin.getServer().getPlayer(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Cleanup method called when the plugin disables.
     * Ensures all targets are properly removed and players are notified.
     */
    public void cleanup() {
        // Make a copy of the keys to avoid concurrent modification
        for (UUID hunterId : new HashSet<>(activeTargets.keySet())) {
            Player hunter = plugin.getServer().getPlayer(hunterId);
            if (hunter != null) {
                removeTarget(hunter);
            }
        }
        activeTargets.clear();
    }

    /**
     * Called when an entity dies to clean up any targeting relationships.
     * Should be called from your combat or death handling system.
     *
     * @param entity The entity that died
     */
    public void handleEntityDeath(LivingEntity entity) {
        // Find all players targeting this entity and remove their marks
        getHuntersTargeting(entity).forEach(this::removeTarget);
    }
}