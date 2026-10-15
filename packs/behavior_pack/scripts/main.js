import { world, system, ItemStack } from "@minecraft/server";
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
};
const OUR_ITEMS = new Set(Object.values(ITEMS));

const PROP = {
  started: "jf:started",
  friendship: "jf:friendship",
  dog: "jf:dog_id",
  cat: "jf:cat_id",
  hasCat: "jf:has_cat",
};
const TAG = { pet: "jf_pet", dog: "jf_dog", cat: "jf_cat" };
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
const COLLAR_RED = 14;

// Friendship goes 0-100. Level thresholds line up with T.LEVELS.
const MAX_FRIENDSHIP = 100;
const LEVEL_MIN = [0, 25, 60, 100];
const CAT_LEVEL = 1;
const GIFT_LEVEL = 2;
const GAIN = { treat: 5, brush: 3, ball: 4, together: 1 };
const COOLDOWN = { treat: 60, brush: 30, ball: 45 }; // seconds before an action earns friendship again

let seconds = 0;
const cooldowns = new Map(); // "playerId:action" -> seconds when it earns friendship again
const nextMoment = new Map();
const nextGift = new Map();
const lastUse = new Map();
const pendingIntro = new Set();
const pendingCat = new Set();

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

const findDog = (player) => findPet(player, PROP.dog, TAG.dog);
const findCat = (player) => (player.getDynamicProperty(PROP.hasCat) ? findPet(player, PROP.cat, TAG.cat) : undefined);

function tame(pet, player) {
  try {
    if (pet.getComponent("minecraft:is_tamed")) return;
    pet.getComponent("minecraft:tameable")?.tame(player);
  } catch {}
}

function setCollar(pet, color) {
  try {
    const component = pet.getComponent("minecraft:color");
    if (component) component.value = color;
  } catch {}
}

// Your pets can't get hurt, so you never lose them.
function protect(pet) {
  try {
    pet.addEffect("resistance", 600, { amplifier: 255, showParticles: false });
    pet.addEffect("fire_resistance", 600, { amplifier: 0, showParticles: false });
  } catch {}
}

function spawnPet(player, typeId, role, name, prop, collar) {
  const pet = player.dimension.spawnEntity(typeId, player.location);
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
  // Taming finishes on the next ticks; the collar only exists once tamed.
  system.runTimeout(() => {
    if (!isValid(pet) || !isValid(player)) return;
    tame(pet, player);
    if (collar !== undefined) setCollar(pet, collar);
  }, 5);
  return pet;
}

const spawnJerry = (player) => spawnPet(player, "minecraft:wolf", TAG.dog, "Jerry", PROP.dog, COLLAR_RED);
const spawnMochi = (player) => spawnPet(player, "minecraft:cat", TAG.cat, "Mochi", PROP.cat);

function bring(pet, player) {
  try {
    pet.teleport(player.location, { dimension: player.dimension });
  } catch {}
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
  if (dog) bring(dog, player);
  else spawnJerry(player);
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
  if (itemId === ITEMS.ball) return play(player);
  target = isValid(target) ? target : lookTarget(player);
  if (itemId === ITEMS.treat) return giveTreat(player, target);
  if (itemId === ITEMS.brush) return brush(player, target);
}

// ---------------------------------------------------------------- commands

function resetPets(player) {
  removeAllPets(player);
  for (const key of Object.values(PROP)) player.setDynamicProperty(key, undefined);
  nextGift.delete(player.id);
  startIntro(player);
}

system.afterEvents.scriptEventReceive.subscribe(
  ({ id, sourceEntity: player }) => {
    if (player?.typeId !== "minecraft:player") return;
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
    if (isValid(player)) player.sendMessage(T.WELCOME_BACK);
  }, 100);
});

world.afterEvents.itemUse.subscribe(({ source, itemStack }) => {
  if (!itemStack || !OUR_ITEMS.has(itemStack.typeId) || source?.typeId !== "minecraft:player") return;
  useItem(source, itemStack.typeId);
});

// Using one of our items on a pet should not make it sit down.
world.beforeEvents.playerInteractWithEntity?.subscribe((event) => {
  const id = event.itemStack?.typeId;
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
