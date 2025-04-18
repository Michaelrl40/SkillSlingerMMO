package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.handlers.ShieldHandler;
import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.skills.guardian.MagicWardSkill;
import com.michael.mmorpg.skills.hunter.PiercingArrowSkill;
import com.michael.mmorpg.skills.ninja.BackstabPassive;
import com.michael.mmorpg.skills.renegade.FivePointPalmSkill;
import com.michael.mmorpg.skills.toxicologist.StealthSkill;
import com.michael.mmorpg.status.CCType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class CombatListener implements Listener {
    private final MinecraftMMORPG plugin;
    private final ShieldHandler shieldHandler;

    public CombatListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.shieldHandler = plugin.getShieldHandler();

    }

    private boolean isCombatableEntity(Entity entity) {
        return entity instanceof Player ||
                entity instanceof Monster ||
                entity instanceof Slime ||
                entity instanceof Flying ||
                entity instanceof EnderDragon ||
                entity instanceof Wither ||
                (entity instanceof Animals && !(entity instanceof Tameable)) ||
                entity instanceof Golem;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity damaged = event.getEntity();
        boolean isCombat = isCombatableEntity(damaged);

        // Get initial source player
        Player source = getSourcePlayer(damager);


        // Check for absorption shield first (before any other processing)
        if (damaged instanceof Player) {
            Player player = (Player) damaged;
            if (player.hasMetadata("absorption_shield_amount")) {
                double shieldAmount = player.getMetadata("absorption_shield_amount").get(0).asDouble();

                if (shieldAmount > 0) {
                    // Apply damage to shield and get remaining damage
                    double damage = event.getFinalDamage();
                    double remainingDamage = plugin.getAbsorptionShieldManager().damageShield(player, damage);

                    if (remainingDamage <= 0) {
                        // Shield absorbed all damage
                        event.setCancelled(true);
                        return; // Skip all further processing
                    } else {
                        // Update damage to remaining after shield absorption
                        event.setDamage(remainingDamage);
                    }
                }
            }
        }

        // Check for skill damage first
        if (damaged.hasMetadata("skill_damage")) {
            handleSkillDamage(event, damaged);
            return; // Skip all other damage processing
        }

        // Handle party protection and self-damage
        if (handleProtectionChecks(event, source, damaged)) {
            return; // Event was cancelled
        }

        // Only handle weapon damage if there's no skill damage
        if (damager instanceof Player) {
            handleWeaponDamage((Player) damager, event, isCombat);
        } else if (damager instanceof Projectile && source != null) {
            handleProjectileDamage(source, event, isCombat);
        }

        if (damaged instanceof Player) {
            Player player = (Player) damaged;
            if (player.hasMetadata("bulwark_reduction")) {
                double reduction = player.getMetadata("bulwark_reduction").get(0).asDouble();
                event.setDamage(event.getDamage() * (1 - reduction));
            }
        }

        // Handle Five Point Palm auto-attack enhancement
        if (damager instanceof Player && damaged instanceof LivingEntity) {
            Player player = (Player) damager;
            if (player.hasMetadata("five_point_palm_buff")) {
                FivePointPalmSkill.onPlayerHit(player, (LivingEntity) damaged);
            }
        }
        //guts method
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.hasMetadata("guts_active")) {
                double damage = event.getFinalDamage();
                double currentHealth = player.getHealth();

                if (currentHealth - damage <= 1.0) {
                    // Prevent death by setting health to 1
                    event.setDamage(currentHealth - 1.0);

                    // Visual feedback
                    player.getWorld().spawnParticle(
                            Particle.TOTEM_OF_UNDYING,
                            player.getLocation().add(0, 1, 0),
                            15, 0.3, 0.5, 0.3, 0.1
                    );
                    player.getWorld().playSound(
                            player.getLocation(),
                            Sound.ITEM_TOTEM_USE,
                            0.5f,
                            1.2f
                    );
                }
            }
        }

        if (event.getEntity().hasMetadata("pinch_damage")) {
            event.setCancelled(true);
            if (event.getDamage() > 0) {
                LivingEntity entity = (LivingEntity) event.getEntity();
                double newHealth = Math.max(0, entity.getHealth() - event.getDamage());
                entity.setHealth(newHealth);
            }
            return;
        }

        // Update combat states
        updateCombatStates(source, damaged, isCombat, event);
    }

    private void handleSkillDamage(EntityDamageByEntityEvent event, Entity damaged) {
        double damage = 0;
        Player attacker = null;

        // Get skill damage from metadata
        if (damaged.hasMetadata("skill_damage_amount")) {
            damage = damaged.getMetadata("skill_damage_amount").get(0).asDouble();
        }

        // Get the skill source (attacker)
        if (damaged.hasMetadata("skill_damage")) {
            attacker = (Player) damaged.getMetadata("skill_damage").get(0).value();
        }

        // NEW CODE: Check WorldGuard protection if both are players
        if (attacker != null && damaged instanceof Player) {
            if (!isPvPAllowed(attacker, damaged)) {
                event.setCancelled(true);
                if (attacker.isOnline()) {
                    attacker.sendMessage("§c✦ PvP is disabled in this region!");
                }
                return; // Skip all further processing
            }
        }

        if (attacker != null) {
            handleGoredrinkerHealing(attacker, damage);
        }

        // Check damage types
        boolean isMagicDamage = damaged.hasMetadata("magic_damage");
        boolean isTrueDamage = damaged.hasMetadata("true_damage");

        // Calculate final damage based on type
        double finalDamage = damage;
        if (!isTrueDamage && !isMagicDamage) {
            double armorDamage = Math.abs(calculateArmorDamage(damage, damaged));
            finalDamage = damage - armorDamage;
        }


        if (event.getEntity() instanceof Player && event.getEntity().hasMetadata("magic_damage")) {
            Player defender = (Player) event.getEntity();
            // Apply Magic Ward reduction if active
            event.setDamage(MagicWardSkill.reduceMagicDamage(defender, event.getDamage()));
        }

        // Check for Guts effect with final calculated damage
        if (damaged instanceof Player) {
            Player target = (Player) damaged;
            if (target.hasMetadata("guts_active")) {
                double currentHealth = target.getHealth();
                if (currentHealth - finalDamage <= 1.0) {
                    // Recalculate damage to leave 1 HP
                    double newDamage = currentHealth - 1.0;
                    event.setCancelled(true);
                    target.setHealth(Math.max(1.0, currentHealth - newDamage));

                    // Visual feedback
                    target.getWorld().spawnParticle(
                            Particle.TOTEM_OF_UNDYING,
                            target.getLocation().add(0, 1, 0),
                            15, 0.3, 0.5, 0.3, 0.1
                    );
                    target.getWorld().playSound(
                            target.getLocation(),
                            Sound.ITEM_TOTEM_USE,
                            0.5f,
                            1.2f
                    );

                    // Update damage for display
                    finalDamage = newDamage;
                    damage = newDamage;
                }
            }
        }

        // Apply the damage if event wasn't cancelled
        if (!event.isCancelled()) {
            // Set base damage
            event.setDamage(EntityDamageEvent.DamageModifier.BASE, damage);

            try {
                if (isTrueDamage) {
                    // True damage bypasses ALL protection
                    event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
                    event.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);
                    event.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION, 0);
                    event.setDamage(EntityDamageEvent.DamageModifier.RESISTANCE, 0);
                    event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
                } else if (isMagicDamage) {
                    // Magic damage only bypasses armor
                    event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
                } else {
                    // Physical damage respects armor
                    double armorDamage = calculateArmorDamage(damage, damaged);
                    event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, armorDamage);
                }
            } catch (Exception ignored) {}

            // Update combat state and show damage numbers
            if (attacker != null && damaged instanceof LivingEntity) {
                LivingEntity livingTarget = (LivingEntity) damaged;
                plugin.getCombatManager().enterCombat(attacker, livingTarget);

                if (damaged instanceof Player) {
                    plugin.getCombatManager().enterCombat((Player) damaged, attacker);
                }

                // Display damage number with appropriate type
                if (!damaged.hasMetadata("damage_displayed")) {
                    DamageDisplayManager.DamageType displayType;
                    if (isTrueDamage) {
                        displayType = DamageDisplayManager.DamageType.TRUE;
                    } else if (isMagicDamage) {
                        displayType = DamageDisplayManager.DamageType.MAGIC;
                    } else {
                        displayType = DamageDisplayManager.DamageType.NORMAL;
                    }

                    plugin.getDamageDisplayManager().spawnDamageDisplay(
                            damaged.getLocation(),
                            finalDamage,
                            displayType
                    );
                    damaged.setMetadata("damage_displayed", new FixedMetadataValue(plugin, true));
                }
            }
        }

        // Clean up metadata
        damaged.removeMetadata("skill_damage", plugin);
        damaged.removeMetadata("skill_damage_amount", plugin);
        damaged.removeMetadata("magic_damage", plugin);
        damaged.removeMetadata("true_damage", plugin);
        damaged.removeMetadata("damage_displayed", plugin);
    }


    private boolean handleProtectionChecks(EntityDamageByEntityEvent event, Player source, Entity damaged) {
        if (source != null) {
            // Check for PvP combat situation
            if (damaged instanceof Player targetPlayer) {
                // Use the WorldGuardManager to check if combat is allowed based on flag and combat status
                if (!plugin.getWorldGuardManager().canPvP(source, damaged)) {
                    // The check already considers combat status, so if it returns false, we should cancel
                    event.setCancelled(true);
                    source.sendMessage("§c✦ Combat is not allowed in this region!");
                    return true;
                }
            }

            // Check party protection
            Party attackerParty = plugin.getPartyManager().getParty(source);
            if (attackerParty != null && attackerParty.shouldPreventInteraction(source, damaged, true)) {
                event.setCancelled(true);
                source.sendMessage("§c✦ You cannot harm party members!");
                return true;
            }

            // Prevent self-damage
            if (damaged.equals(source)) {
                event.setCancelled(true);
                return true;
            }
        }
        return false;
    }

    private void handleWeaponDamage(Player attacker, EntityDamageByEntityEvent event, boolean isCombat) {
        // Skip if this is a skill-based attack
        if (event.getEntity().hasMetadata("skill_damage")) return;

        PlayerData playerData = plugin.getPlayerManager().getPlayerData(attacker);
        if (playerData == null || !playerData.hasClass()) return;

        // Handle crowd control effects
        if (handleCrowdControlEffects(attacker, event)) {
            return;
        }

        // Handle weapon damage only if it's not a skill attack
        if (isCombat) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            String weaponType = weapon.getType().toString().toUpperCase();

            if (isClassWeapon(weaponType)) {
                if (!plugin.getClassManager().canUseWeapon(attacker, weapon)) {
                    event.setCancelled(true);
                    attacker.sendMessage("§c§l⚔ You are not proficient with this weapon in combat!");
                    return;
                }

                if (plugin.getWeaponManager().isConfiguredWeapon(weapon)) {
                    // Get base weapon damage from your system
                    double baseDamage = plugin.getWeaponManager().getWeaponDamage(weapon);

                    // Get the attack cooldown multiplier (0.0 to 1.0)
                    double cooldownMultiplier = attacker.getAttackCooldown();

                    // Apply cooldown multiplier to base damage
                    double damageWithCooldown = baseDamage * cooldownMultiplier;

                    // Get Minecraft's built-in damage multiplier from strength potions
                    double vanillaMultiplier = calculateVanillaStrengthMultiplier(attacker);

                    // Apply potion multiplier after cooldown calculation
                    double finalDamage = damageWithCooldown * vanillaMultiplier;

                    // Determine damage type for display
                    DamageDisplayManager.DamageType displayType;

                    // If they're spam clicking (cooldown < threshold), reduce damage significantly
                    if (cooldownMultiplier < 0.85) {
                        finalDamage *= 0.5;  // Reduce damage by 50% for spam clicking
                        displayType = DamageDisplayManager.DamageType.NORMAL; // Use normal display for weak hits
                        // Visual feedback for weak hits
                        attacker.playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                        attacker.spawnParticle(Particle.SMOKE, attacker.getLocation().add(0, 1, 0),
                                5, 0.2, 0.2, 0.2, 0.05);
                    } else {
                        // Full power hit gets critical display
                        displayType = DamageDisplayManager.DamageType.CRITICAL;
                    }

                    // Dead Man Walking skill code
                    if (plugin.getDeadManManager().hasValidTarget(attacker)) {
                        LivingEntity target = plugin.getDeadManManager().getMarkedTarget(attacker);
                        if (event.getEntity().equals(target)) {
                            finalDamage *= 1.5; // 50% increase
                            displayType = DamageDisplayManager.DamageType.TRUE; // Use true damage display for empowered hits
                            attacker.sendMessage("§6✦ Dead Man's Walking empowers your strike! (" +
                                    String.format("%.1f", finalDamage) + " damage)");
                        }
                    }

                    // Check for backstab
                    if (playerData.getGameClass().getName().equalsIgnoreCase("Ninja")) {
                        if (BackstabPassive.isBackstab(attacker, event.getEntity())) {
                            // Get the backstab multiplier from config
                            Skill backstabSkill = plugin.getSkillManager().getSkillInstance("backstab");
                            double backstabMultiplier = 2.0; // Default
                            if (backstabSkill instanceof BackstabPassive) {
                                backstabMultiplier = ((BackstabPassive) backstabSkill).getDamageMultiplier();
                            }

                            // Apply backstab multiplier directly to final damage
                            finalDamage *= backstabMultiplier;
                            displayType = DamageDisplayManager.DamageType.TRUE; // Show as true damage

                            // Visual and sound effects
                            event.getEntity().getWorld().spawnParticle(
                                    Particle.CRIT,
                                    event.getEntity().getLocation().add(0, 1, 0),
                                    15, 0.3, 0.3, 0.3, 0.2
                            );
                            event.getEntity().getWorld().playSound(
                                    event.getEntity().getLocation(),
                                    Sound.ENTITY_PLAYER_ATTACK_CRIT,
                                    1.0f,
                                    1.5f
                            );
                            attacker.sendMessage("§c✦ Backstab! (" + String.format("%.1f", finalDamage) + " damage)");
                        }
                    }

                    // Add War Chant buff check here
                    if (attacker.hasMetadata("war_chant_buff")) {
                        double bonus = attacker.getMetadata("war_chant_buff").get(0).asDouble();
                        finalDamage *= (1 + bonus); // Increase damage by bonus percentage
                    }


                    // Generate rage for Berserker class if it's a melee weapon
                    if (playerData.getGameClass().getName().equalsIgnoreCase("Berserker") &&
                            (weaponType.endsWith("_SWORD") || weaponType.endsWith("_AXE"))) {
                        // Base rage generation - more for axes, less for swords
                        double baseRageGen = weaponType.endsWith("_AXE") ? 3.0 : 2.0;

                        // Bonus rage based on damage dealt (0.5 rage per point of damage)
                        double damageRageBonus = finalDamage * 0.5;

                        // Total rage to generate
                        double rageGenerated = Math.min(baseRageGen + damageRageBonus, 4.0); // Cap per hit

                        // Add rage through PlayerData system
                        playerData.addRage(rageGenerated);
                    }

                    // Set the different damage categories
                    // This ensures armor is properly applied
                    event.setDamage(EntityDamageEvent.DamageModifier.BASE, finalDamage);

                    // Let Minecraft handle armor calculations
                    // This will automatically apply armor damage reduction
                    try {
                        double armorDamage = event.getDamage(EntityDamageEvent.DamageModifier.ARMOR);
                        double finalArmorDamage = calculateArmorDamage(finalDamage, event.getEntity());
                        event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, finalArmorDamage);
                    } catch (Exception ignored) {}

                    // Calculate the final damage after armor
                    double armorDamageReduction = Math.abs(calculateArmorDamage(finalDamage, event.getEntity()));
                    double damageAfterArmor = finalDamage - armorDamageReduction;

                    // Check for Guts effect
                    if (event.getEntity() instanceof Player) {
                        Player target = (Player) event.getEntity();
                        if (target.hasMetadata("guts_active")) {
                            if (target.getHealth() - damageAfterArmor <= 1.0) {
                                damageAfterArmor = target.getHealth() - 1.0;
                                finalDamage = damageAfterArmor + armorDamageReduction;
                                event.setDamage(EntityDamageEvent.DamageModifier.BASE, finalDamage);

                                // Visual feedback
                                target.getWorld().spawnParticle(
                                        Particle.TOTEM_OF_UNDYING,
                                        target.getLocation().add(0, 1, 0),
                                        15, 0.3, 0.5, 0.3, 0.1
                                );
                                target.getWorld().playSound(
                                        target.getLocation(),
                                        Sound.ITEM_TOTEM_USE,
                                        0.5f,
                                        1.2f
                                );
                            }
                        }
                    }

                    // Spawn damage display
                    plugin.getDamageDisplayManager().spawnDamageDisplay(
                            event.getEntity().getLocation(),
                            damageAfterArmor,
                            displayType
                    );

                    // Check for Goredrinker healing
                    if (attacker.hasMetadata("goredrinker_active")) {
                        double healingPercent = attacker.getMetadata("goredrinker_active").get(0).asDouble();
                        double healing = damageAfterArmor * healingPercent;

                        // Apply healing
                        playerData.regenHealth(healing);

                        // Spawn healing number
                        plugin.getDamageDisplayManager().spawnDamageDisplay(
                                attacker.getLocation(),
                                healing,
                                DamageDisplayManager.DamageType.HEALING
                        );

                        // Healing visual effect
                        Location healLoc = attacker.getLocation().add(0, 1, 0);
                        attacker.getWorld().spawnParticle(
                                Particle.HEART,
                                healLoc,
                                1, 0.2, 0.2, 0.2, 0
                        );
                    }
                }
            }
        }
    }

    private double calculateArmorDamage(double damage, Entity entity) {
        if (!(entity instanceof LivingEntity)) return 0;
        LivingEntity living = (LivingEntity) entity;

        // Get armor value (0-20)
        double armorPoints = living.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue();
        // Get armor toughness (primarily from diamond/netherite)
        double toughness = living.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS).getValue();

        // Minecraft's armor damage reduction formula
        double damageReduction = 1 - Math.min(20, Math.max(armorPoints/5, armorPoints - damage/(2 + toughness/4)))/25;

        return -(damage * (1 - damageReduction));
    }

    /**
     * Calculates the damage multiplier from strength potions using Minecraft's formula
     * Level 0 = 1.3x damage
     * Level 1 = 1.6x damage
     * Level 2 = 1.9x damage
     * and so on, adding 0.3 per level
     */
    private double calculateVanillaStrengthMultiplier(Player player) {
        // Check if player has strength effect
        if (player.hasPotionEffect(PotionEffectType.STRENGTH)) {
            PotionEffect strength = player.getPotionEffect(PotionEffectType.STRENGTH);
            if (strength != null) {
                // Minecraft's strength formula: 1.3 + (0.3 * level)
                return 1.0 + (0.3 * (strength.getAmplifier() + 1));
            }
        }
        return 1.0; // No strength effect
    }


    private double calculateVanillaResistanceMultiplier(Player player) {
        // Check if player has resistance effect
        if (player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
            PotionEffect resistance = player.getPotionEffect(PotionEffectType.RESISTANCE);
            if (resistance != null) {
                // Minecraft's resistance formula: 20% reduction per level
                int level = resistance.getAmplifier() + 1; // Convert to 1-based level
                return Math.max(0, 1.0 - (0.2 * level)); // Cap at 100% reduction
            }
        }
        return 1.0; // No resistance effect
    }

    private void handleProjectileDamage(Player shooter, EntityDamageByEntityEvent event, boolean isCombat) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(shooter);
        if (playerData == null || !playerData.hasClass()) {
            event.setCancelled(true);
            return;
        }

        if (isCombat) {
            Projectile projectile = (Projectile) event.getDamager();

            // Handle bow/arrow damage
            if (projectile instanceof Arrow) {
                // Check if player can use bow or crossbow
                if (!playerData.getGameClass().getAllowedWeapons().contains("BOW") &&
                        !playerData.getGameClass().getAllowedWeapons().contains("CROSSBOW")) {
                    event.setCancelled(true);
                    shooter.sendMessage("§c✦ Your class cannot use bows or crossbows in combat!");
                    return;
                }


                // Check for Piercing Arrow buff
                if (shooter.hasMetadata("piercing_arrow_buff")) {
                    // Get and apply the stored damage value
                    double piercingDamage = shooter.getMetadata("piercing_arrow_damage").get(0).asDouble();

                    // Set true damage metadata
                    event.getEntity().setMetadata("true_damage", new FixedMetadataValue(plugin, true));
                    event.getEntity().setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, piercingDamage));

                    // Allow arrow to continue flight
                    Arrow arrow = (Arrow) projectile;
                    arrow.setVelocity(arrow.getVelocity().multiply(0.8));

                    // Display true damage number
                    plugin.getDamageDisplayManager().spawnDamageDisplay(
                            event.getEntity().getLocation(),
                            piercingDamage,
                            DamageDisplayManager.DamageType.TRUE
                    );

                    // Decrement the remaining shots
                    PiercingArrowSkill.decrementShots(shooter, plugin);
                    return; // Skip normal arrow damage calculation
                }

                // Normal arrow damage calculation
                double baseDamage = plugin.getWeaponManager().getProjectileDamage("ARROW");

                // Calculate final damage based on draw strength
                Arrow arrow = (Arrow) projectile;
                float drawStrength = (float) Math.min(1.0f, arrow.getVelocity().length() / 3.0f);

                // Apply draw strength scaling
                double finalDamage = baseDamage * drawStrength;

                // Apply critical hit if fully drawn
                if (drawStrength > 0.9f) {
                    double critMultiplier = 2.0; // Default crit multiplier
                    // Try to get configured crit multiplier if it exists
                    try {
                        ConfigurationSection arrowConfig = plugin.getConfig().getConfigurationSection("weapons.ARROW");
                        if (arrowConfig != null) {
                            critMultiplier = arrowConfig.getDouble("critMultiplier", 2.0);
                        }
                    } catch (Exception ignored) {}

                    finalDamage *= critMultiplier;
                }

                // Set the damage
                event.setDamage(EntityDamageEvent.DamageModifier.BASE, finalDamage);

                // Apply armor calculations
                try {
                    double armorDamage = calculateArmorDamage(finalDamage, event.getEntity());
                    event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, armorDamage);

                    // Calculate display damage after armor
                    double damageAfterArmor = finalDamage - Math.abs(armorDamage);

                    // Display damage number
                    plugin.getDamageDisplayManager().spawnDamageDisplay(
                            event.getEntity().getLocation(),
                            damageAfterArmor,
                            DamageDisplayManager.DamageType.NORMAL
                    );
                } catch (Exception ignored) {}

            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow) || event.getHitEntity() == null) {
            return;
        }

        Arrow arrow = (Arrow) event.getEntity();

        if (!(arrow.getShooter() instanceof Player)) {
            return;
        }

        Player shooter = (Player) arrow.getShooter();

        // Check if this is a piercing arrow
        if (shooter.hasMetadata("piercing_arrow_buff")) {
            // Don't cancel the event - let damage apply
            LivingEntity hitEntity = (LivingEntity) event.getHitEntity();

            // Schedule arrow continuation
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (arrow.isValid() && !arrow.isDead()) {
                        // Keep the arrow moving
                        Vector currentVelocity = arrow.getVelocity();
                        if (currentVelocity.length() > 0.2) {
                            arrow.setVelocity(currentVelocity.multiply(0.8));

                            // Visual effects
                            arrow.getWorld().spawnParticle(
                                    Particle.DRAGON_BREATH,
                                    arrow.getLocation(),
                                    10, 0.1, 0.1, 0.1, 0.05
                            );
                        } else {
                            arrow.remove();
                        }
                    }
                }
            }.runTaskLater(plugin, 1L);

            // After applying damage, decrement shots
            PiercingArrowSkill.decrementShots(shooter, plugin);
        }
    }

    private boolean handleCrowdControlEffects(Player player, EntityDamageByEntityEvent event) {
        if (plugin.getStatusEffectManager().hasEffect(player, CCType.STUN) ||
                plugin.getStatusEffectManager().hasEffect(player, CCType.SLEEP)) {
            event.setCancelled(true);
            return true;
        }

        if (plugin.getStatusEffectManager().hasEffect(player, CCType.BLIND)) {
            if (Math.random() < 1.0) {
                event.setCancelled(true);
                player.sendMessage("§c👁 Your attack missed due to blindness!");
                return true;
            }
        }
        return false;
    }

    private void updateCombatStates(Player source, Entity damaged, boolean isCombat, EntityDamageByEntityEvent event) {
        // Skip combat state updates if the event was cancelled
        if (!isCombat || event.isCancelled()) return;

        // Handle Player getting hit by anything
        if (damaged instanceof Player damagedPlayer) {
            Entity attacker = source != null ? source : event.getDamager();
            if (attacker instanceof LivingEntity livingAttacker) {
                // Skip combat if they're in the same party
                Party attackerParty = plugin.getPartyManager().getParty(source);
                if (attackerParty == null || !attackerParty.isMember(damagedPlayer)) {
                    plugin.getCombatManager().enterCombat(damagedPlayer, livingAttacker);
                }
            }
        }

        // Handle Player hitting something
        if (source != null && damaged instanceof LivingEntity livingTarget) {
            // Skip combat if they're in the same party
            if (livingTarget instanceof Player) {
                Party attackerParty = plugin.getPartyManager().getParty(source);
                if (attackerParty == null || !attackerParty.isMember((Player)livingTarget)) {
                    plugin.getCombatManager().enterCombat(source, livingTarget);
                }
            } else {
                // Not a player, so enter combat normally
                plugin.getCombatManager().enterCombat(source, livingTarget);
            }
        }
    }

    private Player getSourcePlayer(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        } else if (damager instanceof Projectile) {
            Projectile projectile = (Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }

    private boolean isClassWeapon(String weaponType) {
        return weaponType.endsWith("_SWORD") ||
                weaponType.endsWith("_AXE") ||
                weaponType.endsWith("_HOE") ||
                weaponType.equals("BOW") ||
                weaponType.startsWith("MUSIC_DISC_") ||
                weaponType.equals("CROSSBOW") ||
                weaponType.equals("STICK") ||
                weaponType.equals("BLAZE_ROD") ||
                weaponType.equals("SNOWBALL") ||
                weaponType.endsWith("_SHOVEL")||
                weaponType.equals("PAPER") ||
                weaponType.equals("SHIELD");
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        // Early return if no target was hit
        if (!(event.getHitEntity() instanceof LivingEntity)) {
            return;
        }

        Projectile projectile = event.getEntity();
        LivingEntity target = (LivingEntity) event.getHitEntity();

        if (projectile.hasMetadata("resonating_strike") && event.getHitEntity() instanceof LivingEntity) {
            Player shooter = (Player) projectile.getShooter();

            // Store shooter's original location for effects
            Location originalLoc = shooter.getLocation().clone();

            // Calculate teleport location (slightly behind the target)
            Vector direction = target.getLocation().getDirection();
            Location teleportLoc = target.getLocation().clone().subtract(direction.multiply(1.5));
            teleportLoc.setYaw(target.getLocation().getYaw() + 180); // Face the target

            // Departure effects
            shooter.getWorld().spawnParticle(
                    Particle.WITCH,
                    originalLoc,
                    30, 0.3, 0.5, 0.3, 0.05
            );
            shooter.getWorld().playSound(originalLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);

            // Perform teleport
            shooter.teleport(teleportLoc);

            // Arrival effects
            shooter.getWorld().playSound(teleportLoc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.2f);
            shooter.getWorld().spawnParticle(
                    Particle.WITCH,
                    teleportLoc,
                    30, 0.3, 0.5, 0.3, 0.05
            );
            shooter.getWorld().spawnParticle(
                    Particle.NOTE,
                    teleportLoc,
                    10, 0.5, 0.5, 0.5, 1
            );
        }
        //Resonating strike method
        if (projectile.hasMetadata("resonating_strike") && event.getHitEntity() instanceof LivingEntity) {
            Player shooter = (Player) projectile.getShooter();

            // Store shooter's original location for effects
            Location originalLoc = shooter.getLocation().clone();

            // Calculate teleport location (slightly behind the target)
            Vector direction = target.getLocation().getDirection();
            Location teleportLoc = target.getLocation().clone().subtract(direction.multiply(1.5));
            teleportLoc.setYaw(target.getLocation().getYaw() + 180); // Face the target

            // Departure effects
            shooter.getWorld().spawnParticle(
                    Particle.WITCH,
                    originalLoc,
                    30, 0.3, 0.5, 0.3, 0.05
            );
            shooter.getWorld().playSound(originalLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);

            // Perform teleport
            shooter.teleport(teleportLoc);

            // Arrival effects
            shooter.getWorld().playSound(teleportLoc, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.2f);
            shooter.getWorld().spawnParticle(
                    Particle.WITCH,
                    teleportLoc,
                    30, 0.3, 0.5, 0.3, 0.05
            );
            shooter.getWorld().spawnParticle(
                    Particle.NOTE,
                    teleportLoc,
                    10, 0.5, 0.5, 0.5, 1
            );
        }

        // Add this section for Boomstick handling
        if (projectile.hasMetadata("boomstick_projectile") && event.getHitEntity() instanceof LivingEntity) {
            Location hitLoc = event.getHitEntity().getLocation();

            // Impact effects
            hitLoc.getWorld().spawnParticle(
                    Particle.EXPLOSION,
                    hitLoc.add(0, 1, 0),
                    5, 0.2, 0.2, 0.2, 0.05
            );
            hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);

            // Steam effect
            hitLoc.getWorld().spawnParticle(
                    Particle.SMOKE,
                    hitLoc,
                    15, 0.2, 0.2, 0.2, 0.05
            );

            // Energy dispersion effect
            hitLoc.getWorld().spawnParticle(
                    Particle.DUST,
                    hitLoc,
                    20, 0.3, 0.3, 0.3, 0.1,
                    new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1)
            );
        }

        if (projectile.hasMetadata("fireball")) {
            // Get impact location
            Location impactLoc = projectile.getLocation();

            // Apply damage if we hit an entity
            if (event.getHitEntity() instanceof LivingEntity) {

                // Transfer damage metadata from projectile to target
                if (projectile.hasMetadata("skill_damage")) {
                    target.setMetadata("skill_damage", projectile.getMetadata("skill_damage").get(0));
                }
                if (projectile.hasMetadata("skill_damage_amount")) {
                    target.setMetadata("skill_damage_amount",
                            projectile.getMetadata("skill_damage_amount").get(0));
                }

                // Set as magic damage
                target.setMetadata("magic_damage",
                        new FixedMetadataValue(plugin, true));

                // Apply damage through the damage event system
                if (projectile.getShooter() instanceof Player) {
                    Player shooter = (Player) projectile.getShooter();
                    target.damage(0.1, shooter); // Small damage to trigger event
                }
            }

            // Create impact effects
            impactLoc.getWorld().spawnParticle(
                    Particle.FLAME,
                    impactLoc,
                    20, 0.3, 0.3, 0.3, 0.1
            );
            impactLoc.getWorld().spawnParticle(
                    Particle.LAVA,
                    impactLoc,
                    10, 0.2, 0.2, 0.2, 0.05
            );

            // Add explosion effect
            impactLoc.getWorld().playSound(
                    impactLoc,
                    Sound.ENTITY_GENERIC_EXPLODE,
                    1.0f,
                    1.5f
            );

        }

        // Check for Chain Shield reflection first
        if (target instanceof Player) {
            Player defender = (Player) target;

            // Safely check for chain shield metadata
            if (defender.hasMetadata("chain_shield") && defender.hasMetadata("chain_reflect")) {
                try {
                    double reflectChance = defender.getMetadata("chain_reflect").get(0).asDouble();

                    if (Math.random() < reflectChance) {
                        // Cancel the original hit
                        event.setCancelled(true);

                        // Create the reflected projectile
                        Projectile reflected = (Projectile) defender.getWorld().spawnEntity(
                                defender.getLocation().add(0, 1, 0),
                                projectile.getType()
                        );

                        // Safely transfer all metadata from original projectile
                        for (String metadataKey : projectile.getMetadata("skill_damage").stream()
                                .filter(meta -> meta.getOwningPlugin() != null)
                                .map(meta -> "skill_damage")
                                .toList()) {
                            if (projectile.hasMetadata(metadataKey)) {
                                reflected.setMetadata(metadataKey,
                                        projectile.getMetadata(metadataKey).get(0));
                            }
                        }

                        // Copy damage amount if present
                        if (projectile.hasMetadata("skill_damage_amount")) {
                            reflected.setMetadata("skill_damage_amount",
                                    projectile.getMetadata("skill_damage_amount").get(0));
                        }

                        // Set the new shooter and trajectory
                        if (projectile.getShooter() instanceof Entity) {
                            Entity shooter = (Entity) projectile.getShooter();
                            Vector direction = shooter.getLocation()
                                    .subtract(defender.getLocation()).toVector();

                            reflected.setVelocity(direction.normalize()
                                    .multiply(projectile.getVelocity().length()));
                            reflected.setShooter(defender);
                        }

                        // Visual and sound effects for reflection
                        defender.getWorld().spawnParticle(
                                Particle.CRIT,
                                defender.getLocation().add(0, 1, 0),
                                20, 0.5, 0.5, 0.5, 0.2
                        );
                        defender.getWorld().playSound(
                                defender.getLocation(),
                                Sound.BLOCK_CHAIN_BREAK,
                                1.0f,
                                1.5f
                        );

                        // Send feedback message
                        defender.sendMessage("§6✦ You reflected a projectile!");

                        // Remove original projectile
                        projectile.remove();
                        return;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing Chain Shield reflection: " + e.getMessage());
                }
            }
        }

        // If not reflected, continue with normal projectile processing
        if (projectile instanceof Snowball) {
            // Handle party protection
            if (projectile.getShooter() instanceof Player && target instanceof Player) {
                Player shooter = (Player) projectile.getShooter();

                if (projectile.hasMetadata("caster_party")) {
                    Party casterParty = (Party) projectile.getMetadata("caster_party").get(0).value();
                    if (casterParty.shouldPreventInteraction(shooter, target, true)) {
                        return;
                    }
                }

                // Don't damage the caster
                if (target.equals(shooter)) {
                    return;
                }
            }

            // Apply skill effects if present
            if (projectile.hasMetadata("skill_damage")) {
                applyProjectileSkillEffects(projectile, target);
            }
        }



    }

    private void applyProjectileSkillEffects(Projectile projectile, LivingEntity target) {
        Player shooter = projectile.getShooter() instanceof Player ? (Player) projectile.getShooter() : null;
        if (shooter == null) return;

        // Transfer all damage-related metadata to target
        for (String key : new String[]{"skill_damage", "skill_damage_amount", "magic_damage", "true_damage"}) {
            if (projectile.hasMetadata(key)) {
                target.setMetadata(key, projectile.getMetadata(key).get(0));
            }
        }

        // Apply the damage
        if (projectile.hasMetadata("skill_damage_amount")) {
            double damage = projectile.getMetadata("skill_damage_amount").get(0).asDouble();
            target.damage(damage, shooter);
        }

        // Handle additional effects like slow
        if (projectile.hasMetadata("skill_slow")) {
            int duration = 60; // Default 3 seconds
            int intensity = 1;  // Default level 1

            if (projectile.hasMetadata("skill_slow_duration")) {
                duration = projectile.getMetadata("skill_slow_duration").get(0).asInt() * 20;
            }
            if (projectile.hasMetadata("skill_slow_intensity")) {
                intensity = projectile.getMetadata("skill_slow_intensity").get(0).asInt();
            }

            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, intensity - 1));
        }

        // Clean up metadata after a tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) {
                for (String key : new String[]{"skill_damage", "skill_damage_amount", "magic_damage", "true_damage"}) {
                    target.removeMetadata(key, plugin);
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Process shields through the new shield handler
        double modifiedDamage = shieldHandler.processShields(event, player);
        event.setDamage(modifiedDamage);


        // Update health tracking
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            playerData.setCurrentHealth(player.getHealth());
        });

        // Break stealth on damage
        if (!event.isCancelled()) {
            StealthSkill.breakStealth(player);
        }

    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Clear vanilla exp drops
        event.setDroppedExp(0);

        // Handle kill rewards
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        PlayerData playerData = plugin.getPlayerManager().getPlayerData(killer);
        if (playerData == null || playerData.getGameClass() == null) return;

        // Award experience
        String mobType = entity.getType().toString();
        int expGain = plugin.getConfigManager().getMobExp(mobType);

        if (expGain > 0) {
            playerData.addExperience(expGain);
            killer.sendMessage("§a+" + expGain + " EXP");
        }

        // Handle combat system cleanup
        plugin.getCombatManager().handleTargetDeath(event.getEntity());
    }

    private void handleGoredrinkerHealing(Player attacker, double damageAmount) {
        if (attacker.hasMetadata("goredrinker_active")) {
            double healingPercent = attacker.getMetadata("goredrinker_active").get(0).asDouble();
            double healing = damageAmount * healingPercent;

            // Apply healing
            PlayerData attackerData = plugin.getPlayerManager().getPlayerData(attacker);
            if (attackerData != null) {
                attackerData.setCurrentHealth(attackerData.getCurrentHealth() + healing);

                // Healing visual effect
                Location healLoc = attacker.getLocation().add(0, 1, 0);
                attacker.getWorld().spawnParticle(
                        Particle.HEART,
                        healLoc,
                        1, 0.2, 0.2, 0.2, 0
                );
            }
        }
    }

    private void handleGutsEffect(LivingEntity entity, double damage, EntityDamageByEntityEvent event) {
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (player.hasMetadata("guts_active")) {
                double currentHealth = player.getHealth();

                if (currentHealth - damage <= 1.0) {
                    // Prevent death by setting health to 1
                    if (event.getEntity().hasMetadata("skill_damage")) {
                        // For skill damage
                        double newDamage = currentHealth - 1.0;
                        event.setDamage(newDamage);
                        event.getEntity().setMetadata("skill_damage_amount",
                                new FixedMetadataValue(plugin, newDamage));
                    } else {
                        // For regular damage
                        event.setDamage(currentHealth - 1.0);
                    }

                    // Visual feedback
                    player.getWorld().spawnParticle(
                            Particle.TOTEM_OF_UNDYING,
                            player.getLocation().add(0, 1, 0),
                            15, 0.3, 0.5, 0.3, 0.1
                    );
                    player.getWorld().playSound(
                            player.getLocation(),
                            Sound.ITEM_TOTEM_USE,
                            0.5f,
                            1.2f
                    );
                }
            }
        }
    }

    // Replace the isPvPAllowed method in CombatListener with this:

    /**
     * Checks if PvP is allowed between entities, considering combat state
     * This considers both WorldGuard region flags and combat tags
     *
     * @param attacker The attacking entity
     * @param target The targeted entity
     * @return true if PvP is allowed, false if not
     */
    private boolean isPvPAllowed(Entity attacker, Entity target) {
        // Skip check if not player-vs-player
        if (!(target instanceof Player) || !(attacker instanceof Player)) {
            return true; // Not PvP
        }

        // Use the WorldGuardManager which now checks combat status
        return plugin.getWorldGuardManager().canPvP(attacker, target);
    }

}