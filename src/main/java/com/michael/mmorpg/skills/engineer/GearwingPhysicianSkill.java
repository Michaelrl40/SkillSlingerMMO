package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GearwingPhysicianSkill extends Skill {
    private final double duration;
    private final double healAmount;
    private final double healInterval;
    private final double followRange;

    public GearwingPhysicianSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getDouble("duration", 20.0);
        this.healAmount = config.getDouble("healamount", 2.0);
        this.healInterval = config.getDouble("healinterval", 1.0);
        this.followRange = config.getDouble("followrange", 3.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Spawn the Allay slightly above and behind the player
        Location spawnLoc = player.getLocation().add(0, 1, 0);
        Allay physician = player.getWorld().spawn(spawnLoc, Allay.class);

        // Set metadata to identify this as a special Allay
        physician.setMetadata("gearwing_physician", new FixedMetadataValue(plugin, true));
        physician.setMetadata("owner", new FixedMetadataValue(plugin, player.getUniqueId()));

        // Customize the Allay
        physician.setCustomName(ChatColor.GOLD + "Gearwing Physician");
        physician.setCustomNameVisible(true);
        physician.setGlowing(true); // Add a glow effect

        // Spawn-in effects
        spawnLoc.getWorld().playSound(spawnLoc, Sound.BLOCK_ANVIL_USE, 0.5f, 2.0f);
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 1.2f);

        // Initial mechanical spawn effects
        spawnMechanicalEffects(spawnLoc);

        // Start the healing and following behavior
        new BukkitRunnable() {
            private int ticksElapsed = 0;
            private int particleRotation = 0;
            private final int healIntervalTicks = (int) (healInterval * 20); // Convert seconds to ticks

            @Override
            public void run() {
                // Check if the Allay is still valid and alive
                if (!physician.isValid() || physician.isDead()) {
                    this.cancel();
                    return;
                }

                // Check if duration has expired
                if (ticksElapsed >= duration * 20) { // Convert duration to ticks
                    despawnPhysician(physician);
                    this.cancel();
                    return;
                }

                // Move towards player if too far
                if (physician.getLocation().distance(player.getLocation()) > followRange) {
                    Vector direction = player.getLocation().add(0, 1, 0)
                            .subtract(physician.getLocation()).toVector().normalize();
                    physician.setVelocity(direction.multiply(0.5));
                }

                // Heal on interval (using tick-based timing)
                if (ticksElapsed % healIntervalTicks == 0) {
                    healPlayer(player, physician.getLocation());
                }

                // Continuous mechanical effects
                showHealingEffects(physician.getLocation(), particleRotation++);

                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick instead of every 2 ticks

        setSkillSuccess(true);
    }

    private void healPlayer(Player player, Location physicianLoc) {
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double currentHealth = player.getHealth();
        double newHealth = Math.min(maxHealth, currentHealth + healAmount);
        double actualHealing = newHealth - currentHealth;

        if (actualHealing > 0) {
            // First apply the healing
            player.setHealth(newHealth);

            // Then show the healing display - using the specific pattern that works in other skills
            getPlugin().getDamageDisplayManager().spawnDamageDisplay(
                    player.getLocation(),
                    actualHealing,
                    DamageDisplayManager.DamageType.HEALING
            );

            // Keep the existing healing effect sounds
            player.getWorld().playSound(player.getLocation(),
                    Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);
        }
    }

    private void showHealingEffects(Location location, int rotation) {
        // Rotating mechanical particles
        double radius = 0.5;
        double x = radius * Math.cos(Math.toRadians(rotation * 8));
        double z = radius * Math.sin(Math.toRadians(rotation * 8));

        location.add(x, 0, z);

        // Steam particles
        location.getWorld().spawnParticle(
                Particle.CLOUD,
                location,
                1, 0.05, 0.05, 0.05, 0.01
        );

        // Healing particles
        location.getWorld().spawnParticle(
                Particle.DUST,
                location,
                1, 0.05, 0.05, 0.05, 0,
                new Particle.DustOptions(Color.fromRGB(0, 255, 0), 0.7f)
        );

        // Mechanical particles
        if (rotation % 5 == 0) {
            location.getWorld().spawnParticle(
                    Particle.CRIT,
                    location,
                    1, 0.05, 0.05, 0.05, 0
            );
        }
    }

    private void spawnMechanicalEffects(Location location) {
        // Create a spiral of particles
        for (int i = 0; i < 360; i += 15) {
            double radius = i / 360.0;
            double x = radius * Math.cos(Math.toRadians(i));
            double z = radius * Math.sin(Math.toRadians(i));

            Location particleLoc = location.clone().add(x, i/360.0, z);

            // Steam effect
            location.getWorld().spawnParticle(
                    Particle.CLOUD,
                    particleLoc,
                    1, 0, 0, 0, 0
            );

            // Mechanical sparks
            if (i % 45 == 0) {
                location.getWorld().spawnParticle(
                        Particle.CRIT,
                        particleLoc,
                        3, 0.1, 0.1, 0.1, 0.1
                );
            }
        }
    }

    private void despawnPhysician(Allay physician) {
        Location loc = physician.getLocation();

        // Despawn effects
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);
        loc.getWorld().playSound(loc, Sound.ENTITY_ALLAY_DEATH, 1.0f, 1.2f);

        // Particle effects
        loc.getWorld().spawnParticle(
                Particle.CLOUD,
                loc,
                20, 0.2, 0.2, 0.2, 0.05
        );
        loc.getWorld().spawnParticle(
                Particle.CRIT,
                loc,
                15, 0.2, 0.2, 0.2, 0.1
        );

        // Remove the Allay
        physician.remove();
    }
}