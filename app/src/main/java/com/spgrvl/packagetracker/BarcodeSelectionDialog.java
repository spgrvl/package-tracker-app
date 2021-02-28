package com.spgrvl.packagetracker;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class BarcodeSelectionDialog extends DialogFragment {

    int position = 0; //default selected position

    public interface SingleChoiceListener {
        void onPositiveButtonClicked(String[] list, int position);
    }

    SingleChoiceListener myListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            myListener = (SingleChoiceListener) context;
        } catch (Exception e) {
            throw new ClassCastException(requireActivity().toString() + " SingleChoiceListener must implemented");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        String[] list = requireArguments().getStringArray("barcodes");

        builder.setTitle(R.string.choose_barcode)
                .setSingleChoiceItems(list, position, (dialogInterface, i) -> position = i)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> myListener.onPositiveButtonClicked(list, position))
                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
                });

        return builder.create();
    }

    public static BarcodeSelectionDialog newInstance(String[] barcodes) {
        BarcodeSelectionDialog dialog = new BarcodeSelectionDialog();

        Bundle args = new Bundle();
        args.putStringArray("barcodes", barcodes);
        dialog.setArguments(args);

        return dialog;
    }
}