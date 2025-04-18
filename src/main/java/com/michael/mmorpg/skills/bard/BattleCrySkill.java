package com.michael.mmorpg.skills.bard;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class BattleCrySkill extends Skill {
    private final double damage;
    private final double radius;

    public BattleCrySkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 10.0);
        this.radius = config.getDouble("radius", 10.0);

        // Make sure mana cost is set from config
        // This ensures the skill costs mana like normal skills
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();

        // Visual effects - expanding ring of particles
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
            for (double r = 1; r <= radius; r += 2) {
                double x = center.getX() + r * Math.cos(angle);
                double z = center.getZ() + r * Math.sin(angle);
                Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.5, z);

                // Red note particles
                center.getWorld().spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        3, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f)
                );
            }
        }

        // Sound effects - layered for more impact
        player.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_BASS, 1.5f, 0.8f);
        player.getWorld().playSound(center, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.2f);

        // Damage nearby entities
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity && entity != player) {
                LivingEntity target = (LivingEntity) entity;

                // Skip if target is in the same party
                if (plugin.getPartyManager().getParty(player) != null &&
                        target instanceof Player &&
                        plugin.getPartyManager().getParty(player).isMember((Player)target)) {
                    continue;
                }

                // Apply damage
                target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
                target.damage(damage, player);

                // Individual hit effect
                target.getWorld().spawnParticle(
                        Particle.NOTE,
                        target.getLocation().add(0, 1, 0),
                        5, 0.2, 0.2, 0.2, 0.1
                );
            }
        }

        // Success message
        broadcastLocalSkillMessage(player, "§c[Bard] " + player.getName() + " unleashes a devastating Battle Cry!");

        setSkillSuccess(true);
    }
}