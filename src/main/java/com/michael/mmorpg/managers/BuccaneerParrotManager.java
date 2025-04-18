package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BuccaneerParrotManager implements Listener {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Parrot> parrotCompanions = new HashMap<>();
    private static final double FOLLOW_DISTANCE = 1.2; // Distance to follow at
    private static final double FOLLOW_HEIGHT = 1.5;   // Height above player
    private static final double SMOOTHING = 0.3;       // Lower = smoother movement (0-1)

    public BuccaneerParrotManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        startParrotUpdateTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isBuccaneer(player)) {
            cleanupExistingParrots(player);
            spawnParrotCompanion(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeParrot(event.getPlayer());
    }

    @EventHandler
    public void onParrotDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Parrot)) return;
        Parrot parrot = (Parrot) event.getEntity();

        if (parrot.hasMetadata("buccaneer_parrot")) {
            event.setCancelled(true);
        }
    }

    private void startParrotUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Parrot> entry : new HashMap<>(parrotCompanions).entrySet()) {
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    Parrot parrot = entry.getValue();

                    if (player == null || !player.isOnline() || parrot == null || parrot.isDead()) {
                        if (player != null) {
                            cleanupExistingParrots(player);
                            if (player.isOnline() && isBuccaneer(player)) {
                                spawnParrotCompanion(player);
                            }
                        }
                        continue;
                    }

                    updateParrotPosition(player, parrot);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void updateParrotPosition(Player player, Parrot parrot) {
        // Calculate ideal position
        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.getDirection();

        // Position slightly behind and to the right of the player
        Vector right = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        Location targetLoc = playerLoc.clone().add(right.multiply(0.5));
        targetLoc.add(0, FOLLOW_HEIGHT, 0);

        // Add slight bobbing motion
        double bobbing = Math.sin(System.currentTimeMillis() / 300.0) * 0.1;
        targetLoc.add(0, bobbing, 0);

        // Get current parrot location
        Location currentLoc = parrot.getLocation();

        // Smoothly interpolate to target position
        Vector moveVector = targetLoc.toVector().subtract(currentLoc.toVector());
        moveVector.multiply(SMOOTHING);

        // Update parrot position
        Location newLoc = currentLoc.add(moveVector);
        newLoc.setYaw(playerLoc.getYaw());
        newLoc.setPitch(0);  // Keep level pitch for natural look
        parrot.teleport(newLoc);

        // Add small particle trail for effect
        if (Math.random() < 0.1) { // Only 10% chance to spawn particles
            parrot.getWorld().spawnParticle(
                    Particle.CLOUD,
                    parrot.getLocation(),
                    1, 0.05, 0.05, 0.05, 0.01
            );
        }
    }

    private void cleanupExistingParrots(Player player) {
        // Remove from map
        Parrot oldParrot = parrotCompanions.remove(player.getUniqueId());
        if (oldParrot != null && !oldParrot.isDead()) {
            oldParrot.remove();
        }

        // Clean up any stray parrots with this player's metadata
        for (org.bukkit.entity.Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Parrot && entity.hasMetadata("buccaneer_parrot")) {
                if (entity.hasMetadata("parrot_owner")) {
                    String ownerUUID = entity.getMetadata("parrot_owner").get(0).asString();
                    if (ownerUUID.equals(player.getUniqueId().toString())) {
                        entity.remove();
                    }
                }
            }
        }
    }

    public void spawnParrotCompanion(Player player) {
        cleanupExistingParrots(player);

        // Spawn new parrot slightly above player
        Location spawnLoc = player.getLocation().add(0, FOLLOW_HEIGHT, 0);
        Parrot parrot = (Parrot) player.getWorld().spawnEntity(spawnLoc, EntityType.PARROT);
        parrot.setVariant(Parrot.Variant.RED);
        parrot.setInvulnerable(true);
        parrot.setMetadata("buccaneer_parrot", new FixedMetadataValue(plugin, true));
        parrot.setMetadata("parrot_owner", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        parrot.setRemoveWhenFarAway(false);
        parrot.setPersistent(true);
        parrot.setSitting(false);

        // Store the parrot reference
        parrotCompanions.put(player.getUniqueId(), parrot);
    }

    public void removeParrot(Player player) {
        cleanupExistingParrots(player);
    }

    public Parrot getParrot(Player player) {
        return parrotCompanions.get(player.getUniqueId());
    }

    private boolean isBuccaneer(Player player) {
        return plugin.getPlayerManager().getPlayerData(player) != null &&
                plugin.getPlayerManager().getPlayerData(player).getGameClass() != null &&
                plugin.getPlayerManager().getPlayerData(player).getGameClass().getName().equalsIgnoreCase("Buccaneer");
    }
}