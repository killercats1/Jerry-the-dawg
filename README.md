# Jerry the dawg: Cozy Pets 🐶🐱

A chill **Minecraft Bedrock add-on** about hanging out with your dog. No horror, no scares,
just a very good boy (and eventually a cat). His name is Jerry by default, but **you can name him
(and the cat) anything you like**.

> Bedrock doesn't use Java-style "plugins". This is an **add-on** (a behavior pack + a resource pack)
> that uses Minecraft's official Script API. It works on phones, tablets, Windows, consoles and Realms.

## What you get

When you join a world, **Jerry** shows up: he looks just like a regular tamed wolf (red collar and all,
and he uses your resource pack's wolf texture), but he knows tricks: sitting, rolling over and tilting his head. **Tap him with an empty hand to pet him.**
You also get five items (plus a dog bed and a food bowl, see below):

| Item | What it does |
|---|---|
| 🍪 **Dog Treats** | Use on your dog: hearts, happy barks, more friendship |
| 🪮 **Pet Brush** | Use on Jerry: sparkles and a very fluffy dog |
| 🎾 **Tennis Ball** | Throw it and he plays fetch! |
| 📯 **Dog Whistle** | Calls your pets to you from anywhere. **Sneak + use** it for his status screen (and renaming) |
| 💬 **Talk to Your Dog** | Say something and he reacts (see below) |

## A smart, living dog 🧠

- **Needs and moods.** He gets **hungry**, **sleepy** and **bored** over time. Treats fill him up,
  naps (sitting or lying down) recharge him, and fetch keeps him happy. His mood shows on his face and
  in what he does: begging when hungry, napping at night when tired, bringing you his bone when bored.
  A happy dog makes friends twice as fast. Nothing bad ever happens to him.
- **Practice makes perfect.** New tricks are a bit sloppy at first; sometimes he does a spin instead
  of a roll over. Every good try teaches him, and after 5 he **masters** the trick.
- **Real fetch.** The **Tennis Ball** gets thrown: he runs after it with Minecraft's own pathfinding,
  grabs it in his mouth and brings it back. The thrown ball can't be picked up or broken, and it floats.
- **Guard dog.** He growls and warns you when a monster sneaks up.
- **Protects you.** If something attacks you, he goes after it (he can't get hurt). He doesn't pick
  fights on his own, he won't fight while he's sitting or lying down, and he stays on it until the
  fight's over instead of running back to you halfway through.
- **He cares.** He rushes over if you get hurt, begs with puppy eyes while you eat, does zoomies when
  you come back after a while, and curls up by your bed when you sleep.
- **Status screen.** **Sneak + use the Dog Whistle** to see his mood, needs, friendship and trick
  stars (and to rename your pets or open his wardrobe).

## Dog bed & food bowl 🛏️🥣

You also get a **Dog Bed** and a **Food Bowl** to place anywhere (lost them? **sneak + Dog Whistle → Get pet items**).

- **Fill the bowl** by using **Dog Treats** or any meat on it. When he gets hungry, he trots over and eats.
- When he's **sleepy** (or it's night and he's tired), he walks to **his bed**, turns around three times
  and curls up. Naps in his own bed recharge him twice as fast, and he gets up by himself once he's rested.
- When you go to sleep, he goes to his bed too.

## Wardrobe 🎀

**Sneak + use the Dog Whistle → Wardrobe** to dress him up:

- **Hats:** party hat, flower crown or cool shades
- **Neck:** bandana or bow tie
- **Fur:** classic or any of Minecraft's wolf coats (ashen, black, chestnut, rusty, snowy, spotted, striped, woods)
- **Collar colour:** pick one there, or just **use any dye on him**

His look is saved, so it stays even if the whistle brings a new copy of him.

## Naming your pets 🏷️

When you first get your dog, a box asks **what you want to name him**. You can rename him (or the
cat) any time with a vanilla **name tag**, or from the status screen (**sneak + use the Dog Whistle**). All the
messages use your pets' real names, and voice commands listen for his name too
(*"Biscuit, roll over!"*). Below, "Jerry" just means your dog.

## Jerry's faces 🐶

Jerry makes faces like in the video: **happy** (^ ^) when you pet, brush or play with him,
**heart eyes** for treats and "good boy", **big puppy eyes** when he's listening or confused,
**shocked** cartoon eyes when you say "boo!", and **sleepy** eyes at night. He also carries a
**bone in his mouth** when you play fetch, say "bone", or when he brings you a bone as a gift.

## Talking to Jerry 🎤

1. Use **Talk to Your Dog**, or **sneak and tap him** with an empty hand.
2. Tap the **microphone button on your keyboard** and say it out loud. Your phone's voice typing
   turns it into text. (On Windows, press **Win + H**. Or just type.)
3. Just like in the video, a **speaker icon pops up over Jerry's head** when he hears you, and he reacts.
4. With **Keep talking** on, the talk box pops back up after he answers, so you can keep the
   conversation going: just tap the mic again. Close the box (or send nothing) to stop.

You can give a few commands at once (*"sit, then roll over, then speak"*), and Jerry understands
common voice-typing mistakes like "role over", "set" or "Gerry".

### 🎙️ Real voice, no typing (Android, no PC)

Install the free **Jerry Voice** helper on your phone and you can just *talk*: press Talk to Your Dog
(or turn on always-listening) and speak, with no keyboard. It runs in Termux on the same phone and
connects with `/connect 127.0.0.1:3000`, the same trick the Verity add-on uses, but with no Beta APIs
and no API key. **Setup guide: [voice-helper/README.md](voice-helper/README.md)**

Get the helper with one line in Termux:

```
pkg update -y && pkg install -y python termux-api curl && curl -fLo ~/jerry_voice.py https://raw.githubusercontent.com/killercats1/Jerry-the-dawg/MAIN/dist/jerry_voice.py
```

| Say something like... | Jerry... |
|---|---|
| "good boy", "love you" | wags and wiggles (+ a little friendship) |
| "sit", "stay" | sits and stays until you say "come" |
| "lie down", "down" | lies down on his belly and stays |
| "come", "here", "let's go" | comes to you, even from far away (and gets up) |
| "shake", "paw" | sits and gives you his paw |
| "high five" | high fives you |
| "wave", "say bye" | waves his paw |
| "beg", "sit pretty" | sits up and begs with puppy eyes |
| "roll over", "belly rubs" | rolls onto his back, paws in the air |
| "play dead", "bang" | flops onto his side (eyes closed, tail still wagging) |
| "bow", "take a bow" | play bow: front down, bum up |
| "stretch", "big stretch" | a big long stretch, front paws out |
| "find it", "find the treat" | nose to the ground, sniffs around and finds a hidden treat |
| "shy", "hide your face", "peekaboo" | covers his eyes with his paw, then peeks out |
| "dance", "party" | stands on his back legs and dances to a little tune |
| "backflip", "flip" | does a backflip! |
| "howl", "sing" | sits and howls at the sky |
| "chase your tail" | spins around chasing his tail |
| "spin", "turn around" | does a spin |
| "spin left" / "spin right" | spins that way |
| "speak", "bark" | barks |
| "jump", "hop" | jumps |
| "fetch", "ball" | plays fetch (throws where you look) |
| "play", "zoomies" | zoomies! |
| "treat", "hungry" | gets excited |
| "sleep", "tired" | yawns |
| "bone" | shows off his bone (in his mouth) |
| "boo!", "surprise!", "whoa" | shocked face and a jump |
| "groom", "wash your face" | (for Mochi, really) he tries to wash his face like a cat |
| "Mochi", "kitty" | Mochi says hi (and see below: talk to her by name) |
| anything he doesn't know | tilts his head, confused but happy |

> Why a text box? Minecraft Bedrock add-ons aren't allowed to use the microphone, so the keyboard's
> voice typing does the listening. Add your own phrases in the `VOICE` list in `scripts/text.js`,
> and teach Jerry new mishearings in `MISHEARD`.

## Mochi the cat 🐱

At **Buddy** friendship, **Mochi** joins the family. She looks like a regular tamed cat (any vanilla coat;
an older Mochi keeps hers), but she has a personality:

- **Talk to her by name**: *"Mochi, sit"*, *"Mochi, roll over"*, *"kitty, high five"*. She knows sit, loaf
  (lie down), come, roll over, paw, high five, wave, beg, play dead, spin, jump, chase her tail, shy,
  stretch, groom, zoomies and more, and practices them like Jerry does. Ask for a dog trick and you get
  cat sass.
- **Tap her** to pet her (purrs and a happy face). Treats and the brush work on her too.
- She has **faces** like Jerry (happy, love, big eyes, shocked, sleepy), washes her face, loafs,
  stretches, gets the zoomies, hisses at monsters, sleeps on your pillow and sometimes brings you a gift.
- **Wardrobe**: pick her fur and collar colour, or use a dye on her.

## Friendship

Treats, brushing, playing and just spending time together slowly fill Jerry's friendship bar.

| Level | Friendship | Unlocks |
|---|---|---|
| New Pal | 0 | Jerry follows you around and does cute stuff |
| Buddy | 25 | **Mochi** the cat joins the family |
| Best Friend | 60 | Jerry brings you little gifts (sticks, flowers, berries... sometimes an emerald) |
| Inseparable | 100 | The best friendship in the whole world |

Your pets can't get hurt, so you never lose them.

## Install

1. Download `dist/JerryAndFriends.mcaddon` (or the build artifact from the GitHub Actions tab).
2. Open it. Minecraft imports both packs automatically.
   - **Android**: open the file with Minecraft from your Files app.
   - **iOS**: Files app → share → Minecraft.
   - **Windows**: double-click it.
3. Create or edit a world → **Behavior Packs** → activate *Jerry & Friends*
   (the resource pack is added automatically).
4. Load the world. Jerry shows up a few seconds after you spawn.

No experimental toggles are needed. Requires Minecraft Bedrock **1.21.40 or newer**.

## Commands (cheats on)

| Command | What it does |
|---|---|
| `/scriptevent jf:status` | Show Jerry's friendship |
| `/scriptevent jf:boost` | +25 friendship (handy for testing) |
| `/scriptevent jf:reset` | Start over with a fresh Jerry |

## Project layout

```
packs/behavior_pack/      scripts/main.js (pet logic), scripts/text.js (all messages), items/, entities/jerry.json
packs/resource_pack/      Jerry's model (vanilla wolf shape), animations (sit / roll over / head tilt / speaker icon)
tools/make_jerry.py       regenerates Jerry's model (based on Mojang's vanilla wolf), speaker icon and bone
tools/make_faces.py       draws Jerry's face expressions (happy, puppy, shocked, love, sleepy)
tools/make_textures.py    regenerates the item and pack icons
tools/build.py            validates JSON and builds dist/JerryAndFriends.mcaddon (+ copies the voice helper)
voice-helper/             Jerry Voice: the phone helper for real microphone voice (Termux)
tools/tsconfig.json       CI type-checks the scripts against the real Minecraft API
```

To change any message, edit `packs/behavior_pack/scripts/text.js`. Friendship numbers and
timings are at the top of `main.js`. After changes, run `python3 tools/build.py`.

---
Fan-made, inspired by LookOut3D's Jerry. Not affiliated with LookOut3D or Mojang.
