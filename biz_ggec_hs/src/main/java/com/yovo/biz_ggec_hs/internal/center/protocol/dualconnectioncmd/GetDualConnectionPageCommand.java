package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;



import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolDual;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolPaired;
import com.yovo.biz_ggec_hs.internal.log.HsLog;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;

import java.util.ArrayList;
import java.util.List;

/**
 * 双连接页聚合查询命令。
 * <p>
 * 发送：99 EC BD 00 01 00 12 34
 * 响应：99 EC BD [LEN_H][LEN_L] [顶层 TLV Block...] 12 34
 * <p>
 * 顶层 TLV：0x01 双连接开关 / 0x02 优先设备 / 0x03 设备列表（子项解析见
 * {@link PairedDeviceTlvParser}）。信封与 EC A7 / AC / AE 一致：2 字节长度字段。
 *
 * <p>解析结果是协议页 {@link ProtocolDual}。双连接开关是
 * 该契约的必填项：帧里没有 0x01 就说明响应不合规，按失败上报，不猜一个「单连接」。</p>
 */
public class GetDualConnectionPageCommand extends Command {
    private static final String TAG = "GetDualConnectionPageCommand";

    private static final String COMMAND_PREFIX = "99ECBD";
    private static final String COMMAND_DATA = "99ECBD000100" + "1234";
    private static final String COMMAND_SUFFIX = "1234";

    private static final int TOP_TLV_DUAL_CONNECTION = 0x01;
    private static final int TOP_TLV_PRIORITY = 0x02;
    private static final int TOP_TLV_DEVICE_LIST = 0x03;

    /** 响应帧最小长度：prefix(6) + 长度(4) + suffix(4) */
    private static final int MIN_RESPONSE_LENGTH = 14;

    /** 优先设备状态：已设置（随后 6 字节为地址）。 */
    private static final int PRIORITY_SET = 0x00;
    /** 优先设备状态：未设置。 */
    private static final int PRIORITY_NOT_SET = 0x01;

    public GetDualConnectionPageCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }


    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (responseData == null || responseData.length() < MIN_RESPONSE_LENGTH) {
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        // 与 EC A7 / AC / AE 同一信封：前缀 + 2 字节长度 + TLV 载荷 + 尾标。
        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)) {
            return completed(ResultCode.FAILED, null, "响应帧信封不完整");
        }

        try {
            String payloadHex = responseData.substring(10, responseData.length() - 4);
            ProtocolDual state = parseTopLevelTlv(payloadHex);
            HsLog.i(TAG, "聚合查询解析完成: " + state);
            return completed(ResultCode.SUCCESS, state, "双连接页聚合查询成功");
        } catch (RuntimeException e) {
            HsLog.e(TAG, "解析双连接页聚合数据失败", e);
            return completed(ResultCode.FAILED, null, "解析失败: " + e.getMessage());
        }
    }

    private ProtocolDual parseTopLevelTlv(String payloadHex) {
        Boolean dualEnabled = null;
        String priorityAddress = "";
        List<ProtocolPaired> devices = List.of();
        int index = 0;

        while (index < payloadHex.length()) {
            if (index + 2 > payloadHex.length()) {
                throw new IllegalArgumentException("顶层 TLV 类型字段不完整");
            }

            int topType = Integer.parseInt(payloadHex.substring(index, index + 2), 16);
            index += 2;

            int valueLen;
            if (topType == TOP_TLV_DEVICE_LIST) {
                if (index + 4 > payloadHex.length()) {
                    throw new IllegalArgumentException("顶层 TLV 0x03 长度字段不足");
                }
                valueLen = Integer.parseInt(payloadHex.substring(index, index + 4), 16);
                index += 4;
            } else {
                if (index + 2 > payloadHex.length()) {
                    throw new IllegalArgumentException(
                            "顶层 TLV 0x" + String.format("%02X", topType) + " 长度字段不足");
                }
                valueLen = Integer.parseInt(payloadHex.substring(index, index + 2), 16);
                index += 2;
            }

            if (index + valueLen * 2L > payloadHex.length()) {
                throw new IllegalArgumentException(
                        "顶层 TLV 0x" + String.format("%02X", topType) + " 数据越界");
            }

            String valueHex = payloadHex.substring(index, index + valueLen * 2);
            index += valueLen * 2;

            switch (topType) {
                case TOP_TLV_DUAL_CONNECTION:
                    dualEnabled = parseDualConnection(valueHex);
                    break;
                case TOP_TLV_PRIORITY:
                    priorityAddress = parsePriorityEquipment(valueHex);
                    break;
                case TOP_TLV_DEVICE_LIST:
                    devices = parseDeviceList(valueHex);
                    break;
                default:
                    HsLog.w(TAG, "未知顶层 TLV 类型: 0x" + String.format("%02X", topType));
                    break;
            }
        }

        if (dualEnabled == null) {
            throw new IllegalArgumentException("响应缺少双连接开关字段");
        }
        return new ProtocolDual(dualEnabled, priorityAddress, devices);
    }

    private boolean parseDualConnection(String valueHex) {
        if (valueHex == null || valueHex.length() != 2) {
            throw new IllegalArgumentException("双连接开关 TLV 长度错误");
        }
        int status = Integer.parseInt(valueHex.substring(0, 2), 16);
        return switch (status) {
            case 0x00 -> false;
            case 0x01 -> true;
            default -> throw new IllegalArgumentException("双连接开关状态超出协议范围");
        };
    }

    private String parsePriorityEquipment(String valueHex) {
        if (valueHex == null || valueHex.length() < 2) {
            throw new IllegalArgumentException("优先设备 TLV 数据为空");
        }

        int status = Integer.parseInt(valueHex.substring(0, 2), 16);
        if (status == PRIORITY_SET && valueHex.length() == 14) {
            return PairedDeviceTlvParser.hexToMacAddress(valueHex.substring(2, 14));
        }
        if (status == PRIORITY_NOT_SET && valueHex.length() == 2) {
            return "";
        }
        throw new IllegalArgumentException("优先设备 TLV 状态或长度错误");
    }

    private List<ProtocolPaired> parseDeviceList(String valueHex) {
        if (valueHex == null || valueHex.length() < 2) {
            return List.of();
        }

        int count = Integer.parseInt(valueHex.substring(0, 2), 16);
        HsLog.d(TAG, "已配对设备数量: " + count);

        if (count == 0) {
            return List.of();
        }

        String deviceTlvHex = valueHex.substring(2);
        List<ProtocolPaired> devices = PairedDeviceTlvParser.parseDevices(deviceTlvHex);
        if (devices.size() != count) {
            // 固件 NV 可能存在无效配对记录：数量字节与实际编码不一致时以实际解析结果为准，
            // 避免整个双连接页因单条脏数据失败而拿不到任何信息
            HsLog.w(TAG, "已配对设备数量(" + count + ")与 TLV 实际解析(" + devices.size()
                    + ")不一致，以实际解析结果为准");
        }
        return new ArrayList<>(devices);
    }
}
