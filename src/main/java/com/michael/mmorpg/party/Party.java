package com.michael.mmorpg.party;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.MetadataValue;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {
    private final UUID partyId;
    private Player leader;
    private final Set<Player> members;

    public Party(Player leader) {
        this.partyId = UUID.randomUUID();
        this.leader = leader;
        this.members = new HashSet<>();
        this.members.add(leader);
    }

    public boolean addMember(Player player) {
        return members.add(player);
    }

    public void removeMember(Player player) {
        members.remove(player);
        if (player.equals(leader) && !members.isEmpty()) {
            leader = members.iterator().next();
            broadcast("§e✦ " + leader.getName() + " is now the party leader!");
        }
    }

    public void broadcast(String message) {
        for (Player member : members) {
            member.sendMessage(message);
        }
    }

    public void disband() {
        broadcast("§c✦ The party has been disbanded!");
        members.clear();
        leader = null;
    }

    public boolean isMember(Player player) {
        return members.contains(player);
    }

    public boolean shouldPreventInteraction(Entity source, Entity target, boolean isHarmful) {
        // For beneficial skills (healing etc)
        if (!isHarmful) {
            // If target is not a player at all, prevent interaction
            if (!(target instanceof Player)) {
                return true; // Prevent beneficial skills on non-players
            }

            Player targetPlayer = (Player) target;
            Player sourcePlayer = getSourcePlayer(source);
            if (sourcePlayer == null) return true;

            // Only allow beneficial skills on party members
            return !isMember(targetPlayer); // Return true (prevent) if target is NOT a party member
        }

        // For harmful skills
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            Player sourcePlayer = getSourcePlayer(source);
            if (sourcePlayer == null) return false;

            // Prevent harmful skills on party members
            if (isMember(sourcePlayer) && isMember(targetPlayer)) {
                return true; // Prevent harm to party members
            }
        }

        // Allow harmful skills on non-party members and mobs
        return false;
    }

    private Player getSourcePlayer(Entity source) {
        if (source instanceof Player) {
            return (Player) source;
        }
        if (source instanceof Projectile && ((Projectile) source).getShooter() instanceof Player) {
            return (Player) ((Projectile) source).getShooter();
        }
        if (source.hasMetadata("skill_source") || source.hasMetadata("skill_damage")) {
            for (MetadataValue meta : source.getMetadata(source.hasMetadata("skill_source") ? "skill_source" : "skill_damage")) {
                if (meta.value() instanceof Player) {
                    return (Player) meta.value();
                }
            }
        }
        return null;
    }

    public Player getLeader() {
        return leader;
    }

    public void handleLeaderDisconnect(Player leader) {
        if (members.isEmpty()) {
            return;
        }

        // Get the longest-standing member (first to join after leader)
        Player newLeader = members.iterator().next();
        this.leader = newLeader;
        broadcast("§e✦ " + leader.getName() + " has disconnected! " + newLeader.getName() + " is now the party leader!");
    }

    public void handleMemberDisconnect(Player member) {
        removeMember(member);
        broadcast("§e✦ " + member.getName() + " has disconnected from the party!");
    }

    public Set<Player> getMembers() {
        return new HashSet<>(members);
    }

    public UUID getPartyId() {
        return partyId;
    }
}