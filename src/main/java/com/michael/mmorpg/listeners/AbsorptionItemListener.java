package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.AbsorptionShieldManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listens for golden apple and totem events to replace absorption with custom shields
 */
public class AbsorptionItemListener implements Listener {
    private final MinecraftMMORPG plugin;
    private final AbsorptionShieldManager shieldManager;

    public AbsorptionItemListener(MinecraftMMORPG plugin, AbsorptionShieldManager shieldManager) {
        this.plugin = plugin;
        this.shieldManager = shieldManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // Check for golden apple types
        if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            // Determine if it's an enchanted golden apple by material type
            boolean isEnchanted = item.getType() == Material.ENCHANTED_GOLDEN_APPLE;

            AbsorptionShieldManager.ConsumableType type = isEnchanted ?
                    AbsorptionShieldManager.ConsumableType.ENCHANTED_APPLE :
                    AbsorptionShieldManager.ConsumableType.GOLDEN_APPLE;

            // Check cooldown and apply shield
            if (!shieldManager.applyAbsorptionShield(player, type)) {
                // Cancel consumption if on cooldown
                event.setCancelled(true);
                return;
            }

            // Schedule task to remove absorption effect after consumption
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Remove absorption effect
                    player.removePotionEffect(PotionEffectType.ABSORPTION);

                    // Re-apply other intended effects if needed
                    // For golden apple: hunger, regeneration
                    if (!isEnchanted) {
                        for (PotionEffect effect : player.getActivePotionEffects()) {
                            // Keep vanilla effects except absorption
                            if (effect.getType() == PotionEffectType.REGENERATION ||
                                    effect.getType() == PotionEffectType.FIRE_RESISTANCE) {
                                // Keep these effects
                            }
                        }
                    } else {
                        // For enchanted golden apple: regen, resistance, fire resistance, damage resistance
                        for (PotionEffect effect : player.getActivePotionEffects()) {
                            // Keep vanilla effects except absorption
                            if (effect.getType() == PotionEffectType.REGENERATION ||
                                    effect.getType() == PotionEffectType.FIRE_RESISTANCE ||
                                    effect.getType() == PotionEffectType.RESISTANCE){
                                // Keep these effects
                            }
                        }
                    }
                }
            }.runTaskLater(plugin, 1L); // Run on the next tick after consumption
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Check if the event is cancelled - if it is, the totem won't work
        if (event.isCancelled()) {
            return;
        }

        // Check cooldown for totem
        if (!shieldManager.applyAbsorptionShield(player, AbsorptionShieldManager.ConsumableType.TOTEM)) {
            // If on cooldown, cancel the resurrection
            event.setCancelled(true);
            return;
        }

        // Schedule task to remove absorption effect after totem use
        new BukkitRunnable() {
            @Override
            public void run() {
                // Remove absorption effect but keep other totem effects
                player.removePotionEffect(PotionEffectType.ABSORPTION);
            }
        }.runTaskLater(plugin, 1L);
    }
}