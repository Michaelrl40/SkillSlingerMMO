package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

public class CombatPotionListener implements Listener {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Map<String, Long>> potionCooldowns = new HashMap<>();
    private long defaultCooldown;
    private long drinkableCooldown;  // Cooldown for drinkable potions
    private final Map<PotionEffectType.Category, Long> categoryCooldowns = new HashMap<>();
    private final Set<PotionEffectType.Category> harmfulCategories = new HashSet<>();
    private final Set<PotionEffectType.Category> beneficialCategories = new HashSet<>();
    private long combatDuration;

    public CombatPotionListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    public void loadConfiguration() {
        try {
            FileConfiguration config = plugin.getConfig();

            // Load basic settings with defaults
            this.defaultCooldown = config.getLong("potion-combat.default-cooldown", 10) * 1000; // Convert to milliseconds
            this.drinkableCooldown = config.getLong("potion-combat.drinkable-cooldown", 5) * 1000; // New setting
            this.combatDuration = config.getLong("potion-combat.combat-duration", 10) * 1000;

            // Clear existing cooldowns and effects for reload
            categoryCooldowns.clear();
            harmfulCategories.clear();
            beneficialCategories.clear();

            // Setup categories
            harmfulCategories.add(PotionEffectType.Category.HARMFUL);
            beneficialCategories.add(PotionEffectType.Category.BENEFICIAL);

            // Load specific potion cooldowns for categories
            ConfigurationSection cooldownSection = config.getConfigurationSection("potion-combat.cooldowns");
            if (cooldownSection != null) {
                for (String key : cooldownSection.getKeys(false)) {
                    // Try to get category from effect type
                    try {
                        PotionEffectType type = PotionEffectType.getByName(key.toUpperCase());
                        if (type != null) {
                            PotionEffectType.Category category = type.getEffectCategory();
                            categoryCooldowns.put(category, cooldownSection.getLong(key) * 1000);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error processing cooldown for effect type: " + key);
                    }
                }
            }

            plugin.getLogger().info("Successfully loaded potion combat configuration");
            plugin.getLogger().info("Default cooldown: " + defaultCooldown / 1000 + "s");
            plugin.getLogger().info("Drinkable cooldown: " + drinkableCooldown / 1000 + "s");
            plugin.getLogger().info("Loaded " + categoryCooldowns.size() + " category cooldowns");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading potion combat configuration", e);
            // Load safe defaults if configuration fails
            this.defaultCooldown = 10000;
            this.drinkableCooldown = 5000;
            this.combatDuration = 10000;

            // Setup default categories
            harmfulCategories.add(PotionEffectType.Category.HARMFUL);
            beneficialCategories.add(PotionEffectType.Category.BENEFICIAL);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionSplash(PotionSplashEvent event) {
        if (event.isCancelled()) return;

        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player)) return;

        Player thrower = (Player) potion.getShooter();

        try {
            // Check cooldown
            if (isOnCooldown(thrower, "thrown_potion")) {
                event.setCancelled(true);
                long remainingSeconds = getRemainingCooldown(thrower, "thrown_potion") / 1000;
                thrower.sendMessage("§c✦ Thrown potions are on cooldown for " + remainingSeconds + " seconds!");
                return;
            }

            // Get max cooldown from potion effects
            long maxCooldown = getMaxCooldownFromEffects(potion.getEffects());

            // Get thrower's party
            Party throwerParty = plugin.getPartyManager().getParty(thrower);

            // Process affected entities and check if harmful effects were applied
            boolean harmfulEffectApplied = processAffectedEntities(event, thrower, throwerParty);

            // Only enter combat if harmful effects applied to enemy players
            if (harmfulEffectApplied) {
                plugin.getCombatManager().enterCombat(thrower, thrower);
                thrower.sendMessage("§c✦ You've entered combat by using a harmful potion!");
            }

            // Apply cooldown
            setCooldown(thrower, "thrown_potion", maxCooldown);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error handling potion splash event", e);
        }

        // Handle special poison skill effects
        if (potion.hasMetadata("poison_effect")) {
            handlePoisonSkillEffect(event, potion);
        }
    }

    private void handlePoisonSkillEffect(PotionSplashEvent event, ThrownPotion potion) {
        try {
            Player caster = (Player) potion.getMetadata("skill_damage").get(0).value();
            double damage = potion.getMetadata("skill_damage_amount").get(0).asDouble();
            int duration = potion.getMetadata("skill_duration").get(0).asInt() * 20; // Convert to ticks

            for (LivingEntity entity : event.getAffectedEntities()) {
                if (entity instanceof Player) {
                    Player target = (Player) entity;
                    if (plugin.getPartyManager().areInSameParty(caster, target)) continue;
                }

                // Apply poison effect
                plugin.getCombatManager().enterCombat(caster, entity);
                entity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0));

                // Schedule damage ticks
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (ticks >= duration || !entity.isValid()) {
                            this.cancel();
                            return;
                        }
                        if (ticks % 20 == 0) { // Damage once per second
                            entity.damage(damage, caster);
                        }
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error handling poison skill effect", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLingeringPotionSplash(LingeringPotionSplashEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player thrower = (Player) event.getEntity().getShooter();

        try {
            if (isOnCooldown(thrower, "thrown_potion")) {
                event.setCancelled(true);
                return;
            }

            // Mark the area effect cloud with thrower data
            event.getAreaEffectCloud().setMetadata("potion_owner",
                    new FixedMetadataValue(plugin, thrower.getUniqueId()));

            // Mark the cloud with party data if applicable
            Party throwerParty = plugin.getPartyManager().getParty(thrower);
            if (throwerParty != null) {
                event.getAreaEffectCloud().setMetadata("potion_party",
                        new FixedMetadataValue(plugin, throwerParty));
            }

            // Check if cloud has harmful effects
            boolean hasHarmfulEffects = false;
            for (PotionEffect effect : event.getEntity().getEffects()) {
                if (harmfulCategories.contains(effect.getType().getEffectCategory())) {
                    hasHarmfulEffects = true;
                    break;
                }
            }

            // Mark the cloud with harmful flag if needed
            if (hasHarmfulEffects) {
                event.getAreaEffectCloud().setMetadata("has_harmful_effects",
                        new FixedMetadataValue(plugin, true));
            }

            // Apply cooldown based on effects
            long maxCooldown = getMaxCooldownFromEffects(event.getEntity().getEffects());
            setCooldown(thrower, "thrown_potion", maxCooldown);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error handling lingering potion event", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        if (event.isCancelled()) return;

        if (!event.getEntity().hasMetadata("potion_owner")) return;

        try {
            UUID ownerUUID = (UUID) event.getEntity().getMetadata("potion_owner").get(0).value();
            Player owner = plugin.getServer().getPlayer(ownerUUID);
            if (owner == null) return;

            Party ownerParty = null;
            if (event.getEntity().hasMetadata("potion_party")) {
                ownerParty = (Party) event.getEntity().getMetadata("potion_party").get(0).value();
            }

            boolean affectedNonPartyMember = false;

            Iterator<LivingEntity> iterator = event.getAffectedEntities().iterator();
            while (iterator.hasNext()) {
                LivingEntity entity = iterator.next();
                boolean isPartyMember = entity instanceof Player &&
                        ownerParty != null &&
                        ownerParty.isMember((Player) entity);

                boolean entityWasHarmed = false;

                for (PotionEffect effect : event.getEntity().getCustomEffects()) {
                    PotionEffectType.Category category = effect.getType().getEffectCategory();
                    boolean isHarmful = harmfulCategories.contains(category);
                    boolean isBeneficial = beneficialCategories.contains(category);

                    if ((isHarmful && (isPartyMember || entity == owner)) ||
                            (isBeneficial && !isPartyMember && entity != owner)) {
                        iterator.remove();
                        break;
                    }

                    // Track if this entity was harmed
                    if (isHarmful && !isPartyMember && entity != owner) {
                        entityWasHarmed = true;
                    }
                }

                // If this is a player that was harmed, mark that we need to put thrower in combat
                if (entityWasHarmed && entity instanceof Player && entity != owner) {
                    affectedNonPartyMember = true;
                }
            }

            // Put owner in combat if they affected another player with harmful effects
            if (affectedNonPartyMember && event.getEntity().hasMetadata("has_harmful_effects")) {
                plugin.getCombatManager().enterCombat(owner, owner);
                owner.sendMessage("§c✦ You've entered combat by using a harmful potion cloud!");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error handling area effect cloud event", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDrinkPotion(PlayerItemConsumeEvent event) {
        if (event.isCancelled()) return;

        // Check if the item is a potion
        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION) return;

        Player player = event.getPlayer();

        try {
            // Check cooldown
            if (isOnCooldown(player, "drink_potion")) {
                event.setCancelled(true);
                long remainingSeconds = getRemainingCooldown(player, "drink_potion") / 1000;
                player.sendMessage("§c✦ Drinkable potions are on cooldown for " + remainingSeconds + " seconds!");
                return;
            }

            // Get potion effects
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) return;

            // Get max cooldown for this potion
            long maxCooldown = drinkableCooldown;

            // Check custom effects first
            if (meta.hasCustomEffects()) {
                for (PotionEffect effect : meta.getCustomEffects()) {
                    PotionEffectType.Category category = effect.getType().getEffectCategory();
                    Long categoryCooldown = categoryCooldowns.get(category);
                    if (categoryCooldown != null && categoryCooldown > maxCooldown) {
                        maxCooldown = categoryCooldown;
                    }
                }
            }
            // If no custom effects, we can try to determine the effect more safely
            else {
                // Get the type name string directly from the meta data without using PotionData
                String potionName = item.getItemMeta().toString().toUpperCase();

                // Check for common potion types by keywords in the name
                if (potionName.contains("HEALING") || potionName.contains("INSTANT_HEAL")) {
                    Long healCooldown = categoryCooldowns.get(PotionEffectType.Category.BENEFICIAL);
                    if (healCooldown != null && healCooldown > maxCooldown) {
                        maxCooldown = healCooldown;
                    }
                }
                else if (potionName.contains("HARMING") || potionName.contains("INSTANT_DAMAGE")) {
                    Long damageCooldown = categoryCooldowns.get(PotionEffectType.Category.HARMFUL);
                    if (damageCooldown != null && damageCooldown > maxCooldown) {
                        maxCooldown = damageCooldown;
                    }
                }
                else if (potionName.contains("REGEN") || potionName.contains("REGENERATION")) {
                    Long regenCooldown = categoryCooldowns.get(PotionEffectType.Category.BENEFICIAL);
                    if (regenCooldown != null && regenCooldown > maxCooldown) {
                        maxCooldown = regenCooldown;
                    }
                }
                else if (potionName.contains("STRENGTH") || potionName.contains("INCREASE_DAMAGE")) {
                    Long strengthCooldown = categoryCooldowns.get(PotionEffectType.Category.BENEFICIAL);
                    if (strengthCooldown != null && strengthCooldown > maxCooldown) {
                        maxCooldown = strengthCooldown;
                    }
                }
                else if (potionName.contains("SPEED") || potionName.contains("SWIFTNESS")) {
                    Long speedCooldown = categoryCooldowns.get(PotionEffectType.Category.BENEFICIAL);
                    if (speedCooldown != null && speedCooldown > maxCooldown) {
                        maxCooldown = speedCooldown;
                    }
                }
                else if (potionName.contains("POISON")) {
                    Long poisonCooldown = categoryCooldowns.get(PotionEffectType.Category.HARMFUL);
                    if (poisonCooldown != null && poisonCooldown > maxCooldown) {
                        maxCooldown = poisonCooldown;
                    }
                }
                else if (potionName.contains("SLOWNESS") || potionName.contains("SLOW")) {
                    Long slowCooldown = categoryCooldowns.get(PotionEffectType.Category.HARMFUL);
                    if (slowCooldown != null && slowCooldown > maxCooldown) {
                        maxCooldown = slowCooldown;
                    }
                }
                // Add more types as needed
            }

            // Apply cooldown
            setCooldown(player, "drink_potion", maxCooldown);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error handling potion consumption event", e);
        }
    }

    /**
     * Process affected entities and determine if any harmful effects were applied to non-party members
     * @return true if harmful effects were applied to other players, false otherwise
     */
    private boolean processAffectedEntities(PotionSplashEvent event, Player thrower, Party throwerParty) {
        boolean appliedHarmfulEffect = false;

        for (LivingEntity entity : event.getAffectedEntities()) {
            boolean isPartyMember = entity instanceof Player &&
                    throwerParty != null &&
                    throwerParty.isMember((Player) entity);

            boolean isPlayer = entity instanceof Player;
            boolean isEnemyPlayer = isPlayer && entity != thrower && !isPartyMember;

            for (PotionEffect effect : event.getPotion().getEffects()) {
                PotionEffectType.Category category = effect.getType().getEffectCategory();
                boolean isHarmful = harmfulCategories.contains(category);
                boolean isBeneficial = beneficialCategories.contains(category);

                if (isHarmful) {
                    if (isPartyMember || entity == thrower) {
                        event.setIntensity(entity, 0);
                    } else if (isEnemyPlayer) {
                        // We're applying harmful effects to another player
                        appliedHarmfulEffect = true;
                    }
                } else if (isBeneficial) {
                    if (!isPartyMember && entity != thrower) {
                        event.setIntensity(entity, 0);
                    }
                }
            }
        }

        return appliedHarmfulEffect;
    }

    private long getMaxCooldownFromEffects(Collection<PotionEffect> effects) {
        long maxCooldown = defaultCooldown;
        for (PotionEffect effect : effects) {
            PotionEffectType.Category category = effect.getType().getEffectCategory();
            Long catCooldown = categoryCooldowns.get(category);
            if (catCooldown != null && catCooldown > maxCooldown) {
                maxCooldown = catCooldown;
            }
        }
        return maxCooldown;
    }

    private boolean isOnCooldown(Player player, String type) {
        Map<String, Long> cooldowns = potionCooldowns.get(player.getUniqueId());
        if (cooldowns == null) return false;

        Long cooldownEnd = cooldowns.get(type);
        return cooldownEnd != null && cooldownEnd > System.currentTimeMillis();
    }

    private long getRemainingCooldown(Player player, String type) {
        Map<String, Long> cooldowns = potionCooldowns.get(player.getUniqueId());
        if (cooldowns == null) return 0;

        Long cooldownEnd = cooldowns.get(type);
        if (cooldownEnd == null) return 0;

        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }

    private void setCooldown(Player player, String type, long duration) {
        potionCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(type, System.currentTimeMillis() + duration);
    }

    public void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        potionCooldowns.values().forEach(cooldowns ->
                cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now));
        potionCooldowns.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Maps a PotionType to its corresponding PotionEffectType.Category.
     * @param type The PotionType to convert
     * @return The corresponding category, or null if none exists
     */
    private PotionEffectType.Category getPotionEffectCategoryFromType(PotionType type) {
        // For most vanilla potions, we can determine if they're harmful or beneficial
        switch (type) {
            case HARMING:
            case STRONG_HARMING:
            case POISON:
            case LONG_POISON:
            case STRONG_POISON:
            case SLOWNESS:
            case LONG_SLOWNESS:
            case STRONG_SLOWNESS:
            case WEAKNESS:
            case LONG_WEAKNESS:
                return PotionEffectType.Category.HARMFUL;

            case FIRE_RESISTANCE:
            case LONG_FIRE_RESISTANCE:
            case HEALING:
            case STRONG_HEALING:
            case INVISIBILITY:
            case LONG_INVISIBILITY:
            case LEAPING:
            case LONG_LEAPING:
            case STRONG_LEAPING:
            case NIGHT_VISION:
            case LONG_NIGHT_VISION:
            case REGENERATION:
            case LONG_REGENERATION:
            case STRONG_REGENERATION:
            case SLOW_FALLING:
            case LONG_SLOW_FALLING:
            case STRENGTH:
            case LONG_STRENGTH:
            case STRONG_STRENGTH:
            case SWIFTNESS:
            case LONG_SWIFTNESS:
            case STRONG_SWIFTNESS:
            case TURTLE_MASTER:
            case LONG_TURTLE_MASTER:
            case STRONG_TURTLE_MASTER:
            case WATER_BREATHING:
            case LONG_WATER_BREATHING:
            case LUCK:
                return PotionEffectType.Category.BENEFICIAL;

            // New potion types
            case OOZING:
            case WIND_CHARGED:
            case WEAVING:
            case INFESTED:
                // You'll need to categorize these based on your game mechanics
                return PotionEffectType.Category.BENEFICIAL;

            // Neutrals/other
            case AWKWARD:
            case MUNDANE:
            case THICK:
            case WATER:
            default:
                return null;
        }
    }
}