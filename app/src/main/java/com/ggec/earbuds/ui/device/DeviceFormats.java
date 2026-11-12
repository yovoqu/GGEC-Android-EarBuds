package com.ggec.earbuds.ui.device;

import java.util.Locale;

/** 设备数值的展示格式。未知值与分隔符只在这里写一次。 */
public final class DeviceFormats {

    public static final String UNKNOWN = "--";
    public static final String META_SEPARATOR = "  ·  ";
    public static final String RSSI_UNIT = " dBm";

    private DeviceFormats() {
    }

    public static String battery(int value) {
        return value < 0 ? UNKNOWN : value + "%";
    }

    public static String rssi(int value) {
        return value + RSSI_UNIT;
    }

    public static String textOrUnknown(String value) {
        return value == null || value.isEmpty() ? UNKNOWN : value;
    }

    /**
     * 耳机颜色协议原值 → 展示文字。协议只给正数色号，没有可信的颜色名映射，
     * 因此这里显示色号本身，未知统一走 {@link #UNKNOWN}。
     */
    public static String earColor(int earColor) {
        return earColor < 0 ? UNKNOWN : String.valueOf(earColor);
    }

    /** 文件大小展示：未知（负数）走 {@link #UNKNOWN}，其余按量级选一个单位。 */
    public static String size(long bytes) {
        if (bytes < 0) {
            return UNKNOWN;
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
