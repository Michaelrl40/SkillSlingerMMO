package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class CountdownTask {
    private final MinecraftMMORPG plugin;
    private final Party party;
    private final Dungeon dungeon;
    private BukkitTask task;
    private int secondsLeft = 10;
    private static final double RANGE_CHECK_DISTANCE = 20.0;

    public CountdownTask(MinecraftMMORPG plugin, Party party, Dungeon dungeon) {
        this.plugin = plugin;
        this.party = party;
        this.dungeon = dungeon;
    }

    public void start() {
        party.broadcast("§a✦ Preparing to enter dungeon: " + dungeon.getName());
        party.broadcast("§a✦ Teleport will begin in 10 seconds. Stay within 20 blocks of the entrance.");
        party.broadcast("§a✦ Type §e/dungeon cancel§a to cancel the teleport.");

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    // Time to teleport!
                    completeTeleport();
                    cancel();
                    return;
                }

                // Show countdown every 2 seconds or last 3 seconds
                if (secondsLeft <= 3 || secondsLeft % 2 == 0) {
                    party.broadcast("§e✦ Teleporting to dungeon in §6" + secondsLeft + "§e seconds...");
                }

                secondsLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void completeTeleport() {
        if (party.getLeader() == null || dungeon.getWorld() == null) {
            party.broadcast("§c✦ Teleport failed: Dungeon world not found!");
            plugin.getDungeonManager().activeTeleports.remove(party.getPartyId());
            return;
        }

        // Find suitable spawn location in dungeon world
        Location spawnLocation = dungeon.getWorld().getSpawnLocation();

        // Get entrance location to check distance
        Location entranceLocation = dungeon.getEntranceLocation();

        // Track out-of-range players
        List<String> outOfRangeNames = new ArrayList<>();

        // Keep track of whether any players were teleported
        boolean anyPlayersTeleported = false;

        // Teleport all party members who are in range
        for (Player member : party.getMembers()) {
            // Check if player is close enough to entrance
            if (entranceLocation != null &&
                    member.getWorld().equals(entranceLocation.getWorld()) &&
                    member.getLocation().distanceSquared(entranceLocation) <= RANGE_CHECK_DISTANCE * RANGE_CHECK_DISTANCE) {

                // Teleport to dungeon
                member.teleport(spawnLocation);
                member.sendMessage("§a✦ You have entered the dungeon: " + dungeon.getName());

                // Track player in dungeon
                plugin.getDungeonManager().playerToDungeon.put(member.getUniqueId(), dungeon.getId());

                // At least one player teleported
                anyPlayersTeleported = true;
            } else {
                // Player out of range
                outOfRangeNames.add(member.getName());
                member.sendMessage("§c✦ You were too far from the entrance and missed the teleport!");
            }
        }

        // Only occupy the dungeon if at least one player was teleported
        if (anyPlayersTeleported) {
            dungeon.occupyDungeon(party);

            // Report out-of-range players
            if (!outOfRangeNames.isEmpty()) {
                String players = String.join(", ", outOfRangeNames);
                party.broadcast("§e✦ The following players were out of range and didn't teleport: " + players);
            }
        } else {
            // No players were teleported - don't occupy the dungeon
            Player leader = party.getLeader();
            if (leader != null && leader.isOnline()) {
                leader.sendMessage("§c✦ No players were within range of the entrance! The dungeon key has been wasted.");
            }

            // Since we're not using the dungeon, we could potentially return the key here
            // but that would require more complex implementation
        }

        // Clean up
        plugin.getDungeonManager().activeTeleports.remove(party.getPartyId());
    }
}