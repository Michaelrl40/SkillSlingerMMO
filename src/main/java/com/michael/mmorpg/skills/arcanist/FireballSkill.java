package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class FireballSkill extends Skill {
    private final double projectileSpeed;
    private final double projectileSpread;
    private final double damage;

    public FireballSkill(ConfigurationSection config) {
        super(config);
        this.projectileSpeed = config.getDouble("projectilespeed", 2.5); // Increased from 1.5 to 2.5
        this.projectileSpread = config.getDouble("spread", 0.05); // Reduced spread for straighter path
        this.damage = config.getDouble("damage", 15.0);
    }

    @Override
    protected void performSkill(Player player) {
        Location loc = player.getEyeLocation();
        Vector direction = loc.getDirection();

        // Create the projectile
        Snowball fireball = player.launchProjectile(Snowball.class);

        // Disable gravity for straighter trajectory
        fireball.setGravity(false);

        // Set velocity with minimal spread
        Vector velocity = direction.multiply(projectileSpeed)
                .add(new Vector(
                        (Math.random() - 0.5) * projectileSpread,
                        (Math.random() - 0.5) * projectileSpread,
                        (Math.random() - 0.5) * projectileSpread
                ));

        fireball.setVelocity(velocity);

        // Store original direction for maintaining trajectory
        final Vector originalDirection = velocity.clone();

        // Add metadata for damage and identification
        fireball.setMetadata("fireball", new FixedMetadataValue(plugin, true));
        fireball.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        fireball.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        fireball.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

        // Enhanced visual and sound effects for a more impactful cast
        player.getWorld().spawnParticle(
                Particle.FLAME,
                loc.add(direction.normalize().multiply(0.5)),
                15, 0.2, 0.2, 0.2, 0.08
        );
        player.getWorld().spawnParticle(
                Particle.LAVA,
                loc,
                5, 0.1, 0.1, 0.1, 0.02
        );
        player.getWorld().playSound(
                loc,
                Sound.ENTITY_BLAZE_SHOOT,
                1.0f,
                1.3f // Higher pitch for faster projectile
        );

        // Start enhanced particle trail task
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (fireball.isDead() || !fireball.isValid() || ticks++ > 100) { // Added safety timeout
                    // Create impact effect on death
                    createImpactEffect(fireball.getLocation());
                    cancel();
                    return;
                }

                // Re-apply velocity periodically to maintain speed and trajectory
                if (ticks % 3 == 0) {
                    fireball.setVelocity(originalDirection);
                }

                // More dramatic fire trail
                fireball.getWorld().spawnParticle(
                        Particle.FLAME,
                        fireball.getLocation(),
                        3, 0.1, 0.1, 0.1, 0.02
                );

                // Add occasional smoke and embers for a more realistic fire effect
                if (ticks % 2 == 0) {
                    fireball.getWorld().spawnParticle(
                            Particle.SMOKE,
                            fireball.getLocation(),
                            1, 0.05, 0.05, 0.05, 0.01
                    );
                }

                // Add occasional lava particles for visual interest
                if (Math.random() < 0.2) {
                    fireball.getWorld().spawnParticle(
                            Particle.LAVA,
                            fireball.getLocation(),
                            1, 0, 0, 0, 0
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void createImpactEffect(Location location) {
        if (location.getWorld() != null) {
            // Create a dramatic fire explosion effect
            location.getWorld().spawnParticle(
                    Particle.EXPLOSION,
                    location,
                    1, 0, 0, 0, 0
            );
            location.getWorld().spawnParticle(
                    Particle.FLAME,
                    location,
                    25, 0.4, 0.4, 0.4, 0.2
            );
            location.getWorld().spawnParticle(
                    Particle.LAVA,
                    location,
                    10, 0.2, 0.2, 0.2, 0
            );

            // Impact sounds
            location.getWorld().playSound(
                    location,
                    Sound.ENTITY_GENERIC_EXPLODE,
                    1.0f,
                    1.0f
            );
        }
    }
}