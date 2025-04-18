package com.michael.mmorpg.skills.priest;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class MendSkill extends Skill {
    private final double healAmount;
    private final double range;

    public MendSkill(ConfigurationSection config) {
        super(config);
        this.healAmount = config.getDouble("healamount", 15.0);
        this.range = config.getDouble("range", 12.0);
        this.isHarmfulSkill = false;
    }

    @Override
    public void execute(Player healer) {
        // Get target and let validateTarget handle the party/self targeting rules
        currentTarget = getTargetEntity(healer, range);

        // If no target found, default to self (which is always valid)
        if (currentTarget == null) {
            currentTarget = healer;
        }

        // validateTarget will handle all the party/player validation rules
        if (validateTarget(healer, currentTarget)) {
            return;  // Validation failed, appropriate message was sent
        }

        if (hasCastTime) {
            startCasting(healer);
            return;
        }

        performSkill(healer);
    }

    @Override
    protected void performSkill(Player healer) {
        // We don't need additional validation here because validateTarget already:
        // - Ensured target is a player
        // - Checked party membership
        // - Allowed self-targeting

        double maxHealth = currentTarget.getMaxHealth();
        double currentHealth = currentTarget.getHealth();
        double newHealth = Math.min(maxHealth, currentHealth + healAmount);

        // Calculate actual healing done
        double actualHealing = newHealth - currentHealth;

        // Apply the healing
        currentTarget.setHealth(newHealth);

        // Display healing amount
        if (actualHealing > 0) {
            plugin.getDamageDisplayManager().spawnDamageDisplay(
                    currentTarget.getLocation(),
                    actualHealing,
                    DamageDisplayManager.DamageType.HEALING
            );
        }

        playHealEffects(currentTarget.getLocation());
        setSkillSuccess(true);
    }

    private void playHealEffects(Location location) {
        // Gentle ascending light particles
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.add(0, 0.5, 0),
                8, 0.3, 0.5, 0.3, 0.02
        );

        // Heavenly sound
        location.getWorld().playSound(
                location,
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.8f,
                1.5f
        );
    }
}