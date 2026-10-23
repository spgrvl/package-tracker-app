package com.spgrvl.packagetracker;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;


import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

public class AddNewDialog extends AppCompatDialogFragment {
    private static final String ARG_BARCODE = "barcode";
    private static final String STATE_CARRIER_POSITION = "carrierPosition";
    private EditText editTextTracking;
    private EditText editTextCustomName;
    private AddDialogListener listener;
    private DatabaseHelper databaseHelper;
    // Picker position of the chosen carrier, 0 to auto-detect it
    private int carrierPosition = 0;

    // Passed as an argument, since the system recreates the dialog with the no-argument
    // constructor after a configuration change such as a switch to dark mode
    public static AddNewDialog newInstance(String barcode) {
        AddNewDialog dialog = new AddNewDialog();
        Bundle args = new Bundle();
        args.putString(ARG_BARCODE, barcode);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String barcode = requireArguments().getString(ARG_BARCODE);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.add_new_dialog, null);

        builder.setView(view);

        databaseHelper = new DatabaseHelper(getContext());

        TextInputLayout trackingInputLayout = view.findViewById(R.id.textInputLayout_tracking);
        editTextTracking = view.findViewById(R.id.editText_tracking);
        editTextCustomName = view.findViewById(R.id.editText_customName);
        if (barcode != null) {
            editTextTracking.setText(barcode);
        }
        if (savedInstanceState != null) {
            carrierPosition = savedInstanceState.getInt(STATE_CARRIER_POSITION);
        }
        Carriers.setUpPicker(view.findViewById(R.id.textInputLayout_carrier), carrierPosition, picked -> carrierPosition = picked);

        editTextTracking.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (trackingInputLayout.getError() != null) {
                    trackingInputLayout.setError(null);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        MaterialButton buttonOk = view.findViewById(R.id.button_ok);
        MaterialButton buttonCancel = view.findViewById(R.id.button_cancel);
        MaterialButton buttonScan = view.findViewById(R.id.button_scan);

        Dialog dialog = builder.create();
        dialog.setOnShowListener(d -> applyStableWindowSize(dialog));

        buttonScan.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), BarcodeScanActivity.class));
            dismiss();
        });
        buttonCancel.setOnClickListener(v -> dismiss());
        buttonOk.setOnClickListener(v -> {
            String tracking = editTextTracking.getText().toString().trim();
            if (tracking.isEmpty()) {
                trackingInputLayout.setError(getString(R.string.toast_empty_tracking));
                return;
            }
            if (databaseHelper.findTracking(tracking) != null) {
                trackingInputLayout.setError(getString(R.string.toast_tracking_exists));
                return;
            }
            String customName = editTextCustomName.getText().toString();
            if (customName.isEmpty()) {
                customName = null;
            }
            listener.submitTracking(tracking, customName, Carriers.atPickerPosition(carrierPosition));
            dismiss();
        });

        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CARRIER_POSITION, carrierPosition);
    }

    private void applyStableWindowSize(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setWindowAnimations(0);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int horizontalInset = (int) (20 * getResources().getDisplayMetrics().density);
        View decor = window.getDecorView();
        decor.setPadding(horizontalInset, decor.getPaddingTop(), horizontalInset, decor.getPaddingBottom());
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            applyStableWindowSize(dialog);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        listener = (AddDialogListener) context;
    }

    public interface AddDialogListener {
        void submitTracking(String tracking, String customName, String chosenCarrier);
    }
}
