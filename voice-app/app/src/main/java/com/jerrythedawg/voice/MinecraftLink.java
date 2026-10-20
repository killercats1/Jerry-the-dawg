package com.jerrythedawg.voice;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * The other end of Minecraft's /connect 127.0.0.1:3000: a tiny WebSocket server speaking
 * Minecraft's JSON command protocol (the same as voice-helper/jerry_voice.py).
 * Only accepts connections from this phone.
 */
final class MinecraftLink {
    interface Listener {
        /** You used "Talk to Your Dog" in the game. */
        void onTalkPressed();

        /** Smart Jerry is on (the add-on then sends us questions). */
        boolean smart();

        /** The add-on wants Smart Jerry to answer this player (their question is in their tags). */
        void onAskAI(String player);
    }

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String LISTEN_TAG = "jf_listen"; // the add-on tags you when you press Talk
    private static final String AI_ASK_TAG = "jf_ai_ask"; // ...and this when Smart Jerry should answer

    private final int port;
    private final Listener listener;
    private volatile ServerSocket server;
    private volatile Client client;
    private volatile boolean running;

    MinecraftLink(int port, Listener listener) {
        this.port = port;
        this.listener = listener;
    }

    void start() {
        running = true;
        Thread thread = new Thread(this::acceptLoop, "minecraft-link");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
        }
        Client c = client;
        if (c != null) c.close();
    }

    boolean connected() {
        Client c = client;
        return c != null && !c.closed;
    }

    static String stripColors(String text) {
        return String.valueOf(text).replaceAll("§.", "");
    }

    /** A player's tags, from "/tag <name> list" ("Steve has 3 tags: a, b, c"). */
    java.util.List<String> tags(String player) {
        java.util.List<String> out = new java.util.ArrayList<>();
        JSONObject r = command("tag \"" + player + "\" list");
        if (r == null) return out;
        String msg = stripColors(r.optString("statusMessage", ""));
        int colon = msg.indexOf(':');
        if (colon < 0) return out;
        for (String t : msg.substring(colon + 1).split(",")) {
            String tag = t.trim();
            if (!tag.isEmpty()) out.add(tag);
        }
        return out;
    }

    /** Runs a command in the game as you (returns Minecraft's answer, or null). */
    JSONObject command(String line) {
        Client c = client;
        return c == null ? null : c.command(line, 4000);
    }

    private void acceptLoop() {
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
            server = s;
            AppLog.add("Ready. In Minecraft type: /connect 127.0.0.1:" + port);
            while (running) {
                Socket socket = s.accept();
                Client c = new Client(socket);
                Thread t = new Thread(c::run, "minecraft-client");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (running) {
                AppLog.add("Couldn't open port " + port + " (" + e.getMessage() + "). Is the Termux helper still running? Stop it.");
            }
        }
    }

    private final class Client {
        private final Socket socket;
        private InputStream in;
        private OutputStream out;
        private final Map<String, CompletableFuture<JSONObject>> pending = new ConcurrentHashMap<>();
        volatile boolean closed;

        Client(Socket socket) {
            this.socket = socket;
        }

        void run() {
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
                if (!handshake()) {
                    close();
                    return;
                }
                Client old = client;
                client = this;
                if (old != null) old.close();
                AppLog.add("Minecraft connected! Talk to Jerry.");
                Thread poller = new Thread(this::pollLoop, "minecraft-poll");
                poller.setDaemon(true);
                poller.start();
                readLoop();
            } catch (IOException ignored) {
            } finally {
                close();
                if (client == this) {
                    client = null;
                    if (running) AppLog.add("Minecraft disconnected. After rejoining your world, type /connect 127.0.0.1:" + port + " again.");
                }
            }
        }

        private boolean handshake() throws IOException {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                head.write(b);
                byte[] h = head.toByteArray();
                int n = h.length;
                if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n' && h[n - 2] == '\r' && h[n - 1] == '\n') break;
                if (n > 16384) return false;
            }
            String key = null;
            for (String line : head.toString("UTF-8").split("\r\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Sec-WebSocket-Key")) {
                    key = line.substring(colon + 1).trim();
                }
            }
            if (key == null) {
                out.write("HTTP/1.1 400 Bad Request\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                return false;
            }
            String accept;
            try {
                MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
                accept = android.util.Base64.encodeToString(sha1.digest((key + WS_GUID).getBytes(StandardCharsets.US_ASCII)), android.util.Base64.NO_WRAP);
            } catch (Exception e) {
                return false;
            }
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return true;
        }

        private int readByte() throws IOException {
            int b = in.read();
            if (b < 0) throw new IOException("closed");
            return b;
        }

        private void readFully(byte[] buffer) throws IOException {
            int off = 0;
            while (off < buffer.length) {
                int n = in.read(buffer, off, buffer.length - off);
                if (n < 0) throw new IOException("closed");
                off += n;
            }
        }

        private void readLoop() throws IOException {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            while (!closed) {
                int b1 = readByte();
                int b2 = readByte();
                int opcode = b1 & 0x0F;
                long length = b2 & 0x7F;
                if (length == 126) {
                    length = (readByte() << 8) | readByte();
                } else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) length = (length << 8) | readByte();
                }
                if (length > 16 * 1024 * 1024) throw new IOException("frame too big");
                byte[] mask = null;
                if ((b2 & 0x80) != 0) {
                    mask = new byte[4];
                    readFully(mask);
                }
                byte[] data = new byte[(int) length];
                readFully(data);
                if (mask != null) {
                    for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4];
                }
                if (opcode == 8) return; // close
                if (opcode == 9) { // ping
                    send(10, data);
                    continue;
                }
                if (opcode == 0 || opcode == 1 || opcode == 2) {
                    message.write(data);
                    if ((b1 & 0x80) == 0) continue; // more fragments coming
                    String text = message.toString("UTF-8");
                    message.reset();
                    try {
                        JSONObject packet = new JSONObject(text);
                        String id = packet.optJSONObject("header") == null ? null : packet.getJSONObject("header").optString("requestId", null);
                        CompletableFuture<JSONObject> future = id == null ? null : pending.remove(id);
                        if (future != null) future.complete(packet.optJSONObject("body") == null ? new JSONObject() : packet.getJSONObject("body"));
                    } catch (JSONException ignored) {
                    }
                }
            }
        }

        private synchronized void send(int opcode, byte[] payload) throws IOException {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x80 | opcode);
            int n = payload.length;
            if (n < 126) {
                frame.write(n);
            } else if (n < 65536) {
                frame.write(126);
                frame.write(n >> 8);
                frame.write(n & 0xFF);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) frame.write((int) (((long) n >> (8 * i)) & 0xFF));
            }
            frame.write(payload);
            out.write(frame.toByteArray());
            out.flush();
        }

        JSONObject command(String line, long timeoutMs) {
            if (closed) return null;
            String id = UUID.randomUUID().toString();
            CompletableFuture<JSONObject> future = new CompletableFuture<>();
            pending.put(id, future);
            try {
                JSONObject packet = new JSONObject()
                        .put("header", new JSONObject().put("version", 1).put("requestId", id)
                                .put("messagePurpose", "commandRequest").put("messageType", "commandRequest"))
                        .put("body", new JSONObject().put("version", 1).put("commandLine", line)
                                .put("origin", new JSONObject().put("type", "player")));
                send(1, packet.toString().getBytes(StandardCharsets.UTF_8));
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                pending.remove(id);
                return null;
            }
        }

        /** Says hello every 5 seconds (so the add-on knows voice is on) and watches for the Talk button. */
        private void pollLoop() {
            long lastHello = 0;
            while (!closed && running) {
                long now = System.currentTimeMillis();
                if (now - lastHello > 5000) {
                    lastHello = now;
                    command("scriptevent jf:voice_hello" + (listener.smart() ? " ai" : ""), 4000);
                }
                JSONObject found = command("testfor @a[tag=" + LISTEN_TAG + "]", 4000);
                if (found != null && found.optInt("statusCode", -1) == 0) {
                    command("tag @a remove " + LISTEN_TAG, 4000);
                    command("scriptevent jf:voice_status listening", 4000);
                    listener.onTalkPressed();
                }
                if (listener.smart()) {
                    JSONObject asking = command("testfor @a[tag=" + AI_ASK_TAG + "]", 4000);
                    if (asking != null && asking.optInt("statusCode", -1) == 0) {
                        // "Found Steve" or "Found Steve, Alex"
                        String names = stripColors(asking.optString("statusMessage", "")).replaceFirst("^\\s*Found\\s*", "");
                        for (String name : names.split(",")) {
                            String who = name.trim();
                            if (who.isEmpty() || who.contains("\"")) continue;
                            command("tag \"" + who + "\" remove " + AI_ASK_TAG, 4000);
                            listener.onAskAI(who);
                        }
                    }
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        void close() {
            closed = true;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            for (CompletableFuture<JSONObject> f : pending.values()) f.complete(null);
            pending.clear();
        }
    }
}
