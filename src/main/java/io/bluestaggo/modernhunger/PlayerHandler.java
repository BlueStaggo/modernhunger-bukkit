package io.bluestaggo.modernhunger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.*;

public class PlayerHandler implements Listener {
    private static final Set<EntityDamageEvent.DamageCause> exhaustiveDamageCauses = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.CONTACT,
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            EntityDamageEvent.DamageCause.FALLING_BLOCK,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            EntityDamageEvent.DamageCause.PROJECTILE
        ))
    );

    private final ModernHunger plugin;

    public PlayerHandler(ModernHunger plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBroken(BlockBreakEvent event) {
        CustomFoodManager food = plugin.getCustomFoodManager(event.getPlayer());
        food.addExhaustion(0.005F);
    }

    @EventHandler
    public void onEntityHit(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent eventByEntity = (EntityDamageByEntityEvent) event;
            Entity damager = eventByEntity.getDamager();
            if (damager instanceof Player) {
                CustomFoodManager food = plugin.getCustomFoodManager((Player) damager);
                food.addExhaustion(0.1F);
            }
        }

        if (event.getEntity() instanceof Player) {
            CustomFoodManager food = plugin.getCustomFoodManager((Player) event.getEntity());
            if (exhaustiveDamageCauses.contains(event.getCause())) {
                food.addExhaustion(0.1F);
            }
        }
    }

    @EventHandler
    public void onFoodUpdate(FoodLevelChangeEvent event) {
        HumanEntity human = event.getEntity();
        if (!(human instanceof Player))
            return;

        CustomFoodManager food = plugin.getCustomFoodManager((Player) human);
        food.foodLevel = event.getFoodLevel();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.attachCustomFoodManager(player);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        CustomFoodManager customFoodManager = plugin.attachCustomFoodManager(player);
        customFoodManager.foodLevel = 20;
        customFoodManager.saturationLevel = 5.0F;
        customFoodManager.exhaustionLevel = 0.0F;
    }
}