package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class DarkClawSkill extends Skill {
    private final double initialDamage;
    private final double dotDamage;
    private final int dotDuration;
    private final int tickRate;
    private final int slowDuration;
    private final int slowIntensity;

    public DarkClawSkill(ConfigurationSection config) {
        super(config);
        this.initialDamage = config.getDouble("initialdamage", 8.0);
        this.dotDamage = config.getDouble("dotdamage", 4.0);
        this.dotDuration = config.getInt("dotduration", 5);
        this.tickRate = config.getInt("tickrate", 1);
        this.slowDuration = config.getInt("slowduration", 3);
        this.slowIntensity = config.getInt("slowintensity", 1);
    }

    @Override
    public void execute(Player caster) {
        // Get target using melee targeting
        currentTarget = getMeleeTarget(caster, targetRange);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in melee range!");
            return;
        }

        // Start casting if has cast time
        if (hasCastTime) {
            caster.sendMessage("§5✦ Slashing " + currentTarget.getName() + " with dark claws...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        LivingEntity target = currentTarget;

        // Apply initial hit
        dealInitialDamage(caster, target);

        // Start DOT effect
        applyDotEffect(caster, target);

        // Apply slow effect
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                slowDuration * 20,  // Convert to ticks
                slowIntensity - 1,  // Zero-based intensity
                false,
                true
        ));

        // Play initial hit effects
        playHitEffects(target.getLocation());

        // Set cooldown
        plugin.getSkillManager().setCooldown(caster, getName(), getCooldown());

        // Send messages
        caster.sendMessage("§5✦ You slash " + target.getName() + " with dark claws!");
        if (target instanceof Player) {
            ((Player)target).sendMessage("§5✦ " + caster.getName() + " slashes you with dark claws!");
        }

        setSkillSuccess(true);
    }

    private void dealInitialDamage(Player caster, LivingEntity target) {
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, initialDamage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        target.damage(0.1, caster);
    }

    private void applyDotEffect(Player caster, LivingEntity target) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = dotDuration;

            @Override
            public void run() {
                // Check if effect should end
                if (ticks >= maxTicks || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }

                // Apply DOT damage
                target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, dotDamage));
                target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
                target.damage(0.1, caster);

                // Play DOT tick effect
                playDotTickEffect(target.getLocation());

                ticks++;
            }
        }.runTaskTimer(plugin, tickRate * 20L, tickRate * 20L);
    }

    private void playHitEffects(Location location) {
        // Initial slash effect
        for (double i = 0; i < Math.PI; i += Math.PI / 8) {
            double x = Math.cos(i) * 1.5;
            double z = Math.sin(i) * 1.5;
            Location particleLoc = location.clone().add(x, 1, z);

            location.getWorld().spawnParticle(
                    Particle.DUST,
                    particleLoc,
                    3, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(75, 0, 130), 1.5f)
            );
        }

        // Impact effect
        location.getWorld().spawnParticle(
                Particle.WITCH,
                location.clone().add(0, 1, 0),
                15, 0.3, 0.3, 0.3, 0.1
        );

        // Sound effect
        location.getWorld().playSound(
                location,
                Sound.ENTITY_PHANTOM_BITE,
                1.0f,
                0.5f
        );
    }

    private void playDotTickEffect(Location location) {
        // DOT tick particles
        location.getWorld().spawnParticle(
                Particle.DUST,
                location.clone().add(0, 1, 0),
                5, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(75, 0, 130), 1)
        );

        // Additional dark energy effect
        location.getWorld().spawnParticle(
                Particle.WITCH,
                location.clone().add(0, 1, 0),
                3, 0.2, 0.2, 0.2, 0
        );
    }
}