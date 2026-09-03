package net.nhiroki.bluelineconsole.applicationMain.lib;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import androidx.preference.PreferenceManager;

public class AppLockState {
    private static boolean sIsLocked = true;
    private static long sLastExitTime = 0;
    private static BroadcastReceiver sScreenOffReceiver = null;

    private static synchronized void ensureReceiverRegistered(Context context) {
        if (sScreenOffReceiver == null && context != null) {
            sScreenOffReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        sIsLocked = true;
                    }
                }
            };
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            Context appContext = context.getApplicationContext();
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(sScreenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                appContext.registerReceiver(sScreenOffReceiver, filter);
            }
        }
    }

    public static boolean isLocked(Context context) {
        ensureReceiverRegistered(context);

        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_enabled", false);
        String pin = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_pin", "").trim();
        if (!enabled || pin.isEmpty()) {
            return false;
        }

        if (sIsLocked) {
            return true;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            sIsLocked = true;
            return true;
        }

        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            sIsLocked = true;
            return true;
        }

        return sIsLocked;
    }

    public static void onAppExit(Context context) {
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_enabled", false);
        String pin = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_pin", "").trim();
        if (!enabled || pin.isEmpty()) {
            return;
        }

        boolean screenOffOnly = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_screen_off_only", false);
        if (screenOffOnly) {
            sLastExitTime = 0;
            return;
        }

        String delayStr = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_delay", "0").trim();
        long delayMs = 0;
        try {
            delayMs = Math.max(0, Long.parseLong(delayStr) * 1000L);
        } catch (NumberFormatException ignored) {}

        if (delayMs <= 0) {
            sIsLocked = true;
            sLastExitTime = 0;
        } else {
            sLastExitTime = System.currentTimeMillis();
        }
    }

    public static void onAppResume(Context context) {
        ensureReceiverRegistered(context);

        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_enabled", false);
        String pin = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_pin", "").trim();
        if (!enabled || pin.isEmpty()) {
            sLastExitTime = 0;
            return;
        }

        if (sIsLocked) {
            sLastExitTime = 0;
            return;
        }

        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            sIsLocked = true;
            sLastExitTime = 0;
            return;
        }

        boolean screenOffOnly = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_screen_off_only", false);
        if (screenOffOnly) {
            sLastExitTime = 0;
            return;
        }

        String delayStr = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_delay", "0").trim();
        long delayMs = 0;
        try {
            delayMs = Math.max(0, Long.parseLong(delayStr) * 1000L);
        } catch (NumberFormatException ignored) {}

        if (sLastExitTime > 0 && (System.currentTimeMillis() - sLastExitTime > delayMs)) {
            sIsLocked = true;
        }
        sLastExitTime = 0;
    }

    public static void setLocked(boolean locked) {
        sIsLocked = locked;
        sLastExitTime = 0;
    }

    public static void setLastExitTime(long time) {
        sLastExitTime = time;
    }
}
