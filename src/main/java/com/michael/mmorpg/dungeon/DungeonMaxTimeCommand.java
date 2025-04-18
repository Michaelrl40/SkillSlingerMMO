package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DungeonMaxTimeCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public DungeonMaxTimeCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("mmorpg.dungeon.maxtime")) {
            sender.sendMessage("§c✦ You don't have permission to set dungeon time limits!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c✦ Usage: /dungeonmaxtime [dungeon name] [time in seconds]");
            return true;
        }

        String dungeonName = args[0];
        Dungeon dungeon = plugin.getDungeonManager().getDungeonByName(dungeonName);

        if (dungeon == null) {
            sender.sendMessage("§c✦ Dungeon not found: " + dungeonName);
            return true;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[1]);
            if (seconds < 60) {
                sender.sendMessage("§c✦ Time limit must be at least 60 seconds!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§c✦ Invalid time format. Please specify time in seconds.");
            return true;
        }

        // Update the time limit
        boolean success = plugin.getDungeonManager().updateDungeonTimeLimit(dungeonName, seconds);

        if (success) {
            // Format the time for display
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            String timeDisplay = minutes + " minute" + (minutes != 1 ? "s" : "") +
                    (remainingSeconds > 0 ? " and " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "") : "");

            sender.sendMessage("§a✦ Time limit for dungeon \"" + dungeonName + "\" set to " + timeDisplay + ".");

            // If dungeon is currently occupied, inform about time reset
            if (dungeon.isOccupied()) {
                sender.sendMessage("§e✦ The timer for the current dungeon occupation has been reset.");
            }
        } else {
            sender.sendMessage("§c✦ Failed to update time limit. Please check the console for errors.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> dungeonNames = plugin.getDungeonManager().listDungeons().stream()
                    .map(Dungeon::getName)
                    .collect(Collectors.toList());
            return filterStartingWith(dungeonNames, args[0]);
        }

        if (args.length == 2) {
            // Suggest some common time limits
            return filterStartingWith(List.of("300", "600", "1200", "1800", "3600"), args[1]);
        }

        return completions;
    }

    private List<String> filterStartingWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}