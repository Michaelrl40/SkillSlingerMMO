package com.michael.mmorpg.skills.bandolier;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class UppercutSkill extends Skill {
    private final double damage;
    private final double meleeRange;
    private final double launchPower;
    private final double knockupDuration;

    public UppercutSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 10.0);
        this.meleeRange = config.getDouble("meleerange", 3.0);
        this.launchPower = config.getDouble("launchpower", 1.3);
        this.knockupDuration = config.getDouble("knockupDuration", 1.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target using melee targeting system
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

        // Store the target for messages
        currentTarget = target;

        // Play uppercut sound
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);

        // Create uppercut particle effect
        Location particleStart = target.getLocation().add(0, 0.5, 0);
        for (double y = 0; y < 2.5; y += 0.2) {
            Location particleLoc = particleStart.clone().add(0, y, 0);
            player.getWorld().spawnParticle(
                    Particle.CLOUD,
                    particleLoc,
                    3, 0.2, 0.1, 0.2, 0.02
            );
        }

        // Apply damage
        target.setMetadata("skill_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        target.damage(0.1, player); // Trigger damage event with minimal damage

        // Launch target upward
        Vector launchVec = new Vector(0, launchPower, 0);
        target.setVelocity(launchVec);

        // Keep target in air and prevent fall damage
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int) (knockupDuration * 20);

            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || ticks >= maxTicks) {
                    target.setFallDistance(0f);
                    this.cancel();
                    return;
                }

                // Maintain upward momentum
                if (target.getVelocity().getY() < 0.2) {
                    Vector currentVel = target.getVelocity();
                    currentVel.setY(0.2);
                    target.setVelocity(currentVel);
                }

                // Particle trail
                target.getWorld().spawnParticle(
                        Particle.CLOUD,
                        target.getLocation(),
                        2, 0.2, 0.1, 0.2, 0
                );

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Prevent fall damage
        target.setFallDistance(0f);
        if (target instanceof Player) {
            ((Player) target).addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOW_FALLING, (int)(knockupDuration * 20) + 20, 0)
            );
        }

        setSkillSuccess(true);
    }
}