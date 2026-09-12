package net.nhiroki.bluelineconsole.applock;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.theming.ThemedDialogHelper;

public class AppLockDialogHelper {

    public interface OnPatternSavedListener {
        void onPatternSaved(String patternDigits);
    }

    public interface OnPinSavedListener {
        void onPinSaved(String pin);
    }

    private static Activity getActivityFromContext(Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }
        Context cur = context;
        while (cur instanceof ContextWrapper) {
            if (cur instanceof Activity) {
                return (Activity) cur;
            }
            cur = ((ContextWrapper) cur).getBaseContext();
        }
        return null;
    }

    public static void showPatternDialog(Context context, String title, OnPatternSavedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pattern_input, null);
        builder.setView(view);
        builder.setTitle(title != null && !title.isEmpty() ? title : "🔒 DRAW PATTERN LOCK");

        final AlertDialog dialog = builder.create();

        TextView statusView = view.findViewById(R.id.dialogPatternStatus);
        PatternLockView patternView = view.findViewById(R.id.dialogPatternLockView);
        TextView btnClear = view.findViewById(R.id.dialogPatternBtnClear);
        TextView btnCancel = view.findViewById(R.id.dialogPatternBtnCancel);
        TextView btnSave = view.findViewById(R.id.dialogPatternBtnSave);

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
        Activity act = getActivityFromContext(context);
        if (act != null) {
            ThemedDialogHelper.styleDialog(dialog, act);
        }
    }

    public static void showPinDialog(Context context, String title, String currentPin, OnPinSavedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pin_input, null);
        builder.setView(view);
        builder.setTitle(title != null && !title.isEmpty() ? title : "🔒 SET PIN LOCK");

        final AlertDialog dialog = builder.create();

        EditText pinInput = view.findViewById(R.id.dialogPinEditText);
        TextView btnCancel = view.findViewById(R.id.dialogPinBtnCancel);
        TextView btnSave = view.findViewById(R.id.dialogPinBtnSave);

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
        Activity act = getActivityFromContext(context);
        if (act != null) {
            ThemedDialogHelper.styleDialog(dialog, act);
        }
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
        builder.setTitle("🔒 " + appName.toUpperCase());
        builder.setItems(options, (d, which) -> {
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
        builder.setNegativeButton("[Close]", null);

        final AlertDialog dialog = builder.create();
        dialog.show();
        Activity act = getActivityFromContext(context);
        if (act != null) {
            ThemedDialogHelper.styleDialog(dialog, act);
        }
    }

    public static void showGlobalSettingsDialog(Context context, Runnable onUpdated) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_applock_global_settings, null);
        builder.setView(view);
        builder.setTitle("⚙️ GLOBAL APPLOCK SETTINGS");

        final AlertDialog dialog = builder.create();
        AppLockManager mgr = AppLockManager.getInstance();

        SwitchCompat swMaster = view.findViewById(R.id.dialogGlobalSwitchMasterEnable);
        SwitchCompat swLockAll = view.findViewById(R.id.dialogGlobalSwitchLockAll);
        SwitchCompat swTimeLock = view.findViewById(R.id.dialogGlobalSwitchTimeLock);
        SwitchCompat swLockLauncher = view.findViewById(R.id.dialogGlobalSwitchLockLauncher);

        if (swMaster != null) swMaster.setChecked(mgr.isMasterEnabled(context));
        if (swLockAll != null) swLockAll.setChecked(mgr.isLockAllApps(context));
        if (swTimeLock != null) swTimeLock.setChecked(mgr.isTimeLockEnabled(context));
        if (swLockLauncher != null) swLockLauncher.setChecked(mgr.isLockHomeLauncher(context));

        TextView tvPinInfo = view.findViewById(R.id.dialogMasterPinInfo);
        TextView btnChangePin = view.findViewById(R.id.dialogBtnChangeMasterPin);
        TextView btnResetPin = view.findViewById(R.id.dialogBtnResetMasterPin);

        TextView tvPatInfo = view.findViewById(R.id.dialogMasterPatternInfo);
        TextView btnDrawPat = view.findViewById(R.id.dialogBtnDrawMasterPattern);
        TextView btnResetPat = view.findViewById(R.id.dialogBtnResetMasterPattern);

        TextView tvGraceInfo = view.findViewById(R.id.dialogGracePeriodInfo);
        TextView btnChangeGrace = view.findViewById(R.id.dialogBtnChangeGracePeriod);

        TextView btnDone = view.findViewById(R.id.dialogBtnGlobalDone);

        Runnable refreshLabels = () -> {
            boolean timeActive = mgr.isTimeLockEnabled(context);
            String masterPin = mgr.getMasterPin(context);
            if (tvPinInfo != null) {
                tvPinInfo.setText(timeActive ? "Master PIN: Rolling Time (" + masterPin + ")" : "Master PIN: Custom (" + masterPin + ")");
            }

            String masterPat = mgr.getMasterPattern(context);
            if (tvPatInfo != null) {
                tvPatInfo.setText(timeActive ? "Master Pattern: Rolling Time" : "Master Pattern: Custom (" + masterPat + ")");
            }

            if (tvGraceInfo != null) {
                tvGraceInfo.setText("Grace Period: " + mgr.getGracePeriodSummary(context));
            }
        };

        refreshLabels.run();

        if (swMaster != null) {
            swMaster.setOnCheckedChangeListener((bv, isChecked) -> {
                mgr.setMasterEnabled(context, isChecked);
                Toast.makeText(context, "App Lock " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        }

        if (swLockAll != null) {
            swLockAll.setOnCheckedChangeListener((bv, isChecked) -> {
                mgr.setLockAllApps(context, isChecked);
                Toast.makeText(context, "Lock All Apps " + (isChecked ? "ACTIVE" : "OFF"), Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        }

        if (swTimeLock != null) {
            swTimeLock.setOnCheckedChangeListener((bv, isChecked) -> {
                mgr.setTimeLockEnabled(context, isChecked);
                refreshLabels.run();
                Toast.makeText(context, "Dynamic Time Lock " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        }

        if (swLockLauncher != null) {
            swLockLauncher.setOnCheckedChangeListener((bv, isChecked) -> {
                mgr.setLockHomeLauncher(context, isChecked);
                Toast.makeText(context, "Lock Home Launcher " + (isChecked ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        }

        if (btnChangePin != null) {
            btnChangePin.setOnClickListener(v -> {
                String cur = mgr.getMasterPin(context);
                showPinDialog(context, "Set Master PIN", cur, pin -> {
                    mgr.setMasterPin(context, pin);
                    refreshLabels.run();
                    Toast.makeText(context, "Master PIN set to: " + pin, Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) onUpdated.run();
                });
            });
        }

        if (btnResetPin != null) {
            btnResetPin.setOnClickListener(v -> {
                mgr.setMasterPin(context, AppLockManager.DEFAULT_MASTER_PIN);
                refreshLabels.run();
                Toast.makeText(context, "Master PIN reset to Dynamic Time Lock", Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        }

        if (btnDrawPat != null) {
            btnDrawPat.setOnClickListener(v -> {
                showPatternDialog(context, "Draw Master Pattern", pattern -> {
                    mgr.setMasterPattern(context, pattern);
                    refreshLabels.run();
                    Toast.makeText(context, "Master Pattern saved", Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) onUpdated.run();
                });
            });
        }

        if (btnResetPat != null) {
            btnResetPat.setOnClickListener(v -> {
                mgr.setMasterPattern(context, AppLockManager.DEFAULT_MASTER_PATTERN);
                refreshLabels.run();
                Toast.makeText(context, "Master Pattern reset to Dynamic Time Lock", Toast.LENGTH_SHORT).show();
                if (onUpdated != null) onUpdated.run();
            });
        }

        if (btnChangeGrace != null) {
            btnChangeGrace.setOnClickListener(v -> {
                String[] options = new String[] {
                    "30 seconds",
                    "2 minutes",
                    "5 minutes",
                    "Until phone is locked",
                    "Custom duration..."
                };
                String currentMode = mgr.getGracePeriodMode(context);
                int checkedItem = 3;
                if (AppLockManager.GRACE_30_SEC.equals(currentMode)) checkedItem = 0;
                else if (AppLockManager.GRACE_2_MIN.equals(currentMode)) checkedItem = 1;
                else if (AppLockManager.GRACE_5_MIN.equals(currentMode)) checkedItem = 2;
                else if (AppLockManager.GRACE_UNTIL_LOCKED.equals(currentMode)) checkedItem = 3;
                else if (AppLockManager.GRACE_CUSTOM.equals(currentMode)) checkedItem = 4;

                AlertDialog.Builder graceBuilder = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
                graceBuilder.setTitle("⏳ UNLOCK GRACE PERIOD");
                graceBuilder.setSingleChoiceItems(options, checkedItem, (d, which) -> {
                    d.dismiss();
                    if (which == 0) {
                        mgr.setGracePeriodMode(context, AppLockManager.GRACE_30_SEC);
                        refreshLabels.run();
                        Toast.makeText(context, "Grace period: 30 seconds", Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    } else if (which == 1) {
                        mgr.setGracePeriodMode(context, AppLockManager.GRACE_2_MIN);
                        refreshLabels.run();
                        Toast.makeText(context, "Grace period: 2 minutes", Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    } else if (which == 2) {
                        mgr.setGracePeriodMode(context, AppLockManager.GRACE_5_MIN);
                        refreshLabels.run();
                        Toast.makeText(context, "Grace period: 5 minutes", Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    } else if (which == 3) {
                        mgr.setGracePeriodMode(context, AppLockManager.GRACE_UNTIL_LOCKED);
                        refreshLabels.run();
                        Toast.makeText(context, "Grace period: Until phone is locked", Toast.LENGTH_SHORT).show();
                        if (onUpdated != null) onUpdated.run();
                    } else if (which == 4) {
                        showCustomGraceDialog(context, mgr, () -> {
                            refreshLabels.run();
                            if (onUpdated != null) onUpdated.run();
                        });
                    }
                });
                graceBuilder.setNegativeButton("Cancel", null);
                AlertDialog graceDialog = graceBuilder.create();
                graceDialog.show();
                Activity a = getActivityFromContext(context);
                if (a != null) {
                    ThemedDialogHelper.styleDialog(graceDialog, a);
                }
            });
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
        Activity act = getActivityFromContext(context);
        if (act != null) {
            ThemedDialogHelper.styleDialog(dialog, act);
        }
    }

    private static void showCustomGraceDialog(Context context, AppLockManager mgr, Runnable onSaved) {
        AlertDialog.Builder b = new AlertDialog.Builder(context, R.style.CyberGlassAlertDialogTheme);
        b.setTitle("⚙️ CUSTOM GRACE PERIOD");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Seconds (e.g. 45, 90, 600)");
        input.setTextColor(0xffe6f9ff);
        input.setHintTextColor(0x8899d6ea);
        input.setText(String.valueOf(mgr.getCustomGracePeriodSeconds(context)));
        input.setSelection(input.getText().length());
        input.setPadding(40, 30, 40, 30);
        b.setView(input);

        b.setPositiveButton("SAVE", (d, w) -> {
            String val = input.getText().toString().trim();
            try {
                int sec = Integer.parseInt(val);
                if (sec > 0) {
                    mgr.setCustomGracePeriodSeconds(context, sec);
                    mgr.setGracePeriodMode(context, AppLockManager.GRACE_CUSTOM);
                    Toast.makeText(context, "Custom grace period set to: " + sec + "s", Toast.LENGTH_SHORT).show();
                    if (onSaved != null) onSaved.run();
                } else {
                    Toast.makeText(context, "Invalid duration", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "Invalid duration number", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("CANCEL", null);

        AlertDialog d = b.create();
        d.show();
        Activity a = getActivityFromContext(context);
        if (a != null) {
            ThemedDialogHelper.styleDialog(d, a);
        }
    }
}
