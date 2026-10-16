import { world, system, ItemStack } from "@minecraft/server";
import { ModalFormData } from "@minecraft/server-ui";
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

const PROP = {
  started: "jf:started",
  friendship: "jf:friendship",
  dog: "jf:dog_id",
  cat: "jf:cat_id",
  hasCat: "jf:has_cat",
  keepTalking: "jf:keep_talking",
  dogName: "jf:dog_name",
  catName: "jf:cat_name",
};
const TAG = { pet: "jf_pet", dog: "jf_dog", cat: "jf_cat" };
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
const JERRY = "jf:jerry";
// Matches the jf:pose property in entities/jerry.json and the pose animation controller.
const POSE = {
  stand: 0, sit: 1, roll: 2, tilt: 3, paw: 4, wave: 5, beg: 6,
  playDead: 7, lie: 8, bow: 9, dance: 10, backflip: 11, howl: 12,
};
const DEFAULT_DOG_NAME = "Jerry";
const DEFAULT_CAT_NAME = "Mochi";

// Friendship goes 0-100. Level thresholds line up with T.LEVELS.
const MAX_FRIENDSHIP = 100;
const LEVEL_MIN = [0, 25, 60, 100];
const CAT_LEVEL = 1;
const GIFT_LEVEL = 2;
const GAIN = { treat: 5, brush: 3, ball: 4, talk: 2, pet: 2, together: 1 };
const COOLDOWN = { treat: 60, brush: 30, ball: 45, talk: 30, pet: 20 }; // seconds before an action earns friendship again

let seconds = 0;
const cooldowns = new Map(); // "playerId:action" -> seconds when it earns friendship again
const nextMoment = new Map();
const nextGift = new Map();
const lastUse = new Map();
const pendingIntro = new Set();
const pendingCat = new Set();
const poseTimers = new Map(); // dog id -> latest trick, so an older timer doesn't cut a newer trick short
const propTimers = new Map(); // "dogId:property" -> latest timed change
// The optional phone voice helper (voice-helper/jerry_voice.py) checks in every few seconds.
const voiceHelper = { lastSeen: -999, lastListener: undefined };
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

function personalize(player, text) {
  return String(text)
    .replace(/Jerry/g, () => dogName(player)) // no \b: colour codes like "§bJerry" sit right against the name
    .replace(/Mochi/g, () => catName(player));
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
const findCat = (player) => (player.getDynamicProperty(PROP.hasCat) ? findPet(player, PROP.cat, TAG.cat) : undefined);

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

const spawnJerry = (player, at) => spawnPet(player, JERRY, TAG.dog, dogName(player), PROP.dog, at);
const spawnMochi = (player) => spawnPet(player, "minecraft:cat", TAG.cat, catName(player), PROP.cat);

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
        dog.triggerEvent("jf:stand");
      } catch {}
    }
    setPose(dog, restPose(dog) ?? POSE.stand);
  }, ticks);
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

// Jerry's expressions (face overlays in the resource pack), matching the jf:face property.
const FACE = { normal: 0, happy: 1, puppy: 2, shocked: 3, love: 4, sleepy: 5 };
const showFace = (dog, face, ticks = 60) => setFor(dog, "jf:face", FACE[face] ?? FACE.normal, FACE.normal, ticks);
// The bone he carries in his mouth.
const holdBone = (dog, ticks = 60) => setFor(dog, "jf:bone", true, false, ticks);

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

function spin(pet) {
  for (let i = 1; i <= 12; i++) {
    system.runTimeout(() => {
      if (!isValid(pet)) return;
      try {
        const rotation = pet.getRotation();
        pet.teleport(pet.location, { rotation: { x: rotation.x, y: rotation.y + 30 } });
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
function namePet(player, kind, raw, quiet = false) {
  const isCat = kind === "cat";
  const current = isCat ? catName(player) : dogName(player);
  const pet = isCat ? findCat(player) : findDog(player);
  const name = cleanName(raw);
  if (!name || name === current) {
    if (pet && pet.nameTag !== current) pet.nameTag = current; // keep the tag in sync
    return false;
  }
  player.setDynamicProperty(isCat ? PROP.catName : PROP.dogName, name);
  if (pet) {
    pet.nameTag = name;
    sparkle(pet);
    if (!isCat) showFace(pet, "love", 60);
  }
  if (!quiet) tell(player, isCat ? T.CAT_RENAMED : T.DOG_RENAMED);
  return true;
}

// The naming box: shown when you first get your dog, and on sneak + Dog Whistle.
function openNaming(player, intro = false, retries = 3, done) {
  const hasCat = !intro && !!player.getDynamicProperty(PROP.hasCat);
  const form = new ModalFormData()
    .title(personalize(player, intro ? T.NAME_TITLE_NEW : T.NAME_TITLE))
    .textField(intro ? T.NAME_NEW_DOG : T.NAME_DOG, DEFAULT_DOG_NAME, dogName(player));
  if (hasCat) form.textField(T.NAME_CAT, DEFAULT_CAT_NAME, catName(player));
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
  for (const id of Object.values(ITEMS)) give(player, id);
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

// ---------------------------------------------------------------- every second

function playMoment(player, dog, cat) {
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

function bringGift(player, dog) {
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
  if (dog) protect(dog);
  const cat = findCat(player);
  if (cat) protect(cat);
}

function tick(player) {
  if (!player.getDynamicProperty(PROP.started)) return scheduleIntro(player, 60);
  if (seconds % 10 === 0) protectAll(player);

  const friendship = getNum(player, PROP.friendship);
  if (levelOf(friendship) >= CAT_LEVEL && !player.getDynamicProperty(PROP.hasCat)) scheduleCat(player);

  const dog = findDog(player);
  // Renamed with a name tag?
  if (dog && dog.nameTag && dog.nameTag !== dogName(player)) namePet(player, "dog", dog.nameTag);
  const cat = findCat(player);
  if (cat && cat.nameTag && cat.nameTag !== catName(player)) namePet(player, "cat", cat.nameTag);

  if (!isNear(dog, player, 16)) return;
  if (seconds % 60 === 0) addFriendship(player, GAIN.together);

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
    return actionbar(player, T.TREAT_CAT);
  }
  sound(target.dimension, "mob.wolf.bark", target.location);
  showFace(target, "love", 60);
  const earned = earn(player, "treat");
  actionbar(player, `${pick(T.TREAT)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
}

function brush(player, target) {
  const kind = petKind(player, target);
  if (!kind) return actionbar(player, T.BRUSH_OTHER);
  sparkle(target, "minecraft:villager_happy", 8);
  if (kind === "cat") {
    sound(target.dimension, "mob.cat.purr", target.location);
    return actionbar(player, T.BRUSH_CAT);
  }
  sound(target.dimension, "mob.wolf.panting", target.location);
  showFace(target, "happy", 60);
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

  if (itemId === ITEMS.whistle) return player.isSneaking ? openNaming(player) : whistle(player);
  if (itemId === ITEMS.talk) return startTalking(player);
  if (itemId === ITEMS.ball) return play(player);
  target = isValid(target) ? target : lookTarget(player);
  if (itemId === ITEMS.treat) return giveTreat(player, target);
  if (itemId === ITEMS.brush) return brush(player, target);
}

function petJerry(player, dog) {
  sparkle(dog);
  sound(dog.dimension, "mob.wolf.panting", dog.location);
  tiltHead(dog);
  showFace(dog, "happy", 50);
  const earned = earn(player, "pet");
  actionbar(player, `${pick(T.PET)}${earned ? "" : T.FULL}  ${friendshipBar(player)}`);
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

function onVoiceHello() {
  const wasConnected = voiceConnected();
  voiceHelper.lastSeen = seconds;
  if (wasConnected) return;
  for (const player of world.getAllPlayers()) {
    tell(player, T.VOICE_CONNECTED);
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
    if (dog && isNear(dog, player, 24)) {
      tiltHead(dog); // ears up, big eyes: he's listening
      showFace(dog, "puppy", 90);
    }
  } else if (status === "nothing") {
    actionbar(player, T.VOICE_NOTHING);
  }
}

// alwaysOn: heard by the always-listening mode, so only react to things meant for Jerry:
// anything with his name in it, or a short command on its own ("sit", "roll over").
function onVoice(player, text, alwaysOn) {
  if (!text) return;
  if (alwaysOn) {
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
  for (const [name, token] of [[dogName(player), "jerry"], [catName(player), "mochi"]]) {
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
    .replace(/-/g, " ")
    .replace(/[,.!?;:]+/g, " | ")
    .replace(/\b(and then|then|after that|and)\b/g, " | ")
    .split("|")
    .map((part) => part.replace(/[^a-z' ]+/g, " ").replace(/\s+/g, " ").trim())
    .map((part) => (player ? normalizeNames(part, player) : part))
    .filter(Boolean);
  const entries = [];
  for (const part of parts) {
    const entry = matchVoice(part);
    if (entry && entries[entries.length - 1] !== entry) entries.push(entry);
  }
  return entries.slice(0, 4);
}

// How long each reaction takes, so the next command (or the talk box) waits for it.
const ACTION_TICKS = {
  roll: 56, spin: 34, play: 50, greet: 40, paw: 50, highfive: 50, wave: 50, beg: 50,
  dead: 75, lie: 25, bow: 45, dance: 75, backflip: 30, howl: 55, chase: 75,
};
const actionTicks = (entry) => ACTION_TICKS[entry.action] ?? 30;

// Returns how many ticks Jerry will be busy reacting (0 if he couldn't hear you).
function hearSpeech(player, said) {
  player.sendMessage(`§7You: §f"${said.slice(0, 80)}"`); // your own words, unchanged
  const dog = findDog(player);
  const commands = parseCommands(said, player);
  // "come" works from anywhere, like the whistle; everything else needs Jerry in earshot.
  const calledHim = commands.some((entry) => entry.action === "come");
  if (!dog || (!calledHim && !isNear(dog, player, 24))) {
    tell(player, T.CANT_HEAR);
    return 0;
  }
  // Like in the video: a speaker icon pops up over Jerry when he hears you, then he reacts.
  showSpeaker(dog, commands.some((entry) => entry.action === "bark") ? 70 : 45);
  if (!commands.length) {
    system.runTimeout(() => isValid(player) && isValid(dog) && respond(player, dog, undefined), 12);
    return 50;
  }
  let at = 12;
  for (const entry of commands) {
    system.runTimeout(() => {
      if (isValid(player) && isValid(dog)) respond(player, dog, entry);
    }, at);
    at += actionTicks(entry);
  }
  return at;
}

function respond(player, dog, entry) {
  if (!entry) {
    sound(dog.dimension, "mob.wolf.whine", dog.location);
    tiltHead(dog);
    showFace(dog, "puppy", 50);
    return tell(player, pick(T.VOICE_UNKNOWN));
  }
  if (entry.action === "play") return play(player);
  talkAction(player, dog, entry.action);
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
  tell(player, reply);
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
  }
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
    if (id === "jf:voice_hello") return onVoiceHello();
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
  },
  { namespaces: ["jf"] }
);

// ---------------------------------------------------------------- events

system.runInterval(() => {
  seconds++;
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
  }, 100);
});

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (!itemStack || !OUR_ITEMS.has(itemStack.typeId) || source?.typeId !== "minecraft:player") return;
  useItem(source, itemStack.typeId);
});

// Using one of our items on a pet should not make it sit down; an empty hand pets Jerry.
world.beforeEvents.playerInteractWithEntity?.subscribe((event) => {
  const id = event.itemStack?.typeId;
  if (!id && event.target.typeId === JERRY) {
    // Empty hand: tap to pet him, or sneak + tap to start talking.
    const { player, target } = event;
    const talk = player.isSneaking;
    system.run(() => {
      if (!isValid(player) || !isValid(target) || !target.hasTag(ownerTag(player))) return;
      if (talk) startTalking(player);
      else petJerry(player, target);
    });
    return;
  }
  if (!id || !OUR_ITEMS.has(id)) return;
  event.cancel = true;
  const { player, target } = event;
  system.run(() => {
    if (isValid(player)) useItem(player, id, target);
  });
});

// Clean up leftover copies (e.g. after the whistle respawned a pet that was in an unloaded chunk).
world.afterEvents.entityLoad?.subscribe(({ entity }) => {
  if (!isValid(entity) || !entity.hasTag(TAG.pet)) return;
  const owner = world.getAllPlayers().find((p) => entity.hasTag(ownerTag(p)));
  if (!owner) return;
  const current = owner.getDynamicProperty(entity.hasTag(TAG.cat) ? PROP.cat : PROP.dog);
  if (typeof current === "string" && current !== entity.id) entity.remove();
});
