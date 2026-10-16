// All the add-on's text lives here so it is easy to tweak without touching the logic.
// "§" codes are Minecraft colours: §b aqua, §6 gold, §e yellow, §7 grey, §f white, §a green, §d pink.

export const TITLE = "§6Jerry & Friends";

export const INTRO = [
  "§6[Jerry & Friends]§f Meet §bJerry§f, the goodest boy in the whole Overworld!",
  "§7Give him §fTreats§7, use the §fPet Brush§7, and throw the §fTennis Ball§7 to play.",
  "§7Use §fTalk to Jerry§7 and say something! (On a phone, tap the keyboard microphone to say it out loud.)",
  "§7The more time you spend together, the better friends you'll be. Lost him? Use §fJerry's Whistle§7.",
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
// Used by the "Talk to Jerry" item. On phones, tap the microphone on the keyboard to say it out loud.

export const TALK_TITLE = "Talk to Jerry";
export const TALK_LABEL = "Say something to Jerry!\n§7Tap the §fmicrophone§7 on your keyboard and say it out loud.\nTry: sit, come, roll over, good boy, speak, spin, fetch, bone, boo!, Mochi\nOr a few at once: \"sit, then roll over, then speak\"";
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
export const FUZZY_WORDS = ["speak", "fetch", "mochi", "jerry", "belly", "zoomies", "twirl", "dance", "hello"];

// The first entry whose words appear in what you said wins, so more specific phrases go first.
// action: what Jerry does (see talkAction in main.js). praise: also earns a little friendship.
export const VOICE = [
  { words: ["good boy", "good dog", "love you", "best boy", "good job", "well done", "cute"], face: "love", action: "love", praise: true,
    replies: ["§bJerry§7's tail is wagging so fast it's basically a propeller.", "§bJerry§7 knows. He's very aware. *happy wiggle*"] },
  { words: ["roll over", "roll", "play dead", "belly rub", "belly rubs", "belly"], face: "happy", action: "roll",
    replies: ["§bJerry§7 rolls over onto his back! Belly rubs, please.", "§bJerry§7 flops over with his paws in the air."] },
  { words: ["sit", "stay", "wait", "stop", "freeze", "down"], face: "happy", action: "sit",
    replies: ["§bJerry§7 sits down and waits patiently. Say §f\"come\"§7 to call him.", "§bJerry§7 sits like a very good boy. Say §f\"come\"§7 when you're ready."] },
  { words: ["come", "here", "let's go", "lets go", "follow", "okay", "release", "free", "stand"], face: "happy", action: "come",
    replies: ["§bJerry§7 comes trotting over!", "§bJerry§7 zooms right to you."] },
  { words: ["speak", "bark", "woof", "talk", "say something"], face: "happy", action: "bark",
    replies: ["§bJerry§f: WOOF! WOOF!", "§bJerry§f: Woof! §7(He said it with feeling.)"] },
  { words: ["spin", "dance", "twirl", "trick"], face: "happy", action: "spin",
    replies: ["§bJerry§7 does a spin! Ta-da!", "§bJerry§7 shows off his best dance move."] },
  { words: ["jump", "hop", "up"], face: "happy", action: "hop",
    replies: ["§bJerry§7 jumps! Boing!", "§bJerry§7 hops like a bunny. A big fluffy bunny."] },
  { words: ["bone", "your bone", "get your bone"], face: "happy", action: "bone",
    replies: ["§bJerry§7 proudly shows off his bone.", "§bJerry§7 found his favourite bone. He will not be sharing."] },
  { words: ["fetch", "play", "ball", "zoomies"], action: "play" },
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
export const VOICE_CONNECTED = "§a[Jerry Voice]§f Connected! Use §fTalk to Jerry§f (or sneak + tap him) and just talk.";
export const VOICE_GET_READY = "§eGet ready to talk to Jerry...";
export const VOICE_LISTENING = "§a>> Listening... talk to Jerry now! <<";
export const VOICE_NOTHING = "§7Jerry didn't catch that. Try again!";
export const VOICE_LOST = "§7[Jerry Voice] The voice helper isn't answering, so here's the text box. (Is Termux still running? Run /connect 127.0.0.1:3000 again.)";

export const VOICE_MOCHI = ["§dMochi§f: Mrrp! §7(That means hi.)", "§dMochi§7 meows back at you and slow-blinks."];
export const CANT_HEAR = "§7Jerry's too far away to hear you. Try the whistle!";
export const VOICE_NO_CAT = "§bJerry§7 tilts his head. A cat? Where? He'd love a friend someday.";
export const VOICE_UNKNOWN = [
  "§bJerry§7 tilts his head. He doesn't know that one yet... but he loves hearing your voice.",
  "§bJerry§7 listens very carefully, then wags his tail anyway.",
];
