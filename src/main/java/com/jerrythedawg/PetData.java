package com.jerrythedawg;

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
}
