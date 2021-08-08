package com.spgrvl.packagetracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
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
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener,
        AddNewDialog.AddDialogListener, BarcodeSelectionDialog.SingleChoiceListener {

    RecyclerView trackingNumbersRv;
    final DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
    private SwipeRefreshLayout swipeRefreshLayout;
    protected final ArrayList<String> selectionList = new ArrayList<>();
    protected int counter = 0;
    public int position = -1;
    static boolean startedFlag;
    public boolean isInSelectionMode = false;
    private boolean clipboardPref;
    public static final String PREF_CLIPBOARD = "pref_clipboard";
    public static final String eltaTrackingRegex = "^[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}$";
    public static final String easyMailTracking2Regex = "^[0-9]{11}$";
    public static final String speedexOrCourierCenterOrEasyMailTrackingRegex = "^[0-9]{12}$";
    public static final String delatolasTrackingRegex = "^[A-Za-z0-9]{12}$";
    public static final String acsOrGenikiTrackingRegex = "^[0-9]{10}$";
    public static final String cometHellasTrackingRegex = "^[0-9]{8}$";
    public static final String trackingNumberRegex = String.format("(%s)|(%s)|(%s)|(%s)|(%s)|(%s)", eltaTrackingRegex, easyMailTracking2Regex, speedexOrCourierCenterOrEasyMailTrackingRegex, delatolasTrackingRegex, acsOrGenikiTrackingRegex, cometHellasTrackingRegex);
    private CustomIndexRvAdapter adapter;
    private FloatingActionButton fab;
    private static final long RV_UPDATE_INTERVAL = 10000;
    private Handler rvHandler;
    private Runnable rvRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set custom language if changed on app preferences
        Localization localization = new Localization();
        localization.setLocale(MainActivity.this);
        this.setTitle(R.string.app_name);

        // Read User preferences regarding clipboard access
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        clipboardPref = sharedPreferences.getBoolean(PREF_CLIPBOARD, false);

        setContentView(R.layout.activity_main);

        // Prompt to add package when supported urls are opened with app
        Uri uri = getIntent().getData();
        handleUriIntent(uri);

        // Find Button by ID
        fab = this.findViewById(R.id.fab);

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

        // Refresh package entries every 10 seconds
        rvHandler = new Handler(Looper.getMainLooper());
        rvRunnable = () -> {
            if (!isInSelectionMode) {
                adapter.notifyDataSetChanged();
            }
            rvHandler.postDelayed(rvRunnable, RV_UPDATE_INTERVAL);
        };

        startedFlag = true;
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(MainActivity.this, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        MainActivity.this.startActivity(myIntent);
    }

    private void showTrackingOnRecyclerView() {
        ArrayList<String> trackingNumbers = databaseHelper.getTrackingNumbers(false);
        if (trackingNumbers.size() == 0) {
            findViewById(R.id.empty_layout).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.empty_layout).setVisibility(View.GONE);
        }
        List<TrackingIndexModel> allTracking = databaseHelper.getAllTracking();
        adapter = new CustomIndexRvAdapter(MainActivity.this, allTracking);
        trackingNumbersRv.setAdapter(adapter);
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
        if (isNetworkAvailable()) {
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
        } else {
            Toast.makeText(MainActivity.this, R.string.no_internet, Toast.LENGTH_SHORT).show();
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void updatePackage(String tracking) {
        new Thread(() -> {
            UpdateTrackingDetails upd = new UpdateTrackingDetails(tracking, MainActivity.this, true);
            boolean a = upd.getWebsite();
            if (a) {
                runOnUiThread(this::showTrackingOnRecyclerView);
            }
        }).start();
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
            menu.findItem(R.id.selection_active_button).setVisible(false);
            Objects.requireNonNull(this.getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        } else {
            menu.setGroupVisible(R.id.selection_group, false);
            menu.setGroupVisible(R.id.main_group, true);
            Objects.requireNonNull(this.getSupportActionBar()).setDisplayHomeAsUpEnabled(false);
            this.setTitle(R.string.app_name);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_button) {
            swipeRefreshLayout.setRefreshing(true);
            updateIndex();
        } else if (item.getItemId() == R.id.completed_button) {
            startActivity(new Intent(this, CompletedActivity.class));
        } else if (item.getItemId() == R.id.settings_button) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (item.getItemId() == android.R.id.home) {
            clearSelectionMode();
        } else if (item.getItemId() == R.id.select_all) {
            adapter.selectAll();
        } else if (item.getItemId() == R.id.selection_delete_button && selectionList.size() > 0) {
            delete_packages();
        } else if (item.getItemId() == R.id.selection_complete_button && selectionList.size() > 0) {
            complete_packages();
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

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Read device's clipboard and if a valid tracking number is found, offer to add it
        // Only if set in app preferences
        if (clipboardPref && hasFocus && startedFlag) {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboardManager.hasPrimaryClip()) {
                ClipData clipData = clipboardManager.getPrimaryClip();
                ClipData.Item item = clipData.getItemAt(0);
                if (item.getText() == null) {
                    startedFlag = false;
                    return;
                }
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
            String trackingNumberRegexUri = trackingNumberRegex.replace("^", "").replace("$", "");
            Matcher trackingMatcher = Pattern.compile(trackingNumberRegexUri).matcher(uri.toString());
            if (trackingMatcher.find()) {
                // Open dialog with the tracking number pre-filled
                openDialog(trackingMatcher.group(0));
            }
        }
    }

    @Override
    public void submitTracking(String trackingNumber, String customName) {
        ArrayList<String> tracking_numbers = databaseHelper.getTrackingNumbers(false);
        ArrayList<String> completed_tracking_numbers = databaseHelper.getTrackingNumbers(true);
        tracking_numbers.addAll(completed_tracking_numbers);

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

            // Update that specific package
            updatePackage(trackingNumber);
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

    public void startSelection(int index, String tracking) {
        if (!isInSelectionMode) {
            isInSelectionMode = true;
            fab.setVisibility(View.GONE);
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
        fab.setVisibility(View.VISIBLE);
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

    private void complete_packages() {
        String toast_msg;
        if (selectionList.size() == 1) {
            toast_msg = getString(R.string.package_completed, selectionList.get(0));
        } else {
            toast_msg = getString(R.string.packages_completed, selectionList.size());
        }

        for (String tracking : selectionList) {
            databaseHelper.setCompleted(tracking, true);
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