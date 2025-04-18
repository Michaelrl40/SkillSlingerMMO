package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages custom absorption shields as a replacement for vanilla absorption
 */
public class AbsorptionShieldManager {
    private final MinecraftMMORPG plugin;

    // Map to track cooldowns for consumables
    private final Map<UUID, Map<ConsumableType, Long>> cooldowns = new HashMap<>();

    // Cooldown durations in milliseconds
    private static final long GOLDEN_APPLE_COOLDOWN = 120 * 1000; // 2 mins
    private static final long ENCHANTED_APPLE_COOLDOWN = 5 * 60 * 1000; // 5 minutes
    private static final long TOTEM_COOLDOWN = 10 * 60 * 1000; // 10 minutes

    // Shield values
    private static final double GOLDEN_APPLE_SHIELD = 30.0; // Significant shield
    private static final double ENCHANTED_APPLE_SHIELD = 80.0; // Major shield
    private static final double TOTEM_SHIELD = 50.0; // Medium shield // 5 hearts

    public enum ConsumableType {
        GOLDEN_APPLE,
        ENCHANTED_APPLE,
        TOTEM
    }

    public AbsorptionShieldManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies a custom absorption shield to a player
     * @param player The player to apply the shield to
     * @param type The type of consumable used
     * @return true if the shield was applied, false if on cooldown
     */
    public boolean applyAbsorptionShield(Player player, ConsumableType type) {
        // Check if the consumable is on cooldown
        if (isOnCooldown(player.getUniqueId(), type)) {
            // Calculate remaining cooldown
            long remainingCooldown = getRemainingCooldown(player.getUniqueId(), type);
            long remainingSeconds = remainingCooldown / 1000;

            // Notify player of cooldown
            player.sendMessage(ChatColor.RED + "✦ This item is on cooldown for " +
                    remainingSeconds + " seconds!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        // Apply cooldown
        setCooldown(player.getUniqueId(), type);

        // Determine shield amount based on type
        double shieldAmount = getShieldAmount(type);

        // Apply shield with metadata
        player.setMetadata("absorption_shield_amount", new FixedMetadataValue(plugin, shieldAmount));

        // Send visual feedback
        String itemName = getItemName(type);
        player.sendMessage(ChatColor.GOLD + "✦ " + itemName + " grants you a " +
                shieldAmount + " damage shield!");
        // Play shield activation sound
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 1.0f);

        return true;
    }

    /**
     * Gets the current shield amount for a player
     * @param player The player to check
     * @return The shield amount, or 0 if none
     */
    public double getShieldAmount(Player player) {
        if (player.hasMetadata("absorption_shield_amount")) {
            return player.getMetadata("absorption_shield_amount").get(0).asDouble();
        }
        return 0.0;
    }

    /**
     * DEPRECATED: This method is now replaced by the ShieldHandler
     *
     * Damages the player's shield and returns remaining damage
     * @param player The player whose shield is being damaged
     * @param damage The amount of damage to apply
     * @return The remaining damage after the shield is depleted (0 if shield blocked all damage)
     */
    public double damageShield(Player player, double damage) {
        // This functionality is now handled by ShieldHandler
        // Keeping method for compatibility but it shouldn't be called anymore
        return damage;
    }

    /**
     * Check if a consumable is on cooldown for a player
     */
    public boolean isOnCooldown(UUID playerId, ConsumableType type) {
        if (!cooldowns.containsKey(playerId)) {
            return false;
        }

        Map<ConsumableType, Long> playerCooldowns = cooldowns.get(playerId);
        if (!playerCooldowns.containsKey(type)) {
            return false;
        }

        long cooldownEnd = playerCooldowns.get(type);
        return System.currentTimeMillis() < cooldownEnd;
    }

    /**
     * Get remaining cooldown in milliseconds
     */
    public long getRemainingCooldown(UUID playerId, ConsumableType type) {
        if (!cooldowns.containsKey(playerId)) {
            return 0;
        }

        Map<ConsumableType, Long> playerCooldowns = cooldowns.get(playerId);
        if (!playerCooldowns.containsKey(type)) {
            return 0;
        }

        long cooldownEnd = playerCooldowns.get(type);
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Set cooldown for a consumable
     */
    private void setCooldown(UUID playerId, ConsumableType type) {
        long cooldownDuration = getCooldownDuration(type);
        long cooldownEnd = System.currentTimeMillis() + cooldownDuration;

        cooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(type, cooldownEnd);
    }

    /**
     * Get the cooldown duration for a consumable type
     */
    private long getCooldownDuration(ConsumableType type) {
        return switch (type) {
            case GOLDEN_APPLE -> GOLDEN_APPLE_COOLDOWN;
            case ENCHANTED_APPLE -> ENCHANTED_APPLE_COOLDOWN;
            case TOTEM -> TOTEM_COOLDOWN;
        };
    }

    /**
     * Get the shield amount for a consumable type
     */
    private double getShieldAmount(ConsumableType type) {
        return switch (type) {
            case GOLDEN_APPLE -> GOLDEN_APPLE_SHIELD;
            case ENCHANTED_APPLE -> ENCHANTED_APPLE_SHIELD;
            case TOTEM -> TOTEM_SHIELD;
        };
    }

    /**
     * Get friendly name for a consumable type
     */
    private String getItemName(ConsumableType type) {
        return switch (type) {
            case GOLDEN_APPLE -> "Golden Apple";
            case ENCHANTED_APPLE -> "Enchanted Golden Apple";
            case TOTEM -> "Totem of Undying";
        };
    }

    /**
     * Clear cooldowns for a player (e.g., on logout)
     */
    public void clearCooldowns(UUID playerId) {
        cooldowns.remove(playerId);
    }
}