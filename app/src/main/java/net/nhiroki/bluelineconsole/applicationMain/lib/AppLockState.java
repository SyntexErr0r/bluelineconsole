package net.nhiroki.bluelineconsole.applicationMain.lib;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import androidx.biometric.BiometricManager;
import androidx.preference.PreferenceManager;

public class AppLockState {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 30000L;

    private static boolean sIsLocked = true;
    private static long sLastExitTime = 0;
    private static int sFailedAttempts = 0;
    private static long sLockoutUntil = 0;
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

    public static boolean hasActiveConsoleLockKey(Context context) {
        if (context == null) return false;
        String pin = PreferenceManager.getDefaultSharedPreferences(context).getString("pref_app_lock_pin", "").trim();
        if (!pin.isEmpty()) {
            return true;
        }
        net.nhiroki.bluelineconsole.applock.AppLockManager mgr = net.nhiroki.bluelineconsole.applock.AppLockManager.getInstance();
        if (mgr.isTimeLockEnabled(context)) {
            return true;
        }
        String masterPin = mgr.getMasterPin(context);
        return !masterPin.equals(net.nhiroki.bluelineconsole.applock.AppLockManager.DEFAULT_MASTER_PIN);
    }

    public static boolean isLocked(Context context) {
        ensureReceiverRegistered(context);

        boolean enabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_app_lock_enabled", false);
        if (!enabled || !hasActiveConsoleLockKey(context)) {
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
        if (!enabled || !hasActiveConsoleLockKey(context)) {
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
        if (!enabled || !hasActiveConsoleLockKey(context)) {
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
        if (!locked) {
            resetFailedAttempts();
        }
    }

    public static void setLastExitTime(long time) {
        sLastExitTime = time;
    }

    public static boolean isBiometricSupported(Context context) {
        if (context == null) return false;
        try {
            BiometricManager bm = BiometricManager.from(context);
            int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            return canAuth == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isLockedOut() {
        if (sLockoutUntil > 0) {
            if (System.currentTimeMillis() < sLockoutUntil) {
                return true;
            } else {
                sLockoutUntil = 0;
                sFailedAttempts = 0;
            }
        }
        return false;
    }

    public static long getRemainingLockoutSeconds() {
        if (!isLockedOut()) {
            return 0;
        }
        return Math.max(1, (sLockoutUntil - System.currentTimeMillis() + 999) / 1000);
    }

    public static int getFailedAttempts() {
        return sFailedAttempts;
    }

    public static int getMaxFailedAttempts() {
        return MAX_FAILED_ATTEMPTS;
    }

    public static void recordFailedAttempt() {
        sFailedAttempts++;
        if (sFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            sLockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
        }
    }

    public static void resetFailedAttempts() {
        sFailedAttempts = 0;
        sLockoutUntil = 0;
    }
}
