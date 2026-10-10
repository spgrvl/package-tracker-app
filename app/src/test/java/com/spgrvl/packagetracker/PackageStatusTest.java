package com.spgrvl.packagetracker;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

public class PackageStatusTest {
    private static String[] problem;
    private static String[] delivered;
    private static String[] notDelivered;
    private static String[] outForDelivery;

    @BeforeClass
    public static void loadKeywords() {
        StringResources strings = StringResources.load("values");
        problem = strings.array("status_keywords_problem");
        delivered = strings.array("status_keywords_delivered");
        notDelivered = strings.array("status_keywords_not_delivered");
        outForDelivery = strings.array("status_keywords_out_for_delivery");
    }

    private static PackageStatus active(String statusText) {
        return PackageStatus.classify(false, statusText, problem, delivered, notDelivered, outForDelivery);
    }

    private static PackageStatus completed(String statusText) {
        return PackageStatus.classify(true, statusText, problem, delivered, notDelivered, outForDelivery);
    }

    @Test
    public void deliveredStatuses() {
        assertEquals(PackageStatus.DELIVERED, active("Παραδόθηκε"));
        assertEquals(PackageStatus.DELIVERED, active("ΠΑΡΑΔΟΘΗΚΕ"));
        assertEquals(PackageStatus.DELIVERED, active("ΠΑΡΑΔΟΣΗ - ΠΑΤΡΑ"));
        assertEquals(PackageStatus.DELIVERED, active("Delivered"));
        assertEquals(PackageStatus.DELIVERED, active("Παράδοση σε Skroutz Point"));
        assertEquals(PackageStatus.DELIVERED, active("ΠΑΡΑΔΟΜΕΝΟ"));
        assertEquals(PackageStatus.DELIVERED, active("Η παραγγελία σας ολοκληρώθηκε. Ευχαριστούμε για την προτίμηση."));
        assertEquals(PackageStatus.DELIVERED, active("Your order has been completed. Thank you for choosing us."));
    }

    @Test
    public void outForDeliveryStatuses() {
        assertEquals(PackageStatus.OUT_FOR_DELIVERY, active("Η αποστολή βρίσκεται σε δρομολόγιο παράδοσης"));
        assertEquals(PackageStatus.OUT_FOR_DELIVERY, active("ΠΡΟΣ ΠΑΡΑΔΟΣΗ"));
        assertEquals(PackageStatus.OUT_FOR_DELIVERY, active("Έξοδος για παράδοση"));
        assertEquals(PackageStatus.OUT_FOR_DELIVERY, active("Το δέμα σας βρίσκεται στην θυρίδα"));
        assertEquals(PackageStatus.OUT_FOR_DELIVERY, active("Το δέμα έφτασε στο Clever Point"));
        assertEquals(PackageStatus.OUT_FOR_DELIVERY, active("Arrived at the Clever Point"));
    }

    @Test
    public void issueStatuses() {
        assertEquals(PackageStatus.ISSUE, active("Δεν καταφέραμε να παραλάβουμε το δέμα από το κατάστημα"));
        assertEquals(PackageStatus.ISSUE, active("Το δέμα έχει επιστραφεί"));
        assertEquals(PackageStatus.ISSUE, active("ΠΑΡΑΔΟΣΗ ΣΤΟΝ ΑΠΟΣΤΟΛΕΑ"));
        assertEquals(PackageStatus.ISSUE, active("Cancelled"));
    }

    @Test
    public void problemKeywordsWinOverDelivered() {
        assertEquals(PackageStatus.ISSUE, active("Parcel not delivered"));
        assertEquals(PackageStatus.ISSUE, active("Could not be delivered"));
    }

    @Test
    public void stillOnItsWayDespiteMentioningDelivery() {
        assertEquals(PackageStatus.IN_TRANSIT, active("Being delivered"));
        assertEquals(PackageStatus.IN_TRANSIT, active("To be delivered by E-shop to BOX NOW"));
    }

    @Test
    public void pendingAndInTransitStatuses() {
        assertEquals(PackageStatus.PENDING, active(null));
        assertEquals(PackageStatus.PENDING, active(""));
        assertEquals(PackageStatus.PENDING, active("Status: None"));
        assertEquals(PackageStatus.IN_TRANSIT, active("ΑΝΑΧΩΡΗΣΗ ΑΠΟ ΚΕΝΤΡΟ ΔΙΑΛΟΓΗΣ"));
        // Pickup from sender is normal progress, not a return
        assertEquals(PackageStatus.IN_TRANSIT, active("ΠΑΡΑΛΑΒΗ ΑΠΟ ΑΠΟΣΤΟΛΕΑ - Ρ***Κ"));
    }

    @Test
    public void completedPackagesFollowCarrierStatus() {
        assertEquals(PackageStatus.DELIVERED, completed("Παραδόθηκε"));
        assertEquals(PackageStatus.ISSUE, completed("Το δέμα έχει επιστραφεί"));
        assertEquals(PackageStatus.COMPLETED, completed("ΑΝΑΧΩΡΗΣΗ ΑΠΟ ΚΕΝΤΡΟ ΔΙΑΛΟΓΗΣ"));
        assertEquals(PackageStatus.COMPLETED, completed("Status: None"));
    }

    @Test
    public void boxNowStatusesInBothLanguages() {
        for (String valuesDir : new String[]{"values", "values-el"}) {
            Map<String, String> boxNow = StringResources.load(valuesDir).stringsWithPrefix("boxNow_");
            for (Map.Entry<String, String> status : boxNow.entrySet()) {
                String name = status.getKey();
                PackageStatus expected;
                if (name.matches("boxNow_(canceled|cancelled.*|expired.*|lost|missing|returned)")) {
                    expected = PackageStatus.ISSUE;
                } else if (name.equals("boxNow_delivered")) {
                    expected = PackageStatus.DELIVERED;
                } else if (name.matches("boxNow_(in_)?final_destination")) {
                    expected = PackageStatus.OUT_FOR_DELIVERY;
                } else {
                    expected = PackageStatus.IN_TRANSIT;
                }
                assertEquals(valuesDir + "/" + name + " \"" + status.getValue() + "\"",
                        expected, active(status.getValue()));
            }
        }
    }

    @Test
    public void normalizeIgnoresCaseAccentsAndFinalSigma() {
        assertEquals("παραδοθηκε", PackageStatus.normalize("ΠΑΡΑΔΌΘΗΚΕ"));
        assertEquals("προσ παραδοση", PackageStatus.normalize("Προς Παράδοση"));
        assertEquals("ι", PackageStatus.normalize("ΐ"));
    }
}
