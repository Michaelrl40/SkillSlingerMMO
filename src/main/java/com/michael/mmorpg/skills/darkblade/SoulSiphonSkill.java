package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SoulSiphonSkill extends Skill {
    private final double damage;
    private final double manaSteal;
    private final int drainChannels;

    public SoulSiphonSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.manaSteal = config.getDouble("manasteal", 45.0);
        this.drainChannels = config.getInt("drainchannels", 5);
    }

    @Override
    public void execute(Player caster) {
        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        // Ensure we have a valid target and it's a player
        if (!(currentTarget instanceof Player)) {
            caster.sendMessage("§c✦ This skill can only target players!");
            setSkillSuccess(false);
            return;
        }

        Player target = (Player) currentTarget;

        // Check for PvP protection
        if (!isPvPAllowed(caster, target)) {
            caster.sendMessage("§c✦ You cannot use Soul Siphon in a PvP-protected area!");
            setSkillSuccess(false);
            return;
        }

        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);
        PlayerData casterData = plugin.getPlayerManager().getPlayerData(caster);

        if (targetData == null || casterData == null) {
            setSkillSuccess(false);
            return;
        }

        // Calculate mana to steal
        double targetCurrentMana = targetData.getCurrentMana();
        double targetMaxMana = targetData.getMaxMana();
        double targetMissingMana = targetMaxMana - targetCurrentMana;

        // Base mana steal plus bonus based on missing mana
        double totalManaSteal = manaSteal;

        // Cap stolen mana at target's current mana
        totalManaSteal = Math.min(totalManaSteal, targetCurrentMana);

        // Apply mana drain
        targetData.setCurrentMana(targetCurrentMana - totalManaSteal);

        // Give mana to caster
        double casterCurrentMana = casterData.getCurrentMana();
        double casterMaxMana = casterData.getMaxMana();
        casterData.setCurrentMana(Math.min(casterMaxMana, casterCurrentMana + totalManaSteal));

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        target.damage(0.1, caster);

        // Play siphon effect
        playSiphonEffect(caster.getLocation(), target.getLocation());

        // Send messages
        caster.sendMessage(String.format("§5✦ You siphon %.1f mana from %s's soul!", totalManaSteal, target.getName()));
        target.sendMessage(String.format("§5✦ %s siphons %.1f mana from your soul!", caster.getName(), totalManaSteal));

        setSkillSuccess(true);
    }

    private boolean isPvPAllowed(Player caster, Player target) {
        // If WorldGuard integration is available
        if (plugin.getWorldGuardManager() != null) {
            return plugin.getWorldGuardManager().canPvP(caster, target);
        }

        // Fallback to the method in Skill class if it exists
        return !isInNoPvPZone(caster.getLocation()) && !isInNoPvPZone(target.getLocation());
    }

    private boolean isInNoPvPZone(Location location) {
        if (plugin.getWorldGuardManager() != null) {
            return !plugin.getWorldGuardManager().isPvPAllowed(location);
        }
        return false; // Default to allowing PvP if we can't check
    }

    private void playSiphonEffect(Location casterLoc, Location targetLoc) {
        Vector direction = casterLoc.toVector().subtract(targetLoc.toVector()).normalize();
        double distance = casterLoc.distance(targetLoc);

        // Create multiple drain channels
        for (int channel = 0; channel < drainChannels; channel++) {
            final int channelOffset = channel;

            new BukkitRunnable() {
                private double progress = 0;
                private final double speed = 0.2;

                @Override
                public void run() {
                    if (progress >= 1.0) {
                        cancel();
                        return;
                    }

                    // Calculate current position along the path
                    double offset = Math.sin(progress * Math.PI * 2 + (channelOffset * (Math.PI * 2 / drainChannels))) * 0.3;
                    Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).multiply(offset);

                    Location currentLoc = targetLoc.clone().add(
                            direction.clone().multiply(distance * progress)
                    ).add(perpendicular);

                    // Drain particle effect
                    targetLoc.getWorld().spawnParticle(
                            Particle.DUST,
                            currentLoc,
                            1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(128, 0, 128), 1)
                    );

                    // Soul particle effect
                    if (Math.random() < 0.3) {
                        targetLoc.getWorld().spawnParticle(
                                Particle.SOUL,
                                currentLoc,
                                1, 0.1, 0.1, 0.1, 0.02
                        );
                    }

                    progress += speed;
                }
            }.runTaskTimer(plugin, channel * 2L, 1L);
        }
    }
}