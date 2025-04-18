package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, PlayerData> playerCache;

    public PlayerManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.playerCache = new HashMap<>();
    }

    public void loadPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.getLogger().info("Creating new PlayerData instance for: " + player.getName());
        PlayerData data = new PlayerData(playerId);
        playerCache.put(playerId, data);

        plugin.getLogger().info("Loading player state from database for: " + player.getName());
        plugin.getDatabaseManager().loadPlayerState(playerId, data);

        // Verify loaded data
        if (data.getGameClass() != null) {
            plugin.getLogger().info("Loaded class: " + data.getGameClass().getName() + " for " + player.getName());
        } else {
            plugin.getLogger().warning("No class loaded for " + player.getName());
        }
    }

    public void savePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData data = playerCache.get(playerId);
        if (data != null) {
            plugin.getLogger().info("Saving data for " + player.getName() + "...");
            try {
                plugin.getDatabaseManager().savePlayerState(playerId, data);
                plugin.getLogger().info("Successfully saved data for " + player.getName());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save data for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void removePlayer(Player player) {
        // Save before removing from cache
        savePlayer(player);
        playerCache.remove(player.getUniqueId());
    }

    public PlayerData getPlayerData(Player player) {
        return playerCache.get(player.getUniqueId());
    }
}