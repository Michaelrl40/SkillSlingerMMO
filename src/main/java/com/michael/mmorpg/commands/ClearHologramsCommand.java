package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

public class ClearHologramsCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public ClearHologramsCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int count = 0;

        // Loop through all worlds
        for (World world : Bukkit.getWorlds()) {
            // Get all entities in the world
            for (Entity entity : world.getEntities()) {
                // Check if it's a TextDisplay entity
                if (entity instanceof TextDisplay) {
                    entity.remove();
                    count++;
                }
            }
        }

        sender.sendMessage("§6✦ Cleared " + count + " damage display holograms!");
        return true;
    }
}