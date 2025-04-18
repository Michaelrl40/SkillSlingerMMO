package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.Particle;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.List;

public class ClearPortalsCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public ClearPortalsCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permissions
        if (!sender.hasPermission("mmorpg.admin.clearportals")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Parse arguments
        boolean radius = false;
        int radiusSize = 100;
        boolean silent = false;
        World targetWorld = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (arg.equals("-r") || arg.equals("-radius")) {
                radius = true;
                if (i + 1 < args.length) {
                    try {
                        radiusSize = Integer.parseInt(args[i + 1]);
                        i++; // Skip the next argument
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid radius value. Using default: 100 blocks.");
                    }
                }
            } else if (arg.equals("-s") || arg.equals("-silent")) {
                silent = true;
            } else if (arg.equals("-w") || arg.equals("-world")) {
                if (i + 1 < args.length) {
                    targetWorld = Bukkit.getWorld(args[i + 1]);
                    if (targetWorld == null) {
                        sender.sendMessage(ChatColor.RED + "World '" + args[i + 1] + "' not found.");
                        return true;
                    }
                    i++; // Skip the next argument
                }
            }
        }

        // Execute the portal cleanup
        if (radius && sender instanceof Player) {
            // Clean portals in radius around player
            Player player = (Player) sender;
            int removedCount = clearPortalsInRadius(player, radiusSize, silent);
            sender.sendMessage(ChatColor.GREEN + "Removed " + removedCount +
                    " portals within " + radiusSize + " blocks of your location.");
        } else {
            // Clean all portals in specified world or all worlds
            int removedCount = clearAllPortals(targetWorld, silent);
            if (targetWorld != null) {
                sender.sendMessage(ChatColor.GREEN + "Removed " + removedCount +
                        " portals from world '" + targetWorld.getName() + "'.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Removed " + removedCount +
                        " portals from all worlds.");
            }
        }

        return true;
    }

    private int clearPortalsInRadius(Player player, int radius, boolean silent) {
        int removedCount = 0;
        World world = player.getWorld();

        // Get all entities within radius
        for (Entity entity : world.getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (isPortalEntity(entity)) {
                removePortal(entity, silent);
                removedCount++;
            }
        }

        return removedCount;
    }

    private int clearAllPortals(World targetWorld, boolean silent) {
        int removedCount = 0;
        List<World> worldsToCheck = new ArrayList<>();

        if (targetWorld != null) {
            worldsToCheck.add(targetWorld);
        } else {
            worldsToCheck.addAll(Bukkit.getWorlds());
        }

        for (World world : worldsToCheck) {
            // Check only loaded chunks to avoid performance issues
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (isPortalEntity(entity)) {
                        removePortal(entity, silent);
                        removedCount++;
                    }
                }
            }
        }

        return removedCount;
    }

    private boolean isPortalEntity(Entity entity) {
        return entity instanceof ArmorStand &&
                (entity.hasMetadata("spawn_portal") ||
                        (entity.getCustomName() != null &&
                                entity.getCustomName().contains("SPAWN PORTAL")));
    }

    private void removePortal(Entity entity, boolean silent) {
        // Visual and sound effects if not silent
        if (!silent) {
            entity.getWorld().spawnParticle(
                    Particle.PORTAL,
                    entity.getLocation().add(0, 1, 0),
                    50, 0.5, 1, 0.5, 0.1
            );
            entity.getWorld().playSound(
                    entity.getLocation(),
                    Sound.BLOCK_PORTAL_TRAVEL,
                    0.5f,
                    2.0f
            );
        }

        // Remove the entity
        entity.remove();

        // Log to console
        plugin.getLogger().info("Removed portal at " + formatLocation(entity.getLocation()));
    }

    private String formatLocation(org.bukkit.Location loc) {
        return loc.getWorld().getName() + " [" +
                loc.getBlockX() + ", " +
                loc.getBlockY() + ", " +
                loc.getBlockZ() + "]";
    }
}