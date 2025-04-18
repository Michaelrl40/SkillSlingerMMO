package com.michael.mmorpg.graveyard;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class GraveyardListener implements Listener {
    private final MinecraftMMORPG plugin;

    public GraveyardListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Save death location
        Player player = event.getEntity();
        player.setMetadata("died_location", new FixedMetadataValue(plugin, player.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Pass both the player and the event
        plugin.getGraveyardManager().respawnAtNearestGraveyard(event.getPlayer(), event);
    }

    // Prevent players from setting spawn points with beds
    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
            // Cancel the bed enter event to prevent setting spawn
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cBed spawning is disabled. You will respawn at the nearest graveyard when you die.");
        }
    }
}