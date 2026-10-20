package com.jerrythedawg;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The Dog Bed and the Food Bowl. They're vanilla blocks (a red carpet and a cauldron) placed from
 * our named items; we remember where they are. Fill the bowl with treats or meat and he walks over
 * to eat when he's hungry; when he's sleepy (or it's night) he walks to his bed and naps on it.
 */
final class Home {
    private static final double RANGE = 24;
    private static final Material[] MEAT = {
        Material.RAW_BEEF, Material.COOKED_BEEF, Material.PORK, Material.GRILLED_PORK, Material.RAW_CHICKEN,
        Material.COOKED_CHICKEN, Material.MUTTON, Material.COOKED_MUTTON, Material.RABBIT, Material.COOKED_RABBIT,
        Material.RAW_FISH, Material.COOKED_FISH};

    private final JerryPlugin plugin;
    private final Pets pets;

    Home(JerryPlugin plugin, Pets pets) {
        this.plugin = plugin;
        this.pets = pets;
    }

    // ------------------------------------------------------------------ where things are

    static String key(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static Location location(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isBowl(Block block) {
        String key = key(block.getLocation());
        for (PetData data : plugin.allData().values()) {
            if (data.bowls.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** Has this player got a bed/bowl placed somewhere already? */
    static boolean placed(PetData data, Items item) {
        return !(item == Items.BED ? data.beds : data.bowls).isEmpty();
    }

    /** Gives a bed and a bowl to players who don't have one (in their inventory or placed). */
    void giveFurniture(Player player) {
        PetData data = plugin.data(player);
        for (Items item : new Items[] {Items.BED, Items.BOWL}) {
            if (!Items.has(player, item) && !placed(data, item)) {
                Items.give(player, item);
            }
        }
        if (!data.gotFurniture) {
            data.gotFurniture = true;
            plugin.markDirty();
        }
    }

    // ------------------------------------------------------------------ placing, breaking, filling

    void placed(Player player, Items item, Block block) {
        PetData data = plugin.data(player);
        (item == Items.BED ? data.beds : data.bowls).add(key(block.getLocation()));
        plugin.markDirty();
        pets.tell(player, item == Items.BED ? Text.BED_PLACED : Text.BOWL_PLACED);
    }

    /** Breaking a bed or bowl gives you our item back (not a plain carpet/cauldron). True if it was one. */
    boolean broken(Block block) {
        String key = key(block.getLocation());
        for (PetData data : plugin.allData().values()) {
            Items item = data.beds.remove(key) ? Items.BED : data.bowls.remove(key) ? Items.BOWL : null;
            if (item != null) {
                plugin.markDirty();
                block.setType(Material.AIR);
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item.create());
                return true;
            }
        }
        return false;
    }

    private static boolean isMeat(Material type) {
        for (Material meat : MEAT) {
            if (meat == type) {
                return true;
            }
        }
        return false;
    }

    /** Right-clicked a block. If it's a Food Bowl, handle it and return true. */
    boolean use(Player player, Block block, ItemStack hand) {
        if (block == null || block.getType() != Material.CAULDRON || !isBowl(block)) {
            return false;
        }
        Material type = hand == null ? Material.AIR : hand.getType();
        boolean treats = Items.of(hand) == Items.TREATS;
        if (!treats && !isMeat(type)) {
            // No water in the food bowl, please.
            if (type == Material.WATER_BUCKET || type == Material.GLASS_BOTTLE || type == Material.POTION || type == Material.AIR) {
                pets.tell(player, block.getData() > 0 ? Text.BOWL_FULL : Text.BOWL_HINT);
                return true;
            }
            return false;
        }
        if (block.getData() > 0) {
            pets.tell(player, Text.BOWL_FULL);
            return true;
        }
        block.setData((byte) 3);
        // The Dog Treats are a never-ending bag; real meat gets used up.
        if (!treats && player.getGameMode() != GameMode.CREATIVE) {
            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.setItemInHand(null);
            }
        }
        block.getWorld().playSound(block.getLocation(), Sound.EAT, 0.8f, 1.4f);
        pets.tell(player, Text.BOWL_FILLED);
        return true;
    }

    // ------------------------------------------------------------------ his routine

    /** Every few seconds: hungry and there's food in his bowl? Sleepy and his bed's nearby? */
    void visit(Player player, Wolf dog) {
        PetData data = plugin.data(player);
        if (dog.isSitting() || pets.isBusy(dog) || pets.isFighting(dog, player)) {
            return;
        }
        if (data.hunger < 40) {
            Block bowl = nearest(data.bowls, Material.CAULDRON, player, dog, true);
            if (bowl != null) {
                goTo(player, dog, bowl, true, false);
                return;
            }
        }
        if (data.energy < 30 || Pets.isNight(dog.getWorld())) {
            Block bed = nearest(data.beds, Material.CARPET, player, dog, false);
            if (bed != null && pets.canReact(player, "bednap", 300)) {
                goTo(player, dog, bed, false, false);
            }
        }
    }

    /** "Go to sleep" (or your bedtime): off to his bed if there's one nearby. Returns false if there isn't. */
    boolean goToBed(Player player, Wolf dog, boolean quiet) {
        if (pets.isBusy(dog)) {
            return false;
        }
        Block bed = nearest(plugin.data(player).beds, Material.CARPET, player, dog, false);
        if (bed == null) {
            return false;
        }
        dog.setSitting(false);
        goTo(player, dog, bed, false, quiet);
        return true;
    }

    /** Is he napping on one of his beds? (Naps there recharge him faster.) */
    boolean onBed(Wolf dog, PetData data) {
        for (String key : data.beds) {
            Location bed = location(key);
            if (bed != null && bed.getWorld().equals(dog.getWorld())
                && bed.clone().add(0.5, 0, 0.5).distance(dog.getLocation()) < 1.2) {
                return true;
            }
        }
        return false;
    }

    /** The closest bed/bowl near both of you. Forgets ones that aren't there any more. */
    private Block nearest(List<String> keys, Material type, Player player, Wolf dog, boolean needsFood) {
        Block best = null;
        double bestDistance = RANGE;
        for (Iterator<String> it = keys.iterator(); it.hasNext();) {
            Location at = location(it.next());
            if (at == null || !at.getWorld().equals(dog.getWorld()) || at.distance(player.getLocation()) > RANGE) {
                continue;
            }
            double d = at.distance(dog.getLocation());
            if (d > bestDistance) {
                continue;
            }
            Block block = at.getBlock();
            if (block.getType() != type) {
                it.remove(); // broken some other way (water, explosion...)
                plugin.markDirty();
                continue;
            }
            if (needsFood && block.getData() == 0) {
                continue;
            }
            best = block;
            bestDistance = d;
        }
        return best;
    }

    private void goTo(final Player player, final Wolf dog, final Block spot, final boolean eat, final boolean quiet) {
        pets.setBusy(dog, true);
        if (!quiet) {
            pets.tell(player, eat ? Text.GOING_TO_EAT : Text.GOING_TO_BED);
        }
        final Location target = spot.getLocation().add(0.5, 0, 0.5);
        new BukkitRunnable() {
            private int steps;

            @Override
            public void run() {
                if (!dog.isValid() || !player.isOnline() || dog.isSitting() || steps++ > 30) {
                    done();
                    return;
                }
                Location at = dog.getLocation();
                double flat = Math.hypot(at.getX() - target.getX(), at.getZ() - target.getZ());
                if (!at.getWorld().equals(target.getWorld()) || flat > (eat ? 1.7 : 1.0)) {
                    pets.walkTo(dog, target, 1.1);
                    return;
                }
                done();
                PetData data = plugin.data(player);
                if (eat) {
                    if (spot.getType() != Material.CAULDRON || spot.getData() == 0) {
                        return; // someone beat him to it
                    }
                    spot.setData((byte) 0);
                    pets.sound(dog, Sound.EAT, 1f);
                    pets.later(8, new Runnable() {
                        @Override
                        public void run() {
                            if (dog.isValid()) {
                                pets.sound(dog, Sound.EAT, 1.2f);
                                pets.hearts(dog);
                            }
                        }
                    });
                    pets.particles(spot.getLocation().add(0.5, 0.6, 0.5), Effect.HAPPY_VILLAGER, 6);
                    pets.changeNeeds(data, 2, 0, 40);
                    pets.tell(player, Text.ATE_FROM_BOWL);
                } else {
                    Location bed = target.clone().add(0, 0.07, 0);
                    bed.setYaw(at.getYaw());
                    dog.teleport(bed);
                    dog.setSitting(true);
                    pets.sound(dog, Sound.WOLF_PANT, 0.7f);
                    pets.changeNeeds(data, 0, 10, 0);
                    pets.snore(dog);
                    if (!quiet) {
                        pets.tell(player, Text.NAP_ON_BED);
                    }
                }
            }

            private void done() {
                cancel();
                pets.setBusy(dog, false);
            }
        }.runTaskTimer(plugin, 1L, 10L);
    }
}
