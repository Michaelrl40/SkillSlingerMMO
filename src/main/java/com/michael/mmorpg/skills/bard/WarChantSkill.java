package com.michael.mmorpg.skills.bard;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.party.Party;

import java.util.ArrayList;
import java.util.List;

public class WarChantSkill extends Skill {
    private final double damageBonus;
    private final int duration;
    private final double effectRadius;

    public WarChantSkill(ConfigurationSection config) {
        super(config);
        this.damageBonus = config.getDouble("damagebonus", 0.3); // 30% damage increase
        this.duration = config.getInt("duration", 200); // 10 seconds (20 ticks * 10)
        this.effectRadius = config.getDouble("radius", 12.0); // Default 20 block radius
    }

    @Override
    protected void performSkill(Player player) {
        Party party = plugin.getPartyManager().getParty(player);

        // Play war drums sound sequence
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f), 4L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.2f), 8L);

        // Create pulsing red particle effect
        new BukkitRunnable() {
            int pulses = 0;

            @Override
            public void run() {
                if (pulses++ >= 3) {
                    this.cancel();
                    return;
                }

                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                    double radius = 1.5;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    player.getWorld().spawnParticle(
                            Particle.DUST,
                            player.getLocation().add(x, 1, z),
                            5, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 0, 0), 2.0f)
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        // Visualize the effect radius
        visualizeEffectRadius(player);

        if (party != null) {
            // Track affected and out-of-range members
            List<String> affectedMembers = new ArrayList<>();
            List<String> outOfRangeMembers = new ArrayList<>();

            // Apply damage buff to all party members within range
            for (Player member : party.getMembers()) {
                // Skip the caster for now (will apply separately)
                if (member.equals(player)) continue;

                // Check if member is within range
                if (member.getWorld().equals(player.getWorld()) &&
                        member.getLocation().distance(player.getLocation()) <= effectRadius) {

                    // Apply the damage buff
                    applyDamageBuff(member);
                    affectedMembers.add(member.getName());
                } else {
                    outOfRangeMembers.add(member.getName());
                }
            }

            // Always apply to self regardless of distance
            applyDamageBuff(player);

            // Provide feedback about affected members
            if (!affectedMembers.isEmpty()) {
                player.sendMessage("§c✦ Your War Chant empowered: §f" + String.join(", ", affectedMembers));
            }

            if (!outOfRangeMembers.isEmpty()) {
                player.sendMessage("§c✦ These party members were out of range: §f" +
                        String.join(", ", outOfRangeMembers));
            }

            // Broadcast to nearby players
            broadcastLocalSkillMessage(player, "§c[Bard] " + player.getName() + " performs a War Chant!");
        } else {
            // If not in party, just apply to self
            applyDamageBuff(player);
            player.sendMessage("§c✦ You perform a War Chant!");
        }

        setSkillSuccess(true);
    }

    private void applyDamageBuff(Player player) {
        // Remove any existing buff first to avoid stacking
        if (player.hasMetadata("war_chant_buff")) {
            player.removeMetadata("war_chant_buff", plugin);
        }

        // Apply damage buff metadata
        player.setMetadata("war_chant_buff", new FixedMetadataValue(plugin, damageBonus));

        // Visual effect on buff application
        player.getWorld().spawnParticle(
                Particle.FLAME,
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.05
        );

        player.sendMessage("§c✦ Your attacks are empowered by the War Chant!");

        // Schedule buff removal
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.hasMetadata("war_chant_buff")) {
                    player.removeMetadata("war_chant_buff", plugin);
                    player.sendMessage("§7✦ The effects of War Chant fade away...");

                    // End effect particles
                    player.getWorld().spawnParticle(
                            Particle.SMOKE,
                            player.getLocation().add(0, 1, 0),
                            10, 0.5, 0.5, 0.5, 0.05
                    );
                }
            }
        }.runTaskLater(plugin, duration);
    }

    /**
     * Creates a visual effect to show the radius of the skill
     */
    private void visualizeEffectRadius(Player player) {
        // Create a circle of particles at the edge of the effect radius
        int particles = 72; // Number of particles in the circle
        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            double x = effectRadius * Math.cos(angle);
            double z = effectRadius * Math.sin(angle);

            player.getWorld().spawnParticle(
                    Particle.DUST,
                    player.getLocation().add(x, 0.1, z),
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.0f)
            );
        }
    }
}