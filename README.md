# Jerry & Friends: Cozy Pets 🐶🐱

A chill **Minecraft Bedrock add-on** about hanging out with your dog, Jerry. No horror, no scares,
just a very good boy (and eventually a cat).

> Bedrock doesn't use Java-style "plugins". This is an **add-on** (a behavior pack + a resource pack)
> that uses Minecraft's official Script API. It works on phones, tablets, Windows, consoles and Realms.

## What you get

When you join a world, **Jerry** shows up: a fluffy white-and-grey dog with a red collar and his own
animations (he can sit, roll over and tilt his head). **Tap him with an empty hand to pet him.**
You also get five items:

| Item | What it does |
|---|---|
| 🍪 **Jerry's Treats** | Use on Jerry: hearts, happy barks, more friendship |
| 🪮 **Pet Brush** | Use on Jerry: sparkles and a very fluffy dog |
| 🎾 **Tennis Ball** | Use anywhere near Jerry: zoomies! |
| 📯 **Jerry's Whistle** | Calls your pets to you from anywhere, and shows your friendship |
| 💬 **Talk to Jerry** | Say something and Jerry reacts (see below) |

## Talking to Jerry 🎤

Use **Talk to Jerry** and a text box pops up. On a phone or tablet, tap the **microphone button on
your keyboard** and just say it out loud: your phone's voice typing turns it into text. Just like in
the video, a **speaker icon pops up over Jerry's head** when he hears you, and then he reacts.
(On Windows, press **Win + H** for voice typing. Or just type.)

| Say something like... | Jerry... |
|---|---|
| "good boy", "love you" | wags and wiggles (+ a little friendship) |
| "roll over", "play dead", "belly rubs" | rolls onto his back, paws in the air |
| "sit", "stay", "down" | sits and stays until you say "come" |
| "come", "here", "let's go" | comes to you, even from far away |
| "speak", "bark" | barks |
| "spin", "dance" | does a spin |
| "jump", "hop" | jumps |
| "fetch", "play" | zoomies! |
| "treat", "hungry" | gets excited |
| "sleep", "tired" | yawns |
| "Mochi", "kitty" | Mochi says hi |
| anything he doesn't know | tilts his head, confused but happy |

> Why a text box? Minecraft Bedrock add-ons aren't allowed to use the microphone, so the keyboard's
> voice typing does the listening. Add your own phrases in the `VOICE` list in `scripts/text.js`.

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
packs/resource_pack/      Jerry's model, animations (sit / roll over / head tilt / speaker icon), textures
tools/make_jerry.py       regenerates Jerry's 3D model + texture from one cube list
tools/make_textures.py    regenerates the item and pack icons
tools/build.py            validates JSON and builds dist/JerryAndFriends.mcaddon
tools/tsconfig.json       CI type-checks the scripts against the real Minecraft API
```

To change any message, edit `packs/behavior_pack/scripts/text.js`. Friendship numbers and
timings are at the top of `main.js`. After changes, run `python3 tools/build.py`.

---
Fan-made, inspired by LookOut3D's Jerry. Not affiliated with LookOut3D or Mojang.
