package com.michael.mmorpg.skills.elementalranger;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class GrandTreeSkill extends Skill {
    private final int treeHeight;
    private final double radius;
    private final int trunkRadius;
    private static final Map<UUID, List<Location>> activeTreeBlocks = new HashMap<>();

    public GrandTreeSkill(ConfigurationSection config) {
        super(config);
        this.treeHeight = config.getInt("treeheight", 20);
        this.radius = config.getDouble("radius", 4.0);
        this.trunkRadius = config.getInt("trunkradius", 2);
    }

    @Override
    protected void performSkill(Player player) {
        // Cleanup any existing tree for this player
        removeExistingTree(player);

        // Get target block
        Block targetBlock = player.getTargetBlock(null, 30);
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            player.sendMessage("§c✦ You must target a solid block!");
            setSkillSuccess(false);
            return;
        }

        Location baseLocation = targetBlock.getLocation().add(0, 1, 0);
        World world = baseLocation.getWorld();

        // Create list to track blocks for this tree
        List<Location> treeBlocks = new ArrayList<>();
        activeTreeBlocks.put(player.getUniqueId(), treeBlocks);

        // Build tree instantly
        for (int y = 0; y < treeHeight; y++) {
            // Create trunk
            for (int x = -trunkRadius; x <= trunkRadius; x++) {
                for (int z = -trunkRadius; z <= trunkRadius; z++) {
                    if (Math.sqrt(x*x + z*z) <= trunkRadius) {
                        Location blockLoc = baseLocation.clone().add(x, y, z);
                        Block block = blockLoc.getBlock();
                        if (block.getType() == Material.AIR) {
                            block.setType(Material.OAK_LOG);
                            treeBlocks.add(blockLoc);

                            // Growth particle effect
                            world.spawnParticle(
                                    Particle.HAPPY_VILLAGER,
                                    blockLoc.clone().add(0.5, 0.5, 0.5),
                                    1, 0.1, 0.1, 0.1, 0
                            );
                        }
                    }
                }
            }
        }

        // Create platform at top
        int platformY = treeHeight;
        int platformRadius = trunkRadius + 2;
        for (int x = -platformRadius; x <= platformRadius; x++) {
            for (int z = -platformRadius; z <= platformRadius; z++) {
                if (Math.sqrt(x*x + z*z) <= platformRadius) {
                    Location platformLoc = baseLocation.clone().add(x, platformY, z);
                    Block block = platformLoc.getBlock();
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.OAK_LEAVES);
                        treeBlocks.add(platformLoc);
                    }
                }
            }
        }

        // Teleport nearby players
        world.getNearbyEntities(baseLocation, radius, 2, radius).forEach(entity -> {
            if (entity instanceof Player) {
                Player target = (Player) entity;
                Location destination = target.getLocation().clone();
                destination.setY(baseLocation.getY() + treeHeight + 1);
                target.teleport(destination);
                target.sendMessage("§a✦ You've been lifted by the Grand Tree!");
            }
        });

        // Schedule tree removal
        new BukkitRunnable() {
            @Override
            public void run() {
                removeExistingTree(player);
            }
        }.runTaskLater(plugin, 20L * 30); // Remove after 30 seconds

        setSkillSuccess(true);
    }

    private void removeExistingTree(Player player) {
        List<Location> existingBlocks = activeTreeBlocks.get(player.getUniqueId());
        if (existingBlocks != null) {
            for (Location loc : existingBlocks) {
                Block block = loc.getBlock();
                if (block.getType() == Material.OAK_LOG || block.getType() == Material.OAK_LEAVES) {
                    block.setType(Material.AIR);

                    // Decay particle effect
                    block.getWorld().spawnParticle(
                            Particle.BLOCK_MARKER,
                            loc.clone().add(0.5, 0.5, 0.5),
                            5, 0.2, 0.2, 0.2, 0,
                            block.getBlockData()
                    );
                }
            }
            activeTreeBlocks.remove(player.getUniqueId());
        }
    }

    // Static cleanup method for plugin disable
    public static void cleanupAllTrees() {
        activeTreeBlocks.forEach((uuid, locations) -> {
            locations.forEach(loc -> {
                Block block = loc.getBlock();
                if (block.getType() == Material.OAK_LOG || block.getType() == Material.OAK_LEAVES) {
                    block.setType(Material.AIR);
                }
            });
        });
        activeTreeBlocks.clear();
    }
}