package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class TerrorizeSkill extends Skill {
    private final double sleepDuration;
    private final double damage;
    private final double nightmareDamage;

    public TerrorizeSkill(ConfigurationSection config) {
        super(config);
        this.sleepDuration = config.getDouble("sleepduration", 4000);
        this.damage = config.getDouble("damage", 5.0);
        this.nightmareDamage = config.getDouble("nightmaredamage", 3.0);
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, targetRange);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        // Start casting if has cast time
        if (hasCastTime) {
            caster.sendMessage("§5✦ Channeling dark slumber into " + currentTarget.getName() + "'s mind...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        LivingEntity target = currentTarget;


        // Apply sleep effect
        StatusEffect sleepEffect = new StatusEffect(CCType.SLEEP, (long) sleepDuration, caster, 1);
        plugin.getStatusEffectManager().applyEffect((Player) target, sleepEffect);

        // Play initial sleep effect
        playSleepEffect(target.getLocation());

        // Start nightmare damage over time
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int) (sleepDuration / 1000); // Convert to seconds

            @Override
            public void run() {
                if (!plugin.getStatusEffectManager().hasEffect((Player) target, CCType.SLEEP) ||
                        ticks >= maxTicks || !target.isValid()) {
                    cancel();
                    return;
                }

                // Nightmare effect
                playNightmareEffect(target.getLocation());

                ticks++;
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second

        // Send messages
        caster.sendMessage("§5✦ You force " + target.getName() + " into a dark slumber!");
        if (target instanceof Player) {
            ((Player)target).sendMessage("§5✦ " + caster.getName() + " forces you into a nightmare-filled sleep!");
        }

        setSkillSuccess(true);
    }

    private void playSleepEffect(Location location) {
        // Dark sleep cloud
        for (int i = 0; i < 2; i++) {
            location.getWorld().spawnParticle(
                    Particle.DUST,
                    location.clone().add(0, 1, 0),
                    20, 0.5, 0.5, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(75, 0, 130), 2)
            );
        }

        // Sleep particles rising
        for (int i = 0; i < 8; i++) {
            Vector offset = new Vector(
                    Math.cos(i * Math.PI / 4) * 0.5,
                    0,
                    Math.sin(i * Math.PI / 4) * 0.5
            );
            Location particleLoc = location.clone().add(offset);

            for (double y = 0; y < 2; y += 0.2) {
                location.getWorld().spawnParticle(
                        Particle.WITCH,
                        particleLoc.clone().add(0, y, 0),
                        1, 0.1, 0, 0.1, 0
                );
            }
        }

        // Sleep sounds
        location.getWorld().playSound(location, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.5f);
        location.getWorld().playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.5f, 0.5f);
    }

    private void playNightmareEffect(Location location) {
        // Nightmare wisps
        location.getWorld().spawnParticle(
                Particle.DUST,
                location.clone().add(0, 1, 0),
                10, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1)
        );

        // Dark energy
        location.getWorld().spawnParticle(
                Particle.WITCH,
                location.clone().add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0.05
        );

        // Nightmare sounds
        if (Math.random() < 0.3) { // 30% chance each tick
            location.getWorld().playSound(
                    location,
                    Sound.ENTITY_PHANTOM_AMBIENT,
                    0.3f,
                    0.5f
            );
        }
    }
}