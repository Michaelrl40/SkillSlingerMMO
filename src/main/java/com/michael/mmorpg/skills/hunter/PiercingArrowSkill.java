package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.UUID;

public class PiercingArrowSkill extends Skill {
    private final HashMap<UUID, Integer> activeBuffs = new HashMap<>();
    private final int maxShots;
    private final double damage;

    public PiercingArrowSkill(ConfigurationSection config) {
        super(config);
        this.maxShots = config.getInt("maxshots", 5);
        this.damage = config.getDouble("damage", 8.0);
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) {
            return;
        }

        // Apply the buff to track enhanced arrows
        activeBuffs.put(player.getUniqueId(), maxShots);

        // Add metadata to player to mark the buff is active
        player.setMetadata("piercing_arrow_buff", new FixedMetadataValue(plugin, true));

        // Add metadata for the remaining shots
        player.setMetadata("piercing_shots_remaining", new FixedMetadataValue(plugin, maxShots));

        // Apply metadata for true damage amount
        player.setMetadata("piercing_arrow_damage", new FixedMetadataValue(plugin, damage));

        // Visual effects for activation
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        world.spawnParticle(
                Particle.DRAGON_BREATH,
                loc,
                20, 0.3, 0.3, 0.3, 0.05
        );

        world.playSound(loc, Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 1.5f);

        // Broadcast activation
        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                player.getName() + "'s arrows are empowered with piercing energy!");

        player.sendMessage("§6✦ Your next " + maxShots + " arrows will pierce through targets!");

        setSkillSuccess(true);
    }

    // Helper method to get remaining shots
    public static int getRemainingShots(Player player) {
        if (!player.hasMetadata("piercing_shots_remaining")) {
            return 0;
        }
        return player.getMetadata("piercing_shots_remaining").get(0).asInt();
    }

    // Helper method to decrement shots and update metadata
    public static void decrementShots(Player player, MinecraftMMORPG plugin) {
        if (!player.hasMetadata("piercing_shots_remaining")) {
            return;
        }

        int remainingShots = getRemainingShots(player);
        if (remainingShots <= 1) {
            // Remove all related metadata when buff expires
            player.removeMetadata("piercing_arrow_buff", Skill.plugin);
            player.removeMetadata("piercing_shots_remaining", Skill.plugin);
            player.removeMetadata("piercing_arrow_damage", Skill.plugin);
            player.sendMessage("§c✦ Piercing Arrow effect fades!");
        } else {
            // Update remaining shots
            player.setMetadata("piercing_shots_remaining",
                    new FixedMetadataValue(Skill.plugin, remainingShots - 1));
            player.sendMessage("§6✦ " + (remainingShots - 1) + " piercing shots remaining!");
        }
    }
}