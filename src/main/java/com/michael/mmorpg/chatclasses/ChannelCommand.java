package com.michael.mmorpg.chatclasses;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.chatclasses.ChatChannel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChannelCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public ChannelCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use chat channels.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Display current channel and available channels
            displayChannelInfo(player);
            return true;
        }

        String channelName = args[0].toUpperCase();

        try {
            ChatChannel channel = ChatChannel.valueOf(channelName);
            plugin.getChatManager().setPlayerChannel(player, channel);

            // Subscribe if not already subscribed
            if (!plugin.getChatManager().isSubscribed(player, channel)) {
                plugin.getChatManager().subscribeToChannel(player, channel);
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid channel name. Use: " +
                    Arrays.stream(ChatChannel.values())
                            .map(Enum::name)
                            .map(String::toLowerCase)
                            .collect(Collectors.joining(", ")));
        }

        return true;
    }

    private void displayChannelInfo(Player player) {
        ChatChannel current = plugin.getChatManager().getPlayerChannel(player);

        player.sendMessage("§7--- §fChat Channels §7---");
        player.sendMessage("§7Current channel: " + current.getColor() + current.getDisplayName());
        player.sendMessage("§7Available channels:");

        for (ChatChannel channel : ChatChannel.values()) {
            boolean subscribed = plugin.getChatManager().isSubscribed(player, channel);
            player.sendMessage(String.format("%s%s %s",
                    channel.getColor(),
                    channel.getDisplayName(),
                    subscribed ? "§a(Subscribed)" : "§7(Not subscribed)"));
        }

        player.sendMessage("§7Use §f/ch <channel> §7to switch channels.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();

            for (ChatChannel channel : ChatChannel.values()) {
                String name = channel.name().toLowerCase();
                if (name.startsWith(partial)) {
                    completions.add(name);
                }
            }

            return completions;
        }

        return new ArrayList<>();
    }
}