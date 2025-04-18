package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OverclockedWeaponSkill extends Skill {
    private final double lightningDamage;
    private final int warmupTime;
    private static final Set<UUID> chargingPlayers = new HashSet<>();
    private static final Set<UUID> overchargedPlayers = new HashSet<>();

    public OverclockedWeaponSkill(ConfigurationSection config) {
        super(config);
        this.lightningDamage = config.getDouble("lightningDamage", 25.0);
        this.warmupTime = config.getInt("warmupTime", 3); // Default 3 second warmup
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player is already charging or overcharged
        if (chargingPlayers.contains(player.getUniqueId()) ||
                overchargedPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§c✦ Your weapon is already being overclocked!");
            setSkillSuccess(false);
            return;
        }

        // Start the charging process
        startCharging(player);
        setSkillSuccess(true);
    }

    private void startCharging(Player player) {
        chargingPlayers.add(player.getUniqueId());

        // Initial message
        player.sendMessage("§e✦ Your weapon begins to spark with electrical energy...");

        // Charging animation
        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = warmupTime * 20; // Convert seconds to ticks

            @Override
            public void run() {
                if (!player.isOnline() || !chargingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                // Particle effects during charge
                Location loc = player.getLocation().add(0, 1, 0);
                if (ticks % 5 == 0) { // Every 1/4 second
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 20,
                            0.5, 0.5, 0.5, 0.1);
                    player.getWorld().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                            0.5f, 1.0f + ((float)ticks/maxTicks));
                }

                ticks++;
                if (ticks >= maxTicks) {
                    completeCharge(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void completeCharge(Player player) {
        chargingPlayers.remove(player.getUniqueId());
        overchargedPlayers.add(player.getUniqueId());

        // Visual and sound effects for completion
        Location loc = player.getLocation();
        player.getWorld().strikeLightningEffect(loc);
        player.getWorld().playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0f, 2.0f);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                loc.add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.2);

        // Message
        player.sendMessage("§6✦ Your weapon is overclocked!");

        // Add metadata for combat listener
        player.setMetadata("overclocked_weapon", new FixedMetadataValue(plugin, lightningDamage));
    }

    public static void triggerLightningStrike(Player player, LivingEntity target) {
        if (!target.hasMetadata("taking_lightning_damage")) {
            try {
                // Get the stored lightning damage
                double lightningDamage = player.getMetadata("overclocked_weapon").get(0).asDouble();

                // First set the damage metadata
                target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, lightningDamage));
                target.setMetadata("taking_lightning_damage", new FixedMetadataValue(plugin, true));

                // Apply damage
                target.damage(0.1, player);

                // Visual and sound effects
                Location strikeLocation = target.getLocation();
                target.getWorld().strikeLightningEffect(strikeLocation);
                target.getWorld().playSound(strikeLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        strikeLocation.add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);

                // Clean up metadata
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (target.isValid()) {
                        target.removeMetadata("skill_damage", plugin);
                        target.removeMetadata("skill_damage_amount", plugin);
                        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
                    }
                }, 1L);

                // Consume the overcharge
                consumeOvercharge(player);
            } finally {
                target.removeMetadata("taking_lightning_damage", plugin);
            }
        }
    }

    public static void consumeOvercharge(Player player) {
        overchargedPlayers.remove(player.getUniqueId());
        player.removeMetadata("overclocked_weapon", MinecraftMMORPG.getPlugin(MinecraftMMORPG.class));
        player.sendMessage("§7✦ Your weapon's charge has been expended.");
    }

    public static void cancelCharging(Player player) {
        if (chargingPlayers.remove(player.getUniqueId())) {
            player.sendMessage("§c✦ Your weapon's overclocking was interrupted!");
        }
    }

    public static void cleanup() {
        chargingPlayers.clear();
        overchargedPlayers.clear();
    }
}