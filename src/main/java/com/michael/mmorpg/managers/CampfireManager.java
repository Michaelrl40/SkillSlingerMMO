package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CampfireManager {
    private final MinecraftMMORPG plugin;
    private final double CAMPFIRE_RANGE = 5.0; // Range in blocks
    private final double REGEN_MULTIPLIER = 2.0; // Doubles regen rate

    private final Map<UUID, Boolean> playerBuffStatus = new HashMap<>();

    public CampfireManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        startCampfireCheck();
    }

    private void startCampfireCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                if (playerData == null || !playerData.hasClass()) continue;

                // Skip if player is in combat
                if (plugin.getCombatManager().isInCombat(player)) {
                    removeBuffStatus(player);
                    continue;
                }

                boolean nearCampfire = isNearLitCampfire(player);

                // If player just entered campfire range
                if (nearCampfire && !hasBuffStatus(player)) {
                    player.sendMessage("§6✧ You feel rested near the campfire...");
                    setBuffStatus(player, true);
                }
                // If player just left campfire range
                else if (!nearCampfire && hasBuffStatus(player)) {
                    player.sendMessage("§c✧ You no longer feel the warmth of the campfire.");
                    removeBuffStatus(player);
                }

                if (nearCampfire) {
                    applyRegenBonus(playerData);
                    player.spawnParticle(org.bukkit.Particle.END_ROD,
                            player.getLocation().add(0, 1, 0),
                            1, 0.5, 0.5, 0.5, 0);
                }
            }
        }, 20L, 20L);
    }

    private void setBuffStatus(Player player, boolean status) {
        playerBuffStatus.put(player.getUniqueId(), status);
    }

    private void removeBuffStatus(Player player) {
        playerBuffStatus.remove(player.getUniqueId());
    }

    private boolean hasBuffStatus(Player player) {
        return playerBuffStatus.getOrDefault(player.getUniqueId(), false);
    }

    private boolean isNearLitCampfire(Player player) {
        Location playerLoc = player.getLocation();
        int radius = (int) Math.ceil(CAMPFIRE_RANGE);

        // Check blocks in a cube around the player
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = playerLoc.getBlock().getRelative(x, y, z);

                    // Check if block is a campfire
                    if (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE) {
                        // Check if it's lit
                        if (block.getBlockData() instanceof Lightable) {
                            Lightable campfire = (Lightable) block.getBlockData();
                            if (campfire.isLit()) {
                                // Check actual distance (for circular range)
                                if (block.getLocation().distance(playerLoc) <= CAMPFIRE_RANGE) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void applyRegenBonus(PlayerData playerData) {
        double maxHealth = playerData.getMaxHealth();
        double maxMana = playerData.getMaxMana();
        double maxStamina = playerData.getMaxStamina();
        double maxToxin = playerData.getMaxToxin();

        // Get base regen percentages from their class
        double manaRegenPercent = playerData.getGameClass().getManaRegenPercent() * REGEN_MULTIPLIER;
        double staminaRegenPercent = playerData.getGameClass().getStaminaRegenPercent() * REGEN_MULTIPLIER;

        // Apply regeneration
        if (playerData.getCurrentMana() < maxMana) {
            playerData.regenMana(maxMana * manaRegenPercent);
        }

        if (playerData.getCurrentStamina() < maxStamina) {
            playerData.regenStamina(maxStamina * staminaRegenPercent);
        }

    }
}