package net.nhiroki.bluelineconsole.applicationMain.lib;

import android.content.Context;
import android.os.PowerManager;
import androidx.preference.PreferenceManager;

public class AppLockState {
    private static boolean sIsLocked = true;
    private static long sLastExitTime = 0;

    public static boolean isLocked(Context context) {
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_enabled", false);
        String pin = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_pin", "");
        if (!enabled || pin.isEmpty()) {
            return false;
        }

        if (sIsLocked) {
            return true;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean screenOff = pm != null && !pm.isInteractive();

        boolean screenOffOnly = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_screen_off_only", false);
        if (screenOffOnly) {
            if (screenOff) {
                sIsLocked = true;
            }
        } else {
            String delayStr = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_delay", "0");
            long delayMs = 0;
            try {
                delayMs = Long.parseLong(delayStr) * 1000L;
            } catch (NumberFormatException ignored) {}

            if (screenOff || System.currentTimeMillis() - sLastExitTime > delayMs) {
                sIsLocked = true;
            }
        }

        return sIsLocked;
    }

    public static void setLocked(boolean locked) {
        sIsLocked = locked;
        if (locked) {
            sLastExitTime = 0;
        }
    }

    public static void setLastExitTime(long time) {
        sLastExitTime = time;
    }
}
