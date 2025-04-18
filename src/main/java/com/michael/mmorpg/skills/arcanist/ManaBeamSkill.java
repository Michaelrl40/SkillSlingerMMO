package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ManaBeamSkill extends Skill {
    private final double initialManaCost;
    private final double manaPerSecond;
    private final double baseDamage;
    private final double damageIncreasePerSecond;
    private final double maxDamageMultiplier;
    private final int beamCheckInterval;

    public ManaBeamSkill(ConfigurationSection config) {
        super(config);
        this.initialManaCost = config.getDouble("initialmanacost", 30.0);
        this.manaPerSecond = config.getDouble("manapersecond", 15.0);
        this.baseDamage = config.getDouble("basedamage", 8.0);
        this.damageIncreasePerSecond = config.getDouble("damageincreaseperSecond", 2.0);
        this.maxDamageMultiplier = config.getDouble("maxdamagemultiplier", 3.0);
        this.beamCheckInterval = config.getInt("beamcheckinterval", 5);
    }

    @Override
    protected void performSkill(Player player) {
        // Store just the block coordinates, not the full location
        int startBlockX = player.getLocation().getBlockX();
        int startBlockY = player.getLocation().getBlockY();
        int startBlockZ = player.getLocation().getBlockZ();

        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData == null) {
            setSkillSuccess(false);
            return;
        }

        // Initial mana cost check
        if (playerData.getCurrentMana() < initialManaCost) {
            player.sendMessage("§c✦ Not enough mana!");
            setSkillSuccess(false);
            return;
        }

        // Check if already casting another spell
        if (player.hasMetadata("arcane_missiles_lock") || player.hasMetadata("casting")) {
            player.sendMessage("§c✦ You are already casting another spell!");
            setSkillSuccess(false);
            return;
        }

        // Apply initial mana cost
        playerData.useMana(initialManaCost);

        // Store just the block coordinates in metadata
        player.setMetadata("manabeam_block_x", new FixedMetadataValue(plugin, startBlockX));
        player.setMetadata("manabeam_block_y", new FixedMetadataValue(plugin, startBlockY));
        player.setMetadata("manabeam_block_z", new FixedMetadataValue(plugin, startBlockZ));
        player.setMetadata("manabeam_casting", new FixedMetadataValue(plugin, true));

        // Set lock metadata to prevent other skill usage
        player.setMetadata("arcane_missiles_lock", new FixedMetadataValue(plugin, true));

        final int[] ticksChanneled = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!canContinueCasting(player, playerData)) {
                    endBeam(player);
                    cancel();
                    return;
                }

                ticksChanneled[0]++;

                // Drain mana per second
                if (ticksChanneled[0] % 20 == 0) {
                    playerData.useMana(manaPerSecond);
                }

                double secondsChanneled = ticksChanneled[0] / 20.0;
                double currentDamage = calculateDamage(secondsChanneled);

                // Apply damage more frequently
                if (ticksChanneled[0] % beamCheckInterval == 0) {
                    fireBeam(player, currentDamage);
                }

                playBeamEffects(player);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private boolean canContinueCasting(Player player, PlayerData playerData) {
        // Check if player is still online
        if (!player.isOnline()) {
            return false;
        }

        // Check if player left their starting block - comparing only block coordinates
        if (!isAtStartingBlock(player)) {
            player.sendMessage("§c✦ Mana Beam interrupted - you moved!");
            return false;
        }

        // Check if player has enough mana
        if (playerData.getCurrentMana() < manaPerSecond) {
            player.sendMessage("§c✦ Mana Beam interrupted - not enough mana!");
            return false;
        }

        // Check if player died
        if (player.isDead()) {
            return false;
        }

        return true;
    }

    /**
     * Check if player is still at their starting block position
     * Only compares block coordinates, ignoring exact position and direction
     */
    private boolean isAtStartingBlock(Player player) {
        // Check if all metadata values exist
        if (!player.hasMetadata("manabeam_block_x") ||
                !player.hasMetadata("manabeam_block_y") ||
                !player.hasMetadata("manabeam_block_z")) {
            return false;
        }

        // Get starting block coordinates from metadata
        int startX = player.getMetadata("manabeam_block_x").get(0).asInt();
        int startY = player.getMetadata("manabeam_block_y").get(0).asInt();
        int startZ = player.getMetadata("manabeam_block_z").get(0).asInt();

        // Get current block coordinates
        int currentX = player.getLocation().getBlockX();
        int currentY = player.getLocation().getBlockY();
        int currentZ = player.getLocation().getBlockZ();

        // Compare only block coordinates, ignoring exact position and direction
        return startX == currentX && startY == currentY && startZ == currentZ;
    }

    private void playBeamEffects(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection();

        // Create a tighter, more focused beam with blue particles
        for (double d = 0; d <= targetRange; d += 0.3) {
            Location particleLoc = eyeLoc.clone().add(dir.clone().multiply(d));

            // Main beam - small blue particles
            player.getWorld().spawnParticle(
                    Particle.DUST,
                    particleLoc,
                    1, 0.02, 0.02, 0.02, 0,
                    new Particle.DustOptions(Color.fromRGB(50, 150, 255), 0.5f)
            );

            // Occasional sparkle effect
            if (d % 1.5 == 0 && Math.random() < 0.3) {
                player.getWorld().spawnParticle(
                        Particle.END_ROD,
                        particleLoc,
                        1, 0.02, 0.02, 0.02, 0.01
                );
            }
        }

        // Ambient sound every 10 ticks
        if (plugin.getServer().getCurrentTick() % 10 == 0) {
            player.getWorld().playSound(
                    eyeLoc,
                    Sound.BLOCK_BEACON_AMBIENT,
                    0.3f,
                    2.0f
            );
        }
    }

    private void fireBeam(Player player, double currentDamage) {
        // Get target in beam range
        LivingEntity target = getTargetEntity(player, targetRange);

        if (target == null) return;

        // Skip validation for harmful check since we already did it
        if (target instanceof Player) {
            Party casterParty = plugin.getPartyManager().getParty(player);
            if (casterParty != null && casterParty.isMember((Player)target)) {
                return;
            }

            // Check for PvP protection
            if (!isPvPAllowed(player, target)) {
                return;
            }
        }

        // Apply damage metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, currentDamage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

        // Apply damage
        target.damage(0.1, player);

        // Hit effect at target location
        target.getWorld().spawnParticle(
                Particle.CRIT,
                target.getLocation().add(0, 1, 0),
                5, 0.2, 0.2, 0.2, 0.05
        );

        // Clean up metadata
        new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isValid()) {
                    target.removeMetadata("skill_damage", plugin);
                    target.removeMetadata("skill_damage_amount", plugin);
                    target.removeMetadata("magic_damage", plugin);
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private double calculateDamage(double seconds) {
        double multiplier = 1.0 + (seconds * (damageIncreasePerSecond / baseDamage));
        multiplier = Math.min(multiplier, maxDamageMultiplier);
        return baseDamage * multiplier;
    }

    private void endBeam(Player player) {
        // Remove all metadata related to the beam
        player.removeMetadata("manabeam_block_x", plugin);
        player.removeMetadata("manabeam_block_y", plugin);
        player.removeMetadata("manabeam_block_z", plugin);
        player.removeMetadata("manabeam_casting", plugin);

        // Remove the skill lock metadata to allow other skills to be cast again
        player.removeMetadata("arcane_missiles_lock", plugin);

        // Set cooldown for the skill
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());

        // Play deactivation sound
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_BEACON_DEACTIVATE,
                1.0f,
                1.2f
        );
    }

    // Add a method to manually cancel the beam (for use by other systems if needed)
    public static void cancelBeam(Player player) {
        if (player.hasMetadata("manabeam_casting")) {
            player.removeMetadata("manabeam_block_x", plugin);
            player.removeMetadata("manabeam_block_y", plugin);
            player.removeMetadata("manabeam_block_z", plugin);
            player.removeMetadata("manabeam_casting", plugin);
            player.removeMetadata("arcane_missiles_lock", plugin);

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.BLOCK_BEACON_DEACTIVATE,
                    1.0f,
                    1.2f
            );

            player.sendMessage("§c✦ Mana Beam was interrupted!");
        }
    }
}