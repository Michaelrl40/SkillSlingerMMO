package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SellGoldCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;
    private final int GOLD_PRICE = 8; // Price per gold item in coins

    // Define which materials are considered gold items
    private final List<Material> GOLD_MATERIALS = Arrays.asList(
            Material.GOLD_INGOT
    );

    public SellGoldCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("skillslinger.sellgold")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        int amountToSell = -1; // Default -1 means sell all

        // Check for amount argument
        if (args.length > 0) {
            try {
                amountToSell = Integer.parseInt(args[0]);
                if (amountToSell <= 0) {
                    player.sendMessage(ChatColor.RED + "Amount must be a positive number.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid amount. Usage: /sellgold [amount]");
                return true;
            }
        }

        // Track items in inventory, total gold items, and which items to remove
        List<ItemStack> goldItems = new ArrayList<>();
        int totalGoldItems = 0;

        // First, find all gold items and count them
        for (ItemStack item : inventory.getContents()) {
            if (item != null && GOLD_MATERIALS.contains(item.getType())) {
                goldItems.add(item.clone()); // Clone to avoid modifying original until we're ready
                totalGoldItems += item.getAmount();
            }
        }

        if (totalGoldItems == 0) {
            player.sendMessage(ChatColor.RED + "You don't have any gold items to sell.");
            return true;
        }

        // Determine how many to sell
        int itemsToSell = (amountToSell == -1) ? totalGoldItems : Math.min(amountToSell, totalGoldItems);
        int remainingToSell = itemsToSell;

        // Items that will be removed or modified
        List<ItemStack> itemsToRemove = new ArrayList<>();
        List<ItemStack> itemsToModify = new ArrayList<>();

        // Process items for removal or modification
        for (ItemStack item : goldItems) {
            if (remainingToSell <= 0) break;

            if (item.getAmount() <= remainingToSell) {
                // Remove entire stack
                itemsToRemove.add(item);
                remainingToSell -= item.getAmount();
            } else {
                // Modify stack
                ItemStack modifiedItem = item.clone();
                modifiedItem.setAmount(modifiedItem.getAmount() - remainingToSell);
                itemsToModify.add(modifiedItem);
                remainingToSell = 0;
            }
        }

        // Calculate total value
        int totalValue = itemsToSell * GOLD_PRICE;

        // Remove items from inventory
        for (ItemStack item : itemsToRemove) {
            inventory.remove(Material.matchMaterial(item.getType().toString()));
        }

        // Update modified stacks
        for (ItemStack modifiedItem : itemsToModify) {
            // Find matching item in inventory and update its amount
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack invItem = inventory.getItem(i);
                if (invItem != null && invItem.getType() == modifiedItem.getType()) {
                    inventory.setItem(i, modifiedItem);
                    break;
                }
            }
        }

        // Add coins to the player's balance
        plugin.getEconomyManager().addCoins(player, totalValue);

        // Inform the player
        if (itemsToSell == totalGoldItems) {
            player.sendMessage(ChatColor.GREEN + "You sold all " + itemsToSell + " gold items for " + totalValue + " coins.");
        } else {
            player.sendMessage(ChatColor.GREEN + "You sold " + itemsToSell + " gold items for " + totalValue + " coins.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("all"); // Suggest "all" as an option

            // Add some common quantity suggestions
            completions.add("1");
            completions.add("5");
            completions.add("10");
            completions.add("64");

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}