package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public CoinCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can check their balance!");
                return true;
            }
            // Show balance
            Player player = (Player) sender;
            int balance = plugin.getEconomyManager().getBalance(player);
            sender.sendMessage("§6Balance: §f" + plugin.getEconomyManager().formatCoins(balance) + " coins");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pay":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can pay other players!");
                    return true;
                }
                handlePay((Player) sender, args);
                break;

            case "give":
                if (!sender.hasPermission("mmorpg.coins.admin")) {
                    sender.sendMessage("§cYou don't have permission to give coins!");
                    return true;
                }
                handleGive(sender, args);
                break;

            case "take":
                if (!sender.hasPermission("mmorpg.coins.admin")) {
                    sender.sendMessage("§cYou don't have permission to take coins!");
                    return true;
                }
                handleTake(sender, args);
                break;

            case "set":
                if (!sender.hasPermission("mmorpg.coins.admin")) {
                    sender.sendMessage("§cYou don't have permission to set coins!");
                    return true;
                }
                handleSet(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handlePay(Player sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /coins pay <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        plugin.getEconomyManager().transferCoins(sender, target, amount);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /coins give <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        if (plugin.getEconomyManager().addCoins(target, amount)) {
            sender.sendMessage("§aGave " + amount + " coins to " + target.getName());
        }
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /coins take <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        if (plugin.getEconomyManager().removeCoins(target, amount)) {
            sender.sendMessage("§aTook " + amount + " coins from " + target.getName());
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /coins set <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        plugin.getEconomyManager().removeCoins(target, plugin.getEconomyManager().getBalance(target));
        if (plugin.getEconomyManager().addCoins(target, amount)) {
            sender.sendMessage("§aSet " + target.getName() + "'s balance to " + amount);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Coin Commands ===");
        sender.sendMessage("§7/coins §f- Check your balance");
        sender.sendMessage("§7/coins pay <player> <amount> §f- Pay another player");
        if (sender.hasPermission("mmorpg.coins.admin")) {
            sender.sendMessage("§7/coins give <player> <amount> §f- Give coins to a player");
            sender.sendMessage("§7/coins take <player> <amount> §f- Take coins from a player");
            sender.sendMessage("§7/coins set <player> <amount> §f- Set a player's coins");
        }
    }
}