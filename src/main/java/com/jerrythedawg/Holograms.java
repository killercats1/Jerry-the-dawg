package com.jerrythedawg;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wolf;
import org.bukkit.scheduler.BukkitTask;

/**
 * Floating text over the pets: the red "!" when you say his name and the "I'm listening" speaker
 * (1.8.8 can't add models, so these are invisible marker armor stands with a name tag that follow
 * the pet around). One stand per pet; it's always removed when it's done, when its chunk unloads,
 * when the pet goes away and when the plugin stops.
 */
final class Holograms {
    /** The red exclamation point (like the Bedrock add-on's). */
    static final String[] ALERT = {"§4§l!"};
    /**
     * A speaker with sound waves blinking on, one frame every few ticks. Only characters from
     * Minecraft 1.8's built-in font (■ and brackets), so Eaglercraft shows them too.
     */
    static final String[] SPEAKER = {"§f§l■", "§f§l■§7§l)", "§f§l■§7§l))", "§f§l■§7§l)))"};
    private static final Set<String> OUR_NAMES = new HashSet<String>();

    static {
        OUR_NAMES.addAll(Arrays.asList(ALERT));
        OUR_NAMES.addAll(Arrays.asList(SPEAKER));
    }

    private static final class Bubble {
        final Entity pet;
        final ArmorStand stand;
        final String[] frames;
        final long until;
        final boolean pop;
        int age;

        Bubble(Entity pet, ArmorStand stand, String[] frames, long until, boolean pop) {
            this.pet = pet;
            this.stand = stand;
            this.frames = frames;
            this.until = until;
            this.pop = pop;
        }
    }

    private final JerryPlugin plugin;
    private final Map<UUID, Bubble> bubbles = new HashMap<UUID, Bubble>();
    private BukkitTask task;
    private long tick;

    Holograms(JerryPlugin plugin) {
        this.plugin = plugin;
    }

    /** The red "!" for about a second, with a little pop upwards. */
    void alert(Entity pet, long ticks) {
        show(pet, ALERT, ticks, true);
    }

    /** The speaker icon, its waves blinking on and off. */
    void speaker(Entity pet, long ticks) {
        show(pet, SPEAKER, ticks, false);
    }

    boolean showing(Entity pet, String[] frames) {
        Bubble b = bubbles.get(pet.getUniqueId());
        return b != null && b.frames == frames && b.stand.isValid();
    }

    void hide(Entity pet) {
        Bubble b = bubbles.remove(pet.getUniqueId());
        if (b != null) {
            b.stand.remove();
        }
    }

    private void show(Entity pet, String[] frames, long ticks, boolean pop) {
        if (pet == null || !pet.isValid()) {
            return;
        }
        Bubble old = bubbles.get(pet.getUniqueId());
        ArmorStand stand;
        if (old != null && old.stand.isValid()) {
            stand = old.stand; // reuse it so the bubble doesn't flicker
        } else {
            stand = pet.getWorld().spawn(spot(pet, 0), ArmorStand.class);
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setCustomNameVisible(true);
        }
        stand.setCustomName(frames[0]);
        bubbles.put(pet.getUniqueId(), new Bubble(pet, stand, frames, tick + ticks, pop));
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    update();
                }
            }, 1L, 1L);
        }
    }

    /** Where the name tag goes: just above his head (lower when he's sitting or a puppy). */
    private static Location spot(Entity pet, double lift) {
        double height = 0.75;
        if (pet instanceof Wolf && !((Wolf) pet).isAdult()) {
            height = 0.4;
        }
        if (Pets.isSitting(pet)) {
            height -= 0.2;
        }
        return pet.getLocation().add(0, height + lift, 0);
    }

    private void update() {
        tick++;
        Iterator<Bubble> it = bubbles.values().iterator();
        while (it.hasNext()) {
            Bubble b = it.next();
            if (tick >= b.until || !b.pet.isValid() || !b.stand.isValid() || b.pet.getWorld() != b.stand.getWorld()) {
                b.stand.remove();
                it.remove();
                continue;
            }
            b.age++;
            // The "!" pops up and settles; the speaker bobs gently.
            double lift = b.pop ? (b.age < 4 ? b.age * 0.08 : Math.max(0.12, 0.32 - (b.age - 4) * 0.05))
                : Math.sin(b.age / 6.0) * 0.04;
            b.stand.teleport(spot(b.pet, lift));
            if (b.frames.length > 1 && b.age % 5 == 0) {
                b.stand.setCustomName(b.frames[(b.age / 5) % b.frames.length]);
            }
        }
        if (bubbles.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** A chunk is unloading: take any of our bubbles in it with it. */
    void chunkUnloading(Chunk chunk) {
        Iterator<Bubble> it = bubbles.values().iterator();
        while (it.hasNext()) {
            Bubble b = it.next();
            Location at = b.stand.getLocation(); // (not getChunk(): that could load a chunk mid-unload)
            if ((at.getBlockX() >> 4) == chunk.getX() && (at.getBlockZ() >> 4) == chunk.getZ() && at.getWorld() == chunk.getWorld()) {
                b.stand.remove();
                it.remove();
            }
        }
        removeStrays(chunk.getEntities());
    }

    void removeAll() {
        for (Bubble b : bubbles.values()) {
            b.stand.remove();
        }
        bubbles.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Bubbles left behind by a crash (armor stands are saved with the world): clean them up. */
    void removeStrays(Entity[] entities) {
        for (Entity e : entities) {
            if (isOurs(e)) {
                boolean tracked = false;
                for (Bubble b : bubbles.values()) {
                    tracked |= b.stand.getUniqueId().equals(e.getUniqueId());
                }
                if (!tracked) {
                    e.remove();
                }
            }
        }
    }

    void removeStrays(World world) {
        removeStrays(world.getEntities().toArray(new Entity[0]));
    }

    static boolean isOurs(Entity e) {
        if (!(e instanceof ArmorStand)) {
            return false;
        }
        ArmorStand s = (ArmorStand) e;
        return s.isMarker() && !s.isVisible() && s.isSmall() && OUR_NAMES.contains(s.getCustomName());
    }
}
