package com.michael.mmorpg.skills.guardian;

import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.DyeColor;

import java.util.ArrayList;
import java.util.List;

public class SurgeSkill extends Skill {
    private final int duration;
    private final double damage;
    private final double knockbackStrength;
    private final double pulseInterval;
    private final List<Location> activeBanners = new ArrayList<>();

    public SurgeSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 10);
        this.damage = config.getDouble("damage", 5.0);
        this.knockbackStrength = config.getDouble("knockbackstrength", 1.5);
        this.pulseInterval = config.getDouble("pulseinterval", 1.0);
    }

    @Override
    protected void performSkill(Player caster) {
        Location bannerLoc = findBannerLocation(caster.getLocation());
        if (bannerLoc == null) {
            caster.sendMessage("§c✦ No valid location to place the banner!");
            setSkillSuccess(false);
            return;
        }

        playBannerAnimation(caster, bannerLoc);
        setSkillSuccess(true);
    }

    private void placeBanner(Player caster, Location location) {
        Block block = location.getBlock();
        block.setType(Material.RED_BANNER);

        if (block.getState() instanceof Banner) {
            Banner banner = (Banner) block.getState();
            banner.setBaseColor(DyeColor.RED);
            // Create a more aggressive pattern
            banner.addPattern(new Pattern(DyeColor.BLACK, PatternType.STRIPE_MIDDLE));
            banner.addPattern(new Pattern(DyeColor.RED, PatternType.BORDER));
            banner.addPattern(new Pattern(DyeColor.BLACK, PatternType.TRIANGLE_TOP));
            banner.update();
        }

        activeBanners.add(location);

        // Ground slam effect
        location.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                location.clone().add(0.5, 0, 0.5),
                1, 0, 0, 0, 0
        );
        location.getWorld().playSound(
                location,
                Sound.ENTITY_IRON_GOLEM_ATTACK,
                1.0f,
                0.8f
        );

        startSurgeAura(caster, location);

        // Schedule banner removal
        new BukkitRunnable() {
            @Override
            public void run() {
                removeBanner(location);
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    private void startSurgeAura(Player caster, Location bannerLoc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBanners.contains(bannerLoc) || bannerLoc.getBlock().getType() != Material.RED_BANNER) {
                    cancel();
                    return;
                }

                Party casterParty = plugin.getPartyManager().getParty(caster);

                // Get all nearby entities
                bannerLoc.getWorld().getNearbyEntities(bannerLoc, targetRange, targetRange, targetRange).forEach(entity -> {
                    if (!(entity instanceof LivingEntity) || entity.equals(caster)) return;

                    // Skip party members
                    if (entity instanceof Player && casterParty != null && casterParty.isMember((Player)entity)) {
                        return;
                    }

                    LivingEntity target = (LivingEntity) entity;

                    // Calculate distance and direction for knockback
                    Vector direction = target.getLocation().toVector()
                            .subtract(bannerLoc.toVector())
                            .normalize()
                            .multiply(knockbackStrength)
                            .setY(0.2); // Small upward component

                    // Apply knockback
                    target.setVelocity(direction);

                    // Apply damage
                    target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
                    target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
                    target.damage(0.1, caster);

                    // Clean up metadata next tick
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            target.removeMetadata("skill_damage", plugin);
                            target.removeMetadata("skill_damage_amount", plugin);
                        }
                    }.runTaskLater(plugin, 1L);
                });

                // Shockwave particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double radius = targetRange * 0.7;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = bannerLoc.clone().add(x, 0.1, z);
                    bannerLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.5f)
                    );
                }

                // Pulse sound
                bannerLoc.getWorld().playSound(
                        bannerLoc,
                        Sound.BLOCK_CONDUIT_ATTACK_TARGET,
                        0.5f,
                        0.8f
                );
            }
        }.runTaskTimer(plugin, 0L, (long)(pulseInterval * 20L));
    }

    // Reuse existing methods
    private Location findBannerLocation(Location center) {
        Location groundLoc = center.clone();
        while (groundLoc.getBlock().getType() == Material.AIR && groundLoc.getY() > 0) {
            groundLoc.subtract(0, 1, 0);
        }
        if (groundLoc.getY() <= 0) return null;
        return groundLoc.add(0, 1, 0);
    }

    private void playBannerAnimation(Player caster, Location groundLoc) {
        Location spawnLoc = groundLoc.clone().add(0, 10, 0);

        new BukkitRunnable() {
            int step = 0;
            final Location bannerLoc = groundLoc.clone();

            @Override
            public void run() {
                if (step >= 20) {
                    placeBanner(caster, bannerLoc);
                    cancel();
                    return;
                }

                Location currentLoc = spawnLoc.clone().subtract(0, step * 0.5, 0);

                // Red energy particles around falling banner
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                    double x = Math.cos(angle) * 0.5;
                    double z = Math.sin(angle) * 0.5;
                    currentLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            currentLoc.clone().add(x, 0, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.5f)
                    );
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void removeBanner(Location location) {
        activeBanners.remove(location);
        Block block = location.getBlock();
        if (block.getType() == Material.RED_BANNER) {
            block.setType(Material.AIR);

            // Final explosion effect
            location.getWorld().spawnParticle(
                    Particle.EXPLOSION_EMITTER,
                    location.clone().add(0.5, 0, 0.5),
                    3, 0.2, 0.5, 0.2, 0
            );
            location.getWorld().playSound(
                    location,
                    Sound.ENTITY_GENERIC_EXPLODE,
                    1.0f,
                    1.2f
            );
        }
    }
}