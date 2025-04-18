package com.michael.mmorpg.skills.unused;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CrushingWaveSkill extends Skill {
    private final double radius;
    private final double slowDuration;
    private final int slowIntensity;
    private final double damage;

    public CrushingWaveSkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 6.0);
        this.slowDuration = config.getDouble("slowDuration", 3.0);
        this.slowIntensity = config.getInt("slowIntensity", 2);
        this.damage = config.getDouble("damage", 5.0);
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();

        // Ground slam animation
        new BukkitRunnable() {
            private double angle = 0;
            private double radius = 0.5;
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 20) {
                    this.cancel();
                    return;
                }

                // Expanding ring effect
                radius += 0.3;
                for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
                    double x = Math.cos(i) * radius;
                    double z = Math.sin(i) * radius;
                    Location particleLoc = center.clone().add(x, 0.1, z);
                    
                    world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 
                            1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.CRIT, particleLoc, 
                            2, 0.1, 0, 0.1, 0.05);
                }

                // Ground crack effect
                angle += Math.PI / 8;
                for (double r = 0; r < radius; r += 0.5) {
                    double x = Math.cos(angle) * r;
                    double z = Math.sin(angle) * r;
                    Location crackLoc = center.clone().add(x, 0, z);
                    world.spawnParticle(Particle.BLOCK_CRUMBLE, crackLoc,
                            1, 0.1, 0, 0.1, 0, Material.BEDROCK.createBlockData());
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Impact sound and effects
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.5f);
        world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);
        
        // Apply slow and damage to nearby entities
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity && entity != player) {
                LivingEntity target = (LivingEntity) entity;
                
                // Skip if in same party
                if (validateTarget(player, target)) {
                    continue;
                }

                // Apply slow effect
                if (target instanceof Player) {
                    StatusEffect slowEffect = new StatusEffect(CCType.SLOW, 
                            (long)(slowDuration * 1000), player, slowIntensity);
                    plugin.getStatusEffectManager().applyEffect((Player) target, slowEffect);
                }

                // Apply damage
                target.damage(damage, player);
            }
        }

        setSkillSuccess(true);
    }
} 