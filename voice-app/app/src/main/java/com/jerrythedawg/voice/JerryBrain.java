package com.jerrythedawg.voice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Smart Jerry": a Groq chat model answers as Jerry and picks what he does. The add-on handles
 * simple commands itself and only asks us about questions and chatting.
 */
final class JerryBrain {
    /** What Jerry says and does. */
    static final class Answer {
        String say = "";
        String actions = ""; // comma-separated action ids from ACTIONS
        String face = "happy";
    }

    private static final String[] MODELS = {"llama-3.3-70b-versatile", "llama-3.1-8b-instant"};
    private static final int MEMORY = 10; // messages of conversation he remembers, per player

    // The things he can do (the action ids the add-on understands), with what they look like.
    private static final String ACTIONS = "love (wags and wiggles happily), listen (paw to his ear), hug (stands up and hugs), "
            + "scratch (scratches his ear), sneeze, catch (jumps and catches a treat), find (sniffs around and finds a treat), "
            + "shy (hides his face behind his paw), stretch, groom (washes his face like a cat), sleep (lies down and snores until called), "
            + "dead (plays dead), roll (rolls over for belly rubs), beg, sit (sits and stays), lie (lies down and stays), "
            + "shakeoff (wet-dog shake), highfive, paw (shakes hands), wave, bow, dance, backup (walks backwards), backflip, "
            + "howl, chase (chases his tail), come (comes to the player and follows again), bark, spin, hop (jumps), "
            + "bone (shows off his bone), fetch (the player throws his ball), play (zoomies), shock (jumps in surprise), "
            + "excited (hopes for a treat), yawn, greet (barks hello)";
    private static final String[] FACES = {"happy", "puppy", "love", "sleepy", "shocked"};

    private final String groqKey;
    private final Map<String, ArrayDeque<JSONObject>> history = new HashMap<>();
    private int modelIndex;

    JerryBrain(String groqKey) {
        this.groqKey = groqKey;
    }

    private static String systemPrompt(String context) {
        Map<String, String> c = new HashMap<>();
        for (String part : context.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) c.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        String dog = c.getOrDefault("dog", "Jerry");
        String owner = c.getOrDefault("owner", "the player");
        return "You are " + dog + ", a fluffy, goofy, very loyal pet wolf-dog in a cozy Minecraft add-on. "
                + owner + " is your owner and best friend, and they are talking to you out loud. "
                + "You understand everything and you answer in words, but you are still a dog at heart: cheerful, "
                + "simple, easily excited by treats, balls, belly rubs and naps, and sometimes you add a little "
                + "\"Woof!\" or an action like *wags tail*. Be warm and funny. Keep everything kid-friendly and wholesome: "
                + "nothing scary, mean or gross. If asked something a dog wouldn't know, guess cutely or relate it to dog things, "
                + "but you can still be helpful with simple Minecraft tips.\n"
                + "Right now: " + context + ".\n"
                + "Your mood comes from hunger, energy and fun (low hunger means you're hungry). Mochi is your family's cat "
                + "and the puppy is your little one, if you have them.\n"
                + "You can also DO things. Pick 0 to 3 of these actions when they fit what was said or how you feel "
                + "(if asked to do one, do it): " + ACTIONS + ". If asked for something that isn't on the list, "
                + "do the closest one or play along in words.\n"
                + "Reply with JSON only: {\"say\": \"what you say, 1 or 2 short sentences, under 160 characters, no emojis\", "
                + "\"actions\": [\"action ids\"], \"face\": \"one of happy, puppy, love, sleepy, shocked\"}";
    }

    /** Jerry's answer, or null if Groq couldn't be reached. */
    synchronized Answer reply(String player, String said, String context) {
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt(context)));
            ArrayDeque<JSONObject> past;
            synchronized (history) {
                past = history.computeIfAbsent(player, k -> new ArrayDeque<>());
                for (JSONObject m : past) messages.put(m);
            }
            messages.put(new JSONObject().put("role", "user").put("content", said));
            int tries = 0;
            while (modelIndex < MODELS.length && tries++ < 4) {
                JSONObject body = new JSONObject()
                        .put("model", MODELS[modelIndex])
                        .put("messages", messages)
                        .put("temperature", 0.9)
                        .put("max_tokens", 160)
                        .put("response_format", new JSONObject().put("type", "json_object"));
                int code = post(body.toString());
                if (code == 404 || (code == 400 && lastBody.contains("model"))) {
                    modelIndex++; // that model isn't available: try the next one
                    continue;
                }
                if (code == 401) {
                    AppLog.add("Smart Jerry: Groq says the key is wrong. Check it in the app.");
                    return null;
                }
                if (code == 429) {
                    // Each model has its own free limit, so the small one may still have room.
                    if (modelIndex + 1 < MODELS.length) {
                        modelIndex++;
                        continue;
                    }
                    AppLog.add("Smart Jerry: Groq says slow down (free limit for now). Try again in a minute.");
                    modelIndex = 0;
                    return null;
                }
                if (code >= 400) {
                    AppLog.add("Smart Jerry: Groq error " + code + ": " + snippet(lastBody));
                    return null;
                }
                String content = new JSONObject(lastBody).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content", "");
                Answer a = parse(content);
                if (a == null) return null;
                remember(past, "user", said);
                remember(past, "assistant", content);
                modelIndex = 0; // back to the best model next time
                return a;
            }
            AppLog.add("Smart Jerry: no Groq chat model is available right now.");
            modelIndex = 0;
        } catch (Exception e) {
            AppLog.add("Smart Jerry: Groq didn't answer (" + e.getClass().getSimpleName() + "). Is the internet on?");
        }
        return null;
    }

    static Answer parse(String content) {
        try {
            String json = content.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            JSONObject o = new JSONObject(json.substring(start, end + 1));
            Answer a = new Answer();
            a.say = clean(o.optString("say", ""));
            StringBuilder actions = new StringBuilder();
            JSONArray list = o.optJSONArray("actions");
            if (list != null) {
                for (int i = 0; i < list.length() && i < 3; i++) {
                    String id = list.optString(i, "").toLowerCase(Locale.US).replaceAll("[^a-z]", "");
                    if (id.isEmpty()) continue;
                    if (actions.length() > 0) actions.append(',');
                    actions.append(id);
                }
            }
            a.actions = actions.toString();
            String face = o.optString("face", "happy").toLowerCase(Locale.US).trim();
            for (String f : FACES) if (f.equals(face)) a.face = f;
            return a.say.isEmpty() && a.actions.isEmpty() ? null : a;
        } catch (Exception e) {
            return null;
        }
    }

    /** One line that's safe inside a Minecraft command. */
    static String clean(String text) {
        String t = String.valueOf(text).replaceAll("[\\r\\n|§\"\\\\]+", " ")
                .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Zs}*~]", "") // no emojis: Minecraft can't show them
                .replaceAll("\\s+", " ").trim();
        if (t.length() > 220) t = t.substring(0, 220).trim();
        return t;
    }

    private static void remember(ArrayDeque<JSONObject> past, String role, String content) {
        synchronized (past) {
            try {
                past.addLast(new JSONObject().put("role", role).put("content", content));
            } catch (Exception ignored) {
            }
            while (past.size() > MEMORY) past.removeFirst();
        }
    }

    // ---------------------------------------------------------------- http

    private String lastBody = "";

    private int post(String json) throws java.io.IOException {
        HttpURLConnection http = (HttpURLConnection) new URL("https://api.groq.com/openai/v1/chat/completions").openConnection();
        try {
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setConnectTimeout(10000);
            http.setReadTimeout(20000);
            http.setRequestProperty("Authorization", "Bearer " + groqKey);
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("User-Agent", "JerryVoice/1.0 (Android)");
            try (OutputStream os = http.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = http.getResponseCode();
            InputStream in = code < 400 ? http.getInputStream() : http.getErrorStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            if (in != null) {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) b.write(chunk, 0, n);
                in.close();
            }
            lastBody = b.toString("UTF-8");
            return code;
        } finally {
            http.disconnect();
        }
    }

    private static String snippet(String text) {
        String t = text.replace('\n', ' ');
        return t.substring(0, Math.min(160, t.length()));
    }
}
