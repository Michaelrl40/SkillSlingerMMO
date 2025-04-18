package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class FutureSightSkill extends Skill {
    private final double duration;
    private final double range;

    public FutureSightSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getDouble("duration", 8.0);
        this.range = config.getDouble("range", 15.0);
        this.isHarmfulSkill = false;
    }

    @Override
    public void execute(Player caster) {
        // Get target and let validateTarget handle the party/self targeting rules
        currentTarget = getTargetEntity(caster, range);

        // If no target found, default to self (which is always valid)
        if (currentTarget == null) {
            currentTarget = caster;
        }

        // validateTarget will handle all the party/player validation rules
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
        if (!(currentTarget instanceof Player)) {
            caster.sendMessage("§c✦ You can only grant Future Sight to players!");
            setSkillSuccess(false);
            return;
        }

        Player target = (Player) currentTarget;

        // Remove any existing Future Sight first
        if (target.hasMetadata("future_sight")) {
            target.removeMetadata("future_sight", plugin);
        }

        // Apply Future Sight buff
        target.setMetadata("future_sight", new FixedMetadataValue(plugin, true));

        // Debug message to confirm application
        target.sendMessage("§b✦ A protective shield forms around you, ready to block the next crowd control effect!");

        // Initial effect
        playFutureSightEffect(target.getLocation());

        // Periodic effects
        new BukkitRunnable() {
            final long endTime = System.currentTimeMillis() + (long)(duration * 1000);

            @Override
            public void run() {
                if (!target.isValid() || target.isDead() ||
                        !target.hasMetadata("future_sight") ||
                        System.currentTimeMillis() >= endTime) {

                    // Remove buff if expired naturally
                    if (target.hasMetadata("future_sight")) {
                        target.removeMetadata("future_sight", plugin);
                        if (target.isValid() && !target.isDead()) {
                            target.sendMessage("§c✦ Your Future Sight has faded!");
                            playFutureSightEndEffect(target.getLocation());
                        }
                    }

                    cancel();
                    return;
                }

                // Ongoing floating shield effect
                playFutureSightAura(target.getLocation());
            }
        }.runTaskTimer(plugin, 0L, 5L);

        // Success messages
        if (caster.equals(target)) {
            caster.sendMessage("§b✦ You grant yourself the power of Future Sight!");
        } else {
            target.sendMessage("§b✦ " + caster.getName() + " granted you the power of Future Sight!");
            caster.sendMessage("§b✦ You granted " + target.getName() + " the power of Future Sight!");
        }

        setSkillSuccess(true);
    }

    private void playFutureSightEffect(Location location) {
        World world = location.getWorld();

        // Create expanding rings
        for (double radius = 0.5; radius < 2; radius += 0.5) {
            for (int i = 0; i < 36; i++) {
                double angle = Math.toRadians(i * 10);
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);

                world.spawnParticle(
                        Particle.WITCH,
                        location.clone().add(x, radius/2, z),
                        1, 0, 0, 0, 0
                );
            }
        }

        // Eye symbol particles
        for (double t = 0; t < Math.PI * 2; t += Math.PI / 8) {
            double x = Math.cos(t) * 0.5;
            double z = Math.sin(t) * 0.3; // Compress for eye shape
            Location particleLoc = location.clone().add(x, 1.5, z);
            world.spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1, 0, 0, 0, 0
            );
        }

        // Sound effects
        world.playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
        world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 2.0f);
    }

    private void playFutureSightAura(Location location) {
        World world = location.getWorld();

        // Rotating shield particles
        double time = (System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double angle = time + (i * Math.PI * 2 / 3);
            double x = Math.cos(angle) * 0.7;
            double z = Math.sin(angle) * 0.7;

            world.spawnParticle(
                    Particle.INSTANT_EFFECT,
                    location.clone().add(x, 1.5, z),
                    1, 0, 0, 0, 0
            );
        }

        // Random sparkles
        if (Math.random() < 0.3) {
            world.spawnParticle(
                    Particle.END_ROD,
                    location.clone().add(0, 1.5, 0),
                    1, 0.3, 0.3, 0.3, 0
            );
        }
    }

    private void playFutureSightEndEffect(Location location) {
        World world = location.getWorld();

        // Shattering shield effect
        for (int i = 0; i < 20; i++) {
            Vector direction = new Vector(
                    Math.random() - 0.5,
                    Math.random() - 0.5,
                    Math.random() - 0.5
            ).normalize().multiply(0.5);

            Location particleLoc = location.clone().add(0, 1.5, 0);
            world.spawnParticle(
                    Particle.WITCH,
                    particleLoc,
                    0, direction.getX(), direction.getY(), direction.getZ(), 0.5
            );
        }

        // Sound effect
        world.playSound(location, Sound.BLOCK_GLASS_BREAK, 0.5f, 2.0f);
    }
}