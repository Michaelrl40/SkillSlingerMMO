package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ShadowClonesSkill extends Skill {
    private final int cloneCount;
    private final int duration;
    private final double radius;
    private final double maxRangeFromOrigin;

    // Store active clones per player
    private static final Map<UUID, List<ArmorStand>> activeClones = new HashMap<>();
    private static final Map<UUID, Location> originalLocations = new HashMap<>();
    private static final Map<UUID, Location> castLocations = new HashMap<>();

    public ShadowClonesSkill(ConfigurationSection config) {
        super(config);
        this.cloneCount = config.getInt("clonecount", 4);
        this.duration = config.getInt("duration", 30) * 20; // Convert to ticks
        this.radius = config.getDouble("radius", 3.0);
        this.maxRangeFromOrigin = config.getDouble("maxrangefromorigin", 30.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Remove any existing clones first
        removeClones(player);

        // Store original location
        originalLocations.put(player.getUniqueId(), player.getLocation().clone());

        // Create new clones
        List<ArmorStand> clones = new ArrayList<>();
        Location center = player.getLocation();

        // Calculate positions in a circle
        double angleIncrement = 2 * Math.PI / cloneCount;
        for (int i = 0; i < cloneCount; i++) {
            double angle = angleIncrement * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location cloneLoc = new Location(center.getWorld(), x, center.getY(), z, center.getYaw(), center.getPitch());

            // Create and set up armor stand
            ArmorStand clone = center.getWorld().spawn(cloneLoc, ArmorStand.class);
            setupClone(clone, player);
            clones.add(clone);

            // Particle effect at clone location
            clone.getWorld().spawnParticle(
                    Particle.SMOKE,
                    clone.getLocation().add(0, 1, 0),
                    20, 0.2, 0.5, 0.2, 0.05
            );

            startDistanceCheck(player);

        }

        // Store clones
        activeClones.put(player.getUniqueId(), clones);

        // Set metadata for clone skill targeting
        player.setMetadata("has_shadow_clones", new FixedMetadataValue(plugin, true));

        // Duration timer
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.hasMetadata("has_shadow_clones")) {
                    removeClones(player);
                }
            }
        }.runTaskLater(plugin, duration);

        // Success effect
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
        setSkillSuccess(true);
    }

    private void setupClone(ArmorStand clone, Player player) {
        // Make it invisible but keep equipment visible
        clone.setVisible(false);
        clone.setGravity(false);
        clone.setInvulnerable(true);
        clone.setArms(true);
        clone.setBasePlate(false);

        // Copy player's equipment
        clone.setHelmet(player.getInventory().getHelmet());
        clone.setChestplate(player.getInventory().getChestplate());
        clone.setLeggings(player.getInventory().getLeggings());
        clone.setBoots(player.getInventory().getBoots());

        // Give all clones a netherite sword
        clone.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        // Mark as a clone
        clone.setMetadata("ninja_clone", new FixedMetadataValue(plugin, true));
    }

    private void startDistanceCheck(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.hasMetadata("has_shadow_clones")) {
                    this.cancel();
                    return;
                }

                Location castLoc = castLocations.get(player.getUniqueId());
                if (castLoc != null && player.getLocation().distance(castLoc) > maxRangeFromOrigin) {
                    // Player too far, remove clones with fade effect
                    List<ArmorStand> clones = activeClones.get(player.getUniqueId());
                    if (clones != null) {
                        for (ArmorStand clone : clones) {
                            // Fade effect
                            clone.getWorld().spawnParticle(
                                    Particle.SMOKE,
                                    clone.getLocation().add(0, 1, 0),
                                    30, 0.3, 0.7, 0.3, 0.05
                            );
                            clone.getWorld().playSound(
                                    clone.getLocation(),
                                    Sound.ENTITY_ILLUSIONER_MIRROR_MOVE,
                                    0.5f,
                                    1.5f
                            );
                        }
                    }
                    removeClones(player);
                    player.sendMessage("§c✦ Your shadow clones fade as you move too far from the cast location!");
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20, 20); // Check every second
    }


    public static void removeClones(Player player) {
        List<ArmorStand> clones = activeClones.remove(player.getUniqueId());
        if (clones != null) {
            clones.forEach(clone -> {
                // Smoke effect on removal
                clone.getWorld().spawnParticle(
                        Particle.SMOKE,
                        clone.getLocation().add(0, 1, 0),
                        20, 0.2, 0.5, 0.2, 0.05
                );
                clone.remove();
            });
        }
        originalLocations.remove(player.getUniqueId());
        player.removeMetadata("has_shadow_clones", plugin);
    }

    public static List<Location> getCloneLocations(Player player) {
        List<Location> locations = new ArrayList<>();
        List<ArmorStand> clones = activeClones.get(player.getUniqueId());
        if (clones != null) {
            clones.forEach(clone -> locations.add(clone.getLocation()));
        }
        Location castLoc = castLocations.get(player.getUniqueId());
        if (castLoc != null) {
            locations.add(castLoc);
        }
        return locations;
    }
}
