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

public class DungeonEditCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public DungeonEditCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✦ Only players can edit dungeons!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("mmorpg.dungeon.edit")) {
            player.sendMessage("§c✦ You don't have permission to edit dungeons!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§c✦ Usage: /dungeonedit [dungeon name]");
            return true;
        }

        String dungeonName = String.join(" ", args);
        Dungeon dungeon = plugin.getDungeonManager().getDungeonByName(dungeonName);

        if (dungeon == null) {
            player.sendMessage("§c✦ Dungeon not found: " + dungeonName);
            return true;
        }

        // Teleport player to dungeon world for editing
        player.teleport(dungeon.getWorld().getSpawnLocation());
        player.sendMessage("§a✦ Teleported to dungeon: " + dungeonName);
        player.sendMessage("§e✦ You can now build and modify this dungeon world.");
        player.sendMessage("§e✦ Use §b/dungeon leave§e to return to the main world.");

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