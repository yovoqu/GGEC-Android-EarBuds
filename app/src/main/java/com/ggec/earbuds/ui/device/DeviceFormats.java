package com.ggec.earbuds.ui.device;

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
}
