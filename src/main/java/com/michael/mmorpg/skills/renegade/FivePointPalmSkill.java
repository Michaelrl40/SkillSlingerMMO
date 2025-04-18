package com.michael.mmorpg.skills.renegade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class FivePointPalmSkill extends Skill {
    private final double damage;
    private final double stackDamage;
    private static final String BUFF_KEY = "five_point_palm_buff";
    private static final String STACK_KEY = "five_point_palm_stacks";
    private static final String DAMAGE_KEY = "five_point_palm_damage";
    private final int maxStacks = 5;
    private final double buffDuration;

    public FivePointPalmSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.stackDamage = config.getDouble("stackdamage", 10.0);
        this.buffDuration = config.getDouble("buffduration", 10.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Set cooldown
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());

        // Apply the buff to the player
        player.setMetadata(BUFF_KEY, new FixedMetadataValue(plugin, maxStacks));
        // Store the damage value with the buff
        player.setMetadata(DAMAGE_KEY, new FixedMetadataValue(plugin, stackDamage));

        // Visual and sound effects for buff activation
        Location playerLoc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(
                Particle.END_ROD,
                playerLoc,
                20, 0.3, 0.3, 0.3, 0.1
        );
        player.getWorld().playSound(playerLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);

        player.sendMessage("§6✦ Five Point Palm technique ready! Next " + maxStacks + " attacks empowered!");

        // Remove buff after duration
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.hasMetadata(BUFF_KEY)) {
                int remainingStacks = player.getMetadata(BUFF_KEY).get(0).asInt();
                if (remainingStacks > 0) {
                    player.removeMetadata(BUFF_KEY, plugin);
                    player.removeMetadata(DAMAGE_KEY, plugin);
                    player.sendMessage("§c✦ Five Point Palm technique fades...");
                }
            }
        }, (long)(buffDuration * 20));

        setSkillSuccess(true);
    }

    // This method should be called from a combat listener when the player hits something
    public static void onPlayerHit(Player player, LivingEntity target) {
        if (!player.hasMetadata(BUFF_KEY) || !player.hasMetadata(DAMAGE_KEY)) return;

        int remainingStacks = player.getMetadata(BUFF_KEY).get(0).asInt();
        double storedDamage = player.getMetadata(DAMAGE_KEY).get(0).asDouble();

        if (remainingStacks <= 0) {
            player.removeMetadata(BUFF_KEY, plugin);
            player.removeMetadata(DAMAGE_KEY, plugin);
            return;
        }

        // Get current stacks on target
        int currentStacks = 0;
        if (target.hasMetadata(STACK_KEY)) {
            currentStacks = target.getMetadata(STACK_KEY).get(0).asInt();
        }

        // Apply new stack
        currentStacks++;
        target.setMetadata(STACK_KEY, new FixedMetadataValue(plugin, currentStacks));

        // Update remaining buff stacks
        player.setMetadata(BUFF_KEY, new FixedMetadataValue(plugin, remainingStacks - 1));

        // Visual and sound effects for hit
        Location hitLoc = target.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(
                Particle.CRIT,
                hitLoc,
                10, 0.2, 0.2, 0.2, 0.1
        );
        player.getWorld().playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 2.0f);

        // Show stack count
        player.sendMessage("§6✦ Five Point Palm: " + currentStacks + " stacks");

        // If 5 stacks, detonate
        if (currentStacks >= 5) {
            // Apply stack damage using the stored damage value
            double totalStackDamage = storedDamage * 5;
            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, totalStackDamage));
            target.damage(totalStackDamage, player);

            // Detonation effects
            player.getWorld().spawnParticle(
                    Particle.EXPLOSION_EMITTER,
                    hitLoc,
                    1, 0, 0, 0, 0
            );
            player.getWorld().spawnParticle(
                    Particle.CRIT,
                    hitLoc,
                    30, 0.5, 0.5, 0.5, 0.3
            );
            player.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);

            // Clear stacks and buffs
            target.removeMetadata(STACK_KEY, plugin);
            player.removeMetadata(BUFF_KEY, plugin);
            player.removeMetadata(DAMAGE_KEY, plugin);
            player.sendMessage("§c§l⚔ Five Point Palm: Detonated!");
        }
    }
}