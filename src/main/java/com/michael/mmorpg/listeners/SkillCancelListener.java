package com.michael.mmorpg.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.Skill;

public class SkillCancelListener implements Listener {
    private final MinecraftMMORPG plugin;

    public SkillCancelListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            if (player.hasMetadata("casting")) {
                Skill skill = (Skill) player.getMetadata("casting").get(0).value();
                skill.cancelCast(player);
                player.removeMetadata("casting", plugin);
            }
        }
    }
}