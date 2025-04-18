package com.michael.mmorpg.skills.guardian;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Color;

public class MagicWardSkill extends Skill {
    private final int duration;
    private final double damageReduction;

    public MagicWardSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 10);
        this.damageReduction = config.getDouble("damagereduction", 0.5);
    }

    @Override
    protected void performSkill(Player player) {
        // First remove any existing ward to avoid stacking
        if (player.hasMetadata("magic_ward")) {
            player.removeMetadata("magic_ward", plugin);
        }

        // Apply the magic ward buff
        applyMagicWard(player);

        // Play initial cast effects
        playCastEffects(player);

        // Schedule removal of the buff
        scheduleMagicWardRemoval(player);

        setSkillSuccess(true);
    }

    private void applyMagicWard(Player player) {
        // Add metadata to track magic damage reduction
        player.setMetadata("magic_ward", new FixedMetadataValue(plugin, damageReduction));

        // Notify player
        player.sendMessage("§b✦ Magic Ward activated! Magic damage reduced by " +
                String.format("%.0f", damageReduction * 100) + "%%");
    }

    // Static method to handle damage reduction
    public static double reduceMagicDamage(Player player, double damage) {
        if (player.hasMetadata("magic_ward")) {
            double reduction = player.getMetadata("magic_ward").get(0).asDouble();
            double reducedDamage = damage * (1.0 - reduction);

            // Visual feedback
            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    loc,
                    10, 0.3, 0.3, 0.3, 0.05,
                    new Particle.DustOptions(Color.fromRGB(100, 100, 255), 1.0f)
            );
            player.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);

            player.sendMessage(String.format("§b✦ Magic Ward absorbed %.1f damage!", damage - reducedDamage));
            return reducedDamage;
        }
        return damage;
    }

    // Rest of the visual effects code remains the same...
    private void playCastEffects(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Initial magical barrier effect
        world.spawnParticle(
                Particle.WITCH,
                loc,
                50, 1, 1, 1, 0.1
        );

        // Magical shield dome effect
        for (double phi = 0; phi <= Math.PI; phi += Math.PI/15) {
            double y = Math.cos(phi);
            double r = Math.sin(phi);

            for (double theta = 0; theta <= 2 * Math.PI; theta += Math.PI/15) {
                double x = r * Math.cos(theta);
                double z = r * Math.sin(theta);

                Location particleLoc = loc.clone().add(x, y, z);

                world.spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(100, 100, 255), 1.0f)
                );
            }
        }

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        world.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);

        startWardVisualEffect(player);
    }

    private void startWardVisualEffect(Player player) {
        new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                if (!player.hasMetadata("magic_ward")) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);

                for (double i = 0; i < Math.PI * 2; i += Math.PI / 4) {
                    double x = Math.cos(i + angle) * 1.0;
                    double z = Math.sin(i + angle) * 1.0;

                    player.getWorld().spawnParticle(
                            Particle.DUST,
                            loc.clone().add(x, 0, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 100, 255), 0.7f)
                    );
                }

                if (Math.random() < 0.3) {
                    player.getWorld().spawnParticle(
                            Particle.INSTANT_EFFECT,
                            loc,
                            3, 0.5, 0.5, 0.5, 0
                    );
                }

                angle += 0.2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void scheduleMagicWardRemoval(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.hasMetadata("magic_ward")) {
                    player.removeMetadata("magic_ward", plugin);

                    // Play expiration effects
                    Location loc = player.getLocation().add(0, 1, 0);
                    player.getWorld().spawnParticle(
                            Particle.WITCH,
                            loc,
                            30, 0.5, 0.5, 0.5, 0.1
                    );
                    player.getWorld().playSound(
                            loc,
                            Sound.BLOCK_BEACON_DEACTIVATE,
                            1.0f,
                            1.5f
                    );

                    player.sendMessage("§c✦ Magic Ward fades away!");
                }
            }
        }.runTaskLater(plugin, duration * 20L);
    }
}