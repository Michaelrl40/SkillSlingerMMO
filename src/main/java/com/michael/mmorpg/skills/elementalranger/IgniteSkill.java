package com.michael.mmorpg.skills.elementalranger;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class IgniteSkill extends Skill {
    private final double radius;
    private final int fireDuration;
    private final double knockbackStrength;
    private final double initialDamage;
    private final double burnDamage;
    private final int burnTicks;

    public IgniteSkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 5.0);
        this.fireDuration = config.getInt("fireduration", 5) * 20; // Convert to ticks
        this.knockbackStrength = config.getDouble("knockbackstrength", 0.5);
        this.initialDamage = config.getDouble("initialdamage", 8.0);
        this.burnDamage = config.getDouble("burndamage", 2.0);
        this.burnTicks = config.getInt("burnticks", 5);
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();

        // Initial explosion effect
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f);

        // Create expanding fire ring
        new BukkitRunnable() {
            double size = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (size >= radius || ticks++ > 10) {
                    this.cancel();
                    return;
                }

                // Create fire ring
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = Math.cos(angle) * size;
                    double z = Math.sin(angle) * size;
                    Location flameLoc = center.clone().add(x, 0.2, z);

                    // Fire particles
                    world.spawnParticle(
                            Particle.FLAME,
                            flameLoc,
                            3, 0.1, 0.2, 0.1, 0.02
                    );

                    // Ember effect
                    if (Math.random() < 0.3) {
                        world.spawnParticle(
                                Particle.LAVA,
                                flameLoc.add(0, 0.5, 0),
                                1, 0.1, 0.1, 0.1, 0
                        );
                    }
                }

                // Affect entities in the current ring
                world.getNearbyEntities(center, size, 2, size).forEach(entity -> {
                    if (entity instanceof LivingEntity && entity != player) {
                        LivingEntity target = (LivingEntity) entity;

                        // Check if this entity has already been hit
                        if (!target.hasMetadata("ignite_hit")) {
                            // Apply initial damage
                            target.damage(initialDamage, player);

                            // Apply knockback from center
                            Vector knockback = target.getLocation().subtract(center).toVector()
                                    .normalize()
                                    .multiply(knockbackStrength)
                                    .setY(0.2);
                            target.setVelocity(target.getVelocity().add(knockback));

                            // Set on fire
                            target.setFireTicks(fireDuration);

                            // Mark as hit
                            target.setMetadata("ignite_hit",
                                    new org.bukkit.metadata.FixedMetadataValue(plugin, true));

                            // Start burn damage over time
                            new BukkitRunnable() {
                                int burnCount = 0;

                                @Override
                                public void run() {
                                    if (burnCount++ >= burnTicks || !target.isValid() || target.isDead()) {
                                        target.removeMetadata("ignite_hit", plugin);
                                        this.cancel();
                                        return;
                                    }

                                    if (target.getFireTicks() > 0) {
                                        target.damage(burnDamage, player);

                                        // Burn effect
                                        world.spawnParticle(
                                                Particle.FLAME,
                                                target.getLocation().add(0, 1, 0),
                                                5, 0.2, 0.4, 0.2, 0.02
                                        );
                                    }
                                }
                            }.runTaskTimer(plugin, 20L, 20L); // Damage every second
                        }
                    }
                });

                size += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Rising fire particles
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 20) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 3; i++) {
                    Location particleLoc = center.clone().add(
                            Math.random() * radius * 2 - radius,
                            Math.random() * 2,
                            Math.random() * radius * 2 - radius
                    );

                    world.spawnParticle(
                            Particle.FLAME,
                            particleLoc,
                            0, 0, 0.1, 0, 0
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}