package com.michael.mmorpg.deathchest;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DeathChestManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, DeathChest> chests = new HashMap<>();
    private final Map<UUID, UUID> playerChests = new HashMap<>();
    private static final int PROTECTION_TIME = 180; // 3 minutes in seconds

    public DeathChestManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        loadChests();
        startExpirationTask();
    }

    public DeathChest createDeathChest(Player player, List<ItemStack> items) {
        // Check if player has a chest in inventory
        boolean hasChest = false;
        int chestSlot = -1;

        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            ItemStack item = player.getInventory().getContents()[i];
            if (item != null && item.getType() == Material.CHEST) {
                hasChest = true;
                chestSlot = i;
                break;
            }
        }

        if (!hasChest) {
            return null; // No chest available
        }

        // Remove one chest from inventory
        ItemStack chestItem = player.getInventory().getItem(chestSlot);
        if (chestItem.getAmount() > 1) {
            chestItem.setAmount(chestItem.getAmount() - 1);
        } else {
            player.getInventory().setItem(chestSlot, null);
        }

        // Find a safe location for the chest
        Location location = findSafeLocation(player.getLocation());
        if (location == null) {
            player.sendMessage("§cCouldn't find a safe location for your death chest!");
            return null;
        }

        // Create the chest
        Block chestBlock = location.getBlock();
        chestBlock.setType(Material.CHEST);

        // Create DeathChest object
        DeathChest deathChest = new DeathChest(player, location, PROTECTION_TIME);

        // Store items in chest
        Chest chest = (Chest) chestBlock.getState();
        Inventory inv = chest.getInventory();

        for (ItemStack item : items) {
            if (item != null) {
                inv.addItem(item);
            }
        }

        // Store the contents in the DeathChest object
        deathChest.setContents(inv.getContents().clone());

// Try to place sign on chest faces first
        boolean signPlaced = false;
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block relative = chestBlock.getRelative(face);
            if (relative.getType() == Material.AIR) {
                Material signType = Material.OAK_WALL_SIGN;
                relative.setType(signType);

                // Set sign direction to face the chest
                BlockState blockState = relative.getState();
                if (blockState.getBlockData() instanceof WallSign) {
                    WallSign signData = (WallSign) blockState.getBlockData();
                    signData.setFacing(face.getOppositeFace());
                    relative.setBlockData(signData);
                }

                if (blockState instanceof Sign) {
                    Sign sign = (Sign) blockState;
                    sign.setLine(0, "§6Death Chest");
                    sign.setLine(1, "§f" + player.getName());
                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
                    sign.setLine(2, sdf.format(new Date()));
                    sign.setLine(3, "§cLocked: 3:00");
                    sign.update(true);

                    // Store sign location in DeathChest object
                    deathChest.setSignLocation(relative.getLocation());
                    signPlaced = true;
                    break;
                }
            }
        }

// If no sign was placed on the sides, try to place it on top
        if (!signPlaced) {
            Block topBlock = chestBlock.getRelative(BlockFace.UP);
            if (topBlock.getType() == Material.AIR) {
                Material signType = Material.OAK_SIGN; // Standing sign, not wall sign
                topBlock.setType(signType);

                BlockState blockState = topBlock.getState();
                if (blockState instanceof Sign) {
                    Sign sign = (Sign) blockState;
                    sign.setLine(0, "§6Death Chest");
                    sign.setLine(1, "§f" + player.getName());
                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
                    sign.setLine(2, sdf.format(new Date()));
                    sign.setLine(3, "§cLocked: 3:00");
                    sign.update(true);

                    // Store sign location in DeathChest object
                    deathChest.setSignLocation(topBlock.getLocation());
                    signPlaced = true;
                }
            }
        }

// If we still couldn't place a sign, log it but continue
        if (!signPlaced) {
            plugin.getLogger().warning("Couldn't place a sign for death chest at " +
                    chestBlock.getLocation() + " - no available space.");
        }

        // Add metadata to chest
        chestBlock.setMetadata("deathchest", new FixedMetadataValue(plugin, deathChest.getId().toString()));

        // Store in maps
        chests.put(deathChest.getId(), deathChest);
        playerChests.put(player.getUniqueId(), deathChest.getId());

        // Save to database
        saveChestToDatabase(deathChest);

        return deathChest;
    }

    private boolean saveChestToDatabase(DeathChest chest) {
        Connection conn = null;
        PreparedStatement chestStmt = null;
        PreparedStatement itemsStmt = null;
        boolean autoCommitOriginal = true;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for saving death chest");
                return false;
            }

            // Begin transaction
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Save chest data
            chestStmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO death_chests " +
                            "(id, owner_uuid, owner_name, world, x, y, z, sign_world, sign_x, sign_y, sign_z, creation_time, expiration_time, claimed) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            chestStmt.setString(1, chest.getId().toString());
            chestStmt.setString(2, chest.getOwnerUUID().toString());
            chestStmt.setString(3, chest.getOwnerName());

            Location loc = chest.getLocation();
            chestStmt.setString(4, loc.getWorld().getName());
            chestStmt.setInt(5, loc.getBlockX());
            chestStmt.setInt(6, loc.getBlockY());
            chestStmt.setInt(7, loc.getBlockZ());

            Location signLoc = chest.getSignLocation();
            if (signLoc != null) {
                chestStmt.setString(8, signLoc.getWorld().getName());
                chestStmt.setInt(9, signLoc.getBlockX());
                chestStmt.setInt(10, signLoc.getBlockY());
                chestStmt.setInt(11, signLoc.getBlockZ());
            } else {
                chestStmt.setNull(8, java.sql.Types.VARCHAR);
                chestStmt.setNull(9, java.sql.Types.INTEGER);
                chestStmt.setNull(10, java.sql.Types.INTEGER);
                chestStmt.setNull(11, java.sql.Types.INTEGER);
            }

            chestStmt.setLong(12, chest.getCreationTime());
            chestStmt.setLong(13, chest.getExpirationTime());
            chestStmt.setBoolean(14, chest.isClaimed());

            chestStmt.executeUpdate();

            // Delete any existing items for this chest
            try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM death_chest_items WHERE chest_id = ?")) {
                deleteStmt.setString(1, chest.getId().toString());
                deleteStmt.executeUpdate();
            }

            // Save chest items
            itemsStmt = conn.prepareStatement(
                    "INSERT INTO death_chest_items (chest_id, slot, item_data) VALUES (?, ?, ?)");

            ItemStack[] contents = chest.getContents();
            if (contents != null) {
                for (int slot = 0; slot < contents.length; slot++) {
                    ItemStack item = contents[slot];
                    if (item != null) {
                        try {
                            // Serialize the ItemStack to a byte array
                            byte[] serializedItem = serializeItemStack(item);

                            itemsStmt.setString(1, chest.getId().toString());
                            itemsStmt.setInt(2, slot);
                            itemsStmt.setBytes(3, serializedItem);
                            itemsStmt.executeUpdate();
                        } catch (IOException e) {
                            plugin.getLogger().warning("Failed to serialize item in slot " + slot + ": " + e.getMessage());
                        }
                    }
                }
            }

            // Commit transaction
            conn.commit();
            return true;

        } catch (SQLException e) {
            // Rollback on error
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Failed to rollback transaction: " + rollbackEx.getMessage());
                }
            }
            plugin.getLogger().severe("Error saving death chest to database: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Close resources
            closeQuietly(itemsStmt);
            closeQuietly(chestStmt);

            // Restore original auto-commit setting
            if (conn != null) {
                try {
                    conn.setAutoCommit(autoCommitOriginal);
                } catch (SQLException e) {
                    plugin.getLogger().warning("Could not restore auto-commit setting: " + e.getMessage());
                }
            }
        }
    }

    // Helper method to serialize an ItemStack
    private byte[] serializeItemStack(ItemStack item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

        dataOutput.writeObject(item);
        dataOutput.close();

        return outputStream.toByteArray();
    }

    // Helper method to deserialize an ItemStack
    private ItemStack deserializeItemStack(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();

        return item;
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

    public DeathChest getPlayerDeathChest(UUID playerUUID) {
        UUID chestId = playerChests.get(playerUUID);
        if (chestId != null) {
            return chests.get(chestId);
        }
        return null;
    }

    public DeathChest getDeathChest(UUID chestId) {
        return chests.get(chestId);
    }

    public DeathChest getDeathChestAt(Location location) {
        // First try to get from metadata
        Block block = location.getBlock();
        if (block.hasMetadata("deathchest")) {
            String chestId = block.getMetadata("deathchest").get(0).asString();
            return chests.get(UUID.fromString(chestId));
        }

        // Otherwise search by location
        for (DeathChest chest : chests.values()) {
            if (isSameLocation(chest.getLocation(), location)) {
                return chest;
            }
        }

        return null;
    }

    public void removeDeathChest(DeathChest chest) {
        // Remove from maps
        chests.remove(chest.getId());
        playerChests.remove(chest.getOwnerUUID());

        // Remove metadata from block
        Block block = chest.getLocation().getBlock();
        if (block.hasMetadata("deathchest")) {
            block.removeMetadata("deathchest", plugin);
        }

        // Remove from database
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for removing death chest");
                return;
            }

            // Delete chest and associated items (cascade delete will handle items)
            stmt = conn.prepareStatement("DELETE FROM death_chests WHERE id = ?");
            stmt.setString(1, chest.getId().toString());
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing death chest from database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(stmt);
        }
    }

    public Collection<DeathChest> getAllDeathChests() {
        return chests.values();
    }

    private boolean isSameLocation(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();
    }

    private Location findSafeLocation(Location center) {
        // First try the exact location
        if (isSafeForChest(center)) {
            return center;
        }

        // Try locations in a spiral pattern
        for (int radius = 1; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // Skip positions we already checked
                    if (Math.abs(x) < radius && Math.abs(z) < radius) {
                        continue;
                    }

                    Location loc = center.clone().add(x, 0, z);
                    if (isSafeForChest(loc)) {
                        return loc;
                    }

                    // Try a block above and below
                    Location locAbove = loc.clone().add(0, 1, 0);
                    if (isSafeForChest(locAbove)) {
                        return locAbove;
                    }

                    Location locBelow = loc.clone().add(0, -1, 0);
                    if (isSafeForChest(locBelow)) {
                        return locBelow;
                    }
                }
            }
        }

        return null; // Couldn't find a safe location
    }

    private boolean isSafeForChest(Location location) {
        Block block = location.getBlock();
        return block.getType() == Material.AIR ||
                block.getType() == Material.GRASS_BLOCK ||
                block.getType() == Material.TALL_GRASS ||
                block.getType() == Material.SNOW;
    }

    private void updateSignText(DeathChest chest) {
        // Get sign location directly from the chest object
        Location signLoc = chest.getSignLocation();
        if (signLoc == null) {
            return;
        }

        Block block = signLoc.getBlock();
        if (block.getType().name().contains("WALL_SIGN") || block.getType().name().contains("SIGN")) {
            BlockState state = block.getState();
            if (state instanceof Sign) {
                Sign sign = (Sign) state;
                sign.setLine(3, chest.isLocked() ?
                        "§cLocked: " + chest.getFormattedRemainingTime() :
                        "§aUnlocked");
                sign.update();
            }
        }
    }

    private void startExpirationTask() {
        // Check every 5 seconds if chests need sign updates or removal
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<DeathChest> toRemove = new ArrayList<>();

            for (DeathChest chest : chests.values()) {
                // Update sign with remaining time
                if (!chest.isClaimed()) {
                    updateSignText(chest);

                    // If protection just expired, show particles
                    if (!chest.isLocked() &&
                            chest.getExpirationTime() - System.currentTimeMillis() > -5000 &&
                            chest.getExpirationTime() - System.currentTimeMillis() <= 0) {
                        // Show unlocked effect
                        chest.getLocation().getWorld().spawnParticle(
                                Particle.HAPPY_VILLAGER,
                                chest.getLocation().clone().add(0.5, 1.0, 0.5),
                                20, 0.5, 0.5, 0.5, 0.1
                        );
                    }
                }

                // Check if chest block still exists
                if (chest.getLocation().getBlock().getType() != Material.CHEST) {
                    toRemove.add(chest);
                }
            }

            // Remove any broken or claimed chests
            for (DeathChest chest : toRemove) {
                removeDeathChest(chest);
            }
        }, 20 * 5, 20 * 5); // Run every 5 seconds
    }

    private void loadChests() {
        chests.clear();
        playerChests.clear();

        Connection conn = null;
        PreparedStatement chestStmt = null;
        ResultSet chestRs = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for loading death chests");
                return;
            }

            // Load chest data
            chestStmt = conn.prepareStatement("SELECT * FROM death_chests");
            chestRs = chestStmt.executeQuery();

            while (chestRs.next()) {
                UUID id = UUID.fromString(chestRs.getString("id"));
                UUID ownerUUID = UUID.fromString(chestRs.getString("owner_uuid"));
                String ownerName = chestRs.getString("owner_name");

                // Get chest location
                String worldName = chestRs.getString("world");
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("Skipping death chest in missing world: " + worldName);
                    continue;
                }

                int x = chestRs.getInt("x");
                int y = chestRs.getInt("y");
                int z = chestRs.getInt("z");
                Location location = new Location(world, x, y, z);

                // Get sign location if exists
                Location signLocation = null;
                String signWorld = chestRs.getString("sign_world");
                if (signWorld != null) {
                    World signWorldObj = plugin.getServer().getWorld(signWorld);
                    if (signWorldObj != null) {
                        int signX = chestRs.getInt("sign_x");
                        int signY = chestRs.getInt("sign_y");
                        int signZ = chestRs.getInt("sign_z");
                        signLocation = new Location(signWorldObj, signX, signY, signZ);
                    }
                }

                long creationTime = chestRs.getLong("creation_time");
                long expirationTime = chestRs.getLong("expiration_time");
                boolean claimed = chestRs.getBoolean("claimed");

                // Create DeathChest object
                DeathChest chest = new DeathChest(id, ownerUUID, ownerName, location,
                        creationTime, expirationTime, claimed, signLocation);

                // Load items for this chest
                loadChestItems(conn, chest);

                // Add to maps
                chests.put(chest.getId(), chest);
                playerChests.put(chest.getOwnerUUID(), chest.getId());

                // Add metadata to the chest block if it exists
                Block chestBlock = location.getBlock();
                if (chestBlock.getType() == Material.CHEST) {
                    chestBlock.setMetadata("deathchest", new FixedMetadataValue(plugin, id.toString()));
                }
            }

            plugin.getLogger().info("Loaded " + chests.size() + " death chests from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading death chests from database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(chestRs);
            closeQuietly(chestStmt);
        }
    }

    private void loadChestItems(Connection conn, DeathChest chest) {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.prepareStatement("SELECT * FROM death_chest_items WHERE chest_id = ? ORDER BY slot");
            stmt.setString(1, chest.getId().toString());
            rs = stmt.executeQuery();

            ItemStack[] contents = new ItemStack[27]; // Standard chest size

            while (rs.next()) {
                int slot = rs.getInt("slot");
                byte[] itemData = rs.getBytes("item_data");

                try {
                    ItemStack item = deserializeItemStack(itemData);
                    if (slot >= 0 && slot < contents.length) {
                        contents[slot] = item;
                    }
                } catch (IOException | ClassNotFoundException e) {
                    plugin.getLogger().warning("Failed to deserialize item in slot " + slot + ": " + e.getMessage());
                }
            }

            chest.setContents(contents);

        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading items for death chest " + chest.getId() + ": " + e.getMessage());
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }
}