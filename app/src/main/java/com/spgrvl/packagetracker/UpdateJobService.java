package com.spgrvl.packagetracker;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class UpdateJobService extends JobService {
    private boolean jobCancelled = false;
    public static final String JOB_COUNTER = "job_counter";

    @Override
    public boolean onStartJob(JobParameters params) {
        updateInBackground(params);
        return true;
    }

    private void updateInBackground(JobParameters params) {
        if (jobCancelled) {
            return;
        }

        // Read Job Counter value
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int jobCounter = sharedPreferences.getInt(JOB_COUNTER, 1);

        // Skip updating on the first job run
        if (jobCounter != 1) {
            UpdateTrackingDetails upd = new UpdateTrackingDetails(null, this, false);
            upd.updateAll();
        }

        // Increment Job Counter value
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(JOB_COUNTER, jobCounter + 1);
        editor.apply();

        jobFinished(params, false);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        jobCancelled = true;
        return true;
    }
}