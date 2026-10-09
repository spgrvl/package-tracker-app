package com.spgrvl.packagetracker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class UpdateWorker extends Worker {
    public UpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Use the in-app language for statuses and notifications
        Context context = Localization.getLocalizedContext(getApplicationContext());

        UpdateTrackingDetails upd = new UpdateTrackingDetails(null, context, false);
        upd.updateAll();
        return Result.success();
    }
}
