package com.jerrythedawg;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Finding, spawning and looking after the pets, plus everything they can do. */
final class Pets {
    // Friendship goes 0-100; the level thresholds line up with Text.LEVELS.
    private static final int[] LEVEL_MIN = {0, 25, 60, 100};
    private static final int CAT_LEVEL = 1;
    private static final int GIFT_LEVEL = 2;
    private static final Material[] GIFTS = {
        Material.STICK, Material.BONE, Material.RED_ROSE, Material.YELLOW_FLOWER, Material.FEATHER, Material.EMERALD};

    private final JerryPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Entity> cache = new HashMap<UUID, Entity>();
    private final Map<String, Long> cooldowns = new HashMap<String, Long>();
    private final Map<UUID, Long> nextMoment = new HashMap<UUID, Long>();
    private final Map<UUID, Long> nextGift = new HashMap<UUID, Long>();
    private final Map<String, Long> reactedAt = new HashMap<String, Long>();
    private final Map<UUID, Long> awaySince = new HashMap<UUID, Long>();
    /** Pets in the middle of something (like fetch) that the follow brain should leave alone. */
    private final Set<UUID> busy = new HashSet<UUID>();
    private long seconds;

    /** Tricks that need practice before he's perfect at them. */
    static final Set<String> PRACTICE = new HashSet<String>(Arrays.asList(
        "paw", "highfive", "wave", "beg", "roll", "dead", "bow", "dance", "backflip", "howl", "chase", "spin", "lie", "hop", "shakeoff"));
    static final int MASTER_AT = 5;

    Pets(JerryPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ text

    String personalize(PetData data, String text) {
        return text.replace("Jerry", data.dogName).replace("Mochi", data.catName);
    }

    void tell(Player player, String text) {
        player.sendMessage(personalize(plugin.data(player), text));
    }

    <T> T pick(T[] options) {
        return options[random.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ finding pets

    private Entity find(UUID id) {
        if (id == null) {
            return null;
        }
        Entity cached = cache.get(id);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getUniqueId().equals(id)) {
                    cache.put(id, entity);
                    return entity;
                }
            }
        }
        return null;
    }

    Wolf findDog(Player player) {
        Entity e = find(plugin.data(player).dogId);
        return e instanceof Wolf ? (Wolf) e : null;
    }

    Ocelot findCat(Player player) {
        PetData data = plugin.data(player);
        if (!data.hasCat) {
            return null;
        }
        Entity e = find(data.catId);
        return e instanceof Ocelot ? (Ocelot) e : null;
    }

    /** True for any player's dog or cat (they can't be hurt). */
    boolean isPet(Entity entity) {
        UUID id = entity.getUniqueId();
        for (PetData data : plugin.allData().values()) {
            if (id.equals(data.dogId) || id.equals(data.catId)) {
                return true;
            }
        }
        return false;
    }

    /** The online player who owns this pet, or null. */
    Player ownerOf(Entity entity) {
        UUID id = entity.getUniqueId();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PetData data = plugin.data(player);
            if (id.equals(data.dogId) || id.equals(data.catId)) {
                return player;
            }
        }
        return null;
    }

    /** An old copy of someone's pet (same owner and name, but not the pet we're tracking). */
    void removeIfStaleCopy(Entity entity) {
        if (!(entity instanceof org.bukkit.entity.Tameable) || entity.getCustomName() == null) {
            return;
        }
        org.bukkit.entity.AnimalTamer owner = ((org.bukkit.entity.Tameable) entity).getOwner();
        if (owner == null) {
            return;
        }
        PetData data = plugin.allData().get(owner.getUniqueId());
        if (data == null || isPet(entity)) {
            return;
        }
        boolean isDog = entity instanceof Wolf && entity.getCustomName().equals(data.dogName) && data.dogId != null;
        boolean isCat = entity instanceof Ocelot && entity.getCustomName().equals(data.catName) && data.catId != null;
        if (isDog || isCat) {
            entity.remove();
        }
    }

    boolean isNear(Entity pet, Player player, double range) {
        return pet != null && pet.isValid() && pet.getWorld().equals(player.getWorld())
            && pet.getLocation().distance(player.getLocation()) <= range;
    }

    // ------------------------------------------------------------------ spawning

    Wolf spawnDog(Player player, Location at) {
        PetData data = plugin.data(player);
        Wolf wolf = at.getWorld().spawn(at, Wolf.class);
        wolf.setAdult();
        wolf.setTamed(true);
        wolf.setOwner(player);
        wolf.setCollarColor(DyeColor.RED);
        wolf.setCustomName(data.dogName);
        wolf.setRemoveWhenFarAway(false);
        data.dogId = wolf.getUniqueId();
        cache.put(wolf.getUniqueId(), wolf);
        plugin.markDirty();
        return wolf;
    }

    Ocelot spawnCat(Player player, Location at) {
        PetData data = plugin.data(player);
        Ocelot cat = at.getWorld().spawn(at, Ocelot.class);
        cat.setAdult();
        cat.setTamed(true);
        cat.setOwner(player);
        cat.setCatType(Ocelot.Type.RED_CAT);
        cat.setCustomName(data.catName);
        cat.setRemoveWhenFarAway(false);
        data.catId = cat.getUniqueId();
        cache.put(cat.getUniqueId(), cat);
        plugin.markDirty();
        return cat;
    }

    void onJoin(final Player player) {
        final PetData data = plugin.data(player);
        later(60, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                if (data.started) {
                    tell(player, Text.WELCOME_BACK);
                    return;
                }
                data.started = true;
                Wolf wolf = spawnDog(player, player.getLocation());
                Items.giveAll(player);
                hearts(wolf);
                sound(wolf, Sound.WOLF_BARK, 1f);
                say(player, Text.INTRO, 60);
            }
        });
    }

    void reset(Player player) {
        PetData data = plugin.data(player);
        Wolf dog = findDog(player);
        if (dog != null) {
            dog.remove();
        }
        Ocelot cat = findCat(player);
        if (cat != null) {
            cat.remove();
        }
        plugin.allData().remove(player.getUniqueId());
        plugin.markDirty();
        onJoin(player);
    }

    // ------------------------------------------------------------------ names

    static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.replaceAll("§.", "").replaceAll("[^\\p{L}\\p{N} '\\-]", "").replaceAll("\\s+", " ").trim();
        return name.length() > 20 ? name.substring(0, 20).trim() : name;
    }

    /** Renames the dog or cat (from /dog name, a name tag, ...). Returns false if nothing changed. */
    boolean rename(Player player, boolean cat, String raw, boolean quiet) {
        PetData data = plugin.data(player);
        String current = cat ? data.catName : data.dogName;
        LivingEntity pet = cat ? findCat(player) : findDog(player);
        String name = cleanName(raw);
        if (name.isEmpty() || name.equals(current)) {
            if (pet != null && !current.equals(pet.getCustomName())) {
                pet.setCustomName(current);
            }
            return false;
        }
        if (cat) {
            data.catName = name;
        } else {
            data.dogName = name;
        }
        plugin.markDirty();
        if (pet != null) {
            pet.setCustomName(name);
            if (pet instanceof Wolf) {
                hearts((Wolf) pet);
            }
        }
        if (!quiet) {
            tell(player, cat ? Text.CAT_RENAMED : Text.DOG_RENAMED);
        }
        return true;
    }

    // ------------------------------------------------------------------ friendship

    static int levelOf(int friendship) {
        int level = 0;
        for (int i = 0; i < LEVEL_MIN.length; i++) {
            if (friendship >= LEVEL_MIN[i]) {
                level = i;
            }
        }
        return level;
    }

    String friendshipBar(PetData data) {
        int filled = Math.round(data.friendship / 10f);
        StringBuilder bar = new StringBuilder("§c");
        for (int i = 0; i < 10; i++) {
            if (i == filled) {
                bar.append("§8");
            }
            bar.append('|');
        }
        return bar.append(" §7").append(Text.LEVELS[levelOf(data.friendship)]).append(" §8(").append(data.friendship).append("/100)").toString();
    }

    void addFriendship(Player player, int amount) {
        PetData data = plugin.data(player);
        int before = data.friendship;
        data.friendship = Math.min(100, before + amount);
        plugin.markDirty();
        int level = levelOf(data.friendship);
        if (level > levelOf(before)) {
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            tell(player, "§a§lFriendship up! §fJerry is now your §e" + Text.LEVELS[level] + "§f!");
            tell(player, Text.LEVEL_UP[level]);
            Wolf dog = findDog(player);
            if (dog != null) {
                hearts(dog);
            }
        }
    }

    /** Each action only earns friendship once per cooldown, so spamming doesn't max it out. */
    boolean earn(Player player, String action, int gain, int cooldownSeconds) {
        String key = player.getUniqueId() + ":" + action;
        Long ready = cooldowns.get(key);
        if (ready != null && seconds < ready) {
            return false;
        }
        cooldowns.put(key, seconds + cooldownSeconds);
        addFriendship(player, gain);
        return true;
    }

    // ------------------------------------------------------------------ every second

    void tickAll() {
        seconds++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                tick(player);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Pet tick failed for " + player.getName() + ": " + ex);
            }
        }
    }

    private void tick(Player player) {
        PetData data = plugin.data(player);
        if (!data.started) {
            return;
        }
        Wolf dog = findDog(player);
        Ocelot cat = findCat(player);
        // Renamed with a name tag?
        if (dog != null && dog.getCustomName() != null && !dog.getCustomName().equals(data.dogName)) {
            rename(player, false, dog.getCustomName(), false);
        }
        if (cat != null && cat.getCustomName() != null && !cat.getCustomName().equals(data.catName)) {
            rename(player, true, cat.getCustomName(), false);
        }
        if (levelOf(data.friendship) >= CAT_LEVEL && !data.hasCat) {
            catArrives(player);
        }
        if (dog != null) {
            keepClose(player, dog, dog.isSitting());
            reactToSurroundings(player, dog);
        }
        if (cat != null) {
            keepClose(player, cat, cat.isSitting());
        }
        if (!isNear(dog, player, 16)) {
            return;
        }
        if (seconds % 60 == 0) {
            updateNeeds(data, dog.isSitting(), isNight(player.getWorld()));
            addFriendship(player, "happy".equals(moodOf(data)) ? 2 : 1); // a happy dog makes friends faster
        }
        UUID id = player.getUniqueId();
        if (levelOf(data.friendship) >= GIFT_LEVEL) {
            if (!nextGift.containsKey(id)) {
                nextGift.put(id, seconds + 120);
            }
            if (seconds >= nextGift.get(id)) {
                nextGift.put(id, seconds + 180 + random.nextInt(120));
                bringGift(player, dog);
                return;
            }
        }
        Long next = nextMoment.get(id);
        if (next == null) {
            nextMoment.put(id, seconds + 45);
        } else if (seconds >= next) {
            nextMoment.put(id, seconds + 45 + random.nextInt(30));
            String mood = moodOf(data);
            if (!"happy".equals(mood) && random.nextInt(10) < 6) {
                moodMoment(player, dog, mood);
                return;
            }
            boolean catMoment = isNear(cat, player, 16) && random.nextInt(3) == 0;
            sound(dog, random.nextBoolean() ? Sound.WOLF_PANT : Sound.WOLF_BARK, 1f);
            if (random.nextInt(4) == 0) {
                hop(dog, 0.45);
            }
            if (!data.quiet) {
                tell(player, catMoment ? pick(Text.CAT_MOMENTS) : pick(Text.MOMENTS));
            }
        }
    }

    private void catArrives(Player player) {
        PetData data = plugin.data(player);
        data.hasCat = true;
        Ocelot cat = spawnCat(player, player.getLocation());
        sound(cat, Sound.CAT_MEOW, 1f);
        particles(cat.getLocation(), Effect.HEART, 6);
        player.sendMessage("§d§lA new friend!");
        say(player, Text.CAT_ARRIVES, 60);
    }

    private void bringGift(Player player, Wolf dog) {
        int i = random.nextInt(GIFTS.length);
        dog.getWorld().dropItemNaturally(dog.getLocation(), new ItemStack(GIFTS[i], 1));
        sound(dog, Sound.WOLF_BARK, 1f);
        particles(dog.getLocation(), Effect.HAPPY_VILLAGER, 6);
        tell(player, "§bJerry§7 brought you a gift: §f" + Text.GIFT_NAMES[i] + "§7! He's very proud.");
    }

    // ------------------------------------------------------------------ staying close (pathfinding)

    void walkTo(LivingEntity pet, Location target, double speed) {
        Nav.walkTo(pet, target, speed, plugin.getLogger());
    }

    /**
     * Vanilla 1.8.8 pets only loosely follow you (and run off to fight). This keeps them close:
     * past 6 blocks they walk back to you with real pathfinding, past 24 they catch up instantly.
     */
    private void keepClose(Player player, LivingEntity pet, boolean sitting) {
        if (sitting || busy.contains(pet.getUniqueId()) || player.isFlying() || isFighting(pet, player)) {
            return;
        }
        if (!pet.getWorld().equals(player.getWorld())) {
            pet.teleport(player.getLocation());
            return;
        }
        double d = pet.getLocation().distance(player.getLocation());
        if (d > 24) {
            pet.teleport(player.getLocation());
        } else if (d > 6) {
            walkTo(pet, player.getLocation(), d > 12 ? 1.5 : 1.2);
        }
    }

    // ------------------------------------------------------------------ needs & mood

    static boolean isNight(World world) {
        long time = world.getTime();
        return time >= 13000 && time <= 23000;
    }

    static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    void changeNeeds(PetData data, int fun, int energy, int hunger) {
        data.fun = clamp(data.fun + fun);
        data.energy = clamp(data.energy + energy);
        data.hunger = clamp(data.hunger + hunger);
        plugin.markDirty();
    }

    static String moodOf(PetData data) {
        if (data.hunger < 30) {
            return "hungry";
        }
        if (data.energy < 25) {
            return "tired";
        }
        if (data.fun < 30) {
            return "bored";
        }
        return "happy";
    }

    /** Once a minute while he's with you. Resting (sitting) recharges him. */
    private void updateNeeds(PetData data, boolean resting, boolean night) {
        changeNeeds(data, resting ? -1 : -3, resting ? 8 : (night ? -4 : -2), -3);
    }

    private void moodMoment(Player player, Wolf dog, String mood) {
        PetData data = plugin.data(player);
        if ("hungry".equals(mood)) {
            sound(dog, Sound.WOLF_WHINE, 1f);
            particles(dog.getLocation().add(0, 0.8, 0), Effect.NOTE, 1);
            tell(player, pick(Text.HUNGRY));
        } else if ("tired".equals(mood)) {
            sound(dog, Sound.WOLF_PANT, 0.8f);
            if (isNight(dog.getWorld()) && !dog.isSitting()) {
                dog.setSitting(true);
                tell(player, Text.NAP);
            } else if (!data.quiet) {
                tell(player, pick(Text.TIRED));
            }
        } else {
            sound(dog, Sound.WOLF_WHINE, 1.2f);
            hop(dog, 0.4);
            tell(player, pick(Text.BORED));
        }
    }

    String meter(int value) {
        int filled = Math.round(value / 10f);
        StringBuilder bar = new StringBuilder(value >= 50 ? "§a" : value >= 25 ? "§e" : "§c");
        for (int i = 0; i < 10; i++) {
            if (i == filled) {
                bar.append("§8");
            }
            bar.append('|');
        }
        return bar.toString();
    }

    void showStatus(Player player) {
        PetData data = plugin.data(player);
        int mastered = 0;
        for (String trick : PRACTICE) {
            if (skill(data, trick) >= MASTER_AT) {
                mastered++;
            }
        }
        tell(player, Text.PREFIX + "§f§lJerry");
        tell(player, "§fMood: " + Text.MOOD_LABEL.get(moodOf(data)));
        tell(player, "§fFull: " + meter(data.hunger) + "  §fEnergy: " + meter(data.energy) + "  §fFun: " + meter(data.fun));
        tell(player, "§fFriendship: " + friendshipBar(data));
        tell(player, "§fTricks mastered: §e" + mastered + "/" + PRACTICE.size() + " §7(/dog tricks to see them)");
    }

    // ------------------------------------------------------------------ practice makes perfect

    static int skill(PetData data, String trick) {
        Integer done = data.skills.get(trick);
        return done == null ? 0 : done;
    }

    /** Returns true if he pulls the trick off. Early on he sometimes does something else instead. */
    boolean attemptTrick(final Player player, final Wolf dog, String action) {
        final PetData data = plugin.data(player);
        int done = skill(data, action);
        if (done >= MASTER_AT) {
            return true;
        }
        final String name = Text.TRICK_NAMES.containsKey(action) ? Text.TRICK_NAMES.get(action) : action;
        if (random.nextDouble() > 0.6 + done * 0.08) {
            int oops = random.nextInt(Text.OOPS.length);
            if (oops == 0) {
                spin(dog, 1);
            } else if (oops == 2) {
                dog.setSitting(true);
            } else {
                hop(dog, 0.45);
            }
            sound(dog, Sound.WOLF_WHINE, 1f);
            tell(player, Text.OOPS[oops].replace("{trick}", name));
            return false;
        }
        data.skills.put(action, done + 1);
        plugin.markDirty();
        if (done + 1 == MASTER_AT) {
            later(30, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }
                    tell(player, Text.MASTERED.replace("{trick}", name));
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
                    if (dog.isValid()) {
                        hearts(dog);
                    }
                    addFriendship(player, 3);
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------------ fetch

    /** Throws the Tennis Ball. He runs to it (pathfinding), carries it back and drops it at your feet. */
    void throwBall(final Player player) {
        final Wolf dog = findDog(player);
        if (!isNear(dog, player, 24)) {
            tell(player, Text.TOO_FAR);
            return;
        }
        if (busy.contains(dog.getUniqueId())) {
            tell(player, Text.FETCH_BUSY);
            return;
        }
        Location eye = player.getEyeLocation();
        final Item ball = player.getWorld().dropItem(eye.clone().add(eye.getDirection()), Items.BALL.create());
        ball.setPickupDelay(6000); // nobody grabs it but him
        ball.setVelocity(eye.getDirection().multiply(1.1).add(new Vector(0, 0.25, 0)));
        dog.setSitting(false);
        busy.add(dog.getUniqueId());
        sound(dog, Sound.WOLF_BARK, 1f);
        tell(player, Text.FETCH_THROW);

        new BukkitRunnable() {
            private int ticks;
            private boolean carrying;

            @Override
            public void run() {
                ticks++;
                if (!dog.isValid() || !player.isOnline() || !ball.isValid()) {
                    finish(false);
                    return;
                }
                if (!carrying) {
                    if (ticks < 10) {
                        return; // let it fly first
                    }
                    if (ticks % 10 == 0) {
                        walkTo(dog, ball.getLocation(), 1.6);
                    }
                    boolean reached = dog.getWorld().equals(ball.getWorld()) && dog.getLocation().distance(ball.getLocation()) < 1.6;
                    if (reached || ticks > 240) {
                        if (!reached) {
                            dog.teleport(ball.getLocation());
                        }
                        dog.setPassenger(ball); // carries it (it rides on him)
                        carrying = true;
                        ticks = 0;
                        dog.getWorld().playSound(dog.getLocation(), Sound.ITEM_PICKUP, 1f, 1f);
                        tell(player, Text.FETCH_GOT_IT);
                    }
                } else {
                    if (ticks % 10 == 1) {
                        walkTo(dog, player.getLocation(), 1.5);
                    }
                    boolean back = dog.getWorld().equals(player.getWorld()) && dog.getLocation().distance(player.getLocation()) < 2.5;
                    if (back || ticks > 240) {
                        if (!back) {
                            dog.teleport(player.getLocation());
                        }
                        finish(true);
                    }
                }
            }

            private void finish(boolean ok) {
                cancel();
                busy.remove(dog.getUniqueId());
                if (ball.isValid()) {
                    ball.remove();
                }
                if (!player.isOnline()) {
                    return;
                }
                if (!ok || !dog.isValid()) {
                    tell(player, Text.FETCH_LOST);
                    return;
                }
                hearts(dog);
                sound(dog, Sound.WOLF_BARK, 1f);
                PetData data = plugin.data(player);
                changeNeeds(data, 30, -6, 0);
                boolean earned = earn(player, "ball", 4, 45);
                tell(player, pick(Text.FETCH_DONE) + (earned ? "" : Text.FULL) + "  " + friendshipBar(data));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ smart reactions

    private boolean canReact(Player player, String kind, long cooldown) {
        String key = player.getUniqueId() + ":" + kind;
        Long last = reactedAt.get(key);
        if (last != null && seconds - last < cooldown) {
            return false;
        }
        reactedAt.put(key, seconds);
        return true;
    }

    private void reactToSurroundings(Player player, Wolf dog) {
        UUID id = player.getUniqueId();
        boolean near = isNear(dog, player, 24);
        // Welcome home: you were apart for a while and now you're back together.
        if (near) {
            Long since = awaySince.remove(id);
            if (since != null && seconds - since >= 90 && !busy.contains(dog.getUniqueId())) {
                zoomies(dog);
                tell(player, Text.WELCOME_HOME);
                return;
            }
        } else if (!awaySince.containsKey(id)) {
            awaySince.put(id, seconds);
        }
        // Guard dog: warns you about monsters sneaking up.
        if (near && seconds % 3 == 0) {
            for (Entity e : player.getNearbyEntities(12, 6, 12)) {
                if (e instanceof Monster && canReact(player, "guard", 30)) {
                    sound(dog, Sound.WOLF_GROWL, 1f);
                    later(10, new Runnable() {
                        @Override
                        public void run() {
                            if (dog.isValid()) {
                                sound(dog, Sound.WOLF_BARK, 1f);
                            }
                        }
                    });
                    tell(player, Text.GUARD.replace("{mob}", e.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
                    break;
                }
            }
        }
    }

    /** True while he's fighting off something that attacked you (don't pull him away mid-fight). */
    private boolean isFighting(LivingEntity pet, Player player) {
        if (!(pet instanceof Creature)) {
            return false;
        }
        LivingEntity target = ((Creature) pet).getTarget();
        return target != null && target.isValid() && !target.isDead() && target.getWorld().equals(player.getWorld())
            && target.getLocation().distance(player.getLocation()) < 24;
    }

    /** Something attacked you: your dog goes after it (he can't get hurt). The cat hisses at it. */
    void defend(Player player, LivingEntity attacker) {
        Wolf dog = findDog(player);
        if (dog != null && isNear(dog, player, 24) && !dog.isSitting() && !busy.contains(dog.getUniqueId())
            && !(attacker instanceof Creeper) && !(attacker instanceof Ghast)) {
            dog.setTarget(attacker);
            sound(dog, Sound.WOLF_GROWL, 1f);
            if (canReact(player, "defend", 10)) {
                String name = attacker instanceof Player ? ((Player) attacker).getName()
                    : attacker.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
                tell(player, Text.DEFEND.replace("{mob}", name));
            }
        } else {
            onOwnerHurt(player);
        }
        Ocelot cat = findCat(player);
        if (isNear(cat, player, 16)) {
            sound(cat, Sound.CAT_HISS, 1f);
        }
    }

    /** You got hurt: he rushes over to check on you. */
    void onOwnerHurt(Player player) {
        Wolf dog = findDog(player);
        if (!isNear(dog, player, 32) || busy.contains(dog.getUniqueId()) || !canReact(player, "hurt", 15)) {
            return;
        }
        if (!dog.isSitting()) {
            walkTo(dog, player.getLocation(), 1.6);
        }
        sound(dog, Sound.WOLF_WHINE, 1f);
        tell(player, Text.WORRIED);
    }

    /** You're eating: puppy eyes. */
    void onOwnerEat(Player player) {
        Wolf dog = findDog(player);
        if (!isNear(dog, player, 16) || !canReact(player, "food", 60)) {
            return;
        }
        sound(dog, Sound.WOLF_WHINE, 1.2f);
        tell(player, Text.BEG_FOOD);
    }

    /** You went to bed: he curls up next to you (and it recharges him). */
    void onBedtime(Player player) {
        Wolf dog = findDog(player);
        if (!isNear(dog, player, 24) || !canReact(player, "bed", 300)) {
            return;
        }
        dog.setSitting(true);
        changeNeeds(plugin.data(player), 0, 40, 0);
        tell(player, Text.BEDTIME);
    }

    // ------------------------------------------------------------------ items

    void whistle(Player player) {
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 1.6f);
        Wolf dog = findDog(player);
        if (dog == null) {
            dog = spawnDog(player, player.getLocation());
        } else {
            dog.setSitting(false);
            dog.teleport(player.getLocation());
        }
        boolean hasCat = plugin.data(player).hasCat;
        if (hasCat) {
            Ocelot cat = findCat(player);
            if (cat == null) {
                spawnCat(player, player.getLocation());
            } else {
                cat.setSitting(false);
                cat.teleport(player.getLocation());
            }
        }
        tell(player, (hasCat ? Text.BOTH_CAME : Text.CAME_RUNNING) + "  " + friendshipBar(plugin.data(player)));
    }

    void treat(Player player, Entity target) {
        if (target instanceof Ocelot) {
            sound(target, Sound.CAT_PURR, 1f);
            particles(target.getLocation(), Effect.HEART, 4);
            tell(player, Text.TREAT_CAT);
            return;
        }
        Wolf dog = (Wolf) target;
        hearts(dog);
        sound(dog, Sound.WOLF_BARK, 1f);
        changeNeeds(plugin.data(player), 2, 0, 30);
        boolean earned = earn(player, "treat", 5, 60);
        tell(player, pick(Text.TREAT) + (earned ? "" : Text.FULL) + "  " + friendshipBar(plugin.data(player)));
    }

    void brush(Player player, Entity target) {
        particles(target.getLocation().add(0, 0.6, 0), Effect.HAPPY_VILLAGER, 8);
        if (target instanceof Ocelot) {
            sound(target, Sound.CAT_PURR, 1f);
            tell(player, Text.BRUSH_CAT);
            return;
        }
        sound(target, Sound.WOLF_PANT, 1f);
        changeNeeds(plugin.data(player), 5, 0, 0);
        boolean earned = earn(player, "brush", 3, 30);
        tell(player, pick(Text.BRUSH) + (earned ? "" : Text.FULL) + "  " + friendshipBar(plugin.data(player)));
    }

    void pet(Player player, Wolf dog) {
        hearts(dog);
        sound(dog, Sound.WOLF_PANT, 1f);
        changeNeeds(plugin.data(player), 5, 0, 0);
        boolean earned = earn(player, "pet", 2, 20);
        tell(player, pick(Text.PET) + (earned ? "" : Text.FULL) + "  " + friendshipBar(plugin.data(player)));
    }

    void play(Player player) {
        Wolf dog = findDog(player);
        if (!isNear(dog, player, 16)) {
            tell(player, Text.TOO_FAR);
            return;
        }
        zoomies(dog);
        Ocelot cat = findCat(player);
        if (isNear(cat, player, 16)) {
            hop(cat, 0.4);
            sound(cat, Sound.CAT_MEOW, 1f);
        }
        boolean earned = earn(player, "ball", 4, 45);
        tell(player, pick(Text.PLAY) + (earned ? "" : Text.FULL) + "  " + friendshipBar(plugin.data(player)));
    }

    // ------------------------------------------------------------------ tricks

    /** Does one trick. 1.8.8 has no custom animations, so tricks use vanilla moves and effects. */
    void trick(final Player player, final Wolf dog, String action) {
        if ("love".equals(action)) {
            hearts(dog);
            sound(dog, Sound.WOLF_PANT, 1f);
            hop(dog, 0.3);
        } else if ("sit".equals(action) || "lie".equals(action) || "yawn".equals(action)) {
            dog.setSitting(true);
            sound(dog, "yawn".equals(action) ? Sound.WOLF_PANT : Sound.WOLF_BARK, 1f);
        } else if ("come".equals(action)) {
            dog.setSitting(false);
            if (!isNear(dog, player, 32)) {
                dog.teleport(player.getLocation());
            } else {
                walkTo(dog, player.getLocation(), 1.4); // he walks (runs!) over instead of teleporting
            }
            sound(dog, Sound.WOLF_BARK, 1f);
        } else if ("roll".equals(action)) {
            dog.setSitting(false);
            spin(dog, 2);
            hop(dog, 0.3);
            hearts(dog);
        } else if ("dead".equals(action)) {
            dog.setSitting(true);
            sound(dog, Sound.WOLF_WHINE, 0.6f);
            dog.playEffect(EntityEffect.WOLF_SMOKE);
            later(30, new Runnable() {
                @Override
                public void run() {
                    if (dog.isValid()) {
                        hearts(dog); // "I'm alive!"
                    }
                }
            });
        } else if ("beg".equals(action)) {
            dog.setSitting(true);
            sound(dog, Sound.WOLF_WHINE, 1.2f);
            hearts(dog);
        } else if ("shakeoff".equals(action)) {
            dog.playEffect(EntityEffect.WOLF_SHAKE);
            sound(dog, Sound.WOLF_SHAKE, 1f);
        } else if ("paw".equals(action) || "highfive".equals(action)) {
            hearts(dog);
            sound(dog, "highfive".equals(action) ? Sound.ORB_PICKUP : Sound.WOLF_PANT, 1.4f);
            particles(dog.getLocation().add(0, 0.8, 0), Effect.HAPPY_VILLAGER, 6);
        } else if ("wave".equals(action) || "greet".equals(action)) {
            sound(dog, Sound.WOLF_BARK, 1f);
            hearts(dog);
        } else if ("bow".equals(action)) {
            sound(dog, Sound.WOLF_BARK, 1f);
            hop(dog, 0.25);
        } else if ("dance".equals(action)) {
            dog.setSitting(false);
            dance(dog);
        } else if ("backflip".equals(action)) {
            dog.setSitting(false);
            hop(dog, 0.7);
            spin(dog, 1);
            particles(dog.getLocation().add(0, 1, 0), Effect.HAPPY_VILLAGER, 8);
        } else if ("howl".equals(action)) {
            dog.setSitting(true);
            sound(dog, Sound.WOLF_HOWL, 1f);
            particles(dog.getLocation().add(0, 1.2, 0), Effect.NOTE, 5);
        } else if ("chase".equals(action)) {
            dog.setSitting(false);
            spin(dog, 3);
            sound(dog, Sound.WOLF_BARK, 1f);
        } else if ("spin".equals(action)) {
            spin(dog, 1);
        } else if ("hop".equals(action)) {
            sound(dog, Sound.WOLF_BARK, 1f);
            hop(dog, 0.55);
        } else if ("bark".equals(action)) {
            sound(dog, Sound.WOLF_BARK, 1f);
            later(8, new Runnable() {
                @Override
                public void run() {
                    sound(dog, Sound.WOLF_BARK, 1f);
                }
            });
        } else if ("bone".equals(action)) {
            hearts(dog);
            sound(dog, Sound.WOLF_PANT, 1f);
        } else if ("shock".equals(action)) {
            hop(dog, 0.4);
            sound(dog, Sound.WOLF_WHINE, 1f);
        } else if ("excited".equals(action)) {
            hop(dog, 0.35);
            sound(dog, Sound.WOLF_WHINE, 1f);
        } else if ("play".equals(action)) {
            play(player);
        } else if ("fetch".equals(action)) {
            throwBall(player);
        } else if ("mochi".equals(action)) {
            Ocelot cat = findCat(player);
            if (cat != null) {
                sound(cat, Sound.CAT_MEOW, 1f);
                particles(cat.getLocation(), Effect.HEART, 4);
            }
        }
    }

    // ------------------------------------------------------------------ effects

    void hearts(Wolf dog) {
        dog.playEffect(EntityEffect.WOLF_HEARTS);
    }

    void particles(Location at, Effect effect, int count) {
        for (int i = 0; i < count; i++) {
            Location spot = at.clone().add(random.nextDouble() - 0.5, 0.4 + random.nextDouble() * 0.6, random.nextDouble() - 0.5);
            at.getWorld().playEffect(spot, effect, 0);
        }
    }

    /** The "he heard you" note above his head (the speaker icon in the Bedrock version). */
    void heardYou(Wolf dog) {
        dog.getWorld().playEffect(dog.getLocation().add(0, 1.3, 0), Effect.NOTE, 0);
    }

    void sound(Entity at, Sound sound, float pitch) {
        at.getWorld().playSound(at.getLocation(), sound, 1f, pitch);
    }

    void hop(Entity pet, double strength) {
        pet.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.3, strength, (random.nextDouble() - 0.5) * 0.3));
    }

    /** Turns him around on the spot (turns full circles). */
    void spin(final Entity pet, final int turns) {
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!pet.isValid() || step++ >= turns * 12) {
                    cancel();
                    return;
                }
                Location l = pet.getLocation();
                l.setYaw(l.getYaw() + 30);
                pet.teleport(l);
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    private void zoomies(final Wolf dog) {
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!dog.isValid() || step >= 4) {
                    cancel();
                    return;
                }
                hop(dog, 0.45);
                if (step % 2 == 0) {
                    sound(dog, Sound.WOLF_BARK, 1f);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 12L);
        spin(dog, 1);
    }

    private void dance(final Wolf dog) {
        final float[] tune = {1f, 1.26f, 1.5f, 1.26f, 1.68f, 1.5f};
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!dog.isValid() || step >= tune.length) {
                    cancel();
                    return;
                }
                dog.getWorld().playSound(dog.getLocation(), Sound.NOTE_PIANO, 1f, tune[step]);
                dog.getWorld().playEffect(dog.getLocation().add(0, 1.2, 0), Effect.NOTE, 0);
                if (step % 2 == 0) {
                    hop(dog, 0.4);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
        spin(dog, 2);
    }

    // ------------------------------------------------------------------ scheduling

    void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
    }

    void say(final Player player, String[] lines, long gap) {
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            later(i * gap, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        tell(player, line);
                    }
                }
            });
        }
    }
}
