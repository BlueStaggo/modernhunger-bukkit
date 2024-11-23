package io.bluestaggo.modernhunger;

import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;

public class CustomFoodManager {
    private final Player player;

    public int foodLevel;
    public float saturationLevel;
    public float exhaustionLevel;

    private int tickTimer = 0;
    private boolean wasOnGround;
    private final Location prevLocation = new Location(null, 0.0D, 0.0D, 0.0D);

    public CustomFoodManager(Player player) {
        this.player = player;

        foodLevel = player.getFoodLevel();
        saturationLevel = player.getSaturation();
        exhaustionLevel = player.getExhaustion();
    }

    public void addExhaustion(float exhaustion) {
        exhaustionLevel = Math.min(exhaustionLevel + exhaustion, 40.0F);
    }

    public void tick() {
        // Disable vanilla hunger behaviour
        resetFoodTickTimer(player);

        // Add extra saturation
        if (player.getSaturation() > saturationLevel) {
            saturationLevel = player.getSaturation();
        }

        // Custom hunger behaviour
        tickExhaustion();
        tickStats();

        // Apply new hunger values to vanilla food manager
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturationLevel);
        player.setExhaustion(Math.min(exhaustionLevel, 3.99F));
    }

    private void tickExhaustion() {
        boolean onGround = playerOnGround(player);
        Location location = player.getLocation();
        if (prevLocation.getWorld() == null) {
            player.getLocation(prevLocation);
        }

        // Horizontal movement
        if (!player.isInsideVehicle()) {
            // Player velocity seems to not get updated on the server so we gotta
            Vector velocity = location.subtract(prevLocation).toVector();

            if (playerIsSwimming(player)) {
                // Swimming
                float movementFactor3D = (float) Math.round(velocity.length() * 100.0D) * 0.01F;
                if (movementFactor3D > 0.0F) {
                    addExhaustion(movementFactor3D * 0.01F);
                }
            } else if (player.isSprinting() && onGround) {
                // Sprinting
                Material footBlockType = getPlayerFootLocation(player).getBlock().getType();
                float movementFactor2D = (float) Math.round(velocity.setY(0.0D).length() * 100.0D) * 0.01F;

                if (movementFactor2D > 0.0F
                    && footBlockType != Material.LADDER
                    && footBlockType != Material.VINE) {
                    addExhaustion(movementFactor2D * 0.1F);
                }
            }
        }

        // Vertical movement
        if (!onGround && wasOnGround && player.getLocation().getY() > prevLocation.getY()) {
            // Assume the player jumped here
            addExhaustion(player.isSprinting() ? 0.2F : 0.05F);
        }

        // Hunger effect
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            if (potionEffect.getType() == PotionEffectType.HUNGER) {
                addExhaustion(0.005F * potionEffect.getAmplifier());
            }
        }

        wasOnGround = onGround;
        player.getLocation(prevLocation);
    }

    private void tickStats() {
        Difficulty difficulty = player.getWorld().getDifficulty();

        if (foodLevel > 20) {
            foodLevel = 20;
        }

        if (exhaustionLevel > 4.0F) {
            exhaustionLevel -= 4.0F;
            if (saturationLevel > 0.0F) {
                saturationLevel = Math.max(saturationLevel - 1.0F, 0.0F);
            } else if (difficulty != Difficulty.PEACEFUL) {
                foodLevel = Math.max(foodLevel - 1, 0);
            }
        }

        boolean naturalGeneration = naturalRegenerationEnabled(player.getWorld());
        double health = getDamageableHealth(player);
        double maxHealth = getDamageableMaxHealth(player);
        if (naturalGeneration && saturationLevel > 0.0F && health < maxHealth && foodLevel >= 20) {
            tickTimer++;
            if (tickTimer >= 10) {
                float healAmount = Math.min(this.saturationLevel, 6.0F);
                health = Math.min(health + healAmount / 6.0F, maxHealth);
                setDamageableHealth(player, health);
                addExhaustion(healAmount);
                tickTimer = 0;
            }
        } else if (naturalGeneration && foodLevel >= 18 && health < maxHealth) {
            tickTimer++;
            if (tickTimer >= 80) {
                health = Math.min(health + 1.0D, maxHealth);
                setDamageableHealth(player, health);
                addExhaustion(6.0F);
                tickTimer = 0;
            }
        } else if (foodLevel <= 0) {
            tickTimer++;
            if (tickTimer >= 80) {
                if (difficulty == Difficulty.HARD
                        || difficulty == Difficulty.NORMAL && health > 1.0D
                        || health > 10.0D) {
                    damageDamageable(player, 1.0D);
                }
                tickTimer = 0;
            }
        } else {
            tickTimer = 0;
        }
    }

    // Utilities

    private static Location getPlayerFootLocation(Player player) {
        Location footLocation = player.getEyeLocation();
        footLocation.setY(footLocation.getY() - 1.62D);
        return footLocation;
    }

    private static void resetFoodTickTimer(Player player) {
        try {
            Object playerHandle = player.getClass().getMethod("getHandle").invoke(player);
            Object foodData = playerHandle.getClass().getMethod("getFoodData").invoke(playerHandle);
            foodData.getClass().getField("foodTickTimer").set(foodData, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean playerIsSwimming(Player player) {
        // Swimming on the surface
        Location location = getPlayerFootLocation(player);
        Block block = location.getBlock();
        if (block.getType() == Material.WATER || block.getType() == Material.STATIONARY_WATER) {
            return true;
        }

        // Swimming underwater
        location = player.getEyeLocation();
        block = location.getBlock();
        if (block.getType() != Material.WATER && block.getType() != Material.STATIONARY_WATER) {
            return false;
        }

        int data = block.getData();
        double fluidHeight = data >= 8 ? 0.0D : (data + 1) / 9.0D;
        fluidHeight -= 1.0D / 9.0D;
        double fluidY = block.getY() + 1 - fluidHeight;
        return location.getY() < fluidY;
    }

    private static boolean playerOnGround(Player player) {
        try {
            Object playerHandle = player.getClass().getMethod("getHandle").invoke(player);
            Object onGround = playerHandle.getClass().getField("onGround").get(playerHandle);
            return Boolean.TRUE.equals(onGround);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean naturalRegenerationEnabled(World world) {
        Method getGameRuleValueMethod;
        try {
            getGameRuleValueMethod = world.getClass().getMethod("getGameRuleValue", String.class);
        } catch (Exception e) {
            return true; // Game rules aren't in the game yet
        }

        try {
            String naturalRegeneration = (String) getGameRuleValueMethod.invoke(world, "naturalRegeneration");
            // Check for empty string for versions before the naturalRegeneration game rule was added (<1.6)
            return naturalRegeneration.isEmpty() || Boolean.parseBoolean(naturalRegeneration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // In 1.6, health was changed to use doubles instead of ints, hence these workarounds for multi-version support

    private static double getDamageableHealth(Damageable damageable) {
        try {
            Method getHealthMethod = damageable.getClass().getMethod("getHealth");
            Object health = getHealthMethod.invoke(damageable);
            if (health instanceof Double) {
                return (double) health;
            } else if (health instanceof Integer) {
                return (double) (int) health;
            } else {
                throw new NoSuchMethodException("Could not get proper health value from damageable");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static double getDamageableMaxHealth(Damageable damageable) {
        try {
            Method getMaxHealthMethod = damageable.getClass().getMethod("getMaxHealth");
            Object maxHealth = getMaxHealthMethod.invoke(damageable);
            if (maxHealth instanceof Double) {
                return (double) maxHealth;
            } else if (maxHealth instanceof Integer) {
                return (double) (int) maxHealth;
            } else {
                throw new NoSuchMethodException("Could not get proper max health value from damageable");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setDamageableHealth(Damageable damageable, double health) {
        try {
            Method setDoubleHealthMethod = damageable.getClass().getMethod("setHealth", double.class);
            setDoubleHealthMethod.invoke(damageable, health);
        } catch (Exception e) {
            try {
                Method setIntHealthMethod = damageable.getClass().getMethod("setHealth", int.class);
                setIntHealthMethod.invoke(damageable, (int) Math.round(health));
            } catch (Exception f) {
                throw new RuntimeException(f);
            }
        }
    }

    private static void damageDamageable(Damageable damageable, double amount) {
        try {
            Method doubleDamageMethod = damageable.getClass().getMethod("damage", double.class);
            doubleDamageMethod.invoke(damageable, amount);
        } catch (Exception e) {
            try {
                Method intDamageMethod = damageable.getClass().getMethod("damage", int.class);
                intDamageMethod.invoke(damageable, (int) Math.round(amount));
            } catch (Exception f) {
                throw new RuntimeException(f);
            }
        }
    }
}
