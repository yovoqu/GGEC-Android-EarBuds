package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;



import com.yovo.biz_ggec_hs.api.HsControl;
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
 *
 * <p>协议字节到对外枚举/布尔的转换只在本类里发生：上层与 api 里不出现这些字面量。
 * 超出协议取值的字节按契约违约处理并抛错，不猜一个枚举值兜底。</p>
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

    private static final int CONNECT_STATUS_CONNECTED = 0x01;
    private static final int CONNECT_STATUS_DISCONNECTED = 0x00;
    private static final int FLAG_YES = 0x00;
    private static final int FLAG_NO = 0x01;

    private PairedDeviceTlvParser() {
    }

    /**
     * 解析 TLV 数据段，提取所有设备信息。
     * <p>
     * TLV 格式非法（头部不完整 / 数据越界 / 未以设备名开始 / 字段长度错误）时抛
     * {@link IllegalArgumentException}；合法数据按实际记录数返回。
     */
    static List<HsControl.Paired> parseDevices(String tlvHex) {
        List<HsControl.Paired> devices = new ArrayList<>();

        String currentName = "";
        String currentAddress = "";
        int currentDeviceType = 0;
        int currentConnected = CONNECT_STATUS_DISCONNECTED;
        int currentBusinessType = 0;
        int currentDevice = FLAG_NO;
        int currentPriority = FLAG_NO;
        boolean hasDevice = false;

        int index = 0;
        while (index < tlvHex.length()) {
            if (index + 4 > tlvHex.length()) {
                throw new IllegalArgumentException("TLV 头部数据不完整");
            }

            int type = Integer.parseInt(tlvHex.substring(index, index + 2), 16);
            int length = Integer.parseInt(tlvHex.substring(index + 2, index + 4), 16);
            index += 4;

            if (index + length * 2 > tlvHex.length()) {
                throw new IllegalArgumentException(
                        "TLV 数据长度越界，类型: 0x" + String.format("%02X", type));
            }

            String valueHex = tlvHex.substring(index, index + length * 2);
            index += length * 2;

            if (type == TLV_TYPE_DEVICE_NAME) {
                // 遇到新设备名，先将上一条设备入列
                if (hasDevice) {
                    devices.add(device(currentName, currentAddress, currentDeviceType,
                            currentConnected, currentBusinessType, currentDevice, currentPriority));
                }
                currentName = hexToUtf8String(valueHex);
                currentAddress = "";
                currentDeviceType = 0;
                currentConnected = CONNECT_STATUS_DISCONNECTED;
                currentBusinessType = 0;
                currentDevice = FLAG_NO;
                currentPriority = FLAG_NO;
                hasDevice = true;
            } else if (hasDevice) {
                switch (type) {
                    case TLV_TYPE_BT_ADDRESS:
                        requireLength(type, length, 6);
                        currentAddress = hexToMacAddress(valueHex);
                        break;
                    case TLV_TYPE_DEVICE_TYPE:
                        requireLength(type, length, 1);
                        currentDeviceType = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_CONNECT_STATUS:
                        requireLength(type, length, 1);
                        currentConnected = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_BUSINESS_TYPE:
                        requireLength(type, length, 1);
                        currentBusinessType = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_CURRENT_DEVICE:
                        requireLength(type, length, 1);
                        currentDevice = Integer.parseInt(valueHex.substring(0, 2), 16);
                        break;
                    case TLV_TYPE_PRIORITY_DEVICE:
                        requireLength(type, length, 1);
                        currentPriority = Integer.parseInt(valueHex.substring(0, 2), 16);
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
            devices.add(device(currentName, currentAddress, currentDeviceType,
                    currentConnected, currentBusinessType, currentDevice, currentPriority));
        }

        return devices;
    }

    private static HsControl.Paired device(String name, String address, int deviceType,
                                         int connected, int businessType,
                                         int currentDevice, int priorityDevice) {
        return new HsControl.Paired(
                name,
                address,
                deviceType(deviceType),
                connected(connected),
                business(businessType),
                currentDevice(currentDevice),
                priorityDevice(priorityDevice));
    }

    private static HsControl.Paired.Kind deviceType(int raw) {
        return switch (raw) {
            case 0 -> HsControl.Paired.Kind.OTHER;
            case 1 -> HsControl.Paired.Kind.PHONE;
            case 2 -> HsControl.Paired.Kind.COMPUTER;
            case 3 -> HsControl.Paired.Kind.PAD;
            case 4 -> HsControl.Paired.Kind.TV;
            case 5 -> HsControl.Paired.Kind.WEARABLE;
            default -> throw new IllegalArgumentException(
                    "配对设备类型超出协议范围: 0x" + String.format("%02X", raw));
        };
    }

    private static HsControl.Paired.Activity business(int raw) {
        return switch (raw) {
            case 0 -> HsControl.Paired.Activity.IDLE;
            case 1 -> HsControl.Paired.Activity.MUSIC;
            case 2 -> HsControl.Paired.Activity.CALL;
            default -> throw new IllegalArgumentException(
                    "配对设备业务类型超出协议范围: 0x" + String.format("%02X", raw));
        };
    }

    private static boolean connected(int raw) {
        return switch (raw) {
            case CONNECT_STATUS_CONNECTED -> true;
            case CONNECT_STATUS_DISCONNECTED -> false;
            default -> throw new IllegalArgumentException(
                    "配对设备连接状态超出协议范围: 0x" + String.format("%02X", raw));
        };
    }

    private static boolean currentDevice(int raw) {
        return switch (raw) {
            case FLAG_YES -> true;
            case FLAG_NO -> false;
            default -> throw new IllegalArgumentException(
                    "配对设备当前设备标记超出协议范围: 0x" + String.format("%02X", raw));
        };
    }

    private static boolean priorityDevice(int raw) {
        return switch (raw) {
            case FLAG_YES -> true;
            case FLAG_NO -> false;
            default -> throw new IllegalArgumentException(
                    "配对设备优先设备标记超出协议范围: 0x" + String.format("%02X", raw));
        };
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
