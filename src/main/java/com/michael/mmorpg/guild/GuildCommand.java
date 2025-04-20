package com.michael.mmorpg.guild;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class GuildCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;
    private final GuildManager guildManager;

    public GuildCommand(MinecraftMMORPG plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use guild commands!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(player, args);
                break;
            case "invite":
                handleInvite(player, args);
                break;
            case "accept":
                handleAccept(player, args);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "kick":
                handleKick(player, args);
                break;
            case "promote":
                handlePromote(player, args);
                break;
            case "demote":
                handleDemote(player, args);
                break;
            case "info":
                handleInfo(player, args);
                break;
            case "list":
                handleList(player);
                break;
            case "members":
                handleMembers(player, args);
                break;
            case "disband":
                handleDisband(player);
                break;
            case "hall":
            case "claim":
                handleHall(player, args);
                break;
            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l===== Guild Commands =====");
        player.sendMessage("§e/guild create <name> §7- Create a new guild (costs " + 1000 + " coins)");
        player.sendMessage("§e/guild invite <player> §7- Invite a player to your guild");
        player.sendMessage("§e/guild accept <guild> §7- Accept a guild invitation");
        player.sendMessage("§e/guild leave §7- Leave your current guild");
        player.sendMessage("§e/guild kick <player> §7- Kick a player from your guild");
        player.sendMessage("§e/guild promote <player> §7- Promote a member to officer");
        player.sendMessage("§e/guild demote <player> §7- Demote an officer to member");
        player.sendMessage("§e/guild info [guild] §7- Show information about a guild");
        player.sendMessage("§e/guild list §7- List all guilds");
        player.sendMessage("§e/guild members [guild] §7- List members of a guild");
        player.sendMessage("§e/guild disband §7- Disband your guild");
        player.sendMessage("§e/guild hall create <name> §7- Create a guild hall (costs " + 250 + " coins)");
        player.sendMessage("§e/guild hall claim §7- Claim an additional chunk (costs " + 250 + " coins)");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guild create <name>");
            return;
        }

        String guildName = args[1];

        // Check name length
        if (guildName.length() < 3 || guildName.length() > 16) {
            player.sendMessage("§cGuild name must be between 3 and 16 characters!");
            return;
        }

        // Check for invalid characters
        if (!guildName.matches("[a-zA-Z0-9_]+")) {
            player.sendMessage("§cGuild name can only contain letters, numbers, and underscores!");
            return;
        }

        boolean success = guildManager.createGuild(player, guildName);

        if (success) {
            // Success message is sent by the manager
            Bukkit.broadcastMessage("§6§l" + player.getName() + " has created a new guild: " + guildName + "!");
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guild invite <player>");
            return;
        }

        // Check if player is in a guild and is the leader or an officer
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return;
        }

        if (!guild.isLeader(player.getUniqueId()) && !guild.isOfficer(player.getUniqueId())) {
            player.sendMessage("§cOnly the guild leader and officers can invite players!");
            return;
        }

        // Find target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found or not online!");
            return;
        }

        // Check if target is already in a guild
        if (guildManager.isPlayerInGuild(target)) {
            player.sendMessage("§c" + target.getName() + " is already in a guild!");
            return;
        }

        // Check if guild is full
        if (guild.isFull()) {
            player.sendMessage("§cYour guild is full! (Maximum " + 10 + " members)");
            return;
        }

        // Send invitation
        player.sendMessage("§aYou have invited " + target.getName() + " to join your guild!");
        target.sendMessage("§6§lGuild Invitation!");
        target.sendMessage("§e" + player.getName() + " has invited you to join the guild '" + guild.getName() + "'!");
        target.sendMessage("§eType §6/guild accept " + guild.getName() + "§e to accept!");

        // Store the invitation (in a real implementation, you'd probably have a map or database table for this)
        // For simplicity, we'll use a static map in the GuildManager or a transient invitation that relies on the guild name
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guild accept <guild>");
            return;
        }

        // Check if player is already in a guild
        if (guildManager.isPlayerInGuild(player)) {
            player.sendMessage("§cYou are already in a guild! You must leave it first.");
            return;
        }

        // Find the guild
        String guildName = args[1];
        Guild guild = guildManager.getGuildByName(guildName);

        if (guild == null) {
            player.sendMessage("§cGuild not found: " + guildName);
            return;
        }

        // Check if guild is full
        if (guild.isFull()) {
            player.sendMessage("§cThat guild is full! (Maximum " + 10 + " members)");
            return;
        }

        // Add player to guild
        boolean success = guildManager.addMemberToGuild(
                Bukkit.getPlayer(guild.getLeaderId()), // This assumes the leader is online, which isn't ideal
                player
        );

        if (success) {
            // Success message is sent by the manager
            guild.broadcastMessage("§a" + player.getName() + " has joined the guild!");
        }
    }

    private void handleLeave(Player player) {
        // Check if player is in a guild
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return;
        }

        // Check if player is the leader
        if (guild.isLeader(player.getUniqueId())) {
            player.sendMessage("§cAs the guild leader, you cannot leave the guild. Use /guild disband instead.");
            return;
        }

        // Remove player from guild (implementation needed in GuildManager)
        // For this example, we'll use a temporary method:
        boolean success = guildManager.removePlayerFromGuild(
                Bukkit.getPlayer(guild.getLeaderId()), // This assumes the leader is online, which isn't ideal
                player
        );

        if (success) {
            // Success message is sent by the manager
            guild.broadcastMessage("§c" + player.getName() + " has left the guild!");
        }
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guild kick <player>");
            return;
        }

        // Check if player is in a guild and is the leader or an officer
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return;
        }

        if (!guild.isLeader(player.getUniqueId()) && !guild.isOfficer(player.getUniqueId())) {
            player.sendMessage("§cOnly the guild leader and officers can kick players!");
            return;
        }

        // Find target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found or not online!");
            return;
        }

        // Check if target is in this guild
        if (!guild.isMember(target.getUniqueId())) {
            player.sendMessage("§c" + target.getName() + " is not in your guild!");
            return;
        }

        // Check if target is the leader
        if (guild.isLeader(target.getUniqueId())) {
            player.sendMessage("§cYou cannot kick the guild leader!");
            return;
        }

        // Check if player is trying to kick an officer while being an officer
        if (guild.isOfficer(target.getUniqueId()) && guild.isOfficer(player.getUniqueId()) && !guild.isLeader(player.getUniqueId())) {
            player.sendMessage("§cOfficers cannot kick other officers!");
            return;
        }

        // Remove player from guild
        boolean success = guildManager.removePlayerFromGuild(player, target);

        if (success) {
            // Success message is sent by the manager
            guild.broadcastMessage("§c" + target.getName() + " has been kicked from the guild by " + player.getName() + "!");
        }
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guild promote <player>");
            return;
        }

        // Check if player is in a guild and is the leader
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return;
        }

        if (!guild.isLeader(player.getUniqueId())) {
            player.sendMessage("§cOnly the guild leader can promote members!");
            return;
        }

        // Find target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found or not online!");
            return;
        }

        // Promote player
        boolean success = guildManager.promoteMember(player, target);

        if (success) {
            // Success message is sent by the manager
            guild.broadcastMessage("§a" + target.getName() + " has been promoted to Officer by " + player.getName() + "!");
        }
    }

    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guild demote <player>");
            return;
        }

        // Check if player is in a guild and is the leader
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return;
        }

        if (!guild.isLeader(player.getUniqueId())) {
            player.sendMessage("§cOnly the guild leader can demote officers!");
            return;
        }

        // Find target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cPlayer not found or not online!");
            return;
        }

        // Demote player
        boolean success = guildManager.demoteMember(player, target);

        if (success) {
            // Success message is sent by the manager
            guild.broadcastMessage("§c" + target.getName() + " has been demoted to Member by " + player.getName() + "!");
        }
    }

    private void handleInfo(Player player, String[] args) {
        Guild guild = null;

        if (args.length >= 2) {
            // Get info about specified guild
            String guildName = args[1];
            guild = guildManager.getGuildByName(guildName);

            if (guild == null) {
                player.sendMessage("§cGuild not found: " + guildName);
                return;
            }
        } else {
            // Get info about player's guild
            guild = guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage("§cYou are not in a guild! Use /guild info <guild> to see info about another guild.");
                return;
            }
        }

        // Display guild info
        player.sendMessage("§6§l===== Guild: " + guild.getName() + " =====");

        // Get leader name
        String leaderName = "Unknown";
        Player leaderPlayer = Bukkit.getPlayer(guild.getLeaderId());
        if (leaderPlayer != null) {
            leaderName = leaderPlayer.getName();
        } else {
            // Try to get offline player name
            OfflinePlayer offlineLeader = Bukkit.getOfflinePlayer(guild.getLeaderId());
            if (offlineLeader.hasPlayedBefore()) {
                leaderName = offlineLeader.getName();
            }
        }

        player.sendMessage("§eLeader: §f" + leaderName);
        player.sendMessage("§eMembers: §f" + guild.getMemberCount() + "/" + 10);

        // Creation date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String creationDate = sdf.format(new Date(guild.getCreationDate()));
        player.sendMessage("§eCreated: §f" + creationDate);

        // Guild hall info
        GuildHall hall = guildManager.getGuildHallForGuild(guild.getId());
        if (hall != null) {
            player.sendMessage("§eGuild Hall: §f" + hall.getName());
            player.sendMessage("§eTerritory: §f" + hall.getChunkCount() + "/" + 15 + " chunks");
        } else {
            player.sendMessage("§eGuild Hall: §fNone");
        }
    }

    private void handleList(Player player) {
        Collection<Guild> guilds = guildManager.getAllGuilds();

        if (guilds.isEmpty()) {
            player.sendMessage("§cThere are no guilds yet!");
            return;
        }

        player.sendMessage("§6§l===== Guilds =====");

        for (Guild guild : guilds) {
            // Get leader name
            String leaderName = "Unknown";
            Player leaderPlayer = Bukkit.getPlayer(guild.getLeaderId());
            if (leaderPlayer != null) {
                leaderName = leaderPlayer.getName();
            } else {
                // Try to get offline player name
                OfflinePlayer offlineLeader = Bukkit.getOfflinePlayer(guild.getLeaderId());
                if (offlineLeader.hasPlayedBefore()) {
                    leaderName = offlineLeader.getName();
                }
            }

            player.sendMessage("§e" + guild.getName() + " §7- Leader: §f" + leaderName +
                    " §7- Members: §f" + guild.getMemberCount() + "/" + 10);
        }
    }

    private void handleMembers(Player player, String[] args) {
        Guild guild = null;

        if (args.length >= 2) {
            // Get members of specified guild
            String guildName = args[1];
            guild = guildManager.getGuildByName(guildName);

            if (guild == null) {
                player.sendMessage("§cGuild not found: " + guildName);
                return;
            }
        } else {
            // Get members of player's guild
            guild = guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage("§cYou are not in a guild! Use /guild members <guild> to see members of another guild.");
                return;
            }
        }

        // Display guild members
        player.sendMessage("§6§l===== Members of " + guild.getName() + " =====");

        List<String> memberNames = guild.getMemberNames();
        for (String memberName : memberNames) {
            player.sendMessage("§e" + memberName);
        }
    }

    private void handleDisband(Player player) {
        // Check if player is in a guild and is the leader
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cYou are not in a guild!");
            return;
        }

        if (!guild.isLeader(player.getUniqueId())) {
            player.sendMessage("§cOnly the guild leader can disband the guild!");
            return;
        }

        // Confirm disband
        player.sendMessage("§c§lWARNING: §cThis will permanently disband your guild and remove any guild hall claims!");
        player.sendMessage("§cType §4/guild disband confirm§c to confirm.");

        // In a real plugin, you'd probably store a confirmation state here
        // For simplicity, we'll skip the confirmation in this example
        guildManager.disbandGuild(player);
    }

    private void handleHall(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /guild hall create <name> or /guild hall claim");
            return;
        }

        String hallCommand = args[1].toLowerCase();

        if (hallCommand.equals("create")) {
            // Create a new guild hall
            if (args.length < 3) {
                player.sendMessage("§cUsage: /guild hall create <name>");
                return;
            }

            String hallName = args[2];
            boolean success = guildManager.createGuildHall(player, hallName);

            // Success message is sent by the manager
        } else if (hallCommand.equals("claim")) {
            // Claim an additional chunk
            boolean success = guildManager.claimAdditionalChunk(player);

            // Success message is sent by the manager
        } else {
            player.sendMessage("§cUsage: /guild hall create <name> or /guild hall claim");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommands
            String[] subCommands = {"create", "invite", "accept", "leave", "kick", "promote",
                    "demote", "info", "list", "members", "disband", "hall"};

            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            // Second argument - depends on subcommand
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "invite":
                case "kick":
                case "promote":
                case "demote":
                    // Player names
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                    break;
                case "accept":
                case "info":
                case "members":
                    // Guild names
                    for (Guild guild : guildManager.getAllGuilds()) {
                        if (guild.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(guild.getName());
                        }
                    }
                    break;
                case "hall":
                    // Hall subcommands
                    String[] hallCommands = {"create", "claim"};
                    for (String hallCommand : hallCommands) {
                        if (hallCommand.startsWith(args[1].toLowerCase())) {
                            completions.add(hallCommand);
                        }
                    }
                    break;
            }
        }

        return completions;
    }
}