package com.spgrvl.packagetracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.appcompat.app.AppCompatActivity;
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
    private boolean completed = false;
    private TextView carrierTv;
    private MaterialButton completedButton;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        setContentView(R.layout.activity_tracking_details);

        // Handle window insets for edge-to-edge display
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            androidx.core.graphics.Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBarsInsets.left, 0, systemBarsInsets.right, systemBarsInsets.bottom);
            // Toolbar extends behind the status bar, like the action bar on the other screens
            View toolbarView = findViewById(R.id.toolbar);
            toolbarView.setPadding(toolbarView.getPaddingLeft(), systemBarsInsets.top, toolbarView.getPaddingRight(), 0);
            return insets;
        });

        // Set up toolbar as action bar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        // Find RecyclerView by ID and set divider
        this.trackingDetailsRv = findViewById(R.id.trackingDetailsRv);

        // Find SwipeRefreshLayout by ID
        swipeRefreshLayout = findViewById(R.id.swipeRefreshDetails);
        swipeRefreshLayout.setOnRefreshListener(this);

        // Get the intent sent from parent activity
        Intent intent = getIntent();
        this.tracking = intent.getStringExtra("tracking");

        // Fetch custom name and completed status from DB
        try {
            indexEntry = databaseHelper.getIndexEntry(tracking);
        } catch (CursorIndexOutOfBoundsException e) {
            this.startActivity(new Intent(this, MainActivity.class));
            Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show();
            finish();
        }
        customName = indexEntry.get(4);
        completed = indexEntry.get(7).equals("1");

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

        updateActionBarLogo();

        completedButton = findViewById(R.id.completedButton);
        completedButton.setOnClickListener(v -> handleCompleted());
        updateCompletedButton();

        showDetailsOnRecyclerView();

        // Update details if not completed
        if (!completed) {
            updateDetails(false);
        }

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
        trackingDetailsRv.setAdapter(new CustomDetailsRvAdapter(this, trackingDetails, completed));
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
                        updateActionBarLogo();
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
                        url = "https://elta.gr/track?code=" + tracking;
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
                    case "delatolas":
                        url = "https://docuclass.delatolas.com/tnt_temp.php?id=" + tracking;
                        break;
                    case "skroutzLastMile":
                        url = "https://www.skroutzlastmile.gr/#" + tracking;
                        break;
                    case "easyMail":
                        url = "https://trackntrace.easymail.gr/" + tracking;
                        break;
                    case "boxNow":
                        url = "https://boxnow.gr/?track=" + tracking;
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
        new MaterialAlertDialogBuilder(PackageDetailsActivity.this)
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

    private void handleCompleted() {
        databaseHelper.setCompleted(tracking, !completed);
        completed = !completed;
        updateCompletedButton();
        showDetailsOnRecyclerView();
    }

    private void updateCompletedButton() {
        if (completed) {
            completedButton.setText(R.string.mark_active);
            completedButton.setIconResource(R.drawable.ic_back);
            completedButton.setBackgroundTintList(ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.primary_blue)));
        } else {
            completedButton.setText(R.string.mark_as_completed);
            completedButton.setIconResource(R.drawable.ic_check);
            completedButton.setBackgroundTintList(ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.success_green)));
        }
    }

    private void openDialog() {
        EditDialog editDialog = new EditDialog(tracking);
        editDialog.show(getSupportFragmentManager(), "Edit Tracking Dialog");
    }

    private void updateActionBarLogo() {
        String carrier = getCarrier(false);
        int logoResId = getCarrierLogoResId(carrier);
        if (logoResId != 0) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), logoResId);
            if (bitmap != null) {
                float density = getResources().getDisplayMetrics().density;
                int maxHeight = (int) (32 * density);
                int maxWidth = (int) (40 * density);

                float scale = Math.min(
                        (float) maxHeight / bitmap.getHeight(),
                        (float) maxWidth / bitmap.getWidth());
                int targetWidth = (int) (bitmap.getWidth() * scale);
                int targetHeight = (int) (bitmap.getHeight() * scale);

                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                BitmapDrawable logoDrawable = new BitmapDrawable(getResources(), scaled);
                int rightInset = (int) (8 * density);
                toolbar.setLogo(new InsetDrawable(logoDrawable, 0, 0, rightInset, 0));
            }
        } else {
            toolbar.setLogo(null);
        }
    }

    private int getCarrierLogoResId(String carrier) {
        if (carrier == null) return 0;
        switch (carrier) {
            case "acs":             return R.drawable.ic_carrier_acs;
            case "boxNow":          return R.drawable.ic_carrier_boxnow;
            case "cometHellas":     return R.drawable.ic_carrier_comet_hellas;
            case "courierCenter":   return R.drawable.ic_carrier_courier_center;
            case "delatolas":       return R.drawable.ic_carrier_delatolas;
            case "easyMail":        return R.drawable.ic_carrier_easy_mail;
            case "elta":            return R.drawable.ic_carrier_elta;
            case "geniki":          return R.drawable.ic_carrier_geniki;
            case "skroutzLastMile": return R.drawable.ic_carrier_skroutz_last_mile;
            case "speedex":         return R.drawable.ic_carrier_speedex;
            default:                return 0;
        }
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

        // Edit database (table + index) if changed
        if (newCustomName == null && customName == null && newTrackingNumber.equals(tracking) ||
                (newCustomName != null && newCustomName.equals(customName) && newTrackingNumber.equals(tracking))) {
            Toast.makeText(PackageDetailsActivity.this, R.string.no_information_changed, Toast.LENGTH_SHORT).show();
        } else {
            editTrackingDb(trackingNumber, newTrackingNumber, newCustomName);
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
}