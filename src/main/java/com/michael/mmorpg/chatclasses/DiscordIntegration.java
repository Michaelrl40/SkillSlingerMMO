package com.michael.mmorpg.chatclasses;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.chatclasses.ChatChannel;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.util.DiscordUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

public class DiscordIntegration implements Listener {
    private final MinecraftMMORPG plugin;
    private boolean discordSRVHooked = false;

    public DiscordIntegration(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        // Wait for DiscordSRV to be fully enabled
        if (event.getPlugin().getName().equals("DiscordSRV") && !discordSRVHooked) {
            plugin.getLogger().info("Hooking into DiscordSRV");

            // Register our listener with DiscordSRV
            DiscordSRV.api.subscribe(this);
            discordSRVHooked = true;
        }
    }

    // Listen for messages from Discord
    @Subscribe
    public void onDiscordMessage(DiscordGuildMessageReceivedEvent event) {
        // Ignore messages from the bot itself
        if (event.getAuthor().isBot()) return;

        // Get the channel name from DiscordSRV
        String minecraftChannelName = DiscordSRV.getPlugin().getDestinationGameChannelNameForTextChannel(event.getChannel());

        // Check if it's our global channel
        if ("global".equalsIgnoreCase(minecraftChannelName)) {
            // Format the message for Minecraft
            String formattedMessage = ChatColor.AQUA + "[Discord] " +
                    ChatColor.WHITE + event.getAuthor().getName() + ": " +
                    event.getMessage().getContentDisplay();

            // Send message to all players in our GLOBAL channel
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Only send to players subscribed to GLOBAL
                if (plugin.getChatManager().isSubscribed(player, ChatChannel.GLOBAL)) {
                    player.sendMessage(formattedMessage);
                }
            }
        }
    }

    /**
     * Sends a message from in-game to Discord
     */
    public void sendToDiscord(Player sender, String message, ChatChannel channel) {
        // Only forward messages from GLOBAL channel
        if (channel == ChatChannel.GLOBAL) {
            // Get the Discord channel from DiscordSRV
            TextChannel textChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName("global");

            if (textChannel != null) {
                // Format message for Discord
                String discordMessage = sender.getName() + ": " + message;

                // Send to Discord
                DiscordUtil.sendMessage(textChannel, discordMessage);
            }
        }
    }
}