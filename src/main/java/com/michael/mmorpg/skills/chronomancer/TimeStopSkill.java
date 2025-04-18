package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class TimeStopSkill extends Skill {
    private final double duration;
    private final double range;

    public TimeStopSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getDouble("duration", 3.0);
        this.range = config.getDouble("range", 15.0);
    }

    @Override
    public void execute(Player caster) {
        // Get target and validate (will handle party checks since isHarmful is true)
        currentTarget = getTargetEntity(caster, range);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        // Validate target (will check if target is an enemy since isHarmful is true)
        if (validateTarget(caster, currentTarget)) {
            return;  // Validation failed, appropriate message was sent
        }

        if (hasCastTime) {
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        LivingEntity target = currentTarget;

        // Create and apply stun effect
        if (target instanceof Player) {
            StatusEffect stunEffect = new StatusEffect(CCType.STUN, (int)(duration * 1000), caster, 1);
            plugin.getStatusEffectManager().applyEffect((Player)target, stunEffect);

            // Initial time stop effect
            playTimeStopEffect(target.getLocation());

            // Periodic effects during the time stop
            new BukkitRunnable() {
                int ticks = 0;
                final int maxTicks = (int)(duration * 20);

                @Override
                public void run() {
                    if (ticks >= maxTicks || !target.isValid() || target.isDead()) {
                        playTimeStopEndEffect(target.getLocation());
                        cancel();
                        return;
                    }

                    // Play ongoing time stop effects
                    playTimeStopAura(target.getLocation());
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);

            // Success messages and feedback
            if (target instanceof Player) {
                ((Player) target).sendMessage("§b✦ You have been frozen in time!");
            }
            caster.sendMessage("§b✦ Froze " + (target instanceof Player ? target.getName() : "target") + " in time!");

            setSkillSuccess(true);
        } else {
            caster.sendMessage("§c✦ Time Stop can only affect players!");
            setSkillSuccess(false);
        }
    }

    private void playTimeStopEffect(Location location) {
        World world = location.getWorld();

        // Clock circle particles
        double radius = 1.5;
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10);
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            world.spawnParticle(
                    Particle.END_ROD,
                    location.clone().add(x, 0.1, z),
                    1, 0, 0, 0, 0
            );
        }

        // Time distortion particles
        world.spawnParticle(
                Particle.PORTAL,
                location.clone().add(0, 1, 0),
                50, 0.5, 1, 0.5, 0.1
        );

        // Clock hands effect
        for (double i = 0; i < Math.PI * 2; i += Math.PI / 12) {
            Vector direction = new Vector(Math.cos(i), 0, Math.sin(i));
            Location particleLoc = location.clone().add(0, 1, 0);
            for (double d = 0; d < 2; d += 0.1) {
                Vector offset = direction.clone().multiply(d);
                world.spawnParticle(
                        Particle.WITCH,
                        particleLoc.clone().add(offset),
                        1, 0, 0, 0, 0
                );
            }
        }

        // Sound effects
        world.playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f);
        world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.5f, 0.5f);
    }

    private void playTimeStopAura(Location location) {
        World world = location.getWorld();

        // Frozen time particles
        for (int i = 0; i < 3; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = Math.random() * 1.5;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            double y = Math.random() * 2;

            world.spawnParticle(
                    Particle.SNOWFLAKE,
                    location.clone().add(x, y, z),
                    1, 0, 0, 0, 0
            );
        }

        // Suspended time particles
        if (Math.random() < 0.3) {
            world.spawnParticle(
                    Particle.END_ROD,
                    location.clone().add(0, 1, 0),
                    2, 0.5, 0.5, 0.5, 0
            );
        }
    }

    private void playTimeStopEndEffect(Location location) {
        World world = location.getWorld();

        // Time resuming particles
        world.spawnParticle(
                Particle.FLASH,
                location.clone().add(0, 1, 0),
                1, 0, 0, 0, 0
        );

        world.spawnParticle(
                Particle.PORTAL,
                location.clone().add(0, 1, 0),
                30, 0.5, 1, 0.5, 0.2
        );

        // Sound effect
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
    }
}