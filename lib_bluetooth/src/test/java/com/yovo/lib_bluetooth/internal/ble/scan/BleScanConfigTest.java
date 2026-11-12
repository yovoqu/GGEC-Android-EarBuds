package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;

import org.junit.Test;

/**
 * {@link BleScanConfig.Builder} 合法域契约：非法值在构造点抛 {@link IllegalArgumentException}
 * （消息含字段名与非法值），不再钳位成「看起来生效」的值；合法域内的小值原样保留。
 */
public final class BleScanConfigTest {

    @Test
    public void rejectsNegativeTimeoutMillis() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BleScanConfig.builder().timeoutMillis(-1L));
        assertTrue(error.getMessage().contains("timeoutMillis"));
        assertTrue(error.getMessage().contains("-1"));
    }

    @Test
    public void rejectsZeroNumOfMatches() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BleScanConfig.builder().numOfMatches(0));
        assertTrue(error.getMessage().contains("numOfMatches"));
        assertTrue(error.getMessage().contains("0"));
    }

    @Test
    public void rejectsNumOfMatchesAboveThree() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BleScanConfig.builder().numOfMatches(4));
        assertTrue(error.getMessage().contains("numOfMatches"));
        assertTrue(error.getMessage().contains("4"));
    }

    @Test
    public void rejectsNegativeReportDelayMillis() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BleScanConfig.builder().reportDelayMillis(-1L));
        assertTrue(error.getMessage().contains("reportDelayMillis"));
        assertTrue(error.getMessage().contains("-1"));
    }

    @Test
    public void rejectsNegativeDeduplicationWindowMillis() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BleScanConfig.builder().deduplicationWindowMillis(-1L));
        assertTrue(error.getMessage().contains("deduplicationWindowMillis"));
        assertTrue(error.getMessage().contains("-1"));
    }

    @Test
    public void rejectsNegativeMatchLostTimeoutMillis() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BleScanConfig.builder().matchLostTimeoutMillis(-1L));
        assertTrue(error.getMessage().contains("matchLostTimeoutMillis"));
        assertTrue(error.getMessage().contains("-1"));
    }

    @Test
    public void withReportDelayMillisIsValidatedToo() {
        BleScanConfig config = BleScanConfig.builder().reportDelayMillis(250L).build();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> config.withReportDelayMillis(-5L));
        assertTrue(error.getMessage().contains("reportDelayMillis"));
        assertTrue(error.getMessage().contains("-5"));
    }

    @Test
    public void lowerBoundsStayUnchangedInsteadOfBeingClamped() {
        BleScanConfig config = BleScanConfig.builder()
                .timeoutMillis(0L)
                .numOfMatches(1)
                .reportDelayMillis(0L)
                .deduplicationWindowMillis(0L)
                .matchLostTimeoutMillis(0L)
                .build();
        assertEquals(0L, config.timeoutMillis());
        assertEquals(1, config.numOfMatches());
        assertEquals(0L, config.reportDelayMillis());
        assertEquals(0L, config.deduplicationWindowMillis());
        assertEquals(0L, config.matchLostTimeoutMillis());
    }

    @Test
    public void valuesAboveOldMinimumsAreKeptVerbatim() {
        BleScanConfig config = BleScanConfig.builder()
                .timeoutMillis(1L)
                .numOfMatches(2)
                .reportDelayMillis(1L)
                .deduplicationWindowMillis(1L)
                .matchLostTimeoutMillis(1L)
                .build();
        assertEquals("旧的 200ms 下限钳位必须消失", 1L, config.timeoutMillis());
        assertEquals(2, config.numOfMatches());
        assertEquals(1L, config.reportDelayMillis());
        assertEquals(1L, config.deduplicationWindowMillis());
        assertEquals("旧的 1s 下限钳位必须消失", 1L, config.matchLostTimeoutMillis());
    }

    @Test
    public void builderDefaultsStayInsideLegalDomain() {
        BleScanConfig config = BleScanConfig.builder().build();
        assertEquals(12_000L, config.timeoutMillis());
        assertEquals(3, config.numOfMatches());
        assertEquals(0L, config.reportDelayMillis());
        assertEquals(0L, config.deduplicationWindowMillis());
        assertEquals(0L, config.matchLostTimeoutMillis());
    }

    @Test
    public void productDefaultsStayInsideLegalDomain() {
        BleScanConfig config = YoBleScanDefaults.config();
        assertEquals("产品扫描无超时", 0L, config.timeoutMillis());
        assertEquals(YoBleScanDefaults.MATCH_LOST_TIMEOUT_MILLIS,
                config.matchLostTimeoutMillis());
        assertEquals(0L, config.reportDelayMillis());
        assertEquals(0L, config.deduplicationWindowMillis());
        assertEquals(BleScanCallbackType.ALL_MATCHES, config.callbackType());
    }

    @Test
    public void softwareFallbackStaysInsideLegalDomain() {
        BleScanConfig fallback = BleScanConfig.builder()
                .reportDelayMillis(250L)
                .build()
                .softwareFallback();
        assertEquals(0L, fallback.reportDelayMillis());
        assertTrue(fallback.filters().isEmpty());
    }
}
