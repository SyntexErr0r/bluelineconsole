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
            candidates.add(new AppLockListCandidateEntry(lockedApps));
            return candidates;
        }

        // 2. "lock on" / "lock off" / "applock on" / "applock off"
        if (q.equals("lock on") || q.equals("applock on")) {
            candidates.add(new AppLockToggleCandidateEntry(true));
            return candidates;
        }
        if (q.equals("lock off") || q.equals("applock off")) {
            candidates.add(new AppLockToggleCandidateEntry(false));
            return candidates;
        }

        // 3. "lock <app> pin <pin>"
        Pattern pPin = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+pin\\s+(\\d+)");
        Matcher mPin = pPin.matcher(q);
        if (mPin.find()) {
            String appName = mPin.group(1);
            String pin = mPin.group(2);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockSetPinCandidateEntry(resolvedPkg, appName, pin));
            return candidates;
        }

        // 4. "lock <app> pattern <digits>"
        Pattern pPat = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+pattern\\s+([1-9]+)");
        Matcher mPat = pPat.matcher(q);
        if (mPat.find()) {
            String appName = mPat.group(1);
            String pattern = mPat.group(2);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockSetPatternCandidateEntry(resolvedPkg, appName, pattern));
            return candidates;
        }

        // 5. "lock <app> remove" or "lock <app> off"
        Pattern pRemove = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+(?:remove|off|delete|clear)");
        Matcher mRemove = pRemove.matcher(q);
        if (mRemove.find()) {
            String appName = mRemove.group(1);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockRemoveCandidateEntry(resolvedPkg, appName));
            return candidates;
        }

        // 6. Default overview for "lock" or "applock"
        candidates.add(new AppLockStatusCandidateEntry(enabled, lockedApps.size()));
        candidates.add(new AppLockListCandidateEntry(lockedApps));

        return candidates;
    }

    private static String resolvePackage(Context context, String appName) {
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

        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : apps) {
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null && label.toString().toLowerCase().contains(low)) {
                return ai.packageName;
            }
        }
        return appName;
    }

    public static class AppLockStatusCandidateEntry implements CandidateEntry {
        private final boolean mEnabled;
        private final int mLockedCount;

        public AppLockStatusCandidateEntry(boolean enabled, int count) {
            this.mEnabled = enabled;
            this.mLockedCount = count;
        }

        @Override
        public String getTitle() {
            return "🔒 App Lock: " + (mEnabled ? "ACTIVE" : "DISABLED") + " (" + mLockedCount + " apps)";
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
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("Status: " + (mEnabled ? "ACTIVE (Security Protocol Enforced)" : "DISABLED") + "\n" +
                    "Protected apps: " + mLockedCount + " (WhatsApp: 9428, Telegram: 8353)\n" +
                    "Commands: 'lock list', 'lock <app> pin <pin>', 'lock on/off'");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> Toast.makeText(activity, "App Lock is " + (mEnabled ? "ACTIVE" : "DISABLED"), Toast.LENGTH_SHORT).show();
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

        public AppLockListCandidateEntry(Map<String, AppLockManager.LockedAppConfig> apps) {
            this.mLockedApps = apps;
        }

        @Override
        public String getTitle() {
            return "🔒 Locked Apps List (" + mLockedApps.size() + ")";
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
}
