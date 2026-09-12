package net.nhiroki.bluelineconsole.applock;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.nhiroki.bluelineconsole.R;

public class AppLockDialogHelper {

    public interface OnPatternSavedListener {
        void onPatternSaved(String patternDigits);
    }

    public interface OnPinSavedListener {
        void onPinSaved(String pin);
    }

    public static void showPatternDialog(Context context, String title, OnPatternSavedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pattern_input, null);
        builder.setView(view);

        final AlertDialog dialog = builder.create();

        TextView titleView = view.findViewById(R.id.dialogPatternTitle);
        TextView statusView = view.findViewById(R.id.dialogPatternStatus);
        PatternLockView patternView = view.findViewById(R.id.dialogPatternLockView);
        TextView btnClear = view.findViewById(R.id.dialogPatternBtnClear);
        TextView btnCancel = view.findViewById(R.id.dialogPatternBtnCancel);
        TextView btnSave = view.findViewById(R.id.dialogPatternBtnSave);

        if (title != null && !title.isEmpty()) {
            titleView.setText(title);
        }

        final String[] recordedPattern = new String[]{""};

        patternView.setOnPatternListener(patternDigits -> {
            recordedPattern[0] = patternDigits;
            if (patternDigits.length() >= 4) {
                statusView.setText("Pattern recorded (" + patternDigits.length() + " dots)");
                statusView.setTextColor(0xFF00FF99);
                patternView.showSuccess();
            } else {
                statusView.setText("Too short (" + patternDigits.length() + "/4 dots minimum)");
                statusView.setTextColor(0xFFFF5577);
                patternView.showError();
            }
        });

        btnClear.setOnClickListener(v -> {
            recordedPattern[0] = "";
            patternView.clearPattern();
            statusView.setText("Pattern: (none recorded)");
            statusView.setTextColor(0xFF00F0FF);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            if (recordedPattern[0].length() < 4) {
                Toast.makeText(context, "Connect at least 4 dots to save pattern", Toast.LENGTH_SHORT).show();
                patternView.showError();
                return;
            }
            listener.onPatternSaved(recordedPattern[0]);
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showPinDialog(Context context, String title, String currentPin, OnPinSavedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pin_input, null);
        builder.setView(view);

        final AlertDialog dialog = builder.create();

        TextView titleView = view.findViewById(R.id.dialogPinTitle);
        EditText pinInput = view.findViewById(R.id.dialogPinEditText);
        TextView btnCancel = view.findViewById(R.id.dialogPinBtnCancel);
        TextView btnSave = view.findViewById(R.id.dialogPinBtnSave);

        if (title != null && !title.isEmpty()) {
            titleView.setText(title);
        }

        if (currentPin != null && !currentPin.isEmpty()) {
            pinInput.setText(currentPin);
            pinInput.setSelection(currentPin.length());
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String pin = pinInput.getText().toString().trim();
            if (pin.length() < 4) {
                Toast.makeText(context, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            listener.onPinSaved(pin);
            dialog.dismiss();
        });

        dialog.show();
    }

    public static void showAppActionMenu(Context context, String packageName, String appName, Runnable onUpdated) {
        AppLockManager mgr = AppLockManager.getInstance();
        AppLockManager.LockedAppConfig cfg = mgr.getLockedAppConfig(context, packageName);
        boolean isExempt = mgr.isExempt(context, packageName);
        String t9Pin = AppLockManager.getT9PinForPackage(context, packageName);

        String[] options = new String[]{
                "🔢 Set Custom PIN",
                "🔲 Draw Custom Pattern",
                "🔄 Reset to T9 Default (PIN: " + t9Pin + ")",
                isExempt ? "🛡️ Protect from Lock All" : "🔓 Whitelist / Exempt from Lock All"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        builder.setTitle("🔒 " + appName);
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: {
                    // Set Custom PIN
                    String existingPin = (cfg != null) ? cfg.pin : "";
                    showPinDialog(context, "Set PIN for " + appName, existingPin, pin -> {
                        String pat = (cfg != null) ? cfg.pattern : "";
                        mgr.setAppLock(context, packageName, pin, pat);
                        Toast.makeText(context, "Locked " + appName + " with PIN: " + pin, Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    });
                    break;
                }
                case 1: {
                    // Draw Custom Pattern
                    showPatternDialog(context, "Draw Pattern for " + appName, pattern -> {
                        String currentPin = (cfg != null) ? cfg.pin : "";
                        mgr.setAppLock(context, packageName, currentPin, pattern);
                        Toast.makeText(context, "Pattern saved for " + appName, Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    });
                    break;
                }
                case 2: {
                    // Reset to T9 Default
                    mgr.removeAppLock(context, packageName);
                    Toast.makeText(context, appName + " reset to default T9 lock (PIN: " + t9Pin + ")", Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) onUpdated.run();
                    break;
                }
                case 3: {
                    // Toggle Whitelist
                    mgr.setExempt(context, packageName, !isExempt);
                    Toast.makeText(context, (!isExempt ? "Whitelisted " : "Protected ") + appName, Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) onUpdated.run();
                    break;
                }
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    public static void showGlobalSettingsDialog(Context context, Runnable onUpdated) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_applock_global_settings, null);
        builder.setView(view);

        final AlertDialog dialog = builder.create();
        AppLockManager mgr = AppLockManager.getInstance();

        android.widget.Switch swMaster = view.findViewById(R.id.dialogGlobalSwitchMasterEnable);
        android.widget.Switch swLockAll = view.findViewById(R.id.dialogGlobalSwitchLockAll);
        android.widget.Switch swTimeLock = view.findViewById(R.id.dialogGlobalSwitchTimeLock);

        swMaster.setChecked(mgr.isMasterEnabled(context));
        swLockAll.setChecked(mgr.isLockAllApps(context));
        swTimeLock.setChecked(mgr.isTimeLockEnabled(context));

        TextView tvPinInfo = view.findViewById(R.id.dialogMasterPinInfo);
        TextView btnChangePin = view.findViewById(R.id.dialogBtnChangeMasterPin);
        TextView btnResetPin = view.findViewById(R.id.dialogBtnResetMasterPin);

        TextView tvPatInfo = view.findViewById(R.id.dialogMasterPatternInfo);
        TextView btnDrawPat = view.findViewById(R.id.dialogBtnDrawMasterPattern);
        TextView btnResetPat = view.findViewById(R.id.dialogBtnResetMasterPattern);

        TextView btnDone = view.findViewById(R.id.dialogBtnGlobalDone);

        Runnable refreshLabels = () -> {
            boolean timeActive = mgr.isTimeLockEnabled(context);
            String masterPin = mgr.getMasterPin(context);
            tvPinInfo.setText(timeActive ? "Master PIN: Rolling Time (" + masterPin + ")" : "Master PIN: Custom (" + masterPin + ")");

            String masterPat = mgr.getMasterPattern(context);
            tvPatInfo.setText(timeActive ? "Master Pattern: Rolling Time" : "Master Pattern: Custom (" + masterPat + ")");
        };

        refreshLabels.run();

        swMaster.setOnCheckedChangeListener((bv, isChecked) -> {
            mgr.setMasterEnabled(context, isChecked);
            Toast.makeText(context, "App Lock " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
            if (onUpdated != null) onUpdated.run();
        });

        swLockAll.setOnCheckedChangeListener((bv, isChecked) -> {
            mgr.setLockAllApps(context, isChecked);
            Toast.makeText(context, "Lock All Apps " + (isChecked ? "ACTIVE" : "OFF"), Toast.LENGTH_SHORT).show();
            if (onUpdated != null) onUpdated.run();
        });

        swTimeLock.setOnCheckedChangeListener((bv, isChecked) -> {
            mgr.setTimeLockEnabled(context, isChecked);
            refreshLabels.run();
            Toast.makeText(context, "Dynamic Time Lock " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
            if (onUpdated != null) onUpdated.run();
        });

        btnChangePin.setOnClickListener(v -> {
            String cur = mgr.getMasterPin(context);
            showPinDialog(context, "Set Master PIN", cur, pin -> {
                mgr.setMasterPin(context, pin);
                refreshLabels.run();
                Toast.makeText(context, "Master PIN set to: " + pin, Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        });

        btnResetPin.setOnClickListener(v -> {
            mgr.setMasterPin(context, AppLockManager.DEFAULT_MASTER_PIN);
            refreshLabels.run();
            Toast.makeText(context, "Master PIN reset to Dynamic Time Lock", Toast.LENGTH_SHORT).show();
            if (onUpdated != null) onUpdated.run();
        });

        btnDrawPat.setOnClickListener(v -> {
            showPatternDialog(context, "Draw Master Pattern", pattern -> {
                mgr.setMasterPattern(context, pattern);
                refreshLabels.run();
                Toast.makeText(context, "Master Pattern saved", Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        });

        btnResetPat.setOnClickListener(v -> {
            mgr.setMasterPattern(context, AppLockManager.DEFAULT_MASTER_PATTERN);
            refreshLabels.run();
            Toast.makeText(context, "Master Pattern reset to Dynamic Time Lock", Toast.LENGTH_SHORT).show();
            if (onUpdated != null) onUpdated.run();
        });

        btnDone.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
