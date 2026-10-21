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
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.BufferedReader;
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
    public static final String VERSION_INFO = "version_info";
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private ActivityResultLauncher<Intent> backupLauncher;
    private ActivityResultLauncher<Intent> restoreLauncher;
    private boolean shouldShowRestartButton = false;
    DatabaseHelper databaseHelper = null;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Show app version in the About section
        Objects.requireNonNull((Preference) findPreference(VERSION_INFO)).setSummary(getAppVersion());

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
            if (key.equals(PREF_LANGUAGE)) {
                // Applies immediately; open activities are recreated in the new language
                Localization.applyLanguagePreference(requireContext());
            }
            if (key.equals(PREF_THEME)) {
                // Applies immediately; open activities are recreated in the new theme
                App.applyThemePreference(requireContext());
            }
            if (key.equals(PREF_NOTIF_INTERVAL) || key.equals(PREF_CLIPBOARD)) {
                Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                showRestartButton();
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
                        showRestartButton();
                        Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                        showRestartButton();
                    }
                } else {
                    Toast.makeText(getContext(), R.string.changes_restart_toast, Toast.LENGTH_LONG).show();
                    showRestartButton();
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
            new MaterialAlertDialogBuilder(requireActivity())
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
            csvContent.append(",");
            // Empty when auto-detected; older versions ignore this field when restoring
            csvContent.append(Objects.toString(recordsList.get(i).getChosenCarrier(), ""));
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

    /**
     * Fields of a backup line, or null if it isn't one. Backups replace commas in fields with
     * dots, so lines are split on commas; quotes are plain text, not CSV quoting, since custom
     * names may contain them (e.g. 5" monitor).
     */
    static String[] parseBackupLine(String line) {
        String[] fields = line.split(",", -1);
        return fields.length >= 4 ? fields : null;
    }

    /** The chosen carrier of a parsed backup line, null to auto-detect (older backups have none). */
    static String chosenCarrierOf(String[] fields) {
        return fields.length >= 5 && Carriers.isChoosable(fields[4]) ? fields[4] : null;
    }

    private void readBackupFromUri(Uri uri) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                requireActivity().getContentResolver().openInputStream(uri)))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] nextLine = parseBackupLine(line);
                if (nextLine == null) {
                    continue;
                }
                String tracking = nextLine[0];
                String customName = nextLine[1];
                Boolean completed = Boolean.valueOf(nextLine[2]);
                String created = nextLine[3];

                if (customName.equals("")) {
                    customName = null;
                }
                databaseHelper.addNewTracking(tracking, customName, completed, created, chosenCarrierOf(nextLine));
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
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.settings_action_bar, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                menu.findItem(R.id.restart_button).setVisible(shouldShowRestartButton);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.restart_button) {
                    shouldShowRestartButton = false;
                    triggerRebirth(requireContext());
                    return true;
                } else if (menuItem.getItemId() == android.R.id.home) {
                    requireActivity().finish();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void showRestartButton() {
        shouldShowRestartButton = true;
        requireActivity().invalidateMenu();
    }

    private String getAppVersion() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.unknown);
        }
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