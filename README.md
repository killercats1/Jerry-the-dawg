# Jerry the Dawg: Java 1.8.8 edition 🐶

The **"lesser version"** of Jerry the Dawg, as a **Spigot/Paper plugin for Minecraft Java Edition 1.8.8**.
It's separate from the Bedrock add-on (that's on the `MAIN` branch): different code, different history.

Every player gets a tamed wolf they can **name anything**, teach **tricks**, feed, brush and play with.
Friendship unlocks **Mochi the cat**, **gifts** and finally **a puppy**.

## A smart, living dog 🧠

- **Stays with you.** Vanilla 1.8.8 pets only loosely follow you and run off to fight things. Here
  they stay close: if they drift more than 6 blocks, they **walk back using Minecraft's real
  pathfinding**, and they don't run off to attack stuff. "Come" makes him walk (or run) over.
- **Needs and moods.** He gets **hungry**, **sleepy** and **bored** over time. Treats fill him up,
  sitting recharges him, and fetch keeps him happy. A happy dog makes friends twice as fast.
  Nothing bad ever happens to him.
- **Practice makes perfect.** New tricks are sloppy at first (sometimes a spin instead of a roll over).
  After 5 good tries he **masters** them. `/dog tricks` shows his progress.
- **Real fetch.** Right-click with the **Tennis Ball** to throw it: he runs to it, carries it back (the ball
  bobs along over his head) and drops it at your feet. The thrown ball can't be picked up, burned,
  pricked by cactus, sucked into a hopper or despawn mid-game.
- **Collar colours.** Use any dye on him, like a normal wolf. The colour sticks, even after the whistle.
- **His own bed and food bowl.** Place the **Dog Bed** and **Food Bowl** somewhere cozy. Fill the bowl with
  **Dog Treats** or any meat (right-click it): when he's hungry he walks over and eats. When he's sleepy
  (or it's night) he walks to his bed and naps on it, which recharges him twice as fast.
- **Guard dog.** He growls and warns you when a monster sneaks up.
- **Protects you.** If something attacks you, he goes after it (he can't get hurt). Creepers excepted,
  for everyone's sake. He doesn't pick fights on his own, and the cat just hisses.
- **He cares.** He rushes over if you get hurt, begs while you eat, does zoomies when you come back,
  and curls up by your bed when you sleep (Mochi and the puppy snuggle up too).
- **Status:** `/dog status` or **sneak + use the Dog Whistle** shows his mood, needs and friendship.

## Mochi the cat 🐱

Mochi joins at **Buddy**. She has her own personality: she purrs when you **sneak + right-click** her,
gets random zoomies, naps at night and **hisses at monsters**. Say her name to talk to her:
*"Mochi, high five!"* She knows sit, come, paw, high five, wave, beg, roll over, play dead, jump, spin,
chase her tail, shy, stretch, groom, purr, scratch, shake off, catch, sneeze, go to sleep and hug (she allows
it for about three seconds, then headbutts you). She practices them, like Jerry. Ask her for math or
anything else very dog-like and she'll just lick her paw. Sometimes she brings you a gift too (string,
a feather or a fish), and `/dog catcoat ginger|black|siamese` picks her coat.

## The puppy 🐾

At **Inseparable** a puppy joins the family: a little wolf pup called **Pip** (rename him with
`/dog pupname <name>` or a name tag). He follows you, comes with the whistle, never fights, and tries
to copy whatever trick Jerry does (not always successfully), always naps when Jerry does, and barks
along to Jerry's math (usually off by one). Talk to him by name: *"Pip, sit"*.

## What's different from the Bedrock version

Java 1.8.8 can't add custom models or animations, so this version uses vanilla wolf moves, sounds and
particles:

- **No new poses or face expressions.** Tricks show up as hearts, hops, spins, sitting, the wet-dog
  shake, a real wolf **howl**, music notes and so on.
- **No microphone voice.** But you can **talk to him in chat**, which Java servers make easy:
  *"Biscuit, sit then shake!"*
- Tame-style items are named vanilla items (they still work like the Bedrock ones).

## Install

1. Download `JerryTheDawg-x.x.x.jar` from this repo's
   [Releases](../../releases) (the newest **"Jerry the Dawg for Java 1.8.8"** build).
2. Put it in your **1.8.8 Spigot or Paper server's** `plugins` folder (Eaglercraft servers too, see below) and restart the server.
   (Any server or host where you can upload your own plugins works.)
3. Join! Your dog shows up a few seconds later, along with your pet items.

### Eaglercraft servers 🦅

Eaglercraft is browser Minecraft 1.8.8, and its servers are normal **1.8.8 Spigot/Paper** servers with
an Eaglercraft bridge plugin (EaglerXBungee on a BungeeCord proxy, or EaglerXServer). This plugin
works on them:

- **With a BungeeCord proxy:** put `JerryTheDawg-x.x.x.jar` in the **Spigot/Paper (backend) server's**
  `plugins` folder, **not** the BungeeCord proxy's `plugins` folder.
- **Single server with EaglerXServer:** put it in that server's `plugins` folder, next to EaglerXServer.
- It only uses vanilla 1.8.8 things (chat, wolf sounds, hearts and particles, named items), so
  Eaglercraft players see everything normally. Built for Java 8, which most Eaglercraft setups run.
- Eaglercraft **singleplayer** (in the browser) can't load plugins; it has to be a server.

## Talking to your dog

Say his name and a trick in chat, or use `/dog <trick>`:

| Say | He… |
|---|---|
| "Jerry, sit" / "lie down" | sits and stays until "come" |
| "come", "here" | comes to you, even from far away |
| "shake", "paw", "high five" | gives you his paw (hearts!) |
| "wave", "beg" | waves / begs with a whine |
| "roll over" | rolls around |
| "play dead" | flops down dramatically (then hearts: he's fine!) |
| "dance" | hops and spins to a little tune |
| "backflip" | jumps and flips |
| "howl" | a real wolf howl |
| "fetch" | throws the ball where you look |
| "find it" | sniffs around a few spots and finds a treat |
| "are you shy?", "hide your face" | turns away all shy, then peeks back at you |
| "stretch" | a big stretch and a yawn |
| "spin left" / "spin right" | spins that way |
| "shake it off", "dry off" | a big wet-dog shake, with water flying everywhere |
| "scratch", "itchy" | sits and scratches: *thump thump thump* |
| "hug", "cuddle" | comes over and hugs you (+ friendship) |
| "catch" | jumps up and snaps a treat out of the air |
| "sneeze", "achoo" | a tiny sneeze and a little puff |
| "back up", "beep beep" | walks backwards, still looking at you. Beep. Beep. Beep. |
| "go to sleep", "goodnight" | goes to his dog bed if there is one, or curls up right there and snores until "come" |
| "groom", "wash your face" | tries to wash his face like a cat |
| "what's 2 plus 3?", "count to 4", "how many legs do you have?" | barks the answer (plus, minus, times, divided by; answers 1 to 10) |
| "chase your tail", "spin", "jump", "speak", "bow" | exactly what it sounds like |
| "good boy" | wags (+ friendship) |

Chain them: *"Jerry, sit then shake then roll over"*. Short commands on their own ("sit") work too.

## Items

| Item | What it does |
|---|---|
| 🍪 **Dog Treats** (cookie) | right-click your dog: hearts and friendship |
| 🪶 **Pet Brush** (feather) | right-click your dog: sparkles and friendship |
| 🟢 **Tennis Ball** (slime ball) | right-click to throw it: he plays fetch! |
| 🟡 **Dog Whistle** (gold nugget) | calls your pets to you from anywhere (sneak + use: his status) |
| 🛏️ **Dog Bed** (red carpet) | place it: he naps on it |
| 🥣 **Food Bowl** (cauldron) | place it, then fill it with treats or meat |

**Sneak + right-click** your dog with an empty hand to pet him. A normal right-click sits him down,
like any tamed wolf.

## Commands

| Command | What it does |
|---|---|
| `/dog help` | all commands |
| `/dog tricks` | every trick he knows |
| `/dog name <name>` | rename your dog (a name tag works too) |
| `/dog catname <name>` | rename your cat |
| `/dog pupname <name>` | rename your puppy |
| `/dog catcoat ginger\|black\|siamese` | pick Mochi's coat |
| `/dog call` | call your pets |
| `/dog status` | mood, needs and friendship |
| `/dog items` | get the pet items again (and the bed/bowl, if you haven't put them down) |
| `/dog quiet` | turn his random chat messages off/on |
| `/dog reset` | start over with a new dog |

Friendship: New Pal → **Buddy** (Mochi the cat joins) → **Best Friend** (he brings you gifts) → **Inseparable** (a puppy joins).
Your pets can't be hurt.

## Building it yourself

Pushing to this branch builds the plugin on GitHub (Actions) and publishes the `.jar` as a release.
Locally: `gradle build` (Java 8+; the 1.8.8 API comes from Spigot's repository).
