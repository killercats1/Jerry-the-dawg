# Jerry the Dawg: Java 1.8.8 edition 🐶

The **"lesser version"** of Jerry the Dawg, as a **Spigot/Paper plugin for Minecraft Java Edition 1.8.8**.
It's separate from the Bedrock add-on (that's on the `MAIN` branch): different code, different history.

Every player gets a tamed wolf they can **name anything**, teach **tricks**, feed, brush and play with.
Friendship unlocks **Mochi the cat** and **gifts**.

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
| "shake off" | does the wet-dog shake |
| "dance" | hops and spins to a little tune |
| "backflip" | jumps and flips |
| "howl" | a real wolf howl |
| "chase your tail", "spin", "jump", "speak", "fetch", "bow" | exactly what it sounds like |
| "good boy" | wags (+ friendship) |

Chain them: *"Jerry, sit then shake then roll over"*. Short commands on their own ("sit") work too.

## Items

| Item | What it does |
|---|---|
| 🍪 **Dog Treats** (cookie) | right-click your dog: hearts and friendship |
| 🪶 **Pet Brush** (feather) | right-click your dog: sparkles and friendship |
| 🟢 **Tennis Ball** (slime ball) | right-click: zoomies! |
| 🟡 **Dog Whistle** (gold nugget) | calls your pets to you from anywhere |

**Sneak + right-click** your dog with an empty hand to pet him. A normal right-click sits him down,
like any tamed wolf.

## Commands

| Command | What it does |
|---|---|
| `/dog help` | all commands |
| `/dog tricks` | every trick he knows |
| `/dog name <name>` | rename your dog (a name tag works too) |
| `/dog catname <name>` | rename your cat |
| `/dog call` | call your pets |
| `/dog status` | friendship |
| `/dog items` | get the pet items again |
| `/dog quiet` | turn his random chat messages off/on |
| `/dog reset` | start over with a new dog |

Friendship: New Pal → **Buddy** (Mochi the cat joins) → **Best Friend** (he brings you gifts) → Inseparable.
Your pets can't be hurt.

## Building it yourself

Pushing to this branch builds the plugin on GitHub (Actions) and publishes the `.jar` as a release.
Locally: `gradle build` (Java 8+; the 1.8.8 API comes from Spigot's repository).
