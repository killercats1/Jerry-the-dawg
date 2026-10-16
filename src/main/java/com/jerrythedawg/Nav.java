package com.jerrythedawg;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Makes a pet walk somewhere using Minecraft's own pathfinding (the same navigator vanilla mobs use),
 * so it turns, walks and animates like a normal mob.
 *
 * The 1.8.8 Bukkit API has no pathfinding methods, so this reaches into the server with reflection:
 * {@code ((CraftLivingEntity) pet).getHandle().getNavigation().a(x, y, z, speed)} on 1.8.8
 * Spigot/Paper (v1_8_R3). The methods are looked up per class: a wolf's and a cat's {@code getHandle()}
 * are different methods, and mixing them up used to switch pathfinding off for good (pets then slid
 * around facing the wrong way). If the server really doesn't have these methods, pets fall back to
 * being steered with velocity.
 */
final class Nav {
    private static final Map<Class<?>, Method> HANDLE = new HashMap<Class<?>, Method>();
    private static final Map<Class<?>, Method> NAVIGATION = new HashMap<Class<?>, Method>();
    private static final Map<Class<?>, Method> MOVE_TO = new HashMap<Class<?>, Method>();
    private static boolean unavailable;
    private static boolean warned;

    private Nav() {
    }

    /** Starts walking toward the spot. speed 1.0 = normal walking, ~1.5 = running. */
    static void walkTo(LivingEntity pet, Location target, double speed, Logger log) {
        if (!unavailable) {
            try {
                Object handle = method(HANDLE, pet.getClass(), "getHandle").invoke(pet);
                Object navigation = method(NAVIGATION, handle.getClass(), "getNavigation").invoke(handle);
                method(MOVE_TO, navigation.getClass(), "a", double.class, double.class, double.class, double.class)
                    .invoke(navigation, target.getX(), target.getY(), target.getZ(), speed);
                return;
            } catch (NoSuchMethodException error) {
                // This server doesn't have the 1.8.8 internals: steer from now on.
                unavailable = true;
                log.info("Vanilla pathfinding isn't available on this server (" + error.getMessage() + "); pets will steer instead.");
            } catch (Exception error) {
                // A one-off problem: steer this time, try pathfinding again next time.
                if (!warned) {
                    warned = true;
                    log.warning("Pathfinding hiccup (" + error + "); steering this pet for now.");
                }
            }
        }
        steer(pet, target, speed);
    }

    private static Method method(Map<Class<?>, Method> cache, Class<?> type, String name, Class<?>... args)
            throws NoSuchMethodException {
        Method m = cache.get(type);
        if (m == null) {
            m = type.getMethod(name, args);
            m.setAccessible(true);
            cache.put(type, m);
        }
        return m;
    }

    /** Fallback: push the pet toward the target (facing where it's going), hopping up single blocks. */
    private static void steer(LivingEntity pet, Location target, double speed) {
        Location from = pet.getLocation();
        Vector dir = target.toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.04) {
            return;
        }
        dir.normalize();
        Vector velocity = dir.clone().multiply(0.22 * speed);
        Block ahead = from.clone().add(dir.clone().multiply(0.8)).getBlock();
        boolean blocked = ahead.getType().isSolid();
        velocity.setY(blocked && pet.isOnGround() ? 0.42 : pet.getVelocity().getY());
        pet.setVelocity(velocity);
    }
}
