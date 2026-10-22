package com.spgrvl.packagetracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.CursorIndexOutOfBoundsException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
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
import androidx.core.content.ContextCompat;

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
            // e.g. a notification of a package deleted since
            return;
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
        showCarrier();

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
                        // Deleting the package finishes this screen and drops its details table
                        if (isFinishing()) {
                            return;
                        }
                        showDetailsOnRecyclerView();
                        showCarrier();
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
                        url = "https://www.speedex.gr/speedex/NewTrackAndTrace.aspx?number=" + tracking;
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
                    case "acsSkroutzLastMile":
                        // Skroutz's page covers the whole route, including the ACS events
                        url = "https://www.skroutzlastmile.gr/#" + tracking;
                        break;
                    case "easyMail":
                        url = "https://trackntrace.easymail.gr/" + tracking;
                        break;
                    case "boxNow":
                        url = "https://boxnow.gr/?track=" + tracking;
                        break;
                    case "cleverPoint":
                        url = "https://www.cleverpoint.gr/clevertrack?track_id=" + tracking;
                        break;
                    case "diakinisis":
                        url = "https://pod.diakinisis.gr/tnt.php?id=" + tracking;
                        break;
                    case "aiolos":
                        url = "https://aioloscourier.gr/track-trace/?shipping_num=" + tracking;
                        break;
                    case "eshop":
                        url = "https://www.e-shop.gr/status?id=" + tracking;
                        break;
                    case "plaisio":
                    case "plaisioAiolos":
                    case "plaisioBoxNow":
                    case "plaisioDiakinisis":
                    case "plaisioEasyMail":
                    case "plaisioElta":
                    case "plaisioGeniki":
                    case "plaisioSpeedex":
                        // Plaisio's page links to the courier's own tracking
                        url = "https://www.plaisio.gr/order-tracking/" + tracking;
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
        EditDialog editDialog = EditDialog.newInstance(tracking);
        editDialog.show(getSupportFragmentManager(), "Edit Tracking Dialog");
    }

    private void updateActionBarLogo() {
        String carrier = getCarrier(false);
        int logoResId = getCarrierLogoResId(carrier);
        if (logoResId != 0) {
            // The toolbar is blue in both themes, so it always takes the logos made for dark
            // backgrounds. Logos can be PNGs or XML drawables (e.g. the combined ACS + Skroutz one)
            Configuration night = new Configuration(getResources().getConfiguration());
            night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            Drawable logo = ContextCompat.getDrawable(createConfigurationContext(night), logoResId);
            if (logo != null && logo.getIntrinsicWidth() > 0 && logo.getIntrinsicHeight() > 0) {
                float density = getResources().getDisplayMetrics().density;
                int maxHeight = (int) (32 * density);
                int maxWidth = (int) (40 * density);

                float scale = Math.min(
                        (float) maxHeight / logo.getIntrinsicHeight(),
                        (float) maxWidth / logo.getIntrinsicWidth());
                int targetWidth = Math.max(1, (int) (logo.getIntrinsicWidth() * scale));
                int targetHeight = Math.max(1, (int) (logo.getIntrinsicHeight() * scale));

                // Drawn at full size first: layers of combined logos have fixed sizes and would
                // be cut off in smaller bounds
                Bitmap full = Bitmap.createBitmap(logo.getIntrinsicWidth(), logo.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                logo.setBounds(0, 0, full.getWidth(), full.getHeight());
                logo.draw(new Canvas(full));
                Bitmap scaled = Bitmap.createScaledBitmap(full, targetWidth, targetHeight, true);
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
            case "acsSkroutzLastMile": return R.drawable.ic_carrier_acs_skroutz_last_mile;
            case "aiolos":          return R.drawable.ic_carrier_aiolos;
            case "boxNow":          return R.drawable.ic_carrier_boxnow;
            case "cleverPoint":     return R.drawable.ic_carrier_clever_point;
            case "cometHellas":     return R.drawable.ic_carrier_comet_hellas;
            case "courierCenter":   return R.drawable.ic_carrier_courier_center;
            case "delatolas":       return R.drawable.ic_carrier_delatolas;
            case "diakinisis":      return R.drawable.ic_carrier_diakinisis;
            case "easyMail":        return R.drawable.ic_carrier_easy_mail;
            case "elta":            return R.drawable.ic_carrier_elta;
            case "eshop":           return R.drawable.ic_carrier_eshop;
            case "plaisio":         return R.drawable.ic_carrier_plaisio;
            case "plaisioAiolos":   return R.drawable.ic_carrier_plaisio_aiolos;
            case "plaisioBoxNow":   return R.drawable.ic_carrier_plaisio_boxnow;
            case "plaisioDiakinisis": return R.drawable.ic_carrier_plaisio_diakinisis;
            case "plaisioEasyMail": return R.drawable.ic_carrier_plaisio_easy_mail;
            case "plaisioElta":     return R.drawable.ic_carrier_plaisio_elta;
            case "plaisioGeniki":   return R.drawable.ic_carrier_plaisio_geniki;
            case "plaisioSpeedex":  return R.drawable.ic_carrier_plaisio_speedex;
            case "geniki":          return R.drawable.ic_carrier_geniki;
            case "skroutzLastMile": return R.drawable.ic_carrier_skroutz_last_mile;
            case "speedex":         return R.drawable.ic_carrier_speedex;
            default:                return 0;
        }
    }

    /** Shows the carrier, marked when the user chose it, so a wrong choice is easy to spot. */
    private void showCarrier() {
        String carrier = getCarrier(true);
        if (databaseHelper.getChosenCarrier(tracking) != null) {
            carrierTv.setText(getString(R.string.carrier_chosen, carrier));
        } else {
            carrierTv.setText(carrier);
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
    public void editTracking(String trackingNumber, String newTrackingNumber, String newCustomName, String newChosenCarrier) {

        // remove spaces from start and end of input
        newTrackingNumber = newTrackingNumber.trim();
        if (newCustomName != null) {
            newCustomName = newCustomName.trim();
        }

        // Edit database (table + index) if changed
        if (newTrackingNumber.equals(tracking) && Objects.equals(newCustomName, customName)
                && Objects.equals(newChosenCarrier, databaseHelper.getChosenCarrier(tracking))) {
            Toast.makeText(PackageDetailsActivity.this, R.string.no_information_changed, Toast.LENGTH_SHORT).show();
        } else {
            editTrackingDb(trackingNumber, newTrackingNumber, newCustomName, newChosenCarrier);
        }
    }

    private void editTrackingDb(String trackingNumber, String newTrackingNumber, String newCustomName, String newChosenCarrier) {
        // A new number (not just a name or letter case change) or another chosen carrier clears
        // the old history
        boolean numberChanged = !trackingNumber.equalsIgnoreCase(newTrackingNumber);
        boolean clearsHistory = databaseHelper.editClearsDetails(trackingNumber, newTrackingNumber, newChosenCarrier)
                && databaseHelper.getTrackingDetailsCount(trackingNumber) > 0;
        boolean updateData = databaseHelper.editTracking(trackingNumber, newTrackingNumber, newCustomName, newChosenCarrier);
        if (updateData) {
            if (clearsHistory) {
                Toast.makeText(PackageDetailsActivity.this, numberChanged
                        ? R.string.tracking_changed_history_cleared
                        : R.string.carrier_changed_history_cleared, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(PackageDetailsActivity.this, R.string.edited_successfully, Toast.LENGTH_SHORT).show();
            }
            this.tracking = newTrackingNumber;
            this.customName = newCustomName;
            showDetailsOnRecyclerView();
            showCarrier();
            updateActionBarLogo();
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
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null &&
               (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}