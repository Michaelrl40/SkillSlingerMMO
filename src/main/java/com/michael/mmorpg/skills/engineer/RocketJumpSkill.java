package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RocketJumpSkill extends Skill {
    private final double launchPower;
    private final double upwardForce;
    private final double warmupTime;
    private final double explosionDamage;
    private final double explosionRadius;
    private final int fallProtectionDuration;
    private static final Set<UUID> chargingPlayers = new HashSet<>();

    public RocketJumpSkill(ConfigurationSection config) {
        super(config);
        this.launchPower = config.getDouble("launchPower", 1.5);  // Forward momentum
        this.upwardForce = config.getDouble("upwardForce", 1.0);  // Upward momentum
        this.warmupTime = config.getDouble("warmupTime", 0.5);    // Half second warmup
        this.explosionDamage = config.getDouble("explosionDamage", 8.0);
        this.explosionRadius = config.getDouble("explosionRadius", 3.0);
        this.fallProtectionDuration = config.getInt("fallProtectionDuration", 3); // 3 second default
    }

    @Override
    protected void performSkill(Player player) {
        // Check if already charging
        if (chargingPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§c✦ Already preparing to jump!");
            setSkillSuccess(false);
            return;
        }

        // Start warmup
        startWarmup(player);
        setSkillSuccess(true);
    }

    private void startWarmup(Player player) {
        chargingPlayers.add(player.getUniqueId());

        // Initial feedback
        player.sendMessage("§e✦ Preparing rocket jump...");

        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = (int)(warmupTime * 20);

            @Override
            public void run() {
                if (!player.isOnline() || !chargingPlayers.contains(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                // Warmup particles and sounds
                Location loc = player.getLocation();
                if (ticks % 2 == 0) {  // Every 1/10th second
                    player.getWorld().spawnParticle(Particle.SMOKE,
                            loc.clone().add(0, 0.1, 0), 5, 0.2, 0, 0.2, 0);
                    player.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH,
                            0.3f, 1.0f + ((float)ticks/maxTicks));
                }

                ticks++;
                if (ticks >= maxTicks) {
                    executeJump(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executeJump(Player player) {
        chargingPlayers.remove(player.getUniqueId());
        Location loc = player.getLocation();

        // Get player's look direction for forward momentum
        Vector direction = player.getLocation().getDirection();
        direction.setY(0).normalize(); // Flatten to horizontal vector

        // Create launch vector
        Vector launchVector = direction.multiply(launchPower).setY(upwardForce);

        // Apply velocity
        player.setVelocity(launchVector);

        // Add fall protection
        addFallProtection(player);

        // Explosion effects
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                loc.clone().add(0, 0.1, 0), 3, 0.2, 0, 0.2, 0);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                loc.clone().add(0, 0.1, 0), 20, 0.2, 0, 0.2, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
    }

    private void addFallProtection(Player player) {
        // Add metadata for fall protection
        player.setMetadata("disengage_immunity", new FixedMetadataValue(plugin, true));

        // Add visual effect to show protection is active
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = fallProtectionDuration * 20; // Convert to ticks

            @Override
            public void run() {
                if (!player.isOnline() || !player.hasMetadata("disengage_immunity")) {
                    this.cancel();
                    return;
                }

                // Show particle effect every 5 ticks
                if (ticks % 5 == 0) {
                    player.getWorld().spawnParticle(Particle.CLOUD,
                            player.getLocation().add(0, 0.1, 0),
                            1, 0.2, 0, 0.2, 0);
                }

                // Check if player has landed
                if (player.isOnGround()) {
                    removeFallProtection(player);
                    this.cancel();
                    return;
                }

                // Check if time is up
                ticks++;
                if (ticks >= maxTicks) {
                    removeFallProtection(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void removeFallProtection(Player player) {
        player.removeMetadata("disengage_immunity", plugin);
        player.sendMessage("§e✦ Rocket Jump protection fades!");
    }

    public static void cleanup() {
        // Remove metadata from all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removeMetadata("disengage_immunity", plugin);
        }
        chargingPlayers.clear();
    }
}