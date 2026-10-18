#!/usr/bin/env python3
"""Jerry Voice: talk to Jerry out loud in Minecraft Bedrock (Jerry & Friends add-on).

Minecraft add-ons can't use the microphone, so this little helper does the listening.
It runs on the same phone as Minecraft (in Termux on Android), turns your voice into text,
and sends it into the game through Minecraft's built-in /connect (WebSocket) feature.

Quick start (full guide in voice-helper/README.md):
  1. Termux:      pkg install -y python termux-api
  2. Termux:      python jerry_voice.py
  3. Minecraft:   Settings > General > Enable WebSockets ON, Require Encrypted WebSockets OFF
  4. In your world (cheats on), type in chat:   /connect 127.0.0.1:3000
  5. Use "Talk to Jerry" (or sneak + tap Jerry) and just talk!

Browser mode (--browser), for phones that block the microphone while a game is open:
  Android doesn't let apps in the background use the microphone, but Chrome keeps a web page's
  microphone on (with a "using microphone" notification), like a video call. In this mode the
  helper serves a small page at http://127.0.0.1:3001 : open it in Chrome, tap Start, switch to
  Minecraft and just talk. Clips are turned into text with Groq Whisper (free key, see below).

Speech engines (--engine):
  android  Android's built-in speech recognition via Termux:API (default, free, no key)
  groq     records with Termux:API and transcribes with Groq Whisper (free key from
           console.groq.com; set GROQ_API_KEY or use --groq-key). Use it if "android" can't
           hear you while Minecraft is open.
  typed    type in Termux instead of talking (for testing)

Only uses the Python standard library: no pip installs needed.
"""
import argparse
import asyncio
import base64
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
LISTEN_TAG = "jf_listen"  # the add-on puts this tag on you when you use "Talk to Jerry"


def log(message):
    print(f"[{time.strftime('%H:%M:%S')}] {message}", flush=True)


# ---------------------------------------------------------------- speech to text

def clean_text(text):
    """Keep it safe to put on a command line."""
    text = re.sub(r"[^A-Za-z0-9 ,.'?!-]+", " ", text or "")
    return re.sub(r"\s+", " ", text).strip()[:150]


def listen_android(args):
    """Android's speech recognizer (Termux:API). Prints partial results; the last line is final.
    When it fails it prints an error instead (e.g. "ERROR ERROR NO MATCH"), which is not speech."""
    try:
        out = subprocess.run(["termux-speech-to-text"], capture_output=True, text=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        return ""
    lines = [line.strip() for line in out.stdout.splitlines() if line.strip()]
    errors = [line for line in lines if line.upper().startswith("ERROR")]
    if errors:
        args.error = errors[-1]
        return ""
    return lines[-1] if lines else ""


def listen_groq(args):
    """Record a few seconds with Termux:API, then transcribe with Groq's Whisper."""
    path = os.path.join(tempfile.gettempdir(), "jerry_voice.m4a")
    if os.path.exists(path):
        os.remove(path)
    subprocess.run(["termux-microphone-record", "-f", path, "-l", str(args.seconds), "-e", "aac"],
                   capture_output=True, timeout=10)
    time.sleep(args.seconds + 0.6)
    subprocess.run(["termux-microphone-record", "-q"], capture_output=True, timeout=10)
    for _ in range(20):  # wait for the file to be finished
        if os.path.exists(path) and os.path.getsize(path) > 0:
            break
        time.sleep(0.1)
    if not os.path.exists(path) or os.path.getsize(path) == 0:
        log("No recording was made. Did Termux:API get microphone permission?")
        return ""
    with open(path, "rb") as f:
        audio = f.read()
    return transcribe_groq(audio, "voice.m4a", "audio/mp4", args.groq_key)


# Words Whisper should expect (helps it spell "Jerry" and "Mochi" right).
VOCABULARY = ("Jerry, Mochi, Pip, kitty. Sit, stay, come here, lie down, roll over, shake, paw, high five, "
              "wave, beg, play dead, bow, dance, backflip, howl, chase your tail, spin left, spin right, jump, "
              "speak, fetch, find it, peekaboo, stretch, groom, good boy, good girl, treat, zoomies.")
# Things Whisper likes to "hear" in silence or background noise.
HALLUCINATIONS = {"you", "thank you", "thank you.", "thanks for watching!", "thank you for watching.",
                  "thanks for watching.", ".", "bye.", "so", "uh", "um"}


def transcribe_groq(audio, filename, mime, key):
    """Turn a recording into text with Groq's Whisper (free API key from console.groq.com)."""
    fake = os.environ.get("JERRY_FAKE_TRANSCRIPT")  # for testing without a key
    if fake is not None:
        return fake
    boundary = uuid.uuid4().hex
    fields = {"model": "whisper-large-v3-turbo", "language": "en", "response_format": "json", "prompt": VOCABULARY}
    body = b"".join(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode() for k, v in fields.items()
    )
    body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
             f"Content-Type: {mime}\r\n\r\n").encode() + audio + f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(
        "https://api.groq.com/openai/v1/audio/transcriptions",
        data=body,
        # Groq's firewall turns away Python's default "Python-urllib" user agent with 403 Forbidden.
        headers={"Authorization": f"Bearer {key}", "Content-Type": f"multipart/form-data; boundary={boundary}",
                 "User-Agent": "JerryVoice/2.0 (Minecraft add-on helper)", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            text = json.loads(response.read()).get("text", "").strip()
    except urllib.error.HTTPError as error:
        detail = error.read().decode(errors="replace")[:200].replace("\n", " ")
        if error.code == 401:
            log("Groq says the key is wrong. Copy it again from https://console.groq.com/keys and run once with --groq-key NEW_KEY")
        elif error.code == 429:
            log("Groq says slow down (free limit reached for now). Try again in a minute.")
        else:
            log(f"Groq transcription failed: HTTP {error.code}: {detail}")
        return ""
    except Exception as error:  # noqa: BLE001 - show any network problem and keep going
        log(f"Groq transcription failed: {error}")
        return ""
    return "" if text.lower() in HALLUCINATIONS else text


def listen_typed(args):
    try:
        return input("Say to Jerry> ")
    except EOFError:
        return ""


ENGINES = {"android": listen_android, "groq": listen_groq, "typed": listen_typed}


# ---------------------------------------------------------------- minimal WebSocket server

class Minecraft:
    """One connected Minecraft game, speaking Minecraft's WebSocket JSON protocol."""

    def __init__(self, reader, writer):
        self.reader = reader
        self.writer = writer
        self.pending = {}
        self.closed = False

    async def handshake(self):
        request = await self.reader.readuntil(b"\r\n\r\n")
        headers = {}
        for line in request.decode(errors="replace").split("\r\n")[1:]:
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key.strip().lower()] = value.strip()
        key = headers.get("sec-websocket-key")
        if not key:
            self.writer.write(b"HTTP/1.1 400 Bad Request\r\n\r\n")
            await self.writer.drain()
            return False
        accept = base64.b64encode(hashlib.sha1((key + WS_GUID).encode()).digest()).decode()
        self.writer.write(
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            f"Sec-WebSocket-Accept: {accept}\r\n\r\n".encode()
        )
        await self.writer.drain()
        return True

    async def _read_frame(self):
        b1, b2 = await self.reader.readexactly(2)
        opcode, length = b1 & 0x0F, b2 & 0x7F
        if length == 126:
            length = struct.unpack(">H", await self.reader.readexactly(2))[0]
        elif length == 127:
            length = struct.unpack(">Q", await self.reader.readexactly(8))[0]
        mask = await self.reader.readexactly(4) if b2 & 0x80 else None
        data = bytearray(await self.reader.readexactly(length))
        if mask:
            for i in range(length):
                data[i] ^= mask[i % 4]
        return bool(b1 & 0x80), opcode, bytes(data)

    async def _send_frame(self, opcode, payload):
        header = bytes([0x80 | opcode])
        n = len(payload)
        if n < 126:
            header += bytes([n])
        elif n < 65536:
            header += bytes([126]) + struct.pack(">H", n)
        else:
            header += bytes([127]) + struct.pack(">Q", n)
        self.writer.write(header + payload)
        await self.writer.drain()

    async def read_loop(self):
        """Receive messages; command responses resolve the matching command() call."""
        parts = []
        try:
            while True:
                fin, opcode, data = await self._read_frame()
                if opcode == 8:  # close
                    break
                if opcode == 9:  # ping
                    await self._send_frame(10, data)
                    continue
                if opcode in (0, 1, 2):
                    parts.append(data)
                    if not fin:
                        continue
                    message, parts = b"".join(parts), []
                    try:
                        packet = json.loads(message)
                    except ValueError:
                        continue
                    future = self.pending.pop(packet.get("header", {}).get("requestId"), None)
                    if future and not future.done():
                        future.set_result(packet.get("body", {}))
        except (asyncio.IncompleteReadError, ConnectionError):
            pass
        finally:
            self.closed = True
            for future in self.pending.values():
                if not future.done():
                    future.set_result(None)

    async def command(self, line, timeout=4.0):
        """Run a command in the game as the player and return Minecraft's response body."""
        if self.closed:
            return None
        request_id = str(uuid.uuid4())
        future = asyncio.get_running_loop().create_future()
        self.pending[request_id] = future
        packet = {
            "header": {"version": 1, "requestId": request_id, "messagePurpose": "commandRequest", "messageType": "commandRequest"},
            "body": {"version": 1, "commandLine": line, "origin": {"type": "player"}},
        }
        await self._send_frame(1, json.dumps(packet).encode())
        try:
            return await asyncio.wait_for(future, timeout)
        except asyncio.TimeoutError:
            self.pending.pop(request_id, None)
            return None


# ---------------------------------------------------------------- browser mode
# The page (PAGE below) listens with Chrome's microphone, cuts out each thing you say, and posts
# the clip to /clip. We turn it into text and send it to the game.

BROWSER = {"game": None, "talk_until": 0.0, "last_beat": 0.0, "hidden": None}

PAGE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Jerry Voice</title>
<style>
  body { margin: 0; font-family: system-ui, sans-serif; background: #1e2a22; color: #f2f5ef; }
  main { max-width: 480px; margin: 0 auto; padding: 24px 16px; text-align: center; }
  h1 { font-size: 28px; margin: 8px 0 4px; }
  p { color: #c9d4c3; line-height: 1.45; }
  button { font-size: 22px; padding: 18px 28px; border: 0; border-radius: 14px; background: #5fbf6f; color: #10210f; font-weight: 700; width: 100%; }
  button.stop { background: #e0755f; }
  .meter { height: 14px; background: #33433a; border-radius: 7px; overflow: hidden; margin: 18px 0 6px; }
  .meter div { height: 100%; width: 0; background: #8fe39b; transition: width .08s; }
  .row { display: flex; justify-content: space-between; font-size: 15px; color: #c9d4c3; margin: 6px 0; }
  .heard { font-size: 20px; min-height: 28px; margin: 14px 0; color: #fff; }
  .ok { color: #8fe39b; } .bad { color: #f0a08f; }
</style></head><body><main>
  <h1>🐶 Jerry Voice</h1>
  <p id="hint">Tap <b>Start listening</b>, allow the microphone, then switch to Minecraft and just talk.
  Keep this page open in Chrome (it's fine in the background).</p>
  <button id="go">Start listening</button>
  <div class="meter"><div id="level"></div></div>
  <div class="row"><span>Microphone</span><span id="mic" class="bad">off</span></div>
  <div class="row"><span>Minecraft</span><span id="mc" class="bad">not connected</span></div>
  <div class="heard" id="heard"></div>
</main><script>
const $ = (id) => document.getElementById(id);
let stream, ctx, running = false, level = 0, speaking = false, recorder, chunks = [], startT = 0, lastLoud = 0, noise = 0.01;
let lastAudioBeat = 0, keepAlive;

async function beat() {
  try {
    const r = await fetch("/beat", { method: "POST", body: JSON.stringify({ hidden: document.hidden, running, level }) });
    const s = await r.json();
    $("mc").textContent = s.minecraft ? "connected" : "not connected (type /connect 127.0.0.1:3000)";
    $("mc").className = s.minecraft ? "ok" : "bad";
  } catch (e) {
    $("mc").textContent = "helper not running (start it in Termux)"; $("mc").className = "bad";
  }
}
setInterval(beat, 2000); beat();
document.addEventListener("visibilitychange", () => {
  // Tell the helper right away (sendBeacon still works while the page is being hidden).
  navigator.sendBeacon("/beat", JSON.stringify({ hidden: document.hidden, running, level }));
  if (!document.hidden && ctx && ctx.state === "suspended") ctx.resume();
});

function send(blob) {
  fetch("/clip", { method: "POST", headers: { "Content-Type": blob.type || "audio/webm" }, body: blob })
    .then((r) => r.json()).then((s) => { if (s.text) $("heard").textContent = "“" + s.text + "”"; })
    .catch(() => {});
}

function startClip() {
  chunks = [];
  recorder = new MediaRecorder(stream, MediaRecorder.isTypeSupported("audio/webm;codecs=opus") ? { mimeType: "audio/webm;codecs=opus" } : {});
  recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data);
  const began = ctx.currentTime;
  recorder.onstop = () => {
    if (ctx.currentTime - began > 0.45) send(new Blob(chunks, { type: recorder.mimeType || "audio/webm" }));
  };
  recorder.start();
}

async function start() {
  stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } });
  ctx = new AudioContext();
  const source = ctx.createMediaStreamSource(stream);
  // Runs on every bit of audio, also while Chrome is in the background.
  const proc = ctx.createScriptProcessor(2048, 1, 1);
  source.connect(proc); proc.connect(ctx.destination);
  proc.onaudioprocess = (e) => {
    const d = e.inputBuffer.getChannelData(0);
    let sum = 0; for (let i = 0; i < d.length; i++) sum += d[i] * d[i];
    level = Math.sqrt(sum / d.length);
    const now = ctx.currentTime;
    if (!speaking) noise = noise * 0.995 + level * 0.005;
    const loud = level > Math.max(0.02, noise * 3);
    if (loud) lastLoud = now;
    // Timers slow down in the background, but this audio callback keeps running, so beat from here too.
    if (document.hidden && now - lastAudioBeat > 5) { lastAudioBeat = now; beat(); }
    if (!speaking && loud) { speaking = true; startT = now; startClip(); }
    else if (speaking && (now - lastLoud > 0.8 || now - startT > 7)) { speaking = false; if (recorder.state !== "inactive") recorder.stop(); }
  };
  // Keep-alive: a silent sound playing on a loop tells Chrome this page is busy with audio, so it's
  // less likely to pause it when you switch apps. Shows up as a media notification.
  if (!keepAlive) {
    const silence = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
    const dest = ctx.createMediaStreamDestination();
    const loop = ctx.createBufferSource(); loop.buffer = silence; loop.loop = true; loop.connect(dest); loop.start();
    keepAlive = new Audio(); keepAlive.srcObject = dest.stream; keepAlive.loop = true;
    keepAlive.play().catch(() => {});
    if ("mediaSession" in navigator) navigator.mediaSession.metadata = new MediaMetadata({ title: "Jerry is listening", artist: "Jerry Voice" });
  }
  stream.getAudioTracks()[0].onended = () => { running = false; $("mic").textContent = "stopped by the phone"; $("mic").className = "bad"; beat(); };
  running = true;
  $("mic").textContent = "listening"; $("mic").className = "ok";
  $("go").textContent = "Stop"; $("go").className = "stop";
  $("hint").innerHTML = "Listening! Now switch to <b>Minecraft</b> with the <b>Home</b> or <b>recent apps</b> button (not Back, that closes the page) and talk to Jerry.";
  beat();
}

function stop() {
  running = false;
  stream && stream.getTracks().forEach((t) => t.stop());
  ctx && ctx.close();
  $("mic").textContent = "off"; $("mic").className = "bad";
  $("go").textContent = "Start listening"; $("go").className = "";
}

$("go").onclick = () => (running ? stop() : start().catch((e) => { $("hint").textContent = "Couldn't use the microphone: " + e.message; }));
setInterval(() => { $("level").style.width = Math.min(100, level * 600) + "%"; }, 100);
if (location.search.includes("autostart")) start();
</script></body></html>
"""


async def forward(text):
    """Send something heard in browser mode to the game."""
    game = BROWSER["game"]
    if not game or game.closed:
        log(f"Heard \"{text}\", but Minecraft isn't connected (type /connect 127.0.0.1:3000 in the game).")
        return
    log(f'Heard: "{text}"')
    if time.time() < BROWSER["talk_until"]:  # you pressed Talk: Jerry answers whatever you say
        BROWSER["talk_until"] = 0
        await game.command(f"scriptevent jf:voice {text}")
    else:  # otherwise the add-on only reacts to his name or a short command
        await game.command(f"scriptevent jf:voice_heard {text}")


async def on_http(reader, writer, args):
    def respond(status, kind, body):
        if isinstance(body, str):
            body = body.encode()
        writer.write(f"HTTP/1.1 {status}\r\nContent-Type: {kind}\r\nContent-Length: {len(body)}\r\n"
                     "Cache-Control: no-store\r\nConnection: close\r\n\r\n".encode() + body)

    try:
        head = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), 15)
        lines = head.decode(errors="replace").split("\r\n")
        method, path = (lines[0].split(" ") + ["", ""])[:2]
        headers = {}
        for line in lines[1:]:
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key.strip().lower()] = value.strip()
        length = int(headers.get("content-length") or 0)
        if length > 8_000_000:
            respond("413 Too Large", "text/plain", "too large")
            return
        body = await asyncio.wait_for(reader.readexactly(length), 30) if length else b""
        path = path.split("?")[0]
        game = BROWSER["game"]
        if method == "GET" and path in ("/", "/index.html"):
            respond("200 OK", "text/html; charset=utf-8", PAGE)
        elif method == "POST" and path == "/beat":
            try:
                info = json.loads(body or b"{}")
            except ValueError:
                info = {}
            BROWSER["last_beat"] = time.time()
            hidden = bool(info.get("hidden")) if info.get("running") else None
            if hidden != BROWSER["hidden"]:
                BROWSER["hidden"] = hidden
                log({None: "Browser page open (not listening yet: tap Start).",
                     False: "Browser page is listening.",
                     True: "Browser page is listening in the background."}[hidden])
            BROWSER["warned"] = False
            respond("200 OK", "application/json", json.dumps({"minecraft": bool(game and not game.closed)}))
        elif method == "POST" and path == "/clip":
            kind = headers.get("content-type", "audio/webm").split(";")[0]
            ext = "ogg" if "ogg" in kind else "mp4" if "mp4" in kind else "webm"
            text = clean_text(await asyncio.get_running_loop().run_in_executor(
                None, transcribe_groq, body, f"voice.{ext}", kind, args.groq_key))
            if text:
                await forward(text)
            respond("200 OK", "application/json", json.dumps({"text": text}))
        else:
            respond("404 Not Found", "text/plain", "not found")
        await writer.drain()
    except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, asyncio.TimeoutError, ConnectionError, ValueError):
        pass
    finally:
        writer.close()


# ---------------------------------------------------------------- talking to the add-on

async def heartbeat(game):
    while not game.closed:
        await game.command("scriptevent jf:voice_hello")
        await asyncio.sleep(5)


async def watch_page():
    """Browser mode: say so when the page stops checking in (Chrome paused or closed it)."""
    while True:
        await asyncio.sleep(5)
        quiet = time.time() - BROWSER["last_beat"]
        if BROWSER["hidden"] is not None and quiet > 20 and not BROWSER.get("warned"):
            BROWSER["warned"] = True
            BROWSER["hidden"] = None
            log("The browser page stopped answering: Chrome paused or closed it when you left. Go back to it,\n"
                "  tap Start listening again, and switch apps with Home/recent apps (not Back). If it keeps\n"
                "  happening: Settings > Apps > Chrome > Battery > Unrestricted.")


async def hear(game, args, event_id):
    await game.command("scriptevent jf:voice_status listening")
    if shutil.which("termux-vibrate"):
        subprocess.Popen(["termux-vibrate", "-d", "60"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    args.error = None
    text = clean_text(await asyncio.get_running_loop().run_in_executor(None, ENGINES[args.engine], args))
    if text:
        args.errors_in_a_row = 0
        log(f'Heard: "{text}"')
        await game.command(f"scriptevent {event_id} {text}")
        return
    if args.error:
        args.errors_in_a_row = getattr(args, "errors_in_a_row", 0) + 1
        log(f"Android's speech recognition failed ({args.error}).")
        if args.errors_in_a_row in (1, 5):
            log("  If it keeps failing while Minecraft is open, Android is probably blocking the microphone\n"
                "  for apps in the background. Check that Termux (Play Store) or Termux:API (F-Droid) has\n"
                "  Microphone permission, or use the keyboard's mic button in the talk box instead.\n"
                "  Test the microphone on its own: in Termux run  termux-speech-to-text  and say something.")
        if event_id == "jf:voice":
            await game.command("scriptevent jf:voice_status blocked")
        else:
            await asyncio.sleep(min(10, 2 * args.errors_in_a_row))  # always-listening: don't hammer it
    elif event_id == "jf:voice":
        await game.command("scriptevent jf:voice_status nothing")


async def push_to_talk(game, args):
    """Wait for "Talk to Jerry" in the game, then listen once."""
    while not game.closed:
        found = await game.command(f"testfor @a[tag={LISTEN_TAG}]")
        if found and found.get("statusCode") == 0:
            await game.command(f"tag @a remove {LISTEN_TAG}")
            if args.browser:
                # The page is always listening; the next thing it hears is the answer to Talk.
                if time.time() - BROWSER["last_beat"] > 130 or BROWSER["hidden"] is None:
                    await game.command("scriptevent jf:voice_status nobrowser")
                else:
                    BROWSER["talk_until"] = time.time() + 8
                    await game.command("scriptevent jf:voice_status listening")
            else:
                await hear(game, args, "jf:voice")
        await asyncio.sleep(0.3)


async def always_listening(game, args):
    """Listen all the time; the add-on only reacts to things that sound like commands."""
    while not game.closed:
        await hear(game, args, "jf:voice_heard")
        await asyncio.sleep(0.2)


async def on_connect(reader, writer, args):
    game = Minecraft(reader, writer)
    try:
        if not await game.handshake():
            return
    except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, ConnectionError):
        return
    if args.browser:
        log("Minecraft connected! Talk away (the browser page does the listening).")
    else:
        log("Minecraft connected! Use 'Talk to Jerry' in the game and talk." if not args.always
            else "Minecraft connected! Always listening: just talk to Jerry.")
    BROWSER["game"] = game
    reader_task = asyncio.create_task(game.read_loop())
    listener = push_to_talk(game, args) if args.browser or not args.always else always_listening(game, args)
    tasks = [asyncio.create_task(heartbeat(game)), asyncio.create_task(listener)]
    await reader_task
    for task in tasks:
        task.cancel()
    writer.close()
    log("Minecraft disconnected. Run /connect 127.0.0.1:%d again after rejoining your world." % args.port)


KEY_FILE = os.path.expanduser("~/.jerry_voice_key")


def open_in_chrome(url):
    """Open the page in Chrome specifically: other browsers (Opera, Samsung Internet...) may turn the
    microphone off once you switch to Minecraft. Falls back to the default browser."""
    if shutil.which("am"):
        result = subprocess.run(["am", "start", "-a", "android.intent.action.VIEW", "-d", url, "-p", "com.android.chrome"],
                                capture_output=True, text=True)
        if result.returncode == 0 and "Error" not in (result.stdout + result.stderr):
            return
    if shutil.which("termux-open-url"):
        subprocess.Popen(["termux-open-url", url], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)



def check_engine(args):
    if args.browser:
        if not args.groq_key and os.environ.get("JERRY_FAKE_TRANSCRIPT") is None:
            print("Browser mode turns your voice into text with Groq (free).\n"
                  "1. Get a free key at https://console.groq.com/keys\n"
                  "2. Run once:  python jerry_voice.py --browser --groq-key YOUR_KEY\n"
                  "   (the key is saved, so next time just:  python jerry_voice.py --browser)")
            return False
        return True
    if args.engine == "typed":
        return True
    tool = "termux-speech-to-text" if args.engine == "android" else "termux-microphone-record"
    if not shutil.which(tool):
        print(f"Can't find '{tool}'. In Termux run:  pkg install -y termux-api\n"
              "Play Store Termux: update it and give Termux microphone permission.\n"
              "F-Droid Termux: also install the Termux:API app from F-Droid (give it microphone permission).")
        return False
    if args.engine == "groq" and not args.groq_key:
        print("The groq engine needs a free API key from https://console.groq.com/keys\n"
              "Run with:  python jerry_voice.py --engine groq --groq-key YOUR_KEY")
        return False
    return True


async def main():
    parser = argparse.ArgumentParser(description="Talk to Jerry out loud in Minecraft Bedrock.")
    parser.add_argument("--engine", choices=sorted(ENGINES), default=None)
    parser.add_argument("--groq-key", default=os.environ.get("GROQ_API_KEY", ""))
    parser.add_argument("--always", action="store_true", help="listen all the time instead of push-to-talk")
    parser.add_argument("--browser", action="store_true", help="listen through a Chrome page (works while Minecraft is open)")
    parser.add_argument("--web-port", type=int, default=3001)
    parser.add_argument("--seconds", type=int, default=4, help="recording length for the groq engine")
    parser.add_argument("--timeout", type=int, default=15, help="max seconds to wait for speech")
    parser.add_argument("--port", type=int, default=3000)
    args = parser.parse_args()
    if args.groq_key:
        try:  # remember the key for next time
            with open(KEY_FILE, "w") as f:
                f.write(args.groq_key.strip())
            os.chmod(KEY_FILE, 0o600)
        except OSError:
            pass
    elif os.path.exists(KEY_FILE):
        with open(KEY_FILE) as f:
            args.groq_key = f.read().strip()
    args.engine = args.engine or ("groq" if args.groq_key and not args.browser else "android")
    if not check_engine(args):
        sys.exit(1)

    # Only accept connections from this phone (Minecraft connects to 127.0.0.1), never the network.
    try:
        server = await asyncio.start_server(lambda r, w: on_connect(r, w, args), "127.0.0.1", args.port)
        web = await asyncio.start_server(lambda r, w: on_http(r, w, args), "127.0.0.1", args.web_port) if args.browser else None
    except OSError as error:
        if error.errno not in (98, 48):  # "address already in use" (Linux/Android, macOS)
            raise
        print("Jerry Voice is already running (maybe in another Termux session or tab).\n"
              "Stop the old one first, then start again:\n\n"
              "    pkill -f jerry_voice.py\n"
              f"    python ~/jerry_voice.py{' --browser' if args.browser else ''}\n")
        sys.exit(1)
    if args.browser:
        url = f"http://127.0.0.1:{args.web_port}"
        print(f"""
  Jerry Voice is running in browser mode.
  1. Open {url} in Chrome (not Opera or other browsers) and tap "Start listening".
  2. Open Minecraft, join your world, and type in chat:   /connect 127.0.0.1:{args.port}
  3. Just talk: "Jerry, roll over!" (or use Talk to Your Dog first, then say anything).
  Press Ctrl+C here to stop.
""", flush=True)
        open_in_chrome(url)
        async with server, web:
            await asyncio.gather(server.serve_forever(), web.serve_forever(), watch_page())
        return
    print(f"""
  Jerry Voice is running (speech: {args.engine}{', always listening' if args.always else ''}).
  Now open Minecraft, join your world, and type in chat:

      /connect 127.0.0.1:{args.port}

  (Settings > General: "Enable WebSockets" ON, "Require Encrypted WebSockets" OFF, cheats ON.)
  Press Ctrl+C here to stop.
""", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nJerry Voice stopped.")
