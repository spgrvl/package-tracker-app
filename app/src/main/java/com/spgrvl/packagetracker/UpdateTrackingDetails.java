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
import org.json.JSONException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
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
    // {courier, tracking number} a Plaisio order was handed to, set by trackPlaisio()
    private String[] plaisioCourier;
    public static final String PREF_NOTIF = "pref_notif";
    public static final String PREF_LANGUAGE = "pref_language";
    public static final String eltaTrackingRegex = "^[a-zA-Z]{2}[0-9]{9}[a-zA-Z]{2}$";
    public static final String easyMailTracking2Regex = "^[0-9]{11}$";
    public static final String speedexOrCourierCenterOrEasyMailTrackingRegex = "^[0-9]{12}$";
    public static final String delatolasTrackingRegex = "^[A-Za-z0-9]{12}$";
    public static final String skroutzLastMileTrackingRegex = "^[A-Za-z0-9]{13}$";
    public static final String acsOrGenikiOrBoxNowTrackingRegex = "^[0-9]{10}$";
    public static final String cometHellasTrackingRegex = "^[0-9]{8}$";
    // e-shop.gr order numbers: 21 digits starting with the order's date and time (yyMMddHHmm)
    public static final String eshopOrderRegex = "^[0-9]{21}$";
    public static final String plaisioOrderRegex = "^[0-9]{7}$";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final TimeZone GREEK_TIME_ZONE = TimeZone.getTimeZone("Europe/Athens");
    static final String DISPLAY_DATE_FORMAT = "dd/MM/yyyy HH:mm";

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
        // The tracking page loads its data from this endpoint; it expects a JSON body
        // sent with a form content type
        String lang = languagePref.equals("el_GR") || languagePref.equals("el") ? "el" : "en";
        String body;
        try {
            body = new JSONObject().put("lang", lang).put("voucherNo", tracking).toString();
        } catch (JSONException e) {
            return new ArrayList<>();
        }

        Request request = new Request.Builder()
                .url("https://taxydromiki.com/external_scripts/track-site.php")
                .addHeader("Referer", "https://taxydromiki.com/track/" + tracking + "/")
                .post(RequestBody.create(body, MediaType.parse("application/x-www-form-urlencoded")))
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                return parseGenikiResponse(Objects.requireNonNull(response.body()).string(), tracking);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Parses Geniki's tracking JSON into newest-first details. Returns an empty list when the
     * tracking number is unknown, which carrier detection relies on.
     */
    static ArrayList<TrackingDetailsModel> parseGenikiResponse(String json, String tracking) throws JSONException {
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();
        JSONObject result = new JSONObject(json).getJSONObject(tracking).getJSONObject("TrackAndTraceResult");
        JSONObject checkpointsObject = result.optJSONObject("Checkpoints");
        if (result.optInt("Result", -1) != 0 || checkpointsObject == null) {
            return detailsList;
        }

        // A single checkpoint is returned as an object instead of an array
        JSONArray checkpoints = checkpointsObject.optJSONArray("Checkpoint");
        if (checkpoints == null) {
            checkpoints = new JSONArray();
            JSONObject single = checkpointsObject.optJSONObject("Checkpoint");
            if (single != null) {
                checkpoints.put(single);
            }
        }
        for (int i = 0; i < checkpoints.length(); i++) {
            JSONObject checkpoint = checkpoints.getJSONObject(i);
            detailsList.add(new TrackingDetailsModel(checkpoint.optString("Status"),
                    checkpoint.optString("Shop"), formatGreekLocalTime(checkpoint.optString("StatusDate"))));
        }

        // The delivery itself is only in the summary, after the last checkpoint
        String deliveryDate = result.optString("DeliveryDate");
        String status = result.optString("Status");
        if (!status.isEmpty() && !deliveryDate.isEmpty() && !deliveryDate.startsWith("0001")) {
            String consignee = result.isNull("Consignee") ? "" : result.optString("Consignee").trim();
            if (!consignee.isEmpty()) {
                status = status + " (" + consignee + ")";
            }
            detailsList.add(new TrackingDetailsModel(status, result.optString("DeliveredAt"),
                    formatGreekLocalTime(deliveryDate)));
        }

        Collections.reverse(detailsList);
        return detailsList;
    }

    /** Formats a carrier timestamp given in Greek local time ("yyyy-MM-ddTHH:mm:ss[.f]") for display. */
    static String formatGreekLocalTime(String timestamp) {
        try {
            SimpleDateFormat inputFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
            inputFormatter.setTimeZone(GREEK_TIME_ZONE);
            Date date = inputFormatter.parse(timestamp);
            return new SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.ENGLISH).format(Objects.requireNonNull(date));
        } catch (Exception e) {
            return timestamp;
        }
    }

    private ArrayList<TrackingDetailsModel> trackCleverPoint() {
        String lang = languagePref.equals("el_GR") || languagePref.equals("el") ? "el" : "en";
        String base = "https://platform.cleverpoint.gr/apiv2/shop/tracking/";
        OkHttpClient client = new OkHttpClient();
        try {
            String shipmentId;
            Request findRequest = new Request.Builder()
                    .url(base + "FindAwbs?awb=" + tracking + "&langCode=" + lang)
                    .build();
            try (Response response = client.newCall(findRequest).execute()) {
                if (!response.isSuccessful()) {
                    return new ArrayList<>();
                }
                JSONArray shipments = new JSONArray(Objects.requireNonNull(response.body()).string());
                if (shipments.length() == 0) {
                    return new ArrayList<>();
                }
                shipmentId = shipments.getJSONObject(0).getString("shipmentMasterId");
            }

            Request infoRequest = new Request.Builder()
                    .url(base + "GetTrackingInfo?shipmentMasterId=" + shipmentId + "&langCode=" + lang)
                    .build();
            try (Response response = client.newCall(infoRequest).execute()) {
                if (response.isSuccessful()) {
                    return parseCleverPointInfo(Objects.requireNonNull(response.body()).string(),
                            context.getString(R.string.cleverPoint_arrived));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Parses Clever Point tracking info into newest-first details. Uses its event history when
     * present; older shipments only keep a summary, so events are built from the arrival at the
     * pickup point and the collection, which also names who collected the parcel.
     */
    static ArrayList<TrackingDetailsModel> parseCleverPointInfo(String json, String arrivedLabel) throws JSONException {
        JSONObject info = new JSONObject(json);
        JSONObject main = info.optJSONObject("mainData");
        JSONObject pod = info.optJSONObject("podData");
        JSONArray history = info.optJSONArray("trackingData");
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        if (history != null) {
            for (int i = 0; i < history.length(); i++) {
                JSONObject event = history.getJSONObject(i);
                detailsList.add(new TrackingDetailsModel(event.optString("trackingNote"),
                        optText(event, "stationName"), formatGreekLocalTime(event.optString("timestamp"))));
            }
        }
        if (detailsList.isEmpty() && main != null) {
            String checkIn = optText(main, "checkInDt");
            if (!checkIn.isEmpty()) {
                detailsList.add(new TrackingDetailsModel(arrivedLabel, optText(main, "deliveryStationName"),
                        formatGreekLocalTime(checkIn)));
            }
        }
        // The collection is only in the summary; add it unless the history already ends with it
        String deliveredAt = pod != null ? optText(pod, "deliveryDateTime") : "";
        String status = main != null ? optText(main, "shipmentStatusNameDesc") : "";
        if (!deliveredAt.isEmpty() && !status.isEmpty()) {
            String deliveredTime = formatGreekLocalTime(deliveredAt);
            // Who collected the parcel, partly masked by Clever Point (e.g. "ΓΚΟΥ***** ΣΠΥΡ*****")
            String deliveredTo = optText(pod, "deliveredTo").trim();
            int listedAt = -1;
            for (int i = 0; i < detailsList.size(); i++) {
                if (detailsList.get(i).getDatetime().equals(deliveredTime)) {
                    listedAt = i;
                }
            }
            if (listedAt < 0) {
                detailsList.add(new TrackingDetailsModel(withRecipient(status, deliveredTo),
                        optText(pod, "stationName"), deliveredTime));
            } else {
                TrackingDetailsModel listed = detailsList.get(listedAt);
                detailsList.set(listedAt, new TrackingDetailsModel(withRecipient(listed.getStatus(), deliveredTo),
                        listed.getPlace(), listed.getDatetime()));
            }
        }
        detailsList.sort((a, b) -> Long.compare(displayTimeMillis(b.getDatetime()), displayTimeMillis(a.getDatetime())));
        return detailsList;
    }

    private static String withRecipient(String status, String recipient) {
        return recipient.isEmpty() ? status : status + " (" + recipient + ")";
    }

    /** Like optString, but treats JSON null as empty instead of the text "null". */
    private static String optText(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key);
    }

    private ArrayList<TrackingDetailsModel> trackEshop() {
        try {
            // The page is only available in Greek; known texts are translated through resources
            Document doc = Jsoup.connect("https://www.e-shop.gr/status?id=" + tracking).get();
            Map<String, String> translations = new HashMap<>();
            translations.put("Υποβολή", context.getString(R.string.eshop_submitted));
            translations.put("Έλεγχος Ομαλότητας", context.getString(R.string.eshop_order_check));
            translations.put("Έλεγχος Διαθεσιμότητας Προϊόντων", context.getString(R.string.eshop_availability_check));
            translations.put("Συγκέντρωση Προϊόντων", context.getString(R.string.eshop_collecting_products));
            translations.put("Αποστολή", context.getString(R.string.eshop_shipped));
            translations.put("Η παραγγελία σας ολοκληρώθηκε. Ευχαριστούμε για την προτίμηση.", context.getString(R.string.eshop_completed));
            return parseEshopStatus(doc, translations);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Parses an e-shop.gr order status page into newest-first details: one entry per stage the
     * order has reached (rows marked "statusrowok"), plus the page's summary message on top.
     * Returns an empty list for unknown order numbers.
     */
    static ArrayList<TrackingDetailsModel> parseEshopStatus(Document doc, Map<String, String> translations) {
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();
        SimpleDateFormat inputFormatter = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.ENGLISH);
        inputFormatter.setTimeZone(GREEK_TIME_ZONE);
        SimpleDateFormat outputFormatter = new SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.ENGLISH);

        for (Element row : doc.select("tr:has(> td.statusrowok)")) {
            Elements cells = row.select("> td.statusrowok");
            if (cells.size() < 3) {
                continue;
            }
            String stage = cells.get(1).text().trim();
            String datetime = cells.get(2).text().trim();
            try {
                datetime = outputFormatter.format(Objects.requireNonNull(inputFormatter.parse(datetime)));
            } catch (Exception ignored) {
                // keep the page's own text
            }
            detailsList.add(new TrackingDetailsModel(translate(translations, stage), "", datetime));
        }
        if (detailsList.isEmpty()) {
            return detailsList;
        }
        Collections.reverse(detailsList);

        // The summary has no date of its own; show it with the latest stage's
        Element report = doc.selectFirst("td.statusreport");
        if (report != null && !report.text().trim().isEmpty()) {
            detailsList.add(0, new TrackingDetailsModel(translate(translations, report.text().trim()), "",
                    detailsList.get(0).getDatetime()));
        }
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackDiakinisis() {
        // The tracking page (pod.diakinisis.gr/tnt.php?id=) loads its data from this endpoint
        Request request = new Request.Builder()
                .url("https://pod.diakinisis.gr/server_handler_ext.php")
                .post(new FormBody.Builder()
                        .add("cmd", "srvcmd_ext_tnt")
                        .add("action", "srvaction_getrecordstatus")
                        .add("trackingId", tracking)
                        .add("secondId", "")
                        .add("agreementId", "0")
                        .build())
                .build();
        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                // The messages are only available in Greek; known ones are translated through
                // the diakinisis_* resources (kept from shrinking in res/raw/keep.xml)
                Map<String, String> translations = new HashMap<>();
                for (String[] message : DIAKINISIS_MESSAGES) {
                    translations.put(message[0], context.getString(context.getResources()
                            .getIdentifier(message[1], "string", context.getPackageName())));
                }
                return parseDiakinisis(Objects.requireNonNull(response.body()).string(), translations);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private static final String DIAKINISIS_DELIVERED = "Παραδόθηκε η παραγγελία σας.";

    // Every message seen on Diakinisis' tracking page, with the resource that translates it
    static final String[][] DIAKINISIS_MESSAGES = {
            {"Καταχωρήθηκε η εντολή σας και περιμένουμε τον αποστολέα να ετοιμάσει την αποστολή σας.", "diakinisis_registered"},
            {"Ολοκλήρωση Picking.", "diakinisis_picked"},
            {"Η παραγγελία σας βρίσκεται στο κέντρο διανομής μας.", "diakinisis_at_hub"},
            {"Η παραγγελία σας έχει προγραμματιστεί για δρομολόγηση.", "diakinisis_scheduled"},
            {"Η παραγγελία φορτώθηκε για να σας παραδοθεί.", "diakinisis_out_for_delivery"},
            {"Η παραγγελία σας παραδόθηκε σε τοπικό συνεργάτη.", "diakinisis_handed_to_partner"},
            {DIAKINISIS_DELIVERED, "diakinisis_delivered"},
    };

    /**
     * Parses Diakinisis' tracking response into newest-first details, naming the recipient on the
     * delivery. Returns an empty list for unknown (or expired) tracking numbers.
     */
    static ArrayList<TrackingDetailsModel> parseDiakinisis(String json, Map<String, String> translations) throws JSONException {
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();
        // A JSON array of {status, message, data}; apostrophes in names come escaped as \'
        JSONArray result = new JSONArray(json.replace("\\'", "'"));
        JSONObject data = result.optJSONObject(2);
        if (!"1".equals(result.optString(0)) || data == null) {
            return detailsList;
        }
        JSONObject order = data.optJSONObject("order");
        String recipient = order != null ? optText(order, "recipient").trim() : "";
        JSONArray history = data.optJSONArray("history");
        for (int i = 0; history != null && i < history.length(); i++) {
            JSONObject event = history.getJSONObject(i);
            // One message is written with a Latin "H"
            String action = optText(event, "action").trim().replaceFirst("^H ", "Η ");
            String status = translate(translations, action);
            if (action.equals(DIAKINISIS_DELIVERED)) {
                status = withRecipient(status, recipient);
            }
            String time = optText(event, "action_time");
            String datetime = (optText(event, "action_date") + " " + time.substring(0, Math.min(5, time.length()))).trim();
            // Comments name the depot, or the sender's import file which means nothing to the recipient
            String place = optText(event, "action_comment").trim();
            if (place.startsWith("Imported From")) {
                place = "";
            }
            detailsList.add(new TrackingDetailsModel(status, place, datetime));
        }
        Collections.reverse(detailsList);
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackAiolos() {
        try {
            Document doc = Jsoup.connect("https://aioloscourier.gr/track-trace/?shipping_num=" + tracking).get();
            // The page is only available in Greek; known texts are translated through the
            // aiolos_* resources (kept from shrinking in res/raw/keep.xml)
            Map<String, String> translations = new HashMap<>();
            for (String[] message : AIOLOS_MESSAGES) {
                translations.put(message[0], context.getString(context.getResources()
                        .getIdentifier(message[1], "string", context.getPackageName())));
            }
            return parseAiolos(doc, translations);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private static final String AIOLOS_DELIVERED = "ΑΠΟΣΤΟΛΗ ΠΑΡΑΔΟΘΗΚΕ";
    // Its place is who signed for the parcel: a name, "ΣΦΡΑΓΙΔΑ" (stamp) or "ΜΟΝΟΓΡΑΦΟΣ" (initials)
    private static final String AIOLOS_DELIVERY_DETAILS = "ΣΤΟΙΧΕΙΑ ΠΑΡΑΔΟΣΗΣ";

    // Every message seen on Aiolos' tracking page, with the resource that translates it
    static final String[][] AIOLOS_MESSAGES = {
            {"ΣΕ ΚΟΥΡΙΕΡ ΠΡΟΣ ΠΑΡΑΔΟΣΗ ΣΤΟΝ ΠΑΡΑΛΗΠΤΗ", "aiolos_out_for_delivery"},
            {"ΑΠΑΡΑΔΟΤΑ", "aiolos_not_delivered"},
            {AIOLOS_DELIVERED, "aiolos_delivered"},
    };

    /**
     * Parses an Aiolos tracking page into newest-first details. The delivery details row is
     * folded into the delivery as its recipient, or stands for it when the page lists no
     * delivery. Returns an empty list for unknown tracking numbers.
     */
    static ArrayList<TrackingDetailsModel> parseAiolos(Document doc, Map<String, String> translations) {
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();
        // The first table is a summary of the latest event, the second the full history
        Elements tables = doc.select("table.tracking_table");
        if (tables.size() < 2) {
            return detailsList;
        }
        int deliveredAt = -1;
        for (Element row : tables.get(1).select("tr:has(td)")) {
            Elements cells = row.select("td");
            if (cells.size() < 3) {
                continue;
            }
            String datetime = cells.get(0).text().trim();
            String status = cells.get(1).text().trim();
            String place = cells.get(2).text().trim();
            if (status.equals(AIOLOS_DELIVERY_DETAILS)) {
                if (deliveredAt >= 0) {
                    TrackingDetailsModel delivery = detailsList.get(deliveredAt);
                    detailsList.set(deliveredAt, new TrackingDetailsModel(withRecipient(delivery.getStatus(), place),
                            delivery.getPlace(), delivery.getDatetime()));
                } else {
                    detailsList.add(new TrackingDetailsModel(withRecipient(translate(translations, AIOLOS_DELIVERED), place),
                            "", datetime));
                }
                continue;
            }
            if (status.equals(AIOLOS_DELIVERED)) {
                deliveredAt = detailsList.size();
            }
            detailsList.add(new TrackingDetailsModel(translate(translations, status), place, datetime));
        }
        Collections.reverse(detailsList);
        return detailsList;
    }

    private static String translate(Map<String, String> translations, String text) {
        String translated = translations.get(text);
        return translated != null ? translated : text;
    }

    private ArrayList<TrackingDetailsModel> trackPlaisio() {
        plaisioCourier = null;
        Request request = new Request.Builder()
                .url("https://www.plaisio.gr/api-gw/service/order-tracking/" + tracking)
                // Sent by plaisio.gr's own tracking page; requests without it are refused
                .header("X-Plaisio-Csrf-Protection", "1")
                .build();
        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                String json = Objects.requireNonNull(response.body()).string();
                // The messages are only available in Greek; known ones are translated through
                // the plaisio_* resources (kept from shrinking in res/raw/keep.xml)
                Map<Pattern, String> translations = plaisioTranslations(name -> context.getString(
                        context.getResources().getIdentifier(name, "string", context.getPackageName())));
                Map<String, String> courierNames = new HashMap<>();
                courierNames.put("boxNow", context.getString(R.string.boxNow));
                courierNames.put("geniki", context.getString(R.string.geniki));
                courierNames.put("speedex", context.getString(R.string.speedex));
                courierNames.put("easyMail", context.getString(R.string.easyMail));
                courierNames.put("elta", context.getString(R.string.elta));
                courierNames.put("diakinisis", context.getString(R.string.diakinisis));
                courierNames.put("aiolos", context.getString(R.string.aiolos));
                plaisioCourier = plaisioCourier(json);
                return parsePlaisioHistory(json, translations, courierNames);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /** Fetches the events of the courier the Plaisio order was handed to, if the app tracks it. */
    private ArrayList<TrackingDetailsModel> trackPlaisioCourier() {
        ArrayList<TrackingDetailsModel> courierDetails = new ArrayList<>();
        if (plaisioCourier == null) {
            return courierDetails;
        }
        // The carriers' trackers read the tracking field, so point it to the courier's number
        String orderNumber = tracking;
        tracking = plaisioCourier[1];
        try {
            switch (plaisioCourier[0]) {
                case "boxNow":
                    courierDetails = trackBoxNow();
                    break;
                case "geniki":
                    courierDetails = trackGeniki();
                    break;
                case "speedex":
                    courierDetails = trackSpeedex();
                    break;
                case "easyMail":
                    courierDetails = trackEasyMail();
                    break;
                case "diakinisis":
                    courierDetails = trackDiakinisis();
                    break;
                case "aiolos":
                    courierDetails = trackAiolos();
                    break;
                case "elta":
                    courierDetails = trackElta();
                    if (courierDetails.isEmpty()) {
                        courierDetails = trackEltaBackup();
                    }
                    break;
            }
        } finally {
            tracking = orderNumber;
        }
        return courierDetails;
    }

    // Courier tracking links seen in Plaisio orders, with the tracking number as the first group.
    // Couriers the app can't track are still recognised so that their number is shown.
    private static final String[][] PLAISIO_COURIER_LINKS = {
            {"boxNow", "boxnow\\.gr/.*[?&]track=([A-Za-z0-9]+)"},
            {"geniki", "taxydromiki\\.com/track/([A-Za-z0-9]+)"},
            {"speedex", "speedex\\.gr/.*[?&]voucher_code=([A-Za-z0-9]+)"},
            {"easyMail", "trackntrace\\.easymail\\.gr/([A-Za-z0-9]+)"},
            {"elta", "elta\\.gr/.*/([A-Za-z]{2}[0-9]{9}[A-Za-z]{2})"},
            {"diakinisis", "diakinisis\\.gr/.*[?&]id=([A-Za-z0-9]+)"},
            {"aiolos", "aioloscourier\\.gr/.*[?&]shipping_num=([A-Za-z0-9]+)"},
    };

    /** Returns {courier, tracking number} from the latest courier link of a Plaisio order, or null. */
    static String[] plaisioCourier(String json) throws JSONException {
        JSONArray history = new JSONObject(json).optJSONArray("orderHistory");
        if (history == null) {
            return null;
        }
        for (int i = history.length() - 1; i >= 0; i--) {
            String[] courier = courierFromLink(optText(history.getJSONObject(i), "courierTrackingLink"));
            if (courier != null) {
                return courier;
            }
        }
        return null;
    }

    private static String[] courierFromLink(String link) {
        for (String[] courier : PLAISIO_COURIER_LINKS) {
            Matcher matcher = Pattern.compile(courier[1]).matcher(link);
            if (matcher.find()) {
                return new String[]{courier[0], matcher.group(1)};
            }
        }
        return null;
    }

    /**
     * Whether a carrier's details are the stored events worded differently, in another language
     * or by a newer app version, so they can replace them silently. Never true for fewer events,
     * so that history a carrier deletes stays in the app.
     */
    static boolean rewordsStoredDetails(List<TrackingDetailsModel> fresh, List<TrackingDetailsModel> stored) {
        return !fresh.isEmpty() && fresh.size() == stored.size() && !fresh.toString().equals(stored.toString());
    }

    /** The combined carrier of a Plaisio order handed to a tracked courier, e.g. "plaisioBoxNow". */
    static String plaisioWithCourier(String courier) {
        return "plaisio" + Character.toUpperCase(courier.charAt(0)) + courier.substring(1);
    }

    /** Adds the courier's events to a Plaisio order's, newest first; courier events win ties. */
    static ArrayList<TrackingDetailsModel> mergePlaisioWithCourier(List<TrackingDetailsModel> plaisioDetails,
                                                                    List<TrackingDetailsModel> courierDetails) {
        ArrayList<TrackingDetailsModel> merged = new ArrayList<>(courierDetails);
        merged.addAll(plaisioDetails);
        // Stable sort keeps each side's own order for events in the same minute
        merged.sort((a, b) -> Long.compare(displayTimeMillis(b.getDatetime()), displayTimeMillis(a.getDatetime())));
        return merged;
    }

    /**
     * Maps Plaisio's Greek messages to the formats of the named plaisio_* string resources.
     * Groups in the patterns (store names, dates, couriers) fill the placeholders in order.
     * Checked in order, so a more specific pattern must come before a general one.
     */
    static Map<Pattern, String> plaisioTranslations(Function<String, String> strings) {
        String[][] messages = {
                {"Η παραγγελία σου καταχωρήθηκε με επιτυχία και έχει προχωρήσει στο στάδιο της επεξεργασίας\\.", "plaisio_processing"},
                {"Η παραγγελία σου καταχωρήθηκε με επιτυχία και θα είναι έτοιμη για παραλαβή (\\S+) (\\S+) από το (.+)\\.", "plaisio_processing_pickup"},
                {"Η παραγγελία σου βρίσκεται στο στάδιο της αναμονής\\.", "plaisio_on_hold"},
                {"Η παραγγελία σου είναι στο στάδιο της συλλογής των προϊόντων\\.", "plaisio_collecting_products"},
                {"Η παραγγελία σου είναι στο στάδιο της συλλογής των προϊόντων με εκτιμώμενη ημερομηνία παράδοσης έως τις (.+)\\.", "plaisio_collecting_products_estimate"},
                {"Η παραγγελία σου έχει τιμολογηθεί και προετοιμάζεται για αποστολή\\.", "plaisio_invoiced"},
                {"Η παραγγελία σου έχει τιμολογηθεί και θα είναι έτοιμη για παραλαβή (\\S+) (\\S+) από το (.+)\\.", "plaisio_invoiced_pickup"},
                {"Η παραγγελία σου έχει τιμολογηθεί και προετοιμάζεται για παράδοση στο πρακτορείο μεταφορών (.+)", "plaisio_invoiced_for_agency"},
                {"Η παραγγελία σου έχει τιμολογηθεί και προετοιμάζεται για παράδοση στην (.+)", "plaisio_invoiced_for_courier"},
                {"Η παραγγελία σου είναι έτοιμη και την παραδώσαμε στην courier (\\S+)\\.", "plaisio_handed_to_courier"},
                {"Η παραγγελία σου έχει παραληφθεί από την (.+)", "plaisio_picked_up_by_courier"},
                {"Η παραγγελία σου έχει ήδη δρομολογηθεί για παράδοση στο χώρο σου με εκτιμώμενη ημερομηνία έως τις (.+)\\.", "plaisio_scheduled_delivery"},
                {"Η παραγγελία σου έχει ήδη δρομολογηθεί για παράδοση στο χώρο σου στις (\\S+) με εκτιμώμενη ώρα παράδοσης (\\S+) - (\\S+)\\.", "plaisio_scheduled_delivery_window"},
                {"Η παραγγελία σου με αρ\\.Voucher (\\S+) έχει ξεπεράσει την εκτιμώμενη ημερομηνία παράδοσης έως τις (\\S+) από την Courier (.+)\\. Υπάρχουν καθυστερήσεις λόγω του μεγάλου όγκου αποστολών!", "plaisio_courier_delayed"},
                {"Η παραγγελία σου δρομολογήθηκε για παραλαβή από το (.+)\\.", "plaisio_sent_to_store"},
                {"Η παραγγελία σου βρίσκεται στο (.+) και σε περιμένει να την παραλάβεις σήμερα (.+)\\.", "plaisio_ready_for_pickup"},
                {"Η παραγγελία σου έχει ήδη παραδοθεί\\.", "plaisio_delivered"},
        };
        Map<Pattern, String> translations = new LinkedHashMap<>();
        for (String[] message : messages) {
            translations.put(Pattern.compile(message[0]), strings.apply(message[1]));
        }
        return translations;
    }

    /**
     * Parses Plaisio's order history (oldest first, dates already "dd/MM/yyyy HH:mm") into
     * newest-first details. Messages matching a translation pattern are replaced by its format,
     * filled with the pattern's groups (store names, dates); others are kept as they are. The
     * event handing the order to a courier gets the courier's link appended to its message and
     * the courier and its tracking number as place.
     */
    static ArrayList<TrackingDetailsModel> parsePlaisioHistory(String json, Map<Pattern, String> translations,
                                                               Map<String, String> courierNames) throws JSONException {
        JSONArray history = new JSONObject(json).optJSONArray("orderHistory");
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();
        if (history == null) {
            return detailsList;
        }
        for (int i = 0; i < history.length(); i++) {
            JSONObject event = history.getJSONObject(i);
            String status = optText(event, "statusDescription").trim();
            for (Map.Entry<Pattern, String> translation : translations.entrySet()) {
                Matcher matcher = translation.getKey().matcher(status);
                if (matcher.matches()) {
                    Object[] groups = new Object[matcher.groupCount()];
                    for (int g = 0; g < groups.length; g++) {
                        groups[g] = matcher.group(g + 1);
                    }
                    status = String.format(translation.getValue(), groups);
                    break;
                }
            }
            // Shown so that couriers the app can't track can still be followed on their own site
            String link = optText(event, "courierTrackingLink").trim();
            if (link.startsWith("http")) {
                status += " " + link;
            }
            String place = "";
            String[] courier = courierFromLink(link);
            if (courier != null) {
                String name = courierNames.get(courier[0]);
                place = (name != null ? name : courier[0]) + " " + courier[1];
            }
            detailsList.add(new TrackingDetailsModel(status, place, optText(event, "transactionDate")));
        }
        Collections.reverse(detailsList);
        return detailsList;
    }

    private ArrayList<TrackingDetailsModel> trackSpeedex() {
        try {
            String url = "https://www.speedex.gr/speedex/NewTrackAndTrace.aspx?number=" + tracking;
            return parseSpeedex(Jsoup.connect(url).get());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Parses a Speedex tracking page into newest-first details. Each event card has a title and
     * subtitle lines: the date ("dd/MM/yyyy στις HH:mm"), the place (", CITY" or "ADDRESS, CITY")
     * and sometimes the branch's phone. The delivered card's title has the status on its first
     * line and who received the parcel after it. Returns an empty list for unknown numbers.
     */
    static ArrayList<TrackingDetailsModel> parseSpeedex(Document doc) {
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();
        for (Element title : doc.select(".card-header > .card-title")) {
            String statusText = title.text();
            Element status = title.selectFirst("p");
            if (status != null) {
                String receivedBy = title.ownText().trim();
                statusText = receivedBy.isEmpty() ? status.text() : status.text() + " (" + receivedBy + ")";
            }
            Elements lines = title.parent().select("> .card-subtitle");
            String datetime = lines.size() > 0 ? lines.get(0).text().replace(" στις ", " ").trim() : "";
            String place = lines.size() > 1 ? lines.get(1).text().trim() : "";
            if (place.startsWith(",")) {
                place = place.substring(1).trim();
            }
            if (!statusText.isEmpty()) {
                detailsList.add(new TrackingDetailsModel(statusText, place, datetime));
            }
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
                                String status = jsonObject.getString("Περιγραφή");
                                String place = jsonObject.getString("Σημείο_ελέγχου");
                                detailsList.add(new TrackingDetailsModel(status, place, formatGreekLocalTime(timestampOriginal)));
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

    static boolean isHandedToSkroutzLastMile(List<TrackingDetailsModel> acsDetails) {
        for (TrackingDetailsModel detail : acsDetails) {
            if (detail.getStatus().toUpperCase(Locale.ROOT).contains("SLM")
                    || detail.getPlace().toUpperCase(Locale.ROOT).contains("SLM")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Combines an ACS history with the Skroutz Last Mile one for the same parcel, newest first:
     * ACS events except its "SLM DELIVERY" handover, then the Skroutz events after the last ACS
     * event (earlier Skroutz events mirror ACS ones).
     */
    static ArrayList<TrackingDetailsModel> mergeAcsWithSkroutzLastMile(List<TrackingDetailsModel> acsDetails,
                                                                       List<TrackingDetailsModel> skroutzDetails) {
        ArrayList<TrackingDetailsModel> acsKept = new ArrayList<>();
        long latestAcsTime = Long.MIN_VALUE;
        for (TrackingDetailsModel detail : acsDetails) {
            if (!detail.getStatus().toUpperCase(Locale.ROOT).contains("SLM DELIVERY")) {
                acsKept.add(detail);
                latestAcsTime = Math.max(latestAcsTime, displayTimeMillis(detail.getDatetime()));
            }
        }

        ArrayList<TrackingDetailsModel> merged = new ArrayList<>();
        for (TrackingDetailsModel detail : skroutzDetails) {
            if (displayTimeMillis(detail.getDatetime()) > latestAcsTime) {
                merged.add(detail);
            }
        }
        merged.addAll(acsKept);
        // Stable sort keeps the carriers' own order for events in the same minute
        merged.sort((a, b) -> Long.compare(displayTimeMillis(b.getDatetime()), displayTimeMillis(a.getDatetime())));
        return merged;
    }

    private static long displayTimeMillis(String datetime) {
        try {
            Date date = new SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.ENGLISH).parse(datetime);
            return Objects.requireNonNull(date).getTime();
        } catch (Exception e) {
            return Long.MIN_VALUE;
        }
    }

    private ArrayList<TrackingDetailsModel> trackSkroutzLastMile() {
        String url, lang;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        // Fetch tracking details in app's language
        if (languagePref.equals("el_GR") || languagePref.equals("el")) {
            lang = "el";
        } else {
            lang = "en";
        }

        url = "https://api.sendx.gr/user/hp/" + tracking;

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
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
                        JSONArray jsonArray = jsonResponseObject.getJSONArray("trackingDetails");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String timestampOriginal = jsonObject.getString("createdAt");
                            SimpleDateFormat inputFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
                            inputFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
                            Date date = inputFormatter.parse(timestampOriginal);
                            SimpleDateFormat outputFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
                            String status;
                            if (lang.equals("en")) {
                                status = jsonObject.getString("description");
                            } else {
                                status = jsonObject.getString("description_gr");
                            }
                            detailsList.add(new TrackingDetailsModel(status, "", outputFormatter.format(date)));
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

    private ArrayList<TrackingDetailsModel> trackBoxNow() {
        String url;
        ArrayList<TrackingDetailsModel> detailsList = new ArrayList<>();

        url = "https://api-production.boxnow.gr/api/v1/parcels:track";

        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();
        try {
            json.put("parcelId", tracking);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        RequestBody jsonBody = RequestBody.create(json.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(jsonBody)
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
                        JSONObject jsonResponseObject = new JSONObject(myResponse).getJSONArray("data").getJSONObject(0);
                        JSONArray jsonArray = jsonResponseObject.getJSONArray("events");
                        for (int i = 0; i < jsonArray.length(); i++) {
                            String status;
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String timestampOriginal = jsonObject.getString("createTime");
                            SimpleDateFormat inputFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
                            inputFormatter.setTimeZone(TimeZone.getTimeZone("GMT"));
                            Date date = inputFormatter.parse(timestampOriginal);
                            SimpleDateFormat outputFormatter = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH);
                            String statusType = jsonObject.getString("type");
                            // Check if the status exists in the localized strings for BoxNow
                            int stringResourceId = context.getResources().getIdentifier("boxNow_" + statusType.replace("-", "_"), "string", context.getPackageName());
                            if (stringResourceId != 0) {
                                status = context.getString(stringResourceId);
                            } else {
                                status = statusType;
                            }
                            String place = "";
                            if (jsonObject.has("locationDisplayName")) {
                                place = jsonObject.getString("locationDisplayName");
                                // Replace common hardcoded values with localized ones
                                place = place.replace("DEPOT", context.getString(R.string.boxNow_depot));
                                place = place.replace("HUB", context.getString(R.string.boxNow_hub));
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
        String carrier = detectCarrierByFormat();
        // Clever Point is a network of pickup points tracking other carriers' numbers, so it has
        // no number format of its own: ask it about numbers no carrier recognised
        if (carrier == null && !trackCleverPoint().isEmpty()) {
            carrier = "cleverPoint";
        }
        return carrier;
    }

    private String detectCarrierByFormat() {
        if (Pattern.compile(eshopOrderRegex).matcher(tracking).find()) {
            return "eshop";
        } else if (Pattern.compile(plaisioOrderRegex).matcher(tracking).find()) {
            return "plaisio";
        } else if (Pattern.compile(eltaTrackingRegex).matcher(tracking).find()) {
            return "elta";
        } else if (Pattern.compile(easyMailTracking2Regex).matcher(tracking).find()) {
            return "easyMail";
        } else if (Pattern.compile(speedexOrCourierCenterOrEasyMailTrackingRegex).matcher(tracking).find()) {
            return speedexOrCourierCenterOrEasyMail();
        } else if (Pattern.compile(acsOrGenikiOrBoxNowTrackingRegex).matcher(tracking).find()) {
            return acsOrGenikiOrBoxNow();
        } else if (Pattern.compile(cometHellasTrackingRegex).matcher(tracking).find()) {
            return "cometHellas";
        } else if (Pattern.compile(delatolasTrackingRegex).matcher(tracking).find()) {
            return "delatolas";
        } else if (Pattern.compile(skroutzLastMileTrackingRegex).matcher(tracking).find()) {
            return "skroutzLastMile";
        }
        return null;
    }

    private String acsOrGenikiOrBoxNow() {
        // determine if tracking belongs to ACS, Geniki, BoxNow, Diakinisis or Aiolos
        String carrier;
        ArrayList<TrackingDetailsModel> detailsList;

        detailsList = trackAcs();
        if (!detailsList.isEmpty()) {
            carrier = "acs";
        } else {
            detailsList = trackBoxNow();
            if (!detailsList.isEmpty()) {
                carrier = "boxNow";
            } else {
                detailsList = trackGeniki();
                if (!detailsList.isEmpty()) {
                    carrier = "geniki";
                } else if (!trackDiakinisis().isEmpty()) {
                    carrier = "diakinisis";
                } else if (!trackAiolos().isEmpty()) {
                    carrier = "aiolos";
                } else {
                    carrier = null;
                }
            }
        }
        return carrier;
    }

    private String speedexOrCourierCenterOrEasyMail() {
        // determine if tracking belongs to Speedex, Courier Center or Easy Mail
        String carrier = "";

        try {
            String url = "https://www.courier.gr/track/result?tracknr=" + tracking;
            Document doc = Jsoup.connect(url).get();
            // The page always contains empty date cells, even for unknown numbers, so look for
            // actual event rows (the same cells trackCourierCenter() reads)
            Elements events = doc.getElementsByClass("td action");
            if (events.size() > 0) {
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

        if (!carrier.equals("courierCenter") && !carrier.equals("easyMail")) {
            try {
                String url = "https://www.speedex.gr/speedex/NewTrackAndTrace.aspx?number=" + tracking;
                if (!parseSpeedex(Jsoup.connect(url).get()).isEmpty()) {
                    carrier = "speedex";
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!carrier.equals("")) {
            return carrier;
        } else {
            return null;
        }

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
                        // ACS hands some parcels to Skroutz Last Mile for the final delivery,
                        // which tracks them under the same number
                        if (isHandedToSkroutzLastMile(detailsList)) {
                            ArrayList<TrackingDetailsModel> skroutzDetails = trackSkroutzLastMile();
                            if (!skroutzDetails.isEmpty()) {
                                detailsList = mergeAcsWithSkroutzLastMile(detailsList, skroutzDetails);
                                carrier = "acsSkroutzLastMile";
                            }
                        }
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
                    case "skroutzLastMile":
                        detailsList = trackSkroutzLastMile();
                        break;
                    case "easyMail":
                        detailsList = trackEasyMail();
                        break;
                    case "cleverPoint":
                        detailsList = trackCleverPoint();
                        break;
                    case "eshop":
                        detailsList = trackEshop();
                        break;
                    case "plaisio":
                        detailsList = trackPlaisio();
                        // Plaisio's own history ends when it hands the order to a courier
                        ArrayList<TrackingDetailsModel> courierDetails = trackPlaisioCourier();
                        if (!courierDetails.isEmpty()) {
                            detailsList = mergePlaisioWithCourier(detailsList, courierDetails);
                            carrier = plaisioWithCourier(plaisioCourier[0]);
                        }
                        break;
                    case "boxNow":
                        detailsList = trackBoxNow();
                        break;
                    case "diakinisis":
                        detailsList = trackDiakinisis();
                        break;
                    case "aiolos":
                        detailsList = trackAiolos();
                        break;
                }
            }

            DatabaseHelper databaseHelper = new DatabaseHelper(context);
            int db_count = databaseHelper.getTrackingDetailsCount(tracking);
            int carrier_count = detailsList.size();
            boolean changedDetails = carrier_count == db_count
                    && rewordsStoredDetails(detailsList, databaseHelper.getTrackingDetails(tracking));

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
            } else if (changedDetails) {
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
