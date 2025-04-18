package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public class ResourceManager {
    private final MinecraftMMORPG plugin;

    // Resource update intervals
    private static final long MANA_UPDATE_PERIOD = 100L;    // 5 seconds (20 ticks * 5)
    private static final long STAMINA_UPDATE_PERIOD = 100L; // 6 seconds (20 ticks * 6)
    private static final long TOXIN_UPDATE_PERIOD = 20L;    // 1 second (20 ticks)
    private static final long RAGE_GAIN_PERIOD = 100L;       // 1 second for rage gain in combat
    private static final long RAGE_DECAY_PERIOD = 100L;     // 5 seconds (for rage decay when out of combat)

    // Rage decay settings
    private static final double RAGE_DECAY_AMOUNT = 1.0;    // Amount of rage lost per decay tick
    private static final double RAGE_COMBAT_GAIN = 10.0;

    public ResourceManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        startManaRegeneration();
        startStaminaRegeneration();
        startToxinManagement();
        startRageDecay();
        startCombatRageGeneration();
    }

    private void startManaRegeneration() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                if (playerData == null || !playerData.hasClass()) continue;

                GameClass gameClass = playerData.getGameClass();
                double maxMana = playerData.getMaxMana();
                double manaRegen = maxMana * gameClass.getManaRegenPercent();

                // Check for Mana Song buff
                if (player.hasMetadata("mana_song_buff")) {
                    double bonus = player.getMetadata("mana_song_buff").get(0).asDouble();
                    manaRegen *= (1 + bonus); // Increase regen by bonus percentage
                }

                playerData.regenMana(manaRegen);
            }
        }, MANA_UPDATE_PERIOD, MANA_UPDATE_PERIOD);
    }

    private void startStaminaRegeneration() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                if (playerData == null || !playerData.hasClass()) continue;

                GameClass gameClass = playerData.getGameClass();
                double maxStamina = playerData.getMaxStamina();
                double staminaRegen = maxStamina * gameClass.getStaminaRegenPercent();
                playerData.regenStamina(staminaRegen);
            }
        }, STAMINA_UPDATE_PERIOD, STAMINA_UPDATE_PERIOD);
    }


    private void startRageDecay() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                if (playerData == null || !playerData.hasClass()) continue;

                // Only decay rage if player is not in combat
                if (!plugin.getCombatManager().isInCombat(player)) {
                    double currentRage = playerData.getCurrentRage();
                    if (currentRage > 0) {
                        // Decay rage gradually when out of combat
                        currentRage = Math.max(0, currentRage - RAGE_DECAY_AMOUNT);
                        // Use setRage method to update the value (you might need to add this to PlayerData)
                        if (playerData.getCurrentRage() != currentRage) {
                            playerData.addRage(-RAGE_DECAY_AMOUNT); // Using addRage with negative value
                        }
                    }
                }
            }
        }, RAGE_DECAY_PERIOD, RAGE_DECAY_PERIOD);
    }

    private void startCombatRageGeneration() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                if (playerData == null || !playerData.hasClass()) continue;

                // Only generate rage if player is in combat and is a Berserker
                if (plugin.getCombatManager().isInCombat(player) &&
                        playerData.getGameClass().getName().equalsIgnoreCase("Berserker")) {

                    // Add rage and ensure it doesn't exceed maximum
                    double currentRage = playerData.getCurrentRage();
                    double maxRage = playerData.getMaxRage();

                    if (currentRage < maxRage) {
                        double newRage = Math.min(currentRage + RAGE_COMBAT_GAIN, maxRage);
                        playerData.setCurrentRage(newRage);
                    }
                }
            }
        }, RAGE_GAIN_PERIOD, RAGE_GAIN_PERIOD); // Run every second
    }

    private void startToxinManagement() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                if (playerData == null || !playerData.hasClass()) continue;

                // Only regenerate toxin if no active drains
                if (playerData.getActiveSkills().isEmpty()) {
                    double maxToxin = playerData.getMaxToxin();
                    double toxinRegen = maxToxin * 0.02; // 2% per tick, adjust this value as needed
                    playerData.setCurrentToxin(playerData.getCurrentToxin() + toxinRegen);
                }

                // Process active drains
                Map<String, Double> activeSkills = playerData.getActiveSkills();
                if (!activeSkills.isEmpty()) {
                    double totalDrain = activeSkills.values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum();
                    playerData.useToxin(totalDrain);
                }
            }
        }, TOXIN_UPDATE_PERIOD, TOXIN_UPDATE_PERIOD);
    }


}