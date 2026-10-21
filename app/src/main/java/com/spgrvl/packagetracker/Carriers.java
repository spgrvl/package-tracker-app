package com.spgrvl.packagetracker;

import android.content.Context;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.function.IntConsumer;

/** The carriers a user can choose for a package instead of having it auto-detected. */
final class Carriers {
    // Keys as used by UpdateTrackingDetails.getWebsite(), named after their string resources.
    // Combined carriers (e.g. acsSkroutzLastMile, plaisioBoxNow) are results of tracking, not choices.
    static final String[] KEYS = {
            "acs", "aiolos", "boxNow", "cleverPoint", "cometHellas", "courierCenter", "delatolas",
            "diakinisis", "easyMail", "elta", "eshop", "geniki", "plaisio", "skroutzLastMile", "speedex"};

    private Carriers() {
    }

    static boolean isChoosable(String key) {
        if (key == null) {
            return false;
        }
        for (String k : KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The carrier a tracked carrier was chosen as: combined carriers come from tracking ACS
     * (handed to Skroutz Last Mile) or a Plaisio order (handed to a courier).
     */
    static String chosenAs(String carrier) {
        if (carrier == null) {
            return null;
        }
        if (carrier.equals("acsSkroutzLastMile")) {
            return "acs";
        }
        if (carrier.startsWith("plaisio")) {
            return "plaisio";
        }
        return carrier;
    }

    /** Picker entries: "Auto-detect" first, then the carriers in KEYS order. */
    static String[] pickerLabels(Context context) {
        String[] labels = new String[KEYS.length + 1];
        labels[0] = context.getString(R.string.auto_detect);
        for (int i = 0; i < KEYS.length; i++) {
            labels[i + 1] = context.getString(context.getResources().getIdentifier(KEYS[i], "string", context.getPackageName()));
        }
        return labels;
    }

    /** The picker position of a chosen carrier, 0 (Auto-detect) for none. */
    static int pickerPosition(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** The chosen carrier at a picker position, null for Auto-detect. */
    static String atPickerPosition(int position) {
        return position > 0 && position <= KEYS.length ? KEYS[position - 1] : null;
    }

    /** Fills a carrier dropdown, shows the given position and reports the position picked. */
    static void setUpPicker(MaterialAutoCompleteTextView dropdown, int position, IntConsumer onPicked) {
        String[] labels = pickerLabels(dropdown.getContext());
        dropdown.setSimpleItems(labels);
        // Without filtering, so that the whole list stays available
        dropdown.setText(labels[position], false);
        dropdown.setOnItemClickListener((parent, view, picked, id) -> onPicked.accept(picked));
    }
}
