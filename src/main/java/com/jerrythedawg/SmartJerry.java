package com.jerrythedawg;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * "Smart Jerry": questions and chatting that aren't simple commands go to an AI (a Groq chat
 * model) that answers as your dog and picks a few of his tricks to match. Off unless the server
 * turns it on with a free Groq key (config.yml, or /dog ai). The web call runs off the main thread;
 * everything that touches the world happens back on it.
 */
final class SmartJerry {
    static final long WAIT_TICKS = 240; // 12 seconds, then he gives up and tilts his head

    private static final String[] MODELS = {"llama-3.3-70b-versatile", "llama-3.1-8b-instant"};
    private static final int MEMORY = 10; // messages of conversation he remembers, per player

    /** The actions the AI may pick (the same ids as the chat commands). */
    static final Set<String> ACTIONS = new HashSet<String>(Arrays.asList(
        "love", "listen", "hug", "scratch", "sneeze", "catch", "find", "shy", "stretch", "groom", "sleep", "dead", "roll", "beg",
        "sit", "lie", "shakeoff", "highfive", "paw", "wave", "bow", "dance", "backup", "backflip", "howl", "chase", "come", "bark",
        "spin", "hop", "bone", "fetch", "play", "shock", "excited", "yawn", "greet"));
    private static final String ACTION_HELP = "love (wags and wiggles happily), listen (sits and listens closely), hug (comes over for a hug), "
        + "scratch (scratches his ear), sneeze, catch (jumps and catches a treat), find (sniffs around and finds a treat), "
        + "shy (hides his face), stretch, groom (washes his face like a cat), sleep (lies down and snores until called), "
        + "dead (plays dead), roll (rolls over for belly rubs), beg, sit (sits and stays), lie (lies down and stays), "
        + "shakeoff (wet-dog shake), highfive, paw (shakes hands), wave, bow, dance, backup (walks backwards), backflip, "
        + "howl, chase (chases his tail), come (comes to the player and follows again), bark, spin, hop (jumps), "
        + "bone (shows off his bone), fetch (the player throws his ball), play (zoomies), shock (jumps in surprise), "
        + "excited (hopes for a treat), yawn, greet (barks hello)";
    private static final Pattern CHATTY = Pattern.compile(
        "^(what|whats|what's|why|how|who|where|when|which|do|does|did|are|is|can|could|would|will|should|have|has|tell|i|i'm|im|"
            + "you|you're|youre|your|guess|wanna|want|was|were)\\b");

    /** What he says and does. */
    static final class Answer {
        String say = "";
        final List<String> actions = new ArrayList<String>();
    }

    private final JerryPlugin plugin;
    private final Pets pets;
    private Speech speech;
    private final Map<UUID, ArrayDeque<JsonObject>> history = new HashMap<UUID, ArrayDeque<JsonObject>>();
    private final Map<UUID, Integer> pending = new HashMap<UUID, Integer>();
    private int tokens;

    SmartJerry(JerryPlugin plugin, Pets pets) {
        this.plugin = plugin;
        this.pets = pets;
    }

    void setSpeech(Speech speech) {
        this.speech = speech;
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("smart-jerry.enabled", false) && !key().isEmpty();
    }

    private String key() {
        return plugin.getConfig().getString("smart-jerry.groq-api-key", "").trim();
    }

    /** Simple commands stay instant; questions, chatting and anything he doesn't know go to the AI. */
    boolean wants(String said, List<Text.Voice> commands, String words) {
        if (!enabled()) {
            return false;
        }
        for (Text.Voice v : commands) {
            if ("math".equals(v.action)) {
                return false;
            }
        }
        List<String> list = new ArrayList<String>();
        for (String word : words.split(" ")) {
            if (!word.isEmpty() && !"jerry".equals(word) && !"hey".equals(word)) {
                list.add(word);
            }
        }
        if (commands.isEmpty()) {
            return !list.isEmpty();
        }
        boolean question = said.trim().endsWith("?");
        if (list.size() <= 3 && !question) {
            return false;
        }
        return question || list.size() > 5 || CHATTY.matcher(join(list, " ")).find();
    }

    /** Asks the AI, keeps the speaker up while he "thinks", and acts on the answer. */
    void ask(final Player player, final Wolf dog, String said, long start) {
        final UUID id = player.getUniqueId();
        final int token = ++tokens;
        pending.put(id, token);
        final String context = context(player, dog);
        final String question = clean(said);
        final String apiKey = key();
        pets.later(Math.max(1, start), new Runnable() {
            @Override
            public void run() {
                if (Integer.valueOf(token).equals(pending.get(id)) && dog.isValid()) {
                    pets.holograms().speaker(dog, WAIT_TICKS);
                    pets.sound(dog, Sound.WOLF_WHINE, 1.4f); // "hmm?"
                }
            }
        });
        pets.later(WAIT_TICKS, new Runnable() {
            @Override
            public void run() {
                if (!Integer.valueOf(token).equals(pending.get(id))) {
                    return;
                }
                pending.remove(id);
                if (player.isOnline()) {
                    pets.tell(player, Text.AI_NO_ANSWER);
                }
                if (dog.isValid()) {
                    pets.holograms().hide(dog);
                    pets.sound(dog, Sound.WOLF_WHINE, 1f);
                    pets.spin(dog, 1, 15); // a puzzled little turn (1.8 wolves can't tilt their heads on cue)
                }
            }
        });
        final ArrayDeque<JsonObject> past = memory(id);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final Answer answer = reply(apiKey, past, question, context);
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (answer == null || !Integer.valueOf(token).equals(pending.get(id))) {
                            return; // the timeout explains what happened
                        }
                        pending.remove(id);
                        act(player, dog, answer);
                    }
                });
            }
        });
    }

    private void act(final Player player, final Wolf dog, Answer answer) {
        if (!player.isOnline() || !dog.isValid()) {
            return;
        }
        pets.holograms().speaker(dog, 40);
        if (!answer.say.isEmpty()) {
            player.sendMessage("§b" + plugin.data(player).dogName + "§f: " + answer.say);
        }
        if (answer.actions.isEmpty()) {
            pets.sound(dog, Sound.WOLF_PANT, 1f);
        }
        long at = 6;
        for (String action : answer.actions) {
            final Text.Voice entry = Text.voiceFor(action);
            if (entry == null) {
                continue;
            }
            pets.later(at, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline() && dog.isValid()) {
                        speech.respond(player, dog, entry, true);
                    }
                }
            });
            at += Speech.ticksFor(entry);
        }
    }

    /** What the AI should know about him right now. */
    private String context(Player player, Wolf dog) {
        PetData data = plugin.data(player);
        List<String> mastered = new ArrayList<String>();
        for (Map.Entry<String, Integer> skill : data.skills.entrySet()) {
            if (!skill.getKey().contains(":") && skill.getValue() >= Pets.MASTER_AT) {
                mastered.add(Pets.trickName(skill.getKey()));
            }
        }
        return "dog=" + data.dogName + "; owner=" + player.getName()
            + "; friendship=" + Text.LEVELS[Pets.levelOf(data.friendship)]
            + "; mood=" + Pets.moodOf(data) + "; hunger=" + data.hunger + "/100; energy=" + data.energy + "/100; fun=" + data.fun + "/100"
            + "; cat=" + (data.hasCat ? data.catName : "none yet") + "; puppy=" + (data.hasPup ? data.pupName : "none yet")
            + "; time=" + (Pets.isNight(dog.getWorld()) ? "night" : "day") + "; resting=" + (dog.isSitting() ? "yes" : "no")
            + "; place=" + dog.getWorld().getEnvironment().name().toLowerCase(Locale.ROOT)
            + "; mastered=" + (mastered.isEmpty() ? "nothing yet" : join(mastered, " / "));
    }

    private ArrayDeque<JsonObject> memory(UUID id) {
        ArrayDeque<JsonObject> past = history.get(id);
        if (past == null) {
            past = new ArrayDeque<JsonObject>();
            history.put(id, past);
        }
        return past;
    }

    static String systemPrompt(String context) {
        Map<String, String> c = new HashMap<String, String>();
        for (String part : context.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                c.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        String dog = c.containsKey("dog") ? c.get("dog") : "Jerry";
        String owner = c.containsKey("owner") ? c.get("owner") : "the player";
        return "You are " + dog + ", a fluffy, goofy, very loyal pet wolf-dog on a cozy Minecraft server. "
            + owner + " is your owner and best friend, and they are talking to you in chat. "
            + "You understand everything and you answer in words, but you are still a dog at heart: cheerful, "
            + "simple, easily excited by treats, balls, belly rubs and naps, and sometimes you add a little "
            + "\"Woof!\" or an action like *wags tail*. Be warm and funny. Keep everything kid-friendly and wholesome: "
            + "nothing scary, mean or gross. If asked something a dog wouldn't know, guess cutely or relate it to dog things, "
            + "but you can still be helpful with simple Minecraft tips.\n"
            + "Right now: " + context + ".\n"
            + "Your mood comes from hunger, energy and fun (low hunger means you're hungry). Mochi is your family's cat "
            + "and the puppy is your little one, if you have them.\n"
            + "You can also DO things. Pick 0 to 3 of these actions when they fit what was said or how you feel "
            + "(if asked to do one, do it): " + ACTION_HELP + ". If asked for something that isn't on the list, "
            + "do the closest one or play along in words.\n"
            + "Reply with JSON only: {\"say\": \"what you say, 1 or 2 short sentences, under 160 characters, no emojis\", "
            + "\"actions\": [\"action ids\"], \"face\": \"one of happy, puppy, love, sleepy, shocked\"}";
    }

    /** His answer, or null if Groq couldn't be reached (runs off the main thread). */
    private Answer reply(String apiKey, ArrayDeque<JsonObject> past, String said, String context) {
        try {
            JsonArray messages = new JsonArray();
            messages.add(message("system", systemPrompt(context)));
            synchronized (past) {
                for (JsonObject m : past) {
                    messages.add(m);
                }
            }
            messages.add(message("user", said));
            for (int model = 0; model < MODELS.length; model++) {
                JsonObject body = new JsonObject();
                body.addProperty("model", MODELS[model]);
                body.add("messages", messages);
                body.addProperty("temperature", 0.9);
                body.addProperty("max_tokens", 160);
                JsonObject format = new JsonObject();
                format.addProperty("type", "json_object");
                body.add("response_format", format);
                String[] response = post(apiKey, body.toString());
                int code = Integer.parseInt(response[0]);
                if (code == 404 || code == 429 || (code == 400 && response[1].contains("model"))) {
                    continue; // that model is missing or busy (each has its own free limit): try the next one
                }
                if (code == 401) {
                    plugin.getLogger().warning("Smart Jerry: Groq says the API key is wrong (check smart-jerry.groq-api-key).");
                    return null;
                }
                if (code >= 400) {
                    plugin.getLogger().warning("Smart Jerry: Groq error " + code + ": " + snippet(response[1]));
                    return null;
                }
                String content = new JsonParser().parse(response[1]).getAsJsonObject().getAsJsonArray("choices").get(0)
                    .getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                Answer answer = parse(content);
                if (answer == null) {
                    return null;
                }
                synchronized (past) {
                    past.addLast(message("user", said));
                    past.addLast(message("assistant", content));
                    while (past.size() > MEMORY) {
                        past.removeFirst();
                    }
                }
                return answer;
            }
            plugin.getLogger().warning("Smart Jerry: Groq is busy or has no chat model available right now.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Smart Jerry: Groq didn't answer (" + ex.getClass().getSimpleName() + ").");
        }
        return null;
    }

    /** Reads the AI's JSON answer. Unknown actions are dropped; null if there's nothing usable. */
    static Answer parse(String content) {
        try {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JsonObject o = new JsonParser().parse(content.substring(start, end + 1)).getAsJsonObject();
            Answer a = new Answer();
            JsonElement say = o.get("say");
            a.say = say != null && say.isJsonPrimitive() ? clean(say.getAsString()) : "";
            JsonElement list = o.get("actions");
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : list.getAsJsonArray()) {
                    if (a.actions.size() >= 3) {
                        break;
                    }
                    String id = e.isJsonPrimitive() ? e.getAsString().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "") : "";
                    if (ACTIONS.contains(id)) {
                        a.actions.add(id);
                    }
                }
            }
            return a.say.isEmpty() && a.actions.isEmpty() ? null : a;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** One plain line: no colour codes, line breaks or emojis (the 1.8 font can't show them). */
    static String clean(String text) {
        String t = String.valueOf(text).replaceAll("§.?", "").replaceAll("[\\r\\n]+", " ")
            .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Zs}*~+=<>^]", "")
            .replaceAll("\\s+", " ").trim();
        return t.length() > 220 ? t.substring(0, 220).trim() : t;
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private static String[] post(String apiKey, String json) throws java.io.IOException {
        HttpURLConnection http = (HttpURLConnection) new URL("https://api.groq.com/openai/v1/chat/completions").openConnection();
        try {
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setConnectTimeout(10000);
            http.setReadTimeout(20000);
            http.setRequestProperty("Authorization", "Bearer " + apiKey);
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("User-Agent", "JerryTheDawg/1.0 (Spigot)");
            OutputStream os = http.getOutputStream();
            try {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            } finally {
                os.close();
            }
            int code = http.getResponseCode();
            InputStream in = code < 400 ? http.getInputStream() : http.getErrorStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            if (in != null) {
                try {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) > 0) {
                        b.write(chunk, 0, n);
                    }
                } finally {
                    in.close();
                }
            }
            return new String[] {String.valueOf(code), new String(b.toByteArray(), StandardCharsets.UTF_8)};
        } finally {
            http.disconnect();
        }
    }

    private static String snippet(String text) {
        String t = text.replace('\n', ' ');
        return t.substring(0, Math.min(160, t.length()));
    }

    private static String join(List<String> words, String gap) {
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            out.append(out.length() == 0 ? "" : gap).append(w);
        }
        return out.toString();
    }
}
