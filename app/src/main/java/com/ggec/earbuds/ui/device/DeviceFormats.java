package com.ggec.earbuds.ui.device;

/** 设备数值的展示格式。未知值只在这里写一次。 */
public final class DeviceFormats {

    public static final String UNKNOWN = "--";

    private DeviceFormats() {
    }

    public static String battery(int value) {
        return value < 0 ? UNKNOWN : value + "%";
    }

    public static String textOrUnknown(String value) {
        return value == null || value.isEmpty() ? UNKNOWN : value;
    }
}
