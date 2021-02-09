package com.spgrvl.packagetracker;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CustomIndexListAdapter extends RecyclerView.Adapter<CustomIndexListAdapter.ViewHolder> {

    private final List<TrackingIndexModel> listData;
    private final LayoutInflater layoutInflater;
    private final Context context;

    public CustomIndexListAdapter(Context context, List<TrackingIndexModel> listData) {
        this.listData = listData;
        this.context = context;
        layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(layoutInflater.inflate(R.layout.main_view_item, null));
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

        // Set unread effect
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        boolean isUnread = databaseHelper.getUnreadStatus(tracking.getTracking());
        if (isUnread) {
            holder.tracking.setTypeface(Typeface.DEFAULT_BOLD);
            holder.updated.setTypeface(Typeface.DEFAULT_BOLD);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT_BOLD);
        }

        // Called when user clicks RecyclerView items
        holder.parentView.setOnClickListener(v -> openTrackingDetails(tracking.getTracking()));

        // Called when user long clicks RecyclerView items
        holder.parentView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.sure_confirmation)
                    .setMessage(R.string.delete_confirmation)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        // Remove item from DB
                        databaseHelper.deleteTracking(tracking.getTracking());
                        // Remove item from adapter
                        listData.remove(tracking);
                        notifyDataSetChanged();
                        Toast.makeText(context, context.getString(R.string.package_deleted_partial) + tracking.getTracking(), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return listData.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tracking;
        TextView updated;
        TextView lastUpdate;
        private final View parentView;

        public ViewHolder(@NonNull View view) {
            super(view);
            this.parentView = view;
            this.tracking = (TextView) view.findViewById(R.id.trackingTv);
            this.updated = (TextView) view.findViewById(R.id.updatedTv);
            this.lastUpdate = (TextView) view.findViewById(R.id.lastUpdateTv);
        }
    }

    private void openTrackingDetails(String trackingNumber) {
        Intent myIntent = new Intent(context, PackageDetailsActivity.class);
        myIntent.putExtra("tracking", trackingNumber);
        context.startActivity(myIntent);
    }
}