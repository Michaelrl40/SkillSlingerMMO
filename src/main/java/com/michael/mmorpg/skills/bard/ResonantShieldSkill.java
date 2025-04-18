package com.michael.mmorpg.skills.bard;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.party.Party;

import java.util.ArrayList;
import java.util.List;

public class ResonantShieldSkill extends Skill {
    private final double damageReduction;
    private final int duration;
    private final double healAmount;
    private final double effectRadius;

    public ResonantShieldSkill(ConfigurationSection config) {
        super(config);
        this.damageReduction = config.getDouble("damagereduction", 0.25); // 25% damage reduction
        this.duration = config.getInt("duration", 160); // 8 seconds
        this.healAmount = config.getDouble("healamount", 4.0); // Small initial heal
        this.effectRadius = config.getDouble("radius", 12.0); // Default 20 block radius
    }

    @Override
    protected void performSkill(Player player) {
        Party party = plugin.getPartyManager().getParty(player);

        // Initial sound effect
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);

        // Visualize the effect radius
        visualizeEffectRadius(player);

        if (party != null) {
            // Track affected and out-of-range members
            List<String> affectedMembers = new ArrayList<>();
            List<String> outOfRangeMembers = new ArrayList<>();

            // Apply shield to all party members within range
            for (Player member : party.getMembers()) {
                // Skip the caster for now (we'll apply it separately to ensure they get it)
                if (member.equals(player)) continue;

                // Check if member is within range
                if (member.getWorld().equals(player.getWorld()) &&
                        member.getLocation().distance(player.getLocation()) <= effectRadius) {

                    // Apply the shield
                    applyResonantShield(member);
                    affectedMembers.add(member.getName());
                } else {
                    outOfRangeMembers.add(member.getName());
                }
            }

            // Always apply to self regardless of distance
            applyResonantShield(player);

            // Provide feedback about affected members
            if (!affectedMembers.isEmpty()) {
                player.sendMessage("§b✦ Your Resonant Shield protected: §f" + String.join(", ", affectedMembers));
            }

            if (!outOfRangeMembers.isEmpty()) {
                player.sendMessage("§c✦ These party members were out of range: §f" +
                        String.join(", ", outOfRangeMembers));
            }

            // Broadcast skill use to nearby players
            broadcastLocalSkillMessage(player, "§b[Bard] " + player.getName() + " creates a Resonant Shield!");
        } else {
            // Apply only to self if no party
            applyResonantShield(player);
            player.sendMessage("§b✦ You create a Resonant Shield!");
        }

        setSkillSuccess(true);
    }

    private void applyResonantShield(Player player) {
        // Remove any existing shield first to avoid stacking
        if (player.hasMetadata("resonant_shield")) {
            player.removeMetadata("resonant_shield", plugin);
        }

        // Apply shield buff metadata
        player.setMetadata("resonant_shield", new FixedMetadataValue(plugin, damageReduction));

        // Initial heal
        double currentHealth = player.getHealth();
        double maxHealth = player.getMaxHealth();
        player.setHealth(Math.min(maxHealth, currentHealth + healAmount));

        // Shield application effect
        createShieldEffect(player, true);

        // Periodic shield visualization
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration || !player.isOnline() || !player.hasMetadata("resonant_shield")) {
                    if (player.isOnline() && player.hasMetadata("resonant_shield")) {
                        player.removeMetadata("resonant_shield", plugin);
                        createShieldEffect(player, false);
                        player.sendMessage("§7✦ Your Resonant Shield fades away...");
                    }
                    this.cancel();
                    return;
                }

                if (ticks % 20 == 0) { // Visual effect every second
                    Location loc = player.getLocation();
                    double radius = 1.0;
                    for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        loc.getWorld().spawnParticle(
                                Particle.DUST,
                                loc.clone().add(x, 1, z),
                                1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.0f)
                        );
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createShieldEffect(Player player, boolean isApplying) {
        Location loc = player.getLocation();

        // Sound effect
        player.getWorld().playSound(loc,
                isApplying ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_AMETHYST_BLOCK_BREAK,
                1.0f, isApplying ? 1.2f : 0.8f);

        // Particle spiral
        for (double y = 0; y < 2; y += 0.1) {
            double radius = 1.0;
            double angle = y * Math.PI * 4;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            player.getWorld().spawnParticle(
                    Particle.DUST,
                    loc.clone().add(x, y, z),
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.5f)
            );
        }

        if (isApplying) {
            player.sendMessage("§b✦ You are protected by a Resonant Shield!");
        }
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
                    new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.0f)
            );
        }
    }
}