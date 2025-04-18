package com.michael.mmorpg.managers;

import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.skills.druid.DruidShapeshiftSkill;
import org.bukkit.entity.Player;
import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class ClassManager {
    private final MinecraftMMORPG plugin;
    private final ConfigManager configManager;
    private static final int MASTER_CLASS_COST = 100;
    private final Map<UUID, PendingClassChange> pendingChanges = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 30 * 20; // 30 seconds in ticks

    // Inner class to store pending class changes
    private static class PendingClassChange {
        final GameClass targetClass;
        final BukkitRunnable timeoutTask;

        PendingClassChange(GameClass targetClass, BukkitRunnable timeoutTask) {
            this.targetClass = targetClass;
            this.timeoutTask = timeoutTask;
        }
    }


    public ClassManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public boolean switchClass(Player player, String className) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) {
            player.sendMessage("§c✦ Failed to load player data!");
            return false;
        }

        GameClass targetClass = configManager.getClass(className.toLowerCase());
        if (targetClass == null) {
            player.sendMessage("§c✦ Invalid class name!");
            return false;
        }

        // Check if player can switch to this class
        if (!canSwitchToClass(player, targetClass)) {
            return false;
        }

        // If there's already a pending change, cancel it
        cancelPendingChange(player);

        // Create confirmation message based on class tier
        String confirmMessage;
        if (targetClass.getTier() == GameClass.ClassTier.MASTER) {
            confirmMessage = String.format(
                    "§6✦ Are you sure you want to advance to %s? This will cost %d coins.\n" +
                            "§6✦ Type §f/class confirm§6 to proceed or §f/class cancel§6 to cancel. You have 30 seconds to decide.",
                    targetClass.getName(), MASTER_CLASS_COST
            );
        } else {
            confirmMessage = String.format(
                    "§6✦ Are you sure you want to become a %s?\n" +
                            "§6✦ Type §f/class confirm§6 to proceed or §f/class cancel§6 to cancel. You have 30 seconds to decide.",
                    targetClass.getName()
            );
        }

        // Create timeout task
        BukkitRunnable timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingChanges.containsKey(player.getUniqueId())) {
                    player.sendMessage("§c✦ Class change request has timed out.");
                    pendingChanges.remove(player.getUniqueId());
                }
            }
        };

        // Schedule timeout task
        timeoutTask.runTaskLater(plugin, CONFIRMATION_TIMEOUT);

        // Store pending change
        pendingChanges.put(player.getUniqueId(), new PendingClassChange(targetClass, timeoutTask));

        // Send confirmation message
        player.sendMessage(confirmMessage);
        return true;
    }

    public void confirmClassChange(Player player) {
        PendingClassChange pending = pendingChanges.get(player.getUniqueId());
        if (pending == null) {
            player.sendMessage("§c✦ You have no pending class change to confirm!");
            return;
        }

        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) {
            player.sendMessage("§c✦ Failed to load player data!");
            return;
        }

        // Process the class change
        processClassSwitch(player, playerData, pending.targetClass);

        // Clean up
        pending.timeoutTask.cancel();
        pendingChanges.remove(player.getUniqueId());
    }

    public void cancelClassChange(Player player) {
        PendingClassChange pending = pendingChanges.remove(player.getUniqueId());
        if (pending == null) {
            player.sendMessage("§c✦ You have no pending class change to cancel!");
            return;
        }

        pending.timeoutTask.cancel();
        player.sendMessage("§a✦ Class change cancelled.");
    }

    private void cancelPendingChange(Player player) {
        PendingClassChange pending = pendingChanges.remove(player.getUniqueId());
        if (pending != null) {
            pending.timeoutTask.cancel();
        }
    }

    private boolean canSwitchToClass(Player player, GameClass targetClass) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return false;

        GameClass currentClass = playerData.getGameClass();
        if (currentClass != null && currentClass.getName().equals(targetClass.getName())) {
            player.sendMessage("§c✦ You are already a " + targetClass.getName() + "!");
            return false;
        }

        switch (targetClass.getTier()) {
            case SECONDARY:
                // Secondary classes are always available and free
                return true;

            case MASTER:
                // Check if parent class is mastered
                String parentClass = targetClass.getParentClass();
                if (parentClass == null || !playerData.hasMasteredClass(parentClass)) {
                    player.sendMessage("§c✦ You must master " + parentClass + " before choosing " + targetClass.getName() + "!");
                    return false;
                }

                // Check if player has enough coins for master class switch
                if (!plugin.getEconomyManager().hasEnough(player, MASTER_CLASS_COST)) {
                    player.sendMessage("§c✦ You need " + MASTER_CLASS_COST + " coins to switch to a master class!");
                    return false;
                }
                return true;

            default:
                return false;
        }
    }

    private void processClassSwitch(Player player, PlayerData playerData, GameClass targetClass) {
        // Clean up any active shapeshift before class change
        DruidShapeshiftSkill.cleanupPlayer(player);

        // Only charge coins for master classes
        if (targetClass.getTier() == GameClass.ClassTier.MASTER) {
            plugin.getEconomyManager().removeCoins(player, MASTER_CLASS_COST);
        }

        // Switch to new class
        playerData.setGameClass(targetClass);

        // Send success message with appropriate context
        if (targetClass.getTier() == GameClass.ClassTier.MASTER) {
            player.sendMessage("§a✦ Successfully advanced to " + targetClass.getName() + " for " + MASTER_CLASS_COST + " coins!");
        } else {
            player.sendMessage("§a✦ Successfully changed to " + targetClass.getName() + "!");
        }
    }

    public List<String> getAvailableClasses(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return new ArrayList<>();

        return configManager.getClasses().values().stream()
                .filter(gameClass -> {
                    switch (gameClass.getTier()) {
                        case SECONDARY:
                            return true; // Always show secondary classes
                        case MASTER:
                            String parentClass = gameClass.getParentClass();
                            return parentClass != null && playerData.hasMasteredClass(parentClass);
                        default:
                            return false;
                    }
                })
                .map(GameClass::getName)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a player can use a specific weapon based on its type and custom model data.
     */
    public boolean canUseWeapon(Player player, ItemStack weapon) {
        if (weapon == null) return false;

        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || playerData.getGameClass() == null) return false;

        List<String> allowedWeapons = playerData.getGameClass().getAllowedWeapons();

        // FIRST check if this is a custom weapon
        if (weapon.hasItemMeta() && weapon.getItemMeta().hasCustomModelData()) {
            // Get the custom weapon identifier
            String customWeaponId = plugin.getWeaponManager().getCustomWeaponIdentifier(weapon);

            if (customWeaponId != null) {
                // This is a registered custom weapon - ONLY allow if specifically permitted
                return allowedWeapons.contains(customWeaponId);
            }
        }

        // If we get here, it's a standard weapon without custom model data
        // or with custom model data that isn't registered in our system
        String weaponType = weapon.getType().toString().toUpperCase();
        return allowedWeapons.contains(weaponType);
    }

    // Keep the original method for backward compatibility
    public boolean canUseWeapon(Player player, String weaponType) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || playerData.getGameClass() == null) return false;
        return playerData.getGameClass().getAllowedWeapons().contains(weaponType.toUpperCase());
    }
}