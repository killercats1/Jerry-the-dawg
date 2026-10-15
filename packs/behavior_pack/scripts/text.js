// All story text lives here so it is easy to tweak without touching the logic.
// "§" codes are Minecraft colours: §b aqua, §6 gold, §e yellow, §7 grey, §f white, §a green, §c red.

export const TITLE = "§6Something Replaced My Dog";

export const INTRO = [
  "§6[Something Replaced My Dog]§f This is §bJerry§f. He's the goodest boy in the whole Overworld.",
  "§7Jerry follows you everywhere. If he ever wanders off, use §fJerry's Whistle§7.",
  "§7Go explore! Build a house, chop some trees... just a normal, cozy day with your dog.",
];

// Little "everything is normal" moments before the swap.
export const HAPPY_MOMENTS = [
  { sound: "mob.wolf.bark", text: "§bJerry§7 barks proudly at absolutely nothing." },
  { sound: "mob.wolf.panting", text: "§bJerry§7 is panting happily. Good boy." },
  { sound: "mob.wolf.bark", action: "hop", text: "§bJerry§7 does a happy little hop!" },
  { sound: "mob.wolf.whine", text: "§bJerry§7 wants belly rubs. Obviously." },
  { sound: "mob.wolf.shake", text: "§bJerry§7 shakes off. You are now slightly damp." },
];

export const FORESHADOW = "§7Somewhere nearby, a cat meows. Jerry didn't even notice. Weird.";

export const SWAP_ACTIONBAR = "§7*pop* ...what was that noise?";

export const SWAP_LINES = [
  "§7Hmm. §bJerry§7 is right by your side. He's... looking at you a little differently.",
  "§7Something about Jerry seems §eoff§7 today. Like he forgot how to be a dog.",
  "§7You dig your old §fDetective Magnifier§7 out of your pocket. Use it on Jerry to look for clues!",
];

// Things the imposter does that a dog would never do.
export const QUIRKS = [
  { sound: "mob.cat.meow", text: "§7Did §bJerry§7 just... §emeow§7?" },
  { sound: "mob.cat.purr", text: "§bJerry§7 is making a weird rumbly purring noise." },
  { action: "name", text: "§7Wait... how is Jerry's name tag spelled?" },
  { action: "fish", sound: "random.pop", text: "§bJerry§7 brought you a raw fish. Jerry hates fish." },
  { action: "pounce", sound: "mob.cat.purreow", text: "§bJerry§7 pounced on a leaf like a... cat?" },
  { action: "spin", text: "§bJerry§7 is chasing a tail that's suspiciously thin." },
  { sound: "mob.cat.hiss", text: "§bJerry§7 just hissed at a flower. Rude." },
  { sound: "mob.wolf.bark", text: "§bJerry§7 barked! ...it sounded like someone saying \"bark\" out loud." },
];

export const NAME_GLITCHES = ["Jery", "Jerrry", "Totally Jerry", "Jerry (real)", "Cat- I mean Jerry", "Mrrrjerry"];

export const CLUES = [
  "Tiny retractable claws. Dog claws don't retract!",
  "His collar is ORANGE. Jerry's collar is red.",
  "He smells strongly of fish. Jerry hates fish.",
  "There's a cat hair on him. No wait, a LOT of cat hair.",
  "When you scratch his ears, he purrs. Dogs don't purr.",
];

export const CLUES_DONE = [
  "§eThat's it. You're sure of it now: §cthat is NOT Jerry.",
  "§7The real Jerry would do ANYTHING for his favorite treat. Let's see if this one will.",
  "§7You got §6Jerry's Favorite Treat§7! Use it on \"Jerry\" to find out the truth.",
];

export const ALREADY_SURE = "§7You've seen enough. Offer it §6Jerry's Favorite Treat§7!";
export const TREAT_TOO_EARLY = "§7\"Jerry\" sniffs the treat and looks around for fish. Find more clues first!";
export const TREAT_NOT_JERRY = "§7This treat is for Jerry. Look right at him and use it.";
export const TREAT_SNIFF = "§7\"Jerry\" sniffs the treat... and slowly pushes it away with one paw.";
export const TREAT_CAT = "§6Copycat§7 sniffs the treat and asks if you have any fish instead.";
export const TREAT_REAL = [
  "§bJerry§7 inhales the treat. Instantly. Best day ever.",
  "§bJerry§7 does a spin for the treat. A dog spin. The good kind.",
];

export const LOOK_AT_JERRY = "§7Look right at Jerry, then use the magnifier.";
export const INSPECT_REAL = [
  "§aThat's Jerry! 100% certified good boy.",
  "§aJerry. The real deal. Look at that tail go.",
];
export const INSPECT_CAT = [
  "§6Copycat§7 looks a little guilty. Mostly fluffy.",
  "§6Copycat§7. A cat. You're getting good at this.",
];
export const INSPECT_WOLF = "§7A regular wolf. Not Jerry. Not suspicious.";
export const INSPECT_CAT_OTHER = "§7Just a cat. ...Or is it? (It is.)";
export const INSPECT_OTHER = "§7Nothing suspicious about this one.";

export const REVEAL = [
  "§6Copycat§f: Mrrp! Okay, okay! You got me!",
  "§6Copycat§f: I'm not Jerry. I'm a cat. A very talented cat who can look like a dog.",
  "§6Copycat§f: I saw how much you loved Jerry... and I wanted somebody to love me like that too.",
  "§6Copycat§f: Don't worry, Jerry's totally fine! I tucked him into a cozy dog house for a nap.",
  "§6Copycat§f: ...a very long nap. With snacks. I'm not a monster, I'm a cat.",
];

export const RESCUE_START = [
  "§eFollow the §apaw prints§e to find Jerry! Look for a little dog house with a §fbone§e on the roof.",
  "§7(Copycat is tagging along. It feels bad about the whole thing.)",
];

export const WRONG_DIMENSION = "§7Jerry's paw prints are back in the dimension where you lost him...";
export const STILL_NAPPING = "§7Jerry can't hear the whistle from his nap. Follow the paw prints!";

export const REUNION = [
  "§bJerry§f: WOOF! §7(Translation: I had the BEST nap.)",
  "§6Copycat§f: Sorry for borrowing your human, Jerry...",
  "§bJerry§f: *sniff sniff* ... *happy tail wag*",
  "§7Jerry decided Copycat smells like a friend. It's official: you have a dog AND a cat now.",
  "§6~ THE END ~ §7Thanks for playing §fSomething Replaced My Dog: Cozy Edition§7!",
  "§8Inspired by LookOut3D. Want to replay? Run /scriptevent srmd:reset",
];
