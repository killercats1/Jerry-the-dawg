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

    boundary = uuid.uuid4().hex
    fields = {"model": "whisper-large-v3-turbo", "language": "en", "response_format": "json"}
    body = b"".join(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode() for k, v in fields.items()
    )
    body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="voice.m4a"\r\n'
             f"Content-Type: audio/mp4\r\n\r\n").encode() + audio + f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(
        "https://api.groq.com/openai/v1/audio/transcriptions",
        data=body,
        headers={"Authorization": f"Bearer {args.groq_key}", "Content-Type": f"multipart/form-data; boundary={boundary}"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read()).get("text", "")
    except Exception as error:  # noqa: BLE001 - show any network/API problem and keep going
        log(f"Groq transcription failed: {error}")
        return ""


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


# ---------------------------------------------------------------- talking to the add-on

async def heartbeat(game):
    while not game.closed:
        await game.command("scriptevent jf:voice_hello")
        await asyncio.sleep(5)


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
                "  for apps in the background. Try Minecraft and Termux in split screen, check that Termux:API\n"
                "  has Microphone permission, or use the keyboard's mic button in the talk box instead.\n"
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
    log("Minecraft connected! Use 'Talk to Jerry' in the game and talk." if not args.always
        else "Minecraft connected! Always listening: just talk to Jerry.")
    reader_task = asyncio.create_task(game.read_loop())
    tasks = [asyncio.create_task(heartbeat(game)),
             asyncio.create_task(always_listening(game, args) if args.always else push_to_talk(game, args))]
    await reader_task
    for task in tasks:
        task.cancel()
    writer.close()
    log("Minecraft disconnected. Run /connect 127.0.0.1:%d again after rejoining your world." % args.port)


def check_engine(args):
    if args.engine == "typed":
        return True
    tool = "termux-speech-to-text" if args.engine == "android" else "termux-microphone-record"
    if not shutil.which(tool):
        print(f"Can't find '{tool}'. In Termux run:  pkg install -y termux-api\n"
              "and install the Termux:API app from F-Droid (give it microphone permission).")
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
    parser.add_argument("--seconds", type=int, default=4, help="recording length for the groq engine")
    parser.add_argument("--timeout", type=int, default=15, help="max seconds to wait for speech")
    parser.add_argument("--port", type=int, default=3000)
    args = parser.parse_args()
    args.engine = args.engine or ("groq" if args.groq_key else "android")
    if not check_engine(args):
        sys.exit(1)

    # Only accept connections from this phone (Minecraft connects to 127.0.0.1), never the network.
    server = await asyncio.start_server(lambda r, w: on_connect(r, w, args), "127.0.0.1", args.port)
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
