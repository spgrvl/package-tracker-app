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
        // Set custom language if changed on app preferences
        Localization localization = new Localization();
        localization.setLocale(UpdateWorker.this.getApplicationContext());

        UpdateTrackingDetails upd = new UpdateTrackingDetails(null, getApplicationContext(), false);
        upd.updateAll();
        return Result.success();
    }
}
