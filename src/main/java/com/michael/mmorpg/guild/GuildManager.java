package com.michael.mmorpg.guild;

import com.michael.mmorpg.MinecraftMMORPG;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class GuildManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Guild> guilds;
    private final Map<UUID, GuildHall> guildHalls;
    private final Map<UUID, UUID> playerGuildMap; // Maps player UUID to guild UUID

    private static final int GUILD_CREATION_COST = 1000;
    private static final int CHUNK_CLAIM_COST = 250;

    public GuildManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.guilds = new HashMap<>();
        this.guildHalls = new HashMap<>();
        this.playerGuildMap = new HashMap<>();

        // Initialize database tables if needed
        plugin.getDatabaseManager().initGuildTables();

        // Load all guilds from database
        loadGuildsFromDatabase();

        // Register listeners
        plugin.getServer().getPluginManager().registerEvents(new GuildListener(plugin, this), plugin);
    }

    private void loadGuildsFromDatabase() {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot load guilds - no database connection");
                return;
            }

            // Load guilds
            stmt = conn.prepareStatement("SELECT * FROM guilds");
            rs = stmt.executeQuery();

            while (rs.next()) {
                UUID guildId = UUID.fromString(rs.getString("guild_id"));
                String name = rs.getString("name");
                UUID leaderId = UUID.fromString(rs.getString("leader_id"));

                Guild guild = new Guild(guildId, name, leaderId);
                guilds.put(guildId, guild);
            }

            // Close and cleanup
            rs.close();
            stmt.close();

            // Load guild members and ranks
            for (Guild guild : guilds.values()) {
                loadGuildMembers(conn, guild);
                loadGuildFounders(conn, guild);
            }

            // Load guild halls
            loadGuildHalls(conn);

            // Build player-to-guild map for quick lookup
            updatePlayerGuildMap();

            plugin.getLogger().info("Loaded " + guilds.size() + " guilds from database");

        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading guilds from database: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing resources", e);
            }
        }
    }

    private void loadGuildMembers(Connection conn, Guild guild) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.prepareStatement("SELECT * FROM guild_members WHERE guild_id = ?");
            stmt.setString(1, guild.getId().toString());
            rs = stmt.executeQuery();

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_id"));
                int rank = rs.getInt("rank");

                // Add player to the guild
                guild.addMember(playerId);

                // If officer (rank 2) or leader (rank 3)
                if (rank == 2) {
                    guild.addOfficer(playerId);
                }
                // Leader is already set during guild creation
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void loadGuildFounders(Connection conn, Guild guild) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = conn.prepareStatement("SELECT * FROM guild_founders WHERE guild_id = ?");
            stmt.setString(1, guild.getId().toString());
            rs = stmt.executeQuery();

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_id"));
                guild.addFounder(playerId);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void loadGuildHalls(Connection conn) throws SQLException {
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // Load guild halls
            stmt = conn.prepareStatement("SELECT * FROM land_claims WHERE owner_type = 'GUILD'");
            rs = stmt.executeQuery();

            while (rs.next()) {
                UUID hallId = UUID.fromString(rs.getString("claim_id"));
                UUID guildId = UUID.fromString(rs.getString("owner_id"));
                String world = rs.getString("world");
                int chunkX = rs.getInt("chunk_x");
                int chunkZ = rs.getInt("chunk_z");

                // Skip if guild doesn't exist
                if (!guilds.containsKey(guildId)) {
                    continue;
                }

                // Create guild hall object
                Guild guild = guilds.get(guildId);
                World bukkitWorld = Bukkit.getWorld(world);

                if (bukkitWorld == null) {
                    plugin.getLogger().warning("World not found for guild hall: " + world);
                    continue;
                }

                // Create center location from chunk coordinates
                Location center = new Location(bukkitWorld, chunkX * 16 + 8, 64, chunkZ * 16 + 8);

                // Create or update guild hall
                if (guildHalls.containsKey(hallId)) {
                    GuildHall hall = guildHalls.get(hallId);
                    hall.addChunk(chunkX, chunkZ);
                } else {
                    GuildHall hall = new GuildHall(hallId, guildId, guild.getName() + "'s Hall", world, center);
                    guildHalls.put(hallId, hall);
                }
            }

            plugin.getLogger().info("Loaded " + guildHalls.size() + " guild halls from database");

        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }
    }

    private void updatePlayerGuildMap() {
        playerGuildMap.clear();

        for (Guild guild : guilds.values()) {
            for (UUID playerId : guild.getMembers()) {
                playerGuildMap.put(playerId, guild.getId());
            }
        }
    }

    public Guild getGuild(UUID guildId) {
        return guilds.get(guildId);
    }

    public Guild getGuildByName(String name) {
        for (Guild guild : guilds.values()) {
            if (guild.getName().equalsIgnoreCase(name)) {
                return guild;
            }
        }
        return null;
    }

    public Guild getPlayerGuild(UUID playerId) {
        UUID guildId = playerGuildMap.get(playerId);
        return guildId != null ? guilds.get(guildId) : null;
    }

    public Guild getPlayerGuild(Player player) {
        return getPlayerGuild(player.getUniqueId());
    }

    public boolean isPlayerInGuild(UUID playerId) {
        return playerGuildMap.containsKey(playerId);
    }

    public boolean isPlayerInGuild(Player player) {
        return isPlayerInGuild(player.getUniqueId());
    }

    public Collection<Guild> getAllGuilds() {
        return Collections.unmodifiableCollection(guilds.values());
    }

    public GuildHall getGuildHall(UUID hallId) {
        return guildHalls.get(hallId);
    }

    public Collection<GuildHall> getAllGuildHalls() {
        return Collections.unmodifiableCollection(guildHalls.values());
    }

    public GuildHall getGuildHallForGuild(UUID guildId) {
        for (GuildHall hall : guildHalls.values()) {
            if (hall.getGuildId().equals(guildId)) {
                return hall;
            }
        }
        return null;
    }

    public GuildHall getGuildHallAtChunk(String world, int chunkX, int chunkZ) {
        for (GuildHall hall : guildHalls.values()) {
            if (hall.getWorld().equals(world) && hall.isChunkClaimed(chunkX, chunkZ)) {
                return hall;
            }
        }
        return null;
    }

    public GuildHall getGuildHallAtLocation(Location location) {
        return getGuildHallAtChunk(location.getWorld().getName(),
                location.getBlockX() >> 4,
                location.getBlockZ() >> 4);
    }

    public boolean createGuild(Player player, String guildName) {
        // Check if player is already in a guild
        if (isPlayerInGuild(player)) {
            player.sendMessage("§cYou are already in a guild!");
            return false;
        }

        // Check if guild name is taken
        if (getGuildByName(guildName) != null) {
            player.sendMessage("§cA guild with that name already exists!");
            return false;
        }

        // Check if player has enough coins
        if (!plugin.getEconomyManager().hasEnough(player, GUILD_CREATION_COST)) {
            player.sendMessage("§cYou need " + GUILD_CREATION_COST + " coins to create a guild!");
            return false;
        }

        // Charge player
        if (!plugin.getEconomyManager().removeCoins(player, GUILD_CREATION_COST)) {
            player.sendMessage("§cFailed to create guild - transaction error!");
            return false;
        }

        // Create guild
        UUID guildId = UUID.randomUUID();
        Guild guild = new Guild(guildId, guildName, player.getUniqueId());

        // Save to database
        if (!saveGuildToDatabase(guild)) {
            // Refund if failed
            plugin.getEconomyManager().addCoins(player, GUILD_CREATION_COST);
            player.sendMessage("§cFailed to create guild - database error!");
            return false;
        }

        // Add to in-memory map
        guilds.put(guildId, guild);
        playerGuildMap.put(player.getUniqueId(), guildId);

        player.sendMessage("§aYou have created the guild '" + guildName + "'!");
        return true;
    }

    private boolean saveGuildToDatabase(Guild guild) {
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean autoCommitOriginal = true;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot save guild - no database connection");
                return false;
            }

            // Begin transaction
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Save guild
            stmt = conn.prepareStatement(
                    "INSERT INTO guilds (guild_id, name, leader_id, creation_date) VALUES (?, ?, ?, ?)");
            stmt.setString(1, guild.getId().toString());
            stmt.setString(2, guild.getName());
            stmt.setString(3, guild.getLeaderId().toString());
            stmt.setLong(4, guild.getCreationDate());
            stmt.executeUpdate();
            stmt.close();

            // Save leader as member with rank 3
            stmt = conn.prepareStatement(
                    "INSERT INTO guild_members (player_id, guild_id, rank) VALUES (?, ?, ?)");
            stmt.setString(1, guild.getLeaderId().toString());
            stmt.setString(2, guild.getId().toString());
            stmt.setInt(3, 3); // Leader rank
            stmt.executeUpdate();
            stmt.close();

            // Save founder
            stmt = conn.prepareStatement(
                    "INSERT INTO guild_founders (guild_id, player_id) VALUES (?, ?)");
            stmt.setString(1, guild.getId().toString());
            stmt.setString(2, guild.getLeaderId().toString());
            stmt.executeUpdate();

            // Commit transaction
            conn.commit();
            return true;

        } catch (SQLException e) {
            // Rollback on error
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException rollbackEx) {
                plugin.getLogger().severe("Failed to rollback transaction: " + rollbackEx.getMessage());
            }

            plugin.getLogger().severe("Database error saving guild: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Close resources
            try {
                if (stmt != null) stmt.close();

                // Restore original auto-commit setting
                if (conn != null) {
                    conn.setAutoCommit(autoCommitOriginal);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing resources: " + e.getMessage());
            }
        }
    }

    public boolean addMemberToGuild(Player leader, Player newMember) {
        Guild guild = getPlayerGuild(leader);

        // Check if leader is in a guild and is the leader
        if (guild == null || !guild.isLeader(leader.getUniqueId())) {
            leader.sendMessage("§cYou are not the leader of a guild!");
            return false;
        }

        // Check if guild is full
        if (guild.isFull()) {
            leader.sendMessage("§cYour guild is full! (Maximum " + 10 + " members)");
            return false;
        }

        // Check if player is already in a guild
        if (isPlayerInGuild(newMember)) {
            leader.sendMessage("§c" + newMember.getName() + " is already in a guild!");
            return false;
        }

        // Add player to guild
        if (!guild.addMember(newMember.getUniqueId())) {
            leader.sendMessage("§cFailed to add member to guild!");
            return false;
        }

        // Save to database
        if (!saveGuildMemberToDatabase(guild.getId(), newMember.getUniqueId(), 1)) {
            // Remove from memory if database save fails
            guild.removeMember(newMember.getUniqueId());
            leader.sendMessage("§cFailed to add member to guild - database error!");
            return false;
        }

        // Update player-guild map
        playerGuildMap.put(newMember.getUniqueId(), guild.getId());

        // Notify both players
        leader.sendMessage("§a" + newMember.getName() + " has joined your guild!");
        newMember.sendMessage("§aYou have joined the guild '" + guild.getName() + "'!");

        return true;
    }

    private boolean saveGuildMemberToDatabase(UUID guildId, UUID playerId, int rank) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot save guild member - no database connection");
                return false;
            }

            stmt = conn.prepareStatement(
                    "INSERT INTO guild_members (player_id, guild_id, rank) VALUES (?, ?, ?)");
            stmt.setString(1, playerId.toString());
            stmt.setString(2, guildId.toString());
            stmt.setInt(3, rank);
            stmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error saving guild member: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing statement: " + e.getMessage());
            }
        }
    }

    public boolean removePlayerFromGuild(Player leader, Player member) {
        Guild guild = getPlayerGuild(leader);

        // Check if leader is in a guild and is the leader or an officer
        if (guild == null || (!guild.isLeader(leader.getUniqueId()) && !guild.isOfficer(leader.getUniqueId()))) {
            leader.sendMessage("§cYou do not have permission to remove members!");
            return false;
        }

        // Officers can't remove other officers or the leader
        if (guild.isOfficer(leader.getUniqueId()) &&
                (guild.isOfficer(member.getUniqueId()) || guild.isLeader(member.getUniqueId()))) {
            leader.sendMessage("§cYou cannot remove officers or the guild leader!");
            return false;
        }

        // Check if player is in this guild
        if (!guild.isMember(member.getUniqueId())) {
            leader.sendMessage("§c" + member.getName() + " is not in your guild!");
            return false;
        }

        // Remove player from guild
        if (!guild.removeMember(member.getUniqueId())) {
            leader.sendMessage("§cFailed to remove member from guild!");
            return false;
        }

        // Remove from database
        if (!removeGuildMemberFromDatabase(guild.getId(), member.getUniqueId())) {
            // Add back to memory if database removal fails
            guild.addMember(member.getUniqueId());
            leader.sendMessage("§cFailed to remove member from guild - database error!");
            return false;
        }

        // Update player-guild map
        playerGuildMap.remove(member.getUniqueId());

        // Notify both players
        leader.sendMessage("§a" + member.getName() + " has been removed from your guild!");
        member.sendMessage("§cYou have been removed from the guild '" + guild.getName() + "'!");

        return true;
    }

    private boolean removeGuildMemberFromDatabase(UUID guildId, UUID playerId) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot remove guild member - no database connection");
                return false;
            }

            stmt = conn.prepareStatement(
                    "DELETE FROM guild_members WHERE player_id = ? AND guild_id = ?");
            stmt.setString(1, playerId.toString());
            stmt.setString(2, guildId.toString());
            stmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error removing guild member: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing statement: " + e.getMessage());
            }
        }
    }

    public boolean promoteMember(Player leader, Player member) {
        Guild guild = getPlayerGuild(leader);

        // Check if leader is in a guild and is the leader
        if (guild == null || !guild.isLeader(leader.getUniqueId())) {
            leader.sendMessage("§cYou are not the leader of a guild!");
            return false;
        }

        // Check if player is in this guild
        if (!guild.isMember(member.getUniqueId())) {
            leader.sendMessage("§c" + member.getName() + " is not in your guild!");
            return false;
        }

        // Check if player is already an officer
        if (guild.isOfficer(member.getUniqueId())) {
            leader.sendMessage("§c" + member.getName() + " is already an officer!");
            return false;
        }

        // Promote player
        if (!guild.addOfficer(member.getUniqueId())) {
            leader.sendMessage("§cFailed to promote member!");
            return false;
        }

        // Update in database
        if (!updateGuildMemberRank(guild.getId(), member.getUniqueId(), 2)) {
            // Demote in memory if database update fails
            guild.removeOfficer(member.getUniqueId());
            leader.sendMessage("§cFailed to promote member - database error!");
            return false;
        }

        // Notify both players
        leader.sendMessage("§a" + member.getName() + " has been promoted to Officer!");
        member.sendMessage("§aYou have been promoted to Officer in the guild '" + guild.getName() + "'!");

        return true;
    }

    public boolean demoteMember(Player leader, Player officer) {
        Guild guild = getPlayerGuild(leader);

        // Check if leader is in a guild and is the leader
        if (guild == null || !guild.isLeader(leader.getUniqueId())) {
            leader.sendMessage("§cYou are not the leader of a guild!");
            return false;
        }

        // Check if player is in this guild
        if (!guild.isMember(officer.getUniqueId())) {
            leader.sendMessage("§c" + officer.getName() + " is not in your guild!");
            return false;
        }

        // Check if player is an officer
        if (!guild.isOfficer(officer.getUniqueId())) {
            leader.sendMessage("§c" + officer.getName() + " is not an officer!");
            return false;
        }

        // Demote player
        if (!guild.removeOfficer(officer.getUniqueId())) {
            leader.sendMessage("§cFailed to demote officer!");
            return false;
        }

        // Update in database
        if (!updateGuildMemberRank(guild.getId(), officer.getUniqueId(), 1)) {
            // Promote in memory if database update fails
            guild.addOfficer(officer.getUniqueId());
            leader.sendMessage("§cFailed to demote officer - database error!");
            return false;
        }

        // Notify both players
        leader.sendMessage("§a" + officer.getName() + " has been demoted to Member!");
        officer.sendMessage("§cYou have been demoted to Member in the guild '" + guild.getName() + "'!");

        return true;
    }

    private boolean updateGuildMemberRank(UUID guildId, UUID playerId, int newRank) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot update guild member rank - no database connection");
                return false;
            }

            stmt = conn.prepareStatement(
                    "UPDATE guild_members SET rank = ? WHERE player_id = ? AND guild_id = ?");
            stmt.setInt(1, newRank);
            stmt.setString(2, playerId.toString());
            stmt.setString(3, guildId.toString());
            stmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error updating guild member rank: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing statement: " + e.getMessage());
            }
        }
    }

    public boolean createGuildHall(Player player, String hallName) {
        Guild guild = getPlayerGuild(player);

        // Check if player is in a guild and is the leader
        if (guild == null || !guild.isLeader(player.getUniqueId())) {
            player.sendMessage("§cYou are not the leader of a guild!");
            return false;
        }

        // Check if guild already has a hall
        if (getGuildHallForGuild(guild.getId()) != null) {
            player.sendMessage("§cYour guild already has a hall!");
            return false;
        }

        // Check if player has enough coins
        if (!plugin.getEconomyManager().hasEnough(player, CHUNK_CLAIM_COST)) {
            player.sendMessage("§cYou need " + CHUNK_CLAIM_COST + " coins to claim a guild hall!");
            return false;
        }

        // Check if location is already claimed
        Chunk chunk = player.getLocation().getChunk();
        if (getGuildHallAtChunk(player.getWorld().getName(), chunk.getX(), chunk.getZ()) != null) {
            player.sendMessage("§cThis chunk is already claimed by another guild!");
            return false;
        }

        // ADD THIS: Check if chunk is protected by other WorldGuard regions
        if (isChunkProtectedByOtherRegion(chunk)) {
            player.sendMessage("§cThis area is protected by another region and cannot be claimed!");
            return false;
        }

        // Charge player
        if (!plugin.getEconomyManager().removeCoins(player, CHUNK_CLAIM_COST)) {
            player.sendMessage("§cFailed to claim guild hall - transaction error!");
            return false;
        }

        // Create guild hall
        UUID hallId = UUID.randomUUID();
        GuildHall hall = new GuildHall(
                hallId,
                guild.getId(),
                hallName,
                player.getWorld().getName(),
                player.getLocation()
        );

        // Save to database
        if (!saveGuildHallToDatabase(hall, chunk.getX(), chunk.getZ())) {
            // Refund if failed
            plugin.getEconomyManager().addCoins(player, CHUNK_CLAIM_COST);
            player.sendMessage("§cFailed to claim guild hall - database error!");
            return false;
        }

        // Add to in-memory map
        guildHalls.put(hallId, hall);

        // Create WorldGuard region
        if (!createWorldGuardRegion(hall, chunk)) {
            // This is not critical, continue anyway but log the error
            plugin.getLogger().warning("Failed to create WorldGuard region for guild hall: " + hallId);
        }

        player.sendMessage("§aYou have claimed this chunk as your guild hall!");
        return true;
    }

    public boolean claimAdditionalChunk(Player player) {
        Guild guild = getPlayerGuild(player);

        // Check if player is in a guild and is the leader or an officer
        if (guild == null || (!guild.isLeader(player.getUniqueId()) && !guild.isOfficer(player.getUniqueId()))) {
            player.sendMessage("§cYou do not have permission to claim chunks!");
            return false;
        }

        // Get the guild hall
        GuildHall hall = getGuildHallForGuild(guild.getId());
        if (hall == null) {
            player.sendMessage("§cYour guild doesn't have a hall yet!");
            return false;
        }

        // Check if hall is full
        if (hall.isFull()) {
            player.sendMessage("§cYour guild hall has reached the maximum number of chunks! (Maximum " + 15 + " chunks)");
            return false;
        }

        // Check if player has enough coins
        if (!plugin.getEconomyManager().hasEnough(player, CHUNK_CLAIM_COST)) {
            player.sendMessage("§cYou need " + CHUNK_CLAIM_COST + " coins to claim an additional chunk!");
            return false;
        }

        // Check if location is already claimed
        Chunk chunk = player.getLocation().getChunk();
        if (getGuildHallAtChunk(player.getWorld().getName(), chunk.getX(), chunk.getZ()) != null) {
            player.sendMessage("§cThis chunk is already claimed by a guild!");
            return false;
        }

        // ADD THIS: Check if chunk is protected by other WorldGuard regions
        if (isChunkProtectedByOtherRegion(chunk)) {
            player.sendMessage("§cThis area is protected by another region and cannot be claimed!");
            return false;
        }

        // Check if chunk is adjacent to an existing claim
        if (!hall.isChunkClaimed(chunk) && !hall.isAdjacentToClaimedChunk(chunk.getX(), chunk.getZ())) {
            player.sendMessage("§cNew claims must be adjacent to your existing guild hall!");
            return false;
        }

        // Charge player
        if (!plugin.getEconomyManager().removeCoins(player, CHUNK_CLAIM_COST)) {
            player.sendMessage("§cFailed to claim chunk - transaction error!");
            return false;
        }

        // Add chunk to hall
        if (!hall.addChunk(chunk.getX(), chunk.getZ())) {
            plugin.getEconomyManager().addCoins(player, CHUNK_CLAIM_COST);
            player.sendMessage("§cFailed to claim chunk!");
            return false;
        }

        // Save to database
        if (!saveGuildHallChunkToDatabase(hall.getId(), hall.getGuildId(), chunk.getX(), chunk.getZ())) {
            // Rollback and refund
            hall.removeChunk(chunk.getX(), chunk.getZ());
            plugin.getEconomyManager().addCoins(player, CHUNK_CLAIM_COST);
            player.sendMessage("§cFailed to claim chunk - database error!");
            return false;
        }

        // Update WorldGuard region
        if (!extendWorldGuardRegion(hall, chunk)) {
            // This is not critical, continue anyway but log the error
            plugin.getLogger().warning("Failed to update WorldGuard region for guild hall: " + hall.getId());
        }

        player.sendMessage("§aYou have claimed an additional chunk for your guild hall!");
        return true;
    }

    private boolean saveGuildHallToDatabase(GuildHall hall, int chunkX, int chunkZ) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot save guild hall - no database connection");
                return false;
            }

            stmt = conn.prepareStatement(
                    "INSERT INTO land_claims (claim_id, owner_id, owner_type, world, chunk_x, chunk_z, radius, creation_date) " +
                            "VALUES (?, ?, 'GUILD', ?, ?, ?, 0, ?)");
            stmt.setString(1, hall.getId().toString());
            stmt.setString(2, hall.getGuildId().toString());
            stmt.setString(3, hall.getWorld());
            stmt.setInt(4, chunkX);
            stmt.setInt(5, chunkZ);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error saving guild hall: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing statement: " + e.getMessage());
            }
        }
    }

    private boolean saveGuildHallChunkToDatabase(UUID hallId, UUID guildId, int chunkX, int chunkZ) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot save guild hall chunk - no database connection");
                return false;
            }

            // CHANGE THIS: Generate a unique ID for each chunk claim
            UUID uniqueClaimId = UUID.randomUUID();

            stmt = conn.prepareStatement(
                    "INSERT INTO land_claims (claim_id, owner_id, owner_type, world, chunk_x, chunk_z, radius, creation_date) " +
                            "VALUES (?, ?, 'GUILD', ?, ?, ?, 0, ?)");

            // Use the unique claim ID instead of reusing the hall ID
            stmt.setString(1, uniqueClaimId.toString());
            stmt.setString(2, guildId.toString());

            // Get the world from the existing hall data
            GuildHall hall = guildHalls.get(hallId);
            stmt.setString(3, hall.getWorld());

            stmt.setInt(4, chunkX);
            stmt.setInt(5, chunkZ);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error saving guild hall chunk: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing statement: " + e.getMessage());
            }
        }
    }

    private boolean createWorldGuardRegion(GuildHall hall, Chunk chunk) {
        try {
            // Get necessary WorldGuard components
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(Bukkit.getWorld(hall.getWorld())));

            if (regions == null) {
                plugin.getLogger().warning("Could not get RegionManager for world: " + hall.getWorld());
                return false;
            }

            // Create region ID based on guild ID (shorter)
            String regionId = "guild_" + hall.getGuildId().toString().substring(0, 8);

            // Make sure no region with this ID exists
            if (regions.hasRegion(regionId)) {
                // Try adding a suffix
                int suffix = 1;
                while (regions.hasRegion(regionId + "_" + suffix) && suffix < 100) {
                    suffix++;
                }
                regionId = regionId + "_" + suffix;

                if (regions.hasRegion(regionId)) {
                    plugin.getLogger().warning("Failed to create unique region ID for guild hall");
                    return false;
                }
            }

            // Define region boundaries (chunk)
            com.sk89q.worldedit.math.BlockVector3 min = BukkitAdapter.asBlockVector(
                    new Location(Bukkit.getWorld(hall.getWorld()), chunk.getX() * 16, 0, chunk.getZ() * 16)
            );
            com.sk89q.worldedit.math.BlockVector3 max = BukkitAdapter.asBlockVector(
                    new Location(Bukkit.getWorld(hall.getWorld()), chunk.getX() * 16 + 15, 255, chunk.getZ() * 16 + 15)
            );

            // Create region
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

            // Set flags
            region.setFlag(Flags.BUILD, StateFlag.State.DENY);          // Prevent non-members from building
            region.setFlag(Flags.PVP, StateFlag.State.ALLOW);           // Enable PvP
            region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);   // Disable mob spawning

            // Set custom ALLOW_COMBAT_FLAG for skill system
            region.setFlag(MinecraftMMORPG.ALLOW_COMBAT_FLAG, StateFlag.State.ALLOW);

            // Add region
            regions.addRegion(region);

            // Get guild members
            Guild guild = guilds.get(hall.getGuildId());
            if (guild != null) {
                com.sk89q.worldguard.domains.DefaultDomain owners = new com.sk89q.worldguard.domains.DefaultDomain();

                // Add leader as owner - use UUID string instead of player object
                owners.addPlayer(guild.getLeaderId().toString());

                // Add all guild members as members - use UUID strings
                com.sk89q.worldguard.domains.DefaultDomain members = new com.sk89q.worldguard.domains.DefaultDomain();
                for (UUID memberId : guild.getMembers()) {
                    members.addPlayer(memberId.toString());
                }

                region.setOwners(owners);
                region.setMembers(members);
            }

            // Save changes
            regions.save();

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating WorldGuard region: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean extendWorldGuardRegion(GuildHall hall, Chunk chunk) {
        try {
            // Get necessary WorldGuard components
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(Bukkit.getWorld(hall.getWorld())));

            if (regions == null) {
                plugin.getLogger().warning("Could not get RegionManager for world: " + hall.getWorld());
                return false;
            }

            // Find the existing region for this guild hall
            String baseRegionId = "guild_" + hall.getGuildId().toString().substring(0, 8);
            ProtectedRegion existingRegion = null;

            // Try to find the region with potential suffixes
            if (regions.hasRegion(baseRegionId)) {
                existingRegion = regions.getRegion(baseRegionId);
            } else {
                int suffix = 1;
                while (suffix < 100 && existingRegion == null) {
                    String regionId = baseRegionId + "_" + suffix;
                    if (regions.hasRegion(regionId)) {
                        existingRegion = regions.getRegion(regionId);
                    }
                    suffix++;
                }
            }

            if (existingRegion == null) {
                // No existing region found, create a new one
                return createWorldGuardRegion(hall, chunk);
            }

            // Define the new chunk boundaries
            com.sk89q.worldedit.math.BlockVector3 min = BukkitAdapter.asBlockVector(
                    new Location(Bukkit.getWorld(hall.getWorld()), chunk.getX() * 16, 0, chunk.getZ() * 16)
            );
            com.sk89q.worldedit.math.BlockVector3 max = BukkitAdapter.asBlockVector(
                    new Location(Bukkit.getWorld(hall.getWorld()), chunk.getX() * 16 + 15, 255, chunk.getZ() * 16 + 15)
            );

            // Create a new region for this chunk
            String newRegionId = baseRegionId + "_chunk_" + chunk.getX() + "_" + chunk.getZ();
            ProtectedCuboidRegion newRegion = new ProtectedCuboidRegion(newRegionId, min, max);

            // Copy flags from the existing region
            newRegion.setFlags(existingRegion.getFlags());

            // Ensure flags are properly set
            newRegion.setFlag(Flags.BUILD, StateFlag.State.DENY);
            newRegion.setFlag(Flags.PVP, StateFlag.State.ALLOW);
            newRegion.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
            newRegion.setFlag(MinecraftMMORPG.ALLOW_COMBAT_FLAG, StateFlag.State.ALLOW);

            // Copy owners and members from the existing region
            newRegion.setOwners(existingRegion.getOwners());
            newRegion.setMembers(existingRegion.getMembers());

            // Add the new region
            regions.addRegion(newRegion);

            // Save changes
            regions.save();

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error extending WorldGuard region: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void broadcastToGuild(UUID guildId, String message) {
        Guild guild = getGuild(guildId);
        if (guild != null) {
            guild.broadcastMessage(message);
        }
    }

    public void disbandGuild(Player leader) {
        Guild guild = getPlayerGuild(leader);

        // Check if player is in a guild and is the leader
        if (guild == null || !guild.isLeader(leader.getUniqueId())) {
            leader.sendMessage("§cYou are not the leader of a guild!");
            return;
        }

        // Confirm to all members
        guild.broadcastMessage("§c§lThe guild '" + guild.getName() + "' has been disbanded by " + leader.getName() + "!");

        // Remove guild halls
        GuildHall hall = getGuildHallForGuild(guild.getId());
        if (hall != null) {
            removeGuildHall(hall);
        }

        // Remove from database
        removeGuildFromDatabase(guild.getId());

        // Remove from in-memory maps
        for (UUID memberId : guild.getMembers()) {
            playerGuildMap.remove(memberId);
        }
        guilds.remove(guild.getId());
    }

    private void removeGuildHall(GuildHall hall) {
        try {
            // Remove WorldGuard regions
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(Bukkit.getWorld(hall.getWorld())));

            if (regions != null) {
                // Remove all regions for this guild
                String baseRegionId = "guild_" + hall.getGuildId().toString().substring(0, 8);

                // Try to find and remove the main region
                if (regions.hasRegion(baseRegionId)) {
                    regions.removeRegion(baseRegionId);
                }

                // Try to find and remove any chunk regions
                for (int suffix = 1; suffix < 100; suffix++) {
                    String regionId = baseRegionId + "_" + suffix;
                    if (regions.hasRegion(regionId)) {
                        regions.removeRegion(regionId);
                    }
                }

                // Try to find and remove any chunk-specific regions
                for (GuildHall.ChunkPosition chunk : hall.getClaimedChunks()) {
                    String regionId = baseRegionId + "_chunk_" + chunk.getX() + "_" + chunk.getZ();
                    if (regions.hasRegion(regionId)) {
                        regions.removeRegion(regionId);
                    }
                }

                // Save changes
                regions.save();
            }

            // Remove from database
            removeGuildHallFromDatabase(hall.getId());

            // Remove from in-memory map
            guildHalls.remove(hall.getId());

        } catch (Exception e) {
            plugin.getLogger().severe("Error removing guild hall regions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean removeGuildFromDatabase(UUID guildId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean autoCommitOriginal = true;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot remove guild - no database connection");
                return false;
            }

            // Begin transaction
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Delete from guild_members
            stmt = conn.prepareStatement("DELETE FROM guild_members WHERE guild_id = ?");
            stmt.setString(1, guildId.toString());
            stmt.executeUpdate();
            stmt.close();

            // Delete from guild_founders
            stmt = conn.prepareStatement("DELETE FROM guild_founders WHERE guild_id = ?");
            stmt.setString(1, guildId.toString());
            stmt.executeUpdate();
            stmt.close();

            // Delete from land_claims
            stmt = conn.prepareStatement("DELETE FROM land_claims WHERE owner_id = ? AND owner_type = 'GUILD'");
            stmt.setString(1, guildId.toString());
            stmt.executeUpdate();
            stmt.close();

            // Delete from guilds
            stmt = conn.prepareStatement("DELETE FROM guilds WHERE guild_id = ?");
            stmt.setString(1, guildId.toString());
            stmt.executeUpdate();

            // Commit transaction
            conn.commit();
            return true;

        } catch (SQLException e) {
            // Rollback on error
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException rollbackEx) {
                plugin.getLogger().severe("Failed to rollback transaction: " + rollbackEx.getMessage());
            }

            plugin.getLogger().severe("Database error removing guild: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Close resources
            try {
                if (stmt != null) stmt.close();

                // Restore original auto-commit setting
                if (conn != null) {
                    conn.setAutoCommit(autoCommitOriginal);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing resources: " + e.getMessage());
            }
        }
    }

    private boolean removeGuildHallFromDatabase(UUID hallId) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot remove guild hall - no database connection");
                return false;
            }

            stmt = conn.prepareStatement("DELETE FROM land_claims WHERE claim_id = ?");
            stmt.setString(1, hallId.toString());
            stmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error removing guild hall: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing statement: " + e.getMessage());
            }
        }
    }

    private boolean isChunkProtectedByOtherRegion(Chunk chunk) {
        try {
            // Get WorldGuard components
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(chunk.getWorld()));

            if (regions == null) {
                return false;
            }

            // Check the center of the chunk and the four corners
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();

            // Check five points in the chunk (center + corners)
            int[][] points = new int[][] {
                    {chunkX * 16 + 8, 64, chunkZ * 16 + 8},   // Center
                    {chunkX * 16, 64, chunkZ * 16},           // Lower northwest corner
                    {chunkX * 16 + 15, 64, chunkZ * 16},      // Lower northeast corner
                    {chunkX * 16, 64, chunkZ * 16 + 15},      // Lower southwest corner
                    {chunkX * 16 + 15, 64, chunkZ * 16 + 15}  // Lower southeast corner
            };

            for (int[] point : points) {
                // Create a BlockVector3 for this point
                com.sk89q.worldedit.math.BlockVector3 locationVector =
                        com.sk89q.worldedit.math.BlockVector3.at(point[0], point[1], point[2]);

                // Get all regions at this point
                ApplicableRegionSet regionSet = regions.getApplicableRegions(locationVector);

                // Check if there are any regions at this point that aren't guild regions
                boolean hasNonGuildRegion = false;
                for (ProtectedRegion region : regionSet) {
                    if (!region.getId().startsWith("guild_")) {
                        hasNonGuildRegion = true;
                        break;
                    }
                }

                if (hasNonGuildRegion) {
                    return true; // Found a non-guild region that protects this chunk
                }
            }

            return false; // No protecting regions found
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking for existing regions: " + e.getMessage());
            e.printStackTrace();
            return true; // If there's an error, assume it's protected to be safe
        }
    }
}