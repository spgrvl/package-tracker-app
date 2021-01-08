package com.spgrvl.packagetracker;

import android.content.Context;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;

public class UpdateTrackingDetails {

    private final Context context;
    private String tracking;

    public UpdateTrackingDetails(String tracking, Context context) {
        this.tracking = tracking;
        this.context = context;
    }

    protected boolean getWebsite() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Document doc;
                try {
                    String url = "https://itemsearch.elta.gr/el-GR/Query/Direct/" + tracking;
                    doc = Jsoup.connect(url).get();

                    Elements dateTime = doc.getElementsByAttributeValue("data-title", "Ημερομηνία & Ώρα");
                    Elements status = doc.getElementsByAttributeValue("data-title", "Κατάσταση");
                    Elements place = doc.getElementsByAttributeValue("data-title", "Περιοχή");

                    ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

                    for (int i=0; i<status.size(); i++) {
                        detailsList.add(new TrackingDetailsModel(status.get(i).text(), place.get(i).text(), dateTime.get(i).text()));
                    }

                    DatabaseHelper databaseHelper = new DatabaseHelper(context);

                    // THIS IS JUST FOR TESTING
                    // Log.e("ELTA", String.valueOf(status.size()));
                    // Log.e("DB", String.valueOf(databaseHelper.getTrackingDetailsCount(tracking)));
                    // ABOVE IS JUST FOR TESTING

                    int carrier_count = status.size();
                    int db_count = databaseHelper.getTrackingDetailsCount(tracking);

                    if (carrier_count > db_count) {
                        // updating Details table in DB
                        databaseHelper.updateTrackingDetails(tracking, detailsList);
                        // updating latest status and current datetime in Index table in DB
                        databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), status.get(0).text());
                        // TODO make entry bold-unread
                    }
                    else if (carrier_count < db_count) {
                        Log.e("UpdateTrackingDetails", "Carrier is reporting less updates (" + carrier_count + ") than already stored in DB (" + db_count + ")");
                    }
                    else {
                        // updating current datetime in Index table in DB
                        databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), null);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    protected boolean updateAll() {
        try {
        DatabaseHelper databaseHelper = new DatabaseHelper(context);
        ArrayList<String> tracking_numbers = databaseHelper.getTrackingNumbers();
            for (int i = 0; i < tracking_numbers.size(); i++) {
                tracking = tracking_numbers.get(i);
                getWebsite();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
