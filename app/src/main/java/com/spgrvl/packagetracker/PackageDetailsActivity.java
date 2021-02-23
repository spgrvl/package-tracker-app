package com.spgrvl.packagetracker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.Objects;
import java.util.regex.Pattern;

public class PackageDetailsActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener, EditDialog.AddDialogListener {

    private String tracking;
    private RecyclerView trackingDetailsRv;
    private SwipeRefreshLayout swipeRefreshLayout;
    public static final String eltaTrackingRegex = "[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}";
    public static final String acsTrackingRegex = "[0-9]{10}";

    DatabaseHelper databaseHelper = new DatabaseHelper(PackageDetailsActivity.this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking_details);

        // Show back button on action bar
        Objects.requireNonNull(this.getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // Find RecyclerView by ID and set divider
        this.trackingDetailsRv = findViewById(R.id.trackingDetailsRv);
        trackingDetailsRv.addItemDecoration(new DividerItemDecoration(trackingDetailsRv.getContext(), DividerItemDecoration.VERTICAL));

        // Find SwipeRefreshLayout by ID
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDetails);
        swipeRefreshLayout.setOnRefreshListener(this);

        // Get the intent sent from MainActivity
        Intent intent = getIntent();

        // Parameter in Intent, sent from MainActivity
        this.tracking = intent.getStringExtra("tracking");
        this.setTitle(tracking);

        showDetailsOnRecyclerView();

        // Update details
        updateDetails();

        // Mark item as read in index table
        databaseHelper.setUnreadStatus(tracking, false);
    }

    private void showDetailsOnRecyclerView() {
        trackingDetailsRv.setAdapter(new CustomDetailsListAdapter(this, databaseHelper.getTrackingDetails(tracking)));
    }

    private void updateDetails() {
        new Thread(() -> {
            UpdateTrackingDetails upd = new UpdateTrackingDetails(tracking, PackageDetailsActivity.this, true);
            boolean a = upd.getWebsite();
            if (a) {
                runOnUiThread(() -> {
                    showDetailsOnRecyclerView();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.details_action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.refresh_button) {
            swipeRefreshLayout.setRefreshing(true);
            updateDetails();
        } else if (itemId == android.R.id.home) {
            finish();
        } else if (itemId == R.id.edit_button) {
            openDialog();
        } else if (itemId == R.id.delete_button) {
            deleteTracking();
        } else if (itemId == R.id.open_browser_button) {
            String carrier = detectCarrier();
            if (carrier != null) {
                String url = null;
                if (carrier.equals("elta")) {
                    url = "https://itemsearch.elta.gr/el-GR/Query/Direct/" + tracking;
                } else if (carrier.equals("acs")) {
                    url = "https://a.acssp.gr/track/?k=etr:" + tracking;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                this.startActivity(intent);
            } else {
                Toast.makeText(this, R.string.action_failed_invalid_tracking, Toast.LENGTH_LONG).show();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void deleteTracking() {
        new AlertDialog.Builder(PackageDetailsActivity.this)
                .setTitle(R.string.sure_confirmation)
                .setMessage(R.string.delete_confirmation)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    databaseHelper.deleteTracking(tracking);
                    Toast.makeText(getApplicationContext(), getString(R.string.package_deleted, tracking), Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void openDialog() {
        EditDialog editDialog = new EditDialog(tracking);
        editDialog.show(getSupportFragmentManager(), "Edit Tracking Dialog");
    }

    @Override
    public void onRefresh() {
        updateDetails();
    }


    @Override
    public void editTracking(String trackingNumber, String newTrackingNumber, String newCustomName) {

        // remove spaces from start and end of input
        newTrackingNumber = newTrackingNumber.trim();
        if (newCustomName != null) {
            newCustomName = newCustomName.trim();
        }

        if (newTrackingNumber.isEmpty()) {
            Toast.makeText(this, R.string.toast_empty_tracking, Toast.LENGTH_LONG).show();
        } else {
            // Edit database (table + index)
            editTrackingDb(trackingNumber, newTrackingNumber, newCustomName);
        }
    }

    private void editTrackingDb(String trackingNumber, String newTrackingNumber, String newCustomName) {
        boolean updateData = databaseHelper.editTracking(trackingNumber, newTrackingNumber, newCustomName);
        if (updateData) {
            Toast.makeText(PackageDetailsActivity.this, R.string.edited_successfully, Toast.LENGTH_LONG).show();
            if (!tracking.equals(newTrackingNumber)) {
                this.tracking = newTrackingNumber;
                this.setTitle(tracking);
                swipeRefreshLayout.setRefreshing(true);
                updateDetails();
            }
        } else {
            Toast.makeText(PackageDetailsActivity.this, R.string.something_went_wrong, Toast.LENGTH_LONG).show();
        }
    }

    private String detectCarrier() {
        if (Pattern.compile(eltaTrackingRegex).matcher(tracking).find()) {
            return "elta";
        } else if (Pattern.compile(acsTrackingRegex).matcher(tracking).find()) {
            return "acs";
        }
        return null;
    }
}