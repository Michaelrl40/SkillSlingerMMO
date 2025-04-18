package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scoreboard.*;
import com.michael.mmorpg.skills.Skill;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StealthSkill extends Skill {
    private final float stealthSpeed;
    private final float normalSpeed = 0.2f;
    private final Scoreboard board;
    private final Team stealthTeam;
    private final Map<UUID, BukkitRunnable> stealthTasks = new HashMap<>();

    public StealthSkill(ConfigurationSection config) {
        super(config);
        this.stealthSpeed = (float) config.getDouble("stealthspeed", 0.25f);

        // Initialize scoreboard team for nameplate hiding
        this.board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team existingTeam = board.getTeam("stealth");
        if (existingTeam != null) {
            existingTeam.unregister();
        }
        this.stealthTeam = board.registerNewTeam("stealth");
        this.stealthTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }

    @Override
    protected void performSkill(Player player) {
        // Only handle activation
        if (!isToggleActive(player)) {
            activateStealth(player);
            setSkillSuccess(true);
        }
    }

    private void activateStealth(Player player) {
        // Store original walk speed
        player.setMetadata("original_speed", new FixedMetadataValue(getPlugin(), player.getWalkSpeed()));

        // Apply stealth effects
        player.setSneaking(true);
        player.setWalkSpeed(stealthSpeed);
        stealthTeam.addEntry(player.getName());

        // Start maintenance task
        startStealthTask(player);

        // Create activation effect
        createStealthEffect(player, true);

        // Send feedback message
        player.sendMessage("§8✦ You slip into the shadows... Use again to deactivate.");
    }

    private void startStealthTask(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isToggleActive(player)) {
                    cancel();
                    return;
                }

                // Ensure player stays sneaking
                if (!player.isSneaking()) {
                    player.setSneaking(true);
                }

                // Create subtle stealth particles occasionally
                if (Math.random() < 0.2) {
                    Location loc = player.getLocation();
                    player.getWorld().spawnParticle(Particle.SMOKE,
                            loc.clone().add(0, 0.1, 0), 2, 0.1, 0, 0.1, 0);
                }
            }
        };

        task.runTaskTimer(getPlugin(), 0L, 1L);
        stealthTasks.put(player.getUniqueId(), task);
    }

    @Override
    protected void onToggleDeactivate(Player player) {
        // Cancel stealth task
        BukkitRunnable task = stealthTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Restore original walking speed
        if (player.hasMetadata("original_speed")) {
            player.setWalkSpeed(player.getMetadata("original_speed").get(0).asFloat());
            player.removeMetadata("original_speed", getPlugin());
        } else {
            player.setWalkSpeed(normalSpeed);
        }

        // Remove stealth effects
        stealthTeam.removeEntry(player.getName());
        player.setSneaking(false);

        // Create deactivation effect
        createStealthEffect(player, false);

        // Send feedback message
        player.sendMessage("§7✦ You emerge from the shadows.");
    }

    private void createStealthEffect(Player player, boolean isActivating) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        if (isActivating) {
            // Activation effects
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                double radius = 0.8;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location particleLoc = loc.clone().add(x, 0.1, z);
                world.spawnParticle(Particle.SMOKE, particleLoc, 1, 0, 0, 0, 0);
            }
            world.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.2f);
            world.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3f, 0.7f);
        } else {
            // Deactivation effects
            world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.5, 0),
                    15, 0.3, 0.3, 0.3, 0.02);
            world.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.5f, 1.2f);
        }
    }

    public static void breakStealth(Player player) {
        if (player.hasMetadata("original_speed")) {
            player.sendMessage("§c✦ Your stealth is broken!");

            // Get plugin instance
            MinecraftMMORPG plugin = JavaPlugin.getPlugin(MinecraftMMORPG.class);

            // Force deactivate through command
            player.performCommand("skill stealth");

            // Create break effect
            Location loc = player.getLocation();
            World world = player.getWorld();

            world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.5, 0),
                    15, 0.2, 0.2, 0.2, 0.05);
            world.playSound(loc, Sound.ENTITY_BAT_DEATH, 0.5f, 1.2f);
        }
    }
}