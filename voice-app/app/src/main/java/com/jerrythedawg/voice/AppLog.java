package com.jerrythedawg.voice;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** What the app has been doing, shown on its screen (newest at the bottom). */
final class AppLog {
    interface Listener {
        void onLog();
    }

    private static final int MAX_LINES = 60;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);
    static volatile Listener listener;

    private AppLog() {
    }

    static void add(String message) {
        synchronized (LINES) {
            LINES.addLast("[" + TIME.format(new Date()) + "] " + message);
            while (LINES.size() > MAX_LINES) {
                LINES.removeFirst();
            }
        }
        android.util.Log.i("JerryVoice", message);
        Listener l = listener;
        if (l != null) {
            l.onLog();
        }
    }

    static String text() {
        synchronized (LINES) {
            return String.join("\n", LINES);
        }
    }
}
