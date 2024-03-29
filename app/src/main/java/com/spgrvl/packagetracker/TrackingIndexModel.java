package com.spgrvl.packagetracker;

import androidx.annotation.NonNull;

public class TrackingIndexModel {
    private String tracking;
    private String created;
    private String updated;
    private String lastUpdate;
    private String carrier;
    private String customName;
    private boolean completed;
    private boolean isSelected;

    // constructors
    public TrackingIndexModel(String tracking, String created, String updated, String lastUpdate, String carrier, String customName, boolean completed) {
        this.tracking = tracking;
        this.created = created;
        this.updated = updated;
        this.lastUpdate = lastUpdate;
        this.carrier = carrier;
        this.customName = customName;
        this.completed = completed;
    }

    // toString
    @NonNull
    @Override
    public String toString() {
        return "TrackingModel{" +
                "tracking='" + tracking + '\'' +
                ", created='" + created + '\'' +
                ", updated='" + updated + '\'' +
                ", lastUpdate='" + lastUpdate + '\'' +
                ", carrier='" + carrier + '\'' +
                ", customName='" + customName + '\'' +
                ", completed='" + completed + '\'' +
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

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
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

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
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

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }
}
