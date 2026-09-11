package net.nhiroki.bluelineconsole.agent;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import androidx.preference.PreferenceManager;

import java.util.Locale;

public class AgentTTS {
    private static TextToSpeech sTts = null;
    private static boolean sInitialized = false;

    public static void init(final Context context) {
        if (sTts == null) {
            sTts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && sTts != null) {
                    sTts.setLanguage(Locale.getDefault());
                    sInitialized = true;
                }
            });
        }
    }

    public static void speak(Context context, String text) {
        if (text == null || text.trim().isEmpty()) return;
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("pref_agent_voice_tts_enabled", true);
        if (!enabled) return;

        if (sTts == null) {
            init(context);
        }
        if (sTts != null && sInitialized) {
            if (Build.VERSION.SDK_INT >= 21) {
                sTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent_tts");
            } else {
                sTts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    public static void shutdown() {
        if (sTts != null) {
            sTts.stop();
            sTts.shutdown();
            sTts = null;
            sInitialized = false;
        }
    }
}
