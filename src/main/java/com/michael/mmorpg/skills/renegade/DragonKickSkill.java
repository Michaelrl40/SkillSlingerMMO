package com.michael.mmorpg.skills.renegade;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

@SuppressWarnings("unused")
public class DragonKickSkill extends Skill {
    private final double damage;
    private final double knockbackForce;
    private final double knockupForce;

    public DragonKickSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.knockbackForce = config.getDouble("knockbackforce", 3.0);
        this.knockupForce = config.getDouble("knockupforce", 0.8);
        this.hasCastTime = true;
        this.castTime = 2.0;
        this.slowIntensity = 2;
    }

    @Override
    public void execute(Player player) {
        // Get target using melee targeting
        currentTarget = getMeleeTarget(player, targetRange);

        if (currentTarget == null) {
            player.sendMessage("§c✦ No valid target in melee range!");
            setSkillSuccess(false);
            return;
        }

        // Validate target
        if (validateTarget(player, currentTarget)) {
            setSkillSuccess(false);
            return;
        }

        // Start casting with warmup
        startCastingEffects(player);
        startCasting(player);
    }

    private void startCastingEffects(Player player) {
        new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!player.hasMetadata("casting")) {
                    cancel();
                    return;
                }

                // Warmup particles
                World world = player.getWorld();
                Location loc = player.getLocation();

                // Rising flame effect
                double y = tick * 0.1;
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 4) {
                    double x = Math.cos(angle) * 0.8;
                    double z = Math.sin(angle) * 0.8;
                    Location particleLoc = loc.clone().add(x, y, z);

                    world.spawnParticle(Particle.FLAME, particleLoc,
                            1, 0.02, 0.02, 0.02, 0);
                }

                // Charging sound
                if (tick % 2 == 0) {
                    world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.5f + (tick * 0.05f), 0.8f + (tick * 0.1f));
                }

                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    protected void performSkill(Player player) {
        if (currentTarget == null || !currentTarget.isValid()) {
            player.sendMessage("§c✦ Target is no longer valid!");
            return;
        }

        // Calculate kick direction
        Vector direction = currentTarget.getLocation().subtract(player.getLocation()).toVector().normalize();

        // Apply damage
        currentTarget.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        currentTarget.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        currentTarget.damage(damage, player);

        // If the target is a player, apply knockup CC
        if (currentTarget instanceof Player) {
            Player targetPlayer = (Player) currentTarget;
            StatusEffect knockupEffect = new StatusEffect(CCType.KNOCKUP, 1500, player, 1);
            plugin.getStatusEffectManager().applyEffect(targetPlayer, knockupEffect);
        }

        // Initial upward launch
        currentTarget.setVelocity(new Vector(0, knockupForce * 2, 0));

        // Schedule horizontal launch
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentTarget.isValid()) {
                    Vector launchVector = direction.clone().multiply(knockbackForce * 3);
                    launchVector.setY(knockupForce * 0.5);
                    currentTarget.setVelocity(launchVector);
                }
            }
        }.runTaskLater(plugin, 4L);

        // Visual effects
        World world = player.getWorld();
        Location targetLoc = currentTarget.getLocation();

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 20 || !currentTarget.isValid()) {
                    this.cancel();
                    return;
                }
                Location currentLoc = currentTarget.getLocation();
                // Trail effect
                world.spawnParticle(Particle.EXPLOSION, currentLoc,
                        5, 0.2, 0.2, 0.2, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Impact effects
        world.spawnParticle(Particle.EXPLOSION_EMITTER, targetLoc.add(0, 1, 0),
                3, 0.3, 0.3, 0.3, 0);
        world.playSound(targetLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, .2f, 0.8f);
        setSkillSuccess(true);
    }
}