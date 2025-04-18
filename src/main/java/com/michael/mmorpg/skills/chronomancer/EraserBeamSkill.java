package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class EraserBeamSkill extends Skill {
    private final double damage;
    private final double healing;
    private final double range;
    private final double beamWidth;

    public EraserBeamSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.healing = config.getDouble("healing", 10.0);
        this.range = config.getDouble("range", 20.0);
        this.beamWidth = config.getDouble("beamwidth", 1.0);
    }

    @Override
    protected void performSkill(Player player) {
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection();

        // Initial cast effect
        player.getWorld().playSound(startLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);

        // Store hit entities for this cast
        Set<LivingEntity> hitEntities = new HashSet<>();

        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = 10; // 0.5 second beam duration
            private double currentDistance = 0;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    // Clean up at end of cast
                    for (LivingEntity entity : hitEntities) {
                        // Remove the per-cast hit marker
                        if (entity.hasMetadata("eraser_beam_current_cast")) {
                            entity.removeMetadata("eraser_beam_current_cast", plugin);
                        }
                        // Remove any tick-specific metadata
                        for (int i = 0; i <= maxTicks; i++) {
                            String metaKey = "eraser_beam_hit_" + i;
                            if (entity.hasMetadata(metaKey)) {
                                entity.removeMetadata(metaKey, plugin);
                            }
                        }
                    }
                    hitEntities.clear();
                    this.cancel();
                    return;
                }

                // Reset distance for each tick
                currentDistance = 0;
                Location beamLoc = startLoc.clone();

                while (currentDistance < range) {
                    beamLoc.add(direction.clone().multiply(0.5));
                    currentDistance += 0.5;

                    // Core beam (golden particles)
                    player.getWorld().spawnParticle(
                            Particle.DUST,
                            beamLoc,
                            1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f)
                    );

                    if (currentDistance % 2 < 0.5) {
                        player.getWorld().spawnParticle(
                                Particle.END_ROD,
                                beamLoc,
                                1, 0.1, 0.1, 0.1, 0.01
                        );
                    }

                    // Check for entities in beam
                     for (Entity entity : beamLoc.getWorld().getNearbyEntities(beamLoc, beamWidth, beamWidth, beamWidth)) {
                        if (entity instanceof LivingEntity && entity != player) {
                            LivingEntity target = (LivingEntity) entity;
                            String metaKey = "eraser_beam_hit_" + ticks;

                            // Track this entity for cleanup
                            hitEntities.add(target);

                            // Check if entity was already hit by this beam cast
                            if (!target.hasMetadata("eraser_beam_current_cast")) {
                                if (entity instanceof Player) {
                                    Player targetPlayer = (Player) entity;
                                    if (plugin.getPartyManager().getParty(player) != null &&
                                            plugin.getPartyManager().getParty(player).isMember(targetPlayer)) {
                                        // Heal party members (only once per cast)
                                        plugin.getPlayerManager().getPlayerData(targetPlayer).regenHealth(healing);

                                        target.getWorld().spawnParticle(
                                                Particle.HAPPY_VILLAGER,
                                                target.getLocation().add(0, 1, 0),
                                                5, 0.3, 0.3, 0.3, 0
                                        );

                                        plugin.getDamageDisplayManager().spawnDamageDisplay(
                                                target.getLocation(),
                                                healing,
                                                DamageDisplayManager.DamageType.HEALING
                                        );
                                    } else {
                                        damageTarget(target, player);
                                    }
                                } else {
                                    damageTarget(target, player);
                                }

                                // Mark entity as hit for this entire cast
                                target.setMetadata("eraser_beam_current_cast",
                                        new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                            }
                        }
                    }

                    // Stop beam at solid blocks
                    if (!beamLoc.getBlock().isPassable()) {
                        player.getWorld().spawnParticle(
                                Particle.FLASH,
                                beamLoc,
                                1, 0, 0, 0, 0
                        );
                        break;
                    }
                }

                if (ticks % 2 == 0) {
                    player.getWorld().playSound(startLoc, Sound.BLOCK_BEACON_AMBIENT, 0.5f, 2.0f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void damageTarget(LivingEntity target, Player source) {
        // Apply damage
        target.damage(damage, source);

        // Set skill damage metadata and clean it right after damage
        target.setMetadata("skill_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, source));
        target.setMetadata("skill_damage_amount", new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        target.setMetadata("magic_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        // Damage effect
        target.getWorld().spawnParticle(
                Particle.FLASH,
                target.getLocation().add(0, 1, 0),
                1, 0, 0, 0, 0
        );

        // Clean up damage metadata immediately after hit
        new BukkitRunnable() {
            @Override
            public void run() {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
                target.removeMetadata("magic_damage", plugin);
            }
        }.runTaskLater(plugin, 1L);
    }



}