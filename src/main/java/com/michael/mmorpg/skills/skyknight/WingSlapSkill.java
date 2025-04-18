package com.michael.mmorpg.skills.skyknight;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class WingSlapSkill extends Skill {
    private final double damage;
    private final double knockbackStrength;
    private final double upwardKnockback;

    public WingSlapSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 6.0);
        this.knockbackStrength = config.getDouble("knockbackstrength", 2.0);
        this.upwardKnockback = config.getDouble("upwardknockback", 0.3);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target in melee range
        LivingEntity target = getMeleeTarget(player, targetRange);

        if (target == null) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Set current target for skill systems
        this.currentTarget = target;

        // Perform the wing slap
        executeWingSlap(player, target);
        setSkillSuccess(true);
    }

    private void executeWingSlap(Player player, LivingEntity target) {
        Location playerLoc = player.getLocation();
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.clone().subtract(playerLoc).toVector().normalize();

        // Apply damage
        target.setMetadata("skill_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        target.damage(damage, player);

        // Calculate knockback
        Vector knockback = direction.clone()
                .multiply(knockbackStrength)
                .setY(upwardKnockback);
        target.setVelocity(knockback);

        // Create wing effect particles
        createWingEffect(player, direction);

        // Play sound effects
        player.getWorld().playSound(playerLoc, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);
        player.getWorld().playSound(playerLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
    }

    private void createWingEffect(Player player, Vector direction) {
        Location loc = player.getLocation().add(0, 1, 0);
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        Vector left = right.clone().multiply(-1);

        // Create wing sweep effects on both sides
        for (int i = 0; i < 8; i++) {
            double progress = i / 7.0; // 0 to 1
            double offset = Math.sin(progress * Math.PI) * 2; // Create arc effect

            // Right wing
            Location rightWing = loc.clone().add(
                    right.clone().multiply(progress * 2)
                            .add(direction.clone().multiply(offset))
            );
            player.getWorld().spawnParticle(
                    Particle.CLOUD,
                    rightWing,
                    3, 0.1, 0.1, 0.1, 0.02
            );

            // Left wing
            Location leftWing = loc.clone().add(
                    left.clone().multiply(progress * 2)
                            .add(direction.clone().multiply(offset))
            );
            player.getWorld().spawnParticle(
                    Particle.CLOUD,
                    leftWing,
                    3, 0.1, 0.1, 0.1, 0.02
            );
        }

        // Impact particles at target
        Location targetLoc = player.getLocation().add(direction.multiply(2));
        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                targetLoc,
                2, 0.2, 0.2, 0.2, 0
        );
    }
}