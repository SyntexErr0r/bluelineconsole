package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import net.nhiroki.bluelineconsole.applock.AppLockManager;
import net.nhiroki.bluelineconsole.applicationMain.MainActivity;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;
import net.nhiroki.bluelineconsole.interfaces.CommandSearcher;
import net.nhiroki.bluelineconsole.interfaces.EventLauncher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppLockCommandSearcher implements CommandSearcher {

    @Override
    public void refresh(Context context) {}

    @Override
    public void close() {}

    @Override
    public boolean isPrepared() {
        return true;
    }

    @Override
    public void waitUntilPrepared() {}

    @NonNull
    @Override
    public List<CandidateEntry> searchCandidateEntries(String query, Context context) {
        List<CandidateEntry> candidates = new ArrayList<>();
        if (query == null) return candidates;

        String q = query.trim().toLowerCase();
        if (!q.startsWith("lock") && !q.startsWith("applock")) {
            return candidates;
        }

        AppLockManager mgr = AppLockManager.getInstance();
        boolean enabled = mgr.isMasterEnabled(context);
        Map<String, AppLockManager.LockedAppConfig> lockedApps = mgr.getAllLockedApps(context);

        // 1. "lock list"
        if (q.equals("lock list") || q.equals("applock list")) {
            candidates.add(new AppLockListCandidateEntry(lockedApps, mgr.isLockAllApps(context)));
            return candidates;
        }

        // 2. "lock all on" / "lock all off"
        if (q.equals("lock all on") || q.equals("applock all on")) {
            candidates.add(new AppLockToggleLockAllCandidateEntry(true));
            return candidates;
        }
        if (q.equals("lock all off") || q.equals("applock all off")) {
            candidates.add(new AppLockToggleLockAllCandidateEntry(false));
            return candidates;
        }

        // 3. "lock on" / "lock off"
        if (q.equals("lock on") || q.equals("applock on")) {
            candidates.add(new AppLockToggleCandidateEntry(true));
            return candidates;
        }
        if (q.equals("lock off") || q.equals("applock off")) {
            candidates.add(new AppLockToggleCandidateEntry(false));
            return candidates;
        }

        // 3b. "lock time on" / "lock time off" or "lock master time on" / "lock master time off"
        if (q.equals("lock time on") || q.equals("applock time on") || q.equals("lock master time on") || q.equals("applock master time on")) {
            candidates.add(new AppLockToggleTimeLockCandidateEntry(true));
            return candidates;
        }
        if (q.equals("lock time off") || q.equals("applock time off") || q.equals("lock master time off") || q.equals("applock master time off")) {
            candidates.add(new AppLockToggleTimeLockCandidateEntry(false));
            return candidates;
        }

        // 4. "lock master pin <pin>" or "lock master <pin>"
        Pattern pMasterPin = Pattern.compile("^(?:lock|applock)\\s+master(?:\\s+pin)?\\s+(\\d+)");
        Matcher mMasterPin = pMasterPin.matcher(q);
        if (mMasterPin.find()) {
            String pin = mMasterPin.group(1);
            candidates.add(new AppLockSetMasterPinCandidateEntry(pin));
            return candidates;
        }

        // 5. "lock master pattern <digits>"
        Pattern pMasterPat = Pattern.compile("^(?:lock|applock)\\s+master\\s+pattern\\s+([1-9]+)");
        Matcher mMasterPat = pMasterPat.matcher(q);
        if (mMasterPat.find()) {
            String pattern = mMasterPat.group(1);
            candidates.add(new AppLockSetMasterPatternCandidateEntry(pattern));
            return candidates;
        }

        // 6. "lock whitelist <app>" or "lock exempt <app>"
        Pattern pExempt = Pattern.compile("^(?:lock|applock)\\s+(?:exempt|whitelist)\\s+([a-zA-Z0-9_.-]+)");
        Matcher mExempt = pExempt.matcher(q);
        if (mExempt.find()) {
            String appName = mExempt.group(1);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockExemptCandidateEntry(resolvedPkg, appName, true));
            return candidates;
        }

        // 7. "lock <app> pin <pin>"
        Pattern pPin = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+pin\\s+(\\d+)");
        Matcher mPin = pPin.matcher(q);
        if (mPin.find()) {
            String appName = mPin.group(1);
            String pin = mPin.group(2);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockSetPinCandidateEntry(resolvedPkg, appName, pin));
            return candidates;
        }

        // 8. "lock <app> pattern <digits>"
        Pattern pPat = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+pattern\\s+([1-9]+)");
        Matcher mPat = pPat.matcher(q);
        if (mPat.find()) {
            String appName = mPat.group(1);
            String pattern = mPat.group(2);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockSetPatternCandidateEntry(resolvedPkg, appName, pattern));
            return candidates;
        }

        // 9. "lock <app> remove" or "lock <app> off"
        Pattern pRemove = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+(?:remove|off|delete|clear|exempt)");
        Matcher mRemove = pRemove.matcher(q);
        if (mRemove.find()) {
            String appName = mRemove.group(1);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockRemoveCandidateEntry(resolvedPkg, appName));
            return candidates;
        }

        // 10. "lock <app>" or "lock add <app>"
        Pattern pQuick = Pattern.compile("^(?:lock|applock)\\s+(?:add\\s+)?([a-zA-Z0-9_.-]+)$");
        Matcher mQuick = pQuick.matcher(q);
        if (mQuick.find()) {
            String appName = mQuick.group(1);
            if (!appName.equals("on") && !appName.equals("off") && !appName.equals("all") &&
                !appName.equals("master") && !appName.equals("list") && !appName.equals("status") &&
                !appName.equals("help") && !appName.equals("exempt") && !appName.equals("whitelist")) {
                String resolvedPkg = resolvePackage(context, appName);
                candidates.add(new AppLockQuickLockCandidateEntry(resolvedPkg, appName));
                return candidates;
            }
        }

        // 11. Default overview for "lock" or "applock"
        candidates.add(new AppLockStatusCandidateEntry(enabled, mgr.isLockAllApps(context), mgr.getMasterPin(context), mgr.getMasterPattern(context), lockedApps.size()));
        candidates.add(new AppLockListCandidateEntry(lockedApps, mgr.isLockAllApps(context)));

        return candidates;
    }

    public static String resolvePackage(Context context, String appName) {
        if (appName == null) return "";
        String low = appName.toLowerCase();
        if (low.equals("wa") || low.equals("whatsapp")) {
            return "com.whatsapp";
        }
        if (low.equals("tg") || low.equals("telegram")) {
            return "org.telegram.messenger";
        }
        if (low.contains(".")) {
            return appName; // already a package name
        }

        if (context == null) {
            return appName;
        }

        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : apps) {
                CharSequence label = pm.getApplicationLabel(ai);
                if (label != null && label.toString().equalsIgnoreCase(low)) {
                    return ai.packageName;
                }
            }
            for (ApplicationInfo ai : apps) {
                CharSequence label = pm.getApplicationLabel(ai);
                if (label != null && label.toString().toLowerCase().startsWith(low)) {
                    return ai.packageName;
                }
            }
            for (ApplicationInfo ai : apps) {
                CharSequence label = pm.getApplicationLabel(ai);
                if (label != null && label.toString().toLowerCase().contains(low)) {
                    return ai.packageName;
                }
            }
        } catch (Exception ignored) {}
        return appName;
    }

    public static class AppLockStatusCandidateEntry implements CandidateEntry {
        private final boolean mEnabled;
        private final boolean mLockAllApps;
        private final String mMasterPin;
        private final String mMasterPattern;
        private final int mLockedCount;

        public AppLockStatusCandidateEntry(boolean enabled, boolean lockAll, String masterPin, String masterPattern, int count) {
            this.mEnabled = enabled;
            this.mLockAllApps = lockAll;
            this.mMasterPin = masterPin;
            this.mMasterPattern = masterPattern;
            this.mLockedCount = count;
        }

        @Override
        public String getTitle() {
            return "🔒 App Lock: " + (mEnabled ? "ACTIVE" : "DISABLED") +
                   " | Lock All: " + (mLockAllApps ? "ON" : "OFF") +
                   " (" + mLockedCount + " custom apps)";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔒 BLUELINE APP LOCK SYSTEM");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("• Master System: " + (mEnabled ? "ACTIVE" : "DISABLED") + "\n" +
                    "• Lock All Mode: " + (mLockAllApps ? "ACTIVE (Downloaded & New Apps Locked)" : "OFF (Only Configured Apps)") + "\n" +
                    "• Dynamic Time Lock (Aa:Bb -> Ba:Ab): " + (AppLockManager.getInstance().isTimeLockEnabled(mainActivity) ? "ON (Rolling Time PIN & Pattern Active)" : "OFF") + "\n" +
                    "• Master PIN: " + mMasterPin + " | Master Pattern: " + mMasterPattern + "\n" +
                    "• Configured Apps: " + mLockedCount + " (WhatsApp: 9428, Telegram: 8353)\n" +
                    "• Commands:\n" +
                    "  'lock time on/off' - Toggle dynamic time PIN & pattern\n" +
                    "  'lock all on/off' - Secure all downloaded/new apps\n" +
                    "  'lock master pin <pin>' - Set master PIN\n" +
                    "  'lock master pattern <pat>' - Set master pattern\n" +
                    "  'lock <app> pin <pin>' - Custom PIN for any app\n" +
                    "  'lock <app> off' - Remove lock from app\n" +
                    "  'lock list' - Show protected apps");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> Toast.makeText(activity, "App Lock: " + (mEnabled ? "ACTIVE" : "DISABLED") + " (Lock All: " + (mLockAllApps ? "ON" : "OFF") + ")", Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockListCandidateEntry implements CandidateEntry {
        private final Map<String, AppLockManager.LockedAppConfig> mLockedApps;
        private final boolean mLockAllApps;

        public AppLockListCandidateEntry(Map<String, AppLockManager.LockedAppConfig> apps, boolean lockAllApps) {
            this.mLockedApps = apps;
            this.mLockAllApps = lockAllApps;
        }

        public AppLockListCandidateEntry(Map<String, AppLockManager.LockedAppConfig> apps) {
            this(apps, true);
        }

        @Override
        public String getTitle() {
            return "🔒 Locked Apps List (" + mLockedApps.size() + (mLockAllApps ? " + All Downloaded Apps" : "") + ")";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔒 PROTECTED APPLICATIONS");

            StringBuilder sb = new StringBuilder();
            if (mLockAllApps) {
                sb.append("★ ALL DOWNLOADED & NEW APPS: SECURED (Master Credentials)\n\n");
            }
            PackageManager pm = mainActivity.getPackageManager();
            for (AppLockManager.LockedAppConfig cfg : mLockedApps.values()) {
                String label = cfg.packageName;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(cfg.packageName, 0);
                    label = pm.getApplicationLabel(ai).toString();
                } catch (Exception ignored) {}

                sb.append("• ").append(label).append(" (").append(cfg.packageName).append(")\n")
                  .append("  PIN: ").append(cfg.pin).append(" | Pattern: ").append(cfg.pattern).append("\n");
            }

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText(sb.toString().trim());

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> Toast.makeText(activity, "Listed " + mLockedApps.size() + " locked apps", Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockSetPinCandidateEntry implements CandidateEntry {
        private final String mPackageName;
        private final String mAppName;
        private final String mPin;

        public AppLockSetPinCandidateEntry(String pkg, String appName, String pin) {
            this.mPackageName = pkg;
            this.mAppName = appName;
            this.mPin = pin;
        }

        @Override
        public String getTitle() {
            return "🔒 Lock " + mAppName + " with PIN: " + mPin;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔒 SET APP PIN LOCK");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Press Enter or tap to lock " + mAppName + "\nPackage: " + mPackageName + "\nNew PIN: " + mPin);

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setAppLock(activity, mPackageName, mPin, null);
                Toast.makeText(activity, "Locked " + mAppName + " with PIN: " + mPin, Toast.LENGTH_LONG).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockSetPatternCandidateEntry implements CandidateEntry {
        private final String mPackageName;
        private final String mAppName;
        private final String mPattern;

        public AppLockSetPatternCandidateEntry(String pkg, String appName, String pattern) {
            this.mPackageName = pkg;
            this.mAppName = appName;
            this.mPattern = pattern;
        }

        @Override
        public String getTitle() {
            return "🔒 Lock " + mAppName + " with Pattern: " + mPattern;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔒 SET APP PATTERN LOCK");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Press Enter or tap to lock " + mAppName + "\nPackage: " + mPackageName + "\nNew Pattern: " + mPattern);

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setAppLock(activity, mPackageName, null, mPattern);
                Toast.makeText(activity, "Locked " + mAppName + " with Pattern: " + mPattern, Toast.LENGTH_LONG).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockRemoveCandidateEntry implements CandidateEntry {
        private final String mPackageName;
        private final String mAppName;

        public AppLockRemoveCandidateEntry(String pkg, String appName) {
            this.mPackageName = pkg;
            this.mAppName = appName;
        }

        @Override
        public String getTitle() {
            return "🔓 Remove Lock from " + mAppName;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#ff5577"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔓 REMOVE APP LOCK");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Press Enter or tap to unlock " + mAppName + " (" + mPackageName + ")");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().removeAppLock(activity, mPackageName);
                Toast.makeText(activity, "Removed lock for " + mAppName, Toast.LENGTH_LONG).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockToggleCandidateEntry implements CandidateEntry {
        private final boolean mEnable;

        public AppLockToggleCandidateEntry(boolean enable) {
            this.mEnable = enable;
        }

        @Override
        public String getTitle() {
            return mEnable ? "🔒 Enable App Lock System" : "🔓 Disable App Lock System";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(mEnable ? Color.parseColor("#00f0ff") : Color.parseColor("#ff5577"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText(mEnable ? "🔒 ENABLE APP LOCK" : "🔓 DISABLE APP LOCK");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText(mEnable ? "▶ Press Enter or tap to turn App Lock ON" : "▶ Press Enter or tap to turn App Lock OFF");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setMasterEnabled(activity, mEnable);
                Toast.makeText(activity, "App Lock is now " + (mEnable ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockToggleLockAllCandidateEntry implements CandidateEntry {
        private final boolean mEnable;

        public AppLockToggleLockAllCandidateEntry(boolean enable) {
            this.mEnable = enable;
        }

        @Override
        public String getTitle() {
            return mEnable ? "🔒 Lock All Apps: ON (Secure all downloaded & future apps)" : "🔓 Lock All Apps: OFF";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(mEnable ? Color.parseColor("#00f0ff") : Color.parseColor("#ff5577"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText(mEnable ? "🔒 LOCK ALL APPS (ON)" : "🔓 LOCK ALL APPS (OFF)");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText(mEnable ?
                    "▶ Press Enter or tap to lock ALL downloaded & future apps with Master credentials" :
                    "▶ Press Enter or tap to turn off Lock All (only explicitly configured apps will be locked)");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setLockAllApps(activity, mEnable);
                Toast.makeText(activity, "Lock All Apps is now " + (mEnable ? "ACTIVE" : "OFF"), Toast.LENGTH_SHORT).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockToggleTimeLockCandidateEntry implements CandidateEntry {
        private final boolean mEnable;

        public AppLockToggleTimeLockCandidateEntry(boolean enable) {
            this.mEnable = enable;
        }

        @Override
        public String getTitle() {
            return mEnable ? "⏰ Dynamic Time Lock: ON (Aa:Bb -> Ba:Ab)" : "⏰ Dynamic Time Lock: OFF";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(mEnable ? Color.parseColor("#00f0ff") : Color.parseColor("#ff5577"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText(mEnable ? "⏰ DYNAMIC TIME LOCK (ON)" : "⏰ DYNAMIC TIME LOCK (OFF)");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText(mEnable ?
                    "▶ Press Enter or tap to enable rolling Time PIN & Pattern (Aa:Bb -> Ba:Ab, e.g. 07:57 -> 5707)" :
                    "▶ Press Enter or tap to disable rolling Time PIN & Pattern");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setTimeLockEnabled(activity, mEnable);
                Toast.makeText(activity, "Dynamic Time Lock is now " + (mEnable ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockSetMasterPinCandidateEntry implements CandidateEntry {
        private final String mPin;

        public AppLockSetMasterPinCandidateEntry(String pin) {
            this.mPin = pin;
        }

        @Override
        public String getTitle() {
            return "🔒 Set Master PIN: " + mPin;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔒 SET MASTER PIN");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Press Enter or tap to update Master PIN to: " + mPin + "\n(Used for all apps without a custom PIN)");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setMasterPin(activity, mPin);
                Toast.makeText(activity, "Master PIN updated to: " + mPin, Toast.LENGTH_LONG).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockSetMasterPatternCandidateEntry implements CandidateEntry {
        private final String mPattern;

        public AppLockSetMasterPatternCandidateEntry(String pattern) {
            this.mPattern = pattern;
        }

        @Override
        public String getTitle() {
            return "🔒 Set Master Pattern: " + mPattern;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔒 SET MASTER PATTERN");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Press Enter or tap to update Master Pattern to: " + mPattern + "\n(Used for all apps without a custom pattern)");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setMasterPattern(activity, mPattern);
                Toast.makeText(activity, "Master Pattern updated to: " + mPattern, Toast.LENGTH_LONG).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockExemptCandidateEntry implements CandidateEntry {
        private final String mPackageName;
        private final String mAppName;
        private final boolean mExempt;

        public AppLockExemptCandidateEntry(String pkg, String appName, boolean exempt) {
            this.mPackageName = pkg;
            this.mAppName = appName;
            this.mExempt = exempt;
        }

        @Override
        public String getTitle() {
            return (mExempt ? "🔓 Exempt " : "🔒 Protect ") + mAppName + " from Lock All";
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(mExempt ? Color.parseColor("#ff5577") : Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText(mExempt ? "🔓 EXEMPT APPLICATION" : "🔒 PROTECT APPLICATION");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Press Enter or tap to " + (mExempt ? "whitelist/exempt " : "protect ") + mAppName + " (" + mPackageName + ")");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager.getInstance().setExempt(activity, mPackageName, mExempt);
                Toast.makeText(activity, (mExempt ? "Exempted " : "Secured ") + mAppName, Toast.LENGTH_LONG).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockQuickLockCandidateEntry implements CandidateEntry {
        private final String mPackageName;
        private final String mAppName;

        public AppLockQuickLockCandidateEntry(String pkg, String appName) {
            this.mPackageName = pkg;
            this.mAppName = appName;
        }

        @Override
        public String getTitle() {
            return "🔒 Lock " + mAppName;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            LinearLayout layout = new LinearLayout(mainActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(0, 4, 0, 8);

            TextView header = new TextView(mainActivity);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(Color.parseColor("#00f0ff"));
            header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            header.setText("🔒 LOCK APPLICATION");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Press Enter or tap to lock " + mAppName + " (" + mPackageName + ")\nOr type 'lock " + mAppName + " pin <pin>' for custom PIN");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> {
                AppLockManager mgr = AppLockManager.getInstance();
                mgr.setAppLock(activity, mPackageName, mgr.getMasterPin(activity), mgr.getMasterPattern(activity));
                Toast.makeText(activity, "Locked " + mAppName + " with Master credentials", Toast.LENGTH_LONG).show();
                activity.finishIfNotHome();
            };
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) { return null; }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }
}
