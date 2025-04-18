package com.michael.mmorpg.graveyard;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.graveyard.Graveyard;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GraveyardCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public GraveyardCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            default:
                showHelp(sender);
                return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skillslinger.graveyard.admin")) {
            sender.sendMessage("§cYou don't have permission to create graveyards.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can create graveyards.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /graveyard create <name>");
            return true;
        }

        Player player = (Player) sender;
        String name = args[1];

        if (plugin.getGraveyardManager().createGraveyard(name, player.getLocation())) {
            sender.sendMessage("§aGraveyard '" + name + "' created at your location.");
        } else {
            sender.sendMessage("§cA graveyard with that name already exists.");
        }

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skillslinger.graveyard.admin")) {
            sender.sendMessage("§cYou don't have permission to remove graveyards.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /graveyard remove <name>");
            return true;
        }

        String name = args[1];

        if (plugin.getGraveyardManager().removeGraveyard(name)) {
            sender.sendMessage("§aGraveyard '" + name + "' has been removed.");
        } else {
            sender.sendMessage("§cCouldn't find a graveyard with that name.");
        }

        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<Graveyard> graveyards = plugin.getGraveyardManager().listGraveyards();

        if (graveyards.isEmpty()) {
            sender.sendMessage("§cThere are no graveyards defined yet.");
            return true;
        }

        sender.sendMessage("§6Graveyards:");
        for (Graveyard graveyard : graveyards) {
            Location loc = graveyard.getLocation();
            sender.sendMessage("§7- §f" + graveyard.getName() +
                    " §7(World: §f" + loc.getWorld().getName() +
                    "§7, X: §f" + (int)loc.getX() +
                    "§7, Y: §f" + (int)loc.getY() +
                    "§7, Z: §f" + (int)loc.getZ() + "§7)");
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6Graveyard Commands:");
        sender.sendMessage("§7/graveyard create <name> §f- Create a graveyard at your location");
        sender.sendMessage("§7/graveyard remove <name> §f- Remove a graveyard");
        sender.sendMessage("§7/graveyard list §f- List all graveyards");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            // Suggest existing graveyard names for removal
            return plugin.getGraveyardManager().listGraveyards().stream()
                    .map(Graveyard::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}