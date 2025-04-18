package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PartyCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public PartyCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendPartyHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                handleCreate(player);
                break;
            case "invite":
                if (args.length < 2) {
                    player.sendMessage("§c✦ Usage: /party invite <player>");
                    return true;
                }
                handleInvite(player, args[1]);
                break;
            case "accept":
                handleAccept(player);
                break;
            case "decline":
                handleDecline(player);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "info":
                handleInfo(player);
                break;
            case "chat":
                handleChat(player, args);
                break;
            default:
                sendPartyHelp(player);
                break;
        }

        return true;
    }

    private void handleCreate(Player player) {
        Party existingParty = plugin.getPartyManager().getParty(player);
        if (existingParty != null) {
            player.sendMessage("§c✦ You are already in a party!");
            return;
        }

        Party party = plugin.getPartyManager().createParty(player);
        if (party != null) {
            player.sendMessage("§a✦ Created a new party! Invite players with /party invite <player>");
        }
    }

    private void handleInvite(Player player, String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§c✦ Player not found: " + targetName);
            return;
        }

        // Use the new streamlined invite method
        plugin.getPartyManager().invitePlayer(player, target);
    }

    private void handleAccept(Player player) {
        // Use the new accept method that doesn't require specifying who invited you
        plugin.getPartyManager().acceptInvitation(player);
    }

    private void handleDecline(Player player) {
        // Use the new decline method that doesn't require specifying who invited you
        plugin.getPartyManager().declineInvitation(player);
    }

    private void handleLeave(Player player) {
        Party party = plugin.getPartyManager().getParty(player);
        if (party == null) {
            player.sendMessage("§c✦ You are not in a party!");
            return;
        }

        if (party.getLeader().equals(player)) {
            plugin.getPartyManager().disbandParty(party);
            return;
        }

        plugin.getPartyManager().removeFromParty(player);
        player.sendMessage("§a✦ You have left the party!");
        party.broadcast("§c✦ " + player.getName() + " has left the party!");
    }

    private void handleInfo(Player player) {
        Party party = plugin.getPartyManager().getParty(player);
        if (party == null) {
            player.sendMessage("§c✦ You are not in a party!");
            return;
        }

        player.sendMessage("§6=== Party Information ===");
        player.sendMessage("§eLeader: " + party.getLeader().getName());
        player.sendMessage("§eMembers:");
        for (Player member : party.getMembers()) {
            String prefix = member.equals(party.getLeader()) ? "§6★ " : "§7• ";
            String chatMode = plugin.getPartyManager().isInPartyChatMode(member) ? " §d[Party Chat]" : "";
            player.sendMessage(prefix + member.getName() + chatMode);
        }
    }

    private void handleChat(Player player, String[] args) {
        // If there are more arguments, it's a direct message
        if (args.length > 1) {
            // Combine all arguments after "chat" into one message
            StringBuilder message = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) message.append(" ");
                message.append(args[i]);
            }

            // Send the message
            plugin.getPartyManager().sendPartyChatMessage(player, message.toString());
        }
        // Otherwise, toggle party chat mode
        else {
            plugin.getPartyManager().togglePartyChat(player);
        }
    }

    private void sendPartyHelp(Player player) {
        player.sendMessage("§6=== Party Commands ===");
        player.sendMessage("§7/party create §f- Create a new party");
        player.sendMessage("§7/party invite <player> §f- Invite a player");
        player.sendMessage("§7/party accept §f- Accept an invite");
        player.sendMessage("§7/party decline §f- Decline an invite");
        player.sendMessage("§7/party leave §f- Leave your party");
        player.sendMessage("§7/party info §f- Show party information");
        player.sendMessage("§7/party chat §f- Toggle party chat mode");
        player.sendMessage("§7/party chat <message> §f- Send a message to your party");
    }
}