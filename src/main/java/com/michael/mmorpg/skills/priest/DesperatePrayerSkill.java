package com.michael.mmorpg.skills.priest;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class DesperatePrayerSkill extends Skill {
    private final double baseHeal;
    private final double lowHealthBonus;
    private final double healthThreshold;

    public DesperatePrayerSkill(ConfigurationSection config) {
        super(config);
        this.baseHeal = config.getDouble("baseheal", 20.0);
        this.lowHealthBonus = config.getDouble("lowhealthbonus", 15.0);
        this.healthThreshold = config.getDouble("healththreshold", 0.3);
        this.isHarmfulSkill = false;
    }

    @Override
    protected void performSkill(Player healer) {
        // Calculate current health percentage
        double maxHealth = healer.getMaxHealth();
        double currentHealth = healer.getHealth();
        double healthPercentage = currentHealth / maxHealth;

        // Calculate healing amount based on health percentage
        double healingAmount = baseHeal;
        boolean isLowHealth = false;

        if (healthPercentage <= healthThreshold) {
            healingAmount += lowHealthBonus;
            isLowHealth = true;
        }

        // Apply the healing
        double newHealth = Math.min(maxHealth, currentHealth + healingAmount);
        healer.setHealth(newHealth);

        // Show healing amount using DamageDisplayManager
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                healer.getLocation(),
                healingAmount,
                DamageDisplayManager.DamageType.HEALING
        );

        // Play effects based on whether it was a critical heal
        if (isLowHealth) {
            playLowHealthEffects(healer.getLocation());
            broadcastLocalSkillMessage(healer, "§c§l✦ " + healer.getName() +
                    " channels a critical emergency heal!");
        } else {
            playNormalEffects(healer.getLocation());
            broadcastLocalSkillMessage(healer, "§a✦ " + healer.getName() +
                    " channels emergency healing!");
        }

        setSkillSuccess(true);
    }

    // Rest of your effects methods remain unchanged
    private void playLowHealthEffects(Location location) {
        // More intense effects for critical healing

        // Burst of divine light
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.add(0, 1, 0),
                20, 0.5, 1.0, 0.5, 0.05
        );

        // Ring of healing energy
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            Location particleLoc = location.clone().add(x, 0, z);

            location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    3, 0.1, 0.1, 0.1, 0.02
            );
        }

        // Dramatic sound combination
        location.getWorld().playSound(
                location,
                Sound.BLOCK_BEACON_ACTIVATE,
                0.8f,
                1.2f
        );
        location.getWorld().playSound(
                location,
                Sound.ENTITY_PLAYER_LEVELUP,
                0.5f,
                1.5f
        );
    }

    private void playNormalEffects(Location location) {
        // Standard healing effects
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.add(0, 1, 0),
                12, 0.3, 0.5, 0.3, 0.02
        );

        location.getWorld().playSound(
                location,
                Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.8f,
                1.2f
        );
    }
}