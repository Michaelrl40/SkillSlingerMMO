package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CastingBarManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, CastingData> activeCasts = new HashMap<>();
    private static final String TEAM_PREFIX = "cast_";

    private static class CastingData {
        final String skillName;
        final long startTime;
        final double duration;
        Team castingTeam;

        CastingData(String skillName, double duration) {
            this.skillName = skillName;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
        }
    }

    public CastingBarManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        cleanupAllCastingTeams(); // Clean up any leftover teams when plugin starts
        startUpdateTask();
    }

    /**
     * Cleans up all casting-related teams from the scoreboard
     */
    public void cleanupAllCastingTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Create a copy of team names to avoid ConcurrentModificationException
        java.util.Set<Team> teams = scoreboard.getTeams();
        java.util.List<String> teamNames = new java.util.ArrayList<>();

        for (Team team : teams) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                teamNames.add(team.getName());
            }
        }

        // Now unregister the teams by name
        for (String teamName : teamNames) {
            try {
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    plugin.getLogger().info("Cleaning up leftover casting team: " + teamName);
                    team.unregister();
                }
            } catch (IllegalStateException e) {
                // Team might already be unregistered
            }
        }
    }

    /**
     * Cleans up casting teams for a specific player
     */
    public void cleanupPlayerCastingTeams(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String playerTeamPrefix = TEAM_PREFIX + player.getUniqueId().toString().substring(0, 8);

        // Create a copy of team names to avoid ConcurrentModificationException
        java.util.Set<Team> teams = scoreboard.getTeams();
        java.util.List<String> teamNames = new java.util.ArrayList<>();
        java.util.List<Team> teamsWithPlayer = new java.util.ArrayList<>();

        for (Team team : teams) {
            if (team.getName().startsWith(playerTeamPrefix)) {
                teamNames.add(team.getName());
            }

            // Also identify teams that have this player as a member
            if (team.getName().startsWith(TEAM_PREFIX) && team.getEntries().contains(player.getName())) {
                teamsWithPlayer.add(team);
            }
        }

        // Unregister teams by name
        for (String teamName : teamNames) {
            try {
                Team team = scoreboard.getTeam(teamName);
                if (team != null) {
                    plugin.getLogger().info("Cleaning up player casting team: " + teamName);
                    team.unregister();
                }
            } catch (IllegalStateException e) {
                // Team might already be unregistered
            }
        }

        // Remove player from other teams
        for (Team team : teamsWithPlayer) {
            team.removeEntry(player.getName());
        }
    }

    public void startCasting(Player player, String skillName, double castTime) {
        UUID playerId = player.getUniqueId();

        // First cleanup any existing casting teams for this player
        cleanupPlayerCastingTeams(player);

        CastingData castData = new CastingData(skillName, castTime);
        activeCasts.put(playerId, castData);

        // Create or update casting team for this player
        String teamName = TEAM_PREFIX + playerId.toString().substring(0, 8);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(teamName);

        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setDisplayName("§6Casting...");
        team.setPrefix("§6[Casting] ");
        team.addEntry(player.getName());
        castData.castingTeam = team;

        // Update casting bar immediately
        updateCastingBar(player, castData);
    }

    public void stopCasting(Player player) {
        UUID playerId = player.getUniqueId();
        CastingData castData = activeCasts.remove(playerId);

        if (castData != null && castData.castingTeam != null) {
            // Clean up team
            castData.castingTeam.removeEntry(player.getName());

            // Unregister the team
            try {
                castData.castingTeam.unregister();
            } catch (IllegalStateException e) {
                // Team might already be unregistered
            }

            // Reset player nameplate
            player.setCustomName(player.getName());
            player.setCustomNameVisible(false);
        }
    }

    private void updateCastingBar(Player player, CastingData castData) {
        if (!player.isOnline()) {
            stopCasting(player);
            return;
        }

        long elapsedTime = System.currentTimeMillis() - castData.startTime;
        double percentComplete = Math.min(1.0, elapsedTime / (castData.duration * 1000));

        // Generate progress bar
        int barLength = 20; // Total length of the bar
        int filledBars = (int) (percentComplete * barLength);
        StringBuilder bar = new StringBuilder();

        bar.append("§6").append(castData.skillName).append(" ");
        bar.append("§8[");
        bar.append("§a");
        for (int i = 0; i < filledBars; i++) {
            bar.append("█"); // Filled segment
        }
        bar.append("§7");
        for (int i = filledBars; i < barLength; i++) {
            bar.append("█"); // Empty segment
        }
        bar.append("§8]");

        // Calculate time remaining
        double secondsRemaining = Math.max(0, castData.duration - (elapsedTime / 1000.0));
        bar.append(String.format(" §e%.1fs", secondsRemaining));

        // Update player nameplate
        player.setCustomName(bar.toString());
        player.setCustomNameVisible(true);

        // Update for all nearby players
        for (Player nearbyPlayer : Bukkit.getOnlinePlayers()) {
            nearbyPlayer.hidePlayer(plugin, player);
            nearbyPlayer.showPlayer(plugin, player);
        }
    }

    private void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, CastingData> entry : new HashMap<>(activeCasts).entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                CastingData castData = entry.getValue();

                if (player == null || !player.isOnline()) {
                    activeCasts.remove(entry.getKey());
                    continue;
                }

                long elapsedTime = System.currentTimeMillis() - castData.startTime;
                if (elapsedTime >= castData.duration * 1000) {
                    // Cast completed
                    stopCasting(player);
                } else {
                    // Update the casting bar
                    updateCastingBar(player, castData);
                }
            }
        }, 0L, 2L); // Update every 2 ticks (10 times per second)
    }
}