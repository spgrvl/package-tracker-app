package com.spgrvl.packagetracker;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class CustomIndexListAdapter extends BaseAdapter {

    private List<TrackingIndexModel> listData;
    private LayoutInflater layoutInflater;
    private Context context;

    public CustomIndexListAdapter(Context context, List<TrackingIndexModel> listData) {
        this.listData = listData;
        this.context = context;
        layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return listData.size();
    }

    @Override
    public Object getItem(int position) {
        return listData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.main_view_item, null);
            holder = new ViewHolder();
            holder.tracking = (TextView) convertView.findViewById(R.id.trackingTv);
            holder.updated = (TextView) convertView.findViewById(R.id.updatedTv);
            holder.lastUpdate = (TextView) convertView.findViewById(R.id.lastUpdateTv);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TrackingIndexModel tracking = this.listData.get(position);
        String customName = tracking.getCustomName();
        if (customName != null){
            holder.tracking.setText(customName + " - " + tracking.getTracking());
        } else {
            holder.tracking.setText(tracking.getTracking());
        }

        if (tracking.getUpdated().equals("Never")){
            holder.updated.setText(tracking.getUpdated());
        } else if (DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())).equals("0 minutes ago")) {
            holder.updated.setText("Less than a minute ago");
        } else {
            holder.updated.setText(DateUtils.getRelativeTimeSpanString(Long.parseLong(tracking.getUpdated())));
        }
        holder.lastUpdate.setText(tracking.getLastUpdate());

        // Set unread effect
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        boolean isUnread = databaseHelper.getUnreadStatus(tracking.getTracking());
        if (isUnread) {
            holder.tracking.setTypeface(Typeface.DEFAULT_BOLD);
            holder.updated.setTypeface(Typeface.DEFAULT_BOLD);
            holder.lastUpdate.setTypeface(Typeface.DEFAULT_BOLD);
        }

        return convertView;
    }

    static class ViewHolder {
        TextView tracking;
        TextView updated;
        TextView lastUpdate;
    }

}