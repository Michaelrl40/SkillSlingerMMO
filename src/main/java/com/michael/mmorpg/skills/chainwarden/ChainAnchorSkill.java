package com.michael.mmorpg.skills.chainwarden;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ChainAnchorSkill extends Skill {
    private final double chainVisualsSpacing;
    private final int maxAnchorDistance;
    private final double pullSpeed;
    private final int maxDurationTicks;
    private final double finalUpBoost;
    private final double raycastStep;

    public ChainAnchorSkill(ConfigurationSection config) {
        super(config);
        this.chainVisualsSpacing = config.getDouble("chainvisualsspacing", 0.5);
        this.maxAnchorDistance = config.getInt("maxanchordistance", 20);
        this.pullSpeed = config.getDouble("pullspeed", 0.6);
        this.maxDurationTicks = config.getInt("maxduration", 60);
        this.finalUpBoost = config.getDouble("finalupboost", 0.8); // Upward boost at end
        this.raycastStep = config.getDouble("raycaststep", 0.25); // Smaller step for more precise targeting
    }

    @Override
    protected void performSkill(Player player) {
        // Try to find an anchor point
        Location targetLoc = player.getEyeLocation();
        Vector direction = targetLoc.getDirection();
        Block anchorBlock = null;
        Location anchorLocation = null;
        boolean foundAnchor = false;

        // More precise raycast
        for (double d = 0; d <= maxAnchorDistance; d += raycastStep) {
            Location checkLoc = targetLoc.clone().add(direction.clone().multiply(d));
            Block block = checkLoc.getBlock();

            // Check block and block above for more lenient targeting
            if (!block.isPassable() || !block.getRelative(0, 1, 0).isPassable()) {
                anchorBlock = block;
                // Place anchor point slightly in front and above the block
                Vector backOff = direction.clone().multiply(-0.3);
                anchorLocation = checkLoc.add(backOff).add(0, 1, 0);
                foundAnchor = true;
                break;
            }
        }

        // If no valid anchor point found, don't set cooldown
        if (!foundAnchor) {
            player.sendMessage("§c✦ No valid anchor point found within range!");
            setSkillSuccess(false);
            return;
        }

        final Location finalAnchorLoc = anchorLocation;
        final World world = player.getWorld();

        // Start the pull effect
        new BukkitRunnable() {
            int ticks = 0;
            boolean hasBoosted = false;

            @Override
            public void run() {
                if (ticks++ >= maxDurationTicks || !player.isOnline()) {
                    cancel();
                    player.sendMessage("§c✦ Chain anchor expires!");
                    return;
                }

                Location playerLoc = player.getLocation().add(0, 1, 0);
                Vector toAnchor = finalAnchorLoc.toVector().subtract(playerLoc.toVector());
                double distance = toAnchor.length();

                // End if we've reached close to the destination
                if (distance < 1.5) {
                    if (!hasBoosted) {
                        // Apply final upward boost
                        Vector finalBoost = new Vector(0, finalUpBoost, 0);
                        player.setVelocity(finalBoost);
                        hasBoosted = true;

                        // Extra feedback for the boost
                        world.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.5f, 1.5f);
                        world.spawnParticle(
                                Particle.CLOUD,
                                player.getLocation(),
                                10, 0.2, 0.1, 0.2, 0.05
                        );
                    }
                    cancel();
                    return;
                }

                // Pull player toward anchor point
                Vector pullDir = toAnchor.normalize().multiply(pullSpeed);
                if (distance < 3.0) {
                    // Slow down as we approach to prevent overshooting
                    pullDir.multiply(distance / 3.0);
                }
                player.setVelocity(pullDir);

                // Draw chain particles
                drawChainParticles(playerLoc, finalAnchorLoc, world);

                // Play chain sound every few ticks
                if (ticks % 4 == 0) {
                    world.playSound(playerLoc, Sound.BLOCK_CHAIN_STEP, 0.3f, 1.2f);
                }
            }

            @Override
            public void cancel() {
                super.cancel();
                broadcastLocalSkillMessage(player, "§c[" + getPlayerClass(player) + "] " +
                        player.getName() + "'s chain breaks free!");
                world.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.2f);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Broadcast activation
        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                player.getName() + " launches a chain anchor!");

        // Play anchor sound
        world.playSound(anchorLocation, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);

        // Visual effect at anchor point
        world.spawnParticle(
                Particle.CRIT,
                anchorLocation,
                15, 0.2, 0.2, 0.2, 0.1
        );

        // Only set cooldown if we successfully anchored
        setSkillSuccess(true);
    }

    private void drawChainParticles(Location start, Location end, World world) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += chainVisualsSpacing) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
        }
    }
}