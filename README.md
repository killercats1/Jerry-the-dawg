# Something Replaced My Dog: Cozy Edition 🐶❓🐱

A **Minecraft Bedrock add-on** that remakes LookOut3D's *"Something Replaced My Dog"* premise
as a cozy mystery, with no horror. Your dog Jerry starts acting a little... off. Find out why.

> Bedrock doesn't use Java-style "plugins". This is an **add-on** (a behavior pack + a resource pack)
> that uses Minecraft's official Script API. It works on phones, tablets, Windows, consoles and Realms.

## The story

1. **Happy days.** You get **Jerry**, a tamed wolf with a red collar, and **Jerry's Whistle**.
   Jerry does good-dog things.
2. **The swap.** A few minutes in (at night, or after about 7 minutes of play), Jerry gets swapped
   while you're not looking. *\*pop\**
3. **Something seems off.** "Jerry" meows, purrs, hisses at flowers, brings you fish, misspells his
   own name tag... Use the **Detective Magnifier** on him to collect **3 clues**.
4. **Confrontation.** Offer the imposter **Jerry's Favorite Treat**. It doesn't want it...
   because it's **Copycat**, a lonely shapeshifting cat who just wanted a family.
5. **Find Jerry.** Follow the sparkly **paw prints** to a little dog house with a bone on the roof.
   Jerry's been napping the whole time.
6. **The End.** Jerry and Copycat become friends, and you keep both of them. 💛

Story pets can't be hurt, so the story can't break by accident.

## Install

1. Download `dist/SomethingReplacedMyDog.mcaddon` (or the build artifact from the GitHub Actions tab).
2. Open it. Minecraft imports both packs automatically.
   - **Android**: open the file with Minecraft from your Files app.
   - **iOS**: Files app → share → Minecraft.
   - **Windows**: double-click it.
3. Create or edit a world → **Behavior Packs** → activate *Something Replaced My Dog*
   (the resource pack is added automatically).
4. Load the world. The story starts on its own a few seconds after you spawn.

No experimental toggles are needed. Requires Minecraft Bedrock **1.21.0 or newer**.

## Commands (cheats on)

| Command | What it does |
|---|---|
| `/scriptevent srmd:status` | Show your chapter, play time and clues |
| `/scriptevent srmd:skip` | Jump to the next chapter (handy for testing) |
| `/scriptevent srmd:reset` | Restart the story from the beginning |

## Project layout

```
packs/behavior_pack/      scripts/main.js (story logic), scripts/text.js (all dialogue), items/
packs/resource_pack/      item textures + icon mappings
tools/make_textures.py    regenerates the pixel-art PNGs
tools/build.py            validates JSON and builds dist/SomethingReplacedMyDog.mcaddon
```

To change the dialogue, edit `packs/behavior_pack/scripts/text.js`. Timing (when the swap happens,
how many clues you need) is at the top of `main.js`. After changes, run `python3 tools/build.py`.

---
Fan-made and inspired by LookOut3D's video. Not affiliated with LookOut3D or Mojang.
