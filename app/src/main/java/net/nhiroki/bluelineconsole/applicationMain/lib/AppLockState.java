package net.nhiroki.bluelineconsole.applicationMain.lib;

import android.content.Context;
import androidx.preference.PreferenceManager;

public class AppLockState {
    private static boolean sIsLocked = true;

    public static boolean isLocked(Context context) {
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_enabled", false);
        String pin = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_pin", "");
        return enabled && !pin.isEmpty() && sIsLocked;
    }

    public static void setLocked(boolean locked) {
        sIsLocked = locked;
    }
}
