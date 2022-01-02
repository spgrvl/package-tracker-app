package com.spgrvl.packagetracker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CustomCompletedRvAdapter extends RecyclerView.Adapter<CustomCompletedRvAdapter.ViewHolder> {

    private final List<TrackingIndexModel> listData;
    private final LayoutInflater layoutInflater;
    private final Context context;
    private final CompletedActivity completedActivity;
    private final DatabaseHelper databaseHelper;

    public CustomCompletedRvAdapter(Context context, List<TrackingIndexModel> listData) {
        this.listData = listData;
        this.context = context;
        layoutInflater = LayoutInflater.from(context);
        this.completedActivity = (CompletedActivity) context;
        databaseHelper = new DatabaseHelper(context);

        // find and remove unwanted items
        List<TrackingIndexModel> listRemove = new ArrayList<>();
        for (TrackingIndexModel t : this.listData) {
            if (!t.isCompleted()) {
                listRemove.add(t);
            }
        }
        this.listData.removeAll(listRemove);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(layoutInflater.inflate(R.layout.main_view_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackingIndexModel tracking = this.listData.get(position);
        String customName = tracking.getCustomName();
        if (customName != null) {
            String nameAndTracking = customName + " - " + tracking.getTracking();
            holder.tracking.setText(nameAndTracking);
        } else {
            holder.tracking.setText(tracking.getTracking());
        }

        if (!tracking.getCreated().equals("")) {
            long millisSinceCreated = System.currentTimeMillis() - Long.parseLong(tracking.getCreated());
            String daysSinceCreated = TimeUnit.DAYS.convert(millisSinceCreated, TimeUnit.MILLISECONDS) + " D";
            holder.daysSince.setText(daysSinceCreated);
        } else {
            holder.daysSince.setText("");
        }

        if (tracking.getUpdated().equals("Never")) {
            holder.updated.setText(R.string.never);
        } else if (DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())).equals("0 minutes ago") ||
                DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())).equals("Πριν από 0 λεπτά")) {
            holder.updated.setText(R.string.less_than_a_minute_ago);
        } else {
            holder.updated.setText(DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())));
        }

        if (tracking.getLastUpdate().equals("Status: None")) {
            holder.lastUpdate.setText(R.string.status_none);
        } else {
            holder.lastUpdate.setText(tracking.getLastUpdate());
        }

        if (tracking.getCarrier() != null) {
            try {
                int resId = context.getResources().getIdentifier(tracking.getCarrier(), "string", context.getPackageName());
                holder.carrier.setText(context.getResources().getString(resId));
            } catch (Exception e) {
                holder.carrier.setText(R.string.unknown);
            }
        } else {
            holder.carrier.setText(R.string.unknown);
        }

        holder.packageIcon.setImageResource(R.drawable.ic_package_completed);

        // change the font style depending on package read status
        applyReadStatus(holder, tracking);

        // Called when user clicks RecyclerView items
        holder.parentView.setOnClickListener(v -> {
            if (!completedActivity.isInSelectionMode) {
                openTrackingDetails(tracking.getTracking());
            } else {
                completedActivity.selectItem(holder.checkbox, tracking.getTracking());
            }
        });

        // Called when user long clicks RecyclerView items
        holder.parentView.setOnLongClickListener(v -> {
            completedActivity.startSelection(position, tracking.getTracking());
            return true;
        });

        // set checkbox status
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(tracking.isSelected());

        holder.checkbox.setOnCheckedChangeListener((checkboxView, isChecked) -> tracking.setSelected(isChecked));

        // detect if in multiple selection mode
        if (completedActivity.isInSelectionMode) {
            if (completedActivity.position == position) {
                holder.checkbox.setChecked(true);
                completedActivity.position = -1;
            }
            holder.packageIcon.setVisibility(View.GONE);
            holder.checkbox.setVisibility(View.VISIBLE);
        } else {
            holder.checkbox.setVisibility(View.GONE);
            holder.packageIcon.setVisibility(View.VISIBLE);
            for (TrackingIndexModel t : listData) {
                t.setSelected(false);
            }
        }
    }

    @Override
    public int getItemCount() {
        return listData.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void selectAll() {
        for (TrackingIndexModel t : listData) {
            t.setSelected(true);
            notifyDataSetChanged();
            if (!completedActivity.selectionList.contains(t.getTracking())) {
                completedActivity.selectionList.add(t.getTracking());
            }
            completedActivity.counter = getItemCount();
            completedActivity.updateToolbarText();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tracking;
        final TextView daysSince;
        final TextView updated;
        final TextView lastUpdate;
        final TextView carrier;
        final CheckBox checkbox;
        final ImageView packageIcon;
        private final View parentView;

        public ViewHolder(@NonNull View view) {
            super(view);
            this.parentView = view;
            this.tracking = view.findViewById(R.id.trackingTv);
            this.daysSince = view.findViewById(R.id.daysSinceTv);
            this.updated = view.findViewById(R.id.updatedTv);
            this.lastUpdate = view.findViewById(R.id.lastUpdateTv);
            this.carrier = view.findViewById(R.id.carrierSmallTv);
            this.checkbox = view.findViewById(R.id.checkbox);
            this.packageIcon = view.findViewById(R.id.packageIcon);
        }
    }

    private void applyReadStatus(ViewHolder holder, TrackingIndexModel tracking) {
        boolean isUnread = databaseHelper.getUnreadStatus(tracking.getTracking());
        if (isUnread) {
            holder.tracking.setTypeface(Typeface.DEFAULT_BOLD);
            holder.daysSince.setTypeface(Typeface.DEFAULT_BOLD);
            holder.updated.setTypeface(Typeface.DEFAULT_BOLD);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT_BOLD);
            holder.carrier.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            holder.tracking.setTypeface(Typeface.DEFAULT);
            holder.daysSince.setTypeface(Typeface.DEFAULT);
            holder.updated.setTypeface(Typeface.DEFAULT);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT);
            holder.carrier.setTypeface(Typeface.DEFAULT);
        }
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(context, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        context.startActivity(myIntent);
    }
}