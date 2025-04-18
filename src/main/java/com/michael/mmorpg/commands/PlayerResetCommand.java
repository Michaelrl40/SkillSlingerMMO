package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffect;

public class PlayerResetCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public PlayerResetCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        resetPlayer(player);
        player.sendMessage("§a✦ Your player state has been fully reset!");

        return true;
    }

    private void resetPlayer(Player player) {
        // Reset all attributes to default values
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        player.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(4.0);
        player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.1);
        player.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
        player.getAttribute(Attribute.ARMOR).setBaseValue(0.0);
        player.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(0.0);
        player.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.0);

        // Reset health to max
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());

        // Clear all potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Remove metadata
        player.removeMetadata("disarmed", plugin);
        player.removeMetadata("druid_form", plugin);

        // Clear status effects and immunities
        plugin.getStatusEffectManager().clearAllEffects(player);

        // Reset vanilla mechanics
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setGlowing(false);
        player.setGravity(true);
        player.setInvisible(false);
        player.setSilent(false);

        // Reset combat-related flags
        player.setFireTicks(0);
        player.setNoDamageTicks(0);
        player.setMaximumNoDamageTicks(20);

        // If using disguises, remove them
        if (plugin.getServer().getPluginManager().getPlugin("LibsDisguises") != null) {
            me.libraryaddict.disguise.DisguiseAPI.undisguiseToAll(player);
        }

        // Reset game mode and other settings
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);

    }
}