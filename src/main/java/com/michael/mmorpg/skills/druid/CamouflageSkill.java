package com.michael.mmorpg.skills.druid;

import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class CamouflageSkill extends DruidShapeshiftSkill implements Listener {
    private final long duration;
    private Location lastLocation;
    private BukkitRunnable durationTask;
    private boolean isStealthed = false;
    private final double detectionRange; // How close a mob needs to be to detect you

    public CamouflageSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getLong("duration", 30) * 1000;
        this.detectionRange = config.getDouble("detectionrange", 3.0); // Mobs can detect you within 3 blocks
    }

    @Override
    public void execute(Player player) {
        // Check wolf form requirement
        if (!player.hasMetadata("druid_form") ||
                !player.getMetadata("druid_form").get(0).asString().equalsIgnoreCase("Wolf")) {
            player.sendMessage("§c✦ Camouflage can only be used in Wolf form!");
            return;
        }

        // Toggle stealth off if already active
        if (player.hasMetadata("camouflaged")) {
            deactivateStealth(player);
            return;
        }

        // Store initial position
        lastLocation = player.getLocation().getBlock().getLocation();

        // Activate stealth
        activateStealth(player);
    }

    @Override
    protected void performSkill(Player player) {

    }

    private void activateStealth(Player player) {
        // Mark player as stealthed
        player.setMetadata("camouflaged", new FixedMetadataValue(plugin, true));
        isStealthed = true;

        // Hide from other players
        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            if (otherPlayer != player) {
                otherPlayer.hidePlayer(plugin, player);
            }
        }

        // Clear existing mob targets
        clearMobTargets(player);

        // Register event listeners
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Play stealth effect
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRASS_STEP, 1.0f, 0.5f);
        player.getWorld().spawnParticle(Particle.SMOKE,
                player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);

        // Broadcast stealth message
        broadcastLocalSkillMessage(player, "§2[Druid] " + player.getName() + " fades into the shadows!");

        // Start duration timer
        durationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && isStealthed) {
                    deactivateStealth(player);
                }
            }
        };
        durationTask.runTaskLater(plugin, duration / 50);
    }

    private void clearMobTargets(Player player) {
        // Clear any mobs currently targeting this player
        player.getWorld().getLivingEntities().stream()
                .filter(entity -> entity instanceof Mob)
                .map(entity -> (Mob) entity)
                .filter(mob -> mob.getTarget() != null && mob.getTarget().equals(player))
                .forEach(mob -> mob.setTarget(null));
    }

    private void deactivateStealth(Player player) {
        if (!isStealthed) return;

        // Remove stealth metadata
        player.removeMetadata("camouflaged", plugin);
        isStealthed = false;

        // Show player to everyone
        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            otherPlayer.showPlayer(plugin, player);
        }

        // Unregister events
        HandlerList.unregisterAll(this);

        // Cancel duration task
        if (durationTask != null) {
            durationTask.cancel();
            durationTask = null;
        }

        // Play reveal effect
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.SMOKE,
                player.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);

        // Broadcast reveal message
        broadcastLocalSkillMessage(player, "§2[Druid] " + player.getName() + " emerges from the shadows!");

        // Start cooldown
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player)) return;

        Player player = (Player) event.getTarget();
        if (!player.hasMetadata("camouflaged")) return;

        // If the entity is too close, they can detect you
        if (event.getEntity().getLocation().distance(player.getLocation()) <= detectionRange) {
            player.sendMessage("§c✦ A creature has detected you!");
            deactivateStealth(player);
            return;
        }

        // Cancel the targeting event
        event.setCancelled(true);
        if (event.getEntity() instanceof Mob) {
            ((Mob) event.getEntity()).setTarget(null);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("camouflaged")) return;

        Location fromBlock = event.getFrom().getBlock().getLocation();
        Location toBlock = event.getTo().getBlock().getLocation();

        if (!toBlock.equals(lastLocation)) {
            deactivateStealth(player);
            player.sendMessage("§c✦ Your movement breaks your camouflage!");
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (player.hasMetadata("camouflaged")) {
            deactivateStealth(player);
            player.sendMessage("§c✦ Taking damage breaks your camouflage!");
        }
    }

    // Required method implementations from DruidShapeshiftSkill
    @Override protected Disguise createDisguise() { return null; }
    @Override protected void setupDisguise(Disguise disguise) {}
    @Override protected void applyFormEffects(Player player) {}
    @Override protected void removeFormEffects(Player player) {}
}