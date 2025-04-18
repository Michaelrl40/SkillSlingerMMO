package com.michael.mmorpg.skills.elementalranger;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class IceArrowSkill extends Skill {
    private final double damage;
    private final int stunDuration;
    private final double projectileSpeed;
    private final double projectileSize;
    private final double maxRange;

    public IceArrowSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.stunDuration = config.getInt("stunduration", 3) * 20; // Convert to ticks
        this.projectileSpeed = config.getDouble("projectilespeed", 1.0);
        this.projectileSize = config.getDouble("projectilesize", 2.0);
        this.maxRange = config.getDouble("maxrange", 30.0);
    }

    @Override
    protected void performSkill(Player player) {

        // Cancel if player doesn't have a bow
        if (!(player.getInventory().getItemInMainHand().getType() == Material.BOW ||
                player.getInventory().getItemInMainHand().getType() == Material.CROSSBOW)) {
            player.sendMessage("§c✦ You must be holding a bow or crossbow!");
            setSkillSuccess(false);
            return;
        }


        // Create charging effect during cast time
        new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                if (!player.hasMetadata("casting")) {
                    this.cancel();
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);
                angle += Math.PI / 8;

                // Spinning ice particles
                for (int i = 0; i < 2; i++) {
                    double x = Math.cos(angle + (i * Math.PI)) * 1;
                    double z = Math.sin(angle + (i * Math.PI)) * 1;
                    loc.add(x, 0, z);

                    player.getWorld().spawnParticle(
                            Particle.SNOWFLAKE,
                            loc,
                            1, 0, 0, 0, 0
                    );

                    player.getWorld().spawnParticle(
                            Particle.SNOWFLAKE,
                            loc,
                            3, 0.1, 0.1, 0.1, 0
                    );

                    loc.subtract(x, 0, z);
                }

                // Play charging sound every 5 ticks
                if (angle % (Math.PI/2) < 0.1) {
                    player.getWorld().playSound(
                            loc,
                            Sound.BLOCK_GLASS_BREAK,
                            0.5f,
                            2.0f
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Launch ice arrow after cast time
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection();

        // Initial launch effect
        player.getWorld().playSound(origin, Sound.ENTITY_WITHER_SHOOT, 1.0f, 2.0f);

        // Create the projectile
        new BukkitRunnable() {
            Location currentLoc = origin.clone();
            double distance = 0;
            boolean hit = false;

            @Override
            public void run() {
                if (hit || distance > maxRange) {
                    this.cancel();
                    return;
                }

                // Move projectile
                currentLoc.add(direction.clone().multiply(projectileSpeed));
                distance += projectileSpeed;

                // Create ice arrow particles
                for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
                    double x = Math.cos(i) * projectileSize;
                    double y = Math.sin(i) * projectileSize;
                    Location particleLoc = currentLoc.clone().add(
                            direction.getX() == 0 ? x : 0,
                            y,
                            direction.getZ() == 0 ? x : 0
                    );

                    // Core ice particles
                    player.getWorld().spawnParticle(
                            Particle.SNOWFLAKE,
                            particleLoc,
                            1, 0, 0, 0, 0
                    );

                    // Ice trail
                    player.getWorld().spawnParticle(
                            Particle.SNOWFLAKE,
                            particleLoc,
                            2, 0.1, 0.1, 0.1, 0
                    );

                    // Blue dust for glow effect
                    player.getWorld().spawnParticle(
                            Particle.DUST,
                            particleLoc,
                            0, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.0f)
                    );
                }

                // Check for collision
                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, projectileSize, projectileSize, projectileSize)) {
                    // In the collision check of IceArrow skill:
                    if (entity instanceof LivingEntity && entity != player) {
                        LivingEntity target = (LivingEntity) entity;

                        // Deal damage
                        target.damage(damage, player);

                        // Apply stun if target is a player
                        if (target instanceof Player) {
                            // Convert ticks to milliseconds (1 tick = 50ms)
                            long millisDuration = stunDuration * 50L;
                            StatusEffect stunEffect = new StatusEffect(CCType.STUN, millisDuration, player, 1);
                            plugin.getStatusEffectManager().applyEffect((Player)target, stunEffect);
                        }

                        // Hit effects
                        target.getWorld().spawnParticle(
                                Particle.ITEM_SNOWBALL,
                                target.getLocation().add(0, 1, 0),
                                30, 0.5, 0.5, 0.5, 0.1
                        );
                        target.getWorld().playSound(
                                target.getLocation(),
                                Sound.BLOCK_GLASS_BREAK,
                                1.0f,
                                0.5f
                        );

                        // Create ice explosion effect
                        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                            double x = Math.cos(angle) * 2;
                            double z = Math.sin(angle) * 2;
                            Location explosionLoc = target.getLocation().add(x, 1, z);

                            target.getWorld().spawnParticle(
                                    Particle.SNOWFLAKE,
                                    explosionLoc,
                                    3, 0.1, 0.1, 0.1, 0.1
                            );
                            target.getWorld().spawnParticle(
                                    Particle.CLOUD,
                                    explosionLoc,
                                    2, 0.1, 0.1, 0.1, 0
                            );
                        }

                        hit = true;
                        break;
                    }
                }

                // Trail sound
                if (distance % 2 < projectileSpeed) {
                    currentLoc.getWorld().playSound(
                            currentLoc,
                            Sound.BLOCK_GLASS_STEP,
                            0.5f,
                            2.0f
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}