package com.michael.mmorpg.skills.windwaker;

import com.michael.mmorpg.models.PlayerData;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;
import com.michael.mmorpg.skills.Skill;

public class WindChopSkill extends Skill {
    // Skill properties from config
    private final double damage;
    private final double range;
    private LivingEntity target;

    public WindChopSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.range = config.getDouble("range", 15.0);
    }

    @Override
    public void execute(Player player) {
        // First get the player data
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Check resources BEFORE doing anything else
        if (!plugin.getSkillManager().checkResources(player, playerData, this)) {
            // This will automatically send the "not enough mana/stamina" message
            return;
        }

        // Use base class targeting that includes party checks
        currentTarget = getTargetEntity(player, range);
        if (currentTarget == null) {
            player.sendMessage("§c✦ No target in range!");
            return;
        }
        target = currentTarget;

        // Set success before performing the skill
        setSkillSuccess(true);

        // Perform the skill
        performSkill(player);

        // Only consume resources and set cooldown if the skill succeeded
        if (skillSucceeded) {
            plugin.getSkillManager().consumeResources(playerData, this);
            plugin.getSkillManager().setCooldown(player, getName(), getCooldown());
        }
    }

    @Override
    protected void performSkill(Player player) {
        // Validate target is still valid
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Create the wind chop effect
        createWindChopEffect(player, target);

        // Apply damage after a brief delay to match the visual effect
        new BukkitRunnable() {
            @Override
            public void run() {
                // Set both pieces of metadata
                target.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), player));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), damage));
                target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

                // Apply the damage
                target.damage(damage, player);

            }
        }.runTaskLater(getPlugin(), 5L); // Quarter second delay

        setSkillSuccess(true);

        // Broadcast the strike
        broadcastLocalSkillMessage(player, "§7[Windwaker] " + player.getName() + " strikes " +
                target.getName() + " with Wind Chop!");
    }

    private void createWindChopEffect(Player caster, LivingEntity target) {
        World world = caster.getWorld();
        Location start = caster.getEyeLocation();
        Location end = target.getEyeLocation();
        Vector direction = end.clone().subtract(start).toVector().normalize();
        double distance = start.distance(end);

        // Create the wind blade effect
        new BukkitRunnable() {
            double progress = 0;
            double width = 0.3;

            @Override
            public void run() {
                if (progress >= distance) {
                    // Create impact effect at the end
                    createImpactEffect(end);
                    cancel();
                    return;
                }

                // Calculate current position
                Location current = start.clone().add(direction.clone().multiply(progress));

                // Create wind blade particles
                for (double offset = -width; offset <= width; offset += 0.2) {
                    Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX())
                            .multiply(offset);
                    Location particleLoc = current.clone().add(perpendicular);

                    // Main blade effect
                    world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0);

                    // Add some sweep particles for sharpness
                    if (Math.random() < 0.3) {
                        world.spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }

                // Play whoosh sound that follows the blade
                if (progress % 2 == 0) {
                    world.playSound(current, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.5f);
                }

                progress += 1.0; // Speed of the effect
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void createImpactEffect(Location location) {
        World world = location.getWorld();

        // Create burst of particles
        world.spawnParticle(Particle.CLOUD, location, 15, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticle(Particle.SWEEP_ATTACK, location, 3, 0.2, 0.2, 0.2, 0);

        // Create expanding ring effect
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double radius = 0.8;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = location.clone().add(x, 0, z);

            world.spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0.1);
        }

        // Play impact sounds
        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.5f);
        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
    }
}