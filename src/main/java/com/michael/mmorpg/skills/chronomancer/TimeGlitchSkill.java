package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public class TimeGlitchSkill extends Skill {
    private final double duration;
    private final double glitchInterval;
    private final double damage;
    private final double radius;

    public TimeGlitchSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getDouble("duration", 3.0);
        this.glitchInterval = config.getDouble("glitchInterval", 0.25);
        this.damage = config.getDouble("damage", 8.0);
        this.radius = config.getDouble("radius", 3.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Store initial location
        Location initialLocation = player.getLocation().clone();

        // Add metadata to prevent damage during glitch
        player.setMetadata("time_glitching", new FixedMetadataValue(plugin, true));

        // Initial glitch effect
        playGlitchEffect(player);

        // Calculate number of glitches
        int totalGlitches = (int) (duration / glitchInterval);

        new BukkitRunnable() {
            private int glitchCount = 0;
            private boolean isVisible = false;

            @Override
            public void run() {
                if (glitchCount >= totalGlitches || !player.isValid() || player.isDead()) {
                    player.removeMetadata("time_glitching", plugin);
                    cancel();
                    return;
                }

                // Toggle visibility
                isVisible = !isVisible;

                // Hide/show player
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other != player) {
                        if (isVisible) {
                            other.showPlayer(plugin, player);
                        } else {
                            other.hidePlayer(plugin, player);
                        }
                    }
                }

                // Play effects and damage nearby entities when reappearing
                if (isVisible) {
                    Location currentLoc = player.getLocation();
                    createGlitchExplosion(player, currentLoc);
                }

                // Visual and sound effects
                playGlitchEffect(player);

                glitchCount++;

                // Final glitch cleanup
                if (glitchCount >= totalGlitches) {
                    // Ensure player is visible
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (other != player) {
                            other.showPlayer(plugin, player);
                        }
                    }
                    player.removeMetadata("time_glitching", plugin);
                }
            }
        }.runTaskTimer(plugin, 0L, (long) (glitchInterval * 20));

        setSkillSuccess(true);
    }

    private void createGlitchExplosion(Player caster, Location location) {
        // Get nearby entities
        List<Entity> nearbyEntities = location.getWorld().getNearbyEntities(
                location,
                radius,
                radius,
                radius
        ).stream().filter(e -> e instanceof LivingEntity && e != caster).toList();

        // Damage and knock back nearby entities
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity target) {
                // Calculate damage falloff based on distance
                double distance = location.distance(entity.getLocation());
                double damageMultiplier = 1 - (distance / radius);
                double finalDamage = damage * Math.max(0, damageMultiplier);

                if (finalDamage > 0) {
                    // Display the damage number
                    plugin.getDamageDisplayManager().spawnDamageDisplay(
                            target.getLocation(),
                            finalDamage,
                            DamageDisplayManager.DamageType.MAGIC
                    );

                    // Apply temporal damage
                    target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
                    target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, finalDamage));
                    target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

                    // Directly apply the damage to trigger the damage event properly
                    target.damage(finalDamage, caster);

                    // Add brief slowness effect to represent time distortion
                    if (target instanceof Player) {
                        ((Player) target).addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS,
                                20, // 1 second
                                1,  // Slowness II
                                false,
                                false
                        ));
                    }

                    // Knock back effect
                    Vector knockback = entity.getLocation().subtract(location).toVector()
                            .normalize().multiply(0.5).setY(0.2);
                    entity.setVelocity(knockback);
                }
            }
        }

        // Visual explosion effect
        location.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                location,
                1,
                0, 0, 0,
                0
        );

        // Time distortion particles
        location.getWorld().spawnParticle(
                Particle.PORTAL,
                location,
                50,
                1, 1, 1,
                0.5
        );

        // Static-like particles
        for (int i = 0; i < 20; i++) {
            double offsetX = (Math.random() - 0.5) * 2;
            double offsetY = (Math.random() - 0.5) * 2;
            double offsetZ = (Math.random() - 0.5) * 2;
            location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    location.clone().add(offsetX, offsetY, offsetZ),
                    1,
                    0, 0, 0,
                    0
            );
        }

        // Sound effects
        location.getWorld().playSound(
                location,
                Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                1.0f,
                2.0f
        );
        location.getWorld().playSound(
                location,
                Sound.BLOCK_GLASS_BREAK,
                1.0f,
                0.5f
        );
    }

    private void playGlitchEffect(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        // Glitch particles
        world.spawnParticle(
                Particle.REVERSE_PORTAL,
                loc.clone().add(0, 1, 0),
                30,
                0.3, 0.5, 0.3,
                0.1
        );

        // Electric-like particles
        world.spawnParticle(
                Particle.WAX_OFF,
                loc.clone().add(0, 1, 0),
                15,
                0.3, 0.5, 0.3,
                0.1
        );

        // Distortion sound
        world.playSound(
                loc,
                Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                0.5f,
                2.0f
        );
    }
}