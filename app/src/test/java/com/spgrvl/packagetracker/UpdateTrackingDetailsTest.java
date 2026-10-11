package com.spgrvl.packagetracker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class UpdateTrackingDetailsTest {
    private TimeZone originalTimeZone;

    @Before
    public void useGreekTimeZone() {
        // Display times are formatted in the device time zone
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    private static TrackingDetailsModel detail(String status, String place, String datetime) {
        return new TrackingDetailsModel(status, place, datetime);
    }

    // --- Geniki ---

    @Test
    public void genikiCheckpointsAreNewestFirstWithDeliveryOnTop() throws Exception {
        String json = "{\"success\":true,\"123\":{\"TrackAndTraceResult\":{\"Result\":0,"
                + "\"Checkpoints\":{\"Checkpoint\":["
                + "{\"Status\":\"Δημιουργία ετικέτας αποστολής\",\"StatusCode\":\"C_NW\",\"StatusDate\":\"2026-08-19T13:21:14.4\",\"Shop\":\"ΑΠΟΣΤΟΛΕΑΣ\"},"
                + "{\"Status\":\"Άφιξη σε σημείο εξυπηρέτησης\",\"StatusCode\":\"C_A1\",\"StatusDate\":\"2026-09-04T09:28:14\",\"Shop\":\"ΠΑΤΡΑ\"},"
                + "{\"Status\":\"Καταχώριση στοιχείων παράδοσης\",\"StatusCode\":\"C_P1\",\"StatusDate\":\"2026-09-08T12:47:35\",\"Shop\":\"ΠΑΤΡΑ\"}]},"
                + "\"Status\":\"ΠΑΡΑΔΟΜΕΝΟ\",\"DeliveryDate\":\"2026-09-08T10:20:00\",\"Consignee\":\"TEST RECIPIENT\",\"DeliveredAt\":\"ΠΑΡΑΛΗΠΤΗΣ\"}}}";

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseGenikiResponse(json, "123");

        assertEquals(4, details.size());
        assertEquals("ΠΑΡΑΔΟΜΕΝΟ (TEST RECIPIENT)", details.get(0).getStatus());
        assertEquals("ΠΑΡΑΛΗΠΤΗΣ", details.get(0).getPlace());
        assertEquals("08/09/2026 10:20", details.get(0).getDatetime());
        assertEquals("Καταχώριση στοιχείων παράδοσης", details.get(1).getStatus());
        assertEquals("Δημιουργία ετικέτας αποστολής", details.get(3).getStatus());
        assertEquals("19/08/2026 13:21", details.get(3).getDatetime());
    }

    @Test
    public void genikiUnknownTrackingNumberGivesNoDetails() throws Exception {
        String json = "{\"success\":true,\"123\":{\"TrackAndTraceResult\":{\"Result\":9,\"Checkpoints\":{},"
                + "\"DeliveryDate\":\"0001-01-01T00:00:00\"}}}";
        assertTrue(UpdateTrackingDetails.parseGenikiResponse(json, "123").isEmpty());
    }

    @Test
    public void genikiSingleCheckpointObjectIsParsed() throws Exception {
        String json = "{\"success\":true,\"123\":{\"TrackAndTraceResult\":{\"Result\":0,"
                + "\"Checkpoints\":{\"Checkpoint\":{\"Status\":\"Shipment label created/printed\","
                + "\"StatusDate\":\"2026-08-19T13:21:14\",\"Shop\":\"SHIPPER\"}},"
                + "\"DeliveryDate\":\"0001-01-01T00:00:00\"}}}";

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseGenikiResponse(json, "123");

        assertEquals(1, details.size());
        assertEquals("Shipment label created/printed", details.get(0).getStatus());
    }

    @Test
    public void greekLocalTimesAreConvertedForDisplay() {
        assertEquals("08/07/2026 05:30", UpdateTrackingDetails.formatGreekLocalTime("2026-07-08T05:30:53.347"));
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        assertEquals("08/07/2026 02:30", UpdateTrackingDetails.formatGreekLocalTime("2026-07-08T05:30:53.347"));
    }

    // --- Speedex ---

    private static String speedexCard(String title, String... lines) {
        StringBuilder card = new StringBuilder("<li class=\"timeline-item\"><div class=\"timeline-card card\">"
                + "<div class=\"card-header\"><h4 class=\"card-title" + (title.contains("<p>") ? " delivered-title" : "")
                + "\">" + title + "</h4>");
        for (String line : lines) {
            card.append("<p class=\"card-subtitle text-muted mb-0 pt-1\"><span class=\"font-small-3\">")
                    .append(line).append("</span></p>");
        }
        return card.append("</div></div></li>").toString();
    }

    @Test
    public void speedexEventsAreNewestFirstWithPlaceAndRecipient() {
        // Structure of the current Speedex page; the recipient name here is made up
        String html = "<ul>"
                + speedexCard("ΑΝΑΜΟΝΗ ΑΝΑΘΕΣΗΣ ΑΠΟ ΑΠΟΣΤΟΛΕΑ", "23/07/2026 στις 08:41", ",", "")
                + speedexCard("ΕΙΣΑΓΩΓΗ ΣΕ Δ/Κ", "23/07/2026 στις 13:12", ", ΚΗΦΙΣΙΑ", "")
                + speedexCard("ΕΙΣΑΓΩΓΗ ΣΕ ΚΑΤΑΣΤΗΜΑ", "25/07/2026 στις 08:54", "ΠΡΟΕΚΤΑΣΗ ΑΡΓΥΡΟΚΑΣΤΡΟΥ, ΚΩΣ",
                "2242000000", "<a href=\"https://www.google.com/maps?q=0,0\">View on Google Maps</a>")
                + speedexCard("<p>Η ΑΠΟΣΤΟΛΗ ΠΑΡΑΔΟΘΗΚΕ</p>ΠΑΡΑΛΗΠΤΗΣ: Ονοματεπώνυμο Παραλήπτη",
                "28/07/2026 στις 09:23", ", ΚΩΣ", "")
                + "</ul>";

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseSpeedex(org.jsoup.Jsoup.parse(html));

        assertEquals(4, details.size());
        assertEquals("Η ΑΠΟΣΤΟΛΗ ΠΑΡΑΔΟΘΗΚΕ (ΠΑΡΑΛΗΠΤΗΣ: Ονοματεπώνυμο Παραλήπτη)", details.get(0).getStatus());
        assertEquals("ΚΩΣ", details.get(0).getPlace());
        assertEquals("28/07/2026 09:23", details.get(0).getDatetime());
        assertEquals("ΠΡΟΕΚΤΑΣΗ ΑΡΓΥΡΟΚΑΣΤΡΟΥ, ΚΩΣ", details.get(1).getPlace());
        assertEquals("ΚΗΦΙΣΙΑ", details.get(2).getPlace());
        assertEquals("", details.get(3).getPlace());
        assertEquals("23/07/2026 08:41", details.get(3).getDatetime());
    }

    @Test
    public void speedexUnknownNumberGivesNoDetails() {
        String html = "<section id=\"timeline\"><h3 class=\"page-title\">Track &amp; Trace</h3></section>";
        assertTrue(UpdateTrackingDetails.parseSpeedex(org.jsoup.Jsoup.parse(html)).isEmpty());
    }

    // --- e-shop.gr ---

    private static String eshopRow(String rowClass, String stage, String datetime) {
        return "<tr><td class=\"" + rowClass + "\"><img src=\"check_ok.png\"></td>"
                + "<td class=\"" + rowClass + "\">" + stage + "</td>"
                + "<td class=\"" + rowClass + "\" colspan=2>" + datetime + "</td></tr>";
    }

    @Test
    public void eshopCompletedOrderIsNewestFirstAndTranslated() {
        String html = "<table>"
                + eshopRow("statusrowok", "Υποβολή", "14/03/21 01:50")
                + eshopRow("statusrowok", "Έλεγχος Ομαλότητας", "16/03/21 09:13")
                + eshopRow("statusrowok", "Αποστολή", "26/03/21 13:20")
                + "<tr><td class=\"statusreport\" colspan=4>Η παραγγελία σας ολοκληρώθηκε. Ευχαριστούμε για την προτίμηση.</td></tr>"
                + "</table>";
        java.util.Map<String, String> english = new HashMap<>();
        english.put("Υποβολή", "Order submitted");
        english.put("Αποστολή", "Shipped");
        english.put("Η παραγγελία σας ολοκληρώθηκε. Ευχαριστούμε για την προτίμηση.", "Your order has been completed.");

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseEshopStatus(org.jsoup.Jsoup.parse(html), english);

        assertEquals(4, details.size());
        assertEquals("Your order has been completed.", details.get(0).getStatus());
        assertEquals("26/03/2021 13:20", details.get(0).getDatetime());
        assertEquals("Shipped", details.get(1).getStatus());
        assertEquals("Έλεγχος Ομαλότητας", details.get(2).getStatus()); // untranslated text is kept
        assertEquals("Order submitted", details.get(3).getStatus());
        assertEquals("14/03/2021 01:50", details.get(3).getDatetime());
    }

    @Test
    public void eshopStatusTableInsideLayoutTablesIsParsedOnce() {
        // The real page nests the status table in several layout tables
        String statusTable = "<table>"
                + eshopRow("statusrowok", "Υποβολή", "14/03/21 01:50")
                + eshopRow("statusrowok", "Αποστολή", "26/03/21 13:20")
                + "</table>";
        String html = "<table><tr><td><table><tr><td>" + statusTable + "</td></tr></table></td></tr></table>";

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseEshopStatus(
                org.jsoup.Jsoup.parse(html), new HashMap<>());

        assertEquals(2, details.size());
        assertEquals("Αποστολή", details.get(0).getStatus());
        assertEquals("Υποβολή", details.get(1).getStatus());
    }

    @Test
    public void eshopPendingStagesAreSkipped() {
        String html = "<table>"
                + eshopRow("statusrowok", "Υποβολή", "08/10/26 10:00")
                + eshopRow("statusrowok", "Έλεγχος Ομαλότητας", "08/10/26 12:30")
                + eshopRow("statusrownok", "Συγκέντρωση Προϊόντων", "")
                + "</table>";

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseEshopStatus(
                org.jsoup.Jsoup.parse(html), new HashMap<>());

        assertEquals(2, details.size());
        assertEquals("Έλεγχος Ομαλότητας", details.get(0).getStatus());
    }

    @Test
    public void eshopUnknownOrderGivesNoDetails() {
        assertTrue(UpdateTrackingDetails.parseEshopStatus(
                org.jsoup.Jsoup.parse("<html><body>no order</body></html>"), new HashMap<>()).isEmpty());
    }

    // --- Clever Point ---

    @Test
    public void cleverPointSummaryOnlyBecomesArrivalAndCollection() throws Exception {
        // Older shipments keep no event history, only the summary
        String json = "{\"mainData\":{\"shipmentAwb\":\"123\",\"checkInDt\":\"2023-12-06T14:44:45.727219\","
                + "\"deliveryStationName\":\"STORE - KYPSELI\",\"shipmentStatusNameDesc\":\"Παραδόθηκε\","
                + "\"consigneeName\":\"TEST RECIPIENT\"},"
                + "\"podData\":{\"deliveredTo\":\"ΔΟΚΙ***** ΠΑΡΑ***** \",\"deliveryDateTime\":\"2023-12-06T16:01:01.866457\","
                + "\"stationName\":\"STORE - KYPSELI\"},\"trackingData\":[]}";

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseCleverPointInfo(json, "Arrived at the Clever Point");

        assertEquals(2, details.size());
        assertEquals("Παραδόθηκε (ΔΟΚΙ***** ΠΑΡΑ*****)", details.get(0).getStatus());
        assertEquals("STORE - KYPSELI", details.get(0).getPlace());
        assertEquals("06/12/2023 16:01", details.get(0).getDatetime());
        assertEquals("Arrived at the Clever Point", details.get(1).getStatus());
        assertEquals("06/12/2023 14:44", details.get(1).getDatetime());
    }

    @Test
    public void cleverPointHistoryIsUsedNewestFirstWithoutDuplicatingTheCollection() throws Exception {
        String json = "{\"mainData\":{\"checkInDt\":\"2026-10-05T10:00:00\",\"shipmentStatusNameDesc\":\"Delivered\"},"
                + "\"podData\":{\"deliveredTo\":\"ΔΟΚΙ***** ΠΑΡΑ*****\",\"deliveryDateTime\":\"2026-10-06T18:30:00\",\"stationName\":\"STORE\"},"
                + "\"trackingData\":["
                + "{\"timestamp\":\"2026-10-04T09:15:00\",\"stationName\":null,\"trackingNote\":\"Shipment created\"},"
                + "{\"timestamp\":\"2026-10-06T18:30:00\",\"stationName\":\"STORE\",\"trackingNote\":\"Delivered\"},"
                + "{\"timestamp\":\"2026-10-05T10:00:00\",\"stationName\":\"STORE\",\"trackingNote\":\"Arrived at point\"}]}";

        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseCleverPointInfo(json, "unused");

        assertEquals(3, details.size());
        assertEquals("Delivered (ΔΟΚΙ***** ΠΑΡΑ*****)", details.get(0).getStatus());
        assertEquals("Arrived at point", details.get(1).getStatus());
        assertEquals("Shipment created", details.get(2).getStatus());
        assertEquals("", details.get(2).getPlace());
    }

    @Test
    public void cleverPointWithoutAnyDataGivesNoDetails() throws Exception {
        assertTrue(UpdateTrackingDetails.parseCleverPointInfo("{\"mainData\":null,\"podData\":null,\"trackingData\":[]}", "x").isEmpty());
    }

    // --- Aiolos ---

    private static Document aiolosPage(String... rows) {
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < rows.length; i += 3) {
            history.append("<tr><td>").append(rows[i]).append("</td><td>").append(rows[i + 1])
                    .append("</td><td>").append(rows[i + 2]).append("</td></tr>");
        }
        return Jsoup.parse("<div id=\"tracking_results\"><table class=\"tracking_table\"><tr><th>Κωδικός Αποστολής</th></tr>"
                + "<tr><td>0000000104</td><td>05/10/2026</td><td>-</td><td>-</td></tr></table>"
                + "<table class=\"tracking_table\"><tr><th align=\"left\">Ημ/νία - Ώρα</th><th>Περιγραφή</th>"
                + "<th>Στοιχεία Παράδοσης</th></tr>" + history + "</table></div>");
    }

    private static Map<String, String> aiolosTranslations(String valuesDir) {
        Map<String, String> strings = StringResources.load(valuesDir).stringsWithPrefix("aiolos_");
        Map<String, String> translations = new HashMap<>();
        for (String[] message : UpdateTrackingDetails.AIOLOS_MESSAGES) {
            assertTrue("missing " + valuesDir + "/" + message[1], strings.containsKey(message[1]));
            translations.put(message[0], strings.get(message[1]));
        }
        return translations;
    }

    private static List<String> statuses(List<TrackingDetailsModel> details) {
        List<String> statuses = new ArrayList<>();
        for (TrackingDetailsModel detail : details) {
            statuses.add(detail.getStatus() + " | " + detail.getPlace() + " | " + detail.getDatetime());
        }
        return statuses;
    }

    @Test
    public void aiolosDeliveryDetailsNameTheRecipientOfTheDelivery() {
        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseAiolos(aiolosPage(
                "25/01/2023 08:41", "ΣΕ ΚΟΥΡΙΕΡ ΠΡΟΣ ΠΑΡΑΔΟΣΗ ΣΤΟΝ ΠΑΡΑΛΗΠΤΗ", "ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ",
                "25/01/2023 18:11", "ΑΠΑΡΑΔΟΤΑ", "ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ",
                "27/01/2023 16:18", "ΑΠΟΣΤΟΛΗ ΠΑΡΑΔΟΘΗΚΕ", "ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ",
                "30/01/2023 11:10", "ΣΤΟΙΧΕΙΑ ΠΑΡΑΔΟΣΗΣ", "ΣΦΡΑΓΙΔΑ"), aiolosTranslations("values"));
        assertEquals(Arrays.asList(
                "Shipment delivered (ΣΦΡΑΓΙΔΑ) | ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ | 27/01/2023 16:18",
                "Not delivered | ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ | 25/01/2023 18:11",
                "With courier for delivery to the recipient | ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ | 25/01/2023 08:41"), statuses(details));
    }

    @Test
    public void aiolosDeliveryDetailsWithoutADeliveryStandForIt() {
        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseAiolos(aiolosPage(
                "29/09/2026 08:53", "ΣΕ ΚΟΥΡΙΕΡ ΠΡΟΣ ΠΑΡΑΔΟΣΗ ΣΤΟΝ ΠΑΡΑΛΗΠΤΗ", "ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ",
                "29/09/2026 09:34", "ΣΤΟΙΧΕΙΑ ΠΑΡΑΔΟΣΗΣ", "TEST RECIPIENT",
                "30/09/2026 10:00", "ΚΑΤΙ ΚΑΙΝΟΥΡΓΙΟ", "ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ"), aiolosTranslations("values-el"));
        assertEquals(Arrays.asList(
                // Unknown messages are kept
                "ΚΑΤΙ ΚΑΙΝΟΥΡΓΙΟ | ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ | 30/09/2026 10:00",
                "ΑΠΟΣΤΟΛΗ ΠΑΡΑΔΟΘΗΚΕ (TEST RECIPIENT) |  | 29/09/2026 09:34",
                "ΣΕ ΚΟΥΡΙΕΡ ΠΡΟΣ ΠΑΡΑΔΟΣΗ ΣΤΟΝ ΠΑΡΑΛΗΠΤΗ | ΑΘΗΝΑ-ΚΕΝΤΡΙΚΟ | 29/09/2026 08:53"), statuses(details));
    }

    @Test
    public void aiolosGreekMessagesAreIdenticalToTheSite() {
        Map<String, String> el = aiolosTranslations("values-el");
        for (String[] message : UpdateTrackingDetails.AIOLOS_MESSAGES) {
            assertEquals(message[1], message[0], el.get(message[0]));
        }
        aiolosTranslations("values");
    }

    @Test
    public void aiolosUnknownNumber() {
        // An unknown number only gets the search form, without result tables
        assertTrue(UpdateTrackingDetails.parseAiolos(Jsoup.parse("<form id=\"search_shipping2\"></form>"),
                new HashMap<>()).isEmpty());
    }

    // --- Diakinisis ---

    // Shaped like a real response: a JSON array of {status, message, data}, with \' escapes
    private static final String DIAKINISIS_DELIVERED_ORDER = "[\"1\",\"\",{\"order\":{\"order_id\":\"0000000103\","
            + "\"send_company\":\"ΠΛΑΙΣΙΟ COMPUTERS A.E.B.E.\",\"recipient\":\"TEST RECIPIENT Β\\' ΑΕ\"},"
            + "\"status\":{\"status\":\"Παραδόθηκε η παραγγελία σας.\",\"status_date\":\"31/07/2026\",\"status_time\":\"18:21:19\"},"
            + "\"history\":["
            + diakinisisEvent("Καταχωρήθηκε η εντολή σας και περιμένουμε τον αποστολέα να ετοιμάσει την αποστολή σας.", "27/07/2026", "07:24:31", "Imported From : PLAISIO 27.07.2026.xls") + ","
            + diakinisisEvent("Η παραγγελία σας βρίσκεται στο κέντρο διανομής μας.", "27/07/2026", "13:13:08", "ΣΧΗΜΑΤΑΡΙ") + ","
            + diakinisisEvent("H παραγγελία σας έχει προγραμματιστεί για δρομολόγηση.", "27/07/2026", "19:05:14", "") + ","
            + diakinisisEvent("Κάτι καινούργιο.", "28/07/2026", "09:00:00", "") + ","
            + diakinisisEvent("Παραδόθηκε η παραγγελία σας.", "31/07/2026", "18:21:19", "")
            + "],\"documents\":[],\"ext_link\":\"\"}]";

    private static String diakinisisEvent(String action, String date, String time, String comment) {
        return "{\"action\":\"" + action + "\",\"action_reason_code\":\"\",\"action_date\":\"" + date
                + "\",\"action_time\":\"" + time + "\",\"action_comment\":\"" + comment + "\",\"action_photo\":[]}";
    }

    private static Map<String, String> diakinisisTranslations(String valuesDir) {
        Map<String, String> strings = StringResources.load(valuesDir).stringsWithPrefix("diakinisis_");
        Map<String, String> translations = new HashMap<>();
        for (String[] message : UpdateTrackingDetails.DIAKINISIS_MESSAGES) {
            assertTrue("missing " + valuesDir + "/" + message[1], strings.containsKey(message[1]));
            translations.put(message[0], strings.get(message[1]));
        }
        return translations;
    }

    @Test
    public void diakinisisNewestFirstWithDepotAndRecipient() throws Exception {
        List<TrackingDetailsModel> details = UpdateTrackingDetails.parseDiakinisis(DIAKINISIS_DELIVERED_ORDER,
                diakinisisTranslations("values"));
        assertEquals(5, details.size());
        assertEquals("Your order has been delivered. (TEST RECIPIENT Β' ΑΕ)", details.get(0).getStatus());
        assertEquals("31/07/2026 18:21", details.get(0).getDatetime());
        // Unknown messages are kept in Greek
        assertEquals("Κάτι καινούργιο.", details.get(1).getStatus());
        // Written with a Latin "H" by Diakinisis
        assertEquals("Your order has been scheduled for dispatch.", details.get(2).getStatus());
        assertEquals("Your order is at our distribution center.", details.get(3).getStatus());
        assertEquals("ΣΧΗΜΑΤΑΡΙ", details.get(3).getPlace());
        // The sender's import file isn't a place
        assertEquals("", details.get(4).getPlace());
        assertEquals("27/07/2026 07:24", details.get(4).getDatetime());
    }

    @Test
    public void diakinisisGreekMessagesAreIdenticalToTheSite() {
        Map<String, String> el = diakinisisTranslations("values-el");
        for (String[] message : UpdateTrackingDetails.DIAKINISIS_MESSAGES) {
            assertEquals(message[1], message[0], el.get(message[0]));
        }
        diakinisisTranslations("values");
    }

    @Test
    public void diakinisisUnknownNumber() throws Exception {
        assertTrue(UpdateTrackingDetails.parseDiakinisis("[\"0\",\"Δεν βρέθηκε κάμια Παραγγελία.\",[]]",
                new HashMap<>()).isEmpty());
    }

    // --- Plaisio ---

    // A real store-pickup order, as returned by plaisio.gr (orders hold no personal data)
    private static final String PLAISIO_PICKUP_ORDER = plaisioOrder(
            plaisioEvent("24/07/2026 19:15", "Η παραγγελία σου καταχωρήθηκε με επιτυχία και έχει προχωρήσει στο στάδιο της επεξεργασίας.", null),
            plaisioEvent("24/07/2026 19:25", "Η παραγγελία σου είναι στο στάδιο της συλλογής των προϊόντων.", null),
            plaisioEvent("27/07/2026 06:00", "Η παραγγελία σου δρομολογήθηκε για παραλαβή από το ΚΑΤΑΣΤΗΜΑ ΠΑΤΡΑΣ - ΚΕΝΤΡΟ.", null),
            plaisioEvent("27/07/2026 06:00", "Η παραγγελία σου βρίσκεται στο ΚΑΤΑΣΤΗΜΑ ΠΑΤΡΑΣ - ΚΕΝΤΡΟ και σε περιμένει να την παραλάβεις σήμερα 27/07/2026.", null),
            plaisioEvent("10/10/2026 21:30", "Η παραγγελία σου έχει ήδη παραδοθεί.", null));

    // One of each wording seen in a sample of real orders
    private static final String[] PLAISIO_MESSAGES = {
            "Η παραγγελία σου καταχωρήθηκε με επιτυχία και έχει προχωρήσει στο στάδιο της επεξεργασίας.",
            "Η παραγγελία σου καταχωρήθηκε με επιτυχία και θα είναι έτοιμη για παραλαβή 18/12/2020 10:00 από το ΚΑΤΑΣΤΗΜΑ ΒΕΝΙΖΕΛΟΥ ΘΕΣΣΑΛΟΝΙΚ.",
            "Η παραγγελία σου βρίσκεται στο στάδιο της αναμονής.",
            "Η παραγγελία σου είναι στο στάδιο της συλλογής των προϊόντων.",
            "Η παραγγελία σου έχει τιμολογηθεί και προετοιμάζεται για αποστολή.",
            "Η παραγγελία σου έχει τιμολογηθεί και θα είναι έτοιμη για παραλαβή 18/12/2020 10:00 από το ΚΑΤΑΣΤΗΜΑ ΒΕΝΙΖΕΛΟΥ ΘΕΣΣΑΛΟΝΙΚ.",
            "Η παραγγελία σου έχει τιμολογηθεί και προετοιμάζεται για παράδοση στο πρακτορείο μεταφορών ΔΙΑΚΙΝΗΣΙΣ ΑΕ",
            "Η παραγγελία σου έχει τιμολογηθεί και προετοιμάζεται για παράδοση στο πρακτορείο μεταφορών D.L.S. A.E.",
            "Η παραγγελία σου έχει τιμολογηθεί και προετοιμάζεται για παράδοση στην BOX NOW",
            "Η παραγγελία σου είναι έτοιμη και την παραδώσαμε στην courier 29/07.",
            "Η παραγγελία σου έχει παραληφθεί από την BOX NOW",
            "Η παραγγελία σου έχει ήδη δρομολογηθεί για παράδοση στο χώρο σου με εκτιμώμενη ημερομηνία έως τις 20/02/2020.",
            "Η παραγγελία σου με αρ.Voucher 0000000106 έχει ξεπεράσει την εκτιμώμενη ημερομηνία παράδοσης έως τις 17/03/2021 από την Courier ΓΕΝΙΚΗ ΤΑΧΥΔΡΟΜΙΚΗ. Υπάρχουν καθυστερήσεις λόγω του μεγάλου όγκου αποστολών!",
            "Η παραγγελία σου δρομολογήθηκε για παραλαβή από το ΚΑΤΑΣΤΗΜΑ ΣΥΝΤΑΓΜΑΤΟΣ.",
            "Η παραγγελία σου βρίσκεται στο ΚΑΤΑΣΤΗΜΑ ΣΥΝΤΑΓΜΑΤΟΣ και σε περιμένει να την παραλάβεις σήμερα 30/12/2025.",
            "Η παραγγελία σου έχει ήδη παραδοθεί.",
            "Η παραγγελία σου είναι στο στάδιο της συλλογής των προϊόντων με εκτιμώμενη ημερομηνία παράδοσης έως τις 14/10/2026.",
            "Η παραγγελία σου έχει ήδη δρομολογηθεί για παράδοση στο χώρο σου στις 08/10/2026 με εκτιμώμενη ώρα παράδοσης 10:20 - 12:20.",
    };

    private static String plaisioOrder(String... events) {
        return "{\"orderHistory\":[" + String.join(",", events) + "]}";
    }

    private static String plaisioEvent(String datetime, String description, String courierLink) {
        return "{\"transactionDate\":\"" + datetime + "\",\"statusDescription\":\"" + description
                + "\",\"statusCode\":\"X\",\"courierTrackingLink\":"
                + (courierLink == null ? "null" : "\"" + courierLink + "\"") + "}";
    }

    private static Map<Pattern, String> plaisioTranslations(String valuesDir) {
        Map<String, String> strings = StringResources.load(valuesDir).stringsWithPrefix("plaisio_");
        return UpdateTrackingDetails.plaisioTranslations(name -> {
            assertTrue("missing " + valuesDir + "/" + name, strings.containsKey(name));
            return strings.get(name);
        });
    }

    private static final Map<String, String> PLAISIO_COURIER_NAMES = new HashMap<>();

    static {
        PLAISIO_COURIER_NAMES.put("boxNow", "BoxNow");
        PLAISIO_COURIER_NAMES.put("geniki", "Geniki Taxydromiki");
    }

    private static List<TrackingDetailsModel> parsePlaisio(String json, String valuesDir) throws Exception {
        return UpdateTrackingDetails.parsePlaisioHistory(json, plaisioTranslations(valuesDir), PLAISIO_COURIER_NAMES);
    }

    @Test
    public void plaisioOrderIsNewestFirstAndTranslatedWithStoreAndDates() throws Exception {
        List<TrackingDetailsModel> details = parsePlaisio(PLAISIO_PICKUP_ORDER, "values");

        assertEquals(5, details.size());
        assertEquals("Your order has been delivered.", details.get(0).getStatus());
        assertEquals("10/10/2026 21:30", details.get(0).getDatetime());
        assertEquals("Your order is ready for pickup at ΚΑΤΑΣΤΗΜΑ ΠΑΤΡΑΣ - ΚΕΝΤΡΟ from 27/07/2026.",
                details.get(1).getStatus());
        assertEquals("Your order is on its way to ΚΑΤΑΣΤΗΜΑ ΠΑΤΡΑΣ - ΚΕΝΤΡΟ for pickup.", details.get(2).getStatus());
        assertEquals("Your order was placed successfully and is being processed.", details.get(4).getStatus());
        assertEquals("24/07/2026 19:15", details.get(4).getDatetime());
    }

    @Test
    public void plaisioEveryKnownMessageIsTranslatedAndGreekMatchesTheSite() throws Exception {
        for (String message : PLAISIO_MESSAGES) {
            String json = plaisioOrder(plaisioEvent("01/01/2026 10:00", message, null));
            String english = parsePlaisio(json, "values").get(0).getStatus();
            String greek = parsePlaisio(json, "values-el").get(0).getStatus();

            assertFalse("not translated: " + message, english.startsWith("Η παραγγελία"));
            // The Greek resources reproduce Plaisio's own wording, placeholders included
            assertEquals(message, greek);
        }
    }

    @Test
    public void plaisioTranslationsKeepStoresDatesAndCouriers() throws Exception {
        String json = plaisioOrder(
                plaisioEvent("01/01/2026 10:00", PLAISIO_MESSAGES[1], null),
                plaisioEvent("01/01/2026 10:01", PLAISIO_MESSAGES[7], null),
                plaisioEvent("01/01/2026 10:02", PLAISIO_MESSAGES[12], null));
        List<TrackingDetailsModel> details = parsePlaisio(json, "values");

        assertEquals("Your order with voucher 0000000106 has passed its estimated delivery date of 17/03/2021 "
                        + "with the courier ΓΕΝΙΚΗ ΤΑΧΥΔΡΟΜΙΚΗ. There are delays due to the high volume of shipments!",
                details.get(0).getStatus());
        assertEquals("Your order has been invoiced and is being prepared for handover to the transport agency D.L.S. A.E.",
                details.get(1).getStatus());
        assertEquals("Your order was placed successfully and will be ready for pickup on 18/12/2020 at 10:00 "
                + "from ΚΑΤΑΣΤΗΜΑ ΒΕΝΙΖΕΛΟΥ ΘΕΣΣΑΛΟΝΙΚ.", details.get(2).getStatus());
    }

    @Test
    public void plaisioUnknownMessagesAreKeptAsTheyAre() throws Exception {
        String json = plaisioOrder(plaisioEvent("22/10/2025 10:00", "Κάποιο νέο μήνυμα", null));
        assertEquals("Κάποιο νέο μήνυμα", parsePlaisio(json, "values").get(0).getStatus());
    }

    @Test
    public void plaisioCourierHandoverShowsTheCourierNumber() throws Exception {
        String json = plaisioOrder(
                plaisioEvent("15/11/2024 18:31", PLAISIO_MESSAGES[8], null),
                plaisioEvent("15/11/2024 18:31", PLAISIO_MESSAGES[10], "https://boxnow.gr/?track=0000000107"));

        assertArrayEquals(new String[]{"boxNow", "0000000107"}, UpdateTrackingDetails.plaisioCourier(json));
        List<TrackingDetailsModel> details = parsePlaisio(json, "values");
        assertEquals("BoxNow 0000000107", details.get(0).getPlace());
        assertTrue(details.get(0).getStatus().endsWith(" https://boxnow.gr/?track=0000000107"));
        assertEquals("", details.get(1).getPlace());
    }

    @Test
    public void storedHistoryIsOnlyReplacedWhenReworded() {
        List<TrackingDetailsModel> stored = List.of(
                detail("Delivered", "ΑΘΗΝΑ", "02/01/2026 10:00"),
                detail("Picked up", "", "01/01/2026 10:00"));
        // Another language or a newer app version's wording
        assertTrue(UpdateTrackingDetails.rewordsStoredDetails(List.of(
                detail("Παραδόθηκε", "ΑΘΗΝΑ", "02/01/2026 10:00"),
                detail("Picked up", "", "01/01/2026 10:00")), stored));
        assertFalse(UpdateTrackingDetails.rewordsStoredDetails(List.of(
                detail("Delivered", "ΑΘΗΝΑ", "02/01/2026 10:00"),
                detail("Picked up", "", "01/01/2026 10:00")), stored));
        // History the carrier deleted, even if what's left is worded differently
        assertFalse(UpdateTrackingDetails.rewordsStoredDetails(List.of(
                detail("Παραδόθηκε", "ΑΘΗΝΑ", "02/01/2026 10:00")), stored));
        assertFalse(UpdateTrackingDetails.rewordsStoredDetails(List.of(), stored));
    }

    @Test
    public void plaisioLinksOfUntrackedCouriersAreShownToo() throws Exception {
        String json = plaisioOrder(plaisioEvent("01/01/2026 10:00", "Κάποιο νέο μήνυμα", "https://example.com/0000000108"));
        TrackingDetailsModel detail = parsePlaisio(json, "values").get(0);
        assertEquals("Κάποιο νέο μήνυμα https://example.com/0000000108", detail.getStatus());
        assertEquals("", detail.getPlace());
    }

    @Test
    public void plaisioCourierLinksOfEveryKnownCourier() throws Exception {
        String[][] links = {
                {"https://www.taxydromiki.com/track/0000000105", "geniki", "0000000105"},
                {"http://www.speedex.gr/isapohi.asp?voucher_code=000000000101", "speedex", "000000000101"},
                {"https://itemsearch.elta.gr/Query/Direct/EL123456789GR", "elta", "EL123456789GR"},
                {"https://trackntrace.easymail.gr/000000000102", "easyMail", "000000000102"},
                {"https://pod.diakinisis.gr/tnt.php?id=0000000103", "diakinisis", "0000000103"},
                {"https://www.aioloscourier.gr/track-trace/?shipping_num=0000000104", "aiolos", "0000000104"},
        };
        for (String[] link : links) {
            String json = plaisioOrder(plaisioEvent("01/01/2026 10:00", PLAISIO_MESSAGES[11], link[0]));
            assertArrayEquals(link[0], new String[]{link[1], link[2]}, UpdateTrackingDetails.plaisioCourier(json));
        }
        assertNull(UpdateTrackingDetails.plaisioCourier(PLAISIO_PICKUP_ORDER));
        assertNull(UpdateTrackingDetails.plaisioCourier(plaisioOrder(
                plaisioEvent("01/01/2026 10:00", PLAISIO_MESSAGES[11], "https://example.com/unknown"))));
    }

    @Test
    public void plaisioCombinedWithEveryTrackedCourierIsNamed() {
        for (String valuesDir : new String[]{"values", "values-el"}) {
            Map<String, String> strings = StringResources.load(valuesDir).stringsWithPrefix("");
            for (String courier : new String[]{"boxNow", "geniki", "speedex", "easyMail", "elta", "diakinisis", "aiolos"}) {
                String carrier = UpdateTrackingDetails.plaisioWithCourier(courier);
                assertEquals(valuesDir + "/" + carrier, strings.get("plaisio") + " → " + strings.get(courier),
                        strings.get(carrier));
            }
        }
    }

    @Test
    public void plaisioIsMergedWithTheCourierNewestFirst() {
        List<TrackingDetailsModel> plaisio = Arrays.asList(
                detail("Your order has been picked up by BOX NOW", "BoxNow 0000000107", "15/11/2024 18:31"),
                detail("Your order has been invoiced and is being prepared for handover to BOX NOW", "", "15/11/2024 18:31"),
                detail("Your order was placed successfully and is being processed.", "", "15/11/2024 16:00"));
        List<TrackingDetailsModel> courier = Arrays.asList(
                detail("Delivered", "Locker", "16/11/2024 12:10"),
                detail("Accepted", "", "15/11/2024 18:31"));

        List<String> statuses = new ArrayList<>();
        for (TrackingDetailsModel detail : UpdateTrackingDetails.mergePlaisioWithCourier(plaisio, courier)) {
            statuses.add(detail.getStatus());
        }
        assertEquals(Arrays.asList(
                "Delivered",
                "Accepted",
                "Your order has been picked up by BOX NOW",
                "Your order has been invoiced and is being prepared for handover to BOX NOW",
                "Your order was placed successfully and is being processed."), statuses);
    }

    @Test
    public void plaisioUnknownOrderGivesNoDetails() throws Exception {
        String notFound = "{\"type\":\"https://httpstatuses.io/404\",\"title\":\"Not Found\",\"status\":404}";
        assertTrue(parsePlaisio(notFound, "values").isEmpty());
        assertNull(UpdateTrackingDetails.plaisioCourier(notFound));
    }

    // --- ACS handed over to Skroutz Last Mile ---

    private static List<TrackingDetailsModel> acsHandedToSkroutz() {
        return Arrays.asList(
                detail("ΠΑΡΑΔΟΣΗ - SLM DELIVERY", "W50 - SLM ΠΑΤΡΑ", "13/07/2026 23:47"),
                detail("ΑΝΑΧΩΡΗΣΗ ΑΠΟ ΚΕΝΤΡΟ ΔΙΑΛΟΓΗΣ", "HUB ΑΘΗΝΩΝ", "08/07/2026 05:30"),
                detail("ΚΕΝΤΡΟ ΔΙΑΛΟΓΗΣ", "HUB ΑΘΗΝΩΝ", "08/07/2026 05:30"),
                detail("ΑΝΑΧΩΡΗΣΗ W50 - SLM ΠΑΤΡΑ", "ΚΟΖΑΝΗ", "07/07/2026 15:31"),
                detail("ΠΑΡΑΛΑΒΗ ΑΠΟ ΑΠΟΣΤΟΛΕΑ", "ΚΟΖΑΝΗ", "07/07/2026 15:30"));
    }

    private static List<TrackingDetailsModel> skroutzForSameParcel() {
        return Arrays.asList(
                detail("Παραδόθηκε", "", "13/07/2026 23:47"),
                detail("Παράδοση σε Skroutz Point", "", "13/07/2026 14:28"),
                detail("Η αποστολή βρίσκεται σε δρομολόγιο παράδοσης", "", "13/07/2026 10:50"),
                detail("Η αποστολή βρίσκεται στο SLM HUB Πάτρα", "", "09/07/2026 10:11"),
                detail("Η αποστολή βρίσκεται στο Hub ACS", "", "08/07/2026 07:15"),
                detail("Παραλάβαμε το δέμα από το κατάστημα", "", "07/07/2026 15:35"));
    }

    @Test
    public void acsHandoverToSkroutzIsDetected() {
        assertTrue(UpdateTrackingDetails.isHandedToSkroutzLastMile(acsHandedToSkroutz()));
        assertFalse(UpdateTrackingDetails.isHandedToSkroutzLastMile(Arrays.asList(
                detail("ΠΑΡΑΔΟΣΗ", "ΚΑΤΑΣΤΗΜΑ ΠΑΤΡΑΣ", "13/07/2026 12:00"),
                detail("ΠΑΡΑΛΑΒΗ ΑΠΟ ΑΠΟΣΤΟΛΕΑ", "ΚΟΖΑΝΗ", "07/07/2026 15:30"))));
    }

    @Test
    public void mergedTimelineCoversTheWholeRouteWithoutDuplicates() {
        List<TrackingDetailsModel> merged = UpdateTrackingDetails.mergeAcsWithSkroutzLastMile(
                acsHandedToSkroutz(), skroutzForSameParcel());

        List<String> statuses = new ArrayList<>();
        for (TrackingDetailsModel detail : merged) {
            statuses.add(detail.getStatus());
        }
        assertEquals(Arrays.asList(
                "Παραδόθηκε",
                "Παράδοση σε Skroutz Point",
                "Η αποστολή βρίσκεται σε δρομολόγιο παράδοσης",
                "Η αποστολή βρίσκεται στο SLM HUB Πάτρα",
                "Η αποστολή βρίσκεται στο Hub ACS",
                "ΑΝΑΧΩΡΗΣΗ ΑΠΟ ΚΕΝΤΡΟ ΔΙΑΛΟΓΗΣ",
                "ΚΕΝΤΡΟ ΔΙΑΛΟΓΗΣ",
                "ΑΝΑΧΩΡΗΣΗ W50 - SLM ΠΑΤΡΑ",
                "ΠΑΡΑΛΑΒΗ ΑΠΟ ΑΠΟΣΤΟΛΕΑ"), statuses);
    }

    @Test
    public void skroutzEventsOlderThanAcsAreDroppedWhileStillWithAcs() {
        // Before ACS reaches the hub, Skroutz only mirrors the ACS pickup
        List<TrackingDetailsModel> acs = Arrays.asList(
                detail("ΑΝΑΧΩΡΗΣΗ W50 - SLM ΠΑΤΡΑ", "ΚΟΖΑΝΗ", "07/07/2026 15:31"),
                detail("ΠΑΡΑΛΑΒΗ ΑΠΟ ΑΠΟΣΤΟΛΕΑ", "ΚΟΖΑΝΗ", "07/07/2026 15:30"));
        List<TrackingDetailsModel> skroutz = Arrays.asList(
                detail("Παραλάβαμε το δέμα από το κατάστημα", "", "07/07/2026 15:30"));

        List<TrackingDetailsModel> merged = UpdateTrackingDetails.mergeAcsWithSkroutzLastMile(acs, skroutz);

        assertEquals(2, merged.size());
        assertEquals("ΑΝΑΧΩΡΗΣΗ W50 - SLM ΠΑΤΡΑ", merged.get(0).getStatus());
    }
}
