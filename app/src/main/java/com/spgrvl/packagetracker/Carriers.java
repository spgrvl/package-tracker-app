package com.spgrvl.packagetracker;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.IntConsumer;

/** The carriers a user can choose for a package instead of having it auto-detected. */
final class Carriers {
    // Keys as used by UpdateTrackingDetails.getWebsite(), named after their string resources.
    // Combined carriers (e.g. acsSkroutzLastMile, plaisioBoxNow) are results of tracking, not choices.
    static final String[] KEYS = {
            "acs", "aiolos", "bizCourier", "boxNow", "cleverPoint", "cometHellas", "courierCenter", "delatolas",
            "diakinisis", "easyMail", "elta", "eshop", "geniki", "ikea", "packMan", "plaisio", "skroutzLastMile", "speedex"};

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

    /** Fills a carrier field, shows the given position and reports the position picked. */
    static void setUpPicker(TextInputLayout field, int position, IntConsumer onPicked) {
        MaterialAutoCompleteTextView dropdown = (MaterialAutoCompleteTextView) field.getEditText();
        String[] labels = pickerLabels(dropdown.getContext());
        dropdown.setAdapter(new PickerAdapter(dropdown.getContext(), labels));
        // Without filtering, so that the whole list stays available
        dropdown.setText(labels[position], false);
        showPickedIcon(field, position);
        dropdown.setOnItemClickListener((parent, view, picked, id) -> {
            showPickedIcon(field, picked);
            onPicked.accept(picked);
        });
    }

    /** The chosen carrier's logo before the field, or the generic truck icon for Auto-detect. */
    private static void showPickedIcon(TextInputLayout field, int position) {
        String key = atPickerPosition(position);
        if (key == null) {
            field.setStartIconDrawable(R.drawable.ic_local_shipping);
            field.setStartIconTintList(ContextCompat.getColorStateList(field.getContext(), R.color.text_secondary));
        } else {
            field.setStartIconDrawable(fieldLogo(field.getContext(), logoResId(key)));
            // Logos keep their own colours
            field.setStartIconTintList(null);
        }
    }

    /** A logo scaled to fit the field's icon space (as wide as the list's, at most), keeping its proportions. */
    private static Drawable fieldLogo(Context context, int resId) {
        Drawable logo = ContextCompat.getDrawable(context, resId);
        float density = context.getResources().getDisplayMetrics().density;
        float maxWidth = 40 * density, maxHeight = 24 * density;
        float scale = Math.min(maxWidth / logo.getIntrinsicWidth(), maxHeight / logo.getIntrinsicHeight());
        LayerDrawable fitted = new LayerDrawable(new Drawable[]{logo});
        fitted.setLayerSize(0, Math.round(logo.getIntrinsicWidth() * scale), Math.round(logo.getIntrinsicHeight() * scale));
        return fitted;
    }

    /** Picker entries with each carrier's logo before its name, and a search icon for Auto-detect. */
    private static final class PickerAdapter extends ArrayAdapter<String> {
        PickerAdapter(Context context, String[] labels) {
            super(context, R.layout.carrier_picker_item, R.id.carrier_name, labels);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            ImageView logo = view.findViewById(R.id.carrier_logo);
            String key = atPickerPosition(position);
            logo.setImageResource(key != null ? logoResId(key) : R.drawable.ic_search);
            return view;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            // The field shows the chosen label, which mustn't narrow the list down to it
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    return null;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }

    /** The logo of a tracked carrier, combined ones included, or 0 when there is none. */
    static int logoResId(String carrier) {
        if (carrier == null) return 0;
        switch (carrier) {
            case "acs":             return R.drawable.ic_carrier_acs;
            case "acsSkroutzLastMile": return R.drawable.ic_carrier_acs_skroutz_last_mile;
            case "aiolos":          return R.drawable.ic_carrier_aiolos;
            case "bizCourier":      return R.drawable.ic_carrier_biz_courier;
            case "boxNow":          return R.drawable.ic_carrier_boxnow;
            case "cleverPoint":     return R.drawable.ic_carrier_clever_point;
            case "cometHellas":     return R.drawable.ic_carrier_comet_hellas;
            case "courierCenter":   return R.drawable.ic_carrier_courier_center;
            case "delatolas":       return R.drawable.ic_carrier_delatolas;
            case "diakinisis":      return R.drawable.ic_carrier_diakinisis;
            case "easyMail":        return R.drawable.ic_carrier_easy_mail;
            case "elta":            return R.drawable.ic_carrier_elta;
            case "eshop":           return R.drawable.ic_carrier_eshop;
            case "plaisio":         return R.drawable.ic_carrier_plaisio;
            case "plaisioAiolos":   return R.drawable.ic_carrier_plaisio_aiolos;
            case "plaisioBoxNow":   return R.drawable.ic_carrier_plaisio_boxnow;
            case "plaisioDiakinisis": return R.drawable.ic_carrier_plaisio_diakinisis;
            case "plaisioEasyMail": return R.drawable.ic_carrier_plaisio_easy_mail;
            case "plaisioElta":     return R.drawable.ic_carrier_plaisio_elta;
            case "plaisioGeniki":   return R.drawable.ic_carrier_plaisio_geniki;
            case "plaisioSpeedex":  return R.drawable.ic_carrier_plaisio_speedex;
            case "geniki":          return R.drawable.ic_carrier_geniki;
            case "ikea":            return R.drawable.ic_carrier_ikea;
            case "packMan":         return R.drawable.ic_carrier_pack_man;
            case "skroutzLastMile": return R.drawable.ic_carrier_skroutz_last_mile;
            case "speedex":         return R.drawable.ic_carrier_speedex;
            default:                return 0;
        }
    }
}
