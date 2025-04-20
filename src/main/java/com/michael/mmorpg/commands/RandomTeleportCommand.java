package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class RandomTeleportCommand implements CommandExecutor {

    private final MinecraftMMORPG plugin;
    private final Random random = new Random();
    private final int worldBorder = 10000; // 10k in each direction (20k x 20k world)
    private final int MAX_ATTEMPTS = 25; // Maximum attempts to find a valid location

    public RandomTeleportCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check permissions
        if (!player.hasPermission("mmorpg.randomtp")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Finding a random location to teleport you to...");

        // Apply blindness and slowness effects to the player (3 seconds = 60 ticks)
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, true)); // Slowness 4 (level is 0-based)

        // Use async task for finding a safe location to prevent server lag
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Switch back to main thread for world operations
            Bukkit.getScheduler().runTask(plugin, () -> {
                World world = player.getWorld();

                // Try to find a valid location
                Location destination = findValidLocation(player, world);

                if (destination == null) {
                    player.sendMessage(ChatColor.RED + "Failed to find a safe location after multiple attempts. Please try again.");
                    return;
                }

                // Ensure chunks are loaded before teleporting
                world.getChunkAt(destination).load(true);

                // Teleport the player
                player.teleport(destination);
                player.sendMessage(ChatColor.GREEN + "You have been teleported to a random location!");
            });
        });

        return true;
    }

    /**
     * Finds a valid random location that is:
     * 1. Within the world border
     * 2. At surface level
     * 3. Not in a WorldGuard region
     *
     * @param player The player to teleport
     * @param world The world to search in
     * @return A valid location, or null if none found after MAX_ATTEMPTS
     */
    private Location findValidLocation(Player player, World world) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(world));

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // Generate random coordinates within world border
            int x = random.nextInt(worldBorder * 2) - worldBorder;
            int z = random.nextInt(worldBorder * 2) - worldBorder;

            // Find a safe Y coordinate (surface level)
            int y = findSafeY(world, x, z);

            if (y == -1) {
                continue; // No safe Y found, try again
            }

            Location loc = new Location(world, x + 0.5, y, z + 0.5);

            // Check if the location is in a WorldGuard region
            if (regions != null) {
                ApplicableRegionSet set = regions.getApplicableRegions(
                        BukkitAdapter.asBlockVector(loc));

                if (!set.getRegions().isEmpty()) {
                    // Location is in a protected region, try again
                    continue;
                }
            }

            // Set yaw and pitch to current values to avoid disorientation
            loc.setYaw(player.getLocation().getYaw());
            loc.setPitch(player.getLocation().getPitch());

            return loc;
        }

        return null; // Could not find valid location after MAX_ATTEMPTS
    }

    /**
     * Finds a safe Y coordinate at the surface level.
     *
     * @param world The world to search in
     * @param x The x coordinate
     * @param z The z coordinate
     * @return The safe y coordinate, or -1 if none found
     */
    private int findSafeY(World world, int x, int z) {
        // Try to load the chunk
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4);
        }

        // Start from the top of the world and work down to find the surface
        for (int y = world.getMaxHeight() - 1; y > 0; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block blockAbove = world.getBlockAt(x, y + 1, z);
            Block blockBelow = world.getBlockAt(x, y - 1, z);

            // Check if this is a safe place to stand (solid block below, air above)
            if (!blockAbove.getType().isSolid() &&
                    !block.getType().isSolid() &&
                    blockBelow.getType().isSolid() &&
                    !blockBelow.getType().toString().contains("LAVA") &&
                    !blockBelow.getType().toString().contains("WATER") &&
                    !blockBelow.getType().toString().contains("FIRE") &&
                    !blockBelow.getType().toString().contains("CACTUS")) {

                // Make sure we're not in a tree or other tall structure
                boolean clearAbove = true;
                for (int i = 1; i <= 2; i++) {
                    if (world.getBlockAt(x, y + i, z).getType().isSolid()) {
                        clearAbove = false;
                        break;
                    }
                }

                if (clearAbove) {
                    return y;
                }
            }
        }

        return -1;  // No safe location found
    }
}