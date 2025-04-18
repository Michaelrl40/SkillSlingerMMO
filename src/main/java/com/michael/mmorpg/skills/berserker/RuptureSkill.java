package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RuptureSkill extends Skill {
    private final double initialDamage;
    private final double damagePerBlock;
    private final int duration;
    private final int checkInterval;
    private static final Map<UUID, Location> lastPositions = new HashMap<>();

    public RuptureSkill(ConfigurationSection config) {
        super(config);
        this.initialDamage = config.getDouble("initialdamage", 5.0);
        this.damagePerBlock = config.getDouble("damageperblock", 2.0);
        this.duration = config.getInt("duration", 100); // 5 seconds
        this.checkInterval = config.getInt("checkinterval", 10); // Check every 0.5 seconds
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

        // Get melee target
        LivingEntity target = getMeleeTarget(player, targetRange);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Validate target
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Store initial position
        lastPositions.put(target.getUniqueId(), target.getLocation());

        // Initial effect
        Location loc = target.getLocation();
        target.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT, 0.7f, 1.2f);
        target.getWorld().playSound(loc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.8f);

        // More intense initial particles
        target.getWorld().spawnParticle(
                Particle.BLOCK_CRUMBLE,
                loc.add(0, 1, 0),
                20, 0.3, 0.5, 0.3, 0,
                org.bukkit.Material.REDSTONE_BLOCK.createBlockData()
        );
        target.getWorld().spawnParticle(Particle.CRIMSON_SPORE, loc, 15, 0.3, 0.5, 0.3, 0.05);

        // Apply initial damage with enhanced feedback
        applyRuptureDamage(target, player, initialDamage);

        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            targetPlayer.sendMessage("§c✦ Your legs have been ruptured! Movement will cause damage!");
            // Add slowness effect
            targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0, false, false));
        }

        // Add metadata to track the effect
        target.setMetadata("rupture_active", new FixedMetadataValue(plugin, true));

        // Start damage tracking
        new BukkitRunnable() {
            private int ticks = 0;
            private double totalDistanceDamage = 0.0;

            @Override
            public void run() {
                if (!target.isValid() || !target.hasMetadata("rupture_active") || ticks >= duration) {
                    endEffect();
                    cancel();
                    return;
                }

                ticks++;

                // Check movement every interval
                if (ticks % checkInterval == 0) {
                    Location previousPos = lastPositions.get(target.getUniqueId());
                    Location currentPos = target.getLocation();

                    if (previousPos != null && previousPos.getWorld().equals(currentPos.getWorld())) {
                        double distanceMoved = previousPos.distance(currentPos);
                        if (distanceMoved > 0.1) { // Minimum movement threshold
                            // Calculate damage
                            double movementDamage = damagePerBlock * distanceMoved;
                            totalDistanceDamage += movementDamage;

                            // Enhanced feedback when taking damage
                            applyRuptureDamage(target, player, movementDamage);

                            // More intense movement indicator
                            Location effectLoc = currentPos.clone().add(0, 1, 0);
                            target.getWorld().spawnParticle(
                                    Particle.BLOCK_CRUMBLE,
                                    effectLoc,
                                    8, 0.2, 0.4, 0.2, 0,
                                    org.bukkit.Material.REDSTONE_BLOCK.createBlockData()
                            );
                            target.getWorld().spawnParticle(
                                    Particle.CRIMSON_SPORE,
                                    effectLoc,
                                    5, 0.2, 0.4, 0.2, 0.05
                            );

                            // Enhanced sound feedback
                            target.getWorld().playSound(
                                    currentPos,
                                    Sound.ENTITY_PLAYER_HURT,
                                    0.4f,
                                    1.2f
                            );
                        }
                        lastPositions.put(target.getUniqueId(), currentPos);
                    }
                }

                // Periodic effect reminder
                if (ticks % 20 == 0) { // Every second
                    Location particleLoc = target.getLocation().add(0, 0.1, 0);
                    target.getWorld().spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            particleLoc,
                            3, 0.1, 0, 0.1, 0,
                            org.bukkit.Material.REDSTONE_BLOCK.createBlockData()
                    );
                    target.getWorld().spawnParticle(
                            Particle.CRIMSON_SPORE,
                            particleLoc,
                            2, 0.1, 0, 0.1, 0.05
                    );
                }
            }

            private void endEffect() {
                target.removeMetadata("rupture_active", plugin);
                lastPositions.remove(target.getUniqueId());

                if (totalDistanceDamage > 0) {
                    // Remove slowness if it's a player
                    if (target instanceof Player) {
                        ((Player) target).removePotionEffect(PotionEffectType.SLOWNESS);
                        ((Player) target).sendMessage("§a✦ Rupture effect has worn off.");
                    }

                    // End effect sound and particles
                    Location endLoc = target.getLocation();
                    target.getWorld().playSound(endLoc, Sound.BLOCK_CHAIN_BREAK, 0.7f, 0.8f);
                    target.getWorld().spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            endLoc.add(0, 1, 0),
                            15, 0.3, 0.5, 0.3, 0,
                            org.bukkit.Material.REDSTONE_BLOCK.createBlockData()
                    );

                    player.sendMessage("§c✦ Rupture dealt " + String.format("%.1f", totalDistanceDamage) + " movement damage!");
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void applyRuptureDamage(LivingEntity target, Player source, double damage) {
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, source));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.setMetadata("no_knockback", new FixedMetadataValue(plugin, true));
        target.setMetadata("pinch_damage", new FixedMetadataValue(plugin, true));

        if (target instanceof Player) {
            ((Player) target).damage(0.0); // Trigger hurt effect
            double newHealth = Math.max(0, target.getHealth() - damage);
            target.setHealth(newHealth);
        } else {
            target.damage(damage);
        }

        // Clean up metadata
        target.removeMetadata("skill_damage", plugin);
        target.removeMetadata("skill_damage_amount", plugin);
        target.removeMetadata("no_knockback", plugin);
        target.removeMetadata("pinch_damage", plugin);
    }
}