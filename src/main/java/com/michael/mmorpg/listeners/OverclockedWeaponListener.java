package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.engineer.OverclockedWeaponSkill;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class OverclockedWeaponListener implements Listener {
    private final MinecraftMMORPG plugin;

    public OverclockedWeaponListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Check if the damage was caused directly by a player (melee attack)
        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Player damager = (Player) event.getDamager();
            LivingEntity target = (LivingEntity) event.getEntity();

            // Only trigger if damage was a melee attack (direct player damage, not from a skill)
            if (damager.hasMetadata("overclocked_weapon") && isMeleeAttack(event)) {
                OverclockedWeaponSkill.triggerLightningStrike(damager, target);
            }
        }
    }

    /**
     * Determines if the damage event was from a melee attack
     * @param event The damage event
     * @return true if the damage was from a melee weapon attack, false otherwise
     */
    private boolean isMeleeAttack(EntityDamageByEntityEvent event) {
        // First check: Must be direct player damage, not projectile or other entity
        if (!(event.getDamager() instanceof Player)) {
            return false;
        }

        Player damager = (Player) event.getDamager();

        // Second check: Must not be from a skill (no skill damage metadata)
        if (event.getEntity().hasMetadata("skill_damage")) {
            return false;
        }

        // Third check: Must be using a valid melee weapon
        ItemStack itemInHand = damager.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            return false; // No item in hand
        }

        // Check the damage cause - must be ENTITY_ATTACK for melee
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return false;
        }

        // Check if the weapon type is melee (add more as needed)
        String itemType = itemInHand.getType().name();
        return itemType.endsWith("_SWORD") ||
                itemType.endsWith("_AXE") ||
                itemType.contains("HOE") ||
                itemType.contains("SHOVEL") ||
                itemType.contains("STICK") ||
                itemType.contains("TRIDENT");
    }
}