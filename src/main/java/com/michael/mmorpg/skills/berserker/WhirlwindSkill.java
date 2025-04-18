package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class WhirlwindSkill extends Skill {
    private final double damage;
    private final double radius;
    private final int duration;
    private final int damageTickRate;
    private final int slowIntensity;

    public WhirlwindSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 5.0);
        this.radius = config.getDouble("radius", 8.0);
        this.duration = config.getInt("duration", 60); // 3 seconds at 20 ticks/sec
        this.damageTickRate = config.getInt("damageTickRate", 10); // Damage every 0.5 seconds
        this.slowIntensity = config.getInt("slowintensity", 2); // Slowness III
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Check rage cost
        if (!playerData.useRage(rageCost)) {
            player.sendMessage("§c✦ Not enough rage!");
            setSkillSuccess(false);
            return;
        }

        // Apply slowness effect
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                duration,
                slowIntensity - 1,
                false, true
        ));

        // Play initial sound
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0f,
                0.8f
        );

        // Track hit entities per tick to prevent multiple hits in same tick
        Set<Entity> hitEntities = new HashSet<>();
        int[] currentTick = {0};

        new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline() || currentTick[0] >= duration) {
                    cancel();
                    return;
                }

                currentTick[0]++;
                Location playerLoc = player.getLocation();

                // Clear hit entities each damage tick
                if (currentTick[0] % damageTickRate == 0) {
                    hitEntities.clear();
                }

                // Particle effects
                angle += Math.PI / 8; // Rotate 22.5 degrees per tick
                int particleCount = 8;
                double particleRadius = radius * 0.8;

                // Inner spiral
                for (int i = 0; i < particleCount; i++) {
                    double adjustedAngle = angle + (2 * Math.PI * i / particleCount);
                    double x = Math.cos(adjustedAngle) * particleRadius;
                    double z = Math.sin(adjustedAngle) * particleRadius;
                    Location particleLoc = playerLoc.clone().add(x, 0.5, z);

                    player.getWorld().spawnParticle(
                            Particle.SWEEP_ATTACK,
                            particleLoc,
                            1, 0.1, 0.1, 0.1, 0
                    );
                }

                // Outer swirl
                for (int i = 0; i < particleCount; i++) {
                    double adjustedAngle = -angle + (2 * Math.PI * i / particleCount);
                    double x = Math.cos(adjustedAngle) * (particleRadius + 0.5);
                    double z = Math.sin(adjustedAngle) * (particleRadius + 0.5);
                    Location particleLoc = playerLoc.clone().add(x, 1.0, z);

                    player.getWorld().spawnParticle(
                            Particle.CRIT,
                            particleLoc,
                            1, 0.1, 0.1, 0.1, 0.2
                    );
                }

                // Apply damage every damageTickRate ticks
                if (currentTick[0] % damageTickRate == 0) {
                    // Play spinning sound
                    player.getWorld().playSound(
                            playerLoc,
                            Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                            0.8f,
                            1.0f
                    );

                    for (Entity entity : playerLoc.getWorld().getNearbyEntities(playerLoc, radius, radius, radius)) {
                        if (entity instanceof LivingEntity && entity != player && !hitEntities.contains(entity)) {
                            LivingEntity target = (LivingEntity) entity;

                            // Validate target
                            if (validateTarget(player, target)) continue;

                            // Skip party members and invalid targets without message
                            if (target instanceof Player) {
                                if (plugin.getPartyManager().getParty(player) != null &&
                                        plugin.getPartyManager().getParty(player).isMember((Player)target)) {
                                    continue;
                                }
                            }

                            // Add to hit entities
                            hitEntities.add(entity);

                            // Calculate damage falloff based on distance
                            double distance = playerLoc.distance(target.getLocation());
                            double falloff = 1.0 - (distance / radius);
                            double adjustedDamage = damage * Math.max(0.5, falloff);

                            // Apply knockback
                            Vector knockback = target.getLocation().subtract(playerLoc).toVector()
                                    .normalize().multiply(0.3).setY(0.1);
                            target.setVelocity(knockback);

                            // Apply damage
                            target.setMetadata("skill_damage",
                                    new org.bukkit.metadata.FixedMetadataValue(plugin, player));
                            target.setMetadata("skill_damage_amount",
                                    new org.bukkit.metadata.FixedMetadataValue(plugin, adjustedDamage));
                            target.damage(adjustedDamage, player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}