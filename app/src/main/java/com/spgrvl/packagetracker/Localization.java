package com.spgrvl.packagetracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.preference.PreferenceManager;

import java.util.Locale;

public class Localization {
    public static final String PREF_LANGUAGE = "pref_language";

    public void setLocale(Context context) {
        // Read User preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String languagePref = sharedPreferences.getString(PREF_LANGUAGE, "sys");

        // Set language if not set to System's default
        if (!languagePref.equals("sys")) {
            Locale locale = new Locale(languagePref);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.setLocale(locale);
            context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
        }
    }
}
