package com.spgrvl.packagetracker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CustomIndexRvAdapter extends RecyclerView.Adapter<CustomIndexRvAdapter.ViewHolder> {

    private final List<TrackingIndexModel> listData;
    private final LayoutInflater layoutInflater;
    private final Context context;
    private final MainActivity mainActivity;
    private final DatabaseHelper databaseHelper;

    public CustomIndexRvAdapter(Context context, List<TrackingIndexModel> listData) {
        this.listData = listData;
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
        TrackingIndexModel tracking = this.listData.get(position);
        String customName = tracking.getCustomName();
        if (customName != null) {
            String nameAndTracking = customName + " - " + tracking.getTracking();
            holder.tracking.setText(nameAndTracking);
        } else {
            holder.tracking.setText(tracking.getTracking());
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

        // change the font style depending on package read status
        applyReadStatus(holder, tracking);

        // Called when user clicks RecyclerView items
        holder.parentView.setOnClickListener(v -> {
            if (!mainActivity.isInSelectionMode) {
                openTrackingDetails(tracking.getTracking());
            } else {
                mainActivity.selectItem(holder.checkbox, position);
            }
        });

        // Called when user long clicks RecyclerView items
        holder.parentView.setOnLongClickListener(v -> {
            mainActivity.startSelection(position);
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
            holder.checkbox.setVisibility(View.VISIBLE);
        } else {
            holder.checkbox.setVisibility(View.GONE);
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
            if (!mainActivity.selectionList.contains(t.getTracking())) {
                mainActivity.selectionList.add(t.getTracking());
            }
            mainActivity.counter = getItemCount();
            mainActivity.updateToolbarText();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tracking;
        final TextView updated;
        final TextView lastUpdate;
        final CheckBox checkbox;
        private final View parentView;

        public ViewHolder(@NonNull View view) {
            super(view);
            this.parentView = view;
            this.tracking = view.findViewById(R.id.trackingTv);
            this.updated = view.findViewById(R.id.updatedTv);
            this.lastUpdate = view.findViewById(R.id.lastUpdateTv);
            this.checkbox = view.findViewById(R.id.checkbox);
        }
    }

    private void applyReadStatus(ViewHolder holder, TrackingIndexModel tracking) {
        boolean isUnread = databaseHelper.getUnreadStatus(tracking.getTracking());
        if (isUnread) {
            holder.tracking.setTypeface(Typeface.DEFAULT_BOLD);
            holder.updated.setTypeface(Typeface.DEFAULT_BOLD);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            holder.tracking.setTypeface(Typeface.DEFAULT);
            holder.updated.setTypeface(Typeface.DEFAULT);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT);
        }
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(context, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        context.startActivity(myIntent);
    }
}