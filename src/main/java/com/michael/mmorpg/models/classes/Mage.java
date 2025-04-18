package com.michael.mmorpg.models.classes;

import com.michael.mmorpg.models.GameClass;
import org.bukkit.configuration.ConfigurationSection;

public class Mage extends GameClass {
    public Mage(ConfigurationSection config) {
        super(config);
    }

    @Override
    public double getStaminaRegenPercent() {
        // Warriors have better stamina regeneration
        return staminaRegenPercent * 1; // 50% bonus
    }

    @Override
    public double getManaRegenPercent() {
        return manaRegenPercent * 1;
    }


}