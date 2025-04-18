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
import com.michael.mmorpg.models.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class ManaSongSkill extends Skill {
    private final double manaRegenBonus;
    private final int duration;
    private final double effectRadius;

    public ManaSongSkill(ConfigurationSection config) {
        super(config);
        this.manaRegenBonus = config.getDouble("manaregenbonus", 0.5); // 50% increase
        this.duration = config.getInt("duration", 200); // 10 seconds (20 ticks * 10)
        this.effectRadius = config.getDouble("radius", 20.0); // Default 20 block radius
    }

    @Override
    protected void performSkill(Player player) {
        Party party = plugin.getPartyManager().getParty(player);

        // Play mystical sound sequence
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.0f);

        // Create ascending spiral particle effect
        new BukkitRunnable() {
            double y = 0;
            int count = 0;

            @Override
            public void run() {
                if (count++ > 20) {
                    this.cancel();
                    return;
                }

                double radius = 1.0;
                double angle = y * Math.PI * 2;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;

                player.getWorld().spawnParticle(
                        Particle.DUST,
                        player.getLocation().add(x, y, z),
                        5, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(0, 191, 255), 1.0f)
                );

                y += 0.1;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Visualize the effect radius
        visualizeEffectRadius(player);

        if (party != null) {
            // Track affected and out-of-range members
            List<String> affectedMembers = new ArrayList<>();
            List<String> outOfRangeMembers = new ArrayList<>();

            // Apply mana regen buff to all party members within range
            for (Player member : party.getMembers()) {
                // Skip the caster for now (will apply separately)
                if (member.equals(player)) continue;

                // Check if member is within range
                if (member.getWorld().equals(player.getWorld()) &&
                        member.getLocation().distance(player.getLocation()) <= effectRadius) {

                    // Apply the mana regen buff
                    applyManaRegenBuff(member);
                    affectedMembers.add(member.getName());
                } else {
                    outOfRangeMembers.add(member.getName());
                }
            }

            // Always apply to self regardless of distance
            applyManaRegenBuff(player);

            // Provide feedback about affected members
            if (!affectedMembers.isEmpty()) {
                player.sendMessage("§b✦ Your Mana Song enhanced: §f" + String.join(", ", affectedMembers));
            }

            if (!outOfRangeMembers.isEmpty()) {
                player.sendMessage("§c✦ These party members were out of range: §f" +
                        String.join(", ", outOfRangeMembers));
            }

            // Broadcast to party
            broadcastLocalSkillMessage(player, "§b[Bard] " + player.getName() + " performs a Mana Song!");
        } else {
            // If not in party, just apply to self
            applyManaRegenBuff(player);
            player.sendMessage("§b✦ You perform a Mana Song!");
        }

        setSkillSuccess(true);
    }

    private void applyManaRegenBuff(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Remove any existing buff first to avoid stacking
        if (player.hasMetadata("mana_song_buff")) {
            player.removeMetadata("mana_song_buff", plugin);
        }

        // Apply mana regen buff metadata
        player.setMetadata("mana_song_buff", new FixedMetadataValue(plugin, manaRegenBonus));

        // Visual effect on buff application
        player.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );

        player.sendMessage("§b✦ Your mana regeneration is enhanced by the Mana Song!");

        // Schedule buff removal
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.hasMetadata("mana_song_buff")) {
                    player.removeMetadata("mana_song_buff", plugin);
                    player.sendMessage("§7✦ The effects of Mana Song fade away...");

                    // End effect particles
                    player.getWorld().spawnParticle(
                            Particle.INSTANT_EFFECT,
                            player.getLocation().add(0, 1, 0),
                            10, 0.5, 0.5, 0.5, 0.1
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
                    new Particle.DustOptions(Color.fromRGB(0, 191, 255), 1.0f)
            );
        }
    }
}