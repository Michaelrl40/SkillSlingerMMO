package com.michael.mmorpg.guild;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class GuildListener implements Listener {
    private final MinecraftMMORPG plugin;
    private final GuildManager guildManager;
    private final Map<UUID, UUID> playerGuildTerritoryMap;

    public GuildListener(MinecraftMMORPG plugin, GuildManager guildManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.playerGuildTerritoryMap = new HashMap<>();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player is in a guild hall
        checkGuildHallEntry(player, player.getLocation());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // Check if player changed chunks
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = to.getChunk();

        if (fromChunk.getX() != toChunk.getX() || fromChunk.getZ() != toChunk.getZ() ||
                !event.getFrom().getWorld().equals(to.getWorld())) {

            // Check guild territory changes
            checkGuildTerritoryChange(player, event.getFrom(), to);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up tracking when player leaves
        playerGuildTerritoryMap.remove(event.getPlayer().getUniqueId());
    }

    private void checkGuildTerritoryChange(Player player, Location from, Location to) {
        // Get guild halls at both locations
        GuildHall fromHall = guildManager.getGuildHallAtLocation(from);
        GuildHall toHall = guildManager.getGuildHallAtLocation(to);

        UUID fromGuildId = fromHall != null ? fromHall.getGuildId() : null;
        UUID toGuildId = toHall != null ? toHall.getGuildId() : null;

        // Get player's current tracked territory
        UUID currentTrackedGuildId = playerGuildTerritoryMap.get(player.getUniqueId());

        // Only send messages if there's a change in territory
        if (!Objects.equals(currentTrackedGuildId, toGuildId)) {
            // Player is entering a new guild territory
            if (toGuildId != null) {
                Guild guild = guildManager.getGuild(toGuildId);
                if (guild != null) {
                    // Check if player is a member of this guild
                    if (guild.isMember(player.getUniqueId())) {
                        player.sendMessage("§a§lYou have entered your guild's territory: " + toHall.getName());
                    } else {
                        player.sendMessage("§6§lYou have entered " + guild.getName() + "'s territory!");
                    }
                }

                // Update tracking
                playerGuildTerritoryMap.put(player.getUniqueId(), toGuildId);
            }
            // Player is leaving a guild territory
            else if (currentTrackedGuildId != null) {
                Guild guild = guildManager.getGuild(currentTrackedGuildId);
                if (guild != null) {
                    // Check if player is a member of this guild
                    if (guild.isMember(player.getUniqueId())) {
                        player.sendMessage("§e§lYou have left your guild's territory.");
                    } else {
                        player.sendMessage("§e§lYou have left " + guild.getName() + "'s territory.");
                    }
                }

                // Remove tracking
                playerGuildTerritoryMap.remove(player.getUniqueId());
            }
        }
    }

    private void checkGuildHallEntry(Player player, Location location) {
        // Get the guild hall at the player's location
        GuildHall hall = guildManager.getGuildHallAtLocation(location);

        // If there's a guild hall at this location
        if (hall != null) {
            // Get the guild
            Guild guild = guildManager.getGuild(hall.getGuildId());

            if (guild != null) {
                // Check if player is a member of this guild
                if (guild.isMember(player.getUniqueId())) {
                    player.sendMessage("§a§lYou have entered your guild's territory: " + hall.getName());
                } else {
                    player.sendMessage("§6§lYou have entered " + guild.getName() + "'s territory!");
                }

                // Update tracking
                playerGuildTerritoryMap.put(player.getUniqueId(), hall.getGuildId());
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Skip check for players with admin permission
        if (player.hasPermission("skillslinger.guild.admin")) {
            return;
        }

        // Check if block is in a guild hall
        GuildHall hall = guildManager.getGuildHallAtLocation(event.getBlock().getLocation());

        if (hall != null) {
            // Get the guild
            Guild guild = guildManager.getGuild(hall.getGuildId());

            if (guild != null) {
                // Check if player is a member of this guild
                if (!guild.isMember(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lYou cannot break blocks in another guild's territory!");
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        // Skip check for players with admin permission
        if (player.hasPermission("skillslinger.guild.admin")) {
            return;
        }

        // Check if block is in a guild hall
        GuildHall hall = guildManager.getGuildHallAtLocation(event.getBlock().getLocation());

        if (hall != null) {
            // Get the guild
            Guild guild = guildManager.getGuild(hall.getGuildId());

            if (guild != null) {
                // Check if player is a member of this guild
                if (!guild.isMember(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lYou cannot place blocks in another guild's territory!");
                }
            }
        }
    }
}