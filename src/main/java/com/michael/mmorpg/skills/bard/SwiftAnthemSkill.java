package com.michael.mmorpg.skills.bard;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.michael.mmorpg.party.Party;

import java.util.ArrayList;
import java.util.List;

public class SwiftAnthemSkill extends Skill {
    private final int duration;
    private final int amplifier;
    private final double effectRadius;

    public SwiftAnthemSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 200); // 10 seconds (20 ticks per second)
        this.amplifier = config.getInt("amplifier", 3); // Speed IV (0-based, so 3)
        this.effectRadius = config.getDouble("radius", 12.0); // Default 20 block radius
    }

    @Override
    protected void performSkill(Player player) {
        Party party = plugin.getPartyManager().getParty(player);

        // Visual and sound effects at player's location
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 1.4f);

        // Create spiral particle effect
        for (double y = 0; y < 3; y += 0.2) {
            double radius = 1.0;
            double angle = y * Math.PI * 2;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            player.getWorld().spawnParticle(
                    Particle.DUST,
                    player.getLocation().add(x, y, z),
                    3, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 255, 0), 1.0f)
            );
        }

        // Show the effect radius
        visualizeEffectRadius(player);

        if (party != null) {
            // Keep track of affected members
            List<String> affectedMembers = new ArrayList<>();
            List<String> outOfRangeMembers = new ArrayList<>();

            // Apply speed boost to all party members within range
            for (Player member : party.getMembers()) {
                // Check if member is within range
                if (member.getWorld().equals(player.getWorld()) &&
                        member.getLocation().distance(player.getLocation()) <= effectRadius) {

                    // Add speed effect
                    member.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));

                    // Individual particle effect for each member
                    member.getWorld().spawnParticle(
                            Particle.NOTE,
                            member.getLocation().add(0, 2, 0),
                            10, 0.5, 0.5, 0.5, 1
                    );

                    // Notify each member
                    member.sendMessage("§e✦ You feel energized by " + player.getName() + "'s Swift Anthem!");

                    // Add to affected list
                    if (!member.equals(player)) {
                        affectedMembers.add(member.getName());
                    }
                } else if (!member.equals(player)) {
                    // Add to out of range list
                    outOfRangeMembers.add(member.getName());
                }
            }

            // Always apply to self regardless of distance checks
            if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
            }

            // Provide feedback about which members were affected
            if (!affectedMembers.isEmpty()) {
                player.sendMessage("§e✦ Your Swift Anthem affected: §f" + String.join(", ", affectedMembers));
            }

            if (!outOfRangeMembers.isEmpty()) {
                player.sendMessage("§c✦ These party members were out of range: §f" +
                        String.join(", ", outOfRangeMembers));
            }

            // Broadcast to nearby players
            broadcastLocalSkillMessage(player, "§e[Bard] " + player.getName() + " performs a Swift Anthem!");
        } else {
            // If not in party, just apply to self
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
            player.sendMessage("§e✦ You perform a Swift Anthem!");
        }

        setSkillSuccess(true);
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
                    new Particle.DustOptions(Color.fromRGB(255, 255, 0), 1.0f)
            );
        }
    }
}