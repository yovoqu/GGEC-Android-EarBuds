package com.yovo.biz_ggec_hs.model;

/**
 * Author: tingfeng.yang
 * Date: 2026-04-09
 * Description:
 * 蓝牙已配对设备数据模型
 * 对应协议 EC A7 响应中每条设备 TLV 条目的解析结果
 * <p>
 * 注意：此类是SDK的公开API之一，不会被混淆。
 */
public class PairedDevice {

    // ---------- 设备类型常量（TLV Type 0x02） ----------

    /** 其它设备 */
    public static final int DEVICE_TYPE_OTHER = 0;
    /** 手机设备 */
    public static final int DEVICE_TYPE_PHONE = 1;
    /** 电脑设备 */
    public static final int DEVICE_TYPE_COMPUTER = 2;
    /** PAD 设备 */
    public static final int DEVICE_TYPE_PAD = 3;
    /** TV 设备 */
    public static final int DEVICE_TYPE_TV = 4;
    /** 穿戴设备 */
    public static final int DEVICE_TYPE_WEARABLE = 5;

    // ---------- 业务类型常量（TLV Type 0x04） ----------

    /** 空闲状态 */
    public static final int BUSINESS_TYPE_IDLE = 0;
    /** 音乐播放中 */
    public static final int BUSINESS_TYPE_MUSIC = 1;
    /** 通话中 */
    public static final int BUSINESS_TYPE_CALL = 2;

    /** 是当前设备（TLV 0x05） */
    public static final int CURRENT_DEVICE_YES = 0x00;
    /** 不是当前设备（TLV 0x05） */
    public static final int CURRENT_DEVICE_NO = 0x01;

    /** 是优先设备（TLV 0x06） */
    public static final int PRIORITY_DEVICE_YES = 0x00;
    /** 不是优先设备（TLV 0x06） */
    public static final int PRIORITY_DEVICE_NO = 0x01;

    // ---------- 字段 ----------
    /** 设备名（TLV 0x00），最大 31 字节 */
    private final String deviceName;

    /** 蓝牙 MAC 地址（TLV 0x01），格式 XX:XX:XX:XX:XX:XX */
    private final String bluetoothAddress;

    /** 设备类型（TLV 0x02），参考 DEVICE_TYPE_* 常量 */
    private final int deviceType;

    /** 是否正在与耳机 BT 连接（TLV 0x03），协议原始字节，0x01=已连接 0x00=未连接 */
    private final int connected;

    /** 业务类型（TLV 0x04），参考 BUSINESS_TYPE_* 常量 */
    private final int businessType;

    /** 是否是当前设备（TLV 0x05），协议原始字节，0x00=是 0x01=否 */
    private final int currentDevice;

    /** 是否是优先设备（TLV 0x06），协议原始字节，0x00=是 0x01=否 */
    private final int priorityDevice;

    // ---------- 构造方法（包级可见，仅供命令解析器使用） ----------

    public PairedDevice(String deviceName, String bluetoothAddress,
                        int deviceType, int connected, int businessType,
                        int currentDevice, int priorityDevice) {
        this.deviceName = deviceName != null ? deviceName : "";
        this.bluetoothAddress = bluetoothAddress != null ? bluetoothAddress : "";
        this.deviceType = deviceType;
        this.connected = connected;
        this.businessType = businessType;
        this.currentDevice = currentDevice;
        this.priorityDevice = priorityDevice;
    }

    // ---------- 公开 getter ----------

    /**
     * 获取设备名称
     *
     * @return 设备名称字符串
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * 获取蓝牙 MAC 地址
     *
     * @return MAC 地址字符串（格式：XX:XX:XX:XX:XX:XX）
     */
    public String getBluetoothAddress() {
        return bluetoothAddress;
    }

    /**
     * 获取设备类型
     *
     * @return 设备类型，参考 DEVICE_TYPE_* 常量
     */
    public int getDeviceType() {
        return deviceType;
    }

    /**
     * 是否正与耳机 BT 连接（返回协议原始字节）
     *
     * @return 0x01 表示该设备当前正与耳机通过 BT 连接，0x00 表示未连接
     */
    public int getConnected() {
        return connected;
    }

    /**
     * 获取业务类型
     *
     * @return 业务类型，参考 BUSINESS_TYPE_* 常量
     */
    public int getBusinessType() {
        return businessType;
    }

    /**
     * 是否是当前设备（返回协议原始字节）
     *
     * @return 0x00 表示该设备是当前连接 APP 的设备，0x01 表示不是
     */
    public int getCurrentDevice() {
        return currentDevice;
    }

    /**
     * 是否是优先设备（返回协议原始字节）
     *
     * @return 0x00 表示该设备是优先设备，0x01 表示不是
     */
    public int getPriorityDevice() {
        return priorityDevice;
    }

    @Override
    public String toString() {
        return "PairedDevice{" +
                "name='" + deviceName + '\'' +
                ", addr='" + bluetoothAddress + '\'' +
                ", type=" + deviceType +
                ", connected=" + String.format("%02X", connected) +
                ", businessType=" + businessType +
                ", currentDevice=" + String.format("%02X", currentDevice) +
                ", priorityDevice=" + String.format("%02X", priorityDevice) +
                '}';
    }
}
