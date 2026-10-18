package com.jerrythedawg.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Listens while you play. Android only lets an app use the microphone in the background from a
 * foreground service of type "microphone" that was started while the app was on screen, which is
 * what this is (with its "Jerry is listening" notification).
 */
public class VoiceService extends Service implements Heard, MinecraftLink.Listener {
    static final String ACTION_STOP = "com.jerrythedawg.voice.STOP";
    private static final String CHANNEL = "listening";
    private static final int PORT = 3000;

    static volatile VoiceService instance;

    private MinecraftLink link;
    private PhoneListener phone;
    private GroqListener groq;
    private PowerManager.WakeLock wakeLock;
    private volatile long talkUntil;

    static boolean isRunning() {
        return instance != null;
    }

    static boolean minecraftConnected() {
        VoiceService s = instance;
        return s != null && s.link != null && s.link.connected();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (instance != null) return START_NOT_STICKY; // already listening
        goForeground();
        instance = this;

        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JerryVoice:listening");
        wakeLock.acquire(6 * 60 * 60 * 1000L);

        link = new MinecraftLink(PORT, this);
        link.start();
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String key = prefs.getString(MainActivity.PREF_KEY, "").trim();
        if ("groq".equals(prefs.getString(MainActivity.PREF_ENGINE, "phone")) && !key.isEmpty()) {
            groq = new GroqListener(key, this);
            groq.start();
        } else {
            phone = new PhoneListener(this, this);
            phone.start();
        }
        // Not sticky: if Android stops us, it can't restart the microphone from the background anyway.
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Listening", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, VoiceService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_paw)
                .setContentTitle("Jerry is listening")
                .setContentText("Talk to Jerry in Minecraft. Tap to open.")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public void onTalkPressed() {
        talkUntil = System.currentTimeMillis() + 8000;
    }

    /** Something was said: pass it to the add-on (like the Termux helper does). */
    @Override
    public void heard(String raw) {
        String text = raw.replaceAll("[^A-Za-z0-9 ,.'?!-]+", " ").replaceAll("\\s+", " ").trim();
        if (text.length() > 150) text = text.substring(0, 150);
        if (text.isEmpty()) return;
        if (!link.connected()) {
            AppLog.add("Heard \"" + text + "\", but Minecraft isn't connected (/connect 127.0.0.1:" + PORT + ").");
            return;
        }
        AppLog.add("Heard: \"" + text + "\"");
        boolean pressedTalk = System.currentTimeMillis() < talkUntil;
        talkUntil = 0;
        String event = pressedTalk ? "jf:voice" : "jf:voice_heard"; // voice_heard: only his name or short commands count
        final String line = "scriptevent " + event + " " + text;
        new Thread(() -> link.command(line), "send-voice").start();
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (phone != null) phone.stop();
        if (groq != null) groq.stop();
        if (link != null) link.stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        AppLog.add("Stopped listening.");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
