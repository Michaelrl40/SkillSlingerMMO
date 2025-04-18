package com.michael.mmorpg.title;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.title.Title;
import com.michael.mmorpg.title.Title.TitleCategory;
import com.michael.mmorpg.title.Title.TitleRarity;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class TitleManager {
    private final MinecraftMMORPG plugin;
    private final Map<String, Title> availableTitles = new HashMap<>();
    private final Map<UUID, Set<String>> playerTitles = new HashMap<>();
    private final Map<UUID, String> activeTitles = new HashMap<>();

    public TitleManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        initDatabase();
        registerDefaultTitles();
        loadPlayerTitles();
    }

    private void initDatabase() {
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot initialize title tables - no database connection");
                return;
            }

            // Create table for player titles
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS player_titles (" +
                            "player_uuid TEXT NOT NULL, " +
                            "title_id TEXT NOT NULL, " +
                            "PRIMARY KEY (player_uuid, title_id))"
            )) {
                stmt.execute();
            }

            // Create table for active titles
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS active_titles (" +
                            "player_uuid TEXT PRIMARY KEY, " +
                            "title_id TEXT NOT NULL)"
            )) {
                stmt.execute();
            }

            plugin.getLogger().info("Title database tables initialized successfully!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize title database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerDefaultTitles() {
        // Class Mastery Titles - Fun alternatives to class names

        //warriors
        registerTitle(new Title(
                "Warrior_master",
                "Warrior",
                "Mastered the Warrior class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "berserker_master",
                "Maniac",
                "Mastered the Berserker class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "guardian_master",
                "Bulwark",
                "Mastered the Guardian class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "skyknight_master",
                "Airborn",
                "Mastered the Skyknight class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "Chainwarder_master",
                "Lockdown",
                "Mastered the Chainwarden class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        //Healers
        registerTitle(new Title(
                "healer_master",
                "Healer",
                "Mastered the Healer class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "druid_master",
                "ShapeShifter",
                "Mastered the Druid class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "Chronomancer_master",
                "TimeTeller",
                "Mastered the Chronomancer class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        //Rangers
        registerTitle(new Title(
                "hunter_master",
                "Deadeye",
                "Mastered the Hunter class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        //Rogues


        //mages
        registerTitle(new Title(
                "arcanist_master",
                "Spellweaver",
                "Mastered the Arcanist class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));


        registerTitle(new Title(
                "frostmage_master",
                "Sub-Zero",
                "Mastered the Frostmage class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "Windwaker_master",
                "AirBender",
                "Mastered the Frostmage class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "darkblade_master",
                "Nightwalker",
                "Mastered the Darkblade class",
                TitleCategory.CLASS_MASTERY,
                TitleRarity.EPIC
        ));

        // Add more class mastery titles here

        // Special Titles (examples)
        registerTitle(new Title(
                "alpha_tester",
                "Trailblazer",
                "Participated in alpha testing",
                TitleCategory.SPECIAL,
                TitleRarity.LEGENDARY
        ));

        registerTitle(new Title(
                "pvp_champion",
                "Warlord",
                "Achieved highest PvP rank",
                TitleCategory.ACHIEVEMENT,
                TitleRarity.EPIC
        ));

        registerTitle(new Title(
                "dungeon_master",
                "Delver",
                "Completed all dungeon challenges",
                TitleCategory.ACHIEVEMENT,
                TitleRarity.RARE
        ));
    }

    private void registerTitle(Title title) {
        availableTitles.put(title.getId(), title);
    }

    private void loadPlayerTitles() {
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot load player titles - no database connection");
                return;
            }

            // Load player titles
            try (PreparedStatement stmt = conn.prepareStatement("SELECT player_uuid, title_id FROM player_titles")) {
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    String titleId = rs.getString("title_id");

                    playerTitles.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(titleId);
                }
            }

            // Load active titles
            try (PreparedStatement stmt = conn.prepareStatement("SELECT player_uuid, title_id FROM active_titles")) {
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    String titleId = rs.getString("title_id");

                    activeTitles.put(playerUUID, titleId);
                }
            }

            plugin.getLogger().info("Loaded player titles from database");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load player titles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean awardTitle(UUID playerUUID, String titleId) {
        if (!availableTitles.containsKey(titleId)) {
            return false;
        }

        Set<String> titles = playerTitles.computeIfAbsent(playerUUID, k -> new HashSet<>());
        if (titles.contains(titleId)) {
            return false; // Already has this title
        }

        titles.add(titleId);

        // Save to database
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot save player title - no database connection");
                return false;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO player_titles (player_uuid, title_id) VALUES (?, ?)")) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, titleId);
                stmt.executeUpdate();
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save player title: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean setActiveTitle(UUID playerUUID, String titleId) {
        // Check if player has this title
        Set<String> titles = playerTitles.get(playerUUID);
        if (titles == null || !titles.contains(titleId)) {
            return false;
        }

        // Update active title
        activeTitles.put(playerUUID, titleId);

        // Save to database
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot save active title - no database connection");
                return false;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT OR REPLACE INTO active_titles (player_uuid, title_id) VALUES (?, ?)")) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, titleId);
                stmt.executeUpdate();
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save active title: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public String getFormattedPlayerTitle(Player player) {
        String activeTitle = getActiveTitle(player.getUniqueId());
        if (activeTitle == null || !availableTitles.containsKey(activeTitle)) {
            return ""; // No title
        }

        Title title = availableTitles.get(activeTitle);
        return title.getFormattedTitle();
    }

    public boolean clearActiveTitle(UUID playerUUID) {
        if (!activeTitles.containsKey(playerUUID)) {
            return false;
        }

        activeTitles.remove(playerUUID);

        // Remove from database
        Connection conn = null;
        try {
            conn = plugin.getDatabaseManager().getConnection();
            if (conn == null) {
                plugin.getLogger().severe("Cannot clear active title - no database connection");
                return false;
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM active_titles WHERE player_uuid = ?")) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();
            }

            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear active title: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Set<String> getPlayerTitles(UUID playerUUID) {
        return playerTitles.getOrDefault(playerUUID, Collections.emptySet());
    }

    public String getActiveTitle(UUID playerUUID) {
        return activeTitles.get(playerUUID);
    }

    public Title getTitle(String titleId) {
        return availableTitles.get(titleId);
    }

    public Collection<Title> getAllTitles() {
        return availableTitles.values();
    }

    public String getFormattedDisplayName(Player player) {
        String activeTitle = activeTitles.get(player.getUniqueId());
        if (activeTitle == null || !availableTitles.containsKey(activeTitle)) {
            return player.getDisplayName();
        }

        Title title = availableTitles.get(activeTitle);
        return title.getFormattedTitle() + " " + player.getDisplayName();
    }
}