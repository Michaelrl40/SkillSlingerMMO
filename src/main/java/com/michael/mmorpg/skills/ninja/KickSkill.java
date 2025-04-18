package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class KickSkill extends Skill {
    private final double damage;
    private final long silenceDuration;
    private final int silenceIntensity;

    public KickSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 5.0);
        // Duration in milliseconds (config in seconds)
        this.silenceDuration = (long)(config.getDouble("silenceduration", 2.0) * 1000);
        this.silenceIntensity = config.getInt("silenceintensity", 1);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target in melee range
        LivingEntity target = getMeleeTarget(player, targetRange);

        // Validate target
        if (target == null || validateTarget(player, target)) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store current target for messages
        currentTarget = target;

        // Apply damage
        applySkillDamage(target);

        // Apply silence effect
        if (target instanceof Player) {
            Player playerTarget = (Player) target;

            // Check if target is casting and interrupt if they are
            if (playerTarget.hasMetadata("casting")) {
                // Get the skill being cast if possible
                String skillName = "a skill";
                for (MetadataValue meta : playerTarget.getMetadata("casting")) {
                    if (meta.value() instanceof Skill) {
                        Skill skill = (Skill) meta.value();
                        skillName = skill.getName();
                        // Cancel the cast
                        skill.cancelCast(playerTarget);
                    }
                }

                // Notify both players
                playerTarget.sendMessage("§c✦ Your " + skillName + " was interrupted by a kick!");
                player.sendMessage("§6✦ You interrupted " + playerTarget.getName() + "'s " + skillName + "!");

                // Interrupt effects
                playInterruptEffects(playerTarget);
            }

            // Apply silence effect after interrupt
            StatusEffect silence = new StatusEffect(CCType.SILENCE, silenceDuration, player, silenceIntensity);
            plugin.getStatusEffectManager().applyEffect(playerTarget, silence);
        }

        // Play effects
        playKickEffects(player, target);

        setSkillSuccess(true);
    }

    private void applySkillDamage(LivingEntity target) {
        // Set damage metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, plugin));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        // Apply the damage
        target.damage(damage);

        // Clean up metadata after a tick
        new BukkitRunnable() {
            @Override
            public void run() {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void playKickEffects(Player player, LivingEntity target) {
        // Particle effects
        target.getWorld().spawnParticle(
                Particle.CRIT,
                target.getLocation().add(0, 1, 0),
                10, 0.3, 0.3, 0.3, 0.1
        );

        // Sound effect
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0f,
                1.2f
        );
    }

    private void playInterruptEffects(Player target) {
        // Particles to show interruption
        target.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                target.getLocation().add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0
        );

        // Sound for interruption
        target.getWorld().playSound(
                target.getLocation(),
                Sound.BLOCK_ANVIL_LAND,
                0.5f,
                1.5f
        );
    }
}