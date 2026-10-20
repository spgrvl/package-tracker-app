package com.spgrvl.packagetracker;

import android.app.Dialog;
import android.content.Context;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

public class EditDialog extends AppCompatDialogFragment {
    private final String tracking;
    private EditText editTextTracking;
    private EditText editTextCustomName;
    private AddDialogListener listener;

    public EditDialog(String tracking) {
        this.tracking = tracking;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.add_new_dialog, null);

        builder.setView(view);
        ((TextView) view.findViewById(R.id.dialog_title)).setText(R.string.edit_package);
        ((TextView) view.findViewById(R.id.dialog_subtitle)).setText(R.string.edit_package_subtitle);

        TextInputLayout trackingInputLayout = view.findViewById(R.id.textInputLayout_tracking);
        editTextTracking = view.findViewById(R.id.editText_tracking);
        editTextCustomName = view.findViewById(R.id.editText_customName);
        view.findViewById(R.id.button_scan).setVisibility(View.GONE);

        DatabaseHelper databaseHelper = new DatabaseHelper(getContext());
        ArrayList<String> indexEntry = databaseHelper.getIndexEntry(tracking);
        String dbCustomName = indexEntry.get(4);
        editTextCustomName.setText(dbCustomName != null ? dbCustomName : "");
        editTextTracking.setText(tracking);

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

        Dialog dialog = builder.create();
        dialog.setOnShowListener(d -> applyStableWindowSize(dialog));

        buttonCancel.setOnClickListener(v -> dismiss());
        buttonOk.setOnClickListener(v -> {
            String newTracking = editTextTracking.getText().toString().trim();
            if (newTracking.isEmpty()) {
                trackingInputLayout.setError(getString(R.string.toast_empty_tracking));
                return;
            }
            // Changing only the letter case of the package's own number is allowed
            if (!newTracking.equalsIgnoreCase(tracking) && databaseHelper.findTracking(newTracking) != null) {
                trackingInputLayout.setError(getString(R.string.toast_tracking_exists));
                return;
            }
            String newCustomName = editTextCustomName.getText().toString();
            if (newCustomName.isEmpty()) {
                newCustomName = null;
            }
            listener.editTracking(tracking, newTracking, newCustomName);
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
        void editTracking(String tracking, String newTracking, String newCustomName);
    }
}
