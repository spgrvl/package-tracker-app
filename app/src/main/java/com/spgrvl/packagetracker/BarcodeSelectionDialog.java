package com.spgrvl.packagetracker;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class BarcodeSelectionDialog extends DialogFragment {

    int position = 0; //default selected position

    public interface SingleChoiceListener {
        void onPositiveButtonClicked(String[] list, int position);
        void onNegativeButtonClicked();
    }

    SingleChoiceListener myListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            myListener = (SingleChoiceListener) context;
        } catch (Exception e) {
            throw new ClassCastException(getActivity().toString() + " SingleChoiceListener must implemented");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        String[] list = getArguments().getStringArray("barcodes");

        builder.setTitle("Choose a barcode to add")
                .setSingleChoiceItems(list, position, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        position = i;
                    }
                })
                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        myListener.onPositiveButtonClicked(list, position);
                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        myListener.onNegativeButtonClicked();
                    }
                });

        return builder.create();
    }

    public static BarcodeSelectionDialog newInstance(String[] barcodes) {
        BarcodeSelectionDialog dialog = new BarcodeSelectionDialog ();

        Bundle args = new Bundle();
        args.putStringArray("barcodes", barcodes);
        dialog.setArguments(args);

        return dialog;
    }
}