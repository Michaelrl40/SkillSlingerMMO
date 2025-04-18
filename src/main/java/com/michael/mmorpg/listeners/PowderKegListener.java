package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class PowderKegListener implements Listener {
    private final MinecraftMMORPG plugin;

    public PowderKegListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBarrelHit(EntityDamageByEntityEvent event) {
        // Check if the damaged entity is our powder keg
        if (!(event.getEntity() instanceof ArmorStand)) return;
        ArmorStand barrel = (ArmorStand) event.getEntity();
        if (!barrel.hasMetadata("powder_keg")) return;

        // Validate the attacker
        if (!(event.getDamager() instanceof Player) && !(event.getDamager() instanceof LivingEntity)) return;

        // Cancel vanilla damage
        event.setCancelled(true);

        // Validate barrel metadata
        if (!barrel.hasMetadata("keg_damage") || !barrel.hasMetadata("keg_radius")) {
            plugin.getLogger().warning("Powder Keg missing required metadata!");
            return;
        }

        // Get barrel properties
        double damage = barrel.getMetadata("keg_damage").get(0).asDouble();
        double radius = barrel.getMetadata("keg_radius").get(0).asDouble();

        // Play hit feedback
        barrel.getWorld().playSound(barrel.getLocation(), Sound.BLOCK_WOOD_HIT, 1.0f, 0.8f);
        barrel.getWorld().spawnParticle(
                Particle.BLOCK_CRUMBLE,
                barrel.getLocation().add(0, 0.5, 0),
                10, 0.3, 0.3, 0.3, 0,
                Material.BARREL.createBlockData()
        );

        // Trigger explosion with small delay for feedback
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (barrel.isValid() && !barrel.isDead()) {
                explodeBarrel(barrel, damage, radius);
            }
        }, 1L);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Clean up any powder kegs in unloaded chunks
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof ArmorStand && entity.hasMetadata("powder_keg")) {
                entity.remove();
            }
        }
    }

    public void explodeBarrel(ArmorStand barrel, double damage, double radius) {
        if (barrel.isDead()) return;

        Location loc = barrel.getLocation().add(0.5, 0.5, 0.5); // Center the explosion
        World world = loc.getWorld();

        // Get the barrel owner and their party
        Player owner = null;
        Party ownerParty = null;

        if (barrel.hasMetadata("keg_owner")) {
            owner = (Player) barrel.getMetadata("keg_owner").get(0).value();
        }
        if (barrel.hasMetadata("owner_party")) {
            ownerParty = (Party) barrel.getMetadata("owner_party").get(0).value();
        }

        // Enhanced explosion effects
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.FLAME, loc, 30, 1, 1, 1, 0.2);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 20, 1, 1, 1, 0.15);

        // Damage nearby entities
        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity) || entity.equals(barrel)) continue;

            // Skip owner and party members
            if (entity instanceof Player) {
                Player targetPlayer = (Player) entity;
                if (owner != null && targetPlayer.equals(owner)) continue;
                if (ownerParty != null && ownerParty.isMember(targetPlayer)) continue;
            }

            LivingEntity target = (LivingEntity) entity;

            // Calculate damage with distance falloff
            double distance = target.getLocation().distance(loc);
            double damageMultiplier = 1 - (distance / radius);
            if (damageMultiplier <= 0) continue;

            double finalDamage = damage * damageMultiplier;

            // Enhanced knockback
            Vector knockback = target.getLocation().subtract(loc).toVector().normalize();
            knockback.multiply(2.0 * damageMultiplier);
            knockback.setY(Math.min(1.2 * damageMultiplier, 0.8));
            target.setVelocity(knockback);

            // Apply damage
            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, owner));
            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, finalDamage));

            if (owner != null) {
                target.damage(0.1, owner);
            } else {
                target.damage(finalDamage);
            }
        }

        // Remove the barrel
        barrel.remove();
    }
}