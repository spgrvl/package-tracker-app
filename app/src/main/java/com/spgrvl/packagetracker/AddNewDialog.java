package com.spgrvl.packagetracker;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

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
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.add_new_dialog, null);

        builder.setView(view)
                .setTitle("New package")
                .setNeutralButton("scan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(getActivity(), BarcodeScanActivity.class);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String tracking = editTextTracking.getText().toString();
                        String customName = editTextCustomName.getText().toString();
                        if (customName.isEmpty()) {
                            customName = null;
                        }
                        listener.submitTracking(tracking, customName);
                    }
                });
        editTextTracking = view.findViewById(R.id.editText_tracking);
        editTextCustomName = view.findViewById(R.id.editText_customName);
        if (barcode != null){
            editTextTracking.setText(barcode);
        }
        return builder.create();
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