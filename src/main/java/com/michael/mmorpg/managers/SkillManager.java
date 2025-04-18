package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.arcanist.*;
import com.michael.mmorpg.skills.bandolier.*;
import com.michael.mmorpg.skills.bard.*;
import com.michael.mmorpg.skills.berserker.*;
import com.michael.mmorpg.skills.buccaneer.*;
import com.michael.mmorpg.skills.chronomancer.*;
import com.michael.mmorpg.skills.chainwarden.*;
import com.michael.mmorpg.skills.darkblade.*;
import com.michael.mmorpg.skills.druid.*;
import com.michael.mmorpg.skills.elementalranger.*;
import com.michael.mmorpg.skills.engineer.*;
import com.michael.mmorpg.skills.guardian.*;
import com.michael.mmorpg.skills.healer.HealSkill;
import com.michael.mmorpg.skills.hunter.*;
import com.michael.mmorpg.skills.mage.WeakFireballSkill;
import com.michael.mmorpg.skills.ninja.*;
import com.michael.mmorpg.skills.priest.*;
import com.michael.mmorpg.skills.renegade.*;
import com.michael.mmorpg.skills.rogue.PunchSkill;
import com.michael.mmorpg.skills.skyknight.*;
import com.michael.mmorpg.skills.toxicologist.*;
import com.michael.mmorpg.skills.unused.CrushingWaveSkill;
import com.michael.mmorpg.skills.unused.GarroteSkill;
import com.michael.mmorpg.skills.warrior.StrikeSkill;
import com.michael.mmorpg.skills.windwaker.*;
import com.michael.mmorpg.skills.frostmage.*;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.skills.zcommon.DeathToAllSkill;
import com.michael.mmorpg.skills.zcommon.ForageSkill;
import com.michael.mmorpg.status.CCType;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SkillManager {
    private final MinecraftMMORPG plugin;
    // Maps skill names to their implementing classes
    private final Map<String, Class<? extends Skill>> registeredSkills;
    // Maps player UUIDs to their skill cooldowns
    private final Map<UUID, Map<String, Long>> cooldowns;
    // Tracks global cooldown for all skills
    private final Map<UUID, Long> globalCooldowns = new HashMap<>();
    private static final long GLOBAL_COOLDOWN = 500; // Half second global cooldown

    public SkillManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.registeredSkills = new HashMap<>();
        this.cooldowns = new HashMap<>();
        registerSkills();
    }

    // Centralized messages for consistent feedback
    public static class SkillMessages {
        public static final String COOLDOWN = ChatColor.RED + "⏰ This skill is on cooldown for %s seconds!";
        public static final String NO_MANA = ChatColor.BLUE + "✧ You need %s more mana!";
        public static final String NO_STAMINA = ChatColor.GREEN + "⚡ You need %s more stamina!";
        public static final String NO_HEALTH = ChatColor.RED + "❤ You need %s more health!";
        public static final String NO_ACCESS = ChatColor.RED + "❌ Your class cannot use this skill!";
        public static final String LEVEL_REQUIREMENT = ChatColor.YELLOW + "⭐ You need to be level %s to use this skill!";
        public static final String SKILL_NOT_FOUND = ChatColor.RED + "❌ Unknown skill: %s";
    }

    private void registerSkills() {
        // Register all available skills
        registerSkill("bolt", BoltSkill.class);
        registerSkill("gust", Gust.class);
        registerSkill("Tornado", TornadoSkill.class);
        registerSkill("FrostNova", FrostNovaSkill.class);
        registerSkill("Icespike", IceSpikeSkill.class);
        registerSkill("Blizzard", BlizzardSkill.class);
        registerSkill("IceBolt", IceBoltSkill.class);
        registerSkill("IceBlock", IceBlockSkill.class);
        registerSkill("wolfform", WolfFormSkill.class);
        registerSkill("pandaForm", PandaFormSkill.class);
        registerSkill("regrowth", RegrowthSkill.class);
        registerSkill("Wrath", WrathSkill.class);
        registerSkill("rip", RipSkill.class);
        registerSkill("healingtouch", HealingTouchSkill.class);
        registerSkill("root", RootSkill.class);
        registerSkill("camouflage", CamouflageSkill.class);
        registerSkill("icebarrage", IceBarrageSkill.class);
        registerSkill("IceShield", IceShieldSkill.class);
        registerSkill("SummonIceBlock", SummonIceBlockSkill.class);
        registerSkill("FrostWalk", FrostWalkSkill.class);
        registerSkill("GaleForce", GaleForceSkill.class);
        registerSkill("WindWall", WindWallSkill.class);
        registerSkill("SlipStream", SlipstreamSkill.class);
        registerSkill("WindChop", WindChopSkill.class);
        registerSkill("Updraft", UpdraftSkill.class);
        registerSkill("Stealth", StealthSkill.class);
        registerSkill("Rebirth", RebirthSkill.class);
        registerSkill("Envenom", EnvenomSkill.class);
        registerSkill("Toxicmist", ToxicMistSkill.class);
        registerSkill("Poisonsplash", PoisonSplashSkill.class);
        registerSkill("ToxicStrike", ToxicStrikeSkill.class);
        registerSkill("Venomousembrace", VenomousEmbraceSkill.class);
        registerSkill("Toxintransfusion", ToxinTransfusionSkill.class);
        registerSkill("Poisonbrewing", PoisonBrewingSkill.class);
        registerSkill("Poisonshiv", PoisonShivSkill.class);
        registerSkill("Seizechain", SeizeChainSkill.class);
        registerSkill("chainshield", ChainShieldSkill.class);
        registerSkill("lash", LashSkill.class);
        registerSkill("Chainanchor", ChainAnchorSkill.class);
        registerSkill("Soullink", SoulLinkSkill.class);
        registerSkill("Deathflail", DeathFlailSkill.class);
        registerSkill("MysticChainStrike", MysticChainStrikeSkill.class);
        registerSkill("SummonArrow", SummonArrowSkill.class);
        registerSkill("PiercingArrow", PiercingArrowSkill.class);
        registerSkill("summonpet", SummonPetSkill.class);
        registerSkill("dismisspet", DismissPetSkill.class);
        registerSkill("AimedShot", AimedShotSkill.class);
        registerSkill("maim", MaimSkill.class);
        registerSkill("foottrap", FootTrapSkill.class);
        registerSkill("disengage", DisengageSkill.class);
        registerSkill("track", TrackSkill.class);
        registerSkill("Mend", MendSkill.class);
        registerSkill("radiantburst", RadiantBurstSkill.class);
        registerSkill("prayerofpreservation", PrayerOfPreservationSkill.class);
        registerSkill("desperateprayer", DesperatePrayerSkill.class);
        registerSkill("soulward", SoulWardSkill.class);
        registerSkill("smite", SmiteSkill.class);
        registerSkill("rebuke", RebukeSkill.class);
        registerSkill("condemnation", CondemnationSkill.class);
        registerSkill("proximitymine", ProximityMineSkill.class);
        registerSkill("AutoTurret", AutoTurretSkill.class);
        registerSkill("overclockedweapon", OverclockedWeaponSkill.class);
        registerSkill("rocketjump", RocketJumpSkill.class);
        registerSkill("megacannon", MegaCannonSkill.class);
        registerSkill("net", NetSkill.class);
        registerSkill("sonicwave", SonicWaveSkill.class);
        registerSkill("resonatingstrike", ResonatingStrikeSkill.class);
        registerSkill("dragonkick", DragonKickSkill.class);
        registerSkill("phantomrush", PhantomRushSkill.class);
        registerSkill("crushingwave", CrushingWaveSkill.class);
        registerSkill("boomstick", BoomstickSkill.class);
        registerSkill("gearwingphysician", GearwingPhysicianSkill.class);
        registerSkill("cloudborn", CloudbornSkill.class);
        registerSkill("aerialgrasp", AerialGraspSkill.class);
        registerSkill("divebomb", DiveBombSkill.class);
        registerSkill("windspear", WindSpearSkill.class);
        registerSkill("cloudbounce", CloudBounceSkill.class);
        registerSkill("SpiralPierce", SpiralPierceSkill.class);
        registerSkill("Wingslap", WingSlapSkill.class);
        registerSkill("battlecry", BattleCrySkill.class);
        registerSkill("swiftanthem", SwiftAnthemSkill.class);
        registerSkill("lullaby", LullabySkill.class);
        registerSkill("manasong", ManaSongSkill.class);
        registerSkill("warchant", WarChantSkill.class);
        registerSkill("healinghymn", HealingHymnSkill.class);
        registerSkill("sonicboom", SonicBoomSkill.class);
        registerSkill("resonantshield", ResonantShieldSkill.class);
        registerSkill("fivepointpalm", FivePointPalmSkill.class);
        registerSkill("spinningbirdkick", SpinningBirdKickSkill.class);
        registerSkill("groundbreaker", GroundBreakerSkill.class);
        registerSkill("flintlock", FlintLockSkill.class);
        registerSkill("powderkeg", PowderKegSkill.class);
        registerSkill("peck", PeckSkill.class);
        registerSkill("plunder", PlunderSkill.class);
        registerSkill("SliceAndDice", SliceAndDiceSkill.class);
        registerSkill("DeadManWalking", DeadManWalkingSkill.class);
        registerSkill("CitrusForge", CitrusForgeSkill.class);
        registerSkill("RollLeft", RollLeftSkill.class);
        registerSkill("RollRight", RollRightSkill.class);
        registerSkill("rapidfire", RapidFireSkill.class);
        registerSkill("uppercut", UppercutSkill.class);
        registerSkill("pushoff", PushOffSkill.class);
        registerSkill("bladebreaker", BladebreakerSkill.class);
        registerSkill("throwingaxe", ThrowingAxeSkill.class);
        registerSkill("whirlwind", WhirlwindSkill.class);
        registerSkill("goredrinker", GoredrinkerSkill.class);
        registerSkill("headbutt", HeadButtSkill.class);
        registerSkill("Charge", ChargeSkill.class);
        registerSkill("Rupture", RuptureSkill.class);
        registerSkill("Taunt", TauntSkill.class);
        registerSkill("guts", GutsSkill.class);
        registerSkill("backflip", BackflipSkill.class);
        registerSkill("vanish", VanishSkill.class);
        registerSkill("Kick", KickSkill.class);
        registerSkill("Shuriken", ShurikenSkill.class);
        registerSkill("SmokeBomb", SmokeBombSkill.class);
        registerSkill("BackStab", BackstabPassive.class);
        registerSkill("ShadowStrike", ShadowStrike.class);
        registerSkill("Garrote", GarroteSkill.class);
        registerSkill("DustTechnique", DustTechniqueSkill.class);
        registerSkill("backstab", BackstabPassive.class);
        registerSkill("shadowclones", ShadowClonesSkill.class);
        registerSkill("clone", CloneSkill.class);
        registerSkill("tsunamicall", TsunamiCallSkill.class);
        registerSkill("nethershot", NetherShotSkill.class);
        registerSkill("enderarrow", EnderArrowSkill.class);
        registerSkill("icearrow", IceArrowSkill.class);
        registerSkill("ignite", IgniteSkill.class);
        registerSkill("grandtree", GrandTreeSkill.class);
        registerSkill("rewind", RewindSkill.class);
        registerSkill("timeglitch", TimeGlitchSkill.class);
        registerSkill("futuresight", FutureSightSkill.class);
        registerSkill("TimeStop", TimeStopSkill.class);
        registerSkill("chronoshift", ChronoShiftSkill.class);
        registerSkill("timewave", TimeWaveSkill.class);
        registerSkill("EraserBeam", EraserBeamSkill.class);
        registerSkill("TemporalExile", TemporalExileSkill.class);
        registerSkill("shadowball", ShadowBallSkill.class);
        registerSkill("darkclaw", DarkClawSkill.class);
        registerSkill("nightmare", NightmareSkill.class);
        registerSkill("shadowstep", ShadowStepSkill.class);
        registerSkill("SoulShatter", SoulShatterSkill.class);
        registerSkill("VoidPierce", VoidPierceSkill.class);
        registerSkill("SoulSiphon", SoulSiphonSkill.class);
        registerSkill("Terrorize", TerrorizeSkill.class);
        registerSkill("BulWark", BulwarkSkill.class);
        registerSkill("ShieldBash", ShieldBashSkill.class);
        registerSkill("BannerOfProtection", BannerOfProtectionSkill.class);
        registerSkill("arcanemissiles", ArcaneMissilesSkill.class);
        registerSkill("polymorph", PolymorphSkill.class);
        registerSkill("blink", BlinkSkill.class);
        registerSkill("groupteleport", GroupTeleportSkill.class);
        registerSkill("evocation", EvocationSkill.class);
        registerSkill("fireball", FireballSkill.class);
        registerSkill("counterspell", CounterspellSkill.class);
        registerSkill("manabeam", ManaBeamSkill.class);
        registerSkill("Surge", SurgeSkill.class);
        registerSkill("Ram", RamSkill.class);
        registerSkill("MagicWard", MagicWardSkill.class);
        registerSkill("Unbreakable", UnbreakableSkill.class);
        registerSkill("SpawnPortal", SpawnPortalSkill.class);
        registerSkill("strike", StrikeSkill.class);
        registerSkill("forage", ForageSkill.class);
        registerSkill("punch", PunchSkill.class);
        registerSkill("weakfireball", WeakFireballSkill.class);
        registerSkill("deathtoall", DeathToAllSkill.class);
        registerSkill("heal", HealSkill.class);

    }

    public void registerSkill(String name, Class<? extends Skill> skillClass) {
        registeredSkills.put(name.toLowerCase(), skillClass);
    }


    public boolean executeSkill(Player player, String skillName, String[] args) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) return false;

        // Replace the existing block in SkillManager
        if (player.hasMetadata("block_skills") &&
                !skillName.equalsIgnoreCase("Vanish") &&
                !skillName.equalsIgnoreCase("ShadowStrike") &&
                !skillName.equalsIgnoreCase("SmokeBomb")) {
            player.sendMessage("§c✦ You cannot use skills while vanished!");
            return false;
        }

        // Check if player is affected by CC that prevents skill usage
        if (isPlayerCrowdControlled(player)) {
            String ccType = getCCTypeMessage(player);
            player.sendMessage("§c✦ Cannot use skills while " + ccType + "!");
            return false;
        }

        // In SkillManager.executeSkill method, add this check:
        if (player.hasMetadata("arcane_missiles_lock") &&
                !skillName.equalsIgnoreCase("arcanemissiles")) {
            player.sendMessage("§c✦ You cannot cast skills while channeling Arcane Missiles!");
            return false;
        }

        Class<? extends Skill> skillClass = registeredSkills.get(skillName.toLowerCase());
        if (skillClass == null) {
            player.sendMessage(String.format(SkillMessages.SKILL_NOT_FOUND, skillName));
            return false;
        }

        try {
            ConfigurationSection skillConfig = playerData.getGameClass().getSkillConfig(skillName);
            if (skillConfig == null) {
                player.sendMessage(SkillMessages.NO_ACCESS);
                return false;
            }

            Skill skill = createSkill(skillClass, skillConfig);
            if (skill == null) return false;

            if (!skill.isToggleableSkill() || !skill.isToggleActive(player)) {
                if (!checkRequirements(player, playerData, skill)) {
                    return false;
                }
            }

            // Skip global cooldown check for note skills
            boolean isNoteSkill = skillName.toLowerCase().endsWith("note");
            if (!isNoteSkill && isOnGlobalCooldown(player)) {
                player.sendMessage("§c✦ You must wait 0.5 seconds before casting another skill!");
                return false;
            }

            if (player.hasMetadata("casting")) {
                player.sendMessage("§c✦ You are already casting a skill!");
                return false;
            }

            // Execute the skill with arguments
            skill.execute(player, args);

            if (skill.wasSuccessful()) {
                // Only set global cooldown for non-note skills
                if (!isNoteSkill) {
                    globalCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                }
                return true;
            }

            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isPlayerCrowdControlled(Player player) {
        return plugin.getStatusEffectManager().hasEffect(player, CCType.SILENCE) ||
                plugin.getStatusEffectManager().hasEffect(player, CCType.STUN) ||
                plugin.getStatusEffectManager().hasEffect(player, CCType.CHARM) ||
                plugin.getStatusEffectManager().hasEffect(player, CCType.SLEEP) ||
                plugin.getStatusEffectManager().hasEffect(player, CCType.FEAR);
    }

    private String getCCTypeMessage(Player player) {
        if (plugin.getStatusEffectManager().hasEffect(player, CCType.SILENCE)) return "silenced";
        if (plugin.getStatusEffectManager().hasEffect(player, CCType.STUN)) return "stunned";
        if (plugin.getStatusEffectManager().hasEffect(player, CCType.CHARM)) return "charmed";
        if (plugin.getStatusEffectManager().hasEffect(player, CCType.SLEEP)) return "asleep";
        if (plugin.getStatusEffectManager().hasEffect(player, CCType.FEAR)) return "feared";
        return "disabled";
    }

    private Skill createSkill(Class<? extends Skill> skillClass, ConfigurationSection config) {
        try {
            return skillClass.getConstructor(ConfigurationSection.class).newInstance(config);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean checkRequirements(Player player, PlayerData playerData, Skill skill) {
        // Check level requirement
        if (playerData.getLevel() < skill.getLevelRequired()) {
            player.sendMessage(String.format(SkillMessages.LEVEL_REQUIREMENT, skill.getLevelRequired()));
            return false;
        }

        // Add cooldown check here
        if (isOnCooldown(player, skill.getName())) {
            long remainingSeconds = getRemainingCooldown(player, skill.getName()) / 1000;
            player.sendMessage(String.format(SkillMessages.COOLDOWN, remainingSeconds));
            return false;
        }

        // Check resources
        return checkResources(player, playerData, skill);
    }

    public boolean checkResources(Player player, PlayerData playerData, Skill skill) {
        // Check mana cost
        if (skill.getManaCost() > 0 && playerData.getCurrentMana() < skill.getManaCost()) {
            player.sendMessage(String.format(SkillMessages.NO_MANA,
                    String.format("%.1f", skill.getManaCost() - playerData.getCurrentMana())));
            return false;
        }

        // Check stamina cost
        if (skill.getStaminaCost() > 0 && playerData.getCurrentStamina() < skill.getStaminaCost()) {
            player.sendMessage(String.format(SkillMessages.NO_STAMINA,
                    String.format("%.1f", skill.getStaminaCost() - playerData.getCurrentStamina())));
            return false;
        }

        // Check health cost
        if (skill.getHealthCost() > 0 && playerData.getCurrentHealth() < skill.getHealthCost()) {
            player.sendMessage(String.format(SkillMessages.NO_HEALTH,
                    String.format("%.1f", skill.getHealthCost() - playerData.getCurrentHealth())));
            return false;
        }

        return true;
    }

    private boolean isOnGlobalCooldown(Player player) {
        Long lastCast = globalCooldowns.get(player.getUniqueId());
        if (lastCast == null) return false;

        long remainingCooldown = lastCast + GLOBAL_COOLDOWN - System.currentTimeMillis();
        return remainingCooldown > 0;
    }

    // Consume resources when a skill is used
    public void consumeResources(PlayerData playerData, Skill skill) {
        if (skill.getManaCost() > 0) {
            playerData.useMana(skill.getManaCost());
        }
        if (skill.getStaminaCost() > 0) {
            playerData.useStamina(skill.getStaminaCost());
        }
        if (skill.getHealthCost() > 0) {
            playerData.setCurrentHealth(playerData.getCurrentHealth() - skill.getHealthCost());
        }
    }

    // Set cooldown for a skill
    public void setCooldown(Player player, String skillName, long cooldown) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(skillName.toLowerCase(), System.currentTimeMillis() + cooldown);
    }

    // Check if a skill is on cooldown
    public boolean isOnCooldown(Player player, String skillName) {
        return getRemainingCooldown(player, skillName) > 0;
    }

    public Map<String, Long> getActiveCooldowns(UUID playerId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return new HashMap<>();

        // Filter out expired cooldowns
        return playerCooldowns.entrySet().stream()
                .filter(entry -> entry.getValue() > System.currentTimeMillis())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    public Map<String, Long> getPlayerCooldowns(Player player) {
        return getActiveCooldowns(player.getUniqueId());
    }

    // Get remaining cooldown time for a skill
    public long getRemainingCooldown(Player player, String skillName) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;

        Long cooldownEnd = playerCooldowns.get(skillName.toLowerCase());
        if (cooldownEnd == null) return 0;

        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public Map<String, Long> getCurrentPlayerCooldowns(UUID playerId) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) return new HashMap<>();

        return playerCooldowns.entrySet().stream()
                .filter(entry -> entry.getValue() > System.currentTimeMillis())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    // Display available skills to the player
    public void showSkillList(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) return;

        GameClass gameClass = playerData.getGameClass();
        int playerLevel = playerData.getLevel();

        player.sendMessage("§6=== Available Skills ===");

        for (Map.Entry<String, ConfigurationSection> entry : gameClass.getAllSkillConfigs().entrySet()) {
            String skillName = entry.getKey();
            ConfigurationSection skillConfig = entry.getValue();

            int requiredLevel = skillConfig.getInt("unlocklevel", 1);
            String description = skillConfig.getString("description", "No description");
            double manaCost = skillConfig.getDouble("manacost", 0);
            double staminaCost = skillConfig.getDouble("staminacost", 0);
            boolean hasCastTime = skillConfig.getBoolean("hascasttime", false);
            boolean isToggleable = skillConfig.getBoolean("istoggleableskill", false);

            boolean unlocked = playerLevel >= requiredLevel;
            String status = unlocked ? "§a[Unlocked]" : "§c[Locked - Level " + requiredLevel + "]";

            player.sendMessage(status + " §f" + skillName);
            player.sendMessage("§7  " + description);
            if (manaCost > 0) player.sendMessage("§7  Mana Cost: " + manaCost);
            if (staminaCost > 0) player.sendMessage("§7  Stamina Cost: " + staminaCost);
            if (hasCastTime) player.sendMessage("§7  Cast Time: " + skillConfig.getDouble("casttime", 0) + "s");
            if (isToggleable) player.sendMessage("§7  Toggle Ability");
        }
    }

    public Skill getSkillInstance(String skillName) {
        Class<? extends Skill> skillClass = registeredSkills.get(skillName.toLowerCase());
        if (skillClass == null) return null;

        try {
            // Create a new instance with empty config for temporary usage
            ConfigurationSection config = new org.bukkit.configuration.MemoryConfiguration();
            return skillClass.getConstructor(ConfigurationSection.class).newInstance(config);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Check if a skill exists
    public boolean skillExists(String skillName) {
        return registeredSkills.containsKey(skillName.toLowerCase());
    }
}