package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class ThrowingAxeSkill extends Skill {
    private final double damage;
    private final double projectileSpeed;
    private final double maxDistance;
    private final double returnSpeed;

    public ThrowingAxeSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.projectileSpeed = config.getDouble("projectilespeed", 0.4);
        this.maxDistance = config.getDouble("maxdistance", 12.0);
        this.returnSpeed = config.getDouble("returnspeed", 0.6);
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        if (!playerData.useRage(rageCost)) {
            player.sendMessage("§c✦ Not enough rage!");
            setSkillSuccess(false);
            return;
        }

        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection();
        Location spawnLoc = startLoc.clone().add(direction.clone().multiply(1.0));

        ArmorStand axe = player.getWorld().spawn(spawnLoc, ArmorStand.class);
        axe.setVisible(false);
        axe.setGravity(false);
        axe.setSmall(true);
        axe.setMarker(true);
        axe.getEquipment().setHelmet(new ItemStack(Material.IRON_AXE));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.8f);

        Set<Entity> hitEntities = new HashSet<>();
        Location initialLoc = startLoc.clone();

        new BukkitRunnable() {
            private boolean returning = false;
            private Vector velocity = direction.multiply(projectileSpeed);
            private double spinAngle = 0;
            private final double SPIN_SPEED = 0.5; // Adjust this to control spin speed

            @Override
            public void run() {
                if (!player.isOnline()) {
                    axe.remove();
                    cancel();
                    return;
                }

                Location axeLoc = axe.getLocation();

                // Calculate distance
                double distanceTraveled = !returning ? initialLoc.distance(axeLoc) : 0;

                // Update spin - create a continuous rotation
                spinAngle += SPIN_SPEED;
                double yaw = axeLoc.getYaw();

                // Create smooth spinning motion using sine and cosine
                double x = Math.sin(spinAngle) * 2;
                double z = Math.cos(spinAngle) * 2;
                EulerAngle rotation = new EulerAngle(spinAngle, 0, 0);
                axe.setHeadPose(rotation);

                // Check for return
                if (!returning && distanceTraveled >= maxDistance) {
                    returning = true;
                    player.getWorld().playSound(axeLoc, Sound.ENTITY_IRON_GOLEM_HURT, 0.5f, 2.0f);
                }

                // Handle return
                if (returning) {
                    Vector toPlayer = player.getLocation().add(0, 1, 0).subtract(axeLoc).toVector();
                    double distToPlayer = toPlayer.length();

                    if (distToPlayer < 1.0) {
                        axe.remove();
                        player.getWorld().playSound(player.getLocation(),
                                Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.0f);
                        cancel();
                        return;
                    }

                    velocity = toPlayer.normalize().multiply(returnSpeed);
                }

                // Move axe
                axeLoc.add(velocity);
                axe.teleport(axeLoc);

                // Particles aligned with spin
                double particleRadius = 0.3;
                for (int i = 0; i < 2; i++) {
                    double angle = spinAngle + (Math.PI * i);
                    double pX = Math.cos(angle) * particleRadius;
                    double pZ = Math.sin(angle) * particleRadius;
                    Location particleLoc = axeLoc.clone().add(pX, 0.5, pZ);

                    axeLoc.getWorld().spawnParticle(
                            returning ? Particle.CRIT : Particle.CRIT,
                            particleLoc,
                            1, 0.02, 0.02, 0.02, 0.001
                    );
                }

                // Check for hits (both outward and return journey)
                for (Entity entity : axeLoc.getWorld().getNearbyEntities(axeLoc, 1, 1, 1)) {
                    if (entity instanceof LivingEntity &&
                            entity != player &&
                            entity != axe &&
                            !hitEntities.contains(entity)) {

                        LivingEntity target = (LivingEntity) entity;
                        if (validateTarget(player, target)) continue;

                        hitEntities.add(entity);

                        // Apply damage (can be different for return journey if desired)
                        double currentDamage = damage; // Could modify damage for return journey here
                        target.setMetadata("skill_damage",
                                new org.bukkit.metadata.FixedMetadataValue(plugin, player));
                        target.setMetadata("skill_damage_amount",
                                new org.bukkit.metadata.FixedMetadataValue(plugin, currentDamage));
                        target.damage(currentDamage, player);

                        // Visual and sound effects
                        Location hitLoc = target.getLocation().add(0, 1, 0);
                        // Different particle effect for return hits
                        target.getWorld().spawnParticle(
                                returning ? Particle.SWEEP_ATTACK : Particle.SWEEP_ATTACK,
                                hitLoc,
                                8, 0.3, 0.3, 0.3, 0.1
                        );
                        target.getWorld().playSound(
                                hitLoc,
                                Sound.ENTITY_PLAYER_ATTACK_CRIT,
                                0.7f,
                                returning ? 1.4f : 1.2f // Slightly different pitch for return hits
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}