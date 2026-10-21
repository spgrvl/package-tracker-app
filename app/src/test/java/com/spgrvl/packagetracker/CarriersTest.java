package com.spgrvl.packagetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CarriersTest {

    private static String source(String path) throws IOException {
        // Unit tests run with the module directory as the working directory
        return new String(Files.readAllBytes(Paths.get("src/main/" + path)), StandardCharsets.UTF_8);
    }

    @Test
    public void everyChoosableCarrierCanBeTracked() throws IOException {
        String tracker = source("java/com/spgrvl/packagetracker/UpdateTrackingDetails.java");
        for (String key : Carriers.KEYS) {
            assertTrue("No tracker case for " + key, tracker.contains("case \"" + key + "\":"));
        }
    }

    @Test
    public void everyChoosableCarrierHasANameInBothLanguages() {
        for (String valuesDir : new String[]{"values", "values-el"}) {
            StringResources strings = StringResources.load(valuesDir);
            for (String key : Carriers.KEYS) {
                assertTrue("No " + valuesDir + " string for " + key, strings.stringsWithPrefix(key).containsKey(key));
            }
        }
    }

    @Test
    public void everyChoosableCarrierNameSurvivesResourceShrinking() throws IOException {
        // Picker labels are looked up by name, which the resource shrinker can't see
        String keep = source("res/raw/keep.xml");
        for (String key : Carriers.KEYS) {
            assertTrue("Not kept: " + key, keep.contains("@string/" + key + ",") || keep.contains("@string/" + key + "\""));
        }
    }

    @Test
    public void pickerPositionsMapToCarriersAndBack() {
        assertEquals(0, Carriers.pickerPosition(null));
        assertNull(Carriers.atPickerPosition(0));
        for (String key : Carriers.KEYS) {
            assertEquals(key, Carriers.atPickerPosition(Carriers.pickerPosition(key)));
        }
        assertNull(Carriers.atPickerPosition(Carriers.KEYS.length + 1));
    }

    @Test
    public void combinedCarriersCountAsTheCarrierChosen() {
        assertEquals("acs", Carriers.chosenAs("acsSkroutzLastMile"));
        assertEquals("plaisio", Carriers.chosenAs("plaisioBoxNow"));
        assertEquals("plaisio", Carriers.chosenAs("plaisio"));
        assertEquals("geniki", Carriers.chosenAs("geniki"));
        assertNull(Carriers.chosenAs(null));
    }

    @Test
    public void onlyListedCarriersCanBeChosen() {
        assertTrue(Carriers.isChoosable("boxNow"));
        assertFalse(Carriers.isChoosable("acsSkroutzLastMile"));
        assertFalse(Carriers.isChoosable(""));
        assertFalse(Carriers.isChoosable(null));
    }
}
