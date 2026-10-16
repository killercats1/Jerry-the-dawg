package com.jerrythedawg;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everything saved about one player's pets (stored in plugins/JerryTheDawg/pets.yml). */
final class PetData {
    static final String DEFAULT_DOG_NAME = "Jerry";
    static final String DEFAULT_CAT_NAME = "Mochi";

    UUID dogId;
    UUID catId;
    String dogName = DEFAULT_DOG_NAME;
    String catName = DEFAULT_CAT_NAME;
    int friendship;
    boolean started;
    boolean hasCat;
    boolean quiet;

    // Needs, 0-100. They slowly go down; treats, naps and play bring them back.
    int hunger = 80;
    int energy = 80;
    int fun = 80;

    /** Good tries per trick (5 = mastered). */
    final Map<String, Integer> skills = new HashMap<String, Integer>();
}
