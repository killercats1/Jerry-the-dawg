// All the add-on's text lives here so it is easy to tweak without touching the logic.
// "§" codes are Minecraft colours: §b aqua, §6 gold, §e yellow, §7 grey, §f white, §a green, §d pink.
// "Jerry" and "Mochi" are swapped for whatever you named your dog and cat before anything is shown.

export const TITLE = "§6Jerry & Friends";

export const INTRO = [
  "§6[Jerry & Friends]§f Meet §bJerry§f, the goodest boy in the whole Overworld!",
  "§7Give him §fTreats§7, use the §fPet Brush§7, and throw the §fTennis Ball§7 to play.",
  "§7Use §fTalk to Your Dog§7 and say something! He knows lots of tricks: sit, shake, roll over, play dead, dance, backflip...",
  "§7The more time you spend together, the better friends you'll be. Lost him? Use the §fDog Whistle§7.",
  "§7Want a different name? Use a §fname tag§7 on him, or §fsneak + use the Dog Whistle§7.",
];

export const WELCOME_BACK = "§6[Jerry & Friends]§7 Jerry missed you! *tail wag*";

export const LEVELS = ["New Pal", "Buddy", "Best Friend", "Inseparable"];

export const LEVEL_UP = [
  "",
  "§bJerry§7 now follows you extra closely. You're officially §fBuddies§7!",
  "§bJerry§7 thinks you're the best. Sometimes he'll bring you little §fgifts§7 now!",
  "§bJerry§7 and you are §fInseparable§7. This is the best friendship in the whole world.",
];

// face: which expression Jerry makes (happy, puppy, shocked, love, sleepy).
export const MOMENTS = [
  { sound: "mob.wolf.bark", face: "happy", text: "§bJerry§7 barks proudly at absolutely nothing." },
  { sound: "mob.wolf.panting", face: "happy", text: "§bJerry§7 is panting happily. Good boy." },
  { sound: "mob.wolf.bark", face: "happy", action: "hop", text: "§bJerry§7 does a happy little hop!" },
  { sound: "mob.wolf.whine", face: "puppy", text: "§bJerry§7 wants belly rubs. Obviously." },
  { sound: "mob.wolf.shake", face: "happy", text: "§bJerry§7 shakes off. You are now slightly damp." },
  { sound: "mob.wolf.panting", face: "love", text: "§bJerry§7 sniffs a flower. He approves of this flower." },
  { sound: "mob.wolf.bark", face: "love", text: "§bJerry§7 looks at you like you're his favourite person. You are." },
  { sound: "mob.wolf.panting", face: "happy", action: "roll", text: "§bJerry§7 rolls onto his back for belly rubs!" },
  { sound: "mob.wolf.whine", face: "puppy", action: "tilt", text: "§bJerry§7 tilts his head at you. So curious." },
  { sound: "mob.wolf.whine", face: "shocked", action: "hop", text: "§bJerry§7 saw a bee. He is SHOOK." },
];

export const NIGHT_MOMENTS = [
  { sound: "mob.wolf.panting", face: "sleepy", text: "§bJerry§7 yawns a big sleepy yawn." },
  { sound: "mob.wolf.whine", face: "sleepy", text: "§bJerry§7 is looking at the stars. Or maybe the moon. Hard to say." },
];

export const CAT_MOMENTS = [
  { sound: "mob.cat.purr", face: "happy", text: "§dMochi§7 curls up next to §bJerry§7. Adorable." },
  { sound: "mob.cat.meow", face: "shocked", text: "§dMochi§7 bats at Jerry's tail. Jerry pretends not to notice." },
  { sound: "mob.cat.purreow", face: "shocked", text: "§dMochi§7 and §bJerry§7 are having a staring contest. Jerry blinked first." },
];

export const TREAT = [
  "§bJerry§7 gobbles up the treat. Best day ever!",
  "§bJerry§7 sits nicely for the treat. Such manners!",
  "§bJerry§7 does a little spin, then takes the treat very gently.",
];
export const TREAT_CAT = "§dMochi§7 nibbles the treat politely. Purrrr.";
export const TREAT_OTHER = "§7These treats are for your pets. Look at Jerry and use them.";

export const BRUSH = [
  "§bJerry§7 is so fluffy now!",
  "§bJerry§7 leans into the brush. His back leg is going wild.",
  "§bJerry§7 looks extremely handsome.",
];
export const BRUSH_CAT = "§dMochi§7 is purring so loud you can feel it.";
export const BRUSH_OTHER = "§7Look at Jerry and use the brush to groom him.";

export const PLAY = [
  "§bJerry§7 does zoomies! Look at him go!",
  "§bJerry§7 chases the ball in happy circles.",
  "§bJerry§7 brings the ball back... and won't let go. Classic.",
];
export const PET = [
  "§bJerry§7 leans into the pets. Best. Feeling. Ever.",
  "§bJerry§7 tilts his head and wags his tail.",
  "§bJerry§7 does a happy little wiggle.",
];

export const PLAY_CAT = " §dMochi§7 joins in!";
export const TOO_FAR = "§7Jerry's too far away to play. Try the whistle!";

export const FULL = " §8(He's had plenty for now!)";

export const GIFTS = [
  { item: "minecraft:stick", name: "a stick. The best stick" },
  { item: "minecraft:bone", name: "a bone" },
  { item: "minecraft:poppy", name: "a poppy" },
  { item: "minecraft:dandelion", name: "a dandelion" },
  { item: "minecraft:sweet_berries", name: "some sweet berries" },
  { item: "minecraft:feather", name: "a feather" },
  { item: "minecraft:emerald", name: "an emerald?! Where did he find that" },
];

export const CAT_ARRIVES = [
  "§7A fluffy cat wandered over to say hi to §bJerry§7...",
  "§7They're friends already! Meet §dMochi§7, your new cat. The whistle calls Mochi too.",
];

// ---------------------------------------------------------------- talking to Jerry
// Used by the "Talk to Your Dog" item. On phones, tap the microphone on the keyboard to say it out loud.

export const TALK_TITLE = "Talk to Jerry";
export const TALK_LABEL = "Say something to Jerry!\n§7Tap the §fmicrophone§7 on your keyboard and say it out loud.\nTricks: sit, lie down, shake, high five, wave, beg, roll over, play dead, bow, spin, dance, backflip, howl, chase your tail, speak, fetch\nOr a few at once: \"sit, then shake, then roll over\"";
export const TALK_PLACEHOLDER = "Good boy, Jerry!";
export const TALK_KEEP_GOING = "Keep talking (this box pops back up after Jerry answers)";

// Words voice typing often gets wrong, and what they should have been.
export const MISHEARD = {
  role: "roll", rolled: "roll", rolling: "roll", rollover: "roll over", rolls: "roll",
  set: "sit", sat: "sit", seat: "sit", sitting: "sit", sits: "sit",
  gerry: "jerry", jeri: "jerry", jerri: "jerry", jerrie: "jerry", cherry: "jerry", jerrys: "jerry", "jerry's": "jerry",
  spinning: "spin", spins: "spin", jumping: "jump", jumps: "jump", barking: "bark", sleeping: "sleep",
  mocha: "mochi", moshi: "mochi", mochis: "mochi", mochie: "mochi", motchi: "mochi",
  comeon: "come", kom: "come",
};
// Distinctive words that still count when one letter is off, like "speek" or "fetchh".
export const FUZZY_WORDS = ["speak", "fetch", "mochi", "jerry", "belly", "zoomies", "twirl", "dance", "hello", "backflip", "stretch", "peekaboo"];

// The first entry whose words appear in what you said wins, so more specific phrases go first.
// action: what Jerry does (see talkAction in main.js). praise: also earns a little friendship.
export const VOICE = [
  { words: ["good boy", "good dog", "love you", "best boy", "good job", "well done", "cute"], face: "love", action: "love", praise: true,
    replies: ["§bJerry§7's tail is wagging so fast it's basically a propeller.", "§bJerry§7 knows. He's very aware. *happy wiggle*"] },
  { words: ["find it", "find the treat", "go find", "find", "search", "sniff", "seek", "where is it"], face: "puppy", action: "find",
    replies: ["§bJerry§7 puts his nose to the ground. *sniff sniff*", "§bJerry§7 goes into detective mode."] },
  { words: ["hide your face", "cover your eyes", "are you shy", "shy", "peekaboo", "peek a boo", "embarrassed", "blush"], face: "puppy", action: "shy",
    replies: ["§bJerry§7 hides his face behind his paw... then peeks out. So shy!", "§bJerry§7 covers his eyes. Peekaboo!"] },
  { words: ["big stretch", "stretch", "stretchy", "yoga"], face: "sleepy", action: "stretch",
    replies: ["§bJerry§7 does a big long streeeetch.", "§bJerry§7 stretches out his front paws and wiggles his bum."] },
  { words: ["play dead", "bang", "you're dead", "youre dead"], face: "sleepy", action: "dead",
    replies: ["§bJerry§7 flops over dramatically. Award-winning performance.", "§bJerry§7 plays dead... but his tail is still wagging."] },
  { words: ["roll over", "roll", "belly rub", "belly rubs", "belly"], face: "happy", action: "roll",
    replies: ["§bJerry§7 rolls over onto his back! Belly rubs, please.", "§bJerry§7 flops over with his paws in the air."] },
  { words: ["sit pretty", "beg", "pretty please"], face: "puppy", action: "beg",
    replies: ["§bJerry§7 sits up and begs with his best puppy eyes.", "§bJerry§7 sits pretty. How could you say no to that face?"] },
  { words: ["sit down", "sit", "stay", "wait", "stop", "freeze"], face: "happy", action: "sit",
    replies: ["§bJerry§7 sits down and waits patiently. Say §f\"come\"§7 to call him.", "§bJerry§7 sits like a very good boy. Say §f\"come\"§7 when you're ready."] },
  { words: ["lie down", "lay down", "down", "lie", "lay"], face: "happy", action: "lie",
    replies: ["§bJerry§7 lies down and gets comfy. Say §f\"come\"§7 to call him.", "§bJerry§7 flops down on his belly. Comfy."] },
  { words: ["high five", "high fives", "hi five", "high"], face: "happy", action: "highfive",
    replies: ["§bJerry§7 gives you a high five! *boop*", "§bJerry§7 slaps your hand with his paw. Nailed it."] },
  { words: ["shake", "paw", "give me your paw", "gimme paw", "shake hands"], face: "happy", action: "paw",
    replies: ["§bJerry§7 offers you his paw. What a gentleman.", "§bJerry§7 shakes your hand very politely."] },
  { words: ["wave", "say bye", "bye", "goodbye", "bye bye", "say hi"], face: "happy", action: "wave",
    replies: ["§bJerry§7 waves his paw at you!", "§bJerry§7 waves bye-bye. (He's not going anywhere.)"] },
  { words: ["bow", "take a bow"], face: "happy", action: "bow",
    replies: ["§bJerry§7 takes a bow. Thank you, thank you.", "§bJerry§7 does a big play bow. He wants to play!"] },
  { words: ["dance", "boogie", "party", "dance party"], face: "happy", action: "dance",
    replies: ["§bJerry§7 stands up on his back legs and DANCES!", "§bJerry§7 has moves. Serious moves."] },
  { words: ["backflip", "back flip", "flip", "do a flip"], face: "happy", action: "backflip",
    replies: ["§bJerry§7 does a BACKFLIP! How?!", "§bJerry§7 flips and sticks the landing. 10/10."] },
  { words: ["howl", "sing", "awoo", "awooo", "wolf"], face: "sleepy", action: "howl",
    replies: ["§bJerry§f: Awoooooo! §7(He's a wolf at heart.)", "§bJerry§7 howls a little song for you."] },
  { words: ["chase your tail", "chase his tail", "chase", "tail"], face: "happy", action: "chase",
    replies: ["§bJerry§7 chases his tail around and around and around...", "§bJerry§7 almost caught his tail! Almost."] },
  { words: ["come", "here", "let's go", "lets go", "follow", "okay", "release", "free", "stand"], face: "happy", action: "come",
    replies: ["§bJerry§7 comes trotting over!", "§bJerry§7 zooms right to you."] },
  { words: ["speak", "bark", "woof", "talk", "say something"], face: "happy", action: "bark",
    replies: ["§bJerry§f: WOOF! WOOF!", "§bJerry§f: Woof! §7(He said it with feeling.)"] },
  { words: ["spin left", "turn left", "to the left", "go left"], face: "happy", action: "spinleft",
    replies: ["§bJerry§7 spins to the left!", "§bJerry§7 does a spin the left way. He knows his left!"] },
  { words: ["spin right", "turn right", "to the right", "go right"], face: "happy", action: "spinright",
    replies: ["§bJerry§7 spins to the right!", "§bJerry§7 does a spin the right way. Clever boy!"] },
  { words: ["spin", "twirl", "trick", "turn around"], face: "happy", action: "spin",
    replies: ["§bJerry§7 does a spin! Ta-da!", "§bJerry§7 shows off his best dance move."] },
  { words: ["jump", "hop", "up"], face: "happy", action: "hop",
    replies: ["§bJerry§7 jumps! Boing!", "§bJerry§7 hops like a bunny. A big fluffy bunny."] },
  { words: ["bone", "your bone", "get your bone"], face: "happy", action: "bone",
    replies: ["§bJerry§7 proudly shows off his bone.", "§bJerry§7 found his favourite bone. He will not be sharing."] },
  { words: ["fetch", "ball", "go get it", "get the ball"], action: "fetch" },
  { words: ["play", "zoomies"], action: "play" },
  { words: ["boo", "surprise", "guess what", "oh no", "whoa", "wow"], face: "shocked", action: "shock",
    replies: ["§bJerry§7 jumps! His eyes go HUGE.", "§bJerry§7 is shocked. Absolutely shocked."] },
  { words: ["treat", "hungry", "food", "snack", "dinner", "eat"], face: "puppy", action: "excited",
    replies: ["§bJerry§7's ears perk up at the word \"treat\". Better give him one!", "§bJerry§7 is drooling a tiny bit."] },
  { words: ["sleep", "tired", "night", "nap", "bed"], face: "sleepy", action: "yawn",
    replies: ["§bJerry§7 does a big sleepy yawn.", "§bJerry§7 curls up for a little snooze."] },
  { words: ["mochi", "kitty", "cat"], face: "happy", action: "mochi" },
  { words: ["hello", "hi", "hey", "morning", "jerry"], face: "puppy", action: "greet",
    replies: ["§bJerry§7 barks hello and wags his whole body.", "§bJerry§7 barks hello right back!"] },
];

// The Jerry Voice phone helper (real microphone).
export const VOICE_CONNECTED = "§a[Voice]§f Connected! Use §fTalk to Your Dog§f (or sneak + tap Jerry) and just talk.";
export const VOICE_GET_READY = "§eGet ready to talk to Jerry...";
export const VOICE_LISTENING = "§a>> Listening... talk to Jerry now! <<";
export const VOICE_NOTHING = "§7Jerry didn't catch that. Try again!";
export const VOICE_LOST = "§7[Voice] The voice helper isn't answering, so here's the text box. (Is Termux still running? Run /connect 127.0.0.1:3000 again.)";

// Naming your pets.
export const NAME_TITLE_NEW = "Meet your new dog!";
export const NAME_NEW_DOG = "What do you want to name your dog?\n§7(You can change it later with a name tag, or sneak + use the Dog Whistle.)";
export const NAME_TITLE = "Name your pets";
export const NAME_DOG = "Your dog's name";
export const NAME_CAT = "Your cat's name";
export const DOG_RENAMED = "§aYour dog is now called §bJerry§a! He loves it.";
export const CAT_RENAMED = "§aYour cat is now called §dMochi§a! Purrr.";

export const VOICE_MOCHI = ["§dMochi§f: Mrrp! §7(That means hi.)", "§dMochi§7 meows back at you and slow-blinks."];
export const CANT_HEAR = "§7Jerry's too far away to hear you. Try the whistle!";
export const VOICE_NO_CAT = "§bJerry§7 tilts his head. A cat? Where? He'd love a friend someday.";
export const VOICE_UNKNOWN = [
  "§bJerry§7 tilts his head. He doesn't know that one yet... but he loves hearing your voice.",
  "§bJerry§7 listens very carefully, then wags his tail anyway.",
];

// ---------------------------------------------------------------- needs & moods
// Hunger/energy/fun go down slowly. Treats, naps and play bring them back. Nothing bad ever happens.

export const MOOD_LABEL = {
  happy: "§aHappy",
  hungry: "§6Hungry §7(give him a treat)",
  tired: "§9Sleepy §7(let him lie down for a nap)",
  bored: "§eBored §7(play fetch with him)",
};
export const MOOD_MOMENTS = {
  hungry: [
    { sound: "mob.wolf.whine", face: "puppy", action: "tilt", text: "§bJerry§7's tummy rumbles. Treat, please?" },
    { sound: "mob.wolf.whine", face: "puppy", text: "§bJerry§7 stares at your pocket. He knows the treats are in there." },
  ],
  tired: [
    { sound: "mob.wolf.panting", face: "sleepy", action: "nap", text: "§bJerry§7 lies down for a nap. (Say §f\"come\"§7 to wake him.)" },
    { sound: "mob.wolf.panting", face: "sleepy", text: "§bJerry§7 does a big yawn. Someone needs a nap." },
  ],
  bored: [
    { sound: "mob.wolf.whine", face: "puppy", action: "bone", text: "§bJerry§7 drops his bone at your feet. Play fetch?" },
    { sound: "mob.wolf.bark", face: "puppy", action: "hop", text: "§bJerry§7 bounces around you. He's SO bored. Throw the ball!" },
  ],
};
export const TOO_SLEEPY = "§bJerry§7 is too sleepy for tricks... *yawn* He lies down instead.";

// ---------------------------------------------------------------- practice makes perfect
// Tricks get better with practice: early on he sometimes messes up, then he masters them.
export const TRICK_NAMES = {
  paw: "shake", highfive: "high five", wave: "wave", beg: "beg", roll: "roll over", dead: "play dead",
  bow: "bow", dance: "dance", backflip: "backflip", howl: "howl", chase: "chase his tail", spin: "spin",
  lie: "lie down", hop: "jump", find: "find it", shy: "be shy", stretch: "stretch",
};
export const FOUND_IT = [
  "§bJerry§7 found a treat! *crunch crunch* What a nose!",
  "§bJerry§7 sniffs out a treat and gobbles it up. Detective Jerry strikes again.",
  "§bJerry§7 found it! He looks extremely proud of himself.",
];
export const OOPS = [
  { action: "spin", text: "§bJerry§7 tried to §f{trick}§7... and did a spin instead. Close enough!" },
  { action: "tilt", text: "§bJerry§7 tried to §f{trick}§7, got confused and tilted his head. He's still learning!" },
  { action: "sit", text: "§bJerry§7 tried to §f{trick}§7... and just sat down. Practice makes perfect!" },
  { action: "hop", text: "§bJerry§7 got so excited about §f{trick}§7 that he just jumped around." },
];
export const MASTERED = "§e* * * §bJerry§e mastered §f{trick}§e! * * *";

// ---------------------------------------------------------------- fetch
export const FETCH_THROW = "§7You throw the ball! §bJerry§7 takes off after it...";
export const FETCH_GOT_IT = "§bJerry§7 got it!";
export const FETCH_DONE = [
  "§bJerry§7 drops the ball at your feet. Again! Again!",
  "§bJerry§7 brings the ball back, tail wagging like crazy.",
];
export const FETCH_BUSY = "§7Jerry's already chasing the ball!";
export const FETCH_LOST = "§7The ball's gone... §bJerry§7 looks around, confused.";

// ---------------------------------------------------------------- smart reactions
export const GUARD = "§bJerry§c growls! §7There's a §f{mob}§7 nearby, watch out!";
export const DEFEND = "§bJerry§c jumps in to protect you from the §f{mob}§c!";
export const WORRIED = "§bJerry§7 rushes over to check on you. You okay?";
export const BEG_FOOD = "§bJerry§7 watches you eat with HUGE puppy eyes...";
export const WELCOME_HOME = "§bJerry§7 is SO happy you're back! Zoomies!";
export const BEDTIME = "§bJerry§7 curls up at the foot of your bed. Goodnight!";

// ---------------------------------------------------------------- the status screen (sneak + Dog Whistle)
export const STATUS_TITLE = "Jerry";
export const STATUS_TRICKS = "Tricks";
export const STATUS_RENAME = "Rename";
export const STATUS_WARDROBE = "Wardrobe";

// ---------------------------------------------------------------- wardrobe
export const WARDROBE_TITLE = "Jerry's Wardrobe";
export const WARDROBE_HAT = "Hat";
export const WARDROBE_NECK = "Neck";
export const WARDROBE_COAT = "Fur";
export const WARDROBE_COLLAR = "Collar colour §7(or use any dye on him)";
export const HATS = ["None", "Party hat", "Flower crown", "Cool shades"];
export const NECKS = ["None", "Bandana", "Bow tie"];
// Order matches Array.coats in the resource pack (the game's own wolf coats).
export const COATS = ["Classic", "Ashen", "Black", "Chestnut", "Rusty", "Snowy", "Spotted", "Striped", "Woods"];
// Order matches the minecraft:color values (0 = white ... 14 = red, 15 = black).
export const COLLARS = [
  "White", "Orange", "Magenta", "Light blue", "Yellow", "Lime", "Pink", "Gray",
  "Light gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black",
];
export const LOOK_SAVED = [
  "§bJerry§7 shows off his new look. Very handsome!",
  "§bJerry§7 does a little spin to show off his outfit.",
  "§bJerry§7 looks fabulous and he knows it.",
];
export const COLLAR_DYED = "§bJerry§7's collar is now §f{colour}§7. He loves it!";
export const STATUS_CLOSE = "Close";
export const TRICKS_TITLE = "Jerry's tricks";
export const TRICKS_BACK = "Back";
