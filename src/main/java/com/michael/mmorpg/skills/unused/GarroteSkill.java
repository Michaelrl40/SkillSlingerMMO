package com.michael.mmorpg.skills.unused;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.metadata.FixedMetadataValue;

public class GarroteSkill extends Skill {
    private final double damage;
    private final double staminaDrainPercent;

    public GarroteSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.staminaDrainPercent = config.getDouble("staminadrainpercent", 50.0) / 100.0;
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

        // Apply damage first
        applyDamage(target);

        // If target is a player, drain their stamina
        if (target instanceof Player) {
            drainStamina((Player) target);
        }

        // Play effects
        playGarroteEffects(player, target);

        setSkillSuccess(true);
    }

    private void applyDamage(LivingEntity target) {
        // Set damage metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, plugin));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        // Apply the damage
        target.damage(damage);

        // Clean up metadata after a tick
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
            }
        }.runTaskLater(plugin, 1L);

        // Show damage number
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                target.getLocation(),
                damage,
                DamageDisplayManager.DamageType.NORMAL
        );
    }

    private void drainStamina(Player target) {
        // Get target's player data
        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);
        if (targetData == null) return;

        // Calculate stamina drain
        double currentStamina = targetData.getCurrentStamina();
        double drainAmount = currentStamina * staminaDrainPercent;

        // Apply the drain
        targetData.setCurrentStamina(currentStamina - drainAmount);

        // Show the stamina drain effect in the damage display system
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                target.getLocation().add(0, 0.5, 0),  // Slightly offset from damage number
                drainAmount,
                DamageDisplayManager.DamageType.NORMAL
                // Green color for stamina
        );

        // Notify both players
        target.sendMessage(String.format("§2✦ Your stamina was drained by %.1f!", drainAmount));
        Player attacker = (Player) plugin.getServer().getPlayer(currentTarget.getName());
        if (attacker != null) {
            attacker.sendMessage(String.format("§2✦ Drained %.1f stamina from %s!",
                    drainAmount, target.getName()));
        }
    }

    private void playGarroteEffects(Player player, LivingEntity target) {
        // Create a choking/strangling particle effect
        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().add(0, 1.8, 0),  // At neck height
                15,  // Number of particles
                0.2, 0.1, 0.2,  // Concentrated around the neck area
                0,  // Speed
                new Particle.DustOptions(Color.fromRGB(50, 50, 50), 1.0f)  // Dark grey color
        );

        // Add some "struggle" particles
        target.getWorld().spawnParticle(
                Particle.SMOKE,
                target.getLocation().add(0, 1, 0),
                10, 0.3, 0.5, 0.3, 0.05
        );

        // Choking sound effect
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,  // A scratchy/choking sound
                1.0f,
                0.5f  // Lower pitch for a more menacing sound
        );

        // Rope/wire sound
        target.getWorld().playSound(
                target.getLocation(),
                Sound.BLOCK_TRIPWIRE_CLICK_ON,
                0.8f,
                0.7f
        );
    }
}