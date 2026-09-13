package net.nhiroki.bluelineconsole.commandSearchers.eachSearcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.nhiroki.bluelineconsole.R;

import net.nhiroki.bluelineconsole.applock.AppLockDialogHelper;
import net.nhiroki.bluelineconsole.applock.AppLockManager;
import net.nhiroki.bluelineconsole.applicationMain.AppLockSettingsActivity;
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

        String rawTrimmed = query.trim();
        String q = rawTrimmed.toLowerCase();
        if (q.startsWith("/")) {
            q = q.substring(1).trim();
        }
        if (!q.startsWith("lock") && !q.startsWith("applock")) {
            return candidates;
        }

        AppLockManager mgr = AppLockManager.getInstance();
        boolean enabled = mgr.isMasterEnabled(context);
        Map<String, AppLockManager.LockedAppConfig> lockedApps = mgr.getAllLockedApps(context);

        // 1. "lock", "applock", "lock settings", "lock manage", "lock gui", "lock preferences", "lock config"
        if (q.equals("lock") || q.equals("applock") ||
            q.equals("lock settings") || q.equals("applock settings") ||
            q.equals("lock manage") || q.equals("applock manage") ||
            q.equals("lock gui") || q.equals("applock gui") ||
            q.equals("lock config") || q.equals("applock config")) {
            candidates.add(new AppLockSettingsCandidateEntry(rawTrimmed));
            return candidates;
        }

        // 2. "lock status"
        if (q.equals("lock status") || q.equals("applock status")) {
            candidates.add(new AppLockStatusCandidateEntry(enabled, mgr.isLockAllApps(context), mgr.getMasterPin(context), mgr.getMasterPattern(context), lockedApps.size()));
            return candidates;
        }

        // 2. "lock list"
        if (q.equals("lock list") || q.equals("applock list")) {
            candidates.add(new AppLockListCandidateEntry(lockedApps, mgr.isLockAllApps(context)));
            return candidates;
        }

        // 3. "lock all on" / "lock all off"
        if (q.equals("lock all on") || q.equals("applock all on")) {
            candidates.add(new AppLockToggleLockAllCandidateEntry(true));
            return candidates;
        }
        if (q.equals("lock all off") || q.equals("applock all off")) {
            candidates.add(new AppLockToggleLockAllCandidateEntry(false));
            return candidates;
        }

        // 4. "lock on" / "lock off"
        if (q.equals("lock on") || q.equals("applock on")) {
            candidates.add(new AppLockToggleCandidateEntry(true));
            return candidates;
        }
        if (q.equals("lock off") || q.equals("applock off")) {
            candidates.add(new AppLockToggleCandidateEntry(false));
            return candidates;
        }

        // 5. "lock time on" / "lock time off" or "lock master time on" / "lock master time off"
        if (q.equals("lock time on") || q.equals("applock time on") || q.equals("lock master time on") || q.equals("applock master time on")) {
            candidates.add(new AppLockToggleTimeLockCandidateEntry(true));
            return candidates;
        }
        if (q.equals("lock time off") || q.equals("applock time off") || q.equals("lock master time off") || q.equals("applock master time off")) {
            candidates.add(new AppLockToggleTimeLockCandidateEntry(false));
            return candidates;
        }

        // 6. "lock master pattern" or "lock pattern" (without digits -> launches 9-dot drawing dialog)
        if (q.equals("lock master pattern") || q.equals("applock master pattern") || q.equals("lock pattern") || q.equals("applock pattern")) {
            candidates.add(new AppLockDrawMasterPatternCandidateEntry());
            return candidates;
        }

        // 7. "lock master pin" or "lock pin" (without digits -> launches PIN modal)
        if (q.equals("lock master pin") || q.equals("applock master pin") || q.equals("lock pin") || q.equals("applock pin")) {
            candidates.add(new AppLockEnterMasterPinCandidateEntry());
            return candidates;
        }

        // 8. "lock master pin <pin>" or "lock master <pin>"
        Pattern pMasterPin = Pattern.compile("^(?:lock|applock)\\s+master(?:\\s+pin)?\\s+(\\d+)");
        Matcher mMasterPin = pMasterPin.matcher(q);
        if (mMasterPin.find()) {
            String pin = mMasterPin.group(1);
            candidates.add(new AppLockSetMasterPinCandidateEntry(pin));
            return candidates;
        }

        // 9. "lock master pattern <digits>"
        Pattern pMasterPat = Pattern.compile("^(?:lock|applock)\\s+master\\s+pattern\\s+([1-9]+)");
        Matcher mMasterPat = pMasterPat.matcher(q);
        if (mMasterPat.find()) {
            String pattern = mMasterPat.group(1);
            candidates.add(new AppLockSetMasterPatternCandidateEntry(pattern));
            return candidates;
        }

        // 10. "lock whitelist <app>" or "lock exempt <app>"
        Pattern pExempt = Pattern.compile("^(?:lock|applock)\\s+(?:exempt|whitelist)\\s+([a-zA-Z0-9_.-]+)");
        Matcher mExempt = pExempt.matcher(q);
        if (mExempt.find()) {
            String appName = mExempt.group(1);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockExemptCandidateEntry(resolvedPkg, appName, true));
            return candidates;
        }

        // 11. "lock <app> pin <pin>"
        Pattern pPin = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+pin\\s+(\\d+)");
        Matcher mPin = pPin.matcher(q);
        if (mPin.find()) {
            String appName = mPin.group(1);
            String pin = mPin.group(2);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockSetPinCandidateEntry(resolvedPkg, appName, pin));
            return candidates;
        }

        // 12. "lock <app> pattern <digits>"
        Pattern pPat = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+pattern\\s+([1-9]+)");
        Matcher mPat = pPat.matcher(q);
        if (mPat.find()) {
            String appName = mPat.group(1);
            String pattern = mPat.group(2);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockSetPatternCandidateEntry(resolvedPkg, appName, pattern));
            return candidates;
        }

        // 13. "lock <app> remove" or "lock <app> off"
        Pattern pRemove = Pattern.compile("^(?:lock|applock)\\s+([a-zA-Z0-9_.-]+)\\s+(?:remove|off|delete|clear|exempt)");
        Matcher mRemove = pRemove.matcher(q);
        if (mRemove.find()) {
            String appName = mRemove.group(1);
            String resolvedPkg = resolvePackage(context, appName);
            candidates.add(new AppLockRemoveCandidateEntry(resolvedPkg, appName));
            return candidates;
        }

        // 14. "lock <app>" or "lock add <app>"
        Pattern pQuick = Pattern.compile("^(?:lock|applock)\\s+(?:add\\s+)?([a-zA-Z0-9_.-]+)$");
        Matcher mQuick = pQuick.matcher(q);
        if (mQuick.find()) {
            String appName = mQuick.group(1);
            if (!appName.equals("on") && !appName.equals("off") && !appName.equals("all") &&
                !appName.equals("master") && !appName.equals("list") && !appName.equals("status") &&
                !appName.equals("help") && !appName.equals("exempt") && !appName.equals("whitelist") &&
                !appName.equals("settings") && !appName.equals("manage") && !appName.equals("gui") &&
                !appName.equals("pin") && !appName.equals("pattern") && !appName.equals("time")) {

                List<AppMatch> matches = findMatchingApps(context, appName);
                if (!matches.isEmpty()) {
                    for (AppMatch m : matches) {
                        candidates.add(new AppLockAppActionCandidateEntry(m.packageName, m.appName, m.icon));
                    }
                    return candidates;
                }

                String resolvedPkg = resolvePackage(context, appName);
                candidates.add(new AppLockAppActionCandidateEntry(resolvedPkg, appName, null));
                return candidates;
            }
        }

        // 15. Default fallback: open visual settings UI
        candidates.add(new AppLockSettingsCandidateEntry(rawTrimmed));
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

            AppLockManager mgr = AppLockManager.getInstance();
            boolean timeLock = mgr.isTimeLockEnabled(mainActivity);
            String currentMasterPin = mgr.getMasterPin(mainActivity);
            String currentMasterPattern = mgr.getMasterPattern(mainActivity);

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("• Master System: " + (mEnabled ? "ACTIVE" : "DISABLED") + "\n" +
                    "• Lock All Mode: " + (mLockAllApps ? "ACTIVE (All Apps Protected)" : "OFF (Only Configured Apps)") + "\n" +
                    "• Dynamic Time Lock: " + (timeLock ? "ON (Rolling Time Lock)" : "OFF") + "\n" +
                    "• Master PIN: " + currentMasterPin + " | Pattern: " + currentMasterPattern + "\n" +
                    "• Configured Apps: " + mLockedCount + " (Each app has 4-letter T9 lock by default)\n" +
                    "▶ Tap this card or use buttons below:");

            // Buttons row 1: System switches
            LinearLayout btnRow1 = new LinearLayout(mainActivity);
            btnRow1.setOrientation(LinearLayout.HORIZONTAL);
            btnRow1.setPadding(0, 8, 0, 4);

            TextView btnMaster = new TextView(mainActivity);
            btnMaster.setText(mEnabled ? "[Disable Lock]" : "[Enable Lock]");
            btnMaster.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnMaster.setTextColor(mEnabled ? Color.parseColor("#ff5577") : Color.parseColor("#00ff99"));
            btnMaster.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            btnMaster.setPadding(0, 4, 16, 4);
            btnMaster.setOnClickListener(v -> {
                mgr.setMasterEnabled(mainActivity, !mEnabled);
                Toast.makeText(mainActivity, "App Lock " + (!mEnabled ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                mainActivity.changeInputText("lock");
            });

            TextView btnLockAll = new TextView(mainActivity);
            btnLockAll.setText(mLockAllApps ? "[Lock All: OFF]" : "[Lock All: ON]");
            btnLockAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnLockAll.setTextColor(mLockAllApps ? Color.parseColor("#ffaa00") : Color.parseColor("#00f0ff"));
            btnLockAll.setTypeface(Typeface.MONOSPACE);
            btnLockAll.setPadding(0, 4, 16, 4);
            btnLockAll.setOnClickListener(v -> {
                mgr.setLockAllApps(mainActivity, !mLockAllApps);
                Toast.makeText(mainActivity, "Lock All Apps " + (!mLockAllApps ? "ACTIVE" : "OFF"), Toast.LENGTH_SHORT).show();
                mainActivity.changeInputText("lock");
            });

            TextView btnTimeLock = new TextView(mainActivity);
            btnTimeLock.setText(timeLock ? "[Time Lock: OFF]" : "[Time Lock: ON]");
            btnTimeLock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnTimeLock.setTextColor(timeLock ? Color.parseColor("#ff5577") : Color.parseColor("#00f0ff"));
            btnTimeLock.setTypeface(Typeface.MONOSPACE);
            btnTimeLock.setPadding(0, 4, 0, 4);
            btnTimeLock.setOnClickListener(v -> {
                mgr.setTimeLockEnabled(mainActivity, !timeLock);
                Toast.makeText(mainActivity, "Dynamic Time Lock " + (!timeLock ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                mainActivity.changeInputText("lock");
            });

            btnRow1.addView(btnMaster);
            btnRow1.addView(btnLockAll);
            btnRow1.addView(btnTimeLock);

            // Buttons row 2: Settings & Master Dialogs
            LinearLayout btnRow2 = new LinearLayout(mainActivity);
            btnRow2.setOrientation(LinearLayout.HORIZONTAL);
            btnRow2.setPadding(0, 4, 0, 0);

            TextView btnSettings = new TextView(mainActivity);
            btnSettings.setText("[⚙ Manage Apps]");
            btnSettings.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnSettings.setTextColor(Color.parseColor("#00f0ff"));
            btnSettings.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            btnSettings.setPadding(0, 4, 16, 4);
            btnSettings.setOnClickListener(v -> mainActivity.startActivity(new Intent(mainActivity, AppLockSettingsActivity.class)));

            TextView btnSetMasterPin = new TextView(mainActivity);
            btnSetMasterPin.setText("[Set Master PIN]");
            btnSetMasterPin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnSetMasterPin.setTextColor(Color.parseColor("#99d6ea"));
            btnSetMasterPin.setTypeface(Typeface.MONOSPACE);
            btnSetMasterPin.setPadding(0, 4, 16, 4);
            btnSetMasterPin.setOnClickListener(v -> AppLockDialogHelper.showPinDialog(mainActivity, "Set Master PIN", currentMasterPin, pin -> {
                mgr.setMasterPin(mainActivity, pin);
                Toast.makeText(mainActivity, "Master PIN set to: " + pin, Toast.LENGTH_SHORT).show();
                mainActivity.changeInputText("lock");
            }));

            TextView btnDrawMasterPat = new TextView(mainActivity);
            btnDrawMasterPat.setText("[Draw Pattern]");
            btnDrawMasterPat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnDrawMasterPat.setTextColor(Color.parseColor("#99d6ea"));
            btnDrawMasterPat.setTypeface(Typeface.MONOSPACE);
            btnDrawMasterPat.setPadding(0, 4, 0, 4);
            btnDrawMasterPat.setOnClickListener(v -> AppLockDialogHelper.showPatternDialog(mainActivity, "Draw Master Pattern", pat -> {
                mgr.setMasterPattern(mainActivity, pat);
                Toast.makeText(mainActivity, "Master Pattern saved", Toast.LENGTH_SHORT).show();
                mainActivity.changeInputText("lock");
            }));

            btnRow2.addView(btnSettings);
            btnRow2.addView(btnSetMasterPin);
            btnRow2.addView(btnDrawMasterPat);

            layout.addView(header);
            layout.addView(body);
            layout.addView(btnRow1);
            layout.addView(btnRow2);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> activity.startActivity(new Intent(activity, AppLockSettingsActivity.class));
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
            return mEnable ? "⏰ Dynamic Time Lock: ON" : "⏰ Dynamic Time Lock: OFF";
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
                    "▶ Press Enter or tap to enable rolling Time Lock (PIN & Pattern)" :
                    "▶ Press Enter or tap to disable rolling Time Lock");

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

    public static class AppMatch {
        public final String packageName;
        public final String appName;
        public final Drawable icon;

        public AppMatch(String packageName, String appName, Drawable icon) {
            this.packageName = packageName;
            this.appName = appName;
            this.icon = icon;
        }
    }

    public static List<AppMatch> findMatchingApps(Context context, String query) {
        List<AppMatch> matches = new ArrayList<>();
        if (context == null || query == null || query.trim().isEmpty()) return matches;
        String q = query.trim().toLowerCase();

        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

            for (ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    String pkg = ri.activityInfo.packageName;
                    String label = ri.loadLabel(pm).toString();
                    if (label.toLowerCase().contains(q) || pkg.toLowerCase().contains(q)) {
                        boolean already = false;
                        for (AppMatch m : matches) {
                            if (m.packageName.equalsIgnoreCase(pkg)) {
                                already = true;
                                break;
                            }
                        }
                        if (!already) {
                            matches.add(new AppMatch(pkg, label, ri.loadIcon(pm)));
                        }
                        if (matches.size() >= 5) break;
                    }
                }
            }
        } catch (Exception ignored) {}

        return matches;
    }

    public static class AppLockSettingsCandidateEntry implements CandidateEntry {
        private final String mTitle;

        public AppLockSettingsCandidateEntry() {
            this("lock");
        }

        public AppLockSettingsCandidateEntry(String title) {
            this.mTitle = title;
        }

        @NonNull
        @Override
        public String getTitle() {
            return mTitle;
        }

        @Override
        public View getView(MainActivity mainActivity) {
            TextView textView = new TextView(mainActivity);
            textView.setText(mainActivity.getString(R.string.result_app_lock_summary));
            textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return textView;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> activity.startActivityForResult(new Intent(activity, AppLockSettingsActivity.class), MainActivity.REQUEST_CODE_FOR_COMING_BACK);
        }

        @Override
        public boolean hasLongView() { return false; }

        @Override
        public Drawable getIcon(Context context) {
            if (context == null) return null;
            return ContextCompat.getDrawable(context, R.drawable.ic_lock_cyber);
        }

        @Override
        public boolean hasEvent() { return true; }

        @Override
        public boolean isSubItem() { return false; }

        @Override
        public boolean viewIsRecyclable() { return true; }
    }

    public static class AppLockDrawMasterPatternCandidateEntry implements CandidateEntry {
        @Override
        public String getTitle() {
            return "🔲 Draw Master Pattern (9-Dot Grid)";
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
            header.setText("🔲 DRAW MASTER PATTERN");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Tap or press Enter to record Master Pattern using swipe gestures on the 9-dot grid.");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> AppLockDialogHelper.showPatternDialog(activity, "Draw Master Pattern", pattern -> {
                AppLockManager.getInstance().setMasterPattern(activity, pattern);
                Toast.makeText(activity, "Master Pattern updated", Toast.LENGTH_SHORT).show();
            });
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

    public static class AppLockEnterMasterPinCandidateEntry implements CandidateEntry {
        @Override
        public String getTitle() {
            return "🔢 Set Master PIN (Numeric Modal)";
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
            header.setText("🔢 SET MASTER PIN");

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText("▶ Tap or press Enter to set custom Master PIN via numeric modal.");

            layout.addView(header);
            layout.addView(body);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> AppLockDialogHelper.showPinDialog(activity, "Set Master PIN", AppLockManager.getInstance().getMasterPin(activity), pin -> {
                AppLockManager.getInstance().setMasterPin(activity, pin);
                Toast.makeText(activity, "Master PIN updated to: " + pin, Toast.LENGTH_SHORT).show();
            });
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

    public static class AppLockAppActionCandidateEntry implements CandidateEntry {
        private final String mPackageName;
        private final String mAppName;
        private final Drawable mIcon;

        public AppLockAppActionCandidateEntry(String pkg, String appName, Drawable icon) {
            this.mPackageName = pkg;
            this.mAppName = appName;
            this.mIcon = icon;
        }

        @Override
        public String getTitle() {
            AppLockManager mgr = AppLockManager.getInstance();
            boolean isExempt = mgr.isExempt(null, mPackageName);
            AppLockManager.LockedAppConfig cfg = mgr.getLockedAppConfig(null, mPackageName);
            String status = isExempt ? "Exempt" : (cfg != null ? "Custom Lock" : "T9 Protected");
            return "🔒 " + mAppName + " [" + status + "]";
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
            header.setText("🔒 APP SECURITY: " + mAppName.toUpperCase());

            AppLockManager mgr = AppLockManager.getInstance();
            AppLockManager.LockedAppConfig cfg = mgr.getLockedAppConfig(mainActivity, mPackageName);
            boolean isExempt = mgr.isExempt(mainActivity, mPackageName);
            String t9Pin = AppLockManager.getT9PinForPackage(mainActivity, mPackageName);

            StringBuilder sb = new StringBuilder();
            sb.append("• Package: ").append(mPackageName).append("\n");
            if (isExempt) {
                sb.append("• Status: WHITELISTED / EXEMPT (Bypasses Lock All)\n");
            } else if (cfg != null) {
                sb.append("• Status: CUSTOM LOCK ACTIVE\n");
                if (!cfg.pin.isEmpty()) sb.append("  PIN: ").append(cfg.pin).append("\n");
                if (!cfg.pattern.isEmpty()) sb.append("  Pattern: ").append(cfg.pattern).append("\n");
            } else {
                sb.append("• Status: PROTECTED (T9 PIN: ").append(t9Pin).append(")\n");
                sb.append("  Master Rolling Time Lock also valid.\n");
            }

            TextView body = new TextView(mainActivity);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            body.setTextColor(mainActivity.getAccentColor());
            body.setTypeface(Typeface.MONOSPACE);
            body.setText(sb.toString().trim());

            // Interactive action buttons row
            LinearLayout buttonRow = new LinearLayout(mainActivity);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setPadding(0, 6, 0, 0);

            TextView btnPin = new TextView(mainActivity);
            btnPin.setText("[🔢 Set PIN]");
            btnPin.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnPin.setTextColor(Color.parseColor("#00f0ff"));
            btnPin.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            btnPin.setPadding(0, 4, 16, 4);
            btnPin.setOnClickListener(v -> {
                String existingPin = (cfg != null) ? cfg.pin : "";
                AppLockDialogHelper.showPinDialog(mainActivity, "Set PIN for " + mAppName, existingPin, pin -> {
                    String pat = (cfg != null) ? cfg.pattern : "";
                    mgr.setAppLock(mainActivity, mPackageName, pin, pat);
                    Toast.makeText(mainActivity, "Locked " + mAppName + " with PIN: " + pin, Toast.LENGTH_SHORT).show();
                    mainActivity.changeInputText("lock " + mAppName);
                });
            });

            TextView btnPat = new TextView(mainActivity);
            btnPat.setText("[🔲 Draw Pattern]");
            btnPat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnPat.setTextColor(Color.parseColor("#00f0ff"));
            btnPat.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            btnPat.setPadding(0, 4, 16, 4);
            btnPat.setOnClickListener(v -> {
                AppLockDialogHelper.showPatternDialog(mainActivity, "Draw Pattern for " + mAppName, pattern -> {
                    String existingPin = (cfg != null) ? cfg.pin : "";
                    mgr.setAppLock(mainActivity, mPackageName, existingPin, pattern);
                    Toast.makeText(mainActivity, "Pattern saved for " + mAppName, Toast.LENGTH_SHORT).show();
                    mainActivity.changeInputText("lock " + mAppName);
                });
            });

            TextView btnReset = new TextView(mainActivity);
            btnReset.setText("[🔄 Reset T9]");
            btnReset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnReset.setTextColor(Color.parseColor("#ffaa00"));
            btnReset.setTypeface(Typeface.MONOSPACE);
            btnReset.setPadding(0, 4, 16, 4);
            btnReset.setOnClickListener(v -> {
                mgr.removeAppLock(mainActivity, mPackageName);
                Toast.makeText(mainActivity, "Reset " + mAppName + " to T9 default", Toast.LENGTH_SHORT).show();
                mainActivity.changeInputText("lock " + mAppName);
            });

            TextView btnExempt = new TextView(mainActivity);
            btnExempt.setText(isExempt ? "[🛡️ Protect]" : "[🔓 Exempt]");
            btnExempt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btnExempt.setTextColor(isExempt ? Color.parseColor("#00ff99") : Color.parseColor("#ff5577"));
            btnExempt.setTypeface(Typeface.MONOSPACE);
            btnExempt.setPadding(0, 4, 0, 4);
            btnExempt.setOnClickListener(v -> {
                mgr.setExempt(mainActivity, mPackageName, !isExempt);
                Toast.makeText(mainActivity, (!isExempt ? "Exempted " : "Protected ") + mAppName, Toast.LENGTH_SHORT).show();
                mainActivity.changeInputText("lock " + mAppName);
            });

            buttonRow.addView(btnPin);
            buttonRow.addView(btnPat);
            buttonRow.addView(btnReset);
            buttonRow.addView(btnExempt);

            layout.addView(header);
            layout.addView(body);
            layout.addView(buttonRow);
            return layout;
        }

        @Override
        public EventLauncher getEventLauncher(Context context) {
            return activity -> AppLockDialogHelper.showAppActionMenu(activity, mPackageName, mAppName, () -> {
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).changeInputText("lock " + mAppName);
                }
            });
        }

        @Override
        public boolean hasLongView() { return false; }
        @Override
        public Drawable getIcon(Context context) {
            if (mIcon != null) return mIcon;
            if (context != null) {
                try {
                    return context.getPackageManager().getApplicationIcon(mPackageName);
                } catch (Exception ignored) {}
            }
            return null;
        }
        @Override
        public boolean hasEvent() { return true; }
        @Override
        public boolean isSubItem() { return false; }
        @Override
        public boolean viewIsRecyclable() { return true; }
    }
}
