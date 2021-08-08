package com.spgrvl.packagetracker;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CompletedActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    RecyclerView trackingNumbersRv;
    final DatabaseHelper databaseHelper = new DatabaseHelper(CompletedActivity.this);
    private SwipeRefreshLayout swipeRefreshLayout;
    protected final ArrayList<String> selectionList = new ArrayList<>();
    protected int counter = 0;
    public int position = -1;
    public boolean isInSelectionMode = false;
    private CustomCompletedRvAdapter adapter;
    private static final long RV_UPDATE_INTERVAL = 10000;
    private Handler rvHandler;
    private Runnable rvRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set custom language if changed on app preferences
        Localization localization = new Localization();
        localization.setLocale(CompletedActivity.this);
        this.setTitle(R.string.completed);

        setContentView(R.layout.activity_completed);

        // Find RecyclerView by ID and set divider
        this.trackingNumbersRv = findViewById(R.id.trackingNumbersRv);
        trackingNumbersRv.addItemDecoration(new DividerItemDecoration(trackingNumbersRv.getContext(), DividerItemDecoration.VERTICAL));

        // Find SwipeRefreshLayout by ID
        swipeRefreshLayout = findViewById(R.id.swipeRefreshCompleted);
        swipeRefreshLayout.setOnRefreshListener(this);

        // Populating the RecyclerView from DB
        showTrackingOnRecyclerView();

        // Refresh package entries every 10 seconds
        rvHandler = new Handler(Looper.getMainLooper());
        rvRunnable = () -> {
            if (!isInSelectionMode) {
                adapter.notifyDataSetChanged();
            }
            rvHandler.postDelayed(rvRunnable, RV_UPDATE_INTERVAL);
        };

        // Show back button on action bar
        Objects.requireNonNull(this.getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }

    private void showTrackingOnRecyclerView() {
        ArrayList<String> trackingNumbers = databaseHelper.getTrackingNumbers(true);
        if (trackingNumbers.size() == 0) {
            findViewById(R.id.empty_layout).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.empty_layout).setVisibility(View.GONE);
        }
        List<TrackingIndexModel> allTracking = databaseHelper.getAllTracking();
        adapter = new CustomCompletedRvAdapter(CompletedActivity.this, allTracking);
        trackingNumbersRv.setAdapter(adapter);
    }

    private void updateIndex() {
        if (isNetworkAvailable()) {
            new Thread(() -> {
                UpdateTrackingDetails upd = new UpdateTrackingDetails(null, CompletedActivity.this, true);
                boolean a = upd.updateAll(true);
                if (a) {
                    runOnUiThread(() -> {
                        showTrackingOnRecyclerView();
                        swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }).start();
        } else {
            Toast.makeText(CompletedActivity.this, R.string.no_internet, Toast.LENGTH_SHORT).show();
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void resumeHandler() {
        pauseHandler(); // cancelling any existing handlers if any
        rvHandler.post(rvRunnable);
    }

    private void pauseHandler() {
        rvHandler.removeCallbacks(rvRunnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.index_action_bar, menu);
        menu.setGroupVisible(R.id.selection_group, false);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isInSelectionMode) {
            menu.setGroupVisible(R.id.main_group, false);
            menu.setGroupVisible(R.id.selection_group, true);
            menu.findItem(R.id.selection_complete_button).setVisible(false);
        } else {
            menu.setGroupVisible(R.id.selection_group, false);
            menu.setGroupVisible(R.id.main_group, true);
            menu.findItem(R.id.completed_button).setVisible(false);
            menu.findItem(R.id.settings_button).setVisible(false);
            setTitle(R.string.completed);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_button) {
            swipeRefreshLayout.setRefreshing(true);
            updateIndex();
        } else if (item.getItemId() == R.id.settings_button) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (item.getItemId() == android.R.id.home) {
            if (isInSelectionMode) {
                clearSelectionMode();
            } else {
                super.onBackPressed();
            }
        } else if (item.getItemId() == R.id.select_all) {
            adapter.selectAll();
        } else if (item.getItemId() == R.id.selection_delete_button && selectionList.size() > 0) {
            delete_packages();
        } else if (item.getItemId() == R.id.selection_active_button && selectionList.size() > 0) {
            activate_packages();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRefresh() {
        clearSelectionMode();
        updateIndex();
    }

    @Override
    public void onPause() {
        pauseHandler();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        showTrackingOnRecyclerView();
        resumeHandler();
    }

    @Override
    public void onBackPressed() {
        if (isInSelectionMode) {
            clearSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    public void startSelection(int index, String tracking) {
        if (!isInSelectionMode) {
            isInSelectionMode = true;
            selectionList.add(tracking);
            counter++;
            updateToolbarText();
            position = index;
            adapter.notifyDataSetChanged();
        }
    }

    protected void updateToolbarText() {
        invalidateOptionsMenu();
        if (counter == 0) {
            clearSelectionMode();
        } else if (counter == 1) {
            this.setTitle(getString(R.string.one_item_selected));
        } else {
            this.setTitle(getString(R.string.multiple_items_selected, counter));
        }
    }

    private void clearSelectionMode() {
        isInSelectionMode = false;
        invalidateOptionsMenu();
        counter = 0;
        selectionList.clear();
        adapter.notifyDataSetChanged();
    }

    public void selectItem(View v, String tracking) {
        if (((CheckBox) v).isChecked()) {
            ((CheckBox) v).setChecked(false);
            selectionList.remove(tracking);
            counter--;
        } else {
            ((CheckBox) v).setChecked(true);
            selectionList.add(tracking);
            counter++;
        }
        updateToolbarText();
    }

    private void delete_packages() {
        String msg, toast_msg;
        if (selectionList.size() == 1) {
            msg = getString(R.string.delete_confirmation);
            toast_msg = getString(R.string.package_deleted, selectionList.get(0));
        } else {
            msg = getString(R.string.delete_multiple_confirmation, selectionList.size());
            toast_msg = getString(R.string.packages_deleted, selectionList.size());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.sure_confirmation)
                .setMessage(msg)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    for (String tracking : selectionList) {
                        databaseHelper.deleteTracking(tracking);
                    }
                    showTrackingOnRecyclerView();
                    Toast.makeText(this, toast_msg, Toast.LENGTH_SHORT).show();
                    clearSelectionMode();
                })
                .setNegativeButton(R.string.no, ((dialog, which) -> clearSelectionMode()))
                .show();
    }

    private void activate_packages() {
        String toast_msg;
        if (selectionList.size() == 1) {
            toast_msg = getString(R.string.package_active, selectionList.get(0));
        } else {
            toast_msg = getString(R.string.packages_active, selectionList.size());
        }

        for (String tracking : selectionList) {
            databaseHelper.setCompleted(tracking, false);
        }

        showTrackingOnRecyclerView();
        clearSelectionMode();
        Toast.makeText(this, toast_msg, Toast.LENGTH_SHORT).show();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }
}