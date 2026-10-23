package com.spgrvl.packagetracker;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.util.TypedValue;
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
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CustomIndexRvAdapter extends RecyclerView.Adapter<CustomIndexRvAdapter.ViewHolder> implements Filterable {

    private List<TrackingIndexModel> listData;
    private List<TrackingIndexModel> listDataFiltered;
    private final LayoutInflater layoutInflater;
    private static final float STATUS_TEXT_SIZE_SP = 14;
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
        
        // Set status text, color, and left accent bar per delivery stage
        PackageStatus packageStatus = PackageStatus.of(context, tracking.isCompleted(), tracking.getLastUpdate());
        int statusColor = packageStatus.getColor(context);
        holder.status.setText(packageStatus.labelRes);
        fitStatusText(holder.status);
        if (tracking.isCompleted()) {
            holder.carrierLogo.setImageResource(R.drawable.ic_package_completed);
        } else if (packageStatus == PackageStatus.ISSUE) {
            holder.carrierLogo.setImageResource(R.drawable.ic_package_warning);
        } else {
            holder.carrierLogo.setImageResource(R.drawable.ic_package_waiting);
        }
        holder.status.setTextColor(statusColor);
        if (packageStatus == PackageStatus.ISSUE) {
            holder.statusIcon.setImageResource(R.drawable.ic_error);
            ImageViewCompat.setImageTintList(holder.statusIcon, ColorStateList.valueOf(statusColor));
            holder.statusIcon.setVisibility(View.VISIBLE);
        } else {
            holder.statusIcon.setVisibility(View.GONE);
        }
        holder.accentBorder.setVisibility(View.VISIBLE);
        holder.accentBorder.setBackgroundColor(statusColor);

        int carrierLogoRes = Carriers.logoResId(tracking.getCarrier());
        if (carrierLogoRes != 0) {
            holder.carrierLogo.setImageResource(carrierLogoRes);
            holder.carrierLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        } else {
            holder.carrierLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
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
        final View unreadDot;
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
            this.unreadDot = view.findViewById(R.id.unreadDot);
            this.updatedLayout = view.findViewById(R.id.updatedLayout);
        }
    }

    private String getDaysSinceText(String created) {
        if (created == null || created.trim().isEmpty()) {
            return "";
        }
        try {
            long millisSinceCreated = System.currentTimeMillis() - Long.parseLong(created);
            long days = TimeUnit.DAYS.convert(millisSinceCreated, TimeUnit.MILLISECONDS);
            int n = days > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) days;
            return context.getResources().getQuantityString(R.plurals.days_count, n, n);
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


    /**
     * Shrinks the status label just enough to keep its longest word on one line in the
     * fixed-width status column (e.g. "Ολοκληρώθηκε" would otherwise break mid-word).
     */
    private void fitStatusText(TextView statusTv) {
        statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, STATUS_TEXT_SIZE_SP);
        // Status column is 90dp wide with 2dp padding on each side
        float availableWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 86,
                context.getResources().getDisplayMetrics());
        String[] words = statusTv.getText().toString().split("\\s+");
        // Text width doesn't scale exactly linearly with size, so step down until it really fits
        while (widestWord(statusTv.getPaint(), words) > availableWidth && statusTv.getTextSize() > 1) {
            statusTv.setTextSize(TypedValue.COMPLEX_UNIT_PX, statusTv.getTextSize() - 1);
        }
    }

    private static float widestWord(TextPaint paint, String[] words) {
        float widest = 0;
        for (String word : words) {
            widest = Math.max(widest, paint.measureText(word));
        }
        return widest;
    }

    private String getLastUpdateText(String lastUpdate) {
        if (lastUpdate == null || lastUpdate.trim().isEmpty() || lastUpdate.equals("Status: None")) {
            return context.getString(R.string.status_none);
        }
        return lastUpdate;
    }

    private void applyReadStatus(ViewHolder holder, TrackingIndexModel tracking) {
        boolean isUnread = databaseHelper.getUnreadStatus(tracking.getTracking());
        MaterialCardView card = (MaterialCardView) holder.itemView;

        if (isUnread) {
            holder.packageName.setTypeface(Typeface.DEFAULT_BOLD);
            holder.tracking.setTypeface(Typeface.DEFAULT_BOLD);
            holder.daysSince.setTypeface(Typeface.DEFAULT_BOLD);
            holder.carrierSmall.setTypeface(Typeface.DEFAULT_BOLD);
            holder.status.setTypeface(Typeface.DEFAULT_BOLD);
            holder.updated.setTypeface(Typeface.DEFAULT_BOLD);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT_BOLD);

            holder.unreadDot.setVisibility(View.VISIBLE);
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background_unread));
        } else {
            holder.packageName.setTypeface(Typeface.DEFAULT);
            holder.tracking.setTypeface(Typeface.DEFAULT);
            holder.daysSince.setTypeface(Typeface.DEFAULT);
            holder.carrierSmall.setTypeface(Typeface.DEFAULT);
            holder.status.setTypeface(Typeface.DEFAULT);
            holder.updated.setTypeface(Typeface.DEFAULT);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT);

            holder.unreadDot.setVisibility(View.GONE);
            card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background));
        }
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(context, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        context.startActivity(myIntent);
    }

    private List<TrackingIndexModel> filterList(CharSequence constraint) {
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
        return filteredList;
    }

    /** Filters right away, unlike getFilter(), so a reloaded list never shows unfiltered first. */
    public void filterNow(CharSequence constraint) {
        listDataFiltered = filterList(constraint);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<TrackingIndexModel> filteredList = filterList(constraint);

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