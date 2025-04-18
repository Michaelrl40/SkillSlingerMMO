package com.michael.mmorpg.skills.frostmage;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.skills.Skill;
import java.util.*;

public class FrostWalkSkill extends Skill {
    private final int duration;          // How long the effect lasts in seconds
    private final int radius;            // Radius of ice creation (like enchantment level)
    private final Map<Location, Long> frozenBlocks = new HashMap<>();

    public FrostWalkSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 20);
        this.radius = config.getInt("radius", 2);
    }

    @Override
    protected void performSkill(Player player) {
        // Start the frost walk effect
        startFrostWalk(player);

        // Play activation effects
        createActivationEffects(player);

        // Send feedback message
        player.sendMessage("§b✦ Frost magic empowers your steps!");
        broadcastLocalSkillMessage(player, "§b[Frostmage] " + player.getName() + "'s steps begin to freeze the water!");

        setSkillSuccess(true);
    }

    private void startFrostWalk(Player player) {
        // Create the main effect task
        new BukkitRunnable() {
            int ticksRun = 0;

            @Override
            public void run() {
                // Check if effect should end
                if (ticksRun >= duration * 20 || !player.isOnline()) {
                    cancel();
                    removeFrostWalk(player);
                    return;
                }

                // Create ice under the player
                createIceAround(player);

                // Create ambient effects every 5 ticks
                if (ticksRun % 5 == 0) {
                    createAmbientEffects(player);
                }

                // Warn player when effect is about to end
                if (duration * 20 - ticksRun == 60) { // 3 seconds remaining
                    player.sendMessage("§b✦ Your Frost Walk is about to fade!");
                }

                ticksRun++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        // Create the ice removal task
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                // Remove ice blocks that have existed for more than 2 seconds
                Iterator<Map.Entry<Location, Long>> iterator = frozenBlocks.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Location, Long> entry = iterator.next();
                    if (currentTime - entry.getValue() > 2000) { // 2 seconds
                        Location loc = entry.getKey();
                        Block block = loc.getBlock();
                        if (block.getType() == Material.FROSTED_ICE) {
                            block.setType(Material.WATER);
                            iterator.remove();

                            // Create melting effect
                            block.getWorld().spawnParticle(Particle.SPLASH,
                                    loc.clone().add(0.5, 1, 0.5), 5, 0.2, 0, 0.2, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 20L, 20L);
    }

    private void createIceAround(Player player) {
        Location playerLoc = player.getLocation();

        // Check blocks in a square around the player
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Skip blocks too far away (make it more circular)
                if (x * x + z * z > radius * radius) continue;

                Location checkLoc = playerLoc.clone().add(x, -1, z);
                Block block = checkLoc.getBlock();

                // Check if the block is water
                if (block.getType() == Material.WATER && block.getBlockData() instanceof org.bukkit.block.data.Levelled) {
                    org.bukkit.block.data.Levelled levelledBlock = (org.bukkit.block.data.Levelled) block.getBlockData();

                    // Only freeze source water blocks
                    if (levelledBlock.getLevel() == 0) {
                        block.setType(Material.FROSTED_ICE);
                        frozenBlocks.put(block.getLocation(), System.currentTimeMillis());

                        // Create freezing effect
                        block.getWorld().spawnParticle(Particle.SNOWFLAKE,
                                block.getLocation().add(0.5, 1, 0.5), 1, 0.2, 0, 0.2, 0);
                    }
                }
            }
        }
    }

    private void createActivationEffects(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Create a swirling frost effect
        for (double i = 0; i < Math.PI * 2; i += Math.PI / 16) {
            double x = Math.cos(i) * 1.5;
            double z = Math.sin(i) * 1.5;
            Location particleLoc = loc.clone().add(x, 0.1, z);

            world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 3, 0.1, 0, 0.1, 0);
            world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0.1, 0, 0.02);
        }

        // Play activation sounds
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
        world.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5f, 1.2f);
    }

    private void createAmbientEffects(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Create small frost particles at feet
        world.spawnParticle(Particle.SNOWFLAKE,
                loc.clone().add(0, 0.1, 0), 2, 0.1, 0, 0.1, 0);

        // Occasionally play subtle ice sounds
        if (Math.random() < 0.1) {
            world.playSound(loc, Sound.BLOCK_GLASS_STEP, 0.2f, 1.5f);
        }
    }

    private void removeFrostWalk(Player player) {
        if (player.isOnline()) {
            player.sendMessage("§b✦ Your Frost Walk fades away!");

            // Create fade effect
            Location loc = player.getLocation();
            World world = player.getWorld();

            world.spawnParticle(Particle.SNOWFLAKE, loc, 20, 1, 0.1, 1, 0.1);
            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 0.8f);
        }

        // Let ice blocks naturally melt through the cleanup task
    }
}