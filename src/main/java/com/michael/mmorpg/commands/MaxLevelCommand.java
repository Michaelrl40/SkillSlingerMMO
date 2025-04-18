package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MaxLevelCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public MaxLevelCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData == null || !playerData.hasClass()) {
            player.sendMessage("§cYou need to select a class first!");
            return true;
        }

        GameClass gameClass = playerData.getGameClass();
        GameClass.ClassTier tier = gameClass.getTier();

        // Process the command based on class tier
        switch (tier) {
            case BASE -> maxBaseClass(player, playerData);
            case SECONDARY -> maxSecondaryClass(player, playerData);
            case MASTER -> maxMasterClass(player, playerData);
        }

        return true;
    }

    private void maxBaseClass(Player player, PlayerData playerData) {
        double expNeeded = 0;
        int currentLevel = playerData.getLevel();
        int targetLevel = 20;

        // Calculate exp needed for each level including the final level
        for (int level = currentLevel; level <= targetLevel; level++) {
            expNeeded += 50 * Math.pow(1.2, level - 1);
        }

        // Add mastery buffer to ensure completion
        expNeeded += 1000;

        // Show debug info for exp calculation
        player.sendMessage("§7Debug: Current Level: " + currentLevel);
        player.sendMessage("§7Debug: Adding " + String.format("%.2f", expNeeded) + " experience");

        // Add the calculated experience
        playerData.addExperience(expNeeded);

        // Verify correct level achievement
        if (playerData.getLevel() == targetLevel) {
            player.sendMessage("§6§l=============================");
            player.sendMessage("§a§lBase Class Maximized!");
            player.sendMessage("§eYou are now level §f20 §ein your base class!");
            player.sendMessage("§eYou can now advance to a secondary class!");
            player.sendMessage("§6§l=============================");
        } else {}
    }

    private void maxSecondaryClass(Player player, PlayerData playerData) {
        double expNeeded = 0;
        int currentLevel = playerData.getLevel();
        int targetLevel = 50;

        // Calculate exp needed for each level including the final level
        for (int level = currentLevel; level <= targetLevel; level++) {
            expNeeded += 75 * Math.pow(1.15, level - 1);
        }

        // Add mastery buffer
        expNeeded += 2000;

        // Show debug info for exp calculation
        player.sendMessage("§7Debug: Current Level: " + currentLevel);
        player.sendMessage("§7Debug: Adding " + String.format("%.2f", expNeeded) + " experience");

        // Add the calculated experience
        playerData.addExperience(expNeeded);

        // Verify correct level achievement
        if (playerData.getLevel() == targetLevel) {
            player.sendMessage("§6§l=============================");
            player.sendMessage("§a§lSecondary Class Maximized!");
            player.sendMessage("§eYou are now level §f50 §ein your secondary class!");
            player.sendMessage("§eYou can now advance to a master class!");
            player.sendMessage("§6§l=============================");
        } else {}
    }

    private void maxMasterClass(Player player, PlayerData playerData) {
        double expNeeded = 0;
        int currentLevel = playerData.getLevel();
        int targetLevel = 70;

        // Calculate exp needed for each level including the final level
        for (int level = currentLevel; level <= targetLevel; level++) {
            expNeeded += 100 * Math.pow(1.1, level - 1);
        }

        // Add mastery buffer
        expNeeded += 3000;

        // Show debug info for exp calculation
        player.sendMessage("§7Debug: Current Level: " + currentLevel);
        player.sendMessage("§7Debug: Adding " + String.format("%.2f", expNeeded) + " experience");

        // Add the calculated experience
        playerData.addExperience(expNeeded);

        // Verify correct level achievement
        if (playerData.getLevel() == targetLevel) {
            player.sendMessage("§6§l=============================");
            player.sendMessage("§a§lMaster Class Maximized!");
            player.sendMessage("§eYou are now level §f70 §ein your master class!");
            player.sendMessage("§d§lCongratulations on reaching the pinnacle of power!");
            player.sendMessage("§6§l=============================");
        } else {}
    }
}