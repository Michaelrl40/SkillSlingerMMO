package com.michael.mmorpg.skills.frostmage;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.skills.Skill;

public class IceShieldSkill extends Skill {
    // Configuration values for the shield
    private final double shieldAmount;    // Amount of damage the shield can absorb
    private final int effectDuration;     // How long the shield lasts in seconds

    public IceShieldSkill(ConfigurationSection config) {
        super(config);
        this.shieldAmount = config.getDouble("shieldAmount", 50.0); // Shield blocks 50 damage by default
        this.effectDuration = config.getInt("duration", 20); // Lasts 20 seconds by default
    }

    @Override
    public void execute(Player player) {
        // Show casting effects while using base Skill casting system
        showCastingEffects(player);

        // Start the cast (handled by base Skill class)
        startCasting(player);
    }

    private void showCastingEffects(Player player) {
        // Create continuous casting effects
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // Stop effects if player isn't casting anymore
                if (!isCasting() || tick++ > 100) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                World world = player.getWorld();

                // Create swirling ice particles during cast
                double radius = 1.0;
                double angle = tick * 0.5;
                for (int i = 0; i < 2; i++) {
                    angle += Math.PI;
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location particleLoc = loc.clone().add(x, tick % 2, z);

                    world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0.02);
                }

                // Play subtle sound effects during cast
                if (tick % 10 == 0) {
                    world.playSound(loc, Sound.BLOCK_GLASS_STEP, 0.2f, 1.5f);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    @Override
    protected void performSkill(Player player) {
        // This is called automatically after cast time completes
        activateShield(player);
        setSkillSuccess(true);
    }

    private void activateShield(Player player) {
        // Set custom metadata for shield
        player.setMetadata("ice_shield_amount", new FixedMetadataValue(getPlugin(), shieldAmount));

        // Create shield activation effects
        World world = player.getWorld();
        Location loc = player.getLocation();

        // Create ascending shield effect
        for (double y = 0; y < 2.5; y += 0.2) {
            double radius = 1.5;
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                Location particleLoc = loc.clone().add(x, y, z);
                world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
            }
        }

        // Create shield formation burst effect
        world.spawnParticle(Particle.SNOWFLAKE, loc.add(0, 1, 0), 50, 1, 1, 1, 0.2);
        world.spawnParticle(Particle.END_ROD, loc, 20, 0.5, 0.5, 0.5, 0.1);

        // Play shield activation sounds
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        world.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.2f);
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);

        // Broadcast the shield activation
        broadcastLocalSkillMessage(player, "§b[Frostmage] " + player.getName() + " conjures an Ice Shield!");
        player.sendMessage("§b✦ Ice Shield activated! (" + String.format("%.1f", shieldAmount) + " damage will be absorbed)");

        // Start shield particle effects
        BukkitRunnable shieldEffectTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Stop if shield expires or is broken
                if (!player.isOnline() || !player.hasMetadata("ice_shield_amount") || ticks++ > effectDuration * 20) {
                    if (player.isOnline() && player.hasMetadata("ice_shield_amount")) {
                        player.removeMetadata("ice_shield_amount", getPlugin());
                        player.sendMessage("§c✦ Your Ice Shield has faded away!");
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 0.8f);
                    }
                    cancel();
                    return;
                }

                // Every 10 ticks (0.5 seconds), create shield visual effect
                if (ticks % 10 == 0) {
                    createShieldVisualization(player);
                }

                // Warning when shield is about to expire
                if (ticks == (effectDuration - 5) * 20) { // 5 seconds before expiry
                    player.sendMessage("§b✦ Your Ice Shield is about to fade!");
                    world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 0.8f);
                }
            }
        };

        // Start shield effect task
        shieldEffectTask.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void createShieldVisualization(Player player) {
        if (!player.isOnline()) return;

        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Get current shield amount
        double shieldAmount = 0;
        if (player.hasMetadata("ice_shield_amount")) {
            shieldAmount = player.getMetadata("ice_shield_amount").get(0).asDouble();
        }

        // Scale particle count based on remaining shield strength
        int particleCount = (int) Math.max(1, Math.min(5, shieldAmount / 10));

        // Create spiral pattern around player
        double radius = 0.8;
        long time = System.currentTimeMillis() % 2000; // 2 second cycle
        double progress = time / 2000.0; // 0.0 to 1.0

        for (int i = 0; i < particleCount; i++) {
            double angle = progress * Math.PI * 2 + (i * (Math.PI * 2) / particleCount);
            double height = 0.1 + (Math.sin(progress * Math.PI * 4) + 1) * 0.9; // Oscillate between 0.1 and 1.9

            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location particleLoc = loc.clone().add(x, height, z);
            world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0.05, 0.05, 0.05, 0);

            if (i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0.01);
            }
        }
    }
}