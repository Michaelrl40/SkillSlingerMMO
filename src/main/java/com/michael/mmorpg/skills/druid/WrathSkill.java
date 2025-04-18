package com.michael.mmorpg.skills.druid;

import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class WrathSkill extends DruidShapeshiftSkill {
    private final double damage;
    private final double range;
    private final double projectileSpeed;
    private final double trackingStrength;
    private final int maxDuration;
    private LivingEntity target;

    public WrathSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.range = config.getDouble("range", 20.0);
        this.projectileSpeed = config.getDouble("projectilespeed", 0.5);
        this.trackingStrength = config.getDouble("trackingstrength", 0.2);
        this.maxDuration = config.getInt("maxduration", 200);
    }

    @Override
    public void execute(Player player) {
        // Check if we're transformed
        if (player.hasMetadata("druid_form")) {
            player.sendMessage("§c✦ Wrath can only be used in base form!");
            return;
        }

        // Use base class targeting with our range
        target = getTargetEntity(player, range);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            return;
        }

        // Store target for validation
        currentTarget = target;

        // Start casting
        if (hasCastTime) {
            startCasting(player);
        } else {
            performSkill(player);
        }
    }

    @Override
    protected void performSkill(Player player) {
        if (target == null || !target.isValid() || target.isDead()) {
            setSkillSuccess(false);
            return;
        }

        // Launch the tracking projectile
        launchProjectile(player, target);

        // Play cast sound and effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
                10, 0.3, 0.3, 0.3, 0.1);

        setSkillSuccess(true);
    }

    private void launchProjectile(Player caster, LivingEntity target) {
        Location projectileLocation = caster.getEyeLocation();
        Vector direction = target.getLocation().add(0, target.getHeight() / 2, 0)
                .subtract(projectileLocation).toVector().normalize();

        new BukkitRunnable() {
            private int ticksLived = 0;
            private Vector currentVelocity = direction.multiply(projectileSpeed);
            private final double COLLISION_DISTANCE = 1.0;

            @Override
            public void run() {
                if (ticksLived++ >= maxDuration || !target.isValid() || target.isDead()) {
                    createExplosionEffect(projectileLocation);
                    cancel();
                    return;
                }

                projectileLocation.add(currentVelocity);

                Vector toTarget = target.getLocation().add(0, target.getHeight() / 2, 0)
                        .subtract(projectileLocation).toVector().normalize();

                currentVelocity = currentVelocity.multiply(1 - trackingStrength)
                        .add(toTarget.multiply(trackingStrength))
                        .normalize().multiply(projectileSpeed);

                if (projectileLocation.distance(target.getLocation()) < COLLISION_DISTANCE) {
                    hitTarget(caster, target, projectileLocation);
                    cancel();
                    return;
                }

                createProjectileEffects(projectileLocation);

                if (!projectileLocation.getBlock().isPassable()) {
                    createExplosionEffect(projectileLocation);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createProjectileEffects(Location location) {
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 2, 0.1, 0.1, 0.1, 0);
        location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 1, 0.1, 0.1, 0.1, 0);
        location.getWorld().spawnParticle(Particle.COMPOSTER, location, 1, 0, 0, 0, 0);
    }

    private void createExplosionEffect(Location location) {
        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, location, 30, 0.3, 0.3, 0.3, 0.2);
            world.spawnParticle(Particle.HAPPY_VILLAGER, location, 15, 0.3, 0.3, 0.3, 0.1);
            world.playSound(location, Sound.BLOCK_GRASS_BREAK, 1.0f, 0.5f);
            world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        }
    }

    private void hitTarget(Player caster, LivingEntity target, Location hitLocation) {
        // Set skill damage metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

        // Apply the damage
        target.damage(0.1, caster);

        // Clean up metadata
        getPlugin().getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
            }
        }, 1L);

        // Create visual effects
        createExplosionEffect(hitLocation);
        broadcastLocalSkillMessage(caster, "§2[Druid] " + caster.getName() + "'s Wrath strikes "
                + target.getName() + "!");
    }

    // Required abstract method implementations - not used for this skill
    @Override protected Disguise createDisguise() { return null; }
    @Override protected void setupDisguise(Disguise disguise) {}
    @Override protected void applyFormEffects(Player player) {}
    @Override protected void removeFormEffects(Player player) {}
}