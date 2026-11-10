package com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.model.PairedDevice;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 已配对设备 TLV 块解析器（EC A7 配对列表 / EC BD 双连接页共用）。
 * <p>
 * TLV 以 0x00（设备名）作为每条设备记录的起始边界：
 * 0x00 - 设备名（变长，最大 31 字节）
 * 0x01 - 蓝牙地址（固定 6 字节）
 * 0x02 - 设备类型（1 字节）
 * 0x03 - 连接状态（1 字节，0x01=已连接）
 * 0x04 - 业务类型（1 字节，0x00=空闲 0x01=音乐 0x02=通话）
 * 0x05 - 是否当前设备（1 字节，0x00=是 0x01=否）
 * 0x06 - 是否是优先设备（1 字节，0x00=是 0x01=否）
 */
final class PairedDeviceTlvParser {
    private static final String TAG = "PairedDeviceTlvParser";

    static final int TLV_TYPE_DEVICE_NAME = 0x00;
    static final int TLV_TYPE_BT_ADDRESS = 0x01;
    static final int TLV_TYPE_DEVICE_TYPE = 0x02;
    static final int TLV_TYPE_CONNECT_STATUS = 0x03;
    static final int TLV_TYPE_BUSINESS_TYPE = 0x04;
    static final int TLV_TYPE_CURRENT_DEVICE = 0x05;
    static final int TLV_TYPE_PRIORITY_DEVICE = 0x06;

    private PairedDeviceTlvParser() {
    }

    /**
     * 解析 TLV 数据段，提取所有设备信息。
     * <p>
     * TLV 格式非法（头部不完整 / 数据越界 / 未以设备名开始 / 字段长度错误）时抛
     * {@link IllegalArgumentException}；合法数据按实际记录数返回。
     */
    static List<PairedDevice> parseDevices(String tlvHex) {
        List<PairedDevice> devices = new ArrayList<>();

        // connected 与 currentDevice 保留协议原始字节 0x00/0x01
        String curName = "";
        String curAddr = "";
        int curDeviceType = PairedDevice.DEVICE_TYPE_OTHER;
        int curConnected = 0x00;
        int curBusinessType = PairedDevice.BUSINESS_TYPE_IDLE;
        int curCurrentDevice = PairedDevice.CURRENT_DEVICE_NO;
        int curPriorityDevice = PairedDevice.PRIORITY_DEVICE_NO;
        boolean hasDevice = false;

        int i = 0;
        while (i < tlvHex.length()) {
            if (i + 4 > tlvHex.length()) {
                throw new IllegalArgumentException("TLV 头部数据不完整");
            }

            int type = Integer.parseInt(tlvHex.substring(i, i + 2), 16);
            int len = Integer.parseInt(tlvHex.substring(i + 2, i + 4), 16);
            i += 4;

            if (i + len * 2 > tlvHex.length()) {
                throw new IllegalArgumentException(
                        "TLV 数据长度越界，类型: 0x" + String.format("%02X", type));
            }

            String valueHex = tlvHex.substring(i, i + len * 2);
            i += len * 2;

            if (type == TLV_TYPE_DEVICE_NAME) {
                // 遇到新设备名，先将上一条设备入列
                if (hasDevice) {
                    devices.add(new PairedDevice(curName, curAddr, curDeviceType, curConnected,
                            curBusinessType, curCurrentDevice, curPriorityDevice));
                }
                // 重置当前设备字段
                curName = hexToUtf8String(valueHex);
                curAddr = "";
                curDeviceType = PairedDevice.DEVICE_TYPE_OTHER;
                curConnected = 0x00;
                curBusinessType = PairedDevice.BUSINESS_TYPE_IDLE;
                curCurrentDevice = PairedDevice.CURRENT_DEVICE_NO;
                curPriorityDevice = PairedDevice.PRIORITY_DEVICE_NO;
                hasDevice = true;
            } else if (hasDevice) {
                switch (type) {
                    case TLV_TYPE_BT_ADDRESS:
                        requireLength(type, len, 6);
                        curAddr = hexToMacAddress(valueHex);
                        break;
                    case TLV_TYPE_DEVICE_TYPE:
                        requireLength(type, len, 1);
                        curDeviceType = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_CONNECT_STATUS:
                        requireLength(type, len, 1);
                        curConnected = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_BUSINESS_TYPE:
                        requireLength(type, len, 1);
                        curBusinessType = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_CURRENT_DEVICE:
                        requireLength(type, len, 1);
                        curCurrentDevice = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_PRIORITY_DEVICE:
                        requireLength(type, len, 1);
                        curPriorityDevice = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    default:
                        HsLog.w(TAG, "未知 TLV 类型: 0x" + String.format("%02X", type) + "，跳过");
                        break;
                }
            } else {
                throw new IllegalArgumentException("设备 TLV 未以设备名开始");
            }
        }

        // 最后一条设备入列
        if (hasDevice) {
            devices.add(new PairedDevice(curName, curAddr, curDeviceType, curConnected,
                    curBusinessType, curCurrentDevice, curPriorityDevice));
        }

        return devices;
    }

    private static void requireLength(int type, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    "TLV 字段长度错误，类型=0x" + String.format("%02X", type));
        }
    }

    private static String hexToUtf8String(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        try {
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            HsLog.e(TAG, "设备名转换失败: " + hex, e);
            return "";
        }
    }

    /** 将 12 位十六进制字符串转换为 MAC 地址格式（XX:XX:XX:XX:XX:XX）。 */
    static String hexToMacAddress(String hex) {
        if (hex == null || hex.length() != 12) {
            return hex != null ? hex : "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(hex, i, i + 2);
        }
        return sb.toString().toUpperCase();
    }
}
