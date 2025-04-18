package com.michael.mmorpg.graveyard;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldLoadListener implements Listener {
    private final MinecraftMMORPG plugin;

    public WorldLoadListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();

        // Load any pending graveyards for this world
        plugin.getGraveyardManager().onWorldLoad(world);

        // If this is a dungeon world, ensure it has a graveyard
        if (world.getName().startsWith("dungeon_")) {
            plugin.getLogger().info("Dungeon world loaded: " + world.getName());

            // Check if a graveyard exists in this world
            if (plugin.getGraveyardManager().findGraveyardInWorld(world) == null) {
                // Create a graveyard if none exists
                String graveyardName = world.getName() + "_Respawn";
                plugin.getGraveyardManager().createGraveyard(graveyardName, world.getSpawnLocation());
                plugin.getLogger().info("Created default graveyard for dungeon world: " + world.getName());
            }
        }
    }
}