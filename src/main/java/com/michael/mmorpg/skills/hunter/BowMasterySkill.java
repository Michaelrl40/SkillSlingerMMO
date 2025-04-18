package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class BowMasterySkill extends Skill implements Listener {
    // The multiplier that determines how much faster arrows will fly
    private final double arrowVelocityMultiplier;

    public BowMasterySkill(ConfigurationSection config) {
        super(config);
        // Load the velocity multiplier from config, default to 1.3 if not specified
        this.arrowVelocityMultiplier = config.getDouble("velocitymultiplier", 1.3);
    }

    @Override
    protected void performSkill(Player player) {
        // Since this is a passive skill, this method remains empty
        // The skill's effects are handled through event listening
        setSkillSuccess(true);
    }

    @EventHandler
    public void onBowShoot(EntityShootBowEvent event) {
        // First, verify this is a player shooting
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Verify the player meets class and level requirements
        if (!hasSkillRequirements(player)) {
            return;
        }

        // Verify we're dealing with an arrow projectile
        if (!(event.getProjectile() instanceof Arrow)) {
            return;
        }

        Arrow arrow = (Arrow) event.getProjectile();

        // Increase the arrow's velocity by our multiplier
        arrow.setVelocity(arrow.getVelocity().multiply(arrowVelocityMultiplier));

        // Mark this arrow as enhanced by bow mastery
        arrow.setMetadata("bow_mastery", new FixedMetadataValue(plugin, true));

        // Create visual and sound effects to show the skill is working
        playMasteryEffects(player);
    }

    private boolean hasSkillRequirements(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) {
            return false;
        }

        GameClass playerClass = playerData.getGameClass();
        return (playerClass.getName().equalsIgnoreCase("Hunter") ||
                playerClass.getName().equalsIgnoreCase("ElementalRanger")) &&
                playerData.getLevel() >= getLevelRequired();
    }

    private void playMasteryEffects(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Create a subtle particle effect around the player
        world.spawnParticle(
                Particle.CRIT,
                loc,
                5, 0.2, 0.2, 0.2, 0.1
        );

        // Play a higher-pitched arrow sound to indicate the enhanced shot
        world.playSound(
                loc,
                Sound.ENTITY_ARROW_SHOOT,
                0.3f,
                1.5f
        );
    }
}