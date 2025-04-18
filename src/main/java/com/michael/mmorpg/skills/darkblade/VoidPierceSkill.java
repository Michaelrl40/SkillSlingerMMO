package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.UUID;

public class VoidPierceSkill extends Skill implements Listener {
    private final double damage;
    private final double manaSiphon;
    private final HashMap<UUID, Integer> hitCounter = new HashMap<>();
    private final HashMap<UUID, UUID> lastTargetMap = new HashMap<>(); // Track the last target for each player
    private final HashMap<UUID, Long> lastSiphonTime = new HashMap<>(); // Track last siphon time
    private final int HITS_REQUIRED = 3;
    private final long SIPHON_COOLDOWN = 5000; // 5 seconds in milliseconds

    public VoidPierceSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 20.0);
        this.manaSiphon = config.getDouble("manasiphon", 25.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Skip if already processed by Void Pierce
        if (event.getEntity().hasMetadata("void_pierce_damage")) {
            return;
        }

        // Make sure it's a direct player melee attack, not a projectile or skill
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();

        // Skip if it's not a melee attack (check by examining if a weapon item is held)
        String weaponType = attacker.getInventory().getItemInMainHand().getType().toString().toUpperCase();
        boolean isMeleeWeapon = weaponType.endsWith("_SWORD") ||
                weaponType.endsWith("_AXE") ||
                weaponType.endsWith("_HOE") ||
                weaponType.equals("STICK") ||
                weaponType.equals("BLAZE_ROD") ||
                weaponType.endsWith("_SHOVEL");

        if (!isMeleeWeapon) return;

        // Skip if attacker doesn't have this skill
        if (!hasSkill(attacker)) return;

        // For PvP, check WorldGuard protection
        if (target instanceof Player) {
            if (!isPvPAllowed(attacker, target)) {
                return; // Skip if PvP is disabled in this region
            }
        }

        UUID attackerId = attacker.getUniqueId();
        UUID targetId = target.getUniqueId();

        // Check if target has changed
        UUID lastTargetId = lastTargetMap.get(attackerId);
        if (lastTargetId == null || !lastTargetId.equals(targetId)) {
            // Reset hit counter when switching targets
            hitCounter.remove(attackerId);
            lastTargetMap.put(attackerId, targetId);
        }

        // Increment hit counter
        int hits = hitCounter.getOrDefault(attackerId, 0) + 1;

        // Apply Void Pierce on third hit
        if (hits >= HITS_REQUIRED) {
            hits = 0; // Reset counter
            applyVoidPierce(attacker, target);
        }

        // Update hit counter
        hitCounter.put(attackerId, hits);

        // Play charge effect if we have hits accumulated
        if (hits > 0) {
            playChargeEffect(attacker, hits);
        }
    }

    @Override
    protected boolean isPvPAllowed(Player attacker, LivingEntity target) {
        // If target is not a player, PvP rules don't apply
        if (!(target instanceof Player)) return true;

        // If WorldGuard integration is available, use it
        if (plugin.getWorldGuardManager() != null) {
            return plugin.getWorldGuardManager().canPvP(attacker, target);
        }

        // Fallback method
        return super.isPvPAllowed(attacker, target);
    }

    private boolean isOnSiphonCooldown(Player player) {
        Long lastSiphon = lastSiphonTime.get(player.getUniqueId());
        if (lastSiphon == null) return false;

        return System.currentTimeMillis() - lastSiphon < SIPHON_COOLDOWN;
    }

    private void applySiphonCooldown(Player player) {
        lastSiphonTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private long getRemainingCooldown(Player player) {
        Long lastSiphon = lastSiphonTime.get(player.getUniqueId());
        if (lastSiphon == null) return 0;

        long remaining = SIPHON_COOLDOWN - (System.currentTimeMillis() - lastSiphon);
        return Math.max(0, remaining);
    }

    private boolean hasSkill(Player player) {
        return plugin.getPlayerManager().getPlayerData(player) != null &&
                plugin.getPlayerManager().getPlayerData(player).hasClass() &&
                plugin.getPlayerManager().getPlayerData(player).getGameClass().getName().equals("DarkBlade");
    }

    private void applyVoidPierce(Player attacker, LivingEntity target) {
        // First, verify it's still a melee weapon when applying effects
        String weaponType = attacker.getInventory().getItemInMainHand().getType().toString().toUpperCase();
        boolean isMeleeWeapon = weaponType.endsWith("_SWORD") ||
                weaponType.endsWith("_AXE") ||
                weaponType.endsWith("_HOE") ||
                weaponType.equals("STICK") ||
                weaponType.equals("BLAZE_ROD") ||
                weaponType.endsWith("_SHOVEL");

        if (!isMeleeWeapon) {
            // Not a melee weapon anymore, don't apply effects
            attacker.sendMessage("§c✦ Void Pierce requires a melee weapon!");
            return;
        }

        // Check for PvP protection if target is a player
        if (target instanceof Player && !isPvPAllowed(attacker, target)) {
            attacker.sendMessage("§c✦ Void Pierce cannot be used in PvP-protected areas!");
            return;
        }

        // Mark this damage as coming from void pierce to prevent recursion
        target.setMetadata("void_pierce_damage", new FixedMetadataValue(plugin, true));

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, attacker));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(0.1, attacker);

        target.removeMetadata("void_pierce_damage", plugin);

        // Handle mana siphon with cooldown (only for player targets)
        if (target instanceof Player) {
            if (isOnSiphonCooldown(attacker)) {
                // Still do damage but inform about siphon cooldown
                long remainingSeconds = getRemainingCooldown(attacker) / 1000;
                attacker.sendMessage(String.format("§5✦ Mana siphon on cooldown for %d seconds!", remainingSeconds));
            } else {
                // Apply mana siphon
                Player targetPlayer = (Player) target;
                var targetData = plugin.getPlayerManager().getPlayerData(targetPlayer);
                var attackerData = plugin.getPlayerManager().getPlayerData(attacker);

                if (targetData != null && attackerData != null) {
                    double manaStolen = Math.min(targetData.getCurrentMana(), manaSiphon);
                    targetData.useMana(manaStolen);
                    attackerData.addMana(manaStolen);

                    // Apply cooldown
                    applySiphonCooldown(attacker);

                    // Notify players
                    attacker.sendMessage(String.format("§5✦ Siphoned %.1f mana from %s!", manaStolen, target.getName()));
                    targetPlayer.sendMessage(String.format("§5✦ %s siphoned %.1f mana from you!", attacker.getName(), manaStolen));

                    // Play additional siphon effects
                    playSiphonEffect(target.getLocation());
                }
            }
        }

        // Play base void pierce effects
        playVoidPierceEffect(attacker, target);
    }

    private void playVoidPierceEffect(Player attacker, LivingEntity target) {
        Location targetLoc = target.getLocation().add(0, 1, 0);

        // Void pierce effect
        target.getWorld().spawnParticle(
                Particle.DUST,
                targetLoc,
                20, 0.3, 0.3, 0.3, 0.2,
                new Particle.DustOptions(Color.fromRGB(75, 0, 130), 2)
        );

        // Add sound effect for more impact
        target.getWorld().playSound(
                targetLoc,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                0.6f,
                0.8f
        );
    }

    private void playSiphonEffect(Location location) {
        // Special effect for successful mana siphon
        location.getWorld().spawnParticle(
                Particle.WITCH,
                location.add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0.1
        );

        location.getWorld().playSound(
                location,
                Sound.ENTITY_PHANTOM_AMBIENT,
                0.5f,
                1.2f
        );
    }

    private void playChargeEffect(Player player, int hitCount) {
        Location loc = player.getLocation().add(0, 1, 0);

        Color chargeColor = hitCount == 1 ?
                Color.fromRGB(100, 0, 150) :  // Light purple
                Color.fromRGB(150, 0, 200);   // Darker purple

        player.getWorld().spawnParticle(
                Particle.DUST,
                loc,
                5, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(chargeColor, 1)
        );

    }

    @Override
    public void execute(Player player) {
        // This is a passive skill, so execute does nothing
        player.sendMessage("§5✦ Void Pierce is a passive skill. Land three melee attacks on the same target to trigger it.");
    }

    @Override
    protected void performSkill(Player player) {
        // This is a passive skill, so performSkill does nothing
    }

    // Clear the counter for a player when they log out or change class
    public void clearHitCounter(Player player) {
        hitCounter.remove(player.getUniqueId());
        lastTargetMap.remove(player.getUniqueId());
    }
}