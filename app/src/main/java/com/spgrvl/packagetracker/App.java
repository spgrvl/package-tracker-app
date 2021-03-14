package com.spgrvl.packagetracker;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class App extends Application {
    public static final String CHANNEL_PKG_ID = "channelPkg";
    public static final String PREF_NOTIF = "pref_notif";
    public static final String PREF_THEME = "pref_theme";
    public static final String PREF_NOTIF_INTERVAL = "pref_notif_interval";
    private String notifIntervalPref;

    @Override
    public void onCreate() {
        super.onCreate();

        // Set default settings on first app launch
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Read User preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean notifPref = sharedPreferences.getBoolean(PREF_NOTIF, true);
        notifIntervalPref = sharedPreferences.getString(PREF_NOTIF_INTERVAL, "15");

        // Set app's theme based on user's preference
        String themePref = sharedPreferences.getString(PREF_THEME, "sys");
        switch (themePref) {
            case "sys":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }

        // Create notification channels
        createNotificationChannels();

        // Schedule background updating
        if (notifPref) {
            scheduleWorker();
        } else {
            // Cancel ongoing update worker
            WorkManager.getInstance(this).cancelUniqueWork("updateWorker");
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channelPkg = new NotificationChannel(
                    CHANNEL_PKG_ID,
                    getString(R.string.package_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channelPkg.setDescription(getString(R.string.channelPkg_notification_description));

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channelPkg);
        }
    }

    private void scheduleWorker() {
        // Schedule the update worker
        PeriodicWorkRequest updateWorkRequest = new PeriodicWorkRequest.Builder(
                UpdateWorker.class,
                Integer.parseInt(notifIntervalPref),
                TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "updateWorker",
                ExistingPeriodicWorkPolicy.REPLACE,
                updateWorkRequest);
    }
}
