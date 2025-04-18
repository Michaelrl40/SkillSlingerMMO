package com.michael.mmorpg.skills.bandolier;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public class RapidFireSkill extends Skill {
    private final int arrowCount;
    private final double arrowDamage;
    private final int fireDelay;
    private final double spread;
    private final double arrowVelocity;
    private final Random random = new Random();

    public RapidFireSkill(ConfigurationSection config) {
        super(config);
        this.arrowCount = config.getInt("arrowCount", 6);
        this.arrowDamage = config.getDouble("arrowDamage", 8.0);
        this.fireDelay = config.getInt("fireDelay", 2);
        this.spread = config.getDouble("spread", 0.2);
        this.arrowVelocity = config.getDouble("arrowVelocity", 2.0);
    }

    @Override
    protected void performSkill(Player player) {
        if (player.isSneaking()) {
            setSkillSuccess(false);
            player.sendMessage("§c✦ Cannot rapid fire while sneaking!");
            return;
        }

        // Play initial skill sound
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1.0f, 2.0f);

        // Start firing sequence
        new BukkitRunnable() {
            int arrowsFired = 0;

            @Override
            public void run() {
                if (arrowsFired >= arrowCount || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // Fire an arrow
                fireArrow(player);
                arrowsFired++;

                // Play shot sound
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 1.5f);
            }
        }.runTaskTimer(plugin, 0L, fireDelay);

        setSkillSuccess(true);
    }

    private void fireArrow(Player player) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();

        // Add spread
        double spreadX = (random.nextDouble() - 0.5) * spread;
        double spreadY = (random.nextDouble() - 0.5) * spread;
        double spreadZ = (random.nextDouble() - 0.5) * spread;
        direction.add(new Vector(spreadX, spreadY, spreadZ)).normalize();

        // Create and set up arrow
        Arrow arrow = player.launchProjectile(Arrow.class, direction.multiply(arrowVelocity));

        // Set custom damage
        arrow.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        arrow.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, arrowDamage));

        // Set arrow properties
        arrow.setFireTicks(100); // Make arrows appear on fire
        arrow.setCritical(true); // Make arrows appear critical
        arrow.setKnockbackStrength(1);

        // Add particle trail to the arrow
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                    this.cancel();
                    return;
                }

                // Create fire particle trail
                arrow.getWorld().spawnParticle(
                        Particle.DUST,
                        arrow.getLocation(),
                        1, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 69, 0), 1)
                );
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}