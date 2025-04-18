package com.michael.mmorpg.skills.chainwarden;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class MysticChainStrikeSkill extends Skill {
    private final double damage;

    public MysticChainStrikeSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Use base melee targeting
        LivingEntity target = getMeleeTarget(player, targetRange);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store current target for messages
        currentTarget = target;

        // Play initial strike effect
        playStrikeEffect(player, target);

        // Add slight delay for visual effect before damage
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    setSkillSuccess(false);
                    return;
                }

                // Mark damage as magic damage
                target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
                target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

                // Apply damage
                target.damage(0.1, player); // Small damage to trigger damage event

                // Clean up metadata
                target.removeMetadata("magic_damage", plugin);
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);

                // Create impact effects
                createImpactEffects(target.getLocation().add(0, 1, 0));

                setSkillSuccess(true);
            }
        }.runTaskLater(plugin, 5L); // Quarter second delay
    }

    private void playStrikeEffect(Player player, LivingEntity target) {
        Location start = player.getLocation().add(0, 1, 0);
        Location end = target.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Create chain strike effect path
        for (double progress = 0; progress <= 1.0; progress += 0.1) {
            Location point = start.clone().add(
                    end.clone().subtract(start).toVector().multiply(progress)
            );

            // Add slight randomization for magic effect
            point.add(
                    Math.random() * 0.2 - 0.1,
                    Math.random() * 0.2 - 0.1,
                    Math.random() * 0.2 - 0.1
            );

            // Chain particles
            world.spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);

            // Magic energy particles
            world.spawnParticle(Particle.WITCH, point, 1, 0.1, 0.1, 0.1, 0);
        }

        // Initial cast sounds
        world.playSound(start, Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.2f);
        world.playSound(start, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.5f, 1.0f);
    }

    private void createImpactEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Magic impact burst
        world.spawnParticle(Particle.WITCH, location, 30, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticle(Particle.CRIT, location, 20, 0.2, 0.2, 0.2, 0.5);

        // Chain impact particles
        world.spawnParticle(Particle.CRIT, location, 15, 0.2, 0.2, 0.2, 0.3);

        // Impact sounds
        world.playSound(location, Sound.BLOCK_CHAIN_HIT, 1.0f, 1.2f);
        world.playSound(location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.0f);
    }
}