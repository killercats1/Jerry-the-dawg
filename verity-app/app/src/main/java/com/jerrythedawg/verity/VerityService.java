package com.jerrythedawg.verity;

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
 * Keeps Verity Voice running while you play: Android only lets an app use the microphone in the
 * background from a foreground service of type "microphone" started while the app is on screen.
 */
public class VerityService extends Service {
    static final String ACTION_STOP = "com.jerrythedawg.verity.STOP";
    private static final String CHANNEL = "listening";

    static volatile VerityService instance;

    private VerityBridge bridge;
    private PhoneListener phone;
    private GroqListener groq;
    private PowerManager.WakeLock wakeLock;

    static boolean isRunning() {
        return instance != null;
    }

    static boolean minecraftConnected() {
        VerityService s = instance;
        return s != null && s.bridge != null && s.bridge.connected();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (instance != null) return START_NOT_STICKY;
        goForeground();
        instance = this;

        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VerityVoice:listening");
        wakeLock.acquire(6 * 60 * 60 * 1000L);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String groqKey = prefs.getString(MainActivity.PREF_GROQ, "").trim();
        VerityBrain brain = new VerityBrain(this, groqKey, prefs.getString(MainActivity.PREF_FISH, "").trim(),
                prefs.getString(MainActivity.PREF_MODEL, "").trim());
        if (!brain.hasVoice()) AppLog.add("No FishAudio key/model: Verity will answer in chat only.");

        bridge = new VerityBridge(brain, prefs.getBoolean(MainActivity.PREF_WAKE, false));
        bridge.start();
        if ("groq".equals(prefs.getString(MainActivity.PREF_ENGINE, "phone"))) {
            groq = new GroqListener(groqKey, bridge);
            bridge.setMic(groq::setPaused);
            groq.start();
        } else {
            phone = new PhoneListener(this, bridge);
            bridge.setMic(phone::setPaused);
            phone.start();
        }
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Listening", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, VerityService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_paw)
                .setContentTitle("Verity is listening")
                .setContentText("Talk to Verity in Minecraft. Tap to open.")
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
    public void onDestroy() {
        instance = null;
        if (phone != null) phone.stop();
        if (groq != null) groq.stop();
        if (bridge != null) bridge.stop(); // also turns the bridge tag off so Verity's built-in chat works again
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        AppLog.add("Stopped listening.");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
