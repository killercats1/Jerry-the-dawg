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
};
const TAG = { pet: "jf_pet", dog: "jf_dog", cat: "jf_cat" };
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
const JERRY = "jf:jerry";
const POSE = { stand: 0, sit: 1, roll: 2, tilt: 3 }; // matches the jf:pose property in entities/jerry.json

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
const speakerTimers = new Map();
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

function sound(dim, id, location) {
  try {
    dim.playSound(id, location);
  } catch {
    try {
      world.playSound(id, location);
    } catch {}
  }
}

function sparkle(pet, id = "minecraft:heart_particle", count = 5) {
  const at = pet.location;
  for (let i = 0; i < count; i++) {
    particle(pet.dimension, id, { x: at.x + rand(-0.5, 0.5), y: at.y + rand(0.5, 1.3), z: at.z + rand(-0.5, 0.5) });
  }
}

function actionbar(player, text) {
  try {
    player.onScreenDisplay.setActionBar(text);
  } catch {}
}

function title(player, main, subtitle) {
  try {
    player.onScreenDisplay.setTitle(main, { subtitle, fadeInDuration: 10, stayDuration: 70, fadeOutDuration: 20 });
  } catch {}
}

function say(player, lines, gap = 70) {
  lines.forEach((line, i) =>
    system.runTimeout(() => {
      if (isValid(player)) player.sendMessage(line);
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
    title(player, "§aFriendship up!", `§fJerry is now your §e${T.LEVELS[level]}`);
    sound(player.dimension, "random.levelup", player.location);
    system.runTimeout(() => {
      if (isValid(player)) player.sendMessage(T.LEVEL_UP[level]);
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

const spawnJerry = (player, at) => spawnPet(player, JERRY, TAG.dog, "Jerry", PROP.dog, at);
const spawnMochi = (player) => spawnPet(player, "minecraft:cat", TAG.cat, "Mochi", PROP.cat);

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

const isSitting = (dog) => dog.getDynamicProperty("jf:sitting") === true;

function sit(dog) {
  try {
    dog.triggerEvent("jf:sit"); // stops following and wandering
  } catch {}
  dog.setDynamicProperty("jf:sitting", true);
  setPose(dog, POSE.sit);
}

function stand(dog) {
  try {
    dog.triggerEvent("jf:stand");
  } catch {}
  dog.setDynamicProperty("jf:sitting", undefined);
  setPose(dog, POSE.stand);
}

// A short trick (roll over, head tilt), then back to sitting or standing.
function trick(dog, pose, ticks) {
  const token = (poseTimers.get(dog.id) ?? 0) + 1;
  poseTimers.set(dog.id, token);
  const holdStill = pose === POSE.roll && !isSitting(dog);
  if (holdStill) {
    try {
      dog.triggerEvent("jf:sit");
    } catch {}
  }
  setPose(dog, pose);
  system.runTimeout(() => {
    if (!isValid(dog) || poseTimers.get(dog.id) !== token) return;
    if (holdStill && !isSitting(dog)) {
      try {
        dog.triggerEvent("jf:stand");
      } catch {}
    }
    setPose(dog, isSitting(dog) ? POSE.sit : POSE.stand);
  }, ticks);
}

const rollOver = (dog) => trick(dog, POSE.roll, 50);
const tiltHead = (dog) => trick(dog, POSE.tilt, 36);

// The speaker icon that pops up over Jerry's head when he hears you.
function showSpeaker(dog, ticks) {
  const token = (speakerTimers.get(dog.id) ?? 0) + 1;
  speakerTimers.set(dog.id, token);
  try {
    dog.setProperty("jf:speaking", true);
  } catch {}
  system.runTimeout(() => {
    if (!isValid(dog) || speakerTimers.get(dog.id) !== token) return;
    try {
      dog.setProperty("jf:speaking", false);
    } catch {}
  }, ticks);
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
  say(player, T.INTRO, 80);
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
  actionbar(player, moment.text);
}

function bringGift(player, dog) {
  const gift = pick(T.GIFTS);
  try {
    dog.dimension.spawnItem(new ItemStack(gift.item, 1), dog.location);
  } catch {}
  sound(dog.dimension, "mob.wolf.bark", dog.location);
  sparkle(dog, "minecraft:villager_happy", 6);
  player.sendMessage(`§bJerry§7 brought you a gift: §f${gift.name}§7! He's very proud.`);
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

  if (itemId === ITEMS.whistle) return whistle(player);
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
    player.sendMessage(T.VOICE_LOST);
    openTalk(player);
  }, 50);
}

function onVoiceHello() {
  const wasConnected = voiceConnected();
  voiceHelper.lastSeen = seconds;
  if (wasConnected) return;
  for (const player of world.getAllPlayers()) {
    player.sendMessage(T.VOICE_CONNECTED);
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
    if (dog && isNear(dog, player, 24)) tiltHead(dog); // ears up, he's listening
  } else if (status === "nothing") {
    actionbar(player, T.VOICE_NOTHING);
  }
}

// alwaysOn: heard by the always-listening mode, so only react to things meant for Jerry:
// anything with his name in it, or a short command on its own ("sit", "roll over").
function onVoice(player, text, alwaysOn) {
  if (!text) return;
  if (alwaysOn) {
    const words = cleanWords(text.toLowerCase().replace(/[^a-z' ]+/g, " "));
    const saidName = /\bjerry\b/.test(words);
    const shortCommand = words.split(" ").filter(Boolean).length <= 3 && parseCommands(text).length > 0;
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

function matchVoice(part) {
  const text = ` ${cleanWords(part)} `;
  return T.VOICE.find((entry) => entry.words.some((word) => text.includes(` ${word} `)));
}

// "sit, then roll over and speak" -> [sit, roll, bark]
function parseCommands(said) {
  const parts = said
    .toLowerCase()
    .replace(/-/g, " ")
    .replace(/[,.!?;:]+/g, " | ")
    .replace(/\b(and then|then|after that|and)\b/g, " | ")
    .split("|")
    .map((part) => part.replace(/[^a-z' ]+/g, " ").replace(/\s+/g, " ").trim())
    .filter(Boolean);
  const entries = [];
  for (const part of parts) {
    const entry = matchVoice(part);
    if (entry && entries[entries.length - 1] !== entry) entries.push(entry);
  }
  return entries.slice(0, 4);
}

// How long each reaction takes, so the next command (or the talk box) waits for it.
const ACTION_TICKS = { roll: 56, spin: 34, play: 50, greet: 40 };
const actionTicks = (entry) => ACTION_TICKS[entry.action] ?? 30;

// Returns how many ticks Jerry will be busy reacting (0 if he couldn't hear you).
function hearSpeech(player, said) {
  player.sendMessage(`§7You: §f"${said.slice(0, 80)}"`);
  const dog = findDog(player);
  const commands = parseCommands(said);
  // "come" works from anywhere, like the whistle; everything else needs Jerry in earshot.
  const calledHim = commands.some((entry) => entry.action === "come");
  if (!dog || (!calledHim && !isNear(dog, player, 24))) {
    player.sendMessage(T.CANT_HEAR);
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
    return player.sendMessage(pick(T.VOICE_UNKNOWN));
  }
  if (entry.action === "play") return play(player);
  talkAction(player, dog, entry.action);
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
  player.sendMessage(reply);
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
    if (id === "jf:status") player.sendMessage(`§6[Jerry & Friends]§f Friendship: ${friendshipBar(player)} §7(${getNum(player, PROP.friendship)}/100)`);
    else if (id === "jf:reset") resetPets(player);
    else if (id === "jf:boost") addFriendship(player, 25);
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
    player.sendMessage(T.WELCOME_BACK);
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
