package com.spgrvl.packagetracker;

public class TrackingDetailsModel {
    private String status;
    private String place;
    private String datetime;

    // constructor
    public TrackingDetailsModel(String status, String place, String datetime) {
        this.status = status;
        this.place = place;
        this.datetime = datetime;
    }

    // toString
    @Override
    public String toString() {
        return "TrackingDetailsModel{" +
                "status='" + status + '\'' +
                ", place='" + place + '\'' +
                ", datetime='" + datetime + '\'' +
                '}';
    }

    // getters and setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }
}
