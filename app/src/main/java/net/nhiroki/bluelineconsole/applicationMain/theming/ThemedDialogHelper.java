package net.nhiroki.bluelineconsole.applicationMain.theming;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.preference.PreferenceManager;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.BaseWindowActivity;
import net.nhiroki.bluelineconsole.applicationMain.theming.eachTheme.UnderwaterCausticDrawable;

public class ThemedDialogHelper {
    public static void styleDialog(final AlertDialog alertDialog, final Activity activity) {
        if (alertDialog == null || activity == null) return;

        int color = Color.parseColor("#00f0ff");
        if (activity instanceof BaseWindowActivity) {
            color = ((BaseWindowActivity) activity).getAccentColor();
        } else {
            try {
                String pref = PreferenceManager.getDefaultSharedPreferences(activity)
                        .getString(BaseWindowActivity.PREF_NAME_ACCENT_COLOR, BaseWindowActivity.PREF_VALUE_ACCENT_COLOR_THEME_DEFAULT);
                if (pref.startsWith(BaseWindowActivity.PREF_VALUE_ACCENT_COLOR_PREFIX_COLOR + "-")) {
                    String[] parts = pref.split("-");
                    int red = Integer.parseInt(parts[1]);
                    int green = Integer.parseInt(parts[2]);
                    int blue = Integer.parseInt(parts[3]);
                    color = (255 << 24) | (red << 16) | (green << 8) | blue;
                }
            } catch (Exception ignored) {}
        }
        final int accentColor = color;
        final float density = activity.getResources().getDisplayMetrics().density;

        applyCyberGlassHUD(alertDialog, activity, accentColor, density);

        Window window = alertDialog.getWindow();
        if (window != null) {
            window.getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    applyCyberGlassHUD(alertDialog, activity, accentColor, density);
                }
            });
        }
    }

    private static void applyCyberGlassHUD(final AlertDialog alertDialog, final Activity activity, final int accentColor, final float density) {
        Window window = alertDialog.getWindow();
        if (window == null) return;

        // 1. Transparent dialog window with background blur on Android 12+
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int displayWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int targetWidth = (int) Math.min(displayWidth * 0.94f, 540 * density);
        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.CENTER);

        if (Build.VERSION.SDK_INT >= 31) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(40);
            } catch (Exception ignored) {}
        }

        // 2. Wrap parentPanel inside authentic Cyber Glass HUD Frame
        View parentPanel = alertDialog.findViewById(androidx.appcompat.R.id.parentPanel);
        if (parentPanel == null) {
            parentPanel = alertDialog.findViewById(R.id.parentPanel);
        }

        if (parentPanel != null) {
            // Check if not already wrapped
            if (!(parentPanel.getParent() instanceof ViewGroup && ((ViewGroup) parentPanel.getParent()).getId() == R.id.cyberGlassDialogContentHolder)) {
                ViewGroup windowRoot = (ViewGroup) parentPanel.getParent();
                if (windowRoot != null) {
                    int index = windowRoot.indexOfChild(parentPanel);
                    ViewGroup.LayoutParams origLp = parentPanel.getLayoutParams();

                    // Read original title
                    TextView origTitleView = alertDialog.findViewById(androidx.appcompat.R.id.alertTitle);
                    if (origTitleView == null) origTitleView = alertDialog.findViewById(android.R.id.title);
                    CharSequence titleText = "";
                    if (origTitleView != null && origTitleView.getText() != null) {
                        titleText = origTitleView.getText();
                    }

                    // Hide original topPanel so title isn't doubled
                    View topPanel = alertDialog.findViewById(androidx.appcompat.R.id.topPanel);
                    if (topPanel != null) {
                        topPanel.setVisibility(View.GONE);
                    } else if (origTitleView != null) {
                        origTitleView.setVisibility(View.GONE);
                    }

                    // Inflate Cyber Glass HUD frame
                    View hudRoot = LayoutInflater.from(activity).inflate(R.layout.cyber_glass_dialog_frame, windowRoot, false);
                    TextView headerTitle = hudRoot.findViewById(R.id.cyberGlassDialogHeaderTitle);
                    headerTitle.setText(titleText);

                    FrameLayout contentHolder = hudRoot.findViewById(R.id.cyberGlassDialogContentHolder);

                    windowRoot.removeView(parentPanel);
                    parentPanel.setBackground(null);
                    contentHolder.addView(parentPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    windowRoot.addView(hudRoot, index, origLp);
                }
            }
        }

        // 3. Style Cyber Glass Header Tab (Top-Left Angled Tab)
        View headerWrapper = alertDialog.findViewById(R.id.cyberGlassDialogHeaderWrapper);
        if (headerWrapper != null && headerWrapper.getBackground() != null) {
            DrawableCompat.setTint(headerWrapper.getBackground().mutate(), accentColor);
        }
        TextView headerTitle = alertDialog.findViewById(R.id.cyberGlassDialogHeaderTitle);
        if (headerTitle != null) {
            headerTitle.setTextColor(accentColor);
            headerTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            // If original title was updated, keep synced
            TextView origTitleView = alertDialog.findViewById(androidx.appcompat.R.id.alertTitle);
            if (origTitleView == null) origTitleView = alertDialog.findViewById(android.R.id.title);
            if (origTitleView != null && origTitleView.getText() != null && origTitleView.getText().length() > 0) {
                headerTitle.setText(origTitleView.getText());
            }
        }

        // 4. Style Cyber Glass Main Container with Animated Underwater Caustics
        View mainContainer = alertDialog.findViewById(R.id.cyberGlassDialogMainContainer);
        if (mainContainer != null) {
            if (!(mainContainer.getBackground() instanceof UnderwaterCausticDrawable)) {
                UnderwaterCausticDrawable causticDrawable = new UnderwaterCausticDrawable(accentColor);
                causticDrawable.setDensity(density);
                mainContainer.setBackground(causticDrawable);
                causticDrawable.start();
            } else {
                ((UnderwaterCausticDrawable) mainContainer.getBackground()).setAccentColor(accentColor);
            }
        }

        // 5. Style Cyber Glass Footer Tab (Bottom-Right Angled Tab)
        View footerWrapper = alertDialog.findViewById(R.id.cyberGlassDialogFooterWrapper);
        if (footerWrapper != null && footerWrapper.getBackground() != null) {
            DrawableCompat.setTint(footerWrapper.getBackground().mutate(), accentColor);
        }
        TextView footerTitle = alertDialog.findViewById(R.id.cyberGlassDialogFooterTitle);
        if (footerTitle != null) {
            footerTitle.setTextColor(accentColor);
            footerTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            footerTitle.setText("// " + activity.getString(R.string.app_name).toUpperCase());
        }

        // 6. Clear backgrounds of panels inside parentPanel
        View buttonPanel = alertDialog.findViewById(androidx.appcompat.R.id.buttonPanel);
        if (buttonPanel != null) buttonPanel.setBackground(null);
        View contentPanel = alertDialog.findViewById(androidx.appcompat.R.id.contentPanel);
        if (contentPanel != null) contentPanel.setBackground(null);
        View customPanel = alertDialog.findViewById(androidx.appcompat.R.id.customPanel);
        if (customPanel != null) customPanel.setBackground(null);

        // 7. Message View
        TextView messageView = alertDialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(Color.parseColor("#b0d4e3"));
            messageView.setTypeface(Typeface.MONOSPACE);
        }

        // 8. Action Buttons (Positive, Negative, Neutral)
        Button posBtn = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (posBtn != null) {
            posBtn.setTextColor(accentColor);
            posBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            posBtn.setBackgroundColor(Color.TRANSPARENT);
        }

        Button negBtn = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (negBtn != null) {
            negBtn.setTextColor(accentColor);
            negBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            negBtn.setBackgroundColor(Color.TRANSPARENT);
        }

        Button neuBtn = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (neuBtn != null) {
            neuBtn.setTextColor(accentColor);
            neuBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            neuBtn.setBackgroundColor(Color.TRANSPARENT);
        }

        // 9. Single-Choice List for ListPreference (AI Model, Theme, etc.)
        final ListView listView = alertDialog.getListView();
        if (listView != null) {
            listView.setDivider(new ColorDrawable(Color.argb(45, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))));
            listView.setDividerHeight((int) (1 * density));
            listView.setSelector(new ColorDrawable(Color.argb(60, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))));

            final ColorStateList radioTint = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            accentColor,
                            Color.parseColor("#4d7a94")
                    }
            );

            final ColorStateList textColors = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_checked}
                    },
                    new int[]{
                            accentColor,
                            Color.parseColor("#d8eaf2")
                    }
            );

            listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        View child = listView.getChildAt(i);
                        if (child instanceof CheckedTextView) {
                            CheckedTextView ctv = (CheckedTextView) child;
                            ctv.setTextColor(textColors);
                            ctv.setTypeface(Typeface.MONOSPACE, ctv.isChecked() ? Typeface.BOLD : Typeface.NORMAL);
                            if (Build.VERSION.SDK_INT >= 21) {
                                ctv.setCheckMarkTintList(radioTint);
                            }
                        }
                    }
                    return true;
                }
            });
        }

        // 10. Input text field for EditTextPreference (PIN, Custom Model, Grace Period, API Key)
        EditText editText = alertDialog.findViewById(android.R.id.edit);
        if (editText == null) {
            View customView = alertDialog.findViewById(androidx.appcompat.R.id.custom);
            if (customView != null) {
                editText = findEditText(customView);
            }
        }
        if (editText == null && alertDialog.getWindow() != null) {
            editText = findEditText(alertDialog.getWindow().getDecorView());
        }

        if (editText != null) {
            editText.setTextColor(Color.parseColor("#e6f9ff"));
            editText.setHintTextColor(Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            editText.setTypeface(Typeface.MONOSPACE);
            if (Build.VERSION.SDK_INT >= 21) {
                editText.setBackgroundTintList(ColorStateList.valueOf(accentColor));
            }
            editText.setHighlightColor(Color.argb(90, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    GradientDrawable cursor = new GradientDrawable();
                    cursor.setShape(GradientDrawable.RECTANGLE);
                    cursor.setSize((int) (2 * density), (int) (18 * density));
                    cursor.setColor(accentColor);
                    editText.setTextCursorDrawable(cursor);
                } catch (Throwable ignored) {}
            }
        }
    }

    private static EditText findEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                EditText found = findEditText(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public static void stopCaustic(android.app.Dialog dialog) {
        if (dialog == null) return;
        View mainContainer = dialog.findViewById(R.id.cyberGlassDialogMainContainer);
        if (mainContainer != null && mainContainer.getBackground() instanceof UnderwaterCausticDrawable) {
            ((UnderwaterCausticDrawable) mainContainer.getBackground()).stop();
        }
    }
}
