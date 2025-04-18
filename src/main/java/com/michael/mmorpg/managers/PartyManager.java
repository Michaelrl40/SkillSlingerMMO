package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class PartyManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Party> playerParties = new HashMap<>();
    private final Map<UUID, Party> parties = new HashMap<>();

    // Party invite system
    private final Map<UUID, UUID> pendingInvitations = new HashMap<>();
    private static final long INVITE_DURATION = 60000; // 60 seconds

    // Party chat system
    private final Set<UUID> inPartyChatMode = new HashSet<>();

    public PartyManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a new party with the specified player as leader
     *
     * @param leader The player who will be the party leader
     * @return The created party, or null if the player is already in a party
     */
    public Party createParty(Player leader) {
        if (getParty(leader) != null) {
            return null;  // Player already in a party
        }

        Party party = new Party(leader);
        parties.put(party.getPartyId(), party);
        playerParties.put(leader.getUniqueId(), party);
        return party;
    }

    /**
     * Adds a player to a party
     *
     * @param party The party to add the player to
     * @param player The player to add
     * @return true if the player was successfully added, false otherwise
     */
    public boolean addToParty(Party party, Player player) {
        if (getParty(player) != null) {
            return false;  // Player already in a party
        }

        if (party.addMember(player)) {
            playerParties.put(player.getUniqueId(), party);
            return true;
        }
        return false;
    }

    /**
     * Removes a player from their current party
     *
     * @param player The player to remove
     */
    public void removeFromParty(Player player) {
        Party party = getParty(player);
        if (party != null) {
            if (party.getLeader().equals(player)) {
                disbandParty(party);
            } else {
                party.removeMember(player);
                playerParties.remove(player.getUniqueId());
                removeFromPartyChatMode(player); // Remove from party chat mode
            }
        }
    }

    /**
     * Disbands a party, removing all members
     *
     * @param party The party to disband
     */
    public void disbandParty(Party party) {
        for (Player member : party.getMembers()) {
            playerParties.remove(member.getUniqueId());
            removeFromPartyChatMode(member); // Remove from party chat mode
        }
        parties.remove(party.getPartyId());
        party.disband();
    }

    /**
     * Gets the party a player is in
     *
     * @param player The player to check
     * @return The player's party, or null if they are not in a party
     */
    public Party getParty(Player player) {
        if (player == null) {
            return null;  // Safely handle null player
        }
        return playerParties.get(player.getUniqueId());
    }

    /**
     * Checks if two players are in the same party
     *
     * @param player1 The first player
     * @param player2 The second player
     * @return true if both players are in the same party, false otherwise
     */
    public boolean areInSameParty(Player player1, Player player2) {
        Party party1 = getParty(player1);
        return party1 != null && party1.isMember(player2);
    }

    /**
     * Handles party invitations, creating a party automatically if the sender isn't in one
     *
     * @param sender The player sending the invitation
     * @param target The player being invited
     * @return true if invitation was sent successfully, false otherwise
     */
    public boolean invitePlayer(Player sender, Player target) {
        // Check if target is already in a party
        if (getParty(target) != null) {
            sender.sendMessage("§c✦ " + target.getName() + " is already in a party!");
            return false;
        }

        // Check if target is the same as sender
        if (sender.equals(target)) {
            sender.sendMessage("§c✦ You cannot invite yourself to a party!");
            return false;
        }

        // Get or create a party for the sender
        Party party = getParty(sender);

        // If sender doesn't have a party, create one automatically
        if (party == null) {
            party = createParty(sender);
            sender.sendMessage("§a✦ Party created! You are now the leader.");
        } else if (!party.getLeader().equals(sender)) {
            // Only party leaders can invite others
            sender.sendMessage("§c✦ Only the party leader can invite players!");
            return false;
        }

        // Store the invitation
        storeInvitation(party, target);

        // Notify both players
        party.broadcast("§a✦ " + sender.getName() + " invited " + target.getName() + " to the party!");
        target.sendMessage("§a✦ " + sender.getName() + " has invited you to their party!");
        target.sendMessage("§a✦ Type §e/party accept§a to join or §e/party decline§a to decline.");

        return true;
    }

    /**
     * Stores a party invitation for a player
     *
     * @param party The party the player is invited to
     * @param invited The player being invited
     */
    private void storeInvitation(Party party, Player invited) {
        pendingInvitations.put(invited.getUniqueId(), party.getPartyId());

        // Remove the invitation after a timeout (e.g., 60 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                UUID partyId = pendingInvitations.get(invited.getUniqueId());
                if (partyId != null && partyId.equals(party.getPartyId())) {
                    pendingInvitations.remove(invited.getUniqueId());
                    Party currentParty = parties.get(partyId);
                    if (currentParty != null && currentParty.getLeader() != null) {
                        Player leader = currentParty.getLeader();
                        leader.sendMessage("§e✦ Your invitation to " + invited.getName() + " has expired.");
                        if (invited.isOnline()) {
                            invited.sendMessage("§e✦ Party invitation from " + leader.getName() + " has expired.");
                        }
                    }
                }
            }
        }.runTaskLater(plugin, INVITE_DURATION / 50); // Convert ms to ticks
    }

    /**
     * Accepts a pending party invitation
     *
     * @param player The player accepting the invitation
     * @return true if successfully joined, false if no invitation exists
     */
    public boolean acceptInvitation(Player player) {
        UUID partyId = pendingInvitations.get(player.getUniqueId());
        if (partyId == null) {
            player.sendMessage("§c✦ You don't have any pending party invitations!");
            return false;
        }

        Party party = parties.get(partyId);
        if (party == null) {
            player.sendMessage("§c✦ The party no longer exists!");
            pendingInvitations.remove(player.getUniqueId());
            return false;
        }

        // Add player to party
        if (addToParty(party, player)) {
            pendingInvitations.remove(player.getUniqueId());
            party.broadcast("§a✦ " + player.getName() + " has joined the party!");
            return true;
        } else {
            player.sendMessage("§c✦ Could not join the party!");
            return false;
        }
    }

    /**
     * Declines a pending party invitation
     *
     * @param player The player declining the invitation
     * @return true if declined successfully, false if no invitation exists
     */
    public boolean declineInvitation(Player player) {
        UUID partyId = pendingInvitations.get(player.getUniqueId());
        if (partyId == null) {
            player.sendMessage("§c✦ You don't have any pending party invitations!");
            return false;
        }

        Party party = parties.get(partyId);
        pendingInvitations.remove(player.getUniqueId());

        player.sendMessage("§e✦ You declined the party invitation.");

        if (party != null && party.getLeader() != null) {
            party.getLeader().sendMessage("§e✦ " + player.getName() + " declined your party invitation.");
        }

        return true;
    }

    /**
     * Toggles a player's party chat mode
     *
     * @param player The player to toggle party chat for
     * @return true if enabled, false if disabled
     */
    public boolean togglePartyChat(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if player is in a party
        Party party = getParty(player);
        if (party == null) {
            player.sendMessage("§c✦ You must be in a party to use party chat!");
            return false;
        }

        // Toggle party chat mode
        if (inPartyChatMode.contains(playerId)) {
            inPartyChatMode.remove(playerId);
            player.sendMessage("§e✦ Party chat disabled. Your messages will now go to global chat.");
            return false;
        } else {
            inPartyChatMode.add(playerId);
            player.sendMessage("§a✦ Party chat enabled! Your messages will now only be seen by party members.");
            return true;
        }
    }

    /**
     * Checks if a player is in party chat mode
     *
     * @param player The player to check
     * @return true if in party chat mode, false otherwise
     */
    public boolean isInPartyChatMode(Player player) {
        return inPartyChatMode.contains(player.getUniqueId());
    }

    /**
     * Sends a message to all members of a player's party
     *
     * @param sender The player sending the message
     * @param message The message to send
     * @return true if message was sent, false if player is not in a party
     */
    public boolean sendPartyChatMessage(Player sender, String message) {
        Party party = getParty(sender);
        if (party == null) {
            sender.sendMessage("§c✦ You must be in a party to use party chat!");
            return false;
        }

        // Format the message with party chat prefix
        String formattedMessage = "§d[Party] §r" + sender.getDisplayName() + "§r: " + message;

        // Send to all party members
        for (Player member : party.getMembers()) {
            member.sendMessage(formattedMessage);
        }

        // Log the party chat (optional)
        plugin.getLogger().info("[Party Chat] [" + party.getPartyId() + "] " + sender.getName() + ": " + message);

        return true;
    }

    /**
     * Removes a player from party chat mode when they leave a party
     *
     * @param player The player leaving the party
     */
    private void removeFromPartyChatMode(Player player) {
        inPartyChatMode.remove(player.getUniqueId());
    }

    /**
     * Gets the ID of the party that invited a player
     *
     * @param player The player to check
     * @return The UUID of the party, or null if no invitation exists
     */
    public UUID getPendingInvitation(Player player) {
        return pendingInvitations.get(player.getUniqueId());
    }
}