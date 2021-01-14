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
        FloatingActionButton fab = (FloatingActionButton) this.findViewById(R.id.fab);

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
                        .setTitle("Are you sure?")
                        .setMessage("Do you want to delete this package?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                databaseHelper.deleteTracking(clickedTracking.getTracking());
                                showTrackingOnListView();
                                Toast.makeText(getApplicationContext(), "Deleted package " + clickedTracking.getTracking(), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                return true;
            }
        });

        // Populating the ListView from DB
        showTrackingOnListView();
    }

    private void openTrackingDetails(String trackingNumber) {
        // Create a Intent
        // This object contains content that will be sent to PackageDetailsActivity
        Intent myIntent = new Intent(MainActivity.this, PackageDetailsActivity.class);

        // Put parameters
        myIntent.putExtra("tracking", trackingNumber);

        // Start CheckPackageActivity
        MainActivity.this.startActivity(myIntent);
    }

    private void showTrackingOnListView() {
        trackingNumbersLv.setAdapter(new CustomIndexListAdapter(this, databaseHelper.getAllTracking()));
    }

    public void addTrackingDb(String trackingNumber) {
        boolean insertData = databaseHelper.addNewTracking(trackingNumber); //TEMP VALUES

        if (insertData) {
            Toast.makeText(MainActivity.this, "Successfully added!", Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(MainActivity.this, "Something went wrong!", Toast.LENGTH_LONG).show();
        }
    }

    private void updateIndex() {
        UpdateTrackingDetails upd = new UpdateTrackingDetails(null, MainActivity.this);
        boolean a = upd.updateAll();
        if (a) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showTrackingOnListView();
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
                    updateIndex();
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

            // capture incoming data
            String[] barcodesArray = incomingMessages.getStringArray("barcodesArray");

            // show data in choice dialog
            openChoiceDialog(barcodesArray);
        }
    }

    @Override
    public void submitTracking(String trackingNumber) {

        if (trackingNumber == null || trackingNumber.isEmpty())  {
            Toast.makeText(this, R.string.toast_empty_tracking, Toast.LENGTH_LONG).show();
        } else {

            // Add to database (table + index)
            addTrackingDb(trackingNumber);

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
        submitTracking(list[position]);
    }

    @Override
    public void onNegativeButtonClicked() {
    }
}