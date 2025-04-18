package com.michael.mmorpg.skills.windwaker;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.skills.Skill;

public class UpdraftSkill extends Skill {
    // Core skill properties that control the levitation effect
    private final int levitationDuration;  // Duration in seconds
    private final int levitationAmplifier; // How strong the levitation is
    private final double range;            // How far you can target
    private LivingEntity target;           // Current skill target

    public UpdraftSkill(ConfigurationSection config) {
        super(config);
        // Load our configuration values with sensible defaults
        this.levitationDuration = config.getInt("levitationduration", 3);
        this.levitationAmplifier = config.getInt("levitationamplifier", 2);
        this.range = config.getDouble("range", 15.0);
    }

    @Override
    public void execute(Player player) {
        // Use the base class targeting system that includes party checks
        currentTarget = getTargetEntity(player, range);
        if (currentTarget == null) {
            player.sendMessage("§c✦ No target in range!");
            return;
        }
        target = currentTarget;

        // Since this is instant-cast, perform immediately
        performSkill(player);
    }

    @Override
    protected void performSkill(Player player) {
        // Validate our target is still valid
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Create the initial wind surge effect beneath the target
        createWindSurgeEffect(target.getLocation());

        // Check for and interrupt casting if target is a player
        if (target instanceof Player) {
            Player playerTarget = (Player) target;
            if (playerTarget.hasMetadata("casting")) {
                // Get the skill being cast if possible
                String skillName = "a skill";
                for (MetadataValue meta : playerTarget.getMetadata("casting")) {
                    if (meta.value() instanceof Skill) {
                        Skill skill = (Skill) meta.value();
                        skillName = skill.getName();
                        // Cancel the cast
                        skill.cancelCast(playerTarget);
                    }
                }

                // Notify both players
                playerTarget.sendMessage("§c✦ Your " + skillName + " was interrupted by Updraft!");
                player.sendMessage("§6✦ You interrupted " + playerTarget.getName() + "'s " + skillName + "!");

                // Play interrupt effects
                playInterruptEffects(playerTarget);
            }
        }

        // Apply the levitation effect
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.LEVITATION,
                levitationDuration * 20, // Convert seconds to ticks
                levitationAmplifier,
                false, // Don't make it ambient
                true   // Show particles
        ));

        // Create continuous wind effects around the target
        startWindEffects(target);

        // Broadcast the skill use with appropriate message
        broadcastLocalSkillMessage(player, "§7[Windwaker] " + player.getName() +
                " launches " + target.getName() + " skyward with Updraft!");

        setSkillSuccess(true);
    }

    private void playInterruptEffects(Player target) {
        // Particles to show interruption
        target.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                target.getLocation().add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0
        );

        // Sound for interruption
        target.getWorld().playSound(
                target.getLocation(),
                Sound.BLOCK_ANVIL_LAND,
                0.5f,
                1.5f
        );
    }

    private void createWindSurgeEffect(Location location) {
        World world = location.getWorld();
        Location base = location.clone().subtract(0, 0.5, 0); // Start slightly below feet

        // Create expanding circle of wind at the base
        new BukkitRunnable() {
            double radius = 0;

            @Override
            public void run() {
                if (radius >= 2.0) {
                    cancel();
                    return;
                }

                // Create circle of particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = base.clone().add(x, 0, z);

                    world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0);
                    if (Math.random() < 0.3) {
                        world.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0, 0, 0, 0);
                    }
                }

                // Play the wind surge sound
                if (radius == 0) {
                    world.playSound(base, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);
                    world.playSound(base, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
                }

                radius += 0.2;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void startWindEffects(LivingEntity target) {
        // Create continuous wind effects that follow the target up
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = levitationDuration * 20; // Convert to ticks

            @Override
            public void run() {
                if (ticks >= maxTicks || !target.isValid()) {
                    cancel();
                    return;
                }

                Location loc = target.getLocation();
                World world = loc.getWorld();

                // Create spiraling wind particles around the target
                double angle = ticks * 0.5;
                double radius = 0.8;
                for (int i = 0; i < 2; i++) {
                    angle += Math.PI;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = loc.clone().add(x, 0, z);

                    world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    if (ticks % 5 == 0) {
                        world.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0, 0, 0, 0);
                    }
                }

                // Add upward-moving particles
                if (ticks % 3 == 0) {
                    for (int i = 0; i < 2; i++) {
                        Location upParticleLoc = loc.clone().add(
                                (Math.random() - 0.5) * 1.0,
                                0,
                                (Math.random() - 0.5) * 1.0
                        );
                        world.spawnParticle(Particle.CLOUD, upParticleLoc, 1, 0, 0.2, 0, 0.02);
                    }
                }

                // Play periodic wind sounds
                if (ticks % 10 == 0) {
                    world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.8f);
                }

                ticks++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }
}