package com.michael.mmorpg.skills.buccaneer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class DeadManWalkingSkill extends Skill {
    private class TrackingState {
        Location lastParticleLocation;
        double ghostHeight = 0;
        boolean ghostRising = true;
        int duration = 0;

        TrackingState(Location startLocation) {
            this.lastParticleLocation = startLocation;
        }
    }

    private final double speedBoostAmount;
    private final int trackingDuration;
    private final double trackingParticleSpacing;
    private int effectTaskId = -1;

    public DeadManWalkingSkill(ConfigurationSection config) {
        super(config);
        this.speedBoostAmount = config.getDouble("speedBoostAmount", 0.2);
        this.trackingDuration = config.getInt("trackingDuration", 200);
        this.trackingParticleSpacing = config.getDouble("trackingParticleSpacing", 0.5);
        this.isTargetedSkill = false;
    }

    @Override
    protected void performSkill(Player player) {
        // Check for pecked target
        if (!player.hasMetadata("last_peck_target")) {
            player.sendMessage("§c✦ The dead need a marked soul to pursue! Use Peck first!");
            setSkillSuccess(false);
            return;
        }

        // Get and validate target
        LivingEntity target = (LivingEntity) player.getMetadata("last_peck_target").get(0).value();
        if (target == null || !target.isValid() || target.isDead()) {
            player.sendMessage("§c✦ The soul has already passed to the other side!");
            setSkillSuccess(false);
            return;
        }

        // Start the pursuit effects
        startPursuit(player, target);
        setSkillSuccess(true);
    }

    private void startPursuit(Player player, LivingEntity target) {
        // Register the target with the Dead Man Manager
        plugin.getDeadManManager().markTarget(player, target, trackingDuration);

        // Apply speed boost to help the pursuit
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                trackingDuration,
                (int)(speedBoostAmount * 10),
                false, true, true
        ));

        // Initialize the tracking state for visual effects
        TrackingState state = new TrackingState(target.getLocation());

        // Start the main tracking effect task
        effectTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                // Check if the effect should end
                if (state.duration >= trackingDuration || !target.isValid() || !player.isValid()) {
                    cleanup(player);
                    cancel();
                    return;
                }

                // Update visual effects
                updateTrackingEffects(player, target, state);
                state.duration++;
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();

        // Create initial effects and play sound
        createActivationEffects(player);

        // Feedback messages
        player.sendMessage("§6✦ The spirits of drowned sailors rise to hunt " + target.getName() + "!");
        if (target instanceof Player) {
            ((Player) target).sendMessage("§c✦ The spirits of drowned sailors have been called to hunt you!");
        }
    }

    private void updateTrackingEffects(Player player, LivingEntity target, TrackingState state) {
        Location currentLoc = target.getLocation();
        Vector direction = currentLoc.clone().subtract(state.lastParticleLocation).toVector();
        double distance = direction.length();

        if (distance > trackingParticleSpacing) {
            direction.normalize().multiply(trackingParticleSpacing);

            // Update ghost height for floating effect
            if (state.ghostRising) {
                state.ghostHeight += 0.02;
                if (state.ghostHeight >= 0.5) state.ghostRising = false;
            } else {
                state.ghostHeight -= 0.02;
                if (state.ghostHeight <= 0) state.ghostRising = true;
            }

            createParticleTrail(player, state, direction, currentLoc);
            state.lastParticleLocation = currentLoc.clone();
        }

        // Create haunting sounds every 2 seconds
        if (state.duration % 40 == 0) {
            float pitch = 0.5f + ((float)Math.random() * 0.3f);
            target.getWorld().playSound(
                    target.getLocation(),
                    Sound.ENTITY_DROWNED_AMBIENT,
                    0.3f,
                    pitch
            );
        }
    }

    private void createParticleTrail(Player player, TrackingState state, Vector direction, Location currentLoc) {
        double steps = currentLoc.distance(state.lastParticleLocation) / trackingParticleSpacing;

        for (int i = 0; i < steps; i++) {
            double angle = (state.duration + i) * 0.5;
            Location particleLoc = state.lastParticleLocation.clone().add(direction.clone().multiply(i));

            // Create the trail (only visible to the hunter)
            player.spawnParticle(
                    Particle.FALLING_WATER,
                    particleLoc.clone().add(
                            Math.cos(angle) * 0.3,
                            state.ghostHeight + 0.2,
                            Math.sin(angle) * 0.3
                    ),
                    1, 0, 0, 0, 0
            );

            // Create ambient effects (visible to everyone)
            if (Math.random() < 0.15) {
                particleLoc.getWorld().spawnParticle(
                        Particle.DRIPPING_DRIPSTONE_WATER,
                        particleLoc.clone().add(0, state.ghostHeight + 0.2, 0),
                        1, 0.1, 0.1, 0.1, 0
                );
            }
        }
    }

    private void createActivationEffects(Player player) {
        World world = player.getWorld();

        // Play water-themed haunting sounds
        world.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 1.0f, 0.7f);
        world.playSound(player.getLocation(), Sound.ENTITY_DROWNED_AMBIENT_WATER, 0.5f, 0.8f);

        // Create a watery ring effect
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            Location effectLoc = player.getLocation().add(
                    Math.cos(angle) * 1.5,
                    0,
                    Math.sin(angle) * 1.5
            );

            world.spawnParticle(
                    Particle.SPLASH,
                    effectLoc,
                    10, 0.1, 0.5, 0.1, 0.05
            );
        }
    }

    private void cleanup(Player player) {
        // Cancel the effect task
        if (effectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(effectTaskId);
            effectTaskId = -1;
        }

        // Create fade out effects
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_EXIT, 0.5f, 0.9f);
        world.spawnParticle(
                Particle.SPLASH,
                player.getLocation(),
                20, 0.5, 0.5, 0.5, 0.05
        );
    }
}