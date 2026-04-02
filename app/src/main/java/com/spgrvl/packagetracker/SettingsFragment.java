package com.spgrvl.packagetracker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.opencsv.CSVReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String PREF_NOTIF = "pref_notif";
    public static final String PREF_NOTIF_INTERVAL = "pref_notif_interval";
    public static final String PREF_LANGUAGE = "pref_language";
    public static final String PREF_CLIPBOARD = "pref_clipboard";
    public static final String PREF_THEME = "pref_theme";
    public static final String BACKUP_BUTTON = "backup_button";
    public static final String RESTORE_BUTTON = "restore_button";
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private ActivityResultLauncher<Intent> backupLauncher;
    private ActivityResultLauncher<Intent> restoreLauncher;
    private Menu menu;
    private boolean shouldShowRestartButton = false;
    DatabaseHelper databaseHelper = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setHasOptionsMenu(true);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            writeBackupToUri(uri);
                        }
                    }
                });

        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            readBackupFromUri(uri);
                        }
                    }
                });

        preferenceChangeListener = (sharedPreferences, key) -> {
            if (key.equals(PREF_NOTIF_INTERVAL) || key.equals(PREF_LANGUAGE) || key.equals(PREF_CLIPBOARD) || key.equals(PREF_THEME)) {
                Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                shouldShowRestartButton = true;
                if (menu != null) {
                    menu.findItem(R.id.restart_button).setVisible(true);
                }
            }
            if (key.equals(PREF_NOTIF_INTERVAL)) {
                Preference notifIntervalPref = findPreference(key);
                ListPreference listPref = (ListPreference) notifIntervalPref;
                Objects.requireNonNull(notifIntervalPref).setSummary(listPref.getEntry());
            }
            if (key.equals(PREF_NOTIF)) {
                boolean isSwitchEnabled = sharedPreferences.getBoolean(key, false);
                if (isSwitchEnabled) {
                    // Check for notifications permission
                    if (ContextCompat.checkSelfPermission(requireActivity(), android.Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                        // Show information if Notification Permission got declined
                        Toast.makeText(requireActivity(), R.string.notifications_permission_denied_info, Toast.LENGTH_LONG).show();
                        disableNotifPref(key);
                        shouldShowRestartButton = true;
                        if (menu != null) {
                            menu.findItem(R.id.restart_button).setVisible(true);
                        }
                        Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                        shouldShowRestartButton = true;
                        if (menu != null) {
                            menu.findItem(R.id.restart_button).setVisible(true);
                        }
                    }
                } else {
                    Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                    shouldShowRestartButton = true;
                    if (menu != null) {
                        menu.findItem(R.id.restart_button).setVisible(true);
                    }
                }
            }
        };

        Preference backup_button = findPreference(BACKUP_BUTTON);
        Objects.requireNonNull(backup_button).setOnPreferenceClickListener(preference -> {
            exportCSV();
            return true;
        });

        Preference restore_button = findPreference(RESTORE_BUTTON);
        Objects.requireNonNull(restore_button).setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.sure_confirmation)
                    .setMessage(R.string.restore_confirmation_msg)
                    .setPositiveButton(R.string.yes, (dialog, which) -> importCSV())
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });
    }

    private void disableNotifPref(String key) {
        // disable the notifications preference (visual only) and redirect to app info
        Preference notifPref = findPreference(key);
        if (notifPref != null) {
            notifPref.setEnabled(false);
        }

        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getActivity().getPackageName(), null);
        intent.setData(uri);
        getActivity().startActivity(intent);
    }

    private void exportCSV() {
        String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String fileName = "greek_package_tracker_" + date + ".bak";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        backupLauncher.launch(intent);
    }

    private void writeBackupToUri(Uri uri) {
        List<TrackingIndexModel> recordsList = databaseHelper.getAllTracking();
        StringBuilder csvContent = new StringBuilder();
        for (int i = 0; i < recordsList.size(); i++) {
            csvContent.append(Objects.toString(recordsList.get(i).getTracking(), "").replace(",", "."));
            csvContent.append(",");
            csvContent.append(Objects.toString(recordsList.get(i).getCustomName(), "").replace(",", "."));
            csvContent.append(",");
            csvContent.append(Objects.toString(recordsList.get(i).isCompleted(), ""));
            csvContent.append(",");
            csvContent.append(Objects.toString(recordsList.get(i).getCreated(), ""));
            csvContent.append("\n");
        }

        try (OutputStream os = requireActivity().getContentResolver().openOutputStream(uri)) {
            if (os != null) {
                os.write(csvContent.toString().getBytes());
                Toast.makeText(getContext(), R.string.backup_exported_to, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), R.string.backup_error, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.backup_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void importCSV() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        restoreLauncher.launch(intent);
    }

    private void readBackupFromUri(Uri uri) {
        try (InputStreamReader isr = new InputStreamReader(
                requireActivity().getContentResolver().openInputStream(uri));
             CSVReader csvReader = new CSVReader(isr)) {

            String[] nextLine;
            while ((nextLine = csvReader.readNext()) != null) {
                String tracking = nextLine[0];
                String customName = nextLine[1];
                Boolean completed = Boolean.valueOf(nextLine[2]);
                String created = nextLine[3];

                if (customName.equals("")) {
                    customName = null;
                }
                databaseHelper.addNewTracking(tracking, customName, completed, created);
            }
            Toast.makeText(getContext(), R.string.backup_restored, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.restore_error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        Preference notifIntervalPref = findPreference(PREF_NOTIF_INTERVAL);
        ListPreference listPref = (ListPreference) notifIntervalPref;
        Objects.requireNonNull(notifIntervalPref).setSummary(listPref.getEntry());
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        databaseHelper = new DatabaseHelper(getContext());
    }

    @Override
    public void onPause() {
        super.onPause();

        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        this.menu = menu;
        inflater.inflate(R.menu.settings_action_bar, menu);
        menu.findItem(R.id.restart_button).setVisible(shouldShowRestartButton);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.restart_button).setVisible(shouldShowRestartButton);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.restart_button) {
            shouldShowRestartButton = false;
            triggerRebirth(requireContext());
        } else if (item.getItemId() == android.R.id.home) {
            requireActivity().finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public static void triggerRebirth(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }
}