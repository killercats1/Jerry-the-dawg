package com.jerrythedawg;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
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
    private static final int PUP_LEVEL = 3;
    private static final Material[] GIFTS = {
        Material.STICK, Material.BONE, Material.RED_ROSE, Material.YELLOW_FLOWER, Material.FEATHER, Material.EMERALD};
    private static final Material[] CAT_GIFTS = {Material.STRING, Material.FEATHER, Material.RAW_FISH};

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
    /** Thrown tennis balls in flight (nothing can pick up, burn or despawn these). */
    private final Set<UUID> thrownBalls = new HashSet<UUID>();
    private int ballCounter;
    private final Home home;
    private final Holograms holograms;
    private long seconds;
    /** Players whose pet is listening ("listen up"): pet id and when it stops (server ticks). */
    private final Map<UUID, Object[]> listening = new HashMap<UUID, Object[]>();
    private long serverTick;

    /** How long the red "!" shows before he reacts (like the Bedrock add-on). */
    static final long ALERT_TICKS = 22;
    static final long LISTEN_TICKS = 200;

    /** Tricks that need practice before he's perfect at them. */
    static final Set<String> PRACTICE = new HashSet<String>(Arrays.asList(
        "paw", "highfive", "wave", "beg", "roll", "dead", "bow", "dance", "backflip", "howl", "chase", "spin", "lie", "hop", "shakeoff",
        "find", "shy", "stretch", "spinleft", "spinright", "scratch", "sneeze", "catch", "backup"));
    static final int MASTER_AT = 5;

    /** What Mochi will do when you ask her (she's a cat: she does the rest on her own terms). */
    static final Set<String> CAT_TRICKS = new HashSet<String>(Arrays.asList(
        "listen", "sit", "lie", "come", "paw", "highfive", "roll", "hop", "spin", "spinleft", "spinright", "chase", "purr", "love",
        "wave", "beg", "dead", "shy", "stretch", "groom", "find", "excited", "yawn", "shock", "play", "bark",
        "hug", "sleep", "sneeze", "scratch", "shakeoff", "catch"));
    static final Set<String> CAT_PRACTICE = new HashSet<String>(Arrays.asList(
        "paw", "highfive", "roll", "hop", "spin", "chase", "wave", "beg", "dead", "shy", "stretch", "groom", "catch"));
    /** Tricks the puppy tries to copy when Jerry does them. */
    private static final Set<String> COPYABLE = new HashSet<String>(Arrays.asList(
        "sit", "lie", "paw", "highfive", "wave", "beg", "roll", "dead", "bow", "dance", "backflip", "howl", "chase", "spin",
        "hop", "bark", "spinleft", "spinright", "stretch", "shy", "shakeoff", "scratch", "sneeze", "hug", "catch", "backup"));

    Pets(JerryPlugin plugin) {
        this.plugin = plugin;
        this.home = new Home(plugin, this);
        this.holograms = new Holograms(plugin);
        new BukkitRunnable() {
            @Override
            public void run() {
                serverTick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    Home home() {
        return home;
    }

    Holograms holograms() {
        return holograms;
    }

    // ------------------------------------------------------------------ "!" and "listen up"

    /** You said his name: a red "!" pops up over his head and he turns to look at you. */
    void alert(Player player, Wolf pet) {
        holograms.alert(pet, ALERT_TICKS);
        if (!isBusy(pet)) {
            face(pet, player.getLocation(), 0);
        }
        sound(pet, Sound.WOLF_WHINE, pet.isAdult() ? 1.6f : 1.9f);
    }

    /** "Listen up!": he sits, the speaker shows over his head and notes float up until you tell him something. */
    void listen(Player player, final Wolf pet) {
        final boolean wasSitting = pet.isSitting();
        pet.setSitting(true);
        face(pet, player.getLocation(), 0);
        sound(pet, Sound.WOLF_WHINE, pet.isAdult() ? 1.5f : 1.9f);
        holograms.speaker(pet, LISTEN_TICKS);
        listening.put(player.getUniqueId(), new Object[] {pet.getUniqueId(), serverTick + LISTEN_TICKS, wasSitting});
        final UUID owner = player.getUniqueId();
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                Object[] l = listening.get(owner);
                if (!pet.isValid() || l == null || !pet.getUniqueId().equals(l[0]) || step++ * 10 >= LISTEN_TICKS) {
                    if (l != null && pet.getUniqueId().equals(l[0]) && serverTick >= (Long) l[1]) {
                        listening.remove(owner);
                    }
                    cancel();
                    return;
                }
                pet.getWorld().playEffect(pet.getLocation().add(0, pet.isAdult() ? 1.2 : 0.8, 0), Effect.NOTE, 0);
            }
        }.runTaskTimer(plugin, 5L, 10L);
    }

    boolean isListening(Player player) {
        Object[] l = listening.get(player.getUniqueId());
        return l != null && serverTick < (Long) l[1];
    }

    /** You told him something: he gets up (unless he was already sitting) and gets on with it. */
    void stopListening(Player player) {
        Object[] l = listening.remove(player.getUniqueId());
        if (l == null || serverTick >= (Long) l[1]) {
            return;
        }
        Entity pet = find((UUID) l[0]);
        if (pet instanceof Wolf && pet.isValid()) {
            ((Wolf) pet).setSitting((Boolean) l[2]);
            if (holograms.showing(pet, Holograms.SPEAKER)) {
                holograms.hide(pet);
            }
        }
    }

    // ------------------------------------------------------------------ text

    String personalize(PetData data, String text) {
        return text.replace("Jerry", "\u0001").replace("Mochi", "\u0002").replace("Pip", "\u0003")
            .replace("\u0001", data.dogName).replace("\u0002", data.catName).replace("\u0003", data.pupName);
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

    Wolf findPup(Player player) {
        PetData data = plugin.data(player);
        if (!data.hasPup) {
            return null;
        }
        Entity e = find(data.pupId);
        return e instanceof Wolf ? (Wolf) e : null;
    }

    /** True for any player's dog, cat or puppy (they can't be hurt). */
    boolean isPet(Entity entity) {
        UUID id = entity.getUniqueId();
        for (PetData data : plugin.allData().values()) {
            if (id.equals(data.dogId) || id.equals(data.catId) || id.equals(data.pupId)) {
                return true;
            }
        }
        return false;
    }

    /** True for any player's puppy (it never fights). */
    boolean isPup(Entity entity) {
        UUID id = entity.getUniqueId();
        for (PetData data : plugin.allData().values()) {
            if (id.equals(data.pupId)) {
                return true;
            }
        }
        return false;
    }

    boolean isThrownBall(Entity entity) {
        return thrownBalls.contains(entity.getUniqueId());
    }

    /** On shutdown: a ball still in flight would otherwise become a spare ball someone can pick up. */
    void removeThrownBalls() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (thrownBalls.contains(item.getUniqueId())) {
                    item.remove();
                }
            }
        }
        thrownBalls.clear();
    }

    boolean isBusy(Entity pet) {
        return busy.contains(pet.getUniqueId());
    }

    void setBusy(Entity pet, boolean on) {
        if (on) {
            busy.add(pet.getUniqueId());
        } else {
            busy.remove(pet.getUniqueId());
        }
    }

    /** The online player who owns this pet, or null. */
    Player ownerOf(Entity entity) {
        UUID id = entity.getUniqueId();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PetData data = plugin.data(player);
            if (id.equals(data.dogId) || id.equals(data.catId) || id.equals(data.pupId)) {
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
        boolean isPup = entity instanceof Wolf && entity.getCustomName().equals(data.pupName) && data.pupId != null;
        if (isDog || isCat || isPup) {
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
        wolf.setCollarColor(data.collar);
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
        cat.setCatType(data.catCoat);
        cat.setCustomName(data.catName);
        cat.setRemoveWhenFarAway(false);
        data.catId = cat.getUniqueId();
        cache.put(cat.getUniqueId(), cat);
        plugin.markDirty();
        return cat;
    }

    Wolf spawnPup(Player player, Location at) {
        PetData data = plugin.data(player);
        Wolf pup = at.getWorld().spawn(at, Wolf.class);
        pup.setBaby();
        pup.setAgeLock(true); // stays a puppy forever
        pup.setTamed(true);
        pup.setOwner(player);
        pup.setCollarColor(data.pupCollar);
        pup.setCustomName(data.pupName);
        pup.setRemoveWhenFarAway(false);
        data.pupId = pup.getUniqueId();
        cache.put(pup.getUniqueId(), pup);
        plugin.markDirty();
        return pup;
    }

    /** /dog catcoat ginger|black|siamese */
    void setCatCoat(Player player, String which) {
        PetData data = plugin.data(player);
        if (!data.hasCat) {
            tell(player, Text.NO_CAT);
            return;
        }
        String w = which.toLowerCase(java.util.Locale.ROOT).trim();
        Ocelot.Type type;
        String name;
        if (w.startsWith("black") || w.startsWith("tux")) {
            type = Ocelot.Type.BLACK_CAT;
            name = "black";
        } else if (w.startsWith("siam") || w.startsWith("white") || w.startsWith("cream")) {
            type = Ocelot.Type.SIAMESE_CAT;
            name = "siamese";
        } else if (w.startsWith("ginger") || w.startsWith("orange") || w.startsWith("red") || w.startsWith("tabby")) {
            type = Ocelot.Type.RED_CAT;
            name = "ginger";
        } else {
            tell(player, Text.CAT_COAT_HELP);
            return;
        }
        data.catCoat = type;
        plugin.markDirty();
        Ocelot cat = findCat(player);
        if (cat != null) {
            cat.setCatType(type);
            particles(cat.getLocation(), Effect.HAPPY_VILLAGER, 6);
            sound(cat, Sound.CAT_PURREOW, 1f);
        }
        tell(player, Text.CAT_COAT_SET.replace("{coat}", name));
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
                    if (!data.gotFurniture) {
                        home.giveFurniture(player); // new in this version: everyone gets a bed and a bowl once
                        tell(player, Text.FURNITURE_NEW);
                    }
                    return;
                }
                data.started = true;
                Wolf wolf = spawnDog(player, player.getLocation());
                Items.giveAll(player);
                home.giveFurniture(player);
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
        Wolf pup = findPup(player);
        if (pup != null) {
            pup.remove();
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

    /** Renames the "dog", "cat" or "pup" (from /dog name, a name tag, ...). Returns false if nothing changed. */
    boolean rename(Player player, String kind, String raw, boolean quiet) {
        PetData data = plugin.data(player);
        boolean cat = "cat".equals(kind);
        boolean pup = "pup".equals(kind);
        String current = cat ? data.catName : pup ? data.pupName : data.dogName;
        LivingEntity pet = cat ? findCat(player) : pup ? findPup(player) : findDog(player);
        String name = cleanName(raw);
        if (name.isEmpty() || name.equals(current)) {
            if (pet != null && !current.equals(pet.getCustomName())) {
                pet.setCustomName(current);
            }
            return false;
        }
        if (cat) {
            data.catName = name;
        } else if (pup) {
            data.pupName = name;
        } else {
            data.dogName = name;
        }
        plugin.markDirty();
        if (pet != null) {
            pet.setCustomName(name);
            hearts(pet);
        }
        if (!quiet) {
            tell(player, cat ? Text.CAT_RENAMED : pup ? Text.PUP_RENAMED : Text.DOG_RENAMED);
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
        Wolf pup = findPup(player);
        // Renamed with a name tag?
        if (dog != null && dog.getCustomName() != null && !dog.getCustomName().equals(data.dogName)) {
            rename(player, "dog", dog.getCustomName(), false);
        }
        if (cat != null && cat.getCustomName() != null && !cat.getCustomName().equals(data.catName)) {
            rename(player, "cat", cat.getCustomName(), false);
        }
        if (pup != null && pup.getCustomName() != null && !pup.getCustomName().equals(data.pupName)) {
            rename(player, "pup", pup.getCustomName(), false);
        }
        // A new collar colour (dyed)? Keep it for next time he's whistled or respawned.
        if (dog != null && dog.getCollarColor() != data.collar) {
            data.collar = dog.getCollarColor();
            plugin.markDirty();
        }
        if (pup != null && pup.getCollarColor() != data.pupCollar) {
            data.pupCollar = pup.getCollarColor();
            plugin.markDirty();
        }
        if (levelOf(data.friendship) >= CAT_LEVEL && !data.hasCat) {
            catArrives(player);
        }
        if (levelOf(data.friendship) >= PUP_LEVEL && !data.hasPup) {
            pupArrives(player);
        }
        if (dog != null) {
            keepClose(player, dog, dog.isSitting()); // (skips while he's fighting or fetching)
            reactToSurroundings(player, dog);
            if (seconds % 5 == 0 && isNear(dog, player, 24)) {
                home.visit(player, dog); // food bowl? dog bed?
            }
        }
        if (cat != null) {
            keepClose(player, cat, cat.isSitting());
            if (isNear(cat, player, 16)) {
                catLife(player, cat);
            }
        }
        if (pup != null) {
            keepClose(player, pup, pup.isSitting());
        }
        if (!isNear(dog, player, 16)) {
            return;
        }
        if (seconds % 60 == 0) {
            updateNeeds(data, dog.isSitting(), isNight(player.getWorld()), dog.isSitting() && home.onBed(dog, data));
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
            boolean pupMoment = !catMoment && isNear(pup, player, 16) && random.nextInt(3) == 0;
            sound(dog, random.nextBoolean() ? Sound.WOLF_PANT : Sound.WOLF_BARK, 1f);
            if (random.nextInt(4) == 0) {
                hop(dog, 0.45);
            }
            if (pupMoment) {
                hop(pup, 0.35);
                sound(pup, Sound.WOLF_BARK, 1.8f);
            }
            if (!data.quiet) {
                tell(player, catMoment ? pick(Text.CAT_MOMENTS) : pupMoment ? pick(Text.PUP_MOMENTS) : pick(Text.MOMENTS));
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

    private void pupArrives(Player player) {
        PetData data = plugin.data(player);
        data.hasPup = true;
        Wolf pup = spawnPup(player, player.getLocation());
        sound(pup, Sound.WOLF_BARK, 1.8f);
        hearts(pup);
        player.sendMessage("§a§lA new friend!");
        say(player, Text.PUP_ARRIVES, 60);
    }

    /** Mochi does her own thing: the odd burst of zoomies, naps at night, and hissing at monsters. */
    private void catLife(Player player, Ocelot cat) {
        PetData data = plugin.data(player);
        if (seconds % 3 == 0) {
            for (Entity e : cat.getNearbyEntities(8, 4, 8)) {
                if (e instanceof Monster && canReact(player, "cathiss", 30)) {
                    sound(cat, Sound.CAT_HISS, 1f);
                    tell(player, Text.CAT_HISS.replace("{mob}", e.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
                    return;
                }
            }
        }
        if (cat.isSitting() || isBusy(cat)) {
            return;
        }
        if (isNight(cat.getWorld()) && random.nextInt(120) == 0 && canReact(player, "catnap", 600)) {
            cat.setSitting(true);
            sound(cat, Sound.CAT_PURR, 0.8f);
            if (!data.quiet) {
                tell(player, Text.CAT_NAP);
            }
        } else if (random.nextInt(150) == 0 && canReact(player, "catzoom", 120)) {
            catZoomies(cat);
            if (!data.quiet) {
                tell(player, pick(Text.CAT_ZOOMIES));
            }
        }
    }

    private void catZoomies(final Ocelot cat) {
        new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (!cat.isValid() || step >= 5) {
                    cancel();
                    return;
                }
                cat.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.9, 0.4, (random.nextDouble() - 0.5) * 0.9));
                if (step % 2 == 0) {
                    sound(cat, Sound.CAT_MEOW, 1.3f);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void bringGift(Player player, Wolf dog) {
        // Sometimes it's Mochi with a present instead.
        Ocelot cat = findCat(player);
        if (isNear(cat, player, 16) && random.nextInt(100) < 35) {
            int c = random.nextInt(CAT_GIFTS.length);
            cat.getWorld().dropItemNaturally(cat.getLocation(), new ItemStack(CAT_GIFTS[c], 1));
            sound(cat, Sound.CAT_PURREOW, 1f);
            particles(cat.getLocation(), Effect.HAPPY_VILLAGER, 6);
            tell(player, Text.CAT_GIFT.replace("{gift}", Text.CAT_GIFT_NAMES[c]));
            return;
        }
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
    private void updateNeeds(PetData data, boolean resting, boolean night, boolean onBed) {
        changeNeeds(data, resting ? -1 : -3, onBed ? 16 : resting ? 8 : (night ? -4 : -2), -3);
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
        if (data.hasCat || data.hasPup) {
            tell(player, "§fFamily: " + (data.hasCat ? "§dMochi§7 the cat" : "") + (data.hasCat && data.hasPup ? "§7, " : "")
                + (data.hasPup ? "§aPip§7 the puppy" : ""));
        }
    }

    // ------------------------------------------------------------------ practice makes perfect

    static int skill(PetData data, String trick) {
        Integer done = data.skills.get(trick);
        return done == null ? 0 : done;
    }

    /** Returns true if he pulls the trick off. Early on he sometimes does something else instead. */
    boolean attemptTrick(Player player, Wolf dog, String action) {
        return attempt(player, dog, action, action, Text.OOPS, Text.MASTERED);
    }

    /** Same for Mochi (her practice is saved separately, as "cat:roll" and so on). */
    boolean attemptCatTrick(Player player, Ocelot cat, String action) {
        return attempt(player, cat, action, "cat:" + action, Text.CAT_OOPS, Text.CAT_MASTERED);
    }

    static String trickName(String action) {
        return Text.TRICK_NAMES.containsKey(action) ? Text.TRICK_NAMES.get(action) : action;
    }

    private boolean attempt(final Player player, final LivingEntity pet, String action, String key, String[] oopsTexts, final String mastered) {
        final PetData data = plugin.data(player);
        int done = skill(data, key);
        if (done >= MASTER_AT) {
            return true;
        }
        final String name = trickName(action);
        if (random.nextDouble() > 0.6 + done * 0.08) {
            int oops = random.nextInt(oopsTexts.length);
            if (oops == 0) {
                spin(pet, 1);
            } else if (oops == 2) {
                setSitting(pet, true);
            } else {
                hop(pet, 0.45);
            }
            sound(pet, pet instanceof Ocelot ? Sound.CAT_MEOW : Sound.WOLF_WHINE, pet instanceof Ocelot ? 0.8f : 1f);
            tell(player, oopsTexts[oops].replace("{trick}", name));
            return false;
        }
        data.skills.put(key, done + 1);
        plugin.markDirty();
        if (done + 1 == MASTER_AT) {
            later(30, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }
                    tell(player, mastered.replace("{trick}", name));
                    player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1.2f);
                    if (pet.isValid()) {
                        hearts(pet);
                    }
                    addFriendship(player, 3);
                }
            });
        }
        return true;
    }

    static void setSitting(Entity pet, boolean sitting) {
        if (pet instanceof Wolf) {
            ((Wolf) pet).setSitting(sitting);
        } else if (pet instanceof Ocelot) {
            ((Ocelot) pet).setSitting(sitting);
        }
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
        // Each thrown ball is one of a kind, so two balls in the air can't merge into one.
        ItemStack stack = Items.BALL.create();
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        java.util.List<String> lore = new java.util.ArrayList<String>(meta.getLore());
        lore.add("§0#" + (++ballCounter));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        final Item ball = player.getWorld().dropItem(eye.clone().add(eye.getDirection()), stack);
        ball.setPickupDelay(6000); // nobody grabs it but him
        thrownBalls.add(ball.getUniqueId()); // and lava, cactus, fire, hoppers and despawning can't take it either
        // A gentle throw keeps the ball inside the 10 blocks where vanilla "follow owner" kicks in,
        // so that habit doesn't tug him back to you halfway there.
        ball.setVelocity(eye.getDirection().multiply(0.8).add(new Vector(0, 0.2, 0)));
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
                    if (ticks % 5 == 0) {
                        walkTo(dog, ball.getLocation(), 1.6);
                    }
                    boolean reached = dog.getWorld().equals(ball.getWorld()) && dog.getLocation().distance(ball.getLocation()) < 1.6;
                    if (reached || ticks > 240) {
                        if (!reached) {
                            dog.teleport(ball.getLocation());
                        }
                        carrying = true; // the ball now follows just above his head (see below)
                        ticks = 0;
                        dog.getWorld().playSound(dog.getLocation(), Sound.ITEM_PICKUP, 1f, 1f);
                        tell(player, Text.FETCH_GOT_IT);
                    }
                } else {
                    // Carry the ball: keep it floating at his head. (Making it ride him would stop him walking.)
                    ball.teleport(dog.getLocation().add(0, 0.95, 0));
                    ball.setVelocity(new Vector(0, 0, 0));
                    if (ticks % 5 == 1) {
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
                thrownBalls.remove(ball.getUniqueId());
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

    boolean canReact(Player player, String kind, long cooldown) {
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
    boolean isFighting(LivingEntity pet, Player player) {
        if (!(pet instanceof Creature)) {
            return false;
        }
        Creature creature = (Creature) pet;
        LivingEntity target = creature.getTarget();
        if (target == null) {
            return false;
        }
        boolean stillThere = target.isValid() && !target.isDead() && target.getWorld().equals(pet.getWorld())
            && target.getLocation().distance(pet.getLocation()) < 32;
        if (!stillThere) {
            creature.setTarget(null); // it's gone: stop chasing a ghost
        }
        return stillThere;
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
        if (!isNear(dog, player, 32) || busy.contains(dog.getUniqueId()) || isFighting(dog, player)
            || !canReact(player, "hurt", 15)) {
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
        if (!home.goToBed(player, dog, true)) {
            dog.setSitting(true);
        }
        changeNeeds(plugin.data(player), 0, 40, 0);
        String pupText = "";
        Wolf pup = findPup(player);
        if (isNear(pup, player, 24)) {
            pup.setSitting(true);
            pupText = Text.BEDTIME_PUP;
        }
        Ocelot cat = findCat(player);
        if (isNear(cat, player, 24)) {
            cat.setSitting(true);
            sound(cat, Sound.CAT_PURR, 0.8f);
            tell(player, Text.BEDTIME_CAT + pupText);
        } else {
            tell(player, Text.BEDTIME + pupText);
        }
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
        PetData data = plugin.data(player);
        if (data.hasCat) {
            Ocelot cat = findCat(player);
            if (cat == null) {
                spawnCat(player, player.getLocation());
            } else {
                cat.setSitting(false);
                cat.teleport(player.getLocation());
            }
        }
        if (data.hasPup) {
            Wolf pup = findPup(player);
            if (pup == null) {
                spawnPup(player, player.getLocation());
            } else {
                pup.setSitting(false);
                pup.teleport(player.getLocation());
            }
        }
        String who = data.hasCat && data.hasPup ? Text.ALL_CAME : data.hasPup ? Text.PUP_CAME : data.hasCat ? Text.BOTH_CAME : Text.CAME_RUNNING;
        tell(player, who + "  " + friendshipBar(data));
    }

    void treat(Player player, Entity target) {
        if (target instanceof Ocelot) {
            sound(target, Sound.CAT_PURR, 1f);
            particles(target.getLocation(), Effect.HEART, 4);
            tell(player, Text.TREAT_CAT);
            return;
        }
        if (isPup(target)) {
            hearts(target);
            sound(target, Sound.WOLF_BARK, 1.8f);
            tell(player, Text.TREAT_PUP);
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
        if (isPup(target)) {
            sound(target, Sound.WOLF_PANT, 1.7f);
            tell(player, Text.BRUSH_PUP);
            return;
        }
        sound(target, Sound.WOLF_PANT, 1f);
        changeNeeds(plugin.data(player), 5, 0, 0);
        boolean earned = earn(player, "brush", 3, 30);
        tell(player, pick(Text.BRUSH) + (earned ? "" : Text.FULL) + "  " + friendshipBar(plugin.data(player)));
    }

    /** Sneak + right-click Mochi with an empty hand: purrs. */
    void petCat(Player player, final Ocelot cat) {
        sound(cat, Sound.CAT_PURR, 1f);
        later(20, new Runnable() {
            @Override
            public void run() {
                if (cat.isValid()) {
                    sound(cat, Sound.CAT_PURR, 1.1f);
                }
            }
        });
        hearts(cat);
        boolean earned = earn(player, "petcat", 2, 20);
        tell(player, pick(Text.PET_CAT) + (earned ? "" : Text.FULL) + "  " + friendshipBar(plugin.data(player)));
    }

    void pet(Player player, Wolf dog) {
        if (isPup(dog)) {
            hearts(dog);
            sound(dog, Sound.WOLF_PANT, 1.7f);
            tell(player, pick(Text.PET_PUP));
            return;
        }
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
        if ("listen".equals(action)) {
            listen(player, dog);
        } else if ("love".equals(action)) {
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
            shakeOff(dog, 1f);
        } else if ("scratch".equals(action)) {
            scratch(dog, 1f);
        } else if ("hug".equals(action)) {
            hug(player, dog);
        } else if ("sneeze".equals(action)) {
            sneeze(dog, 1f);
        } else if ("catch".equals(action)) {
            catchTreat(player, dog, 1f);
        } else if ("backup".equals(action)) {
            backUp(player, dog);
        } else if ("sleep".equals(action)) {
            changeNeeds(plugin.data(player), 0, 10, 0);
            sound(dog, Sound.WOLF_PANT, 0.6f);
            if (!home.goToBed(player, dog, true)) { // he has a bed: off he goes
                dog.setSitting(true);
                snore(dog);
            }
        } else if ("groom".equals(action)) {
            sound(dog, Sound.WOLF_PANT, 1.2f);
            particles(dog.getLocation().add(0, 0.6, 0), Effect.HAPPY_VILLAGER, 3);
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
        } else if ("spinleft".equals(action) || "spinright".equals(action)) {
            dog.setSitting(false);
            spin(dog, 1, "spinleft".equals(action) ? -30 : 30);
        } else if ("find".equals(action)) {
            findIt(player, dog);
        } else if ("shy".equals(action)) {
            shy(player, dog);
        } else if ("stretch".equals(action)) {
            final boolean wasSitting = dog.isSitting();
            dog.setSitting(true); // front paws out, bum up... as close as a 1.8 wolf gets
            sound(dog, Sound.WOLF_PANT, 0.7f);
            later(25, new Runnable() {
                @Override
                public void run() {
                    if (dog.isValid()) {
                        dog.setSitting(wasSitting);
                        sound(dog, Sound.WOLF_WHINE, 0.6f); // the big yawn at the end
                    }
                }
            });
        } else if ("purr".equals(action)) {
            sound(dog, Sound.WOLF_GROWL, 1.6f); // he tries his best
            hearts(dog);
        } else if ("pupHi".equals(action)) {
            Wolf pup = findPup(player);
            if (pup != null) {
                sound(pup, Sound.WOLF_BARK, 1.8f);
                hearts(pup);
            }
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

    /** "Find it!": he sniffs around a few spots, then finds a treat. */
    private void findIt(final Player player, final Wolf dog) {
        if (busy.contains(dog.getUniqueId())) {
            return;
        }
        dog.setSitting(false);
        busy.add(dog.getUniqueId());
        final int spots = 2 + random.nextInt(2);
        new BukkitRunnable() {
            private int step;
            private Location spot;

            @Override
            public void run() {
                if (!dog.isValid() || !player.isOnline()) {
                    done();
                    return;
                }
                if (step % 2 == 0) {
                    spot = dog.getLocation().add(random.nextDouble() * 10 - 5, 0, random.nextDouble() * 10 - 5);
                    walkTo(dog, spot, 1.1);
                } else {
                    dog.getWorld().playEffect(dog.getLocation(), Effect.SMOKE, 4); // *sniff sniff*
                    dog.getWorld().playEffect(dog.getLocation().add(0, 0.3, 0), Effect.SMOKE, 4);
                    sound(dog, Sound.WOLF_PANT, 1.4f);
                }
                if (++step >= spots * 2) {
                    done();
                    hearts(dog);
                    sound(dog, Sound.EAT, 1f);
                    sound(dog, Sound.WOLF_BARK, 1f);
                    changeNeeds(plugin.data(player), 5, 0, 10);
                    tell(player, pick(Text.FOUND_IT));
                }
            }

            private void done() {
                cancel();
                busy.remove(dog.getUniqueId());
            }
        }.runTaskTimer(plugin, 5L, 20L);
    }

    /** "Are you shy?": he turns away, then peeks back at you. */
    private void shy(final Player player, final LivingEntity dog) {
        sound(dog, dog instanceof Ocelot ? Sound.CAT_MEOW : Sound.WOLF_WHINE, 1.4f);
        face(dog, player.getLocation(), 180);
        later(35, new Runnable() {
            @Override
            public void run() {
                if (dog.isValid() && player.isOnline()) {
                    face(dog, player.getLocation(), 0); // ...peekaboo!
                    hearts(dog);
                }
            }
        });
    }

    /** Turns the pet to face a spot (offset 180 = facing away from it). */
    void face(Entity pet, Location spot, float offset) {
        Location l = pet.getLocation();
        double dx = spot.getX() - l.getX();
        double dz = spot.getZ() - l.getZ();
        l.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)) + offset);
        pet.teleport(l);
    }

    /** Mochi's tricks (cats do fewer tricks, but they're very good at them). */
    void catTrick(Player player, Ocelot cat, String action) {
        if ("listen".equals(action)) {
            cat.getWorld().playSound(cat.getLocation(), Sound.CAT_MEOW, 0.4f, 1.7f); // a tiny "mrp"
        } else if ("sit".equals(action) || "lie".equals(action)) {
            cat.setSitting(true);
            sound(cat, Sound.CAT_MEOW, 1f);
        } else if ("come".equals(action)) {
            cat.setSitting(false);
            if (!isNear(cat, player, 32)) {
                cat.teleport(player.getLocation());
            } else {
                walkTo(cat, player.getLocation(), 1.3);
            }
            sound(cat, Sound.CAT_MEOW, 1.2f);
        } else if ("paw".equals(action) || "highfive".equals(action)) {
            hearts(cat);
            sound(cat, Sound.CAT_PURREOW, 1f);
        } else if ("roll".equals(action)) {
            cat.setSitting(false);
            spin(cat, 2);
            hop(cat, 0.3);
            hearts(cat);
        } else if ("hop".equals(action)) {
            hop(cat, 0.5);
            sound(cat, Sound.CAT_MEOW, 1.3f);
        } else if ("spin".equals(action) || "spinleft".equals(action) || "spinright".equals(action)) {
            spin(cat, 1, "spinleft".equals(action) ? -30 : 30);
        } else if ("chase".equals(action)) {
            cat.setSitting(false);
            spin(cat, 3);
            sound(cat, Sound.CAT_MEOW, 1.4f);
        } else if ("wave".equals(action) || "bark".equals(action)) {
            sound(cat, Sound.CAT_MEOW, 1f);
            particles(cat.getLocation(), Effect.HAPPY_VILLAGER, 4);
        } else if ("beg".equals(action)) {
            sitFor(cat, 50);
            sound(cat, Sound.CAT_PURREOW, 1f);
        } else if ("dead".equals(action)) {
            sitFor(cat, 60);
            sound(cat, Sound.CAT_MEOW, 0.7f);
            final Ocelot c = cat;
            later(40, new Runnable() {
                @Override
                public void run() {
                    if (c.isValid()) {
                        hearts(c); // "I'm alive!"
                    }
                }
            });
        } else if ("shy".equals(action)) {
            shy(player, cat);
        } else if ("stretch".equals(action) || "groom".equals(action)) {
            sitFor(cat, 50);
            sound(cat, Sound.CAT_PURR, "stretch".equals(action) ? 0.8f : 1f);
        } else if ("find".equals(action)) {
            final Ocelot c = cat;
            cat.setSitting(false);
            walkTo(cat, cat.getLocation().add(random.nextDouble() * 6 - 3, 0, random.nextDouble() * 6 - 3), 1.1);
            later(40, new Runnable() {
                @Override
                public void run() {
                    if (c.isValid()) {
                        hop(c, 0.45); // pounce!
                        sound(c, Sound.CAT_MEOW, 1.3f);
                    }
                }
            });
        } else if ("excited".equals(action)) {
            hop(cat, 0.3);
            sound(cat, Sound.CAT_PURREOW, 1.2f);
        } else if ("yawn".equals(action)) {
            cat.setSitting(true);
            sound(cat, Sound.CAT_PURR, 0.8f);
        } else if ("shock".equals(action)) {
            hop(cat, 0.55);
            sound(cat, Sound.CAT_HISS, 1f);
        } else if ("play".equals(action)) {
            catZoomies(cat);
        } else if ("hug".equals(action)) {
            // she allows it. Briefly. Then a headbutt.
            final Ocelot c = cat;
            hearts(cat);
            sound(cat, Sound.CAT_PURR, 1f);
            later(40, new Runnable() {
                @Override
                public void run() {
                    if (c.isValid()) {
                        hop(c, 0.25);
                    }
                }
            });
        } else if ("sleep".equals(action)) {
            cat.setSitting(true);
            sound(cat, Sound.CAT_PURR, 0.8f);
            snore(cat);
        } else if ("sneeze".equals(action)) {
            sneeze(cat, 1.4f);
        } else if ("scratch".equals(action)) {
            scratch(cat, 1.4f);
        } else if ("shakeoff".equals(action)) {
            shakeOff(cat, 1.5f);
        } else if ("catch".equals(action)) {
            catchTreat(player, cat, 1.4f);
        } else {
            sound(cat, Sound.CAT_PURR, 1f); // purr, love
            hearts(cat);
        }
    }

    /** The puppy's version of a trick. Tiny, enthusiastic, not always correct. */
    void pupTrick(Player player, Wolf pup, String action, boolean ok) {
        if (!ok) {
            spin(pup, 1);
            hop(pup, 0.2);
            sound(pup, Sound.WOLF_WHINE, 1.8f);
            return;
        }
        if ("listen".equals(action)) {
            listen(player, pup);
        } else if ("sit".equals(action) || "lie".equals(action) || "dead".equals(action) || "beg".equals(action)
            || "stretch".equals(action) || "yawn".equals(action)) {
            pup.setSitting(true);
            sound(pup, Sound.WOLF_PANT, 1.7f);
        } else if ("howl".equals(action)) {
            pup.setSitting(true);
            sound(pup, Sound.WOLF_HOWL, 1.9f); // a very small awoo
        } else if ("come".equals(action)) {
            pup.setSitting(false);
            if (!isNear(pup, player, 32)) {
                pup.teleport(player.getLocation());
            } else {
                walkTo(pup, player.getLocation(), 1.4);
            }
            sound(pup, Sound.WOLF_BARK, 1.8f);
        } else if ("roll".equals(action) || "backflip".equals(action) || "dance".equals(action)) {
            pup.setSitting(false);
            spin(pup, 2);
            hop(pup, 0.35);
        } else if ("spin".equals(action) || "chase".equals(action) || "spinleft".equals(action) || "spinright".equals(action)) {
            spin(pup, "chase".equals(action) ? 2 : 1, "spinleft".equals(action) ? -30 : 30);
        } else if ("bark".equals(action) || "greet".equals(action) || "wave".equals(action)) {
            sound(pup, Sound.WOLF_BARK, 1.8f);
        } else if ("shakeoff".equals(action)) {
            shakeOff(pup, 1.6f);
        } else if ("scratch".equals(action)) {
            scratch(pup, 1.7f);
        } else if ("sneeze".equals(action)) {
            sneeze(pup, 1.7f);
        } else if ("catch".equals(action)) {
            catchTreat(player, pup, 1.6f);
        } else if ("backup".equals(action)) {
            backUp(player, pup);
        } else if ("sleep".equals(action)) {
            pup.setSitting(true);
            sound(pup, Sound.WOLF_PANT, 1.2f);
            snore(pup);
        } else if ("hug".equals(action)) {
            if (!isNear(pup, player, 4)) {
                walkTo(pup, player.getLocation(), 1.3);
            }
            hearts(pup);
            sound(pup, Sound.WOLF_PANT, 1.7f);
        } else if ("paw".equals(action) || "highfive".equals(action) || "love".equals(action) || "shy".equals(action)) {
            hearts(pup);
            sound(pup, Sound.WOLF_PANT, 1.7f);
        } else {
            hop(pup, 0.4);
            sound(pup, Sound.WOLF_BARK, 1.8f);
        }
    }

    /** When Jerry does a trick, the puppy often tries it too. */
    void pupCopies(final Player player, final String action) {
        final Wolf pup = findPup(player);
        if (!isNear(pup, player, 16)) {
            return;
        }
        if ("come".equals(action)) {
            pupTrick(player, pup, action, true); // where Jerry goes, the puppy goes
            return;
        }
        if ("sleep".equals(action)) {
            later(20, new Runnable() {
                @Override
                public void run() {
                    if (pup.isValid() && player.isOnline()) {
                        pupTrick(player, pup, action, true); // naps are contagious
                        tell(player, Text.PUP_SLEEP);
                    }
                }
            });
            return;
        }
        if (!COPYABLE.contains(action) || random.nextInt(10) >= 6) {
            return;
        }
        later(20, new Runnable() {
            @Override
            public void run() {
                if (!pup.isValid() || !player.isOnline()) {
                    return;
                }
                boolean ok = random.nextBoolean();
                pupTrick(player, pup, action, ok);
                tell(player, (ok ? pick(Text.PUP_COPY) : pick(Text.PUP_COPY_FAIL)).replace("{trick}", trickName(action)));
            }
        });
    }

    // ------------------------------------------------------------------ the newer tricks
    // 1.8.8 has no custom animations, so these are sitting, moving, sounds and particles.

    static boolean isSitting(Entity pet) {
        return pet instanceof Wolf ? ((Wolf) pet).isSitting() : pet instanceof Ocelot && ((Ocelot) pet).isSitting();
    }

    /** Sits for a moment, then goes back to how he was. */
    void sitFor(final Entity pet, long ticks) {
        final boolean was = isSitting(pet);
        setSitting(pet, true);
        later(ticks, new Runnable() {
            @Override
            public void run() {
                if (pet.isValid()) {
                    setSitting(pet, was);
                }
            }
        });
    }

    /** A big wet-dog shake: water flies everywhere. */
    void shakeOff(final LivingEntity pet, float pitch) {
        if (pet instanceof Wolf) {
            pet.playEffect(EntityEffect.WOLF_SHAKE);
            sound(pet, Sound.WOLF_SHAKE, pitch);
        } else {
            hop(pet, 0.15);
            sound(pet, Sound.CAT_MEOW, pitch);
        }
        for (int i = 0; i < 3; i++) {
            later(i * 8, new Runnable() {
                @Override
                public void run() {
                    if (pet.isValid()) {
                        particles(pet.getLocation(), Effect.SPLASH, 10);
                        particles(pet.getLocation(), Effect.WATERDRIP, 4);
                    }
                }
            });
        }
    }

    /** Sits and scratches behind an ear: *thump thump thump*. */
    void scratch(final LivingEntity pet, final float pitch) {
        sitFor(pet, 60);
        for (int i = 0; i < 5; i++) {
            later(8 + i * 8, new Runnable() {
                @Override
                public void run() {
                    if (pet.isValid()) {
                        sound(pet, Sound.STEP_WOOD, pitch);
                    }
                }
            });
        }
    }

    /** A tiny sneeze: a sharp breath in, then *choo* and a little puff. */
    void sneeze(final LivingEntity pet, final float pitch) {
        final boolean cat = pet instanceof Ocelot;
        sound(pet, cat ? Sound.CAT_MEOW : Sound.WOLF_WHINE, Math.min(2f, pitch + 0.5f));
        later(9, new Runnable() {
            @Override
            public void run() {
                if (!pet.isValid()) {
                    return;
                }
                sound(pet, cat ? Sound.CAT_HISS : Sound.WOLF_BARK, Math.min(2f, pitch + 0.6f));
                Location nose = pet.getEyeLocation().add(pet.getLocation().getDirection().multiply(0.6));
                for (int i = 0; i < 3; i++) {
                    pet.getWorld().playEffect(nose, Effect.CLOUD, 0);
                }
            }
        });
    }

    /** Snaps a treat out of the air. */
    void catchTreat(final Player player, final LivingEntity pet, final float pitch) {
        face(pet, player.getLocation(), 0);
        later(6, new Runnable() {
            @Override
            public void run() {
                if (pet.isValid()) {
                    hop(pet, 0.45);
                }
            }
        });
        later(12, new Runnable() {
            @Override
            public void run() {
                if (!pet.isValid()) {
                    return;
                }
                sound(pet, Sound.EAT, pitch);
                particles(pet.getLocation().add(0, 0.6, 0), Effect.HAPPY_VILLAGER, 5);
                if (player.isOnline() && pet.getUniqueId().equals(plugin.data(player).dogId)) {
                    changeNeeds(plugin.data(player), 2, 0, 2);
                }
            }
        });
    }

    /** Stands up on his back legs (as much as a 1.8 wolf can) and hugs you. */
    private void hug(final Player player, final Wolf dog) {
        dog.setSitting(false);
        if (!isNear(dog, player, 4)) {
            walkTo(dog, player.getLocation(), 1.3);
        }
        hearts(dog);
        sound(dog, Sound.WOLF_PANT, 1f);
        later(20, new Runnable() {
            @Override
            public void run() {
                if (dog.isValid() && player.isOnline()) {
                    face(dog, player.getLocation(), 0);
                    hop(dog, 0.3);
                }
            }
        });
        later(30, new Runnable() {
            @Override
            public void run() {
                if (dog.isValid()) {
                    hearts(dog);
                }
            }
        });
    }

    /** "Back up!": a few careful steps backwards, still looking at you. Beep. Beep. Beep. */
    void backUp(final Player player, final LivingEntity pet) {
        if (busy.contains(pet.getUniqueId())) {
            return;
        }
        setSitting(pet, false);
        busy.add(pet.getUniqueId());
        final Location from = player.getLocation();
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!pet.isValid() || ++ticks > 24 || !pet.getWorld().equals(from.getWorld())) {
                    done();
                    return;
                }
                Location at = pet.getLocation();
                double dx = at.getX() - from.getX();
                double dz = at.getZ() - from.getZ();
                double d = Math.hypot(dx, dz);
                if (d < 0.1) { // right on top of you: straight back from where he's facing
                    Vector facing = at.getDirection();
                    dx = -facing.getX();
                    dz = -facing.getZ();
                    d = Math.max(0.01, Math.hypot(dx, dz));
                }
                Location next = at.clone().add(dx / d * 0.12, 0, dz / d * 0.12);
                if (next.getBlock().getType().isSolid() || next.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                    done(); // bumped into something
                    return;
                }
                next.setYaw((float) Math.toDegrees(Math.atan2(dx, -dz))); // still facing you
                next.setPitch(0);
                pet.teleport(next);
                if (ticks % 8 == 1) {
                    pet.getWorld().playSound(pet.getLocation(), Sound.NOTE_PLING, 0.6f, 2f);
                }
            }

            private void done() {
                cancel();
                busy.remove(pet.getUniqueId());
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Little puffs and snores while he sleeps, until he gets up (or a minute passes). */
    void snore(final LivingEntity pet) {
        new BukkitRunnable() {
            private int n;

            @Override
            public void run() {
                if (!pet.isValid() || !isSitting(pet) || n >= 12) {
                    cancel();
                    return;
                }
                pet.getWorld().playEffect(pet.getEyeLocation().add(0, 0.4, 0), Effect.CLOUD, 0);
                if (n % 3 == 1) {
                    sound(pet, pet instanceof Ocelot ? Sound.CAT_PURR : Sound.WOLF_PANT, 0.5f);
                }
                n++;
            }
        }.runTaskTimer(plugin, 40L, 100L);
    }

    /** "What's 2 plus 3?" He barks the answer. He's very smart. (count -1 = too hard) */
    void doMath(final Player player, final Wolf dog, final int n, final boolean isPup) {
        final float pitch = isPup ? 1.8f : 1f;
        if (n < 0) {
            sound(dog, Sound.WOLF_WHINE, pitch);
            face(dog, player.getLocation(), 0);
            tell(player, asPup(pick(Text.MATH_HARD), isPup));
            return;
        }
        if (n == 0) {
            face(dog, player.getLocation(), 0);
            tell(player, asPup(Text.MATH_ZERO, isPup));
            return;
        }
        sitFor(dog, 20 + n * 10); // sits up nice and tall to concentrate
        for (int i = 0; i < n; i++) {
            later(8 + i * 10, new Runnable() {
                @Override
                public void run() {
                    if (dog.isValid()) {
                        sound(dog, Sound.WOLF_BARK, pitch);
                    }
                }
            });
        }
        later(14 + n * 10, new Runnable() {
            @Override
            public void run() {
                if (!dog.isValid() || !player.isOnline()) {
                    return;
                }
                particles(dog.getLocation().add(0, 0.6, 0), Effect.HAPPY_VILLAGER, 6);
                String reply = pick(Text.MATH_DONE).replace("{n}", String.valueOf(n)).replace("{times}", n == 1 ? "time" : "times");
                if (earn(player, "talk", 2, 30)) {
                    reply += "  " + friendshipBar(plugin.data(player));
                }
                tell(player, asPup(reply, isPup));
                // The puppy has a go too. He's still learning to count.
                final Wolf pup = findPup(player);
                if (!isPup && isNear(pup, player, 24) && random.nextBoolean()) {
                    int g = n + (random.nextBoolean() ? 1 : -1);
                    final int guess = g == 0 ? 2 : g;
                    for (int i = 0; i < guess; i++) {
                        later(10 + i * 8, new Runnable() {
                            @Override
                            public void run() {
                                if (pup.isValid()) {
                                    sound(pup, Sound.WOLF_BARK, 1.8f);
                                }
                            }
                        });
                    }
                    later(14 + guess * 8, new Runnable() {
                        @Override
                        public void run() {
                            if (player.isOnline()) {
                                tell(player, Text.PUP_MATH.replace("{n}", String.valueOf(guess)));
                            }
                        }
                    });
                }
            }
        });
    }

    private static String asPup(String text, boolean isPup) {
        return isPup ? text.replace("§bJerry", "§aPip").replace("Jerry", "Pip") : text;
    }

    // ------------------------------------------------------------------ effects

    /** Hearts over any pet (wolves have their own heart effect; for cats we draw them). */
    void hearts(Entity pet) {
        if (pet instanceof Wolf) {
            ((Wolf) pet).playEffect(EntityEffect.WOLF_HEARTS);
        } else {
            particles(pet.getLocation(), Effect.HEART, 4);
        }
    }

    void particles(Location at, Effect effect, int count) {
        for (int i = 0; i < count; i++) {
            Location spot = at.clone().add(random.nextDouble() - 0.5, 0.4 + random.nextDouble() * 0.6, random.nextDouble() - 0.5);
            at.getWorld().playEffect(spot, effect, 0);
        }
    }

    /** "He heard you": a note and the speaker over his head for a couple of seconds. */
    void heardYou(Wolf dog) {
        dog.getWorld().playEffect(dog.getLocation().add(0, 1.3, 0), Effect.NOTE, 0);
        holograms.speaker(dog, 40);
    }

    void sound(Entity at, Sound sound, float pitch) {
        at.getWorld().playSound(at.getLocation(), sound, 1f, pitch);
    }

    void hop(Entity pet, double strength) {
        pet.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.3, strength, (random.nextDouble() - 0.5) * 0.3));
    }

    /** Turns him around on the spot (turns full circles). */
    void spin(Entity pet, int turns) {
        spin(pet, turns, 30);
    }

    /** step is degrees per move: positive turns right, negative turns left. */
    void spin(final Entity pet, final int turns, final float step) {
        new BukkitRunnable() {
            private int moves;

            @Override
            public void run() {
                if (!pet.isValid() || moves++ >= turns * 12) {
                    cancel();
                    return;
                }
                Location l = pet.getLocation();
                l.setYaw(l.getYaw() + step);
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
