package com.yovo.lib_bluetooth.model.ble;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link BleConfig} 的参数契约：非法值抛 {@link IllegalArgumentException}，不静默钳位。
 *
 * <p>这些值都是调用方代码里的常量，非法即编程错误。历史上它们被 {@code Math.max} 改写成合法值，
 * 于是「配置 0」看起来生效、实际按别的数字跑；下游 {@code YoBleScanThrottleGuard} 的校验
 * 也因此永远不可达。现在约定：越界直接失败。</p>
 */
public final class BleConfigTest {

    @Test
    public void rejectsNonPositiveScanThrottleWindow() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BleConfig.builder().scanThrottleWindowMillis(0L));
        assertTrue(thrown.getMessage().contains("scanThrottleWindowMillis"));
        assertTrue(thrown.getMessage().contains("0"));

        assertThrows(IllegalArgumentException.class,
                () -> BleConfig.builder().scanThrottleWindowMillis(-1L));
    }

    @Test
    public void rejectsNonPositiveScanThrottleMaxStarts() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BleConfig.builder().scanThrottleMaxStarts(0));
        assertTrue(thrown.getMessage().contains("scanThrottleMaxStarts"));
        assertTrue(thrown.getMessage().contains("0"));
    }

    @Test
    public void rejectsDiagnosticsCapacityBelowMinimum() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BleConfig.builder().diagnosticsTimelineCapacity(31));
        assertTrue(thrown.getMessage().contains("diagnosticsTimelineCapacity"));
        assertTrue(thrown.getMessage().contains("31"));
    }

    @Test
    public void defaultsStayInsideTheLegalDomain() {
        BleConfig config = BleConfig.builder().build();
        assertTrue(config.scanThrottleWindowMillis() > 0L);
        assertTrue(config.scanThrottleMaxStarts() > 0);
        assertTrue(config.diagnosticsTimelineCapacity() >= 32);
        assertEquals(5, BleConfig.builder().scanThrottleMaxStarts(5).build().scanThrottleMaxStarts());
    }
}
