package com.michael.mmorpg.skills.frostmage;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.block.data.BlockData;

public class IceSpikeSkill extends Skill {
    private final double damage;
    private final double range;
    private final double slowDuration;
    private final double impactRadius;

    public IceSpikeSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.range = config.getDouble("range", 15.0);
        this.slowDuration = config.getDouble("slowduration", 5.0);
        this.impactRadius = config.getDouble("impactradius", 3.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Find the target location - where player is looking at the ground
        Location targetLocation = getTargetLocation(player);

        // Create the particle-based ice spike from the sky
        createParticleIceSpike(player, targetLocation);

        // Play casting sound and effect at player's position
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.05);

        // Broadcast the skill usage
        broadcastLocalSkillMessage(player, "§b[" + getPlayerClass(player) + "] " + player.getName() + " summons an Ice Spike from the sky!");

        setSkillSuccess(true);
    }

    private Location getTargetLocation(Player player) {
        // Try to find a target block in range
        Block targetBlock = player.getTargetBlock(null, (int) range);
        Location targetLocation;

        if (targetBlock != null && !targetBlock.getType().isAir()) {
            // If looking at a block, target the space above it
            targetLocation = targetBlock.getLocation().add(0.5, 1, 0.5);
        } else {
            // If not looking at a block, project to max range
            targetLocation = player.getEyeLocation().add(
                    player.getLocation().getDirection().multiply(range));

            // Find the ground along the ray
            Location groundLocation = findGround(player.getEyeLocation(), targetLocation);
            if (groundLocation != null) {
                targetLocation = groundLocation.add(0.5, 1, 0.5);
            }
        }

        return targetLocation;
    }

    private Location findGround(Location start, Location end) {
        World world = start.getWorld();
        Vector direction = end.toVector().subtract(start.toVector()).normalize();
        double distance = start.distance(end);

        // Start a bit in front of the player to avoid hitting themselves
        Location current = start.clone().add(direction.clone().multiply(2));

        for (double d = 2; d <= distance; d += 0.5) {
            current = start.clone().add(direction.clone().multiply(d));

            // Check if we hit a block or reached the ground
            Block block = world.getBlockAt(current);
            Block blockBelow = world.getBlockAt(current.clone().add(0, -1, 0));

            if (!block.getType().isAir() ||
                    (block.getType().isAir() && !blockBelow.getType().isAir())) {
                return block.getLocation();
            }
        }

        // If no ground found, use the end location
        return end;
    }

    private void createParticleIceSpike(Player player, Location targetLocation) {
        World world = targetLocation.getWorld();

        // Create a spawn location high above the target
        final Location spawnLocation = targetLocation.clone().add(0, 20, 0);
        final Location impactLocation = targetLocation.clone();

        // Duration for the ice spike to fall
        final int travelDuration = 20; // 1 second (20 ticks)

        // Distance between particles in the spike column
        final double particleSpacing = 0.5;

        // Create the falling ice spike effect using only particles
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= travelDuration) {
                    // When animation completes, create impact effects
                    handleImpact(player, impactLocation);
                    this.cancel();
                    return;
                }

                // Calculate current height progress (0.0 to 1.0)
                double progress = (double) tick / travelDuration;

                // Current position - linear interpolation from spawn to impact
                double currentHeight = spawnLocation.getY() - (spawnLocation.getY() - impactLocation.getY()) * progress;

                // Create a column of particles from top to current position
                for (double y = currentHeight; y <= spawnLocation.getY(); y += particleSpacing) {
                    Location particleLocation = new Location(
                            world,
                            spawnLocation.getX(),
                            y,
                            spawnLocation.getZ());

                    // Main ice spike particles
                    world.spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            particleLocation,
                            1, 0.1, 0, 0.1, 0,
                            Material.ICE.createBlockData());

                    // Additional snow particles
                    if (Math.random() < 0.3) {
                        world.spawnParticle(
                                Particle.SNOWFLAKE,
                                particleLocation.clone().add((Math.random() - 0.5) * 0.5, 0, (Math.random() - 0.5) * 0.5),
                                1, 0.05, 0.05, 0.05, 0.01);
                    }
                }

                // Create a slightly thicker "tip" at the bottom
                Location tipLocation = new Location(
                        world,
                        spawnLocation.getX(),
                        currentHeight,
                        spawnLocation.getZ());

                for (int i = 0; i < 5; i++) {
                    double offsetX = (Math.random() - 0.5) * 0.3;
                    double offsetZ = (Math.random() - 0.5) * 0.3;

                    world.spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            tipLocation.clone().add(offsetX, 0, offsetZ),
                            2, 0.05, 0.1, 0.05, 0,
                            Material.PACKED_ICE.createBlockData());
                }

                // Sound effect for the falling spike
                if (tick % 5 == 0) {
                    world.playSound(
                            tipLocation,
                            Sound.BLOCK_GLASS_BREAK,
                            0.2f,
                            2.0f);
                }

                tick++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void handleImpact(Player player, Location impactLocation) {
        World world = impactLocation.getWorld();
        Party casterParty = getPlugin().getPartyManager().getParty(player);

        // Create impact visuals
        createImpactEffect(impactLocation);

        // Apply damage to nearby entities
        for (Entity entity : world.getNearbyEntities(impactLocation, impactRadius, impactRadius, impactRadius)) {
            if (entity instanceof LivingEntity target && !entity.equals(player)) {
                // Skip party members if player is in a party
                if (entity instanceof Player targetPlayer) {
                    if (casterParty != null && casterParty.isMember(targetPlayer)) {
                        continue;
                    }
                }

                // Calculate distance-based damage scaling
                double distance = entity.getLocation().distance(impactLocation);
                double scaledDamage = damage * (1 - (distance / impactRadius) * 0.7); // At least 30% damage at max radius

                if (scaledDamage <= 0) continue;

                // Apply damage and effects
                target.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), player));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), scaledDamage));
                target.setMetadata("magic_damage", new FixedMetadataValue(getPlugin(), true));

                // Use damage event system for proper combat tracking
                target.damage(0.1, player);

                // Apply slow effect
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        (int)(slowDuration * 20),
                        1, false, true, true));

                // Apply freezing effect on hit entities
                world.spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0),
                        15, 0.3, 0.5, 0.3, 0.05);
                world.spawnParticle(Particle.BLOCK_CRUMBLE, target.getLocation().add(0, 1, 0),
                        10, 0.3, 0.5, 0.3, 0, Material.ICE.createBlockData());

                // Clean up metadata
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        target.removeMetadata("skill_damage", getPlugin());
                        target.removeMetadata("skill_damage_amount", getPlugin());
                        target.removeMetadata("magic_damage", getPlugin());
                    }
                }.runTaskLater(getPlugin(), 1L);
            }
        }

        // Create temporary ice effect on the ground
        createTemporaryIceEffect(impactLocation, 60); // 3 seconds
    }

    private void createImpactEffect(Location location) {
        World world = location.getWorld();

        // Ground frost effect
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            for (double radius = 0.5; radius <= impactRadius; radius += 0.5) {
                double x = location.getX() + (radius * Math.cos(angle));
                double z = location.getZ() + (radius * Math.sin(angle));
                Location frostLoc = new Location(world, x, location.getY() + 0.1, z);
                world.spawnParticle(Particle.ITEM_SNOWBALL, frostLoc, 1, 0.1, 0, 0.1, 0);
                world.spawnParticle(Particle.BLOCK_CRUMBLE, frostLoc, 2, 0.2, 0, 0.2, 0, Material.ICE.createBlockData());
            }
        }

        // Create ice spike shattering effect
        for (int i = 0; i < 30; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = Math.random() * 2;
            double offsetZ = (Math.random() - 0.5) * 2;

            world.spawnParticle(
                    Particle.BLOCK_CRUMBLE,
                    location.clone().add(offsetX, offsetY, offsetZ),
                    3, 0.1, 0.1, 0.1, 0.1,
                    Material.PACKED_ICE.createBlockData());
        }

        // Impact explosion
        world.spawnParticle(Particle.EXPLOSION, location, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.SNOWFLAKE, location, 30, 1.5, 0.5, 1.5, 0.1);
        world.spawnParticle(Particle.BLOCK, location, 40, 1.5, 0.5, 1.5, 0.1, Material.ICE.createBlockData());

        // Sound effects
        world.playSound(location, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.8f);
        world.playSound(location, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);
        world.playSound(location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.5f);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
    }

    private void createTemporaryIceEffect(Location center, int durationTicks) {
        World world = center.getWorld();
        int radius = (int) Math.ceil(impactRadius);

        // Store original blocks
        final Block centerBlock = world.getBlockAt(center);

        // Spawn ice pattern particles instead of changing blocks
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x*x + z*z <= radius*radius) {
                    Location particleLoc = centerBlock.getLocation().clone().add(x, 0.1, z);

                    // Frost particle at this location that lasts for the duration
                    new BukkitRunnable() {
                        int tick = 0;

                        @Override
                        public void run() {
                            if (tick >= durationTicks) {
                                this.cancel();
                                return;
                            }

                            if (tick % 10 == 0) {
                                world.spawnParticle(Particle.BLOCK_CRUMBLE,
                                        particleLoc.clone().add(0.5, 0, 0.5),
                                        1, 0.3, 0, 0.3, 0, Material.ICE.createBlockData());
                            }

                            tick++;
                        }
                    }.runTaskTimer(getPlugin(), 0L, 1L);
                }
            }
        }
    }
}