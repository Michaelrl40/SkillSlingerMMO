package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class DustTechniqueSkill extends Skill {
    private final double radius;        // Area of effect radius
    private final long effectDuration;  // Duration of darkness effect
    private final int effectIntensity;  // Intensity of the effect

    public DustTechniqueSkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 10.0);
        // Convert duration from seconds to milliseconds for status system
        this.effectDuration = (long)(config.getDouble("effectduration", 5.0) * 1000);
        this.effectIntensity = config.getInt("effectintensity", 1);
    }

    @Override
    protected void performSkill(Player player) {
        // Store skill's origin point
        Location center = player.getLocation();

        // Create expanding dust cloud effect
        createDustCloud(center);

        // Find and affect targets
        List<Entity> nearbyEntities = player.getNearbyEntities(radius, radius, radius);
        for (Entity entity : nearbyEntities) {
            // Skip non-living entities and validate targets
            if (!(entity instanceof LivingEntity) || validateTarget(player, (LivingEntity)entity)) {
                continue;
            }

            LivingEntity target = (LivingEntity) entity;

            if (target instanceof Player) {
                // Apply darkness through status effect system for players
                Player playerTarget = (Player) target;
                StatusEffect blindness = new StatusEffect(CCType.BLIND, effectDuration, player, effectIntensity);
                plugin.getStatusEffectManager().applyEffect(playerTarget, blindness);

                // Send message to affected players
                playerTarget.sendMessage("§8✦ You are blinded by a cloud of dust!");
            } else {
                // For non-player entities, use a potion effect
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.DARKNESS,
                        (int)(effectDuration / 50), // Convert to ticks
                        effectIntensity - 1,
                        false,
                        false
                ));
            }

            // Play effect on each target hit
            playHitEffect(target.getLocation());
        }

        // Notify the caster of success
        player.sendMessage("§7✦ You release a cloud of blinding dust!");

        setSkillSuccess(true);
    }

    private void createDustCloud(Location center) {
        // Create an expanding dust cloud effect
        new BukkitRunnable() {
            double currentRadius = 0;
            int ticks = 0;
            final int maxTicks = 20; // 1 second of expansion

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // Calculate expanding radius
                currentRadius = (radius * ticks) / maxTicks;

                // Create dust particles in a circular pattern
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                    double x = Math.cos(angle) * currentRadius;
                    double z = Math.sin(angle) * currentRadius;

                    Location particleLoc = center.clone().add(x, 0.5, z);

                    // Main dust color
                    center.getWorld().spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            3, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 150, 150), 1.0f)
                    );

                    // Additional smoke effect
                    center.getWorld().spawnParticle(
                            Particle.SMOKE,
                            particleLoc,
                            2, 0.1, 0.1, 0.1, 0.02
                    );
                }

                // Add some rising dust particles
                for (int i = 0; i < 5; i++) {
                    double randX = (Math.random() - 0.5) * currentRadius * 2;
                    double randZ = (Math.random() - 0.5) * currentRadius * 2;
                    Location particleLoc = center.clone().add(randX, 0, randZ);

                    center.getWorld().spawnParticle(
                            Particle.CLOUD,
                            particleLoc,
                            1, 0, 0.5, 0, 0.05
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Play initial throw sound
        center.getWorld().playSound(
                center,
                Sound.BLOCK_SAND_BREAK,
                1.0f,
                0.8f
        );
    }

    private void playHitEffect(Location location) {
        // Create a small puff of dust when hitting each target
        location.getWorld().spawnParticle(
                Particle.SMOKE,
                location.add(0, 1, 0),
                8, 0.3, 0.3, 0.3, 0.05
        );

        // Add some grey dust particles
        location.getWorld().spawnParticle(
                Particle.DUST,
                location,
                5, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(130, 130, 130), 0.8f)
        );

        // Play a soft hit sound
        location.getWorld().playSound(
                location,
                Sound.BLOCK_SAND_HIT,
                0.5f,
                1.2f
        );
    }
}