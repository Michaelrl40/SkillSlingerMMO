package com.michael.mmorpg.skills.mage;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class WeakFireballSkill extends Skill {

    private final double damage;
    private final double projectileSpeed;
    private final double explosionRadius;

    public WeakFireballSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.projectileSpeed = config.getDouble("projectileSpeed", 1.0);
        this.explosionRadius = config.getDouble("explosionRadius", 2.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Create fireball projectile from player's eye location
        Location spawnLoc = player.getEyeLocation();
        Vector direction = spawnLoc.getDirection();

        // Use a snowball as the base entity (invisible later)
        Snowball projectile = player.launchProjectile(Snowball.class, direction.multiply(projectileSpeed));

        // Set metadata for damage and owner
        projectile.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        projectile.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        projectile.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        projectile.setMetadata("fireball", new FixedMetadataValue(plugin, true));

        // Launch effects and sound
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.2f);

        // Particle trail to make it look like a fireball
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Cancel if projectile is gone or after 5 seconds (100 ticks)
                if (!projectile.isValid() || projectile.isDead() || ticks > 100) {
                    cancel();
                    return;
                }

                // Fireball particles
                Location particleLoc = projectile.getLocation();
                particleLoc.getWorld().spawnParticle(
                        Particle.FLAME,
                        particleLoc,
                        3, 0.1, 0.1, 0.1, 0.01
                );

                // Core of the fireball with smaller colored particles
                particleLoc.getWorld().spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1, 0.1, 0.1, 0.1, 0.0,
                        new Particle.DustOptions(Color.fromRGB(255, 100, 0), 1.0f)
                );

                // Smoke trail
                particleLoc.getWorld().spawnParticle(
                        Particle.SMOKE,
                        particleLoc,
                        1, 0.05, 0.05, 0.05, 0.01
                );

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}