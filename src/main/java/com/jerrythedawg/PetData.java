package com.jerrythedawg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.DyeColor;

/** Everything saved about one player's pets (stored in plugins/JerryTheDawg/pets.yml). */
final class PetData {
    static final String DEFAULT_DOG_NAME = "Jerry";
    static final String DEFAULT_CAT_NAME = "Mochi";
    static final String DEFAULT_PUP_NAME = "Pip";

    UUID dogId;
    UUID catId;
    UUID pupId;
    String dogName = DEFAULT_DOG_NAME;
    String catName = DEFAULT_CAT_NAME;
    String pupName = DEFAULT_PUP_NAME;
    int friendship;
    boolean started;
    boolean hasCat;
    boolean hasPup;
    boolean quiet;
    boolean gotFurniture;

    /** Collar colours (dye him to change it; it's kept when he's whistled back or respawned). */
    DyeColor collar = DyeColor.RED;
    DyeColor pupCollar = DyeColor.LIGHT_BLUE;

    // Needs, 0-100. They slowly go down; treats, naps and play bring them back.
    int hunger = 80;
    int energy = 80;
    int fun = 80;

    /** Good tries per trick (5 = mastered). Mochi's tricks are saved as "cat:roll" and so on. */
    final Map<String, Integer> skills = new HashMap<String, Integer>();

    /** Where your Dog Beds and Food Bowls are ("world,x,y,z"). */
    final List<String> beds = new ArrayList<String>();
    final List<String> bowls = new ArrayList<String>();
}
