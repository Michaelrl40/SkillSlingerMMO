package com.michael.mmorpg.skills.guardian;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Bulwark - Guardian skill that greatly reduces damage but applies a movement speed penalty
 */
public class BulwarkSkill extends Skill {
    private final double damageReduction;
    private final int duration;
    private final int slowIntensity;

    public BulwarkSkill(ConfigurationSection config) {
        super(config);
        this.damageReduction = config.getDouble("damagereduction", 0.5); // 50% damage reduction by default
        this.duration = config.getInt("duration", 10); // 10 seconds by default
        this.slowIntensity = config.getInt("slowintensity", 6); // Severe slowness
    }

    @Override
    protected void performSkill(Player player) {
        // Apply Bulwark reduction metadata
        player.setMetadata("bulwark_reduction", new FixedMetadataValue(plugin, damageReduction));

        // Apply movement speed reduction
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                duration * 20, // Convert to ticks
                slowIntensity - 1, // Convert to 0-based value
                false, true, true // ambient, particles, icon
        ));

        // Visual and sound effects
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.7f, 0.5f);
        player.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.8f);

        // Create activation effect
        createBulwarkEffect(player, true);

        // Broadcast skill use
        broadcastLocalSkillMessage(player, "§8[Guardian] " + player.getName() + " becomes an immovable Bulwark!");
        player.sendMessage("§8✦ Bulwark activated! (Damage reduced by " +
                Math.round(damageReduction * 100) + "% for " + duration + " seconds)");

        // Schedule cleanup and periodic effects
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration * 20 || !player.isOnline() || !player.hasMetadata("bulwark_reduction")) {
                    if (player.isOnline() && player.hasMetadata("bulwark_reduction")) {
                        player.removeMetadata("bulwark_reduction", plugin);
                        createBulwarkEffect(player, false);
                        player.sendMessage("§7✦ Your Bulwark stance ends.");
                    }
                    this.cancel();
                    return;
                }

                // Periodic shield visualization (every second)
                if (ticks % 20 == 0) {
                    showBulwarkVisualization(player);
                }

                // Heavy footstep effect (every 10 ticks when moving)
                if (ticks % 10 == 0 && player.getVelocity().lengthSquared() > 0.01) {
                    player.getWorld().playSound(
                            player.getLocation(),
                            Sound.BLOCK_STONE_STEP,
                            0.3f,
                            0.5f
                    );

                    // Ground impact particles
                    player.getWorld().spawnParticle(
                            Particle.BLOCK_CRUMBLE,
                            player.getLocation().add(0, 0.1, 0),
                            8, 0.2, 0, 0.2, 0,
                            org.bukkit.Material.STONE.createBlockData()
                    );
                }

                // Warning when about to expire
                if (ticks == (duration - 3) * 20) { // 3 seconds before expiration
                    player.sendMessage("§8✦ Your Bulwark stance is weakening...");
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void createBulwarkEffect(Player player, boolean isActivating) {
        Location loc = player.getLocation();

        // Shield burst effect
        for (double y = 0; y < 2.5; y += 0.2) {
            double radius = isActivating ? 0.8 + (y * 0.4) : 2.0 - (y * 0.4);
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 12) {
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                Location particleLoc = loc.clone().add(x, y, z);
                player.getWorld().spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(130, 130, 130), 2.0f)
                );
            }
        }

        // Ground impact
        player.getWorld().spawnParticle(
                Particle.BLOCK_CRUMBLE,
                player.getLocation().add(0, 0.1, 0),
                30, 1.5, 0.1, 1.5, 0.1,
                org.bukkit.Material.STONE.createBlockData()
        );

        // Shockwave effect
        if (isActivating) {
            new BukkitRunnable() {
                double radius = 0.5;
                int iterations = 0;

                @Override
                public void run() {
                    if (iterations++ >= 10) {
                        cancel();
                        return;
                    }

                    for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        Location particleLoc = player.getLocation().add(x, 0.1, z);
                        player.getWorld().spawnParticle(
                                Particle.DUST,
                                particleLoc,
                                1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(100, 100, 100), 1.5f)
                        );
                    }

                    radius += 0.3;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    private void showBulwarkVisualization(Player player) {
        if (!player.isOnline()) return;

        Location loc = player.getLocation().add(0, 1, 0);

        // Create swirling "iron" particles around player
        double radius = 1.2;
        double heightOffset = Math.sin(System.currentTimeMillis() / 500.0) * 0.3;

        for (int i = 0; i < 4; i++) {
            double angle = (System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2 + (i * Math.PI / 2);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = loc.clone().add(x, heightOffset, z);
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    particleLoc,
                    1, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(120, 120, 120), 2.0f)
            );

            // Add occasional iron-like particle
            if (Math.random() < 0.3) {
                player.getWorld().spawnParticle(
                        Particle.FALLING_DUST,
                        loc.clone().add(
                                (Math.random() - 0.5) * 1.5,
                                Math.random() * 2,
                                (Math.random() - 0.5) * 1.5
                        ),
                        1, 0.1, 0.1, 0.1, 0,
                        org.bukkit.Material.IRON_BLOCK.createBlockData()
                );
            }
        }
    }
}