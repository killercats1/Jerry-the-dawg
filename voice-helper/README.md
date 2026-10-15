# Jerry Voice: talk to Jerry out loud 🎤

Minecraft Bedrock add-ons can't use the microphone, so **Jerry Voice** is a tiny helper that
runs on the **same phone** as Minecraft, listens to you, and sends what you said into the game
using Minecraft's built-in `/connect` feature. No PC needed. It's the same trick the Verity
Bedrock add-on uses, but it **doesn't need Beta APIs or any API key**.

You only do steps 1-4 once. After that it's just step 5 each time you play.

## 1. Install the apps (once)

1. Install **F-Droid** from <https://f-droid.org>.
2. In F-Droid, install **Termux** and **Termux:API**.
   (Get both from F-Droid. The Play Store versions don't work together.)
3. Long-press the **Termux:API** icon → **App info**:
   - **Permissions → Microphone → Allow**
   - **Battery → Unrestricted** (so it keeps listening while Minecraft is open)

## 2. Install the helper (once)

1. Download `jerry_voice.py` into your phone's **Download** folder.
2. Open **Termux** and run these (tap **Allow** when it asks for storage):

```
termux-setup-storage
pkg update -y && pkg install -y python termux-api
cp ~/storage/downloads/jerry_voice.py ~/
```

## 3. Minecraft settings (once)

**Settings → General**:

- **Enable WebSockets: ON**
- **Require Encrypted WebSockets: OFF**

## 4. Your world (once)

Turn **Cheats ON** in the world settings (needed for `/connect`).
You do **not** need Beta APIs or any other experiment.

## 5. Every time you play

1. Open **Termux** and run:
   ```
   python ~/jerry_voice.py
   ```
2. Switch to **Minecraft**, join your world, open chat and type:
   ```
   /connect 127.0.0.1:3000
   ```
   You'll see **"[Jerry Voice] Connected!"** and Jerry will bark.
3. Use **Talk to Jerry** (or **sneak + tap Jerry**). When you see **">> Listening... <<"**,
   just talk: *"Jerry, roll over!"*

If the helper isn't running, Talk to Jerry just opens the text box like before, so nothing breaks.

## Always listening (no button)

```
python ~/jerry_voice.py --always
```

Now you don't press anything. Jerry reacts when you **say his name** (*"Jerry, sit"*) or a
**short command on its own** (*"roll over"*), and ignores everything else you say.

## If Jerry can't hear you

Some phones don't let Android's speech recognition run while a game is open. If you keep seeing
*"Jerry didn't catch that"*, switch to the **Groq** engine (this is what Verity uses):

1. Get a free API key at <https://console.groq.com/keys>.
2. Run:
   ```
   python ~/jerry_voice.py --engine groq --groq-key YOUR_KEY
   ```
   It records 4 seconds each time you press Talk (change with `--seconds 6`).
   This sends your recording to Groq to turn it into text.

Other things to check:

- **"Can't find termux-speech-to-text"**: run `pkg install -y termux-api` and make sure the
  **Termux:API app** is installed from F-Droid.
- **`/connect` fails**: check step 3 (WebSockets ON, encryption OFF) and that Termux is still running.
- **Rejoined the world?** Run `/connect 127.0.0.1:3000` again.
- **Stop the helper**: press **Ctrl + C** in Termux (or close Termux from its notification).

## Safety

The helper only accepts connections from your own phone (`127.0.0.1`), never from the network.
It only sends Jerry & Friends commands (`scriptevent jf:...`, plus `tag` and `testfor` to know
when you pressed Talk). With the default engine, your voice goes to your phone's own speech
recognition and nowhere else.
