package com.spgrvl.packagetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
        assertEquals("ΠΑΡΑΔΟΜΕΝΟ", details.get(0).getStatus());
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
}
