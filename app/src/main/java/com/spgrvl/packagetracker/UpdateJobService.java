package com.spgrvl.packagetracker;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class UpdateJobService extends JobService {
    private boolean jobCancelled = false;

    @Override
    public boolean onStartJob(JobParameters params) {
        updateInBackground(params);
        return true;
    }

    private void updateInBackground(JobParameters params) {
        if (jobCancelled) {
            return;
        }
        UpdateTrackingDetails upd = new UpdateTrackingDetails(null, this);
        upd.updateAll();
        jobFinished(params, false);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        jobCancelled = true;
        return true;
    }
}