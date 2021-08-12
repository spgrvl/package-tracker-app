package com.spgrvl.packagetracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String tracking = intent.getStringExtra("tracking");
        String action = intent.getStringExtra("action");

        if (action.equals("mark_as_read")) {
            markAsRead(tracking, context);
        } else if (action.equals("mark_as_completed")) {
            markAsCompleted(tracking, context);
        }

        // Dismiss notification
        int packageId = intent.getIntExtra("packageId", 0);
        NotificationManagerCompat.from(context).cancel(null, packageId);
    }

    private void markAsRead(String tracking, Context context) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        databaseHelper.setUnreadStatus(tracking, false);
    }

    private void markAsCompleted(String tracking, Context context) {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        databaseHelper.setCompleted(tracking, true);
        databaseHelper.setUnreadStatus(tracking, false);
    }
}
