package com.spgrvl.packagetracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DatabaseHelperTest {

    @Test
    public void detailsTableQuotesPlainTrackingNumbers() {
        assertEquals("\"EL123456789GR\"", DatabaseHelper.detailsTable("EL123456789GR"));
    }

    @Test
    public void detailsTableEscapesQuotesInTrackingNumbers() {
        assertEquals("\"AB'12\"\"Q\"", DatabaseHelper.detailsTable("AB'12\"Q"));
    }
}
