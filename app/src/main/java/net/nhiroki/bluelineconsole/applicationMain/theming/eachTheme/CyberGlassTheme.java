package net.nhiroki.bluelineconsole.applicationMain.theming.eachTheme;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.drawable.DrawableCompat;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.BaseWindowActivity;

public class CyberGlassTheme extends BaseTheme {
    private static final String THEME_ID = "cyber_glass";
    private static final @StringRes int THEME_TITLE_STRING_RES = R.string.theme_name_cyber_glass;

    @Override
    protected void configureDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    @Override
    public void beforeCreateActivity(BaseWindowActivity activity) {
        super.beforeCreateActivity(activity);
        activity.setTheme(activity.isHomeActivity() ? R.style.AppThemeCyberGlassHome : R.style.AppThemeCyberGlass);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void apply(BaseWindowActivity activity) {
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        if (Build.VERSION.SDK_INT >= 31) {
            try {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                activity.getWindow().getAttributes().setBlurBehindRadius(45);
            } catch (Exception ignored) {}
        }

        activity.setTheme(activity.isHomeActivity() ? R.style.AppThemeCyberGlassHome : R.style.AppThemeCyberGlass);
        activity.setContentView(R.layout.base_window_layout_cyber_glass);

        this.setFooterMargin(activity);
        this.registerExitListener(activity, activity.isHomeActivity());
    }

    @Override
    public void onCreateFinal(BaseWindowActivity activity) {
        super.onCreateFinal(activity);

        View headerWrapper = activity.findViewById(R.id.baseWindowHeaderWrapper);
        if (headerWrapper != null) {
            headerWrapper.setOnTouchListener(activity.new TitleBarDragOnTouchListener());
        }

        EditText mainInputText = activity.findViewById(R.id.mainInputText);
        if (mainInputText != null) {
            mainInputText.setHint("> help");
            mainInputText.setHintTextColor(Color.parseColor("#5500f0ff"));
            mainInputText.setBackgroundResource(R.drawable.cyber_glass_input_box);
        }

        View inputWrapper = activity.findViewById(R.id.mainInputTextWrapperLinearLayout);
        if (inputWrapper != null) {
            inputWrapper.setBackgroundColor(Color.TRANSPARENT);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) inputWrapper.getLayoutParams();
            if (lp != null) {
                int margin = (int) (4 * activity.getResources().getDisplayMetrics().density);
                lp.setMargins(margin, margin, margin, margin);
                inputWrapper.setLayoutParams(lp);
            }
        }

        View window = activity.findViewById(R.id.baseWindowMainLinearLayout);
        int currentAccent = activity.getAccentColor();
        if (window != null) {
            UnderwaterCausticDrawable causticDrawable = new UnderwaterCausticDrawable(currentAccent);
            causticDrawable.setDensity(activity.getResources().getDisplayMetrics().density);
            window.setBackground(causticDrawable);
            causticDrawable.start();
        }

        ListView candidateListView = activity.findViewById(R.id.candidateListView);
        if (candidateListView != null) {
            candidateListView.setDivider(new ColorDrawable(Color.argb(38, Color.red(currentAccent), Color.green(currentAccent), Color.blue(currentAccent))));
            candidateListView.setDividerHeight((int) (1 * activity.getResources().getDisplayMetrics().density));
            candidateListView.setSelector(new ColorDrawable(Color.argb(51, Color.red(currentAccent), Color.green(currentAccent), Color.blue(currentAccent))));
            candidateListView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
    }

    @Override
    public void applyAccentColor(BaseWindowActivity activity, @ColorInt int color) {
        View window = activity.findViewById(R.id.baseWindowMainLinearLayout);
        if (window != null && window.getBackground() instanceof UnderwaterCausticDrawable) {
            ((UnderwaterCausticDrawable) window.getBackground()).setAccentColor(color);
        } else if (window != null && window.getBackground() != null) {
            DrawableCompat.setTint(window.getBackground().mutate(), color);
        }
        View header = activity.findViewById(R.id.baseWindowHeaderWrapper);
        if (header != null && header.getBackground() != null) {
            DrawableCompat.setTint(header.getBackground().mutate(), color);
        }
        View footer = activity.findViewById(R.id.baseWindowFooterWrapper);
        if (footer != null && footer.getBackground() != null) {
            DrawableCompat.setTint(footer.getBackground().mutate(), color);
        }
        TextView headerText = activity.findViewById(R.id.baseWindowMainHeaderTextView);
        if (headerText != null) {
            headerText.setTextColor(color);
        }
        TextView footerText = activity.findViewById(R.id.baseWindowMainFooterTextView);
        if (footerText != null) {
            footerText.setTextColor(color);
        }
        ListView candidateListView = activity.findViewById(R.id.candidateListView);
        if (candidateListView != null) {
            candidateListView.setDivider(new ColorDrawable(Color.argb(38, Color.red(color), Color.green(color), Color.blue(color))));
            candidateListView.setSelector(new ColorDrawable(Color.argb(51, Color.red(color), Color.green(color), Color.blue(color))));
        }
    }

    @Override
    public RemoteViews createRemoteViewsForWidget(Context context, PendingIntent pendingIntent) {
        RemoteViews ret = new RemoteViews(context.getPackageName(), R.layout.widget_launcher_dark_theme);
        ret.setOnClickPendingIntent(R.id.widgetLauncherRootLinearLayout, pendingIntent);
        return ret;
    }

    @Override
    public String getThemeID() {
        return THEME_ID;
    }

    @Override
    public CharSequence getThemeTitle(Context context) {
        return context.getString(THEME_TITLE_STRING_RES);
    }

    @Override
    protected boolean hasFooter() {
        return true;
    }

    @Override
    public boolean supportsAccentColor() {
        return true;
    }

    @Override
    public @ColorInt int getDefaultAccentColor(Context context) {
        return Color.parseColor("#00f0ff");
    }

    @Override
    public void changeBaseWindowElementSizeForAnimation(final BaseWindowActivity activity, boolean visible) {
        if (!activity.getAnimationEnabledPreferenceValue()) {
            super.changeBaseWindowElementSizeForAnimation(activity, visible);
            return;
        }

        final View centerLL = activity.findViewById(R.id.baseWindowMainLinearLayout);
        final View headerWrapper = activity.findViewById(R.id.baseWindowHeaderWrapper);
        final View footerWrapper = activity.findViewById(R.id.baseWindowFooterWrapper);

        if (centerLL == null) {
            super.changeBaseWindowElementSizeForAnimation(activity, visible);
            return;
        }

        super.changeBaseWindowElementSizeForAnimation(activity, true);

        final float density = activity.getResources().getDisplayMetrics().density;

        if (visible) {
            centerLL.post(new Runnable() {
                @Override
                public void run() {
                    centerLL.setPivotX(centerLL.getWidth() / 2f);
                    centerLL.setPivotY(centerLL.getHeight() / 2f);
                    centerLL.setScaleX(0.5f);
                    centerLL.setScaleY(0.03f);
                    centerLL.setAlpha(0f);

                    if (headerWrapper != null) {
                        headerWrapper.setTranslationX(-50f * density);
                        headerWrapper.setAlpha(0f);
                    }
                    if (footerWrapper != null) {
                        footerWrapper.setTranslationX(50f * density);
                        footerWrapper.setAlpha(0f);
                    }

                    // Phase 1: Laser Slit Flash Blink (0-60ms)
                    centerLL.animate()
                            .alpha(1f)
                            .scaleX(1.0f)
                            .setDuration(60)
                            .setInterpolator(new AccelerateInterpolator())
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    // Phase 2: Eye Blink Opening Aperture (60-220ms)
                                    centerLL.animate()
                                            .scaleY(1.0f)
                                            .setDuration(160)
                                            .setInterpolator(new OvershootInterpolator(1.12f))
                                            .start();
                                }
                            })
                            .start();

                    // Phase 3: HUD Header & Footer Lock-In (130-230ms)
                    if (headerWrapper != null) {
                        headerWrapper.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(100)
                                .setStartDelay(130)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                    if (footerWrapper != null) {
                        footerWrapper.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(100)
                                .setStartDelay(140)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    }
                }
            });
        } else {
            // Eye Blinks Shut on Exit
            if (headerWrapper != null) {
                headerWrapper.animate().translationX(-40f * density).alpha(0f).setDuration(70).start();
            }
            if (footerWrapper != null) {
                footerWrapper.animate().translationX(40f * density).alpha(0f).setDuration(70).start();
            }
            centerLL.setPivotX(centerLL.getWidth() / 2f);
            centerLL.setPivotY(centerLL.getHeight() / 2f);
            centerLL.animate()
                    .scaleY(0.02f)
                    .alpha(0f)
                    .setDuration(100)
                    .setInterpolator(new AccelerateInterpolator())
                    .start();
        }
    }
}
