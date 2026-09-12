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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.os.Build;
import android.widget.ProgressBar;
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

    private TextView mTvTopStatusSummary;
    private EditText mSearchEdit;
    private ListView mAppListView;
    private AppListAdapter mAdapter;
    private View mFastScrollContainer;
    private View mFastScrollThumb;
    private View mLoadingContainer;
    private boolean mIsDraggingFastScroll = false;

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
        if (hasFocus && mAppListView != null) {
            mAppListView.post(this::refreshFastScrollState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTopSummary();
        if (mAppListView != null) {
            mAppListView.post(this::refreshFastScrollState);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.finish();
    }

    private void initViews() {
        View btnOpenSettings = findViewById(R.id.appLockBtnOpenSettings);
        btnOpenSettings.setOnClickListener(v -> AppLockDialogHelper.showGlobalSettingsDialog(this, this::refreshTopSummary));

        mTvTopStatusSummary = findViewById(R.id.appLockTopStatusSummary);

        mLoadingContainer = findViewById(R.id.appLockLoadingContainer);
        ProgressBar pb = findViewById(R.id.appLockLoadingProgress);
        if (pb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pb.setIndeterminateTintList(ColorStateList.valueOf(0xff00f0ff));
            pb.setIndeterminateTintMode(PorterDuff.Mode.SRC_IN);
        }

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

        mFastScrollContainer = findViewById(R.id.appLockFastScrollContainer);
        mFastScrollThumb = findViewById(R.id.appLockFastScrollThumb);

        if (mFastScrollContainer != null && mFastScrollThumb != null) {
            mFastScrollContainer.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_MOVE:
                        mIsDraggingFastScroll = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        handleFastScrollDrag(event.getY());
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mIsDraggingFastScroll = false;
                        return true;
                }
                return false;
            });

            // Layout listener to ensure thumb updates immediately on layout settlement or window expansion
            mFastScrollContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom - top > 0 && !mAllApps.isEmpty()) {
                    refreshFastScrollState();
                }
            });

            mAppListView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (bottom - top > 0 && !mAllApps.isEmpty()) {
                    refreshFastScrollState();
                }
            });

            mAppListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {}

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (!mIsDraggingFastScroll) {
                        updateFastScrollThumbPosition(firstVisibleItem, visibleItemCount, totalItemCount);
                    }
                }
            });
        }

        refreshTopSummary();
    }

    private void handleFastScrollDrag(float touchY) {
        if (mAdapter == null || mAdapter.getCount() == 0 || mFastScrollContainer == null || mFastScrollThumb == null) return;
        int containerHeight = mFastScrollContainer.getHeight();
        int thumbHeight = mFastScrollThumb.getHeight();
        if (thumbHeight <= 0) {
            thumbHeight = (int) (44 * getResources().getDisplayMetrics().density);
        }
        if (containerHeight <= thumbHeight) return;

        float maxTranslation = containerHeight - thumbHeight;
        float clampedY = Math.max(0, Math.min(touchY - (thumbHeight / 2f), maxTranslation));
        mFastScrollThumb.setTranslationY(clampedY);

        float fraction = clampedY / maxTranslation;
        int targetPosition = (int) (fraction * (mAdapter.getCount() - 1));
        targetPosition = Math.max(0, Math.min(targetPosition, mAdapter.getCount() - 1));
        mAppListView.setSelectionFromTop(targetPosition, 0);
    }

    private void updateFastScrollThumbPosition(int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mFastScrollContainer == null || mFastScrollThumb == null) return;

        // Keep hidden while apps are loading in background or if empty
        if (totalItemCount == 0 || mAllApps.isEmpty() || (mLoadingContainer != null && mLoadingContainer.getVisibility() == View.VISIBLE)) {
            mFastScrollContainer.setVisibility(View.GONE);
            return;
        }

        boolean isFiltered = mSearchEdit != null && !mSearchEdit.getText().toString().trim().isEmpty();
        if (isFiltered && visibleItemCount >= totalItemCount) {
            mFastScrollContainer.setVisibility(View.GONE);
            return;
        }

        mFastScrollContainer.setVisibility(View.VISIBLE);

        int containerHeight = mFastScrollContainer.getHeight();
        int thumbHeight = mFastScrollThumb.getHeight();
        if (thumbHeight <= 0) {
            thumbHeight = (int) (44 * getResources().getDisplayMetrics().density);
        }
        if (containerHeight <= thumbHeight) {
            mFastScrollContainer.post(() -> {
                if (mFastScrollContainer != null && mFastScrollThumb != null) {
                    int cHeight = mFastScrollContainer.getHeight();
                    int tHeight = mFastScrollThumb.getHeight();
                    if (tHeight <= 0) tHeight = (int) (44 * getResources().getDisplayMetrics().density);
                    if (cHeight > tHeight) {
                        float maxTrans = cHeight - tHeight;
                        float frac = (float) firstVisibleItem / Math.max(1, totalItemCount - Math.max(1, visibleItemCount));
                        frac = Math.max(0f, Math.min(1f, frac));
                        mFastScrollThumb.setTranslationY(frac * maxTrans);
                    }
                }
            });
            return;
        }

        float maxTranslation = containerHeight - thumbHeight;
        float fraction = (float) firstVisibleItem / Math.max(1, totalItemCount - Math.max(1, visibleItemCount));
        fraction = Math.max(0f, Math.min(1f, fraction));
        mFastScrollThumb.setTranslationY(fraction * maxTranslation);
    }

    private void refreshFastScrollState() {
        if (mFastScrollContainer == null || mFastScrollThumb == null || mAppListView == null || mAdapter == null) return;
        int totalCount = mAdapter.getCount();

        // Keep hidden while apps are loading in background or if empty
        if (totalCount == 0 || mAllApps.isEmpty() || (mLoadingContainer != null && mLoadingContainer.getVisibility() == View.VISIBLE)) {
            mFastScrollContainer.setVisibility(View.GONE);
            return;
        }

        boolean isFiltered = mSearchEdit != null && !mSearchEdit.getText().toString().trim().isEmpty();
        int firstVisible = mAppListView.getFirstVisiblePosition();
        int lastVisible = mAppListView.getLastVisiblePosition();
        int visibleCount = (lastVisible >= firstVisible && firstVisible >= 0) ? (lastVisible - firstVisible + 1) : 0;

        if (isFiltered && visibleCount >= totalCount) {
            mFastScrollContainer.setVisibility(View.GONE);
            return;
        }

        mFastScrollContainer.setVisibility(View.VISIBLE);
        updateFastScrollThumbPosition(firstVisible, visibleCount > 0 ? visibleCount : 1, totalCount);
    }

    private void refreshTopSummary() {
        AppLockManager mgr = AppLockManager.getInstance();
        boolean master = mgr.isMasterEnabled(this);
        boolean lockAll = mgr.isLockAllApps(this);
        boolean time = mgr.isTimeLockEnabled(this);

        if (mTvTopStatusSummary != null) {
            mTvTopStatusSummary.setText((master ? "Lock: ON" : "Lock: OFF") +
                    " | " + (lockAll ? "All: ON" : "All: OFF") +
                    " | " + (time ? "Time: ON" : "Time: OFF"));
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
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
                if (mLoadingContainer != null) {
                    mLoadingContainer.setVisibility(View.GONE);
                }
                if (mAppListView != null) {
                    mAppListView.setVisibility(View.VISIBLE);
                }
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
            refreshFastScrollState();
            if (mAppListView != null) {
                mAppListView.post(this::refreshFastScrollState);
            }
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
            TextView statusView = convertView.findViewById(R.id.appLockItemStatus);
            ImageView btnCustom = convertView.findViewById(R.id.appLockItemBtnCustom);
            ImageView btnLockToggle = convertView.findViewById(R.id.appLockItemBtnLockToggle);

            iconView.setImageDrawable(item.icon);
            titleView.setText(item.appName);

            AppLockManager mgr = AppLockManager.getInstance();
            AppLockManager.LockedAppConfig cfg = mgr.getLockedAppConfig(mContext, item.packageName);
            boolean isExempt = mgr.isExempt(mContext, item.packageName);
            boolean isHome = mgr.isHomeLauncher(mContext, item.packageName);
            String t9Pin = AppLockManager.getT9PinForPackage(mContext, item.packageName);

            boolean isLocked = !isHome && !isExempt;

            if (isHome) {
                statusView.setText("🛡️ Home Launcher (Always Unlocked)");
                statusView.setTextColor(0xFF4D7A94);
                btnLockToggle.setImageResource(R.drawable.ic_unlock_cyber);
                btnLockToggle.setEnabled(false);
            } else if (isExempt) {
                statusView.setText("🔓 Unlocked (Exempt from Lock)");
                statusView.setTextColor(0xFFFF5577);
                btnLockToggle.setImageResource(R.drawable.ic_unlock_cyber);
                btnLockToggle.setEnabled(true);
                btnLockToggle.setOnClickListener(v -> {
                    mgr.setExempt(mContext, item.packageName, false);
                    Toast.makeText(mContext, item.appName + " is now LOCKED", Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                });
            } else if (cfg != null) {
                StringBuilder sb = new StringBuilder("🔒 Custom: ");
                if (!cfg.pin.isEmpty()) sb.append("PIN: ").append(cfg.pin);
                if (!cfg.pattern.isEmpty()) {
                    if (!cfg.pin.isEmpty()) sb.append(" | ");
                    sb.append("Pattern Set");
                }
                statusView.setText(sb.toString());
                statusView.setTextColor(0xFF00FF99);
                btnLockToggle.setImageResource(R.drawable.ic_lock_cyber);
                btnLockToggle.setEnabled(true);
                btnLockToggle.setOnClickListener(v -> {
                    mgr.setExempt(mContext, item.packageName, true);
                    Toast.makeText(mContext, item.appName + " is now UNLOCKED (Exempt)", Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                });
            } else {
                statusView.setText("🔒 Locked (T9 PIN: " + t9Pin + ")");
                statusView.setTextColor(0xFF00F0FF);
                btnLockToggle.setImageResource(R.drawable.ic_lock_cyber);
                btnLockToggle.setEnabled(true);
                btnLockToggle.setOnClickListener(v -> {
                    mgr.setExempt(mContext, item.packageName, true);
                    Toast.makeText(mContext, item.appName + " is now UNLOCKED (Exempt)", Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                });
            }

            btnCustom.setOnClickListener(v -> AppLockDialogHelper.showAppActionMenu(mContext, item.packageName, item.appName, this::notifyDataSetChanged));

            return convertView;
        }
    }
}
