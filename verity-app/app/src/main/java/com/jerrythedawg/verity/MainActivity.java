package com.jerrythedawg.verity;

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
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Keys, settings, start/stop, and what's happening. */
public class MainActivity extends Activity {
    static final String PREFS = "verity_voice";
    static final String PREF_GROQ = "groq_key";
    static final String PREF_FISH = "fish_key";
    static final String PREF_MODEL = "fish_model";
    static final String PREF_ENGINE = "engine";
    static final String PREF_WAKE = "wake_word";
    private static final int ASK_PERMISSIONS = 1;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private EditText groqBox, fishBox, modelBox;
    private RadioButton groqEngine;
    private CheckBox wakeWord;
    private TextView status, log;
    private ScrollView logScroll;
    private Button startStop;

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

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(pad, pad, pad, pad);

        TextView title = text("Verity Voice", 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(title);
        form.addView(text("For PnTMC's \"Verity - Bedrock Edition\" add-on (Beta APIs + cheats on). "
                + "Replaces the Termux script: listens while you play, Groq answers, FishAudio speaks.\n"
                + "1. Paste your keys and tap Start.\n"
                + "2. In Minecraft (WebSockets on, encryption off) type:  /connect 127.0.0.1:3000\n"
                + "3. Talk to Verity, or type in chat.", 14, Color.rgb(205, 200, 220)));

        groqBox = keyBox("Groq API key (console.groq.com/keys)", PREF_GROQ);
        fishBox = keyBox("FishAudio API key (fish.audio/app/api-keys)", PREF_FISH);
        modelBox = keyBox("FishAudio model ID (Verity's voice)", PREF_MODEL);
        form.addView(groqBox);
        form.addView(fishBox);
        form.addView(modelBox);

        RadioGroup engines = new RadioGroup(this);
        RadioButton phoneEngine = radio("Hear me with the phone's speech recognition");
        groqEngine = radio("Hear me with Groq Whisper (uses the Groq key)");
        engines.addView(phoneEngine);
        engines.addView(groqEngine);
        ("groq".equals(prefs.getString(PREF_ENGINE, "phone")) ? groqEngine : phoneEngine).setChecked(true);
        form.addView(engines);

        wakeWord = new CheckBox(this);
        wakeWord.setText("Only answer when I say \"Verity\" (or after !talk)");
        wakeWord.setTextColor(Color.WHITE);
        wakeWord.setChecked(prefs.getBoolean(PREF_WAKE, false));
        form.addView(wakeWord);

        startStop = new Button(this);
        startStop.setTextSize(20);
        startStop.setOnClickListener(v -> {
            if (VerityService.isRunning()) {
                startService(new Intent(this, VerityService.class).setAction(VerityService.ACTION_STOP));
            } else if (save()) {
                askThenStart();
            }
            ui.postDelayed(this::update, 300);
        });
        form.addView(startStop);

        Button battery = new Button(this);
        battery.setText("Don't let the phone pause Verity Voice (battery)");
        battery.setOnClickListener(v -> askBattery());
        form.addView(battery);

        status = text("", 16, Color.WHITE);
        form.addView(status);
        log = text("", 12, Color.rgb(205, 200, 220));
        log.setTypeface(Typeface.MONOSPACE);
        form.addView(log);

        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(Color.rgb(26, 22, 36));
        logScroll.addView(form);
        setContentView(logScroll);
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
        boolean running = VerityService.isRunning();
        startStop.setText(running ? "Stop" : "Start");
        status.setText(!running ? "Not running."
                : VerityService.minecraftConnected() ? "Running, Minecraft connected. Talk to Verity!"
                : "Running. Now open Minecraft and type /connect 127.0.0.1:3000");
        log.setText(AppLog.text());
    }

    private boolean save() {
        String groq = groqBox.getText().toString().trim();
        prefs.edit()
                .putString(PREF_GROQ, groq)
                .putString(PREF_FISH, fishBox.getText().toString().trim())
                .putString(PREF_MODEL, modelBox.getText().toString().trim())
                .putString(PREF_ENGINE, groqEngine.isChecked() ? "groq" : "phone")
                .putBoolean(PREF_WAKE, wakeWord.isChecked())
                .apply();
        if (groq.isEmpty()) {
            AppLog.add("Paste your Groq key first: Verity needs it to think.");
            return false;
        }
        return true;
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
            startForegroundService(new Intent(this, VerityService.class));
        } else {
            requestPermissions(needed.toArray(new String[0]), ASK_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != ASK_PERMISSIONS) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Started while this screen is showing, so Android lets it keep the microphone in the background.
            startForegroundService(new Intent(this, VerityService.class));
        } else {
            AppLog.add("Verity Voice needs the microphone to hear you. Allow it and tap Start again.");
        }
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

    private EditText keyBox(String hint, String pref) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(prefs.getString(pref, ""));
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        return e;
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
        return r;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
