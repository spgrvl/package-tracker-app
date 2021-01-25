package com.spgrvl.packagetracker;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

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
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.add_new_dialog, null);

        builder.setView(view)
                .setTitle(R.string.edit_package)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String newTracking = editTextTracking.getText().toString();
                        String newCustomName = editTextCustomName.getText().toString();
                        if (newCustomName.isEmpty()) {
                            newCustomName = null;
                        }
                        listener.editTracking(tracking, newTracking, newCustomName);
                    }
                });
        editTextTracking = view.findViewById(R.id.editText_tracking);
        editTextCustomName = view.findViewById(R.id.editText_customName);

        DatabaseHelper databaseHelper = new DatabaseHelper(getContext());
        ArrayList<String> indexEntry = databaseHelper.getIndexEntry(tracking);
        String dbCustomName = indexEntry.get(4);

        editTextCustomName.setText(dbCustomName);
        editTextTracking.setText(tracking);

        return builder.create();
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