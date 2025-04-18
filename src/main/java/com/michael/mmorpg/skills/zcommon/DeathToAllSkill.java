package com.michael.mmorpg.skills.zcommon;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class DeathToAllSkill extends Skill {

    private final int castDelay;
    private final int beamCount;

    public DeathToAllSkill(ConfigurationSection config) {
        super(config);
        this.castDelay = config.getInt("castDelay", 5);
        this.beamCount = config.getInt("beamCount", 50);
    }

    @Override
    protected void performSkill(Player player) {
        // This is an easter egg skill for level 1000
        // Make sure the player is actually at that level (double-check)
        int playerLevel = plugin.getPlayerManager().getPlayerData(player).getLevel();
        if (playerLevel < 1000) {
            player.sendMessage("§c✦ You are not worthy of this power!");
            setSkillSuccess(false);
            return;
        }

        // Broadcast an ominous warning
        Bukkit.broadcastMessage("§4§l" + player.getName() + " IS CASTING DEATH TO ALL!");
        Bukkit.broadcastMessage("§4§lTHE END IS NEAR!");

        // Play apocalyptic sounds to everyone
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        }

        // Cast animation - create a beam of light from the sky
        createBeamEffect(player.getLocation(), player.getWorld());

        // Schedule the apocalypse with a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                executeDeathToAll(player);
            }
        }.runTaskLater(plugin, castDelay * 20L); // Convert seconds to ticks

        setSkillSuccess(true);
    }

    private void createBeamEffect(Location center, World world) {
        // Create multiple beams of light for a dramatic effect
        for (int i = 0; i < beamCount; i++) {
            final int beamIndex = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Calculate random offset from center
                    double offsetX = (Math.random() - 0.5) * 40;
                    double offsetZ = (Math.random() - 0.5) * 40;
                    Location beamLoc = center.clone().add(offsetX, 0, offsetZ);

                    // Create particles from sky to ground
                    for (double y = 0; y < 256; y += 1.5) {
                        Location particleLoc = beamLoc.clone();
                        particleLoc.setY(y);

                        // Red beam of destruction
                        world.spawnParticle(
                                Particle.DUST,
                                particleLoc,
                                3, 0.2, 0.2, 0.2, 0,
                                new Particle.DustOptions(Color.RED, 2.0f)
                        );
                    }

                    // Add impact effect at ground level
                    world.spawnParticle(
                            Particle.EXPLOSION,
                            beamLoc,
                            3, 0.5, 0.5, 0.5, 0.1
                    );

                    world.playSound(beamLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f + (float)Math.random() * 0.5f);
                }
            }.runTaskLater(plugin, (i * 3) + (int)(Math.random() * 10));
        }
    }

    private void executeDeathToAll(Player caster) {
        // Get all online players
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

        // Dramatic pause
        Bukkit.broadcastMessage("§4§lDEATH TO ALL!");

        // Kill every player except the caster (if you want to spare the caster)
        for (Player target : onlinePlayers) {
            if (target.equals(caster)) {
                // Skip the caster if you want them to survive
                // Uncomment to kill the caster too: target.setHealth(0);
                continue;
            }

            // Final visual effect at the target
            target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1, 0, 0, 0, 0);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);

            // Apply the kill - this ensures death even with protection effects
            // Note: You might want to disable this in certain game modes or for admins
            target.setHealth(0);
        }

        // Final message
        Bukkit.broadcastMessage("§4§l" + caster.getName() + " has purged the server!");
    }
}