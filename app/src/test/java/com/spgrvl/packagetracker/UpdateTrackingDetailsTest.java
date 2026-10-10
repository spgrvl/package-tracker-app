package com.spgrvl.packagetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

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
