package com.michael.mmorpg.guild;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class Guild {
    private final UUID id;
    private String name;
    private UUID leaderId;
    private final Set<UUID> officers;
    private final Set<UUID> members;
    private final long creationDate;
    private final Set<UUID> founders;
    private static final int MAX_MEMBERS = 10;

    public Guild(UUID id, String name, UUID leaderId) {
        this.id = id;
        this.name = name;
        this.leaderId = leaderId;
        this.officers = new HashSet<>();
        this.members = new HashSet<>();
        this.members.add(leaderId); // Leader is also a member
        this.creationDate = System.currentTimeMillis();
        this.founders = new HashSet<>();
        this.founders.add(leaderId); // Creator is a founder
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public Set<UUID> getOfficers() {
        return Collections.unmodifiableSet(officers);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public Set<UUID> getFounders() {
        return Collections.unmodifiableSet(founders);
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public boolean addOfficer(UUID playerId) {
        // Player must be a member first
        if (!members.contains(playerId)) {
            return false;
        }

        return officers.add(playerId);
    }

    public boolean removeOfficer(UUID playerId) {
        return officers.remove(playerId);
    }

    public boolean addMember(UUID playerId) {
        if (isFull()) {
            return false;
        }

        return members.add(playerId);
    }

    public boolean removeMember(UUID playerId) {
        // Can't remove the leader this way
        if (playerId.equals(leaderId)) {
            return false;
        }

        // Remove from officer list if they're an officer
        officers.remove(playerId);

        return members.remove(playerId);
    }

    public void addFounder(UUID playerId) {
        if (members.contains(playerId)) {
            founders.add(playerId);
        }
    }

    public boolean isLeader(UUID playerId) {
        return playerId.equals(leaderId);
    }

    public boolean isOfficer(UUID playerId) {
        return officers.contains(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isFounder(UUID playerId) {
        return founders.contains(playerId);
    }

    public int getRank(UUID playerId) {
        if (isLeader(playerId)) return 3; // Leader rank
        if (isOfficer(playerId)) return 2; // Officer rank
        if (isMember(playerId)) return 1; // Member rank
        return 0; // Not in guild
    }

    public void broadcastMessage(String message) {
        for (UUID memberId : members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    public List<String> getMemberNames() {
        List<String> names = new ArrayList<>();
        for (UUID memberId : members) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null) {
                String prefix = "";
                if (isLeader(memberId)) prefix = "§6[Leader] ";
                else if (isOfficer(memberId)) prefix = "§e[Officer] ";
                else prefix = "§7[Member] ";

                names.add(prefix + player.getName());
            } else {
                // For offline players, get name from cache or use UUID
                String prefix = "";
                if (isLeader(memberId)) prefix = "§6[Leader] ";
                else if (isOfficer(memberId)) prefix = "§e[Officer] ";
                else prefix = "§7[Member] ";

                // You might want to implement a name cache for offline players
                names.add(prefix + memberId.toString().substring(0, 8) + "... (Offline)");
            }
        }
        return names;
    }
}