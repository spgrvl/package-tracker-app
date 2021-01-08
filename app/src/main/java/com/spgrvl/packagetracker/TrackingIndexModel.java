package com.spgrvl.packagetracker;

public class TrackingIndexModel {
    private String tracking;
    private String updated;
    private String lastUpdate;

    // constructors
    public TrackingIndexModel(String tracking, String updated, String lastUpdate) {
        this.tracking = tracking;
        this.updated = updated;
        this.lastUpdate = lastUpdate;
    }

    // toString
    @Override
    public String toString() {
        return "TrackingModel{" +
                "tracking='" + tracking + '\'' +
                ", updated='" + updated + '\'' +
                ", lastUpdate='" + lastUpdate + '\'' +
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
}
