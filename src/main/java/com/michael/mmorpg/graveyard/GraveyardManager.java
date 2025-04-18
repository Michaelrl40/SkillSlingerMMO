package com.michael.mmorpg.graveyard;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.sql.*;
import java.util.*;

public class GraveyardManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Graveyard> graveyards = new HashMap<>();

    // Added: Store pending graveyards by world name
    private final Map<String, List<PendingGraveyard>> pendingGraveyardsByWorld = new HashMap<>();

    // Added: Class to store graveyard data before world is loaded
    private static class PendingGraveyard {
        final UUID id;
        final String name;
        final String worldName;
        final double x, y, z;
        final float yaw, pitch;

        PendingGraveyard(UUID id, String name, String worldName,
                         double x, double y, double z, float yaw, float pitch) {
            this.id = id;
            this.name = name;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public GraveyardManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        loadGraveyards();
    }

    public boolean createGraveyard(String name, Location location) {
        // Check if a graveyard with this name already exists
        for (Graveyard graveyard : graveyards.values()) {
            if (graveyard.getName().equalsIgnoreCase(name)) {
                return false;
            }
        }

        // Create new graveyard
        Graveyard graveyard = new Graveyard(name, location);

        // Save to database
        if (saveGraveyardToDatabase(graveyard)) {
            // Add to local cache
            graveyards.put(graveyard.getId(), graveyard);
            return true;
        }

        return false;
    }

    private boolean saveGraveyardToDatabase(Graveyard graveyard) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for saving graveyard");
                return false;
            }

            stmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO graveyards (id, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

            Location loc = graveyard.getLocation();
            stmt.setString(1, graveyard.getId().toString());
            stmt.setString(2, graveyard.getName());
            stmt.setString(3, loc.getWorld().getName());
            stmt.setDouble(4, loc.getX());
            stmt.setDouble(5, loc.getY());
            stmt.setDouble(6, loc.getZ());
            stmt.setFloat(7, loc.getYaw());
            stmt.setFloat(8, loc.getPitch());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving graveyard to database: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            closeQuietly(stmt);
        }
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
     * Checks if at least one graveyard exists
     */
    public boolean hasGraveyards() {
        return !graveyards.isEmpty();
    }

    /**
     * Creates a default graveyard at world spawn if none exist
     */
    public void ensureDefaultGraveyard() {
        if (graveyards.isEmpty()) {
            Location worldSpawn = plugin.getServer().getWorlds().get(0).getSpawnLocation();
            createGraveyard("World Spawn", worldSpawn);
            plugin.getLogger().info("Created default graveyard at world spawn location");
        }
    }

    public boolean removeGraveyard(String name) {
        UUID graveyardId = null;

        // Find the graveyard by name
        for (Map.Entry<UUID, Graveyard> entry : graveyards.entrySet()) {
            if (entry.getValue().getName().equalsIgnoreCase(name)) {
                graveyardId = entry.getKey();
                break;
            }
        }

        // Remove if found
        if (graveyardId != null) {
            if (removeGraveyardFromDatabase(graveyardId)) {
                graveyards.remove(graveyardId);
                return true;
            }
        }

        return false;
    }

    private boolean removeGraveyardFromDatabase(UUID graveyardId) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for removing graveyard");
                return false;
            }

            stmt = conn.prepareStatement("DELETE FROM graveyards WHERE id = ?");
            stmt.setString(1, graveyardId.toString());

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing graveyard from database: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            closeQuietly(stmt);
        }
    }

    public List<Graveyard> listGraveyards() {
        return new ArrayList<>(graveyards.values());
    }

    /**
     * Modified: Updated to prioritize same-world graveyards
     */
    public Graveyard getClosestGraveyard(Location location) {
        if (graveyards.isEmpty()) {
            return null;
        }

        Graveyard closest = null;
        double closestDistance = Double.MAX_VALUE;

        // First, try to find graveyards in the same world
        for (Graveyard graveyard : graveyards.values()) {
            if (graveyard.getLocation().getWorld().equals(location.getWorld())) {
                double distance = graveyard.getLocation().distanceSquared(location);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = graveyard;
                }
            }
        }

        // If we found a graveyard in the same world, use it
        if (closest != null) {
            return closest;
        }

        // Otherwise, fall back to the default behavior - find a graveyard in any world
        // Since we're falling back, prioritize the main world
        World mainWorld = plugin.getServer().getWorlds().get(0);
        for (Graveyard graveyard : graveyards.values()) {
            if (graveyard.getLocation().getWorld().equals(mainWorld)) {
                if (closest == null || graveyard.getName().equalsIgnoreCase("World Spawn")) {
                    closest = graveyard;
                    break;
                }
            }
        }

        // If still no graveyard found, take any graveyard
        if (closest == null && !graveyards.isEmpty()) {
            closest = graveyards.values().iterator().next();
        }

        return closest;
    }

    /**
     * Modified: Uses PlayerRespawnEvent to set respawn location
     */
    public void respawnAtNearestGraveyard(Player player, PlayerRespawnEvent event) {
        // Ensure we have at least one graveyard
        ensureDefaultGraveyard();

        // Find closest graveyard to death location
        Location deathLocation = null;
        if (player.hasMetadata("died_location")) {
            deathLocation = (Location) player.getMetadata("died_location").get(0).value();
            player.removeMetadata("died_location", plugin);
        } else {
            deathLocation = player.getLocation();
        }

        Graveyard closest = getClosestGraveyard(deathLocation);

        if (closest == null) {
            plugin.getLogger().warning("No graveyard found for respawning player " + player.getName());
            return;
        }

        // Use setRespawnLocation for proper respawn handling
        if (event != null) {
            event.setRespawnLocation(closest.getLocation());
            plugin.getLogger().info("Setting respawn location for " + player.getName() +
                    " to graveyard: " + closest.getName() +
                    " in world: " + closest.getLocation().getWorld().getName());
        } else {
            // Fallback to teleport if not called from respawn event
            player.teleport(closest.getLocation());
        }

        player.sendMessage("§6You have been resurrected at the " + closest.getName() + " graveyard.");
    }

    /**
     * Original method kept for backward compatibility
     */
    public void respawnAtNearestGraveyard(Player player) {
        respawnAtNearestGraveyard(player, null);
    }

    /**
     * Modified: Updated to handle pending graveyards for worlds that aren't loaded yet
     */
    private void loadGraveyards() {
        graveyards.clear();
        pendingGraveyardsByWorld.clear();

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for loading graveyards");
                return;
            }

            stmt = conn.prepareStatement("SELECT * FROM graveyards");
            rs = stmt.executeQuery();

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String name = rs.getString("name");
                String worldName = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");

                World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    // World exists, create the graveyard now
                    Location location = new Location(world, x, y, z, yaw, pitch);
                    Graveyard graveyard = new Graveyard(id, name, location);
                    graveyards.put(id, graveyard);
                    plugin.getLogger().info("Loaded graveyard: " + name);
                } else {
                    // World doesn't exist yet, store as pending
                    PendingGraveyard pending = new PendingGraveyard(
                            id, name, worldName, x, y, z, yaw, pitch);

                    pendingGraveyardsByWorld.computeIfAbsent(worldName, k -> new ArrayList<>())
                            .add(pending);

                    plugin.getLogger().info("Graveyard '" + name + "' in world '" + worldName +
                            "' is pending until world is loaded");
                }
            }

            plugin.getLogger().info("Loaded " + graveyards.size() + " graveyards from database, " +
                    "with " + countPendingGraveyards() + " pending for worlds to load");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading graveyards from database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    /**
     * Added: Count the number of pending graveyards
     */
    private int countPendingGraveyards() {
        int count = 0;
        for (List<PendingGraveyard> list : pendingGraveyardsByWorld.values()) {
            count += list.size();
        }
        return count;
    }

    /**
     * Added: Called when a world is loaded to create any pending graveyards for that world
     */
    public void onWorldLoad(World world) {
        String worldName = world.getName();
        List<PendingGraveyard> pendingList = pendingGraveyardsByWorld.remove(worldName);

        if (pendingList == null || pendingList.isEmpty()) {
            return;
        }

        plugin.getLogger().info("Loading " + pendingList.size() + " pending graveyards for world: " + worldName);

        for (PendingGraveyard pending : pendingList) {
            Location location = new Location(world, pending.x, pending.y, pending.z, pending.yaw, pending.pitch);
            Graveyard graveyard = new Graveyard(pending.id, pending.name, location);
            graveyards.put(pending.id, graveyard);
            plugin.getLogger().info("Loaded pending graveyard: " + pending.name);
        }
    }

    /**
     * Added: Creates a graveyard in a world that may not be loaded yet
     */
    public boolean ensureGraveyardInWorld(String worldName, String graveyardName) {
        // Check if world exists
        World world = plugin.getServer().getWorld(worldName);

        if (world != null) {
            // World exists, create graveyard at spawn
            return createGraveyard(graveyardName, world.getSpawnLocation());
        } else {
            // World doesn't exist, log a warning
            plugin.getLogger().warning("Attempted to create graveyard in non-existent world: " + worldName);
            return false;
        }
    }

    /**
     * Added: Finds a graveyard by name
     */
    public Graveyard getGraveyardByName(String name) {
        for (Graveyard graveyard : graveyards.values()) {
            if (graveyard.getName().equalsIgnoreCase(name)) {
                return graveyard;
            }
        }
        return null;
    }

    /**
     * Added: Finds a graveyard in a specific world
     */
    public Graveyard findGraveyardInWorld(World world) {
        for (Graveyard graveyard : graveyards.values()) {
            if (graveyard.getLocation().getWorld().equals(world)) {
                return graveyard;
            }
        }
        return null;
    }
}