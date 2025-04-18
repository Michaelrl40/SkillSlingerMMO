package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class SmokeBombSkill extends Skill implements Listener {
    private final long duration;  // Duration in ticks
    private static final HashMap<UUID, ItemStack[]> storedArmor = new HashMap<>();
    private static final HashMap<UUID, ItemStack[]> storedHeldItems = new HashMap<>();
    private static final HashMap<UUID, BukkitTask> activeEffects = new HashMap<>();

    public SmokeBombSkill(ConfigurationSection config) {
        super(config);
        // Convert duration from seconds to ticks (20 ticks = 1 second)
        this.duration = (long)(config.getDouble("duration", 5.0) * 20);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void performSkill(Player player) {
        // Cancel any existing smoke bomb effect
        BukkitTask existingTask = activeEffects.remove(player.getUniqueId());
        if (existingTask != null) {
            existingTask.cancel();
            removeSmokeBombEffects(player);
        }

        // Apply smoke bomb effects
        applySmokeBombEffects(player);

        // Schedule the end of the effect
        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.hasMetadata("smokebomb")) {
                    removeSmokeBombEffects(player);
                    player.sendMessage("§7✦ Your smoke bomb effect fades...");
                }
            }
        }.runTaskLater(plugin, duration);

        activeEffects.put(player.getUniqueId(), task);
        setSkillSuccess(true);
    }

    private void applySmokeBombEffects(Player player) {
        // Store and hide equipment
        storeAndHideEquipment(player);

        // Apply effects
        player.setMetadata("smokebomb", new FixedMetadataValue(plugin, true));
        player.setMetadata("block_skills", new FixedMetadataValue(plugin, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int)duration, 0, false, false));

        // Create smoke cloud effect
        createSmokeCloud(player.getLocation());

        // Play sound effect
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_FIRE_EXTINGUISH,
                1.0f,
                1.2f
        );

        player.sendMessage("§7✦ You vanish in a cloud of smoke!");
    }

    void removeSmokeBombEffects(Player player) {
        // Remove effects and metadata
        player.removeMetadata("smokebomb", plugin);
        player.removeMetadata("block_skills", plugin);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Restore equipment
        restoreEquipment(player);

        // Play fade effect
        createSmokeFadeEffect(player.getLocation());
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_FIRE_EXTINGUISH,
                0.5f,
                0.8f
        );

        // Remove from active effects tracking
        activeEffects.remove(player.getUniqueId());
    }

    private void createSmokeCloud(@NotNull Location location) {
        // Create a dense smoke cloud effect
        location.getWorld().spawnParticle(
                Particle.LARGE_SMOKE,
                location.add(0, 1, 0),
                50,  // Number of particles
                1.0, 1.0, 1.0,  // Spread
                0.05  // Speed
        );

        // Add some campfire smoke for vertical effect
        location.getWorld().spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE,
                location,
                20,  // Number of particles
                0.5, 0.5, 0.5,  // Spread
                0.05  // Speed
        );
    }

    private void createSmokeFadeEffect(Location location) {
        location.getWorld().spawnParticle(
                Particle.SMOKE,
                location.add(0, 1, 0),
                20,  // Number of particles
                0.5, 0.5, 0.5,  // Spread
                0.02  // Speed
        );
    }

    private void storeAndHideEquipment(Player player) {
        PlayerInventory inv = player.getInventory();
        UUID playerId = player.getUniqueId();

        // Store armor pieces
        ItemStack[] armor = new ItemStack[4];
        armor[0] = inv.getHelmet() != null ? inv.getHelmet().clone() : null;
        armor[1] = inv.getChestplate() != null ? inv.getChestplate().clone() : null;
        armor[2] = inv.getLeggings() != null ? inv.getLeggings().clone() : null;
        armor[3] = inv.getBoots() != null ? inv.getBoots().clone() : null;
        storedArmor.put(playerId, armor);

        // Store held items
        ItemStack[] heldItems = new ItemStack[2];
        heldItems[0] = inv.getItemInMainHand().clone();
        heldItems[1] = inv.getItemInOffHand().clone();
        storedHeldItems.put(playerId, heldItems);

        // Hide all equipment
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        inv.setItemInMainHand(null);
        inv.setItemInOffHand(null);
    }

    private void restoreEquipment(Player player) {
        PlayerInventory inv = player.getInventory();
        UUID playerId = player.getUniqueId();

        // Restore armor
        ItemStack[] armor = storedArmor.remove(playerId);
        if (armor != null) {
            inv.setHelmet(armor[0]);
            inv.setChestplate(armor[1]);
            inv.setLeggings(armor[2]);
            inv.setBoots(armor[3]);
        }

        // Restore held items
        ItemStack[] heldItems = storedHeldItems.remove(playerId);
        if (heldItems != null) {
            inv.setItemInMainHand(heldItems[0]);
            inv.setItemInOffHand(heldItems[1]);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        // Break smoke bomb effect on any damage
        if (player.hasMetadata("smokebomb") && !event.isCancelled()) {
            player.sendMessage("§c✦ Your smoke bomb effect was broken by damage!");
            removeSmokeBombEffects(player);

            // Cancel the scheduled end task
            BukkitTask task = activeEffects.remove(player.getUniqueId());
            if (task != null) {
                task.cancel();
            }
        }
    }
}