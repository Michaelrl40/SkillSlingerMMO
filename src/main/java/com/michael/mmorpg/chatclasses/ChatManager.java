package com.michael.mmorpg.chatclasses;

import com.michael.mmorpg.MinecraftMMORPG;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.util.DiscordUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class ChatManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, ChatChannel> playerChannels = new HashMap<>();
    // Store channel subscriptions for specialized channels
    private final Map<ChatChannel, Set<UUID>> channelSubscriptions = new HashMap<>();

    // The channel name in DiscordSRV config
    private static final String DISCORD_CHANNEL_NAME = "global";

    public ChatManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;

        // Initialize channel subscription sets
        for (ChatChannel channel : ChatChannel.values()) {
            if (channel != ChatChannel.GLOBAL && !channel.isProximityBased()) {
                channelSubscriptions.put(channel, new HashSet<>());
            }
        }
    }

    /**
     * Sets a player's active channel for chatting
     */
    public void setPlayerChannel(Player player, ChatChannel channel) {
        playerChannels.put(player.getUniqueId(), channel);
        player.sendMessage(channel.getColor() + "You are now chatting in " + channel.getDisplayName() + " channel.");
    }

    /**
     * Gets a player's active channel
     */
    public ChatChannel getPlayerChannel(Player player) {
        return playerChannels.getOrDefault(player.getUniqueId(), ChatChannel.LOCAL);
    }

    /**
     * Subscribes a player to a channel
     */
    public void subscribeToChannel(Player player, ChatChannel channel) {
        if (channel.isProximityBased() || channel == ChatChannel.GLOBAL) {
            // All players are always subscribed to global and proximity channels
            return;
        }

        channelSubscriptions.get(channel).add(player.getUniqueId());
        player.sendMessage(channel.getColor() + "You are now subscribed to " + channel.getDisplayName() + " channel.");
    }

    /**
     * Subscribes a player to a channel without sending notification
     */
    public void subscribeToChannelSilently(Player player, ChatChannel channel) {
        if (channel.isProximityBased() || channel == ChatChannel.GLOBAL) {
            // All players are always subscribed to global and proximity channels
            return;
        }

        channelSubscriptions.get(channel).add(player.getUniqueId());
        // No notification message
    }

    /**
     * Unsubscribes a player from a channel
     */
    public void unsubscribeFromChannel(Player player, ChatChannel channel) {
        if (channel.isProximityBased() || channel == ChatChannel.GLOBAL) {
            // Cannot unsubscribe from global or proximity channels
            return;
        }

        channelSubscriptions.get(channel).remove(player.getUniqueId());
        player.sendMessage(channel.getColor() + "You are no longer subscribed to " + channel.getDisplayName() + " channel.");
    }

    /**
     * Checks if a player is subscribed to a channel
     */
    public boolean isSubscribed(Player player, ChatChannel channel) {
        if (channel.isProximityBased() || channel == ChatChannel.GLOBAL) {
            // All players are always subscribed to global and proximity channels
            return true;
        }

        return channelSubscriptions.get(channel).contains(player.getUniqueId());
    }

    /**
     * Sends a message to a channel
     */
    public void sendMessage(Player sender, String message) {
        ChatChannel channel = getPlayerChannel(sender);
        String titlePrefix = "";
        if (plugin.getTitleManager() != null) {
            titlePrefix = plugin.getTitleManager().getFormattedPlayerTitle(sender);
        }

        String formattedMessage = channel.format(sender.getName(), message, titlePrefix);

        if (channel.isProximityBased()) {
            // Send to players within range
            int rangeSquared = channel.getRange() * channel.getRange();
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (recipient.getWorld().equals(sender.getWorld()) &&
                        recipient.getLocation().distanceSquared(sender.getLocation()) <= rangeSquared) {
                    recipient.sendMessage(formattedMessage);
                }
            }
        } else if (channel == ChatChannel.GLOBAL) {
            // Send to all players
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                recipient.sendMessage(formattedMessage);
            }

            // Try sending to Discord using the DiscordSRV API
            sendToDiscord(sender, message);
        } else {
            // Send to subscribed players
            for (UUID subscriberId : channelSubscriptions.get(channel)) {
                Player subscriber = Bukkit.getPlayer(subscriberId);
                if (subscriber != null && subscriber.isOnline()) {
                    subscriber.sendMessage(formattedMessage);
                }
            }
        }
    }

    /**
     * Sends a message to Discord
     */
    private void sendToDiscord(Player sender, String message) {
        if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            try {
                // Method 1: Using DiscordSRV's processMessage method
                DiscordSRV.getPlugin().processChatMessage(
                        sender,                // The player who sent the message
                        message,               // The raw message content
                        DISCORD_CHANNEL_NAME,  // The channel name as configured in DiscordSRV
                        false                  // Don't cancel the event
                );
                return; // Successfully sent
            } catch (Exception e) {
                plugin.getLogger().warning("Error using DiscordSRV processChatMessage: " + e.getMessage());
                // Continue to next method
            }

            // Method 2: Getting channel directly
            try {
                TextChannel textChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(DISCORD_CHANNEL_NAME);

                if (textChannel != null) {
                    String discordMessage = sender.getName() + ": " + message;
                    DiscordUtil.sendMessage(textChannel, discordMessage);
                    return; // Successfully sent
                } else {
                    plugin.getLogger().warning("Discord channel for '" + DISCORD_CHANNEL_NAME + "' not found");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending directly to channel: " + e.getMessage());
            }

            // Method 3: Try main channel as fallback
            try {
                TextChannel mainChannel = DiscordSRV.getPlugin().getMainTextChannel();
                if (mainChannel != null) {
                    String discordMessage = sender.getName() + ": " + message;
                    DiscordUtil.sendMessage(mainChannel, discordMessage);
                } else {
                    plugin.getLogger().warning("Main Discord channel not found");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending to main channel: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("DiscordSRV is not enabled");
        }
    }

    /**
     * Removes a player from all channels (for logout/cleanup)
     */
    public void removePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        playerChannels.remove(playerId);

        // Remove from subscription lists
        for (Set<UUID> subscribers : channelSubscriptions.values()) {
            subscribers.remove(playerId);
        }
    }
}