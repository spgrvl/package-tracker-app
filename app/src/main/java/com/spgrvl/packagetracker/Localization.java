package com.spgrvl.packagetracker;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.PreferenceManager;

import java.util.Locale;

/**
 * Applies the in-app language preference using the per-app language APIs, so the language
 * survives configuration changes (dark mode, font size, rotation, ...) on every screen.
 */
public class Localization {
    public static final String PREF_LANGUAGE = "pref_language";
    private static final String LANGUAGE_SYSTEM = "sys";

    /**
     * Applies the stored language preference. Call from Application.onCreate and whenever
     * the preference changes; running activities are recreated in the new language.
     */
    public static void applyLanguagePreference(Context context) {
        String languagePref = getLanguagePreference(context);
        LocaleListCompat locales = languagePref.equals(LANGUAGE_SYSTEM)
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(languagePref);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // AppCompat can only reach LocaleManager through a running activity, so on
            // API 33+ talk to it directly; this also works from Application.onCreate
            LocaleManager localeManager = context.getSystemService(LocaleManager.class);
            LocaleList target = LocaleList.forLanguageTags(locales.toLanguageTags());
            if (!localeManager.getApplicationLocales().equals(target)) {
                localeManager.setApplicationLocales(target);
            }
        } else {
            AppCompatDelegate.setApplicationLocales(locales);
        }
    }

    /**
     * Returns a context whose resources use the in-app language, for code that runs without
     * an activity (background worker, notifications). Below API 33 the per-app language only
     * applies to activities, so the application context still uses the system language.
     */
    public static Context getLocalizedContext(Context context) {
        String languagePref = getLanguagePreference(context);
        if (languagePref.equals(LANGUAGE_SYSTEM) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context;
        }
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(languagePref));
        return context.createConfigurationContext(config);
    }

    private static String getLanguagePreference(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_LANGUAGE, LANGUAGE_SYSTEM);
    }
}
