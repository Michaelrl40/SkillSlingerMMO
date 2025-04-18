package com.michael.mmorpg.skills.renegade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;

public class ResonatingStrikeSkill extends Skill {
    private final double damage;
    private final double criticalThreshold;
    private final double criticalMultiplier;

    public ResonatingStrikeSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.criticalThreshold = config.getDouble("criticalThreshold", 0.3);
        this.criticalMultiplier = config.getDouble("criticalMultiplier", 1.8);
    }

    @Override
    protected void performSkill(Player player) {
        // Find nearest marked target regardless of range
        LivingEntity target = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof LivingEntity && entity.hasMetadata("sonic_wave_marked")) {
                LivingEntity livingEntity = (LivingEntity) entity;
                double distance = livingEntity.getLocation().distance(player.getLocation());

                if (distance < closestDistance) {
                    target = livingEntity;
                    closestDistance = distance;
                }
            }
        }

        if (target == null) {
            player.sendMessage("§c✦ No marked targets found!");
            setSkillSuccess(false);
            return;
        }

        // Validate target
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Calculate damage
        double finalDamage = damage;

        // Check for critical strike
        if (target.getHealth() <= target.getMaxHealth() * criticalThreshold) {
            finalDamage *= criticalMultiplier;
            player.sendMessage("§6✦ Critical Strike!");
        }

        // Dash to target
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.toVector().subtract(player.getLocation().toVector()).normalize();

        // Position slightly behind the target for the strike
        Location strikeLocation = targetLoc.clone().subtract(direction.clone().multiply(2));

        // Teleport and face target
        player.teleport(strikeLocation);
        player.setVelocity(new Vector(0, 0, 0));

        Location playerLoc = player.getLocation();
        playerLoc.setDirection(target.getLocation().subtract(playerLoc).toVector());
        player.teleport(playerLoc);

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, finalDamage));
        target.damage(finalDamage, player);

        // Remove the Sonic Wave mark
        target.removeMetadata("sonic_wave_marked", plugin);

        setSkillSuccess(true);
    }
} 