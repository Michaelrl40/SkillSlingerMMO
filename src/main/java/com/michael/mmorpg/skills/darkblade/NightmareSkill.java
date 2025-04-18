package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class NightmareSkill extends Skill {
    private final int duration;
    private final int arenaWidth;
    private final int arenaHeight;
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    private final Map<UUID, List<Block>> arenaBlocks = new HashMap<>();
    // Build arena high in the sky
    private static final int ARENA_Y = 200;  // High altitude

    public NightmareSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 10);
        this.arenaWidth = config.getInt("arenawidth", 7);
        this.arenaHeight = config.getInt("arenaheight", 5);
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, targetRange);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        // Check if target is a player
        if (!(currentTarget instanceof Player)) {
            caster.sendMessage("§c✦ You can only drag players into nightmares!");
            return;
        }

        // Check for PvP protection
        if (currentTarget instanceof Player) {
            Player targetPlayer = (Player) currentTarget;
            if (!isPvPAllowed(caster, targetPlayer)) {
                caster.sendMessage("§c✦ You cannot use Nightmare in a PvP-protected area!");
                return;
            }
        }

        // Start casting if has cast time
        if (hasCastTime) {
            caster.sendMessage("§5✦ Dragging " + currentTarget.getName() + " into a nightmare...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        Player target = (Player) currentTarget;

        // Build arena and execute nightmare
        executeNightmare(caster, target);
    }

    private void executeNightmare(Player caster, Player target) {
        World world = caster.getWorld();

        // Calculate arena center high in the sky
        Location arenaCenter = caster.getLocation().clone();
        arenaCenter.setY(ARENA_Y);

        // Debug message
        caster.sendMessage("§5✦ Creating nightmare dome in the sky...");

        // Store original locations
        originalLocations.put(caster.getUniqueId(), caster.getLocation().clone());
        originalLocations.put(target.getUniqueId(), target.getLocation().clone());

        // Build the arena first and ensure it's complete
        List<Block> blocks = buildNightmareArena(arenaCenter);
        arenaBlocks.put(caster.getUniqueId(), blocks);

        // Create teleport locations
        Location casterSpot = arenaCenter.clone().add(arenaWidth/4, 1, 0);
        Location targetSpot = arenaCenter.clone().add(-arenaWidth/4, 1, 0);

        // Ensure spawn points are safe
        casterSpot.getBlock().setType(Material.AIR);
        casterSpot.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        targetSpot.getBlock().setType(Material.AIR);
        targetSpot.clone().add(0, 1, 0).getBlock().setType(Material.AIR);

        // Teleport players after ensuring arena is built
        new BukkitRunnable() {
            @Override
            public void run() {
                // Play pre-teleport effects at original locations
                playTeleportEffect(caster.getLocation());
                playTeleportEffect(target.getLocation());

                // Teleport players
                caster.teleport(casterSpot);
                target.teleport(targetSpot);

                // Play post-teleport effects
                playTeleportEffect(casterSpot);
                playTeleportEffect(targetSpot);

                // Send messages
                target.sendMessage("§5✦ " + caster.getName() + " has dragged you into a nightmare realm!");
                caster.sendMessage("§5✦ You've trapped " + target.getName() + " in your nightmare realm!");

                // Start the nightmare sequence
                startNightmareSequence(caster, target, arenaCenter);
            }
        }.runTaskLater(plugin, 1L); // Small delay to ensure arena is fully built
    }

    private List<Block> buildNightmareArena(Location center) {
        List<Block> blocks = new ArrayList<>();
        World world = center.getWorld();
        int halfWidth = arenaWidth / 2;

        // Build the box structure
        for (int y = 0; y <= arenaHeight; y++) {
            for (int x = -halfWidth; x <= halfWidth; x++) {
                for (int z = -halfWidth; z <= halfWidth; z++) {
                    // Skip interior blocks
                    if (y > 0 && y < arenaHeight &&
                            x > -halfWidth && x < halfWidth &&
                            z > -halfWidth && z < halfWidth) {
                        continue;
                    }

                    Location blockLoc = center.clone().add(x, y, z);
                    Block block = blockLoc.getBlock();

                    // Determine block type
                    Material material;
                    if (y == 0 || y == arenaHeight) {
                        material = Material.OBSIDIAN; // Solid floor and ceiling
                    } else if (x == -halfWidth || x == halfWidth ||
                            z == -halfWidth || z == halfWidth) {
                        material = Math.random() < 0.7 ? Material.OBSIDIAN : Material.SCULK;
                    } else {
                        continue; // Skip non-wall blocks
                    }

                    // Set block immediately
                    block.setType(material, false);
                    blocks.add(block);

                    // Add lighting
                    if ((x == -halfWidth || x == halfWidth || z == -halfWidth || z == halfWidth) &&
                            y == arenaHeight/2 && (Math.abs(x) + Math.abs(z)) % 2 == 0) {
                        Location lampLoc = blockLoc.clone().add(
                                x == -halfWidth ? 1 : x == halfWidth ? -1 : 0,
                                0,
                                z == -halfWidth ? 1 : z == halfWidth ? -1 : 0
                        );
                        Block lamp = lampLoc.getBlock();
                        lamp.setType(Material.SOUL_LANTERN);
                        blocks.add(lamp);
                    }
                }
            }
        }

        return blocks;
    }

    private void startNightmareSequence(Player caster, Player target, Location arenaCenter) {
        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = duration * 20;

            @Override
            public void run() {
                if (tick >= maxTicks || !caster.isValid() || !target.isValid()) {
                    endNightmare(caster, target);
                    cancel();
                    return;
                }

                // Nightmare arena effects
                playNightmareEffects(arenaCenter);

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playNightmareEffects(Location center) {
        World world = center.getWorld();

        // Ambient particles
        for (int i = 0; i < 5; i++) {
            Location particleLoc = center.clone().add(
                    (Math.random() - 0.5) * arenaWidth,
                    (Math.random()) * arenaHeight,
                    (Math.random() - 0.5) * arenaWidth
            );

            world.spawnParticle(
                    Particle.SOUL,
                    particleLoc,
                    1, 0.1, 0.1, 0.1, 0
            );
        }

        // Additional eerie particles
        if (Math.random() < 0.3) {
            Location particleLoc = center.clone().add(
                    (Math.random() - 0.5) * arenaWidth,
                    (Math.random()) * arenaHeight,
                    (Math.random() - 0.5) * arenaWidth
            );

            world.spawnParticle(
                    Particle.WARPED_SPORE,
                    particleLoc,
                    3, 0.2, 0.2, 0.2, 0.01
            );
        }


    }

    private void playTeleportEffect(Location location) {
        World world = location.getWorld();

        world.spawnParticle(
                Particle.REVERSE_PORTAL,
                location,
                50, 0.5, 1, 0.5, 0.1
        );

        world.playSound(
                location,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                1.0f,
                0.5f
        );
    }

    private void endNightmare(Player caster, Player target) {
        // Restore original locations
        Location casterLoc = originalLocations.remove(caster.getUniqueId());
        Location targetLoc = originalLocations.remove(target.getUniqueId());

        if (casterLoc != null && caster.isValid()) {
            caster.teleport(casterLoc);
            playTeleportEffect(caster.getLocation());
        }

        if (targetLoc != null && target.isValid()) {
            target.teleport(targetLoc);
            playTeleportEffect(target.getLocation());
        }

        // Remove arena blocks
        List<Block> blocks = arenaBlocks.remove(caster.getUniqueId());
        if (blocks != null) {
            for (Block block : blocks) {
                block.setType(Material.AIR);
            }
        }

        // Send messages
        if (caster.isValid()) caster.sendMessage("§5✦ The nightmare realm dissipates...");
        if (target.isValid()) target.sendMessage("§5✦ You are freed from the nightmare!");
    }

    private boolean isPvPAllowed(Player caster, Player target) {
        // If WorldGuard integration is available
        if (plugin.getWorldGuardManager() != null) {
            return plugin.getWorldGuardManager().canPvP(caster, target);
        }

        // Fallback to the method in Skill class if it exists
        return !isInNoPvPZone(caster.getLocation()) && !isInNoPvPZone(target.getLocation());
    }


    private boolean isInNoPvPZone(Location location) {
        if (plugin.getWorldGuardManager() != null) {
            return !plugin.getWorldGuardManager().isPvPAllowed(location);
        }
        return false; // Default to allowing PvP if we can't check
    }
}