package com.jerrythedawg.verity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * The phone's own speech recognition, started again and again so it's always listening.
 * Free, no key. SpeechRecognizer has to be used from the main thread.
 */
final class PhoneListener implements RecognitionListener {
    private final Context context;
    private final Heard out;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean active;
    private boolean paused; // while Verity talks, so she doesn't hear herself
    private boolean onDevice;
    private boolean triedOnDevice;
    private int errorsInARow;
    private long lastErrorLog;

    PhoneListener(Context context, Heard out) {
        this.context = context.getApplicationContext();
        this.out = out;
    }

    void start() {
        main.post(() -> {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                AppLog.add("This phone has no speech recognition service. Switch to the Groq engine.");
                return;
            }
            active = true;
            create();
            listen();
        });
    }

    void stop() {
        main.post(() -> {
            active = false;
            main.removeCallbacksAndMessages(null);
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }
        });
    }

    private void create() {
        if (recognizer != null) recognizer.destroy();
        // On-device recognition (Android 13+) is faster and doesn't beep; fall back to the normal one.
        onDevice = false;
        if (!triedOnDevice && Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            try {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
                onDevice = true;
            } catch (RuntimeException e) {
                recognizer = null;
            }
        }
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);
        AppLog.add(onDevice ? "Listening with on-device speech recognition." : "Listening with the phone's speech recognition.");
    }

    /** Stop listening while Verity's voice plays (the mic would pick it up), then carry on. */
    void setPaused(boolean pause) {
        main.post(() -> {
            if (paused == pause) return;
            paused = pause;
            if (recognizer == null) return;
            if (pause) {
                main.removeCallbacksAndMessages(null);
                recognizer.cancel();
            } else {
                again(300);
            }
        });
    }

    private void listen() {
        if (!active || paused || recognizer == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        try {
            recognizer.startListening(intent);
        } catch (RuntimeException e) {
            again(1000);
        }
    }

    private void again(long delayMs) {
        main.postDelayed(this::listen, delayMs);
    }

    @Override
    public void onResults(Bundle results) {
        errorsInARow = 0;
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (!paused && texts != null && !texts.isEmpty() && !texts.get(0).trim().isEmpty()) out.heard(texts.get(0));
        again(100);
    }

    @Override
    public void onError(int error) {
        errorsInARow++;
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                again(100); // nobody talked: just listen again
                return;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                recognizer.cancel();
                again(600);
                return;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                AppLog.add("No microphone permission. Open the app and allow the microphone.");
                return;
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
            case SpeechRecognizer.ERROR_CLIENT:
                if (onDevice) triedOnDevice = true; // on-device didn't work out: use the normal one
                main.postDelayed(() -> {
                    if (!active) return;
                    create();
                    listen();
                }, 500);
                return;
            case SpeechRecognizer.ERROR_AUDIO:
                logError("The phone blocked the microphone (audio error). Try the Groq engine.");
                again(1500);
                return;
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                logError("Speech recognition says slow down. Waiting a bit.");
                again(5000);
                return;
            default: // network problems and the like
                logError("Speech recognition error " + error + " (network?). Trying again.");
                again(Math.min(10000, 1000L * errorsInARow));
        }
    }

    private void logError(String message) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog > 30000) {
            lastErrorLog = now;
            AppLog.add(message);
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { }
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { }
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }
}
