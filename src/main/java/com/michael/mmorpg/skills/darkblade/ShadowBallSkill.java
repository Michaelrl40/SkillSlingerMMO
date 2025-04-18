package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ShadowBallSkill extends Skill {
    private final double damage;
    private final double height;

    public ShadowBallSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 18.0);
        this.height = config.getDouble("height", 10.0);
    }

    @Override
    public void execute(Player caster) {
        currentTarget = getTargetEntity(caster, targetRange);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        if (hasCastTime) {
            caster.sendMessage("§5✦ Summoning shadow ball above " + currentTarget.getName() + "...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        LivingEntity target = currentTarget;
        Location spawnLoc = target.getLocation().clone().add(0, height, 0);

        // Subtle spawn effect
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.2f);

        caster.sendMessage("§5✦ You summon a ball of shadows above " + target.getName() + "!");

        new BukkitRunnable() {
            private static final int MAX_TICKS = 20;
            private int ticks = 0;
            private Location currentLoc = spawnLoc.clone();

            @Override
            public void run() {
                if (ticks >= MAX_TICKS || !target.isValid() || target.isDead()) {
                    hitTarget(target, caster, currentLoc);
                    cancel();
                    return;
                }

                // Update target location for tracking
                Location targetLoc = target.getLocation().add(0, 1, 0);
                Vector direction = targetLoc.toVector().subtract(currentLoc.toVector()).normalize();

                // Calculate smooth movement
                double progress = (double) ticks / MAX_TICKS;
                double speed = height / MAX_TICKS * (1 + progress); // Accelerate as it falls

                // Move towards target
                currentLoc.add(direction.multiply(speed));

                // Core shadow ball - just one dense black particle
                currentLoc.getWorld().spawnParticle(
                        Particle.DUST,
                        currentLoc,
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(1, 1, 1), 1)
                );

                // Subtle trail - very few particles
                if (ticks % 2 == 0) {
                    currentLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            currentLoc,
                            1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(20, 0, 30), 0.7f)
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void hitTarget(LivingEntity target, Player caster, Location impactLocation) {
        // Create impact effect at the target's location
        Location location = target.getLocation().add(0, 1, 0);

        // Minimal explosion effect
        location.getWorld().spawnParticle(
                Particle.DUST,
                location,
                15, 0.3, 0.3, 0.3, 0.1,
                new Particle.DustOptions(Color.fromRGB(1, 1, 1), 1)
        );

        // Quick dark burst
        location.getWorld().playSound(location, Sound.ENTITY_PHANTOM_DEATH, 0.8f, 1.2f);

        // Apply damage only to the original target
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        target.damage(0.1, caster);

        // Show more impact particles just for visual effect
        location.getWorld().spawnParticle(
                Particle.SQUID_INK,
                location,
                8, 0.2, 0.2, 0.2, 0.05
        );

        impactLocation.getWorld().spawnParticle(
                Particle.SMOKE,
                impactLocation,
                10, 0.1, 0.1, 0.1, 0.05
        );
    }
}