package com.jerrythedawg;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Makes a pet walk somewhere using Minecraft's own pathfinding (the same navigator vanilla mobs use).
 *
 * The 1.8.8 Bukkit API has no pathfinding methods, so this reaches into the server with reflection:
 * {@code ((CraftLivingEntity) pet).getHandle().getNavigation().a(x, y, z, speed)} on 1.8.8
 * Spigot/Paper (v1_8_R3). If that isn't available (an unusual server), it falls back to steering the
 * pet with velocity so it still heads the right way.
 */
final class Nav {
    private static Method getHandle;
    private static Method getNavigation;
    private static Method moveTo;
    private static boolean unavailable;

    private Nav() {
    }

    /** Starts walking toward the spot. speed 1.0 = normal walking, ~1.5 = running. */
    static void walkTo(LivingEntity pet, Location target, double speed, Logger log) {
        if (!unavailable) {
            try {
                if (getHandle == null) {
                    getHandle = pet.getClass().getMethod("getHandle");
                }
                Object handle = getHandle.invoke(pet);
                if (getNavigation == null) {
                    getNavigation = handle.getClass().getMethod("getNavigation");
                }
                Object navigation = getNavigation.invoke(handle);
                if (moveTo == null) {
                    moveTo = navigation.getClass().getMethod("a", double.class, double.class, double.class, double.class);
                }
                moveTo.invoke(navigation, target.getX(), target.getY(), target.getZ(), speed);
                return;
            } catch (Throwable error) {
                unavailable = true;
                log.info("Vanilla pathfinding isn't available on this server (" + error + "); pets will steer instead.");
            }
        }
        steer(pet, target, speed);
    }

    /** Fallback: push the pet toward the target, hopping up single blocks in the way. */
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
