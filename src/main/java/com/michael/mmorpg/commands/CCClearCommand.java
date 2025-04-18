package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CCClearCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public CCClearCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Clear all status effects and immunities
        plugin.getStatusEffectManager().clearAllEffects(player);
        player.sendMessage("§a✦ All status effects and immunities have been cleared!");

        return true;
    }
}