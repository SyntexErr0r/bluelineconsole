package net.nhiroki.bluelineconsole.applock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.nhiroki.bluelineconsole.agent.BlueLineAgentService;
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;

public class AppLockActivity extends Activity {
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    private String mPackageName = "";
    private AppLockManager.LockedAppConfig mConfig;
    private boolean mIsUnlocked = false;
    private int mFailedAttempts = 0;
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private boolean mIsPinMode = false; // Default to Pattern mode or PIN mode based on config

    // UI elements
    private LinearLayout mRootLayout;
    private ImageView mAppIconView;
    private TextView mAppTitleView;
    private TextView mStatusTextView;
    private TextView mToggleModeButton;

    // PIN UI
    private LinearLayout mPinContainer;
    private TextView mPinDotsView;
    private final StringBuilder mEnteredPin = new StringBuilder();

    // Pattern UI
    private LinearLayout mPatternContainer;
    private PatternLockView mPatternLockView;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FLAG_SECURE: Prevents screenshots and forces blank/redacted snapshot in Android Recents
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        mPackageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        if (mPackageName == null || mPackageName.isEmpty()) {
            finish();
            return;
        }

        mConfig = AppLockManager.getInstance().getLockedAppConfig(this, mPackageName);
        if (mConfig == null || !mConfig.enabled) {
            // Not locked
            mIsUnlocked = true;
            finish();
            return;
        }

        // If app is already unlocked in current session, dismiss immediately
        if (AppLockManager.getInstance().isAppUnlockedForSession(mPackageName)) {
            mIsUnlocked = true;
            finish();
            return;
        }

        buildUI();
    }

    private void buildUI() {
        mRootLayout = new LinearLayout(this);
        mRootLayout.setOrientation(LinearLayout.VERTICAL);
        mRootLayout.setBackgroundColor(Color.parseColor("#090d16")); // Cyber dark opaque background
        mRootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mRootLayout.setPadding(32, 48, 32, 48);

        // 1. Cyber Header
        TextView protocolHeader = new TextView(this);
        protocolHeader.setText("🔒 BLUELINE SECURITY PROTOCOL");
        protocolHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        protocolHeader.setTextColor(Color.parseColor("#00f0ff"));
        protocolHeader.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        protocolHeader.setGravity(Gravity.CENTER);
        protocolHeader.setPadding(0, 16, 0, 8);
        mRootLayout.addView(protocolHeader);

        // 2. App Icon & Name
        LinearLayout appInfoLayout = new LinearLayout(this);
        appInfoLayout.setOrientation(LinearLayout.HORIZONTAL);
        appInfoLayout.setGravity(Gravity.CENTER);
        appInfoLayout.setPadding(0, 16, 0, 16);

        mAppIconView = new ImageView(this);
        int iconSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMargins(0, 0, 16, 0);
        mAppIconView.setLayoutParams(iconParams);

        String appLabel = mPackageName;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(mPackageName, 0);
            Drawable icon = pm.getApplicationIcon(ai);
            mAppIconView.setImageDrawable(icon);
            appLabel = pm.getApplicationLabel(ai).toString();
        } catch (Exception ignored) {}

        mAppTitleView = new TextView(this);
        mAppTitleView.setText(appLabel + " is Locked");
        mAppTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        mAppTitleView.setTextColor(Color.WHITE);
        mAppTitleView.setTypeface(Typeface.DEFAULT_BOLD);

        appInfoLayout.addView(mAppIconView);
        appInfoLayout.addView(mAppTitleView);
        mRootLayout.addView(appInfoLayout);

        // 3. Status prompt
        mStatusTextView = new TextView(this);
        mStatusTextView.setText("Draw pattern or enter PIN to unlock");
        mStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mStatusTextView.setTextColor(Color.parseColor("#80ffffff"));
        mStatusTextView.setTypeface(Typeface.MONOSPACE);
        mStatusTextView.setGravity(Gravity.CENTER);
        mStatusTextView.setPadding(0, 4, 0, 16);
        mRootLayout.addView(mStatusTextView);

        // 4. Pattern Container
        mPatternContainer = new LinearLayout(this);
        mPatternContainer.setOrientation(LinearLayout.VERTICAL);
        mPatternContainer.setGravity(Gravity.CENTER);

        mPatternLockView = new PatternLockView(this);
        int patternSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 280, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams patternParams = new LinearLayout.LayoutParams(patternSize, patternSize);
        mPatternLockView.setLayoutParams(patternParams);
        mPatternLockView.setOnPatternListener(this::onPatternEntered);
        mPatternContainer.addView(mPatternLockView);
        mRootLayout.addView(mPatternContainer);

        // 5. PIN Container
        mPinContainer = new LinearLayout(this);
        mPinContainer.setOrientation(LinearLayout.VERTICAL);
        mPinContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        mPinContainer.setVisibility(View.GONE);

        mPinDotsView = new TextView(this);
        mPinDotsView.setText("○  ○  ○  ○");
        mPinDotsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mPinDotsView.setTextColor(Color.parseColor("#00f0ff"));
        mPinDotsView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        mPinDotsView.setGravity(Gravity.CENTER);
        mPinDotsView.setPadding(0, 12, 0, 24);
        mPinContainer.addView(mPinDotsView);

        View pinPad = buildPinPad();
        mPinContainer.addView(pinPad);
        mRootLayout.addView(mPinContainer);

        // 6. Mode Switcher (Pattern <-> PIN)
        mToggleModeButton = new TextView(this);
        mToggleModeButton.setText("[ 🔢 SWITCH TO PIN ]");
        mToggleModeButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        mToggleModeButton.setTextColor(Color.parseColor("#00f0ff"));
        mToggleModeButton.setTypeface(Typeface.MONOSPACE);
        mToggleModeButton.setGravity(Gravity.CENTER);
        mToggleModeButton.setPadding(16, 24, 16, 16);
        mToggleModeButton.setOnClickListener(v -> toggleMode());
        mRootLayout.addView(mToggleModeButton);

        // 7. Cancel & Exit Button
        TextView exitButton = new TextView(this);
        exitButton.setText("[ ✕ CANCEL & EXIT ]");
        exitButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        exitButton.setTextColor(Color.parseColor("#ff5577"));
        exitButton.setTypeface(Typeface.MONOSPACE);
        exitButton.setGravity(Gravity.CENTER);
        exitButton.setPadding(16, 8, 16, 16);
        exitButton.setOnClickListener(v -> ejectToHome());
        mRootLayout.addView(exitButton);

        setContentView(mRootLayout);
    }

    private View buildPinPad() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        String[] keys = {
                "1", "2", "3",
                "4", "5", "6",
                "7", "8", "9",
                "C", "0", "⌫"
        };

        int btnWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72, getResources().getDisplayMetrics());
        int btnHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 54, getResources().getDisplayMetrics());

        for (String k : keys) {
            Button btn = new Button(this);
            btn.setText(k);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            btn.setTextColor(Color.WHITE);
            btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            btn.setBackgroundColor(Color.parseColor("#152033"));

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = btnWidth;
            params.height = btnHeight;
            params.setMargins(8, 8, 8, 8);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> onKeypadClick(k));
            grid.addView(btn);
        }

        return grid;
    }

    private void onKeypadClick(String key) {
        if ("C".equals(key)) {
            mEnteredPin.setLength(0);
            updatePinDots();
        } else if ("⌫".equals(key)) {
            if (mEnteredPin.length() > 0) {
                mEnteredPin.deleteCharAt(mEnteredPin.length() - 1);
                updatePinDots();
            }
        } else {
            if (mEnteredPin.length() < 8) {
                mEnteredPin.append(key);
                updatePinDots();
            }

            int expectedLen = (mConfig.pin != null && !mConfig.pin.isEmpty()) ? mConfig.pin.length() : 4;
            if (mEnteredPin.length() >= expectedLen) {
                verifyPin(mEnteredPin.toString());
            }
        }
    }

    private void updatePinDots() {
        int expectedLen = (mConfig.pin != null && !mConfig.pin.isEmpty()) ? mConfig.pin.length() : 4;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expectedLen; i++) {
            if (i < mEnteredPin.length()) {
                sb.append("●  ");
            } else {
                sb.append("○  ");
            }
        }
        mPinDotsView.setText(sb.toString().trim());
    }

    private void toggleMode() {
        mIsPinMode = !mIsPinMode;
        if (mIsPinMode) {
            mPatternContainer.setVisibility(View.GONE);
            mPinContainer.setVisibility(View.VISIBLE);
            mToggleModeButton.setText("[ 💠 SWITCH TO PATTERN ]");
            mStatusTextView.setText("Enter PIN code for " + mAppTitleView.getText());
            mEnteredPin.setLength(0);
            updatePinDots();
        } else {
            mPatternContainer.setVisibility(View.VISIBLE);
            mPinContainer.setVisibility(View.GONE);
            mToggleModeButton.setText("[ 🔢 SWITCH TO PIN ]");
            mStatusTextView.setText("Draw pattern for " + mAppTitleView.getText());
            mPatternLockView.clearPattern();
        }
    }

    private void onPatternEntered(String patternDigits) {
        AppLogger.i("APPLOCK", "Pattern entered: " + patternDigits);
        if (mConfig == null) return;

        boolean matches = patternDigits.equals(mConfig.pattern);
        if (matches) {
            mPatternLockView.showSuccess();
            onUnlockSuccess();
        } else {
            mPatternLockView.showError();
            onUnlockFailed("Incorrect pattern");
        }
    }

    private void verifyPin(String pin) {
        AppLogger.i("APPLOCK", "PIN entered length: " + pin.length());
        if (mConfig == null) return;

        boolean matches = pin.equals(mConfig.pin);
        if (matches) {
            mPinDotsView.setTextColor(Color.parseColor("#00ff99"));
            onUnlockSuccess();
        } else {
            mPinDotsView.setTextColor(Color.parseColor("#ff0055"));
            // Shake PIN dots
            ValueAnimator shake = ValueAnimator.ofFloat(0, 16, -16, 12, -12, 6, -6, 0);
            shake.setDuration(400);
            shake.addUpdateListener(anim -> mPinDotsView.setTranslationX((float) anim.getAnimatedValue()));
            shake.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mEnteredPin.setLength(0);
                    mPinDotsView.setTextColor(Color.parseColor("#00f0ff"));
                    updatePinDots();
                }
            });
            shake.start();
            onUnlockFailed("Incorrect PIN");
        }
    }

    private void onUnlockSuccess() {
        mIsUnlocked = true;
        mStatusTextView.setText("ACCESS GRANTED");
        mStatusTextView.setTextColor(Color.parseColor("#00ff99"));

        AppLockManager.getInstance().unlockAppSession(mPackageName);

        mHandler.postDelayed(() -> {
            finish();
            overridePendingTransition(0, 0);
        }, 200);
    }

    private void onUnlockFailed(String reason) {
        mFailedAttempts++;
        int remaining = MAX_FAILED_ATTEMPTS - mFailedAttempts;

        if (mFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            mStatusTextView.setText("❌ ACCESS DENIED. Maximum attempts exceeded. Ejecting...");
            mStatusTextView.setTextColor(Color.parseColor("#ff0055"));
            if (mPatternLockView != null) mPatternLockView.setInputEnabled(false);

            mHandler.postDelayed(this::ejectToHome, 600);
        } else {
            mStatusTextView.setText("❌ " + reason + " (" + remaining + " attempts left)");
            mStatusTextView.setTextColor(Color.parseColor("#ff0055"));
        }
    }

    private void ejectToHome() {
        mIsUnlocked = false;
        AppLogger.w("APPLOCK", "Ejecting to Home screen (anti-bypass)");
        if (BlueLineAgentService.getInstance() != null) {
            BlueLineAgentService.getInstance().pressHome();
        } else {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        // Strict anti-bypass: Back button immediately sends intruder to Home screen
        ejectToHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If activity loses focus without being unlocked (swiping up, recents, split screen), force Home!
        if (!mIsUnlocked) {
            AppLogger.w("APPLOCK", "AppLockActivity paused while locked. Enforcing Home eject.");
            if (BlueLineAgentService.getInstance() != null) {
                BlueLineAgentService.getInstance().pressHome();
            }
        }
    }
}
