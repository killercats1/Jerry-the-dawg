package com.jerrythedawg.verity;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Does the job of Verity's computer/Termux bridge (PnTMC's "Verity - Bedrock Edition") over the
 * /connect link. The add-on's side of it is in its scripts/verity_ai_bridge.js:
 *   tag pntmc_bridge_on            we're connected: typed chat goes to us instead of keyword replies
 *   scriptevent pntmc:ai_ask Name|text
 *   → add-on prepares and tags the player: pntmc_ai_needed | pntmc_ai_ignore | pntmc_ai_silent,
 *     plus context: pntmc_acted, pntmc_i_<intent>, pntmc_d_<draft>, pntmc_ph_<phase>, pntmc_b_/e_/k_
 *   scriptevent pntmc:ai_say Name|ticks|reply   (chat + mouth animation; we play the voice)
 *   pntmc_hurt_speak + pntmc_d_<line> → we say the line → scriptevent pntmc:ai_hurt Name|line
 *   pntmc_want_talk                 "!talk" in chat: the next thing you say goes to Verity
 */
final class VerityBridge implements Heard, VerityLink.Listener {
    /** Verity's thinking and voice (VerityBrain; a stand-in in tests). */
    interface Mind {
        String reply(String player, String said, VerityBrain.Prep prep);

        /** A ready voice clip, or null for chat only. */
        Voice speak(String line);
    }

    interface Voice {
        int durationMs();

        void playAndWait();
    }

    interface Mic {
        void setPaused(boolean paused);
    }

    static final int PORT = 3000;
    private static final String BRIDGE_ON = "pntmc_bridge_on";

    private final VerityLink link;
    private final Mind mind;
    private volatile Mic mic;
    /** One thing at a time: asks, replies and voice lines happen in order. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile String playerName = "";
    private final boolean wakeWordOnly;
    private volatile long talkUntil;
    private volatile boolean busy;
    private volatile boolean polling;
    private volatile boolean running = true;

    VerityBridge(Mind mind, boolean wakeWordOnly) {
        this.mind = mind;
        this.wakeWordOnly = wakeWordOnly;
        this.link = new VerityLink(PORT, this);
    }

    void setMic(Mic mic) {
        this.mic = mic;
    }

    void start() {
        link.start();
    }

    boolean connected() {
        return link.connected();
    }

    /** Turn the bridge tag off (so Verity's built-in chat works again) and close. */
    void stop() {
        running = false;
        worker.shutdownNow();
        Thread off = new Thread(() -> {
            if (link.connected()) link.command("tag @s remove " + BRIDGE_ON);
            link.stop();
        }, "verity-off");
        off.start();
    }

    @Override
    public void onConnected() {
        JSONObject found = link.command("testfor @s");
        String msg = found == null ? "" : strip(found.optString("statusMessage", ""));
        if (msg.startsWith("Found ")) playerName = msg.substring(6).trim();
        link.command("tag @s add " + BRIDGE_ON);
        AppLog.add("Verity bridge on" + (playerName.isEmpty() ? "" : " for " + playerName) + ". Talk to Verity!");
        if (!polling) {
            polling = true;
            Thread t = new Thread(this::pollTags, "verity-tags");
            t.setDaemon(true);
            t.start();
        }
    }

    @Override
    public void onDisconnected() {
        // nothing: we keep listening and wait for the next /connect
    }

    /** Typed chat: with the bridge on, the add-on leaves it to us. */
    @Override
    public void onChat(String type, String sender, String message) {
        if (!"chat".equals(type)) return;
        if (!playerName.isEmpty() && !playerName.equalsIgnoreCase(strip(sender))) return;
        String text = message.trim();
        if (text.isEmpty() || text.startsWith("!") || text.startsWith("/")) return; // add-on commands
        worker.execute(() -> ask(text));
    }

    /** Something you said out loud. */
    @Override
    public void heard(String raw) {
        String text = VerityBrain.clean(raw);
        if (text.isEmpty() || busy) return;
        boolean talking = System.currentTimeMillis() < talkUntil;
        if (wakeWordOnly && !talking && !text.toLowerCase(Locale.US).contains("verity")) return;
        talkUntil = 0;
        if (!link.connected()) {
            AppLog.add("Heard \"" + text + "\", but Minecraft isn't connected (/connect 127.0.0.1:" + PORT + ").");
            return;
        }
        AppLog.add("You: \"" + text + "\"");
        worker.execute(() -> ask(text));
    }

    /** One message to Verity, the way her bridge does it. */
    private void ask(String text) {
        if (!link.connected()) return;
        busy = true;
        try {
            String said = text.replace('\n', ' ');
            link.command("scriptevent pntmc:ai_ask " + playerName + "|" + said);
            // Wait for the add-on to say whether Verity should answer, and what it prepared.
            Set<String> tags = null;
            long until = System.currentTimeMillis() + 8000;
            while (System.currentTimeMillis() < until) {
                Set<String> now = tags();
                if (now.contains("pntmc_ai_ignore") || now.contains("pntmc_ai_silent") || now.contains("pntmc_ai_needed")) {
                    tags = now;
                    break;
                }
                sleep(250);
            }
            if (tags == null) tags = tags();
            link.command("tag @s remove pntmc_ai_needed");
            link.command("tag @s remove pntmc_ai_ignore");
            link.command("tag @s remove pntmc_ai_silent");
            if (tags.contains("pntmc_ai_ignore")) return; // not for Verity
            if (tags.contains("pntmc_ai_silent")) return; // she already answered with a built-in voice line

            VerityBrain.Prep prep = prepFrom(tags);
            String reply = mind.reply(playerName, said, prep);
            if (reply == null) return;
            say(reply, "pntmc:ai_say");
        } finally {
            busy = false;
        }
    }

    /** Verity speaks: voice clip (if FishAudio is set up) + the add-on shows chat and moves her mouth. */
    private void say(String line, String event) {
        AppLog.add("Verity: \"" + line + "\"");
        Voice clip = mind.speak(line);
        if ("pntmc:ai_hurt".equals(event)) {
            // Hurt lines: we print the chat line ourselves; the add-on keeps her hurt face.
            link.command("tellraw @a " + rawText("<§eVerity§r> " + line));
            link.command("scriptevent " + event + " " + playerName + "|" + line);
        } else if (clip != null) {
            int ticks = Math.max(20, (int) Math.ceil(clip.durationMs() / 50.0));
            link.command("scriptevent " + event + " " + playerName + "|" + ticks + "|" + line);
        } else {
            link.command("scriptevent " + event + " " + playerName + "|" + line);
        }
        if (clip != null) {
            pauseListening(true);
            clip.playAndWait();
            pauseListening(false);
        }
    }

    private void pauseListening(boolean pause) {
        Mic m = mic;
        if (m != null) m.setPaused(pause);
    }

    /** Watches for "!talk" and for hurt lines the add-on wants spoken. */
    private void pollTags() {
        while (running) {
            if (link.connected() && !busy) {
                Set<String> tags = tags();
                if (tags.contains("pntmc_want_talk")) {
                    link.command("tag @s remove pntmc_want_talk");
                    talkUntil = System.currentTimeMillis() + 10000;
                    AppLog.add("!talk: listening for what you say next.");
                }
                if (tags.contains("pntmc_hurt_speak")) {
                    link.command("tag @s remove pntmc_hurt_speak");
                    String line = "";
                    for (String t : tags) if (t.startsWith("pntmc_d_")) line = t.substring(8);
                    final String text = sentence(line.replace('_', ' '));
                    if (!text.isEmpty()) worker.execute(() -> say(text, "pntmc:ai_hurt"));
                }
            }
            sleep(700);
        }
    }

    /** The player's tags, from "/tag @s list" ("Steve has 3 tags: a, b, c"). */
    private Set<String> tags() {
        Set<String> out = new HashSet<>();
        JSONObject r = link.command("tag @s list");
        if (r == null) return out;
        String msg = strip(r.optString("statusMessage", ""));
        int colon = msg.indexOf(':');
        if (colon < 0) return out;
        for (String t : msg.substring(colon + 1).split(",")) {
            String tag = t.trim();
            if (!tag.isEmpty()) out.add(tag);
        }
        return out;
    }

    private static VerityBrain.Prep prepFrom(Set<String> tags) {
        VerityBrain.Prep p = new VerityBrain.Prep();
        for (String t : tags) {
            if (t.equals("pntmc_acted")) p.acted = true;
            else if (t.startsWith("pntmc_i_")) p.intent = words(t.substring(8));
            else if (t.startsWith("pntmc_d_")) p.draft = words(t.substring(8));
            else if (t.startsWith("pntmc_b_")) p.biome = words(t.substring(8));
            else if (t.startsWith("pntmc_e_")) p.entity = words(t.substring(8));
            else if (t.startsWith("pntmc_k_")) p.block = words(t.substring(8));
            else if (t.startsWith("pntmc_ph_")) {
                try {
                    p.phase = Integer.parseInt(t.substring(9));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return p;
    }

    private static String words(String token) {
        return token.replace('_', ' ').trim();
    }

    private static String sentence(String s) {
        String t = s.trim();
        if (t.isEmpty()) return t;
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        char last = t.charAt(t.length() - 1);
        return last == '.' || last == '!' || last == '?' ? t : t + "!";
    }

    private static String strip(String s) {
        return String.valueOf(s).replaceAll("§.", "");
    }

    private static String rawText(String text) {
        try {
            return new JSONObject().put("rawtext", new org.json.JSONArray().put(new JSONObject().put("text", text))).toString();
        } catch (Exception e) {
            return "{\"rawtext\":[{\"text\":\"\"}]}";
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

}
