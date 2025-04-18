package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.SkillListManager;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class SkillCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;
    private final SkillListManager skillListManager;

    public SkillCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.skillListManager = new SkillListManager(plugin);
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
            player.sendMessage("§cYou need to select a class first! Use /class select <className>");
            return true;
        }

        if (args.length < 1) {
            sendSkillHelp(player);
            return true;
        }

        String firstArg = args[0].toLowerCase();

        // Handle skill list command
        if (firstArg.equals("list")) {
            int page = 1;

            // Check if a page number was provided
            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid page number. Using page 1.");
                }
            }

            skillListManager.showSkillList(player, page);
            return true;
        }

        // Only send the generic message if the skill doesn't exist
        if (!plugin.getSkillManager().skillExists(firstArg)) {
            player.sendMessage("§cUnknown skill! Use /skill list to see available skills.");
            return true;
        }

        // Create a new array with the remaining arguments (for targeted skills)
        String[] skillArgs = null;
        if (args.length > 1) {
            skillArgs = Arrays.copyOfRange(args, 1, args.length);
        }

        // Execute the skill with the arguments
        plugin.getSkillManager().executeSkill(player, firstArg, skillArgs);

        return true;
    }

    private void sendSkillHelp(Player player) {
        player.sendMessage("§6=== Skill Commands ===");
        player.sendMessage("§7/skill list §f- Show all available skills (page 1)");
        player.sendMessage("§7/skill list <page> §f- Show skills for a specific page");
        player.sendMessage("§7/skill <skillname> §f- Use a skill");
        player.sendMessage("§7/skill <skillname> <target> §f- Use a targeted skill");
    }
}