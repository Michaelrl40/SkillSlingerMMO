package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinsCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public CoinsCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can check their balance!");
                return true;
            }
            showBalance((Player) sender, (Player) sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give":
                if (!sender.hasPermission("skillslinger.coins.admin")) {
                    sender.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                handleGive(sender, args);
                break;

            case "take":
                if (!sender.hasPermission("skillslinger.coins.admin")) {
                    sender.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                handleTake(sender, args);
                break;

            case "set":
                if (!sender.hasPermission("skillslinger.coins.admin")) {
                    sender.sendMessage("§cYou don't have permission to use this command!");
                    return true;
                }
                handleSet(sender, args);
                break;

            case "pay":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cOnly players can pay other players!");
                    return true;
                }
                handlePay((Player) sender, args);
                break;

            default:
                if (sender.hasPermission("skillslinger.coins.admin")) {
                    sendAdminHelp(sender);
                } else {
                    sendPlayerHelp(sender);
                }
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

        if (target.equals(sender)) {
            sender.sendMessage("§cYou can't pay yourself!");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive!");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        PlayerData senderData = plugin.getPlayerManager().getPlayerData(sender);
        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);

        if (!senderData.hasEnoughCoins(amount)) {
            sender.sendMessage("§c✦ You don't have enough coins! Required: " + amount + ", Balance: " + senderData.getCoins());
            return;
        }

        senderData.removeCoins(amount);
        targetData.addCoins(amount);

        sender.sendMessage("§a✦ Sent " + amount + " coins to " + target.getName());
        target.sendMessage("§a✦ Received " + amount + " coins from " + sender.getName());
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
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive!");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);
        targetData.addCoins(amount);
        sender.sendMessage("§a✦ Gave " + amount + " coins to " + target.getName());
        target.sendMessage("§a✦ Received " + amount + " coins from server");
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
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive!");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);
        if (targetData.removeCoins(amount)) {
            sender.sendMessage("§a✦ Took " + amount + " coins from " + target.getName());
            target.sendMessage("§c✦ Lost " + amount + " coins");
        } else {
            sender.sendMessage("§c✦ Player doesn't have enough coins!");
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
            if (amount < 0) {
                sender.sendMessage("§cAmount cannot be negative!");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return;
        }

        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);
        targetData.setCoins(amount);
        sender.sendMessage("§a✦ Set " + target.getName() + "'s coins to " + amount);
        target.sendMessage("§e✦ Your balance has been set to " + amount + " coins");
    }

    private void showBalance(Player target, Player viewer) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(target);
        String message = viewer == null || !viewer.equals(target)
                ? "§6" + target.getName() + "'s Balance: §f" + data.getCoins() + " coins"
                : "§6Balance: §f" + data.getCoins() + " coins";

        if (viewer != null) {
            viewer.sendMessage(message);
        } else {
            Bukkit.getConsoleSender().sendMessage(message);
        }
    }

    private void sendPlayerHelp(CommandSender sender) {
        sender.sendMessage("§6=== Coin Commands ===");
        sender.sendMessage("§7/coins §f- Check your balance");
        sender.sendMessage("§7/coins pay <player> <amount> §f- Pay another player");
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§6=== Coin Commands ===");
        sender.sendMessage("§7/coins §f- Check your balance");
        sender.sendMessage("§7/coins pay <player> <amount> §f- Pay another player");
        sender.sendMessage("§7/coins give <player> <amount> §f- Give coins to a player");
        sender.sendMessage("§7/coins take <player> <amount> §f- Take coins from a player");
        sender.sendMessage("§7/coins set <player> <amount> §f- Set a player's coins");
    }
}