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
        UpdateTrackingDetails upd = new UpdateTrackingDetails(null, getApplicationContext(), false);
        upd.updateAll();
        return Result.success();
    }
}
