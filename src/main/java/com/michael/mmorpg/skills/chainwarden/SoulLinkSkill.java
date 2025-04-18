package com.michael.mmorpg.skills.chainwarden;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SoulLinkSkill extends Skill {
    private final double visualSpacing;
    private final int linkDuration;
    private final double projectileSpeed;

    public SoulLinkSkill(ConfigurationSection config) {
        super(config);
        this.visualSpacing = config.getDouble("visualspacing", 0.5);
        this.linkDuration = config.getInt("linkduration", 600);
        this.projectileSpeed = config.getDouble("projectilespeed", 1.0);
        this.isHarmfulSkill = false;
    }

    @Override
    protected void performSkill(Player player) {
        // Launch soul projectile
        Snowball soulProjectile = player.launchProjectile(Snowball.class);
        soulProjectile.setVelocity(player.getLocation().getDirection().multiply(projectileSpeed));
        soulProjectile.setGravity(false);

        soulProjectile.setMetadata("soul_link", new FixedMetadataValue(plugin, true));
        soulProjectile.setMetadata("caster", new FixedMetadataValue(plugin, player.getUniqueId()));

        // Track the projectile and create visual effects
        new BukkitRunnable() {
            final World world = player.getWorld();
            int ticks = 0;
            final int MAX_FLIGHT_TICKS = 40;

            @Override
            public void run() {
                if (!soulProjectile.isValid() || soulProjectile.isDead() || ticks++ > MAX_FLIGHT_TICKS) {
                    soulProjectile.remove();
                    this.cancel();
                    return;
                }

                // Soul trail effect
                drawSoulTrail(player.getEyeLocation(), soulProjectile.getLocation(), world);

                // Check for party member hits
                for (Entity entity : soulProjectile.getNearbyEntities(1, 1, 1)) {
                    if (!(entity instanceof Player) || entity == player) continue;
                    Player target = (Player) entity;

                    if (!isValidPartyTarget(player, target)) continue;

                    establishSoulLink(player, target);
                    soulProjectile.remove();
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private boolean isValidPartyTarget(Player caster, Player target) {
        if (caster.equals(target)) return false;
        return plugin.getPartyManager().getParty(caster) != null &&
                plugin.getPartyManager().getParty(caster).isMember(target);
    }

    private void establishSoulLink(Player caster, Player target) {
        String linkId = caster.getUniqueId() + "_" + target.getUniqueId();
        target.setMetadata("soul_link_target", new FixedMetadataValue(plugin, caster.getUniqueId()));
        caster.setMetadata("soul_link_caster", new FixedMetadataValue(plugin, target.getUniqueId()));

        World world = caster.getWorld();

        // Soul link establishment effect
        Location midPoint = caster.getLocation().add(target.getLocation()).multiply(0.5);
        world.spawnParticle(Particle.SOUL, midPoint, 30, 0.5, 0.5, 0.5, 0.1);
        world.playSound(midPoint, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.8f);

        broadcastLocalSkillMessage(caster, "§9[" + getPlayerClass(caster) + "] " +
                caster.getName() + "'s soul is now bound to " + target.getName() + "!");

        // Continuous link effect
        new BukkitRunnable() {
            int ticksRemaining = linkDuration;

            @Override
            public void run() {
                if (ticksRemaining-- <= 0 || !caster.isOnline() || !target.isOnline() ||
                        !caster.hasMetadata("soul_link_caster") || !target.hasMetadata("soul_link_target")) {
                    removeSoulLink(caster, target);
                    this.cancel();
                    return;
                }

                if (ticksRemaining % 5 == 0) {
                    drawSoulTrail(caster.getLocation().add(0, 1, 0),
                            target.getLocation().add(0, 1, 0), world);
                }

                if (ticksRemaining % 20 == 0) {
                    world.playSound(midPoint, Sound.PARTICLE_SOUL_ESCAPE, 0.3f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void removeSoulLink(Player caster, Player target) {
        if (caster.isOnline()) {
            caster.removeMetadata("soul_link_caster", plugin);
        }
        if (target.isOnline()) {
            target.removeMetadata("soul_link_target", plugin);
        }

        World world = caster.getWorld();
        Location midPoint = caster.getLocation().add(target.getLocation()).multiply(0.5);
        world.playSound(midPoint, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.5f);
        world.spawnParticle(Particle.SOUL, midPoint, 20, 0.5, 0.5, 0.5, 0.1);

        broadcastLocalSkillMessage(caster, "§9[" + getPlayerClass(caster) + "] The soul bond between " +
                caster.getName() + " and " + target.getName() + " fades!");
    }

    private void drawSoulTrail(Location start, Location end, World world) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += visualSpacing) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));

            // Soul flame effect
            world.spawnParticle(Particle.SOUL, particleLoc, 1, 0.05, 0.05, 0.05, 0);

            // Occasional soul fire particles
            if (Math.random() < 0.2) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }
}