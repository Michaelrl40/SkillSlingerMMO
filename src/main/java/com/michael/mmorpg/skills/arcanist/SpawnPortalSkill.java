package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnPortalSkill extends Skill implements Listener {
    private final long portalDuration;
    private final double portalRadius;
    private final Set<UUID> recentlyTeleported = new HashSet<>();
    private final int cooldownAfterTeleport;
    private final int particlesPerCircle;

    // Track players who received portal entry message to avoid spam
    private final Map<UUID, Long> portalEntryMessageTime = new HashMap<>();
    // How often (in milliseconds) a player can receive the portal entry message
    private static final long PORTAL_MESSAGE_COOLDOWN = 3000; // 3 seconds

    // Static registry of all active portals to make cleanup easier
    private static final Map<UUID, Long> activePortals = new ConcurrentHashMap<>();

    public SpawnPortalSkill(ConfigurationSection config) {
        super(config);
        this.portalDuration = config.getLong("portalDuration", 30) * 20L; // Convert seconds to ticks
        this.portalRadius = config.getDouble("portalRadius", 1.5);
        this.cooldownAfterTeleport = config.getInt("cooldownAfterTeleport", 2); // Seconds
        this.particlesPerCircle = config.getInt("particlesPerCircle", 20);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player is in combat
        if (plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage("§c✦ You cannot create a portal while in combat!");
            setSkillSuccess(false);
            return;
        }

        // Create portal at player's location
        Location portalLocation = player.getLocation().clone();
        portalLocation.add(0, 0.1, 0); // Slightly above ground to avoid clipping

        // Mark the skill as successful before actually creating the portal
        setSkillSuccess(true);

        // Create the portal
        createPortal(player, portalLocation);

        // Announce portal creation
        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                player.getName() + " has opened a portal to spawn!");
    }

    private void createPortal(Player creator, Location location) {
        // Create an invisible armor stand as the portal marker
        ArmorStand portalMarker = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        portalMarker.setVisible(false);
        portalMarker.setGravity(false);
        portalMarker.setInvulnerable(true);
        portalMarker.setCustomName("§5§l✧ SPAWN PORTAL ✧");
        portalMarker.setCustomNameVisible(true);

        // Set metadata to identify this portal
        portalMarker.setMetadata("spawn_portal", new FixedMetadataValue(plugin, creator.getUniqueId().toString()));

        // Add creation timestamp for cleanup checks
        portalMarker.setMetadata("portal_created", new FixedMetadataValue(plugin, System.currentTimeMillis()));

        // Register this portal in our active portals map
        activePortals.put(portalMarker.getUniqueId(), System.currentTimeMillis() + (portalDuration * 50)); // End time

        // Give it a small floating end crystal as a visual marker
        if (portalMarker.getEquipment() != null) {
            // Put an end crystal on the head for visual effect
            ItemStack crystal = new ItemStack(Material.END_CRYSTAL);
            portalMarker.getEquipment().setHelmet(crystal);

            // Make it look like it's floating slightly
            portalMarker.setHeadPose(new EulerAngle(Math.toRadians(180), 0, 0));
        }

        // Start portal animation
        new BukkitRunnable() {
            private final long startTime = System.currentTimeMillis();
            private final long duration = portalDuration;
            private double rotation = 0;

            @Override
            public void run() {
                // Check if the portal should expire
                long elapsedTicks = (System.currentTimeMillis() - startTime) / 50; // Convert ms to ticks
                if (!portalMarker.isValid() || elapsedTicks > duration) {
                    // Portal expires
                    if (portalMarker.isValid()) {
                        // Remove from active portals registry
                        activePortals.remove(portalMarker.getUniqueId());

                        // Remove the portal from the world
                        portalMarker.remove();
                        location.getWorld().playSound(location, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 2.0f);

                        // Portal closing particles
                        location.getWorld().spawnParticle(
                                Particle.PORTAL,
                                location.clone().add(0, 1, 0),
                                50,
                                0.5, 1, 0.5,
                                0.1
                        );
                    }
                    this.cancel();
                    return;
                }

                // Create portal animation
                createPortalAnimation(location, rotation);

                // Increase rotation for next frame
                rotation += 5;
                if (rotation >= 360) {
                    rotation = 0;
                }

                // Add ambient sounds occasionally
                if (Math.random() < 0.1) {
                    location.getWorld().playSound(location, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createPortalAnimation(Location center, double rotation) {
        World world = center.getWorld();

        // Create two particle circles
        for (int i = 0; i < particlesPerCircle; i++) {
            double angle = Math.toRadians(i * (360.0 / particlesPerCircle) + rotation);

            // Lower circle - purple particles
            double x1 = center.getX() + portalRadius * Math.cos(angle);
            double z1 = center.getZ() + portalRadius * Math.sin(angle);
            Location particleLoc1 = new Location(world, x1, center.getY() + 0.1, z1);

            world.spawnParticle(
                    Particle.PORTAL,
                    particleLoc1,
                    1,
                    0, 0, 0,
                    0.02
            );

            // Upper circle - end rod particles
            double x2 = center.getX() + portalRadius * Math.cos(angle);
            double z2 = center.getZ() + portalRadius * Math.sin(angle);
            Location particleLoc2 = new Location(world, x2, center.getY() + 2, z2);

            world.spawnParticle(
                    Particle.END_ROD,
                    particleLoc2,
                    1,
                    0, 0, 0,
                    0.01
            );

            // Connect circles with vertical particles occasionally
            if (i % 4 == 0) {
                connectWithParticles(particleLoc1, particleLoc2);
            }
        }

        // Add some swirling particles in the center
        for (int i = 0; i < 2; i++) {
            double offsetX = (Math.random() - 0.5) * portalRadius;
            double offsetZ = (Math.random() - 0.5) * portalRadius;

            Location particleLoc = center.clone().add(offsetX, 1, offsetZ);
            world.spawnParticle(
                    Particle.DRAGON_BREATH,
                    particleLoc,
                    1,
                    0, 0, 0,
                    0.05
            );
        }
    }

    private void connectWithParticles(Location lower, Location upper) {
        World world = lower.getWorld();
        double distance = upper.getY() - lower.getY();
        int steps = (int) (distance * 5); // 5 particles per block

        for (int i = 0; i < steps; i++) {
            double progress = (double) i / steps;
            double y = lower.getY() + (progress * distance);

            Location particleLoc = new Location(
                    world,
                    lower.getX(),
                    y,
                    lower.getZ()
            );

            world.spawnParticle(
                    Particle.WITCH,
                    particleLoc,
                    1,
                    0.1, 0, 0.1,
                    0
            );
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip if only looking around (head movement)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Skip if player was recently teleported to avoid loop
        if (recentlyTeleported.contains(playerId)) {
            return;
        }

        // Check if player is in combat
        if (plugin.getCombatManager().isInCombat(player)) {
            return; // Combat players can't use portals
        }

        boolean nearPortal = false;

        // Check if player is near any spawn portal
        for (Entity entity : player.getNearbyEntities(portalRadius, 3, portalRadius)) {
            if (entity instanceof ArmorStand && entity.hasMetadata("spawn_portal")) {
                // Found a portal!
                ArmorStand portal = (ArmorStand) entity;
                nearPortal = true;

                // Start or continue the teleport countdown
                startTeleportCountdown(player, portal);
                break; // Only process one portal at a time
            }
        }

        // If player moved away from all portals, cancel any active countdown
        if (!nearPortal) {
            cancelTeleportCountdown(player);
        }
    }

    // Map to track players in teleport countdown
    private final Map<UUID, BukkitRunnable> activeTeleports = new HashMap<>();

    private void startTeleportCountdown(Player player, ArmorStand portal) {
        UUID playerId = player.getUniqueId();

        // If player already has a countdown going, don't start a new one
        if (activeTeleports.containsKey(playerId)) {
            return;
        }

        // Check if we should send the initial message (avoid spam)
        boolean shouldSendInitialMessage = true;
        long currentTime = System.currentTimeMillis();

        if (portalEntryMessageTime.containsKey(playerId)) {
            long lastMessageTime = portalEntryMessageTime.get(playerId);
            if (currentTime - lastMessageTime < PORTAL_MESSAGE_COOLDOWN) {
                shouldSendInitialMessage = false;
            }
        }

        // Record message time
        portalEntryMessageTime.put(playerId, currentTime);

        // Initial message (only if not too recent)
        if (shouldSendInitialMessage) {
            player.sendMessage("§5✧ Stay in the portal for 5 seconds to teleport to spawn... §5✧");
        }

        // Create countdown runnable
        BukkitRunnable countdownTask = new BukkitRunnable() {
            private int secondsRemaining = 5;
            private final Location portalLocation = portal.getLocation().clone();
            private int lastAnnounced = 6; // Make sure first tick gets announced

            @Override
            public void run() {
                // Check if player is still in the portal zone
                if (player.getLocation().distance(portalLocation) > portalRadius ||
                        !player.isOnline() ||
                        plugin.getCombatManager().isInCombat(player) ||
                        !portal.isValid()) {

                    // Only send cancellation message if we were well into the countdown
                    if (lastAnnounced < 5) {
                        player.sendMessage("§c✧ Teleport cancelled. You left the portal or entered combat. §c✧");
                    }
                    cancelTeleportCountdown(player);
                    return;
                }

                // Update countdown
                secondsRemaining--;

                // Visual effect during countdown
                player.getWorld().spawnParticle(
                        Particle.PORTAL,
                        player.getLocation().add(0, 1, 0),
                        10,
                        0.3, 0.5, 0.3,
                        0.1
                );

                if (secondsRemaining > 0) {
                    // Only send countdown message if the second has changed
                    if (secondsRemaining != lastAnnounced) {
                        // Send countdown message
                        player.sendMessage("§5✧ Teleporting in §e" + secondsRemaining + " §5seconds... §5✧");
                        lastAnnounced = secondsRemaining;

                        // Play tick sound
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f);
                    }
                } else {
                    // Time to teleport!
                    player.sendMessage("§5✧ Teleporting now! §5✧");

                    // Add teleport cooldown
                    recentlyTeleported.add(playerId);

                    // Remove from active teleports
                    activeTeleports.remove(playerId);

                    // Teleport effects at current location
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.getWorld().spawnParticle(
                            Particle.PORTAL,
                            player.getLocation().add(0, 1, 0),
                            40,
                            0.5, 1, 0.5,
                            0.1
                    );

                    // Teleport to spawn
                    Location spawnLoc = player.getWorld().getSpawnLocation();

                    // Make sure spawn is safe
                    spawnLoc.setY(player.getWorld().getHighestBlockYAt(spawnLoc) + 1);

                    // Teleport with a short delay for effects
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.teleport(spawnLoc);

                            // Arrival effects at spawn
                            player.getWorld().playSound(spawnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                            player.getWorld().spawnParticle(
                                    Particle.END_ROD,
                                    spawnLoc.clone().add(0, 1, 0),
                                    30,
                                    0.5, 1, 0.5,
                                    0.05
                            );

                            // Remove from cooldown after a short delay
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    recentlyTeleported.remove(playerId);
                                }
                            }.runTaskLater(plugin, cooldownAfterTeleport * 20L);
                        }
                    }.runTaskLater(plugin, 5L);

                    this.cancel();
                }
            }
        };

        // Start the countdown - every second
        countdownTask.runTaskTimer(plugin, 20L, 20L);

        // Store the task
        activeTeleports.put(playerId, countdownTask);
    }

    private void cancelTeleportCountdown(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitRunnable countdown = activeTeleports.remove(playerId);

        if (countdown != null) {
            countdown.cancel();
        }
    }

    /**
     * Cleans up any stuck portal entities in all worlds.
     * This method should be called on plugin startup and shutdown,
     * as well as periodically to ensure no portals are left behind.
     *
     * @param plugin The plugin instance
     */
    public static void cleanupStuckPortals(MinecraftMMORPG plugin) {
        int cleaned = 0;

        // First, remove any portals from our registry that are past their expiration time
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = activePortals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (currentTime > entry.getValue()) {
                it.remove();
            }
        }

        // Now check all entities in all worlds for portals
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand && entity.hasMetadata("spawn_portal")) {
                    boolean shouldRemove = false;

                    // Check if the portal is in our registry
                    if (!activePortals.containsKey(entity.getUniqueId())) {
                        shouldRemove = true;
                    }
                    // Double-check with timestamp for extra safety
                    else if (entity.hasMetadata("portal_created")) {
                        try {
                            long creationTime = entity.getMetadata("portal_created").get(0).asLong();
                            // Remove if older than 60 seconds (as a safety net)
                            if ((currentTime - creationTime) > 60000) {
                                shouldRemove = true;
                                activePortals.remove(entity.getUniqueId());
                            }
                        } catch (Exception e) {
                            // If we can't read the timestamp, assume it's stuck
                            shouldRemove = true;
                            activePortals.remove(entity.getUniqueId());
                        }
                    } else {
                        // No timestamp at all, definitely remove it
                        shouldRemove = true;
                    }

                    if (shouldRemove) {
                        // Portal removal effects
                        entity.getWorld().spawnParticle(
                                Particle.PORTAL,
                                entity.getLocation().add(0, 1, 0),
                                50, 0.5, 1, 0.5, 0.1
                        );
                        entity.getWorld().playSound(
                                entity.getLocation(),
                                Sound.BLOCK_PORTAL_TRAVEL,
                                0.5f,
                                2.0f
                        );

                        // Remove the entity
                        entity.remove();
                        cleaned++;
                    }
                }
            }
        }

        if (cleaned > 0) {
            plugin.getLogger().info("Portal cleanup: Removed " + cleaned + " stuck portals");
        }
    }

    /**
     * Starts a recurring task to clean up any stuck portals.
     * Should be called when the plugin starts up.
     *
     * @param plugin The plugin instance
     */
    public static void startPortalCleanupTask(MinecraftMMORPG plugin) {
        // Run cleanup every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupStuckPortals(plugin);
            }
        }.runTaskTimer(plugin, 20 * 60 * 5, 20 * 60 * 5); // 5 minutes in ticks
    }

    /**
     * Checks if a specific entity is a portal that should be removed.
     * Helper method to determine if a portal is stuck or expired.
     *
     * @param entity The entity to check
     * @return true if the portal should be removed, false otherwise
     */
    private static boolean shouldRemovePortal(Entity entity) {
        if (!entity.hasMetadata("spawn_portal")) {
            return false;
        }

        // Check registry first (most efficient)
        if (!activePortals.containsKey(entity.getUniqueId())) {
            return true;
        }

        // Double-check with timestamp
        if (entity.hasMetadata("portal_created")) {
            try {
                long creationTime = entity.getMetadata("portal_created").get(0).asLong();
                long currentTime = System.currentTimeMillis();
                return (currentTime - creationTime) > 60000; // 60 seconds
            } catch (Exception e) {
                return true;
            }
        }

        // No timestamp, assume it should be removed
        return true;
    }
}