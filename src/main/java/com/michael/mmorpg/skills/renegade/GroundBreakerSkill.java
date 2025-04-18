package com.michael.mmorpg.skills.renegade;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GroundBreakerSkill extends Skill {
    private final double damage;
    private final double radius;
    private final double knockup;
    private final double slowDuration;
    private final int slowIntensity;

    public GroundBreakerSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 50.0);
        this.radius = config.getDouble("radius", 5.0);
        this.knockup = config.getDouble("knockup", 0.5);
        this.slowDuration = config.getDouble("slowduration", 2.0);
        this.slowIntensity = config.getInt("slowintensity", 2);
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();

        // Initial impact effect
        player.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                center,
                1, 0, 0, 0, 0
        );
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);

        // Create expanding shockwave
        new BukkitRunnable() {
            private double currentRadius = 0;
            private final double stepSize = 0.5;

            @Override
            public void run() {
                if (currentRadius >= radius) {
                    this.cancel();
                    return;
                }

                // Create circle of particles for shockwave
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;
                    Location particleLoc = center.clone().add(x, 0, z);

                    // Ground crack effect
                    player.getWorld().spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            particleLoc,
                            5, 0.2, 0, 0.2, 0,
                            center.getBlock().getBlockData()
                    );

                    // Energy effect
                    player.getWorld().spawnParticle(
                            Particle.INSTANT_EFFECT,
                            particleLoc.clone().add(0, 0.5, 0),
                            2, 0, 0.2, 0, 0
                    );
                }

                // Check for entities in the current ring
                double minRadius = Math.max(0, currentRadius - stepSize);
                player.getWorld().getNearbyEntities(center, currentRadius, 2, currentRadius).stream()
                        .filter(entity -> entity instanceof LivingEntity)
                        .filter(entity -> entity != player)
                        .filter(entity -> {
                            double distance = entity.getLocation().distance(center);
                            return distance <= currentRadius && distance > minRadius;
                        })
                        .forEach(entity -> {
                            LivingEntity target = (LivingEntity) entity;

                            // Apply damage
                            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
                            target.damage(damage, player);

                            // Apply knockup
                            Vector velocity = target.getVelocity();
                            velocity.setY(knockup);
                            target.setVelocity(velocity);

                            // Apply slow CC if target is a player
                            if (target instanceof Player) {
                                StatusEffect slowEffect = new StatusEffect(
                                        CCType.SLOW,
                                        (long)(slowDuration * 1000), // Convert to milliseconds
                                        player,
                                        slowIntensity
                                );
                                plugin.getStatusEffectManager().applyEffect((Player) target, slowEffect);
                            }

                            // Impact effect on hit
                            target.getWorld().spawnParticle(
                                    Particle.EXPLOSION,
                                    target.getLocation().add(0, 0.5, 0),
                                    10, 0.3, 0.3, 0.3, 0.1
                            );
                        });

                // Sound effect that follows the wave
                if (currentRadius % 1 < stepSize) {
                    player.getWorld().playSound(
                            center,
                            Sound.BLOCK_STONE_BREAK,
                            0.5f,
                            0.5f
                    );
                }

                currentRadius += stepSize;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}