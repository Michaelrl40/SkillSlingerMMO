package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;

public class ShurikenSkill extends Skill {
    private final double damage;
    private final double bleedDamage;
    private final long bleedDuration;
    private final int bleedTicks;
    private final double projectileSpeed;
    private final double projectileRange;

    public ShurikenSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.bleedDamage = config.getDouble("bleeddamage", 2.0);
        this.bleedDuration = (long)(config.getDouble("bleedduration", 3.0) * 1000); // Convert to milliseconds
        this.bleedTicks = config.getInt("bleedticks", 3);
        this.projectileSpeed = config.getDouble("projectilespeed", 1.0);
        this.projectileRange = config.getDouble("projectilerange", 20.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get initial projectile direction
        Vector direction = player.getLocation().getDirection();
        Location startLocation = player.getEyeLocation();

        // Play throw sound
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 2.0f);

        // Create the projectile
        new BukkitRunnable() {
            private final Vector velocity = direction.multiply(projectileSpeed);
            private Location currentLocation = startLocation.clone();
            private double distanceTraveled = 0;
            private double rotationAngle = 0;

            @Override
            public void run() {
                // Check if we've hit max range
                if (distanceTraveled > projectileRange) {
                    cancel();
                    return;
                }

                // Move the projectile
                currentLocation.add(velocity);
                distanceTraveled += projectileSpeed;
                rotationAngle += Math.PI / 4; // 45 degree rotation each tick

                // Create the shuriken particle effect
                createShurikenParticles(currentLocation, rotationAngle);

                // Check for collisions
                if (checkCollision(currentLocation, player)) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void createShurikenParticles(Location center, double angle) {
        double size = 0.3; // Size of the shuriken

        // Create a simple 4-pointed star shape with grey dust particles
        for (int i = 0; i < 4; i++) {
            double pointAngle = angle + (i * Math.PI / 2);
            double x = Math.cos(pointAngle) * size;
            double z = Math.sin(pointAngle) * size;

            // Center particle
            center.getWorld().spawnParticle(
                    Particle.DUST,
                    center.clone().add(x, 0, z),
                    1,
                    0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(128, 128, 128), 0.5f)
            );
        }
    }

    private boolean checkCollision(Location location, Player caster) {
        // Check for entities in a small radius
        for (LivingEntity entity : location.getWorld().getNearbyLivingEntities(location, 0.5)) {
            // Skip the caster and invalid targets
            if (entity == caster || validateTarget(caster, entity)) {
                continue;
            }

            // Hit effect
            location.getWorld().spawnParticle(
                    Particle.CRIT,
                    location,
                    10, 0.2, 0.2, 0.2, 0.1
            );

            // Play hit sound
            location.getWorld().playSound(
                    location,
                    Sound.BLOCK_ANVIL_LAND,
                    0.5f,
                    2.0f
            );

            // Apply damage and effects
            applyDamage(entity, caster);
            applyBleedEffect(entity, caster);

            // Enter combat if hitting a player
            if (entity instanceof Player) {
                Player targetPlayer = (Player) entity;
                // Put both players in combat with each other
                plugin.getCombatManager().enterCombat(caster, targetPlayer);
                plugin.getCombatManager().enterCombat(targetPlayer, caster);
            }

            return true; // Stop the projectile
        }
        return false;
    }

    private void applyDamage(LivingEntity target, Player caster) {
        // Set damage metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        // Apply the damage
        target.damage(damage);

        // Show the damage number using DamageDisplayManager
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                target.getLocation(),
                damage,
                DamageDisplayManager.DamageType.NORMAL
        );

        // Clean up metadata after a tick
        new BukkitRunnable() {
            @Override
            public void run() {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void applyBleedEffect(LivingEntity target, Player caster) {
        // Create bleed effect runnable
        new BukkitRunnable() {
            int ticksRemaining = bleedTicks;

            @Override
            public void run() {
                if (ticksRemaining <= 0 || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }

                // Apply bleed damage
                target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, bleedDamage));
                target.damage(bleedDamage);

                // Show the bleed damage number
                plugin.getDamageDisplayManager().spawnDamageDisplay(
                        target.getLocation(),
                        bleedDamage,
                        DamageDisplayManager.DamageType.NORMAL  // You could also use a different type for bleeds
                );

                // Show bleed effect
                target.getWorld().spawnParticle(
                        Particle.DUST,
                        target.getLocation().add(0, 1, 0),
                        5, 0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.0f)
                );

                ticksRemaining--;
            }
        }.runTaskTimer(plugin, 20L, 20L); // Apply bleed every second
    }
}