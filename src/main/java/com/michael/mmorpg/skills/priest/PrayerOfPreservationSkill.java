package com.michael.mmorpg.skills.priest;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PrayerOfPreservationSkill extends Skill {
    private final double initialHeal;
    private final double hotHealing;
    private final int duration;
    private final int tickRate;
    private final double range;

    public PrayerOfPreservationSkill(ConfigurationSection config) {
        super(config);
        this.initialHeal = config.getDouble("initialheal", 5.0);
        this.hotHealing = config.getDouble("hothealing", 3.0);
        this.duration = config.getInt("hotduration", 8);
        this.tickRate = config.getInt("tickrate", 2);
        this.range = config.getDouble("range", 10.0);
        this.isHarmfulSkill = false;  // Enable party member targeting
    }

    @Override
    public void execute(Player healer) {
        // Get target using normal targeting system
        currentTarget = getTargetEntity(healer, range);

        // Default to self if no target found - self targeting is always valid
        if (currentTarget == null) {
            currentTarget = healer;
        }

        // Let validateTarget handle all party/player validation
        if (validateTarget(healer, currentTarget)) {
            return;  // Target validation failed, message already sent
        }

        if (hasCastTime) {
            startCasting(healer);
            return;
        }

        performSkill(healer);
    }

    @Override
    protected void performSkill(Player healer) {
        // No need for additional validation here since validateTarget handled:
        // - Ensuring target is a player
        // - Checking party membership
        // - Allowing self-targeting

        // Apply initial heal if configured
        if (initialHeal > 0) {
            applyHealing(currentTarget, initialHeal);
        }

        // Start heal over time effect
        final LivingEntity healTarget = currentTarget;
        new BukkitRunnable() {
            int ticksElapsed = 0;
            int totalTicks = duration * 20;
            int ticksBetweenHeals = tickRate * 20;

            @Override
            public void run() {
                if (ticksElapsed >= totalTicks || !healTarget.isValid() || healTarget.isDead()) {
                    this.cancel();
                    if (healTarget instanceof Player) {
                        ((Player) healTarget).sendMessage("§7✦ Your preservation effect fades away.");
                    }
                    return;
                }

                if (ticksElapsed % ticksBetweenHeals == 0) {
                    applyHealing(healTarget, hotHealing);
                    playHoTEffect(healTarget.getLocation());
                }

                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        setSkillSuccess(true);
    }

    private void applyHealing(LivingEntity target, double amount) {
        double maxHealth = target.getMaxHealth();
        double currentHealth = target.getHealth();
        double newHealth = Math.min(maxHealth, currentHealth + amount);

        // Calculate actual healing done
        double actualHealing = newHealth - currentHealth;

        // Apply the healing
        target.setHealth(newHealth);

        // Show healing using DamageDisplayManager
        if (actualHealing > 0) {
            plugin.getDamageDisplayManager().spawnDamageDisplay(
                    target.getLocation(),
                    actualHealing,
                    DamageDisplayManager.DamageType.HEALING
            );
        }
    }

    private void playInitialEffect(Location location) {
        // Initial blessing effect
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.add(0, 1, 0),
                12, 0.3, 0.5, 0.3, 0.02
        );

        // Divine sound
        location.getWorld().playSound(
                location,
                Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                0.7f,
                1.2f
        );
    }

    private void playHoTEffect(Location location) {
        // Subtle healing pulse
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.add(0, 1, 0),
                3, 0.2, 0.2, 0.2, 0.01
        );

        // Gentle healing chime
        location.getWorld().playSound(
                location,
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.3f,
                1.5f
        );
    }
}