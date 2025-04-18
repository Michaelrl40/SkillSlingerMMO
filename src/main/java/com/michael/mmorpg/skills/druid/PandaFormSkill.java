package com.michael.mmorpg.skills.druid;

import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.watchers.PandaWatcher;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.jetbrains.annotations.NotNull;

public class PandaFormSkill extends DruidShapeshiftSkill {
    // Configuration values for the form's bonuses
    private final int strengthBonus;      // Strength amplifier
    private final int healthBonus;        // Additional health hearts
    private final double defenseBonus;    // Damage reduction percentage
    private final int movementPenalty;    // Slowness level as trade-off
    private double originalAttackSpeed;      // Store original attack speed
    private final double scaleMultiplier = 2.0;

    public PandaFormSkill(ConfigurationSection config) {
        super(config);
        // Load custom values from config, with sensible defaults
        this.strengthBonus = config.getInt("strengthbonus", 1);     // Strength I by default
        this.healthBonus = config.getInt("healthbonus", 6);         // +3 hearts by default
        this.defenseBonus = config.getDouble("defensebonus", 0.2);  // 20% damage reduction
        this.movementPenalty = config.getInt("movementpenalty", 0); // Slowness I by default
    }

    @Override
    protected void performSkill(Player player) {

    }

    @Override
    protected Disguise createDisguise() {
        // Create the base panda disguise
        MobDisguise disguise = new MobDisguise(DisguiseType.PANDA);

        // Customize the panda's appearance
        PandaWatcher watcher = (PandaWatcher) disguise.getWatcher();
        // Make the panda look strong to match its effects
        watcher.setMainGene(org.bukkit.entity.Panda.Gene.AGGRESSIVE);

        return disguise;
    }

    @Override
    protected void setupDisguise(Disguise disguise) {
        // Any additional disguise setup can go here
    }

    @Override
    protected void applyFormEffects(Player player) {
        // Apply existing effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, strengthBonus, false, false));

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                Integer.MAX_VALUE, movementPenalty, false, false));

        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, (int)(defenseBonus * 2), false, false));

        // Increase max health
        AttributeInstance healthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute != null) {
            double baseHealth = healthAttribute.getBaseValue();
            healthAttribute.setBaseValue(baseHealth + (healthBonus * 2));
            player.setHealth(player.getHealth() + (healthBonus * 2));
        }

        // Add scale modifier
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) {
            double baseScale = scaleAttribute.getBaseValue();
            scaleAttribute.setBaseValue(baseScale * scaleMultiplier); // Define scaleMultiplier as a class field
        }
    }

    @Override
    protected void removeFormEffects(Player player) {
        // Remove all applied effects
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.RESISTANCE);

        // Reset scale
        AttributeInstance scaleAttribute = player.getAttribute(Attribute.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(1.0); // Reset to default scale
        }

    }
}