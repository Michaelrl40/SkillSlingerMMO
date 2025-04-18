package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PartyChatListener implements Listener {
    private final MinecraftMMORPG plugin;

    public PartyChatListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player is in party chat mode
        if (plugin.getPartyManager().isInPartyChatMode(player)) {
            // Cancel the normal chat event
            event.setCancelled(true);

            // Send the message to the party instead
            // Run on the main thread since we're using the PartyManager methods
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getPartyManager().sendPartyChatMessage(player, event.getMessage())
            );
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // When a player quits, we need to check if they're in a party
        // The party manager will handle removing them from party chat mode
        // This is already handled in your existing code that manages
        // player disconnections from parties.
    }
}