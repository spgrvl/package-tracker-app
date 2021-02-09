package com.spgrvl.packagetracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener,
        AddNewDialog.AddDialogListener, BarcodeSelectionDialog.SingleChoiceListener {

    RecyclerView trackingNumbersRv;
    DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
    private SwipeRefreshLayout swipeRefreshLayout;
    static boolean startedFlag;
    private final String trackingNumberRegex = "[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set custom language if changed on app preferences
        Localization localization = new Localization();
        localization.setLocale(MainActivity.this);
        this.setTitle(R.string.app_name);

        setContentView(R.layout.activity_main);

        // Prompt to add package when supported urls are opened with app
        Uri uri = getIntent().getData();
        handleUriIntent(uri);

        // Find Button by ID
        FloatingActionButton fab = this.findViewById(R.id.fab);

        // Find RecyclerView by ID and set divider
        this.trackingNumbersRv = findViewById(R.id.trackingNumbersRv);
        trackingNumbersRv.addItemDecoration(new DividerItemDecoration(trackingNumbersRv.getContext(), DividerItemDecoration.VERTICAL));

        // Find SwipeRefreshLayout by ID
        swipeRefreshLayout = findViewById(R.id.swipeRefreshMain);
        swipeRefreshLayout.setOnRefreshListener(this);

        // Called when user clicks fab (floating add button)
        fab.setOnClickListener(v -> openDialog(null));

        // Populating the RecyclerView from DB
        showTrackingOnRecyclerView();

        startedFlag = true;
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(MainActivity.this, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        MainActivity.this.startActivity(myIntent);
    }

    private void showTrackingOnRecyclerView() {
        trackingNumbersRv.setAdapter(new CustomIndexListAdapter(this, databaseHelper.getAllTracking()));
        trackingNumbersRv.setLayoutManager(new LinearLayoutManager(this));
    }

    public void addTrackingDb(String trackingNumber, String customName) {
        boolean insertData = databaseHelper.addNewTracking(trackingNumber, customName);

        if (insertData) {
            Toast.makeText(MainActivity.this, R.string.added_successfully, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(MainActivity.this, R.string.something_went_wrong, Toast.LENGTH_LONG).show();
        }
    }

    private void updateIndex() {
        new Thread(() -> {
            UpdateTrackingDetails upd = new UpdateTrackingDetails(null, MainActivity.this, true);
            boolean a = upd.updateAll();
            if (a) {
                runOnUiThread(() -> {
                    showTrackingOnRecyclerView();
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.index_action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_button) {
            swipeRefreshLayout.setRefreshing(true);
            updateIndex();
        } else if (item.getItemId() == R.id.settings_button) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        updateIndex();
    }

    @Override
    public void onResume() {
        super.onResume();
        showTrackingOnRecyclerView();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Read device's clipboard and if a valid tracking number is found, offer to add it
        if (hasFocus && startedFlag) {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboardManager.hasPrimaryClip()) {
                ClipData clipData = clipboardManager.getPrimaryClip();
                ClipData.Item item = clipData.getItemAt(0);
                String clipText = item.getText().toString();
                startedFlag = false;

                Matcher trackingMatcher = Pattern.compile(trackingNumberRegex).matcher(clipText);
                if (trackingMatcher.find()) {
                    String tracking = trackingMatcher.group(0);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.ask_add_package)
                            .setMessage(getString(R.string.possible_tracking_id) + tracking)
                            .setPositiveButton(R.string.yes, (dialog, which) -> submitTracking(tracking, null))
                            .setNeutralButton(R.string.edit, (dialog, which) -> openDialog(tracking))
                            .setNegativeButton(R.string.no, null)
                            .show();
                }
            } else {
                startedFlag = false;
            }
        }
    }

    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // get barcode list from intent
        String[] barcodesArray = intent.getStringArrayExtra("barcodesArray");
        if (barcodesArray != null) {
            // show barcodes in choice dialog
            openChoiceDialog(barcodesArray);
        }

        // handle uri intents when app is already open
        Uri uri = intent.getData();
        handleUriIntent(uri);
    }

    private void handleUriIntent(Uri uri) {
        if (uri != null) {
            Matcher trackingMatcher = Pattern.compile(trackingNumberRegex).matcher(uri.toString());
            if (trackingMatcher.find()) {
                // Open dialog with the tracking number pre-filled
                openDialog(trackingMatcher.group(0));
            }
        }
    }

    @Override
    public void submitTracking(String trackingNumber, String customName) {
        ArrayList<String> tracking_numbers = databaseHelper.getTrackingNumbers();

        // remove spaces from start and end of input
        trackingNumber = trackingNumber.trim();
        if (customName != null) {
            customName = customName.trim();
        }

        if (trackingNumber.isEmpty()) {
            Toast.makeText(this, R.string.toast_empty_tracking, Toast.LENGTH_LONG).show();
        } else if (tracking_numbers.contains(trackingNumber)) {
            Toast.makeText(this, R.string.toast_tracking_exists, Toast.LENGTH_LONG).show();
            openTrackingDetails(trackingNumber);
        } else {
            // Add to database (table + index)
            addTrackingDb(trackingNumber, customName);

            // Update the RecyclerView adapter
            showTrackingOnRecyclerView();
        }
    }

    public void openDialog(String tracking) {
        AddNewDialog addDialog = new AddNewDialog(tracking);
        addDialog.show(getSupportFragmentManager(), "New Tracking Dialog");
    }

    public void openChoiceDialog(String[] barcodes) {
        DialogFragment singleChoiceDialog = BarcodeSelectionDialog.newInstance(barcodes);
        singleChoiceDialog.show(getSupportFragmentManager(), "Barcode Choice Dialog");
    }

    @Override
    public void onPositiveButtonClicked(String[] list, int position) {
        // Open dialog with the tracking number pre-filled with barcode
        openDialog(list[position]);
    }

    @Override
    public void onNegativeButtonClicked() {
    }
}