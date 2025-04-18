package com.michael.mmorpg.skills.bandolier;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;

public class PushOffSkill extends Skill {
    private final double damage;
    private final double meleeRange;
    private final double pushForce;
    private final double leapForce;
    private final double height;

    public PushOffSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 5.0);
        this.meleeRange = config.getDouble("meleerange", 3.0);
        this.pushForce = config.getDouble("pushForce", 1.2);
        this.leapForce = config.getDouble("leapForce", 1.0);
        this.height = config.getDouble("height", 0.3);
    }

    @Override
    protected void performSkill(Player player) {
        // Get melee target
        LivingEntity target = getMeleeTarget(player, meleeRange);

        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Validate target (checks for party members, etc)
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Store target for messages
        currentTarget = target;

        // Calculate push direction for target (away from player)
        Vector pushDir = target.getLocation().toVector()
                .subtract(player.getLocation().toVector())
                .normalize()
                .multiply(pushForce)
                .setY(height);

        // Calculate leap direction for player (opposite of push)
        Vector leapDir = pushDir.clone()
                .multiply(-1)
                .normalize()
                .multiply(leapForce)
                .setY(height);

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(0.1, player);

        // Push target and player
        target.setVelocity(pushDir);
        player.setVelocity(leapDir);

        // Reset fall distance to prevent damage
        target.setFallDistance(0f);
        player.setFallDistance(0f);

        // Play effects at impact point
        Location impactLoc = target.getLocation();

        // Impact particles
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                impactLoc,
                20, 0.3, 0.3, 0.3, 0.2
        );

        // Directional particles showing push/leap
        new BukkitRunnable() {
            int ticks = 0;
            final Location startLoc = impactLoc.clone();
            final Vector pushParticleDir = pushDir.clone().normalize().multiply(0.5);
            final Vector leapParticleDir = leapDir.clone().normalize().multiply(0.5);

            @Override
            public void run() {
                if (ticks >= 10) {
                    this.cancel();
                    return;
                }

                // Push direction particles
                Location pushParticleLoc = startLoc.clone().add(pushParticleDir.clone().multiply(ticks));
                player.getWorld().spawnParticle(
                        Particle.CLOUD,
                        pushParticleLoc,
                        3, 0.1, 0.1, 0.1, 0.02
                );

                // Leap direction particles
                Location leapParticleLoc = startLoc.clone().add(leapParticleDir.clone().multiply(ticks));
                player.getWorld().spawnParticle(
                        Particle.CLOUD,
                        leapParticleLoc,
                        3, 0.1, 0.1, 0.1, 0.02
                );

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Play sounds
        player.getWorld().playSound(impactLoc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.2f);
        player.getWorld().playSound(impactLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        setSkillSuccess(true);
    }
}