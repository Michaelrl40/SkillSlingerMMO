package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.MetadataValue;

import java.util.UUID;

public class TurretListener implements Listener {
    private final MinecraftMMORPG plugin;

    public TurretListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTurretDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();

        // Check if the entity is a turret
        if (!(entity instanceof ArmorStand) || !entity.hasMetadata("turret_owner")) {
            return;
        }

        ArmorStand turret = (ArmorStand) entity;

        // Get turret owner
        UUID ownerId = (UUID) turret.getMetadata("turret_owner").get(0).value();
        Player owner = plugin.getServer().getPlayer(ownerId);

        // Check for party member damage from any source
        Player damager = null;

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent edbe = (EntityDamageByEntityEvent) event;
            damager = getDamageSource(edbe.getDamager());
        }

        // If there's a player source and they're in the same party as the owner, cancel damage
        if (damager != null && owner != null) {
            if (plugin.getPartyManager().getParty(owner) != null &&
                    plugin.getPartyManager().getParty(owner).isMember(damager)) {
                event.setCancelled(true);
                damager.sendMessage("§c✦ You cannot damage party member's turrets!");
                return;
            }
        }

        // Cancel vanilla damage to handle it ourselves
        event.setCancelled(true);

        // Get current health and apply damage
        double currentHealth = turret.getHealth();
        double newHealth = currentHealth - event.getFinalDamage();

        // Handle damage
        if (newHealth <= 0) {
            // Turret destruction effects
            turret.getWorld().createExplosion(turret.getLocation(), 0, false, false);

            // Remove turret
            if (owner != null) {
                owner.sendMessage("§c✦ Your turret has been destroyed!");
            }
            turret.remove();
        } else {
            // Update health
            turret.setHealth(newHealth);

            // Damage effect
            turret.getWorld().spawnParticle(org.bukkit.Particle.SMOKE,
                    turret.getLocation().add(0, 1, 0),
                    10, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private Player getDamageSource(Entity damager) {
        // Direct player damage
        if (damager instanceof Player) {
            return (Player) damager;
        }

        // Projectile damage
        if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }

        // Skill damage
        if (damager.hasMetadata("skill_damage")) {
            for (MetadataValue meta : damager.getMetadata("skill_damage")) {
                if (meta.value() instanceof Player) {
                    return (Player) meta.value();
                }
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTurretDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();

        // Check if the entity is a turret
        if (!(entity instanceof ArmorStand) || !entity.hasMetadata("turret_owner")) {
            return;
        }

        // Check if the damager is a player
        Player damager = null;
        if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        }

        if (damager != null) {
            // Get turret owner
            UUID ownerId = (UUID) entity.getMetadata("turret_owner").get(0).value();

            // Check if the damager is in the same party as the owner
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner != null && plugin.getPartyManager().getParty(owner) != null &&
                    plugin.getPartyManager().getParty(owner).isMember(damager)) {
                event.setCancelled(true);
                damager.sendMessage("§c✦ You cannot damage party member's turrets!");
                return;
            }
        }
    }
}