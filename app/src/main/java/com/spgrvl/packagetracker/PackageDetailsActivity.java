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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PackageDetailsActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener, EditDialog.AddDialogListener {

    private String tracking;
    private RecyclerView trackingDetailsRv;
    private SwipeRefreshLayout swipeRefreshLayout;
    public static final String eltaTrackingRegex = "[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}";
    public static final String speedexTrackingRegex = "[0-9]{12}";
    public static final String acsOrGenikiTrackingRegex = "[0-9]{10}";
    public static final String cometHellasTrackingRegex = "[0-9]{8}";

    final DatabaseHelper databaseHelper = new DatabaseHelper(PackageDetailsActivity.this);

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
        } else if (itemId == R.id.open_browser_button) {
            String carrier = detectCarrier();
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
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("text", tracking);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, R.string.tracking_copied_clipboard, Toast.LENGTH_LONG).show();
                        break;
                    case "geniki":
                        url = "https://www.taxydromiki.com/track/" + tracking;
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
            // Edit database (table + index)
            editTrackingDb(trackingNumber, newTrackingNumber, newCustomName);
        }
    }

    private void editTrackingDb(String trackingNumber, String newTrackingNumber, String newCustomName) {
        boolean updateData = databaseHelper.editTracking(trackingNumber, newTrackingNumber, newCustomName);
        if (updateData) {
            Toast.makeText(PackageDetailsActivity.this, R.string.edited_successfully, Toast.LENGTH_SHORT).show();
            if (!tracking.equals(newTrackingNumber)) {
                this.tracking = newTrackingNumber;
                this.setTitle(tracking);
                swipeRefreshLayout.setRefreshing(true);
                updateDetails(true);
            }
        } else {
            Toast.makeText(PackageDetailsActivity.this, R.string.something_went_wrong, Toast.LENGTH_LONG).show();
        }
    }

    private String detectCarrier() {
        if (Pattern.compile(eltaTrackingRegex).matcher(tracking).find()) {
            return "elta";
        } else if (Pattern.compile(speedexTrackingRegex).matcher(tracking).find()) {
            return "speedex";
        } else if (Pattern.compile(acsOrGenikiTrackingRegex).matcher(tracking).find()) {
            return acsOrGeniki();
        } else if (Pattern.compile(cometHellasTrackingRegex).matcher(tracking).find()) {
            return "cometHellas";
        }
        return null;
    }

    private String acsOrGeniki() {
        // determine if tracking belongs to ACS or Geniki
        final boolean[] isAcs = {false};

        String url = "https://www.acscourier.net/el/track-and-trace?p_p_id=ACSCustomersAreaTrackTrace_WAR_ACSCustomersAreaportlet&p_p_lifecycle=2&p_p_resource_id=trackTraceJson&generalCode=" + tracking;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                countDownLatch.countDown();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String myResponse = Objects.requireNonNull(response.body()).string();
                    try {
                        JSONObject jsonResponseObject = new JSONObject(myResponse);
                        JSONArray jsonResultsArray = jsonResponseObject.getJSONArray("results");
                        if (jsonResultsArray.length() > 0) {
                            isAcs[0] = true;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (isAcs[0]) {
            return "acs";
        } else {
            return "geniki";
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }
}