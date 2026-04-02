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
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

import java.util.ArrayList;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

public class AddNewDialog extends AppCompatDialogFragment {
    private final String barcode;
    private EditText editTextTracking;
    private EditText editTextCustomName;
    private AddDialogListener listener;
    private DatabaseHelper databaseHelper;

    public AddNewDialog(String barcode) {
        this.barcode = barcode;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
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
            ArrayList<String> existing = databaseHelper.getTrackingNumbers(false);
            existing.addAll(databaseHelper.getTrackingNumbers(true));
            if (existing.contains(tracking)) {
                trackingInputLayout.setError(getString(R.string.toast_tracking_exists));
                return;
            }
            String customName = editTextCustomName.getText().toString();
            if (customName.isEmpty()) {
                customName = null;
            }
            listener.submitTracking(tracking, customName);
            dismiss();
        });

        return dialog;
    }

    private void applyStableWindowSize(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setWindowAnimations(0);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        void submitTracking(String tracking, String customName);
    }
}
