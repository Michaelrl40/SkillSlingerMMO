package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.Particle;

import java.util.List;
import java.util.UUID;

public class ChainLinkDamageListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ChainLinkDamageListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        // Skip if the damage is from the chain link sharing itself
        if (event.getEntity().hasMetadata("chain_link_damage")) {
            return;
        }

        // Check if the damaged entity is a linked target (Player)
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player damagedPlayer = (Player) event.getEntity();

        // Check if player is a chain link target
        if (!damagedPlayer.hasMetadata("chain_link_target")) {
            return;
        }

        // Get the linked caster
        List<MetadataValue> metadata = damagedPlayer.getMetadata("chain_link_target");
        if (metadata.isEmpty()) {
            return;
        }

        UUID casterUUID = (UUID) metadata.get(0).value();
        Player caster = plugin.getServer().getPlayer(casterUUID);

        // Verify caster is still valid and linked
        if (caster == null || !caster.isOnline() || !caster.hasMetadata("chain_link_caster")) {
            return;
        }

        // Calculate shared damage
        double originalDamage = event.getFinalDamage();
        double sharedDamage = originalDamage * 0.5;

        // Reduce target's damage
        event.setDamage(originalDamage - sharedDamage);

        // Apply shared damage to caster with metadata to prevent recursion
        caster.setMetadata("chain_link_damage", new FixedMetadataValue(plugin, true));
        caster.damage(sharedDamage);
        caster.removeMetadata("chain_link_damage", plugin);

        // Show damage sharing effect
        showDamageShareEffect(damagedPlayer, caster);

        // Handle combat state
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent edbe = (EntityDamageByEntityEvent) event;
            LivingEntity attacker = null;

            // Determine the actual attacker
            if (edbe.getDamager() instanceof LivingEntity) {
                attacker = (LivingEntity) edbe.getDamager();
            } else if (edbe.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) edbe.getDamager();
                if (projectile.getShooter() instanceof LivingEntity) {
                    attacker = (LivingEntity) projectile.getShooter();
                }
            }

            // Enter combat for both players with the attacker
            if (attacker != null) {
                // Put damaged player in combat
                plugin.getCombatManager().enterCombat(damagedPlayer, attacker);

                // Put linked caster in combat with the same attacker
                plugin.getCombatManager().enterCombat(caster, attacker);
            }
        }

        // Debug messages
        damagedPlayer.sendMessage("§6✦ " + caster.getName() + " absorbed " +
                String.format("%.1f", sharedDamage) + " damage for you!");
        caster.sendMessage("§6✦ You absorbed " + String.format("%.1f", sharedDamage) +
                " damage for " + damagedPlayer.getName() + "!");
    }

    private void showDamageShareEffect(Player target, Player caster) {
        // Create particle effect between players to show damage transfer
        target.getWorld().spawnParticle(
                Particle.WITCH,
                target.getLocation().add(0, 1, 0),
                10, 0.3, 0.5, 0.3, 0
        );

        caster.getWorld().spawnParticle(
                Particle.WITCH,
                caster.getLocation().add(0, 1, 0),
                10, 0.3, 0.5, 0.3, 0
        );

        // Play effect sound
        target.getWorld().playSound(target.getLocation(),
                org.bukkit.Sound.BLOCK_CHAIN_HIT, 0.5f, 1.2f);
    }
}