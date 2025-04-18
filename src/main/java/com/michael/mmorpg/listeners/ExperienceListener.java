package com.michael.mmorpg.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.entity.EntitySpawnEvent;
import com.michael.mmorpg.MinecraftMMORPG;

public class ExperienceListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ExperienceListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof ExperienceOrb) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Set dropped exp to 0
        event.setDroppedExp(0);
    }

    @EventHandler
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        // Cancel vanilla exp changes
        event.setAmount(0);
    }
}