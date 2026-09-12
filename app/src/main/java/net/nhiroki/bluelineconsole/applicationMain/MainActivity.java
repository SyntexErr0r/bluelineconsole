package net.nhiroki.bluelineconsole.applicationMain;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.agent.BlueLineAgentService;
import net.nhiroki.bluelineconsole.applock.AppLockManager;
import net.nhiroki.bluelineconsole.applock.PatternLockView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import net.nhiroki.bluelineconsole.BuildConfig;
import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.lib.EditTextConfigurations;
import net.nhiroki.bluelineconsole.commandSearchers.CommandSearchAggregator;
import net.nhiroki.bluelineconsole.commands.logs.AppLogger;
import net.nhiroki.bluelineconsole.dataStore.deviceLocal.WidgetsSetting;
import net.nhiroki.bluelineconsole.interfaces.CandidateEntry;


public class MainActivity extends BaseWindowActivity {
    private CandidateListAdapter resultCandidateListAdapter;
    private CommandSearchAggregator commandSearchAggregator = null;
    private ExecutorService threadPool = null;

    public static final int REQUEST_CODE_FOR_COMING_BACK = 1;
    public static final int REQUEST_CODE_FOR_SCREEN_CAPTURE = 99;
    private Runnable pendingScreenCaptureCallback = null;

    private boolean cameBackFlag = false;
    private boolean comingBackFlag = false;

    private boolean showStartUpHelp = false;
    private boolean migrationLostHappened = false;

    private boolean homeItemExists = false;

    private EditText mainInputText;
    private ListView candidateListView;

    private int resumeId = 0;

    private boolean temporaryContentShown = false;

    private static MainActivity myActiveInstance = null;
    private final Handler lockoutHandler = new Handler(Looper.getMainLooper());
    private Runnable lockoutRunnable = null;
    private boolean biometricPromptShowing = false;


    public static final String ACTION_UNLOCK_APP = "net.nhiroki.bluelineconsole.action.UNLOCK_APP";
    public static final String EXTRA_UNLOCK_PACKAGE = "net.nhiroki.bluelineconsole.extra.UNLOCK_PACKAGE";

    private String mTargetLockedPackage = null;
    private String mTargetLockedAppName = null;
    private boolean mIsAppUnlockMode = false;
    private int mAppUnlockFailedAttempts = 0;

    public MainActivity() {
        super(R.layout.main_activity_body, true);
    }

    public static void setIsComingBack(boolean flag) {
        if (myActiveInstance != null) {
            myActiveInstance.comingBackFlag = flag;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        this.setIntent(intent);
        this.handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_UNLOCK_APP.equals(action) || intent.hasExtra(EXTRA_UNLOCK_PACKAGE)) {
            String targetPkg = intent.getStringExtra(EXTRA_UNLOCK_PACKAGE);
            if (targetPkg != null && !targetPkg.isEmpty()) {
                setupAppUnlockMode(targetPkg);
                return;
            }
        }
        String search = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (search != null) {
            mainInputText.setText(search);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!this.iAmHomeActivity) {
            MainActivity.myActiveInstance = this;
        }

        this.mainInputText = findViewById(R.id.mainInputText);

        this.migrationLostHappened = WidgetsSetting.migrationLostHappened(this);
        this.showStartUpHelp = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(StartUpHelpActivity.PREF_KEY_SHOW_STARTUP_HELP, true);


        this.setHeaderFooterTexts(getString(R.string.app_name), String.format(getString(R.string.displayedFullVersionString), BuildConfig.VERSION_NAME));

        AppNotification.update(this);

        this.candidateListView = findViewById(R.id.candidateListView);
        resultCandidateListAdapter = new CandidateListAdapter(this, new ArrayList<>(), candidateListView);

        candidateListView.setAdapter(resultCandidateListAdapter);

        candidateListView.setOnItemClickListener((parent, view, position, id) -> resultCandidateListAdapter.invokeEvent(position, MainActivity.this));

        candidateListView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && v.onKeyDown(keyCode, event)) {
                return true;
            }

            //noinspection RedundantIfStatement
            if (event.getAction() == KeyEvent.ACTION_UP && v.onKeyUp(keyCode, event)) {
                return true;
            }

            return false;
        });

        EditTextConfigurations.applyCommandEditTextConfigurations(mainInputText, this);
        mainInputText.requestFocus();
        mainInputText.requestFocusFromTouch();

        this.handleIncomingIntent(this.getIntent());

        mainInputText.setOnEditorActionListener((v, actionId, event) -> {
            if (this.mIsAppUnlockMode) {
                validateUnlockInput(mainInputText.getText().toString().trim(), true);
                return true;
            }
            if (resultCandidateListAdapter.isEmpty()) {
                return false;
            }
            if (event == null || event.getAction() != KeyEvent.ACTION_UP) {
                resultCandidateListAdapter.invokeFirstChoiceEvent(MainActivity.this);
                return true;
            }
            return false;
        });

        mainInputText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN){
                candidateListView.requestFocus();
                candidateListView.requestFocusFromTouch();
                return MainActivity.this.resultCandidateListAdapter.selectChosenNowAsListView() && candidateListView.onKeyDown(keyCode, event);
            }
            return false;
        });

        this.changeBaseWindowElementSizeForAnimation(false);
        this.enableBaseWindowAnimation();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            this.changeBaseWindowElementSizeForAnimation(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (this.commandSearchAggregator != null) {
            this.commandSearchAggregator.close();
        }
        if (!this.iAmHomeActivity) {
            MainActivity.myActiveInstance = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (commandSearchAggregator == null) {
            commandSearchAggregator = new CommandSearchAggregator(this);
            mainInputText.addTextChangedListener(new MainInputTextListener(mainInputText.getText()));
        }

        net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.onAppResume(this);

        if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLocked(this)) {
            this.updateAppLockUI();
            this.enableBaseWindowAnimation();
            ++this.resumeId;
            this.comingBackFlag = false;
            this.tryTriggerBiometricUnlock();
            return;
        }

        if (this.mIsAppUnlockMode) {
            this.setWholeLayout();
            this.enableBaseWindowAnimation();
            return;
        }

        this.completeResumeSetup();
        this.updateAppLockUI();
    }

    private void completeResumeSetup() {
        if (this.mIsAppUnlockMode) {
            return;
        }
        AppLogger.d("LIFECYCLE", "MainActivity resumed (home=" + this.iAmHomeActivity + ", cameBack=" + cameBackFlag + ")");
        resultCandidateListAdapter.setShowIcons(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_appearance_show_icons", true));

        ++this.resumeId;
        this.comingBackFlag = false;

        EditTextConfigurations.applyCommandEditTextConfigurations(mainInputText, this);

        if (!this.themeStateMatchesConfig()) {
            this.finish();
            this.startActivity(new Intent(this, this.getClass()));
            return;
        }

        threadPool = Executors.newSingleThreadExecutor();

        if (!cameBackFlag) {
            if (this.iAmHomeActivity) {
                if (! mainInputText.getText().toString().isEmpty()) {
                    this.changeInputText("");
                }

            } else {
                mainInputText.setText("");

                if (!this.iAmHomeActivity) {
                    resultCandidateListAdapter.clear();
                    resultCandidateListAdapter.notifyDataSetChanged();
                }
            }
        }
        // Refresh after searching temporary list
        commandSearchAggregator.refresh(this);

        if (this.showStartUpHelp) {
            this.showStartUpHelp = false;
            this.cameBackFlag = true;
            this.comingBackFlag = true;
            startActivityForResult(new Intent(MainActivity.this, StartUpHelpActivity.class), MainActivity.REQUEST_CODE_FOR_COMING_BACK);
            return;
        }

        if (this.migrationLostHappened) {
            this.migrationLostHappened = false;
            this.cameBackFlag = true;
            this.comingBackFlag = true;
            startActivityForResult(new Intent(MainActivity.this, NotificationMigrationLostActivity.class), MainActivity.REQUEST_CODE_FOR_COMING_BACK);
            return;
        }

        final CharSequence currentMainInputTextContents = mainInputText.getText();
        if (this.cameBackFlag || this.iAmHomeActivity || ! currentMainInputTextContents.toString().isEmpty()) {
            this.onCommandInput(currentMainInputTextContents);
        }

        this.cameBackFlag = false;

        MainActivity.this.enableBaseWindowAnimation();
    }

    private void updateAppLockUI() {
        if (lockoutRunnable != null) {
            lockoutHandler.removeCallbacks(lockoutRunnable);
            lockoutRunnable = null;
        }

        if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLocked(this)) {
            findViewById(R.id.candidateViewWrapperLinearLayout).setVisibility(View.GONE);
            mainInputText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);

            if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut()) {
                mainInputText.setEnabled(false);
                long remaining = net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getRemainingLockoutSeconds();
                mainInputText.setHint(String.format(getString(R.string.app_lock_locked_out), remaining));
                mainInputText.setText("");

                lockoutRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (MainActivity.this.isFinishing()) return;
                        if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut()) {
                            long rem = net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getRemainingLockoutSeconds();
                            mainInputText.setHint(String.format(getString(R.string.app_lock_locked_out), rem));
                            lockoutHandler.postDelayed(this, 1000);
                        } else {
                            mainInputText.setEnabled(true);
                            updateAppLockUI();
                            tryTriggerBiometricUnlock();
                        }
                    }
                };
                lockoutHandler.postDelayed(lockoutRunnable, 1000);
            } else {
                mainInputText.setEnabled(true);
                int failed = net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getFailedAttempts();
                if (failed > 0) {
                    mainInputText.setHint(String.format(getString(R.string.app_lock_incorrect_pin), failed, net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.getMaxFailedAttempts()));
                } else {
                    mainInputText.setHint("Enter PIN...");
                }
                mainInputText.setText("");
            }
        } else {
            mainInputText.setEnabled(true);
            mainInputText.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            mainInputText.setHint(null);
            findViewById(R.id.candidateViewWrapperLinearLayout).setVisibility(View.VISIBLE);
        }
    }

    public void requestScreenCapture(Runnable onGranted) {
        this.pendingScreenCaptureCallback = onGranted;
        android.media.projection.MediaProjectionManager mgr =
                (android.media.projection.MediaProjectionManager) getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
        if (mgr != null) {
            startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CODE_FOR_SCREEN_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FOR_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                net.nhiroki.bluelineconsole.applicationMain.lib.ScreenCaptureHelper.setProjectionResult(resultCode, data);
                if (this.pendingScreenCaptureCallback != null) {
                    Runnable cb = this.pendingScreenCaptureCallback;
                    this.pendingScreenCaptureCallback = null;
                    cb.run();
                }
            } else {
                android.widget.Toast.makeText(this, "Screen capture permission was declined.", android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }
        this.cameBackFlag = (requestCode == REQUEST_CODE_FOR_COMING_BACK) && (resultCode == RESULT_OK);
    }

    @Override
    protected void onPause() {
        ++this.resumeId;
        if (lockoutRunnable != null) {
            lockoutHandler.removeCallbacks(lockoutRunnable);
            lockoutRunnable = null;
        }
        biometricPromptShowing = false;
        if (threadPool != null) {
            threadPool.shutdownNow();
            threadPool = null;
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (this.mIsAppUnlockMode) {
            this.exitAppUnlockModeAndFinish();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onHeightChange() {
        super.onHeightChange();
        this.setWholeLayout();
    }

    @Override
    protected void onStop() {
        // This app should be as stateless as possible. When app disappears most activities should finish.
        super.onStop();
        if (this.mIsAppUnlockMode) {
            this.exitAppUnlockMode();
        }
        if (!comingBackFlag) {
            net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.onAppExit(this);
        }
        if (!this.iAmHomeActivity && !this.comingBackFlag) {
            this.finish();
        }
    }

    @Override
    protected void enableWindowAnimationForElements() {
        super.enableWindowAnimationForElements();

        this.enableWindowAnimationForEachViewGroup(findViewById(R.id.mainRootLinearLayout));
        this.enableWindowAnimationForEachViewGroup(findViewById(R.id.mainInputTextWrapperLinearLayout));
        this.enableWindowAnimationForEachViewGroup(findViewById(R.id.candidateViewWrapperLinearLayout));
        this.enableWindowAnimationForEachViewGroup(findViewById(R.id.appLockWrapperLinearLayout));
    }

    @Override
    protected void disableWindowAnimationForElements() {
        super.disableWindowAnimationForElements();

        this.disableWindowAnimationForEachViewGroup(findViewById(R.id.mainRootLinearLayout));
        this.disableWindowAnimationForEachViewGroup(findViewById(R.id.mainInputTextWrapperLinearLayout));
        this.disableWindowAnimationForEachViewGroup(findViewById(R.id.candidateViewWrapperLinearLayout));
        this.disableWindowAnimationForEachViewGroup(findViewById(R.id.appLockWrapperLinearLayout));
    }

    public void changeInputText(String text) {
        mainInputText.setText(text);
        this.onCommandInput(mainInputText.getText());
    }

    private void setWholeLayout() {
        if (resultCandidateListAdapter.isEmpty()) {
            findViewById(R.id.candidateViewWrapperLinearLayout).setPaddingRelative(0, 0, 0, 0);
        } else {
            findViewById(R.id.candidateViewWrapperLinearLayout).setPaddingRelative(0, (int)(6 * getResources().getDisplayMetrics().density + 0.5), 0, 0);
        }

        final boolean contentFilled = !mainInputText.getText().toString().isEmpty() || this.homeItemExists || this.mIsAppUnlockMode;

        this.setWindowBoundarySize(contentFilled ? ROOT_WINDOW_FULL_WIDTH_IN_MOBILE : ROOT_WINDOW_ALWAYS_HORIZONTAL_MARGIN, 0);

        this.setWindowLocationGravity(contentFilled ? Gravity.TOP : Gravity.CENTER_VERTICAL);

        final double pixelsPerSp = getResources().getDisplayMetrics().scaledDensity;

        // mainInputText: editTextSize * (1 (text) + 0.3 * 2 (padding)
        // If space is limited, split remaining height into 1(EditText):2(ListView and other margins)
        final double editTextSizeSp = Math.min(40.0, this.getWindowBodyAvailableHeight() / 4.8 / pixelsPerSp);
        mainInputText.setTextSize((int) editTextSizeSp);
        mainInputText.setPadding((int) (editTextSizeSp * 0.3 * pixelsPerSp), (int)(editTextSizeSp * 0.3 * pixelsPerSp), (int)(editTextSizeSp * 0.3 * pixelsPerSp), (int)(editTextSizeSp * 0.3 * pixelsPerSp));

        mainInputText.requestFocus();
        mainInputText.requestFocusFromTouch();
    }

    private void executeSearch(String query) {
        List<CandidateEntry> candidates = new ArrayList<>();

        if (! query.isEmpty()) {
            candidates.addAll(commandSearchAggregator.searchCandidateEntries(query, MainActivity.this));
        }

        if (this.iAmHomeActivity && query.isEmpty()) {
            List<CandidateEntry> homeScreenEntries = commandSearchAggregator.homeScreenDefaultCandidateEntries(this);
            candidates.addAll(homeScreenEntries);
            this.homeItemExists = ! homeScreenEntries.isEmpty();
        }

        if (! query.isEmpty()) {
            candidates.addAll(commandSearchAggregator.searchCandidateEntriesForLast(query, this));
        }

        resultCandidateListAdapter.clear();
        resultCandidateListAdapter.addAll(candidates);
        resultCandidateListAdapter.notifyDataSetChanged();

        if (! candidates.isEmpty()) {
            candidateListView.setSelection(0);
        }

        this.setWholeLayout();

        this.temporaryContentShown = true;
    }

    private void triggerShakeAnimation() {
        View target = findViewById(R.id.baseWindowMainLinearLayout);
        if (target == null) {
            target = mainInputText;
        }
        if (target != null) {
            Animation shake = new TranslateAnimation(0, 16, 0, 0);
            shake.setDuration(400);
            shake.setInterpolator(new CycleInterpolator(4));
            target.startAnimation(shake);
        }
    }

    private void tryTriggerBiometricUnlock() {
        if (!net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLocked(this)) {
            return;
        }
        if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut()) {
            return;
        }
        boolean bioEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_app_lock_biometric", true);
        if (!bioEnabled || !net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isBiometricSupported(this)) {
            return;
        }
        if (this.biometricPromptShowing) {
            return;
        }

        try {
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setSubtitle(getString(R.string.preferences_item_app_lock_pin_summary))
                    .setNegativeButtonText(getString(android.R.string.cancel))
                    .build();

            this.biometricPromptShowing = true;
            BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    biometricPromptShowing = false;
                    net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.setLocked(false);
                    enableBaseWindowAnimation();
                    updateAppLockUI();
                    completeResumeSetup();
                }

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    biometricPromptShowing = false;
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    triggerShakeAnimation();
                    net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.recordFailedAttempt();
                    updateAppLockUI();
                }
            });

            prompt.authenticate(promptInfo);
        } catch (Exception ignored) {
            this.biometricPromptShowing = false;
        }
    }

    private void onCommandInput(final CharSequence query) {
        if (this.mIsAppUnlockMode) {
            this.validateUnlockInput(query.toString().trim(), false);
            return;
        }

        if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLocked(this)) {
            if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isLockedOut()) {
                mainInputText.setText("");
                return;
            }
            String storedPin = PreferenceManager.getDefaultSharedPreferences(this).getString("pref_app_lock_pin", "").trim();
            if (!storedPin.isEmpty()) {
                if (query.toString().equals(storedPin)) {
                    mainInputText.setText("");
                    net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.setLocked(false);
                    this.enableBaseWindowAnimation();
                    this.updateAppLockUI();
                    this.completeResumeSetup();
                } else if (query.length() >= storedPin.length()) {
                    triggerShakeAnimation();
                    net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.recordFailedAttempt();
                    mainInputText.setText("");
                    this.updateAppLockUI();
                }
            }
            return;
        }

        if (commandSearchAggregator.isPrepared() || (query.toString().isEmpty() && !this.iAmHomeActivity)) {
            findViewById(R.id.commandSearchWaitingNotification).setVisibility(View.GONE);
            executeSearch(query.toString());

        } else {
            final int myResumeId = this.resumeId;

            if (!this.iAmHomeActivity || !this.temporaryContentShown) {
                findViewById(R.id.commandSearchWaitingNotification).setVisibility(View.VISIBLE);
                resultCandidateListAdapter.clear();
                resultCandidateListAdapter.notifyDataSetChanged();
            }

            if (threadPool == null || threadPool.isShutdown()) {
                threadPool = Executors.newSingleThreadExecutor();
            }

            threadPool.execute(() -> {
                commandSearchAggregator.waitUntilPrepared();
                MainActivity.this.runOnUiThread(() -> {
                    if (MainActivity.this.resumeId != myResumeId) {
                        // Already different session, canceling the operation.
                        return;
                    }
                    executeSearch(mainInputText.getText().toString());
                    findViewById(R.id.commandSearchWaitingNotification).setVisibility(View.GONE);
                });
            });
        }
    }

    private void setupAppUnlockMode(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            exitAppUnlockMode();
            return;
        }
        AppLockManager.LockedAppConfig config = AppLockManager.getInstance().getEffectiveLockedAppConfig(this, packageName);
        if (config == null || !config.enabled || AppLockManager.getInstance().isAppUnlockedForSession(packageName)) {
            exitAppUnlockMode();
            return;
        }

        this.mTargetLockedPackage = packageName;
        this.mIsAppUnlockMode = true;
        this.mAppUnlockFailedAttempts = 0;

        String appName = packageName;
        Drawable appIcon = null;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            appName = pm.getApplicationLabel(info).toString();
            appIcon = pm.getApplicationIcon(info);
        } catch (Exception ignored) {}
        this.mTargetLockedAppName = appName;

        this.setHeaderFooterTexts("🔒 " + appName + " (LOCKED)", "BlueLine Console AppLock");

        View appLockWrapper = findViewById(R.id.appLockWrapperLinearLayout);
        View candidateWrapper = findViewById(R.id.candidateViewWrapperLinearLayout);
        if (candidateWrapper != null) candidateWrapper.setVisibility(View.GONE);
        if (appLockWrapper != null) appLockWrapper.setVisibility(View.VISIBLE);

        ImageView iconView = findViewById(R.id.appLockAppIcon);
        if (iconView != null) {
            if (appIcon != null) {
                iconView.setImageDrawable(appIcon);
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
        }

        TextView nameView = findViewById(R.id.appLockAppName);
        if (nameView != null) {
            nameView.setText(appName);
        }

        TextView statusView = findViewById(R.id.appLockStatusText);
        if (statusView != null) {
            statusView.setText("Enter PIN or swipe pattern to unlock");
        }

        TypedValue tvAccent = new TypedValue();
        getTheme().resolveAttribute(R.attr.bluelineconsoleAccentColor, tvAccent, true);
        final int accentColor = tvAccent.data;

        TypedValue tvDisabled = new TypedValue();
        getTheme().resolveAttribute(R.attr.bluelineconsoleDisabledTextColor, tvDisabled, true);
        final int disabledColor = tvDisabled.data;

        PatternLockView patternView = findViewById(R.id.appLockPatternView);
        if (patternView != null) {
            patternView.setAccentColor(accentColor);
            patternView.setOnPatternListener(this::validateUnlockPattern);
        }

        final TextView tabPin = findViewById(R.id.appLockTabPin);
        final TextView tabPattern = findViewById(R.id.appLockTabPattern);
        final TextView tabBiometric = findViewById(R.id.appLockTabBiometric);
        final View keypadView = findViewById(R.id.appLockPinKeypad);

        if (tabPin != null && tabPattern != null && keypadView != null && patternView != null) {
            tabPin.setTextColor(accentColor);
            tabPattern.setTextColor(disabledColor);
            keypadView.setVisibility(View.VISIBLE);
            patternView.setVisibility(View.GONE);

            tabPin.setOnClickListener(v -> {
                tabPin.setTextColor(accentColor);
                tabPattern.setTextColor(disabledColor);
                keypadView.setVisibility(View.VISIBLE);
                patternView.setVisibility(View.GONE);
                mainInputText.setHint("Enter PIN or Pattern digits to unlock...");
            });

            tabPattern.setOnClickListener(v -> {
                tabPattern.setTextColor(accentColor);
                tabPin.setTextColor(disabledColor);
                keypadView.setVisibility(View.GONE);
                patternView.setVisibility(View.VISIBLE);
                patternView.clearPattern();
                mainInputText.setHint("Swipe 9-dot pattern to unlock...");
            });
        }

        if (tabBiometric != null) {
            if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isBiometricSupported(this)) {
                tabBiometric.setVisibility(View.VISIBLE);
                tabBiometric.setOnClickListener(v -> tryTriggerBiometricForAppUnlock());
            } else {
                tabBiometric.setVisibility(View.GONE);
            }
        }

        setupKeypadButtons();

        View dismissBtn = findViewById(R.id.appLockDismissBtn);
        if (dismissBtn != null) {
            dismissBtn.setOnClickListener(v -> exitAppUnlockModeAndFinish());
        }

        mainInputText.setText("");
        mainInputText.setHint("Enter PIN or Pattern digits to unlock...");
        mainInputText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        mainInputText.setEnabled(true);
        mainInputText.requestFocus();

        this.setWholeLayout();
        this.enableBaseWindowAnimation();

        if (net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isBiometricSupported(this)) {
            new Handler(Looper.getMainLooper()).postDelayed(this::tryTriggerBiometricForAppUnlock, 300);
        }
    }

    private void setupKeypadButtons() {
        int[] numIds = new int[]{
                R.id.btnKey0, R.id.btnKey1, R.id.btnKey2, R.id.btnKey3, R.id.btnKey4,
                R.id.btnKey5, R.id.btnKey6, R.id.btnKey7, R.id.btnKey8, R.id.btnKey9
        };
        for (int id : numIds) {
            View btn = findViewById(id);
            if (btn instanceof TextView) {
                final String digit = ((TextView) btn).getText().toString();
                btn.setOnClickListener(v -> mainInputText.append(digit));
            }
        }

        View btnClear = findViewById(R.id.btnKeyClear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                Editable text = mainInputText.getText();
                if (text != null && text.length() > 0) {
                    text.delete(text.length() - 1, text.length());
                }
            });
            btnClear.setOnLongClickListener(v -> {
                mainInputText.setText("");
                return true;
            });
        }

        View btnEnter = findViewById(R.id.btnKeyEnter);
        if (btnEnter != null) {
            btnEnter.setOnClickListener(v -> {
                CharSequence text = mainInputText.getText();
                if (text != null) {
                    validateUnlockInput(text.toString().trim(), true);
                }
            });
        }
    }

    private void validateUnlockInput(String input, boolean forceCheck) {
        if (!this.mIsAppUnlockMode || this.mTargetLockedPackage == null) return;
        AppLockManager.LockedAppConfig config = AppLockManager.getInstance().getEffectiveLockedAppConfig(this, this.mTargetLockedPackage);
        if (config == null) return;

        String t9Pin = AppLockManager.getT9PinForPackage(this, this.mTargetLockedPackage);
        String masterPin = AppLockManager.getInstance().getMasterPin(this);

        boolean timeLockActive = AppLockManager.getInstance().isTimeLockEnabled(this);

        // Strict exact PIN matches (requires all 4 digits for T9 and Time Lock)
        boolean pinMatch = (!config.pin.isEmpty() && input.equals(config.pin)) ||
                           (!t9Pin.isEmpty() && input.equals(t9Pin)) ||
                           (!masterPin.equals(AppLockManager.DEFAULT_MASTER_PIN) && input.equals(masterPin)) ||
                           (timeLockActive && AppLockManager.isValidTimeBasedPin(input));

        // Exact pattern string match if typed directly
        boolean patternMatch = (!config.pattern.isEmpty() && input.equals(config.pattern)) ||
                              (!t9Pin.isEmpty() && input.equals(t9Pin));

        if (pinMatch || patternMatch) {
            onAppUnlockSuccess();
        } else {
            int targetLen = config.pin.length();
            if (targetLen == 0 && !t9Pin.isEmpty()) targetLen = t9Pin.length();
            if (targetLen == 0) targetLen = 4;
            if (forceCheck || (input.length() >= targetLen)) {
                onAppUnlockFailure();
            }
        }
    }

    private void validateUnlockPattern(String patternDigits) {
        if (!this.mIsAppUnlockMode || this.mTargetLockedPackage == null) return;
        AppLockManager.LockedAppConfig config = AppLockManager.getInstance().getEffectiveLockedAppConfig(this, this.mTargetLockedPackage);
        String t9Pin = AppLockManager.getT9PinForPackage(this, this.mTargetLockedPackage);
        String masterPattern = AppLockManager.getInstance().getMasterPattern(this);
        boolean timeLockActive = AppLockManager.getInstance().isTimeLockEnabled(this);

        boolean match = (config != null && (
                (!config.pattern.isEmpty() && AppLockManager.matchesPattern(patternDigits, config.pattern)) ||
                (!config.pin.isEmpty() && AppLockManager.matchesPattern(patternDigits, config.pin))
        )) ||
        (!t9Pin.isEmpty() && AppLockManager.matchesPattern(patternDigits, t9Pin)) ||
        (!masterPattern.equals(AppLockManager.DEFAULT_MASTER_PATTERN) && AppLockManager.matchesPattern(patternDigits, masterPattern)) ||
        (timeLockActive && AppLockManager.isValidTimeBasedPattern(patternDigits));

        if (match) {
            PatternLockView patternView = findViewById(R.id.appLockPatternView);
            if (patternView != null) {
                patternView.showSuccess();
            }
            new Handler(Looper.getMainLooper()).postDelayed(this::onAppUnlockSuccess, 200);
        } else {
            PatternLockView patternView = findViewById(R.id.appLockPatternView);
            if (patternView != null) {
                patternView.showError();
            }
            onAppUnlockFailure();
        }
    }

    private void tryTriggerBiometricForAppUnlock() {
        if (!this.mIsAppUnlockMode || this.mTargetLockedPackage == null) return;
        if (!net.nhiroki.bluelineconsole.applicationMain.lib.AppLockState.isBiometricSupported(this)) return;
        if (this.biometricPromptShowing) return;

        try {
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setSubtitle("Authenticate to unlock " + (mTargetLockedAppName != null ? mTargetLockedAppName : "App"))
                    .setNegativeButtonText(getString(android.R.string.cancel))
                    .build();

            this.biometricPromptShowing = true;
            BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    biometricPromptShowing = false;
                    onAppUnlockSuccess();
                }

                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    biometricPromptShowing = false;
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    onAppUnlockFailure();
                }
            });

            prompt.authenticate(promptInfo);
        } catch (Exception ignored) {
            this.biometricPromptShowing = false;
        }
    }

    private void onAppUnlockSuccess() {
        if (!this.mIsAppUnlockMode || this.mTargetLockedPackage == null) return;
        String pkg = this.mTargetLockedPackage;
        String appName = this.mTargetLockedAppName != null ? this.mTargetLockedAppName : pkg;

        AppLockManager.getInstance().unlockAppSession(pkg);
        AppLockManager.getInstance().notifyAppLaunchedFromConsole(pkg);

        Toast.makeText(this, "Unlocked " + appName, Toast.LENGTH_SHORT).show();

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launchIntent);
        }
        exitAppUnlockMode();
        new Handler(Looper.getMainLooper()).postDelayed(this::finishIfNotHome, 300);
    }

    private void onAppUnlockFailure() {
        this.mAppUnlockFailedAttempts++;
        triggerShakeAnimation();
        mainInputText.setText("");
        if (this.mAppUnlockFailedAttempts >= 3) {
            Toast.makeText(this, "Access denied: 3 failed attempts.", Toast.LENGTH_SHORT).show();
            if (this.mTargetLockedPackage != null) {
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        am.killBackgroundProcesses(this.mTargetLockedPackage);
                    }
                } catch (Exception ignored) {}
            }
            exitAppUnlockModeAndFinish();
            BlueLineAgentService service = BlueLineAgentService.getInstance();
            if (service != null) {
                service.pressHome();
            }
        } else {
            TextView status = findViewById(R.id.appLockStatusText);
            if (status != null) {
                status.setText(String.format("Incorrect PIN or Pattern (%d of 3 attempts)", this.mAppUnlockFailedAttempts));
            }
        }
    }

    private void exitAppUnlockMode() {
        this.mIsAppUnlockMode = false;
        this.mTargetLockedPackage = null;
        this.mTargetLockedAppName = null;
        this.mAppUnlockFailedAttempts = 0;

        View appLockWrapper = findViewById(R.id.appLockWrapperLinearLayout);
        if (appLockWrapper != null) {
            appLockWrapper.setVisibility(View.GONE);
        }
        View candidateWrapper = findViewById(R.id.candidateViewWrapperLinearLayout);
        if (candidateWrapper != null) {
            candidateWrapper.setVisibility(View.VISIBLE);
        }

        this.setHeaderFooterTexts(getString(R.string.app_name), String.format(getString(R.string.displayedFullVersionString), BuildConfig.VERSION_NAME));
        mainInputText.setInputType(InputType.TYPE_CLASS_TEXT);
        mainInputText.setHint(null);
        mainInputText.setText("");
        setWholeLayout();
    }

    private void exitAppUnlockModeAndFinish() {
        exitAppUnlockMode();
        finishIfNotHome();
    }

    private class MainInputTextListener implements TextWatcher {
        public MainInputTextListener(CharSequence s) {
            if(! s.toString().equals("")) {
                onCommandInput(s);
            }
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            MainActivity.this.temporaryContentShown = false;
            onCommandInput(s);
        }

        @Override
        public void afterTextChanged(Editable s) { }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
    }
}
