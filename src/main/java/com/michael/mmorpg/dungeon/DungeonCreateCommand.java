package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DungeonCreateCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public DungeonCreateCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✦ Only players can create dungeons!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("mmorpg.dungeon.create")) {
            player.sendMessage("§c✦ You don't have permission to create dungeons!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§c✦ Usage: /dungeoncreate [dungeon name]");
            return true;
        }

        String dungeonName = String.join(" ", args);
        boolean success = plugin.getDungeonManager().createDungeon(dungeonName);

        if (success) {
            player.sendMessage("§a✦ Dungeon created: " + dungeonName);
        } else {
            player.sendMessage("§c✦ Failed to create dungeon. It may already exist or there was an error.");
        }

        return true;
    }
}