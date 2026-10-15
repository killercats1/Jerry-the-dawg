import { world, system, ItemStack } from "@minecraft/server";
import * as T from "./text.js";

// ---------------------------------------------------------------------------
// Story: Jerry (a tamed wolf) gets secretly swapped for Copycat, a shapeshifting
// cat. The player spots the quirks, collects clues with the magnifier, unmasks
// the imposter with a treat, then follows paw prints to rescue the real Jerry.
// Every player has their own story, saved in dynamic properties.
// ---------------------------------------------------------------------------

const ITEMS = {
  whistle: "srmd:dog_whistle",
  magnifier: "srmd:magnifier",
  treat: "srmd:dog_treat",
};
const OUR_ITEMS = new Set(Object.values(ITEMS));

const STAGE = { NEW: 0, HAPPY: 1, SUSPICIOUS: 2, CONFRONT: 3, RESCUE: 4, DONE: 5 };
const STAGE_NAMES = ["Not started", "Happy days", "Something seems off", "Confront the imposter", "Find Jerry", "The End"];

// Timing, in seconds of play time.
const FORESHADOW_AT = 120;
const SWAP_AFTER = 180; // earliest swap (waits for night)
const SWAP_FORCE_AFTER = 420; // swap even during the day
const CLUES_NEEDED = 3;

const PROP = {
  stage: "srmd:stage",
  timer: "srmd:timer",
  clueMask: "srmd:clue_mask",
  dog: "srmd:dog_id",
  cat: "srmd:cat_id",
  house: "srmd:house",
};
const TAG = { pet: "srmd_pet", real: "srmd_real", fake: "srmd_fake", cat: "srmd_cat" };
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
const COLLAR_RED = 14;
const COLLAR_ORANGE = 1;

let seconds = 0;
const nextMoment = new Map(); // player id -> seconds when the next quirk/moment may happen
const lastQuirk = new Map();
const lastUse = new Map();
const rescueReadyAt = new Map(); // waits for Copycat's confession to finish
const pendingIntro = new Set();

// ---------------------------------------------------------------- helpers

const rand = (min, max) => min + Math.random() * (max - min);
const pick = (list) => list[Math.floor(Math.random() * list.length)];

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

const getStage = (player) => getNum(player, PROP.stage);
const setStage = (player, stage) => player.setDynamicProperty(PROP.stage, stage);
const ownerTag = (player) => `srmd_owner_${player.id}`;
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);

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

function puff(dim, at, id = "minecraft:totem_particle", count = 12) {
  for (let i = 0; i < count; i++) {
    particle(dim, id, { x: at.x + rand(-0.6, 0.6), y: at.y + rand(0.2, 1.2), z: at.z + rand(-0.6, 0.6) });
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

// Sends lines one at a time like a conversation, then calls `done`.
function say(player, lines, gap = 60, done) {
  lines.forEach((line, i) =>
    system.runTimeout(() => {
      if (isValid(player)) player.sendMessage(line);
    }, i * gap)
  );
  if (done) {
    system.runTimeout(() => {
      if (isValid(player)) done();
    }, lines.length * gap);
  }
}

function compass(dx, dz) {
  const names = ["north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"];
  const angle = (Math.atan2(dx, -dz) * 180) / Math.PI; // 0 = north (-Z), 90 = east (+X)
  return names[(Math.round(angle / 45) + 8) % 8];
}

function inView(player, entity) {
  const head = player.getHeadLocation();
  const view = player.getViewDirection();
  const to = { x: entity.location.x - head.x, y: entity.location.y + 0.4 - head.y, z: entity.location.z - head.z };
  const length = Math.hypot(to.x, to.y, to.z) || 1;
  return (to.x * view.x + to.y * view.y + to.z * view.z) / length > 0.35;
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

function takeItem(player, id) {
  const inv = inventory(player);
  if (!inv) return;
  for (let i = 0; i < inv.size; i++) if (inv.getItem(i)?.typeId === id) inv.setItem(i, undefined);
}

// ---------------------------------------------------------------- pets

function dogRole(stage) {
  if (stage === STAGE.HAPPY || stage === STAGE.DONE) return TAG.real;
  if (stage === STAGE.SUSPICIOUS || stage === STAGE.CONFRONT) return TAG.fake;
  return undefined;
}

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

// Story pets can't be hurt, so the story can't get stuck on a dead dog.
function protect(pet) {
  try {
    pet.addEffect("resistance", 600, { amplifier: 255, showParticles: false });
    pet.addEffect("fire_resistance", 600, { amplifier: 0, showParticles: false });
  } catch {}
}

function spawnPet(player, typeId, role, name, location, collar) {
  const pet = player.dimension.spawnEntity(typeId, location);
  try {
    if (pet.getComponent("minecraft:is_baby")) pet.triggerEvent("minecraft:ageable_grow_up");
  } catch {}
  pet.nameTag = name;
  pet.addTag(TAG.pet);
  pet.addTag(role);
  pet.addTag(ownerTag(player));
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

function protectAll(player) {
  const role = dogRole(getStage(player));
  const dog = role && findPet(player, PROP.dog, role);
  if (dog) protect(dog);
  const cat = getStage(player) >= STAGE.RESCUE && findPet(player, PROP.cat, TAG.cat);
  if (cat) protect(cat);
}

// ---------------------------------------------------------------- chapter 1: happy days

function scheduleIntro(player, delay) {
  if (pendingIntro.has(player.id)) return;
  pendingIntro.add(player.id);
  system.runTimeout(() => {
    pendingIntro.delete(player.id);
    if (isValid(player) && getStage(player) === STAGE.NEW) startIntro(player);
  }, delay);
}

function startIntro(player) {
  removeAllPets(player);
  const jerry = spawnPet(player, "minecraft:wolf", TAG.real, "Jerry", player.location, COLLAR_RED);
  player.setDynamicProperty(PROP.dog, jerry.id);
  player.setDynamicProperty(PROP.timer, 0);
  setStage(player, STAGE.HAPPY);
  give(player, ITEMS.whistle);
  title(player, T.TITLE, "§fCozy Edition");
  sound(player.dimension, "mob.wolf.bark", player.location);
  say(player, T.INTRO, 80);
  nextMoment.set(player.id, seconds + 30);
}

function tickHappy(player) {
  const timer = getNum(player, PROP.timer) + 1;
  player.setDynamicProperty(PROP.timer, timer);
  const jerry = findPet(player, PROP.dog, TAG.real);

  if (timer === FORESHADOW_AT) {
    const p = player.location;
    sound(player.dimension, "mob.cat.straymeow", { x: p.x + rand(-8, 8), y: p.y, z: p.z + rand(-8, 8) });
    actionbar(player, T.FORESHADOW);
    return;
  }
  if (timer >= SWAP_AFTER && (isNight() || timer >= SWAP_FORCE_AFTER) && trySwap(player, jerry)) return;

  if (!jerry || jerry.dimension.id !== player.dimension.id || dist(jerry.location, player.location) > 16) return;
  if (seconds < (nextMoment.get(player.id) ?? 0)) return;
  nextMoment.set(player.id, seconds + Math.floor(rand(35, 60)));
  const moment = pick(T.HAPPY_MOMENTS);
  sound(jerry.dimension, moment.sound, jerry.location);
  if (moment.action === "hop") {
    try {
      jerry.applyImpulse({ x: 0, y: 0.5, z: 0 });
    } catch {}
  }
  actionbar(player, moment.text);
}

// ---------------------------------------------------------------- the swap

// The swap only happens while the player isn't watching Jerry (unless forced).
function trySwap(player, jerry, force = false) {
  if (!jerry || jerry.dimension.id !== player.dimension.id) {
    if (!force) return false;
    if (isValid(jerry)) jerry.remove();
    doSwap(player, player.location);
    return true;
  }
  const d = dist(player.location, jerry.location);
  if (!force && (d > 40 || (d < 20 && inView(player, jerry)))) return false;
  const location = jerry.location;
  jerry.remove();
  doSwap(player, location);
  return true;
}

function doSwap(player, location) {
  const fake = spawnPet(player, "minecraft:wolf", TAG.fake, "Jerry", location, COLLAR_ORANGE);
  player.setDynamicProperty(PROP.dog, fake.id);
  player.setDynamicProperty(PROP.clueMask, 0);
  setStage(player, STAGE.SUSPICIOUS);
  puff(player.dimension, location);
  sound(player.dimension, "random.pop", location);
  system.runTimeout(() => {
    if (isValid(player)) actionbar(player, T.SWAP_ACTIONBAR);
  }, 30);
  system.runTimeout(() => {
    if (isValid(player)) say(player, T.SWAP_LINES, 80, () => give(player, ITEMS.magnifier));
  }, 200);
  nextMoment.set(player.id, seconds + 25);
}

// ---------------------------------------------------------------- chapter 2: something seems off

function tickSuspicious(player) {
  const fake = findPet(player, PROP.dog, TAG.fake);
  if (!fake || fake.dimension.id !== player.dimension.id || dist(fake.location, player.location) > 16) return;
  if (seconds < (nextMoment.get(player.id) ?? 0)) return;
  nextMoment.set(player.id, seconds + Math.floor(rand(15, 32)));

  const options = T.QUIRKS.filter((q) => q !== lastQuirk.get(player.id));
  const quirk = pick(options);
  lastQuirk.set(player.id, quirk);
  doQuirk(player, fake, quirk);
}

function doQuirk(player, fake, quirk) {
  const dim = fake.dimension;
  if (quirk.sound) sound(dim, quirk.sound, fake.location);
  try {
    switch (quirk.action) {
      case "name":
        fake.nameTag = pick(T.NAME_GLITCHES);
        system.runTimeout(() => {
          if (isValid(fake)) fake.nameTag = "Jerry";
        }, 60);
        break;
      case "fish":
        dim.spawnItem(new ItemStack("minecraft:cod", 1), player.location);
        break;
      case "pounce":
        fake.applyImpulse({ x: 0, y: 0.55, z: 0 });
        break;
      case "spin":
        for (let i = 1; i <= 12; i++) {
          system.runTimeout(() => {
            if (!isValid(fake)) return;
            const rotation = fake.getRotation();
            fake.teleport(fake.location, { rotation: { x: rotation.x, y: rotation.y + 30 } });
          }, i * 2);
        }
        break;
    }
  } catch {}
  actionbar(player, quirk.text);
}

function inspectFake(player, fake) {
  if (getStage(player) === STAGE.CONFRONT) {
    actionbar(player, T.ALREADY_SURE);
    return;
  }
  let mask = getNum(player, PROP.clueMask);
  const remaining = T.CLUES.map((_, i) => i).filter((i) => !(mask & (1 << i)));
  const clue = pick(remaining);
  mask |= 1 << clue;
  player.setDynamicProperty(PROP.clueMask, mask);
  const found = T.CLUES.length - remaining.length + 1;

  puff(fake.dimension, fake.location, "minecraft:villager_happy", 6);
  sound(player.dimension, "random.orb", player.location);
  player.sendMessage(`§eClue ${found}/${CLUES_NEEDED}: §f${T.CLUES[clue]}`);

  if (found >= CLUES_NEEDED) {
    setStage(player, STAGE.CONFRONT);
    title(player, "§cThat's not Jerry!", "§f...probably.");
    say(player, T.CLUES_DONE, 70, () => give(player, ITEMS.treat));
  }
}

// ---------------------------------------------------------------- chapter 3: the reveal

function reveal(player, fake) {
  setStage(player, STAGE.RESCUE);
  rescueReadyAt.set(player.id, Infinity); // paw prints start once Copycat finishes talking
  actionbar(player, T.TREAT_SNIFF);

  const dim = fake.dimension;
  let at = fake.location;
  system.runTimeout(() => {
    if (!isValid(player)) return;
    if (isValid(fake)) {
      at = fake.location;
      fake.remove();
    }
    puff(dim, at, "minecraft:totem_particle", 24);
    sound(dim, "random.pop", at);
    sound(dim, "mob.cat.meow", at);
    const cat = spawnPet(player, "minecraft:cat", TAG.cat, "Copycat", at);
    player.setDynamicProperty(PROP.cat, cat.id);
    player.setDynamicProperty(PROP.dog, undefined);
    takeItem(player, ITEMS.treat);
    title(player, "§6It was a CAT!", "§fThe whole time?!");
    say(player, T.REVEAL, 90, () => {
      rescueReadyAt.delete(player.id);
      if (!getHouse(player)) planHouse(player);
      say(player, T.RESCUE_START, 70);
    });
  }, 50);
}

// ---------------------------------------------------------------- chapter 4: find Jerry

function getHouse(player) {
  try {
    const raw = player.getDynamicProperty(PROP.house);
    return typeof raw === "string" ? JSON.parse(raw) : undefined;
  } catch {
    return undefined;
  }
}

const saveHouse = (player, house) => player.setDynamicProperty(PROP.house, JSON.stringify(house));

function findGround(dim, x, startY, z) {
  for (let y = startY + 16; y >= startY - 24; y--) {
    let here, below, above;
    try {
      here = dim.getBlock({ x, y, z });
      below = dim.getBlock({ x, y: y - 1, z });
      above = dim.getBlock({ x, y: y + 1, z });
    } catch {
      continue; // outside the world's height limits
    }
    if (!here || !below || !above) return undefined; // chunk not loaded
    if (below.typeId.includes("leaves")) continue; // no dog houses in trees
    if (here.isAir && above.isAir && !below.isAir && !below.isLiquid) return y;
  }
  return undefined;
}

function planHouse(player) {
  const dim = player.dimension;
  const p = player.location;
  let spot;
  for (let attempt = 0; attempt < 16 && !spot; attempt++) {
    const angle = Math.random() * Math.PI * 2;
    const radius = rand(22, 32);
    const x = Math.floor(p.x + Math.cos(angle) * radius);
    const z = Math.floor(p.z + Math.sin(angle) * radius);
    const y = findGround(dim, x, Math.floor(p.y), z);
    if (y !== undefined) spot = { x, y, z };
  }
  if (!spot) spot = { x: Math.floor(p.x) + 24, y: Math.floor(p.y), z: Math.floor(p.z) };

  // Point the doorway back toward the player.
  const dx = p.x - spot.x;
  const dz = p.z - spot.z;
  const door = Math.abs(dx) > Math.abs(dz) ? { x: Math.sign(dx), z: 0 } : { x: 0, z: Math.sign(dz) || 1 };
  const house = { ...spot, dim: dim.id, door, built: false };
  saveHouse(player, house);
  return house;
}

function setBlock(dim, location, type) {
  const block = dim.getBlock(location);
  if (!block) throw new Error("chunk not loaded");
  if (typeof block.setType === "function") block.setType(type);
  else dim.runCommandAsync(`setblock ${location.x} ${location.y} ${location.z} ${type}`);
}

// A 5x5 plank dog house with a red roof and a bone block on top as a landmark.
function buildHouse(dim, h) {
  const set = (dx, dy, dz, type) => setBlock(dim, { x: h.x + dx, y: h.y + dy, z: h.z + dz }, type);
  try {
    for (let dx = -2; dx <= 2; dx++) {
      for (let dz = -2; dz <= 2; dz++) {
        const edge = Math.abs(dx) === 2 || Math.abs(dz) === 2;
        const isDoor = (h.door.x !== 0 && dx === 2 * h.door.x && dz === 0) || (h.door.z !== 0 && dz === 2 * h.door.z && dx === 0);
        set(dx, -1, dz, "minecraft:oak_planks");
        for (let dy = 0; dy <= 1; dy++) set(dx, dy, dz, edge && !isDoor ? "minecraft:oak_planks" : "minecraft:air");
        if (!edge) set(dx, 0, dz, "minecraft:yellow_carpet");
        set(dx, 2, dz, "minecraft:red_wool");
        set(dx, 3, dz, Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? "minecraft:red_wool" : "minecraft:air");
      }
    }
    set(0, 4, 0, "minecraft:red_wool");
    set(0, 5, 0, "minecraft:bone_block");
    return true;
  } catch (error) {
    console.warn(`[srmd] dog house not built yet: ${error}`);
    return false;
  }
}

function tickRescue(player) {
  if (seconds < (rescueReadyAt.get(player.id) ?? 0)) return;
  const house = getHouse(player) ?? planHouse(player);
  if (player.dimension.id !== house.dim) {
    actionbar(player, T.WRONG_DIMENSION);
    return;
  }
  const dim = player.dimension;
  if (!house.built && buildHouse(dim, house)) {
    house.built = true;
    saveHouse(player, house);
  }

  const p = player.location;
  const dx = house.x + 0.5 - p.x;
  const dz = house.z + 0.5 - p.z;
  const d = Math.hypot(dx, dz);
  if (d < 4.5 && Math.abs(house.y - p.y) < 6) {
    reunion(player, { x: house.x + 0.5, y: house.y, z: house.z + 0.5 });
    return;
  }

  // Paw prints: a trail of sparkles leading toward the dog house.
  for (let step = 2; step <= Math.min(10, d - 1); step += 2) {
    particle(dim, "minecraft:villager_happy", { x: p.x + (dx / d) * step, y: p.y + 0.3, z: p.z + (dz / d) * step });
  }
  actionbar(player, `§ePaw prints lead §f${compass(dx, dz)} §7- ${Math.round(d)} blocks`);
  if (d < 20 && seconds % 3 === 0) sound(dim, "mob.wolf.panting", { x: house.x + 0.5, y: house.y + 0.5, z: house.z + 0.5 });
}

// ---------------------------------------------------------------- chapter 5: the reunion

function reunion(player, at) {
  setStage(player, STAGE.DONE);
  const dim = player.dimension;
  const jerry = spawnPet(player, "minecraft:wolf", TAG.real, "Jerry", at, COLLAR_RED);
  player.setDynamicProperty(PROP.dog, jerry.id);
  for (let i = 0; i < 6; i++) {
    system.runTimeout(() => {
      if (isValid(jerry)) puff(dim, jerry.location, "minecraft:heart_particle", 4);
    }, i * 10);
  }
  sound(dim, "mob.wolf.bark", at);
  sound(dim, "random.levelup", player.location);
  title(player, "§aJERRY!", "§fYou found him!");
  say(player, T.REUNION, 90);
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

function whistle(player) {
  sound(player.dimension, "note.flute", player.location);
  const stage = getStage(player);
  if (stage === STAGE.NEW) return;
  const came = [];

  const role = dogRole(stage);
  if (role) {
    const dog = findPet(player, PROP.dog, role);
    if (dog) bring(dog, player);
    else {
      const collar = role === TAG.real ? COLLAR_RED : COLLAR_ORANGE;
      player.setDynamicProperty(PROP.dog, spawnPet(player, "minecraft:wolf", role, "Jerry", player.location, collar).id);
    }
    came.push("§bJerry");
  }
  if (stage >= STAGE.RESCUE) {
    const cat = findPet(player, PROP.cat, TAG.cat);
    if (cat) bring(cat, player);
    else player.setDynamicProperty(PROP.cat, spawnPet(player, "minecraft:cat", TAG.cat, "Copycat", player.location).id);
    came.push("§6Copycat");
  }

  if (stage === STAGE.RESCUE) actionbar(player, T.STILL_NAPPING);
  else actionbar(player, `${came.join(" §7and ")} §7came running!`);
}

function inspect(player, target) {
  sound(player.dimension, "item.spyglass.use", player.location);
  if (!target) return actionbar(player, T.LOOK_AT_JERRY);
  const mine = target.hasTag(ownerTag(player));
  if (mine && target.hasTag(TAG.fake)) return inspectFake(player, target);
  if (mine && target.hasTag(TAG.real)) return actionbar(player, pick(T.INSPECT_REAL));
  if (mine && target.hasTag(TAG.cat)) return actionbar(player, pick(T.INSPECT_CAT));
  if (target.typeId === "minecraft:wolf") return actionbar(player, T.INSPECT_WOLF);
  if (target.typeId === "minecraft:cat") return actionbar(player, T.INSPECT_CAT_OTHER);
  actionbar(player, T.INSPECT_OTHER);
}

function offerTreat(player, target) {
  if (!target || !target.hasTag(ownerTag(player))) return actionbar(player, T.TREAT_NOT_JERRY);
  if (target.hasTag(TAG.fake)) {
    if (getStage(player) === STAGE.CONFRONT) return reveal(player, target);
    return actionbar(player, T.TREAT_TOO_EARLY);
  }
  if (target.hasTag(TAG.real)) {
    puff(target.dimension, target.location, "minecraft:heart_particle", 5);
    sound(target.dimension, "mob.wolf.bark", target.location);
    return actionbar(player, pick(T.TREAT_REAL));
  }
  if (target.hasTag(TAG.cat)) return actionbar(player, T.TREAT_CAT);
  actionbar(player, T.TREAT_NOT_JERRY);
}

function useItem(player, itemId, target) {
  // Tapping an entity can fire both the interact and the item-use event.
  const now = system.currentTick;
  if (now - (lastUse.get(player.id) ?? -100) < 8) return;
  lastUse.set(player.id, now);

  if (itemId === ITEMS.whistle) return whistle(player);
  target = isValid(target) ? target : lookTarget(player);
  if (itemId === ITEMS.magnifier) return inspect(player, target);
  if (itemId === ITEMS.treat) return offerTreat(player, target);
}

// ---------------------------------------------------------------- testing commands

function resetStory(player) {
  removeAllPets(player);
  for (const key of Object.values(PROP)) player.setDynamicProperty(key, undefined);
  takeItem(player, ITEMS.magnifier);
  takeItem(player, ITEMS.treat);
  rescueReadyAt.delete(player.id);
  startIntro(player);
}

function skipChapter(player) {
  switch (getStage(player)) {
    case STAGE.NEW:
      return startIntro(player);
    case STAGE.HAPPY:
      trySwap(player, findPet(player, PROP.dog, TAG.real), true);
      return give(player, ITEMS.magnifier);
    case STAGE.SUSPICIOUS:
      player.setDynamicProperty(PROP.clueMask, 0b111);
      setStage(player, STAGE.CONFRONT);
      return say(player, T.CLUES_DONE, 40, () => give(player, ITEMS.treat));
    case STAGE.CONFRONT: {
      const fake = findPet(player, PROP.dog, TAG.fake) ?? spawnPet(player, "minecraft:wolf", TAG.fake, "Jerry", player.location);
      return reveal(player, fake);
    }
    case STAGE.RESCUE:
      rescueReadyAt.delete(player.id);
      return reunion(player, player.location);
    default:
      player.sendMessage("§7The story is complete! Use §f/scriptevent srmd:reset§7 to replay.");
  }
}

function status(player) {
  const stage = getStage(player);
  const clues = getNum(player, PROP.clueMask).toString(2).split("1").length - 1;
  player.sendMessage(
    `§6[SRMD]§f Chapter ${stage}: ${STAGE_NAMES[stage] ?? "?"} §7| play time ${getNum(player, PROP.timer)}s | clues ${clues}/${CLUES_NEEDED}`
  );
}

// ---------------------------------------------------------------- events

function tick(player) {
  const stage = getStage(player);
  if (stage === STAGE.NEW) return scheduleIntro(player, 60);
  if (seconds % 10 === 0) protectAll(player);
  if (stage === STAGE.HAPPY) tickHappy(player);
  else if (stage === STAGE.SUSPICIOUS || stage === STAGE.CONFRONT) tickSuspicious(player);
  else if (stage === STAGE.RESCUE) tickRescue(player);
}

system.runInterval(() => {
  seconds++;
  for (const player of world.getAllPlayers()) {
    try {
      tick(player);
    } catch (error) {
      console.warn(`[srmd] ${error}`);
    }
  }
}, 20);

world.afterEvents.playerSpawn.subscribe(({ player, initialSpawn }) => {
  if (!initialSpawn) return;
  rescueReadyAt.delete(player.id); // in case they left mid-dialogue
  const stage = getStage(player);
  if (stage === STAGE.NEW) return scheduleIntro(player, 100);
  system.runTimeout(() => {
    if (isValid(player)) player.sendMessage(`§6[Something Replaced My Dog]§7 Chapter: §f${STAGE_NAMES[stage]}`);
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
  const stage = getStage(owner);
  const isCat = entity.hasTag(TAG.cat);
  const role = isCat ? TAG.cat : entity.hasTag(TAG.fake) ? TAG.fake : TAG.real;
  const expected = isCat ? stage >= STAGE.RESCUE : role === dogRole(stage);
  const current = owner.getDynamicProperty(isCat ? PROP.cat : PROP.dog);
  if (!expected || (typeof current === "string" && current !== entity.id)) entity.remove();
});

system.afterEvents.scriptEventReceive.subscribe(
  ({ id, sourceEntity }) => {
    if (sourceEntity?.typeId !== "minecraft:player") return;
    if (id === "srmd:reset") resetStory(sourceEntity);
    else if (id === "srmd:skip") skipChapter(sourceEntity);
    else if (id === "srmd:status") status(sourceEntity);
  },
  { namespaces: ["srmd"] }
);
