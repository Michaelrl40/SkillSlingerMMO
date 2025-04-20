package com.michael.mmorpg;

import com.michael.mmorpg.chatclasses.ChannelCommand;
import com.michael.mmorpg.chatclasses.ChatListener;
import com.michael.mmorpg.chatclasses.ChatManager;
import com.michael.mmorpg.commands.*;
import com.michael.mmorpg.deathchest.DeathChestCommand;
import com.michael.mmorpg.deathchest.DeathChestListener;
import com.michael.mmorpg.deathchest.DeathChestManager;
import com.michael.mmorpg.dungeon.*;
import com.michael.mmorpg.graveyard.GraveyardCommand;
import com.michael.mmorpg.graveyard.GraveyardListener;
import com.michael.mmorpg.graveyard.GraveyardManager;
import com.michael.mmorpg.graveyard.WorldLoadListener;
import com.michael.mmorpg.guild.GuildCommand;
import com.michael.mmorpg.guild.GuildManager;
import com.michael.mmorpg.handlers.ShieldHandler;
import com.michael.mmorpg.listeners.*;
import com.michael.mmorpg.managers.*;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.skills.arcanist.SpawnPortalSkill;
import com.michael.mmorpg.skills.darkblade.VoidPierceSkill;
import com.michael.mmorpg.skills.druid.DruidShapeshiftSkill;
import com.michael.mmorpg.skills.elementalranger.GrandTreeSkill;
import com.michael.mmorpg.skills.hunter.BowMasterySkill;
import com.michael.mmorpg.status.StatusEffectManager;
import com.michael.mmorpg.title.TitleCommand;
import com.michael.mmorpg.title.TitleManager;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import static com.michael.mmorpg.skills.Skill.plugin;

public class MinecraftMMORPG extends JavaPlugin {
    private static MinecraftMMORPG instance;
    private ClassManager classManager;
    private PlayerManager playerManager;
    private ConfigManager configManager;
    private SkillManager skillManager;
    private CombatManager combatManager;
    private WeaponManager weaponManager;
    private DatabaseManager databaseManager;
    private StatusEffectManager statusEffectManager;
    private DamageDisplayManager damageDisplayManager;
    private PartyManager partyManager;
    private LevelCelebrationManager levelCelebrationManager;
    private ResourceManager resourceManager;
    private CustomPotionManager customPotionManager;
    private PotionCraftingManager potionCraftingManager;
    private CampfireManager campfireManager;
    private BuccaneerParrotManager buccaneerParrotManager;
    private PowderKegListener powderKegListener;
    private DeadManManager deadManManager;
    private CitrusForgeManager citrusForgeManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private CastingBarManager castingBarManager;
    private SkillListManager skillListManager;
    private BankManager bankManager;
    private AuctionHouseManager auctionHouseManager;
    private WorldGuardManager worldGuardManager;
    private AbsorptionShieldManager absorptionShieldManager;
    private ShieldHandler shieldHandler;
    public static StateFlag ALLOW_COMBAT_FLAG;
    private ChatManager chatManager;
    private GraveyardManager graveyardManager;
    private DeathChestManager deathChestManager;
    private TitleManager titleManager;
    private DungeonManager dungeonManager;
    private DungeonKey dungeonKeyManager;
    private GuildManager guildManager;






    @Override
    public void onLoad() {
        instance = this;

        // Register our custom flag
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            // Create the flag with default value FALSE (deny combat)
            StateFlag flag = new StateFlag("allow-combat", false);
            registry.register(flag);
            ALLOW_COMBAT_FLAG = flag;
            System.out.println("[Skillslinger] Successfully registered allow-combat flag");
        } catch (Exception e) {
            System.out.println("[Skillslinger] Error registering custom flag: " + e.getMessage());
            e.printStackTrace();
        }
    }



    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        databaseManager = new DatabaseManager(this);


        // Initialize configurations
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        GameClass.initializeLevelRanges(getConfig());
        databaseManager.initDatabase();
        databaseManager.initGraveyardTables();
        databaseManager.initDeathChestTables();
        databaseManager.initDungeonTables();
        SpawnPortalSkill.cleanupStuckPortals(this);
        SpawnPortalSkill.startPortalCleanupTask(this);


        // Initialize other managers that rely on the database
        classManager = new ClassManager(this);
        playerManager = new PlayerManager(this);
        skillManager = new SkillManager(this);
        combatManager = new CombatManager(this);
        weaponManager = new WeaponManager(this);
        statusEffectManager = new StatusEffectManager(this);
        damageDisplayManager = new DamageDisplayManager(this);
        partyManager = new PartyManager(this);
        levelCelebrationManager = new LevelCelebrationManager(this);
        resourceManager = new ResourceManager(this);
        customPotionManager = new CustomPotionManager(this);
        potionCraftingManager = new PotionCraftingManager(this, customPotionManager);
        campfireManager = new CampfireManager(this);
        buccaneerParrotManager = new BuccaneerParrotManager(this);
        powderKegListener = new PowderKegListener(this);
        deadManManager = new DeadManManager(this);
        citrusForgeManager = new CitrusForgeManager(this);
        economyManager = new EconomyManager(this);
        shopManager = new ShopManager(this);
        castingBarManager = new CastingBarManager(this);
        skillListManager = new SkillListManager(this);
        bankManager = new BankManager(this);
        auctionHouseManager = new AuctionHouseManager(this);
        absorptionShieldManager = new AbsorptionShieldManager(this);
        shieldHandler = new ShieldHandler(this);
        worldGuardManager = new WorldGuardManager(this);
        chatManager = new ChatManager(this);
        graveyardManager = new GraveyardManager(this);
        deathChestManager = new DeathChestManager(this);
        titleManager = new TitleManager(this);
        dungeonManager = new DungeonManager(this);
        dungeonKeyManager = new DungeonKey(this);
        guildManager = new GuildManager(this);






        Skill.setPlugin(this);
        graveyardManager.ensureDefaultGraveyard();


        // Register PlaceholderAPI expansion (do this AFTER initializing managers)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                getLogger().info("Registering placeholders with PlaceholderAPI");
                new com.michael.mmorpg.PlaceHolder.MMORPGPlaceholders(this).register();
            }
        }, 40L); // Wait 2 seconds (40 ticks) after server start to register








        // Register events
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new ExperienceListener(this), this);
        getServer().getPluginManager().registerEvents(new ArmorEquipListener(this), this);
        getServer().getPluginManager().registerEvents(new HungerListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillCancelListener(this), this);
        getServer().getPluginManager().registerEvents(new ChainLinkDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new BowMasterySkill(getConfig()), this);
        getServer().getPluginManager().registerEvents(new CombatPotionListener(this), this);
        getServer().getPluginManager().registerEvents(new PotionListener(this, customPotionManager), this);
        getServer().getPluginManager().registerEvents(new DisengageListener(this), this);
        getServer().getPluginManager().registerEvents(new SoulWardListener(this), this);
        getServer().getPluginManager().registerEvents(new TurretListener(this), this);
        getServer().getPluginManager().registerEvents(new MegaCannonListener(this), this);
        getServer().getPluginManager().registerEvents(new OverclockedWeaponListener(this), this);
        getServer().getPluginManager().registerEvents(new CloudbornListener(this), this);
        getServer().getPluginManager().registerEvents(new ElytraBoostListener(this), this);
        getServer().getPluginManager().registerEvents(new PowderKegListener(this), this);
        getServer().getPluginManager().registerEvents(buccaneerParrotManager, this);
        getServer().getPluginManager().registerEvents(new DeadManListener(this), this);
        getServer().getPluginManager().registerEvents(new CitrusForgeListener(this), this);
        getServer().getPluginManager().registerEvents(new BandolierArrowListener(this), this);
        getServer().getPluginManager().registerEvents(new HealingDisplayListener(this), this);
        getServer().getPluginManager().registerEvents(new BackstabListener(this), this);
        getServer().getPluginManager().registerEvents(new TsunamiCallListener(this), this);
        getServer().getPluginManager().registerEvents(new NetherShotListener(this), this);
        getServer().getPluginManager().registerEvents(new EnderArrowListener(this), this);
        getServer().getPluginManager().registerEvents(new ShieldEquipListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantmentListener(this), this);
        getServer().getPluginManager().registerEvents(new ShieldListener(this), this);
        getServer().getPluginManager().registerEvents(new VoidPierceSkill(getConfig()), this);
        getServer().getPluginManager().registerEvents(new ElementalArrowListener(this), this);
        getServer().getPluginManager().registerEvents(new EconomyInventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new AbsorptionItemListener(this, absorptionShieldManager), this);
        getServer().getPluginManager().registerEvents(new PartyChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new GraveyardListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathChestListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemBindListener(this), this);
        getServer().getPluginManager().registerEvents(new DungeonKeyListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldLoadListener(this), this);




        // Register commands
        getCommand("class").setExecutor(new ClassCommand(this));
        getCommand("skill").setExecutor(new SkillCommand(this));
        getCommand("ccclear").setExecutor(new CCClearCommand(this));
        getCommand("resetplayer").setExecutor(new PlayerResetCommand(this));
        getCommand("party").setExecutor(new PartyCommand(this));
        getCommand("maxlevel").setExecutor(new MaxLevelCommand(this));
        getCommand("givepotion").setExecutor(new GivePotionCommand(this, customPotionManager));
        getCommand("clearholograms").setExecutor(new ClearHologramsCommand(this));
        getCommand("coins").setExecutor(new CoinsCommand(this));
        getCommand("bank").setExecutor(new BankCommand(this));
        getCommand("auctionhouse").setExecutor(new AuctionHouseCommand(this));
        getCommand("sellgold").setExecutor(new SellGoldCommand(this));
        getCommand("clearportals").setExecutor(new ClearPortalsCommand(this));
        getCommand("channel").setExecutor(new ChannelCommand(this));
        getCommand("ch").setExecutor(new ChannelCommand(this));
        getCommand("graveyard").setExecutor(new GraveyardCommand(this));
        getCommand("deathchest").setExecutor(new DeathChestCommand(this));
        getCommand("bind").setExecutor(new BindCommand(this));
        getCommand("title").setExecutor(new TitleCommand(this));
        getCommand("dungeon").setExecutor(new DungeonCommand(this));
        getCommand("dungeoncreate").setExecutor(new DungeonCreateCommand(this));
        getCommand("dungeonedit").setExecutor(new DungeonEditCommand(this));
        getCommand("dungeonentrance").setExecutor(new DungeonEntranceCommand(this));
        getCommand("dungeonkey").setExecutor(new DungeonKeyCommand(this));
        getCommand("dungeonmaxtime").setExecutor(new DungeonMaxTimeCommand(this));
        getCommand("guild").setExecutor(new GuildCommand(this, guildManager));
        getCommand("randomtp").setExecutor(new RandomTeleportCommand(this));




        startAutoSave();
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DruidShapeshiftSkill.cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        dungeonManager.handlePlayerDeath(player);
    }


    public static MinecraftMMORPG getInstance() {
        return instance;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public WeaponManager getWeaponManager() {
        return weaponManager;
    }

    public DamageDisplayManager getDamageDisplayManager() {
        return damageDisplayManager;
    }

    public StatusEffectManager getStatusEffectManager() {
        return statusEffectManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public LevelCelebrationManager getLevelCelebrationManager() {
        return levelCelebrationManager;
    }


    public BuccaneerParrotManager getBuccaneerParrotManager() {
        return buccaneerParrotManager;
    }

    public PowderKegListener getPowderKegListener() {
        return powderKegListener;
    }

    public DeadManManager getDeadManManager() {
        return deadManManager;
    }

    public CitrusForgeManager getCitrusForgeManager() {
        return citrusForgeManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CastingBarManager getCastingBarManager() {
        return castingBarManager;
    }

    public SkillListManager getSkillListManager() {
        return skillListManager;
    }

    public BankManager getBankManager() {
        return bankManager;
    }

    public AuctionHouseManager getAuctionHouseManager() {
        return auctionHouseManager;
    }

    public WorldGuardManager getWorldGuardManager() {
        return worldGuardManager;
    }

    public AbsorptionShieldManager getAbsorptionShieldManager() {
        return absorptionShieldManager;
    }

    public ShieldHandler getShieldHandler() {
        return shieldHandler;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public GraveyardManager getGraveyardManager() {
        return graveyardManager;
    }

    public DeathChestManager getDeathChestManager() {
        return deathChestManager;
    }

    public TitleManager getTitleManager() {
        return titleManager;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }







    @Override
    public void onDisable() {
        plugin.getLogger().info("Saving all player data before shutdown...");
        // Save all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getLogger().info("Saving data for " + player.getName());
            playerManager.savePlayer(player);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            bankManager.unloadPlayerBank(player);
        }

        if (dungeonManager != null) {
            dungeonManager.shutdown();
        }

        plugin.getLogger().info("Saving bank and auction data before shutdown...");

        plugin.getLogger().info("Closing database connection...");
        databaseManager.close();

        GrandTreeSkill.cleanupAllTrees();
        databaseManager.close();
        DruidShapeshiftSkill.cleanup();
        SpawnPortalSkill.cleanupStuckPortals(this);
    }

    private void startAutoSave() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerManager.savePlayer(player);  // Changed from savePlayerData to savePlayer
            }
            getLogger().info("Auto-saved player data");
        }, 6000L, 6000L); // Save every 5 minutes (6000 ticks)
    }

}



