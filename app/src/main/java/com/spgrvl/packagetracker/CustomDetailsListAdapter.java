package com.spgrvl.packagetracker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class CustomDetailsListAdapter extends BaseAdapter {

    private List<TrackingDetailsModel> listData;
    private LayoutInflater layoutInflater;
    private Context context;

    public CustomDetailsListAdapter(Context aContext, List<TrackingDetailsModel> listData) {
        this.context = aContext;
        this.listData = listData;
        layoutInflater = LayoutInflater.from(aContext);
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
            convertView = layoutInflater.inflate(R.layout.tracking_details_item, null);
            holder = new ViewHolder();
            holder.status = convertView.findViewById(R.id.statusTv);
            holder.place = convertView.findViewById(R.id.placeTv);
            holder.datetime = convertView.findViewById(R.id.datetimeTv);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        TrackingDetailsModel tracking = this.listData.get(position);
        holder.status.setText(tracking.getStatus());
        holder.place.setText(tracking.getPlace());
        holder.datetime.setText(tracking.getDatetime());

        return convertView;
    }

    static class ViewHolder {
        TextView status;
        TextView place;
        TextView datetime;
    }

}