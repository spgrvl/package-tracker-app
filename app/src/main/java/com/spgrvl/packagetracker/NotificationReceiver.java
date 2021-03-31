package com.spgrvl.packagetracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String tracking = intent.getStringExtra("tracking");
        markAsRead(tracking, context);

        // Dismiss notification
        int packageId = intent.getIntExtra("packageId", 0);
        NotificationManagerCompat.from(context).cancel(null, packageId);
    }

    private void markAsRead(String tracking, Context context) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        databaseHelper.setUnreadStatus(tracking, false);
    }
}
