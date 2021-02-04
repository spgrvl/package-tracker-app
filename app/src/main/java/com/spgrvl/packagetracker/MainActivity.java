package com.spgrvl.packagetracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener,
        AddNewDialog.AddDialogListener, BarcodeSelectionDialog.SingleChoiceListener {

    ListView trackingNumbersLv;
    DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
    private SwipeRefreshLayout swipeRefreshLayout;

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

        // Find ListView by ID
        this.trackingNumbersLv = findViewById(R.id.trackingNumbersLv);

        // Find SwipeRefreshLayout by ID
        swipeRefreshLayout = findViewById(R.id.swipeRefreshMain);
        swipeRefreshLayout.setOnRefreshListener(this);

        // Called when user clicks fab (floating add button)
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDialog(null);
            }
        });

        // Called when user clicks ListView items
        trackingNumbersLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                view.setSelected(true);
                TrackingIndexModel clickedTracking = (TrackingIndexModel) parent.getItemAtPosition(position);
                openTrackingDetails(clickedTracking.getTracking());
            }
        });

        // Called when user long clicks ListView items
        trackingNumbersLv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

                TrackingIndexModel clickedTracking = (TrackingIndexModel) parent.getItemAtPosition(position);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.sure_confirmation)
                        .setMessage(R.string.delete_confirmation)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                databaseHelper.deleteTracking(clickedTracking.getTracking());
                                showTrackingOnListView();
                                Toast.makeText(getApplicationContext(), getString(R.string.package_deleted_partial) + " " + clickedTracking.getTracking(), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            }
        });

        // Populating the ListView from DB
        showTrackingOnListView();
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(MainActivity.this, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        MainActivity.this.startActivity(myIntent);
    }

    private void showTrackingOnListView() {
        trackingNumbersLv.setAdapter(new CustomIndexListAdapter(this, databaseHelper.getAllTracking()));
    }

    public void addTrackingDb(String trackingNumber, String customName) {
        boolean insertData = databaseHelper.addNewTracking(trackingNumber, customName);

        if (insertData) {
            Toast.makeText(MainActivity.this, R.string.added_successfully, Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(MainActivity.this, R.string.something_went_wrong, Toast.LENGTH_LONG).show();
        }
    }

    private void updateIndex() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                UpdateTrackingDetails upd = new UpdateTrackingDetails(null, MainActivity.this, true);
                boolean a = upd.updateAll();
                if (a) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showTrackingOnListView();
                            swipeRefreshLayout.setRefreshing(false);
                        }
                    });
                }
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
    public void onResume(){
        super.onResume();
        showTrackingOnListView();
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
            String regex = "[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}";
            Matcher trackingMatcher = Pattern.compile(regex).matcher(uri.toString());
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

        if (trackingNumber.isEmpty())  {
            Toast.makeText(this, R.string.toast_empty_tracking, Toast.LENGTH_LONG).show();
        } else if (tracking_numbers.contains(trackingNumber)) {
            Toast.makeText(this, R.string.toast_tracking_exists, Toast.LENGTH_LONG).show();
            openTrackingDetails(trackingNumber);
        } else {
            // Add to database (table + index)
            addTrackingDb(trackingNumber, customName);

            // Update the ListView adapter
            showTrackingOnListView();
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