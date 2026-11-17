package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 扫描结果的构造与去重契约。 */
public final class ScanResultTest {

    @Test
    public void nullRequiredFieldsAreRejectedAtConstruction() {
        assertThrows(NullPointerException.class,
                () -> new ScanResult(null, "HS01", "AA:BB:CC:00:00:01", -50));
        assertThrows(IllegalArgumentException.class,
                () -> new ScanResult("", "HS01", "AA:BB:CC:00:00:01", -50));
        assertThrows(IllegalArgumentException.class,
                () -> new ScanResult("HS01", "HS01", "", -50));
    }

    @Test
    public void nullCandidateIsRejectedInsteadOfComparedAsDifferent() {
        ScanResult result = new ScanResult("HS01", "HS01", "AA:BB:CC:00:00:01", -50);
        assertFalse(result.sameDevice(null));
    }

    @Test
    public void emptyNameFallsBackToModelIdInDisplayName() {
        ScanResult missingName = new ScanResult("HS01", "", "AA:BB:CC:00:00:01", -50);
        assertEquals("HS01", missingName.displayName());
        assertEquals("AA:BB:CC:00:00:01", missingName.displayAddress());
    }

    @Test
    public void sameAddressWithDifferentCaseIsSameDevice() {
        ScanResult r1 = new ScanResult("HS01", "Earbuds", "AA:BB:CC:00:00:01", -50);
        ScanResult r2 = new ScanResult("HS01", "Earbuds", "aa:bb:cc:00:00:01", -60);
        assertTrue(r1.sameDevice(r2));
    }

    @Test
    public void sameAddressWithDifferentModelIsAnotherDevice() {
        ScanResult hs01 = new ScanResult("HS01", "HS01", "AA:BB:CC:00:00:01", -50);
        ScanResult hs02 = new ScanResult("HS02", "Vidda V11", "AA:BB:CC:00:00:01", -50);
        assertFalse(hs01.sameDevice(hs02));
    }
}
