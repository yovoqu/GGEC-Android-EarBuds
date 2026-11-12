package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 扫描结果的构造契约：没上报的一侧是空串，null 在构造点抛出。 */
public final class ScanResultTest {

    @Test
    public void nullRequiredFieldsAreRejectedAtConstruction() {
        assertEquals("modelId", assertThrows(NullPointerException.class,
                () -> new ScanResult(null, "AA:BB:CC:00:00:01", "HS01", "", "", -50)).getMessage());
        assertEquals("bleAddress", assertThrows(NullPointerException.class,
                () -> new ScanResult("HS01", null, "HS01", "", "", -50)).getMessage());
        assertEquals("bleName", assertThrows(NullPointerException.class,
                () -> new ScanResult("HS01", "AA:BB:CC:00:00:01", null, "", "", -50)).getMessage());
        assertEquals("classicAddress", assertThrows(NullPointerException.class,
                () -> new ScanResult("HS01", "AA:BB:CC:00:00:01", "HS01", null, "", -50)).getMessage());
        assertEquals("classicName", assertThrows(NullPointerException.class,
                () -> new ScanResult("HS01", "AA:BB:CC:00:00:01", "HS01", "", null, -50)).getMessage());
    }

    @Test
    public void nullCandidateIsRejectedInsteadOfComparedAsDifferent() {
        ScanResult result = new ScanResult("HS01", "AA:BB:CC:00:00:01", "HS01", "", "", -50);

        assertEquals("other", assertThrows(NullPointerException.class,
                () -> result.sameDevice(null)).getMessage());
    }

    @Test
    public void emptyBleNameStaysEmptyAndCompareUsesBleAddress() {
        ScanResult missingName = new ScanResult("HS01", "AA:BB:CC:00:00:01", "", "11:22:33:44:55:66", "", -50);
        ScanResult sameAddress = new ScanResult("HS01", "aa:bb:cc:00:00:01", "HS01", "", "", -60);

        assertEquals("", missingName.bleName());
        assertEquals("11:22:33:44:55:66", missingName.classicAddress());
        assertTrue(missingName.sameDevice(sameAddress));
    }

    @Test
    public void sameBleAddressWithDifferentModelIsAnotherDevice() {
        ScanResult hs01 = new ScanResult("HS01", "AA:BB:CC:00:00:01", "HS01", "10:00:00:00:00:01", "", -50);
        ScanResult hs02 = new ScanResult("HS02", "AA:BB:CC:00:00:01", "Vidda V11", "10:00:00:00:00:02", "", -50);

        assertTrue(!hs01.sameDevice(hs02));
    }
}
