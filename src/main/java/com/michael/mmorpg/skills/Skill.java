package com.michael.mmorpg.skills;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.party.Party;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public abstract class Skill {
    // Core plugin instance for accessing managers and utilities
    public static MinecraftMMORPG plugin;

    // Basic skill configuration properties
    protected final String name;
    protected final String description;
    protected final int levelRequired;
    protected final double manaCost;
    protected final double staminaCost;
    protected final double healthCost;
    protected final double rageCost;
    protected final double toxinCost;
    protected final double toxinDrainPerTick; // For toggle skills
    protected long cooldown;
    protected final ConfigurationSection config;
    protected boolean isHarmfulSkill;
    protected LivingEntity currentTarget;
    protected boolean isMeleeSkill;
    protected final double targetRange;

    // Cast-time related properties
    protected boolean hasCastTime;
    protected double castTime;
    protected int slowIntensity;
    protected boolean isCasting;
    protected int castTaskId;
    protected boolean isTargetedSkill;

    // Toggle-ability related properties
    protected final boolean isToggleableSkill;
    private static final Map<UUID, Set<String>> activeToggles = new HashMap<>();
    protected boolean skillSucceeded = false;

    public Skill(ConfigurationSection config) {
        this.config = config;
        this.name = config.getString("name", "Unknown Skill");
        this.description = config.getString("description", "No description");
        this.levelRequired = config.getInt("unlockLevel", 1);
        this.manaCost = config.getDouble("manacost", 0);
        this.staminaCost = config.getDouble("staminacost", 0);
        this.healthCost = config.getDouble("healthcost", 0);
        this.rageCost = config.getDouble("ragecost", 0);
        this.toxinCost = config.getDouble("toxincost", 0);
        this.toxinDrainPerTick = config.getDouble("toxindrain", 0);
        this.cooldown = config.getLong("cooldown", 0) * 1000;
        this.isTargetedSkill = config.getBoolean("istargetedskill", true);
        this.isToggleableSkill = config.getBoolean("istoggleableskill", false);
        this.isHarmfulSkill = config.getBoolean("isharmful", true);
        this.hasCastTime = config.getBoolean("hascasttime", false);
        this.castTime = config.getDouble("casttime", 0);
        this.slowIntensity = config.getInt("slowintensity", 0);
        this.isMeleeSkill = config.getBoolean("ismeleeskill", false);

        // Add range configuration with fallback
        if (config.contains("range")) {
            this.targetRange = config.getDouble("range", 3.0);  // Default melee range
        } else {
            // Fallback to old meleerange for compatibility
            this.targetRange = config.getDouble("meleerange", 3.0);
        }

        this.isCasting = false;
        this.castTaskId = -1;
    }

    protected LivingEntity getTargetEntity(Player player, double range) {
        Location playerLoc = player.getEyeLocation();
        Vector direction = playerLoc.getDirection();

        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        Party casterParty = plugin.getPartyManager().getParty(player);

        // Check all nearby entities
        for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, range, range, range)) {
            // Skip if not a living entity or is the caster
            if (!(entity instanceof LivingEntity) || entity == player) continue;

            if (isHarmfulSkill && entity instanceof Player) {
                if (!isPvPAllowed(player, (LivingEntity)entity)) {
                    continue; // Skip this target if PvP is disabled
                }
            }

            // For healing skills (non-harmful), only consider players in the caster's party
            if (!isHarmfulSkill) {
                // Skip if not a player
                if (!(entity instanceof Player)) continue;

                // Skip if not in party (unless it's self-targeting)
                if (casterParty != null && !casterParty.isMember((Player)entity)) continue;
            }
            // For harmful skills, skip party members
            else if (casterParty != null && entity instanceof Player &&
                    casterParty.isMember((Player)entity)) {
                continue;
            }

            LivingEntity target = (LivingEntity) entity;

            // Get the center of the entity's hitbox
            Location entityCenter = target.getLocation().add(0, target.getHeight() / 2, 0);
            Vector toEntity = entityCenter.clone().subtract(playerLoc).toVector();
            double distance = toEntity.length();

            if (distance > range) continue;

            // Variable cone angle based on distance
            double angleThreshold = Math.toRadians(
                    distance < 3.0 ? 25.0 :  // Very close range (25 degree cone)
                            distance < 5.0 ? 15.0 :  // Close range (15 degree cone)
                                    8.0                      // Normal range (8 degree cone)
            );

            double maxAngleCos = Math.cos(angleThreshold);

            // Calculate angle between player's look direction and vector to entity
            double dot = toEntity.clone().normalize().dot(direction);

            // More forgiving horizontal check
            Vector horizontalDirection = direction.clone().setY(0).normalize();
            Vector horizontalToTarget = toEntity.clone().setY(0).normalize();
            double horizontalDot = horizontalToTarget.dot(horizontalDirection);

            // Adjust the horizontal requirement based on distance
            double minHorizontalDot = distance < 3.0 ? -0.5 :  // Very forgiving at close range
                    distance < 5.0 ? -0.2 :   // More forgiving at medium range
                            0.0;                      // Normal at long range

            if (dot > maxAngleCos && horizontalDot > minHorizontalDot) {
                // Check if the ray intersects with the entity's hitbox
                double entityWidth = distance < 3.0 ? 1.0 :    // Larger hitbox at close range
                        distance < 5.0 ? 0.9 :    // Medium hitbox at medium range
                                0.8;                      // Normal hitbox at long range

                double entityHeight = target.getHeight();

                // Create a bounding box around the entity
                Location bottomCenter = target.getLocation();
                Location min = bottomCenter.clone().subtract(entityWidth/2, 0, entityWidth/2);
                Location max = bottomCenter.clone().add(entityWidth/2, entityHeight, entityWidth/2);

                // Ray trace check
                if (intersectsBox(playerLoc, direction, min, max)) {
                    if (distance < closestDistance && hasLineOfSight(playerLoc, entityCenter)) {
                        closest = target;
                        closestDistance = distance;
                    }
                }
            }
        }

        if (closest != null) {
            showTargetingBeam(player, closest);
        }

        return closest;
    }

    private boolean intersectsBox(Location start, Vector direction, Location min, Location max) {
        Vector startVec = start.toVector();

        // Check each axis
        double tx1 = (min.getX() - start.getX()) / direction.getX();
        double tx2 = (max.getX() - start.getX()) / direction.getX();
        double ty1 = (min.getY() - start.getY()) / direction.getY();
        double ty2 = (max.getY() - start.getY()) / direction.getY();
        double tz1 = (min.getZ() - start.getZ()) / direction.getZ();
        double tz2 = (max.getZ() - start.getZ()) / direction.getZ();

        double tmin = Math.max(Math.max(Math.min(tx1, tx2), Math.min(ty1, ty2)), Math.min(tz1, tz2));
        double tmax = Math.min(Math.min(Math.max(tx1, tx2), Math.max(ty1, ty2)), Math.max(tz1, tz2));

        // If tmax < 0, ray is intersecting box, but box is behind ray
        if (tmax < 0) return false;

        // If tmin > tmax, ray doesn't intersect box
        if (tmin > tmax) return false;

        return true;
    }

    private boolean hasLineOfSight(Location start, Location end) {
        World world = start.getWorld();
        if (world == null) return false;

        return world.rayTraceBlocks(
                start,
                end.toVector().subtract(start.toVector()).normalize(),
                start.distance(end),
                FluidCollisionMode.NEVER,
                true
        ) == null;
    }

    protected void showTargetingBeam(Player player, LivingEntity target) {
        Location start = player.getEyeLocation();
        Location end = target.getEyeLocation();
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize().multiply(0.5);

        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    protected boolean validateTarget(Player caster, LivingEntity target) {
        if (target == null || !target.isValid() || target.isDead()) {
            caster.sendMessage("§c✦ Target is no longer valid!");
            return true;
        }

        // Get caster's party
        Party casterParty = plugin.getPartyManager().getParty(caster);

        // For harmful skills against players, check WorldGuard PvP flag
        if (isHarmfulSkill && target instanceof Player) {
            if (!isPvPAllowed(caster, target)) {
                caster.sendMessage("§c✦ PvP is disabled in this region!");
                return true; // Prevent targeting
            }
        }

        // For healing/beneficial skills
        if (!isHarmfulSkill) {
            // Allow self-targeting always
            if (target.equals(caster)) {
                return false; // false means validation passed
            }

            // If target is not a player, prevent targeting
            if (!(target instanceof Player)) {
                caster.sendMessage("§c✦ You can only heal players!");
                return true;
            }

            // If caster is in a party, only allow targeting party members
            if (casterParty != null) {
                Player targetPlayer = (Player) target;
                if (!casterParty.isMember(targetPlayer)) {
                    caster.sendMessage("§c✦ You can only heal party members!");
                    return true;
                }
            } else {
                // If not in a party, only allow self-targeting
                caster.sendMessage("§c✦ You can only heal yourself when not in a party!");
                return true;
            }
        }
        // For harmful skills
        else {
            // Never allow self-targeting for harmful skills
            if (target.equals(caster)) {
                caster.sendMessage("§c✦ You cannot target yourself with harmful skills!");
                return true;
            }

            // Check party protection
            if (casterParty != null && target instanceof Player) {
                Player targetPlayer = (Player) target;
                if (casterParty.isMember(targetPlayer)) {
                    caster.sendMessage("§c✦ You cannot harm party members!");
                    return true;
                }
            }
        }

        if (target instanceof Player && target.hasMetadata("in_toxic_mist")) {
            caster.sendMessage("§c✦ Target is concealed in toxic mist!");
            return true;  // Prevents targeting
        }

        // Show targeting message if all checks pass
        broadcastTargetMessage(caster, target);
        return false; // Validation passed
    }

    public void execute(Player player) {
        // For toggle skills
        if (isToggleableSkill) {
            handleToggleExecution(player);
            return;
        }

        // For cast-time skills
        if (hasCastTime) {
            startCasting(player);
            return;
        }

        // For instant-cast skills
        skillSucceeded = true;
        performSkill(player);

        // Handle resource consumption and cooldown only for instant-cast skills
        if (skillSucceeded) {
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
            if (playerData != null) {
                plugin.getSkillManager().consumeResources(playerData, this);
                plugin.getSkillManager().setCooldown(player, getName(), getCooldown());

                // Only broadcast the "used" message for instant-cast skills here
                if (!hasCastTime) {
                    String targetMsg = currentTarget != null ? " on " + currentTarget.getName() : "";
                    broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                            player.getName() + " used " + name + targetMsg + "!");
                }
            }
        }
    }

    public void execute(Player player, String[] args) {
        if (args == null || args.length == 0) {
            execute(player);  // Use the standard execution
            return;
        }

        // For toggle skills
        if (isToggleableSkill) {
            handleToggleExecution(player);
            return;
        }

        // For cast-time skills
        if (hasCastTime) {
            startCasting(player);
            return;
        }

        // Set success to true initially - individual skills can set it to false if needed
        skillSucceeded = true;

        // Perform the targeted skill execution
        performSkillWithTarget(player, args);

        // Handle resource consumption and cooldown just like regular skills
        if (skillSucceeded) {
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
            if (playerData != null) {
                plugin.getSkillManager().consumeResources(playerData, this);
                plugin.getSkillManager().setCooldown(player, getName(), getCooldown());

                // Broadcast the skill usage
                String targetMsg = currentTarget != null ? " on " + currentTarget.getName() : "";
                broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                        player.getName() + " used " + name + targetMsg + "!");
            }
        }
    }

    // Add this new method to Skill.java
    protected void performSkillWithTarget(Player player, String[] args) {
        // Default implementation - individual skills will override this
        player.sendMessage("§c✦ This skill doesn't support targeted casting!");
        skillSucceeded = false;
    }

    protected LivingEntity getMeleeTarget(Player player, double range) {
        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.getDirection();

        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        double meleeAngleCos = Math.cos(Math.toRadians(60)); // Wider angle for melee (60 degrees)

        Party casterParty = plugin.getPartyManager().getParty(player);

        for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, range, range, range)) {
            if (!(entity instanceof LivingEntity) || entity == player) continue;

            // Party checks
            if (!isHarmfulSkill) {
                if (!(entity instanceof Player)) continue;
                if (casterParty != null && !casterParty.isMember((Player)entity)) continue;
            } else if (casterParty != null && entity instanceof Player &&
                    casterParty.isMember((Player)entity)) {
                continue;
            }

            LivingEntity target = (LivingEntity) entity;
            Location targetLoc = target.getLocation();
            Vector toTarget = targetLoc.subtract(playerLoc).toVector();

            double distance = toTarget.length();
            if (distance > range) continue;

            // Melee-specific angle check (horizontal plane prioritized)
            Vector horizontalDirection = direction.clone().setY(0).normalize();
            Vector horizontalToTarget = toTarget.clone().setY(0).normalize();
            double horizontalDot = horizontalToTarget.dot(horizontalDirection);

            if (horizontalDot > meleeAngleCos) {
                // Check if entity is in front of the player
                if (distance < closestDistance) {
                    closest = target;
                    closestDistance = distance;
                }
            }
        }

        if (closest != null) {
            showMeleeTargetingEffect(player, closest);
        }

        return closest;
    }

    protected void showMeleeTargetingEffect(Player player, LivingEntity target) {
        Location start = player.getLocation().add(0, 1, 0);
        Location end = target.getLocation().add(0, 1, 0);

        // Show a quick arc effect between player and target
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize().multiply(0.5);

        for (double d = 0; d < distance; d += 0.5) {
            double progress = d / distance;
            // Create an arc effect
            double height = Math.sin(progress * Math.PI) * 0.5;
            Location point = start.clone().add(direction.clone().multiply(d)).add(0, height, 0);
            player.getWorld().spawnParticle(Particle.CRIT, point, 1, 0, 0, 0, 0);
        }
    }

    protected void handleToggleExecution(Player player) {
        // Check toggle state first
        boolean currentlyActive = isToggleActive(player);
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Reset success flag
        skillSucceeded = false;

        if (currentlyActive) {
            // If it's already active, remove toxin drain and deactivate
            if (toxinDrainPerTick > 0) {
            }
            deactivateToggle(player);
            skillSucceeded = true;
        } else {
            // Check resources before allowing activation
            if (!plugin.getSkillManager().checkResources(player, playerData, this)) {
                return;
            }

            // Not active and has resources, try to activate it
            performSkill(player);

            // If skill was successful, add to active toggles
            if (skillSucceeded) {
                UUID playerId = player.getUniqueId();
                Set<String> toggles = activeToggles.computeIfAbsent(playerId, k -> new HashSet<>());
                toggles.add(name.toLowerCase());

                // Add toxin drain if applicable
                if (toxinDrainPerTick > 0) {
                }

                // Consume initial resources
                plugin.getSkillManager().consumeResources(playerData, this);
            }
        }
    }

    public boolean isToggleActive(Player player) {
        Set<String> playerToggles = activeToggles.get(player.getUniqueId());
        return playerToggles != null && playerToggles.contains(name.toLowerCase());
    }

    protected void onToggleDeactivate(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData != null && toxinDrainPerTick > 0) {
                    }
    }

    public void deactivateToggle(Player player) {
        UUID playerId = player.getUniqueId();
        Set<String> playerToggles = activeToggles.get(playerId);

        if (playerToggles != null) {
            playerToggles.remove(name.toLowerCase());
            if (playerToggles.isEmpty()) {
                activeToggles.remove(playerId);
            }
        }

        // Call cleanup method that child classes can override
        onToggleDeactivate(player);

        // Apply cooldown when toggled off
        plugin.getSkillManager().setCooldown(player, name, getCooldown());
    }

    protected void startCasting(Player player) {
        if (isCasting) return;

        isCasting = true;
        player.setMetadata("casting", new FixedMetadataValue(plugin, this));

        // Start casting bar
        plugin.getCastingBarManager().startCasting(player, name, castTime);

        if (slowIntensity > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    (int)(castTime * 20), slowIntensity - 1));
        }

        // Show "begins to cast" only for longer cast times
        if (castTime > 0.5) {
            String targetMsg = currentTarget != null ? " on " + currentTarget.getName() : "";
            broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                    player.getName() + " begins to cast " + name + targetMsg + "!");
        }

        castTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (isCasting) {
                    skillSucceeded = true;
                    performSkill(player);
                    if (skillSucceeded) {
                        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                        if (playerData != null) {
                            plugin.getSkillManager().consumeResources(playerData, Skill.this);
                            plugin.getSkillManager().setCooldown(player, getName(), getCooldown());
                        }
                        // Always show the "used" message when skill succeeds
                        String targetMsg = currentTarget != null ? " on " + currentTarget.getName() : "";
                        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                                player.getName() + " used " + name + targetMsg + "!");
                    }
                    endCast(player);
                }
            }
        }.runTaskLater(plugin, (long)(castTime * 20)).getTaskId();
    }

    public void cancelCast(Player player) {
        if (!isCasting) return;

        if (castTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(castTaskId);
        }

        endCast(player);
        broadcastLocalSkillMessage(player, "§c[" + getPlayerClass(player) + "] " +
                player.getName() + "'s " + name + " was interrupted!");

        // Stop the casting bar
        plugin.getCastingBarManager().stopCasting(player);
    }



    protected void endCast(Player player) {
        isCasting = false;
        castTaskId = -1;
        if (slowIntensity > 0) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        player.removeMetadata("casting", plugin);
    }

    protected String getPlayerClass(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        return playerData != null && playerData.hasClass() ? playerData.getGameClass().getName() : "None";
    }

    protected void broadcastLocalSkillMessage(Player caster, String message) {
        Location location = caster.getLocation();
        location.getWorld().getNearbyEntities(location, 25, 25, 25).forEach(entity -> {
            if (entity instanceof Player && !entity.equals(caster)) {  // Skip caster in the loop
                ((Player) entity).sendMessage(message);
            }
        });
        caster.sendMessage(message);  // Send to caster once
    }

    protected void broadcastTargetMessage(Player caster, LivingEntity target) {
        String targetName;
        if (target instanceof Player) {
            targetName = ((Player) target).getName();
        } else {
            targetName = formatMobName(target.getType().toString());
        }
    }

    private String formatMobName(String mobType) {
        String[] words = mobType.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1));
        }
        return formatted.toString();
    }

    protected boolean isPvPAllowed(Player player, LivingEntity target) {
        // Skip check if target is not a player
        if (!(target instanceof Player)) {
            return true;
        }

        // Check WorldGuard PvP flag
        return plugin.getWorldGuardManager().canPvP(player, target);
    }


    protected abstract void performSkill(Player player);

    protected void setSkillSuccess(boolean success) {
        this.skillSucceeded = success;
    }


    public boolean wasSuccessful() {
        return skillSucceeded;
    }

    public static void setPlugin(MinecraftMMORPG plugin) {
        Skill.plugin = plugin;
    }

    // Getters
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getLevelRequired() { return levelRequired; }
    public double getManaCost() { return manaCost; }
    public double getStaminaCost() { return staminaCost; }
    public double getHealthCost() { return healthCost; }
    public long getCooldown() { return cooldown; }
    public boolean isHasCastTime() { return hasCastTime; }
    public boolean isCasting() { return isCasting; }
    public boolean isToggleableSkill() { return isToggleableSkill; }
    protected static MinecraftMMORPG getPlugin() { return plugin; }
    public double getRageCost() { return rageCost; }
    public double getToxinCost() { return toxinCost; }
    public double getToxinDrainPerTick() { return toxinDrainPerTick; }

    public static void cleanup() {
        activeToggles.clear();
    }
}