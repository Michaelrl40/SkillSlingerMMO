package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import com.michael.mmorpg.models.PlayerData;

public class PoisonSplashSkill extends Skill {
    private final double poisonDamage;
    private final int poisonDuration;
    private final double radius;

    public PoisonSplashSkill(ConfigurationSection config) {
        super(config);
        this.poisonDamage = config.getDouble("poisonDamage", 5.0);
        this.poisonDuration = config.getInt("poisonDuration", 12);
        this.radius = config.getDouble("radius", 4.0);
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
        if (data == null) return;

        if (data.getCurrentToxin() < toxinCost) {
            player.sendMessage("§c☠ Not enough toxin!");
            setSkillSuccess(false); // Set to false to prevent cooldown
            return;
        }

        data.setCurrentToxin(data.getCurrentToxin() - toxinCost);

        // Create thrown potion for trajectory
        ThrownPotion thrownPotion = player.launchProjectile(ThrownPotion.class);
        Vector direction = player.getLocation().getDirection();
        direction.multiply(0.5).setY(direction.getY() + 0.2);
        thrownPotion.setVelocity(direction);

        // Track the projectile and create poison area when it lands
        new BukkitRunnable() {
            @Override
            public void run() {
                if (thrownPotion.isOnGround() || !thrownPotion.isValid()) {
                    createPoisonArea(player, thrownPotion.getLocation());
                    thrownPotion.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0F, 1.0F);
        player.getWorld().spawnParticle(
                Particle.DRAGON_BREATH,
                player.getLocation().add(0, 1.5, 0),
                10, 0.2, 0.2, 0.2, 0.05
        );

        setSkillSuccess(true);
    }

    private void createPoisonArea(Player owner, Location location) {
        AreaEffectCloud cloud = (AreaEffectCloud) location.getWorld().spawnEntity(
                location,
                EntityType.AREA_EFFECT_CLOUD
        );

        cloud.setRadius((float) radius);
        cloud.setDuration(poisonDuration * 10);
        cloud.setParticle(Particle.DRAGON_BREATH);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cloud.isValid()) {
                    this.cancel();
                    return;
                }

                for (Entity entity : cloud.getNearbyEntities(radius, radius, radius)) {
                    if (!(entity instanceof LivingEntity) || entity == owner) continue;
                    if (entity instanceof Player &&
                            plugin.getPartyManager().getParty(owner) != null &&
                            plugin.getPartyManager().getParty(owner).isMember((Player)entity)) continue;

                    LivingEntity target = (LivingEntity) entity;

                    // Apply as skill damage instead of direct damage to ensure fixed damage amount
                    target.setMetadata("skill_damage", new FixedMetadataValue(plugin, owner));
                    target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, poisonDamage));
                    target.setMetadata("true_damage", new FixedMetadataValue(plugin, true));

                    // Apply minimal damage to trigger the damage event with our metadata
                    target.damage(0.1, owner);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}