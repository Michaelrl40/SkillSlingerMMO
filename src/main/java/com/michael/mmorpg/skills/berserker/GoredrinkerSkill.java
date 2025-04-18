package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle.DustOptions;

public class GoredrinkerSkill extends Skill {
    private final double healingPercent;
    private final int duration;
    private final DustOptions bloodParticle;

    public GoredrinkerSkill(ConfigurationSection config) {
        super(config);
        this.healingPercent = config.getDouble("healingpercent", 0.5); // 50% of damage dealt
        this.duration = config.getInt("duration", 100); // 5 seconds at 20 ticks/sec
        this.bloodParticle = new DustOptions(Color.fromRGB(139, 0, 0), 1.0f);  // Dark red
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Check rage cost
        if (!playerData.useRage(rageCost)) {
            player.sendMessage("§c✦ Not enough rage!");
            setSkillSuccess(false);
            return;
        }

        // Apply lifesteal effect metadata
        player.setMetadata("goredrinker_active", new FixedMetadataValue(plugin, healingPercent));

        // Play activation effects
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_WITHER_HURT, 1.0f, 2.0f);
        player.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f, 0.8f);

        // Initial particle burst
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2 * i) / 20;
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            loc.getWorld().spawnParticle(
                    Particle.DUST,
                    loc.clone().add(x, 1.0, z),
                    3, 0.1, 0.1, 0.1, 0,
                    bloodParticle
            );
        }

        // Run particle effects and handle duration
        new BukkitRunnable() {
            private int ticks = 0;
            private double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= duration) {
                    if (player.isOnline()) {
                        player.removeMetadata("goredrinker_active", plugin);
                        // Play deactivation effects
                        player.getWorld().playSound(player.getLocation(),
                                Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.2f);
                    }
                    cancel();
                    return;
                }

                ticks++;
                angle += Math.PI / 8;

                // Ambient particles while active
                Location playerLoc = player.getLocation();
                for (int i = 0; i < 2; i++) {
                    double circleAngle = angle + (Math.PI * i);
                    double x = Math.cos(circleAngle) * 0.8;
                    double z = Math.sin(circleAngle) * 0.8;
                    playerLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            playerLoc.clone().add(x, 1.0, z),
                            1, 0.1, 0.1, 0.1, 0,
                            bloodParticle
                    );
                }

                // Every second, play a subtle sound
                if (ticks % 20 == 0) {
                    player.getWorld().playSound(player.getLocation(),
                            Sound.BLOCK_CONDUIT_AMBIENT, 0.3f, 1.5f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}