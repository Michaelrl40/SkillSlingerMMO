package com.michael.mmorpg.skills.guardian;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class UnbreakableSkill extends Skill {
    private final int duration;
    private final float scaleMultiplier = 2.0f; // Double the size

    public UnbreakableSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 10);
    }

    @Override
    protected void performSkill(Player player) {
        // Remove CC effects and add immunity
        plugin.getStatusEffectManager().removeAllActiveEffects(player);
        plugin.getStatusEffectManager().addFullCCImmunity(player, duration * 1000L);

        // Scale player
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) {
            double baseScale = scaleAttribute.getBaseValue();
            scaleAttribute.setBaseValue(baseScale * scaleMultiplier);
        }

        // Play effects and start visual indicator
        playActivationEffects(player);
        startPersistentEffect(player);

        // Notify player
        player.sendMessage("§e✦ You become UNBREAKABLE! Your size and power have doubled!");

        // Schedule return to normal
        new BukkitRunnable() {
            @Override
            public void run() {
                if (scaleAttribute != null) {
                    scaleAttribute.setBaseValue(1.0);
                }
                playExpirationEffects(player);
            }
        }.runTaskLater(plugin, duration * 20L);

        setSkillSuccess(true);
    }

    private void playActivationEffects(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Initial golden burst
        world.spawnParticle(
                Particle.DUST,
                loc,
                50, 1, 1, 1, 0.1,
                new Particle.DustOptions(Color.fromRGB(255, 215, 0), 2.0f)
        );

        // Rising rings animation
        new BukkitRunnable() {
            double y = 0;

            @Override
            public void run() {
                if (y > 2) {
                    cancel();
                    return;
                }

                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = Math.cos(angle) * 0.8;
                    double z = Math.sin(angle) * 0.8;

                    Location particleLoc = loc.clone().add(x, y, z);
                    world.spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f)
                    );
                }

                y += 0.1;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Powerful activation sounds
        world.playSound(loc, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.5f);
        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
    }

    private void startPersistentEffect(Player player) {
        new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = duration * 20;

            @Override
            public void run() {
                if (ticks >= totalTicks) {
                    cancel();
                    // Play expiration effects
                    playExpirationEffects(player);
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);

                // Spiral effect
                double angle = ticks * 0.2;
                for (double y = 0; y < 2; y += 0.2) {
                    double x = Math.cos(angle + y * 2) * 0.6;
                    double z = Math.sin(angle + y * 2) * 0.6;

                    player.getWorld().spawnParticle(
                            Particle.DUST,
                            loc.clone().add(x, y, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 0.7f)
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playExpirationEffects(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        // Final flash
        player.getWorld().spawnParticle(
                Particle.FLASH,
                loc,
                3, 0.3, 0.5, 0.3, 0
        );

        // Breaking effect
        player.getWorld().playSound(
                loc,
                Sound.BLOCK_CHAIN_BREAK,
                1.0f,
                0.8f
        );

        player.sendMessage("§c✦ Your Unbreakable effect fades!");
    }
}