package com.michael.mmorpg.skills.bard;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.party.Party;

public class HealingHymnSkill extends Skill {
    private final double healPerTick;
    private final int duration;
    private final double radius;
    private final int tickInterval;

    public HealingHymnSkill(ConfigurationSection config) {
        super(config);
        this.healPerTick = config.getDouble("healpertick", 2.0);
        this.duration = config.getInt("duration", 200); // 10 seconds
        this.radius = config.getDouble("radius", 8.0);
        this.tickInterval = config.getInt("tickinterval", 20); // Heal every second

        // The mana cost and cooldown should be set in the config file and
        // handled automatically by the parent Skill class
    }

    @Override
    protected void performSkill(Player player) {
        Party party = plugin.getPartyManager().getParty(player);

        // Initial visual and sound effects
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_FLUTE, 1.0f, 1.0f);

        // Start healing over time effect
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    this.cancel();
                    return;
                }

                if (ticks >= duration) {
                    this.cancel();
                    return;
                }

                if (ticks % tickInterval == 0) {
                    // Get current player location
                    Location currentCenter = player.getLocation();

                    // Play ongoing sound effects
                    currentCenter.getWorld().playSound(currentCenter, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.4f);

                    // Create healing zone particles
                    for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        Location particleLoc = currentCenter.clone().add(x, 0.1, z);

                        // Green healing particles
                        currentCenter.getWorld().spawnParticle(
                                Particle.DUST,
                                particleLoc,
                                3, 0.2, 0.1, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.0f)
                        );

                        // Add some note particles
                        if (Math.random() < 0.3) {
                            currentCenter.getWorld().spawnParticle(
                                    Particle.NOTE,
                                    particleLoc.clone().add(0, 1, 0),
                                    1, 0.1, 0.1, 0.1, 0
                            );
                        }
                    }

                    // Heal nearby party members using current location
                    if (party != null) {
                        for (Player member : party.getMembers()) {
                            if (member.getLocation().distance(currentCenter) <= radius) {
                                healPlayer(member);
                            }
                        }
                    } else {
                        // If no party, just heal the caster
                        healPlayer(player);
                    }
                }

                ticks++;
            }

            private void healPlayer(Player target) {
                double currentHealth = target.getHealth();
                double maxHealth = target.getMaxHealth();
                double newHealth = Math.min(maxHealth, currentHealth + healPerTick);
                double actualHealing = newHealth - currentHealth;

                if (actualHealing > 0) {
                    target.setHealth(newHealth);

                    // Show healing numbers
                    plugin.getDamageDisplayManager().spawnDamageDisplay(
                            target.getLocation(),
                            actualHealing,
                            DamageDisplayManager.DamageType.HEALING
                    );

                    // Individual healing particles
                    target.getWorld().spawnParticle(
                            Particle.HEART,
                            target.getLocation().add(0, 2, 0),
                            1, 0.1, 0.1, 0.1, 0
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Broadcast skill use
        if (party != null) {
            broadcastLocalSkillMessage(player, "§a[Bard] " + player.getName() + " performs a Healing Hymn!");
        } else {
            player.sendMessage("§a✦ You perform a Healing Hymn!");
        }

        setSkillSuccess(true);
    }
}