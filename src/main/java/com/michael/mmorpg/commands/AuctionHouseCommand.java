package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.AuctionHouseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionHouseCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public AuctionHouseCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✦ Only players can use the auction house!");
            return true;
        }

        Player player = (Player) sender;


        // Default command with no args: open auction house
        if (args.length == 0) {
            plugin.getAuctionHouseManager().openAuctionHouse(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "open":
            case "browse":
                plugin.getAuctionHouseManager().openAuctionHouse(player);
                return true;

            case "help":
                sendHelpMessage(player);
                return true;

            case "sell":
                if (args.length >= 2) {
                    // Get the item in hand
                    ItemStack itemInHand = player.getInventory().getItemInMainHand();

                    if (itemInHand == null || itemInHand.getType().isAir()) {
                        player.sendMessage("§c✦ You must be holding an item to sell!");
                        return true;
                    }

                    try {
                        int price = Integer.parseInt(args[1]);

                        // Validate price
                        if (price <= 0) {
                            player.sendMessage("§c✦ Price must be greater than 0!");
                            return true;
                        }

                        if (price > 1000000) {
                            player.sendMessage("§c✦ Price cannot exceed 1,000,000 coins!");
                            return true;
                        }

                        // Create listing directly
                        boolean success = plugin.getAuctionHouseManager().sellItemFromHand(player, itemInHand, price);

                        if (success) {
                            // Clear the item from the player's hand
                            player.getInventory().setItemInMainHand(null);
                            player.sendMessage("§a✦ Your item has been listed for " + price + " coins!");

                            // Open my listings page
                            plugin.getAuctionHouseManager().openMyListings(player);
                        }

                    } catch (NumberFormatException e) {
                        player.sendMessage("§c✦ Invalid price. Usage: /auctionhouse sell <price>");
                    }
                } else {
                    player.sendMessage("§c✦ Please specify a price. Usage: /auctionhouse sell <price>");
                }
                return true;

            case "listings":
            case "my":
            case "mylistings":
                // Open my listings page directly
                plugin.getAuctionHouseManager().openMyListings(player);
                return true;

            default:
                player.sendMessage("§c✦ Unknown auction house command. Type /auctionhouse help for assistance.");
                return true;
        }
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6§l===== Auction House Commands =====");
        player.sendMessage("§6/auctionhouse §7- Open the auction house");
        player.sendMessage("§6/auctionhouse open §7- Open the auction house");
        player.sendMessage("§6/auctionhouse sell <price> §7- Sell the item in your hand");
        player.sendMessage("§6/auctionhouse listings §7- View your active listings");
        player.sendMessage("§6/auctionhouse help §7- Show this help message");
        player.sendMessage("§7Aliases: §f/ah");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("open", "browse", "sell", "listings", "help");
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}