package net.nhiroki.bluelineconsole.applock;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import net.nhiroki.bluelineconsole.agent.BlueLineAgentService;
import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AppLockManager {
    private static final String PREF_FILE = "blueline_app_lock_prefs";
    private static final String KEY_MASTER_ENABLED = "pref_app_lock_master_enabled";
    private static final String KEY_LOCKED_APPS_JSON = "pref_locked_apps_json";

    public static final String KEY_LOCK_ALL_APPS = "pref_app_lock_all_apps";
    public static final String KEY_MASTER_PIN = "pref_app_lock_master_pin";
    public static final String KEY_MASTER_PATTERN = "pref_app_lock_master_pattern";
    public static final String KEY_EXEMPT_APPS = "pref_app_lock_exempt_apps";

    public static final String DEFAULT_MASTER_PIN = "0000";
    public static final String DEFAULT_MASTER_PATTERN = "1258";

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
    private boolean mLockAllApps = true;
    private String mMasterPin = DEFAULT_MASTER_PIN;
    private String mMasterPattern = DEFAULT_MASTER_PATTERN;
    private final Set<String> mExemptApps = new HashSet<>();
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
        mLockAllApps = prefs.getBoolean(KEY_LOCK_ALL_APPS, true);
        mMasterPin = prefs.getString(KEY_MASTER_PIN, DEFAULT_MASTER_PIN);
        mMasterPattern = prefs.getString(KEY_MASTER_PATTERN, DEFAULT_MASTER_PATTERN);

        Set<String> exemptSet = prefs.getStringSet(KEY_EXEMPT_APPS, null);
        if (exemptSet != null) {
            mExemptApps.clear();
            for (String s : exemptSet) {
                mExemptApps.add(s.toLowerCase());
            }
        }

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

        // Initialize default rules: WhatsApp (PIN 9428, Pattern 9428 -> geometrically 94258), Telegram (PIN 8353, Pattern 835)
        if (mLockedApps.isEmpty()) {
            initDefaultLocks(prefs);
        }
    }

    private void initDefaultLocks(SharedPreferences prefs) {
        // WhatsApp defaults: PIN 9428, Pattern 9428 (dots 2 to 8 dynamically cross 5)
        mLockedApps.put("com.whatsapp", new LockedAppConfig("com.whatsapp", "9428", "94258", true));
        mLockedApps.put("com.whatsapp.w4b", new LockedAppConfig("com.whatsapp.w4b", "9428", "94258", true));

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
                    .putBoolean(KEY_LOCK_ALL_APPS, mLockAllApps)
                    .putString(KEY_MASTER_PIN, mMasterPin)
                    .putString(KEY_MASTER_PATTERN, mMasterPattern)
                    .putStringSet(KEY_EXEMPT_APPS, new HashSet<>(mExemptApps))
                    .putString(KEY_LOCKED_APPS_JSON, arr.toString())
                    .apply();
        } catch (Exception e) {
            AppLogger.e("APPLOCK", "Error saving locked apps", e);
        }
    }

    /**
     * Algorithmic 3x3 pattern expansion:
     * Traverses the digit path on a 3x3 grid (dots 1-9) and automatically inserts
     * intermediate dots crossed along straight or diagonal lines that haven't yet been visited.
     * E.g. "9428" -> "94258" (because moving from 2 to 8 crosses 5).
     * "13" -> "123", "19" -> "159", "37" -> "357", "79" -> "789", etc.
     */
    public static String expandPatternWithIntermediateDots(String rawPattern) {
        if (rawPattern == null || rawPattern.length() <= 1) {
            return rawPattern == null ? "" : rawPattern;
        }

        // Validate that all characters are 1-9
        for (int i = 0; i < rawPattern.length(); i++) {
            char c = rawPattern.charAt(i);
            if (c < '1' || c > '9') {
                return rawPattern;
            }
        }

        StringBuilder expanded = new StringBuilder();
        Set<Integer> visited = new HashSet<>();

        int prevDot = rawPattern.charAt(0) - '0';
        expanded.append(prevDot);
        visited.add(prevDot);

        for (int i = 1; i < rawPattern.length(); i++) {
            int currDot = rawPattern.charAt(i) - '0';
            if (currDot == prevDot) {
                continue;
            }

            int prevRow = (prevDot - 1) / 3;
            int prevCol = (prevDot - 1) % 3;
            int currRow = (currDot - 1) / 3;
            int currCol = (currDot - 1) % 3;

            int dRow = currRow - prevRow;
            int dCol = currCol - prevCol;

            // Intermediate dot exists if both row and col difference are even
            // and at least one difference spans 2 units
            if (Math.abs(dRow) % 2 == 0 && Math.abs(dCol) % 2 == 0 &&
                    (Math.abs(dRow) == 2 || Math.abs(dCol) == 2)) {
                int midRow = prevRow + dRow / 2;
                int midCol = prevCol + dCol / 2;
                int midDot = midRow * 3 + midCol + 1;

                if (!visited.contains(midDot)) {
                    visited.add(midDot);
                    expanded.append(midDot);
                }
            }

            if (!visited.contains(currDot)) {
                visited.add(currDot);
                expanded.append(currDot);
            }

            prevDot = currDot;
        }

        return expanded.toString();
    }

    /**
     * Deduplicates previously visited digits in order (for pattern gesture compatibility with PINs).
     */
    public static String deduplicatePatternDigits(String s) {
        if (s == null || s.length() <= 1) return s == null ? "" : s;
        StringBuilder sb = new StringBuilder();
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!seen.contains(c)) {
                seen.add(c);
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Dynamically verifies if an input matches a target pattern without any hardcoding.
     */
    public static boolean matchesPattern(String input, String target) {
        if (input == null || target == null) return false;
        String trimmedInput = input.trim();
        String trimmedTarget = target.trim();
        if (trimmedInput.isEmpty() || trimmedTarget.isEmpty()) return false;

        // Direct equality
        if (trimmedInput.equals(trimmedTarget)) return true;

        // Expanded geometric match
        String expInput = expandPatternWithIntermediateDots(trimmedInput);
        String expTarget = expandPatternWithIntermediateDots(trimmedTarget);
        if (expInput.equals(expTarget)) return true;

        // Match against deduplicated target (e.g. PIN "8353" swiped as pattern gesture "835")
        String dedupTarget = deduplicatePatternDigits(trimmedTarget);
        if (expInput.equals(expandPatternWithIntermediateDots(dedupTarget))) return true;

        return false;
    }

    public synchronized boolean isMasterEnabled(Context context) {
        ensureInitialized(context);
        return mMasterEnabled;
    }

    private SharedPreferences getPrefs(Context context) {
        return context != null ? context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE) : null;
    }

    public synchronized void setMasterEnabled(Context context, boolean enabled) {
        ensureInitialized(context);
        mMasterEnabled = enabled;
        saveLockedApps(getPrefs(context));
    }

    public synchronized boolean isLockAllApps(Context context) {
        ensureInitialized(context);
        return mLockAllApps;
    }

    public synchronized void setLockAllApps(Context context, boolean enabled) {
        ensureInitialized(context);
        mLockAllApps = enabled;
        saveLockedApps(getPrefs(context));
        AppLogger.i("APPLOCK", "Lock All Apps set to: " + enabled);
    }

    public synchronized String getMasterPin(Context context) {
        ensureInitialized(context);
        return mMasterPin;
    }

    public synchronized void setMasterPin(Context context, String pin) {
        ensureInitialized(context);
        if (pin != null && !pin.trim().isEmpty()) {
            mMasterPin = pin.trim();
            saveLockedApps(getPrefs(context));
            AppLogger.i("APPLOCK", "Master PIN updated");
        }
    }

    public synchronized String getMasterPattern(Context context) {
        ensureInitialized(context);
        return mMasterPattern;
    }

    public synchronized void setMasterPattern(Context context, String pattern) {
        ensureInitialized(context);
        if (pattern != null && !pattern.trim().isEmpty()) {
            mMasterPattern = pattern.trim();
            saveLockedApps(getPrefs(context));
            AppLogger.i("APPLOCK", "Master pattern updated");
        }
    }

    public synchronized boolean isExempt(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return false;
        return mExemptApps.contains(packageName.toLowerCase());
    }

    public synchronized void setExempt(Context context, String packageName, boolean exempt) {
        ensureInitialized(context);
        if (packageName == null || packageName.isEmpty()) return;
        String pkg = packageName.toLowerCase();
        if (exempt) {
            mExemptApps.add(pkg);
        } else {
            mExemptApps.remove(pkg);
        }
        saveLockedApps(getPrefs(context));
        AppLogger.i("APPLOCK", "Exempt status for " + pkg + ": " + exempt);
    }

    public synchronized LockedAppConfig getLockedAppConfig(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return null;
        return mLockedApps.get(packageName.toLowerCase());
    }

    /**
     * Resolves effective config for any app: returns app-specific config if registered,
     * or a synthesized Master config if protected under Lock All mode.
     */
    public synchronized LockedAppConfig getEffectiveLockedAppConfig(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return null;
        String pkg = packageName.toLowerCase();

        LockedAppConfig cfg = mLockedApps.get(pkg);
        if (cfg != null) {
            return cfg;
        }

        if (mLockAllApps && !mExemptApps.contains(pkg) && !isSystemPackage(context, pkg)) {
            return new LockedAppConfig(pkg, mMasterPin, mMasterPattern, true);
        }

        return null;
    }

    public boolean isSystemPackage(Context context, String pkg) {
        if (pkg == null) return true;
        String p = pkg.toLowerCase();

        // BlueLine Console itself
        if (p.equals("net.nhiroki.bluelineconsole") ||
            p.equals("net.nhiroki.bluelineconsole.beta") ||
            (context != null && p.equals(context.getPackageName().toLowerCase()))) {
            return true;
        }

        // Android System UI & core OS
        if (p.equals("android") || p.equals("com.android.systemui") || p.contains("systemui")) {
            return true;
        }

        // Setup Wizard
        if (p.contains("setupwizard")) {
            return true;
        }

        // Launchers / Home Screen / Recents / Quickstep
        if (p.contains("launcher") || p.contains("quickstep") || p.contains("recents")) {
            return true;
        }

        // Emergency dialer & phone
        if (p.contains("emergency") || p.equals("com.android.phone")) {
            return true;
        }

        // Keyboards / Input Method Editors (IMEs)
        if (isInputMethod(context, p)) {
            return true;
        }

        return false;
    }

    private boolean isInputMethod(Context context, String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();
        if (p.contains("inputmethod") || p.contains("keyboard") ||
            p.equals("com.google.android.inputmethod.latin") ||
            p.equals("com.samsung.android.honeyboard") ||
            p.equals("com.touchtype.swiftkey")) {
            return true;
        }
        if (context != null) {
            try {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    List<android.view.inputmethod.InputMethodInfo> list = imm.getInputMethodList();
                    if (list != null) {
                        for (android.view.inputmethod.InputMethodInfo imi : list) {
                            if (imi.getPackageName().equalsIgnoreCase(p)) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public synchronized boolean isPackageLocked(Context context, String packageName) {
        ensureInitialized(context);
        if (!mMasterEnabled || packageName == null) return false;
        String pkg = packageName.toLowerCase();

        if (isSystemPackage(context, pkg)) {
            return false;
        }

        LockedAppConfig cfg = mLockedApps.get(pkg);
        if (cfg != null) {
            return cfg.enabled;
        }

        if (mExemptApps.contains(pkg)) {
            return false;
        }

        if (mLockAllApps && context != null) {
            try {
                android.content.pm.PackageManager pm = context.getPackageManager();
                if (pm != null) {
                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    public synchronized void onPackageInstalled(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null || packageName.isEmpty()) return;
        String pkg = packageName.toLowerCase();
        if (isSystemPackage(context, pkg)) return;

        AppLogger.i("APPLOCK", "Newly installed package secured: " + pkg);
        if (!mLockedApps.containsKey(pkg) && !mExemptApps.contains(pkg)) {
            saveLockedApps(getPrefs(context));
        }
    }

    public synchronized void setAppLock(Context context, String packageName, String pin, String pattern) {
        ensureInitialized(context);
        if (packageName == null || packageName.isEmpty()) return;
        String key = packageName.toLowerCase();
        LockedAppConfig existing = mLockedApps.get(key);
        String finalPin = (pin != null && !pin.isEmpty()) ? pin : (existing != null ? existing.pin : "0000");
        String finalPattern = (pattern != null && !pattern.isEmpty()) ? pattern : (existing != null ? existing.pattern : "");

        mLockedApps.put(key, new LockedAppConfig(key, finalPin, finalPattern, true));
        saveLockedApps(getPrefs(context));
        AppLogger.i("APPLOCK", "Set lock for " + key + " (pin=" + finalPin + ", pattern=" + finalPattern + ")");
    }

    public synchronized void removeAppLock(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return;
        String key = packageName.toLowerCase();
        mLockedApps.remove(key);
        saveLockedApps(getPrefs(context));
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

        // 1. Don't intercept system packages, BlueLine Console, launchers, or keyboards
        if (isSystemPackage(context, pkg)) {
            return;
        }

        // 2. Detect app switching to re-lock previous apps
        if (mLastForegroundPackage != null && !mLastForegroundPackage.equals(pkg)) {
            if (mUnlockedSessions.contains(mLastForegroundPackage)) {
                mUnlockedSessions.remove(mLastForegroundPackage);
                AppLogger.i("APPLOCK", "Switched from " + mLastForegroundPackage + " to " + pkg + "; session re-locked.");
            }
        }
        mLastForegroundPackage = pkg;

        // 3. Determine if this package is locked (explicit rule or Lock All mode)
        if (!isPackageLocked(context, pkg)) {
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

        // Throttle lock triggers to once per 1000ms
        long now = System.currentTimeMillis();
        if (now - mLastLockTriggerTime < 1000) {
            return;
        }
        mLastLockTriggerTime = now;

        AppLogger.i("APPLOCK", "Unauthorized access to " + pkg + ". Closing app and opening BlueLine Console unlock.");

        // 1. Immediately close the unauthorized app by pressing Home
        if (context instanceof BlueLineAgentService) {
            ((BlueLineAgentService) context).pressHome();
        } else if (BlueLineAgentService.getInstance() != null) {
            BlueLineAgentService.getInstance().pressHome();
        }

        // 2. Launch BlueLine Console MainActivity in App Unlock mode
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent lockIntent = new Intent(context, MainActivity.class);
                lockIntent.setAction(MainActivity.ACTION_UNLOCK_APP);
                lockIntent.putExtra(MainActivity.EXTRA_UNLOCK_PACKAGE, pkg);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(lockIntent);
            } catch (Exception e) {
                AppLogger.e("APPLOCK", "Error starting MainActivity for unlock", e);
            }
        }, 60);
    }
}
