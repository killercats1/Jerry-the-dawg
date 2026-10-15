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

export const MOMENTS = [
  { sound: "mob.wolf.bark", text: "§bJerry§7 barks proudly at absolutely nothing." },
  { sound: "mob.wolf.panting", text: "§bJerry§7 is panting happily. Good boy." },
  { sound: "mob.wolf.bark", action: "hop", text: "§bJerry§7 does a happy little hop!" },
  { sound: "mob.wolf.whine", text: "§bJerry§7 wants belly rubs. Obviously." },
  { sound: "mob.wolf.shake", text: "§bJerry§7 shakes off. You are now slightly damp." },
  { sound: "mob.wolf.panting", text: "§bJerry§7 sniffs a flower. He approves of this flower." },
  { sound: "mob.wolf.bark", text: "§bJerry§7 looks at you like you're his favourite person. You are." },
];

export const NIGHT_MOMENTS = [
  { sound: "mob.wolf.panting", text: "§bJerry§7 yawns a big sleepy yawn." },
  { sound: "mob.wolf.whine", text: "§bJerry§7 is looking at the stars. Or maybe the moon. Hard to say." },
];

export const CAT_MOMENTS = [
  { sound: "mob.cat.purr", text: "§dMochi§7 curls up next to §bJerry§7. Adorable." },
  { sound: "mob.cat.meow", text: "§dMochi§7 bats at Jerry's tail. Jerry pretends not to notice." },
  { sound: "mob.cat.purreow", text: "§dMochi§7 and §bJerry§7 are having a staring contest. Jerry blinked first." },
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
export const TALK_LABEL = "Say something to Jerry!\n§7Tip: tap the §fmicrophone§7 on your keyboard to say it out loud.\nTry: sit, come, good boy, speak, spin, fetch, Mochi";
export const TALK_PLACEHOLDER = "Good boy, Jerry!";

// The first entry whose words appear in what you said wins, so more specific phrases go first.
// action: what Jerry does (see talkAction in main.js). praise: also earns a little friendship.
export const VOICE = [
  { words: ["good boy", "good dog", "love you", "best boy", "good job", "well done", "cute"], action: "love", praise: true,
    replies: ["§bJerry§7's tail is wagging so fast it's basically a propeller.", "§bJerry§7 knows. He's very aware. *happy wiggle*"] },
  { words: ["sit", "stay", "wait", "stop", "freeze"], action: "stay",
    replies: ["§bJerry§7 stays right there and waits patiently. Say §f\"come\"§7 to call him.", "§bJerry§7 freezes like a statue. A fluffy statue. Say §f\"come\"§7 when you're ready."] },
  { words: ["come", "here", "let's go", "lets go", "follow", "okay", "release", "free"], action: "come",
    replies: ["§bJerry§7 comes trotting over!", "§bJerry§7 zooms right to you."] },
  { words: ["speak", "bark", "woof", "talk", "say something"], action: "bark",
    replies: ["§bJerry§f: WOOF! WOOF!", "§bJerry§f: Woof! §7(He said it with feeling.)"] },
  { words: ["spin", "roll over", "dance", "twirl", "trick"], action: "spin",
    replies: ["§bJerry§7 does a spin! Ta-da!", "§bJerry§7 shows off his best dance move."] },
  { words: ["jump", "hop", "up"], action: "hop",
    replies: ["§bJerry§7 jumps! Boing!", "§bJerry§7 hops like a bunny. A big fluffy bunny."] },
  { words: ["fetch", "play", "ball", "zoomies"], action: "play" },
  { words: ["treat", "hungry", "food", "snack", "dinner", "eat"], action: "excited",
    replies: ["§bJerry§7's ears perk up at the word \"treat\". Better give him one!", "§bJerry§7 is drooling a tiny bit."] },
  { words: ["sleep", "tired", "night", "nap", "bed"], action: "yawn",
    replies: ["§bJerry§7 does a big sleepy yawn.", "§bJerry§7 curls up for a little snooze."] },
  { words: ["mochi", "kitty", "cat"], action: "mochi" },
  { words: ["hello", "hi", "hey", "morning", "jerry"], action: "bark",
    replies: ["§bJerry§7 barks hello and wags his whole body.", "§bJerry§7 barks hello right back!"] },
];

export const VOICE_MOCHI = ["§dMochi§f: Mrrp! §7(That means hi.)", "§dMochi§7 meows back at you and slow-blinks."];
export const CANT_HEAR = "§7Jerry's too far away to hear you. Try the whistle!";
export const VOICE_NO_CAT = "§bJerry§7 tilts his head. A cat? Where? He'd love a friend someday.";
export const VOICE_UNKNOWN = [
  "§bJerry§7 tilts his head. He doesn't know that one yet... but he loves hearing your voice.",
  "§bJerry§7 listens very carefully, then wags his tail anyway.",
];
