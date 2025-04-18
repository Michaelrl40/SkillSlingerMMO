package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BankCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public BankCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✦ Only players can use bank commands!");
            return true;
        }

        Player player = (Player) sender;

        // Default command with no args: open bank
        if (args.length == 0) {
            plugin.getBankManager().openBankInterface(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "open":
                plugin.getBankManager().openBankInterface(player);
                return true;

            case "upgrade":
                boolean success = plugin.getBankManager().upgradeBank(player);
                if (success) {
                    // Re-open bank interface with new size
                    plugin.getBankManager().openBankInterface(player);
                }
                return true;

            case "help":
                sendHelpMessage(player);
                return true;

            default:
                player.sendMessage("§c✦ Unknown bank command. Type /bank help for assistance.");
                return true;
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6§l===== Bank Commands =====");
        player.sendMessage("§6/bank §7- Open your bank");
        player.sendMessage("§6/bank open §7- Open your bank");
        player.sendMessage("§6/bank upgrade §7- Upgrade your bank to the next tier");
        player.sendMessage("§6/bank help §7- Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("open", "upgrade", "help");
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}