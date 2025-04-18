package com.michael.mmorpg.skills.skyknight;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class CloudBounceSkill extends Skill {
    private final double bounceHeight;
    private final double upwardVelocity;

    public CloudBounceSkill(ConfigurationSection config) {
        super(config);
        this.bounceHeight = config.getDouble("bounceheight", 15.0);
        // Increased multiplier for stronger bounce
        this.upwardVelocity = Math.sqrt(2 * 0.08 * bounceHeight) * 1.3;
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player is gliding
        if (!player.isGliding()) {
            player.sendMessage("§c✦ You must be gliding to use Cloud Bounce!");
            setSkillSuccess(false);
            return;
        }

        // Apply the bounce
        startBounce(player);
        setSkillSuccess(true);
    }

    private void startBounce(Player player) {
        // Apply upward velocity
        Vector velocity = new Vector(0, upwardVelocity, 0);
        player.setVelocity(velocity);

        // Play bounce effects
        playBounceEffects(player);

        // Set fall damage immunity until landing
        player.setMetadata("cloudbounce_immunity", new FixedMetadataValue(plugin, true));
    }

    private void playBounceEffects(Player player) {
        Location loc = player.getLocation();

        // Intense cloud burst effect
        player.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                loc,
                1, 0, 0, 0, 0
        );
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                loc,
                50, 0.8, 0.1, 0.8, 0.3
        );

        // Powerful sound effects
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 2.0f);
        player.getWorld().playSound(loc, Sound.ENTITY_GOAT_LONG_JUMP, 1.0f, 0.5f);
    }

    // Event helper method to check immunity
    public static boolean hasBounceFallImmunity(Player player) {
        return player.hasMetadata("cloudbounce_immunity");
    }

    // Cleanup method
    public static void cleanup(Player player) {
        if (player.hasMetadata("cloudbounce_immunity")) {
            player.removeMetadata("cloudbounce_immunity", plugin);
        }
    }
}