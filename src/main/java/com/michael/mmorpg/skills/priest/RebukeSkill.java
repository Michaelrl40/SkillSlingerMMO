package com.michael.mmorpg.skills.priest;

import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class RebukeSkill extends Skill {
    private final double radius;
    private final long fearDuration;
    private final double fleeSpeed;

    public RebukeSkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 5.0);
        this.fearDuration = config.getLong("fearduration", 2000);
        this.fleeSpeed = config.getDouble("fleespeed", 0.3);
        this.isHarmfulSkill = true;
    }

    @Override
    protected void performSkill(Player player) {
        Location loc = player.getLocation();

        // Create initial holy light effect
        createHolyLightEffect(loc);

        // Apply effect to nearby entities
        player.getWorld().getNearbyEntities(loc, radius, radius, radius).forEach(entity -> {
            if (entity instanceof LivingEntity && entity != player) {
                // Skip party members
                if (entity instanceof Player) {
                    Player target = (Player) entity;
                    Party casterParty = plugin.getPartyManager().getParty(player);
                    if (casterParty != null && casterParty.isMember(target)) {
                        return;
                    }
                }

                applyRebukeEffect(player, (LivingEntity) entity);
            }
        });

        setSkillSuccess(true);
    }

    private void applyRebukeEffect(Player caster, LivingEntity target) {
        if (target instanceof Player) {
            // Apply fear effect to players using status system
            StatusEffect fear = new StatusEffect(CCType.FEAR, fearDuration, caster, 1);
            plugin.getStatusEffectManager().applyEffect((Player)target, fear);
        } else {
            // Handle non-player entities with a gentler retreat behavior
            new BukkitRunnable() {
                int ticks = 0;
                final int maxTicks = (int)(fearDuration/50);

                @Override
                public void run() {
                    if (ticks >= maxTicks || target.isDead()) {
                        cancel();
                        return;
                    }

                    // Calculate retreat direction
                    Vector direction = target.getLocation().toVector()
                            .subtract(caster.getLocation().toVector())
                            .normalize()
                            .multiply(fleeSpeed);

                    // Add slight randomness to movement for more natural retreat
                    direction.add(new Vector(
                            (Math.random() - 0.5) * 0.1,
                            0,
                            (Math.random() - 0.5) * 0.1
                    ));

                    target.setVelocity(direction);

                    // Create gentle holy particle trail
                    target.getWorld().spawnParticle(
                            Particle.END_ROD,
                            target.getLocation().add(0, 0.5, 0),
                            1, 0.1, 0.1, 0.1, 0
                    );

                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    private void createHolyLightEffect(Location center) {
        // Create expanding ring of light
        new BukkitRunnable() {
            double radius = 0;
            final double maxRadius = 5.0;

            @Override
            public void run() {
                if (radius >= maxRadius) {
                    cancel();
                    return;
                }

                // Create ring of particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = center.clone().add(x, 0.1, z);

                    // Alternate between white and gold particles
                    if (angle % (Math.PI / 8) == 0) {
                        center.getWorld().spawnParticle(
                                Particle.END_ROD,
                                particleLoc,
                                1, 0, 0, 0, 0
                        );
                    } else {
                        center.getWorld().spawnParticle(
                                Particle.DUST,
                                particleLoc,
                                1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(255, 223, 77), 1.0f)
                        );
                    }
                }

                radius += 0.2;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Play holy sound effects
        center.getWorld().playSound(
                center,
                Sound.BLOCK_BEACON_ACTIVATE,
                0.5f,
                1.5f
        );
        center.getWorld().playSound(
                center,
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.7f,
                1.2f
        );
    }
}