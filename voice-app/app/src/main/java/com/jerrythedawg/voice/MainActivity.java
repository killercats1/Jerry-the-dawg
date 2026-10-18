package com.jerrythedawg.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Start/stop, pick how speech is turned into text, and see what's happening. */
public class MainActivity extends Activity {
    static final String PREFS = "jerry_voice";
    static final String PREF_ENGINE = "engine";
    static final String PREF_KEY = "groq_key";
    private static final int ASK_PERMISSIONS = 1;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private TextView status;
    private TextView log;
    private ScrollView logScroll;
    private Button startStop;
    private EditText keyBox;
    private RadioButton groqChoice;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            update();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int pad = dp(16);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(Color.rgb(30, 42, 34));

        TextView title = text("🐶 Jerry Voice", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title);
        page.addView(text("1. Tap Start (allow the microphone).\n"
                + "2. Open Minecraft, join your world and type:  /connect 127.0.0.1:3000\n"
                + "3. Just talk: \"Jerry, roll over!\"  (or press Talk to Your Dog, then say anything)\n"
                + "Leave this app running; the notification shows it's listening.", 15, Color.rgb(201, 212, 195)));

        RadioGroup engines = new RadioGroup(this);
        RadioButton phoneChoice = radio("Phone's speech recognition (free)");
        groqChoice = radio("Groq Whisper (free key from console.groq.com)");
        engines.addView(phoneChoice);
        engines.addView(groqChoice);
        boolean useGroq = "groq".equals(prefs.getString(PREF_ENGINE, "phone"));
        (useGroq ? groqChoice : phoneChoice).setChecked(true);
        page.addView(engines);

        keyBox = new EditText(this);
        keyBox.setHint("Paste your Groq key here");
        keyBox.setText(prefs.getString(PREF_KEY, ""));
        keyBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        keyBox.setTextColor(Color.WHITE);
        keyBox.setHintTextColor(Color.GRAY);
        keyBox.setVisibility(useGroq ? View.VISIBLE : View.GONE);
        page.addView(keyBox);
        engines.setOnCheckedChangeListener((group, id) -> keyBox.setVisibility(groqChoice.isChecked() ? View.VISIBLE : View.GONE));

        startStop = new Button(this);
        startStop.setTextSize(20);
        startStop.setOnClickListener(v -> {
            if (VoiceService.isRunning()) {
                startService(new Intent(this, VoiceService.class).setAction(VoiceService.ACTION_STOP));
            } else {
                saveSettings();
                askThenStart();
            }
            ui.postDelayed(this::update, 300);
        });
        page.addView(startStop);

        Button battery = new Button(this);
        battery.setText("Don't let the phone pause Jerry Voice (battery)");
        battery.setOnClickListener(v -> askBattery());
        page.addView(battery);

        status = text("", 16, Color.WHITE);
        status.setPadding(0, dp(10), 0, dp(6));
        page.addView(status);

        log = text("", 12, Color.rgb(201, 212, 195));
        log.setTypeface(Typeface.MONOSPACE);
        logScroll = new ScrollView(this);
        logScroll.addView(log);
        page.addView(logScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppLog.listener = () -> ui.post(this::update);
        ui.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppLog.listener = null;
        ui.removeCallbacks(refresh);
    }

    private void update() {
        boolean running = VoiceService.isRunning();
        startStop.setText(running ? "Stop listening" : "Start listening");
        status.setText(!running ? "Not listening."
                : VoiceService.minecraftConnected() ? "Listening, Minecraft connected. Talk to Jerry!"
                : "Listening. Now open Minecraft and type /connect 127.0.0.1:3000");
        log.setText(AppLog.text());
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void saveSettings() {
        prefs.edit()
                .putString(PREF_ENGINE, groqChoice.isChecked() ? "groq" : "phone")
                .putString(PREF_KEY, keyBox.getText().toString().trim())
                .apply();
        if (groqChoice.isChecked() && keyBox.getText().toString().trim().isEmpty()) {
            AppLog.add("No Groq key yet, so using the phone's speech recognition.");
        }
    }

    private void askThenStart() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (needed.isEmpty()) {
            startListening();
        } else {
            requestPermissions(needed.toArray(new String[0]), ASK_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != ASK_PERMISSIONS) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            AppLog.add("Jerry Voice needs the microphone to hear you. Allow it and tap Start again.");
        }
    }

    private void startListening() {
        // Started while this screen is showing, so Android lets it keep the microphone in the background.
        startForegroundService(new Intent(this, VoiceService.class));
    }

    private void askBattery() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power.isIgnoringBatteryOptimizations(getPackageName())) {
            AppLog.add("Battery: already unrestricted.");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private RadioButton radio(String label) {
        RadioButton r = new RadioButton(this);
        r.setText(label);
        r.setTextColor(Color.WHITE);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
