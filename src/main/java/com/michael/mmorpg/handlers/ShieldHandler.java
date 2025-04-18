package com.michael.mmorpg.handlers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * Centralized shield handling system to process all types of shields
 * and ensure hit effects still occur even when damage is negated
 */
public class ShieldHandler {

    private final MinecraftMMORPG plugin;

    // Minimum damage to trigger hit effects while effectively negating damage
    private static final double MIN_DAMAGE = 0.01;

    public ShieldHandler(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    /**
     * Process all shield types and apply their effects to a damage event
     *
     * @param event The damage event
     * @param entity The entity being damaged
     * @return The modified damage value after shield processing
     */
    public double processShields(EntityDamageEvent event, Entity entity) {
        if (!(entity instanceof Player)) return event.getDamage();

        Player player = (Player) entity;
        double originalDamage = event.getDamage();
        double modifiedDamage = originalDamage;
        boolean shieldActivated = false;

        // Process Phantom Shield
        if (player.hasMetadata("phantom_shield_amount")) {
            double result = processPhantomShield(player, originalDamage);
            if (result == 0) {
                modifiedDamage = MIN_DAMAGE;
                shieldActivated = true;
            } else if (result < originalDamage) {
                modifiedDamage = result;
            }
        }

        // Process Ice Shield (if Phantom Shield didn't fully absorb damage)
        if (!shieldActivated && player.hasMetadata("ice_shield_amount")) {
            double result = processIceShield(player, modifiedDamage);
            if (result == 0) {
                modifiedDamage = MIN_DAMAGE;
                shieldActivated = true;
            } else if (result < modifiedDamage) {
                modifiedDamage = result;
            }
        }

        // Process Absorption Shield (if other shields didn't fully absorb damage)
        if (!shieldActivated && player.hasMetadata("absorption_shield_amount")) {
            double result = processAbsorptionShield(player, modifiedDamage);
            if (result == 0) {
                modifiedDamage = MIN_DAMAGE;
                shieldActivated = true;
            } else if (result < modifiedDamage) {
                modifiedDamage = result;
            }
        }

        // Apply percentage-based damage reduction (Resonant Shield)
        if (player.hasMetadata("resonant_shield")) {
            double reduction = player.getMetadata("resonant_shield").get(0).asDouble();
            modifiedDamage *= (1 - reduction);

            // Visual feedback for resonant shield
            if (reduction > 0) {
                displayResonantShieldEffect(player);
            }
        }

        // Apply Bulwark reduction (percentage-based)
        if (player.hasMetadata("bulwark_reduction")) {
            double reduction = player.getMetadata("bulwark_reduction").get(0).asDouble();
            modifiedDamage *= (1 - reduction);
        }

        // Never return 0 damage - always use MIN_DAMAGE to ensure hit effects
        return Math.max(modifiedDamage, MIN_DAMAGE);
    }

    /**
     * Process Phantom Shield damage absorption
     *
     * @param player The player with the shield
     * @param damage The incoming damage
     * @return The remaining damage after shield absorption (0 if fully absorbed)
     */
    private double processPhantomShield(Player player, double damage) {
        double shieldAmount = player.getMetadata("phantom_shield_amount").get(0).asDouble();

        if (shieldAmount <= 0) {
            player.removeMetadata("phantom_shield_amount", plugin);
            return damage;
        }

        if (shieldAmount >= damage) {
            // Shield absorbs all damage
            double remainingShield = shieldAmount - damage;
            player.setMetadata("phantom_shield_amount", new FixedMetadataValue(plugin, remainingShield));

            // Visual feedback
            player.getWorld().spawnParticle(
                    Particle.WITCH,
                    player.getLocation().add(0, 1, 0),
                    15, 0.5, 0.5, 0.5, 0.1
            );

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_PHANTOM_HURT,
                    0.5f,
                    1.2f
            );

            // Don't show message every time to reduce spam
            if (Math.random() < 0.3) { // Only show message ~30% of the time
                player.sendMessage("§5✦ Shield absorbed " + String.format("%.1f", damage) +
                        " damage! (" + String.format("%.1f", remainingShield) + " remaining)");
            }

            return 0; // All damage absorbed
        } else {
            // Shield breaks
            double remainingDamage = damage - shieldAmount;
            player.removeMetadata("phantom_shield_amount", plugin);

            // Visual feedback for shield break
            player.getWorld().spawnParticle(
                    Particle.WITCH,
                    player.getLocation().add(0, 1, 0),
                    30, 0.5, 0.5, 0.5, 0.1
            );

            player.getWorld().spawnParticle(
                    Particle.EXPLOSION,
                    player.getLocation().add(0, 1, 0),
                    5, 0.3, 0.3, 0.3, 0.05
            );

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_PHANTOM_DEATH,
                    0.7f,
                    1.2f
            );

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.BLOCK_GLASS_BREAK,
                    0.5f,
                    0.8f
            );

            player.sendMessage("§5✦ Your phantom shield has shattered!");

            return remainingDamage;
        }
    }

    /**
     * Process Ice Shield damage absorption
     *
     * @param player The player with the shield
     * @param damage The incoming damage
     * @return The remaining damage after shield absorption (0 if fully absorbed)
     */
    private double processIceShield(Player player, double damage) {
        double shieldAmount = player.getMetadata("ice_shield_amount").get(0).asDouble();

        if (shieldAmount <= 0) {
            player.removeMetadata("ice_shield_amount", plugin);
            return damage;
        }

        if (shieldAmount >= damage) {
            // Shield absorbs all damage
            double remainingShield = shieldAmount - damage;
            player.setMetadata("ice_shield_amount", new FixedMetadataValue(plugin, remainingShield));

            // Visual feedback
            player.getWorld().spawnParticle(
                    Particle.BLOCK_CRUMBLE,
                    player.getLocation().add(0, 1, 0),
                    30, 0.5, 0.5, 0.5, 0.1,
                    org.bukkit.Material.ICE.createBlockData()
            );

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.BLOCK_GLASS_BREAK,
                    0.5f,
                    1.5f
            );

            // Don't show message every time to reduce spam
            if (Math.random() < 0.3) { // Only show message ~30% of the time
                player.sendMessage("§b✦ Ice Shield absorbed " + String.format("%.1f", damage) +
                        " damage! (" + String.format("%.1f", remainingShield) + " remaining)");
            }

            return 0; // All damage absorbed
        } else {
            // Shield breaks
            double remainingDamage = damage - shieldAmount;
            player.removeMetadata("ice_shield_amount", plugin);

            // Visual feedback for shield break
            player.getWorld().spawnParticle(
                    Particle.BLOCK_CRUMBLE,
                    player.getLocation().add(0, 1, 0),
                    60, 0.5, 0.5, 0.5, 0.2,
                    org.bukkit.Material.ICE.createBlockData()
            );

            player.getWorld().spawnParticle(
                    Particle.SPLASH,
                    player.getLocation().add(0, 1, 0),
                    40, 0.5, 0.5, 0.5, 0.1
            );

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.BLOCK_GLASS_BREAK,
                    1.0f,
                    0.7f
            );

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.BLOCK_GLASS_BREAK,
                    0.8f,
                    0.5f
            );

            player.sendMessage("§b✦ Your ice shield has shattered!");

            return remainingDamage;
        }
    }

    /**
     * Process Absorption Shield damage absorption
     *
     * @param player The player with the shield
     * @param damage The incoming damage
     * @return The remaining damage after shield absorption (0 if fully absorbed)
     */
    private double processAbsorptionShield(Player player, double damage) {
        double shieldAmount = player.getMetadata("absorption_shield_amount").get(0).asDouble();

        if (shieldAmount <= 0) {
            player.removeMetadata("absorption_shield_amount", plugin);
            return damage;
        }

        if (shieldAmount >= damage) {
            // Shield absorbs all damage
            double remainingShield = shieldAmount - damage;
            player.setMetadata("absorption_shield_amount", new FixedMetadataValue(plugin, remainingShield));

            // Visual feedback
            player.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    player.getLocation().add(0, 1, 0),
                    15, 0.5, 0.5, 0.5, 0.1
            );

            // Don't show message every time to reduce spam
            if (Math.random() < 0.3) { // Only show message ~30% of the time
                player.sendMessage("§6✦ Absorption Shield absorbed " + String.format("%.1f", damage) +
                        " damage! (" + String.format("%.1f", remainingShield) + " remaining)");
            }

            return 0; // All damage absorbed
        } else {
            // Shield breaks
            double remainingDamage = damage - shieldAmount;
            player.removeMetadata("absorption_shield_amount", plugin);

            // Visual feedback for shield break
            player.getWorld().spawnParticle(
                    Particle.ANGRY_VILLAGER,
                    player.getLocation().add(0, 1, 0),
                    15, 0.5, 0.5, 0.5, 0.1
            );

            player.sendMessage("§c✦ Your absorption shield has been depleted!");
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.5f, 1.0f);

            return remainingDamage;
        }
    }

    /**
     * Display visual effect for the Resonant Shield when it blocks damage
     */
    private void displayResonantShieldEffect(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        // Subtle particle effect
        player.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                10, 0.5, 0.5, 0.5, 0.05,
                new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.0f)
        );
    }
}