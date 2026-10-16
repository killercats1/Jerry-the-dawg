package com.jerrythedawg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

/** Understands what you say to your dog (in chat or with /dog) and makes him react. */
final class Speech {
    private static final int MAX_COMMANDS = 4;

    private final JerryPlugin plugin;
    private final Pets pets;
    private final Random random = new Random();

    Speech(JerryPlugin plugin, Pets pets) {
        this.plugin = plugin;
        this.pets = pets;
    }

    /** A chat message: only react to things meant for him (his name, or a short command on its own). */
    void heardChat(Player player, String message) {
        PetData data = plugin.data(player);
        if (!data.started) {
            return;
        }
        String words = normalizeNames(cleanWords(simplify(message)), data);
        boolean saidName = (" " + words + " ").contains(" jerry ");
        boolean shortCommand = words.split(" ").length <= 3 && !parse(message, data).isEmpty();
        if (saidName || shortCommand) {
            hear(player, message);
        }
    }

    /** Reacts to whatever you said (chat or /dog). */
    void hear(final Player player, String said) {
        PetData data = plugin.data(player);
        final List<Text.Voice> commands = parse(said, data);
        final Wolf dog = pets.findDog(player);
        boolean calledHim = false;
        for (Text.Voice v : commands) {
            calledHim |= "come".equals(v.action);
        }
        // "come" works from anywhere, like the whistle; everything else needs him in earshot.
        if (dog == null || (!calledHim && !pets.isNear(dog, player, 24))) {
            pets.tell(player, Text.CANT_HEAR);
            return;
        }
        pets.heardYou(dog);
        if (commands.isEmpty()) {
            pets.sound(dog, org.bukkit.Sound.WOLF_WHINE, 1f);
            pets.tell(player, pets.pick(Text.UNKNOWN));
            return;
        }
        long at = 6;
        for (final Text.Voice entry : commands) {
            pets.later(at, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && dog.isValid()) {
                        respond(player, dog, entry);
                    }
                }
            });
            at += ticksFor(entry.action);
        }
    }

    private void respond(Player player, Wolf dog, Text.Voice entry) {
        if ("play".equals(entry.action) || "fetch".equals(entry.action)) {
            pets.trick(player, dog, entry.action); // these say their own thing
            return;
        }
        if (Pets.PRACTICE.contains(entry.action)) {
            PetData data = plugin.data(player);
            pets.changeNeeds(data, 3, -1, 0);
            // Too sleepy? Sometimes he'd rather nap.
            if (data.energy < 15 && random.nextBoolean()) {
                dog.setSitting(true);
                pets.tell(player, Text.TOO_SLEEPY);
                return;
            }
            if (!pets.attemptTrick(player, dog, entry.action)) {
                return;
            }
        }
        pets.trick(player, dog, entry.action);
        String reply;
        if ("mochi".equals(entry.action)) {
            reply = plugin.data(player).hasCat ? pets.pick(Text.MOCHI_HI) : Text.NO_CAT;
        } else {
            reply = entry.replies.get(random.nextInt(entry.replies.size()));
        }
        if (entry.praise && pets.earn(player, "talk", 2, 30)) {
            reply += "  " + pets.friendshipBar(plugin.data(player));
        }
        pets.tell(player, reply);
    }

    private static long ticksFor(String action) {
        if ("dance".equals(action) || "chase".equals(action)) {
            return 70;
        }
        if ("roll".equals(action) || "play".equals(action) || "dead".equals(action)) {
            return 50;
        }
        return 30;
    }

    // ------------------------------------------------------------------ understanding words

    private static String simplify(String text) {
        return text.toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("[^a-z' ]+", " ").replaceAll("\\s+", " ").trim();
    }

    /** "sit, then roll over and speak" -> [sit, roll, bark] */
    List<Text.Voice> parse(String said, PetData data) {
        String marked = said.toLowerCase(Locale.ROOT).replace('-', ' ')
            .replaceAll("[,.!?;:]+", " | ")
            .replaceAll("\\b(and then|then|after that|and)\\b", " | ");
        List<Text.Voice> out = new ArrayList<Text.Voice>();
        for (String part : marked.split("\\|")) {
            String words = normalizeNames(cleanWords(simplify(part)), data);
            if (words.isEmpty()) {
                continue;
            }
            Text.Voice entry = match(words);
            if (entry != null && (out.isEmpty() || out.get(out.size() - 1) != entry)) {
                out.add(entry);
            }
            if (out.size() >= MAX_COMMANDS) {
                break;
            }
        }
        return out;
    }

    private static Text.Voice match(String words) {
        String padded = " " + words + " ";
        for (Text.Voice entry : Text.VOICE) {
            for (String word : entry.words) {
                if (padded.contains(" " + word + " ")) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** Fixes common typos and lets a few distinctive words match when they're one letter off. */
    private static String cleanWords(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String fixed = Text.MISHEARD.get(word);
            if (fixed == null) {
                fixed = word;
                for (String target : Text.FUZZY_WORDS) {
                    if (levenshtein(word, target) <= 1) {
                        fixed = target;
                        break;
                    }
                }
            }
            out.append(out.length() == 0 ? "" : " ").append(fixed);
        }
        return out.toString();
    }

    /** Your dog's and cat's own names (or something one letter off) count as "jerry" / "mochi". */
    private static String normalizeNames(String text, PetData data) {
        String out = " " + text + " ";
        String[][] names = {{simplify(data.dogName), "jerry"}, {simplify(data.catName), "mochi"}};
        for (String[] pair : names) {
            String name = pair[0];
            String token = pair[1];
            if (name.length() < 2 || name.equals(token)) {
                continue;
            }
            out = out.replace(" " + name + " ", " " + token + " ");
            if (!name.contains(" ") && name.length() >= 4) {
                StringBuilder sb = new StringBuilder(" ");
                for (String word : out.trim().split(" ")) {
                    sb.append(!word.isEmpty() && levenshtein(word, name) <= 1 ? token : word).append(' ');
                }
                out = sb.toString();
            }
        }
        return out.trim().replaceAll("\\s+", " ");
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] row = new int[b.length() + 1];
            row[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                row[j] = Math.min(Math.min(prev[j] + 1, row[j - 1] + 1), prev[j - 1] + cost);
            }
            prev = row;
        }
        return prev[b.length()];
    }
}
