package com.michael.mmorpg.skills.bard;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class LullabySkill extends Skill {
    private final double radius;
    private final int sleepDuration;

    public LullabySkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 8.0);
        this.sleepDuration = config.getInt("sleepduration", 5000); // 5 seconds in milliseconds

        // Mana cost and cooldown should be set in config
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();

        // Play soothing sounds in sequence
        player.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f);

        // Schedule delayed notes for melody effect
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                player.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.6f), 4L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                player.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.4f), 8L);

        // Create expanding circle particle effect
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
            for (double r = 0; r <= radius; r += 0.5) {
                double x = center.getX() + (r * Math.cos(angle));
                double z = center.getZ() + (r * Math.sin(angle));
                Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.5, z);

                // Blue note particles for sleep theme
                center.getWorld().spawnParticle(
                        Particle.NOTE,
                        particleLoc,
                        1, 0, 0, 0, 0
                );
            }
        }

        // Apply sleep effect to nearby entities
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity && entity != player) {
                LivingEntity target = (LivingEntity) entity;

                // Skip party members
                if (entity instanceof Player) {
                    Player playerTarget = (Player) entity;
                    if (plugin.getPartyManager().getParty(player) != null &&
                            plugin.getPartyManager().getParty(player).isMember(playerTarget)) {
                        continue;
                    }
                }

                // Create and apply sleep effect
                if (target instanceof Player) {
                    StatusEffect sleepEffect = new StatusEffect(CCType.SLEEP, sleepDuration, player, 1);
                    plugin.getStatusEffectManager().applyEffect((Player) target, sleepEffect);
                } else {
                    // For non-player entities, apply strong slowness to simulate sleep
                    target.setMetadata("sleeping", new FixedMetadataValue(plugin, true));
                }
            }
        }

        // Broadcast skill use
        broadcastLocalSkillMessage(player, "§9[Bard] " + player.getName() + " performs a soothing Lullaby!");

        setSkillSuccess(true);
    }
}