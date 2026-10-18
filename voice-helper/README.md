# Jerry Voice: talk to Jerry out loud 🎤

Minecraft Bedrock add-ons can't use the microphone, so **Jerry Voice** is a tiny helper that
runs on the **same phone** as Minecraft, listens to you, and sends what you said into the game
using Minecraft's built-in `/connect` feature. No PC needed. It's the same trick the Verity
Bedrock add-on uses, but it **doesn't need Beta APIs or any API key**.

You only do steps 1-4 once. After that it's just step 5 each time you play.

## 1. Install the apps (once)

**Either** of these works (don't mix them):

- **Play Store:** install **Termux** (a recent version has speech-to-text built in, no extra app).
  Then long-press the **Termux** icon → **App info** → **Permissions → Microphone → Allow**,
  and **Battery → Unrestricted**.
- **F-Droid:** install **F-Droid** from <https://f-droid.org>, then **Termux** and **Termux:API**
  from it. Long-press the **Termux:API** icon → **App info** → **Permissions → Microphone → Allow**,
  and **Battery → Unrestricted**.

## 2. Install the helper (once)

Open **Termux** and paste this one line (it installs what's needed and downloads the helper):

```
pkg update -y && pkg install -y python termux-api curl && curl -fLo ~/jerry_voice.py https://raw.githubusercontent.com/killercats1/Jerry-the-dawg/MAIN/dist/jerry_voice.py
```

Run the same line again any time to update the helper to the newest version.

<details>
<summary>Or copy it over by hand</summary>

1. Download `jerry_voice.py` into your phone's **Download** folder.
2. In **Termux** run (tap **Allow** when it asks for storage):

```
termux-setup-storage
pkg update -y && pkg install -y python termux-api
cp ~/storage/downloads/jerry_voice.py ~/
```

</details>

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

## Phone blocks the mic while Minecraft is open? Use browser mode 🌐

Newer Android phones (Android 11+, e.g. Samsung Galaxy A-series) don't let apps in the background use
the microphone, so Termux can't hear you while Minecraft is on screen (you'll see "NO MATCH").
**Chrome is allowed to keep a web page's microphone on** (like a video call), so browser mode lets a
Chrome page do the listening:

1. Get a free key at <https://console.groq.com/keys> (it turns your voice into text).
2. In Termux, run this once (the key is saved, so next time it's just `python ~/jerry_voice.py --browser`):
   ```
   python ~/jerry_voice.py --browser --groq-key YOUR_KEY
   ```
3. Chrome opens **http://127.0.0.1:3001** (if not, open it yourself). Tap **Start listening** and
   **Allow** the microphone. Chrome shows a "using microphone" notification while it's on.
4. Switch to **Minecraft**, `/connect 127.0.0.1:3000`, and just talk: *"Jerry, roll over!"*
   You can also press **Talk to Your Dog** first and then say anything.

Leave Chrome on that page in the background. Your recordings go to Groq to be turned into text.

## Always listening (no button)

```
python ~/jerry_voice.py --always
```

Now you don't press anything. Jerry reacts when you **say his name** (*"Jerry, sit"*) or a
**short command on its own** (*"roll over"*), and ignores everything else you say.

## If Jerry can't hear you

If you see **"ERROR ... NO MATCH"** in Termux (or the talk box keeps opening), Android's speech
recognition didn't get any sound. Usually that's because Android blocks the microphone for apps in the
background while a game is open. Try these, in order:

1. **Test the mic on its own**: in Termux run `termux-speech-to-text` and say something. If your words
   show up, the mic works and it's the background block. If not, give the app the **Microphone**
   permission (long-press its icon → App info → Permissions): **Termux** if you got it from the
   Play Store, **Termux:API** if you got it from F-Droid.
2. **Split screen**: open Minecraft and Termux side by side (on Samsung: recent apps → the app icon →
   *Open in split screen view*). Apps on screen are allowed to use the mic.
3. **Keyboard voice typing** always works: when the talk box opens, tap the keyboard's mic.

Or switch to the **Groq** engine (this is what Verity uses):

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
