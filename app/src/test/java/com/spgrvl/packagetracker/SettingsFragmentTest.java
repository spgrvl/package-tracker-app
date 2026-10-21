package com.spgrvl.packagetracker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SettingsFragmentTest {

    @Test
    public void parseBackupLineReadsAllFields() {
        assertArrayEquals(new String[]{"SE000000000GR", "Shoes", "false", "1792500000000"},
                SettingsFragment.parseBackupLine("SE000000000GR,Shoes,false,1792500000000"));
    }

    @Test
    public void parseBackupLineKeepsQuotesInNames() {
        assertArrayEquals(new String[]{"1234567", "5\" monitor", "true", "1792500000000"},
                SettingsFragment.parseBackupLine("1234567,5\" monitor,true,1792500000000"));
    }

    @Test
    public void parseBackupLineKeepsEmptyNames() {
        assertArrayEquals(new String[]{"1234567", "", "false", ""},
                SettingsFragment.parseBackupLine("1234567,,false,"));
    }

    @Test
    public void chosenCarrierIsReadFromTheFifthField() {
        assertEquals("geniki", SettingsFragment.chosenCarrierOf(
                SettingsFragment.parseBackupLine("1234567890,Shoes,false,1792500000000,geniki")));
    }

    @Test
    public void backupsWithoutAChosenCarrierAutoDetect() {
        // Backups made before carriers could be chosen have four fields
        assertNull(SettingsFragment.chosenCarrierOf(SettingsFragment.parseBackupLine("1234567890,Shoes,false,1792500000000")));
        assertNull(SettingsFragment.chosenCarrierOf(SettingsFragment.parseBackupLine("1234567890,Shoes,false,1792500000000,")));
        assertNull(SettingsFragment.chosenCarrierOf(SettingsFragment.parseBackupLine("1234567890,Shoes,false,1792500000000,unknownCarrier")));
    }

    @Test
    public void parseBackupLineRejectsIncompleteLines() {
        assertNull(SettingsFragment.parseBackupLine(""));
        assertNull(SettingsFragment.parseBackupLine("1234567,Shoes,false"));
    }
}
