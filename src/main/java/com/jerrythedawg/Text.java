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
        "§bJerry§7 and you are §fInseparable§7. This is the best friendship in the whole world. (And someone small is on the way...)",
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
    static final String[] PUP_MOMENTS = {
        "§aPip§7 pounces on §bJerry§7's tail. Jerry lets him win.",
        "§aPip§7 trips over his own paws. He's fine! He's great!",
        "§aPip§7 follows §bJerry§7 everywhere, copying his every step.",
    };
    static final String[] PUP_ARRIVES = {
        "§7A tiny puppy came bouncing over to §bJerry§7...",
        "§7Jerry's taking him under his paw! Meet §aPip§7, your new puppy. Rename him with §f/dog pupname <name>§7.",
        "§7Pip copies Jerry's tricks (not always correctly). Talk to him by name: §f\"Pip, sit\"§7.",
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
    static final String TREAT_PUP = "§aPip§7 crunches the treat with his tiny teeth. Crumbs everywhere.";
    static final String BRUSH_PUP = "§aPip§7 wiggles so much the brushing takes twice as long.";
    static final String[] PET_PUP = {"§aPip§7 rolls over for tummy rubs immediately.", "§aPip§7 licks your hand. And your sleeve. And your other hand."};
    static final String[] PET_CAT = {"§dMochi§7 purrs and headbutts your hand.", "§dMochi§7 slow-blinks at you. That's cat for \"I love you\"."};
    static final String USE_ON_DOG = "§7Right-click §bJerry§7 with it.";
    static final String FULL = " §8(He's had plenty for now!)";
    static final String TOO_FAR = "§7Jerry's too far away. Try the §fDog Whistle§7!";
    static final String CANT_HEAR = "§7Jerry's too far away to hear you. Try the whistle!";
    static final String CAME_RUNNING = "§bJerry§7 came running!";
    static final String BOTH_CAME = "§bJerry§7 and §dMochi§7 came running!";
    static final String PUP_CAME = "§bJerry§7 and §aPip§7 came running!";
    static final String ALL_CAME = "§bJerry§7, §dMochi§7 and §aPip§7 came running!";
    static final String CANT_HEAR_CAT = "§dMochi§7 is too far away to hear you. (Or she's ignoring you. She's a cat.)";
    static final String CANT_HEAR_PUP = "§aPip§7 is too far away to hear you. Try the whistle!";
    static final String DOG_RENAMED = "§aYour dog is now called §bJerry§a! He loves it.";
    static final String CAT_RENAMED = "§aYour cat is now called §dMochi§a! Purrr.";
    static final String PUP_RENAMED = "§aYour puppy is now called §aPip§a! He's thrilled.";
    static final String NO_PUP = "§7You don't have a puppy yet. Become §fInseparable§7 with §bJerry§7!";
    static final String NO_CAT = "§7You don't have a cat yet. Keep being friends with §bJerry§7!";
    static final String QUIET_ON = "§7Jerry will keep his thoughts to himself (no random messages). §f/dog quiet§7 again to undo.";
    static final String QUIET_OFF = "§7Jerry will tell you what he's up to again.";

    static final String[] UNKNOWN = {
        "§bJerry§7 tilts his head. He doesn't know that one yet... but he loves hearing your voice.",
        "§bJerry§7 listens very carefully, then wags his tail anyway.",
    };
    static final String[] MOCHI_HI = {"§dMochi§f: Mrrp! §7(That means hi.)", "§dMochi§7 meows back at you and slow-blinks."};
    static final String[] PIP_HI = {"§aPip§f: Yip! Yip! §7(That means hi.)", "§aPip§7 wags so hard he falls over."};
    static final String CAT_NOPE = "§dMochi§7 stares at you. Cats don't §f{trick}§7. She licks her paw instead.";

    static final String[] HELP = {
        "§6[Jerry the Dawg]§f Commands:",
        "§f/dog tricks§7 - every trick he knows",
        "§f/dog <trick>§7 - e.g. §f/dog roll over§7 (or just say it in chat with his name)",
        "§f/dog name <name>§7 - rename your dog §8|§f /dog catname <name>§7 - rename your cat",
        "§f/dog call§7 - call your pets §8|§f /dog status§7 - mood, needs, friendship (or sneak + use the whistle)",
        "§f/dog items§7 - get the pet items again §8|§f /dog quiet§7 - no random messages",
        "§f/dog pupname <name>§7 - rename your puppy §8|§f /dog catcoat <ginger|black|siamese>§7 - Mochi's coat",
    };

    /** One voice/chat command: the words that trigger it, what he does, and what he says. */
    static final class Voice {
        final String action;
        final boolean praise;
        final List<String> words;
        final List<String> replies;
        /** For "math": how many times to bark (-1 = too hard for a dog). */
        final int count;

        Voice(String action, boolean praise, String[] words, String... replies) {
            this.action = action;
            this.praise = praise;
            this.words = Arrays.asList(words);
            this.replies = Arrays.asList(replies);
            this.count = 0;
        }

        /** A math problem: "what's 2 plus 3?" -> bark 5 times. */
        Voice(int count) {
            this.action = "math";
            this.praise = false;
            this.words = Arrays.asList("do math");
            this.replies = Arrays.<String>asList();
            this.count = count;
        }
    }

    private static String[] w(String... words) {
        return words;
    }

    // The first entry whose words appear in what you said wins, so longer phrases go first.
    static final List<Voice> VOICE = Collections.unmodifiableList(Arrays.asList(
        new Voice("love", true, w("good boy", "good dog", "love you", "best boy", "good job", "well done", "cute",
                "good girl", "good kitty", "good puppy", "good pup"),
            "§bJerry§7's tail is wagging so fast it's basically a propeller.", "§bJerry§7 knows. He's very aware. *happy wiggle*"),
        new Voice("hug", true, w("give me a hug", "hug me", "hug", "hugs", "cuddle", "cuddles", "snuggle"),
            "§bJerry§7 stands up and gives you the biggest hug. *happy wiggle*", "§bJerry§7 wraps his paws around you. Best hug ever."),
        new Voice("scratch", false, w("scratch your ear", "scratch", "itchy", "itch", "got fleas"),
            "§bJerry§7 sits and scratches behind his ear. *thump thump thump*", "§bJerry§7 finds the itchy spot. Ahhh, that's the one."),
        new Voice("sneeze", false, w("sneeze", "achoo", "a choo", "bless you", "ah choo"),
            "§bJerry§f: Achoo! §7(Bless you, Jerry.)", "§bJerry§7 does the tiniest sneeze and looks surprised at himself."),
        new Voice("catch", false, w("catch", "catch it", "catch the treat", "heads up"),
            "§bJerry§7 snaps the treat right out of the air! *crunch*", "§bJerry§7 catches it like a pro. Didn't even blink."),
        new Voice("find", false, w("find it", "find the treat", "go find", "find", "seek", "sniff"),
            "§bJerry§7 puts his nose to the ground. *sniff sniff sniff*", "§bJerry§7 is on the case. Detective Jerry."),
        new Voice("shy", false, w("hide your face", "are you shy", "shy", "peekaboo", "peek a boo", "hide"),
            "§bJerry§7 turns away all shy... then peeks back at you. Aww.", "§bJerry§7 hides his face. Too cute to look at directly."),
        new Voice("stretch", false, w("big stretch", "stretch", "stretching"),
            "§bJerry§7 does a big long streeeetch... and a yawn.", "§bJerry§7 stretches out every single paw."),
        new Voice("groom", false, w("groom", "wash your face", "wash", "clean yourself", "lick your paw", "bath"),
            "§bJerry§7 tries to wash his face like a cat. It doesn't really work.", "§bJerry§7 licks his paw, very pleased with himself."),
        new Voice("sleep", false, w("go to sleep", "go to bed", "goodnight", "good night", "night night", "sleep tight", "bedtime", "lights out"),
            "§bJerry§7 curls up and closes his eyes. Goodnight, Jerry. Say §f\"come\"§7 to wake him.",
            "§bJerry§7 does three little circles and falls asleep. Shhh."),
        new Voice("spinleft", false, w("spin left", "turn left", "spin to the left"),
            "§bJerry§7 spins to the left!", "§bJerry§7 does a left spin. Nailed it."),
        new Voice("spinright", false, w("spin right", "turn right", "spin to the right"),
            "§bJerry§7 spins to the right!", "§bJerry§7 does a right spin. Perfect."),
        new Voice("purr", false, w("purr", "purr for me", "purring"),
            "§bJerry§7 tries to purr. It comes out as a happy grumble.", "§bJerry§7 is a dog. But he gives it a go."),
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
        new Voice("shakeoff", false, w("shake it off", "shake off", "shake yourself", "dry off", "get dry", "you're wet", "youre wet",
                "you're soaked"),
            "§bJerry§7 shakes from nose to tail. Water EVERYWHERE.", "§bJerry§7 does a big wet-dog shake. You're soaked now."),
        new Voice("highfive", false, w("high five", "high fives", "hi five", "high"),
            "§bJerry§7 gives you a high five! *boop*", "§bJerry§7 slaps your hand with his paw. Nailed it."),
        new Voice("paw", false, w("shake", "paw", "give me your paw", "gimme paw", "shake hands"),
            "§bJerry§7 offers you his paw. What a gentleman.", "§bJerry§7 shakes your hand very politely."),
        new Voice("wave", false, w("wave", "say bye", "bye", "goodbye", "bye bye", "say hi"),
            "§bJerry§7 waves his paw at you!", "§bJerry§7 waves bye-bye. (He's not going anywhere.)"),
        new Voice("bow", false, w("bow", "take a bow"),
            "§bJerry§7 takes a bow. Thank you, thank you.", "§bJerry§7 does a big play bow. He wants to play!"),
        new Voice("dance", false, w("dance", "boogie", "party", "dance party"),
            "§bJerry§7 hops and spins to the music. DANCE PARTY!", "§bJerry§7 has moves. Serious moves."),
        new Voice("backup", false, w("back up", "backup", "back off", "go back", "reverse", "beep beep", "step back"),
            "§bJerry§7 walks backwards. Beep. Beep. Beep.", "§bJerry§7 backs up very carefully, looking right at you."),
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
        new Voice("pupHi", false, w("pip", "puppy", "pup")),
        new Voice("greet", false, w("hello", "hi", "hey", "morning", "jerry"),
            "§bJerry§7 barks hello and wags his whole body.", "§bJerry§7 barks hello right back!")
    ));

    static final String[] TRICK_LIST = {
        "§6[Jerry the Dawg]§f Tricks §7(say them in chat with his name, or §f/dog <trick>§7):",
        "§fsit, lie down, come, shake, high five, wave, beg, roll over, play dead, bow,",
        "§fdance, backflip, howl, chase your tail, spin, spin left/right, jump, speak, shake off,",
        "§ffetch, find it, are you shy, stretch, groom, hug, scratch, sneeze, catch, back up, go to sleep",
        "§fMath: §f\"what's 2 plus 3?\"§7, §f\"count to 4\"§7 (he barks the answer, 1 to 10)",
        "§dMochi§7 (say her name first): §fsit, come, paw, high five, wave, beg, roll over, play dead, jump, spin,",
        "§fchase your tail, shy, stretch, groom, purr, hug, scratch, shake off, catch, sneeze, go to sleep",
        "§aPip§7 the puppy tries anything §bJerry§7 does (say his name to talk to him).",
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
            {"hugging", "hug"}, {"hugged", "hug"}, {"scratching", "scratch"}, {"sneezing", "sneeze"}, {"sneezes", "sneeze"},
            {"catching", "catch"},
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
            {"find", "find it"}, {"shy", "shy"}, {"stretch", "stretch"}, {"spinleft", "spin left"}, {"spinright", "spin right"},
            {"scratch", "scratch"}, {"sneeze", "sneeze"}, {"catch", "catch"}, {"backup", "back up"},
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
    static final String[] CAT_OOPS = {
        "§dMochi§7 was going to §f{trick}§7... then spun around instead. On purpose, obviously.",
        "§dMochi§7 considered §f{trick}§7 and decided against it. Maybe later.",
        "§dMochi§7 sat down instead of §f{trick}§7. She's thinking about it.",
        "§dMochi§7 pounced on an imaginary bug instead of §f{trick}§7.",
    };
    static final String CAT_MASTERED = "§e* * * §dMochi§e mastered §f{trick}§e! (She'll pretend she always knew.) * * *";
    static final String[] PUP_COPY = {
        "§aPip§7 copies §bJerry§7 and does a tiny §f{trick}§7! So proud.",
        "§aPip§7 watched §bJerry§7 and does §f{trick}§7 too!",
    };
    static final String[] PUP_COPY_FAIL = {
        "§aPip§7 tries to §f{trick}§7 like §bJerry§7... and tumbles over. Adorable.",
        "§aPip§7 tries §f{trick}§7 too, gets confused and chases his tail instead.",
    };
    static final String PUP_DID = "§aPip§7 does a tiny §f{trick}§7! He looks at you for approval.";
    static final String PUP_OOPS = "§aPip§7 tries to §f{trick}§7 and trips over his own paws. He's still learning!";
    static final String PUP_SLEEP = "§aPip§7 curls up right next to §bJerry§7 and falls asleep too.";
    static final String PUP_LOVE = "§aPip§7's whole body wags. Puppies don't do anything halfway.";

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
    static final String BEDTIME_CAT = "§bJerry§7 curls up at the foot of your bed, and §dMochi§7 loafs right on your pillow. Goodnight!";
    static final String BEDTIME_PUP = " §aPip§7 snuggles up against §bJerry§7.";

    // ------------------------------------------------------------------ math ("what's 2 plus 3?")

    static final String[] MATH_DONE = {
        "§bJerry§7 barked §f{n}§7 {times}. He's a genius!",
        "§bJerry§7 barks §f{n}§7 {times} and looks VERY proud. Correct!",
        "§f{n}§7! §bJerry§7 got it right. Somebody call the dog university.",
    };
    static final String MATH_ZERO = "§bJerry§7 stares at you and doesn't bark at all. Zero barks. ...Correct!";
    static final String[] MATH_HARD = {
        "§bJerry§7 tilts his head. That's too much math for one dog. (Try answers from 1 to 10!)",
        "§bJerry§7 counts on his paws and runs out of paws. (Try answers from 1 to 10!)",
    };
    static final String PUP_MATH = "§aPip§7 barks §f{n}§7 times too. Not quite, little guy. *tail wag anyway*";

    static final Map<String, String[]> CAT_REPLIES;

    static {
        Map<String, String[]> m = new HashMap<String, String[]>();
        m.put("sit", new String[] {"§dMochi§7 sits. Not because you asked. She just wanted to."});
        m.put("lie", m.get("sit"));
        m.put("come", new String[] {"§dMochi§7 strolls over. Eventually.", "§dMochi§7 trots right to you, tail up."});
        m.put("paw", new String[] {"§dMochi§7 boops your hand with her paw.", "§dMochi§7 gives you her paw. A rare honour."});
        m.put("highfive", new String[] {"§dMochi§7 high fives you! Tiny beans!"});
        m.put("roll", new String[] {"§dMochi§7 rolls over and shows her tummy. (It's a trap.)"});
        m.put("hop", new String[] {"§dMochi§7 leaps straight up like a spring!"});
        m.put("spin", new String[] {"§dMochi§7 does an elegant twirl."});
        m.put("spinleft", m.get("spin"));
        m.put("spinright", m.get("spin"));
        m.put("chase", new String[] {"§dMochi§7 chases her tail. She will deny this ever happened."});
        m.put("purr", new String[] {"§dMochi§7 purrs like a tiny motorbike.", "§dMochi§7 purrs and kneads the ground."});
        m.put("love", new String[] {"§dMochi§7 knows she's a good kitty. She's very aware.", "§dMochi§7 slow-blinks at you. Purrrr."});
        m.put("wave", new String[] {"§dMochi§7 waves her paw at you. So fancy.", "§dMochi§7 swipes the air. That's a wave. Probably."});
        m.put("beg", new String[] {"§dMochi§7 sits up on her back legs and stares into your soul.", "§dMochi§7 begs like a little meerkat."});
        m.put("dead", new String[] {"§dMochi§7 flops dramatically onto her side. The drama!", "§dMochi§7 plays dead. Her tail is still twitching."});
        m.put("shy", new String[] {"§dMochi§7 hides her face behind her paw. Cute overload.", "§dMochi§7 covers her eyes... then peeks at you."});
        m.put("stretch", new String[] {"§dMochi§7 does a looong cat stretch, bum in the air.", "§dMochi§7 stretches out her front paws. *yawn*"});
        m.put("groom", new String[] {"§dMochi§7 licks her paw and washes her face. Very important business.",
            "§dMochi§7 grooms herself. She's already perfect."});
        m.put("find", new String[] {"§dMochi§7 sniffs around... and finds a dust bunny. She's very proud.",
            "§dMochi§7 hunts around and pounces on a leaf!"});
        m.put("excited", new String[] {"§dMochi§7's ears perk up. Did someone say snacks?", "§dMochi§f: Mrrrow? §7(Treats?)"});
        m.put("yawn", new String[] {"§dMochi§7 does a tiny yawn. Her tongue sticks out a bit.", "§dMochi§7 curls up for a cat nap."});
        m.put("shock", new String[] {"§dMochi§7 jumps three feet in the air! Her tail is a bottle brush.", "§dMochi§7's eyes go HUGE."});
        m.put("play", new String[] {"§dMochi§7 gets the zoomies and tears around you!", "§dMochi§7 pounces on everything. Zoomies!"});
        m.put("bark", new String[] {"§dMochi§f: Meow! §7(She does not bark. She would like that noted.)", "§dMochi§f: Mrrrow!"});
        m.put("hug", new String[] {"§dMochi§7 allows the hug for exactly three seconds. Then she headbutts your chin. Love!",
            "§dMochi§7 purrs in your arms... then wriggles free. That counts."});
        m.put("sleep", new String[] {"§dMochi§7 curls into a perfect circle and falls asleep. Shhh.",
            "§dMochi§7 tucks her nose under her tail. Goodnight, Mochi."});
        m.put("sneeze", new String[] {"§dMochi§f: *tiny sneeze* §7She looks offended at her own nose.",
            "§dMochi§7 sneezes the smallest sneeze ever recorded."});
        m.put("scratch", new String[] {"§dMochi§7 scratches behind her ear with her back paw. *scritch scritch*",
            "§dMochi§7 finds an itch and deals with it. Elegantly."});
        m.put("shakeoff", new String[] {"§dMochi§7 shakes herself off and glares at you. She did NOT ask to be wet.",
            "§dMochi§7 does a tiny cat shake. Every hair is back in place."});
        m.put("catch", new String[] {"§dMochi§7 swats the treat out of the air and eats it like it was her idea.",
            "§dMochi§7 pounces and catches it! Purr-fect."});
        CAT_REPLIES = Collections.unmodifiableMap(m);
    }

    static final String CAT_HISS = "§dMochi§c hisses! §7There's a §f{mob}§7 nearby.";
    static final String CAT_NAP = "§dMochi§7 curls into a little ball and falls asleep. (Say §f\"Mochi, come\"§7 to wake her.)";
    static final String[] CAT_ZOOMIES = {
        "§dMochi§7 suddenly gets the zoomies! Nobody knows why.",
        "§dMochi§7 sprints in a circle, then sits down like nothing happened.",
    };

    static final String[] FOUND_IT = {
        "§bJerry§7 found it! *crunch crunch* He's very pleased with himself.",
        "§bJerry§7 sniffed out the treat! Best nose in the Overworld.",
    };

    static final String FURNITURE_NEW = "§6[Jerry the Dawg]§7 New! §bJerry§7 has a §cDog Bed§7 and a §7Food Bowl§7. Place them somewhere cozy!";
    static final String BED_PLACED = "§7You put down §bJerry§7's bed. He'll nap on it when he's sleepy (and at night).";
    static final String BOWL_PLACED = "§7You put down §bJerry§7's food bowl. Fill it with §fDog Treats§7 or any meat (right-click it).";
    static final String BOWL_FILLED = "§7You fill the food bowl. §bJerry§7 will eat when he's hungry.";
    static final String BOWL_FULL = "§7The food bowl is already full.";
    static final String BOWL_HINT = "§7Fill the food bowl with §fDog Treats§7 or any meat.";
    static final String GOING_TO_EAT = "§bJerry§7 trots over to his food bowl...";
    static final String ATE_FROM_BOWL = "§bJerry§7 licks the bowl clean. Delicious!";
    static final String GOING_TO_BED = "§bJerry§7 heads to his bed...";
    static final String NAP_ON_BED = "§bJerry§7 curls up on his bed for a nap. (Say §f\"come\"§7 to wake him.)";

    static final String CAT_GIFT = "§dMochi§7 brought you a gift: §f{gift}§7! She expects praise.";
    static final String[] CAT_GIFT_NAMES = {"a piece of string. Her favourite toy", "a feather", "a fish. Fresh. Very fresh"};
    static final String CAT_COAT_SET = "§dMochi§7 now has a §f{coat}§7 coat. She looks fabulous (she always did).";
    static final String CAT_COAT_HELP = "§7Pick §dMochi§7's coat: §f/dog catcoat ginger§7, §fblack§7 or §fsiamese";
    static final String[] GIFT_NAMES = {"a stick. The best stick", "a bone", "a poppy", "a dandelion", "a feather", "an emerald?! Where did he find that"};
}
