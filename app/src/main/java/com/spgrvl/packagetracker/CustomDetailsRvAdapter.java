package com.spgrvl.packagetracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CustomDetailsRvAdapter extends RecyclerView.Adapter<CustomDetailsRvAdapter.ViewHolder> {

    private final List<TrackingDetailsModel> listData;
    private final LayoutInflater layoutInflater;

    public CustomDetailsRvAdapter(Context context, List<TrackingDetailsModel> listData) {
        this.listData = listData;
        layoutInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(layoutInflater.inflate(R.layout.tracking_details_item, null));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrackingDetailsModel tracking = this.listData.get(position);
        holder.status.setText(tracking.getStatus());
        holder.place.setText(tracking.getPlace());
        holder.datetime.setText(tracking.getDatetime());
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
        final TextView place;
        final TextView datetime;

        public ViewHolder(@NonNull View view) {
            super(view);
            this.status = view.findViewById(R.id.statusTv);
            this.place = view.findViewById(R.id.placeTv);
            this.datetime = view.findViewById(R.id.datetimeTv);
        }
    }

}