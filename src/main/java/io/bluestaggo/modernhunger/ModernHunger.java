package io.bluestaggo.modernhunger;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ModernHunger extends JavaPlugin {
    private static final String CUSTOM_FOOD_MANAGER_ID = "modernhunger:custom_food_manager";

    @Override
    public void onEnable() {
        Server server = getServer();

        PluginManager pluginManager = server.getPluginManager();
        pluginManager.registerEvents(new PlayerHandler(this), this);

        for (Player player : server.getOnlinePlayers()) {
            attachCustomFoodManager(player);
        }

        server.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (Player player : server.getOnlinePlayers()) {
                getCustomFoodManager(player).tick();
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        Server server = getServer();

        for (Player player : server.getOnlinePlayers()) {
            detachCustomFoodManager(player);
        }

        server.getScheduler().cancelTasks(this);
    }

    public CustomFoodManager attachCustomFoodManager(Player player) {
        CustomFoodManager customFoodManager = new CustomFoodManager(player);
        player.setMetadata(CUSTOM_FOOD_MANAGER_ID, new FixedMetadataValue(this, customFoodManager));
        return customFoodManager;
    }

    public CustomFoodManager getCustomFoodManager(Player player) {
        List<MetadataValue> metadataValues = player.getMetadata(CUSTOM_FOOD_MANAGER_ID);
        for (MetadataValue metadataValue : metadataValues) {
            if (metadataValue.getOwningPlugin() == this && metadataValue.value() instanceof CustomFoodManager) {
                return (CustomFoodManager) metadataValue.value();
            }
        }
        return attachCustomFoodManager(player);
    }

    public void detachCustomFoodManager(Player player) {
        player.removeMetadata(CUSTOM_FOOD_MANAGER_ID, this);
    }
}
