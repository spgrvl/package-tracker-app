package com.spgrvl.packagetracker;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.view.inputmethod.InputMethodManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener,
        AddNewDialog.AddDialogListener, BarcodeSelectionDialog.SingleChoiceListener {

    RecyclerView trackingNumbersRv;
    RecyclerView deliveredPackagesRv;
    final DatabaseHelper databaseHelper = new DatabaseHelper(MainActivity.this);
    private SwipeRefreshLayout swipeRefreshLayout;
    private SwipeRefreshLayout swipeRefreshDelivered;
    private EditText searchEditText;
    private BottomNavigationView bottomNavigation;
    private CustomIndexRvAdapter deliveredAdapter;
    private View emptyLayout;
    private TextView emptyTitleText;
    private TextView emptySubtitleText;
    private int activePackageCount = 0;
    private int deliveredPackageCount = 0;
    private int currentTabId = R.id.nav_active;
    protected final ArrayList<String> selectionList = new ArrayList<>();
    protected int counter = 0;
    public int position = -1;
    static boolean startedFlag;
    public boolean isInSelectionMode = false;
    private boolean clipboardPref;
    public static final String PREF_CLIPBOARD = "pref_clipboard";
    public static final String PREF_NOTIF = "pref_notif";
    public static final String eltaTrackingRegex = "^[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}$";
    public static final String easyMailTracking2Regex = "^[0-9]{11}$";
    public static final String speedexOrCourierCenterOrEasyMailTrackingRegex = "^[0-9]{12}$";
    public static final String delatolasTrackingRegex = "^[A-Za-z0-9]{12}$";
    public static final String skroutzLastMileTrackingRegex = "^[A-Za-z0-9]{13}$";
    public static final String acsOrGenikiOrBoxNowTrackingRegex = "^[0-9]{10}$";
    public static final String cometHellasTrackingRegex = "^[0-9]{8}$";
    public static final String trackingNumberRegex = String.format("(%s)|(%s)|(%s)|(%s)|(%s)|(%s)|(%s)",
            eltaTrackingRegex, easyMailTracking2Regex, speedexOrCourierCenterOrEasyMailTrackingRegex,
            delatolasTrackingRegex, skroutzLastMileTrackingRegex, acsOrGenikiOrBoxNowTrackingRegex,
            cometHellasTrackingRegex);
    private CustomIndexRvAdapter adapter;
    private static final long RV_UPDATE_INTERVAL = 10000;
    private Handler rvHandler;
    private Runnable rvRunnable;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        this.setTitle(R.string.app_name);

        // Read User preferences regarding clipboard access
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        clipboardPref = sharedPreferences.getBoolean(PREF_CLIPBOARD, false);

        setContentView(R.layout.activity_main);

        // Handle window insets for edge-to-edge display
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            // Get the system bars insets
            androidx.core.graphics.Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            
            // Apply padding to the root view to avoid content going behind system bars
            v.setPadding(systemBarsInsets.left, systemBarsInsets.top, systemBarsInsets.right, 0);
            
            return insets;
        });

        // Check for notifications permission and request if needed
        boolean notifPref = sharedPreferences.getBoolean(PREF_NOTIF, true);
        if (notifPref) {
            checkNotificationPermission();
        }

        // Find UI elements by ID
        searchEditText = findViewById(R.id.searchEditText);
        emptyLayout = findViewById(R.id.empty_layout);
        emptyTitleText = findViewById(R.id.emptyTitleText);
        emptySubtitleText = findViewById(R.id.emptySubtitleText);

        // Find RecyclerViews by ID
        this.trackingNumbersRv = findViewById(R.id.trackingNumbersRv);
        this.deliveredPackagesRv = findViewById(R.id.deliveredPackagesRv);

        // Find SwipeRefreshLayouts by ID
        swipeRefreshLayout = findViewById(R.id.swipeRefreshMain);
        swipeRefreshLayout.setOnRefreshListener(this);
        
        swipeRefreshDelivered = findViewById(R.id.swipeRefreshDelivered);
        swipeRefreshDelivered.setOnRefreshListener(this);
        
        // Find BottomNavigationView by ID
        bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setOnItemSelectedListener(this::onNavigationItemSelected);
        
        // Handle system window insets for bottom navigation container
        View bottomNavigationContainer = findViewById(R.id.bottomNavigationContainer);
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationContainer, (v, insets) -> {
            androidx.core.graphics.Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBarsInsets.bottom);
            return insets;
        });
        
        // Handle content container insets to prevent overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.contentContainer), (v, insets) -> {
            androidx.core.graphics.Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBarsInsets.bottom + dpToPx(80)); // Bottom nav height + system bar
            return insets;
        });

        // Set up search functionality
        setupSearchFunctionality();
        
        // Set up click listener to clear search focus when clicking outside
        setupSearchFocusHandling();

        // Populating the RecyclerView from DB
        showTrackingOnRecyclerView();

        // Refresh package entries every 10 seconds
        rvHandler = new Handler(Looper.getMainLooper());
        rvRunnable = () -> {
            if (!isInSelectionMode) {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                if (deliveredAdapter != null) {
                    deliveredAdapter.notifyDataSetChanged();
                }
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

    private void setupSearchFunctionality() {
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterPackages(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void filterPackages(String query) {
        if (currentTabId == R.id.nav_active && adapter != null) {
            // Filter active packages
            adapter.getFilter().filter(query);
        } else if (currentTabId == R.id.nav_delivered && deliveredAdapter != null) {
            // Filter delivered packages
            deliveredAdapter.getFilter().filter(query);
        }
    }

    private void setupSearchFocusHandling() {
        // Set up click listener on the root view to clear search focus
        findViewById(android.R.id.content).setOnClickListener(v -> {
            if (searchEditText.hasFocus()) {
                searchEditText.clearFocus();
                // Hide the keyboard
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
                }
            }
        });
        
        // Prevent the search bar itself from clearing focus when clicked
        searchEditText.setOnClickListener(v -> {
            // Do nothing - let the search bar keep focus
        });
    }

    private void showTrackingOnRecyclerView() {
        // Show active packages
        List<TrackingIndexModel> activeTracking = databaseHelper.getAllTracking(false);
        adapter = new CustomIndexRvAdapter(MainActivity.this, activeTracking);
        trackingNumbersRv.setAdapter(adapter);

        // Show delivered packages
        List<TrackingIndexModel> deliveredTracking = databaseHelper.getAllTracking(true);
        deliveredAdapter = new CustomIndexRvAdapter(MainActivity.this, deliveredTracking);
        deliveredPackagesRv.setAdapter(deliveredAdapter);

        // Force RecyclerViews to measure and layout
        trackingNumbersRv.requestLayout();
        trackingNumbersRv.invalidate();
        deliveredPackagesRv.requestLayout();
        deliveredPackagesRv.invalidate();

        activePackageCount = activeTracking.size();
        deliveredPackageCount = deliveredTracking.size();
        updateEmptyState();
    }

    public void addTrackingDb(String trackingNumber, String customName) {
        boolean insertData = databaseHelper.addNewTracking(trackingNumber, customName, false, null);

        if (insertData) {
            Toast.makeText(MainActivity.this, R.string.added_successfully, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(MainActivity.this, R.string.something_went_wrong, Toast.LENGTH_LONG).show();
        }
    }

    private void stopAllSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        if (swipeRefreshDelivered != null) {
            swipeRefreshDelivered.setRefreshing(false);
        }
    }

    private void updateIndex() {
        if (isNetworkAvailable()) {
            new Thread(() -> {
                UpdateTrackingDetails upd = new UpdateTrackingDetails(null, MainActivity.this, true);
                boolean a = upd.updateAll();
                runOnUiThread(() -> {
                    if (a) {
                        showTrackingOnRecyclerView();
                    }
                    stopAllSwipeRefresh();
                });
            }).start();
        } else {
            Toast.makeText(MainActivity.this, R.string.no_internet, Toast.LENGTH_SHORT).show();
            stopAllSwipeRefresh();
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
            boolean isDeliveredTab = isDeliveredTabSelected();
            menu.findItem(R.id.selection_complete_button).setVisible(!isDeliveredTab);
            menu.findItem(R.id.selection_active_button).setVisible(isDeliveredTab);
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
        } else if (item.getItemId() == R.id.settings_button) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (item.getItemId() == android.R.id.home) {
            clearSelectionMode();
        } else if (item.getItemId() == R.id.select_all) {
            CustomIndexRvAdapter currentAdapter = getCurrentAdapter();
            if (currentAdapter != null) {
                currentAdapter.selectAll();
            }
        } else if (item.getItemId() == R.id.selection_delete_button && selectionList.size() > 0) {
            delete_packages();
        } else if (item.getItemId() == R.id.selection_complete_button && selectionList.size() > 0) {
            complete_packages();
        } else if (item.getItemId() == R.id.selection_active_button && selectionList.size() > 0) {
            mark_packages_active();
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onRefresh() {
        clearSelectionMode();
        updateIndex();
    }

    private boolean onNavigationItemSelected(android.view.MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.nav_active) {
            if (isInSelectionMode && currentTabId != R.id.nav_active) {
                clearSelectionMode();
            }
            currentTabId = R.id.nav_active;
            showActiveTab();
            return true;
        } else if (itemId == R.id.nav_delivered) {
            if (isInSelectionMode && currentTabId != R.id.nav_delivered) {
                clearSelectionMode();
            }
            currentTabId = R.id.nav_delivered;
            showDeliveredTab();
            return true;
        } else if (itemId == R.id.nav_add) {
            openDialog(null);
            return false;
        }
        
        return false;
    }

    private void showActiveTab() {
        // Show active packages content
        findViewById(R.id.swipeRefreshMain).setVisibility(View.VISIBLE);
        findViewById(R.id.swipeRefreshDelivered).setVisibility(View.GONE);

        // Clear search field and reset filters
        searchEditText.setText("");
        if (adapter != null) {
            adapter.getFilter().filter("");
        }
        updateEmptyState();
    }

    private void showDeliveredTab() {
        // Show delivered packages content
        findViewById(R.id.swipeRefreshMain).setVisibility(View.GONE);
        findViewById(R.id.swipeRefreshDelivered).setVisibility(View.VISIBLE);

        // Clear search field and reset filters
        searchEditText.setText("");
        if (deliveredAdapter != null) {
            deliveredAdapter.getFilter().filter("");
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean showForActive = currentTabId == R.id.nav_active && activePackageCount == 0;
        boolean showForDelivered = currentTabId == R.id.nav_delivered && deliveredPackageCount == 0;

        if (showForActive) {
            emptyTitleText.setText(R.string.no_active_packages);
            emptySubtitleText.setText(R.string.no_packages_instructions);
            emptyLayout.setVisibility(View.VISIBLE);
        } else if (showForDelivered) {
            emptyTitleText.setText(R.string.no_delivered_packages);
            emptySubtitleText.setText(R.string.no_delivered_instructions);
            emptyLayout.setVisibility(View.VISIBLE);
        } else {
            emptyLayout.setVisibility(View.GONE);
        }
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
                    new MaterialAlertDialogBuilder(MainActivity.this)
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

        if (tracking_numbers.contains(trackingNumber)) {
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
            selectionList.add(tracking);
            counter++;
            updateToolbarText();
            position = index;
            CustomIndexRvAdapter currentAdapter = getCurrentAdapter();
            if (currentAdapter != null) {
                currentAdapter.notifyDataSetChanged();
            }
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
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (deliveredAdapter != null) {
            deliveredAdapter.notifyDataSetChanged();
        }
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
        new MaterialAlertDialogBuilder(this)
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

    private void mark_packages_active() {
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

    private boolean isDeliveredTabSelected() {
        return currentTabId == R.id.nav_delivered;
    }

    private CustomIndexRvAdapter getCurrentAdapter() {
        return isDeliveredTabSelected() ? deliveredAdapter : adapter;
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && 
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            // Fallback for older versions (though minSdk is 24, this is just for safety)
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
        }
    }

    private void checkNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // This is only possible in API level >= 33 (TIRAMISU)
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                notificationPermissionDenied();
            }
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (!isGranted) {
            notificationPermissionDenied();
        }
    });

    private void notificationPermissionDenied() {
        SharedPreferences.Editor notifPrefEditor = sharedPreferences.edit();
        notifPrefEditor.putBoolean(PREF_NOTIF, false);
        notifPrefEditor.apply();
        Toast.makeText(MainActivity.this, R.string.notifications_permission_denied, Toast.LENGTH_SHORT).show();
    }
}