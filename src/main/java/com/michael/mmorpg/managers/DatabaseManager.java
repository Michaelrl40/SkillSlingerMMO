package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.models.PlayerData;
import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {
    private final MinecraftMMORPG plugin;
    private Connection connection;

    public DatabaseManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        try {
            // Load the JDBC driver early to catch any class loading issues
            Class.forName("org.sqlite.JDBC");
            initDatabase();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite JDBC driver not found! Database functionality will not work.");
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized void initDatabase() {
        try {
            // Make sure connection is established
            Connection conn = getConnection();

            try (Statement stmt = conn.createStatement()) {
                // Create the players table
                stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                        "uuid TEXT PRIMARY KEY," +
                        "current_class TEXT NOT NULL DEFAULT 'Vagrant'," +
                        "health DOUBLE NOT NULL DEFAULT 20.0," +
                        "mana DOUBLE NOT NULL DEFAULT 50.0," +
                        "stamina DOUBLE NOT NULL DEFAULT 50.0," +
                        "rage DOUBLE NOT NULL DEFAULT 0.0," +
                        "toxin DOUBLE NOT NULL DEFAULT 0.0," +
                        "coins INTEGER NOT NULL DEFAULT 0" +
                        ")");

                // Create the class progress table
                stmt.execute("CREATE TABLE IF NOT EXISTS class_progress (" +
                        "uuid TEXT," +
                        "class_name TEXT," +
                        "level INTEGER NOT NULL DEFAULT 1," +
                        "experience REAL NOT NULL DEFAULT 0," +
                        "total_experience REAL NOT NULL DEFAULT 0," +
                        "is_mastered BOOLEAN NOT NULL DEFAULT 0," +
                        "PRIMARY KEY (uuid, class_name)," +
                        "FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                        ")");

                plugin.getLogger().info("Player database tables initialized successfully!");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Player database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized Connection getConnection() {
        try {
            // Check if connection needs to be created or re-established
            if (connection == null || connection.isClosed()) {
                plugin.getLogger().info("Establishing new database connection...");

                // Ensure data folder exists
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }

                // Create and configure connection
                String dbPath = new File(plugin.getDataFolder(), "players.db").getAbsolutePath();
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                connection.setAutoCommit(true);

                // Test the connection with a simple query
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SELECT 1");
                }

                plugin.getLogger().info("Database connection established successfully to: " + dbPath);
            }
            return connection;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to establish database connection", e);
            return null;
        }
    }

    public void savePlayerState(UUID playerId, PlayerData data) {
        Connection conn = null;
        PreparedStatement playerStmt = null;
        PreparedStatement classStmt = null;
        boolean autoCommitOriginal = true;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot save player state - no database connection");
                return;
            }

            plugin.getLogger().info("Saving state for player: " + playerId);

            // Begin transaction
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Prepare statements outside try-with-resources to manually manage the transaction
            playerStmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO players " +
                            "(uuid, current_class, health, mana, stamina, rage, toxin, coins) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

            classStmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO class_progress " +
                            "(uuid, class_name, level, experience, total_experience, is_mastered) " +
                            "VALUES (?, ?, ?, ?, ?, ?)");

            // Save player base data
            playerStmt.setString(1, playerId.toString());
            playerStmt.setString(2, data.getGameClass() != null ? data.getGameClass().getName() : "Vagrant");
            playerStmt.setDouble(3, data.getCurrentHealth());
            playerStmt.setDouble(4, data.getCurrentMana());
            playerStmt.setDouble(5, data.getCurrentStamina());
            playerStmt.setDouble(6, data.getCurrentRage());
            playerStmt.setDouble(7, data.getCurrentToxin());
            playerStmt.setInt(8, data.getCoins());
            playerStmt.executeUpdate();

            // Save class progress
            for (Map.Entry<String, PlayerData.ClassProgress> entry : data.getClassProgress().entrySet()) {
                String className = entry.getKey();
                PlayerData.ClassProgress progress = entry.getValue();

                classStmt.setString(1, playerId.toString());
                classStmt.setString(2, className);
                classStmt.setInt(3, progress.getLevel());
                classStmt.setDouble(4, progress.getExperience());
                classStmt.setDouble(5, progress.getTotalExperience());

                GameClass gameClass = plugin.getConfigManager().getClass(className);
                boolean isMastered = gameClass != null &&
                        gameClass.isMasteryComplete(progress.getLevel(), progress.getTotalExperience());
                classStmt.setBoolean(6, isMastered);

                classStmt.executeUpdate();
            }

            // Commit the transaction
            conn.commit();
            plugin.getLogger().info("Successfully saved player state for: " + playerId);

        } catch (SQLException e) {
            // Rollback on error
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Failed to rollback transaction: " + rollbackEx.getMessage());
                }
            }
            plugin.getLogger().severe("Database error saving player state: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close resources
            closeQuietly(classStmt);
            closeQuietly(playerStmt);

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

    public void loadPlayerState(UUID playerId, PlayerData data) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            plugin.getLogger().info("Loading state for player: " + playerId);

            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot load player state - no database connection");
                return;
            }

            stmt = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?");
            stmt.setString(1, playerId.toString());
            rs = stmt.executeQuery();

            if (rs.next()) {
                String currentClassName = rs.getString("current_class");
                data.setCurrentHealth(rs.getDouble("health"));
                data.setCurrentMana(rs.getDouble("mana"));
                data.setCurrentStamina(rs.getDouble("stamina"));
                data.setCurrentRage(rs.getDouble("rage"));
                data.setCurrentToxin(rs.getDouble("toxin"));
                data.setCoins(rs.getInt("coins"));  // Load coins

                loadClassProgress(playerId, data);

                if (currentClassName != null) {
                    GameClass currentClass = plugin.getConfigManager().getClass(currentClassName.toLowerCase());
                    if (currentClass != null) {
                        data.setGameClass(currentClass);
                    }
                }

                plugin.getLogger().info("Successfully loaded player state for: " + playerId);
            } else {
                plugin.getLogger().info("No existing data found - initializing new player");
                initializeNewPlayer(playerId, data);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player data: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    private void loadClassProgress(UUID playerId, PlayerData data) throws SQLException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot load class progress - no database connection");
                return;
            }

            stmt = conn.prepareStatement("SELECT * FROM class_progress WHERE uuid = ?");
            stmt.setString(1, playerId.toString());
            rs = stmt.executeQuery();

            while (rs.next()) {
                String className = rs.getString("class_name");
                data.setProgressForClass(
                        className,
                        rs.getInt("level"),
                        rs.getDouble("experience"),
                        rs.getDouble("total_experience")
                );
            }
        } finally {
            closeQuietly(rs);
            closeQuietly(stmt);
        }
    }

    private void initializeNewPlayer(UUID playerId, PlayerData data) {
        Connection conn = null;
        PreparedStatement playerStmt = null;
        PreparedStatement classStmt = null;
        boolean autoCommitOriginal = true;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot initialize new player - no database connection");
                return;
            }

            // Begin transaction
            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Initialize with Vagrant class
            GameClass vagrantClass = plugin.getConfigManager().getClass("vagrant");
            if (vagrantClass != null) {
                data.setGameClass(vagrantClass);
            }

            // Insert initial record
            playerStmt = conn.prepareStatement(
                    "INSERT INTO players (uuid, current_class) VALUES (?, 'Vagrant')");
            playerStmt.setString(1, playerId.toString());
            playerStmt.executeUpdate();

            // Initialize Vagrant class progress
            classStmt = conn.prepareStatement(
                    "INSERT INTO class_progress (uuid, class_name, level, experience, total_experience) " +
                            "VALUES (?, 'Vagrant', 1, 0.0, 0.0)");
            classStmt.setString(1, playerId.toString());
            classStmt.executeUpdate();

            // Commit the transaction
            conn.commit();
            plugin.getLogger().info("Successfully initialized new player: " + playerId);

        } catch (SQLException e) {
            // Rollback on error
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Failed to rollback transaction: " + rollbackEx.getMessage());
                }
            }
            plugin.getLogger().severe("Failed to initialize new player: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close resources
            closeQuietly(classStmt);
            closeQuietly(playerStmt);

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

    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("Database connection closed successfully.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error closing database: " + e.getMessage());
                e.printStackTrace();
            } finally {
                connection = null;
            }
        }
    }

    public void initGuildTables() {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot initialize guild tables - no database connection");
                return;
            }

            stmt = conn.createStatement();

            // Create guilds table
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS guilds (" +
                            "guild_id TEXT PRIMARY KEY, " +
                            "name TEXT NOT NULL, " +
                            "leader_id TEXT NOT NULL, " +
                            "creation_date INTEGER NOT NULL" +
                            ")"
            );

            // Create guild_members table
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS guild_members (" +
                            "player_id TEXT, " +
                            "guild_id TEXT, " +
                            "rank INTEGER NOT NULL, " +
                            "PRIMARY KEY (player_id, guild_id), " +
                            "FOREIGN KEY (guild_id) REFERENCES guilds(guild_id) ON DELETE CASCADE" +
                            ")"
            );

            // Create guild_founders table
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS guild_founders (" +
                            "guild_id TEXT, " +
                            "player_id TEXT, " +
                            "PRIMARY KEY (guild_id, player_id), " +
                            "FOREIGN KEY (guild_id) REFERENCES guilds(guild_id) ON DELETE CASCADE" +
                            ")"
            );

            plugin.getLogger().info("Guild database tables initialized successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize guild database tables: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(stmt);
        }
    }

    public void initLandClaimTables() {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot initialize land claim tables - no database connection");
                return;
            }

            stmt = conn.createStatement();

            // Create land_claims table with fixed schema (added missing comma)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS land_claims (" +
                            "claim_id TEXT PRIMARY KEY, " +
                            "owner_id TEXT NOT NULL, " +
                            "owner_type TEXT NOT NULL, " +
                            "world TEXT NOT NULL, " +
                            "chunk_x INTEGER NOT NULL, " +
                            "chunk_z INTEGER NOT NULL, " +
                            "radius INTEGER NOT NULL, " +
                            "creation_date INTEGER NOT NULL, " +
                            "allowed_players TEXT, " +  // Fixed missing comma here
                            "guild_build_permissions TEXT" +
                            ")"
            );

            plugin.getLogger().info("Land claim database tables initialized successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize land claim database tables: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(stmt);
        }
    }

    public void initGraveyardTables() {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot initialize graveyard tables - no database connection");
                return;
            }

            stmt = conn.createStatement();

            // Create graveyards table
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS graveyards (" +
                            "id TEXT PRIMARY KEY, " +
                            "name TEXT NOT NULL, " +
                            "world TEXT NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL" +
                            ")"
            );

            plugin.getLogger().info("Graveyard database tables initialized successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize graveyard database tables: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(stmt);
        }
    }

    public void initDeathChestTables() {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot initialize death chest tables - no database connection");
                return;
            }

            stmt = conn.createStatement();

            // Create death_chests table
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS death_chests (" +
                            "id TEXT PRIMARY KEY, " +
                            "owner_uuid TEXT NOT NULL, " +
                            "owner_name TEXT NOT NULL, " +
                            "world TEXT NOT NULL, " +
                            "x INT NOT NULL, " +
                            "y INT NOT NULL, " +
                            "z INT NOT NULL, " +
                            "sign_world TEXT, " +
                            "sign_x INT, " +
                            "sign_y INT, " +
                            "sign_z INT, " +
                            "creation_time BIGINT NOT NULL, " +
                            "expiration_time BIGINT NOT NULL, " +
                            "claimed BOOLEAN NOT NULL" +
                            ")"
            );

            // Create death_chest_items table for storing chest contents
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS death_chest_items (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "chest_id TEXT NOT NULL, " +
                            "slot INT NOT NULL, " +
                            "item_data BLOB NOT NULL, " +
                            "FOREIGN KEY (chest_id) REFERENCES death_chests(id) ON DELETE CASCADE" +
                            ")"
            );

            plugin.getLogger().info("Death chest database tables initialized successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize death chest database tables: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(stmt);
        }
    }

    // Update initDungeonTables to include time_limit column
    public void initDungeonTables() {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to init dungeon tables - no connection");
                return;
            }

            stmt = conn.createStatement();

            // Create dungeons table with time_limit field
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS dungeons (" +
                            "id TEXT PRIMARY KEY, " +
                            "name TEXT NOT NULL, " +
                            "world TEXT NOT NULL, " +
                            "entrance_world TEXT, " +
                            "entrance_x REAL, " +
                            "entrance_y REAL, " +
                            "entrance_z REAL, " +
                            "entrance_yaw REAL, " +
                            "entrance_pitch REAL, " +
                            "time_limit INTEGER DEFAULT 1200" +
                            ")");

            // Check if time_limit column already exists
            boolean columnExists = false;
            try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(dungeons)")) {
                while (rs.next()) {
                    if ("time_limit".equalsIgnoreCase(rs.getString("name"))) {
                        columnExists = true;
                        break;
                    }
                }
            }

            // Add the column if it doesn't exist
            if (!columnExists) {
                stmt.executeUpdate("ALTER TABLE dungeons ADD COLUMN time_limit INTEGER DEFAULT 1200");
                plugin.getLogger().info("Added time_limit column to dungeons table");
            }

            plugin.getLogger().info("Initialized dungeon database tables");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error initializing dungeon tables: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing statement: " + e.getMessage());
            }
        }
    }

    /**
     * Update the time limit for a dungeon
     */
    public void updateDungeonTimeLimit(UUID dungeonId, int timeLimit) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Failed to get database connection for updating dungeon time limit");
                return;
            }

            stmt = conn.prepareStatement("UPDATE dungeons SET time_limit = ? WHERE id = ?");
            stmt.setInt(1, timeLimit);
            stmt.setString(2, dungeonId.toString());

            int result = stmt.executeUpdate();
            if (result > 0) {
                plugin.getLogger().info("Updated time limit for dungeon " + dungeonId);
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating dungeon time limit: " + e.getMessage());
            e.printStackTrace();
        } finally {
            closeQuietly(stmt);
        }
    }


    // Helper method to quietly close JDBC resources
    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error closing resource", e);
            }
        }
    }
}