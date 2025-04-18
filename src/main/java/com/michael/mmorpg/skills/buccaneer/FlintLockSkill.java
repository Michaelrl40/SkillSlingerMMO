package com.michael.mmorpg.skills.buccaneer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class FlintLockSkill extends Skill {

    private final double damage;
    private final double projectileSpeed;
    private final double range;

    public FlintLockSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.projectileSpeed = config.getDouble("projectileSpeed", 3.0);
        this.range = config.getDouble("range", 20.0);
    }

    @Override
    protected void performSkill(Player player) {
        Location loc = player.getLocation();

        // Play flintlock firing effects
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 2.0f);
        player.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 2.0f);

        // Create the projectile
        Snowball bullet = player.launchProjectile(Snowball.class);

        // Set projectile properties
        Vector direction = player.getLocation().getDirection();
        bullet.setVelocity(direction.multiply(projectileSpeed));
        bullet.setGravity(false);

        // Add metadata for damage handling
        bullet.setMetadata("flintlock_shot", new FixedMetadataValue(plugin, true));
        bullet.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        bullet.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        // Create particle trail
        new BukkitRunnable() {
            int distance = 0;

            @Override
            public void run() {
                if (!bullet.isValid() || bullet.isDead() || distance > range) {
                    cancel();
                    bullet.remove();
                    return;
                }

                // Smoke trail
                bullet.getWorld().spawnParticle(
                        Particle.SMOKE,
                        bullet.getLocation(),
                        3, 0.02, 0.02, 0.02, 0.01
                );

                // Spark effect
                bullet.getWorld().spawnParticle(
                        Particle.DUST,
                        bullet.getLocation(),
                        2, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 140, 0), 1)
                );

                distance++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}