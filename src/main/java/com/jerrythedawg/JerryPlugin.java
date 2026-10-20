package com.jerrythedawg;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Jerry the Dawg for Java Edition 1.8.8: the "lesser version" of the Bedrock add-on.
 * Every player gets a tamed wolf they can name, teach tricks (by chat or /dog), feed, brush and
 * play with. Friendship unlocks Mochi the cat, gifts and a puppy. 1.8.8 can't do custom models, so tricks
 * are shown with vanilla wolf moves, sounds and particles instead of new animations.
 */
public final class JerryPlugin extends JavaPlugin {
    private final Map<UUID, PetData> players = new HashMap<UUID, PetData>();
    private File dataFile;
    private boolean dirty;
    private Pets pets;
    private Speech speech;

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "pets.yml");
        load();
        pets = new Pets(this);
        speech = new Speech(this, pets);
        getServer().getPluginManager().registerEvents(new PetListener(pets, speech), this);
        DogCommand command = new DogCommand(this, pets, speech);
        getCommand("dog").setExecutor(command);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                pets.tickAll();
            }
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    save();
                }
            }
        }, 20L * 60, 20L * 60);
        // Players already online (e.g. after /reload) get their intro or welcome too.
        for (Player player : Bukkit.getOnlinePlayers()) {
            pets.onJoin(player);
        }
    }

    @Override
    public void onDisable() {
        if (pets != null) {
            pets.removeThrownBalls(); // a game cut off by a restart shouldn't leave a spare ball lying around
        }
        save();
    }

    PetData data(Player player) {
        PetData data = players.get(player.getUniqueId());
        if (data == null) {
            data = new PetData();
            players.put(player.getUniqueId(), data);
        }
        return data;
    }

    Map<UUID, PetData> allData() {
        return players;
    }

    void markDirty() {
        dirty = true;
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection s = yaml.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            PetData d = new PetData();
            d.dogId = uuid(s.getString("dog"));
            d.catId = uuid(s.getString("cat"));
            d.pupId = uuid(s.getString("pup"));
            d.dogName = s.getString("dog-name", PetData.DEFAULT_DOG_NAME);
            d.catName = s.getString("cat-name", PetData.DEFAULT_CAT_NAME);
            d.pupName = s.getString("pup-name", PetData.DEFAULT_PUP_NAME);
            d.friendship = s.getInt("friendship");
            d.started = s.getBoolean("started");
            d.hasCat = s.getBoolean("has-cat");
            d.hasPup = s.getBoolean("has-pup");
            d.gotFurniture = s.getBoolean("got-furniture");
            d.collar = dye(s.getString("collar"), DyeColor.RED);
            d.pupCollar = dye(s.getString("pup-collar"), DyeColor.LIGHT_BLUE);
            d.catCoat = catCoat(s.getString("cat-coat"));
            d.beds.addAll(s.getStringList("beds"));
            d.bowls.addAll(s.getStringList("bowls"));
            d.quiet = s.getBoolean("quiet");
            d.hunger = s.getInt("hunger", 80);
            d.energy = s.getInt("energy", 80);
            d.fun = s.getInt("fun", 80);
            ConfigurationSection skills = s.getConfigurationSection("skills");
            if (skills != null) {
                for (String trick : skills.getKeys(false)) {
                    d.skills.put(trick, skills.getInt(trick));
                }
            }
            UUID player = uuid(key);
            if (player != null) {
                players.put(player, d);
            }
        }
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PetData> e : players.entrySet()) {
            PetData d = e.getValue();
            String k = e.getKey().toString();
            yaml.set(k + ".dog", d.dogId == null ? null : d.dogId.toString());
            yaml.set(k + ".cat", d.catId == null ? null : d.catId.toString());
            yaml.set(k + ".pup", d.pupId == null ? null : d.pupId.toString());
            yaml.set(k + ".dog-name", d.dogName);
            yaml.set(k + ".cat-name", d.catName);
            yaml.set(k + ".pup-name", d.pupName);
            yaml.set(k + ".friendship", d.friendship);
            yaml.set(k + ".started", d.started);
            yaml.set(k + ".has-cat", d.hasCat);
            yaml.set(k + ".has-pup", d.hasPup);
            yaml.set(k + ".got-furniture", d.gotFurniture);
            yaml.set(k + ".collar", d.collar.name());
            yaml.set(k + ".pup-collar", d.pupCollar.name());
            yaml.set(k + ".cat-coat", d.catCoat.name());
            yaml.set(k + ".beds", d.beds);
            yaml.set(k + ".bowls", d.bowls);
            yaml.set(k + ".quiet", d.quiet);
            yaml.set(k + ".hunger", d.hunger);
            yaml.set(k + ".energy", d.energy);
            yaml.set(k + ".fun", d.fun);
            for (Map.Entry<String, Integer> skill : d.skills.entrySet()) {
                yaml.set(k + ".skills." + skill.getKey(), skill.getValue());
            }
        }
        try {
            getDataFolder().mkdirs();
            yaml.save(dataFile);
            dirty = false;
        } catch (IOException ex) {
            getLogger().warning("Couldn't save pets.yml: " + ex.getMessage());
        }
    }

    private static DyeColor dye(String text, DyeColor fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return DyeColor.valueOf(text);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static org.bukkit.entity.Ocelot.Type catCoat(String text) {
        try {
            org.bukkit.entity.Ocelot.Type type = org.bukkit.entity.Ocelot.Type.valueOf(text);
            return type == org.bukkit.entity.Ocelot.Type.WILD_OCELOT ? org.bukkit.entity.Ocelot.Type.RED_CAT : type;
        } catch (RuntimeException ex) { // missing or unknown
            return org.bukkit.entity.Ocelot.Type.RED_CAT;
        }
    }

    private static UUID uuid(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
