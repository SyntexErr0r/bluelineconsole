package net.nhiroki.bluelineconsole.applicationMain;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;
import androidx.preference.ListPreferenceDialogFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import net.nhiroki.bluelineconsole.applicationMain.theming.ThemedDialogHelper;

import net.nhiroki.bluelineconsole.BuildConfig;
import net.nhiroki.bluelineconsole.R;
import net.nhiroki.bluelineconsole.applicationMain.lib.EditTextConfigurations;
import net.nhiroki.bluelineconsole.applicationMain.theming.AppThemeDirectory;
import net.nhiroki.bluelineconsole.commandSearchers.lib.StringMatchStrategy;
import net.nhiroki.bluelineconsole.commands.urls.WebSearchEngine;
import net.nhiroki.bluelineconsole.commands.urls.WebSearchEnginesDatabase;
import net.nhiroki.bluelineconsole.widget.LauncherWidgetProvider;

import java.util.List;

import static android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS;

public class PreferencesFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onResume() {
        super.onResume();

        final List<WebSearchEngine> urlListForLocale = new WebSearchEnginesDatabase(PreferencesFragment.this.getContext()).getURLListForLocale(PreferencesFragment.this.getContext().getResources().getConfiguration().locale, true);

        int webSearchEngineCount = 0;
        for (WebSearchEngine e : urlListForLocale) {
            if (e.has_query) {
                ++webSearchEngineCount;
            }
        }

        CharSequence[] search_engine_entries = new CharSequence[webSearchEngineCount + 1];
        search_engine_entries[0] = getString(R.string.preferences_item_default_search_option_none);

        CharSequence[] search_engine_entry_values = new CharSequence[webSearchEngineCount + 1];
        search_engine_entry_values[0] = "none";

        int searchEnginePos = 1;

        for (WebSearchEngine e : urlListForLocale) {
            if (e.has_query) {
                search_engine_entry_values[searchEnginePos] = e.id_for_preference_value;
                search_engine_entries[searchEnginePos] = e.display_name_locale_independent;
                ++searchEnginePos;
            }
        }

        ((ListPreference) findPreference(WebSearchEnginesDatabase.PREF_KEY_DEFAULT_SEARCH)).setEntries(search_engine_entries);
        ((ListPreference) findPreference(WebSearchEnginesDatabase.PREF_KEY_DEFAULT_SEARCH)).setEntryValues(search_engine_entry_values);

        findPreference("dummy_pref_app_info").setSummary(String.format(this.getString(R.string.displayedFullVersionString), BuildConfig.VERSION_NAME));
        findPreference("dummy_pref_app_info").setSelectable(false);

        int stringMatchStrategySize = StringMatchStrategy.STRATEGY_LIST.length;

        CharSequence[] string_match_strategy_entries = new CharSequence[stringMatchStrategySize];
        CharSequence[] string_match_strategy_entry_values = new CharSequence[stringMatchStrategySize];

        for (int i = 0; i < stringMatchStrategySize; ++i) {
            string_match_strategy_entries[i] = StringMatchStrategy.getStrategyName(this.getActivity(), StringMatchStrategy.STRATEGY_LIST[i]);
            string_match_strategy_entry_values[i] = StringMatchStrategy.getStrategyPrefValue(StringMatchStrategy.STRATEGY_LIST[i]);
        }

        ((ListPreference) findPreference(StringMatchStrategy.PREF_NAME)).setEntries(string_match_strategy_entries);
        ((ListPreference) findPreference(StringMatchStrategy.PREF_NAME)).setEntryValues(string_match_strategy_entry_values);

        findPreference("pref_default_assist_app").setOnPreferenceClickListener(
                preference -> {
                    // Not a perfect behavior, main window disappears
                    // This config is not to be used everyday, it is enough if just not too confusing
                    ((PreferencesActivity) PreferencesFragment.this.getActivity()).setComingBackFlag();
                    Intent intent = new Intent(ACTION_VOICE_INPUT_SETTINGS);
                    PreferencesFragment.this.startActivity(intent);
                    return true;
                }
        );

        ((ListPreference) findPreference(AppThemeDirectory.PREF_NAME_THEME)).setEntries(AppThemeDirectory.getThemePreferenceTitles(this.getContext()));
        ((ListPreference) findPreference(AppThemeDirectory.PREF_NAME_THEME)).setEntryValues(AppThemeDirectory.getThemePreferenceKeys());

        findPreference(AppThemeDirectory.PREF_NAME_THEME).setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    LauncherWidgetProvider.updateTheme(PreferencesFragment.this.getContext(), (String)newValue);
                    PreferencesFragment.this.getActivity().finish();
                    return true;
                }
        );

        if (Build.VERSION.SDK_INT < 24) {
            findPreference(EditTextConfigurations.PREF_KEY_MAIN_EDITTEXT_HINT_LOCALE_ENGLISH).setVisible(false);
        }

        EditTextPreference pinPref = findPreference("pref_app_lock_pin");
        if (pinPref != null) {
            pinPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            });
            pinPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof String) {
                    pinPref.setText(((String) newValue).trim());
                    return false;
                }
                return true;
            });
        }

        Preference screenOffOnlyPref = findPreference("pref_app_lock_screen_off_only");
        EditTextPreference delayPref = findPreference("pref_app_lock_delay");
        if (screenOffOnlyPref != null && delayPref != null) {
            delayPref.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
            delayPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof String) {
                    try {
                        long val = Math.max(0, Long.parseLong(((String) newValue).trim()));
                        delayPref.setText(String.valueOf(val));
                    } catch (NumberFormatException e) {
                        delayPref.setText("0");
                    }
                    return false;
                }
                return true;
            });

            boolean screenOffEnabled = PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_app_lock_screen_off_only", false);
            delayPref.setEnabled(!screenOffEnabled);

            screenOffOnlyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                delayPref.setEnabled(!(Boolean) newValue);
                return true;
            });
        }

        ListPreference modelPref = findPreference("pref_ai_model");
        EditTextPreference customModelPref = findPreference("pref_ai_custom_model");
        if (modelPref != null) {
            CharSequence[] modelTitles = new CharSequence[]{
                    "Gemini 2.5 Flash (Fast & Balanced - Recommended)",
                    "Gemini 2.5 Pro (Advanced Reasoning)",
                    "Gemini 2.0 Flash Lite (Ultra-Low Latency)",
                    "Gemini 1.5 Flash (Standard)",
                    "Custom Model..."
            };
            CharSequence[] modelValues = new CharSequence[]{
                    "gemini-2.5-flash",
                    "gemini-2.5-pro",
                    "gemini-2.0-flash-lite",
                    "gemini-1.5-flash",
                    "custom"
            };
            modelPref.setEntries(modelTitles);
            modelPref.setEntryValues(modelValues);
            modelPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

            if (customModelPref != null) {
                String current = modelPref.getValue();
                customModelPref.setEnabled("custom".equals(current));

                modelPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    customModelPref.setEnabled("custom".equals(newValue));
                    return true;
                });
                customModelPref.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            }
        }
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (getParentFragmentManager().findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") != null) {
            return;
        }

        final DialogFragment f;
        if (preference instanceof EditTextPreference) {
            f = ThemedEditTextPreferenceDialogFragment.newInstance(preference.getKey());
        } else if (preference instanceof ListPreference) {
            f = ThemedListPreferenceDialogFragment.newInstance(preference.getKey());
        } else {
            super.onDisplayPreferenceDialog(preference);
            return;
        }
        f.setTargetFragment(this, 0);
        f.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
    }

    public static class ThemedListPreferenceDialogFragment extends ListPreferenceDialogFragmentCompat {
        public static ThemedListPreferenceDialogFragment newInstance(String key) {
            ThemedListPreferenceDialogFragment fragment = new ThemedListPreferenceDialogFragment();
            Bundle b = new Bundle(1);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onStart() {
            super.onStart();
            if (getDialog() instanceof AlertDialog) {
                ThemedDialogHelper.styleDialog((AlertDialog) getDialog(), getActivity());
            }
        }
    }

    public static class ThemedEditTextPreferenceDialogFragment extends EditTextPreferenceDialogFragmentCompat {
        public static ThemedEditTextPreferenceDialogFragment newInstance(String key) {
            ThemedEditTextPreferenceDialogFragment fragment = new ThemedEditTextPreferenceDialogFragment();
            Bundle b = new Bundle(1);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onStart() {
            super.onStart();
            if (getDialog() instanceof AlertDialog) {
                ThemedDialogHelper.styleDialog((AlertDialog) getDialog(), getActivity());
            }
        }
    }
}
