package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.UUID;

public final class YoBleScanFilterMatcherTest {

    @Test
    public void maskedBytesEqualMaskMatches() {
        assertTrue(YoBleScanFilterMatcher.matchesMaskedBytes(
                new byte[]{0x01, 0x02}, new byte[]{(byte) 0xFF, 0x00}, new byte[]{0x01, 0x09}));
        assertFalse(YoBleScanFilterMatcher.matchesMaskedBytes(
                new byte[]{0x01, 0x02}, new byte[]{(byte) 0xFF, (byte) 0xFF}, new byte[]{0x01, 0x09}));
    }

    @Test
    public void shortMaskReturnsFalseWithoutThrowing() {
        // 掩码比期望数据短：不得越界，按不匹配处理。
        assertFalse(YoBleScanFilterMatcher.matchesMaskedBytes(
                new byte[]{0x01, 0x02, 0x03}, new byte[]{(byte) 0xFF}, new byte[]{0x01, 0x02, 0x03}));
    }

    @Test
    public void nullActualIsFalseNullExpectedIsTrue() {
        assertFalse(YoBleScanFilterMatcher.matchesMaskedBytes(new byte[]{0x01}, null, null));
        assertTrue(YoBleScanFilterMatcher.matchesMaskedBytes(null, null, new byte[]{0x01}));
    }

    @Test
    public void maskedUuidMatches() {
        UUID target = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
        UUID candidate = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
        UUID mask = UUID.fromString("0000fff0-0000-0000-0000-000000000000");
        assertTrue(YoBleScanFilterMatcher.matchesMaskedUuid(target, mask, candidate));
        assertFalse(YoBleScanFilterMatcher.matchesMaskedUuid(target, null, candidate));
    }
}
