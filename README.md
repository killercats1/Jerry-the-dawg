# Jerry & Friends: Cozy Pets 🐶🐱

A chill **Minecraft Bedrock add-on** about hanging out with your dog, Jerry. No horror, no scares,
just a very good boy (and eventually a cat).

> Bedrock doesn't use Java-style "plugins". This is an **add-on** (a behavior pack + a resource pack)
> that uses Minecraft's official Script API. It works on phones, tablets, Windows, consoles and Realms.

## What you get

When you join a world, **Jerry** (a tamed wolf with a red collar) shows up along with four items:

| Item | What it does |
|---|---|
| 🍪 **Jerry's Treats** | Use on Jerry: hearts, happy barks, more friendship |
| 🪮 **Pet Brush** | Use on Jerry: sparkles and a very fluffy dog |
| 🎾 **Tennis Ball** | Use anywhere near Jerry: zoomies! |
| 📯 **Jerry's Whistle** | Calls your pets to you from anywhere, and shows your friendship |

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

No experimental toggles are needed. Requires Minecraft Bedrock **1.21.0 or newer**.

## Commands (cheats on)

| Command | What it does |
|---|---|
| `/scriptevent jf:status` | Show Jerry's friendship |
| `/scriptevent jf:boost` | +25 friendship (handy for testing) |
| `/scriptevent jf:reset` | Start over with a fresh Jerry |

## Project layout

```
packs/behavior_pack/      scripts/main.js (pet logic), scripts/text.js (all messages), items/
packs/resource_pack/      item textures + icon mappings
tools/make_textures.py    regenerates the pixel-art PNGs
tools/build.py            validates JSON and builds dist/JerryAndFriends.mcaddon
```

To change any message, edit `packs/behavior_pack/scripts/text.js`. Friendship numbers and
timings are at the top of `main.js`. After changes, run `python3 tools/build.py`.

---
Fan-made, inspired by LookOut3D's Jerry. Not affiliated with LookOut3D or Mojang.
