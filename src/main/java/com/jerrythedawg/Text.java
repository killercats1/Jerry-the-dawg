package com.jerrythedawg;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All the plugin's text lives here so it is easy to tweak without touching the logic.
 * "Jerry" and "Mochi" are swapped for whatever you named your dog and cat before anything is shown.
 * "§" codes are Minecraft colours: §b aqua, §6 gold, §e yellow, §7 grey, §f white, §a green, §d pink.
 */
final class Text {
    private Text() {
    }

    static final String PREFIX = "§6[Jerry the Dawg]§r ";

    static final String[] INTRO = {
        "§6[Jerry the Dawg]§f Meet §bJerry§f, the goodest boy in the whole Overworld!",
        "§7Want a different name? Type §f/dog name <name>§7 (or use a name tag on him).",
        "§7Talk to him in chat! Say his name and a trick: §f\"Jerry, sit\"§7, §f\"Jerry, roll over\"§7... (§f/dog tricks§7 lists them all)",
        "§7Right-click him with §fDog Treats§7, the §fPet Brush§7 or the §fTennis Ball§7. Lost him? Use the §fDog Whistle§7.",
    };
    static final String WELCOME_BACK = "§6[Jerry the Dawg]§7 Jerry missed you! *tail wag*";

    static final String[] LEVELS = {"New Pal", "Buddy", "Best Friend", "Inseparable"};
    static final String[] LEVEL_UP = {
        "",
        "§bJerry§7 now follows you extra closely. You're officially §fBuddies§7!",
        "§bJerry§7 thinks you're the best. Sometimes he'll bring you little §fgifts§7 now!",
        "§bJerry§7 and you are §fInseparable§7. This is the best friendship in the whole world.",
    };

    static final String[] MOMENTS = {
        "§bJerry§7 barks proudly at absolutely nothing.",
        "§bJerry§7 is panting happily. Good boy.",
        "§bJerry§7 wants belly rubs. Obviously.",
        "§bJerry§7 sniffs a flower. He approves of this flower.",
        "§bJerry§7 looks at you like you're his favourite person. You are.",
    };
    static final String[] CAT_MOMENTS = {
        "§dMochi§7 curls up next to §bJerry§7. Adorable.",
        "§dMochi§7 and §bJerry§7 are having a staring contest. Jerry blinked first.",
    };
    static final String[] CAT_ARRIVES = {
        "§7A fluffy cat wandered over to say hi to §bJerry§7...",
        "§7They're friends already! Meet §dMochi§7, your new cat. The whistle calls Mochi too.",
    };

    static final String[] TREAT = {
        "§bJerry§7 gobbles up the treat. Best day ever!",
        "§bJerry§7 sits nicely for the treat. Such manners!",
    };
    static final String[] BRUSH = {"§bJerry§7 is so fluffy now!", "§bJerry§7 leans into the brush. His back leg is going wild."};
    static final String[] PLAY = {"§bJerry§7 does zoomies! Look at him go!", "§bJerry§7 chases the ball in happy circles."};
    static final String[] PET = {"§bJerry§7 leans into the pets. Best. Feeling. Ever.", "§bJerry§7 does a happy little wiggle."};
    static final String TREAT_CAT = "§dMochi§7 nibbles the treat politely. Purrrr.";
    static final String BRUSH_CAT = "§dMochi§7 is purring so loud you can feel it.";
    static final String USE_ON_DOG = "§7Right-click §bJerry§7 with it.";
    static final String FULL = " §8(He's had plenty for now!)";
    static final String TOO_FAR = "§7Jerry's too far away. Try the §fDog Whistle§7!";
    static final String CANT_HEAR = "§7Jerry's too far away to hear you. Try the whistle!";
    static final String CAME_RUNNING = "§bJerry§7 came running!";
    static final String BOTH_CAME = "§bJerry§7 and §dMochi§7 came running!";
    static final String DOG_RENAMED = "§aYour dog is now called §bJerry§a! He loves it.";
    static final String CAT_RENAMED = "§aYour cat is now called §dMochi§a! Purrr.";
    static final String NO_CAT = "§7You don't have a cat yet. Keep being friends with §bJerry§7!";
    static final String QUIET_ON = "§7Jerry will keep his thoughts to himself (no random messages). §f/dog quiet§7 again to undo.";
    static final String QUIET_OFF = "§7Jerry will tell you what he's up to again.";

    static final String[] UNKNOWN = {
        "§bJerry§7 tilts his head. He doesn't know that one yet... but he loves hearing your voice.",
        "§bJerry§7 listens very carefully, then wags his tail anyway.",
    };
    static final String[] MOCHI_HI = {"§dMochi§f: Mrrp! §7(That means hi.)", "§dMochi§7 meows back at you and slow-blinks."};

    static final String[] HELP = {
        "§6[Jerry the Dawg]§f Commands:",
        "§f/dog tricks§7 - every trick he knows",
        "§f/dog <trick>§7 - e.g. §f/dog roll over§7 (or just say it in chat with his name)",
        "§f/dog name <name>§7 - rename your dog §8|§f /dog catname <name>§7 - rename your cat",
        "§f/dog call§7 - call your pets §8|§f /dog status§7 - mood, needs, friendship (or sneak + use the whistle)",
        "§f/dog items§7 - get the pet items again §8|§f /dog quiet§7 - no random messages",
    };

    /** One voice/chat command: the words that trigger it, what he does, and what he says. */
    static final class Voice {
        final String action;
        final boolean praise;
        final List<String> words;
        final List<String> replies;

        Voice(String action, boolean praise, String[] words, String... replies) {
            this.action = action;
            this.praise = praise;
            this.words = Arrays.asList(words);
            this.replies = Arrays.asList(replies);
        }
    }

    private static String[] w(String... words) {
        return words;
    }

    // The first entry whose words appear in what you said wins, so longer phrases go first.
    static final List<Voice> VOICE = Collections.unmodifiableList(Arrays.asList(
        new Voice("love", true, w("good boy", "good dog", "love you", "best boy", "good job", "well done", "cute"),
            "§bJerry§7's tail is wagging so fast it's basically a propeller.", "§bJerry§7 knows. He's very aware. *happy wiggle*"),
        new Voice("dead", false, w("play dead", "bang", "you're dead", "youre dead"),
            "§bJerry§7 flops down dramatically. Award-winning performance.", "§bJerry§7 plays dead... but his tail is still wagging."),
        new Voice("roll", false, w("roll over", "roll", "belly rub", "belly rubs", "belly"),
            "§bJerry§7 rolls around in the grass! Belly rubs, please.", "§bJerry§7 wiggles on his back with his paws in the air."),
        new Voice("beg", false, w("sit pretty", "beg", "pretty please"),
            "§bJerry§7 sits up and begs with his best puppy eyes.", "§bJerry§7 sits pretty. How could you say no?"),
        new Voice("sit", false, w("sit down", "sit", "stay", "wait", "stop", "freeze"),
            "§bJerry§7 sits down and waits patiently. Say §f\"come\"§7 to call him.", "§bJerry§7 sits like a very good boy."),
        new Voice("lie", false, w("lie down", "lay down", "down", "lie", "lay"),
            "§bJerry§7 lies down and gets comfy. Say §f\"come\"§7 to call him.", "§bJerry§7 flops down on his belly. Comfy."),
        new Voice("shakeoff", false, w("shake off", "dry off", "shake it off", "get dry"),
            "§bJerry§7 shakes himself off. You are now slightly damp.", "§bJerry§7 does a big full-body shake!"),
        new Voice("highfive", false, w("high five", "high fives", "hi five", "high"),
            "§bJerry§7 gives you a high five! *boop*", "§bJerry§7 slaps your hand with his paw. Nailed it."),
        new Voice("paw", false, w("shake", "paw", "give me your paw", "gimme paw", "shake hands"),
            "§bJerry§7 offers you his paw. What a gentleman.", "§bJerry§7 shakes your hand very politely."),
        new Voice("wave", false, w("wave", "say bye", "bye", "goodbye", "bye bye", "say hi"),
            "§bJerry§7 waves his paw at you!", "§bJerry§7 waves bye-bye. (He's not going anywhere.)"),
        new Voice("bow", false, w("bow", "take a bow", "stretch"),
            "§bJerry§7 takes a bow. Thank you, thank you.", "§bJerry§7 does a big play bow. He wants to play!"),
        new Voice("dance", false, w("dance", "boogie", "party", "dance party"),
            "§bJerry§7 hops and spins to the music. DANCE PARTY!", "§bJerry§7 has moves. Serious moves."),
        new Voice("backflip", false, w("backflip", "back flip", "flip", "do a flip"),
            "§bJerry§7 does a BACKFLIP! How?!", "§bJerry§7 flips and sticks the landing. 10/10."),
        new Voice("howl", false, w("howl", "sing", "awoo", "awooo", "wolf"),
            "§bJerry§f: Awoooooo! §7(He's a wolf at heart.)", "§bJerry§7 howls a little song for you."),
        new Voice("chase", false, w("chase your tail", "chase his tail", "chase", "tail"),
            "§bJerry§7 chases his tail around and around and around...", "§bJerry§7 almost caught his tail! Almost."),
        new Voice("come", false, w("come", "here", "let's go", "lets go", "follow", "okay", "release", "free", "stand"),
            "§bJerry§7 comes trotting over!", "§bJerry§7 zooms right to you."),
        new Voice("bark", false, w("speak", "bark", "woof", "talk", "say something"),
            "§bJerry§f: WOOF! WOOF!", "§bJerry§f: Woof! §7(He said it with feeling.)"),
        new Voice("spin", false, w("spin", "twirl", "trick", "turn around"),
            "§bJerry§7 does a spin! Ta-da!", "§bJerry§7 shows off his best spin."),
        new Voice("hop", false, w("jump", "hop", "up"),
            "§bJerry§7 jumps! Boing!", "§bJerry§7 hops like a bunny. A big fluffy bunny."),
        new Voice("bone", false, w("bone", "your bone", "get your bone"),
            "§bJerry§7 proudly shows off his bone.", "§bJerry§7 found his favourite bone. He will not be sharing."),
        new Voice("fetch", false, w("fetch", "ball", "go get it", "get the ball")),
        new Voice("play", false, w("play", "zoomies")),
        new Voice("shock", false, w("boo", "surprise", "guess what", "oh no", "whoa", "wow"),
            "§bJerry§7 jumps! His eyes go HUGE.", "§bJerry§7 is shocked. Absolutely shocked."),
        new Voice("excited", false, w("treat", "hungry", "food", "snack", "dinner", "eat"),
            "§bJerry§7's ears perk up at the word \"treat\". Better give him one!", "§bJerry§7 is drooling a tiny bit."),
        new Voice("yawn", false, w("sleep", "tired", "night", "nap", "bed"),
            "§bJerry§7 does a big sleepy yawn.", "§bJerry§7 curls up for a little snooze."),
        new Voice("mochi", false, w("mochi", "kitty", "cat")),
        new Voice("greet", false, w("hello", "hi", "hey", "morning", "jerry"),
            "§bJerry§7 barks hello and wags his whole body.", "§bJerry§7 barks hello right back!")
    ));

    static final String[] TRICK_LIST = {
        "§6[Jerry the Dawg]§f Tricks §7(say them in chat with his name, or §f/dog <trick>§7):",
        "§fsit, lie down, come, shake, high five, wave, beg, roll over, play dead, bow,",
        "§fdance, backflip, howl, chase your tail, spin, jump, speak, shake off, fetch",
        "§7New tricks are sloppy at first. Practice them and he'll master them!",
        "§7Chain them: §f\"Jerry, sit then shake then roll over\"",
    };

    // Words chat autocorrect/typos often get wrong, and what they should have been.
    static final Map<String, String> MISHEARD;

    static {
        Map<String, String> m = new HashMap<String, String>();
        String[][] pairs = {
            {"role", "roll"}, {"rolled", "roll"}, {"rolling", "roll"}, {"rollover", "roll over"}, {"rolls", "roll"},
            {"set", "sit"}, {"sat", "sit"}, {"seat", "sit"}, {"sitting", "sit"}, {"sits", "sit"},
            {"gerry", "jerry"}, {"jeri", "jerry"}, {"jerri", "jerry"}, {"jerrys", "jerry"}, {"jerry's", "jerry"},
            {"spinning", "spin"}, {"jumping", "jump"}, {"barking", "bark"}, {"sleeping", "sleep"},
            {"mocha", "mochi"}, {"moshi", "mochi"},
        };
        for (String[] pair : pairs) {
            m.put(pair[0], pair[1]);
        }
        MISHEARD = Collections.unmodifiableMap(m);
    }

    // Distinctive words that still count when one letter is off, like "speek" or "fetchh".
    static final List<String> FUZZY_WORDS = Arrays.asList(
        "speak", "fetch", "mochi", "jerry", "belly", "zoomies", "twirl", "dance", "hello", "backflip");

    // ------------------------------------------------------------------ needs & moods

    static final Map<String, String> MOOD_LABEL;

    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("happy", "§aHappy");
        m.put("hungry", "§6Hungry §7(give him a treat)");
        m.put("tired", "§9Sleepy §7(let him sit or lie down for a nap)");
        m.put("bored", "§eBored §7(play fetch with him)");
        MOOD_LABEL = Collections.unmodifiableMap(m);
    }

    static final String[] HUNGRY = {
        "§bJerry§7's tummy rumbles. Treat, please?",
        "§bJerry§7 stares at your pocket. He knows the treats are in there.",
    };
    static final String[] TIRED = {"§bJerry§7 does a big yawn. Someone needs a nap."};
    static final String NAP = "§bJerry§7 lies down for a nap. (Say §f\"come\"§7 to wake him.)";
    static final String[] BORED = {
        "§bJerry§7 bounces around you. He's SO bored. Throw the ball!",
        "§bJerry§7 nudges your hand. Play fetch?",
    };
    static final String TOO_SLEEPY = "§bJerry§7 is too sleepy for tricks... *yawn* He lies down instead.";

    // ------------------------------------------------------------------ practice makes perfect

    static final Map<String, String> TRICK_NAMES;

    static {
        Map<String, String> m = new java.util.LinkedHashMap<String, String>();
        String[][] names = {
            {"paw", "shake"}, {"highfive", "high five"}, {"wave", "wave"}, {"beg", "beg"}, {"roll", "roll over"},
            {"dead", "play dead"}, {"bow", "bow"}, {"dance", "dance"}, {"backflip", "backflip"}, {"howl", "howl"},
            {"chase", "chase his tail"}, {"spin", "spin"}, {"lie", "lie down"}, {"hop", "jump"}, {"shakeoff", "shake off"},
        };
        for (String[] pair : names) {
            m.put(pair[0], pair[1]);
        }
        TRICK_NAMES = Collections.unmodifiableMap(m);
    }

    static final String[] OOPS = {
        "§bJerry§7 tried to §f{trick}§7... and did a spin instead. Close enough!",
        "§bJerry§7 tried to §f{trick}§7, got confused and tilted his head. He's still learning!",
        "§bJerry§7 tried to §f{trick}§7... and just sat down. Practice makes perfect!",
        "§bJerry§7 got so excited about §f{trick}§7 that he just jumped around.",
    };
    static final String MASTERED = "§e* * * §bJerry§e mastered §f{trick}§e! * * *";

    // ------------------------------------------------------------------ fetch & reactions

    static final String FETCH_THROW = "§7You throw the ball! §bJerry§7 takes off after it...";
    static final String FETCH_GOT_IT = "§bJerry§7 got it!";
    static final String[] FETCH_DONE = {
        "§bJerry§7 drops the ball at your feet. Again! Again!",
        "§bJerry§7 brings the ball back, tail wagging like crazy.",
    };
    static final String FETCH_BUSY = "§7Jerry's already chasing the ball!";
    static final String FETCH_LOST = "§7The ball's gone... §bJerry§7 looks around, confused.";
    static final String GUARD = "§bJerry§c growls! §7There's a §f{mob}§7 nearby, watch out!";
    static final String DEFEND = "§bJerry§c jumps in to protect you from the §f{mob}§c!";
    static final String WORRIED = "§bJerry§7 rushes over to check on you. You okay?";
    static final String BEG_FOOD = "§bJerry§7 watches you eat with HUGE puppy eyes...";
    static final String WELCOME_HOME = "§bJerry§7 is SO happy you're back! Zoomies!";
    static final String BEDTIME = "§bJerry§7 curls up at the foot of your bed. Goodnight!";

    static final String[] GIFT_NAMES = {"a stick. The best stick", "a bone", "a poppy", "a dandelion", "a feather", "an emerald?! Where did he find that"};
}
