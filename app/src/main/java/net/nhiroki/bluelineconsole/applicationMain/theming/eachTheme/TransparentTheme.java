package net.nhiroki.bluelineconsole.applicationMain.theming.eachTheme;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;

import android.annotation.SuppressLint;

import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.BaseWindowActivity;

public class TransparentTheme extends BlueLineConsoleDefaultTheme {
    private static final String THEME_ID = "transparent";

    @SuppressLint("MissingSuperCall")
    @Override
    public void apply(BaseWindowActivity activity) {
        activity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        if (this.isDarkMode(activity)) {
            activity.setTheme(activity.isHomeActivity() ? R.style.AppThemeTransparentDarkHome : R.style.AppThemeTransparentDark);
        } else {
            activity.setTheme(activity.isHomeActivity() ? R.style.AppThemeTransparentLightHome : R.style.AppThemeTransparentLight);
        }
        activity.setContentView(R.layout.base_window_layout_default);

        this.setFooterMargin(activity);

        this.registerExitListener(activity, activity.isHomeActivity());
    }

    @Override
    public String getThemeID() {
        return THEME_ID;
    }

    @Override
    public CharSequence getThemeTitle(Context context) {
        return context.getString(R.string.theme_name_transparent);
    }
}
