package com.spgrvl.packagetracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class PackageDetailsActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener{

    private String tracking;
    private ListView trackingDetailsLv;
    private SwipeRefreshLayout swipeRefreshLayout;

    DatabaseHelper databaseHelper = new DatabaseHelper(PackageDetailsActivity.this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking_details);

        // Find ListView by ID
        this.trackingDetailsLv = findViewById(R.id.trackingDetailsLv);

        // Find SwipeRefreshLayout by ID
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDetails);
        swipeRefreshLayout.setOnRefreshListener(this);

        // Get the intent sent from MainActivity
        Intent intent = getIntent();

        // Parameter in Intent, sent from MainActivity
        this.tracking = intent.getStringExtra("tracking");
        this.setTitle("Tracking package " + tracking);

        showDetailsOnListView();
    }

    private void showDetailsOnListView() {
        trackingDetailsLv.setAdapter(new CustomDetailsListAdapter(this, databaseHelper.getTrackingDetails(tracking)));
    }

    private void updateDetails() {
        UpdateTrackingDetails upd = new UpdateTrackingDetails(tracking, PackageDetailsActivity.this);
        boolean a = upd.getWebsite();
        if (a) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showDetailsOnListView();
                }
            });
        }
        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_button) {
            swipeRefreshLayout.setRefreshing(true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    updateDetails();
                }
            }).start();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateDetails();
            }
        }).start();
    }
}