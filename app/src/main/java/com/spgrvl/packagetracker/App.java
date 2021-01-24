package com.spgrvl.packagetracker;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.os.Build;

public class App extends Application {
    public static final String CHANNEL_PKG_ID = "channelPkg";
    private static final int UPD_JOB_ID = 38925;

    @Override
    public void onCreate() {
        super.onCreate();

        // Create notification channels
        createNotificationChannels();

        // Schedule background updating
        scheduleJob();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channelPkg = new NotificationChannel(
                    CHANNEL_PKG_ID,
                    "Package Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channelPkg.setDescription("This notification channel displays information about new package updates");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channelPkg);
        }
    }

    private void scheduleJob() {
        ComponentName componentName = new ComponentName(this, UpdateJobService.class);
        JobInfo info = new JobInfo.Builder(UPD_JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY) // only when there is internet connection
                .setPersisted(true) // survive reboots
                .setPeriodic(15 * 60 * 1000) // interval - 15 minutes
                .build();

        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        scheduler.schedule(info);
    }
}
