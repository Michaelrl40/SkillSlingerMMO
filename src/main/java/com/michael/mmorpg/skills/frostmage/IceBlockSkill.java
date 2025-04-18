package com.michael.mmorpg.skills.frostmage;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.michael.mmorpg.skills.Skill;

public class IceBlockSkill extends Skill {
    private final int duration;

    public IceBlockSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 5);
    }

    @Override
    protected void performSkill(Player player) {
        Location loc = player.getLocation();

        // Mark player as in ice block
        player.setMetadata("ice_block", new FixedMetadataValue(MinecraftMMORPG.getInstance(), true));

        // Make player invulnerable and stunned
        player.setInvulnerable(true);
        player.setMetadata("rooted", new FixedMetadataValue(MinecraftMMORPG.getInstance(), true));

        // Apply stun effect through StatusEffectManager
        StatusEffect stunEffect = new StatusEffect(CCType.STUN, duration * 1000L, player, 1);
        MinecraftMMORPG.getInstance().getStatusEffectManager().applyEffect(player, stunEffect);

        // Visual effects
        createIceBlock(player);

        // Remove negative effects except stun and silence
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (isRemovableEffect(effect.getType())) {
                player.removePotionEffect(effect.getType());
            }
        }

        // Schedule the end of ice block
        new BukkitRunnable() {
            @Override
            public void run() {
                endIceBlock(player);
            }
        }.runTaskLater(MinecraftMMORPG.getInstance(), duration * 20L);

        // Continuous particle effects
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.hasMetadata("ice_block")) {
                    cancel();
                    return;
                }

                Location particleLoc = player.getLocation().add(0, 1, 0);
                double radius = 0.8;
                for (double y = 0; y < 2; y += 0.2) {
                    double angle = y * 5;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    particleLoc.add(x, y, z);
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0, 0, 0, 0);
                    particleLoc.subtract(x, y, z);
                }
            }
        }.runTaskTimer(MinecraftMMORPG.getInstance(), 0L, 2L);
    }

    private boolean isRemovableEffect(PotionEffectType type) {
        return !type.equals(PotionEffectType.SLOWNESS);  // For stuns
    }

    private void createIceBlock(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);

        for (double x = -1; x <= 1; x += 0.5) {
            for (double z = -1; z <= 1; z += 0.5) {
                for (double y = 0; y <= 2; y += 0.5) {
                    if (Math.abs(x) == 1 || Math.abs(z) == 1 || y == 0 || y == 2) {
                        Location particleLoc = loc.clone().add(x, y, z);
                        world.spawnParticle(Particle.BLOCK_CRUMBLE,
                                particleLoc, 1, 0, 0, 0, 0,
                                Material.ICE.createBlockData());
                    }
                }
            }
        }
    }

    private void endIceBlock(Player player) {
        if (!player.hasMetadata("ice_block")) return;

        // Remove ice block state and effects
        player.removeMetadata("ice_block", MinecraftMMORPG.getInstance());
        player.removeMetadata("rooted", MinecraftMMORPG.getInstance());
        player.setInvulnerable(false);

        // Remove stun effect
        MinecraftMMORPG.getInstance().getStatusEffectManager().removeEffect(player, CCType.STUN);

        // Play breaking effects
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE,
                player.getLocation().add(0, 1, 0),
                50, 0.5, 1, 0.5, 0,
                Material.ICE.createBlockData());
    }
}