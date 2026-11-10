package com.yovo.biz_ggec_hs.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 已配对设备列表的对外映射（与 iOS ListenPairedDevice 数组元素格式一致）。
 */
public final class PairedDeviceMapper {

    /** 设备名。 */
    public static final String KEY_DEVICE_NAME = "00";
    /** 蓝牙 MAC 地址。 */
    public static final String KEY_MAC_ADDRESS = "01";
    /** 设备类型（0=其它 1=手机 2=电脑 3=PAD 4=TV 5=穿戴）。 */
    public static final String KEY_DEVICE_TYPE = "02";
    /** 连接状态（1=正与耳机 BT 连接 0=无）。 */
    public static final String KEY_CONNECT_STATUS = "03";
    /** 业务类型（0=空闲 1=音乐 2=通话）。 */
    public static final String KEY_BUSINESS_TYPE = "04";
    /** 是否当前设备（0=是 1=否）。 */
    public static final String KEY_CURRENT_DEVICE = "05";
    /** 是否优先设备（0=是 1=否）。 */
    public static final String KEY_PRIORITY_DEVICE = "06";

    private PairedDeviceMapper() {
    }

    /**
     * 将 {@link PairedDevice} 列表映射为公开 API 使用的 Map 列表。
     *
     * @param devices SDK 内部设备模型列表
     * @return 键为 "00"~"06" 的 Map 列表，永不返回 null
     */
    public static List<Map<String, String>> toMapList(List<PairedDevice> devices) {
        List<Map<String, String>> mapped = new ArrayList<>();
        if (devices == null) {
            return mapped;
        }
        for (PairedDevice device : devices) {
            Map<String, String> item = new HashMap<>();
            item.put(KEY_DEVICE_NAME, device.getDeviceName());
            item.put(KEY_MAC_ADDRESS, device.getBluetoothAddress());
            item.put(KEY_DEVICE_TYPE, String.valueOf(device.getDeviceType()));
            item.put(KEY_CONNECT_STATUS, String.format("%02X", device.getConnected()));
            item.put(KEY_BUSINESS_TYPE, String.valueOf(device.getBusinessType()));
            item.put(KEY_CURRENT_DEVICE, String.format("%02X", device.getCurrentDevice()));
            item.put(KEY_PRIORITY_DEVICE, String.format("%02X", device.getPriorityDevice()));
            mapped.add(item);
        }
        return mapped;
    }
}
