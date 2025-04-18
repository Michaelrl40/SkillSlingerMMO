package com.michael.mmorpg.skills.frostmage;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.FluidCollisionMode;
import com.michael.mmorpg.skills.Skill;

public class IceBarrageSkill extends Skill {
    // Core skill properties
    private final double damage;
    private final double range;
    private final double projectileSpeed;
    private final double trackingStrength;
    private final int maxDuration;
    private final int projectileCount;
    private final long fireDelay;
    private LivingEntity target;

    public IceBarrageSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 10.0);
        this.range = config.getDouble("range", 20.0);
        this.projectileSpeed = config.getDouble("projectilespeed", 0.8);
        this.trackingStrength = config.getDouble("trackingstrength", 0.15);
        this.maxDuration = config.getInt("maxduration", 100);
        this.projectileCount = config.getInt("projectilecount", 5);
        this.fireDelay = Math.round(config.getDouble("firedelay", 0.5) * 20); // Convert seconds to ticks
    }

    @Override
    public void execute(Player player) {
        // Get target using base targeting system
        target = getTargetEntity(player, range);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            return;
        }

        // Store target for validation
        currentTarget = target;

        // Start the cast
        startCasting(player);
    }

    @Override
    protected void performSkill(Player player) {
        if (target == null || !target.isValid() || target.isDead()) {
            player.sendMessage("§c✦ Target is no longer valid!");
            return;
        }

        // Launch sequence of projectiles
        launchProjectileSequence(player, target);
        setSkillSuccess(true);

        // Play cast sound and effects
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 2.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0),
                15, 0.3, 0.3, 0.3, 0.1);
    }

    private void launchProjectileSequence(Player caster, LivingEntity initialTarget) {
        new BukkitRunnable() {
            int projectilesLaunched = 0;

            @Override
            public void run() {
                if (projectilesLaunched >= projectileCount) {
                    cancel();
                    return;
                }

                if (initialTarget.isValid() && !initialTarget.isDead()) {
                    launchSingleProjectile(caster, initialTarget);
                    projectilesLaunched++;

                    // Play incremental pitch sound for each projectile
                    caster.getWorld().playSound(caster.getLocation(),
                            Sound.BLOCK_GLASS_BREAK, 0.5f, 1.5f + (projectilesLaunched * 0.1f));
                } else {
                    cancel();
                }
            }
        }.runTaskTimer(getPlugin(), 0L, fireDelay);
    }

    private void launchSingleProjectile(Player caster, LivingEntity initialTarget) {
        Location projectileLocation = caster.getEyeLocation();

        // Add spread to initial direction
        Vector direction = initialTarget.getLocation().add(0, 0.5, 0)
                .subtract(projectileLocation).toVector().normalize();
        direction.add(new Vector(
                (Math.random() - 0.5) * 0.2,
                (Math.random() - 0.5) * 0.2,
                (Math.random() - 0.5) * 0.2
        )).normalize();

        new BukkitRunnable() {
            private int ticksLived = 0;
            private Vector currentVelocity = direction.multiply(projectileSpeed);
            private final double COLLISION_DISTANCE = 1.0;

            @Override
            public void run() {
                if (ticksLived++ >= maxDuration || !initialTarget.isValid()) {
                    createIceExplosion(projectileLocation);
                    cancel();
                    return;
                }

                projectileLocation.add(currentVelocity);

                // Update tracking
                Vector toTarget = initialTarget.getLocation().add(0, 0.5, 0)
                        .subtract(projectileLocation).toVector().normalize();
                currentVelocity = currentVelocity.multiply(1 - trackingStrength)
                        .add(toTarget.multiply(trackingStrength))
                        .normalize().multiply(projectileSpeed);

                // Check collision
                if (projectileLocation.distance(initialTarget.getLocation()) < COLLISION_DISTANCE) {
                    hitTarget(caster, initialTarget, projectileLocation);
                    cancel();
                    return;
                }

                createProjectileEffects(projectileLocation);

                // Check for block collision
                if (!projectileLocation.getBlock().isPassable()) {
                    createIceExplosion(projectileLocation);
                    cancel();
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void createProjectileEffects(Location location) {
        World world = location.getWorld();
        // Ice shard trail effect
        world.spawnParticle(Particle.SNOWFLAKE, location, 2, 0.1, 0.1, 0.1, 0);
        world.spawnParticle(Particle.ITEM_SNOWBALL, location, 1, 0.1, 0.1, 0.1, 0.05);
        // Ice crystal trail
        world.spawnParticle(Particle.END_ROD, location, 1, 0, 0, 0, 0.02);
    }

    private void createIceExplosion(Location location) {
        World world = location.getWorld();
        // Use only visual particles, no block particles
        world.spawnParticle(Particle.SNOWFLAKE, location, 25, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticle(Particle.ITEM_SNOWBALL, location, 20, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticle(Particle.END_ROD, location, 10, 0.2, 0.2, 0.2, 0.05);
        world.spawnParticle(Particle.CLOUD, location, 5, 0.2, 0.2, 0.2, 0);

        // Ice explosion sounds
        world.playSound(location, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.0f);
        world.playSound(location, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5f, 1.2f);
    }

    private void hitTarget(Player caster, LivingEntity target, Location hitLocation) {
        // Apply damage with proper metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), damage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        target.damage(damage, caster);
        // Remove cleanup - let CombatListener handle it

        createIceExplosion(hitLocation);

        // Only broadcast the message for the first hit to avoid spam
        if (!target.hasMetadata("ice_barrage_hit")) {
            target.setMetadata("ice_barrage_hit", new FixedMetadataValue(getPlugin(), true));

            // Remove this metadata after a short delay (this is fine to keep since it's not damage-related)
            new BukkitRunnable() {
                @Override
                public void run() {
                    target.removeMetadata("ice_barrage_hit", getPlugin());
                }
            }.runTaskLater(getPlugin(), 5L);
        }
    }
}
