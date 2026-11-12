package com.yovo.biz_ggec_hs.internal.center.port;

/**
 * 扫描节奏。只给底层蓝牙适配使用，不进公开 API。
 */
public record ScanPace(Mode mode, long reportDelayMs) {

    public enum Mode {
        LOW_POWER, BALANCED, LOW_LATENCY
    }

    public static ScanPace lowPower() {
        return new ScanPace(Mode.LOW_POWER, 0L);
    }

    public static ScanPace balanced() {
        return new ScanPace(Mode.BALANCED, 0L);
    }

    public static ScanPace lowLatency() {
        return new ScanPace(Mode.LOW_LATENCY, 0L);
    }
}
