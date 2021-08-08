package com.spgrvl.packagetracker;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String PREF_NOTIF = "pref_notif";
    public static final String PREF_NOTIF_INTERVAL = "pref_notif_interval";
    public static final String PREF_LANGUAGE = "pref_language";
    public static final String PREF_CLIPBOARD = "pref_clipboard";
    public static final String PREF_THEME = "pref_theme";
    public static final String BACKUP_BUTTON = "backup_button";
    public static final String RESTORE_BUTTON = "restore_button";
    public static final String FOLDER_NAME = "Backup";
    public static final String FILE_NAME = "packages.bak";
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private Menu menu;
    DatabaseHelper databaseHelper = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setHasOptionsMenu(true);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        preferenceChangeListener = (sharedPreferences, key) -> {
            if (key.equals(PREF_NOTIF) || key.equals(PREF_NOTIF_INTERVAL) || key.equals(PREF_LANGUAGE) || key.equals(PREF_CLIPBOARD) || key.equals(PREF_THEME)) {
                Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                menu.findItem(R.id.restart_button).setVisible(true);
            }
            if (key.equals(PREF_NOTIF_INTERVAL)) {
                Preference notifIntervalPref = findPreference(key);
                ListPreference listPref = (ListPreference) notifIntervalPref;
                Objects.requireNonNull(notifIntervalPref).setSummary(listPref.getEntry());
            }
        };

        Preference backup_button = findPreference(BACKUP_BUTTON);
        Objects.requireNonNull(backup_button).setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.sure_confirmation)
                    .setMessage(R.string.backup_confirmation_msg)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        if (isExtStorageRW()) {
                            exportCSV();
                        } else {
                            Toast.makeText(getContext(), R.string.backup_error, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });

        Preference restore_button = findPreference(RESTORE_BUTTON);
        Objects.requireNonNull(restore_button).setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.sure_confirmation)
                    .setMessage(R.string.restore_confirmation_msg)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        if (isExtStorageRW()) {
                            importCSV();
                        } else {
                            Toast.makeText(getContext(), R.string.restore_error, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });
    }

    private boolean isExtStorageRW() {
        // check if storage is available for RW operations
        String extStorageState = Environment.getExternalStorageState();
        return extStorageState.equals(Environment.MEDIA_MOUNTED);
    }

    private void exportCSV() {
        // complete filepath
        String filePathAndName = requireActivity().getExternalFilesDir(FOLDER_NAME) + "/" + FILE_NAME;

        // get records from index table
        List<TrackingIndexModel> recordsList = databaseHelper.getAllTracking();
        StringBuilder csvContent = new StringBuilder();
        for (int i = 0; i < recordsList.size(); i++) {
            csvContent.append(Objects.toString(recordsList.get(i).getTracking(), "").replace(",", "."));
            csvContent.append(",");
            csvContent.append(Objects.toString(recordsList.get(i).getCustomName(), "").replace(",", "."));
            csvContent.append(",");
            csvContent.append(Objects.toString(recordsList.get(i).isCompleted(), ""));
            csvContent.append("\n");
        }

        // write csv file
        File bakFile = new File(requireActivity().getExternalFilesDir(FOLDER_NAME), (FILE_NAME));
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(bakFile);
            fos.write(csvContent.toString().getBytes());
            Toast.makeText(getContext(), getString(R.string.backup_exported_to) + filePathAndName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), R.string.backup_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void importCSV() {
        // use same path and file name as backup
        String filePathAndName = requireActivity().getExternalFilesDir(FOLDER_NAME) + "/" + FILE_NAME;

        File csvFile = new File(filePathAndName);

        // check if exists
        if (csvFile.exists()) {
            // backup exists
            try {
                CSVReader csvReader = new CSVReader(new FileReader(csvFile.getAbsolutePath()));

                String[] nextLine;
                while ((nextLine = csvReader.readNext()) != null) {
                    String tracking = nextLine[0];
                    String customName = nextLine[1];
                    Boolean completed = Boolean.valueOf(nextLine[2]);

                    if (customName.equals("")) {
                        customName = null;
                    }
                    databaseHelper.addNewTracking(tracking, customName, completed);
                }
                Toast.makeText(getContext(), R.string.backup_restored, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), R.string.restore_error, Toast.LENGTH_SHORT).show();
            }
        } else {
            // backup does not exist
            Toast.makeText(getContext(), R.string.no_backup_found, Toast.LENGTH_SHORT).show();
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
        menu.findItem(R.id.restart_button).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.restart_button) {
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