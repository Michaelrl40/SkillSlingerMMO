package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DungeonCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public DungeonCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✦ Only players can use dungeon commands!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "leave":
                return handleLeave(player);

            case "cancel":
                return handleCancel(player);

            case "list":
                return handleList(player);

            case "info":
                if (args.length < 2) {
                    player.sendMessage("§c✦ Usage: /dungeon info [dungeon name]");
                    return true;
                }
                return handleInfo(player, args[1]);

            default:
                showHelp(player);
                return true;
        }
    }

    private boolean handleLeave(Player player) {
        boolean success = plugin.getDungeonManager().leaveDungeon(player);
        if (!success) {
            player.sendMessage("§c✦ You are not in a dungeon!");
        }
        return true;
    }

    private boolean handleCancel(Player player) {
        boolean success = plugin.getDungeonManager().cancelTeleport(player);
        if (!success) {
            player.sendMessage("§c✦ No dungeon teleport in progress!");
        }
        return true;
    }

    private boolean handleList(Player player) {
        List<Dungeon> dungeons = plugin.getDungeonManager().listDungeons();

        if (dungeons.isEmpty()) {
            player.sendMessage("§e✦ No dungeons have been created yet.");
            return true;
        }

        player.sendMessage("§6§l=== Available Dungeons ===");
        for (Dungeon dungeon : dungeons) {
            String status = dungeon.isOccupied() ? "§c[Occupied]" : "§a[Available]";
            player.sendMessage("§e- " + dungeon.getName() + " " + status);
        }
        return true;
    }

    private boolean handleInfo(Player player, String dungeonName) {
        Dungeon dungeon = plugin.getDungeonManager().getDungeonByName(dungeonName);
        if (dungeon == null) {
            player.sendMessage("§c✦ Dungeon not found: " + dungeonName);
            return true;
        }

        player.sendMessage("§6§l=== Dungeon Info: " + dungeon.getName() + " ===");
        player.sendMessage("§eStatus: " + (dungeon.isOccupied() ? "§cOccupied" : "§aAvailable"));

        if (dungeon.isOccupied() && dungeon.getCurrentParty() != null) {
            player.sendMessage("§eOccupied by: " + dungeon.getCurrentParty().getLeader().getName() + "'s party");
            player.sendMessage("§eOccupation time: " + formatDuration(dungeon.getOccupationDuration()));
        }

        if (dungeon.getEntranceLocation() != null) {
            player.sendMessage("§eEntrance: " + formatLocation(dungeon.getEntranceLocation()));
        } else {
            player.sendMessage("§eEntrance: §cNot set");
        }

        return true;
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("§7World: §f%s §7X: §f%d §7Y: §f%d §7Z: §f%d",
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l=== Dungeon Commands ===");
        player.sendMessage("§e/dungeon leave §7- Leave current dungeon");
        player.sendMessage("§e/dungeon cancel §7- Cancel dungeon teleport");
        player.sendMessage("§e/dungeon list §7- List all dungeons");
        player.sendMessage("§e/dungeon info [name] §7- Show dungeon info");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = List.of("leave", "cancel", "list", "info");
            return filterCompletions(subCommands, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            List<String> dungeonNames = plugin.getDungeonManager().listDungeons().stream()
                    .map(Dungeon::getName)
                    .collect(Collectors.toList());
            return filterCompletions(dungeonNames, args[1]);
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> options, String input) {
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}