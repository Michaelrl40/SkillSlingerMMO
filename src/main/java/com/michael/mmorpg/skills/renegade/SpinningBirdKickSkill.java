package com.michael.mmorpg.skills.renegade;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.HashSet;
import java.util.Set;

public class SpinningBirdKickSkill extends Skill {
    private final double damage;
    private final double radius;
    private final double knockback;
    private final double invulnDuration;

    public SpinningBirdKickSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 35.0);
        this.radius = config.getDouble("radius", 4.0);
        this.knockback = config.getDouble("knockback", 2.0);
        this.invulnDuration = config.getDouble("invulnduration", 0.5);
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();
        Set<Entity> hitEntities = new HashSet<>();

        // Apply invulnerability
        player.setMetadata("spinning_bird_invuln", new FixedMetadataValue(plugin, true));

        // Damage application and effects without spinning
        new BukkitRunnable() {
            private double angle = 0;
            private int ticks = 0;
            private final int duration = (int)(invulnDuration * 20);

            @Override
            public void run() {
                if (ticks >= duration) {
                    player.removeMetadata("spinning_bird_invuln", plugin);
                    this.cancel();
                    return;
                }

                // Create 360-degree particle effect without rotating player
                for (double i = 0; i < Math.PI * 2; i += Math.PI / 16) {
                    double x = Math.cos(angle + i) * radius;
                    double z = Math.sin(angle + i) * radius;
                    Location particleLoc = center.clone().add(x, 0.5, z);
                    player.getWorld().spawnParticle(
                            Particle.SWEEP_ATTACK,
                            particleLoc,
                            1, 0, 0, 0, 0
                    );
                }

                // Check for new entities to damage
                player.getWorld().getNearbyEntities(center, radius, 2, radius).stream()
                        .filter(entity -> entity instanceof LivingEntity)
                        .filter(entity -> entity != player)
                        .filter(entity -> !hitEntities.contains(entity))
                        .forEach(entity -> {
                            LivingEntity target = (LivingEntity) entity;
                            hitEntities.add(target);

                            // Apply damage
                            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
                            target.damage(damage, player);

                            // Show hit effect without knockback
                            target.getWorld().spawnParticle(
                                    Particle.CRIT,
                                    target.getLocation().add(0, 1, 0),
                                    10, 0.3, 0.3, 0.3, 0.1
                            );
                        });

                // Sound effect
                if (ticks % 4 == 0) {
                    player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 2.0f);
                }

                angle += Math.PI / 8;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Visual feedback that skill was activated
        player.getWorld().spawnParticle(
                Particle.FLASH,
                player.getLocation().add(0, 1, 0),
                2, 0, 0, 0, 0
        );
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0f, 1.2f
        );

        setSkillSuccess(true);
    }
}