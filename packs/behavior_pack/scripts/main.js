import { world, system, ItemStack } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import * as T from "./text.js";

// ---------------------------------------------------------------------------
// Jerry & Friends: a chill pet add-on. Every player gets Jerry, a tamed wolf.
// Treats, brushing, playing and time together raise friendship. At "Buddy" a
// cat named Mochi joins the family; at "Best Friend" Jerry brings you gifts.
// Progress is saved per player in dynamic properties.
// ---------------------------------------------------------------------------

const ITEMS = {
  whistle: "jf:dog_whistle",
  brush: "jf:pet_brush",
  treat: "jf:dog_treat",
  ball: "jf:tennis_ball",
  talk: "jf:talk",
};
const OUR_ITEMS = new Set(Object.values(ITEMS));
// The placeable dog bed and food bowl (blocks/). A full bowl is its own block so his "go eat"
// behavior can look for full bowls only.
const BLOCKS = { bed: "jf:dog_bed", bowl: "jf:food_bowl", fullBowl: "jf:food_bowl_full" };
const FOODS = new Set([
  "jf:dog_treat", "minecraft:beef", "minecraft:cooked_beef", "minecraft:porkchop", "minecraft:cooked_porkchop",
  "minecraft:chicken", "minecraft:cooked_chicken", "minecraft:mutton", "minecraft:cooked_mutton",
  "minecraft:rabbit", "minecraft:cooked_rabbit", "minecraft:rotten_flesh",
]);

const PROP = {
  started: "jf:started",
  friendship: "jf:friendship",
  dog: "jf:dog_id",
  cat: "jf:cat_id",
  hasCat: "jf:has_cat",
  keepTalking: "jf:keep_talking",
  needs: "jf:needs",
  skills: "jf:skills",
  dogName: "jf:dog_name",
  catName: "jf:cat_name",
  look: "jf:look",
  gotHome: "jf:got_home",
  pup: "jf:pup_id",
  hasPup: "jf:has_pup",
  pupName: "jf:pup_name",
};
const TAG = { pet: "jf_pet", dog: "jf_dog", cat: "jf_cat", pup: "jf_pup" };
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
const JERRY = "jf:jerry";
const MOCHI = "jf:mochi";
const BALL = "jf:thrown_ball"; // the thrown tennis ball (can't be picked up or broken)
const FETCH_SLOTS = 8; // matches the jf:fetch_chase_0..7 behaviors in entities/jerry.json
// Matches the jf:pose property in entities/jerry.json and the pose animation controller.
const POSE = {
  stand: 0, sit: 1, roll: 2, tilt: 3, paw: 4, wave: 5, beg: 6,
  playDead: 7, lie: 8, bow: 9, dance: 10, backflip: 11, howl: 12, sniff: 13, shy: 14, stretch: 15, groom: 16,
  scratch: 17, shakeOff: 18, hug: 19, sleep: 20, sneeze: 21, // Mochi has scratch and shakeOff too, but not the rest
  listen: 22,
};
const DEFAULT_DOG_NAME = "Jerry";
const DEFAULT_CAT_NAME = "Mochi";
const DEFAULT_PUP_NAME = "Pip";

// Friendship goes 0-100. Level thresholds line up with T.LEVELS.
const MAX_FRIENDSHIP = 100;
const LEVEL_MIN = [0, 25, 60, 100];
const CAT_LEVEL = 1;
const GIFT_LEVEL = 2;
const PUP_LEVEL = 3;
const GAIN = { treat: 5, brush: 3, ball: 4, talk: 2, pet: 2, together: 1 };
const COOLDOWN = { treat: 60, brush: 30, ball: 45, talk: 30, pet: 20 }; // seconds before an action earns friendship again

let seconds = 0;
const cooldowns = new Map(); // "playerId:action" -> seconds when it earns friendship again
const nextMoment = new Map();
const nextGift = new Map();
const lastUse = new Map();
const pendingIntro = new Set();
const pendingCat = new Set();
const pendingPup = new Set();
const refreshed = new Set(); // dogs whose behaviors were refreshed this session
const poseTimers = new Map(); // dog id -> latest trick, so an older timer doesn't cut a newer trick short
const propTimers = new Map(); // "dogId:property" -> latest timed change
const fetching = new Map(); // dog id -> the running fetch game
const fighting = new Map(); // dog id -> { attacker id, until tick } while he's defending you
const errands = new Map(); // dog id -> his trip to the food bowl or his bed
const reactedAt = new Map(); // "playerId:reaction" -> seconds, so reactions don't spam
const awaySince = new Map(); // player id -> seconds when the dog was last nearby
const listening = new Map(); // player id -> { dog id, until tick } while he's all ears ("listen up")
// The optional phone voice helper (voice-helper/jerry_voice.py) checks in every few seconds.
const voiceHelper = { lastSeen: -999, lastListener: undefined, ai: false };
const LISTEN_TAG = "jf_listen"; // the helper listens when it sees this tag on a player

// ---------------------------------------------------------------- helpers

const rand = (min, max) => min + Math.random() * (max - min);
const pick = (list) => list[Math.floor(Math.random() * list.length)];
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
const ownerTag = (player) => `jf_owner_${player.id}`;

function isValid(entity) {
  if (!entity) return false;
  try {
    return typeof entity.isValid === "function" ? entity.isValid() : !!entity.isValid;
  } catch {
    return false;
  }
}

function getNum(player, key) {
  const value = player.getDynamicProperty(key);
  return typeof value === "number" ? value : 0;
}

function isNear(pet, player, range) {
  return isValid(pet) && pet.dimension.id === player.dimension.id && dist(pet.location, player.location) <= range;
}

function particle(dim, id, location) {
  try {
    dim.spawnParticle(id, location);
  } catch {}
}

function sound(dim, id, location, options) {
  try {
    dim.playSound(id, location, options);
  } catch {
    try {
      world.playSound(id, location, options);
    } catch {}
  }
}

function sparkle(pet, id = "minecraft:heart_particle", count = 5) {
  const at = pet.location;
  for (let i = 0; i < count; i++) {
    particle(pet.dimension, id, { x: at.x + rand(-0.5, 0.5), y: at.y + rand(0.5, 1.3), z: at.z + rand(-0.5, 0.5) });
  }
}

// Your pets can have any names. All text is written with "Jerry" and "Mochi" and swapped for
// the real names right before it's shown.
const dogName = (player) => player.getDynamicProperty(PROP.dogName) || DEFAULT_DOG_NAME;
const catName = (player) => player.getDynamicProperty(PROP.catName) || DEFAULT_CAT_NAME;
const pupName = (player) => player.getDynamicProperty(PROP.pupName) || DEFAULT_PUP_NAME;

function personalize(player, text) {
  return String(text)
    .replace(/Jerry/g, () => dogName(player)) // no \b: colour codes like "§bJerry" sit right against the name
    .replace(/Mochi/g, () => catName(player))
    .replace(/Pip/g, () => pupName(player));
}

function tell(player, text) {
  try {
    player.sendMessage(personalize(player, text));
  } catch {}
}

function actionbar(player, text) {
  try {
    player.onScreenDisplay.setActionBar(personalize(player, text));
  } catch {}
}

function title(player, main, subtitle) {
  try {
    player.onScreenDisplay.setTitle(personalize(player, main), {
      subtitle: subtitle && personalize(player, subtitle),
      fadeInDuration: 10,
      stayDuration: 70,
      fadeOutDuration: 20,
    });
  } catch {}
}

function say(player, lines, gap = 70) {
  lines.forEach((line, i) =>
    system.runTimeout(() => {
      if (isValid(player)) tell(player, line);
    }, i * gap)
  );
}

const isNight = () => {
  const time = world.getTimeOfDay();
  return time >= 12500 && time <= 23500;
};

// ---------------------------------------------------------------- items

function inventory(player) {
  return player.getComponent("minecraft:inventory")?.container;
}

function hasItem(player, id) {
  const inv = inventory(player);
  if (!inv) return false;
  for (let i = 0; i < inv.size; i++) if (inv.getItem(i)?.typeId === id) return true;
  return false;
}

function giveAllItems(player) {
  for (const id of [...Object.values(ITEMS), BLOCKS.bed, BLOCKS.bowl]) give(player, id);
  player.setDynamicProperty(PROP.gotHome, true);
}

function give(player, id) {
  if (hasItem(player, id)) return;
  const inv = inventory(player);
  const stack = new ItemStack(id, 1);
  const leftover = inv ? inv.addItem(stack) : stack;
  if (leftover) player.dimension.spawnItem(leftover, player.location);
}

// ---------------------------------------------------------------- friendship

function levelOf(friendship) {
  let level = 0;
  LEVEL_MIN.forEach((min, i) => {
    if (friendship >= min) level = i;
  });
  return level;
}

function friendshipBar(player) {
  const friendship = getNum(player, PROP.friendship);
  const filled = Math.round(friendship / 10);
  return `§c${"|".repeat(filled)}§8${"|".repeat(10 - filled)} §7${T.LEVELS[levelOf(friendship)]}`;
}

function addFriendship(player, amount) {
  const before = getNum(player, PROP.friendship);
  const after = Math.min(MAX_FRIENDSHIP, before + amount);
  player.setDynamicProperty(PROP.friendship, after);
  const level = levelOf(after);
  if (level > levelOf(before)) {
    const dog = findDog(player);
    if (dog) showFace(dog, "love", 100);
    title(player, "§aFriendship up!", `§fJerry is now your §e${T.LEVELS[level]}`);
    sound(player.dimension, "random.levelup", player.location);
    system.runTimeout(() => {
      if (isValid(player)) tell(player, T.LEVEL_UP[level]);
    }, 40);
  }
}

// Each action only earns friendship once per cooldown, so spamming doesn't max it out.
function earn(player, action) {
  const key = `${player.id}:${action}`;
  if (seconds < (cooldowns.get(key) ?? 0)) return false;
  cooldowns.set(key, seconds + COOLDOWN[action]);
  addFriendship(player, GAIN[action]);
  return true;
}

// ---------------------------------------------------------------- pets

function findPet(player, prop, role) {
  const id = player.getDynamicProperty(prop);
  if (typeof id === "string") {
    try {
      const entity = world.getEntity(id);
      if (isValid(entity) && entity.hasTag(role)) return entity;
    } catch {}
  }
  for (const dimId of DIMENSIONS) {
    try {
      const found = world.getDimension(dimId).getEntities({ tags: [role, ownerTag(player)] });
      if (found.length) {
        player.setDynamicProperty(prop, found[0].id);
        return found[0];
      }
    } catch {}
  }
  return undefined;
}

function findDog(player) {
  const dog = findPet(player, PROP.dog, TAG.dog);
  if (!dog || dog.typeId === JERRY) return dog;
  // Older versions used a vanilla wolf for Jerry; swap in the new Jerry where the old one stood.
  const at = { dimension: dog.dimension, location: dog.location };
  dog.remove();
  return spawnJerry(player, at);
}
function findCat(player) {
  if (!player.getDynamicProperty(PROP.hasCat)) return undefined;
  const cat = findPet(player, PROP.cat, TAG.cat);
  if (!cat || cat.typeId === MOCHI) return cat;
  // Older versions used a vanilla cat for Mochi; swap in the new Mochi, keeping her coat.
  const at = { dimension: cat.dimension, location: cat.location };
  try {
    const variant = /** @type {any} */ (cat.getComponent("minecraft:variant"))?.value;
    if (typeof variant === "number" && getLook(player).catCoat === undefined) setLook(player, { catCoat: variant });
  } catch {}
  cat.remove();
  return spawnMochi(player, at);
}

function tame(pet, player) {
  try {
    if (pet.getComponent("minecraft:is_tamed")) return;
    pet.getComponent("minecraft:tameable")?.tame(player);
  } catch {}
}

// Your pets can't get hurt, so you never lose them.
function protect(pet) {
  try {
    pet.addEffect("resistance", 600, { amplifier: 255, showParticles: false });
    pet.addEffect("fire_resistance", 600, { amplifier: 0, showParticles: false });
  } catch {}
}

function spawnPet(player, typeId, role, name, prop, at = { dimension: player.dimension, location: player.location }) {
  const pet = at.dimension.spawnEntity(typeId, at.location);
  try {
    if (pet.getComponent("minecraft:is_baby")) pet.triggerEvent("minecraft:ageable_grow_up");
  } catch {}
  pet.nameTag = name;
  pet.addTag(TAG.pet);
  pet.addTag(role);
  pet.addTag(ownerTag(player));
  player.setDynamicProperty(prop, pet.id);
  tame(pet, player);
  protect(pet);
  // In case taming didn't stick on the spawn tick.
  system.runTimeout(() => {
    if (isValid(pet) && isValid(player)) tame(pet, player);
  }, 5);
  return pet;
}

function spawnJerry(player, at) {
  const dog = spawnPet(player, JERRY, TAG.dog, dogName(player), PROP.dog, at);
  system.runTimeout(() => isValid(dog) && isValid(player) && applyLook(player, dog), 10); // after taming sets the red collar
  return dog;
}
const findPup = (player) => (player.getDynamicProperty(PROP.hasPup) ? findPet(player, PROP.pup, TAG.pup) : undefined);
const isPup = (pet) => isValid(pet) && pet.hasTag(TAG.pup);

// The puppy is a little Jerry: same entity, but a baby (smaller, big head, never fights).
function spawnPup(player, at) {
  const pup = spawnPet(player, JERRY, TAG.pup, pupName(player), PROP.pup, at);
  const shrink = () => {
    try {
      if (isValid(pup)) pup.triggerEvent("jf:make_puppy");
    } catch {}
  };
  shrink();
  system.runTimeout(shrink, 5);
  system.runTimeout(() => isValid(pup) && isValid(player) && applyPupLook(pup, getLook(player)), 10);
  return pup;
}

function spawnMochi(player, at) {
  const cat = spawnPet(player, MOCHI, TAG.cat, catName(player), PROP.cat, at);
  system.runTimeout(() => isValid(cat) && isValid(player) && applyCatLook(cat, getLook(player)), 10);
  return cat;
}

function bring(pet, player) {
  try {
    pet.teleport(player.location, { dimension: player.dimension });
  } catch {}
}

// ---------------------------------------------------------------- Jerry's poses
// The client animations (sit, roll over, head tilt, speaker icon) are driven by entity properties.

function setPose(dog, pose) {
  try {
    dog.setProperty("jf:pose", pose);
  } catch {}
}

// Resting poses (sit, lie down) last until he's called; tricks go back to them afterwards.
function restPose(dog) {
  const rest = dog.getDynamicProperty("jf:rest");
  if (typeof rest === "number") return rest;
  return dog.getDynamicProperty("jf:sitting") === true ? POSE.sit : undefined; // saved by older versions
}

const isSitting = (dog) => restPose(dog) !== undefined;

function sit(dog, pose = POSE.sit) {
  try {
    dog.triggerEvent("jf:sit"); // stops following and wandering
  } catch {}
  dog.setDynamicProperty("jf:rest", pose);
  setPose(dog, pose);
}

function stand(dog) {
  try {
    dog.triggerEvent("jf:stand");
  } catch {}
  dog.setDynamicProperty("jf:rest", undefined);
  dog.setDynamicProperty("jf:sitting", undefined);
  dog.setDynamicProperty("jf:on_bed", undefined);
  const errand = errands.get(dog.id);
  if (errand) errand.cancel = true; // called or told to get up: forget the trip
  setPose(dog, POSE.stand);
}

// A short trick, then back to resting or standing. Most tricks keep him in place while he does them.
function trick(dog, pose, ticks, holdStill = true) {
  const token = (poseTimers.get(dog.id) ?? 0) + 1;
  poseTimers.set(dog.id, token);
  const freeze = holdStill && !isSitting(dog);
  if (freeze) {
    try {
      dog.triggerEvent("jf:sit");
    } catch {}
  }
  setPose(dog, pose);
  system.runTimeout(() => {
    if (!isValid(dog) || poseTimers.get(dog.id) !== token) return;
    if (freeze && !isSitting(dog)) {
      try {
        dog.triggerEvent(movingEvent(dog)); // back to fetch (or his trip to the bowl) if he was busy
      } catch {}
    }
    setPose(dog, restPose(dog) ?? POSE.stand);
  }, ticks);
}

// The behavior he should be running when not doing a trick.
function movingEvent(dog) {
  if (fetching.has(dog.id)) return fetching.get(dog.id).event ?? "jf:sit";
  if (errands.has(dog.id)) return errands.get(dog.id).event;
  return "jf:stand";
}

const rollOver = (dog) => trick(dog, POSE.roll, 50);
const tiltHead = (dog) => trick(dog, POSE.tilt, 36, false);

// Sets one of Jerry's properties for a while, then puts it back. A newer call wins, so an
// older timer never cuts a newer expression short.
function setFor(dog, key, value, resetTo, ticks) {
  const id = `${dog.id}:${key}`;
  const token = (propTimers.get(id) ?? 0) + 1;
  propTimers.set(id, token);
  try {
    dog.setProperty(key, value);
  } catch {}
  system.runTimeout(() => {
    if (!isValid(dog) || propTimers.get(id) !== token) return;
    try {
      dog.setProperty(key, resetTo);
    } catch {}
  }, ticks);
}

// The speaker icon that pops up over Jerry's head when he hears you.
const showSpeaker = (dog, ticks) => setFor(dog, "jf:speaking", true, false, ticks);

// You said his name: a red "!" pops up over his head and he turns to look at you (like in the video).
const ALERT_TICKS = 22;
function alertPet(player, pet) {
  setFor(pet, "jf:alert", true, false, ALERT_TICKS);
  showFace(pet, "puppy", ALERT_TICKS + 10);
  const dx = player.location.x - pet.location.x;
  const dz = player.location.z - pet.location.z;
  try {
    pet.setRotation({ x: 0, y: (Math.atan2(-dx, dz) * 180) / Math.PI });
  } catch {}
  sound(pet.dimension, "mob.wolf.whine", pet.location, { pitch: isPup(pet) ? 1.8 : 1.4 });
}

function saidDogName(said, player) {
  const words = ` ${cleanWords(normalizeNames(said.toLowerCase().replace(/[^a-z' ]+/g, " "), player))} `;
  return / jerry /.test(words);
}

// "Jerry, listen up!": he cups a paw to his ear and the speaker icon pulses until you tell him
// something (or about 10 seconds pass). While he's listening you don't need to say his name.
function startListening(player, dog, ticks = 200) {
  listening.set(player.id, { dog: dog.id, until: system.currentTick + ticks });
  trick(dog, POSE.listen, ticks);
  showSpeaker(dog, ticks);
  showFace(dog, "puppy", ticks);
  sound(dog.dimension, "mob.wolf.whine", dog.location, { pitch: 1.5 });
}

const isListening = (player) => (listening.get(player.id)?.until ?? 0) > system.currentTick;

// You said something: he puts his paw down and gets on with it.
function stopListening(player) {
  const info = listening.get(player.id);
  listening.delete(player.id);
  if (!info || info.until <= system.currentTick) return;
  let pet, pose;
  try {
    pet = world.getEntity(info.dog);
    pose = pet?.getProperty("jf:pose");
  } catch {}
  if (isValid(pet) && pose === POSE.listen) trick(pet, restPose(pet) ?? POSE.stand, 1);
}

// Jerry's expressions (face overlays in the resource pack), matching the jf:face property.
const FACE = { normal: 0, happy: 1, puppy: 2, shocked: 3, love: 4, sleepy: 5 };
const showFace = (dog, face, ticks = 60) => setFor(dog, "jf:face", FACE[face] ?? FACE.normal, FACE.normal, ticks);
// The bone he carries in his mouth.
const holdBone = (dog, ticks = 60) => setFor(dog, "jf:bone", true, false, ticks);

// ---------------------------------------------------------------- his look (wardrobe)
// Hat, neckwear and fur are entity properties the resource pack shows; the collar is the vanilla
// wolf collar colour (minecraft:color). Saved on you, so a new Jerry (whistle, reset) keeps it.

const LOOK_START = { hat: 0, neck: 0, coat: 0, collar: 14 };
// Dyes (and the old dye items) -> collar colour.
const DYES = {
  white_dye: 0, bone_meal: 0, orange_dye: 1, magenta_dye: 2, light_blue_dye: 3, yellow_dye: 4,
  lime_dye: 5, pink_dye: 6, gray_dye: 7, light_gray_dye: 8, cyan_dye: 9, purple_dye: 10,
  blue_dye: 11, lapis_lazuli: 11, brown_dye: 12, cocoa_beans: 12, green_dye: 13, red_dye: 14,
  black_dye: 15, ink_sac: 15,
};
const dyeColour = (itemId) => DYES[String(itemId).replace("minecraft:", "")];

function getLook(player) {
  try {
    const raw = player.getDynamicProperty(PROP.look);
    if (typeof raw === "string") return { ...LOOK_START, ...JSON.parse(raw) };
  } catch {}
  return { ...LOOK_START };
}

function setLook(player, change) {
  const look = { ...getLook(player), ...change };
  player.setDynamicProperty(PROP.look, JSON.stringify(look));
  const dog = findDog(player);
  if (dog) applyLook(player, dog);
  const cat = player.getDynamicProperty(PROP.hasCat) ? findPet(player, PROP.cat, TAG.cat) : undefined;
  if (cat?.typeId === MOCHI) applyCatLook(cat, look);
  const pup = findPup(player);
  if (pup) applyPupLook(pup, look);
  return look;
}

// The puppy's coat (picked when he arrives) and collar; no outfits for the little one.
function applyPupLook(pup, look) {
  const props = { "jf:coat": look.pupCoat ?? 0, "jf:hat": 0, "jf:neck": 0 };
  for (const [key, value] of Object.entries(props)) {
    try {
      if (pup.getProperty(key) !== value) pup.setProperty(key, value);
    } catch {}
  }
  try {
    const collar = pup.getComponent("minecraft:color");
    const colour = look.pupCollar ?? 3;
    if (collar && collar.value !== colour) collar.value = colour;
  } catch {}
}

// Mochi's coat (any vanilla cat coat, tabby if she never had one) and collar.
function applyCatLook(cat, look) {
  const coat = look.catCoat ?? 8;
  try {
    if (cat.getProperty("jf:coat") !== coat) cat.setProperty("jf:coat", coat);
  } catch {}
  try {
    const collar = cat.getComponent("minecraft:color");
    const colour = look.catCollar ?? 14;
    if (collar && collar.value !== colour) collar.value = colour;
  } catch {}
}

function applyLook(player, dog, look = getLook(player)) {
  for (const key of ["hat", "neck", "coat"]) {
    try {
      if (dog.getProperty(`jf:${key}`) !== look[key]) dog.setProperty(`jf:${key}`, look[key]);
    } catch {}
  }
  try {
    const collar = dog.getComponent("minecraft:color");
    if (collar && collar.value !== look.collar) collar.value = look.collar;
  } catch {}
}

function dyeCollar(player, pet, colour) {
  const isCat = pet.typeId === MOCHI;
  setLook(player, isCat ? { catCollar: colour } : isPup(pet) ? { pupCollar: colour } : { collar: colour });
  sparkle(pet, "minecraft:villager_happy", 6);
  sound(pet.dimension, isCat ? "mob.cat.purr" : "mob.wolf.panting", pet.location);
  showFace(pet, "happy", 60);
  const text = isCat ? T.CAT_COLLAR_DYED : T.COLLAR_DYED.replace(/Jerry/g, isPup(pet) ? "Pip" : "Jerry");
  actionbar(player, text.replace("{colour}", T.COLLARS[colour].toLowerCase()));
}

function openWardrobe(player, retries = 3) {
  const look = getLook(player);
  const hasCat = !!player.getDynamicProperty(PROP.hasCat);
  const form = new ModalFormData()
    .title(personalize(player, T.WARDROBE_TITLE))
    .dropdown(T.WARDROBE_HAT, T.HATS, look.hat)
    .dropdown(T.WARDROBE_NECK, T.NECKS, look.neck)
    .dropdown(T.WARDROBE_COAT, T.COATS, look.coat)
    .dropdown(T.WARDROBE_COLLAR, T.COLLARS, look.collar);
  if (hasCat) {
    form
      .dropdown(personalize(player, T.WARDROBE_CAT_COAT), T.CAT_COATS, look.catCoat ?? 8)
      .dropdown(personalize(player, T.WARDROBE_CAT_COLLAR), T.COLLARS, look.catCollar ?? 14);
  }
  const hasPup = !!player.getDynamicProperty(PROP.hasPup);
  if (hasPup) form.dropdown(personalize(player, T.WARDROBE_PUP_COAT), T.COATS, look.pupCoat ?? 0);
  form
    .show(player)
    .then((response) => {
      if (response.canceled) {
        if (response.cancelationReason === "UserBusy" && retries > 0) {
          system.runTimeout(() => isValid(player) && openWardrobe(player, retries - 1), 10);
        }
        return;
      }
      const values = (response.formValues ?? []).map(Number);
      const [hat, neck, coat, collar] = values;
      const pickOr = (value, fallback) => (Number.isInteger(value) ? value : fallback);
      const change = { hat: hat || 0, neck: neck || 0, coat: coat || 0, collar: pickOr(collar, look.collar) };
      let next = 4;
      if (hasCat) {
        Object.assign(change, { catCoat: pickOr(values[next], look.catCoat ?? 8), catCollar: pickOr(values[next + 1], look.catCollar ?? 14) });
        next += 2;
      }
      if (hasPup) change.pupCoat = pickOr(values[next], look.pupCoat ?? 0);
      setLook(player, change);
      const dog = findDog(player);
      if (isValid(dog)) {
        spin(dog);
        sparkle(dog, "minecraft:villager_happy", 8);
        showFace(dog, "happy", 60);
      }
      tell(player, pick(T.LOOK_SAVED));
    })
    .catch((error) => console.warn(`[jf] wardrobe failed: ${error}`));
}

function removeAllPets(player) {
  for (const dimId of DIMENSIONS) {
    try {
      for (const pet of world.getDimension(dimId).getEntities({ tags: [ownerTag(player)] })) pet.remove();
    } catch {}
  }
}

function hop(pet, strength = 0.45) {
  try {
    pet.applyImpulse({ x: rand(-0.15, 0.15), y: strength, z: rand(-0.15, 0.15) });
  } catch {}
}

// dir 1 turns right (clockwise seen from above), -1 turns left.
function spin(pet, dir = 1) {
  for (let i = 1; i <= 12; i++) {
    system.runTimeout(() => {
      if (!isValid(pet)) return;
      try {
        const rotation = pet.getRotation();
        pet.teleport(pet.location, { rotation: { x: rotation.x, y: rotation.y + 30 * dir } });
      } catch {}
    }, i * 2);
  }
}

// ---------------------------------------------------------------- names

function cleanName(raw) {
  return String(raw ?? "")
    .replace(/§./g, "")
    .replace(/[^\p{L}\p{N} '\-]/gu, "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 20);
}

// Gives a pet a new name (from the naming box, a name tag or /scriptevent jf:name).
const NAMES = {
  dog: { prop: PROP.dogName, get: dogName, find: findDog, renamed: () => T.DOG_RENAMED },
  cat: { prop: PROP.catName, get: catName, find: findCat, renamed: () => T.CAT_RENAMED },
  pup: { prop: PROP.pupName, get: pupName, find: findPup, renamed: () => T.PUP_RENAMED },
};

function namePet(player, kind, raw, quiet = false) {
  const info = NAMES[kind] ?? NAMES.dog;
  const current = info.get(player);
  const pet = info.find(player);
  const name = cleanName(raw);
  if (!name || name === current) {
    if (pet && pet.nameTag !== current) pet.nameTag = current; // keep the tag in sync
    return false;
  }
  player.setDynamicProperty(info.prop, name);
  if (pet) {
    pet.nameTag = name;
    sparkle(pet);
    showFace(pet, "love", 60);
  }
  if (!quiet) tell(player, info.renamed());
  return true;
}

// The naming box: shown when you first get your dog, and on sneak + Dog Whistle.
function openNaming(player, intro = false, retries = 3, done) {
  const hasCat = !intro && !!player.getDynamicProperty(PROP.hasCat);
  const hasPup = !intro && !!player.getDynamicProperty(PROP.hasPup);
  const form = new ModalFormData()
    .title(personalize(player, intro ? T.NAME_TITLE_NEW : T.NAME_TITLE))
    .textField(intro ? T.NAME_NEW_DOG : T.NAME_DOG, DEFAULT_DOG_NAME, dogName(player));
  if (hasCat) form.textField(T.NAME_CAT, DEFAULT_CAT_NAME, catName(player));
  if (hasPup) form.textField(T.NAME_PUP_FIELD, DEFAULT_PUP_NAME, pupName(player));
  form
    .show(player)
    .then((response) => {
      if (response.canceled) {
        if (response.cancelationReason === "UserBusy" && retries > 0) {
          system.runTimeout(() => isValid(player) && openNaming(player, intro, retries - 1, done), 20);
          return;
        }
      } else {
        namePet(player, "dog", response.formValues?.[0], intro);
        if (hasCat) namePet(player, "cat", response.formValues?.[1]);
        if (hasPup) namePet(player, "pup", response.formValues?.[hasCat ? 2 : 1]);
      }
      done?.();
    })
    .catch(() => done?.());
}

// ---------------------------------------------------------------- start & cat

function scheduleIntro(player, delay) {
  if (pendingIntro.has(player.id)) return;
  pendingIntro.add(player.id);
  system.runTimeout(() => {
    pendingIntro.delete(player.id);
    if (isValid(player) && !player.getDynamicProperty(PROP.started)) startIntro(player);
  }, delay);
}

function startIntro(player) {
  removeAllPets(player);
  spawnJerry(player);
  player.setDynamicProperty(PROP.started, true);
  giveAllItems(player);
  title(player, T.TITLE, "§fYour very good dog");
  sound(player.dimension, "mob.wolf.bark", player.location);
  // First thing: pick a name (it can be changed any time). Then the welcome messages.
  openNaming(player, true, 10, () => isValid(player) && say(player, T.INTRO, 80));
  nextMoment.set(player.id, seconds + 30);
}

function scheduleCat(player) {
  if (pendingCat.has(player.id)) return;
  pendingCat.add(player.id);
  system.runTimeout(() => {
    pendingCat.delete(player.id);
    if (!isValid(player) || player.getDynamicProperty(PROP.hasCat)) return;
    player.setDynamicProperty(PROP.hasCat, true);
    const mochi = spawnMochi(player);
    const dog = findDog(player);
    if (dog) {
      showFace(dog, "shocked", 40); // a cat?!
      system.runTimeout(() => isValid(dog) && showFace(dog, "happy", 80), 40);
    }
    sound(player.dimension, "mob.cat.meow", player.location);
    sparkle(mochi);
    title(player, "§dA new friend!", "§fMeet Mochi");
    say(player, T.CAT_ARRIVES, 70);
  }, 200);
}

function schedulePup(player) {
  if (pendingPup.has(player.id)) return;
  pendingPup.add(player.id);
  system.runTimeout(() => {
    pendingPup.delete(player.id);
    if (!isValid(player) || player.getDynamicProperty(PROP.hasPup)) return;
    player.setDynamicProperty(PROP.hasPup, true);
    setLook(player, { pupCoat: Math.floor(Math.random() * T.COATS.length) });
    const pup = spawnPup(player);
    const dog = findDog(player);
    if (dog) {
      showFace(dog, "love", 100);
      sound(dog.dimension, "mob.wolf.bark", dog.location);
    }
    sparkle(pup);
    showFace(pup, "puppy", 100);
    title(player, "§eA puppy!", "§fSomeone small joined the family");
    say(player, T.PUP_ARRIVES, 70);
    // Name him (the box waits until the messages are done).
    system.runTimeout(() => {
      if (!isValid(player)) return;
      new ModalFormData()
        .title(personalize(player, T.NAME_PUP_TITLE))
        .textField(T.NAME_PUP, DEFAULT_PUP_NAME, pupName(player))
        .show(player)
        .then((response) => {
          if (!response.canceled) namePet(player, "pup", response.formValues?.[0]);
        })
        .catch(() => {});
    }, 160);
  }, 300);
}

// ---------------------------------------------------------------- every second

function playMoment(player, dog, cat) {
  // A dog that needs something lets you know about it.
  const mood = moodOf(getNeeds(player));
  if (mood !== "happy" && Math.random() < 0.6) {
    const moment = pick(T.MOOD_MOMENTS[mood]);
    sound(dog.dimension, moment.sound, dog.location);
    if (moment.action === "tilt") tiltHead(dog);
    if (moment.action === "hop") hop(dog);
    if (moment.action === "bone") holdBone(dog, 80);
    if (moment.action === "nap") {
      if (isSitting(dog)) return; // already resting
      if (goToBed(player, dog)) return;
      sit(dog, POSE.lie);
    }
    showFace(dog, moment.face, moment.action === "nap" ? 400 : 70);
    return actionbar(player, moment.text);
  }
  // Mochi and the puppy do their own thing sometimes.
  if (isNear(cat, player, 16) && Math.random() < 0.3) return catMoment(player, cat);
  const pup = findPup(player);
  if (isNear(pup, player, 16) && Math.random() < 0.3) return pupMoment(player, pup, dog);
  const options = [...T.MOMENTS];
  if (isNight()) options.push(...T.NIGHT_MOMENTS);
  if (isNear(cat, player, 16)) options.push(...T.CAT_MOMENTS);
  const moment = pick(options);
  const source = T.CAT_MOMENTS.includes(moment) ? cat : dog;
  sound(source.dimension, moment.sound, source.location);
  if (moment.action === "hop") hop(dog);
  if (moment.action === "roll" && !isSitting(dog)) rollOver(dog);
  if (moment.action === "tilt") tiltHead(dog);
  if (moment.face) showFace(dog, moment.face, 70);
  actionbar(player, moment.text);
}

function catMoment(player, cat) {
  const moment = pick(T.MOCHI_MOMENTS);
  sound(cat.dimension, moment.sound, cat.location);
  if (moment.action === "groom") trick(cat, POSE.groom, 70);
  if (moment.action === "stretch") trick(cat, POSE.stretch, 50);
  if (moment.action === "tilt") tiltHead(cat);
  if (moment.action === "zoomies") catZoomies(cat);
  if (moment.action === "lie" && !isSitting(cat)) trick(cat, POSE.lie, 200);
  showFace(cat, moment.face, moment.action === "lie" ? 200 : 70);
  actionbar(player, moment.text);
}

function bringGift(player, dog) {
  // Sometimes it's Mochi with a present instead.
  const cat = findCat(player);
  if (isNear(cat, player, 16) && Math.random() < 0.35) {
    const gift = pick(T.CAT_GIFTS);
    showFace(cat, "happy", 80);
    try {
      cat.dimension.spawnItem(new ItemStack(gift.item, 1), cat.location);
    } catch {}
    sound(cat.dimension, "mob.cat.purreow", cat.location);
    sparkle(cat, "minecraft:villager_happy", 6);
    return tell(player, `§dMochi§7 brought you a gift: §f${gift.name}§7! She expects praise.`);
  }
  const gift = pick(T.GIFTS);
  showFace(dog, "happy", 80);
  // A bone gets carried over in his mouth first, like in the video.
  const carry = gift.item === "minecraft:bone" ? 40 : 0;
  if (carry) holdBone(dog, carry);
  system.runTimeout(() => {
    if (!isValid(dog)) return;
    try {
      dog.dimension.spawnItem(new ItemStack(gift.item, 1), dog.location);
    } catch {}
  }, carry);
  sound(dog.dimension, "mob.wolf.bark", dog.location);
  sparkle(dog, "minecraft:villager_happy", 6);
  tell(player, `§bJerry§7 brought you a gift: §f${gift.name}§7! He's very proud.`);
}

function protectAll(player) {
  const dog = findDog(player);
  if (dog) {
    protect(dog);
    applyLook(player, dog); // keeps his outfit and collar right
    // Once per session: refresh his behaviors (worlds from before "defend you" moved to its own group).
    if (!refreshed.has(dog.id) && !isSitting(dog) && !fetching.has(dog.id) && !errands.has(dog.id)) {
      refreshed.add(dog.id);
      try {
        dog.triggerEvent("jf:stand");
      } catch {}
    }
  }
  const pup = findPup(player);
  if (pup) {
    protect(pup);
    applyPupLook(pup, getLook(player));
  }
  const cat = findCat(player);
  if (cat) {
    protect(cat);
    applyCatLook(cat, getLook(player));
  }
}

function tick(player) {
  if (!player.getDynamicProperty(PROP.started)) return scheduleIntro(player, 60);
  if (seconds % 10 === 0) protectAll(player);

  const friendship = getNum(player, PROP.friendship);
  if (levelOf(friendship) >= CAT_LEVEL && !player.getDynamicProperty(PROP.hasCat)) scheduleCat(player);
  if (levelOf(friendship) >= PUP_LEVEL && !player.getDynamicProperty(PROP.hasPup)) schedulePup(player);

  const dog = findDog(player);
  // Renamed with a name tag?
  if (dog && dog.nameTag && dog.nameTag !== dogName(player)) namePet(player, "dog", dog.nameTag);
  const cat = findCat(player);
  if (cat && cat.nameTag && cat.nameTag !== catName(player)) namePet(player, "cat", cat.nameTag);
  const pup = findPup(player);
  if (pup && pup.nameTag && pup.nameTag !== pupName(player)) namePet(player, "pup", pup.nameTag);

  if (dog) reactToSurroundings(player, dog);
  if (!isNear(dog, player, 16)) return;
  const busy = fetching.has(dog.id) || isFighting(dog); // no naps or gifts in the middle of a game or a fight
  if (seconds % 60 === 0) {
    updateNeeds(player, dog);
    // A happy dog makes friends faster.
    addFriendship(player, moodOf(getNeeds(player)) === "happy" ? GAIN.together * 2 : GAIN.together);
  }

  if (busy || errands.has(dog.id)) return;
  // Hungry and there's a full bowl? Sleepy and there's his bed? Off he goes.
  if (seconds % 5 === 0 && !isSitting(dog)) {
    const needs = getNeeds(player);
    if (needs.hunger < 40 && canReact(player, "bowl", 20) && goEat(player, dog)) return;
    if ((needs.energy < 30 || (isNight() && needs.energy < 80)) && canReact(player, "bedtrip", 60) && goToBed(player, dog)) return;
  }
  if (levelOf(friendship) >= GIFT_LEVEL) {
    if (!nextGift.has(player.id)) nextGift.set(player.id, seconds + 120);
    if (seconds >= nextGift.get(player.id)) {
      nextGift.set(player.id, seconds + Math.floor(rand(180, 300)));
      return bringGift(player, dog);
    }
  }

  if (seconds < (nextMoment.get(player.id) ?? 0)) return;
  nextMoment.set(player.id, seconds + Math.floor(rand(35, 60)));
  playMoment(player, dog, findCat(player));
}

// ---------------------------------------------------------------- item actions

function lookTarget(player) {
  try {
    for (const hit of player.getEntitiesFromViewDirection({ maxDistance: 12 })) {
      if (hit.entity.id !== player.id) return hit.entity;
    }
  } catch {}
  return undefined;
}

// Which of the player's pets (if any) is this?
function petKind(player, entity) {
  if (!isValid(entity) || !entity.hasTag(ownerTag(player))) return undefined;
  if (entity.hasTag(TAG.dog)) return "dog";
  if (entity.hasTag(TAG.cat)) return "cat";
  if (entity.hasTag(TAG.pup)) return "pup";
  return undefined;
}

function whistle(player) {
  sound(player.dimension, "note.flute", player.location);
  const dog = findDog(player);
  if (dog) {
    if (isSitting(dog)) stand(dog);
    bring(dog, player);
  } else spawnJerry(player);
  let text = "§bJerry";
  if (player.getDynamicProperty(PROP.hasPup)) {
    const pup = findPup(player);
    if (pup) {
      if (isSitting(pup)) stand(pup);
      bring(pup, player);
    } else spawnPup(player);
    text += "§7, §ePip";
  }
  if (player.getDynamicProperty(PROP.hasCat)) {
    const cat = findCat(player);
    if (cat) bring(cat, player);
    else spawnMochi(player);
    text += " §7and §dMochi";
  }
  actionbar(player, `${text} §7came running!  ${friendshipBar(player)}`);
}

function giveTreat(player, target) {
  const kind = petKind(player, target);
  if (!kind) return actionbar(player, T.TREAT_OTHER);
  sparkle(target);
  if (kind === "cat") {
    sound(target.dimension, "mob.cat.purr", target.location);
    showFace(target, "love", 60);
    return actionbar(player, T.TREAT_CAT);
  }
  if (kind === "pup") {
    sound(target.dimension, "mob.wolf.bark", target.location, { pitch: 1.5 });
    showFace(target, "love", 60);
    return actionbar(player, T.TREAT_PUP);
  }
  sound(target.dimension, "mob.wolf.bark", target.location);
  showFace(target, "love", 60);
  changeNeeds(player, { hunger: 30, fun: 2 });
  const earned = earn(player, "treat");
  actionbar(player, `${pick(T.TREAT)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
}

function brush(player, target) {
  const kind = petKind(player, target);
  if (!kind) return actionbar(player, T.BRUSH_OTHER);
  sparkle(target, "minecraft:villager_happy", 8);
  if (kind === "cat") {
    sound(target.dimension, "mob.cat.purr", target.location);
    showFace(target, "happy", 60);
    return actionbar(player, T.BRUSH_CAT);
  }
  if (kind === "pup") {
    showFace(target, "happy", 60);
    return actionbar(player, T.BRUSH_PUP);
  }
  sound(target.dimension, "mob.wolf.panting", target.location);
  showFace(target, "happy", 60);
  changeNeeds(player, { fun: 5 });
  const earned = earn(player, "brush");
  actionbar(player, `${pick(T.BRUSH)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
}

function play(player) {
  const dog = findDog(player);
  if (!isNear(dog, player, 16)) return actionbar(player, T.TOO_FAR);
  for (let i = 0; i < 4; i++) {
    system.runTimeout(() => {
      if (!isValid(dog)) return;
      hop(dog);
      if (i % 2 === 0) sound(dog.dimension, "mob.wolf.bark", dog.location);
    }, i * 12);
  }
  spin(dog);
  holdBone(dog, 70);
  showFace(dog, "happy", 70);
  let text = pick(T.PLAY);
  const cat = findCat(player);
  if (isNear(cat, player, 16)) {
    hop(cat, 0.4);
    sound(cat.dimension, "mob.cat.meow", cat.location);
    text += T.PLAY_CAT;
  }
  const earned = earn(player, "ball");
  actionbar(player, `${text}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
}

function useItem(player, itemId, target) {
  // Tapping a pet can fire both the interact and the item-use event.
  const now = system.currentTick;
  if (now - (lastUse.get(player.id) ?? -100) < 8) return;
  lastUse.set(player.id, now);

  if (itemId === ITEMS.whistle) return player.isSneaking ? openStatus(player) : whistle(player);
  if (itemId === ITEMS.talk) return startTalking(player);
  if (itemId === ITEMS.ball) return throwBall(player);
  target = isValid(target) ? target : lookTarget(player);
  if (itemId === ITEMS.treat) return giveTreat(player, target);
  if (itemId === ITEMS.brush) return brush(player, target);
}

function petMochi(player, cat) {
  sparkle(cat);
  sound(cat.dimension, "mob.cat.purr", cat.location);
  tiltHead(cat);
  showFace(cat, "happy", 50);
  const earned = earn(player, "pet");
  actionbar(player, `${pick(T.PET_CAT)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
}

function petPup(player, pup) {
  sparkle(pup);
  sound(pup.dimension, "mob.wolf.panting", pup.location, { pitch: 1.5 });
  hop(pup, 0.3);
  showFace(pup, "happy", 50);
  const earned = earn(player, "pet");
  actionbar(player, `${pick(T.PET_PUP)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
}

function petJerry(player, dog) {
  sparkle(dog);
  sound(dog.dimension, "mob.wolf.panting", dog.location);
  tiltHead(dog);
  showFace(dog, "happy", 50);
  changeNeeds(player, { fun: 5 });
  const earned = earn(player, "pet");
  actionbar(player, `${pick(T.PET)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
}

// ---------------------------------------------------------------- needs & mood
// Hunger, energy and fun slowly go down. Treats, naps and play bring them back. His mood
// (the lowest need) changes his face and what he does. Nothing bad ever happens to him.

const NEEDS_START = { hunger: 80, energy: 80, fun: 80 };

function getNeeds(player) {
  try {
    const raw = player.getDynamicProperty(PROP.needs);
    if (typeof raw === "string") return { ...NEEDS_START, ...JSON.parse(raw) };
  } catch {}
  return { ...NEEDS_START };
}

function changeNeeds(player, delta) {
  const needs = getNeeds(player);
  for (const key of Object.keys(delta)) needs[key] = Math.max(0, Math.min(100, Math.round(needs[key] + delta[key])));
  player.setDynamicProperty(PROP.needs, JSON.stringify(needs));
  return needs;
}

function moodOf(needs) {
  if (needs.hunger < 30) return "hungry";
  if (needs.energy < 25) return "tired";
  if (needs.fun < 30) return "bored";
  return "happy";
}

// Once a minute while he's with you. Resting (sitting or lying down) recharges him.
function updateNeeds(player, dog) {
  const resting = isSitting(dog);
  const inBed = resting && dog.getDynamicProperty("jf:on_bed") === true;
  const needs = changeNeeds(player, {
    hunger: -3,
    fun: resting ? -1 : -3,
    energy: inBed ? 16 : resting ? 8 : isNight() ? -4 : -2,
  });
  // A good nap in his own bed: he gets up by himself in the daytime once he's rested.
  if (inBed && needs.energy >= 95 && !isNight() && !player.isSleeping) {
    stand(dog);
    showFace(dog, "happy", 60);
    tell(player, T.WOKE_UP);
  }
}

// ---------------------------------------------------------------- practice makes perfect

const PRACTICE = new Set([
  "paw", "highfive", "wave", "beg", "roll", "dead", "bow", "dance", "backflip", "howl", "chase", "spin", "lie", "hop",
  "find", "shy", "stretch", "shakeoff", "scratch", "sneeze", "catch", "backup",
]);
// The tricks Mochi knows (practiced like Jerry's). Anything else gets cat sass.
const CAT_PRACTICE = new Set(["paw", "highfive", "wave", "beg", "roll", "dead", "spin", "lie", "hop", "shy", "stretch", "chase", "groom", "catch"]);
const CAT_ALWAYS = new Set([
  "love", "sit", "come", "greet", "bark", "spinleft", "spinright", "find", "excited", "yawn", "shock", "play", "mochi", "hug", "sleep", "sneeze",
  "scratch", "shakeoff", "listen",
]);
const MASTER_AT = 5; // successful tries to master a trick

function getSkills(player) {
  try {
    const raw = player.getDynamicProperty(PROP.skills);
    if (typeof raw === "string") return JSON.parse(raw);
  } catch {}
  return {};
}

// Returns true if the pet pulls the trick off. Early on they sometimes do something else instead.
function attemptTrick(player, pet, action) {
  const isCat = pet.typeId === MOCHI;
  const key = isCat ? `cat:${action}` : action;
  const skills = getSkills(player);
  const done = skills[key] ?? 0;
  if (done >= MASTER_AT) return true;
  const trickName = T.TRICK_NAMES[action] ?? action;
  if (Math.random() > 0.6 + done * 0.08) {
    const oops = pick(isCat ? T.CAT_OOPS : T.OOPS);
    if (oops.action === "spin") spin(pet);
    else if (oops.action === "tilt") tiltHead(pet);
    else if (oops.action === "sit") sit(pet);
    else if (oops.action === "groom") trick(pet, POSE.groom, 50);
    else hop(pet, 0.45);
    showFace(pet, "puppy", 50);
    sound(pet.dimension, isCat ? "mob.cat.meow" : "mob.wolf.whine", pet.location);
    tell(player, oops.text.replace("{trick}", trickName));
    return false;
  }
  skills[key] = done + 1;
  player.setDynamicProperty(PROP.skills, JSON.stringify(skills));
  if (skills[key] === MASTER_AT) {
    system.runTimeout(() => {
      if (!isValid(player)) return;
      tell(player, (isCat ? T.CAT_MASTERED : T.MASTERED).replace("{trick}", trickName));
      sound(player.dimension, "random.levelup", player.location);
      if (isValid(pet)) showFace(pet, "love", 60);
      addFriendship(player, 3);
    }, 30);
  }
  return true;
}

// ---------------------------------------------------------------- fetch
// The Tennis Ball really gets thrown: he runs after it, picks it up and brings it back.

// He runs with Minecraft's own pathfinding: his jf:fetch_chase_N behavior goes after his ball and
// jf:fetch_return follows you back (see entities/jerry.json). The script just watches. If he gets
// stuck (or the behaviors aren't working), it gently steers him instead.

const flat = (a, b) => Math.hypot(a.x - b.x, a.z - b.z);

// Fallback steering: eases his speed toward the target (no sudden snaps) and faces where he's going.
function runToward(dog, target, speed = 0.25) {
  const p = dog.location;
  const dx = target.x - p.x;
  const dz = target.z - p.z;
  const d = Math.hypot(dx, dz);
  if (d < 0.05) return d;
  const v = dog.getVelocity();
  // Hop up single blocks in the way.
  let jump = 0;
  try {
    const ahead = dog.dimension.getBlock({ x: Math.floor(p.x + (dx / d) * 0.8), y: Math.floor(p.y), z: Math.floor(p.z + (dz / d) * 0.8) });
    if (ahead && !ahead.isAir && !ahead.isLiquid && dog.isOnGround) jump = 0.42;
  } catch {}
  const ease = 0.35;
  dog.applyImpulse({ x: ((dx / d) * speed - v.x) * ease, y: jump, z: ((dz / d) * speed - v.z) * ease });
  try {
    dog.setRotation({ x: 0, y: (Math.atan2(-dx, dz) * 180) / Math.PI });
  } catch {}
  return d;
}

function setBall(dog, on) {
  try {
    dog.setProperty("jf:ball", on);
  } catch {}
}

// You only ever keep one ball in your inventory.
function keepOneBall(player) {
  const inv = inventory(player);
  if (!inv) return;
  let seen = false;
  for (let i = 0; i < inv.size; i++) {
    if (inv.getItem(i)?.typeId !== ITEMS.ball) continue;
    if (seen) inv.setItem(i, undefined);
    seen = true;
  }
}

// The thrown ball. Older worlds (or if the ball entity is missing) fall back to a dropped item.
function spawnBall(player) {
  const head = player.getHeadLocation();
  const dir = player.getViewDirection();
  const at = { x: head.x + dir.x * 0.6, y: head.y + dir.y * 0.6 - 0.2, z: head.z + dir.z * 0.6 };
  try {
    const ball = player.dimension.spawnEntity(BALL, at);
    ball.applyImpulse({ x: dir.x * 0.85, y: dir.y * 0.6 + 0.25, z: dir.z * 0.85 });
    return ball;
  } catch {}
  const ball = player.dimension.spawnItem(new ItemStack(ITEMS.ball, 1), { x: head.x + dir.x * 1.5, y: head.y + dir.y * 1.5, z: head.z + dir.z * 1.5 });
  ball.applyImpulse({ x: dir.x * 0.7, y: dir.y * 0.6 + 0.25, z: dir.z * 0.7 });
  return ball;
}

const activeBalls = () => new Set([...fetching.values()].map((game) => game.ballId));

// Thrown balls left over from a game that got cut off (world closed mid-fetch) are cleaned up.
function removeStrayBalls() {
  const keep = activeBalls();
  for (const id of DIMENSIONS) {
    try {
      for (const ball of world.getDimension(id).getEntities({ type: BALL })) {
        if (ball.typeId === BALL && !keep.has(ball.id)) ball.remove();
      }
    } catch {}
  }
}

function throwBall(player) {
  const dog = findDog(player);
  if (!isNear(dog, player, 24)) return actionbar(player, T.TOO_FAR);
  if (fetching.has(dog.id)) return actionbar(player, T.FETCH_BUSY);

  let ball;
  try {
    ball = spawnBall(player);
  } catch {
    return play(player); // couldn't throw it; just play instead
  }
  if (isSitting(dog)) stand(dog);
  fighting.delete(dog.id); // playing beats fighting
  showFace(dog, "happy", 200);
  sound(dog.dimension, "mob.wolf.bark", dog.location);
  actionbar(player, T.FETCH_THROW);

  // Each game gets a numbered ball (tag jf_ball_N) and his matching behavior only chases that
  // one, so two dogs never run off with each other's ball. If every number is taken, he steers.
  const used = new Set([...fetching.values()].map((g) => g.slot));
  const slot = [...Array(FETCH_SLOTS).keys()].find((n) => !used.has(n));
  try {
    if (slot !== undefined) ball.addTag(`jf_ball_${slot}`);
  } catch {}
  const chaseEvent = slot === undefined ? undefined : `jf:fetch_chase_${slot}`;
  const game = { phase: "chase", ticks: 0, ballId: ball.id, slot, event: chaseEvent, best: Infinity, bestAt: 0, steer: false };
  const setPhase = (phase, event) => {
    Object.assign(game, { phase, event, ticks: 0, best: Infinity, bestAt: 0, steer: !event });
    try {
      dog.triggerEvent(event ?? "jf:sit"); // no behavior to use: pause his follow brain and steer
    } catch {
      game.steer = true; // no fetch behaviors: steer him the whole way
    }
  };
  // Is he getting closer? If he hasn't for 1.5 seconds, help him along with steering.
  const watch = (d) => {
    if (d < game.best - 0.3) {
      game.best = d;
      game.bestAt = game.ticks;
    } else if (game.ticks - game.bestAt > 30) {
      game.steer = true;
    }
  };
  const finish = (result) => {
    system.clearRun(game.run);
    fetching.delete(dog.id);
    if (isValid(ball)) ball.remove();
    if (!isValid(dog)) return;
    setBall(dog, false);
    try {
      if (!isSitting(dog)) dog.triggerEvent("jf:stand");
    } catch {}
    if (!isValid(player)) return;
    keepOneBall(player);
    if (result === "stopped") return; // you told him to sit mid-game
    if (!result) return actionbar(player, T.FETCH_LOST);
    sparkle(dog);
    showFace(dog, "love", 60);
    sound(dog.dimension, "mob.wolf.bark", dog.location);
    changeNeeds(player, { fun: 30, energy: -6 });
    const earned = earn(player, "ball");
    actionbar(player, `${pick(T.FETCH_DONE)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
  };
  game.run = system.runInterval(() => {
    try {
      if (!isValid(dog) || !isValid(player)) return finish(false);
      if (isSitting(dog)) return finish("stopped");
      game.ticks++;
      if (game.phase === "chase") {
        if (!isValid(ball)) return finish(false); // (only a dropped-item ball can be picked up)
        if (ball.location.y < -60) return finish(false); // fell out of the world
        if (game.ticks < 8) return; // let it fly a moment first
        const target = ball.location;
        const d = flat(dog.location, target);
        watch(d);
        if (game.steer) runToward(dog, target, 0.26);
        if ((d < 1.6 && Math.abs(dog.location.y - target.y) < 1.5) || game.ticks > 240) {
          if (game.ticks > 240) dog.teleport(target);
          ball.remove();
          setBall(dog, true);
          sound(dog.dimension, "random.pop", dog.location);
          actionbar(player, T.FETCH_GOT_IT);
          setPhase("return", "jf:fetch_return");
        }
      } else {
        if (dog.dimension.id !== player.dimension.id) return finish(false);
        const d = flat(dog.location, player.location);
        watch(d);
        if (game.steer) runToward(dog, player.location, 0.24);
        if (d < 2.2 || game.ticks > 240) {
          if (game.ticks > 240) dog.teleport(player.location);
          finish(true);
        }
      }
    } catch (error) {
      console.warn(`[jf] fetch: ${error}`);
      finish(false);
    }
  }, 1);
  fetching.set(dog.id, game);
  setPhase("chase", chaseEvent);
}

// ---------------------------------------------------------------- defending you
// His "owner hurt" behavior does the actual chasing. The script remembers the fight so nothing
// else (like rushing back to check on you) pulls him away from it halfway through.

function isFighting(dog) {
  const fight = fighting.get(dog.id);
  if (!fight) return false;
  const foe = world.getEntity(fight.attackerId);
  if (system.currentTick > fight.until || !isValid(foe) || foe.dimension.id !== dog.dimension.id || dist(foe.location, dog.location) > 24) {
    fighting.delete(dog.id); // won, it ran off, or it's been a while: the fight's over
    return false;
  }
  return true;
}

// ---------------------------------------------------------------- dog bed & food bowl
// Placed beds and bowls are remembered (world property), so we know where to send him.

const HOMES = "jf:homes";

function getHomes() {
  try {
    const raw = world.getDynamicProperty(HOMES);
    if (typeof raw === "string") return JSON.parse(raw);
  } catch {}
  return [];
}

const sameSpot = (a, b) => a.d === b.d && a.x === b.x && a.y === b.y && a.z === b.z;

function saveHomes(list) {
  world.setDynamicProperty(HOMES, JSON.stringify(list.slice(-200)));
}

function rememberHome(block) {
  const spot = { d: block.dimension.id, x: block.location.x, y: block.location.y, z: block.location.z };
  saveHomes([...getHomes().filter((h) => !sameSpot(h, spot)), spot]);
}

function forgetHome(dimId, location) {
  const spot = { d: dimId, x: location.x, y: location.y, z: location.z };
  saveHomes(getHomes().filter((h) => !sameSpot(h, spot)));
}

// The nearest placed block of one of these types within range of the dog (gone ones are forgotten).
function nearestHome(dog, types, range = 16) {
  let best;
  let bestDist = range;
  for (const spot of getHomes()) {
    if (spot.d !== dog.dimension.id) continue;
    const centre = { x: spot.x + 0.5, y: spot.y, z: spot.z + 0.5 };
    const d = dist(centre, dog.location);
    if (d > bestDist) continue;
    let block;
    try {
      block = dog.dimension.getBlock(spot);
    } catch {}
    if (!block) continue; // not loaded right now
    if (![BLOCKS.bed, BLOCKS.bowl, BLOCKS.fullBowl].includes(block.typeId)) {
      forgetHome(spot.d, spot); // broken by something else (explosion, piston...)
      continue;
    }
    if (!types.includes(block.typeId)) continue;
    best = { block, centre };
    bestDist = d;
  }
  return best;
}

function fillBowl(player, block, itemId) {
  if (block.typeId === BLOCKS.fullBowl) return actionbar(player, T.BOWL_FULL_ALREADY);
  if (!FOODS.has(itemId)) return actionbar(player, T.BOWL_HOW);
  // Meat gets used up (treats come from your endless treat bag).
  if (itemId !== ITEMS.treat) {
    try {
      if (player.getGameMode() !== "creative") {
        const inv = inventory(player);
        const stack = inv?.getItem(player.selectedSlotIndex);
        if (stack?.typeId === itemId) {
          if (stack.amount > 1) {
            stack.amount -= 1;
            inv.setItem(player.selectedSlotIndex, stack);
          } else inv.setItem(player.selectedSlotIndex, undefined);
        }
      }
    } catch {}
  }
  block.setType(BLOCKS.fullBowl);
  sound(block.dimension, "random.pop", block.location);
  particle(block.dimension, "minecraft:villager_happy", { x: block.location.x + 0.5, y: block.location.y + 0.4, z: block.location.z + 0.5 });
  actionbar(player, T.BOWL_FILLED);
}

// A trip with his own pathfinding (jf:go_eat / jf:go_bed), steering if he gets stuck, then
// arrive() runs there. Anything else he gets asked to do (sit, fetch, a fight) cancels it.
function startErrand(player, dog, event, spot, arrive) {
  if (errands.has(dog.id)) return;
  const errand = { event, ticks: 0, best: Infinity, bestAt: 0, steer: false };
  errands.set(dog.id, errand);
  try {
    dog.triggerEvent(event);
  } catch {
    errand.steer = true;
  }
  const done = (ok) => {
    system.clearRun(errand.run);
    errands.delete(dog.id);
    if (!isValid(dog)) return;
    if (!ok) {
      if (!isSitting(dog) && !fetching.has(dog.id)) {
        try {
          dog.triggerEvent("jf:stand");
        } catch {}
      }
      return;
    }
    arrive();
  };
  errand.run = system.runInterval(() => {
    try {
      if (!isValid(dog) || !isValid(player)) return done(false);
      if (errand.cancel || isSitting(dog) || fetching.has(dog.id) || isFighting(dog) || dog.dimension.id !== spot.dimension) return done(false);
      errand.ticks += 2;
      const d = flat(dog.location, spot.centre);
      if (d < errand.best - 0.3) {
        errand.best = d;
        errand.bestAt = errand.ticks;
      } else if (errand.ticks - errand.bestAt > 40) errand.steer = true;
      if (errand.steer) runToward(dog, spot.centre, 0.22);
      if (d < 1.3) return done(true);
      if (errand.ticks > 400) {
        dog.teleport({ ...spot.centre, y: spot.centre.y + 0.3 });
        return done(true);
      }
    } catch (error) {
      console.warn(`[jf] errand: ${error}`);
      done(false);
    }
  }, 2);
}

function goEat(player, dog) {
  const bowl = nearestHome(dog, [BLOCKS.fullBowl]);
  if (!bowl) return false;
  actionbar(player, T.GOING_TO_EAT);
  showFace(dog, "happy", 60);
  startErrand(player, dog, "jf:go_eat", { dimension: dog.dimension.id, centre: bowl.centre }, () => {
    let block;
    try {
      block = dog.dimension.getBlock(bowl.block.location);
    } catch {}
    try {
      dog.triggerEvent("jf:stand");
    } catch {}
    if (block?.typeId !== BLOCKS.fullBowl) return; // someone got there first
    block.setType(BLOCKS.bowl);
    for (let i = 0; i < 4; i++) system.runTimeout(() => isValid(dog) && sound(dog.dimension, "random.eat", dog.location), i * 6);
    sparkle(dog);
    showFace(dog, "love", 60);
    changeNeeds(player, { hunger: 45 });
    if (isValid(player)) tell(player, pick(T.ATE));
  });
  return true;
}

function goToBed(player, dog, quiet = false) {
  const bed = nearestHome(dog, [BLOCKS.bed], 24);
  if (!bed) return false;
  if (!quiet) actionbar(player, T.GOING_TO_BED);
  showFace(dog, "sleepy", 100);
  startErrand(player, dog, "jf:go_bed", { dimension: dog.dimension.id, centre: bed.centre }, () => {
    try {
      dog.teleport({ ...bed.centre, y: bed.block.location.y + 0.2 });
    } catch {}
    spin(dog); // three little turns before lying down
    system.runTimeout(() => {
      if (!isValid(dog) || errands.has(dog.id) || fetching.has(dog.id)) return;
      sit(dog, POSE.lie);
      dog.setDynamicProperty("jf:on_bed", true);
      showFace(dog, "sleepy", 600);
      if (!quiet && isValid(player)) tell(player, T.ON_BED);
    }, 26);
  });
  return true;
}

// ---------------------------------------------------------------- smart reactions

function canReact(player, kind, cooldown) {
  const key = `${player.id}:${kind}`;
  if (seconds - (reactedAt.get(key) ?? -9999) < cooldown) return false;
  reactedAt.set(key, seconds);
  return true;
}

const prettyMob = (typeId) => typeId.replace("minecraft:", "").replace(/_/g, " ");

function reactToSurroundings(player, dog) {
  const near = isNear(dog, player, 24);
  // Welcome home: you were apart for a while and now you're back together.
  if (near) {
    const since = awaySince.get(player.id);
    awaySince.delete(player.id);
    if (since !== undefined && seconds - since >= 90 && !fetching.has(dog.id)) {
      play(player);
      return actionbar(player, T.WELCOME_HOME);
    }
  } else if (!awaySince.has(player.id)) {
    awaySince.set(player.id, seconds);
  }
  if (!near || fetching.has(dog.id)) return;

  // Bedtime: he naps by your bed (and it recharges him).
  try {
    if (player.isSleeping && canReact(player, "bed", 300)) {
      if (!goToBed(player, dog, true)) sit(dog, POSE.lie);
      showFace(dog, "sleepy", 400);
      changeNeeds(player, { energy: 40 });
      const pup = findPup(player);
      let pupText = "";
      if (isNear(pup, player, 24)) {
        sit(pup, POSE.lie);
        showFace(pup, "sleepy", 400);
        pupText = T.BEDTIME_PUP;
      }
      const cat = findCat(player);
      if (!isNear(cat, player, 24)) return actionbar(player, T.BEDTIME + pupText);
      sit(cat, POSE.lie);
      showFace(cat, "sleepy", 400);
      return actionbar(player, T.BEDTIME_CAT + pupText);
    }
  } catch {}

  // Guard dog: warns you about monsters sneaking up.
  if (seconds % 3 === 0) {
    let monster;
    try {
      monster = player.dimension.getEntities({ location: player.location, maxDistance: 12, families: ["monster"] })[0];
    } catch {}
    if (monster && canReact(player, "guard", 30)) {
      showFace(dog, "shocked", 50);
      sound(dog.dimension, "mob.wolf.growl", dog.location);
      system.runTimeout(() => isValid(dog) && sound(dog.dimension, "mob.wolf.bark", dog.location), 10);
      try {
        const m = monster.location;
        dog.setRotation({ x: 0, y: (Math.atan2(-(m.x - dog.location.x), m.z - dog.location.z) * 180) / Math.PI });
      } catch {}
      let text = T.GUARD.replace("{mob}", prettyMob(monster.typeId));
      const cat = findCat(player);
      if (isNear(cat, player, 16)) {
        sound(cat.dimension, "mob.cat.hiss", cat.location);
        showFace(cat, "shocked", 50);
        text += T.GUARD_CAT;
      }
      actionbar(player, text);
    }
  }
}

world.afterEvents.entityHurt.subscribe(
  ({ hurtEntity, damageSource }) => {
    const player = world.getAllPlayers().find((p) => p.id === hurtEntity.id);
    if (!player || !player.getDynamicProperty(PROP.started)) return;
    const dog = findDog(player);
    if (!isNear(dog, player, 32) || fetching.has(dog.id)) return;
    // Something attacked you: he goes after it (his "owner hurt" behavior does the chasing).
    const attacker = damageSource?.damagingEntity;
    if (attacker && attacker.id !== dog.id && attacker.id !== player.id && !isSitting(dog)) {
      fighting.set(dog.id, { attackerId: attacker.id, until: system.currentTick + 20 * 15 });
      showFace(dog, "shocked", 40);
      sound(dog.dimension, "mob.wolf.growl", dog.location);
      if (canReact(player, "defend", 10)) actionbar(player, T.DEFEND.replace("{mob}", prettyMob(attacker.typeId)));
      return;
    }
    if (isFighting(dog) || !canReact(player, "hurt", 15)) return; // busy protecting you
    if (!isSitting(dog) && !isNear(dog, player, 4)) bring(dog, player);
    showFace(dog, "puppy", 80);
    tiltHead(dog);
    sound(dog.dimension, "mob.wolf.whine", dog.location);
    actionbar(player, T.WORRIED);
  },
  { entityTypes: ["minecraft:player"] }
);

world.afterEvents.itemCompleteUse.subscribe(({ source, itemStack }) => {
  if (source?.typeId !== "minecraft:player" || !itemStack || itemStack.typeId.includes("potion")) return;
  const player = world.getAllPlayers().find((p) => p.id === source.id);
  if (!player) return;
  const dog = findDog(player);
  if (!isNear(dog, player, 16) || !canReact(player, "food", 60)) return;
  tiltHead(dog);
  showFace(dog, "puppy", 80);
  sound(dog.dimension, "mob.wolf.whine", dog.location);
  actionbar(player, T.BEG_FOOD);
});

// ---------------------------------------------------------------- the status screen (sneak + Dog Whistle)

function meter(value) {
  const filled = Math.round(value / 10);
  return `${value >= 50 ? "§a" : value >= 25 ? "§e" : "§c"}${"|".repeat(filled)}§8${"|".repeat(10 - filled)}`;
}

function openStatus(player, retries = 3) {
  const needs = getNeeds(player);
  const skills = getSkills(player);
  const mastered = [...PRACTICE].filter((a) => (skills[a] ?? 0) >= MASTER_AT).length;
  const body = [
    `§fMood: ${T.MOOD_LABEL[moodOf(needs)]}`,
    `§fFull:     ${meter(needs.hunger)}`,
    `§fEnergy: ${meter(needs.energy)}`,
    `§fFun:      ${meter(needs.fun)}`,
    `§fFriendship: ${friendshipBar(player)}`,
    `§fTricks mastered: §e${mastered}/${PRACTICE.size}`,
  ];
  const family = ["§bJerry"];
  if (player.getDynamicProperty(PROP.hasCat)) family.push("§dMochi");
  if (player.getDynamicProperty(PROP.hasPup)) family.push("§ePip");
  if (family.length > 1) body.push(`§fFamily: ${family.join("§7, ")}`);
  if (player.getDynamicProperty(PROP.hasCat)) {
    const catMastered = [...CAT_PRACTICE].filter((a) => (skills[`cat:${a}`] ?? 0) >= MASTER_AT).length;
    body.push(`§dMochi§f's tricks mastered: §e${catMastered}/${CAT_PRACTICE.size}`);
  }
  const text = body.join("\n");
  new ActionFormData()
    .title(personalize(player, T.STATUS_TITLE))
    .body(personalize(player, text))
    .button(T.STATUS_TRICKS)
    .button(T.STATUS_WARDROBE)
    .button(T.STATUS_ITEMS)
    .button(T.STATUS_RENAME)
    .button(T.STATUS_CLOSE)
    .show(player)
    .then((response) => {
      if (response.canceled) {
        if (response.cancelationReason === "UserBusy" && retries > 0) {
          system.runTimeout(() => isValid(player) && openStatus(player, retries - 1), 10);
        }
        return;
      }
      if (response.selection === 0) openTricks(player);
      else if (response.selection === 1) openWardrobe(player);
      else if (response.selection === 2) {
        giveAllItems(player);
        tell(player, T.ITEMS_GIVEN);
      } else if (response.selection === 3) openNaming(player);
    })
    .catch((error) => console.warn(`[jf] status screen failed: ${error}`));
}

function openTricks(player) {
  const skills = getSkills(player);
  const lines = [...PRACTICE].map((action) => {
    const done = Math.min(MASTER_AT, skills[action] ?? 0);
    const stars = `§e${"*".repeat(done)}§8${"*".repeat(MASTER_AT - done)}`;
    return `${stars} §f${T.TRICK_NAMES[action]}${done >= MASTER_AT ? " §a(mastered!)" : ""}`;
  });
  lines.push("", "§7Plus: sit, come, speak, fetch, bone, play... always perfect.");
  new ActionFormData()
    .title(personalize(player, T.TRICKS_TITLE))
    .body(lines.join("\n"))
    .button(T.TRICKS_BACK)
    .show(player)
    .then((response) => {
      if (!response.canceled) openStatus(player);
    })
    .catch(() => {});
}

// ---------------------------------------------------------------- talking
// Bedrock add-ons can't use the microphone, so "voice" goes through a text box:
// on phones the keyboard's microphone button turns speech into text for us.

// ---------------------------------------------------------------- real voice (phone helper)
// Add-ons can't use the microphone, so the Jerry Voice helper app listens for us and sends
// what it heard back with "/scriptevent jf:voice <text>" over Minecraft's /connect.

const voiceConnected = () => seconds - voiceHelper.lastSeen < 15;

function startTalking(player) {
  if (!voiceConnected()) return openTalk(player);
  voiceHelper.lastListener = player.id;
  player.addTag(LISTEN_TAG);
  actionbar(player, T.VOICE_GET_READY);
  // If the helper doesn't pick it up quickly, it's gone; fall back to the text box.
  system.runTimeout(() => {
    if (!isValid(player) || !player.hasTag(LISTEN_TAG)) return;
    player.removeTag(LISTEN_TAG);
    voiceHelper.lastSeen = -999;
    tell(player, T.VOICE_LOST);
    openTalk(player);
  }, 50);
}

function onVoiceHello(message) {
  const wasConnected = voiceConnected();
  const hadAI = voiceHelper.ai;
  voiceHelper.lastSeen = seconds;
  voiceHelper.ai = /\bai\b/.test(String(message)); // "Smart Jerry" is on in the app
  if (wasConnected) {
    if (voiceHelper.ai && !hadAI) for (const player of world.getAllPlayers()) tell(player, T.AI_ON);
    return;
  }
  for (const player of world.getAllPlayers()) {
    tell(player, T.VOICE_CONNECTED);
    if (voiceHelper.ai) tell(player, T.AI_ON);
    const dog = findDog(player);
    if (dog && isNear(dog, player, 24)) {
      showSpeaker(dog, 50);
      sound(dog.dimension, "mob.wolf.bark", dog.location);
    }
  }
}

function onVoiceStatus(player, status) {
  if (status === "listening") {
    // Keep the hint up while the microphone is open.
    for (let i = 0; i < 3; i++) system.runTimeout(() => isValid(player) && actionbar(player, T.VOICE_LISTENING), i * 40);
    const dog = findDog(player);
    if (dog && isNear(dog, player, 24)) startListening(player, dog, 100); // paw to his ear: he's listening
  } else if (status === "nobrowser") {
    tell(player, T.VOICE_NO_BROWSER);
  } else if (status === "blocked") {
    // The phone's speech recognizer failed (e.g. no mic in the background): fall back to the talk box.
    actionbar(player, T.VOICE_BLOCKED);
    if (canReact(player, "voice_help", 600)) tell(player, T.VOICE_BLOCKED_HELP);
    system.runTimeout(() => isValid(player) && openTalk(player), 10);
  } else if (status === "nothing") {
    actionbar(player, T.VOICE_NOTHING);
  }
}

// alwaysOn: heard by the always-listening mode, so only react to things meant for Jerry:
// anything with his name in it, or a short command on its own ("sit", "roll over").
function onVoice(player, text, alwaysOn) {
  if (!text) return;
  if (alwaysOn && !isListening(player)) {
    const words = cleanWords(normalizeNames(text.toLowerCase().replace(/[^a-z' ]+/g, " "), player));
    const saidName = /\bjerry\b/.test(words);
    const shortCommand = words.split(" ").filter(Boolean).length <= 3 && parseCommands(text, player).length > 0;
    if (!saidName && !shortCommand) return;
  }
  hearSpeech(player, text);
}

function openTalk(player, retries = 3) {
  const keepTalking = player.getDynamicProperty(PROP.keepTalking) !== false;
  const form = new ModalFormData()
    .title(T.TALK_TITLE)
    .textField(T.TALK_LABEL, T.TALK_PLACEHOLDER)
    .toggle(T.TALK_KEEP_GOING, keepTalking);
  form
    .show(player)
    .then((response) => {
      if (response.canceled) {
        // "UserBusy" means another screen (like chat) was still closing; try again shortly.
        if (response.cancelationReason === "UserBusy" && retries > 0) {
          system.runTimeout(() => isValid(player) && openTalk(player, retries - 1), 10);
        }
        return; // closed with X: the conversation is over
      }
      const said = String(response.formValues?.[0] ?? "").trim();
      const keepGoing = response.formValues?.[1] !== false;
      player.setDynamicProperty(PROP.keepTalking, keepGoing);
      if (!said) return; // sending nothing also ends the conversation
      const busyTicks = hearSpeech(player, said);
      // Conversation mode: once Jerry has finished reacting, the talk box pops back up,
      // so on a phone you just tap the keyboard microphone again.
      if (keepGoing && busyTicks > 0) {
        system.runTimeout(() => isValid(player) && openTalk(player), busyTicks + 30);
      }
    })
    .catch((error) => console.warn(`[jf] talk form failed: ${error}`));
}

function levenshtein(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const row = [i];
    for (let j = 1; j <= b.length; j++) {
      row[j] = Math.min(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    }
    prev = row;
  }
  return prev[b.length];
}

// Voice typing often mishears words ("role over", "set", "Gerry"), so fix the common ones and
// let a few distinctive words match when they're one letter off.
function cleanWords(text) {
  return text
    .split(" ")
    .filter(Boolean)
    .map((word) => {
      if (T.MISHEARD[word]) return T.MISHEARD[word];
      const close = T.FUZZY_WORDS.find((target) => levenshtein(word, target) <= 1);
      return close ?? word;
    })
    .join(" ");
}

// Your dog's and cat's own names (or something one letter off) count as "jerry" / "mochi".
function normalizeNames(text, player) {
  let out = ` ${text} `;
  for (const [name, token] of [[dogName(player), "jerry"], [catName(player), "mochi"], [pupName(player), "pip"]]) {
    const n = name.toLowerCase().replace(/[^a-z' ]+/g, " ").replace(/\s+/g, " ").trim();
    if (n.length < 2 || n === token) continue;
    out = out.split(` ${n} `).join(` ${token} `);
    if (!n.includes(" ") && n.length >= 4) {
      out = out.split(" ").map((word) => (word && levenshtein(word, n) <= 1 ? token : word)).join(" ");
    }
  }
  return out.trim();
}

function matchVoice(part) {
  const text = ` ${cleanWords(part)} `;
  return T.VOICE.find((entry) => entry.words.some((word) => text.includes(` ${word} `)));
}

// "sit, then roll over and speak" -> [sit, roll, bark]
function parseCommands(said, player) {
  const parts = said
    .toLowerCase()
    .replace(/(\d)\s*-\s*(?=\d)/g, "$1 minus ")
    .replace(/-/g, " ")
    .replace(/[,.!?;:]+/g, " | ")
    .replace(/\b(and then|then|after that|and)\b/g, " | ")
    .split("|");
  const entries = [];
  for (const raw of parts) {
    let part = raw.replace(/[^a-z' ]+/g, " ").replace(/\s+/g, " ").trim();
    if (player) part = normalizeNames(part, player);
    const entry = mathProblem(raw) ?? (part ? matchVoice(part) : undefined);
    if (entry && entries[entries.length - 1] !== entry) entries.push(entry);
  }
  // "Jerry, listen up" is one command, not a hello and then a command.
  const commands = entries.filter((entry) => entry.action !== "greet");
  return (commands.length ? commands : entries).slice(0, 4);
}

// "What's 2 plus 3?" -> he barks 5 times. Also "count to 4" and "how many legs do you have".
const NUMBER_WORDS = [
  "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
  "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
];
const NUM = `(\\d+|${NUMBER_WORDS.join("|")})`;
const NUM_AFTER = `(\\d+|${NUMBER_WORDS.join("|")}|to|too|for)`; // "two plus to" is what the mic hears sometimes
const SUMS = [
  { re: "plus|\\+|add", op: (a, b) => a + b },
  { re: "minus|take away|takeaway", op: (a, b) => a - b },
  { re: "times|x|\\*|×|multiplied by", op: (a, b) => a * b },
  { re: "divided by|over|\\/|÷", op: (a, b) => a / b },
];
const toNumber = (word) => (/^\d+$/.test(word) ? Number(word) : { to: 2, too: 2, for: 4 }[word] ?? NUMBER_WORDS.indexOf(word));

function mathProblem(text) {
  const t = ` ${text.toLowerCase().replace(/\s+/g, " ")} `;
  let answer;
  for (const sum of SUMS) {
    const m = t.match(new RegExp(`\\b${NUM}\\s*(?:${sum.re})\\s*${NUM_AFTER}\\b`));
    if (m) {
      answer = sum.op(toNumber(m[1]), toNumber(m[2]));
      break;
    }
  }
  const count = t.match(new RegExp(`\\bcount (?:up )?to ${NUM}\\b`));
  if (answer === undefined && count) answer = toNumber(count[1]);
  if (answer === undefined && /\bhow many legs\b/.test(t)) answer = 4;
  if (answer === undefined) return undefined;
  const ok = Number.isInteger(answer) && answer >= 0 && answer <= 10;
  return { action: "math", count: ok ? answer : -1 };
}

// How long each reaction takes, so the next command (or the talk box) waits for it.
const ACTION_TICKS = {
  roll: 56, spin: 34, play: 50, greet: 40, paw: 50, highfive: 50, wave: 50, beg: 50,
  dead: 75, lie: 25, bow: 45, dance: 75, backflip: 30, howl: 55, chase: 75,
  find: 110, shy: 65, stretch: 55, spinleft: 34, spinright: 34, groom: 70,
  shakeoff: 35, scratch: 65, hug: 65, sneeze: 25, catch: 30, backup: 34, sleep: 40, listen: 20,
};
const actionTicks = (entry) => (entry.action === "math" ? 30 + Math.max(1, entry.count) * 10 : ACTION_TICKS[entry.action] ?? 30);

// Did you say Mochi's name (or "kitty")? Then the commands are for her.
function talkingToCat(said, player) {
  if (!player.getDynamicProperty(PROP.hasCat)) return false;
  const words = ` ${cleanWords(normalizeNames(said.toLowerCase().replace(/[^a-z' ]+/g, " "), player))} `;
  return / (mochi|kitty) /.test(words) && !/ jerry /.test(words);
}

// Did you say the puppy's name? Then the commands are for him.
function talkingToPup(said, player) {
  if (!player.getDynamicProperty(PROP.hasPup)) return false;
  const words = ` ${cleanWords(normalizeNames(said.toLowerCase().replace(/[^a-z' ]+/g, " "), player))} `;
  return / pip /.test(words) && !/ jerry /.test(words);
}

// Returns how many ticks Jerry (or Mochi, or the puppy) will be busy reacting (0 if nobody could hear you).
function hearSpeech(player, said) {
  player.sendMessage(`§7You: §f"${said.slice(0, 80)}"`); // your own words, unchanged
  if (talkingToCat(said, player)) return hearCat(player, said);
  if (talkingToPup(said, player)) return hearPup(player, said);
  const dog = findDog(player);
  const commands = parseCommands(said, player);
  // "come" works from anywhere, like the whistle; everything else needs Jerry in earshot.
  const calledHim = commands.some((entry) => entry.action === "come");
  if (!dog || (!calledHim && !isNear(dog, player, 24))) {
    tell(player, T.CANT_HEAR);
    return 0;
  }
  // Like in the video: say his name and a red "!" pops up first, then the speaker icon shows he's
  // listening and he reacts.
  const start = saidDogName(said, player) ? ALERT_TICKS : 0;
  if (start) alertPet(player, dog);
  if (wantsAI(said, commands, player)) return askAI(player, dog, said, start);
  const speakerTicks = commands.some((entry) => entry.action === "bark") ? 70 : 45;
  system.runTimeout(() => isValid(dog) && showSpeaker(dog, speakerTicks), Math.max(1, start));
  if (!commands.length) {
    system.runTimeout(() => isValid(player) && isValid(dog) && respond(player, dog, undefined), 12 + start);
    return 50 + start;
  }
  let at = 12 + start;
  for (const entry of commands) {
    system.runTimeout(() => {
      if (isValid(player) && isValid(dog)) respond(player, dog, entry);
    }, at);
    at += actionTicks(entry);
  }
  return at;
}

// ---------------------------------------------------------------- smart Jerry (AI)
// With "Smart Jerry" on in the Jerry Voice app, anything that isn't a simple command ("Jerry, what's
// your favourite food?") goes to an AI that answers as Jerry and picks what he does. Add-ons can't
// reach the internet, so the question goes out in tags on you (jf_q:..., jf_ctx:..., jf_ai_ask) and
// the app answers over /connect with "/scriptevent jf:ai_reply <name>|<actions>|<face>|<words>".

const AI_ASK_TAG = "jf_ai_ask";
const AI_WAIT_TICKS = 240; // 12 seconds, then he gives up and tilts his head
const AI_FACES = new Set(["happy", "puppy", "love", "sleepy", "shocked"]);
const AI_ACTIONS = new Set(T.VOICE.map((entry) => entry.action).filter((action) => action !== "mochi"));
const aiPending = new Map(); // player id -> { dog id, token }
const CHATTY = /^(what|whats|what's|why|how|who|where|when|which|do|does|did|are|is|can|could|would|will|should|have|has|tell|i|i'm|im|you|you're|youre|your|guess|wanna|want|did|was|were)\b/;

const aiReady = () => voiceConnected() && voiceHelper.ai;

// Simple commands stay instant; questions, chatting and anything he doesn't know go to the AI.
function wantsAI(said, commands, player) {
  if (!aiReady() || commands.some((entry) => entry.action === "math")) return false;
  const words = cleanWords(normalizeNames(said.toLowerCase().replace(/[^a-z' ]+/g, " "), player))
    .split(" ")
    .filter((word) => word && word !== "jerry" && word !== "hey");
  if (!commands.length) return words.length > 0;
  const question = /\?\s*$/.test(said);
  if (words.length <= 3 && !question) return false;
  return question || words.length > 5 || CHATTY.test(words.join(" "));
}

const tagSafe = (text, max = 150) => String(text).replace(/§./g, "").replace(/[,|"\\]/g, " ").replace(/\s+/g, " ").trim().slice(0, max);

function clearAITags(player) {
  for (const tag of player.getTags()) {
    if (tag === AI_ASK_TAG || tag.startsWith("jf_q:") || tag.startsWith("jf_ctx:")) player.removeTag(tag);
  }
}

// What the AI should know about him right now (no commas: tags are listed comma-separated).
function aiContext(player, dog) {
  const needs = getNeeds(player);
  const skills = getSkills(player);
  const mastered = Object.keys(skills).filter((key) => !key.includes(":") && skills[key] >= MASTER_AT);
  return [
    `dog=${dogName(player)}`,
    `owner=${player.name}`,
    `friendship=${T.LEVELS[levelOf(getNum(player, PROP.friendship))]}`,
    `mood=${moodOf(needs)}`,
    `hunger=${needs.hunger}/100`,
    `energy=${needs.energy}/100`,
    `fun=${needs.fun}/100`,
    `cat=${player.getDynamicProperty(PROP.hasCat) ? catName(player) : "none yet"}`,
    `puppy=${player.getDynamicProperty(PROP.hasPup) ? pupName(player) : "none yet"}`,
    `time=${isNight() ? "night" : "day"}`,
    `resting=${isSitting(dog) ? "yes" : "no"}`,
    `place=${dog.dimension.id.replace("minecraft:", "")}`,
    `mastered=${mastered.map((key) => T.TRICK_NAMES[key] ?? key).join(" / ") || "nothing yet"}`,
  ].join("; ");
}

function askAI(player, dog, said, start) {
  clearAITags(player);
  player.addTag(`jf_q:${tagSafe(said)}`);
  player.addTag(`jf_ctx:${tagSafe(aiContext(player, dog), 400)}`);
  player.addTag(AI_ASK_TAG);
  const token = (aiPending.get(player.id)?.token ?? 0) + 1;
  aiPending.set(player.id, { dog: dog.id, token });
  // He thinks about it: ears up, head tilted, speaker icon on until the answer comes.
  system.runTimeout(() => {
    if (!isValid(dog) || aiPending.get(player.id)?.token !== token) return;
    showSpeaker(dog, AI_WAIT_TICKS);
    showFace(dog, "puppy", AI_WAIT_TICKS);
    tiltHead(dog);
  }, Math.max(1, start));
  system.runTimeout(() => {
    if (aiPending.get(player.id)?.token !== token) return;
    aiPending.delete(player.id);
    if (!isValid(player)) return;
    clearAITags(player);
    tell(player, T.AI_NO_ANSWER);
    if (isValid(dog)) {
      showSpeaker(dog, 1);
      tiltHead(dog);
      sound(dog.dimension, "mob.wolf.whine", dog.location);
    }
  }, AI_WAIT_TICKS);
  return 70 + start;
}

function onAIReply(message) {
  const [name = "", actionList = "", face = "", ...rest] = String(message).split("|");
  const words = rest.join("|").replace(/§/g, "").trim().slice(0, 240);
  const player = world.getAllPlayers().find((p) => p.name === name.trim());
  if (!player) return;
  const pending = aiPending.get(player.id);
  aiPending.delete(player.id);
  clearAITags(player);
  let dog;
  try {
    dog = pending ? world.getEntity(pending.dog) : undefined;
  } catch {}
  if (!isValid(dog)) dog = findDog(player);
  if (!isValid(dog)) return;
  showSpeaker(dog, 40);
  if (words) player.sendMessage(`§b${dogName(player)}§f: ${words}`);
  const actions = actionList
    .split(",")
    .map((action) => action.trim().toLowerCase())
    .filter((action) => AI_ACTIONS.has(action))
    .slice(0, 3);
  let at = 6;
  for (const action of actions) {
    // He already said his piece, so no canned reply; praise still counts as friendship.
    const entry = { action, praise: action === "love" };
    system.runTimeout(() => isValid(player) && isValid(dog) && respond(player, dog, entry), at);
    at += actionTicks(entry);
  }
  if (!actions.length) sound(dog.dimension, "mob.wolf.panting", dog.location);
  if (AI_FACES.has(face)) system.runTimeout(() => isValid(dog) && showFace(dog, face, 80), at);
}

function hearCat(player, said) {
  const cat = findCat(player);
  const commands = parseCommands(said, player).filter((entry) => entry.action !== "mochi" && entry.action !== "greet");
  const calledHer = commands.some((entry) => entry.action === "come");
  if (!cat || (!calledHer && !isNear(cat, player, 24))) {
    tell(player, T.CAT_NOT_HERE);
    return 0;
  }
  showFace(cat, "puppy", 30); // ears up: she heard you (whether she listens is another matter)
  if (!commands.length) commands.push(T.VOICE.find((entry) => entry.action === "greet"));
  let at = 12;
  for (const entry of commands) {
    system.runTimeout(() => {
      if (isValid(player) && isValid(cat)) respondCat(player, cat, entry);
    }, at);
    at += actionTicks(entry);
  }
  return at;
}

function respondCat(player, cat, entry) {
  const action = entry.action;
  stopListening(player);
  const dim = cat.dimension;
  const trickName = T.TRICK_NAMES[action] ?? action;
  if (!CAT_PRACTICE.has(action) && !CAT_ALWAYS.has(action)) {
    trick(cat, POSE.groom, 50); // a dog trick? she washes her paw instead
    sound(dim, "mob.cat.meow", cat.location);
    return tell(player, pick(T.CAT_SASS).replace("{trick}", trickName));
  }
  if (CAT_PRACTICE.has(action) && !attemptTrick(player, cat, action)) return;
  const meow = () => sound(dim, "mob.cat.meow", cat.location);
  switch (action) {
    case "love":
      sparkle(cat);
      showFace(cat, "love", 60);
      sound(dim, "mob.cat.purr", cat.location);
      if (earn(player, "talk")) actionbar(player, friendshipBar(player));
      break;
    case "sit":
      meow();
      sit(cat);
      break;
    case "lie":
      sound(dim, "mob.cat.purr", cat.location);
      sit(cat, POSE.lie);
      break;
    case "come":
      if (isSitting(cat)) stand(cat);
      if (!isNear(cat, player, 6)) bring(cat, player);
      meow();
      break;
    case "roll":
      sparkle(cat, "minecraft:villager_happy", 6);
      trick(cat, POSE.roll, 50);
      break;
    case "greet":
    case "bark":
      meow();
      tiltHead(cat);
      break;
    case "spin":
    case "spinleft":
    case "spinright":
      spin(cat, action === "spinleft" ? -1 : 1);
      break;
    case "hop":
      hop(cat, 0.5);
      meow();
      break;
    case "paw":
    case "highfive":
      sound(dim, "mob.cat.purr", cat.location);
      trick(cat, POSE.paw, 45);
      if (action === "highfive") system.runTimeout(() => isValid(cat) && sound(cat.dimension, "random.orb", cat.location, { pitch: 1.6 }), 10);
      break;
    case "wave":
      meow();
      trick(cat, POSE.wave, 50);
      break;
    case "beg":
      sound(dim, "mob.cat.purreow", cat.location);
      trick(cat, POSE.beg, 50);
      break;
    case "dead":
      meow();
      trick(cat, POSE.playDead, 75);
      break;
    case "chase":
      for (let i = 0; i < 3; i++) system.runTimeout(() => isValid(cat) && spin(cat), i * 24);
      meow();
      break;
    case "shy":
      trick(cat, POSE.shy, 60);
      system.runTimeout(() => isValid(cat) && showFace(cat, "love", 30), 45);
      break;
    case "stretch":
      trick(cat, POSE.stretch, 50);
      break;
    case "groom":
      sound(dim, "mob.cat.purr", cat.location);
      trick(cat, POSE.groom, 70);
      break;
    case "find":
      trick(cat, POSE.sniff, 60, false);
      system.runTimeout(() => isValid(cat) && hop(cat, 0.45), 60); // pounce!
      break;
    case "excited":
      sound(dim, "mob.cat.purreow", cat.location);
      tiltHead(cat);
      break;
    case "listen":
      tiltHead(cat); // one ear flick, that's all you get
      showSpeaker(cat, 40);
      break;
    case "yawn":
      sit(cat, POSE.lie);
      break;
    case "shock":
      hop(cat, 0.55);
      sound(dim, "mob.cat.hiss", cat.location);
      break;
    case "play":
      catZoomies(cat);
      break;
    case "hug":
      // she allows it. Briefly. Then a headbutt.
      sparkle(cat);
      showFace(cat, "love", 60);
      sound(dim, "mob.cat.purr", cat.location);
      system.runTimeout(() => isValid(cat) && hop(cat, 0.25), 40);
      if (earn(player, "talk")) actionbar(player, friendshipBar(player));
      break;
    case "sleep":
      sound(dim, "mob.cat.purr", cat.location);
      sit(cat, POSE.lie);
      showFace(cat, "sleepy", 1200);
      break;
    case "sneeze":
      tiltHead(cat);
      system.runTimeout(() => isValid(cat) && sound(cat.dimension, "mob.panda.sneeze", cat.location, { pitch: 1.9 }), 6);
      break;
    case "scratch":
      trick(cat, POSE.scratch, 60);
      break;
    case "shakeoff":
      trick(cat, POSE.shakeOff, 30);
      sparkle(cat, "minecraft:water_splash_particle", 8);
      break;
    case "catch":
      hop(cat, 0.5);
      system.runTimeout(() => isValid(cat) && sound(cat.dimension, "random.eat", cat.location, { pitch: 1.4 }), 10);
      break;
  }
  const faces = { lie: "sleepy", yawn: "sleepy", shock: "shocked", dead: "sleepy", beg: "puppy", excited: "puppy", shy: "puppy", sneeze: "shocked" };
  if (action !== "love" && action !== "hug" && action !== "sleep") showFace(cat, faces[action] ?? "happy", actionTicks(entry) + 20);
  const replies = T.CAT_REPLIES[action];
  if (replies) tell(player, pick(replies));
}

// ---------------------------------------------------------------- the puppy
// He does Jerry's tricks (no fetch or treasure hunts yet), but he's little, so he messes up a lot.

const PUP_SKIP = new Set(["fetch", "find", "mochi", "play"]);

function hearPup(player, said) {
  const pup = findPup(player);
  const commands = parseCommands(said, player).filter((entry) => entry.action !== "greet");
  const calledHim = commands.some((entry) => entry.action === "come");
  if (!pup || (!calledHim && !isNear(pup, player, 24))) {
    tell(player, T.PUP_NOT_HERE);
    return 0;
  }
  alertPet(player, pup); // you always say his name to talk to him
  system.runTimeout(() => isValid(pup) && showSpeaker(pup, 40), ALERT_TICKS);
  if (!commands.length) commands.push(T.VOICE.find((entry) => entry.action === "greet"));
  let at = 12 + ALERT_TICKS;
  for (const entry of commands) {
    system.runTimeout(() => {
      if (isValid(player) && isValid(pup)) respondPup(player, pup, entry);
    }, at);
    at += actionTicks(entry);
  }
  return at;
}

function respondPup(player, pup, entry) {
  if (entry.action !== "listen") stopListening(player);
  if (entry.action === "fetch" || entry.action === "play") {
    pupZoomies(pup);
    return tell(player, T.PUP_MOMENTS[3].text);
  }
  if (entry.action === "find") return trick(pup, POSE.sniff, 50, false);
  if (entry.action === "math") return doMath(player, pup, entry);
  if (PUP_SKIP.has(entry.action)) return tiltHead(pup);
  if (PRACTICE.has(entry.action) && Math.random() < 0.3) {
    pupOops(pup);
    return tell(player, pick(T.PUP_OOPS));
  }
  talkAction(player, pup, entry.action);
  if (entry.face) showFace(pup, entry.face, actionTicks(entry) + 20);
  if (entry.replies) tell(player, pick(entry.replies).replace(/Jerry/g, "Pip"));
}

function pupOops(pup) {
  const which = Math.floor(Math.random() * 3);
  if (which === 0) trick(pup, POSE.playDead, 40); // falls over
  else if (which === 1) spin(pup);
  else trick(pup, POSE.sit, 40);
  showFace(pup, "puppy", 50);
  return which;
}

function pupZoomies(pup) {
  for (let i = 0; i < 4; i++) system.runTimeout(() => isValid(pup) && hop(pup, 0.35), i * 10);
  spin(pup);
}

// When Jerry does a trick, the puppy (if he's around) tries to copy him.
const PUP_FOLLOWS = new Set(["sit", "lie", "come", "sleep"]); // these he always copies
const PUP_COPIES = new Set([
  "roll", "paw", "highfive", "wave", "beg", "dead", "bow", "dance", "howl", "chase", "spin", "spinleft", "spinright",
  "hop", "bark", "shy", "stretch", "love", "shock", "shakeoff", "scratch", "sneeze", "hug", "catch", "backup",
]);

function pupCopies(player, action) {
  const pup = findPup(player);
  if (!isNear(pup, player, 24)) return;
  const always = PUP_FOLLOWS.has(action);
  if (!always && !(PUP_COPIES.has(action) && Math.random() < 0.6)) return;
  system.runTimeout(() => {
    if (!isValid(pup) || !isValid(player)) return;
    if (always || Math.random() < 0.5) {
      talkAction(player, pup, action);
      if (!always && Math.random() < 0.5) tell(player, pick(T.PUP_COPY_OK));
    } else {
      tell(player, T.PUP_COPY_OOPS[pupOops(pup)]);
    }
  }, 15);
}

function pupMoment(player, pup, dog) {
  const moment = pick(T.PUP_MOMENTS);
  sound(pup.dimension, moment.sound, pup.location, { pitch: 1.5 });
  if (moment.action === "hop") hop(pup, 0.4);
  if (moment.action === "tilt") tiltHead(pup);
  if (moment.action === "nap" && !isSitting(pup)) trick(pup, POSE.lie, 200);
  if (moment.action === "zoomies") {
    pupZoomies(pup);
    if (isValid(dog) && !isSitting(dog)) spin(dog);
  }
  showFace(pup, moment.face, moment.action === "nap" ? 200 : 70);
  actionbar(player, moment.text);
}

function catZoomies(cat) {
  for (let i = 0; i < 4; i++) {
    system.runTimeout(() => {
      if (!isValid(cat)) return;
      hop(cat, 0.4);
      try {
        cat.applyImpulse({ x: rand(-0.5, 0.5), y: 0, z: rand(-0.5, 0.5) });
      } catch {}
    }, i * 10);
  }
  spin(cat);
}

function respond(player, dog, entry) {
  if (entry?.action !== "listen") stopListening(player);
  if (!entry) {
    sound(dog.dimension, "mob.wolf.whine", dog.location);
    tiltHead(dog);
    showFace(dog, "puppy", 50);
    return tell(player, pick(T.VOICE_UNKNOWN));
  }
  if (entry.action === "play") return play(player);
  if (entry.action === "fetch") return throwBall(player);
  if (entry.action === "math") return doMath(player, dog, entry);
  if (PRACTICE.has(entry.action)) {
    changeNeeds(player, { fun: 3, energy: -1 });
    // Too sleepy? Sometimes he'd rather nap.
    if (getNeeds(player).energy < 15 && Math.random() < 0.5) {
      sit(dog, POSE.lie);
      showFace(dog, "sleepy", 200);
      sound(dog.dimension, "mob.wolf.panting", dog.location);
      return tell(player, T.TOO_SLEEPY);
    }
    if (!attemptTrick(player, dog, entry.action)) return;
  }
  talkAction(player, dog, entry.action);
  if (!isPup(dog)) pupCopies(player, entry.action);
  if (entry.face) showFace(dog, entry.face, actionTicks(entry) + 20);
  let reply = entry.replies ? pick(entry.replies) : "";
  if (entry.action === "mochi") {
    const cat = findCat(player);
    reply = cat ? pick(T.VOICE_MOCHI) : T.VOICE_NO_CAT;
    if (cat) {
      sound(cat.dimension, "mob.cat.meow", cat.location);
      sparkle(cat);
    }
  }
  if (entry.praise && earn(player, "talk")) reply += `  ${friendshipBar(player)}`;
  if (reply) tell(player, reply);
}

function talkAction(player, dog, action) {
  const dim = dog.dimension;
  switch (action) {
    case "love":
      sparkle(dog);
      sound(dim, "mob.wolf.panting", dog.location);
      return hop(dog, 0.3);
    case "sit":
      sound(dim, "mob.wolf.bark", dog.location);
      return sit(dog);
    case "come":
      if (isSitting(dog)) stand(dog);
      if (!isNear(dog, player, 6)) bring(dog, player);
      return sound(dim, "mob.wolf.bark", dog.location);
    case "roll":
      sparkle(dog, "minecraft:villager_happy", 6);
      sound(dim, "mob.wolf.panting", dog.location);
      return rollOver(dog);
    case "greet":
      sound(dim, "mob.wolf.bark", dog.location);
      return tiltHead(dog);
    case "bark":
      sound(dim, "mob.wolf.bark", dog.location);
      return system.runTimeout(() => isValid(dog) && sound(dog.dimension, "mob.wolf.bark", dog.location), 8);
    case "spin":
      sparkle(dog, "minecraft:villager_happy", 6);
      return spin(dog);
    case "hop":
      sound(dim, "mob.wolf.bark", dog.location);
      return hop(dog, 0.55);
    case "excited":
      hop(dog, 0.35);
      return sound(dim, "mob.wolf.whine", dog.location);
    case "listen":
      return startListening(player, dog);
    case "yawn":
      return sound(dim, "mob.wolf.panting", dog.location);
    case "shock":
      hop(dog, 0.4);
      return sound(dim, "mob.wolf.whine", dog.location);
    case "bone":
      holdBone(dog, 100);
      return sound(dim, "mob.wolf.panting", dog.location);
    case "paw":
      sound(dim, "mob.wolf.panting", dog.location);
      return trick(dog, POSE.paw, 50);
    case "highfive":
      trick(dog, POSE.paw, 50);
      system.runTimeout(() => {
        if (!isValid(dog)) return;
        sparkle(dog, "minecraft:villager_happy", 8);
        sound(dog.dimension, "random.orb", dog.location, { pitch: 1.4 });
      }, 10);
      return;
    case "wave":
      sound(dim, "mob.wolf.bark", dog.location);
      return trick(dog, POSE.wave, 50);
    case "beg":
      sound(dim, "mob.wolf.whine", dog.location);
      return trick(dog, POSE.beg, 50);
    case "dead":
      sound(dim, "mob.wolf.whine", dog.location, { pitch: 0.7 });
      return trick(dog, POSE.playDead, 75);
    case "lie":
      sound(dim, "mob.wolf.panting", dog.location);
      return sit(dog, POSE.lie);
    case "bow":
      sound(dim, "mob.wolf.bark", dog.location);
      return trick(dog, POSE.bow, 45);
    case "dance":
      trick(dog, POSE.dance, 75);
      // a little tune to dance to
      [1, 1.26, 1.5, 1.26, 1.68, 1.5].forEach((pitch, i) =>
        system.runTimeout(() => isValid(dog) && sound(dog.dimension, "note.harp", dog.location, { pitch }), i * 10)
      );
      return;
    case "backflip":
      hop(dog, 0.6);
      sparkle(dog, "minecraft:villager_happy", 6);
      return trick(dog, POSE.backflip, 26, false);
    case "howl":
      trick(dog, POSE.howl, 55);
      [0.6, 0.75, 0.6].forEach((pitch, i) =>
        system.runTimeout(() => isValid(dog) && sound(dog.dimension, "mob.wolf.whine", dog.location, { pitch }), 6 + i * 14)
      );
      return;
    case "chase":
      for (let i = 0; i < 3; i++) system.runTimeout(() => isValid(dog) && spin(dog), i * 24);
      return sound(dim, "mob.wolf.bark", dog.location);
    case "spinleft":
    case "spinright":
      sparkle(dog, "minecraft:villager_happy", 6);
      return spin(dog, action === "spinleft" ? -1 : 1);
    case "find":
      return findIt(player, dog);
    case "groom":
      sound(dim, "mob.wolf.panting", dog.location);
      return tiltHead(dog);
    case "shy":
      sound(dim, "mob.wolf.whine", dog.location, { pitch: 1.3 });
      trick(dog, POSE.shy, 60);
      // ...then he peeks out from behind his paw.
      return system.runTimeout(() => {
        if (!isValid(dog)) return;
        sparkle(dog, "minecraft:heart_particle", 3);
        showFace(dog, "love", 30);
      }, 45);
    case "stretch":
      sound(dim, "mob.wolf.panting", dog.location, { pitch: 0.8 });
      trick(dog, POSE.stretch, 50);
      return changeNeeds(player, { energy: 2 });
    case "shakeoff":
      trick(dog, POSE.shakeOff, 30);
      sound(dim, "mob.wolf.shake", dog.location);
      for (let i = 0; i < 3; i++) system.runTimeout(() => isValid(dog) && sparkle(dog, "minecraft:water_splash_particle", 10), i * 8);
      return;
    case "scratch":
      trick(dog, POSE.scratch, 60);
      // *thump thump thump* goes the back paw
      for (let i = 0; i < 5; i++) system.runTimeout(() => isValid(dog) && sound(dog.dimension, "mob.wolf.step", dog.location, { pitch: 1.3 }), 8 + i * 8);
      return;
    case "hug":
      if (!isNear(dog, player, 4)) bring(dog, player);
      lookAt(dog, player);
      trick(dog, POSE.hug, 60);
      sparkle(dog, "minecraft:heart_particle", 4);
      system.runTimeout(() => isValid(dog) && sparkle(dog, "minecraft:heart_particle", 4), 30);
      return sound(dim, "mob.wolf.panting", dog.location);
    case "sneeze":
      trick(dog, POSE.sneeze, 20, false);
      return system.runTimeout(() => {
        if (!isValid(dog)) return;
        sound(dog.dimension, "mob.panda.sneeze", dog.location, { pitch: isPup(dog) ? 1.7 : 1.3 });
        sparkle(dog, "minecraft:basic_smoke_particle", 3);
      }, 9);
    case "catch":
      lookAt(dog, player);
      trick(dog, POSE.beg, 24);
      system.runTimeout(() => isValid(dog) && hop(dog, 0.45), 6);
      return system.runTimeout(() => {
        if (!isValid(dog)) return;
        sound(dog.dimension, "random.eat", dog.location);
        sparkle(dog, "minecraft:villager_happy", 5);
        changeNeeds(player, { hunger: 2, fun: 2 });
      }, 12);
    case "backup":
      return backUp(player, dog);
    case "sleep":
      sound(dim, "mob.wolf.panting", dog.location, { pitch: 0.6 });
      changeNeeds(player, { energy: 10 });
      if (!isPup(dog) && goToBed(player, dog, true)) return; // he has a bed: off he goes
      sit(dog, POSE.sleep);
      showFace(dog, "sleepy", 1200);
      return snore(dog, 0);
  }
}

// Little Zzz's float up while he sleeps, until he gets up (or a minute passes).
function snore(dog, n) {
  if (!isValid(dog) || restPose(dog) !== POSE.sleep || n >= 12) return;
  const at = dog.location;
  particle(dog.dimension, "jf:zzz", { x: at.x, y: at.y + 0.8, z: at.z });
  if (n % 3 === 1) sound(dog.dimension, "mob.wolf.panting", at, { pitch: 0.5 });
  system.runTimeout(() => snore(dog, n + 1), 100);
}

// Turns a pet to face you.
function lookAt(pet, player) {
  try {
    const dx = player.location.x - pet.location.x;
    const dz = player.location.z - pet.location.z;
    pet.setRotation({ x: 0, y: (Math.atan2(-dx, dz) * 180) / Math.PI });
  } catch {}
}

// "Back up!": he walks backwards a few steps, still looking at you. Beep. Beep. Beep.
function backUp(player, dog) {
  if (isSitting(dog)) stand(dog);
  trick(dog, POSE.stand, 28); // pauses his follow brain while he reverses
  const from = player.location;
  let ticks = 0;
  const run = system.runInterval(() => {
    if (!isValid(dog) || ++ticks > 24) return system.clearRun(run);
    const p = dog.location;
    let dx = p.x - from.x;
    let dz = p.z - from.z;
    let d = Math.hypot(dx, dz);
    if (d < 0.1) {
      const yaw = (dog.getRotation().y * Math.PI) / 180;
      dx = Math.sin(yaw); // straight behind him
      dz = -Math.cos(yaw);
      d = 1;
    }
    const v = dog.getVelocity();
    try {
      dog.applyImpulse({ x: ((dx / d) * 0.12 - v.x) * 0.35, y: 0, z: ((dz / d) * 0.12 - v.z) * 0.35 });
      dog.setRotation({ x: 0, y: (Math.atan2(dx, -dz) * 180) / Math.PI });
    } catch {}
    if (ticks % 8 === 1) sound(dog.dimension, "note.pling", dog.location, { pitch: 2 });
  }, 1);
}

// "What's 2 plus 3?" He barks the answer. He's very smart.
function doMath(player, dog, entry) {
  const n = entry.count;
  const say = (text) => tell(player, isPup(dog) ? text.replace(/Jerry/g, "Pip") : text);
  if (n < 0) {
    tiltHead(dog);
    showFace(dog, "puppy", 60);
    sound(dog.dimension, "mob.wolf.whine", dog.location);
    return say(pick(T.MATH_HARD));
  }
  if (n === 0) {
    tiltHead(dog);
    showFace(dog, "happy", 60);
    return say(T.MATH_ZERO);
  }
  trick(dog, POSE.sit, 20 + n * 10); // sits up nice and tall to concentrate
  for (let i = 0; i < n; i++) {
    system.runTimeout(() => {
      if (!isValid(dog)) return;
      sound(dog.dimension, "mob.wolf.bark", dog.location);
    }, 8 + i * 10);
  }
  system.runTimeout(() => {
    if (!isValid(dog) || !isValid(player)) return;
    sparkle(dog, "minecraft:villager_happy", 6);
    showFace(dog, "happy", 40);
    let reply = pick(T.MATH_DONE).replace("{n}", String(n)).replace("{times}", n === 1 ? "time" : "times");
    if (earn(player, "talk")) reply += `  ${friendshipBar(player)}`;
    say(reply);
    // The puppy has a go too. He's still learning to count.
    const pup = findPup(player);
    if (!isPup(dog) && isNear(pup, player, 24) && Math.random() < 0.5) {
      const guess = n + (Math.random() < 0.5 ? 1 : -1) || 2;
      for (let i = 0; i < guess; i++) system.runTimeout(() => isValid(pup) && sound(pup.dimension, "mob.wolf.bark", pup.location, { pitch: 1.6 }), 10 + i * 8);
      system.runTimeout(() => isValid(player) && tell(player, T.PUP_MATH.replace("{n}", String(guess))), 14 + guess * 8);
    }
  }, 14 + n * 10);
}

// "Find it!": he sniffs around a few spots near you, nose to the ground, and finds a treat.
function findIt(player, dog) {
  if (isSitting(dog)) stand(dog);
  const token = (poseTimers.get(dog.id) ?? 0) + 1;
  poseTimers.set(dog.id, token); // a trick started meanwhile takes over the pose
  try {
    dog.triggerEvent("jf:sit"); // pause his follow brain while he snuffles around
  } catch {}
  setPose(dog, POSE.sniff);
  const home = player.location;
  const spots = [0, 1, 2].map(() => {
    const angle = rand(0, Math.PI * 2);
    const r = rand(2, 4.5);
    return { x: home.x + Math.cos(angle) * r, y: home.y, z: home.z + Math.sin(angle) * r };
  });
  let ticks = 0;
  const run = system.runInterval(() => {
    ticks++;
    if (!isValid(dog) || fetching.has(dog.id) || isSitting(dog)) return system.clearRun(run);
    const spot = spots[Math.min(2, Math.floor(ticks / 30))];
    if (ticks < 90) {
      runToward(dog, spot, 0.14);
      if (ticks % 6 === 0) particle(dog.dimension, "minecraft:basic_smoke_particle", { ...dog.location, y: dog.location.y + 0.2 });
      if (ticks % 15 === 0) sound(dog.dimension, "mob.wolf.panting", dog.location, { pitch: 1.5 });
      return;
    }
    system.clearRun(run);
    if (poseTimers.get(dog.id) === token) setPose(dog, POSE.stand);
    try {
      dog.triggerEvent("jf:stand");
    } catch {}
    sparkle(dog);
    showFace(dog, "love", 60);
    sound(dog.dimension, "random.pop", dog.location);
    sound(dog.dimension, "mob.wolf.bark", dog.location);
    changeNeeds(player, { hunger: 8, fun: 8 });
    if (isValid(player)) tell(player, pick(T.FOUND_IT));
  }, 1);
}

// ---------------------------------------------------------------- commands

function resetPets(player) {
  removeAllPets(player);
  for (const key of Object.values(PROP)) player.setDynamicProperty(key, undefined);
  nextGift.delete(player.id);
  startIntro(player);
}

system.afterEvents.scriptEventReceive.subscribe(
  ({ id, sourceEntity, message }) => {
    if (id === "jf:voice_hello") return onVoiceHello(message);
    if (id === "jf:ai_reply") return onAIReply(message);
    const players = world.getAllPlayers();
    // Commands from the voice helper normally run as you; if not, use whoever asked to talk.
    const player =
      players.find((p) => p.id === sourceEntity?.id) ??
      players.find((p) => p.id === voiceHelper.lastListener) ??
      players[0];
    if (!player) return;
    if (id === "jf:voice") return onVoice(player, message, false);
    if (id === "jf:voice_heard") return onVoice(player, message, true);
    if (id === "jf:voice_status") return onVoiceStatus(player, message);
    if (id === "jf:status") tell(player, `§6[Jerry & Friends]§f Friendship: ${friendshipBar(player)} §7(${getNum(player, PROP.friendship)}/100)`);
    else if (id === "jf:reset") resetPets(player);
    else if (id === "jf:boost") addFriendship(player, 25);
    else if (id === "jf:name") namePet(player, "dog", message);
    else if (id === "jf:cat_name") namePet(player, "cat", message);
    else if (id === "jf:pup_name") namePet(player, "pup", message);
  },
  { namespaces: ["jf"] }
);

// ---------------------------------------------------------------- events

system.runInterval(() => {
  seconds++;
  if (seconds % 10 === 0) removeStrayBalls();
  for (const player of world.getAllPlayers()) {
    try {
      tick(player);
    } catch (error) {
      console.warn(`[jf] ${error}`);
    }
  }
}, 20);

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  if (!player.getDynamicProperty(PROP.started)) return scheduleIntro(player, 100);
  system.runTimeout(() => {
    if (!isValid(player)) return;
    tell(player, T.WELCOME_BACK);
    give(player, ITEMS.talk); // players from before "Talk to Jerry" existed
    if (!player.getDynamicProperty(PROP.gotHome)) {
      giveAllItems(player); // players from before the dog bed and food bowl existed
      tell(player, T.HOME_ITEMS);
    }
  }, 100);
});

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (!itemStack || !OUR_ITEMS.has(itemStack.typeId) || source?.typeId !== "minecraft:player") return;
  useItem(source, itemStack.typeId);
});

// Using one of our items on a pet should not make it sit down; an empty hand pets Jerry.
world.beforeEvents.playerInteractWithEntity?.subscribe((event) => {
  const id = event.itemStack?.typeId;
  if (!id && (event.target.typeId === JERRY || event.target.typeId === MOCHI)) {
    // Empty hand: tap to pet, or sneak + tap to start talking.
    const { player, target } = event;
    const talk = player.isSneaking;
    system.run(() => {
      if (!isValid(player) || !isValid(target) || !target.hasTag(ownerTag(player))) return;
      if (talk) startTalking(player);
      else if (target.typeId === MOCHI) petMochi(player, target);
      else if (isPup(target)) petPup(player, target);
      else petJerry(player, target);
    });
    return;
  }
  // A dye on your dog changes his collar colour.
  if (dyeColour(id) !== undefined && (event.target.typeId === JERRY || event.target.typeId === MOCHI)) {
    const { player, target } = event;
    if (!target.hasTag(ownerTag(player))) return;
    event.cancel = true;
    system.run(() => isValid(player) && isValid(target) && dyeCollar(player, target, dyeColour(id)));
    return;
  }
  if (!id || !OUR_ITEMS.has(id)) return;
  event.cancel = true;
  const { player, target } = event;
  system.run(() => {
    if (isValid(player)) useItem(player, id, target);
  });
});

// Dog beds and food bowls: remember where they are; fill bowls with treats or meat.
world.afterEvents.playerPlaceBlock.subscribe(({ block }) => {
  if (block.typeId === BLOCKS.bed || block.typeId === BLOCKS.bowl) rememberHome(block);
});

world.afterEvents.playerBreakBlock.subscribe(({ block, brokenBlockPermutation }) => {
  if ([BLOCKS.bed, BLOCKS.bowl, BLOCKS.fullBowl].includes(brokenBlockPermutation.type.id)) forgetHome(block.dimension.id, block.location);
});

world.beforeEvents.playerInteractWithBlock.subscribe((event) => {
  const { block, player, itemStack } = event;
  if (block.typeId !== BLOCKS.bowl && block.typeId !== BLOCKS.fullBowl) return;
  if (!itemStack || !event.isFirstEvent) return;
  event.cancel = true;
  lastUse.set(player.id, system.currentTick); // so the same tap doesn't also use the treats on Jerry
  const itemId = itemStack.typeId;
  system.run(() => {
    if (!isValid(player)) return;
    try {
      const now = block.dimension.getBlock(block.location);
      if (now) fillBowl(player, now, itemId);
    } catch {}
  });
});

// Clean up leftover copies (e.g. after the whistle respawned a pet that was in an unloaded chunk).
world.afterEvents.entityLoad?.subscribe(({ entity }) => {
  if (!isValid(entity)) return;
  if (entity.typeId === BALL && !activeBalls().has(entity.id)) return entity.remove(); // from a game that got cut off
  if (!entity.hasTag(TAG.pet)) return;
  const owner = world.getAllPlayers().find((p) => entity.hasTag(ownerTag(p)));
  if (!owner) return;
  const current = owner.getDynamicProperty(entity.hasTag(TAG.cat) ? PROP.cat : entity.hasTag(TAG.pup) ? PROP.pup : PROP.dog);
  if (typeof current === "string" && current !== entity.id) entity.remove();
});
