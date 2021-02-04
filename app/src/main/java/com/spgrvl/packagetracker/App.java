package com.spgrvl.packagetracker;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

public class App extends Application {
    public static final String CHANNEL_PKG_ID = "channelPkg";
    public static final String PREF_NOTIF = "pref_notif";
    public static final String PREF_NOTIF_INTERVAL = "pref_notif_interval";
    private static final int UPD_JOB_ID = 38925;
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

        // Create notification channels
        createNotificationChannels();

        if (notifPref) {
            // Schedule background updating
            scheduleJob();
        } else {
            // Cancel ongoing update job
            JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            scheduler.cancel(UPD_JOB_ID);
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

    private void scheduleJob() {
        ComponentName componentName = new ComponentName(this, UpdateJobService.class);
        JobInfo info = new JobInfo.Builder(UPD_JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // only when there is internet connection
                .setPersisted(true) // survive reboots
                .setPeriodic(Integer.parseInt(notifIntervalPref) * 60 * 1000) // interval in minutes
                .build();

        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        scheduler.schedule(info);
    }
}
