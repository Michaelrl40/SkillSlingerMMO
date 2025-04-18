package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class EconomyManager implements Listener {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Integer> pendingTransactions;

    public EconomyManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.pendingTransactions = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Get a player's coin balance
     */
    public int getBalance(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player);
        return data != null ? data.getCoins() : 0;
    }

    /**
     * Add coins to a player's balance
     * @return true if successful
     */
    public boolean addCoins(Player player, int amount) {
        if (amount < 0) return false;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player);
        if (data == null) return false;

        // Check for integer overflow
        int newBalance = data.getCoins() + amount;
        if (newBalance < data.getCoins()) return false;

        data.setCoins(newBalance);
        player.sendMessage("§a✦ Received " + amount + " coins!");
        return true;
    }

    /**
     * Remove coins from a player's balance
     * @return true if successful
     */
    public boolean removeCoins(Player player, int amount) {
        if (amount < 0) return false;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player);
        if (data == null) return false;

        if (data.getCoins() < amount) {
            player.sendMessage("§c✦ You don't have enough coins!");
            return false;
        }

        data.setCoins(data.getCoins() - amount);
        player.sendMessage("§c✦ Lost " + amount + " coins!");
        return true;
    }

    /**
     * Transfer coins between players
     * @return true if successful
     */
    public boolean transferCoins(Player from, Player to, int amount) {
        if (amount <= 0) return false;
        if (from.equals(to)) return false;

        // Start transaction
        synchronized (pendingTransactions) {
            if (pendingTransactions.containsKey(from.getUniqueId())) {
                from.sendMessage("§c✦ You already have a pending transaction!");
                return false;
            }
            pendingTransactions.put(from.getUniqueId(), amount);
        }

        try {
            if (!removeCoins(from, amount)) {
                return false;
            }

            if (!addCoins(to, amount)) {
                // Transaction failed, refund sender
                addCoins(from, amount);
                return false;
            }

            from.sendMessage("§a✦ Sent " + amount + " coins to " + to.getName());
            to.sendMessage("§a✦ Received " + amount + " coins from " + from.getName());
            return true;

        } finally {
            // Clear transaction
            synchronized (pendingTransactions) {
                pendingTransactions.remove(from.getUniqueId());
            }
        }
    }

    /**
     * Check if a player has enough coins
     */
    public boolean hasEnough(Player player, int amount) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player);
        return data != null && data.getCoins() >= amount;
    }

    /**
     * Format coin amount for display
     */
    public String formatCoins(int amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(amount);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Make sure new players have PlayerData initialized
        plugin.getPlayerManager().getPlayerData(event.getPlayer());
    }
}