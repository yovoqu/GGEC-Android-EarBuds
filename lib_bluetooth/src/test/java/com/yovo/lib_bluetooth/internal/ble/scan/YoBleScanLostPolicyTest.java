package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;

import org.junit.Test;

public final class YoBleScanLostPolicyTest {

    @Test
    public void allMatchesRemovesDevicesWhenLostTimeoutPositive() {
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .matchLostTimeoutMillis(10_000L)
                .build();
        assertTrue(YoBleScanLostPolicy.softwareLostEnabled(config));
    }

    @Test
    public void noLostTimeoutDisablesSoftwareLost() {
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.ALL_MATCHES)
                .build();
        assertFalse(YoBleScanLostPolicy.softwareLostEnabled(config));
    }

    @Test
    public void matchLostCallbackRemovesDevicesAfterTimeout() {
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.MATCH_LOST)
                .matchLostTimeoutMillis(10_000L)
                .build();
        assertTrue(YoBleScanLostPolicy.softwareLostEnabled(config));
    }

    @Test
    public void firstMatchAndLostRemovesDevicesAfterTimeout() {
        BleScanConfig config = BleScanConfig.builder()
                .callbackType(BleScanCallbackType.FIRST_MATCH_AND_LOST)
                .matchLostTimeoutMillis(1_000L)
                .build();
        assertTrue(YoBleScanLostPolicy.softwareLostEnabled(config));
    }
}
