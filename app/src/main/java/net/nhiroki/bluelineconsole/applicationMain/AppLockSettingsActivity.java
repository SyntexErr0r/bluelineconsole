package net.nhiroki.bluelineconsole.applicationMain;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applock.AppLockDialogHelper;
import net.nhiroki.bluelineconsole.applock.AppLockManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppLockSettingsActivity extends BaseWindowActivity {

    public static class AppEntry {
        public final String packageName;
        public final String appName;
        public final Drawable icon;

        public AppEntry(String packageName, String appName, Drawable icon) {
            this.packageName = packageName;
            this.appName = appName;
            this.icon = icon;
        }
    }

    private Switch mSwitchMasterEnable;
    private Switch mSwitchLockAll;
    private Switch mSwitchTimeLock;

    private TextView mTvMasterPinInfo;
    private TextView mTvMasterPatternInfo;

    private EditText mSearchEdit;
    private ListView mAppListView;
    private AppListAdapter mAdapter;

    private final List<AppEntry> mAllApps = new ArrayList<>();
    private final List<AppEntry> mFilteredApps = new ArrayList<>();

    public AppLockSettingsActivity() {
        super(R.layout.applock_settings_activity, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setHeaderFooterTexts("App Lock Settings", null);
        this.setWindowBoundarySize(ROOT_WINDOW_FULL_WIDTH_IN_MOBILE, 3);

        this.changeBaseWindowElementSizeForAnimation(false);
        this.enableBaseWindowAnimation();

        initViews();
        loadAppsAsync();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        this.changeBaseWindowElementSizeForAnimation(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMasterCredentials();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.finish();
    }

    private void initViews() {
        final AppLockManager mgr = AppLockManager.getInstance();

        mSwitchMasterEnable = findViewById(R.id.appLockSwitchMasterEnable);
        mSwitchLockAll = findViewById(R.id.appLockSwitchLockAll);
        mSwitchTimeLock = findViewById(R.id.appLockSwitchTimeLock);

        mSwitchMasterEnable.setChecked(mgr.isMasterEnabled(this));
        mSwitchLockAll.setChecked(mgr.isLockAllApps(this));
        mSwitchTimeLock.setChecked(mgr.isTimeLockEnabled(this));

        mSwitchMasterEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mgr.setMasterEnabled(this, isChecked);
            Toast.makeText(this, "App Lock " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
        });

        mSwitchLockAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mgr.setLockAllApps(this, isChecked);
            Toast.makeText(this, "Lock All Apps " + (isChecked ? "ACTIVE" : "OFF"), Toast.LENGTH_SHORT).show();
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
        });

        mSwitchTimeLock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mgr.setTimeLockEnabled(this, isChecked);
            refreshMasterCredentials();
            Toast.makeText(this, "Dynamic Time Lock " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
            if (mAdapter != null) mAdapter.notifyDataSetChanged();
        });

        mTvMasterPinInfo = findViewById(R.id.appLockMasterPinInfo);
        mTvMasterPatternInfo = findViewById(R.id.appLockMasterPatternInfo);

        TextView btnChangeMasterPin = findViewById(R.id.appLockBtnChangeMasterPin);
        TextView btnResetMasterPin = findViewById(R.id.appLockBtnResetMasterPin);
        TextView btnDrawMasterPattern = findViewById(R.id.appLockBtnDrawMasterPattern);
        TextView btnResetMasterPattern = findViewById(R.id.appLockBtnResetMasterPattern);

        btnChangeMasterPin.setOnClickListener(v -> {
            String current = mgr.getMasterPin(this);
            AppLockDialogHelper.showPinDialog(this, "Set Custom Master PIN", current, pin -> {
                mgr.setMasterPin(this, pin);
                refreshMasterCredentials();
                Toast.makeText(this, "Master PIN updated", Toast.LENGTH_SHORT).show();
            });
        });

        btnResetMasterPin.setOnClickListener(v -> {
            mgr.setMasterPin(this, AppLockManager.DEFAULT_MASTER_PIN);
            refreshMasterCredentials();
            Toast.makeText(this, "Master PIN reset to Dynamic Time Lock", Toast.LENGTH_SHORT).show();
        });

        btnDrawMasterPattern.setOnClickListener(v -> {
            AppLockDialogHelper.showPatternDialog(this, "Draw Custom Master Pattern", pattern -> {
                mgr.setMasterPattern(this, pattern);
                refreshMasterCredentials();
                Toast.makeText(this, "Master Pattern updated", Toast.LENGTH_SHORT).show();
            });
        });

        btnResetMasterPattern.setOnClickListener(v -> {
            mgr.setMasterPattern(this, AppLockManager.DEFAULT_MASTER_PATTERN);
            refreshMasterCredentials();
            Toast.makeText(this, "Master Pattern reset to Dynamic Time Lock", Toast.LENGTH_SHORT).show();
        });

        mSearchEdit = findViewById(R.id.appLockSearchEdit);
        mSearchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        mAppListView = findViewById(R.id.appLockAppListView);
        mAdapter = new AppListAdapter(this, mFilteredApps);
        mAppListView.setAdapter(mAdapter);

        mAppListView.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = mFilteredApps.get(position);
            AppLockDialogHelper.showAppActionMenu(this, entry.packageName, entry.appName, () -> {
                if (mAdapter != null) mAdapter.notifyDataSetChanged();
            });
        });

        refreshMasterCredentials();
    }

    private void refreshMasterCredentials() {
        AppLockManager mgr = AppLockManager.getInstance();
        boolean timeLock = mgr.isTimeLockEnabled(this);

        String masterPin = mgr.getMasterPin(this);
        if (timeLock && masterPin.equals(mgr.getMasterPin(this))) {
            mTvMasterPinInfo.setText("Master PIN: Rolling Time (" + masterPin + ")");
        } else {
            mTvMasterPinInfo.setText("Master PIN: Custom (" + masterPin + ")");
        }

        String masterPattern = mgr.getMasterPattern(this);
        if (timeLock) {
            mTvMasterPatternInfo.setText("Master Pattern: Rolling Time Lock");
        } else {
            mTvMasterPatternInfo.setText("Master Pattern: Custom (" + masterPattern + ")");
        }
    }

    private void loadAppsAsync() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

            List<AppEntry> list = new ArrayList<>();
            for (ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue; // Skip console itself
                    String name = ri.loadLabel(pm).toString();
                    Drawable icon = ri.loadIcon(pm);
                    list.add(new AppEntry(pkg, name, icon));
                }
            }

            Collections.sort(list, Comparator.comparing(a -> a.appName.toLowerCase()));

            runOnUiThread(() -> {
                mAllApps.clear();
                mAllApps.addAll(list);
                filterApps(mSearchEdit.getText().toString());
            });
        }).start();
    }

    private void filterApps(String query) {
        mFilteredApps.clear();
        String q = query.trim().toLowerCase();
        for (AppEntry entry : mAllApps) {
            if (q.isEmpty() || entry.appName.toLowerCase().contains(q) || entry.packageName.toLowerCase().contains(q)) {
                mFilteredApps.add(entry);
            }
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private static class AppListAdapter extends BaseAdapter {
        private final Context mContext;
        private final List<AppEntry> mApps;

        public AppListAdapter(Context context, List<AppEntry> apps) {
            this.mContext = context;
            this.mApps = apps;
        }

        @Override
        public int getCount() {
            return mApps.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return mApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.applock_app_item, parent, false);
            }

            AppEntry item = getItem(position);
            ImageView iconView = convertView.findViewById(R.id.appLockItemIcon);
            TextView titleView = convertView.findViewById(R.id.appLockItemTitle);
            TextView pkgView = convertView.findViewById(R.id.appLockItemPackage);
            TextView statusView = convertView.findViewById(R.id.appLockItemStatus);
            TextView actionBtn = convertView.findViewById(R.id.appLockItemActionBtn);

            iconView.setImageDrawable(item.icon);
            titleView.setText(item.appName);
            pkgView.setText(item.packageName);

            AppLockManager mgr = AppLockManager.getInstance();
            AppLockManager.LockedAppConfig cfg = mgr.getLockedAppConfig(mContext, item.packageName);
            boolean isExempt = mgr.isExempt(mContext, item.packageName);
            boolean isHome = mgr.isHomeLauncher(mContext, item.packageName);
            String t9Pin = AppLockManager.getT9PinForPackage(mContext, item.packageName);

            if (isHome) {
                statusView.setText("🛡️ Home Launcher (Always Unlocked)");
                statusView.setTextColor(0xFF4D7A94);
            } else if (isExempt) {
                statusView.setText("🔓 Whitelisted / Exempt from Lock");
                statusView.setTextColor(0xFFFF5577);
            } else if (cfg != null) {
                StringBuilder sb = new StringBuilder("🔒 Custom Lock: ");
                if (!cfg.pin.isEmpty()) sb.append("PIN: ").append(cfg.pin);
                if (!cfg.pattern.isEmpty()) {
                    if (!cfg.pin.isEmpty()) sb.append(" | ");
                    sb.append("Pattern: ").append(cfg.pattern);
                }
                statusView.setText(sb.toString());
                statusView.setTextColor(0xFF00FF99);
            } else {
                statusView.setText("🔒 T9 PIN: " + t9Pin + " (Universal Time Master active)");
                statusView.setTextColor(0xFF00F0FF);
            }

            actionBtn.setOnClickListener(v -> AppLockDialogHelper.showAppActionMenu(mContext, item.packageName, item.appName, this::notifyDataSetChanged));

            return convertView;
        }
    }
}
