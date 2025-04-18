package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
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

import java.util.HashMap;
import java.util.UUID;

public class VanishSkill extends Skill implements Listener {
    private final double detectionRange;
    private static final HashMap<UUID, ItemStack[]> storedArmor = new HashMap<>();
    private static final HashMap<UUID, ItemStack[]> storedHeldItems = new HashMap<>();
    private static final HashMap<UUID, BukkitTask> detectionTasks = new HashMap<>();

    public VanishSkill(ConfigurationSection config) {
        super(config);
        this.detectionRange = config.getDouble("detectionrange", 3.0);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void performSkill(Player player) {
        // Reset skill success state
        setSkillSuccess(false);

        // Use the parent class's toggle state instead of metadata
        boolean currentlyActive = isToggleActive(player);

        // Handle toggle states
        if (currentlyActive) {
            // Deactivating vanish - let parent class handle toggle state
            removeVanishEffects(player);
            setSkillSuccess(true);
        } else {
            // Trying to activate vanish
            if (plugin.getCombatManager().isInCombat(player)) {
                player.sendMessage("§c✦ Cannot use Vanish while in combat!");
                return;
            }
            applyVanishEffects(player);
            setSkillSuccess(true);
        }
    }

    private void applyVanishEffects(Player player) {
        // Store and hide equipment
        storeAndHideEquipment(player);

        // Apply effects and metadata
        player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
        player.setMetadata("block_skills", new FixedMetadataValue(plugin, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        // Visual and sound effects
        playVanishEffects(player, true);
        player.sendMessage("§7✦ You fade into the shadows... §8(Cannot use skills while vanished)");

        // Start detection check
        startDetectionCheck(player);
    }

    private void removeVanishEffects(Player player) {
        // Cancel detection task if running
        BukkitTask task = detectionTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }

        // Remove effects and metadata
        player.removeMetadata("vanished", plugin);
        player.removeMetadata("block_skills", plugin);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        // Restore equipment
        restoreEquipment(player);

        // Visual and sound effects
        playVanishEffects(player, false);

        // Only show the message if the toggle is actually active in the parent class
        // This prevents double messages when breaking vanish due to damage
        if (isToggleActive(player)) {
            player.sendMessage("§7✦ You emerge from the shadows.");
        }

        // NOTE: Don't set cooldown here - let the parent class handle it
        // The parent Skill.deactivateToggle() method will handle cooldown
    }

    void breakVanish(Player player, String reason) {
        if (!player.hasMetadata("vanished")) return;

        // Remove effects first without setting cooldown
        removeVanishEffects(player);

        // Show break message
        player.sendMessage("§c✦ " + reason);

        // Force toggle state update - this will apply the cooldown once
        deactivateToggle(player);
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

        // Restore armor pieces (this part works fine as is)
        ItemStack[] armor = storedArmor.remove(playerId);
        if (armor != null) {
            inv.setHelmet(armor[0]);
            inv.setChestplate(armor[1]);
            inv.setLeggings(armor[2]);
            inv.setBoots(armor[3]);
        }

        // Get the currently held items before restoring
        ItemStack[] heldItems = storedHeldItems.remove(playerId);
        if (heldItems != null) {
            ItemStack currentMainHand = inv.getItemInMainHand();
            ItemStack currentOffHand = inv.getItemInOffHand();

            // If player is holding items when vanish ends
            if (!currentMainHand.getType().isAir() || !currentOffHand.getType().isAir()) {
                // Store current items in temporary variables
                ItemStack tempMain = currentMainHand.clone();
                ItemStack tempOff = currentOffHand.clone();

                // Clear current held items
                inv.setItemInMainHand(null);
                inv.setItemInOffHand(null);

                // Restore vanished items to their original slots
                inv.setItemInMainHand(heldItems[0]);
                inv.setItemInOffHand(heldItems[1]);

                // Find empty slots for the items player was holding
                if (!tempMain.getType().isAir()) {
                    // Try to add to first available slot
                    HashMap<Integer, ItemStack> leftover = inv.addItem(tempMain);
                    if (!leftover.isEmpty()) {
                        // If no space, drop at player's location
                        player.getWorld().dropItemNaturally(player.getLocation(), tempMain);
                        player.sendMessage("§e✦ Some items were dropped at your feet due to full inventory!");
                    }
                }

                if (!tempOff.getType().isAir()) {
                    HashMap<Integer, ItemStack> leftover = inv.addItem(tempOff);
                    if (!leftover.isEmpty()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), tempOff);
                        player.sendMessage("§e✦ Some items were dropped at your feet due to full inventory!");
                    }
                }
            } else {
                // If no items are being held, simply restore the original items
                inv.setItemInMainHand(heldItems[0]);
                inv.setItemInOffHand(heldItems[1]);
            }
        }

        // Update the player's inventory
        player.updateInventory();
    }

    private void playVanishEffects(Player player, boolean activate) {
        // Visual particle effect
        player.getWorld().spawnParticle(
                Particle.SMOKE,
                player.getLocation().add(0, 1, 0),
                20, 0.3, 0.5, 0.3, 0.02
        );

        // Sound effect with different pitch for activate/deactivate
        float pitch = activate ? 1.2f : 0.8f;
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, pitch);
    }

    private void startDetectionCheck(Player player) {
        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.hasMetadata("vanished")) {
                    cancel();
                    return;
                }

                // Check for nearby players
                for (Entity nearby : player.getNearbyEntities(detectionRange, detectionRange, detectionRange)) {
                    if (nearby instanceof Player && nearby != player) {
                        Player nearbyPlayer = (Player) nearby;

                        if (!plugin.getPartyManager().areInSameParty(player, nearbyPlayer)) {
                            breakVanish(player, "A player detected you!");
                            cancel();
                            return;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);

        detectionTasks.put(player.getUniqueId(), task);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        // Only break vanish if BOTH metadata exists AND the toggle is active in the parent class
        if (player.hasMetadata("vanished") && isToggleActive(player) && !event.isCancelled()) {
            breakVanish(player, "Your vanish was broken by damage!");
        }
    }

    @Override
    protected void onToggleDeactivate(Player player) {
        // Just make sure to clean up the effects when parent class deactivates the toggle
        if (player.hasMetadata("vanished")) {
            removeVanishEffects(player);
        }
    }
}