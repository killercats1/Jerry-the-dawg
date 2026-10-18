# Jerry Voice (Android app) 🎤🐶

Talk to Jerry out loud while you play Minecraft Bedrock on your phone. No PC, no Termux, no browser.

Newer Android phones block the microphone for apps in the background, so the Termux helper and web
pages can't hear you once Minecraft is on screen. Jerry Voice is a tiny app that listens the way
Android allows: you start it on screen, it shows a **"Jerry is listening"** notification, and then it
keeps hearing you while you play.

## Install (once)

1. On your phone, download **JerryVoice.apk**:
   <https://github.com/killercats1/Jerry-the-dawg/releases/download/voice-app/JerryVoice.apk>
2. Open it. Android asks to allow installing apps from your browser: allow it, then **Install**.
   - Samsung: if it says **Auto Blocker** stopped it, turn it off in **Settings → Security and privacy →
     Auto Blocker**, install, then turn it back on.
   - "Play Protect" may warn that it doesn't know the app (it's not from the Play Store): tap
     **More details → Install anyway**.
3. In Minecraft: **Settings → General → Enable WebSockets ON**, **Require Encrypted WebSockets OFF**,
   and turn **Cheats ON** in your world.

## Every time you play

1. If the Termux helper is running, stop it (`pkill -f jerry_voice.py`): they both use port 3000.
2. Open **Jerry Voice** and tap **Start listening** (allow the microphone and notifications).
3. Open Minecraft, join your world and type: `/connect 127.0.0.1:3000`
4. Just talk: *"Jerry, roll over!"*, *"Mochi, sit"*, *"Pip, wave"*. Or press **Talk to Your Dog** first
   and then say anything.

Stop it from the notification (**Stop**) or in the app.

## Speech engines

- **Phone's speech recognition** (default, free): the same recognition your keyboard uses.
- **Groq Whisper**: records with the app and sends each clip to Groq to turn into text. Needs a free key
  from <https://console.groq.com/keys>. Try it if the phone's recognition doesn't hear you or beeps a lot.

## Problems?

The app's screen shows what it's doing. Tap **"Don't let the phone pause Jerry Voice"** if it stops by
itself. If Minecraft disconnects, type `/connect 127.0.0.1:3000` again after rejoining.
