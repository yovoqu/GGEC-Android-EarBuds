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

    /**
     * 录音时长展示：{@code HH:MM:SS}。录音页面（录制计时与文件时长）的唯一实现。
     */
    public static String duration(long durationMs) {
        long seconds = durationMs / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long rest = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, rest);
    }

    /**
     * 播放进度时间轴展示：不足 1 小时用 {@code MM:SS}，超过用 {@code H:MM:SS}。
     * 未知（负数）走 {@link #UNKNOWN}。
     */
    public static String clock(long durationMs) {
        if (durationMs < 0) {
            return UNKNOWN;
        }
        long seconds = durationMs / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long rest = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, rest);
    }
}
