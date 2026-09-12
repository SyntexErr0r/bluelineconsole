package net.nhiroki.bluelineconsole.applock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import net.nhiroki.bluelineconsole.commands.logs.AppLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AppLockManager {
    private static final String PREF_FILE = "blueline_app_lock_prefs";
    private static final String KEY_MASTER_ENABLED = "pref_app_lock_master_enabled";
    private static final String KEY_LOCKED_APPS_JSON = "pref_locked_apps_json";

    public static class LockedAppConfig {
        public final String packageName;
        public String pin;
        public String pattern; // Sequence of 1-9 digits (e.g. "9428" or "835")
        public boolean enabled;

        public LockedAppConfig(String packageName, String pin, String pattern, boolean enabled) {
            this.packageName = packageName;
            this.pin = pin != null ? pin : "";
            this.pattern = pattern != null ? pattern : "";
            this.enabled = enabled;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("pkg", packageName);
                obj.put("pin", pin);
                obj.put("pattern", pattern);
                obj.put("enabled", enabled);
            } catch (Exception ignored) {}
            return obj;
        }

        public static LockedAppConfig fromJson(JSONObject obj) {
            if (obj == null) return null;
            String pkg = obj.optString("pkg", "");
            String pin = obj.optString("pin", "");
            String pattern = obj.optString("pattern", "");
            boolean enabled = obj.optBoolean("enabled", true);
            return new LockedAppConfig(pkg, pin, pattern, enabled);
        }
    }

    private static AppLockManager sInstance;

    private boolean mInitialized = false;
    private boolean mMasterEnabled = true;
    private final Map<String, LockedAppConfig> mLockedApps = new HashMap<>();

    // In-memory runtime session states
    private final Set<String> mUnlockedSessions = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Long> mConsoleAuthorizedUntil = new ConcurrentHashMap<>();
    private volatile String mLastForegroundPackage = null;
    private volatile long mLastLockTriggerTime = 0;

    private AppLockManager() {
        initDefaultLocks(null);
    }

    public static synchronized AppLockManager getInstance() {
        if (sInstance == null) {
            sInstance = new AppLockManager();
        }
        return sInstance;
    }

    public synchronized void ensureInitialized(Context context) {
        if (mInitialized || context == null) return;
        mInitialized = true;

        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        mMasterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true);

        String jsonStr = prefs.getString(KEY_LOCKED_APPS_JSON, null);
        if (jsonStr != null && !jsonStr.trim().isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    LockedAppConfig cfg = LockedAppConfig.fromJson(arr.getJSONObject(i));
                    if (cfg != null && !cfg.packageName.isEmpty()) {
                        mLockedApps.put(cfg.packageName.toLowerCase(), cfg);
                    }
                }
            } catch (Exception e) {
                AppLogger.e("APPLOCK", "Error loading locked apps JSON", e);
            }
        }

        // Initialize default rules requested: WhatsApp (PIN 9428, Pattern 9428), Telegram (PIN 8353, Pattern 835)
        if (mLockedApps.isEmpty()) {
            initDefaultLocks(prefs);
        }
    }

    private void initDefaultLocks(SharedPreferences prefs) {
        // WhatsApp defaults: PIN 9428, Pattern 9428 (dots 9 -> 4 -> 2 -> 8)
        mLockedApps.put("com.whatsapp", new LockedAppConfig("com.whatsapp", "9428", "9428", true));
        mLockedApps.put("com.whatsapp.w4b", new LockedAppConfig("com.whatsapp.w4b", "9428", "9428", true));

        // Telegram defaults: PIN 8353, Pattern 835 (dots 8 -> 3 -> 5)
        mLockedApps.put("org.telegram.messenger", new LockedAppConfig("org.telegram.messenger", "8353", "835", true));
        mLockedApps.put("org.telegram.messenger.web", new LockedAppConfig("org.telegram.messenger.web", "8353", "835", true));
        mLockedApps.put("org.telegram.messenger.beta", new LockedAppConfig("org.telegram.messenger.beta", "8353", "835", true));
        mLockedApps.put("nekox.messenger", new LockedAppConfig("nekox.messenger", "8353", "835", true));
        mLockedApps.put("org.thunderdog.challegram", new LockedAppConfig("org.thunderdog.challegram", "8353", "835", true));

        saveLockedApps(prefs);
    }

    private synchronized void saveLockedApps(SharedPreferences prefs) {
        if (prefs == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (LockedAppConfig cfg : mLockedApps.values()) {
                arr.put(cfg.toJson());
            }
            prefs.edit()
                    .putBoolean(KEY_MASTER_ENABLED, mMasterEnabled)
                    .putString(KEY_LOCKED_APPS_JSON, arr.toString())
                    .apply();
        } catch (Exception e) {
            AppLogger.e("APPLOCK", "Error saving locked apps", e);
        }
    }

    public synchronized boolean isMasterEnabled(Context context) {
        ensureInitialized(context);
        return mMasterEnabled;
    }

    public synchronized void setMasterEnabled(Context context, boolean enabled) {
        ensureInitialized(context);
        mMasterEnabled = enabled;
        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply();
    }

    public synchronized LockedAppConfig getLockedAppConfig(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return null;
        return mLockedApps.get(packageName.toLowerCase());
    }

    public synchronized boolean isPackageLocked(Context context, String packageName) {
        ensureInitialized(context);
        if (!mMasterEnabled || packageName == null) return false;
        LockedAppConfig cfg = mLockedApps.get(packageName.toLowerCase());
        return cfg != null && cfg.enabled;
    }

    public synchronized void setAppLock(Context context, String packageName, String pin, String pattern) {
        ensureInitialized(context);
        if (packageName == null || packageName.isEmpty()) return;
        String key = packageName.toLowerCase();
        LockedAppConfig existing = mLockedApps.get(key);
        String finalPin = (pin != null && !pin.isEmpty()) ? pin : (existing != null ? existing.pin : "0000");
        String finalPattern = (pattern != null && !pattern.isEmpty()) ? pattern : (existing != null ? existing.pattern : "");

        mLockedApps.put(key, new LockedAppConfig(key, finalPin, finalPattern, true));
        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        saveLockedApps(prefs);
        AppLogger.i("APPLOCK", "Set lock for " + key + " (pin=" + finalPin + ", pattern=" + finalPattern + ")");
    }

    public synchronized void removeAppLock(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return;
        String key = packageName.toLowerCase();
        mLockedApps.remove(key);
        SharedPreferences prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        saveLockedApps(prefs);
        AppLogger.i("APPLOCK", "Removed lock for " + key);
    }

    public synchronized Map<String, LockedAppConfig> getAllLockedApps(Context context) {
        ensureInitialized(context);
        return Collections.unmodifiableMap(new HashMap<>(mLockedApps));
    }

    /**
     * Called when an app is launched through BlueLine Console (search result, command, or AI agent).
     * Grants a 25-second authorization window so the lock screen doesn't block the user.
     */
    public void notifyAppLaunchedFromConsole(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        mConsoleAuthorizedUntil.put(packageName.toLowerCase(), System.currentTimeMillis() + 25000);
        AppLogger.i("APPLOCK", "Authorized launch from BlueLine Console for: " + packageName);
    }

    /**
     * Grants an unlocked session for the current active app run.
     */
    public void unlockAppSession(String packageName) {
        if (packageName == null) return;
        mUnlockedSessions.add(packageName.toLowerCase());
        AppLogger.i("APPLOCK", "App unlocked for session: " + packageName);
    }

    public boolean isAppUnlockedForSession(String packageName) {
        if (packageName == null) return false;
        return mUnlockedSessions.contains(packageName.toLowerCase());
    }

    /**
     * Inspects active window package state from Accessibility Service.
     */
    public void onWindowStateChanged(Context context, String currentPackage) {
        if (currentPackage == null || context == null) return;
        ensureInitialized(context);

        if (!mMasterEnabled) return;

        String pkg = currentPackage.toLowerCase();
        String myPkg = context.getPackageName().toLowerCase();

        // Don't intercept BlueLine Console itself, AppLockActivity, or Android System UI
        if (pkg.equals(myPkg) || pkg.equals("net.nhiroki.bluelineconsole") || pkg.equals("net.nhiroki.bluelineconsole.beta")) {
            return;
        }

        // Detect app switching to re-lock previous apps
        if (mLastForegroundPackage != null && !mLastForegroundPackage.equals(pkg)) {
            if (mUnlockedSessions.contains(mLastForegroundPackage)) {
                mUnlockedSessions.remove(mLastForegroundPackage);
                AppLogger.i("APPLOCK", "Switched from " + mLastForegroundPackage + " to " + pkg + "; session re-locked.");
            }
        }
        mLastForegroundPackage = pkg;

        LockedAppConfig config = mLockedApps.get(pkg);
        if (config == null || !config.enabled) {
            return;
        }

        // Check if user launched directly through BlueLine Console
        Long authTime = mConsoleAuthorizedUntil.get(pkg);
        if (authTime != null) {
            if (System.currentTimeMillis() <= authTime) {
                mConsoleAuthorizedUntil.remove(pkg);
                mUnlockedSessions.add(pkg);
                AppLogger.i("APPLOCK", "Bypassed lock for console-authorized launch: " + pkg);
                return;
            } else {
                mConsoleAuthorizedUntil.remove(pkg);
            }
        }

        // Check if already unlocked in active session
        if (mUnlockedSessions.contains(pkg)) {
            return;
        }

        // Throttle lock activity launches to once per 1200ms
        long now = System.currentTimeMillis();
        if (now - mLastLockTriggerTime < 1200) {
            return;
        }
        mLastLockTriggerTime = now;

        AppLogger.i("APPLOCK", "Locking access to " + pkg + ". Launching AppLockActivity.");

        Intent lockIntent = new Intent(context, AppLockActivity.class);
        lockIntent.putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, pkg);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_NO_ANIMATION |
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                            Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(lockIntent);
    }
}
