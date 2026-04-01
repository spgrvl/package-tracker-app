package com.spgrvl.packagetracker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CustomIndexRvAdapter extends RecyclerView.Adapter<CustomIndexRvAdapter.ViewHolder> implements Filterable {

    private List<TrackingIndexModel> listData;
    private List<TrackingIndexModel> listDataFiltered;
    private final LayoutInflater layoutInflater;
    private final Context context;
    private final MainActivity mainActivity;
    private final DatabaseHelper databaseHelper;

    public CustomIndexRvAdapter(Context context, List<TrackingIndexModel> listData) {
        this.listData = new ArrayList<>(listData);
        this.listDataFiltered = new ArrayList<>(listData);
        this.context = context;
        layoutInflater = LayoutInflater.from(context);
        this.mainActivity = (MainActivity) context;
        databaseHelper = new DatabaseHelper(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(layoutInflater.inflate(R.layout.main_view_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackingIndexModel tracking = this.listDataFiltered.get(position);
        
        // Set package title in one line: "Name - Tracking"
        String customName = tracking.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            holder.packageName.setText(customName + " - " + tracking.getTracking());
        } else {
            holder.packageName.setText(tracking.getTracking());
        }
        holder.tracking.setText(tracking.getTracking());
        holder.tracking.setVisibility(View.GONE);
        
        // Set status based on completion (delivered: green text + green left stripe; active: blue stripe)
        int accentBlue = ContextCompat.getColor(context, R.color.accent_blue);
        int deliveredGreen = ContextCompat.getColor(context, R.color.success_green);
        if (tracking.isCompleted()) {
            holder.status.setText(R.string.delivered);
            holder.status.setTextColor(deliveredGreen);
            holder.statusIcon.setVisibility(View.GONE);
            holder.accentBorder.setVisibility(View.VISIBLE);
            holder.accentBorder.setBackgroundColor(deliveredGreen);
            holder.carrierLogo.setImageResource(R.drawable.ic_package_completed);
        } else {
            if (isDeliveredStatusText(tracking.getLastUpdate())) {
                // Display-only delivered state: stays in Active tab unless completed flag is set elsewhere.
                holder.status.setText(R.string.delivered);
                holder.status.setTextColor(deliveredGreen);
            } else if (isOutForDeliveryStatus(tracking.getLastUpdate())) {
                holder.status.setText(R.string.out_for_delivery);
                holder.status.setTextColor(accentBlue);
            } else {
                holder.status.setText(R.string.in_transit);
                holder.status.setTextColor(accentBlue);
            }
            holder.statusIcon.setVisibility(View.GONE);
            holder.accentBorder.setVisibility(View.VISIBLE);
            holder.accentBorder.setBackgroundColor(accentBlue);
            holder.carrierLogo.setImageResource(R.drawable.ic_package_waiting);
        }

        // Set updated time
        holder.updatedLayout.setVisibility(View.VISIBLE);
        if (tracking.getUpdated().equals("Never")) {
            holder.updated.setText(R.string.never);
        } else if (DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())).equals("0 minutes ago") ||
                DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())).equals("Πριν από 0 λεπτά")) {
            holder.updated.setText(R.string.less_than_a_minute_ago);
        } else {
            holder.updated.setText(DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())));
        }
        
        holder.daysSince.setText(getDaysSinceText(tracking.getCreated()));
        holder.carrierSmall.setText(getCarrierShortText(tracking.getCarrier()));
        holder.lastUpdate.setText(getLastUpdateText(tracking.getLastUpdate()));

        // Show/hide checkbox based on selection mode
        if (mainActivity.isInSelectionMode) {
            holder.checkbox.setVisibility(View.VISIBLE);
            holder.checkbox.setChecked(tracking.isSelected());
        } else {
            holder.checkbox.setVisibility(View.GONE);
        }

        // change the font style depending on package read status
        applyReadStatus(holder, tracking);

        // Called when user clicks RecyclerView items
        holder.parentView.setOnClickListener(v -> {
            if (!mainActivity.isInSelectionMode) {
                openTrackingDetails(tracking.getTracking());
            } else {
                mainActivity.selectItem(holder.checkbox, tracking.getTracking());
            }
        });

        // Called when user long clicks RecyclerView items
        holder.parentView.setOnLongClickListener(v -> {
            mainActivity.startSelection(position, tracking.getTracking());
            return true;
        });

        // set checkbox status
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(tracking.isSelected());

        holder.checkbox.setOnCheckedChangeListener((checkboxView, isChecked) -> tracking.setSelected(isChecked));

        // detect if in multiple selection mode
        if (mainActivity.isInSelectionMode) {
            if (mainActivity.position == position) {
                holder.checkbox.setChecked(true);
                mainActivity.position = -1;
            }
            holder.carrierLogo.setVisibility(View.GONE);
            holder.checkbox.setVisibility(View.VISIBLE);
        } else {
            holder.checkbox.setVisibility(View.GONE);
            holder.carrierLogo.setVisibility(View.VISIBLE);
            for (TrackingIndexModel t : listData) {
                t.setSelected(false);
            }
        }
    }

    @Override
    public int getItemCount() {
        return listDataFiltered.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void selectAll() {
        boolean allFilteredSelected = !listDataFiltered.isEmpty();
        for (TrackingIndexModel t : listDataFiltered) {
            if (!t.isSelected() || !mainActivity.selectionList.contains(t.getTracking())) {
                allFilteredSelected = false;
                break;
            }
        }

        boolean shouldSelect = !allFilteredSelected;
        for (TrackingIndexModel t : listDataFiltered) {
            t.setSelected(shouldSelect);
            if (shouldSelect) {
                if (!mainActivity.selectionList.contains(t.getTracking())) {
                    mainActivity.selectionList.add(t.getTracking());
                }
            } else {
                mainActivity.selectionList.remove(t.getTracking());
            }
        }
        mainActivity.counter = mainActivity.selectionList.size();
        notifyDataSetChanged();
        mainActivity.updateToolbarText();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView packageName;
        final TextView tracking;
        final TextView status;
        final TextView updated;
        final TextView daysSince;
        final TextView carrierSmall;
        final TextView lastUpdate;
        final CheckBox checkbox;
        final ImageView carrierLogo;
        final ImageView statusIcon;
        final View accentBorder;
        final View updatedLayout;
        private final View parentView;

        public ViewHolder(@NonNull View view) {
            super(view);
            this.parentView = view;
            this.packageName = view.findViewById(R.id.packageNameTv);
            this.tracking = view.findViewById(R.id.trackingTv);
            this.status = view.findViewById(R.id.statusTv);
            this.updated = view.findViewById(R.id.updatedTv);
            this.daysSince = view.findViewById(R.id.daysSinceTv);
            this.carrierSmall = view.findViewById(R.id.carrierSmallTv);
            this.lastUpdate = view.findViewById(R.id.lastUpdateTv);
            this.checkbox = view.findViewById(R.id.checkbox);
            this.carrierLogo = view.findViewById(R.id.carrierLogo);
            this.statusIcon = view.findViewById(R.id.statusIcon);
            this.accentBorder = view.findViewById(R.id.accentBorder);
            this.updatedLayout = view.findViewById(R.id.updatedLayout);
        }
    }

    /**
     * Matches carrier last-status text against known phrases (English/Greek) so list status
     * does not depend on a single hardcoded English substring.
     */
    private boolean isOutForDeliveryStatus(String lastUpdate) {
        if (lastUpdate == null || lastUpdate.trim().isEmpty()) {
            return false;
        }
        String normalized = lastUpdate.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("status:")) {
            normalized = normalized.substring("status:".length()).trim();
        }
        String[] keywords = context.getResources().getStringArray(R.array.status_keywords_out_for_delivery);
        for (String kw : keywords) {
            if (kw != null && !kw.isEmpty() && normalized.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeliveredStatusText(String lastUpdate) {
        if (lastUpdate == null || lastUpdate.trim().isEmpty()) {
            return false;
        }
        String normalized = lastUpdate.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("status:")) {
            normalized = normalized.substring("status:".length()).trim();
        }
        return normalized.contains("delivered")
                || normalized.contains("παραδόθηκε")
                || normalized.contains("παραδοθηκε");
    }

    private String getDaysSinceText(String created) {
        if (created == null || created.trim().isEmpty()) {
            return "";
        }
        try {
            long millisSinceCreated = System.currentTimeMillis() - Long.parseLong(created);
            long days = TimeUnit.DAYS.convert(millisSinceCreated, TimeUnit.MILLISECONDS);
            return days + " D";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String getCarrierShortText(String carrierCode) {
        String carrierName = context.getString(R.string.unknown);
        if (carrierCode != null && !carrierCode.trim().isEmpty()) {
            int resId = context.getResources().getIdentifier(carrierCode, "string", context.getPackageName());
            if (resId != 0) {
                carrierName = context.getString(resId);
            }
        }
        return carrierName;
    }

    private String getLastUpdateText(String lastUpdate) {
        if (lastUpdate == null || lastUpdate.trim().isEmpty() || lastUpdate.equals("Status: None")) {
            return context.getString(R.string.status_none);
        }
        return lastUpdate;
    }

    private void applyReadStatus(ViewHolder holder, TrackingIndexModel tracking) {
        boolean isUnread = databaseHelper.getUnreadStatus(tracking.getTracking());
        if (isUnread) {
            holder.packageName.setTypeface(Typeface.DEFAULT_BOLD);
            holder.tracking.setTypeface(Typeface.DEFAULT_BOLD);
            holder.daysSince.setTypeface(Typeface.DEFAULT_BOLD);
            holder.carrierSmall.setTypeface(Typeface.DEFAULT_BOLD);
            holder.status.setTypeface(Typeface.DEFAULT_BOLD);
            holder.updated.setTypeface(Typeface.DEFAULT_BOLD);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            holder.packageName.setTypeface(Typeface.DEFAULT);
            holder.tracking.setTypeface(Typeface.DEFAULT);
            holder.daysSince.setTypeface(Typeface.DEFAULT);
            holder.carrierSmall.setTypeface(Typeface.DEFAULT);
            holder.status.setTypeface(Typeface.DEFAULT);
            holder.updated.setTypeface(Typeface.DEFAULT);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT);
        }
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(context, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        context.startActivity(myIntent);
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<TrackingIndexModel> filteredList = new ArrayList<>();

                if (constraint == null || constraint.length() == 0) {
                    filteredList.addAll(listData);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    for (TrackingIndexModel item : listData) {
                        if (item.getTracking().toLowerCase().contains(filterPattern) ||
                            (item.getCustomName() != null && item.getCustomName().toLowerCase().contains(filterPattern))) {
                            filteredList.add(item);
                        }
                    }
                }

                results.values = filteredList;
                results.count = filteredList.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                listDataFiltered.clear();
                listDataFiltered.addAll((List<TrackingIndexModel>) results.values);
                notifyDataSetChanged();
            }
        };
    }
}