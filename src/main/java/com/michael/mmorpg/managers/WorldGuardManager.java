package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class WorldGuardManager {
    private final MinecraftMMORPG plugin;
    private boolean worldGuardEnabled = false;


    public WorldGuardManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        try {
            // Check if WorldGuard is available
            Class.forName("com.sk89q.worldguard.WorldGuard");
            worldGuardEnabled = true;
            plugin.getLogger().info("WorldGuard integration enabled for combat protection!");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("WorldGuard not found, combat region protection disabled!");
        }
    }

    /**
     * Checks if combat is allowed at this location based on our flag
     */
    public boolean isCombatAllowed(Location location) {
        if (!worldGuardEnabled) return true; // If WorldGuard isn't available, allow by default

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));

            // Check our custom flag
            StateFlag.State state = set.queryValue(null, MinecraftMMORPG.ALLOW_COMBAT_FLAG);

            // Return true only if flag is explicitly set to ALLOW
            return state == StateFlag.State.ALLOW;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking allow-combat flag: " + e.getMessage());
            return false; // Default to denying combat on error
        }
    }

    /**
     * Checks if combat should be allowed between these entities
     * Considers both the flag value AND combat status
     */
    public boolean canPvP(Entity attacker, Entity target) {
        // If either entity is not a player, it's not PvP
        if (!(attacker instanceof Player) || !(target instanceof Player)) {
            return true;
        }

        Player attackerPlayer = (Player) attacker;
        Player targetPlayer = (Player) target;

        // FIRST CHECK: Are they already in combat with each other?
        // If yes, allow combat regardless of region protection
        if (plugin.getCombatManager().areMutuallyInCombat(attackerPlayer, targetPlayer)) {
            plugin.getLogger().fine("Allowing combat in protected region due to existing combat status");
            return true;
        }

        // SECOND CHECK: If not in combat, check if the region allows combat
        // Combat is only allowed if both players are in regions with allow-combat set to ALLOW
        boolean attackerCanCombat = isCombatAllowed(attacker.getLocation());
        boolean targetCanCombat = isCombatAllowed(target.getLocation());

        // Only allow combat to start if both locations permit it
        return attackerCanCombat && targetCanCombat;
    }

    /**
     * Check if two players are in combat with each other
     * @param player1 First player
     * @param player2 Second player
     * @return true if either player has tagged the other for combat
     */
    private boolean areInCombat(Player player1, Player player2) {
        // First check if the combat manager exists
        CombatManager combatManager = plugin.getCombatManager();
        if (combatManager == null) {
            return false;
        }

        // Check if either player has the other in their combat targets
        return combatManager.hasTarget(player1, player2) ||
                combatManager.hasTarget(player2, player1);
    }

    /**
     * Legacy method that keeps compatibility with old code
     * Now just redirects to our custom combat flag check
     */
    public boolean isPvPAllowed(Location location) {
        return isCombatAllowed(location);
    }
}