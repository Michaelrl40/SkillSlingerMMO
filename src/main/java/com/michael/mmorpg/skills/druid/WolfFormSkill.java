package com.michael.mmorpg.skills.druid;

import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.WolfWatcher;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class WolfFormSkill extends DruidShapeshiftSkill {
    private final int speedBonus;
    private final int jumpBonus;

    public WolfFormSkill(ConfigurationSection config) {
        super(config);
        this.speedBonus = config.getInt("speedbonus", 1);
        this.jumpBonus = config.getInt("jumpbonus", 1);
    }

    @Override
    protected void performSkill(Player player) {

    }

    @Override
    protected Disguise createDisguise() {
        MobDisguise disguise = new MobDisguise(DisguiseType.WOLF);
        disguise.setModifyBoundingBox(false);
        disguise.setViewSelfDisguise(true);
        WolfWatcher watcher = (WolfWatcher) disguise.getWatcher();
        watcher.setInvisible(false);
        return disguise;
    }

    @Override
    protected void setupDisguise(Disguise disguise) {
        WolfWatcher watcher = (WolfWatcher) disguise.getWatcher();
        watcher.setTamed(true);
        watcher.setAngry(false);
    }

    @Override
    protected void applyFormEffects(Player player) {
        // Set collision handling through ActionBarManager


        // Apply wolf form benefits
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                Integer.MAX_VALUE, speedBonus, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                Integer.MAX_VALUE, jumpBonus, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    protected void removeFormEffects(Player player) {
        // Restore collision through ActionBarManager

        // Remove all form-specific effects
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

}