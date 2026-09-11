package net.nhiroki.bluelineconsole.applicationMain.theming;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import net.nhiroki.bluelineconsole.applicationMain.BaseWindowActivity;

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

        applyStyles(alertDialog, accentColor, density);

        Window window = alertDialog.getWindow();
        if (window != null) {
            window.getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    applyStyles(alertDialog, accentColor, density);
                }
            });
        }
    }

    private static void applyStyles(final AlertDialog alertDialog, final int accentColor, final float density) {
        // 1. Floating frosted dark glass background with dynamic accentColor border
        Window window = alertDialog.getWindow();
        if (window != null) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setColor(Color.parseColor("#f2030914")); // Deep frosted cyber glass
            shape.setStroke((int) (2 * density), accentColor);
            shape.setCornerRadius(12 * density);

            int inset = (int) (16 * density);
            InsetDrawable insetDrawable = new InsetDrawable(shape, inset, inset, inset, inset);
            window.setBackgroundDrawable(insetDrawable);
        }

        // 2. Title View
        TextView titleView = alertDialog.findViewById(androidx.appcompat.R.id.alertTitle);
        if (titleView == null) {
            titleView = alertDialog.findViewById(android.R.id.title);
        }
        if (titleView != null) {
            titleView.setTextColor(accentColor);
            titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            titleView.setTextSize(18);
        }

        // 3. Message View
        TextView messageView = alertDialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextColor(Color.parseColor("#b0d4e3"));
            messageView.setTypeface(Typeface.MONOSPACE);
        }

        // 4. Action Buttons (Positive, Negative, Neutral)
        Button posBtn = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (posBtn != null) {
            posBtn.setTextColor(accentColor);
            posBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        }

        Button negBtn = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
        if (negBtn != null) {
            negBtn.setTextColor(accentColor);
            negBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        }

        Button neuBtn = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
        if (neuBtn != null) {
            neuBtn.setTextColor(accentColor);
            neuBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        }

        // 5. Single-Choice List for ListPreference (AI Model, Theme, etc.)
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

        // 6. Input text field for EditTextPreference (PIN, Custom Model, Grace Period, API Key)
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
}
