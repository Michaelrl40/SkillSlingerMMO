package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShieldListener implements Listener {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Integer> shieldHits;
    private final Map<UUID, Long> shieldCooldowns;
    private final int maxShieldHits = 3;
    private final long shieldCooldownDuration = 20000; // 20 seconds in milliseconds

    public ShieldListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.shieldHits = new HashMap<>();
        this.shieldCooldowns = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player defender = (Player) event.getEntity();

        // Verify they're blocking with a shield
        if (!isBlockingWithShield(defender)) return;

        // Check if shield is on cooldown
        if (isShieldOnCooldown(defender)) {
            // Cancel the shield block
            event.setCancelled(false);
            defender.setCooldown(Material.SHIELD, (int)(getRemainingCooldown(defender) * 20)); // Set Minecraft's native cooldown
            // Notify player shield is on cooldown
            defender.sendMessage("§c✦ Your shield is broken and cannot block! (" +
                    getRemainingCooldown(defender) + "s remaining)");
            return;
        }

        // Process the shield hit
        processShieldHit(defender);

        // Create shield block effect
        playShieldBlockEffect(defender);

        // Reduce damage while shield is active
        event.setDamage(event.getDamage() * 0.5); // Reduce damage by 50%
    }

    private boolean isBlockingWithShield(Player player) {
        return player.isBlocking() &&
                (player.getInventory().getItemInMainHand().getType() == Material.SHIELD ||
                        player.getInventory().getItemInOffHand().getType() == Material.SHIELD);
    }

    private void processShieldHit(Player defender) {
        UUID defenderId = defender.getUniqueId();
        int currentHits = shieldHits.getOrDefault(defenderId, 0) + 1;
        shieldHits.put(defenderId, currentHits);

        // Notify player of shield status
        if (currentHits < maxShieldHits) {
            defender.sendMessage("§e✦ Shield durability: " + (maxShieldHits - currentHits) + " hits remaining");
        }

        // Check if shield breaks
        if (currentHits >= maxShieldHits) {
            // Force player to stop blocking by simulating sneak and unsneak
            defender.setSneaking(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    defender.setSneaking(false);
                }
            }.runTaskLater(plugin, 1L);

            breakShield(defender);
        }
    }

    private void breakShield(Player defender) {
        UUID defenderId = defender.getUniqueId();

        // Put shield on cooldown
        shieldCooldowns.put(defenderId, System.currentTimeMillis() + shieldCooldownDuration);

        // Apply Minecraft's native cooldown to prevent shield use
        defender.setCooldown(Material.SHIELD, 20 * 20); // 20 seconds in ticks

        // Play native shield break effect
        defender.playEffect(EntityEffect.SHIELD_BREAK);

        // Reset hit counter
        shieldHits.remove(defenderId);

        // Notify player
        defender.sendMessage("§c✦ Your shield breaks! It will recover in 20 seconds.");

        // Schedule shield recovery
        new BukkitRunnable() {
            @Override
            public void run() {
                if (shieldCooldowns.containsKey(defenderId) &&
                        System.currentTimeMillis() >= shieldCooldowns.get(defenderId)) {
                    shieldCooldowns.remove(defenderId);
                    if (defender.isOnline()) {
                        defender.sendMessage("§a✦ Your shield has recovered!");
                        playShieldRecoverEffect(defender);
                    }
                }
            }
        }.runTaskLater(plugin, 20 * 20); // 20 seconds
    }

    private boolean isShieldOnCooldown(Player player) {
        Long cooldownEnd = shieldCooldowns.get(player.getUniqueId());
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    private long getRemainingCooldown(Player player) {
        Long cooldownEnd = shieldCooldowns.get(player.getUniqueId());
        if (cooldownEnd == null) return 0;
        return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000);
    }

    private void playShieldBlockEffect(Player defender) {
        defender.getWorld().playSound(
                defender.getLocation(),
                Sound.ITEM_SHIELD_BLOCK,
                1.0f,
                1.0f
        );
    }

    private void playShieldBreakEffect(Player defender) {
        defender.getWorld().playSound(
                defender.getLocation(),
                Sound.ITEM_SHIELD_BREAK,
                1.0f,
                0.8f
        );

        defender.getWorld().playSound(
                defender.getLocation(),
                Sound.BLOCK_ANVIL_LAND,
                0.5f,
                1.2f
        );
    }

    private void playShieldRecoverEffect(Player defender) {
        defender.getWorld().playSound(
                defender.getLocation(),
                Sound.BLOCK_ANVIL_USE,
                0.5f,
                1.5f
        );
    }

    public void cleanup() {
        shieldHits.clear();
        shieldCooldowns.clear();
    }
}