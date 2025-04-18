package com.michael.mmorpg.skills.windwaker;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;
import java.util.HashSet;
import java.util.Set;

public class WindWallSkill extends Skill {
    private final double width;           // Width of the wall
    private final double height;          // Height of the wall
    private final int duration;           // How long the wall lasts
    private final double deflectForce;    // How strongly projectiles are deflected

    // Track active walls for cleanup
    private static final Set<WindWall> activeWalls = new HashSet<>();

    public WindWallSkill(ConfigurationSection config) {
        super(config);
        this.width = config.getDouble("width", 5.0);
        this.height = config.getDouble("height", 4.0);
        this.duration = config.getInt("duration", 10);
        this.deflectForce = config.getDouble("deflectforce", 1.5);
    }

    @Override
    protected void performSkill(Player player) {
        // Create wall location slightly in front of player
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        Location wallCenter = player.getLocation().add(direction.clone().multiply(2));

        // Create the wind wall
        WindWall wall = new WindWall(player, wallCenter, direction);
        activeWalls.add(wall);

        // Schedule wall removal
        new BukkitRunnable() {
            @Override
            public void run() {
                wall.remove();
                activeWalls.remove(wall);
            }
        }.runTaskLater(getPlugin(), duration * 20L);

        // Play creation effects
        createWallEffects(wallCenter, direction);

        // Broadcast the skill use
        broadcastLocalSkillMessage(player, "§7[Windwaker] " + player.getName() + " summons a wall of wind!");

        setSkillSuccess(true);
    }

    private class WindWall {
        private final Player caster;
        private final Location center;
        private final Vector facing;
        private final Vector right;
        private int taskId;
        private final Party casterParty;

        public WindWall(Player caster, Location center, Vector facing) {
            this.caster = caster;
            this.center = center;
            this.facing = facing;
            this.right = new Vector(-facing.getZ(), 0, facing.getX());
            this.casterParty = plugin.getPartyManager().getParty(caster);

            startWallTask();
        }

        private void startWallTask() {
            taskId = new BukkitRunnable() {
                @Override
                public void run() {
                    // Check for nearby projectiles
                    center.getWorld().getNearbyEntities(center, width/2, height/2, width/2).forEach(entity -> {
                        if (entity instanceof Projectile) {
                            handleProjectile((Projectile) entity);
                        }
                    });

                    // Create wall particles
                    createWallParticles();
                }
            }.runTaskTimer(getPlugin(), 0L, 1L).getTaskId();
        }

        private void handleProjectile(Projectile projectile) {
            // Get projectile shooter
            ProjectileSource shooter = projectile.getShooter();

            // Allow friendly projectiles through
            if (shooter instanceof Player) {
                Player shooterPlayer = (Player) shooter;
                if (casterParty != null && casterParty.isMember(shooterPlayer)) {
                    return;
                }
            }

            // Calculate if projectile is passing through wall
            Location projLoc = projectile.getLocation();
            Vector toProj = projLoc.clone().subtract(center).toVector();

            // Project onto wall normal (facing direction)
            double distanceToWall = toProj.dot(facing);

            // Only deflect projectiles moving towards the wall
            if (Math.abs(distanceToWall) < 0.5) {
                Vector projVel = projectile.getVelocity();
                if (projVel.dot(facing) < 0) {
                    // Calculate position along wall width
                    double rightOffset = toProj.dot(right);

                    if (Math.abs(rightOffset) <= width/2 && projLoc.getY() <= center.getY() + height) {
                        // Deflect the projectile
                        deflectProjectile(projectile);
                    }
                }
            }
        }

        private void deflectProjectile(Projectile projectile) {
            // Remove the projectile and create effect
            Location deflectLoc = projectile.getLocation();
            projectile.remove();

            // Create deflection effect
            World world = deflectLoc.getWorld();
            world.spawnParticle(Particle.CLOUD, deflectLoc, 10, 0.2, 0.2, 0.2, 0.1);
            world.spawnParticle(Particle.SWEEP_ATTACK, deflectLoc, 2, 0.1, 0.1, 0.1, 0);
            world.playSound(deflectLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 2.0f);
        }

        private void createWallParticles() {
            World world = center.getWorld();
            double particleSpacing = 0.5; // Space between particles

            // Create particle grid
            for (double h = 0; h < height; h += particleSpacing) {
                for (double w = -width/2; w <= width/2; w += particleSpacing) {
                    // Calculate particle position
                    Location particleLoc = center.clone().add(
                            right.clone().multiply(w).add(new Vector(0, h, 0))
                    );

                    // Create swirling wind effect
                    if (Math.random() < 0.3) {
                        world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                    if (Math.random() < 0.1) {
                        world.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }
            }

            // Occasional wind sound
            if (Math.random() < 0.1) {
                world.playSound(center, Sound.ENTITY_PHANTOM_FLAP, 0.2f, 1.5f);
            }
        }

        public void remove() {
            // Cancel the wall task
            Bukkit.getScheduler().cancelTask(taskId);

            // Create dissipation effect
            World world = center.getWorld();
            for (double h = 0; h < height; h += 0.5) {
                for (double w = -width/2; w <= width/2; w += 0.5) {
                    Location particleLoc = center.clone().add(
                            right.clone().multiply(w).add(new Vector(0, h, 0))
                    );
                    world.spawnParticle(Particle.CLOUD, particleLoc, 2, 0.2, 0.2, 0.2, 0.1);
                }
            }
            world.playSound(center, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.5f);
        }
    }

    private void createWallEffects(Location center, Vector direction) {
        World world = center.getWorld();
        Vector right = new Vector(-direction.getZ(), 0, direction.getX());

        // Create rising wind effect
        new BukkitRunnable() {
            double heightProgress = 0;

            @Override
            public void run() {
                if (heightProgress >= height) {
                    cancel();
                    return;
                }

                // Create particle line
                for (double w = -width/2; w <= width/2; w += 0.3) {
                    Location particleLoc = center.clone().add(
                            right.clone().multiply(w).add(new Vector(0, heightProgress, 0))
                    );
                    world.spawnParticle(Particle.CLOUD, particleLoc, 2, 0.1, 0.1, 0.1, 0.05);
                    world.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                }

                // Play formation sound
                if (heightProgress % 1.0 < 0.1) {
                    world.playSound(center, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.5f);
                }

                heightProgress += 0.2;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }
}