package com.michael.mmorpg.skills.skyknight;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AerialGraspSkill extends Skill {
    private static final Map<UUID, GraspData> activeGrasps = new HashMap<>();
    private final double holdTime;  // Time in seconds to hold target
    private final double maxHeight; // Maximum height to lift target

    private static class GraspData {
        final LivingEntity target;
        final BukkitTask task;
        final double startTime;

        GraspData(LivingEntity target, BukkitTask task) {
            this.target = target;
            this.task = task;
            this.startTime = System.currentTimeMillis();
        }
    }

    public AerialGraspSkill(ConfigurationSection config) {
        super(config);
        this.holdTime = config.getDouble("holdtime", 4.0);  // Default 4 seconds holding
        this.maxHeight = config.getDouble("maxheight", 3.0);  // Default 3 blocks up
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player is already holding something
        if (activeGrasps.containsKey(player.getUniqueId())) {
            player.sendMessage("§c✦ You are already holding a target!");
            setSkillSuccess(false);
            return;
        }

        // Get target in front of player using base Skill's melee targeting
        LivingEntity target = getMeleeTarget(player, 3.0);

        if (target == null) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Set current target for skill systems
        this.currentTarget = target;

        // Start the grab sequence
        startGrasp(player, target);
        setSkillSuccess(true);
    }

    private void startGrasp(Player player, LivingEntity target) {
        // Initial grab effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
        target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 15, 0.5, 0.5, 0.5, 0.05);

        // Schedule the holding sequence
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks = 0;
            private final int maxTicks = (int)(holdTime * 20); // Convert seconds to ticks
            private double currentHeight = 0;
            private final double riseSpeed = maxHeight / (maxTicks / 2.0); // Rise to max height in half the time
            private boolean isRising = true;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || target.isDead()) {
                    releaseTarget(player, false);
                    return;
                }

                if (ticks >= maxTicks) {
                    // Time expired, drop gently
                    releaseTarget(player, true);
                    return;
                }

                // Calculate position above player
                Location playerLoc = player.getLocation();
                Location holdPos = playerLoc.clone();

                if (isRising && currentHeight < maxHeight) {
                    // Still rising phase
                    currentHeight += riseSpeed;
                    if (currentHeight >= maxHeight) {
                        isRising = false;
                        currentHeight = maxHeight;
                    }
                } else if (ticks > maxTicks / 2) {
                    // Start gradually feeling heavier
                    double timeRatio = (double)(ticks - maxTicks/2) / (maxTicks/2);

                    // Visual struggling effect increases with time
                    if (ticks % 10 == 0) {
                        player.getWorld().playSound(
                                player.getLocation(),
                                Sound.ENTITY_PLAYER_BREATH,
                                0.5f + (float)timeRatio * 0.5f,
                                0.8f - (float)timeRatio * 0.3f
                        );

                        // Particle effect showing strain
                        player.getWorld().spawnParticle(
                                Particle.SMOKE,
                                player.getLocation().add(0, 1, 0),
                                (int)(5 * timeRatio),
                                0.2, 0.2, 0.2,
                                0.02
                        );
                    }

                    // Calculate slight dip in height as target gets "heavier"
                    currentHeight = maxHeight * (1 - (timeRatio * 0.2));
                }

                // Position target above player
                holdPos.add(0, currentHeight, 0);
                target.teleport(holdPos);

                // Visual effects during hold
                if (ticks % 5 == 0) {
                    target.getWorld().spawnParticle(
                            Particle.CLOUD,
                            target.getLocation(),
                            3, 0.2, 0.2, 0.2, 0.01
                    );
                }

                ticks++;
            }
        }, 0L, 1L);

        // Store grasp data
        activeGrasps.put(player.getUniqueId(), new GraspData(target, task));

        // Message to player
        player.sendMessage("§b✦ You grasp " + (target instanceof Player ?
                ((Player)target).getDisplayName() : "your target") + " with air currents!");

        // Message to target if it's a player
        if (target instanceof Player) {
            ((Player)target).sendMessage("§b✦ " + player.getDisplayName() +
                    " has lifted you with wind magic!");
        }
    }

    private void releaseTarget(Player player, boolean controlled) {
        GraspData data = activeGrasps.remove(player.getUniqueId());
        if (data != null) {
            data.task.cancel();

            // Final effects for release
            if (controlled) {
                // Normal release - target gets too heavy
                player.sendMessage("§6✦ Your target becomes too heavy to hold!");
                player.getWorld().playSound(
                        player.getLocation(),
                        Sound.ENTITY_PLAYER_HURT,
                        0.5f, 1.2f
                );

                // Gentle release with some wind particles
                data.target.getWorld().spawnParticle(
                        Particle.CLOUD,
                        data.target.getLocation(),
                        20, 0.5, 0.3, 0.5, 0.05
                );

                // Target player message
                if (data.target instanceof Player) {
                    ((Player)data.target).sendMessage("§6✦ " + player.getDisplayName() +
                            " can no longer hold you up!");
                }
            } else {
                // Unexpected release - player logged off or target died
                if (player.isOnline()) {
                    player.sendMessage("§c✦ You lost your grip on your target!");
                }
            }
        }
    }

    // Clean up method for plugin disable
    public static void cleanup() {
        activeGrasps.values().forEach(data -> data.task.cancel());
        activeGrasps.clear();
    }
}