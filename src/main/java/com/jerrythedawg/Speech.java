package com.jerrythedawg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

/** Understands what you say to your dog (in chat or with /dog) and makes him react. */
final class Speech {
    private static final int MAX_COMMANDS = 4;

    private final JerryPlugin plugin;
    private final Pets pets;
    private final SmartJerry smart;
    private final Random random = new Random();

    Speech(JerryPlugin plugin, Pets pets, SmartJerry smart) {
        this.plugin = plugin;
        this.pets = pets;
        this.smart = smart;
        smart.setSpeech(this);
    }

    /** A chat message: only react to things meant for your pets (a name, or a short command on its own). */
    void heardChat(Player player, String message) {
        PetData data = plugin.data(player);
        if (!data.started) {
            return;
        }
        String padded = " " + normalizeNames(cleanWords(simplify(message)), data) + " ";
        String words = padded.trim();
        boolean saidName = padded.contains(" jerry ") || (data.hasCat && padded.contains(" mochi "))
            || (data.hasPup && padded.contains(" pip "));
        boolean shortCommand = words.split(" ").length <= 3 && !parse(message, data).isEmpty();
        // After "listen up" your next message counts even without his name.
        if (saidName || shortCommand || pets.isListening(player)) {
            hear(player, message);
        }
    }

    /** Reacts to whatever you said (chat or /dog). Say Mochi's or the puppy's name to talk to them instead. */
    void hear(final Player player, String said) {
        PetData data = plugin.data(player);
        String padded = " " + normalizeNames(cleanWords(simplify(said)), data) + " ";
        if (!padded.contains(" jerry ")) {
            if (data.hasCat && padded.contains(" mochi ")) {
                hearCat(player, said);
                return;
            }
            if (data.hasPup && padded.contains(" pip ")) {
                hearPup(player, said);
                return;
            }
        }
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
        // Like in the video: say his name and a red "!" pops up first, then he shows he's listening
        // and reacts.
        long start = 0;
        if (padded.contains(" jerry ")) {
            pets.alert(player, dog);
            start = Pets.ALERT_TICKS;
        }
        if (smart.wants(said, commands, padded.trim())) {
            if (!isListen(commands)) {
                pets.stopListening(player);
            }
            smart.ask(player, dog, said, start);
            return;
        }
        pets.later(Math.max(1, start), new Runnable() {
            @Override
            public void run() {
                if (dog.isValid()) {
                    pets.heardYou(dog);
                }
            }
        });
        if (commands.isEmpty()) {
            pets.later(Math.max(1, start), new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && dog.isValid()) {
                        pets.stopListening(player);
                        pets.sound(dog, org.bukkit.Sound.WOLF_WHINE, 1f);
                        pets.tell(player, pets.pick(Text.UNKNOWN));
                    }
                }
            });
            return;
        }
        long at = 6 + start;
        for (final Text.Voice entry : commands) {
            pets.later(at, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && dog.isValid()) {
                        respond(player, dog, entry);
                    }
                }
            });
            at += ticksFor(entry);
        }
    }

    private static boolean isListen(List<Text.Voice> commands) {
        for (Text.Voice v : commands) {
            if ("listen".equals(v.action)) {
                return true;
            }
        }
        return false;
    }

    private void respond(Player player, Wolf dog, Text.Voice entry) {
        respond(player, dog, entry, false);
    }

    /** fromAI: Smart Jerry already said his piece, so no canned reply (friendship still counts). */
    void respond(Player player, Wolf dog, Text.Voice entry, boolean fromAI) {
        if (!"listen".equals(entry.action)) {
            pets.stopListening(player);
        }
        if ("play".equals(entry.action) || "fetch".equals(entry.action)) {
            pets.trick(player, dog, entry.action); // these say their own thing
            return;
        }
        if ("math".equals(entry.action)) {
            pets.doMath(player, dog, entry.count, false);
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
        pets.pupCopies(player, entry.action); // the puppy wants to do it too
        if (fromAI) {
            if (entry.praise && pets.earn(player, "talk", 2, 30)) {
                pets.tell(player, "  " + pets.friendshipBar(plugin.data(player)));
            }
            return;
        }
        String reply;
        if ("mochi".equals(entry.action)) {
            reply = plugin.data(player).hasCat ? pets.pick(Text.MOCHI_HI) : Text.NO_CAT;
        } else if ("pupHi".equals(entry.action)) {
            reply = plugin.data(player).hasPup ? pets.pick(Text.PIP_HI) : Text.NO_PUP;
        } else {
            reply = entry.replies.get(random.nextInt(entry.replies.size()));
        }
        if (entry.praise && pets.earn(player, "talk", 2, 30)) {
            reply += "  " + pets.friendshipBar(plugin.data(player));
        }
        pets.tell(player, reply);
    }

    // ------------------------------------------------------------------ talking to Mochi and the puppy

    /** Commands meant for someone other than Jerry (without the "hi Mochi" / "hi Pip" bits). */
    private List<Text.Voice> commandsFor(String said, PetData data) {
        List<Text.Voice> out = new ArrayList<Text.Voice>();
        for (Text.Voice v : parse(said, data)) {
            if (!"mochi".equals(v.action) && !"pupHi".equals(v.action) && !"greet".equals(v.action)) {
                out.add(v);
            }
        }
        return out;
    }

    private void hearCat(final Player player, String said) {
        PetData data = plugin.data(player);
        final Ocelot cat = pets.findCat(player);
        final List<Text.Voice> commands = commandsFor(said, data);
        boolean called = false;
        for (Text.Voice v : commands) {
            called |= "come".equals(v.action);
        }
        if (cat == null || (!called && !pets.isNear(cat, player, 24))) {
            pets.tell(player, Text.CANT_HEAR_CAT);
            return;
        }
        if (commands.isEmpty()) {
            pets.sound(cat, org.bukkit.Sound.CAT_MEOW, 1f);
            pets.tell(player, pets.pick(Text.MOCHI_HI));
            return;
        }
        long at = 6;
        for (final Text.Voice entry : commands) {
            pets.later(at, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && cat.isValid()) {
                        respondCat(player, cat, entry);
                    }
                }
            });
            at += ticksFor(entry);
        }
    }

    private void respondCat(Player player, Ocelot cat, Text.Voice entry) {
        String action = entry.action;
        if (!Pets.CAT_TRICKS.contains(action)) {
            pets.sound(cat, org.bukkit.Sound.CAT_MEOW, 0.9f);
            pets.tell(player, Text.CAT_NOPE.replace("{trick}", entry.words.get(0)));
            return;
        }
        if (Pets.CAT_PRACTICE.contains(action) && !pets.attemptCatTrick(player, cat, action)) {
            return;
        }
        pets.catTrick(player, cat, action);
        String reply = pets.pick(Text.CAT_REPLIES.get(action));
        if (entry.praise && pets.earn(player, "talk", 2, 30)) {
            reply += "  " + pets.friendshipBar(plugin.data(player));
        }
        pets.tell(player, reply);
    }

    private void hearPup(final Player player, String said) {
        PetData data = plugin.data(player);
        final Wolf pup = pets.findPup(player);
        final List<Text.Voice> commands = commandsFor(said, data);
        boolean called = false;
        for (Text.Voice v : commands) {
            called |= "come".equals(v.action);
        }
        if (pup == null || (!called && !pets.isNear(pup, player, 24))) {
            pets.tell(player, Text.CANT_HEAR_PUP);
            return;
        }
        pets.alert(player, pup); // you always say his name to talk to him
        if (commands.isEmpty()) {
            pets.later(Pets.ALERT_TICKS, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && pup.isValid()) {
                        pets.heardYou(pup);
                        pets.sound(pup, org.bukkit.Sound.WOLF_BARK, 1.8f);
                        pets.tell(player, pets.pick(Text.PIP_HI));
                    }
                }
            });
            return;
        }
        long at = 6 + Pets.ALERT_TICKS;
        for (final Text.Voice entry : commands) {
            pets.later(at, new Runnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !pup.isValid()) {
                        return;
                    }
                    String action = entry.action;
                    if (!"listen".equals(action)) {
                        pets.stopListening(player);
                    }
                    if ("math".equals(action)) {
                        pets.doMath(player, pup, entry.count, true);
                        return;
                    }
                    if ("love".equals(action)) {
                        pets.pupTrick(player, pup, action, true);
                        pets.tell(player, Text.PUP_LOVE);
                        return;
                    }
                    // Puppies get "sit" and "come" right; anything fancier is a coin toss.
                    boolean ok = "sit".equals(action) || "come".equals(action) || "lie".equals(action) || "sleep".equals(action)
                        || "hug".equals(action) || "listen".equals(action) || random.nextInt(10) < 6;
                    pets.pupTrick(player, pup, action, ok);
                    String name = Text.TRICK_NAMES.containsKey(action) ? Text.TRICK_NAMES.get(action) : entry.words.get(0);
                    pets.tell(player, (ok ? Text.PUP_DID : Text.PUP_OOPS).replace("{trick}", name));
                }
            });
            at += ticksFor(entry);
        }
    }

    static long ticksFor(Text.Voice entry) {
        String action = entry.action;
        if ("math".equals(action)) {
            return 30 + Math.max(1, entry.count) * 10;
        }
        if ("scratch".equals(action) || "hug".equals(action)) {
            return 65;
        }
        if ("shakeoff".equals(action) || "backup".equals(action)) {
            return 35;
        }
        if ("sleep".equals(action)) {
            return 40;
        }
        if ("find".equals(action)) {
            return 140;
        }
        if ("dance".equals(action) || "chase".equals(action)) {
            return 70;
        }
        if ("shy".equals(action)) {
            return 50;
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
        String marked = said.toLowerCase(Locale.ROOT).replaceAll("(\\d)\\s*-\\s*(?=\\d)", "$1 minus ").replace('-', ' ')
            .replaceAll("[,.!?;:]+", " | ")
            .replaceAll("\\b(and then|then|after that|and)\\b", " | ");
        List<Text.Voice> out = new ArrayList<Text.Voice>();
        for (String part : marked.split("\\|")) {
            String words = normalizeNames(cleanWords(simplify(part)), data);
            Text.Voice entry = mathProblem(part);
            if (entry == null && !words.isEmpty()) {
                entry = match(words);
            }
            if (entry != null && (out.isEmpty() || out.get(out.size() - 1) != entry)) {
                out.add(entry);
            }
            if (out.size() >= MAX_COMMANDS) {
                break;
            }
        }
        // "Jerry, listen up" is one command, not a hello and then a command.
        List<Text.Voice> real = new ArrayList<Text.Voice>();
        for (Text.Voice v : out) {
            if (!"greet".equals(v.action)) {
                real.add(v);
            }
        }
        return real.isEmpty() ? out : real;
    }

    // "What's 2 plus 3?" -> he barks 5 times. Also "count to 4" and "how many legs do you have".
    private static final String[] NUMBER_WORDS = {
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
    private static final String NUM = "(\\d+|" + String.join("|", NUMBER_WORDS) + ")";
    private static final String NUM_AFTER = "(\\d+|" + String.join("|", NUMBER_WORDS) + "|to|too|for)"; // "two plus to" is a common mishearing
    private static final String[] SUMS = {"plus|\\+|add", "minus|take away|takeaway", "times|x|\\*|\u00d7|multiplied by", "divided by|over|/|\u00f7"};
    private static final Pattern[] SUM_PATTERNS = new Pattern[SUMS.length];
    private static final Pattern COUNT = Pattern.compile("\\bcount (?:up )?to " + NUM + "\\b");
    private static final Pattern LEGS = Pattern.compile("\\bhow many legs\\b");

    static {
        for (int i = 0; i < SUMS.length; i++) {
            SUM_PATTERNS[i] = Pattern.compile("\\b" + NUM + "\\s*(?:" + SUMS[i] + ")\\s*" + NUM_AFTER + "\\b");
        }
    }

    private static int toNumber(String word) {
        if (word.matches("\\d+")) {
            return word.length() > 6 ? Integer.MAX_VALUE : Integer.parseInt(word);
        }
        if ("to".equals(word) || "too".equals(word)) {
            return 2;
        }
        if ("for".equals(word)) {
            return 4;
        }
        return Arrays.asList(NUMBER_WORDS).indexOf(word);
    }

    /** A math problem he can bark the answer to, or null if this part of what you said isn't one. */
    static Text.Voice mathProblem(String text) {
        String t = " " + text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + " ";
        Double answer = null;
        for (int i = 0; i < SUM_PATTERNS.length && answer == null; i++) {
            Matcher m = SUM_PATTERNS[i].matcher(t);
            if (m.find()) {
                double a = toNumber(m.group(1));
                double b = toNumber(m.group(2));
                answer = i == 0 ? a + b : i == 1 ? a - b : i == 2 ? a * b : (b == 0 ? Double.NaN : a / b);
            }
        }
        if (answer == null) {
            Matcher m = COUNT.matcher(t);
            if (m.find()) {
                answer = (double) toNumber(m.group(1));
            } else if (LEGS.matcher(t).find()) {
                answer = 4.0;
            }
        }
        if (answer == null) {
            return null;
        }
        double n = answer;
        boolean ok = !Double.isNaN(n) && n == Math.rint(n) && n >= 0 && n <= 10;
        return new Text.Voice(ok ? (int) n : -1);
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

    /** Your pets' own names (or something one letter off) count as "jerry" / "mochi" / "pip". */
    private static String normalizeNames(String text, PetData data) {
        String out = " " + text + " ";
        String[][] names = {{simplify(data.dogName), "jerry"}, {simplify(data.catName), "mochi"}, {simplify(data.pupName), "pip"}};
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
