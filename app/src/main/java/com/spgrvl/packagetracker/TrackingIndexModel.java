package com.spgrvl.packagetracker;

import androidx.annotation.NonNull;

public class TrackingIndexModel {
    private String tracking;
    private String updated;
    private String lastUpdate;
    private String customName;
    private boolean isSelected;

    // constructors
    public TrackingIndexModel(String tracking, String updated, String lastUpdate, String customName) {
        this.tracking = tracking;
        this.updated = updated;
        this.lastUpdate = lastUpdate;
        this.customName = customName;
    }

    // toString
    @NonNull
    @Override
    public String toString() {
        return "TrackingModel{" +
                "tracking='" + tracking + '\'' +
                ", updated='" + updated + '\'' +
                ", lastUpdate='" + lastUpdate + '\'' +
                ", customName='" + customName + '\'' +
                ", isSelected='" + isSelected + '\'' +
                '}';
    }

    // getters and setters
    public String getTracking() {
        return tracking;
    }

    public void setTracking(String tracking) {
        this.tracking = tracking;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(String lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }
}
