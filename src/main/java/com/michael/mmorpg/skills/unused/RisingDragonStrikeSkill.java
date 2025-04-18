package com.michael.mmorpg.skills.unused;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class RisingDragonStrikeSkill extends Skill {
    private final double damage;
    private final double range;
    private final double launchPower;
    private final double knockupDuration;

    public RisingDragonStrikeSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 45.0);
        this.range = config.getDouble("range", 3.0);
        this.launchPower = config.getDouble("launchpower", 1.2);
        this.knockupDuration = config.getDouble("knockupDuration", 1.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target in melee range
        LivingEntity target = getMeleeTarget(player, range);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(damage, player);

        // Launch both player and target upward
        Vector upwardForce = new Vector(0, launchPower, 0);
        target.setVelocity(upwardForce);
        player.setVelocity(upwardForce);

        // Add fall damage protection using the disengage immunity system
        player.setMetadata("disengage_immunity", new FixedMetadataValue(plugin, true));
        target.setMetadata("disengage_immunity", new FixedMetadataValue(plugin, true));

        // Visual and sound effects
        Location effectLoc = target.getLocation();
        player.getWorld().playSound(effectLoc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 2.0f);

        // Dragon particle effect spiraling upward
        new BukkitRunnable() {
            private double angle = 0;
            private double height = 0;
            private final double maxHeight = 3.0;

            @Override
            public void run() {
                if (height >= maxHeight) {
                    this.cancel();
                    return;
                }

                Location particleLoc = effectLoc.clone().add(0, height, 0);
                double radius = 1.0 - (height / maxHeight);

                // Create spiral effect
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                particleLoc.add(x, 0, z);

                player.getWorld().spawnParticle(
                        Particle.DRAGON_BREATH,
                        particleLoc,
                        5, 0.1, 0.1, 0.1, 0.05
                );

                angle += Math.PI / 8;
                height += 0.2;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Remove fall immunity after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.removeMetadata("disengage_immunity", plugin);
                }
                if (target.isValid()) {
                    target.removeMetadata("disengage_immunity", plugin);
                }
            }
        }.runTaskLater(plugin, (long)(knockupDuration * 20));

        setSkillSuccess(true);
    }
}