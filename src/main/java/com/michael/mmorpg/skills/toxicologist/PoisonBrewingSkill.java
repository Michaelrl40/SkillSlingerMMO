package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.michael.mmorpg.models.PlayerData;

public class PoisonBrewingSkill extends Skill {
    private final int poisonDuration;
    private final int poisonAmplifier;

    public PoisonBrewingSkill(ConfigurationSection config) {
        super(config);
        this.poisonDuration = config.getInt("poisonDuration", 200);  // 10 seconds (200 ticks)
        this.poisonAmplifier = config.getInt("poisonAmplifier", 0);  // Poison I (0 = level 1)
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
        if (data == null) return;

        // Check if player has enough toxin
        if (data.getCurrentToxin() < toxinCost) {
            player.sendMessage("§c☠ Not enough toxin to brew poison!");
            return;
        }

        // Check if player has room in inventory
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c☠ Your inventory is full!");
            return;
        }

        // Create the splash poison potion
        ItemStack poisonPotion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) poisonPotion.getItemMeta();

        // Add poison effect to the potion
        meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, poisonAmplifier), true);
        meta.setDisplayName("§2Toxicologist's Poison");
        poisonPotion.setItemMeta(meta);

        // Use toxin
        data.setCurrentToxin(data.getCurrentToxin() - toxinCost);

        // Give potion to player
        player.getInventory().addItem(poisonPotion);

        // Visual and sound effects for brewing
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0F, 1.0F);
        player.getWorld().spawnParticle(
                Particle.WITCH,
                player.getLocation().add(0, 1, 0),
                20, 0.3, 0.3, 0.3, 0.1
        );

        player.sendMessage("§2☠ You brewed a splash poison potion!");
        setSkillSuccess(true);
    }
}