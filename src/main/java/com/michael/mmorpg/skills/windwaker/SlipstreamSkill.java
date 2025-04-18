package com.michael.mmorpg.skills.windwaker;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SlipstreamSkill extends Skill {
    // Configuration values
    private final double launchHeight;      // Height to launch players
    private final double launchRadius;      // Radius to affect party members
    private final double ascendSpeed;       // How quickly players rise
    private final int slowFallDuration;     // How long slow fall lasts in seconds

    // Track affected players for cleanup
    private final Set<UUID> affectedPlayers = new HashSet<>();

    public SlipstreamSkill(ConfigurationSection config) {
        super(config);
        this.launchHeight = config.getDouble("launchheight", 30.0);
        this.launchRadius = config.getDouble("launchradius", 5.0);
        this.ascendSpeed = config.getDouble("ascendspeed", 0.8);
        this.slowFallDuration = config.getInt("slowfallduration", 30);
    }

    @Override
    protected void performSkill(Player caster) {
        // Get the caster's party
        Party party = plugin.getPartyManager().getParty(caster);
        List<Player> targetPlayers = new ArrayList<>();

        // Add caster to targets
        targetPlayers.add(caster);

        // Add nearby party members if in a party
        if (party != null) {
            for (Player member : party.getMembers()) {
                // Check if member is online and nearby, but not the caster
                if (member != caster &&
                        member.isOnline() &&
                        member.getLocation().distance(caster.getLocation()) <= launchRadius) {
                    targetPlayers.add(member);
                }
            }
        }

        // Launch all target players
        for (Player player : targetPlayers) {
            launchPlayer(player);
        }

        // Create the initial wind surge effect
        createWindSurgeEffect(caster.getLocation());

        // Broadcast the skill use
        String partyMessage = targetPlayers.size() > 1 ? " and their allies" : "";
        broadcastLocalSkillMessage(caster, "§7[Windwaker] " + caster.getName() +
                partyMessage + " rides the Slipstream!");

        setSkillSuccess(true);
    }

    private void launchPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        affectedPlayers.add(playerId);

        // Initial feedback
        player.sendMessage("§7✦ You're caught in the Slipstream!");

        // Apply slow falling effect
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING,
                slowFallDuration * 20,  // Convert to ticks
                0,  // Amplifier (level 1)
                true,  // Ambient
                true   // Show particles
        ));

        // Start the ascension
        new BukkitRunnable() {
            private double heightGained = 0;
            private int particleTick = 0;

            @Override
            public void run() {
                // Check if player is still valid and online
                if (!player.isOnline() || heightGained >= launchHeight) {
                    cancel();
                    return;
                }

                // Apply upward velocity
                Vector velocity = new Vector(0, ascendSpeed, 0);
                player.setVelocity(velocity);

                // Create ascending particle effects
                createAscendingEffects(player.getLocation(), particleTick++);

                // Update height gained
                heightGained += ascendSpeed;

                // Play whoosh sound every few ticks
                if (particleTick % 5 == 0) {
                    player.getWorld().playSound(player.getLocation(),
                            Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.5f);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        // Schedule cleanup of tracking
        new BukkitRunnable() {
            @Override
            public void run() {
                affectedPlayers.remove(playerId);
            }
        }.runTaskLater(getPlugin(), slowFallDuration * 20L);
    }

    private void createWindSurgeEffect(Location center) {
        World world = center.getWorld();

        // Create expanding ring effect at ground level
        new BukkitRunnable() {
            double radius = 0;

            @Override
            public void run() {
                if (radius >= launchRadius) {
                    cancel();
                    return;
                }

                // Create circle of particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = center.clone().add(x, 0.1, z);

                    world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0);
                    if (Math.random() < 0.3) {
                        world.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0, 0, 0, 0);
                    }
                }

                // Play surge sound
                if (radius == 0) {
                    world.playSound(center, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.7f);
                    world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
                }

                radius += 0.2;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void createAscendingEffects(Location location, int tick) {
        World world = location.getWorld();

        // Create spiral effect around the player
        double angle = tick * 0.5;
        for (int i = 0; i < 2; i++) {
            angle += Math.PI;
            double x = Math.cos(angle) * 0.8;
            double z = Math.sin(angle) * 0.8;
            Location particleLoc = location.clone().add(x, 0, z);

            world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0.1, 0, 0.1, 0);
            if (tick % 3 == 0) {
                world.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0, 0, 0, 0);
            }
        }

        // Add some upward-moving particles
        if (Math.random() < 0.3) {
            Location upParticleLoc = location.clone().add(
                    (Math.random() - 0.5) * 0.5,
                    0.5,
                    (Math.random() - 0.5) * 0.5
            );
            world.spawnParticle(Particle.END_ROD, upParticleLoc, 1, 0, 0.2, 0, 0.02);
        }
    }
}