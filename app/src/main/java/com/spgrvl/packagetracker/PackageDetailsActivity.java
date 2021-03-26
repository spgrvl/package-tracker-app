package com.spgrvl.packagetracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PackageDetailsActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener, EditDialog.AddDialogListener {

    private String tracking;
    private RecyclerView trackingDetailsRv;
    private SwipeRefreshLayout swipeRefreshLayout;

    final DatabaseHelper databaseHelper = new DatabaseHelper(PackageDetailsActivity.this);
    ArrayList<String> indexEntry;
    private String customName = null;
    private TextView carrierTv;

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

        // Fetch custom name from DB
        indexEntry = databaseHelper.getIndexEntry(tracking);
        customName = indexEntry.get(4);

        // Set title and subtitle in action bar
        if (customName != null) {
            this.setTitle(customName);
            getSupportActionBar().setSubtitle(tracking);
        } else {
            this.setTitle(tracking);
        }

        // Find Carrier TextView by ID and set value
        this.carrierTv = findViewById(R.id.carrierTv);
        carrierTv.setText(getCarrier(true));

        showDetailsOnRecyclerView();

        // Update details
        updateDetails(false);

        // Mark item as read in index table
        databaseHelper.setUnreadStatus(tracking, false);
    }

    private void showDetailsOnRecyclerView() {
        List<TrackingDetailsModel> trackingDetails = databaseHelper.getTrackingDetails(tracking);
        if (trackingDetails.size() == 0) {
            findViewById(R.id.empty_details).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.empty_details).setVisibility(View.GONE);
        }
        trackingDetailsRv.setAdapter(new CustomDetailsRvAdapter(this, trackingDetails));
    }

    private void updateDetails(boolean manualUpdate) {
        if (isNetworkAvailable()) {
            new Thread(() -> {
                UpdateTrackingDetails upd = new UpdateTrackingDetails(tracking, PackageDetailsActivity.this, true);
                boolean a = upd.getWebsite();
                if (a) {
                    runOnUiThread(() -> {
                        showDetailsOnRecyclerView();
                        carrierTv.setText(getCarrier(true));
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }).start();
        } else if (manualUpdate) {
            Toast.makeText(this, R.string.no_internet, Toast.LENGTH_SHORT).show();
            swipeRefreshLayout.setRefreshing(false);
        }
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
            updateDetails(true);
        } else if (itemId == android.R.id.home) {
            finish();
        } else if (itemId == R.id.edit_button) {
            openDialog();
        } else if (itemId == R.id.delete_button) {
            deleteTracking();
        } else if (itemId == R.id.copy_tracking_number) {
            copyTracking();
        } else if (itemId == R.id.open_browser_button) {
            String carrier = getCarrier(false);
            if (carrier != null) {
                String url = null;
                switch (carrier) {
                    case "elta":
                        url = "https://itemsearch.elta.gr/el-GR/Query/Direct/" + tracking;
                        break;
                    case "speedex":
                        url = "http://www.speedex.gr/speedex/NewTrackAndTrace.aspx?number=" + tracking;
                        break;
                    case "acs":
                        url = "https://a.acssp.gr/track/?k=etr:" + tracking;
                        break;
                    case "cometHellas":
                        url = "https://www.comethellas.gr/track-n-trace/";
                        // Copy tracking number to clipboard since there is no direct url
                        copyTracking();
                        break;
                    case "geniki":
                        url = "https://www.taxydromiki.com/track/" + tracking;
                        break;
                    case "courierCenter":
                        url = "https://www.courier.gr/track/result?tracknr=" + tracking;
                        break;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                this.startActivity(intent);
            } else {
                Toast.makeText(this, R.string.action_failed_invalid_tracking, Toast.LENGTH_LONG).show();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void copyTracking() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("text", tracking);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.tracking_copied_clipboard, Toast.LENGTH_LONG).show();
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

    private String getCarrier(boolean presentable) {
        // Fetch carrier from DB
        indexEntry = databaseHelper.getIndexEntry(tracking);
        String carrier = indexEntry.get(6);

        if (presentable) {
            if (carrier != null) {
                int resId = getResources().getIdentifier(carrier, "string", getPackageName());
                return getResources().getString(resId);
            } else {
                return getString(R.string.unknown_carrier);
            }
        } else {
            return carrier;
        }
    }

    @Override
    public void onRefresh() {
        updateDetails(true);
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
            // Edit database (table + index) if changed
            if (newCustomName == null && customName == null && newTrackingNumber.equals(tracking) ||
                    (newCustomName != null && newCustomName.equals(customName) && newTrackingNumber.equals(tracking))) {
                Toast.makeText(PackageDetailsActivity.this, R.string.no_information_changed, Toast.LENGTH_SHORT).show();
            } else {
                editTrackingDb(trackingNumber, newTrackingNumber, newCustomName);
            }
        }
    }

    private void editTrackingDb(String trackingNumber, String newTrackingNumber, String newCustomName) {
        boolean updateData = databaseHelper.editTracking(trackingNumber, newTrackingNumber, newCustomName);
        if (updateData) {
            Toast.makeText(PackageDetailsActivity.this, R.string.edited_successfully, Toast.LENGTH_SHORT).show();
            this.tracking = newTrackingNumber;
            // Set title and subtitle in action bar
            if (newCustomName != null) {
                this.setTitle(newCustomName);
                Objects.requireNonNull(getSupportActionBar()).setSubtitle(tracking);
            } else {
                this.setTitle(tracking);
                Objects.requireNonNull(getSupportActionBar()).setSubtitle(null);
            }
            swipeRefreshLayout.setRefreshing(true);
            updateDetails(true);
        } else {
            Toast.makeText(PackageDetailsActivity.this, R.string.something_went_wrong, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }
}