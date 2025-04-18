package com.michael.mmorpg.skills.buccaneer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class PlunderSkill extends Skill {
    private final double meleeRange;
    private final double damage;

    public PlunderSkill(ConfigurationSection config) {
        super(config);
        // Set the cast time to 0.5 seconds
        this.castTime = 0.5;
        this.hasCastTime = true;
        this.isMeleeSkill = true;
        this.isHarmfulSkill = true;

        // Get configuration values with defaults
        this.meleeRange = config.getDouble("range", 3.0);
        this.damage = config.getDouble("damage", 5.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target in melee range
        LivingEntity target = getMeleeTarget(player, meleeRange);

        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store target for validation and messages
        currentTarget = target;

        // Validate the target
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Only steal effects from players
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            List<PotionEffect> stolenEffects = new ArrayList<>();

            // Check each active effect on the target
            for (PotionEffect effect : targetPlayer.getActivePotionEffects()) {
                // Check if it's a beneficial effect using your system
                if (isBeneficialEffect(effect.getType())) {
                    // Store effect details before removal
                    stolenEffects.add(new PotionEffect(
                            effect.getType(),
                            effect.getDuration(),
                            effect.getAmplifier(),
                            effect.isAmbient(),
                            effect.hasParticles(),
                            effect.hasIcon()
                    ));

                    // Remove effect from target
                    targetPlayer.removePotionEffect(effect.getType());
                }
            }

            // Apply stolen effects to the player with appropriate metadata
            if (!stolenEffects.isEmpty()) {
                // Apply effects with combat system metadata
                for (PotionEffect effect : stolenEffects) {
                    player.addPotionEffect(effect);
                }

                // Feedback messages
                player.sendMessage("§6✦ You plundered " + stolenEffects.size() + " effects from " + targetPlayer.getName() + "!");
                targetPlayer.sendMessage("§c✦ Your effects were plundered by " + player.getName() + "!");
            }
        }

        // Apply damage with skill system
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(0.1, player);

        // Visual and sound effects
        playPlunderEffects(player, target);

        setSkillSuccess(true);
    }

    private boolean isBeneficialEffect(PotionEffectType type) {
        try {
            // Reference the beneficial effects from your config system
            ConfigurationSection config = plugin.getConfig().getConfigurationSection("potion-combat");
            if (config != null) {
                List<String> beneficialEffects = config.getStringList("beneficial-effects");
                return beneficialEffects.contains(type.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking beneficial effect: " + type.getName());
        }

        // Fallback list if config check fails
        switch (type.getName().toLowerCase()) {
            case "speed":
            case "jump":
            case "regeneration":
            case "damage_resistance":
            case "fire_resistance":
            case "water_breathing":
            case "invisibility":
            case "night_vision":
            case "health_boost":
            case "absorption":
            case "saturation":
            case "dolphins_grace":
            case "conduit_power":
            case "hero_of_the_village":
            case "slow_falling":
            case "strength":
            case "luck":
                return true;
            default:
                return false;
        }
    }

    private void playPlunderEffects(Player player, LivingEntity target) {
        Location targetLoc = target.getLocation();
        World world = target.getWorld();

        // Initial hit effect
        world.playSound(targetLoc, Sound.ENTITY_WITCH_DRINK, 1.0f, 1.2f);
        world.playSound(targetLoc, Sound.ITEM_BOTTLE_EMPTY, 1.0f, 0.8f);

        // Particle trails from target to player
        Vector direction = player.getLocation().subtract(targetLoc).toVector().normalize();
        double distance = player.getLocation().distance(targetLoc);

        new BukkitRunnable() {
            double progress = 0;
            @Override
            public void run() {
                if (progress >= distance) {
                    cancel();
                    return;
                }

                Location currentLoc = targetLoc.clone().add(direction.clone().multiply(progress));
                world.spawnParticle(
                        Particle.WITCH,
                        currentLoc,
                        5, 0.2, 0.2, 0.2, 0.02
                );

                progress += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Success effect at player
        world.spawnParticle(
                Particle.WAX_ON,
                player.getLocation().add(0, 1, 0),
                20, 0.3, 0.5, 0.3, 0.1
        );
    }
}