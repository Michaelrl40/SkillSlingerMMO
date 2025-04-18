package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class BoomstickSkill extends Skill {
    private final int burstCount;
    private final double burstDelay;
    private final double projectileSpeed;
    private final double damage;
    private final double spread;
    private final ItemStack previousItem;

    public BoomstickSkill(ConfigurationSection config) {
        super(config);
        this.burstCount = config.getInt("burstcount", 3);
        this.burstDelay = config.getDouble("burstdelay", 0.2);
        this.projectileSpeed = config.getDouble("projectilespeed", 2.0);
        this.damage = config.getDouble("damage", 40.0);
        this.spread = config.getDouble("spread", 0.1);
        this.previousItem = null;
    }

    @Override
    protected void performSkill(Player player) {
        // Store current item and give player blaze rod (rifle appearance)
        ItemStack previousItem = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(new ItemStack(Material.BLAZE_ROD));

        // Initial rifle equip effects
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
        player.getWorld().spawnParticle(
                Particle.SMOKE,
                player.getLocation().add(0, 1, 0),
                10, 0.2, 0.2, 0.2, 0.05
        );

        // Fire burst of projectiles
        new BukkitRunnable() {
            private int shotsLeft = burstCount;

            @Override
            public void run() {
                if (shotsLeft <= 0) {
                    // Restore previous item
                    player.getInventory().setItemInMainHand(previousItem);
                    this.cancel();
                    return;
                }

                fireLaserShot(player);
                shotsLeft--;
            }
        }.runTaskTimer(plugin, 0L, (long)(burstDelay * 20));

        setSkillSuccess(true);
    }

    private void fireLaserShot(Player player) {
        // Create projectile
        Snowball projectile = player.launchProjectile(Snowball.class);

        // Add spread to the shot
        Vector direction = player.getLocation().getDirection();
        direction.add(new Vector(
                (Math.random() - 0.5) * spread,
                (Math.random() - 0.5) * spread,
                (Math.random() - 0.5) * spread
        )).normalize();

        // Set projectile properties
        projectile.setVelocity(direction.multiply(projectileSpeed));
        projectile.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        projectile.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        projectile.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        projectile.setMetadata("boomstick_projectile", new FixedMetadataValue(plugin, true));

        // Play firing effects
        Location loc = player.getEyeLocation();
        player.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 2.0f);
        player.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.3f, 1.5f);

        // Muzzle flash
        player.getWorld().spawnParticle(
                Particle.DUST,
                loc.add(direction.multiply(1)),
                5, 0.1, 0.1, 0.1, 0.1,
                new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1)
        );

        // Trail effect for the projectile
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead()) {
                    this.cancel();
                    return;
                }

                Location projLoc = projectile.getLocation();
                // Red laser trail
                player.getWorld().spawnParticle(
                        Particle.DUST,
                        projLoc,
                        2, 0.02, 0.02, 0.02, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 50, 50), 0.7f)
                );
                // Steam effect
                player.getWorld().spawnParticle(
                        Particle.SMOKE,
                        projLoc,
                        1, 0.02, 0.02, 0.02, 0.02
                );
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}