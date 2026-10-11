package com.spgrvl.packagetracker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CustomDetailsRvAdapter extends RecyclerView.Adapter<CustomDetailsRvAdapter.ViewHolder> {

    private final List<TrackingDetailsModel> listData;
    private final LayoutInflater layoutInflater;
    private final int latestStatusColor;

    public CustomDetailsRvAdapter(Context context, List<TrackingDetailsModel> listData, boolean completed) {
        this.listData = listData;
        layoutInflater = LayoutInflater.from(context);
        // Entries are stored newest first, so the first one is the current status
        String latestStatus = listData.isEmpty() ? null : listData.get(0).getStatus();
        this.latestStatusColor = PackageStatus.of(context, completed, latestStatus).getColor(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(layoutInflater.inflate(R.layout.tracking_details_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackingDetailsModel tracking = this.listData.get(position);
        holder.status.setText(tracking.getStatus());
        if (tracking.getPlace() == null || tracking.getPlace().isEmpty()) {
            holder.meta.setText(tracking.getDatetime());
        } else {
            holder.meta.setText(tracking.getPlace() + " · " + tracking.getDatetime());
        }

        // Timeline: highlight the latest event, hide connector lines at both ends
        boolean isLatest = position == 0;
        if (isLatest) {
            holder.dot.setBackgroundResource(R.drawable.timeline_dot_filled);
            holder.dot.setBackgroundTintList(ColorStateList.valueOf(latestStatusColor));
        } else {
            holder.dot.setBackgroundResource(R.drawable.timeline_dot_hollow);
            holder.dot.setBackgroundTintList(null);
        }
        holder.status.setTypeface(null, isLatest ? Typeface.BOLD : Typeface.NORMAL);
        holder.lineAbove.setVisibility(isLatest ? View.INVISIBLE : View.VISIBLE);
        holder.lineBelow.setVisibility(position == listData.size() - 1 ? View.INVISIBLE : View.VISIBLE);

        // Called when user long clicks RecyclerView items
        View.OnLongClickListener copyToClipboard = v -> {
            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);

            String clipboardMessage;
            if (!tracking.getPlace().equals("")) {
                clipboardMessage = tracking.getStatus() + ", \n" + tracking.getPlace() + ", \n" + tracking.getDatetime();
            } else {
                clipboardMessage = tracking.getStatus() + ", \n" + tracking.getDatetime();
            }

            ClipData clip = ClipData.newPlainText("text", clipboardMessage);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), R.string.status_to_clipboard, Toast.LENGTH_SHORT).show();
            return true;
        };
        holder.parentView.setOnLongClickListener(copyToClipboard);
        // Links in the status make it take its own touches
        holder.status.setOnLongClickListener(copyToClipboard);
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
        final TextView status;
        final TextView meta;
        final View dot;
        final View lineAbove;
        final View lineBelow;
        private final View parentView;

        public ViewHolder(@NonNull View view) {
            super(view);
            this.parentView = view;
            this.status = view.findViewById(R.id.statusTv);
            this.meta = view.findViewById(R.id.metaTv);
            this.dot = view.findViewById(R.id.dot);
            this.lineAbove = view.findViewById(R.id.lineAbove);
            this.lineBelow = view.findViewById(R.id.lineBelow);
        }
    }

}