package com.jerrythedawg.voice;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Records the microphone itself, cuts out each thing you say (by loudness) and turns it into text
 * with Groq's Whisper (free key from console.groq.com).
 */
final class GroqListener {
    private static final int RATE = 16000;
    private static final int CHUNK = 512; // 32 ms
    private static final String VOCABULARY = "Jerry, Mochi, Pip, kitty. Sit, stay, come here, lie down, roll over, "
            + "shake, paw, high five, wave, beg, play dead, bow, dance, backflip, howl, chase your tail, spin left, "
            + "spin right, jump, speak, fetch, find it, peekaboo, stretch, groom, good boy, good girl, treat, zoomies.";
    private static final Set<String> HALLUCINATIONS = new HashSet<>(Arrays.asList(
            "you", "thank you", "thank you.", "thanks for watching!", "thank you for watching.",
            "thanks for watching.", ".", "bye.", "so", "uh", "um"));

    private final String key;
    private final Heard out;
    private final ExecutorService uploader = Executors.newSingleThreadExecutor();
    private volatile boolean active;
    private Thread thread;

    GroqListener(String key, Heard out) {
        this.key = key;
        this.out = out;
    }

    void start() {
        active = true;
        thread = new Thread(this::recordLoop, "groq-listener");
        thread.start();
    }

    void stop() {
        active = false;
        uploader.shutdownNow();
    }

    @SuppressLint("MissingPermission") // checked before the service starts
    private void recordLoop() {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord record;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, CHUNK * 8));
            record.startRecording();
        } catch (RuntimeException e) {
            AppLog.add("Couldn't open the microphone: " + e.getMessage());
            return;
        }
        AppLog.add("Listening with Groq Whisper.");
        short[] buffer = new short[CHUNK];
        ArrayDeque<short[]> preRoll = new ArrayDeque<>(); // the moment just before you start talking
        ByteArrayOutputStream clip = null;
        double noise = 0.01;
        long clipStart = 0, lastLoud = 0, quietSince = System.currentTimeMillis();
        boolean warnedSilent = false;
        while (active) {
            int n = record.read(buffer, 0, CHUNK);
            if (n <= 0) continue;
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double v = buffer[i] / 32768.0;
                sum += v * v;
            }
            double level = Math.sqrt(sum / n);
            long now = System.currentTimeMillis();
            if (clip == null) noise = noise * 0.995 + level * 0.005;
            boolean loud = level > Math.max(0.02, noise * 3);
            if (level > 0.0005) quietSince = now;
            else if (!warnedSilent && now - quietSince > 20000) {
                warnedSilent = true; // pure digital silence for 20 s: the phone is muting us
                AppLog.add("The microphone is giving complete silence: the phone may be blocking it.");
            }
            short[] copy = Arrays.copyOf(buffer, n);
            if (clip == null) {
                preRoll.addLast(copy);
                if (preRoll.size() > 10) preRoll.removeFirst();
                if (loud) {
                    clip = new ByteArrayOutputStream();
                    for (short[] part : preRoll) writeSamples(clip, part);
                    preRoll.clear();
                    clipStart = now;
                    lastLoud = now;
                }
            } else {
                writeSamples(clip, copy);
                if (loud) lastLoud = now;
                if (now - lastLoud > 800 || now - clipStart > 7000) {
                    byte[] pcm = clip.toByteArray();
                    clip = null;
                    if (pcm.length > RATE * 2 * 0.45) {
                        uploader.execute(() -> transcribe(pcm));
                    }
                }
            }
        }
        record.stop();
        record.release();
    }

    private static void writeSamples(ByteArrayOutputStream outStream, short[] samples) {
        for (short s : samples) {
            outStream.write(s & 0xFF);
            outStream.write((s >> 8) & 0xFF);
        }
    }

    private static byte[] wav(byte[] pcm) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(pcm.length + 44);
        writeAscii(b, "RIFF");
        writeInt(b, 36 + pcm.length);
        writeAscii(b, "WAVEfmt ");
        writeInt(b, 16);
        writeShort(b, 1);
        writeShort(b, 1);
        writeInt(b, RATE);
        writeInt(b, RATE * 2);
        writeShort(b, 2);
        writeShort(b, 16);
        writeAscii(b, "data");
        writeInt(b, pcm.length);
        b.write(pcm, 0, pcm.length);
        return b.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream b, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        b.write(bytes, 0, bytes.length);
    }

    private static void writeInt(ByteArrayOutputStream b, int v) {
        for (int i = 0; i < 4; i++) b.write((v >> (8 * i)) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream b, int v) {
        b.write(v & 0xFF);
        b.write((v >> 8) & 0xFF);
    }

    private void transcribe(byte[] pcm) {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection http = null;
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            String[][] fields = {{"model", "whisper-large-v3-turbo"}, {"language", "en"}, {"response_format", "json"}, {"prompt", VOCABULARY}};
            for (String[] f : fields) {
                writeAscii(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f[0] + "\"\r\n\r\n");
                byte[] value = f[1].getBytes(StandardCharsets.UTF_8);
                body.write(value, 0, value.length);
                writeAscii(body, "\r\n");
            }
            writeAscii(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\nContent-Type: audio/wav\r\n\r\n");
            byte[] audio = wav(pcm);
            body.write(audio, 0, audio.length);
            writeAscii(body, "\r\n--" + boundary + "--\r\n");

            http = (HttpURLConnection) new URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection();
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setConnectTimeout(15000);
            http.setReadTimeout(30000);
            http.setRequestProperty("Authorization", "Bearer " + key);
            http.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            http.setRequestProperty("User-Agent", "JerryVoice/1.0 (Android)");
            try (OutputStream os = http.getOutputStream()) {
                body.writeTo(os);
            }
            int code = http.getResponseCode();
            String reply = readAll(code < 400 ? http.getInputStream() : http.getErrorStream());
            if (code == 401) {
                AppLog.add("Groq says the key is wrong. Copy it again from console.groq.com/keys.");
                return;
            }
            if (code == 429) {
                AppLog.add("Groq says slow down (free limit for now). Try again in a minute.");
                return;
            }
            if (code >= 400) {
                AppLog.add("Groq error " + code + ": " + reply.substring(0, Math.min(160, reply.length())));
                return;
            }
            String text = new JSONObject(reply).optString("text", "").trim();
            if (!text.isEmpty() && !HALLUCINATIONS.contains(text.toLowerCase(Locale.US))) out.heard(text);
        } catch (Exception e) {
            AppLog.add("Groq didn't answer (" + e.getClass().getSimpleName() + "). Is the internet on?");
        } finally {
            if (http != null) http.disconnect();
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) b.write(chunk, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}
