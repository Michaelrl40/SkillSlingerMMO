package com.michael.mmorpg.chatclasses;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.chatclasses.ChatChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ChatListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Cancel the vanilla chat event
        event.setCancelled(true);

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Handle channel switching shortcuts
        if (message.startsWith("@")) {
            String[] parts = message.split(" ", 2);
            String channelPrefix = parts[0].substring(1).toUpperCase();

            // Check if it's a valid channel
            for (ChatChannel channel : ChatChannel.values()) {
                if (channel.name().startsWith(channelPrefix)) {
                    // If it's just a channel switch with no message
                    if (parts.length == 1) {
                        plugin.getChatManager().setPlayerChannel(player, channel);
                        return;
                    }

                    // If it includes a message, temporarily send to that channel
                    ChatChannel originalChannel = plugin.getChatManager().getPlayerChannel(player);
                    plugin.getChatManager().setPlayerChannel(player, channel);
                    plugin.getChatManager().sendMessage(player, parts[1]);
                    plugin.getChatManager().setPlayerChannel(player, originalChannel);
                    return;
                }
            }
        }

        // Normal message handling - send to current channel
        plugin.getChatManager().sendMessage(player, message);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Set default channel to Local instead of Global
        plugin.getChatManager().setPlayerChannel(player, ChatChannel.LOCAL);

        // Subscribe to all non-proximity channels silently (without notifications)
        for (ChatChannel channel : ChatChannel.values()) {
            if (!channel.isProximityBased() && channel != ChatChannel.GLOBAL) {
                plugin.getChatManager().subscribeToChannelSilently(player, channel);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up channel data when player leaves
        plugin.getChatManager().removePlayer(event.getPlayer());
    }
}