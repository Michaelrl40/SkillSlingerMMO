package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.druid.RebirthSkill;
import com.michael.mmorpg.status.CCType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/**
 * Unified player event listener that handles all player-related events in the MMORPG system.
 * This includes player joining/leaving, combat events, movement, and status effect handling.
 */
public class PlayerListener implements Listener {
    private final MinecraftMMORPG plugin;

    public PlayerListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("Loading data for player: " + player.getName());

        // Load player data
        plugin.getPlayerManager().loadPlayer(player);

        // Get player data and update health/stats
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData != null && playerData.getGameClass() != null) {
            GameClass gameClass = playerData.getGameClass();

            // Set max health based on the player's class/level regardless
            player.setMaxHealth(gameClass.getMaxHealth(playerData.getLevel()));
            player.setHealth(player.getMaxHealth());

        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("Saving data for player: " + player.getName());

        // Handle party-related logic
        Party party = plugin.getPartyManager().getParty(player);
        if (party != null) {
            if (party.getLeader().equals(player)) {
                party.handleLeaderDisconnect(player);
            } else {
                party.handleMemberDisconnect(player);
            }
        }

        // Save and clean up player data
        plugin.getPlayerManager().savePlayer(player);
        plugin.getPlayerManager().removePlayer(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        System.out.println("Player died: " + player.getName());  // Debug

        // Track death for Rebirth skill
        RebirthSkill.trackDeath(player);

        // Remove Citrus Forge items from drops
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (plugin.getCitrusForgeManager().getFruitId(item) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData != null && playerData.getGameClass() != null) {
            GameClass gameClass = playerData.getGameClass();
            player.setMaxHealth(gameClass.getMaxHealth(playerData.getLevel()));
            player.setHealth(player.getMaxHealth());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check for movement-restricting status effects
        if (plugin.getStatusEffectManager().hasEffect(player, CCType.ROOT) ||
                plugin.getStatusEffectManager().hasEffect(player, CCType.STUN) ||
                plugin.getStatusEffectManager().hasEffect(player, CCType.SLEEP) ||
                player.hasMetadata("rooted")) {  // Check for Thunderstorm root
            event.setCancelled(true);
            return;
        }

        // Process any active movement-affecting status effects
        plugin.getStatusEffectManager().handleMovementEffects(player);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            plugin.getStatusEffectManager().onDamageBreakEffects(player);
        }
    }
}