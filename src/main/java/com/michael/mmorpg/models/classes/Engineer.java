package com.michael.mmorpg.models.classes;

import com.michael.mmorpg.models.GameClass;
import org.bukkit.configuration.ConfigurationSection;

public class Engineer extends GameClass {
    public Engineer(ConfigurationSection config) {
        super(config);
    }

    @Override
    public double getStaminaRegenPercent() {
        return staminaRegenPercent * 1;
    }

    @Override
    public double getManaRegenPercent() {
        return manaRegenPercent * 1;
    }


}