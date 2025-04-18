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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.DyeColor;

import java.util.ArrayList;
import java.util.List;

public class BannerOfProtectionSkill extends Skill {
    private final int duration;
    private final int resistanceLevel;
    private final List<Location> activeBanners = new ArrayList<>();

    public BannerOfProtectionSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 15);
        this.resistanceLevel = config.getInt("resistancelevel", 1);
    }

    @Override
    public void execute(Player caster) {
        // Start casting if has cast time
        if (hasCastTime) {
            caster.sendMessage("§6✦ Summoning the Banner of Protection...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        Location bannerLoc = findBannerLocation(caster.getLocation());
        if (bannerLoc == null) {
            caster.sendMessage("§c✦ No valid location to place the banner!");
            setSkillSuccess(false);
            return;
        }

        // Start banner animation
        playBannerAnimation(caster, bannerLoc);

        // Set cooldown
        plugin.getSkillManager().setCooldown(caster, getName(), getCooldown());

        setSkillSuccess(true);
    }

    private Location findBannerLocation(Location center) {
        // Find ground location
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
                if (step >= 20) { // Animation complete
                    placeBanner(caster, bannerLoc);
                    cancel();
                    return;
                }

                // Banner falling effect
                Location currentLoc = spawnLoc.clone().subtract(0, step * 0.5, 0);

                // Gold particles around banner
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                    double x = Math.cos(angle) * 0.5;
                    double z = Math.sin(angle) * 0.5;
                    currentLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            currentLoc.clone().add(x, 0, z),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f)
                    );
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void placeBanner(Player caster, Location location) {
        Block block = location.getBlock();
        block.setType(Material.WHITE_BANNER);

        if (block.getState() instanceof Banner) {
            Banner banner = (Banner) block.getState();
            banner.setBaseColor(DyeColor.WHITE);

            // Add shield pattern
            banner.addPattern(new Pattern(DyeColor.BLUE, PatternType.CROSS));
            banner.addPattern(new Pattern(DyeColor.WHITE, PatternType.BORDER));
            banner.update();
        }

        // Add to active banners
        activeBanners.add(location);

        // Play landing effect
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.clone().add(0.5, 0, 0.5),
                20, 0.2, 0.5, 0.2, 0.1
        );
        location.getWorld().playSound(
                location,
                Sound.BLOCK_ANVIL_LAND,
                1.0f,
                1.5f
        );

        // Start protection aura
        startProtectionAura(caster, location);

        // Schedule banner removal
        new BukkitRunnable() {
            @Override
            public void run() {
                removeBanner(location);
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    private void startProtectionAura(Player caster, Location bannerLoc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeBanners.contains(bannerLoc) || bannerLoc.getBlock().getType() != Material.WHITE_BANNER) {
                    cancel();
                    return;
                }

                // Get caster's party
                Party casterParty = plugin.getPartyManager().getParty(caster);

                // Get all players in range
                bannerLoc.getWorld().getNearbyPlayers(bannerLoc, targetRange).forEach(player -> {
                    // Check if player is the caster or in the same party
                    if (player.equals(caster) || (casterParty != null && casterParty.isMember(player))) {
                        // Apply resistance
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.RESISTANCE,
                                40, // 2-second duration, reapplied every second
                                resistanceLevel - 1,
                                false,
                                false
                        ));
                    }
                });

                // Aura particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = Math.cos(angle) * targetRange;
                    double z = Math.sin(angle) * targetRange;
                    Location particleLoc = bannerLoc.clone().add(x, 0.1, z);
                    bannerLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(200, 200, 255), 1)
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Check every second
    }

    private void removeBanner(Location location) {
        activeBanners.remove(location);
        Block block = location.getBlock();
        if (block.getType() == Material.WHITE_BANNER) {
            block.setType(Material.AIR);

            // Removal effect
            location.getWorld().spawnParticle(
                    Particle.CLOUD,
                    location.clone().add(0.5, 0, 0.5),
                    20, 0.2, 0.5, 0.2, 0.1
            );
            location.getWorld().playSound(
                    location,
                    Sound.BLOCK_BEACON_DEACTIVATE,
                    1.0f,
                    1.2f
            );
        }
    }
}