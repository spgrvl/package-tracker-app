package com.spgrvl.packagetracker;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AddNewDialog extends AppCompatDialogFragment {
    private final String barcode;
    private EditText editTextTracking;
    private EditText editTextCustomName;
    private AddDialogListener listener;

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

        editTextTracking = view.findViewById(R.id.editText_tracking);
        editTextCustomName = view.findViewById(R.id.editText_customName);
        if (barcode != null) {
            editTextTracking.setText(barcode);
        }

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
            String tracking = editTextTracking.getText().toString();
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
