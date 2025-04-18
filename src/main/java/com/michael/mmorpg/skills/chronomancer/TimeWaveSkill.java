package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;

public class TimeWaveSkill extends Skill {
    private final double range;
    private final double cooldownReduction;
    private final double waveSpeed;
    private final double width;
    private final Set<Player> affectedPlayers = new HashSet<>();

    public TimeWaveSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 15.0);
        this.cooldownReduction = config.getDouble("cooldownReduction", 5.0);
        this.waveSpeed = config.getDouble("waveSpeed", 0.5);
        this.width = config.getDouble("width", 3.0);
    }

    @Override
    protected void performSkill(Player caster) {
        Location startLoc = caster.getLocation();
        Vector direction = caster.getLocation().getDirection().setY(0).normalize();
        Party casterParty = plugin.getPartyManager().getParty(caster);
        affectedPlayers.clear();

        // Initial wave effect
        playWaveEffect(startLoc);

        new BukkitRunnable() {
            private double distance = 0;

            @Override
            public void run() {
                if (distance >= range) {
                    cancel();
                    return;
                }

                // Calculate current wave position
                Location currentLoc = startLoc.clone().add(direction.clone().multiply(distance));

                // Find players in the wave
                currentLoc.getWorld().getNearbyPlayers(currentLoc, width, 2, width).forEach(player -> {
                    // Only affect each player once
                    if (!affectedPlayers.contains(player)) {
                        // Check if player is in party or is the caster
                        if (player.equals(caster) || (casterParty != null && casterParty.isMember(player))) {
                            // Get all active cooldowns for the player
                            Map<String, Long> playerCooldowns = plugin.getSkillManager().getPlayerCooldowns(player);
                            if (playerCooldowns != null) {
                                int reducedCount = 0;
                                for (Map.Entry<String, Long> entry : playerCooldowns.entrySet()) {
                                    String skillName = entry.getKey();
                                    long currentEndTime = entry.getValue();
                                    long currentTime = System.currentTimeMillis();

                                    if (currentEndTime > currentTime) {
                                        // Calculate new cooldown
                                        long reducedTime = currentEndTime - (long)(cooldownReduction * 1000);
                                        if (reducedTime < currentTime) {
                                            reducedTime = currentTime;
                                        }

                                        // Set the new cooldown
                                        plugin.getSkillManager().setCooldown(player, skillName,
                                                reducedTime - currentTime);
                                        reducedCount++;
                                    }
                                }

                                if (reducedCount > 0) {
                                    // Feedback based on number of skills affected
                                    player.sendMessage("§b✦ Time Wave reduced " + reducedCount +
                                            " of your cooldowns by " +
                                            String.format("%.1f", cooldownReduction) + " seconds!");

                                    // Player effect
                                    playPlayerAffectEffect(player.getLocation(), reducedCount);
                                }
                            }

                            // Add to affected players
                            affectedPlayers.add(player);
                        }
                    }
                });

                // Visual wave effects
                playTravelingWaveEffect(currentLoc, direction);

                // Increment distance
                distance += waveSpeed;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void playWaveEffect(Location location) {
        World world = location.getWorld();

        world.spawnParticle(
                Particle.FLASH,
                location.clone().add(0, 1, 0),
                1, 0, 0, 0, 0
        );

        world.spawnParticle(
                Particle.PORTAL,
                location.clone().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );

        world.playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
    }

    private void playTravelingWaveEffect(Location location, Vector direction) {
        World world = location.getWorld();
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        for (double w = -width; w <= width; w += 0.5) {
            Vector widthVector = perpendicular.clone().multiply(w);
            Location particleLoc = location.clone().add(widthVector);

            // Time distortion particles
            world.spawnParticle(
                    Particle.WITCH,
                    particleLoc.clone().add(0, 0.1, 0),
                    1, 0, 0, 0, 0
            );

            if (Math.random() < 0.3) {
                world.spawnParticle(
                        Particle.END_ROD,
                        particleLoc.clone().add(0, Math.random() * 2, 0),
                        1, 0, 0, 0, 0
                );
            }
        }
    }

    private void playPlayerAffectEffect(Location location, int reducedCount) {
        World world = location.getWorld();

        // Scale effect based on number of skills affected
        int particles = Math.min(16, reducedCount * 2);

        for (int i = 0; i < particles; i++) {
            double angle = Math.PI * 2 * i / particles;
            double radius = 0.5 + (Math.random() * 0.5);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            world.spawnParticle(
                    Particle.END_ROD,
                    location.clone().add(x, 1, z),
                    1, 0, 0, 0, 0
            );
        }

        world.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 2.0f);
    }
}