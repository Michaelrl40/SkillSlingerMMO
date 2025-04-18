package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ProximityMineSkill extends Skill {
    private final double damage;
    private final double radius;
    private final double triggerRadius;
    private final double armTime;
    private final int maxMines;
    private static final Map<UUID, Set<Location>> playerMines = new HashMap<>();

    public ProximityMineSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.radius = config.getDouble("radius", 3.0);
        this.triggerRadius = config.getDouble("triggerRadius", 2.0);
        this.armTime = config.getDouble("armTime", 2.0);
        this.maxMines = config.getInt("maxMines", 3);
    }

    @Override
    protected void performSkill(Player player) {
        // Get the target location (block player is looking at)
        Location target = player.getTargetBlock(null, 5).getLocation().add(0, 1, 0);

        // Check if player has too many mines
        Set<Location> mines = playerMines.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (mines.size() >= maxMines) {
            player.sendMessage("§c✦ You have too many active mines! (Maximum: " + maxMines + ")");
            setSkillSuccess(false);
            return;
        }

        // Place the mine
        placeMine(player, target);
        setSkillSuccess(true);
    }

    private void placeMine(Player player, Location location) {
        // Place a red candle at the mine location
        location.getBlock().setType(Material.RED_CANDLE);

        // Add mine to player's set
        Set<Location> mines = playerMines.get(player.getUniqueId());
        mines.add(location);

        // Visual effect for placing mine
        location.getWorld().spawnParticle(Particle.DUST, location, 10,
                0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(Color.RED, 1));
        location.getWorld().playSound(location, Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);

        // Show arming animation
        new BukkitRunnable() {
            private final double startTime = System.currentTimeMillis();

            @Override
            public void run() {
                if (!mines.contains(location)) {
                    this.cancel();
                    return;
                }

                double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                if (elapsed >= armTime) {
                    // Mine is armed
                    location.getWorld().spawnParticle(Particle.DUST, location, 5,
                            0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.GREEN, 1));
                    startTriggerDetection(player, location);
                    this.cancel();
                } else {
                    // Arming animation
                    location.getWorld().spawnParticle(Particle.DUST, location, 5,
                            0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.YELLOW, 1));
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void startTriggerDetection(Player owner, Location mineLocation) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<Location> mines = playerMines.get(owner.getUniqueId());
                if (mines == null || !mines.contains(mineLocation)) {
                    this.cancel();
                    return;
                }

                // Check for nearby entities
                for (Entity entity : mineLocation.getWorld().getNearbyEntities(mineLocation, triggerRadius, triggerRadius, triggerRadius)) {
                    if (!(entity instanceof LivingEntity) || entity == owner) continue;

                    // Check if entity is in party
                    if (entity instanceof Player) {
                        Player targetPlayer = (Player) entity;
                        if (plugin.getPartyManager().getParty(owner) != null &&
                                plugin.getPartyManager().getParty(owner).isMember(targetPlayer)) {
                            continue;
                        }
                    }

                    // Trigger the mine
                    detonateMine(owner, mineLocation);
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, (long)(armTime * 20), 5L);
    }

    private void detonateMine(Player owner, Location mineLocation) {
        // Remove the candle
        mineLocation.getBlock().setType(Material.AIR);

        // Remove from tracking
        Set<Location> mines = playerMines.get(owner.getUniqueId());
        if (mines != null) {
            mines.remove(mineLocation);
        }

        // Visual and sound effects
        mineLocation.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, mineLocation, 1);
        mineLocation.getWorld().playSound(mineLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Damage nearby entities
        for (Entity entity : mineLocation.getWorld().getNearbyEntities(mineLocation, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity) || entity == owner) continue;

            LivingEntity target = (LivingEntity) entity;

            // Check party protection
            if (target instanceof Player) {
                Player targetPlayer = (Player) target;
                if (plugin.getPartyManager().getParty(owner) != null &&
                        plugin.getPartyManager().getParty(owner).isMember(targetPlayer)) {
                    continue;
                }
            }

            // Calculate damage based on distance
            double distance = target.getLocation().distance(mineLocation);
            double scaledDamage = damage * (1 - (distance / radius));

            // Apply damage
            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, owner));
            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, scaledDamage));
            target.damage(0.1, owner); // Trigger damage event with minimal damage

            // Knockback effect
            Vector knockback = target.getLocation().subtract(mineLocation).toVector().normalize();
            target.setVelocity(knockback.multiply(0.5));
        }
    }

    public static void cleanup() {
        // Remove all placed candles when cleaning up
        for (Set<Location> mines : playerMines.values()) {
            for (Location mineLoc : mines) {
                if (mineLoc.getBlock().getType() == Material.RED_CANDLE) {
                    mineLoc.getBlock().setType(Material.AIR);
                }
            }
        }
        playerMines.clear();
    }
}