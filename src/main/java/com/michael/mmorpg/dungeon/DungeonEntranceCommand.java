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

public class DungeonEntranceCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public DungeonEntranceCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✦ Only players can set dungeon entrances!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("mmorpg.dungeon.entrance")) {
            player.sendMessage("§c✦ You don't have permission to set dungeon entrances!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§c✦ Usage: /dungeonentrance [dungeon name]");
            return true;
        }

        String dungeonName = String.join(" ", args);
        Dungeon dungeon = plugin.getDungeonManager().getDungeonByName(dungeonName);

        if (dungeon == null) {
            player.sendMessage("§c✦ Dungeon not found: " + dungeonName);
            return true;
        }

        boolean success = plugin.getDungeonManager().setDungeonEntrance(dungeonName, player.getLocation());

        if (success) {
            player.sendMessage("§a✦ Entrance for dungeon '" + dungeonName + "' set at your current location!");
        } else {
            player.sendMessage("§c✦ Failed to set dungeon entrance. Please try again.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> dungeonNames = plugin.getDungeonManager().listDungeons().stream()
                    .map(Dungeon::getName)
                    .collect(Collectors.toList());
            return filterStartingWith(dungeonNames, args[0]);
        }

        return new ArrayList<>();
    }

    private List<String> filterStartingWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}