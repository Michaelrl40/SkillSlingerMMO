package com.michael.mmorpg.skills.bard;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class SonicBoomSkill extends Skill {
    private final double damage;
    private final double range;
    private final double knockbackStrength;

    public SonicBoomSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.range = config.getDouble("range", 12.0);
        this.knockbackStrength = config.getDouble("knockbackstrength", 1.5);
    }

    @Override
    protected void performSkill(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();

        // Charge up effect
        player.getWorld().playSound(start, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

        // Create cone-shaped shockwave
        for (double t = 0; t <= range; t += 0.5) {
            double coneWidth = t * 0.3; // Cone gets wider as it goes
            Location point = start.clone().add(direction.clone().multiply(t));

            // Create circular pattern at each point
            for (double theta = 0; theta < Math.PI * 2; theta += Math.PI / 8) {
                double x = coneWidth * Math.cos(theta);
                double y = coneWidth * Math.sin(theta);
                Vector perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0));
                Vector upward = direction.clone().crossProduct(perpendicular);
                Location particleLoc = point.clone().add(
                        perpendicular.clone().multiply(x)).add(upward.clone().multiply(y));

                // Red shockwave particles
                player.getWorld().spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f)
                );
            }

            // Check for entities at each step
            for (Entity entity : player.getWorld().getNearbyEntities(point, coneWidth, coneWidth, coneWidth)) {
                if (entity instanceof LivingEntity && entity != player) {
                    LivingEntity target = (LivingEntity) entity;

                    // Skip party members
                    if (entity instanceof Player && plugin.getPartyManager().getParty(player) != null &&
                            plugin.getPartyManager().getParty(player).isMember((Player)entity)) {
                        continue;
                    }

                    // Apply damage and knockback
                    Vector knockback = direction.clone().multiply(knockbackStrength);
                    target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                    target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
                    target.damage(damage, player);
                    target.setVelocity(knockback);

                    // Impact effect
                    target.getWorld().spawnParticle(
                            Particle.EXPLOSION_EMITTER,
                            target.getLocation(),
                            3, 0.2, 0.2, 0.2, 0
                    );
                }
            }
        }

        // Sound effects
        player.getWorld().playSound(start, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 2.0f);
        player.getWorld().playSound(start, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);

        broadcastLocalSkillMessage(player, "§c[Bard] " + player.getName() + " unleashes a devastating Sonic Boom!");

        setSkillSuccess(true);
    }
}