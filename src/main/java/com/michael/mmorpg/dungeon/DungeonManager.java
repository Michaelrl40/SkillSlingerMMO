package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.graveyard.Graveyard;
import com.michael.mmorpg.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DungeonManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Dungeon> dungeons = new HashMap<>();
    final Map<UUID, UUID> playerToDungeon = new HashMap<>();
    final Map<UUID, CountdownTask> activeTeleports = new HashMap<>();

    public DungeonManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        loadDungeons();
        startEmptyDungeonChecker();
    }

    /**
     * Creates a new dungeon with the specified name
     */
    public boolean createDungeon(String name) {
        // Check if dungeon with this name already exists
        for (Dungeon dungeon : dungeons.values()) {
            if (dungeon.getName().equalsIgnoreCase(name)) {
                return false;
            }
        }

        // Create a new world for this dungeon
        String worldName = "dungeon_" + name.toLowerCase().replace(" ", "_");
        World world = createNewWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("Failed to create world for dungeon: " + name);
            return false;
        }

        // Create dungeon object
        UUID dungeonId = UUID.randomUUID();
        Dungeon dungeon = new Dungeon(dungeonId, name, world);

        // Save to database
        if (saveDungeonToDatabase(dungeon)) {
            // Add to local cache
            dungeons.put(dungeonId, dungeon);
            plugin.getLogger().info("Created new dungeon: " + name);
            return true;
        }

        return false;
    }

    /**
     * Creates a new world for a dungeon
     */
    private World createNewWorld(String worldName) {
        WorldCreator creator = new WorldCreator(worldName);
        // You can customize world generation here
        return creator.createWorld();
    }

    /**
     * Sets the entrance location for a dungeon
     */
    public boolean setDungeonEntrance(String dungeonName, Location location) {
        Dungeon dungeon = getDungeonByName(dungeonName);
        if (dungeon == null) return false;

        dungeon.setEntranceLocation(location);
        return updateDungeonInDatabase(dungeon);
    }

    /**
     * Initiates teleportation to a dungeon for a party
     */
    public boolean teleportPartyToDungeon(Player leader, String dungeonName) {
        Dungeon dungeon = getDungeonByName(dungeonName);
        if (dungeon == null) {
            leader.sendMessage("§c✦ Dungeon doesn't exist: " + dungeonName);
            return false;
        }

        if (dungeon.isOccupied()) {
            leader.sendMessage("§c✦ This dungeon is currently occupied by another party!");
            return false;
        }

        if (dungeon.getEntranceLocation() == null) {
            leader.sendMessage("§c✦ This dungeon doesn't have an entrance set!");
            return false;
        }

        Party party = plugin.getPartyManager().getParty(leader);
        if (party == null) {
            leader.sendMessage("§c✦ You must be in a party to enter a dungeon!");
            return false;
        }

        if (!party.getLeader().equals(leader)) {
            leader.sendMessage("§c✦ Only the party leader can use a dungeon key!");
            return false;
        }

        // Check if teleport is already in progress
        if (activeTeleports.containsKey(party.getPartyId())) {
            leader.sendMessage("§c✦ Teleport already in progress!");
            return false;
        }

        // Start teleport countdown
        UUID partyId = party.getPartyId();
        CountdownTask task = new CountdownTask(plugin, party, dungeon);
        activeTeleports.put(partyId, task);
        task.start();

        return true;
    }

    /**
     * Cancels an active teleport countdown
     */
    public boolean cancelTeleport(Player player) {
        Party party = plugin.getPartyManager().getParty(player);
        if (party == null) {
            player.sendMessage("§c✦ You are not in a party!");
            return false;
        }

        UUID partyId = party.getPartyId();
        CountdownTask task = activeTeleports.get(partyId);
        if (task == null) {
            player.sendMessage("§c✦ No teleport in progress!");
            return false;
        }

        task.cancel();
        activeTeleports.remove(partyId);
        party.broadcast("§e✦ " + player.getName() + " canceled the dungeon teleport!");
        return true;
    }

    /**
     * Removes a player from their current dungeon
     */
    public boolean leaveDungeon(Player player) {
        UUID dungeonId = playerToDungeon.get(player.getUniqueId());
        if (dungeonId == null) {
            player.sendMessage("§c✦ You are not in a dungeon!");
            return false;
        }

        // Get main world spawn
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null) {
            player.sendMessage("§c✦ Cannot find main world to teleport to!");
            return false;
        }

        // Teleport to main world spawn
        player.teleport(mainWorld.getSpawnLocation());
        playerToDungeon.remove(player.getUniqueId());
        player.sendMessage("§a✦ You have left the dungeon.");

        // Check if dungeon is now empty (all party members left)
        Dungeon dungeon = dungeons.get(dungeonId);
        if (dungeon != null && dungeon.isOccupied()) {
            boolean anyPlayersLeft = false;
            for (UUID playerId : playerToDungeon.keySet()) {
                if (dungeonId.equals(playerToDungeon.get(playerId))) {
                    anyPlayersLeft = true;
                    break;
                }
            }

            if (!anyPlayersLeft) {
                dungeon.freeDungeon();
                plugin.getLogger().info("Dungeon freed: " + dungeon.getName());
            }
        }

        return true;
    }

    /**
     * Handles player death in a dungeon
     */
    /**
     * Handles player death in a dungeon
     */
    public void handlePlayerDeath(Player player) {
        UUID dungeonId = playerToDungeon.get(player.getUniqueId());
        if (dungeonId == null) return; // Player not in a dungeon

        // Get the dungeon
        Dungeon dungeon = dungeons.get(dungeonId);
        if (dungeon == null) return;

        // Check if there's a graveyard in this dungeon world
        World dungeonWorld = dungeon.getWorld();
        boolean hasGraveyardInDungeon = false;

        for (Graveyard graveyard : plugin.getGraveyardManager().listGraveyards()) {
            if (graveyard.getLocation().getWorld().equals(dungeonWorld)) {
                hasGraveyardInDungeon = true;
                break;
            }
        }

        // If there's no graveyard in this dungeon, the player will be respawned
        // in the main world, so we need to remove them from dungeon tracking
        if (!hasGraveyardInDungeon) {
            playerToDungeon.remove(player.getUniqueId());
            plugin.getLogger().info("Player " + player.getName() +
                    " died in dungeon " + dungeon.getName() +
                    " and will respawn in main world (no dungeon graveyard)");

            // Check if all party members are now gone from the dungeon
            checkAndFreeDungeon(dungeon);
        } else {
            plugin.getLogger().info("Player " + player.getName() +
                    " died in dungeon " + dungeon.getName() +
                    " and will respawn at dungeon graveyard");
        }
    }

    /**
     * Checks if a dungeon is empty and frees it if so
     */
    private void checkAndFreeDungeon(Dungeon dungeon) {
        if (!dungeon.isOccupied()) return;

        Party party = dungeon.getCurrentParty();
        if (party == null) {
            // No party associated, free it
            dungeon.freeDungeon();
            return;
        }

        // Check if any party members are still in the dungeon
        boolean anyPartyMembersLeft = false;
        for (Player member : party.getMembers()) {
            UUID memberDungeonId = playerToDungeon.get(member.getUniqueId());
            if (memberDungeonId != null && memberDungeonId.equals(dungeon.getId())) {
                anyPartyMembersLeft = true;
                break;
            }
        }

        // If no party members are left in the dungeon, free it
        if (!anyPartyMembersLeft) {
            dungeon.freeDungeon();
            plugin.getLogger().info("Dungeon automatically freed after all party members left/died: " + dungeon.getName());
            party.broadcast("§c✦ Your party has left the dungeon " + dungeon.getName() + ".");
        }
    }

    /**
     * Gets a dungeon by name
     */
    public Dungeon getDungeonByName(String name) {
        for (Dungeon dungeon : dungeons.values()) {
            if (dungeon.getName().equalsIgnoreCase(name)) {
                return dungeon;
            }
        }
        return null;
    }

    /**
     * Gets a dungeon by ID
     */
    public Dungeon getDungeonById(UUID id) {
        return dungeons.get(id);
    }

    /**
     * Gets the dungeon a player is currently in
     */
    public Dungeon getPlayerDungeon(Player player) {
        UUID dungeonId = playerToDungeon.get(player.getUniqueId());
        if (dungeonId == null) return null;
        return dungeons.get(dungeonId);
    }

    /**
     * Lists all available dungeons
     */
    public List<Dungeon> listDungeons() {
        return new ArrayList<>(dungeons.values());
    }

    // In DungeonManager class - update saveDungeonToDatabase to include time limit
    private boolean saveDungeonToDatabase(Dungeon dungeon) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for saving dungeon");
                return false;
            }

            stmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO dungeons (id, name, world, time_limit) VALUES (?, ?, ?, ?)");

            stmt.setString(1, dungeon.getId().toString());
            stmt.setString(2, dungeon.getName());
            stmt.setString(3, dungeon.getWorld().getName());
            stmt.setInt(4, dungeon.getTimeLimit());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving dungeon to database: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            closeQuietly(stmt);
        }
    }

    // In DungeonManager class - update updateDungeonInDatabase to include time limit
    private boolean updateDungeonInDatabase(Dungeon dungeon) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for updating dungeon");
                return false;
            }

            String query = "UPDATE dungeons SET name = ?, world = ?, time_limit = ?";

            // Add entrance location data if available
            boolean hasEntrance = dungeon.getEntranceLocation() != null;
            if (hasEntrance) {
                query += ", entrance_world = ?, entrance_x = ?, entrance_y = ?, entrance_z = ?, entrance_yaw = ?, entrance_pitch = ?";
            }

            query += " WHERE id = ?";

            stmt = conn.prepareStatement(query);

            int paramIndex = 1;
            stmt.setString(paramIndex++, dungeon.getName());
            stmt.setString(paramIndex++, dungeon.getWorld().getName());
            stmt.setInt(paramIndex++, dungeon.getTimeLimit());

            if (hasEntrance) {
                Location entrance = dungeon.getEntranceLocation();
                stmt.setString(paramIndex++, entrance.getWorld().getName());
                stmt.setDouble(paramIndex++, entrance.getX());
                stmt.setDouble(paramIndex++, entrance.getY());
                stmt.setDouble(paramIndex++, entrance.getZ());
                stmt.setFloat(paramIndex++, entrance.getYaw());
                stmt.setFloat(paramIndex++, entrance.getPitch());
            }

            stmt.setString(paramIndex, dungeon.getId().toString());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating dungeon in database: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            closeQuietly(stmt);
        }
    }

    // In DungeonManager class - update loadDungeons method to load time limits
    private void loadDungeons() {
        dungeons.clear();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for loading dungeons");
                return;
            }

            stmt = conn.prepareStatement("SELECT * FROM dungeons");
            rs = stmt.executeQuery();

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");
                String worldName = rs.getString("world");

                // Load time limit
                int timeLimit = 1200; // Default 20 minutes
                try {
                    timeLimit = rs.getInt("time_limit");
                    if (rs.wasNull()) {
                        timeLimit = 1200;
                    }
                } catch (SQLException e) {
                    // Column might not exist in older versions
                    plugin.getLogger().info("Time limit column not found, using default: " + e.getMessage());
                }

                // Load or create world
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().info("Loading world for dungeon: " + name);
                    world = Bukkit.createWorld(new WorldCreator(worldName));
                }

                if (world != null) {
                    Dungeon dungeon = new Dungeon(id, name, world);
                    dungeon.setTimeLimit(timeLimit);

                    // Load entrance location if it exists
                    String entranceWorld = rs.getString("entrance_world");
                    if (entranceWorld != null && !rs.wasNull()) {
                        World eWorld = Bukkit.getWorld(entranceWorld);
                        if (eWorld != null) {
                            double x = rs.getDouble("entrance_x");
                            double y = rs.getDouble("entrance_y");
                            double z = rs.getDouble("entrance_z");
                            float yaw = rs.getFloat("entrance_yaw");
                            float pitch = rs.getFloat("entrance_pitch");

                            Location entrance = new Location(eWorld, x, y, z, yaw, pitch);
                            dungeon.setEntranceLocation(entrance);
                        }
                    }

                    dungeons.put(id, dungeon);
                    plugin.getLogger().info("Loaded dungeon: " + name + " with time limit: " + timeLimit + " seconds");
                } else {
                    plugin.getLogger().warning("Failed to load world for dungeon: " + name);
                }
            }

            plugin.getLogger().info("Loaded " + dungeons.size() + " dungeons from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading dungeons from database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    // Add a new method to update just the time limit
    public boolean updateDungeonTimeLimit(String dungeonName, int timeLimit) {
        Dungeon dungeon = getDungeonByName(dungeonName);
        if (dungeon == null) return false;

        dungeon.setTimeLimit(timeLimit);

        // Restart the timeout task if the dungeon is occupied
        if (dungeon.isOccupied()) {
            dungeon.startTimeoutTask();
        }

        return updateDungeonInDatabase(dungeon);
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                plugin.getLogger().warning("Error closing resource: " + e.getMessage());
            }
        }
    }

    /**
     * Called on plugin disable to save all dungeon states
     */
    public void shutdown() {
        // Free all dungeons
        for (Dungeon dungeon : dungeons.values()) {
            if (dungeon.isOccupied()) {
                dungeon.freeDungeon();
            }
        }

        // Save any pending changes
        for (Dungeon dungeon : dungeons.values()) {
            updateDungeonInDatabase(dungeon);
        }
    }

    /**
     * Starts a task to check for and free empty dungeons
     */
    public void startEmptyDungeonChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Dungeon dungeon : dungeons.values()) {
                if (dungeon.isOccupied()) {
                    boolean anyPlayersInDungeon = false;

                    // Check if any players are still in this dungeon
                    for (UUID playerId : playerToDungeon.keySet()) {
                        if (dungeon.getId().equals(playerToDungeon.get(playerId))) {
                            Player player = Bukkit.getPlayer(playerId);
                            // Only count online players
                            if (player != null && player.isOnline()) {
                                anyPlayersInDungeon = true;
                                break;
                            }
                        }
                    }

                    // If no players are in the dungeon, free it
                    if (!anyPlayersInDungeon) {
                        plugin.getLogger().info("Automatically freeing empty dungeon: " + dungeon.getName());
                        dungeon.freeDungeon();
                    }
                }
            }
        }, 1200L, 1200L); // Check every minute (1200 ticks)
    }

}