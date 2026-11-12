package com.yovo.biz_ggec_hs.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 电量契约：协议文本在 {@link HsBattery#fromProtocolText} 一次转成 {@code int}，
 * 未知只用 {@link HsBattery#UNKNOWN_LEVEL}；第三种文本形态属于上游违约，必须抛错而不是回落成未知。
 */
public class HsBatteryTest {

    @Test
    public void protocolUnknownTextBecomesUnknownLevel() {
        HsBattery battery = HsBattery.fromProtocolText("--", "--", "--", false, 0);

        assertEquals(HsBattery.UNKNOWN_LEVEL, battery.leftLevel());
        assertEquals(HsBattery.UNKNOWN_LEVEL, battery.rightLevel());
        assertEquals(HsBattery.UNKNOWN_LEVEL, battery.caseLevel());
        assertFalse(battery.caseCharging());
    }

    @Test
    public void protocolDecimalTextBecomesStructuredLevel() {
        HsBattery battery = HsBattery.fromProtocolText("80", "1", "100", true, 3);

        assertEquals(80, battery.leftLevel());
        assertEquals(1, battery.rightLevel());
        assertEquals(100, battery.caseLevel());
        assertTrue(battery.caseCharging());
        assertEquals(3, battery.earInCaseStatus());
    }

    @Test
    public void nonProtocolThresholdTextIsRejected() {
        for (String illegal : new String[]{"abc", "", "0", "101", "80%", " 80"}) {
            assertThrows("非法协议电平必须抛错: " + illegal,
                    IllegalArgumentException.class,
                    () -> HsBattery.fromProtocolText(illegal, "--", "--", false, 0));
        }
    }

    @Test
    public void missingThresholdTextIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HsBattery.fromProtocolText(null, "--", "--", false, 0));
    }

    @Test
    public void structuredConstructorKeepsUnknownAndValueApart() {
        HsBattery battery = new HsBattery(80, HsBattery.UNKNOWN_LEVEL, 100, false, 0);

        assertEquals(80, battery.leftLevel());
        assertEquals(HsBattery.UNKNOWN_LEVEL, battery.rightLevel());
        assertEquals(100, battery.caseLevel());
    }

    @Test
    public void structuredConstructorRejectsOutOfRangeLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> new HsBattery(0, HsBattery.UNKNOWN_LEVEL, HsBattery.UNKNOWN_LEVEL, false, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new HsBattery(HsBattery.UNKNOWN_LEVEL, 101, HsBattery.UNKNOWN_LEVEL, false, 0));
    }
}
