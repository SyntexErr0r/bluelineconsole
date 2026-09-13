package net.nhiroki.bluelineconsole.applock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import net.nhiroki.bluelineconsole.agent.BlueLineAgentService;
import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
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
    public static final String KEY_TIME_LOCK_ENABLED = "pref_app_lock_time_lock_enabled";
    public static final String KEY_MASTER_PIN = "pref_app_lock_master_pin";
    public static final String KEY_MASTER_PATTERN = "pref_app_lock_master_pattern";
    public static final String KEY_EXEMPT_APPS = "pref_app_lock_exempt_apps";
    public static final String KEY_LOCK_HOME_LAUNCHER = "pref_app_lock_lock_launcher";

    public static final String KEY_GRACE_PERIOD_MODE = "pref_app_lock_grace_period_mode";
    public static final String KEY_GRACE_PERIOD_CUSTOM_SEC = "pref_app_lock_grace_period_custom_sec";

    public static final String GRACE_UNTIL_LOCKED = "until_locked";
    public static final String GRACE_30_SEC = "30s";
    public static final String GRACE_2_MIN = "2m";
    public static final String GRACE_5_MIN = "5m";
    public static final String GRACE_CUSTOM = "custom";

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
    private boolean mTimeLockEnabled = true;
    private boolean mLockHomeLauncher = false;
    private String mMasterPin = DEFAULT_MASTER_PIN;
    private String mMasterPattern = DEFAULT_MASTER_PATTERN;
    private final Set<String> mExemptApps = new HashSet<>();
    private final Map<String, LockedAppConfig> mLockedApps = new HashMap<>();

    // In-memory runtime session states
    private String mGracePeriodMode = GRACE_UNTIL_LOCKED;
    private int mGracePeriodCustomSec = 60;
    private final Set<String> mUnlockedSessions = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, Long> mUnlockedSessionsUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> mConsoleAuthorizedUntil = new ConcurrentHashMap<>();
    private static BroadcastReceiver sScreenOffReceiver = null;
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
        mTimeLockEnabled = prefs.getBoolean(KEY_TIME_LOCK_ENABLED, true);
        mLockHomeLauncher = prefs.getBoolean(KEY_LOCK_HOME_LAUNCHER, false);
        mMasterPin = prefs.getString(KEY_MASTER_PIN, DEFAULT_MASTER_PIN);
        mMasterPattern = prefs.getString(KEY_MASTER_PATTERN, DEFAULT_MASTER_PATTERN);
        mGracePeriodMode = prefs.getString(KEY_GRACE_PERIOD_MODE, GRACE_UNTIL_LOCKED);
        mGracePeriodCustomSec = prefs.getInt(KEY_GRACE_PERIOD_CUSTOM_SEC, 60);

        if (sScreenOffReceiver == null) {
            sScreenOffReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                        clearUnlockedSessions();
                        AppLogger.i("APPLOCK", "Screen off detected; all unlocked app sessions cleared.");
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
                        if (cfg.packageName.contains("telegram") && "835".equals(cfg.pattern)) {
                            cfg = new LockedAppConfig(cfg.packageName, cfg.pin, "8352", cfg.enabled);
                        }
                        mLockedApps.put(cfg.packageName.toLowerCase(), cfg);
                    }
                }
            } catch (Exception e) {
                AppLogger.e("APPLOCK", "Error loading locked apps JSON", e);
            }
        }

        // Initialize default rules: WhatsApp (PIN 9428, Pattern 94258), Telegram (PIN 8353, Pattern 8352), Termux (PIN 8376, Pattern 8376)
        if (mLockedApps.isEmpty()) {
            initDefaultLocks(prefs);
        } else {
            if (!mLockedApps.containsKey("com.termux")) {
                mLockedApps.put("com.termux", new LockedAppConfig("com.termux", "8376", "8376", true));
                saveLockedApps(prefs);
            }
        }
    }

    private void initDefaultLocks(SharedPreferences prefs) {
        // WhatsApp defaults: PIN 9428 (W-H-A-T), Pattern 9428 (dots 2 to 8 dynamically cross 5)
        mLockedApps.put("com.whatsapp", new LockedAppConfig("com.whatsapp", "9428", "94258", true));
        mLockedApps.put("com.whatsapp.w4b", new LockedAppConfig("com.whatsapp.w4b", "9428", "94258", true));

        // Telegram defaults: PIN 8353 (T-E-L-E), Pattern 8352 (dots 8 -> 3 -> 5 -> 2)
        mLockedApps.put("org.telegram.messenger", new LockedAppConfig("org.telegram.messenger", "8353", "8352", true));
        mLockedApps.put("org.telegram.messenger.web", new LockedAppConfig("org.telegram.messenger.web", "8353", "8352", true));
        mLockedApps.put("org.telegram.messenger.beta", new LockedAppConfig("org.telegram.messenger.beta", "8353", "8352", true));
        mLockedApps.put("nekox.messenger", new LockedAppConfig("nekox.messenger", "8353", "8352", true));
        mLockedApps.put("org.thunderdog.challegram", new LockedAppConfig("org.thunderdog.challegram", "8353", "8352", true));

        // Termux defaults: PIN 8376 (T-E-R-M), Pattern 8376 (dots 8 -> 3 -> 7 -> 6, crossing 5 between 3 and 7)
        mLockedApps.put("com.termux", new LockedAppConfig("com.termux", "8376", "8376", true));

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
                    .putBoolean(KEY_TIME_LOCK_ENABLED, mTimeLockEnabled)
                    .putBoolean(KEY_LOCK_HOME_LAUNCHER, mLockHomeLauncher)
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
     * Algorithmic 11-node Wing-Zero Pattern Lock coordinate mapping:
     * Row 0: 1 (col 1), 2 (col 2), 3 (col 3)
     * Row 1: 0 (col 0), 4 (col 1), 5 (col 2), 6 (col 3), 0 (col 4)
     * Row 2: 7 (col 1), 8 (col 2), 9 (col 3)
     */
    public static int getDotRow(char c) {
        switch (c) {
            case '1': case '2': case '3': return 0;
            case '0': case '4': case '5': case '6': return 1;
            case '7': case '8': case '9': return 2;
            default: return -1;
        }
    }

    public static int getDotCol(char c) {
        switch (c) {
            case '1': case '4': case '7': return 1;
            case '2': case '5': case '8': return 2;
            case '3': case '6': case '9': return 3;
            case '0': return 0; // Default Left 0 (or 4 for Right 0)
            default: return -1;
        }
    }

    public static char getDotChar(int row, int col) {
        if (row == 0) {
            if (col == 1) return '1';
            if (col == 2) return '2';
            if (col == 3) return '3';
        } else if (row == 1) {
            if (col == 0 || col == 4) return '0';
            if (col == 1) return '4';
            if (col == 2) return '5';
            if (col == 3) return '6';
        } else if (row == 2) {
            if (col == 1) return '7';
            if (col == 2) return '8';
            if (col == 3) return '9';
        }
        return '\0';
    }

    /**
     * Traverses the digit path on a standard 3x3 grid (dots 1-9) and automatically inserts
     * intermediate dots crossed along straight or diagonal lines that haven't yet been visited.
     * E.g. "9428" -> "94258" (because moving from 2 to 8 crosses 5).
     * "13" -> "123", "19" -> "159", "37" -> "357", "79" -> "789", etc.
     */
    public static String expandPatternWithIntermediateDots(String rawPattern) {
        if (rawPattern == null || rawPattern.length() <= 1) {
            return rawPattern == null ? "" : rawPattern;
        }

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
     * Traverses the digit path on the 11-node Wing-Zero Pattern Lock (Row 0: 1-2-3, Row 1: 0-4-5-6-0, Row 2: 7-8-9)
     * and automatically inserts intermediate dots crossed along straight or diagonal lines.
     * E.g. "13" -> "123", "46" -> "456", "79" -> "789", "17" -> "147", "28" -> "258", "39" -> "369", "19" -> "159", "37" -> "357".
     * Also handles Left 0 and Right 0 wing connections.
     */
    public static String expand11NodePattern(String rawPattern) {
        if (rawPattern == null || rawPattern.length() <= 1) {
            return rawPattern == null ? "" : rawPattern;
        }

        StringBuilder expanded = new StringBuilder();
        Set<Character> visited = new HashSet<>();

        char prevChar = rawPattern.charAt(0);
        expanded.append(prevChar);
        visited.add(prevChar);

        for (int i = 1; i < rawPattern.length(); i++) {
            char currChar = rawPattern.charAt(i);
            // Allow double 0 since there are two distinct '0' nodes (Left 0 and Right 0)
            if (currChar == prevChar && currChar != '0') {
                continue;
            }

            int prevRow = getDotRow(prevChar);
            int prevCol = getDotCol(prevChar);
            int currRow = getDotRow(currChar);
            int currCol = getDotCol(currChar);

            // Dynamic wing selection: if connecting to/from right side of grid (col > 2), use right 0 (col 4)
            if (prevChar == '0' && currChar == '0') {
                prevCol = 0;
                currCol = 4;
            } else if (currChar == '0' && prevCol > 2) {
                currCol = 4;
            } else if (prevChar == '0' && currCol > 2) {
                prevCol = 4;
            }

            if (prevRow != -1 && prevCol != -1 && currRow != -1 && currCol != -1) {
                int dRow = currRow - prevRow;
                int dCol = currCol - prevCol;

                if (Math.abs(dRow) % 2 == 0 && Math.abs(dCol) % 2 == 0 &&
                        (Math.abs(dRow) == 2 || Math.abs(dCol) == 2)) {
                    int midRow = prevRow + dRow / 2;
                    int midCol = prevCol + dCol / 2;

                    char midChar = getDotChar(midRow, midCol);
                    if (midChar != '\0' && midChar != prevChar) {
                        expanded.append(midChar);
                    }
                } else if (dRow == 0 && Math.abs(dCol) == 4) {
                    int stepC = dCol / 4;
                    for (int c = prevCol + stepC; c != currCol; c += stepC) {
                        char midChar = getDotChar(1, c);
                        if (midChar != '\0' && midChar != prevChar) {
                            expanded.append(midChar);
                        }
                    }
                }
            }

            if (currChar != prevChar || currChar == '0') {
                expanded.append(currChar);
            }

            prevChar = currChar;
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
     * Supports:
     * 1. Direct equality (e.g. "3502" == "3502")
     * 2. Core-bridge repeat swipes (e.g. "2C2C2C2" matches "2222")
     * 3. 11-node Cyber Matrix geometric expansion
     * 4. 3x3 geometric expansion (e.g. "9428" matching "94258")
     * 5. Target deduplication
     */
    public static boolean matchesPattern(String input, String target) {
        if (input == null || target == null) return false;
        String trimmedInput = input.trim();
        String trimmedTarget = target.trim();
        // A pattern lock gesture requires at least 4 connected dots
        if (trimmedInput.length() < 4 || trimmedTarget.length() < 4) return false;

        // 1. Direct equality
        if (trimmedInput.equalsIgnoreCase(trimmedTarget)) return true;

        // 2. Core-bridge normalized equality (e.g. "2C2C2C2" matches "2222")
        String normInput = trimmedInput.replace("C", "").replace("c", "");
        String normTarget = trimmedTarget.replace("C", "").replace("c", "");
        if (normInput.length() >= 4 && (normInput.equals(trimmedTarget) || normInput.equals(normTarget))) {
            return true;
        }

        // 3. 11-Node Cyber Matrix geometric match
        String exp11Input = expand11NodePattern(trimmedInput);
        String exp11Target = expand11NodePattern(trimmedTarget);
        if (exp11Input.length() >= 4 && exp11Target.length() >= 4) {
            if (exp11Input.equalsIgnoreCase(exp11Target) || exp11Input.equalsIgnoreCase(trimmedTarget) || trimmedInput.equalsIgnoreCase(exp11Target)) {
                return true;
            }
        }

        // 4. 11-Node geometric match on Core-normalized strings
        if (normInput.length() >= 4 && normTarget.length() >= 4) {
            String exp11NormInput = expand11NodePattern(normInput);
            String exp11NormTarget = expand11NodePattern(normTarget);
            if (exp11NormInput.equalsIgnoreCase(exp11NormTarget) || exp11NormInput.equalsIgnoreCase(normTarget) || normInput.equalsIgnoreCase(exp11NormTarget)) {
                return true;
            }
        }

        // 5. 3x3 geometric expansion (backward compatibility for standard 3x3 patterns like "9428" -> "94258")
        String expInput = expandPatternWithIntermediateDots(trimmedInput);
        String expTarget = expandPatternWithIntermediateDots(trimmedTarget);
        if (expInput.length() >= 4 && expTarget.length() >= 4) {
            if (expInput.equalsIgnoreCase(expTarget) || expInput.equalsIgnoreCase(trimmedTarget) || trimmedInput.equalsIgnoreCase(expTarget)) {
                return true;
            }
        }

        // 6. Match against deduplicated target ONLY if it retains at least 4 distinct dots
        String dedupTarget = deduplicatePatternDigits(trimmedTarget);
        if (dedupTarget.length() >= 4 && (trimmedInput.equals(dedupTarget) || expInput.equals(expandPatternWithIntermediateDots(dedupTarget)) || exp11Input.equals(expand11NodePattern(dedupTarget)))) {
            return true;
        }

        return false;
    }

    /**
     * Converts an application name to a 4-digit PIN based on standard phone T9 keypad letters:
     * 2: ABC, 3: DEF, 4: GHI, 5: JKL, 6: MNO, 7: PQRS, 8: TUV, 9: WXYZ.
     * E.g. "WhatsApp" -> "9428" (WHAT)
     *      "Telegram" -> "8353" (TELE)
     *      "Termux"   -> "8376" (TERM)
     *      "Smart Launcher" -> "7627" (SMAR)
     */
    public static String computeT9PinFromName(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toUpperCase(name.charAt(i));
            if (c >= 'A' && c <= 'Z') {
                switch (c) {
                    case 'A': case 'B': case 'C': sb.append('2'); break;
                    case 'D': case 'E': case 'F': sb.append('3'); break;
                    case 'G': case 'H': case 'I': sb.append('4'); break;
                    case 'J': case 'K': case 'L': sb.append('5'); break;
                    case 'M': case 'N': case 'O': sb.append('6'); break;
                    case 'P': case 'Q': case 'R': case 'S': sb.append('7'); break;
                    case 'T': case 'U': case 'V': sb.append('8'); break;
                    case 'W': case 'X': case 'Y': case 'Z': sb.append('9'); break;
                }
                if (sb.length() == 4) break;
            }
        }
        while (sb.length() > 0 && sb.length() < 4) {
            sb.append(sb.charAt(sb.length() - 1));
        }
        return sb.toString();
    }

    public static String getT9PinForPackage(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return "";
        String label = packageName;
        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                if (pm != null) {
                    ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                    CharSequence l = pm.getApplicationLabel(ai);
                    if (l != null && l.length() > 0) {
                        label = l.toString();
                    }
                }
            } catch (Exception ignored) {}
        } else {
            int lastDot = packageName.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < packageName.length() - 1) {
                label = packageName.substring(lastDot + 1);
            }
        }
        return computeT9PinFromName(label);
    }

    /**
     * Computes 4-digit PIN from hour and minute using formula: Aa:Bb -> Ba:Ab
     * A = hour tens, a = hour units, B = minute tens, b = minute units.
     * e.g. 07:57 -> 5707.
     */
    public static String computeTimePin(int hour, int minute) {
        int A = (hour / 10) % 10;
        int a = hour % 10;
        int B = (minute / 10) % 10;
        int b = minute % 10;
        return "" + B + a + A + b;
    }

    /**
     * Converts a 4-digit PIN to a valid pattern swipe on the 11-node Wing-Zero grid.
     * '0' is directly swiped using the Left or Right '0' wing nodes.
     * '0' is strictly '0' and is NEVER mapped to 5 or 9.
     * Expands intermediate dots so physical swipes match properly.
     */
    public static String computeTimePatternFromPin(String pin) {
        if (pin == null || pin.isEmpty()) return "";
        return expand11NodePattern(pin);
    }

    /**
     * Returns valid time-based PINs for given Calendar (both 12h and 24h formats).
     */
    public static List<String> getTimeBasedPinsForCalendar(Calendar cal) {
        List<String> pins = new ArrayList<>();
        if (cal == null) return pins;
        int h24 = cal.get(Calendar.HOUR_OF_DAY);
        int m = cal.get(Calendar.MINUTE);
        int h12 = cal.get(Calendar.HOUR);
        if (h12 == 0) h12 = 12;

        String pin12 = computeTimePin(h12, m);
        String pin24 = computeTimePin(h24, m);

        pins.add(pin12);
        if (!pins.contains(pin24)) {
            pins.add(pin24);
        }
        return pins;
    }

    /**
     * Returns all valid time-based PINs within +/- 1 minute window for clock skew tolerance.
     */
    public static List<String> getAllValidTimeBasedPins() {
        List<String> allPins = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (long delta : new long[]{0L, -60000L, 60000L}) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now + delta);
            for (String p : getTimeBasedPinsForCalendar(cal)) {
                if (!allPins.contains(p)) {
                    allPins.add(p);
                }
            }
        }
        return allPins;
    }

    /**
     * Returns all valid time-based patterns (Option A) within +/- 1 minute window.
     * Includes both raw 4-digit PIN sequence (directly swipeable on 11-node cyber matrix)
     * and smart-remapped patterns.
     */
    public static List<String> getAllValidTimeBasedPatterns() {
        List<String> patterns = new ArrayList<>();
        for (String pin : getAllValidTimeBasedPins()) {
            if (pin != null && pin.length() >= 4 && !patterns.contains(pin)) {
                patterns.add(pin);
            }
            String pat = computeTimePatternFromPin(pin);
            if (!pat.isEmpty() && !patterns.contains(pat)) {
                patterns.add(pat);
            }
        }
        return patterns;
    }

    public static boolean isValidTimeBasedPin(String input) {
        if (input == null || input.isEmpty()) return false;
        return getAllValidTimeBasedPins().contains(input.trim());
    }

    public static boolean isValidTimeBasedPattern(String inputPattern) {
        if (inputPattern == null || inputPattern.trim().length() < 4) return false;
        for (String validPat : getAllValidTimeBasedPatterns()) {
            if (matchesPattern(inputPattern, validPat)) {
                return true;
            }
        }
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
        AppLogger.i("APPLOCK", "Master AppLock enabled set to: " + enabled);
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

    public synchronized boolean isLockHomeLauncher(Context context) {
        ensureInitialized(context);
        return mLockHomeLauncher;
    }

    public synchronized void setLockHomeLauncher(Context context, boolean enabled) {
        ensureInitialized(context);
        mLockHomeLauncher = enabled;
        saveLockedApps(getPrefs(context));
        AppLogger.i("APPLOCK", "Lock Home Launcher set to: " + enabled);
    }

    public synchronized boolean isTimeLockEnabled(Context context) {
        ensureInitialized(context);
        return mTimeLockEnabled;
    }

    public synchronized void setTimeLockEnabled(Context context, boolean enabled) {
        ensureInitialized(context);
        mTimeLockEnabled = enabled;
        saveLockedApps(getPrefs(context));
        AppLogger.i("APPLOCK", "Time-based lock enabled set to: " + enabled);
    }

    public synchronized String getMasterPin(Context context) {
        ensureInitialized(context);
        if (!mMasterPin.equals(DEFAULT_MASTER_PIN)) {
            return mMasterPin;
        }
        if (mTimeLockEnabled) {
            Calendar cal = Calendar.getInstance();
            int h = cal.get(Calendar.HOUR_OF_DAY);
            int m = cal.get(Calendar.MINUTE);
            return computeTimePin(h, m);
        }
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
        if (!mMasterPattern.equals(DEFAULT_MASTER_PATTERN)) {
            return mMasterPattern;
        }
        if (mTimeLockEnabled) {
            Calendar cal = Calendar.getInstance();
            int h = cal.get(Calendar.HOUR_OF_DAY);
            int m = cal.get(Calendar.MINUTE);
            String pin = computeTimePin(h, m);
            return computeTimePatternFromPin(pin);
        }
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

    public synchronized String getGracePeriodMode(Context context) {
        if (context != null) ensureInitialized(context);
        return mGracePeriodMode;
    }

    public synchronized void setGracePeriodMode(Context context, String mode) {
        if (context != null) ensureInitialized(context);
        if (mode != null) {
            mGracePeriodMode = mode;
            if (context != null) {
                getPrefs(context).edit().putString(KEY_GRACE_PERIOD_MODE, mode).apply();
            }
            AppLogger.i("APPLOCK", "Grace period mode updated to: " + mode);
        }
    }

    public synchronized int getCustomGracePeriodSeconds(Context context) {
        if (context != null) ensureInitialized(context);
        return mGracePeriodCustomSec;
    }

    public synchronized void setCustomGracePeriodSeconds(Context context, int seconds) {
        if (context != null) ensureInitialized(context);
        if (seconds > 0) {
            mGracePeriodCustomSec = seconds;
            if (context != null) {
                getPrefs(context).edit().putInt(KEY_GRACE_PERIOD_CUSTOM_SEC, seconds).apply();
            }
            AppLogger.i("APPLOCK", "Custom grace period updated to: " + seconds + "s");
        }
    }

    public synchronized String getGracePeriodSummary(Context context) {
        if (context != null) ensureInitialized(context);
        if (GRACE_30_SEC.equals(mGracePeriodMode)) {
            return "30 seconds";
        } else if (GRACE_2_MIN.equals(mGracePeriodMode)) {
            return "2 minutes";
        } else if (GRACE_5_MIN.equals(mGracePeriodMode)) {
            return "5 minutes";
        } else if (GRACE_CUSTOM.equals(mGracePeriodMode)) {
            if (mGracePeriodCustomSec < 60) {
                return "Custom (" + mGracePeriodCustomSec + "s)";
            } else if (mGracePeriodCustomSec % 60 == 0) {
                return "Custom (" + (mGracePeriodCustomSec / 60) + "m)";
            } else {
                return "Custom (" + (mGracePeriodCustomSec / 60) + "m " + (mGracePeriodCustomSec % 60) + "s)";
            }
        }
        return "Until phone is locked";
    }

    public synchronized boolean isExempt(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return false;
        return mExemptApps.contains(packageName.toLowerCase());
    }

    public synchronized void setExempt(Context context, String packageName, boolean exempt) {
        ensureInitialized(context);
        if (packageName == null) return;
        String pkg = packageName.toLowerCase();
        if (exempt) {
            mExemptApps.add(pkg);
        } else {
            mExemptApps.remove(pkg);
        }
        saveLockedApps(getPrefs(context));
        AppLogger.i("APPLOCK", "Exempt set for " + pkg + " to " + exempt);
    }

    public synchronized List<String> getExemptApps(Context context) {
        ensureInitialized(context);
        return new ArrayList<>(mExemptApps);
    }

    public synchronized LockedAppConfig getLockedAppConfig(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return null;
        return mLockedApps.get(packageName.toLowerCase());
    }

    /**
     * Resolves effective config for any app: returns app-specific config if registered,
     * or a synthesized config with T9 name-based PIN and Master credentials.
     */
    public synchronized LockedAppConfig getEffectiveLockedAppConfig(Context context, String packageName) {
        ensureInitialized(context);
        if (packageName == null) return null;
        String pkg = packageName.toLowerCase();

        String t9Pin = getT9PinForPackage(context, pkg);

        LockedAppConfig cfg = mLockedApps.get(pkg);
        if (cfg != null) {
            return cfg;
        }

        if (mLockAllApps && !mExemptApps.contains(pkg) && !isSystemPackage(context, pkg) && (!isHomeLauncher(context, pkg) || mLockHomeLauncher)) {
            String effPin = (t9Pin != null && !t9Pin.isEmpty()) ? t9Pin : getMasterPin(context);
            String effPattern = (t9Pin != null && !t9Pin.isEmpty()) ? expandPatternWithIntermediateDots(t9Pin) : getMasterPattern(context);
            return new LockedAppConfig(pkg, effPin, effPattern, true);
        }

        return null;
    }

    /**
     * Identifies if a package is an Android Home Launcher (Smart Launcher, Nova, Pixel Launcher, etc.)
     * Launchers must NEVER be locked so the user's home screen is never blocked or trapped in a loop.
     */
    public boolean isHomeLauncher(Context context, String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase();

        // 1. Standard keywords in launcher package names
        if (p.contains("launcher") || p.contains("quickstep") || p.contains("recents") || p.contains("trebuchet") || p.contains("home")) {
            return true;
        }

        // 2. Known third-party launchers without "launcher" or "home" in package name
        if (p.startsWith("ginlemon.flower") || // Smart Launcher (ginlemon.flowerfree, ginlemon.flowerpro, ginlemon.flower)
            p.startsWith("bitpit.launcher") || // Niagara Launcher
            p.equals("com.teslacoilsw.launcher") || // Nova Launcher
            p.startsWith("ch.deletescape.lawnchair") || // Lawnchair
            p.startsWith("app.lawnchair")) {
            return true;
        }

        // 3. Query Android PackageManager for registered home launchers
        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                if (pm != null) {
                    Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                    homeIntent.addCategory(Intent.CATEGORY_HOME);
                    List<ResolveInfo> list = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (list != null) {
                        for (ResolveInfo ri : list) {
                            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                                if (ri.activityInfo.packageName.equalsIgnoreCase(p)) {
                                    return true;
                                }
                            }
                        }
                    }
                    List<ResolveInfo> allList = pm.queryIntentActivities(homeIntent, 0);
                    if (allList != null) {
                        for (ResolveInfo ri : allList) {
                            if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                                if (ri.activityInfo.packageName.equalsIgnoreCase(p)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
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

        // Home Launchers (Smart Launcher, Nova, Pixel Launcher, etc.)
        if (isHomeLauncher(context, p)) {
            if (mLockHomeLauncher || (mLockedApps.containsKey(p) && mLockedApps.get(p).enabled)) {
                return false;
            }
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

        if (isHomeLauncher(context, pkg) && !mLockHomeLauncher && (!mLockedApps.containsKey(pkg) || !mLockedApps.get(pkg).enabled)) {
            return false;
        }

        LockedAppConfig cfg = mLockedApps.get(pkg);
        if (cfg != null) {
            return cfg.enabled;
        }

        if (mExemptApps.contains(pkg)) {
            return false;
        }

        if (mLockAllApps) {
            if (isHomeLauncher(context, pkg)) {
                return mLockHomeLauncher;
            }
            if (context != null) {
                try {
                    PackageManager pm = context.getPackageManager();
                    if (pm != null) {
                        Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                        if (launchIntent != null) {
                            return true;
                        }
                    }
                } catch (Exception ignored) {}
            }
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
     * Grants an unlocked session for the current active app run based on configured Grace Period.
     */
    public void unlockAppSession(String packageName) {
        unlockAppSession(null, packageName);
    }

    public void unlockAppSession(Context context, String packageName) {
        if (packageName == null) return;
        String pkg = packageName.toLowerCase();
        if (context != null) {
            ensureInitialized(context);
        }
        long durationMs;
        if (GRACE_30_SEC.equals(mGracePeriodMode)) {
            durationMs = 30 * 1000L;
        } else if (GRACE_2_MIN.equals(mGracePeriodMode)) {
            durationMs = 2 * 60 * 1000L;
        } else if (GRACE_5_MIN.equals(mGracePeriodMode)) {
            durationMs = 5 * 60 * 1000L;
        } else if (GRACE_CUSTOM.equals(mGracePeriodMode)) {
            durationMs = Math.max(1, mGracePeriodCustomSec) * 1000L;
        } else {
            durationMs = Long.MAX_VALUE;
        }

        long expiry = (durationMs == Long.MAX_VALUE) ? Long.MAX_VALUE : (System.currentTimeMillis() + durationMs);
        mUnlockedSessionsUntil.put(pkg, expiry);
        mUnlockedSessions.add(pkg);
        AppLogger.i("APPLOCK", "App unlocked for session: " + pkg + " (grace=" + mGracePeriodMode + ", expires=" + expiry + ")");
    }

    public void clearUnlockedSessions() {
        mUnlockedSessionsUntil.clear();
        mUnlockedSessions.clear();
    }

    public boolean isAppUnlockedForSession(String packageName) {
        if (packageName == null) return false;
        String pkg = packageName.toLowerCase();
        Long expiry = mUnlockedSessionsUntil.get(pkg);
        if (expiry == null) {
            return mUnlockedSessions.contains(pkg);
        }
        if (expiry != Long.MAX_VALUE && System.currentTimeMillis() > expiry) {
            mUnlockedSessionsUntil.remove(pkg);
            mUnlockedSessions.remove(pkg);
            AppLogger.i("APPLOCK", "Grace period expired for: " + pkg);
            return false;
        }
        return true;
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

        // 2. Track foreground package without prematurely wiping unlocked sessions
        // (Grace periods and screen-off events manage session expiration instead)
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
                unlockAppSession(context, pkg);
                AppLogger.i("APPLOCK", "Bypassed lock for console-authorized launch: " + pkg);
                return;
            } else {
                mConsoleAuthorizedUntil.remove(pkg);
            }
        }

        // Check if already unlocked in active session / grace period
        if (isAppUnlockedForSession(pkg)) {
            return;
        }

        // Throttle lock triggers to once per 1000ms
        long now = System.currentTimeMillis();
        if (now - mLastLockTriggerTime < 1000) {
            return;
        }
        mLastLockTriggerTime = now;

        AppLogger.i("APPLOCK", "Unauthorized access to " + pkg + ". Opening BlueLine Console unlock overlay.");

        // Launch BlueLine Console MainActivity in App Unlock mode directly over the locked app
        // (Do NOT call pressHome() to keep split-screen and multi-window environments intact)
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

    public void resetLockTriggerThrottle() {
        this.mLastLockTriggerTime = 0;
    }
}
