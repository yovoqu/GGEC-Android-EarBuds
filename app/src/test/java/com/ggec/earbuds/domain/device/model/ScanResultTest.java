package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 扫描结果的构造契约：广播没带的字段写空串，null 是上游缺值，构造点抛出。 */
public final class ScanResultTest {

    @Test
    public void nullRequiredFieldsAreRejectedAtConstruction() {
        assertEquals("modelId", assertThrows(NullPointerException.class,
                () -> new ScanResult(null, "v1", "HS01", "AA:BB:CC:00:00:01", -50)).getMessage());
        assertEquals("variantId", assertThrows(NullPointerException.class,
                () -> new ScanResult("HS01", null, "HS01", "AA:BB:CC:00:00:01", -50)).getMessage());
        assertEquals("bleName", assertThrows(NullPointerException.class,
                () -> new ScanResult("HS01", "v1", null, "AA:BB:CC:00:00:01", -50)).getMessage());
        assertEquals("bleMac", assertThrows(NullPointerException.class,
                () -> new ScanResult("HS01", "v1", "HS01", null, -50)).getMessage());
    }

    @Test
    public void nullCandidateIsRejectedInsteadOfComparedAsDifferent() {
        ScanResult result = new ScanResult("HS01", "v1", "HS01", "AA:BB:CC:00:00:01", -50);

        assertEquals("other", assertThrows(NullPointerException.class,
                () -> result.sameDevice(null)).getMessage());
    }

    @Test
    public void emptyFieldsStayExplicitAndCompareCaseInsensitively() {
        ScanResult missingName = new ScanResult("HS01", "", "", "AA:BB:CC:00:00:01", -50);
        ScanResult sameMac = new ScanResult("HS01", "v1", "HS01", "aa:bb:cc:00:00:01", -60);

        assertEquals("", missingName.bleName());
        assertEquals("", missingName.variantId());
        assertTrue(missingName.sameDevice(sameMac));
    }
}
