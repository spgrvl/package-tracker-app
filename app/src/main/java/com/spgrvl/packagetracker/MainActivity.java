package com.spgrvl.packagetracker;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener,
        AddNewDialog.AddDialogListener, BarcodeSelectionDialog.SingleChoiceListener {

    ListView trackingNumbersLv;
    DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                openDialog();
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.index_action_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_button) {
            swipeRefreshLayout.setRefreshing(true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    updateIndex();
                }
            }).start();
        } else if (item.getItemId() == R.id.settings_button) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateIndex();
            }
        }).start();
    }

    @Override
    public void onResume(){
        super.onResume();
        showTrackingOnListView();
    }

    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // listen for incoming messages
        Bundle incomingMessages = intent.getExtras();

        if (incomingMessages != null) {

            // capture incoming barcodes list
            String[] barcodesArray = incomingMessages.getStringArray("barcodesArray");

            // show barcodes in choice dialog
            openChoiceDialog(barcodesArray);
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
        } else {
            // Add to database (table + index)
            addTrackingDb(trackingNumber, customName);

            // Update the ListView adapter
            showTrackingOnListView();
        }
    }

    public void openDialog() {
        AddNewDialog addDialog = new AddNewDialog(null);
        addDialog.show(getSupportFragmentManager(), "New Tracking Dialog");
    }

    public void openChoiceDialog(String[] barcodes) {
        DialogFragment singleChoiceDialog = BarcodeSelectionDialog.newInstance(barcodes);
        singleChoiceDialog.show(getSupportFragmentManager(), "Barcode Choice Dialog");
    }

    @Override
    public void onPositiveButtonClicked(String[] list, int position) {
        // Open dialog with the tracking number pre-filled with barcode
        AddNewDialog addDialog = new AddNewDialog(list[position]);
        addDialog.show(getSupportFragmentManager(), "New Tracking Dialog");
    }

    @Override
    public void onNegativeButtonClicked() {
    }
}