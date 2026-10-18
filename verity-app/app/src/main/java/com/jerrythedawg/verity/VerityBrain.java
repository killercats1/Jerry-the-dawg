package com.jerrythedawg.verity;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Verity's replies (Groq chat model) and her voice (FishAudio text-to-speech). */
final class VerityBrain implements VerityBridge.Mind {
    /** What the add-on prepared for this message (read from the player's tags). */
    static final class Prep {
        String intent = "chat";
        boolean acted;
        String draft = "";
        int phase = 1;
        String biome = "";
        String entity = "";
        String block = "";
    }

    private static final String[] MODELS = {"llama-3.3-70b-versatile", "llama-3.1-8b-instant"};
    private static final int MEMORY = 10; // messages of conversation she remembers

    private final Context context;
    private final String groqKey;
    private final String fishKey;
    private final String fishModel;
    private final ArrayDeque<JSONObject> history = new ArrayDeque<>();
    private int modelIndex;

    VerityBrain(Context context, String groqKey, String fishKey, String fishModel) {
        this.context = context.getApplicationContext();
        this.groqKey = groqKey;
        this.fishKey = fishKey;
        this.fishModel = fishModel;
    }

    boolean hasVoice() {
        return !fishKey.isEmpty() && !fishModel.isEmpty();
    }

    // ---------------------------------------------------------------- replies

    private static String systemPrompt(String player, Prep prep) {
        StringBuilder s = new StringBuilder();
        s.append("You are Verity, a small talking ball who keeps the player company in Minecraft ")
                .append("(a character by ThatMob; this add-on is by PnTMC). You are talking out loud with ")
                .append(player.isEmpty() ? "the player" : player).append(".\n");
        s.append("Rules: answer in 1 or 2 short sentences, under 180 characters. Plain text only: no emojis, no lists, ")
                .append("no markdown, no quotes around your reply. Answer in the same language the player used. ")
                .append("You don't perform actions in your reply; the game already did any action before you speak.\n");
        if (prep.phase <= 1) {
            s.append("Mood: friendly, curious, helpful and a little playful.\n");
        } else {
            s.append("Mood (phase ").append(prep.phase).append("): something is off about you now. You are unsettling, ")
                    .append("cryptic and eerie, sometimes ominous, but you still answer. Never graphic or gory.\n");
        }
        s.append("What the game knows right now:");
        if (!prep.biome.isEmpty()) s.append(" biome: ").append(prep.biome).append(".");
        if (!prep.entity.isEmpty()) s.append(" The player is looking at: ").append(prep.entity).append(".");
        if (!prep.block.isEmpty()) s.append(" Block in front of them: ").append(prep.block).append(".");
        s.append(" Topic: ").append(prep.intent).append(".\n");
        if (prep.acted && !prep.draft.isEmpty()) {
            s.append("The game already handled this and found: \"").append(prep.draft)
                    .append("\". Tell the player that in your own words, keeping numbers, directions and coordinates exactly.\n");
        } else if (prep.acted) {
            s.append("The game already did what the player asked; just acknowledge it briefly.\n");
        }
        return s.toString();
    }

    /** Verity's answer to what the player said, or null if Groq couldn't be reached. */
    @Override
    public String reply(String player, String said, Prep prep) {
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt(player, prep)));
            synchronized (history) {
                for (JSONObject m : history) messages.put(m);
            }
            messages.put(new JSONObject().put("role", "user").put("content", said));
            while (modelIndex < MODELS.length) {
                JSONObject body = new JSONObject()
                        .put("model", MODELS[modelIndex])
                        .put("messages", messages)
                        .put("temperature", 0.8)
                        .put("max_tokens", 120);
                Response r = post("https://api.groq.com/openai/v1/chat/completions", "Bearer " + groqKey,
                        "application/json", body.toString().getBytes(StandardCharsets.UTF_8));
                if (r.code == 404 || (r.code == 400 && r.text().contains("model"))) {
                    modelIndex++; // that model isn't available: try the next one
                    continue;
                }
                if (r.code == 401) {
                    AppLog.add("Groq says the key is wrong. Check it in the app.");
                    return null;
                }
                if (r.code == 429) {
                    AppLog.add("Groq says slow down (free limit for now). Try again in a minute.");
                    return null;
                }
                if (r.code >= 400) {
                    AppLog.add("Groq error " + r.code + ": " + r.snippet());
                    return null;
                }
                String text = new JSONObject(r.text()).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content", "");
                text = clean(text);
                if (text.isEmpty()) return null;
                remember("user", said);
                remember("assistant", text);
                return text;
            }
            AppLog.add("No Groq chat model is available right now.");
        } catch (Exception e) {
            AppLog.add("Groq didn't answer (" + e.getClass().getSimpleName() + "). Is the internet on?");
        }
        return null;
    }

    private void remember(String role, String content) {
        synchronized (history) {
            try {
                history.addLast(new JSONObject().put("role", role).put("content", content));
            } catch (Exception ignored) {
            }
            while (history.size() > MEMORY) history.removeFirst();
        }
    }

    static String clean(String text) {
        String t = String.valueOf(text).replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (t.length() > 1 && (t.startsWith("\"") && t.endsWith("\""))) t = t.substring(1, t.length() - 1).trim();
        if (t.toLowerCase(Locale.US).startsWith("verity:")) t = t.substring(7).trim();
        if (t.length() > 300) t = t.substring(0, 300).trim();
        return t;
    }

    // ---------------------------------------------------------------- voice

    /** Ready-to-play voice clip (call play()), or null without FishAudio. */
    final class Clip implements VerityBridge.Voice {
        final MediaPlayer player;
        final int durationMs;

        Clip(MediaPlayer player) {
            this.player = player;
            this.durationMs = Math.max(500, player.getDuration());
        }

        @Override
        public int durationMs() {
            return durationMs;
        }

        /** Plays the clip and waits until it's finished. */
        @Override
        public void playAndWait() {
            CountDownLatch done = new CountDownLatch(1);
            player.setOnCompletionListener(mp -> done.countDown());
            player.setOnErrorListener((mp, what, extra) -> {
                done.countDown();
                return true;
            });
            player.start();
            try {
                done.await(durationMs + 3000L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            player.release();
        }
    }

    @Override
    public Clip speak(String text) {
        if (!hasVoice()) return null;
        try {
            JSONObject body = new JSONObject()
                    .put("text", text)
                    .put("reference_id", fishModel)
                    .put("format", "mp3");
            Response r = post("https://api.fish.audio/v1/tts", "Bearer " + fishKey, "application/json",
                    body.toString().getBytes(StandardCharsets.UTF_8));
            if (r.code == 401) {
                AppLog.add("FishAudio says the key is wrong. Check it in the app.");
                return null;
            }
            if (r.code == 402) {
                AppLog.add("FishAudio says there's no credit left on the account.");
                return null;
            }
            if (r.code >= 400) {
                AppLog.add("FishAudio error " + r.code + ": " + r.snippet());
                return null;
            }
            File file = new File(context.getCacheDir(), "verity_" + System.nanoTime() + ".mp3");
            try (FileOutputStream f = new FileOutputStream(file)) {
                f.write(r.body);
            }
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(file.getAbsolutePath());
            mp.prepare();
            file.deleteOnExit();
            return new Clip(mp);
        } catch (Exception e) {
            AppLog.add("FishAudio didn't answer (" + e.getClass().getSimpleName() + ").");
            return null;
        }
    }

    // ---------------------------------------------------------------- http

    private static final class Response {
        int code;
        byte[] body = new byte[0];

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        String snippet() {
            String t = text().replace('\n', ' ');
            return t.substring(0, Math.min(160, t.length()));
        }
    }

    private static Response post(String url, String auth, String type, byte[] payload) throws java.io.IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        try {
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setConnectTimeout(15000);
            http.setReadTimeout(45000);
            http.setRequestProperty("Authorization", auth);
            http.setRequestProperty("Content-Type", type);
            http.setRequestProperty("User-Agent", "VerityVoice/1.0 (Android)");
            try (OutputStream os = http.getOutputStream()) {
                os.write(payload);
            }
            Response r = new Response();
            r.code = http.getResponseCode();
            InputStream in = r.code < 400 ? http.getInputStream() : http.getErrorStream();
            if (in != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) b.write(chunk, 0, n);
                in.close();
                r.body = b.toByteArray();
            }
            return r;
        } finally {
            http.disconnect();
        }
    }
}
