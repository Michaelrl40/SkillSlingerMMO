package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.engineer.MegaCannonSkill;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

public class MegaCannonListener implements Listener {
    private final MinecraftMMORPG plugin;

    public MegaCannonListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if right-clicking with Mega Cannon
        if (event.getAction() == Action.RIGHT_CLICK_AIR ||
                event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.hasItemMeta() &&
                    item.getItemMeta().hasCustomModelData() &&
                    item.getItemMeta().getCustomModelData() == 12345) {

                event.setCancelled(true); // Prevent dispenser GUI

                // Get the skill instance
                MegaCannonSkill skill = (MegaCannonSkill) plugin.getSkillManager()
                        .getSkillInstance("megacannon");
                if (skill != null) {
                    // Fire the cannon
                    skill.fireProjectile(player);
                }
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData() &&
                item.getItemMeta().getCustomModelData() == 12345) {

            // Remove the dropped item entity
            event.getItemDrop().remove();
            event.getPlayer().sendMessage("§c✦ Your Mega Cannon vanishes as you drop it!");

            // Play effect
            event.getPlayer().getWorld().playSound(event.getPlayer().getLocation(),
                    Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        if (projectile.hasMetadata("mega_cannon_projectile")) {
            // Get the skill instance
            MegaCannonSkill skill = (MegaCannonSkill) plugin.getSkillManager()
                    .getSkillInstance("megacannon");
            if (skill != null) {
                // Handle the projectile hit
                skill.handleProjectileHit(projectile, event.getHitEntity());
            }

            // Remove the projectile
            projectile.remove();
        }
    }
}