package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.damage.DamageType;
import org.bukkit.potion.PotionEffectType;

public class EnchantmentListener implements Listener {
    private final MinecraftMMORPG plugin;

    public EnchantmentListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        if (event.getEntity() instanceof Player) {
            double reduction = calculateProtectionReduction(
                    (Player) event.getEntity(),
                    event.getCause()
            );

            if (reduction > 0) {
                double newDamage = event.getFinalDamage() * (1 - reduction);
                event.setDamage(newDamage);
            }
        }

    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        if (event.getModifiedType() == PotionEffectType.ABSORPTION) {
            if (event.getNewEffect() != null) {
                int amplifier = event.getNewEffect().getAmplifier();
                // 10 HP per level (amplifier + 1 since it's 0-based)
                double absorption = (amplifier + 1) * 10;
                player.setAbsorptionAmount(absorption);
            } else {
                // Effect is being removed
                player.setAbsorptionAmount(0);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            ItemStack weapon = attacker.getInventory().getItemInMainHand();

            double enchantmentDamage = calculateWeaponEnchantments(
                    weapon,
                    event.getEntity()
            );

            if (enchantmentDamage > 0) {
                double newDamage = event.getDamage() + enchantmentDamage;
                event.setDamage(newDamage);
            }
        }

        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        double absorptionAmount = player.getAbsorptionAmount();
        if (absorptionAmount > 0) {
            double damage = event.getFinalDamage();
            double remainingAbsorption = Math.max(0, absorptionAmount - damage);
            double remainingDamage = Math.max(0, damage - absorptionAmount);

            player.setAbsorptionAmount(remainingAbsorption);

            if (remainingDamage > 0) {
                event.setDamage(remainingDamage);
            } else {
                event.setCancelled(true);
            }
        }

    }

    public double calculateWeaponEnchantments(ItemStack weapon, Entity target) {
        if (!weapon.hasItemMeta() || !weapon.getItemMeta().hasEnchants()) {
            return 0.0;
        }

        double totalEnchantDamage = 0.0;

        // Calculate Sharpness damage
        int sharpnessLevel = weapon.getItemMeta().getEnchantLevel(Enchantment.SHARPNESS);
        if (sharpnessLevel > 0) {
            totalEnchantDamage += (0.5 * sharpnessLevel) + 0.5;
        }

        // Calculate Smite damage against undead
        if (isUndead(target)) {
            int smiteLevel = weapon.getItemMeta().getEnchantLevel(Enchantment.SMITE);
            totalEnchantDamage += smiteLevel * 2.5;
        }

        // Calculate Bane of Arthropods damage
        if (isArthropod(target)) {
            int arthropodsLevel = weapon.getItemMeta().getEnchantLevel(Enchantment.BANE_OF_ARTHROPODS);
            totalEnchantDamage += arthropodsLevel * 2.5;
        }

        return totalEnchantDamage;
    }

    public double calculateProtectionReduction(Player player, EntityDamageEvent.DamageCause cause) {
        double totalReduction = 0.0;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;

            // Regular Protection (all damage types)
            int protLevel = armor.getItemMeta().getEnchantLevel(Enchantment.PROTECTION);
            totalReduction += protLevel * 0.04;

            // Specific Protection types based on damage cause
            switch (cause) {
                case FIRE:
                case FIRE_TICK:
                case LAVA:
                    int fireProtLevel = armor.getItemMeta().getEnchantLevel(Enchantment.FIRE_PROTECTION);
                    totalReduction += fireProtLevel * 0.08;
                    break;

                case FALL:
                    int fallProtLevel = armor.getItemMeta().getEnchantLevel(Enchantment.FEATHER_FALLING);
                    totalReduction += fallProtLevel * 0.12;
                    break;

                case PROJECTILE:
                    int projProtLevel = armor.getItemMeta().getEnchantLevel(Enchantment.PROJECTILE_PROTECTION);
                    totalReduction += projProtLevel * 0.08;
                    break;

                case BLOCK_EXPLOSION:
                case ENTITY_EXPLOSION:
                    int blastProtLevel = armor.getItemMeta().getEnchantLevel(Enchantment.BLAST_PROTECTION);
                    totalReduction += blastProtLevel * 0.08;
                    break;
            }
        }

        return Math.min(0.8, totalReduction);
    }

    private boolean isUndead(Entity entity) {
        return entity instanceof Zombie ||
                entity instanceof Skeleton ||
                entity instanceof WitherSkeleton ||
                entity instanceof Phantom ||
                entity instanceof Drowned ||
                entity instanceof Husk ||
                entity instanceof Stray ||
                entity instanceof Zoglin ||
                entity instanceof ZombieVillager ||
                entity instanceof Wither;
    }

    private boolean isArthropod(Entity entity) {
        return entity instanceof Spider ||
                entity instanceof CaveSpider ||
                entity instanceof Silverfish ||
                entity instanceof Endermite ||
                entity instanceof Bee;
    }
}