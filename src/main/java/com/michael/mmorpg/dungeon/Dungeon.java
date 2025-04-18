package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class Dungeon {
    private final UUID id;
    private String name;
    private World world;
    private Location entranceLocation;
    private boolean occupied;
    private Party currentParty;
    private long occupationStartTime;
    private int timeLimit = 1200; // Default: 20 minutes (in seconds)
    private BukkitTask timeoutTask;

    public Dungeon(UUID id, String name, World world) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.occupied = false;
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

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Location getEntranceLocation() {
        return entranceLocation;
    }

    public void setEntranceLocation(Location entranceLocation) {
        this.entranceLocation = entranceLocation;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public Party getCurrentParty() {
        return currentParty;
    }

    // Update occupy method to start a timeout timer
    public void occupyDungeon(Party party) {
        this.occupied = true;
        this.currentParty = party;
        this.occupationStartTime = System.currentTimeMillis();

        // Cancel any existing timeout task
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }

        // Start a new timeout task
        startTimeoutTask();
    }

    // Update free method to cancel timeout task
    public void freeDungeon() {
        this.occupied = false;
        this.currentParty = null;

        // Cancel timeout task
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }

    public long getOccupationDuration() {
        if (!occupied) return 0;
        return System.currentTimeMillis() - occupationStartTime;
    }

    // Add getter and setter for time limit
    public int getTimeLimit() {
        return timeLimit;
    }

    public void setTimeLimit(int seconds) {
        this.timeLimit = seconds;
    }

    public boolean isPartyMember(UUID playerId) {
        if (!occupied || currentParty == null) return false;

        return currentParty.getMembers().stream()
                .anyMatch(player -> player.getUniqueId().equals(playerId));
    }

    // Add method to start timeout task
    public void startTimeoutTask() {
        final MinecraftMMORPG plugin = MinecraftMMORPG.getInstance();

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (occupied && currentParty != null) {
                // Time's up - kick all party members out
                for (Player member : currentParty.getMembers()) {
                    if (plugin.getDungeonManager().getPlayerDungeon(member) == this) {
                        // Teleport to main world spawn
                        World mainWorld = Bukkit.getWorld("world");
                        if (mainWorld != null) {
                            member.teleport(mainWorld.getSpawnLocation());
                            member.sendMessage("§c✦ Time's up! You've been ejected from the dungeon.");
                        }

                        // Remove from dungeon tracking
                        plugin.getDungeonManager().playerToDungeon.remove(member.getUniqueId());
                    }
                }

                // Broadcast to party
                currentParty.broadcast("§c✦ Your time in dungeon " + name + " has expired!");

                // Free the dungeon
                freeDungeon();
                plugin.getLogger().info("Dungeon freed due to time limit: " + name);
            }
        }, timeLimit * 20L); // Convert seconds to ticks
    }


}