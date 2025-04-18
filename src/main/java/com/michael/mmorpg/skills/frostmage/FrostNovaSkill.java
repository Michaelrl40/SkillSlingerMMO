package com.michael.mmorpg.skills.frostmage;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class FrostNovaSkill extends Skill {
    private final double radius;
    private final long rootDuration;
    private final int rootIntensity;

    public FrostNovaSkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 8.0);
        this.rootDuration = config.getLong("rootDuration", 5000); // 5 seconds
        this.rootIntensity = config.getInt("rootIntensity", 2);
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();
        Party casterParty = plugin.getPartyManager().getParty(player);

        // Create expanding ring effect
        createExpandingRingEffect(player, center, world);

        // Apply root effect to nearby entities
        player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius).forEach(entity -> {
            if ((entity instanceof LivingEntity) && entity != player) {
                LivingEntity target = (LivingEntity) entity;

                // Check for party membership before applying effects
                if (entity instanceof Player) {
                    if (casterParty != null && casterParty.shouldPreventInteraction(player, entity, true)) {
                        return; // Skip party members
                    }
                }

                // Apply root effect depending on entity type
                if (target instanceof Player) {
                    // For players, use the status effect system
                    StatusEffect root = new StatusEffect(CCType.ROOT, rootDuration, player, 1);
                    MinecraftMMORPG.getInstance().getStatusEffectManager().applyEffect((Player) target, root);
                } else {
                    // For non-players, apply maximum slowness and lock position
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            (int) (rootDuration / 50),
                            6, // Maximum slowness
                            false,
                            true));

                    // Start position locking for non-player entities
                    Location lockLocation = target.getLocation();
                    new BukkitRunnable() {
                        int ticks = 0;
                        final int maxTicks = (int) (rootDuration / 50);

                        @Override
                        public void run() {
                            if (ticks >= maxTicks || target.isDead()) {
                                this.cancel();
                                return;
                            }
                            target.teleport(lockLocation);
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                }

                // Visual effect on affected entity
                createFrostEffect(target.getLocation().add(0, 1, 0), world);
            }
        });

        // Create center burst effect
        createCenterBurstEffect(center, world);

        // Play initial cast sounds
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 1.0f);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);

        // Broadcast skill use message
        broadcastLocalSkillMessage(player, "§b[" + getPlayerClass(player) + "] " + player.getName() + " unleashed Frost Nova!");
    }

    private void createExpandingRingEffect(Player player, Location center, World world) {
        new BukkitRunnable() {
            double currentRadius = 0;
            final double expandSpeed = 0.5;
            int ticks = 0;

            @Override
            public void run() {
                if (currentRadius >= radius || ticks >= 20) {
                    this.cancel();
                    return;
                }

                // Create circle of particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = center.getX() + (currentRadius * Math.cos(angle));
                    double z = center.getZ() + (currentRadius * Math.sin(angle));
                    Location particleLoc = new Location(world, x, center.getY(), z);

                    // Ground frost particles
                    world.spawnParticle(Particle.ITEM_SNOWBALL, particleLoc, 2, 0.1, 0, 0.1, 0);
                    world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0, 0, 0, 0);

                    // Ice crystals rising effect
                    world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0.5, 0, 0.02);

                    // Ground ice effect
                    Location groundLoc = particleLoc.clone().add(0, 0.1, 0);
                    world.spawnParticle(Particle.BLOCK_CRUMBLE, groundLoc, 1, 0.1, 0, 0.1, 0, Material.ICE.createBlockData());
                }

                // Create dome effect
                for (double y = 0; y <= 2; y += 0.5) {
                    double radiusAtHeight = currentRadius * (1 - y/4); // Dome shape
                    for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                        double x = center.getX() + (radiusAtHeight * Math.cos(angle));
                        double z = center.getZ() + (radiusAtHeight * Math.sin(angle));
                        Location particleLoc = new Location(world, x, center.getY() + y, z);

                        world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0, 0, 0, 0);
                    }
                }

                if (ticks % 4 == 0) {
                    world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.5f, 2.0f);
                    world.playSound(center, Sound.BLOCK_SNOW_BREAK, 0.5f, 1.5f);
                }

                currentRadius += expandSpeed;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void createFrostEffect(Location location, World world) {
        world.spawnParticle(Particle.SNOWFLAKE, location, 20, 0.5, 1, 0.5, 0.05);
    }

    private void createCenterBurstEffect(Location center, World world) {
        world.spawnParticle(Particle.EXPLOSION, center, 5, 0.5, 0.5, 0.5, 0.05);
        world.spawnParticle(Particle.ITEM_SNOWBALL, center, 30, 0.5, 0.5, 0.5, 0.1);
    }
}