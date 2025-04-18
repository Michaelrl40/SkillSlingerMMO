package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
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

public class DungeonKeyCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;
    private final DungeonKey dungeonKey;

    public DungeonKeyCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.dungeonKey = new DungeonKey(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) && !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§c✦ Only players can use this command!");
            return true;
        }

        if (args.length < 1) {
            if (sender instanceof Player) {
                showHelp((Player) sender);
            } else {
                sender.sendMessage("§c✦ Usage: /dungeonkey give [dungeon name] [player]");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c✦ Only players can use this command!");
                    return true;
                }
                // Check permission
                if (!sender.hasPermission("mmorpg.dungeon.key")) {
                    sender.sendMessage("§c✦ You don't have permission to manage dungeon keys!");
                    return true;
                }
                return handleCreate((Player) sender, args);

            case "check":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c✦ Only players can use this command!");
                    return true;
                }
                // Check permission
                if (!sender.hasPermission("mmorpg.dungeon.key")) {
                    sender.sendMessage("§c✦ You don't have permission to manage dungeon keys!");
                    return true;
                }
                return handleCheck((Player) sender);

            case "give":
                // Check permission
                if (!sender.hasPermission("mmorpg.dungeon.key.give")) {
                    sender.sendMessage("§c✦ You don't have permission to give dungeon keys!");
                    return true;
                }
                return handleGive(sender, args);

            default:
                if (sender instanceof Player) {
                    showHelp((Player) sender);
                } else {
                    sender.sendMessage("§c✦ Unknown subcommand: " + subCommand);
                }
                return true;
        }
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c✦ Usage: /dungeonkey create [dungeon name] [key name] [key lore]");
            return true;
        }

        // Get the item player is holding
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType().isAir()) {
            player.sendMessage("§c✦ You must hold an item to convert into a dungeon key!");
            return true;
        }

        String dungeonName = args[1];
        Dungeon dungeon = plugin.getDungeonManager().getDungeonByName(dungeonName);

        if (dungeon == null) {
            player.sendMessage("§c✦ Dungeon not found: " + dungeonName);
            return true;
        }

        // Get key name from args[2]
        String keyName = args[2];

        // Rest of the args starting from index 3 is the lore
        String lore = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        // Convert the held item to a key using the correct method with the name parameter
        ItemStack keyItem = dungeonKey.convertToKey(heldItem, dungeonName, keyName, lore);

        // Replace the held item with the key
        player.getInventory().setItemInMainHand(keyItem);

        player.sendMessage("§a✦ Your item has been converted into a key named \"" + keyName + "\" for dungeon: " + dungeonName);
        player.sendMessage("§a✦ This key is now saved as the template for this dungeon.");
        return true;
    }

    private boolean handleCheck(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir()) {
            player.sendMessage("§c✦ You must hold an item to check if it's a dungeon key!");
            return true;
        }

        String dungeonName = dungeonKey.getDungeonNameFromKey(item);

        if (dungeonName != null) {
            player.sendMessage("§a✦ This is a key for dungeon: " + dungeonName);
        } else {
            player.sendMessage("§c✦ This item is not a dungeon key.");
        }

        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c✦ Usage: /dungeonkey give [dungeon name] [player]");
            return true;
        }

        String dungeonName = args[1];
        Dungeon dungeon = plugin.getDungeonManager().getDungeonByName(dungeonName);

        if (dungeon == null) {
            sender.sendMessage("§c✦ Dungeon not found: " + dungeonName);
            return true;
        }

        // Check if a key template exists for this dungeon
        if (!dungeonKey.hasKeyTemplate(dungeonName)) {
            sender.sendMessage("§c✦ No key template exists for dungeon: " + dungeonName);
            sender.sendMessage("§c✦ Create a key first using: /dungeonkey create " + dungeonName + " [key name] [lore]");
            return true;
        }

        String playerName = args[2];

        // Get the target player
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage("§c✦ Player not found: " + playerName);
            return true;
        }

        // Get a copy of the key template
        ItemStack keyItem = dungeonKey.getKeyForDungeon(dungeonName);

        // Give the key to the player
        targetPlayer.getInventory().addItem(keyItem);

        sender.sendMessage("§a✦ Given a key for dungeon \"" + dungeonName + "\" to " + targetPlayer.getName());
        targetPlayer.sendMessage("§a✦ You received a key for dungeon: " + dungeonName);

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l=== Dungeon Key Commands ===");
        player.sendMessage("§e/dungeonkey create [dungeon] [key name] [lore] §7- Convert held item to key");
        player.sendMessage("§e/dungeonkey check §7- Check if held item is a key");
        if (player.hasPermission("mmorpg.dungeon.key.give")) {
            player.sendMessage("§e/dungeonkey give [dungeon] [player] §7- Give a copy of the dungeon key to a player");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("create", "check"));
            if (sender.hasPermission("mmorpg.dungeon.key.give")) {
                subCommands.add("give");
            }
            return filterStartingWith(subCommands, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("give")) {
                List<String> dungeonNames = plugin.getDungeonManager().listDungeons().stream()
                        .map(Dungeon::getName)
                        .collect(Collectors.toList());
                return filterStartingWith(dungeonNames, args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return null; // Return null to show online players
        }

        return completions;
    }

    private List<String> filterStartingWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}