# Verity Voice (Android app) 🎤

Talk to **Verity** (PnTMC's "Verity - Bedrock Edition" add-on) out loud while you play on your phone.
It does the job of Verity's Termux/PC script, but as an app, so it keeps hearing you while Minecraft
is open (newer Android phones block the microphone for Termux once you switch to the game).

It listens to you (and to what you type in chat), lets the add-on work out what you mean, asks Groq's AI
for Verity's reply, shows it in chat with her mouth moving, and speaks it in her voice with FishAudio.

> This is a fan-made helper, not made by PnTMC or ThatMob. You still need the Verity add-on itself.

## You need

- The **Verity - Bedrock Edition** add-on in your world, with **Beta APIs** and **Cheats** on.
- Minecraft: **Settings → General → Enable WebSockets ON**, **Require Encrypted WebSockets OFF**.
- A free **Groq API key**: <https://console.groq.com/keys>
- For her voice: a **FishAudio API key** (<https://fish.audio/app/api-keys/>) and a **model ID**
  (<https://fish.audio/app/discovery/?q=verity>). Without them she answers in chat only.

## Install (once)

1. Download **VerityVoice.apk**:
   <https://github.com/killercats1/Jerry-the-dawg/releases/download/verity-voice/VerityVoice.apk>
2. Open it and install (same as Jerry Voice: allow installing from your browser; Samsung Auto Blocker
   off while installing; Play Protect → More details → Install anyway).

## Every time you play

1. Stop anything else using port 3000 (Jerry Voice, the Termux script).
2. Open **Verity Voice**, paste your keys (they're saved), tap **Start**.
3. Open Minecraft, join your Verity world and type `/connect 127.0.0.1:3000`.
4. Talk to Verity, or type in chat. Type `!talk` and then speak if you turned on
   *"Only answer when I say Verity"*.

Tap **Stop** (in the app or the notification) when you're done: that also turns the bridge off so the
add-on's built-in chat works again without the app.
