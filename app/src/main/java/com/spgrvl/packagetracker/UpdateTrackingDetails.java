package com.spgrvl.packagetracker;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static com.spgrvl.packagetracker.App.CHANNEL_PKG_ID;

public class UpdateTrackingDetails {
    private final Boolean isOnForeground;
    private final boolean notifPref;
    private String languagePref;
    private boolean updatingAll;
    private final NotificationManagerCompat notificationManager;
    private final Context context;
    private String tracking;
    public static final String PREF_NOTIF = "pref_notif";
    public static final String PREF_LANGUAGE = "pref_language";
    public static final String eltaTrackingRegex = "[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}";
    public static final String acsTrackingRegex = "[0-9]{10}";

    public UpdateTrackingDetails(String tracking, Context context, Boolean isOnForeground) {
        this.tracking = tracking;
        this.context = context;
        this.updatingAll = false;
        this.isOnForeground = isOnForeground;
        notificationManager = NotificationManagerCompat.from(context);

        // Read User preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        notifPref = sharedPreferences.getBoolean(PREF_NOTIF, true);
        languagePref = sharedPreferences.getString(PREF_LANGUAGE, "sys");
    }

    private ArrayList<TrackingDetailsModel> trackElta() {
        Document doc;
        String url;
        Elements dateTime, status, place;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        try {
            // Find system's language if there is no language preference set
            if (languagePref.equals("sys")) {
                languagePref = String.valueOf(context.getResources().getConfiguration().getLocales().get(0));
            }

            // Fetch tracking details in app's language
            if (languagePref.equals("el_GR") || languagePref.equals("el")) {
                url = "https://itemsearch.elta.gr/el-GR/Query/Direct/" + tracking;
                doc = Jsoup.connect(url).get();
                dateTime = doc.getElementsByAttributeValue("data-title", "Ημερομηνία & Ώρα");
                status = doc.getElementsByAttributeValue("data-title", "Κατάσταση");
                place = doc.getElementsByAttributeValue("data-title", "Περιοχή");
            } else {
                url = "https://itemsearch.elta.gr/en-GB/Query/Direct/" + tracking;
                doc = Jsoup.connect(url).get();
                dateTime = doc.getElementsByAttributeValue("data-title", "Date & Time");
                status = doc.getElementsByAttributeValue("data-title", "Status");
                place = doc.getElementsByAttributeValue("data-title", "Location");
            }

            for (int i = 0; i < status.size(); i++) {
                detailsList.add(new TrackingDetailsModel(status.get(i).text(), place.get(i).text(), dateTime.get(i).text()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackAcs() {
        String url;
        RequestQueue mQueue;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        // Find system's language if there is no language preference set
        if (languagePref.equals("sys")) {
            languagePref = String.valueOf(context.getResources().getConfiguration().getLocales().get(0));
        }

        // Fetch tracking details in app's language
        if (languagePref.equals("el_GR") || languagePref.equals("el")) {
            url = "https://www.acscourier.net/el/track-and-trace?p_p_id=ACSCustomersAreaTrackTrace_WAR_ACSCustomersAreaportlet&p_p_lifecycle=2&p_p_resource_id=trackTraceJson&generalCode=" + tracking;
        } else {
            url = "https://www.acscourier.net/en/track-and-trace?p_p_id=ACSCustomersAreaTrackTrace_WAR_ACSCustomersAreaportlet&p_p_lifecycle=2&p_p_resource_id=trackTraceJson&generalCode=" + tracking;
        }

        RequestFuture<JSONObject> future = RequestFuture.newFuture();
        JsonObjectRequest request = new JsonObjectRequest(url, new JSONObject(), future, future);
        mQueue = Volley.newRequestQueue(context);
        mQueue.add(request);

        try {
            JSONObject response = future.get();
            JSONArray jsonArray1 = response.getJSONArray("results");
            JSONObject jsonObject = jsonArray1.getJSONObject(0);
            JSONArray jsonArray = jsonObject.getJSONArray("controlPoints");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject controlPoint = jsonArray.getJSONObject(i);
                long timestampMillis = controlPoint.getLong("date");
                DateFormat timestamp = new SimpleDateFormat("dd/MM/yyyy hh:mm", Locale.FRANCE);
                Date date = new Date(timestampMillis);
                String status = controlPoint.getString("description");
                String place = controlPoint.getString("controlPoint");
                detailsList.add(new TrackingDetailsModel(status, place, timestamp.format(date)));
            }
        } catch (InterruptedException | ExecutionException | JSONException e) {
            e.printStackTrace();
        }

        Collections.reverse(detailsList);
        return detailsList;
    }

    private String detectCarrier() {
        if (Pattern.compile(eltaTrackingRegex).matcher(tracking).find()) {
            return "elta";
        } else if (Pattern.compile(acsTrackingRegex).matcher(tracking).find()) {
            return "acs";
        }
        return null;
    }

    protected boolean getWebsite() {
        Thread t = new Thread(() -> {
            ArrayList<TrackingDetailsModel> detailsList;

            String carrier = detectCarrier();
            if (carrier != null) {
                if (carrier.equals("elta")) {
                    detailsList = trackElta();
                } else if (carrier.equals("acs")) {
                    detailsList = trackAcs();
                } else {
                    detailsList = null;
                }
            } else {
                detailsList = new ArrayList<>();
            }

            DatabaseHelper databaseHelper = new DatabaseHelper(context);
            int db_count = databaseHelper.getTrackingDetailsCount(tracking);
            int carrier_count = detailsList.size();
            String db_last_update = databaseHelper.getIndexEntry(tracking).get(3);
            boolean changedLanguage = false;
            if (carrier_count > 0) {
                changedLanguage = !detailsList.get(0).getStatus().equals(db_last_update);
            }

            if (carrier_count > db_count) {
                // updating Details table in DB
                databaseHelper.updateTrackingDetails(tracking, detailsList);

                // updating latest status and current datetime in Index table in DB
                // mark as unread only if updating all packages and not individually
                databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), detailsList.get(0).getStatus(), updatingAll);

                // show notification only if updating all packages in background and notifications are enabled on user preferences
                if (notifPref && updatingAll && !isOnForeground) {
                    String notificationTitle;
                    ArrayList<String> indexEntry = databaseHelper.getIndexEntry(tracking);
                    String customName = indexEntry.get(4);
                    if (customName != null) {
                        notificationTitle = customName + " - " + tracking;
                    } else {
                        notificationTitle = tracking;
                    }

                    // use unique RowID in order to send individual notifications for each package and be able to follow up later
                    int rowId = Integer.parseInt(indexEntry.get(0));

                    sendPackageUpdateNotification(notificationTitle, detailsList.get(0).getStatus(), rowId, tracking);
                }
            } else if (changedLanguage) {
                databaseHelper.updateTrackingDetails(tracking, detailsList);
                databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), detailsList.get(0).getStatus(), false);
            } else if (carrier_count < db_count) {
                Log.e("UpdateTrackingDetails", "Carrier is reporting less updates (" + carrier_count + ") than already stored in DB (" + db_count + ")");
            } else {
                // updating current datetime in Index table in DB
                databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), null, false);
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
        this.updatingAll = true;
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

    protected void sendPackageUpdateNotification(String title, String message, int packageId, String tracking) {
        // action that occurs when notification is clicked
        Intent activityIntent = new Intent(context, PackageDetailsActivity.class);
        activityIntent.putExtra("tracking", tracking);
        PendingIntent contentIntent = PendingIntent.getActivity(context, packageId, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // notification builder
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_PKG_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(packageId, notification);
    }
}
