package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ChronoShiftSkill extends Skill {
    private final double range;

    public ChronoShiftSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 15.0);
        this.isHarmfulSkill = false;  // Allow targeting of both allies and enemies
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, range);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        // Skip normal validation and do our own
        if (currentTarget.equals(caster)) {
            caster.sendMessage("§c✦ You cannot swap with yourself!");
            return;
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

        // Store original locations
        Location casterLocation = caster.getLocation();
        Location targetLocation = target.getLocation();

        // Store original directions
        float casterYaw = casterLocation.getYaw();
        float casterPitch = casterLocation.getPitch();
        float targetYaw = targetLocation.getYaw();
        float targetPitch = targetLocation.getPitch();

        // Create fade effect at both locations
        playShiftOutEffect(casterLocation);
        playShiftOutEffect(targetLocation);

        // Schedule teleport after short delay for effect
        new BukkitRunnable() {
            @Override
            public void run() {
                // Set new locations preserving original directions
                Location newCasterLoc = targetLocation.clone();
                newCasterLoc.setYaw(casterYaw);
                newCasterLoc.setPitch(casterPitch);

                Location newTargetLoc = casterLocation.clone();
                newTargetLoc.setYaw(targetYaw);
                newTargetLoc.setPitch(targetPitch);

                // Perform teleports
                caster.teleport(newCasterLoc);
                target.teleport(newTargetLoc);

                // Play arrival effects
                playShiftInEffect(newCasterLoc);
                playShiftInEffect(newTargetLoc);

                // Success messages
                if (target instanceof Player) {
                    ((Player) target).sendMessage("§b✦ You've been chronoshifted by " + caster.getName() + "!");
                }
                caster.sendMessage("§b✦ Swapped positions with " + (target instanceof Player ? target.getName() : "target") + "!");
            }
        }.runTaskLater(plugin, 5L);  // Quarter second delay for effect

        setSkillSuccess(true);
    }

    private void playShiftOutEffect(Location location) {
        World world = location.getWorld();

        // Create spiral effect
        for (double y = 0; y < 2; y += 0.1) {
            double radius = 0.8 * (2.0 - y) / 2.0;  // Radius shrinks as height increases
            double angle = y * 20;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            world.spawnParticle(
                    Particle.PORTAL,
                    location.clone().add(x, y, z),
                    1, 0, 0, 0, 0
            );
        }

        // Clock distortion particles
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            double x = Math.cos(angle);
            double z = Math.sin(angle);

            world.spawnParticle(
                    Particle.WITCH,
                    location.clone().add(x, 1, z),
                    1, 0, 0, 0, 0
            );
        }

        // Ground ring
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2 * i / 20;
            double x = 1.2 * Math.cos(angle);
            double z = 1.2 * Math.sin(angle);

            world.spawnParticle(
                    Particle.END_ROD,
                    location.clone().add(x, 0.1, z),
                    1, 0, 0, 0, 0
            );
        }

        // Sound effects
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);
        world.playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 2.0f);
    }

    private void playShiftInEffect(Location location) {
        World world = location.getWorld();

        // Reverse spiral effect
        for (double y = 2; y > 0; y -= 0.1) {
            double radius = 0.8 * (2.0 - y) / 2.0;
            double angle = y * 20;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            world.spawnParticle(
                    Particle.PORTAL,
                    location.clone().add(x, y, z),
                    1, 0, 0, 0, 0
            );
        }

        // Arrival burst
        world.spawnParticle(
                Particle.FLASH,
                location.clone().add(0, 1, 0),
                1, 0, 0, 0, 0
        );

        // Ground shockwave
        for (double radius = 0; radius <= 1.2; radius += 0.2) {
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2 * i / 12;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);

                world.spawnParticle(
                        Particle.END_ROD,
                        location.clone().add(x, 0.1, z),
                        1, 0, 0, 0, 0
                );
            }
        }

        // Sound effects
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
    }
}