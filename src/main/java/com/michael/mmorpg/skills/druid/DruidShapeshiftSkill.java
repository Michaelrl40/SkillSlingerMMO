package com.michael.mmorpg.skills.druid;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class DruidShapeshiftSkill extends Skill {
    protected final long duration;
    protected boolean isToggleable;
    protected final String formName;
    protected String requiredForm;  // null = usable in any form, "none" = base form only
    protected final boolean canUseInForms;  // true = can be used while transformed
    protected Disguise currentDisguise;

    // Static tracking of active shapeshifts
    private static final Map<UUID, DruidShapeshiftSkill> activeShapeshifts = new HashMap<>();

    public DruidShapeshiftSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getLong("duration", 30) * 1000;
        this.isToggleable = config.getBoolean("istoggleableskill", true);
        this.formName = config.getString("formname", "Unknown Form");
        this.requiredForm = config.getString("requiredform", null);
        this.canUseInForms = config.getBoolean("useinforms", true);
    }

    @Override
    public void execute(Player player) {
        // First check form requirements
        if (!checkFormRequirements(player)) {
            setSkillSuccess(false);
            return;
        }

        // Get player data for resource checks
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) {
            setSkillSuccess(false);
            return;
        }

        // For shapeshifting skills, handle transformation
        if (isToggleable) {
            handleShapeshiftSkill(player);
            return;
        }

        // Handle targeting for non-shapeshift skills
        if (isTargetedSkill) {
            // Use base targeting system
            if (isMeleeSkill) {
                currentTarget = getMeleeTarget(player, targetRange);
            } else {
                currentTarget = getTargetEntity(player, targetRange);
            }

            // For healing skills, allow self-targeting
            if (currentTarget == null && !isHarmfulSkill) {
                currentTarget = player;
            }

            // Validate target if we found one
            if (currentTarget != null && validateTarget(player, currentTarget)) {
                setSkillSuccess(false);
                return;
            }
        }

        // Handle cast time
        if (hasCastTime) {
            startCasting(player);
        } else {
            performSkill(player);
        }
    }

    private boolean checkFormRequirements(Player player) {
        if (requiredForm != null && requiredForm.equalsIgnoreCase("none")) {
            if (player.hasMetadata("druid_form")) {
                player.sendMessage("§c✦ This skill can only be used in your base form!");
                return false;
            }
        } else if (player.hasMetadata("druid_form")) {
            String currentForm = player.getMetadata("druid_form").get(0).asString();

            if (requiredForm != null && !requiredForm.equalsIgnoreCase(currentForm)) {
                player.sendMessage("§c✦ This skill can only be used in " + requiredForm + " form!");
                return false;
            }

            if (!canUseInForms) {
                player.sendMessage("§c✦ This skill cannot be used while transformed!");
                return false;
            }
        } else if (requiredForm != null && !requiredForm.equalsIgnoreCase("none")) {
            player.sendMessage("§c✦ This skill requires " + requiredForm + " form!");
            return false;
        }
        return true;
    }

    protected void transformPlayer(Player player) {
        // Create and apply the disguise
        currentDisguise = createDisguise();
        if (currentDisguise != null) {
            // Remove any existing shapeshift first
            DruidShapeshiftSkill activeForm = activeShapeshifts.get(player.getUniqueId());
            if (activeForm != null && activeForm != this) {
                activeForm.revertForm(player);
            }

            player.setMetadata("druid_form", new FixedMetadataValue(plugin, formName));
            setupDisguise(currentDisguise);
            currentDisguise.setNotifyBar(DisguiseConfig.NotifyBar.NONE);
            DisguiseAPI.disguiseEntity(player, currentDisguise);
            applyFormEffects(player);

            // Track this form
            activeShapeshifts.put(player.getUniqueId(), this);

            // Consume resources and broadcast message
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
            if (playerData != null) {
                plugin.getSkillManager().consumeResources(playerData, this);
            }
            broadcastLocalSkillMessage(player, "§2[Druid] " + player.getName() + " transforms into a " + formName + "!");
            setSkillSuccess(true);
        }
    }

    protected void revertForm(Player player) {
        if (DisguiseAPI.isDisguised(player)) {
            DisguiseAPI.undisguiseToAll(player);
        }
        player.removeMetadata("druid_form", plugin);
        removeFormEffects(player);

        // Remove from tracking
        activeShapeshifts.remove(player.getUniqueId());

        broadcastLocalSkillMessage(player, "§2[Druid] " + player.getName() + " reverts from " + formName + " form!");
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());
    }

    private void handleShapeshiftSkill(Player player) {
        if (player.hasMetadata("druid_form")) {
            String currentForm = player.getMetadata("druid_form").get(0).asString();
            if (currentForm.equals(formName)) {
                revertForm(player);
                return;
            }
            player.sendMessage("§c✦ You must revert from " + currentForm + " form before taking a new form!");
            return;
        }
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());
        transformPlayer(player);
    }

    // Static cleanup methods
    public static void cleanupPlayer(Player player) {
        DruidShapeshiftSkill activeForm = activeShapeshifts.get(player.getUniqueId());
        if (activeForm != null) {
            activeForm.revertForm(player);
        }
    }

    public static void cleanupAll() {
        for (Map.Entry<UUID, DruidShapeshiftSkill> entry : new HashMap<>(activeShapeshifts).entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                entry.getValue().revertForm(player);
            }
        }
        activeShapeshifts.clear();
    }

    // Abstract methods to be implemented by specific animal forms
    protected abstract Disguise createDisguise();
    protected abstract void setupDisguise(Disguise disguise);
    protected abstract void applyFormEffects(Player player);
    protected abstract void removeFormEffects(Player player);
}