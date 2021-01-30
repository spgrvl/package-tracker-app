package com.spgrvl.packagetracker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String PREF_NOTIF = "pref_notif";
    public static final String PREF_NOTIF_INTERVAL = "pref_notif_interval";
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private Menu menu;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setHasOptionsMenu(true);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(PREF_NOTIF) || key.equals(PREF_NOTIF_INTERVAL)) {
                    Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_SHORT).show();
                    menu.findItem(R.id.restart_button).setVisible(true);
                }
                if (key.equals(PREF_NOTIF_INTERVAL)) {
                    Preference notifIntervalPref = findPreference(key);
                    ListPreference listPref = (ListPreference) notifIntervalPref;
                    notifIntervalPref.setSummary(listPref.getEntry());
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        Preference notifIntervalPref = findPreference(PREF_NOTIF_INTERVAL);
        ListPreference listPref = (ListPreference) notifIntervalPref;
        notifIntervalPref.setSummary(listPref.getEntry());
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        this.menu = menu;
        inflater.inflate(R.menu.settings_action_bar, menu);
        menu.findItem(R.id.restart_button).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.restart_button) {
            triggerRebirth(getContext());
        } else if (item.getItemId() == android.R.id.home) {
            getActivity().finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public static void triggerRebirth(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }
}