package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class BackstabPassive extends Skill {
    private final double damageMuliplier;

    public BackstabPassive(ConfigurationSection config) {
        super(config);
        this.damageMuliplier = config.getDouble("damagemultiplier", 2.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Not used for passive skills
    }

    /**
     * Checks if a hit qualifies as a backstab
     * @param attacker The player attacking
     * @param target The entity being attacked
     * @return true if this is a backstab, false otherwise
     */
    public static boolean isBackstab(Player attacker, Entity target) {
        if (!(target instanceof LivingEntity)) {
            return false;
        }

        // Get the direction the target is facing
        Vector targetDirection = target.getLocation().getDirection();

        // Get the vector from target to attacker (important: target TO attacker, not the other way around)
        Vector toAttacker = attacker.getLocation().subtract(target.getLocation()).toVector();

        // Project vectors to horizontal plane for more reliable calculation
        targetDirection.setY(0);
        toAttacker.setY(0);

        // Normalize vectors
        targetDirection.normalize();
        toAttacker.normalize();

        // Calculate dot product
        double dot = toAttacker.dot(targetDirection);

        // If dot product < -0.5, attacker is behind target (120-degree cone)
        // Changed from > 0.5 to < -0.5 because we want the opposite direction
        return dot < -0.5;
    }

    /**
     * Gets the damage multiplier for backstabs
     * @return The configured damage multiplier
     */
    public double getDamageMultiplier() {
        return this.damageMuliplier;
    }
}