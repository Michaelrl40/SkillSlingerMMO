package com.michael.mmorpg.skills.rogue;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class PunchSkill extends Skill {

    private final double damage;

    public PunchSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 10.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target within melee range
        LivingEntity target = getMeleeTarget(player, targetRange);

        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store target for reference (used in broadcastLocalSkillMessage)
        currentTarget = target;

        // Validate target for party/self restrictions
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        // Deal damage
        target.damage(0.1, player); // Small amount to trigger damage event

        // Apply small knockback
        Vector knockbackDirection = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.3);
        knockbackDirection.setY(0.1); // Small vertical component
        target.setVelocity(target.getVelocity().add(knockbackDirection));

        // Visual and sound effects
        Location targetLoc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.CRIT, targetLoc, 8, 0.3, 0.3, 0.3, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 1.2f);

        // Spawn damage display
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                targetLoc,
                damage,
                DamageDisplayManager.DamageType.NORMAL
        );

        setSkillSuccess(true);
    }
}