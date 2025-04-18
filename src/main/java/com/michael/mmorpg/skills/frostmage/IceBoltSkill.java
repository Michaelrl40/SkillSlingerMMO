package com.michael.mmorpg.skills.frostmage;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;

public class IceBoltSkill extends Skill {
    private final double damage;
    private final double range;
    private final double speed;
    private final int slowduration;
    private final int slowintensity;

    public IceBoltSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.range = config.getDouble("range", 30.0);
        this.speed = config.getDouble("speed", 2.5); // Increased from 1.2 to 2.5 for faster travel
        this.slowduration = config.getInt("slowduration", 3);
        this.slowintensity = config.getInt("slowintensity", 2);
    }

    @Override
    protected void performSkill(Player player) {
        Location loc = player.getEyeLocation();
        Vector direction = loc.getDirection();

        // Get the caster's party for party checks
        Party casterParty = plugin.getPartyManager().getParty(player);

        // Create the snowball
        Snowball snowball = player.getWorld().spawn(loc, Snowball.class);
        snowball.setShooter(player);

        // Keep trajectory straight with minimal upward arc
        // Remove the upward arc adjustment for a straighter path
        direction.normalize().multiply(speed);
        snowball.setVelocity(direction);

        // Override gravity effects using continuous velocity updates
        snowball.setGravity(false); // Disable gravity effect on the snowball

        // Set damage metadata
        snowball.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), player));
        snowball.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), damage));
        snowball.setMetadata("skill_shooter", new FixedMetadataValue(getPlugin(), player));
        snowball.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

        // Set slow effect metadata
        snowball.setMetadata("skill_slow", new FixedMetadataValue(getPlugin(), true));
        snowball.setMetadata("skill_slow_duration", new FixedMetadataValue(getPlugin(), slowduration));
        snowball.setMetadata("skill_slow_intensity", new FixedMetadataValue(getPlugin(), slowintensity));

        // Add party metadata if needed
        if (casterParty != null) {
            snowball.setMetadata("caster_party", new FixedMetadataValue(getPlugin(), casterParty));
        }

        // Play cast sound effect - higher pitch for faster projectile
        player.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 2.2f);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1.7f);

        // Create initial cast particles - more intense for faster projectile
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 15, 0.2, 0.2, 0.2, 0.08);
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 8, 0.1, 0.1, 0.1, 0.03);

        // Track the snowball and add particle effects
        new BukkitRunnable() {
            private final Location startLoc = loc.clone();
            private int ticks = 0;
            private final Vector originalDirection = direction.clone();

            @Override
            public void run() {
                if (snowball.isDead() || snowball.isOnGround() ||
                        startLoc.distance(snowball.getLocation()) > range ||
                        ticks++ > 100) {  // Failsafe timeout
                    createImpactEffect(snowball.getLocation());
                    snowball.remove();
                    cancel();
                    return;
                }

                // Re-apply velocity every few ticks to maintain speed and straightness
                if (ticks % 3 == 0) {
                    snowball.setVelocity(originalDirection);
                }

                Location currentLoc = snowball.getLocation();

                // Main trail - lighter trail for faster projectile
                currentLoc.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        currentLoc,
                        2, 0.05, 0.05, 0.05, 0.01);

                // Secondary effects - streamlined for faster projectile
                if (ticks % 2 == 0) {
                    currentLoc.getWorld().spawnParticle(Particle.ITEM_SNOWBALL,
                            currentLoc,
                            1, 0.05, 0.05, 0.05, 0.01);
                }

                // Add some sparkle, less frequently for faster projectile
                if (Math.random() < 0.2) {
                    currentLoc.getWorld().spawnParticle(Particle.END_ROD,
                            currentLoc,
                            1, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        setSkillSuccess(true);
    }

    private void createImpactEffect(Location location) {
        World world = location.getWorld();
        if (world != null) {
            // More intense impact for a faster projectile
            world.spawnParticle(Particle.SNOWFLAKE, location, 20, 0.4, 0.4, 0.4, 0.25);
            world.spawnParticle(Particle.ITEM_SNOWBALL, location, 15, 0.3, 0.3, 0.3, 0.15);
            world.spawnParticle(Particle.CLOUD, location, 8, 0.3, 0.3, 0.3, 0.05);
            world.spawnParticle(Particle.END_ROD, location, 5, 0.2, 0.2, 0.2, 0.08);

            // Impact sounds - louder for faster projectile
            world.playSound(location, Sound.BLOCK_GLASS_BREAK, 0.7f, 1.4f);
            world.playSound(location, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 1.4f);
        }
    }
}