package net.nhiroki.bluelineconsole.agent;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceInputManager {
    public static final int PERMISSION_REQUEST_RECORD_AUDIO = 2001;

    public interface VoiceCallback {
        void onReady();
        void onResult(String text);
        void onError(String errorMsg);
        void onEnd();
    }

    private final Activity mActivity;
    private SpeechRecognizer mSpeechRecognizer = null;
    private boolean mIsListening = false;

    public VoiceInputManager(Activity activity) {
        this.mActivity = activity;
    }

    public boolean hasRecordAudioPermission() {
        return ContextCompat.checkSelfPermission(mActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_RECORD_AUDIO);
    }

    public boolean isListening() {
        return mIsListening;
    }

    public void startListening(final VoiceCallback callback) {
        if (!hasRecordAudioPermission()) {
            requestRecordAudioPermission();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(mActivity)) {
            Toast.makeText(mActivity, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        stopListening();

        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mActivity);
        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                mIsListening = true;
                if (callback != null) callback.onReady();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                mIsListening = false;
                if (callback != null) callback.onEnd();
            }

            @Override
            public void onError(int error) {
                mIsListening = false;
                String msg = "Voice recognition error (" + error + ")";
                if (error == SpeechRecognizer.ERROR_NO_MATCH) msg = "No speech detected";
                else if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) msg = "Network error";
                else if (error == SpeechRecognizer.ERROR_AUDIO) msg = "Audio recording error";
                if (callback != null) {
                    callback.onError(msg);
                    callback.onEnd();
                }
            }

            @Override
            public void onResults(Bundle results) {
                mIsListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    if (callback != null) callback.onResult(recognizedText);
                }
                if (callback != null) callback.onEnd();
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak command to BlueLine Agent...");
        mSpeechRecognizer.startListening(intent);
    }

    public void stopListening() {
        if (mSpeechRecognizer != null) {
            try {
                mSpeechRecognizer.stopListening();
                mSpeechRecognizer.destroy();
            } catch (Exception ignored) {}
            mSpeechRecognizer = null;
        }
        mIsListening = false;
    }
}
