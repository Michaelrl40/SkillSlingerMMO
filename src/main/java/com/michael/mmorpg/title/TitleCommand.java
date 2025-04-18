package com.michael.mmorpg.title;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.title.Title;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TitleCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public TitleCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                return handleListTitles(player);
            case "set":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /title set <titleId>");
                    return true;
                }
                return handleSetTitle(player, args[1]);
            case "clear":
                return handleClearTitle(player);
            case "info":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /title info <titleId>");
                    return true;
                }
                return handleTitleInfo(player, args[1]);
            case "give":
                if (!player.hasPermission("skillslinger.title.admin")) {
                    player.sendMessage("§cYou don't have permission to use this command.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /title give <player> <titleId>");
                    return true;
                }
                return handleGiveTitle(player, args[1], args[2]);
            default:
                player.sendMessage("§cUnknown subcommand. Use /title help for assistance.");
                return true;
        }
    }

    private boolean handleListTitles(Player player) {
        Set<String> playerTitleIds = plugin.getTitleManager().getPlayerTitles(player.getUniqueId());
        String activeTitle = plugin.getTitleManager().getActiveTitle(player.getUniqueId());

        if (playerTitleIds.isEmpty()) {
            player.sendMessage("§cYou don't have any titles yet.");
            return true;
        }

        player.sendMessage("§6=== Your Titles ===");

        for (String titleId : playerTitleIds) {
            Title title = plugin.getTitleManager().getTitle(titleId);
            if (title != null) {
                String status = titleId.equals(activeTitle) ? " §a[Active]" : "";
                player.sendMessage(title.getFormattedTitle() + " - " + title.getDescription() + status);
            }
        }

        player.sendMessage("§7Use /title set <titleId> to set your active title.");
        return true;
    }

    private boolean handleSetTitle(Player player, String titleId) {
        Set<String> playerTitleIds = plugin.getTitleManager().getPlayerTitles(player.getUniqueId());

        if (!playerTitleIds.contains(titleId)) {
            player.sendMessage("§cYou don't have this title.");
            return true;
        }

        Title title = plugin.getTitleManager().getTitle(titleId);
        if (title == null) {
            player.sendMessage("§cInvalid title ID.");
            return true;
        }

        plugin.getTitleManager().setActiveTitle(player.getUniqueId(), titleId);
        player.sendMessage("§aYour title has been set to " + title.getFormattedTitle());
        return true;
    }

    private boolean handleClearTitle(Player player) {
        if (plugin.getTitleManager().getActiveTitle(player.getUniqueId()) == null) {
            player.sendMessage("§cYou don't have an active title.");
            return true;
        }

        plugin.getTitleManager().clearActiveTitle(player.getUniqueId());
        player.sendMessage("§aYour title has been cleared.");
        return true;
    }

    private boolean handleTitleInfo(Player player, String titleId) {
        Title title = plugin.getTitleManager().getTitle(titleId);

        if (title == null) {
            player.sendMessage("§cInvalid title ID.");
            return true;
        }

        player.sendMessage("§6=== Title Info ===");
        player.sendMessage("§7ID: §f" + title.getId());
        player.sendMessage("§7Display: " + title.getFormattedTitle());
        player.sendMessage("§7Description: §f" + title.getDescription());
        player.sendMessage("§7Category: §f" + title.getCategory());
        player.sendMessage("§7Rarity: " + title.getRarity().getColor() + title.getRarity());

        boolean playerHas = plugin.getTitleManager().getPlayerTitles(player.getUniqueId()).contains(titleId);
        player.sendMessage("§7Unlocked: " + (playerHas ? "§aYes" : "§cNo"));

        return true;
    }

    private boolean handleGiveTitle(Player player, String targetName, String titleId) {
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            player.sendMessage("§cPlayer not found.");
            return true;
        }

        Title title = plugin.getTitleManager().getTitle(titleId);

        if (title == null) {
            player.sendMessage("§cInvalid title ID.");
            return true;
        }

        boolean success = plugin.getTitleManager().awardTitle(target.getUniqueId(), titleId);

        if (success) {
            player.sendMessage("§aAwarded title " + title.getFormattedTitle() + " §ato " + target.getName());
            target.sendMessage("§aYou've been awarded the title: " + title.getFormattedTitle());
            target.sendMessage("§7Use /title set " + titleId + " to use it.");
        } else {
            player.sendMessage("§cPlayer already has this title or an error occurred.");
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== Title Commands ===");
        player.sendMessage("§e/title list §7- List all your titles");
        player.sendMessage("§e/title set <titleId> §7- Set your active title");
        player.sendMessage("§e/title clear §7- Remove your active title");
        player.sendMessage("§e/title info <titleId> §7- Show info about a title");

        if (player.hasPermission("skillslinger.title.admin")) {
            player.sendMessage("§e/title give <player> <titleId> §7- Give a title to a player");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("list");
            subCommands.add("set");
            subCommands.add("clear");
            subCommands.add("info");

            if (player.hasPermission("skillslinger.title.admin")) {
                subCommands.add("give");
            }

            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                // Return only titles the player has
                return plugin.getTitleManager().getPlayerTitles(player.getUniqueId()).stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("info")) {
                // Return all title IDs
                return plugin.getTitleManager().getAllTitles().stream()
                        .map(Title::getId)
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("give") && player.hasPermission("skillslinger.title.admin")) {
                // Return online player names
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && player.hasPermission("skillslinger.title.admin")) {
            // Return all title IDs
            return plugin.getTitleManager().getAllTitles().stream()
                    .map(Title::getId)
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}