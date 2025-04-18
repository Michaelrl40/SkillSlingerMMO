package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.models.PlayerData;

public class VenomousEmbraceSkill extends Skill {
    private final double damagePerToxin;
    private final double speedBoostDuration;
    private final int speedBoostLevel;
    private final double minToxinRequired;
    private final double maxDamageMultiplier;

    public VenomousEmbraceSkill(ConfigurationSection config) {
        super(config);
        this.damagePerToxin = config.getDouble("damagePerToxin", 0.5);
        this.speedBoostDuration = config.getDouble("speedBoostDuration", 3.0);
        this.speedBoostLevel = config.getInt("speedBoostLevel", 1);
        this.minToxinRequired = config.getDouble("minToxinRequired", 10.0);
        this.maxDamageMultiplier = config.getDouble("maxDamageMultiplier", 2.0);
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) {
            setSkillSuccess(false);
            return;
        }

        // Check toxin requirement
        if (playerData.getCurrentToxin() < minToxinRequired) {
            player.sendMessage("§c☠ Need at least " + minToxinRequired + " toxin to use Venomous Embrace!");
            setSkillSuccess(false);
            return;
        }

        // Use base melee targeting
        LivingEntity target = getMeleeTarget(player, targetRange);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store current target for messages
        currentTarget = target;

        // Calculate damage based on current toxin
        double currentToxin = playerData.getCurrentToxin();
        double damage = calculateDamage(currentToxin);

        // Start the embrace effect sequence
        startEmbraceEffect(player, target, playerData, currentToxin, damage);
    }

    private double calculateDamage(double toxinAmount) {
        double damage = toxinAmount * damagePerToxin;
        if (toxinAmount > 50) {
            double scaling = Math.min(toxinAmount / 100.0, maxDamageMultiplier);
            damage *= scaling;
        }
        return damage;
    }

    private void startEmbraceEffect(Player player, LivingEntity target, PlayerData playerData,
                                    double currentToxin, double damage) {
        // Play initial cast effect
        playInitialEffect(player, target);

        // Add slight delay before damage
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    setSkillSuccess(false);
                    return;
                }

                executeEmbrace(player, target, playerData, currentToxin, damage);
            }
        }.runTaskLater(plugin, 10L); // 0.5 second delay
    }

    private void executeEmbrace(Player player, LivingEntity target, PlayerData playerData,
                                double initialToxin, double damage) {
        // Apply damage through skill damage system
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(0.1, player);
        target.removeMetadata("skill_damage", plugin);
        target.removeMetadata("skill_damage_amount", plugin);

        // Consume all toxin
        playerData.setCurrentToxin(0);

        // Apply speed boost with potential bonus duration
        int speedDuration = (int)(speedBoostDuration * 20);
        if (initialToxin >= 50) {
            speedDuration *= 1.5;
        }

        // Apply speed buff
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                speedDuration,
                speedBoostLevel - 1,
                false,
                true
        ));

        // Create impact effects
        createImpactEffects(player, target);

        // Send feedback messages
        String damageMsg = String.format("%.1f", damage);
        player.sendMessage("§2☠ Venomous Embrace consumed " + (int)initialToxin +
                " toxin to deal " + damageMsg + " damage!");

        if (initialToxin >= 50) {
            player.sendMessage("§a✧ High toxin granted extended speed boost!");
        }

        // Set success for cooldown
        setSkillSuccess(true);
    }

    private void playInitialEffect(Player player, LivingEntity target) {
        Location start = player.getLocation().add(0, 1, 0);
        Location end = target.getLocation().add(0, 1, 0);

        // Create spiral effect between player and target
        for (double progress = 0; progress <= 1.0; progress += 0.1) {
            double angle = progress * Math.PI * 4;
            double radius = 0.3 * Math.sin(progress * Math.PI);

            Location point = start.clone().add(
                    end.clone().subtract(start).multiply(progress)
            );

            // Add spiral motion
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);
            point.add(x, 0, z);

            player.getWorld().spawnParticle(
                    Particle.DUST,
                    point,
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1)
            );
        }

        player.getWorld().playSound(
                start,
                Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR,
                1.0F, 1.5F
        );
    }

    private void createImpactEffects(Player player, LivingEntity target) {
        Location targetLoc = target.getLocation().add(0, 1, 0);

        // Target impact effects
        target.getWorld().spawnParticle(
                Particle.DRAGON_BREATH,
                targetLoc,
                50, 0.5, 1.0, 0.5, 0.1
        );

        // Player speed boost effect
        player.getWorld().spawnParticle(
                Particle.WITCH,
                player.getLocation().add(0, 1, 0),
                30, 0.5, 1.0, 0.5, 0.1
        );

        // Impact sounds
        target.getWorld().playSound(targetLoc, Sound.ENTITY_WITHER_HURT, 1.0F, 2.0F);
    }
}