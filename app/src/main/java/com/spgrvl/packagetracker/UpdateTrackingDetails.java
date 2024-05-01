package com.spgrvl.packagetracker;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.preference.PreferenceManager;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
    public static final String eltaTrackingRegex = "^[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}$";
    public static final String easyMailTracking2Regex = "^[0-9]{11}$";
    public static final String speedexOrCourierCenterOrEasyMailTrackingRegex = "^[0-9]{12}$";
    public static final String delatolasTrackingRegex = "^[A-Za-z0-9]{12}$";
    public static final String acsOrGenikiTrackingRegex = "^[0-9]{10}$";
    public static final String cometHellasTrackingRegex = "^[0-9]{8}$";

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

        // Find system's language if there is no language preference set
        if (languagePref.equals("sys")) {
            languagePref = String.valueOf(context.getResources().getConfiguration().getLocales().get(0));
        }
    }

    private ArrayList<TrackingDetailsModel> trackElta() {
        String url;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        String lang;
        if (languagePref.equals("el_GR") || languagePref.equals("el")) {
            lang = "1";
        } else {
            lang = "2";
        }

        url = "https://www.elta.gr/trackApi";

        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("code[]", tracking)
                .add("in_lang", lang)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                countDownLatch.countDown();
            }

            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String myResponse = Objects.requireNonNull(response.body()).string();
                    try {
                        JSONArray jsonResponseArray = new JSONArray(myResponse);
                        JSONObject jsonResponseObject = jsonResponseArray.getJSONObject(0);
                        int updatesCounter = jsonResponseObject.getJSONObject("response").getInt("out_counter");
                        JSONArray jsonArray = jsonResponseObject.getJSONObject("response").getJSONArray("out_status");
                        for (int i = 0; i < updatesCounter; i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String timestampOriginal = jsonObject.getString("out_date") + jsonObject.getString("out_time");
                            SimpleDateFormat inputFormatter = new SimpleDateFormat("yyyyMMddHHmm", Locale.ENGLISH);
                            Date date = inputFormatter.parse(timestampOriginal);
                            SimpleDateFormat outputFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
                            String status = jsonObject.getString("out_status_name");
                            String place = jsonObject.getString("out_station");
                            detailsList.add(new TrackingDetailsModel(status, place, outputFormatter.format(date)));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackEltaBackup() {
        Document doc;
        String url;
        Elements dateTime, status, place;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackGeniki() {
        Document doc;
        String url;
        Elements status, location, date, time;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        try {
            // Fetch tracking details in app's language
            if (languagePref.equals("el_GR") || languagePref.equals("el")) {
                url = "https://www.taxydromiki.com/track/" + tracking;
            } else {
                url = "https://www.taxydromiki.com/en/track/" + tracking;
            }
            doc = Jsoup.connect(url).get();
            status = doc.getElementsByClass("checkpoint-status");
            location = doc.getElementsByClass("checkpoint-location");
            date = doc.getElementsByClass("checkpoint-date");
            time = doc.getElementsByClass("checkpoint-time");
            String dateProper, dateTime = "";

            int locationSize = location.size();
            for (int i = 0; i < locationSize; i++) {
                Matcher dateMatcher = Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{4}").matcher(date.get(i).ownText());
                if (dateMatcher.find()) {
                    dateProper = dateMatcher.group(0);
                    dateTime = dateProper + " " + time.get(i).ownText();
                }
                detailsList.add(new TrackingDetailsModel(status.get(i).ownText(), location.get(i).ownText(), dateTime));
            }
            // delivery entry has no location so needs to be added manually
            Matcher dateMatcher = Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{4}").matcher(date.get(locationSize).ownText());
            if (dateMatcher.find()) {
                dateProper = dateMatcher.group(0);
                dateTime = dateProper + " " + time.get(locationSize).ownText();
            }
            detailsList.add(new TrackingDetailsModel(status.get(locationSize).ownText(), "", dateTime));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Collections.reverse(detailsList);
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackSpeedex() {
        Document doc;
        String url, deliveredSubtitle;
        Elements deliveredTitle, deliveredSubtitleClass, cardTitle, cardSubtitleClass;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        try {
            // Fetch tracking details
            url = "http://www.speedex.gr/speedex/NewTrackAndTrace.aspx?number=" + tracking;
            doc = Jsoup.connect(url).get();

            cardTitle = doc.getElementsByClass("card-title");
            cardSubtitleClass = doc.getElementsByClass("card-subtitle text-muted mb-0 pt-1");

            int deliveredCards = doc.getElementsByClass("card-subtitle text-muted mb-0 pt-1 delivered-subtitle").size();

            for (int i = 0; i < cardTitle.size() - deliveredCards; i++) {
                String cardSubtitle = cardSubtitleClass.get(i).child(0).text();
                detailsList.add(new TrackingDetailsModel(cardTitle.get(i).text(),
                        cardSubtitle.split(", ")[0],
                        cardSubtitle.split(", ")[1].replace(" στις ", " ")));
            }

            deliveredTitle = doc.getElementsByClass("card-title delivered-title");
            deliveredSubtitleClass = doc.getElementsByClass("card-subtitle text-muted mb-0 pt-1 delivered-subtitle");
            deliveredSubtitle = deliveredSubtitleClass.get(0).child(0).text();
            detailsList.add(new TrackingDetailsModel(deliveredTitle.get(0).text(),
                    deliveredSubtitle.split(", ")[0],
                    deliveredSubtitle.split(", ")[1].replace(" στις ", " ")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Collections.reverse(detailsList);
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackAcs() {
        String url, lang;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        // Fetch tracking details in app's language
        if (languagePref.equals("el_GR") || languagePref.equals("el")) {
            lang = "el";
        } else {
            lang = "en";
        }

        url = "https://webapp.acscourier.net/api/v1/track/" + tracking + "?lang=" + lang;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Referer", "https://webapp.acscourier.net/track-shipment/" + tracking)
                .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                countDownLatch.countDown();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String myResponse = Objects.requireNonNull(response.body()).string();
                    try {
                        JSONObject jsonResponseObject = new JSONObject(myResponse);
                        JSONArray jsonArray = jsonResponseObject.getJSONArray("details");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            if (jsonObject.getInt("flag_sort") == 1) {
                                String timestampOriginal = jsonObject.getString("Ημερομηνία_ώρα");
                                SimpleDateFormat inputFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
                                Date date = inputFormatter.parse(timestampOriginal);
                                SimpleDateFormat outputFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
                                String status = jsonObject.getString("Περιγραφή");
                                String place = jsonObject.getString("Σημείο_ελέγχου");
                                detailsList.add(new TrackingDetailsModel(status, place, outputFormatter.format(date)));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackCometHellas() {
        String url;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        url = "https://www.comethellas.gr/ry4A0yqtF0nePjzSAdXEGjmJmLuXajIzwseKknxx.php?vouchers=" + tracking;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Referer", "https://www.comethellas.gr/track-n-trace/")
                .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                countDownLatch.countDown();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String myResponse = Objects.requireNonNull(response.body()).string();
                    try {
                        JSONObject jsonResponseObject = new JSONObject(myResponse);
                        JSONArray jsonArray = jsonResponseObject.getJSONArray("data");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String timestampOriginal = jsonObject.getString("datetime");
                            SimpleDateFormat inputFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH);
                            inputFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
                            Date date = inputFormatter.parse(timestampOriginal);
                            SimpleDateFormat outputFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
                            String deliveryName = jsonObject.getString("DeliveryName");
                            String extraData = jsonObject.getString("extraData");
                            String statusAndPlace = jsonObject.getString("status")
                                    .replace("<i>", "\n")
                                    .replace("</i>", "");
                            String status = statusAndPlace.split(" \\[")[0];
                            String place = "";
                            if (statusAndPlace.contains(" [")) {
                                place = statusAndPlace.split(" \\[")[1]
                                        .split("]")[0]
                                        .trim()
                                        .replaceAll(" +", " ");
                            }
                            if (!deliveryName.equals("null")) {
                                status = status + " (" + deliveryName + ")";
                            }
                            if (!extraData.equals("null")) {
                                status = status + " [" + extraData + "]";
                            }
                            detailsList.add(new TrackingDetailsModel(status, place, outputFormatter.format(date)));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Collections.reverse(detailsList);
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackCourierCenter() {
        Document doc;
        String url;
        Elements date, time, status, place;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        try {
            // Fetch tracking details
            url = "https://www.courier.gr/track/result?tracknr=" + tracking;
            doc = Jsoup.connect(url).get();
            date = doc.getElementsByClass("td date");
            time = doc.getElementsByClass("td time");
            status = doc.getElementsByClass("td action");
            place = doc.getElementsByClass("td area");

            for (int i = 0; i < status.size(); i++) {
                detailsList.add(new TrackingDetailsModel(
                        status.get(i).text().replace(" ΝΟ: [" + tracking + "]", ""),
                        place.get(i).text(),
                        String.format("%s %s", date.get(i).text(), time.get(i).text())));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackDelatolas() {
        String url;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        String lang;
        if (languagePref.equals("el_GR") || languagePref.equals("el")) {
            lang = "el";
        } else {
            lang = "en";
        }

        url = "https://docuclass.delatolas.com/js/code/epod/track_and_trace/tnt_server.php";

        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("cmd", "getstatusnew")
                .add("orderid", tracking)
                .add("language", lang)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                countDownLatch.countDown();
            }

            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String myResponse = Objects.requireNonNull(response.body()).string();

                    // Extract data from response
                    Matcher entriesMatcher = Pattern.compile("\\{h_date.*?\\}").matcher(myResponse);
                    while (entriesMatcher.find()) {
                        Matcher entriesDataMatcher = Pattern.compile("\\{h_date:'(\\d{2}/\\d{2}/\\d{4})',h_status:'(.*)'\\}").matcher(Objects.requireNonNull(entriesMatcher.group(0)));
                        if (entriesDataMatcher.find()) {
                            String date = entriesDataMatcher.group(1);
                            String status = entriesDataMatcher.group(2);
                            detailsList.add(new TrackingDetailsModel(status, "", date));
                        }
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Collections.reverse(detailsList);
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackEasyMail() {
        String url;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        try {
            // Fetch tracking details in app's language
            if (languagePref.equals("el_GR") || languagePref.equals("el")) {
                url = "https://trackntrace.easymail.gr/" + tracking;
            } else {
                url = "https://trackntrace.easymail.gr/en/" + tracking;
            }

            Document doc = Jsoup.connect(url).get();
            Elements table = doc.getElementsByClass("table table-bordered table-hover");
            Elements rows = table.select("tbody").select("tr");

            for (Element row : rows) {
                Elements cols = row.select("td");
                detailsList.add(new TrackingDetailsModel(
                        cols.get(1).text(),
                        cols.get(2).text(),
                        cols.get(0).text().replace("  ", " ")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return detailsList;
    }

    private String detectCarrier() {
        if (Pattern.compile(eltaTrackingRegex).matcher(tracking).find()) {
            return "elta";
        } else if (Pattern.compile(easyMailTracking2Regex).matcher(tracking).find()) {
            return "easyMail";
        } else if (Pattern.compile(speedexOrCourierCenterOrEasyMailTrackingRegex).matcher(tracking).find()) {
            return speedexOrCourierCenterOrEasyMail();
        } else if (Pattern.compile(acsOrGenikiTrackingRegex).matcher(tracking).find()) {
            return acsOrGeniki();
        } else if (Pattern.compile(cometHellasTrackingRegex).matcher(tracking).find()) {
            return "cometHellas";
        } else if (Pattern.compile(delatolasTrackingRegex).matcher(tracking).find()) {
            return "delatolas";
        }
        return null;
    }

    private String acsOrGeniki() {
        // determine if tracking belongs to ACS or Geniki
        boolean isGeniki = false;

        try {
            String url = "https://www.taxydromiki.com/track/" + tracking;
            Document doc = Jsoup.connect(url).get();
            Elements date = doc.getElementsByClass("checkpoint-date");
            if (date.size() > 0) {
                isGeniki = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (isGeniki) {
            return "geniki";
        } else {
            return "acs";
        }
    }

    private String speedexOrCourierCenterOrEasyMail() {
        // determine if tracking belongs to Speedex, Courier Center or Easy Mail
        String carrier = "speedex";

        try {
            String url = "https://www.courier.gr/track/result?tracknr=" + tracking;
            Document doc = Jsoup.connect(url).get();
            Elements date = doc.getElementsByClass("td date");
            if (date.size() > 0) {
                carrier = "courierCenter";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!carrier.equals("courierCenter")) {
            try {
                String url = "https://trackntrace.easymail.gr/" + tracking;
                Document doc = Jsoup.connect(url).get();
                Elements table = doc.getElementsByClass("table table-bordered table-hover");
                if (!table.isEmpty()) {
                    carrier = "easyMail";
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return carrier;
    }

    protected boolean getWebsite() {
        Thread t = new Thread(() -> {
            ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

            String carrier = detectCarrier();
            if (carrier != null) {
                switch (carrier) {
                    case "elta":
                        detailsList = trackElta();
                        if (detailsList.isEmpty()) {
                            detailsList = trackEltaBackup();
                        }
                        break;
                    case "speedex":
                        detailsList = trackSpeedex();
                        break;
                    case "acs":
                        detailsList = trackAcs();
                        break;
                    case "cometHellas":
                        detailsList = trackCometHellas();
                        break;
                    case "geniki":
                        detailsList = trackGeniki();
                        break;
                    case "courierCenter":
                        detailsList = trackCourierCenter();
                        break;
                    case "delatolas":
                        detailsList = trackDelatolas();
                        break;
                    case "easyMail":
                        detailsList = trackEasyMail();
                        break;
                }
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
                // mark as unread only if updating all packages and not individually or updating while in foreground
                databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), detailsList.get(0).getStatus(), updatingAll | isOnForeground, carrier);

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

                    String notificationMessage;
                    if (!detailsList.get(0).getPlace().equals("")) {
                        notificationMessage = String.format("%s (%s)", detailsList.get(0).getStatus(), detailsList.get(0).getPlace());
                    } else {
                        notificationMessage = detailsList.get(0).getStatus();
                    }

                    // use unique RowID in order to send individual notifications for each package and be able to follow up later
                    int rowId = Integer.parseInt(indexEntry.get(0));

                    sendPackageUpdateNotification(notificationTitle, notificationMessage, rowId, tracking);
                }
            } else if (changedLanguage) {
                databaseHelper.updateTrackingDetails(tracking, detailsList);
                databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), detailsList.get(0).getStatus(), false, carrier);
            } else if (carrier_count < db_count) {
                Log.e("UpdateTrackingDetails", "Carrier is reporting less updates (" + carrier_count + ") than already stored in DB (" + db_count + ")");
            } else {
                // updating current datetime in Index table in DB
                databaseHelper.updateTrackingIndex(tracking, String.valueOf(System.currentTimeMillis()), null, false, carrier);
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

    protected boolean updateAll(boolean completed) {
        this.updatingAll = true;
        try {
            DatabaseHelper databaseHelper = new DatabaseHelper(context);
            ArrayList<String> tracking_numbers = databaseHelper.getTrackingNumbers(completed);
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

    protected boolean updateAll() {
        return updateAll(false);
    }

    protected void sendPackageUpdateNotification(String title, String message, int packageId, String tracking) {
        // action that occurs when notification is clicked
        Intent resultIntent = new Intent(context, PackageDetailsActivity.class);
        resultIntent.putExtra("tracking", tracking);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(packageId, PendingIntent.FLAG_IMMUTABLE);

        // action that occurs when "MARK READ" button is clicked
        Intent markReadIntent = new Intent(context, NotificationReceiver.class);
        markReadIntent.putExtra("tracking", tracking);
        markReadIntent.putExtra("packageId", packageId);
        markReadIntent.putExtra("action", "mark_as_read");
        PendingIntent markReadPendingIntent =
                PendingIntent.getBroadcast(context, Integer.parseInt(packageId + "1"), markReadIntent, PendingIntent.FLAG_IMMUTABLE);

        // action that occurs when "COMPLETED" button is clicked
        Intent markCompletedIntent = new Intent(context, NotificationReceiver.class);
        markCompletedIntent.putExtra("tracking", tracking);
        markCompletedIntent.putExtra("packageId", packageId);
        markCompletedIntent.putExtra("action", "mark_as_completed");
        PendingIntent markCompletedPendingIntent =
                PendingIntent.getBroadcast(context, Integer.parseInt(packageId + "2"), markCompletedIntent, PendingIntent.FLAG_IMMUTABLE);

        // notification builder
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_PKG_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(resultPendingIntent)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_eye, context.getString(R.string.mark_read), markReadPendingIntent)
                .addAction(R.drawable.ic_check, context.getString(R.string.mark_completed), markCompletedPendingIntent)
                .build();

        notificationManager.notify(packageId, notification);
    }
}
