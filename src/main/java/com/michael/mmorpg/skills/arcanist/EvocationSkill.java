package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.models.PlayerData;

public class EvocationSkill extends Skill {
    private final double manaRestorePercent;
    private final double channelTime;
    private final int ticksPerManaGain;

    public EvocationSkill(ConfigurationSection config) {
        super(config);
        this.manaRestorePercent = config.getDouble("manarestorepercent", 0.8);
        this.channelTime = config.getDouble("channeltime", 5.0);
        this.ticksPerManaGain = config.getInt("tickspermanagain", 5);
    }

    @Override
    protected void performSkill(Player player) {
        Location startLoc = player.getLocation().clone();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        double maxMana = playerData.getMaxMana();
        double manaPerTick = (maxMana * manaRestorePercent) / ((channelTime * 20) / ticksPerManaGain);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(channelTime * 20), 4));

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(channelTime * 20);

            @Override
            public void run() {
                // Check for movement or skill casting
                if (!player.getLocation().equals(startLoc) || player.hasMetadata("casting")) {
                    cancel();
                    player.removePotionEffect(PotionEffectType.SLOWNESS);
                    player.sendMessage("§c✦ Evocation channel broken!");
                    return;
                }

                if (ticks % ticksPerManaGain == 0) {
                    double currentMana = playerData.getCurrentMana();
                    playerData.setCurrentMana(Math.min(maxMana, currentMana + manaPerTick));
                }

                // Visual effects
                player.getWorld().spawnParticle(
                        Particle.INSTANT_EFFECT,
                        player.getLocation().add(0, 1, 0),
                        3, 0.3, 0.5, 0.3, 0.05
                );

                if (++ticks >= maxTicks) {
                    player.sendMessage("§b✦ Evocation completed!");
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}